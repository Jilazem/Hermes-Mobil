package com.hermes.mobile.data

/**
 * Sesli okuma (TTS) kararları — **saf** katman.
 *
 * Sözleşme + ses ucu ekibinin 15.09 notları:
 *  - `POST /synthesize {text, engine}` → `audio/ogg`; motorlar
 *    `kahya | chatterbox | kadin`, **varsayılan kahya**.
 *  - Motorlar **tembel** açılır (`kahya :8172`, `chatterbox :8173`); kapalıysa
 *    servis kendisi açar ve **ilk çağrı yavaştır** (soğukken 173-187 sn).
 *    Bu yüzden UI'da "ilk yanıt uzun sürebilir" durumu gösterilir
 *    ([coldStartHint]).
 *  - Üretim önerisi: ana motor **kahya**, kadın ses için **kadin**;
 *    chatterbox referanssızken kararsız (bant geziyor) → listede var ama
 *    varsayılan değil.
 *
 * Okunacak metin markdown'dır (asistan balonu) — biçim işaretleri sese
 * dönüşmemeli ([plainText]).
 */
object VoiceSpeakLogic {

    /** Motorlar — sözleşmedeki dizgi kimlikleri. */
    enum class Engine(val id: String) {
        KAHYA("kahya"),
        CHATTERBOX("chatterbox"),
        KADIN("kadin");

        companion object {
            val DEFAULT = KAHYA

            /** Bilinmeyen/boş kimlik varsayılana düşer (fail-closed değil, sessiz). */
            fun fromId(raw: String?): Engine =
                entries.firstOrNull { it.id == raw?.trim()?.lowercase() } ?: DEFAULT

            val ids: List<String> get() = entries.map { it.id }
        }
    }

    /** Bu süreden sonra "ilk yanıt uzun sürebilir" satırı gösterilir. */
    const val COLD_HINT_AFTER_MS = 8_000L

    /** Tek istekte gönderilecek azami karakter — uzun yanıt kırpılır. */
    const val MAX_CHARS = 1_200

    enum class Phase { Idle, Downloading, Playing }

    data class State(
        val phase: Phase = Phase.Idle,
        /** Çalınan/indirilen mesajın anahtarı — aynı mesaja ikinci basış durdurur. */
        val key: String? = null,
        val waitingMs: Long = 0L,
        val message: String? = null,
        val cached: Boolean = false,
    ) {
        val busy: Boolean get() = phase == Phase.Downloading
        val playing: Boolean get() = phase == Phase.Playing
    }

    /** Ayarlardaki motor seçeneği için etiketler. */
    fun engineOptions(t: (String, String) -> String): List<Pair<String, String>> = listOf(
        Engine.KAHYA.id to t("Kahya (önerilen)", "Kahya (recommended)"),
        Engine.KADIN.id to t("Kadın", "Female"),
        Engine.CHATTERBOX.id to t("Chatterbox (deneysel)", "Chatterbox (experimental)"),
    )

    /** Motor açıklaması — Ayarlar satırının altı. */
    fun engineHint(engine: Engine, t: (String, String) -> String): String = when (engine) {
        Engine.KAHYA -> t(
            "Ana motor. İlk sentez motoru ısıtır: 2-3 dakika sürebilir.",
            "Primary engine. The first synthesis warms the engine: it can take 2-3 minutes.",
        )
        Engine.KADIN -> t(
            "Kadın ses. Üretimde kadın ses için önerilen motor.",
            "Female voice. The recommended engine for a female voice.",
        )
        Engine.CHATTERBOX -> t(
            "Referanssızken kararsız (bant geziyor) — deneysel, varsayılan değil.",
            "Unstable without a reference (band wanders) — experimental, not the default.",
        )
    }

    /**
     * Bekleme sırasında gösterilecek satır.
     *
     * Soğuk motor uyarısı [COLD_HINT_AFTER_MS] sonra çıkar: hemen göstermek
     * yanlış alarm olurdu (ılık motorda yanıt 1-2 sn).
     */
    fun statusLine(state: State, t: (String, String) -> String): String? = when (state.phase) {
        // Idle'da yalnız son hata/uyarı satırı varsa gösterilir.
        Phase.Idle -> state.message
        Phase.Playing -> t("Çalıyor — durdurmak için dokun", "Playing — tap to stop")
        Phase.Downloading ->
            if (state.waitingMs >= COLD_HINT_AFTER_MS) {
                t(
                    "İlk yanıt uzun sürebilir: ses motoru şimdi açılıyor (soğukken 3 dakikaya kadar)",
                    "The first reply can take a while: the voice engine is starting now (up to 3 minutes when cold)",
                )
            } else {
                t("Ses indiriliyor…", "Downloading audio…")
            }
    }

