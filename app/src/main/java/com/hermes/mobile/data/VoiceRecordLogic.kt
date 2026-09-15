package com.hermes.mobile.data

/**
 * Kayıt durum makinesi — **saf** (Android'e bağlı değil, JVM testinde koşar).
 *
 * Neden ayrı: bas-konuş düğmesinin yarışları (iki kez bas, parmağı kaydırıp
 * bırak, 60 sn dolmadan bırak, kayıt sürerken ekrandan çık) en kolay yanlış
 * giden yer. Karar burada tek yerde veriliyor; UI yalnız çiziyor,
 * [VoiceMessageController] yalnız uyguluyor (tur-10 dersi: bayrak yetmez,
 * geçişleri saf makineye al).
 *
 * Sözleşme: ses yükleme **<= 60 sn** → kayıt 60 sn'de KENDİLİĞİNDEN durur
 * ([VoiceApiEndpoints.MAX_RECORD_MS]) ve parmak hâlâ basılıyken yükleme
 * başlar ([autoStopReached]).
 */
object VoiceRecordLogic {

    /** Parmak 0,8 sn'den kısa kaldıysa kazara basma sayılır, hiç yüklenmez. */
    const val MIN_MILLIS = 800L

    /** Kayıt fazı. */
    enum class Phase { Idle, Recording, Transcribing, Failed }

    data class State(
        val phase: Phase = Phase.Idle,
        val startedAtMs: Long = 0L,
        val elapsedMs: Long = 0L,
        val message: String? = null,
    ) {
        val recording: Boolean get() = phase == Phase.Recording
        val busy: Boolean get() = phase == Phase.Transcribing
    }

    /** Parmak bırakıldığında ne yapılacak. */
    enum class ReleaseAction { Discard, Upload, Ignore }

    fun start(state: State, nowMs: Long): State = when (state.phase) {
        // Kayıt sürerken gelen ikinci basış YOK sayılır (yeniden başlatma yok).
        Phase.Recording, Phase.Transcribing -> state
        Phase.Idle, Phase.Failed -> State(Phase.Recording, startedAtMs = nowMs)
    }

    /** Süre tazelenir; 60 sn tavanı aşılmaz (etiket 1:00'da durur). */
    fun tick(state: State, elapsedMs: Long): State =
        if (state.phase != Phase.Recording) state
        else state.copy(elapsedMs = elapsedMs.coerceIn(0L, VoiceApiEndpoints.MAX_RECORD_MS))

    /** Kayıt 60 sn'yi doldurdu mu — parmak basılı olsa da yükleme başlar. */
    fun autoStopReached(state: State, elapsedMs: Long): Boolean =
        state.phase == Phase.Recording && elapsedMs >= VoiceApiEndpoints.MAX_RECORD_MS

    /**
     * Parmak bırakıldı.
     *
     * @param elapsedMs kaydın süresi
     * @return yeni durum + yapılacak işlem
     */
    fun release(state: State, elapsedMs: Long): Pair<State, ReleaseAction> = when (state.phase) {
        Phase.Recording ->
            if (elapsedMs < MIN_MILLIS) {
                State(Phase.Idle, message = null) to ReleaseAction.Discard
            } else {
                State(Phase.Transcribing, elapsedMs = elapsedMs) to ReleaseAction.Upload
            }
        // Kayıt yokken bırakma (kaydırıp çıkma) hiçbir şey yapmaz.
        Phase.Idle, Phase.Failed, Phase.Transcribing -> state to ReleaseAction.Ignore
    }

    /** Metinleştirme başarılı — durum Idle'a döner, metin çağırana aittir. */
    fun transcribed(state: State): State = State(Phase.Idle)

    fun failed(state: State, message: String): State = State(Phase.Failed, message = message)

    fun cancelled(): State = State(Phase.Idle)

    /** Kayıt geri sayım etiketi: `0:07`, `0:59`, `1:00` (tam saniye, aşağı yuvarlanır). */
    fun timerLabel(elapsedMs: Long): String {
        val total = (elapsedMs.coerceIn(0L, VoiceApiEndpoints.MAX_RECORD_MS)) / 1000
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }

    /** Kayıt halkası için 0..1 ilerleme. */
    fun progress(elapsedMs: Long): Float =
        (elapsedMs.toFloat() / VoiceApiEndpoints.MAX_RECORD_MS).coerceIn(0f, 1f)

    /** Kayıt dosyası adı — çakışmasın diye zaman damgalı. */
    fun fileName(nowMs: Long): String = "kayit-$nowMs.ogg"

    /** Kayıt sürerken ekranda görünen durum satırı. */
    fun recordHint(state: State, t: (String, String) -> String): String = when (state.phase) {
        Phase.Recording ->
            t("Dinliyorum… bırakınca metne çevirir (en fazla 60 sn)",
                "Listening… release to transcribe (max 60 s)")
        Phase.Transcribing -> t("Metne çevriliyor…", "Transcribing…")
        Phase.Idle -> ""
        Phase.Failed -> t("Kayıt başarısız", "Recording failed")
    }
}
