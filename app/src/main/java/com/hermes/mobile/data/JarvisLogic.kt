package com.hermes.mobile.data

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tur-23 (JARVIS-1): seslendirme sırasında REAL ses seviyesi üretimi.
 *
 * Android'de `MediaPlayer` oynatırken canlı dalga biçimi verisi YOKTUR
 * (getWaveForm yok, AudioEffect/LoudnessEnhancer bilinçli olarak KULLANILMIYOR
 * — izin/ek-ses-hattı karmaşası ve cihaz farkı riski). Bunun yerine sesli
 * yanıtın WAV gövdesi indirildiği anda **decode edilip RMS karelerine**
 * ayrılır; oynatma zamanıyla (MediaPlayer.position, ms) eşleştirilerek
 * gerçek genlik okunur. MP3 gibi sıkışık biçimlerde WAV bulunamazsa
 * güvenli varsayılan sabit genliğe düşülür (yanlış sessizlik göstermemek
 * için 0 DEĞİL, sabit 0.6f — tur22 BigOrb davranışıyla birebir).
 *
 * Saf Kotlin: Android bağımlılığı yok, JVM testinde PCM baytları üretilerek
 * doğrulanır (RmsFramesTest).
 */
object SpeakLevelLogic {

    /** Kare uzunluğu: 20 ms — gözün algıladığı animasyon çözünürlüğüne yeterli. */
    const val FRAME_MS = 20L

    /** WAV bulunamazsa kullanılan sabit genlik (0 DEĞİL — 'ölü' görünmesin). */
    const val FALLBACK_LEVEL = 0.6f

    /** En yüksek görünen genlik değerinin bu tavanla ezilmesi (patlama önleme). */
    const val MAX_LEVEL = 1f

    /** Genlik okunamayan/boş aralık için nötr taban (halka tamamen sönmez). */
    const val FLOOR_LEVEL = 0.15f

    /**
     * PCM 16-bit little-endian, tek kanal veya çok kanal gövdeden 20 ms'lik
     * RMS kareleri üretir.
     *
     * @param pcm      16-bit PCM örnekleri (LE, imzalı short aralığında)
     * @param sampleRate Hz (ör. 16000, 22050, 24000)
     * @return 0..1 aralığında kare değerleri; boş girdi → boş liste
     */
    fun rmsFrames(pcm: ShortArray, sampleRate: Int): List<Float> {
        if (sampleRate <= 0 || pcm.isEmpty()) return emptyList()
        val frameLen = (sampleRate * FRAME_MS / 1000).coerceAtLeast(1).toInt()
        val out = ArrayList<Float>((pcm.size + frameLen - 1) / frameLen)
        var i = 0
        while (i < pcm.size) {
            val end = minOf(i + frameLen, pcm.size)
            var sum = 0.0
            var n = 0
            for (k in i until end) {
                sum += pcm[k].toDouble() * pcm[k].toDouble()
                n++
            }
            // 32767 tam ölçek → ~0.9999; 10000 ≈ 0.305 → insan sesi RMS'i tipik 0.05..0.3
            // aralığında kalıyor; animasyon için 3.2x kazanç + tavan uygulanır.
            val rms = sqrt(sum / n) / 32767.0
            val lvl = (rms * 3.2).coerceIn(0.0, 1.0)
            out.add(lvl.toFloat())
            i = end
        }
        return out
    }

    /**
     * Oynatma konumuna (ms) göre kare indeksi. Negatif konum 0'a, kare
     * sayısını aşan konum son kareye kenetlenir (oynatma sonu ile PCM
     * kareleme sınırı birebir hizalı olmak zorunda değil).
     */
    fun frameIndexAt(positionMs: Long, frameCount: Int): Int {
        if (frameCount <= 0) return -1
        val idx = (positionMs.coerceAtLeast(0L) / FRAME_MS).toInt()
        return idx.coerceAtMost(frameCount - 1)
    }

    /**
     * Kare değerini zamanla yumuşatır (basit üstel ortalama): ani sıçrama
     * yerine ~3 karelik geçiş. `prev` ilk karede null geçer.
     */
    fun smooth(prev: Float?, next: Float): Float {
        val n = next.coerceIn(0f, MAX_LEVEL)
        if (prev == null) return n
        // α=0.45: yükselirken hızlı, düşerken yumuşak algısı veren sabit karışım
        val v = prev + (n - prev) * 0.45f
        return v.coerceIn(FLOOR_LEVEL.coerceAtMost(n), MAX_LEVEL)
    }

