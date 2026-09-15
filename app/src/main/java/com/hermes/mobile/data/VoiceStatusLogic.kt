package com.hermes.mobile.data

/**
 * "Ses ucu durumu" satırı + **ısıtma** akışının saf mantığı (tur-12).
 *
 * Kullanıcı bildirimi (tur-12): bölümdeki durum **sabit** kalıyordu; motor
 * kapalıyken yalnız "kapalı" yazıyor, yükleme süreci/hazır olma anı
 * görünmüyordu. Bu dosya üç işi Android'siz (JVM testinde koşar) çözer:
 *
 *  1. `/health` gövdesini **yapılandırılmış** duruma çevirmek: her motor için
 *     ayrı etiket + açık/kapalı (renk kodu UI'da `HermesColors.Online/Offline`).
 *     Satır biçimi kullanıcının istediği sıradır:
 *     `Metinleştirme: açık · Kahya: kapalı · Kadın: kapalı · Chatterbox: kapalı`
 *  2. Canlı yenileme politikası ([REFRESH_MS] = 4,5 sn): bölüm ekranda
 *     görünürken /health yenilenir, bölüm kapanınca döngü durur.
 *  3. **Isıtma durum makinesi**: `Isıt` düğmesi kısa sabit cümleyi
 *     ([WARM_SENTENCE]) `/synthesize`e gönderir ve motoru yükler. Durumlar
 *     `Isıt → Isıtılıyor… (~2-3 dk) → Hazır ✓` (hata/timeout'ta net mesaj);
 *     çift tıklama koruması [warmStart]'ın aynı nesneyi döndürmesiyle sağlanır.
 *
 * Sözleşme: motorlar **tembel** açılır, soğuk ilk sentez 173-187 sn sürer →
 * tavan [WARM_TIMEOUT_MS] = [VoiceApiEndpoints.SYNTH_TIMEOUT_MS] = 300 sn.
 */
object VoiceStatusLogic {

    /**
     * Canlı yenileme aralığı.
     *
     * 4,5 sn: /health motoru ISITMAZ (sözleşme gereği yalnız durum okur), bu
     * yüzden sık yoklama sunucuyu yormaz; kullanıcı "yüklendi" anını birkaç
     * saniye içinde görür.
     */
    const val REFRESH_MS = 4_500L

    /** Isıtma için gönderilen kısa sabit cümle — motoru yükler, uzun iş yapmaz. */
    const val WARM_SENTENCE = "Merhaba, sesli asistan hazır!"

    /** Isıtma tavanı: istemci sentez zaman aşımıyla aynı (300 sn). */
    const val WARM_TIMEOUT_MS = VoiceApiEndpoints.SYNTH_TIMEOUT_MS

    /** Durum satırındaki parçaların sırası (sözleşmedeki motor sırası). */
    val ENGINE_ORDER: List<VoiceSpeakLogic.Engine> = listOf(
        VoiceSpeakLogic.Engine.KAHYA,
        VoiceSpeakLogic.Engine.KADIN,
        VoiceSpeakLogic.Engine.CHATTERBOX,
    )

    /** Durum satırının tek parçası: etiket + açık/kapalı + bilinmiyor. */
    data class Chip(
        val label: String,
        val on: Boolean,
        /** Sunucu bu anahtar için değer verdiyse true; yoksa "bilinmiyor". */
        val known: Boolean = true,
    )

    /**
     * `/health` denemesinin sonucu.
     *
     * [health] null ise [error] doludur (uç yok / 403 / ulaşılamadı) — satır
     * "Şimdi dene" düğmesinin sonucudur, canlı yenilemenin de.
     */
    data class Probe(
        val health: VoiceHealth? = null,
        val base: String = "",
        val error: String? = null,
        val atMs: Long = 0L,
    ) {
        val ok: Boolean get() = health != null
        val engines: Map<String, String> get() = health?.engines ?: emptyMap()

        /** Durum satırı gösterilebilir mi (en az bir bilgi var mı). */
        val usable: Boolean get() = ok
    }

    // ── Durum satırı ──────────────────────────────────────────────────

    /** Motorun kısa görünen adı (durum satırında kullanılır). */
    fun engineShort(engine: VoiceSpeakLogic.Engine, t: (String, String) -> String): String =
        when (engine) {
            VoiceSpeakLogic.Engine.KAHYA -> t("Kahya", "Kahya")
            VoiceSpeakLogic.Engine.KADIN -> t("Kadın", "Female")
            VoiceSpeakLogic.Engine.CHATTERBOX -> t("Chatterbox", "Chatterbox")
        }

