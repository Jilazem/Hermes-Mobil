package com.hermes.mobile.assistant

import com.hermes.mobile.data.VoiceSpeakLogic

/**
 * Jarvis sesli asistanının SAF karar mantığı (Android'siz, birim testli).
 *
 * Asistan akışı: dinle → (durdurma sözü? / telefon komutu? / ekran sorusu?)
 * → Hermes'e sor → yanıtı AKARKEN cümle cümle seslendir → tekrar dinle.
 * Buradaki her karar, yanlış olduğunda kullanıcının sesle fark edeceği bir
 * şey (erken susmak, yarım cümle okumak, ekranı gereksiz göndermek); bu
 * yüzden hepsi testli.
 */
object JarvisLogic {

    /** Türkçe katlama + küçük harf + noktalama silme (eşleştirme için). */
    fun fold(s: String): String = buildString(s.length) {
        for (c in s.lowercase()) append(
            when (c) {
                'ç' -> 'c'; 'ğ' -> 'g'; 'ı' -> 'i'; 'ö' -> 'o'; 'ş' -> 's'; 'ü' -> 'u'
                '.', ',', '!', '?', ';', ':', '"', '\'', '’' -> ' '
                else -> c
            },
        )
    }.replace("̇", "").replace(Regex("\\s+"), " ").trim()

    // ── Sohbeti bitiren sözler ──────────────────────────────────────────

    private val STOP = setOf(
        "tesekkurler", "tesekkur ederim", "tesekkurler jarvis", "sag ol", "sagol", "sag olun",
        "tamam bu kadar", "bu kadar", "kapat", "kapan", "gorusuruz", "iyi geceler", "iptal",
        "sus", "dur", "vazgec", "bosver", "bos ver", "gerek yok", "hosca kal",
        "thanks", "thank you", "stop", "cancel", "goodbye", "that's all", "thats all", "never mind",
    )

    /** "teşekkürler", "kapat", "iptal" gibi — kısa ve tek başına söylenmişse sohbet biter. */
    fun isStopPhrase(text: String): Boolean {
        val f = fold(text)
        if (f.isEmpty()) return false
        if (f in STOP) return true
        // "tamam teşekkürler", "peki sağ ol" gibi kısa ekli biçimler.
        val words = f.split(" ")
        return words.size <= 4 && STOP.any { s -> f.endsWith(" $s") || f.startsWith("$s ") } &&
            words.none { it in setOf("ama", "fakat", "sonra", "ve") }
    }

    // ── Ekran bağlamı ──────────────────────────────────────────────────

    private val SCREEN = Regex(
        """\b(ekran(da|daki|i|in)?|bu sayfa(da|yi|daki)?""" +
            """|bu (mesaj|yazi|metin|haber|makale|urun|video|resim|gorsel)\w*""" +
            """|burada(ki)?|bunu (oku|ozetle|cevir|acikla|anlat|yanitla|cevapla)|bu ne(dir)?|sence bu""" +
            """|what'?s on (my )?screen|this (page|message|article)|summari[sz]e this|read this)\b""",
    )

    /** Soru o an ekranda olanı mı soruyor? (Evetse ekran metni ajana eklenir.) */
    fun needsScreen(text: String): Boolean = SCREEN.containsMatchIn(fold(text))

    /** Ekran metnini ajana giden mesaja ekler; boşsa soruyu olduğu gibi bırakır. */
    fun withScreen(question: String, screenText: String, appLabel: String): String {
        val body = screenText.trim().take(MAX_SCREEN_CHARS)
        if (body.isEmpty()) return question
        val app = appLabel.ifBlank { "?" }
        return "[Kullanıcının telefon ekranı — uygulama: $app]\n$body\n[/ekran]\n\n$question"
    }

    const val MAX_SCREEN_CHARS = 4_000

    /** Sesle okunacak en fazla yanıt uzunluğu; fazlası "devamı sohbette". */
    const val MAX_SPOKEN_CHARS = 900

    // ── İlk mesaj yönergesi ────────────────────────────────────────────

    /**
     * Yeni asistan oturumunun ilk mesajına eklenen kısa yönerge. Yanıt SESLE
     * okunacağı için markdown/liste/kod yerine kısa konuşma dili istenir.
     */
    fun voicePrefix(address: String): String {
        val hitap = address.trim().takeIf { it.isNotEmpty() }?.let { " Kullanıcıya \"$it\" diye hitap et." }.orEmpty()
        return "[Sesli asistan kipi (Jarvis): yanıtın sesli okunacak. Kısa ve doğal konuş (genelde 1-3 cümle), " +
            "markdown, liste, tablo, emoji ve kod kullanma; gerekirse ayrıntıyı sohbet ekranına bırak.$hitap " +
            "Telefonla ilgili işler için telefon araçlarını kullan.]\n\n"
    }