    /**
     * WAV başlığından (44 bayt klasik RIFF ya da uzayan 'LIST' ekli) PCM
     * örneklerini ayıklar. Destek: PCM (format=1), 16-bit, 1/2 kanal,
     * 8000–48000 Hz, canonical 'data' alt-bloğu. Desteklenmeyen her
     * durumda null (çağıran FALLBACK_LEVEL'e düşer) — yarım-yazılmış
     * veya sıkışık dosyada yanlış veri OKUNMAZ.
     */
    fun parseWavPcm(bytes: ByteArray): Pair<ShortArray, Int>? {
        // 44 bayt = klasik canonical başlık (RIFF+fsize+WAVE+fmt(24)+data+size);
        // data gövdesi boşsa aşağıda null'a düşer.
        if (bytes.size < 44) return null
        if (tag(bytes, 0) != "RIFF") return null
        // WAV düzeni: RIFF(0-3) + dosya boyutu(4-7) + WAVE(8-11)
        if (tag(bytes, 8) != "WAVE") return null

        var pos = 12
        var channels = -1
        var sampleRate = -1
        var bits = -1
        var fmtSeen = false
        var pcm: ByteArray? = null

        while (pos + 8 <= bytes.size) {
            val id = tag(bytes, pos)
            val size = le32(bytes, pos + 4)
            if (size < 0) return null
            val body = pos + 8
            val bodyEnd = minOf(body + size, bytes.size)
            when (id) {
                "fmt " -> {
                    if (body + 16 > bytes.size) return null
                    val format = le16(bytes, body)
                    channels = le16(bytes, body + 2)
                    sampleRate = le32(bytes, body + 4)
                    bits = le16(bytes, body + 14)
                    // Yalnızca sade 16-bit PCM — sıkışık/özel biçim reddedilir.
                    if (format != 1 || bits != 16) return null
                    if (channels !in 1..2) return null
                    if (sampleRate !in 8000..48000) return null
                    fmtSeen = true
                }
                "data" -> {
                    if (!fmtSeen) return null
                    pcm = bytes.copyOfRange(body, bodyEnd)
                }
            }
            // RIFF blokları 2-byte hizalı okunur (padded).
            pos = body + size + (size % 2)
        }

        val data = pcm ?: return null
        if (channels <= 0) return null
        val totalSamples = data.size / 2
        val mono = totalSamples / channels
        if (mono <= 0) return null
        val out = ShortArray(mono)
        for (i in 0 until mono) {
            // Ortalama almak yerine ilk kanalı al: konuşma tek kanalda yeterlidir
            // ve kanal-toplamı girişimi çift kanalda genliği yükseltip tavana yapışır.
            val off = i * channels * 2
            out[i] = ((data[off + 1].toInt() shl 8) or (data[off].toInt() and 0xFF)).toShort()
        }
        return out to sampleRate
    }

    /**
     * Verilen WAV baytlarından 20 ms RMS kare dizisi üretir; çözümleme
     * başarısızsa null (çağıran FALLBACK_LEVEL'e düşer).
     */
    fun framesFromWav(bytes: ByteArray): List<Float>? {
        val parsed = parseWavPcm(bytes) ?: return null
        val (pcm, rate) = parsed
        val frames = rmsFrames(pcm, rate)
        return frames.ifEmpty { null }
    }

    // ── küçük LE okuyucular ─────────────────────────────────────────────
    private fun tag(b: ByteArray, at: Int): String =
        String(b, at, 4, Charsets.ISO_8859_1)

    private fun le16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or
            ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or
            ((b[at + 3].toInt() and 0xFF) shl 24)
}

object JarvisIdentity {
    /**
     * Tur-23: asistan persona adı — TEK KAYNAK. Ekran içi tüm "asistan"
     * metinleri buradan geçer. Android'in sistem rol etiketi ("Hermes
     * Asistan", Settings→Default apps) OS sınırlaması gereğidir ve
     * DEĞİŞMEZ; persona adı ayrıdır.
     */
    const val NAME = "Jarvis"

    /**
     * Tur-23 r1: persona sistem yönergesi — yerel asistan turunun `system`
     * mesajı olarak gerçek kullanıma bağlıdır (ChatViewModel.sendLocalAssistant).
     * Ad TEK KAYNAKTAN (NAME) gelir; persona adı değişirse yönerge de değişir.
     */
    val SYSTEM_PROMPT: String
        get() = "Sen $NAME'sın — kişisel yapay zekâ asistanı. Türkçe konuş. Sesli " +
            "sohbette kısa ve doğal cümleler kur; uzun liste okuma. Emin " +
            "değilsen söyle."
}

/**
 * Tur-23: VoiceVisualizer'ın 4 fazı → animasyon parametre haritası.
 *
 * Fazların KAYNAĞI iki ayrı katmandan gelir (biri canlı-ses, biri sesli-mesaj
 * durum makinesi); her ikisinden tek bir Jarvis fazı türetilir. Harita
 * saf-veridir: UI yalnız sayıları okur, dal davranışı burada test edilir.
 */
enum class JarvisPhase { Idle, Listening, Thinking, Speaking }