    /** Satırın ilk parçası: `Metinleştirme` (STT). */
    fun sttLabel(t: (String, String) -> String): String = t("Metinleştirme", "Transcription")

    /**
     * `/health` gövdesinden durum parçaları.
     *
     * Eksik motor anahtarı **kapalı** sayılır (fail-closed): motor açık
     * olduğunu iddia etmiyorsa ısıtma düğmesi görünür.
     */
    fun chips(h: VoiceHealth, t: (String, String) -> String): List<Chip> {
        val stt = Chip(
            label = sttLabel(t),
            on = h.sttOk,
            known = h.stt.isNotBlank(),
        )
        val engines = ENGINE_ORDER.map { e ->
            val raw = h.engines.entries
                .firstOrNull { it.key.equals(e.id, ignoreCase = true) }?.value
            Chip(
                label = engineShort(e, t),
                on = isOn(raw),
                known = raw != null,
            )
        }
        return listOf(stt) + engines
    }

    /** Parçanın değer metni: `açık` / `kapalı` / `bilinmiyor`. */
    fun valueText(chip: Chip, t: (String, String) -> String): String = when {
        !chip.known -> t("bilinmiyor", "unknown")
        chip.on -> t("açık", "on")
        else -> t("kapalı", "off")
    }

    /**
     * Kullanıcının istediği tek satır biçimi:
     * `Metinleştirme: açık · Kahya: kapalı · Kadın: kapalı · Chatterbox: kapalı`
     */
    fun line(chips: List<Chip>, t: (String, String) -> String): String =
        chips.joinToString(" · ") { "${it.label}: ${valueText(it, t)}" }

    /**
     * `kapali/acik` (ve Türkçe karşılıkları) → açık mı.
     *
     * Türkçe harf tuzağı: `"AÇIK".lowercase()` JVM'de yerel ayara göre
     * `"açık"` DEĞİL `"açik"` (noktasız ı kaybolur) verebiliyor ve `İ` bazı
     * yerellerde `i̇` (birleşik nokta) döndürüyor — bu yüzden karşılaştırma
     * öncesi ASCII'ye indirgiyoruz ([ascii]).
     */
    fun isOn(raw: String?): Boolean {
        val v = ascii(raw ?: return false)
        return v == "acik" || v == "on" || v == "true" || v == "hazir" || v == "ready"
    }

    /** Türkçe harfleri ASCII'ye indirip küçük harfe çevirir (yerel ayardan bağımsız). */
    fun ascii(s: String): String = buildString(s.length) {
        for (ch in s.trim()) {
            when (ch) {
                'I', 'İ', 'ı', 'i' -> append('i')
                'Ç', 'ç' -> append('c')
                'Ş', 'ş' -> append('s')
                'Ğ', 'ğ' -> append('g')
                'Ö', 'ö' -> append('o')
                'Ü', 'ü' -> append('u')
                else -> append(ch.lowercaseChar())
            }
        }
    }

    /** Seçili motor açık mı — eksik/boş bilgi **kapalı** sayılır (fail-closed). */
    fun engineOpen(h: VoiceHealth?, engine: VoiceSpeakLogic.Engine): Boolean {
        val raw = h?.engines?.entries
            ?.firstOrNull { it.key.equals(engine.id, ignoreCase = true) }?.value
        return isOn(raw)
    }

    /**
     * `Isıt` düğmesi görünür mü: durum BİLİNİYOR (uç yanıt verdi) ve seçili
     * motor kapalı. Uç yanıt vermiyorsa ısıtma da çalışmaz — düğme
     * gösterilmez, hata satırı gösterilir.
     */
    fun warmVisible(probe: Probe?, engine: VoiceSpeakLogic.Engine): Boolean =
        probe?.health != null && !engineOpen(probe.health, engine)

    /** "Şimdi dene" sonucu motor kapalı çıktığında gösterilen ipucu. */
    fun coldHint(t: (String, String) -> String): String = t(
        "Motor kapalı — 'Isıt' ile ön-yüklersen ilk çağrı hızlı olur",
        "The engine is off — preload it with 'Warm up' and the first call will be fast",
    )

    // ── Isıtma durum makinesi ─────────────────────────────────────────

    enum class WarmPhase { Idle, Warming, Ready, Failed }