    /** Markdown'ı ses için sade metne çevirir. */
    fun plainText(markdown: String): String {
        var s = markdown
        // Kod blokları sesli okunmaz: içerik yerine işaretleyici.
        s = Regex("```[\\s\\S]*?```").replace(s, " ")
        s = Regex("`([^`]*)`").replace(s, "$1")
        // Markdown bağlantı → yalnız görünen metin.
        s = Regex("!\\[([^\\]]*)\\]\\([^)]*\\)").replace(s, "$1")
        s = Regex("\\[([^\\]]*)\\]\\([^)]*\\)").replace(s, "$1")
        s = Regex("(?m)^\\s{0,3}#{1,6}\\s*").replace(s, "")
        s = Regex("(?m)^\\s{0,3}>\\s?").replace(s, "")
        s = Regex("(?m)^\\s*[-*+]\\s+").replace(s, " ")
        s = Regex("(?m)^\\s*\\d+[.)]\\s+").replace(s, " ")
        s = s.replace("**", "").replace("__", "").replace("*", "").replace("_", " ")
        s = s.replace("~~", "")
        s = Regex("\\|").replace(s, " ")
        s = Regex("[ \\t]+").replace(s, " ")
        s = Regex("\\n{2,}").replace(s, "\n")
        return s.trim()
    }

    /** Sentez için hazırlanan metin: sadeleştir, kırp, boşsa null. */
    fun prepare(markdown: String, maxChars: Int = MAX_CHARS): String? {
        val plain = plainText(markdown)
        if (plain.isBlank()) return null
        if (plain.length <= maxChars) return plain
        // Cümle ortasında kesmemek için son boşluktan kırp.
        val cut = plain.take(maxChars)
        val at = cut.lastIndexOfAny(charArrayOf(' ', '\n', '.', ',', '!', '?'))
        return (if (at > maxChars / 2) cut.take(at) else cut).trim() + "…"
    }

    /**
     * İndirilen sesin önbellek dosya adı.
     *
     * Aynı metin + aynı motor aynı dosyayı verir; ikinci dokunuşta indirme
     * yok. Hash Kotlin'in `hashCode`ı: önbellek **cihaz içi**, çakışma riski
     * (`hashCode` 32 bit) burada zararsız — en kötü ihtimalle başka bir metin
     * çalınır ve dosya yeni indirmeyle değiştirilir; bu yüzden adı üretirken
     * metin uzunluğu da ekleniyor.
     */
    fun cacheName(text: String, engine: Engine): String {
        val h = (text.hashCode().toLong() shl 20) xor (engine.id.hashCode().toLong())
        return "tts-${engine.id}-${h}-${text.length}.ogg"
    }

    /** Aynı mesajın sesi zaten iniyor/çalıyorsa dokunuş onu DURDURUR. */
    fun togglesOff(state: State, key: String): Boolean =
        state.key == key && (state.phase == Phase.Downloading || state.phase == Phase.Playing)

    /** Uçuşta (in-flight) indirme/durdurma geçişleri — testte determinist. */
    fun start(key: String): State = State(Phase.Downloading, key = key)

    fun started(key: String, cached: Boolean): State = State(Phase.Playing, key = key, cached = cached)

    fun waiting(state: State, elapsedMs: Long): State =
        if (state.phase == Phase.Downloading) state.copy(waitingMs = elapsedMs)
        else state.copy(waitingMs = elapsedMs)

    fun done(): State = State(Phase.Idle)

    fun failed(message: String): State = State(Phase.Idle, message = message)

    /** Ayarlar satırında gösterilen motor adı. */
    fun engineLabel(engine: Engine, t: (String, String) -> String): String =
        engineOptions(t).firstOrNull { it.first == engine.id }?.second ?: engine.id
}