/** Tek bir Jarvis fazının çizim parametreleri (sabit, UI'dan bağımsız). */
data class JarvisAnim(
    /** Merkez halka nabız periyodu (ms). 0 = duraq (indirimli animasyon). */
    val pulsePeriodMs: Int,
    /** Nabız genliği: yarıçapın (0..1) ne kadar salınacağı. */
    val pulseAmp: Float,
    /** Dış yayınların dönüş süresi (ms). 0 = duraq (idle / indirimli). */
    val arcPeriodMs: Int,
    /** Genlik barları görünsün mü (yalnız Listening/Speaking). */
    val bars: Boolean,
    /** Merkez parlama (thinking için 1f, diğerleri 0f — UI'da karışım oranı). */
    val centerGlow: Float,
)

object JarvisVisualLogic {

    /** İndirimli animasyon (prefers-reduced-motion): 0 = duraq, 1 = tam hız. */
    const val REDUCED = 0f

    /**
     * 4 temel fazın parametre tablosu. Sıra sabit ve testte indeksle
     * doğrulanır: Idle / Listening / Thinking / Speaking.
     */
    val ANIMS: Map<JarvisPhase, JarvisAnim> = mapOf(
        // Idle: yavaş nefes; yayınlar durağan (pil + dikkat).
        JarvisPhase.Idle to JarvisAnim(
            pulsePeriodMs = 2600, pulseAmp = 0.06f,
            arcPeriodMs = 0, bars = false, centerGlow = 0f,
        ),
        // Listening: hızlı nefes + yayınlar döner + genlik barları (mic seviyesi).
        JarvisPhase.Listening to JarvisAnim(
            pulsePeriodMs = 900, pulseAmp = 0.12f,
            arcPeriodMs = 1600, bars = true, centerGlow = 0f,
        ),
        // Thinking (STT→sunucu): yayınlar hızlı, merkez parlar, bar yok.
        JarvisPhase.Thinking to JarvisAnim(
            pulsePeriodMs = 520, pulseAmp = 0.04f,
            arcPeriodMs = 700, bars = false, centerGlow = 1f,
        ),
        // Speaking: genlik-sürücülü büyüme (amplitude), yayınlar orta hızda,
        // dış ışıma seviyeyle artar (UI'da alpha).
        JarvisPhase.Speaking to JarvisAnim(
            pulsePeriodMs = 650, pulseAmp = 0.08f,
            arcPeriodMs = 1300, bars = true, centerGlow = 0.35f,
        ),
    )

    /**
     * İndirimli animasyonda (Settings → animasyon 0) hareket 0'a iner;
     * renk/faz değişimi KALIR (accessibility: bilgi kaybı yok, hareket yok).
     */
    fun reducedOf(anim: JarvisAnim): JarvisAnim =
        anim.copy(pulsePeriodMs = 0, arcPeriodMs = 0, pulseAmp = 0f, bars = false, centerGlow = anim.centerGlow)

    /**
     * Canlı-ses (LiveVoiceClient) durumundan Jarvis fazı — harita
     * total'dir; yeni bir durum eklenirse test yakalar (default = Idle).
     */
    fun fromLiveState(stateName: String): JarvisPhase = when (stateName) {
        "Listening" -> JarvisPhase.Listening
        // LiveVoiceClient.State'teThinking yok; STT→sunucu aralığı Connecting'tir.
        "Connecting" -> JarvisPhase.Thinking
        "Speaking" -> JarvisPhase.Speaking
        else -> JarvisPhase.Idle
    }

    /**
     * Sesli-mesaj durum makinesinden (VoiceSpeakLogic.Phase adı) Jarvis
     * fazı — indirme Thinking, çalma Speaking, boş/null Idle.
     * (Gerçek enum: Idle | Downloading | Playing — uydurma aşama YOK.)
     */
    fun fromSpeakStage(phaseName: String?): JarvisPhase = when (phaseName) {
        "Downloading" -> JarvisPhase.Thinking
        "Playing" -> JarvisPhase.Speaking
        null, "", "Idle" -> JarvisPhase.Idle
        else -> JarvisPhase.Idle
    }

    /**
     * Asistan-modu şerit fazından (AssistantModeLogic.Phase adı) Jarvis
     * fazı — kayıt=Listening, çeviri/bekleme=Thinking, okuma=Speaking,
     * hazır/kapalı=Idle.
     */
    fun fromAssistantPhase(name: String): JarvisPhase = when (name) {
        "Recording" -> JarvisPhase.Listening
        "Transcribing", "AwaitingReply" -> JarvisPhase.Thinking
        "Speaking" -> JarvisPhase.Speaking
        else -> JarvisPhase.Idle
    }

    /**
     * Speaking fazında çizilecek efektif genlik.
     * - frame == null → oynatıcıdan kare alınamadı (mp3 / parse yok):
     *   FALLBACK sabiti (tur22 BigOrb 0.6f ile birebir — sessiz görünmez).
     * - frame bulunduysa → yumuşatılmış gerçek RMS.
     */
    fun effectiveSpeakLevel(frame: Float?, prev: Float?): Float {
        if (frame == null) return abs(SpeakLevelLogic.FALLBACK_LEVEL)
        return SpeakLevelLogic.smooth(prev, frame)
    }
}