    data class WarmState(
        val phase: WarmPhase = WarmPhase.Idle,
        /** Isıtılan motor — motor değişirse "Hazır ✓" o motora ait sayılmaz. */
        val engineId: String = "",
        val message: String? = null,
        val startedAtMs: Long = 0L,
        val tookMs: Long = 0L,
        val atMs: Long = 0L,
    ) {
        val busy: Boolean get() = phase == WarmPhase.Warming
        val ready: Boolean get() = phase == WarmPhase.Ready
    }

    /**
     * `Isıt` düğmesine basıldı.
     *
     * **Çift tık koruması:** zaten ısıtılıyorsa AYNI nesne döner
     * (`===`), çağıran ikinci isteği başlatmaz.
     */
    fun warmStart(
        prev: WarmState,
        engine: VoiceSpeakLogic.Engine,
        now: Long,
    ): WarmState {
        if (prev.phase == WarmPhase.Warming) return prev
        return WarmState(
            phase = WarmPhase.Warming,
            engineId = engine.id,
            startedAtMs = now,
            atMs = now,
        )
    }

    /** Isıtma başarılı: motor artık hazır (yanıt baytları geldi). */
    fun warmDone(prev: WarmState, tookMs: Long, now: Long, t: (String, String) -> String): WarmState =
        WarmState(
            phase = WarmPhase.Ready,
            engineId = prev.engineId,
            message = warmDoneMsg(tookMs, t),
            startedAtMs = prev.startedAtMs,
            tookMs = tookMs,
            atMs = now,
        )

    /** Isıtma başarısız (ağ hata mesajı ya da tavan). */
    fun warmFail(prev: WarmState, message: String, now: Long): WarmState =
        WarmState(
            phase = WarmPhase.Failed,
            engineId = prev.engineId,
            message = message,
            startedAtMs = prev.startedAtMs,
            tookMs = (now - prev.startedAtMs).coerceAtLeast(0L),
            atMs = now,
        )

    /** Isıtma tamamlandı satırı — süre saniye olarak görünür. */
    fun warmDoneMsg(tookMs: Long, t: (String, String) -> String): String = t(
        "Motor ısıtıldı (${seconds(tookMs)} sn) — ilk ses artık hızlı",
        "Engine warmed up (${seconds(tookMs)} s) — the first playback is fast now",
    )

    /** Tavan aşıldı — motor arka planda yüklenmeye devam ediyor olabilir. */
    fun warmTimeoutMsg(t: (String, String) -> String): String = t(
        "Isıtma ${WARM_TIMEOUT_MS / 1000} sn'de tamamlanmadı — motor arkada yükleniyor " +
            "olabilir; birazdan 'Yenile' ile durumu denetle",
        "Warming up did not finish within ${WARM_TIMEOUT_MS / 1000} s — the engine may still be " +
            "loading; check the state with 'Refresh' in a moment",
    )

    /** Düğme etiketi: Isıt / Isıtılıyor… (~2-3 dk) / Hazır ✓ / Yeniden dene. */
    fun warmLabel(
        state: WarmState,
        engine: VoiceSpeakLogic.Engine,
        t: (String, String) -> String,
    ): String = when {
        state.busy -> t("Isıtılıyor… (~2-3 dk)", "Warming up… (~2-3 min)")
        state.phase == WarmPhase.Ready && state.engineId == engine.id ->
            t("Hazır ✓", "Ready ✓")
        state.phase == WarmPhase.Failed -> t("Yeniden dene", "Try again")
        else -> t("Isıt", "Warm up")
    }

    /** Bu motor için ısıtma tamam mı ("Hazır ✓" o motora ait mi). */
    fun warmReadyFor(state: WarmState, engine: VoiceSpeakLogic.Engine): Boolean =
        state.phase == WarmPhase.Ready && state.engineId == engine.id

    /**
     * Motor seçimi değişince eski "Hazır ✓" işareti sıfırlanır.
     * Isıtma SÜRERKEN dokunulmaz (iş kaybolmasın).
     */
    fun warmReset(prev: WarmState, engine: VoiceSpeakLogic.Engine): WarmState =
        if (prev.busy || prev.phase == WarmPhase.Idle || prev.engineId == engine.id) prev
        else WarmState()

    /** Saniye metni: 42 → "42", 0,4 sn → "0". */
    fun seconds(ms: Long): String = (ms / 1000L).coerceAtLeast(0L).toString()
}
