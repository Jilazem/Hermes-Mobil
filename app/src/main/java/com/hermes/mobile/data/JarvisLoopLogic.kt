package com.hermes.mobile.data

import java.util.Locale

/**
 * JARVIS-2 (tur24) — sürekli sesli sohbet döngüsünün **saf** karar katmanı.
 *
 * Neden ayrı: "tek dokunuşla açılan, yanıt bitince kendiliğinden dinlemeye
 * dönen" döngünün yanlış gitmeye en açık yerleri zamanlama kararlarıdır
 * (ne zaman kes?, kaç hatada kapat?, hangi motor?). Basılı-tut akışından
 * ([VoiceRecordLogic]) taban tabana zıt bir sözleşmesi var: burada parmak
 * kalkmaz — sessizlik kalkar. Geçişler tek yerde, JVM testinde saat sahte
 * `nowMs`'lerle koşsun diye Android'den bağımsız yazıldı (tur-10 dersi:
 * bayrak yetmez, geçişleri saf makineye al).
 *
 * Döngü: Listening → (VAD keser) Stt → Gönder → WaitReply → Speaking →
 * oynatma biterse tekrar Listening. Hata (STT boş / ağ kopuk) 2 kez denenir,
 * 3.'sünde döngü KAPATILIR — sonsuz döngü yasak (görev maddesi 1).
 */
object JarvisLoopLogic {

    /** Döngü fazı. `Off` = döngü kapalı (basılı-tut akışı bundan bağımsız). */
    enum class Phase { Off, Listening, Stt, WaitReply, Speaking }

    data class State(
        val phase: Phase = Phase.Off,
        /** Ardışık boş-STT / ağ hatası sayısı — başarıda sıfırlanır. */
        val failures: Int = 0,
        /** Bu dinleme turunun başlangıcı (VAD penceresi). */
        val startedAtMs: Long = 0L,
        /** Son sesli örnek — sessizlik sayacı bundan işler. */
        val lastVoiceAtMs: Long = 0L,
        /** Bu turda hiç ses geldi mi (giriş toleransı için). */
        val sawVoice: Boolean = false,
    )

    /** VAD kesme kararı. */
    enum class VadCut { Continue, CutSilence, CutTimeout, CutNoVoice }

    /** Ses sayılan eşik — `MediaRecorder.getMaxAmplitude()` 0..32767 ölçeği. */
    const val AMP_THRESHOLD = 1500

    /** Ses geldikten sonra kesme için gereken ardışık sessizlik (görev: 1.2-1.5 sn). */
    const val SILENCE_MS = 1300L

    /** Hiç ses gelmezse tur boş sayılır ve kesilir — kullanıcı döngüde bekletilmez. */
    const val NO_VOICE_MS = 6_000L

    /** Sözleşme tavanı: tek kayıt <=60 sn (`VoiceApiEndpoints.MAX_RECORD_MS` ile aynı). */
    const val MAX_LISTEN_MS = VoiceApiEndpoints.MAX_RECORD_MS

    /** Kabul edilecek ardışık hata sayısı — üstünde döngü kapanır. */
    const val MAX_FAILURES = 2

    /** `/health` önbellek tavanı (görev maddesi 3). */
    const val HEALTH_CACHE_MS = 10_000L

    /** VAD yoklama aralığı — kayıt ticker'ıyla aynı ritim. */
    const val VAD_TICK_MS = 120L

    /** Döngüyü tek dokunuşla açan başlangıç durumu. */
    fun listening(startedAtMs: Long): State = State(
        phase = Phase.Listening,
        startedAtMs = startedAtMs,
        lastVoiceAtMs = startedAtMs,
    )

    /**
     * Bir genlik örneği işler: eşik üstü → `lastVoiceAtMs` tazelenir,
     * `sawVoice` kalıcı olur. Eşik altı örnek zamanı İLERİ almaz.
     */
    fun vadSample(s: State, amplitude: Int, nowMs: Long): State {
        if (s.phase != Phase.Listening) return s
        return if (amplitude >= AMP_THRESHOLD) {
            s.copy(lastVoiceAtMs = nowMs, sawVoice = true)
        } else {
            s
        }
    }

    /**
     * Kesme kararı. Öncelik sırası kasıtlı: önce 60 sn tavanı (sözleşme),
     * sonra sessizlik, sonra hiç-ses tavanı — hepsi keserse Stt'ye geçilir.
     */
    fun vadCut(s: State, nowMs: Long): VadCut {
        if (s.phase != Phase.Listening) return VadCut.Continue
        val elapsed = (nowMs - s.startedAtMs).coerceAtLeast(0L)
        return when {
            elapsed >= MAX_LISTEN_MS -> VadCut.CutTimeout
            s.sawVoice && (nowMs - s.lastVoiceAtMs) >= SILENCE_MS -> VadCut.CutSilence
            !s.sawVoice && elapsed >= NO_VOICE_MS -> VadCut.CutNoVoice
            else -> VadCut.Continue
        }
    }

    // ── Hata politikası ──────────────────────────────────────────────

    /**
     * Boş-STT / ağ hatası işler.
     *
     * @return yeni durum + "döngüyü kapat" kararı (3. hatada true)
     */
    fun onSttFailure(s: State, nowMs: Long): Pair<State, Boolean> {
        val n = s.failures + 1
        return if (n > MAX_FAILURES) {
            s.copy(failures = n) to true
        } else {
            // Deneme hakkı var: sayaç KORUNUR, tur sıfırlanır (yoksa ticker inmez).
            listening(nowMs).copy(failures = n) to false
        }
    }