    /** Aynı asistan oturumu bu süre içinde yeniden kullanılır (bağlam korunur). */
    const val SESSION_TTL_MS = 30 * 60_000L

    fun sessionReusable(savedAtMs: Long, nowMs: Long, id: String?): Boolean =
        !id.isNullOrBlank() && savedAtMs > 0 && nowMs - savedAtMs in 0..SESSION_TTL_MS

    // ── Sesli okuma ────────────────────────────────────────────────────

    private val EMOJI = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]")

    /** Markdown + simge temizliği: TTS'in "yıldız", "nokta" diye okuyacağı her şey gider. */
    fun speakable(text: String): String = VoiceSpeakLogic.plainText(text)
        .replace(Regex("https?://\\S+"), " bağlantı ")
        .replace(EMOJI, "")
        .replace(Regex("\\s*[·→—•]\\s*"), ", ")
        .replace(Regex("[#*_`|<>\\[\\]{}]"), " ")
        .replace(Regex("\\s*\\n+\\s*"), ". ")
        .replace(Regex("\\s{2,}"), " ")
        .replace(Regex("(\\.\\s*){2,}"), ". ")
        .trim()

    /**
     * Akan yanıtı cümlelere böler: tam cümle hazır olur olmaz seslendirilir,
     * böylece uzun yanıtın bitmesi beklenmez. Çok kısa parçalar bir sonrakine
     * eklenir (her kelimede durup kalkan kesik ses olmasın).
     */
    class SentenceSplitter(private val minChars: Int = 24) {
        private val buf = StringBuilder()
        private val carry = StringBuilder()

        fun push(chunk: String): List<String> {
            buf.append(chunk)
            val out = mutableListOf<String>()
            while (true) {
                val cut = boundary(buf) ?: break
                val sentence = buf.substring(0, cut).trim()
                buf.delete(0, cut)
                if (sentence.isEmpty()) continue
                if (carry.isNotEmpty()) carry.append(' ')
                carry.append(sentence)
                if (carry.length >= minChars) {
                    out += carry.toString()
                    carry.setLength(0)
                }
            }
            return out
        }

        /** Akış bitti: kalan her şey. */
        fun flush(): String? {
            val rest = (carry.toString() + " " + buf.toString()).trim()
            carry.setLength(0)
            buf.setLength(0)
            return rest.takeIf { it.isNotEmpty() }
        }

        /** Cümle sonu: .!?… (ardından boşluk) ya da satır sonu. "3.5", "vb." gibi yerlerde bölmez. */
        private fun boundary(s: CharSequence): Int? {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\n') return i + 1
                if (c == '.' || c == '!' || c == '?' || c == '…' || c == ':' || c == ';') {
                    if (i + 1 >= s.length) return null // devamı gelmeden karar verme
                    val next = s[i + 1]
                    if (next == ' ' || next == '\n') {
                        if (c == '.' && abbreviationBefore(s, i)) { i++; continue }
                        return i + 1
                    }
                }
                i++
            }
            return null
        }

        private fun abbreviationBefore(s: CharSequence, dot: Int): Boolean {
            var j = dot - 1
            while (j >= 0 && s[j].isLetter()) j--
            val word = s.subSequence(j + 1, dot).toString().lowercase()
            return word in setOf("vb", "vs", "dr", "prof", "sn", "no", "bkz", "yy", "mr", "mrs", "st") ||
                (word.length == 1 && word[0].isLetter())
        }
    }

    // ── Sürekli sohbet ─────────────────────────────────────────────────

    /**
     * Yanıttan sonra yeniden dinlensin mi? Sürekli kip açıkken evet; üst üste
     * [MAX_SILENT_FOLLOWUPS] kez hiç ses gelmezse durur (mikrofon sonsuza dek
     * açık kalmasın).
     */
    fun shouldListenAgain(continuous: Boolean, silentFollowups: Int): Boolean =
        continuous && silentFollowups < MAX_SILENT_FOLLOWUPS

    const val MAX_SILENT_FOLLOWUPS = 1

    /** Telefon eylemi uygulama açtı mı (öyleyse panel çekilir, açılan uygulama görünsün). */
    fun opensApp(tool: String): Boolean = tool in setOf(
        "phone_open_app", "phone_navigate", "phone_dial", "phone_sms_draft", "phone_open_url",
        "phone_settings", "phone_web_search", "phone_add_event", "phone_set_alarm",
    )
}