    /** Oynatma bitti → otomatik tekrar dinlemeye geçiş (Sayaç KORUNUR —
     *  tura başlarken başarılı STT zaten sıfırladı; buraya varan tur başarılıdır). */
    fun onSpeakDone(s: State, nowMs: Long): State =
        if (s.phase == Phase.Off) s else listening(nowMs)

    /** Faz geçiş yardımcıları (durum bütünlüğü tek yerde). */
    fun toStt(s: State): State = s.copy(phase = Phase.Stt)

    /**
     * Başarılı STT → Gönder + WaitReply: hata sayacı burada SIFIRLANIR —
     * "ardışık" hata, arada başarılı tur varsa artık ardışık değildir
     * (görev 1: 2 hata payı art arda olmayan hatalara tolerans göstermez).
     */
    fun toWaitReplyOk(s: State): State = s.copy(phase = Phase.WaitReply, failures = 0)
    fun toWaitReply(s: State): State = s.copy(phase = Phase.WaitReply)
    fun toSpeaking(s: State): State = s.copy(phase = Phase.Speaking)
    fun toOff(s: State): State = State()

    // ── "Kapat" komutu ────────────────────────────────────────────────

    /**
     * STT metninde basit komut eşleşmesi — tam eşitlik (küçük harf TR, nokta
     * sansürsüz, kırpılmış). "kapat şunu" gibi uzun cümleler BİLİNÇLİ olarak
     * eşleşmez: asistan komutu kısa olmalı, yanlışlıkla kapanma daha kötü.
     */
    val STOP_COMMANDS: Set<String> = setOf(
        "kapat", "dur", "durdur", "bitti", "bitsin", "yeter", "çıkalım", "durdu",
    )

    fun isStopCommand(text: String): Boolean {
        val norm = text.trim()
            .lowercase(newLocaleTr())
            .trimEnd('.', '!', '?', ':', ';', ',', ' ', '\n', '\t')
            .trim()
        return norm in STOP_COMMANDS
    }

    /** TR küçültme — `i` düzgün küçülsün (I → ı) ve Azero gibi tuzaklardan kaçınalım. */
    private fun newLocaleTr(): Locale = Locale("tr")

    // ── Motor düşüş sırası ───────────────────────────────────────────

    /** Görev maddesi 3: chatterbox → kadin → pocket (kullanıcı favorisi önce). */
    val LOOP_FALLBACK: List<String> =
        listOf("chatterbox", "kadin", "pocket-kadin", "pocket-erkek", "yerel")

    /** `/health` engines haritasında motor açık mı (`"acik"`/`"açık"`). */
    fun engineOn(engines: Map<String, String>, id: String): Boolean {
        val v = engines.entries.firstOrNull { it.key.equals(id, ignoreCase = true) }?.value
            ?: return false
        return v.trim().lowercase(newLocaleTr()) in setOf("acik", "açık", "true", "on")
    }

    /**
     * Döngü motorunu çöz: tercih açıksa o; kapalı/bilinmiyorsa düşüş
     * sırasındaki ilk açık motor; hepsi kapalıysa null (çağıran kibar
     * metin + ton ile döngüyü kapatır — sessiz başarısızlık yok).
     *
     * `engines` boşsa (sunucuya hiç ulaşılamadı) tercih olduğu gibi döner:
     * "bilinmiyor" ≠ "kapalı" — denemeye değer, hata olursa akış zaten sayar.
     */
    fun resolveLoopEngine(preferredId: String, engines: Map<String, String>): String? {
        if (engines.isEmpty()) return preferredId
        if (engineOn(engines, preferredId)) return preferredId
        return LOOP_FALLBACK.firstOrNull { engineOn(engines, it) }
    }

    /** Düşüş zinciri: `from`'dan sonraki açık motor; tükendiyse null. */
    fun nextEngineOn(fromId: String, engines: Map<String, String>): String? {
        val i = LOOP_FALLBACK.indexOf(fromId)
        if (i < 0) return null
        return LOOP_FALLBACK.drop(i + 1).firstOrNull { engineOn(engines, it) }
    }

    /** `/health` 10 sn cache: süre doldu mu (ilk çağrıda sonMs=0 → true). */
    fun healthDue(lastProbeMs: Long, nowMs: Long): Boolean =
        nowMs - lastProbeMs >= HEALTH_CACHE_MS

    /**
     * Tam ekran altyazı satırı (görev maddesi 2) — saf veri: "sen" ve
     * "Jarvis" rolleri tek listede tutulur, UI kayan akış olarak çizer.
     */
    data class Caption(val role: String, val text: String)

    /** Döngü durum noktası etiketi (tam ekran üst şeridi — saf metin). */
    fun phaseLabel(s: State, t: (String, String) -> String): String = when (s.phase) {
        Phase.Off -> ""
        Phase.Listening -> t("Dinliyor…", "Listening…")
        Phase.Stt -> t("Metne çevriliyor…", "Transcribing…")
        Phase.WaitReply -> t("Düşünüyor…", "Thinking…")
        Phase.Speaking -> t("Söylüyor", "Speaking")
    }
}
