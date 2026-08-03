package com.hermes.mobile.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Yazılan mesajın bir telefon eylemi olup olmadığını **cihazda** çözer.
 *
 * Neden sunucuya sormuyoruz: Hermes ajanı ayrı bir makinede koşuyor ve telefona
 * hiçbir şekilde ulaşamıyor. Uygulama açmak, numara çevirmek, yol tarifi
 * başlatmak ancak telefonun kendisinin yapabileceği işler.
 *
 * Tasarım kararı: **model değil, kural.** Her mesajı bir modele sınıflandırtmak
 * her mesaja saniyeler eklerdi ve çevrimdışı çalışmazdı. Kalıplar dar, buyruk
 * kipine bağlı ve cümlenin başına demirli. Eşleşme yoksa mesaj hiç dokunulmadan
 * ajana gider — kararsız kalınca `null` dönmek, yanlış eylem yapmaktan iyidir.
 *
 * **İki dilli.** Türkçe ve İngilizce kalıplar birlikte deneniyor; kullanıcının
 * hangi dilde yazdığını önceden bilmiyoruz.
 *
 * **Türkçe karakter katlaması:** telefonda çoğu kişi "aç" yerine "ac" yazıyor.
 * Eşleştirme ASCII'ye katlanmış metin üzerinde yapılıyor, argümanlar ise
 * **özgün metinden** kesiliyor — böylece "Kadıköy" hedef olarak bozulmadan
 * geçiyor. Katlama birebir (harf sayısı değişmiyor), konumlar örtüşüyor.
 */
object PhoneIntent {

    data class Action(val tool: String, val args: Map<String, String>) {
        fun toJson(): JsonObject = buildJsonObject {
            args.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
        }
    }

    /** Boş sohbet ekranında gösterilen örnekler. Dörtten fazlası sığmıyor. */
    val EXAMPLES = listOf(
        "WhatsApp aç",
        "Kadıköy'e yol tarifi",
        "pil ne kadar",
        "feneri aç",
    )

    fun parse(raw: String): Action? {
        val text = raw.trim().trimEnd('.', '!', '?').trim()
        // Uzun metin bir istek değil, anlatıdır; ajana gitsin.
        if (text.isEmpty() || text.length > 100) return null

        val folded = fold(text)

        // Okuma niyetleri QUESTION korumasindan ONCE deneniyor: "bugun ne
        // kacirdim?" ve "neredeyim?" biciminde soru ama cevabi telefonda,
        // ajanda degil. Sunucudaki ajan bu bilgilere zaten ulasamiyor.
        read(folded)?.let { return it }

        if (QUESTION.containsMatchIn(folded)) return null

        status(folded)?.let { return it }
        flashlight(folded)?.let { return it }
        volume(folded)?.let { return it }
        media(folded)?.let { return it }
        timer(folded)?.let { return it }
        alarm(folded)?.let { return it }
        addEvent(text, folded)?.let { return it }
        navigate(text, folded)?.let { return it }
        settings(folded)?.let { return it }
        webSearch(text, folded)?.let { return it }
        sms(text, folded)?.let { return it }
        dial(text, folded)?.let { return it }
        tasker(text, folded)?.let { return it }
        // En geniş kalıp en sonda: "ayarları aç" yanlışlıkla uygulama araması
        // olarak yakalanmasın.
        return openApp(text, folded)
    }

    /** Türkçe harfleri ASCII karşılığına indirger; uzunluk korunur. */
    private fun fold(s: String): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                'ç', 'Ç' -> 'c'
                'ğ', 'Ğ' -> 'g'
                'ı', 'I' -> 'i'
                'İ', 'i' -> 'i'
                'ö', 'Ö' -> 'o'
                'ş', 'Ş' -> 's'
                'ü', 'Ü' -> 'u'
                else -> c.lowercaseChar()
            }
        )
    }

    private fun action(tool: String, vararg pairs: Pair<String, String>) =
        Action(tool, pairs.toMap())

    /** Katlanmış metindeki eşleşmeyi özgün metinden keser. */
    private fun slice(original: String, m: MatchResult, group: Int): String =
        m.groups[group]?.range?.let { original.substring(it.first, it.last + 1) }.orEmpty().trim()

    /**
     * Soru ya da nezaket cümlesi eylem değildir.
     *
     * "wifi ayarlarını nasıl açarım" bir bilgi isteği; ajana gitmeli.
     */
    private val QUESTION = Regex(
        """\b(nasil|nedir|ne demek|neden|misin|misiniz|musun|musunuz""" +
            """|abilir mi|ebilir mi""" +
            """|how do|how can|how would|what is|what's|why|explain|should i)\b"""
    )

    // ── Cihaz durumu ─────────────────────────────────────────────────
    private val STATUS = Regex(
        """\b(pil|batarya|sarj|depolama|bos alan)\b.*\b(ne kadar|yuzde|durum|kac|kaldi)\b""" +
            """|^(pil|batarya|sarj)\b|\btelefon(un)? durumu\b""" +
            // İngilizce
            """|^(battery|storage)\b|\b(battery|storage) (level|left|status)\b""" +
            """|\bphone status\b|\bhow much (battery|storage)\b"""
    )
    private val STATUS_NET = Regex(
        """\b(hangi (ag|wifi)|internet var mi|wifi (var mi|bagli mi))\b""" +
            """|\b(which (network|wifi)|am i online|network status)\b"""
    )

    // ── Okuma niyetleri ──────────────────────────────────────────────
    //
    // Bunlar cihazda cozulmek ZORUNDA: ajan baska bir makinede, telefonun
    // bildirimlerini/rehberini/konumunu goremiyor. Once "kacirdim/neredeyim"
    // gibi kaliplar, sonra genel sozcukler.

    private val NOTIFS = Regex(
        """\b(ne kacirdim|neler kacirdim|bildirim(ler|lerim|leri)?( var mi| neler)?)\b""" +
            """|\byeni (mesaj|bildirim)( var mi)?\b""" +
            """|\bwhat did i miss\b|\bany (new )?(notifications?|messages?)\b""" +
            """|^notifications?$"""
    )

    private val CALENDAR_READ = Regex(
        """\b(bugun|yarin|bu hafta)\s+(ne var|programim|ajandam|neler var)\b""" +
            """|\b(takvim(im|de|imde)?|ajandam|programim)\b\s*(ne|nasil|var mi|neler)?\b""" +
            """|\b(randevu(m|lar|larim)?)\b""" +
            """|\bwhat(?:'s| is) (on )?(my )?(schedule|calendar|agenda)\b""" +
            """|\b(my )?(schedule|agenda) (for )?(today|tomorrow)\b""" +
            """|^calendar$|^agenda$"""
    )

    private val LOCATION = Regex(
        """\b(neredeyim|konumum( ne| nedir| neresi)?|hangi (semtte|ilcede|sehirde)yim)\b""" +
            """|\bwhere am i\b|\b(my|current) location\b"""
    )

    private val CLIP_READ = Regex(
        """\b(pano(da|mda)? ne var|panoyu oku|kopyaladigim ne)\b""" +
            """|\bwhat(?:'s| is) (in|on) (the |my )?clipboard\b|^clipboard$"""
    )

    // "Ahmet'in numarasi" / "number for Ahmet" -- ismi yakalayip rehbere sor.
    //
    // Iyelik eki tek karakter degil: "-in", "-nin", "-un". Once `[iın]` yazip
    // tek harf yutunca isim "ahmet'i" olarak cikiyordu ve rehberde
    // bulunamiyordu. Kesme isareti istege bagli ("ahmetin numarasi" da olur).
    private val CONTACT_TR = Regex(
        """^(.{2,40}?)(?:['’]?n?[iu]n)?\s+(?:numarasi|telefonu|numarasini)\b"""
    )
    private val CONTACT_EN = Regex(
        """\b(?:number|phone) (?:for|of)\s+(.{2,40})$|^find contact\s+(.{2,40})$"""
    )

    private fun read(folded: String): Action? {
        if (NOTIFS.containsMatchIn(folded)) return action("phone_notifications")
        if (LOCATION.containsMatchIn(folded)) return action("phone_location")
        if (CLIP_READ.containsMatchIn(folded)) return action("phone_clipboard_read")
        // Takvim: "etkinlik ekle" bunun isi degil, o phone_add_event.
        if (!EVENT_ADD.containsMatchIn(folded) && CALENDAR_READ.containsMatchIn(folded)) {
            val saat = if ("yarin" in folded || "tomorrow" in folded) "48" else "24"
            return action("phone_calendar", "hours" to saat)
        }
        CONTACT_TR.find(folded)?.let { m ->
            val ad = m.groupValues[1].trim()
            if (ad.isNotBlank()) return action("phone_contacts", "name" to ad)
        }
        CONTACT_EN.find(folded)?.let { m ->
            val ad = (m.groupValues[1].ifBlank { m.groupValues[2] }).trim()
            if (ad.isNotBlank()) return action("phone_contacts", "name" to ad)
        }
        return null
    }

    /**
     * Takvime etkinlik ekler.
     *
     * Baslik ve zaman ayiklanamazsa da arac cagriliyor: takvim uygulamasi
     * bos bir taslakla aciliyor ve kullanici doldurmaya devam ediyor. Bu,
     * "anlamadim" demekten iyi -- kullanici zaten takvime gitmek istiyordu.
     */
    private val EVENT_TR = Regex(
        """^(?:takvime|ajandaya)\s+(.+?)\s*(?:ekle|kaydet|olustur)$""" +
            """|^(.+?)\s+(?:icin)?\s*randevu (?:ekle|olustur|kaydet)$"""
    )
    private val EVENT_EN = Regex(
        """^(?:add|create|schedule)\s+(?:an?\s+)?(?:event|meeting|appointment)\s*""" +
            """(?:called|named|for|:)?\s*(.*)$"""
    )

    private fun addEvent(original: String, folded: String): Action? {
        EVENT_TR.find(folded)?.let { m ->
            val baslik = slice(original, m, 1).ifBlank { slice(original, m, 2) }
            return action("phone_add_event", "title" to baslik)
        }
        EVENT_EN.find(folded)?.let { m ->
            return action("phone_add_event", "title" to slice(original, m, 1))
        }
        return null
    }

    /** "takvime ekle", "randevu olustur" -- okuma degil, yazma niyeti. */
    private val EVENT_ADD = Regex(
        """\b(ekle|olustur|kaydet|kur)\b|\b(add|create|schedule) (an? )?(event|meeting|appointment)\b"""
    )

    private fun status(folded: String): Action? = when {
        STATUS_NET.containsMatchIn(folded) -> action("phone_status", "what" to "ag")
        STATUS.containsMatchIn(folded) -> action("phone_status", "what" to "hepsi")
        else -> null
    }

    // ── El feneri ────────────────────────────────────────────────────
    // Sözcük sonuna `\b` konamaz: "feneri" derken ek geliyor, sınır oluşmuyor.
    // Eki `\S*` yutuyor.
    private val TORCH_TR = Regex(
        """\b(fener|el feneri|flas|isik|lamba)\S*\s*(ac|yak|kapat|sondur)\b"""
    )
    private val TORCH_TR_ALT = Regex("""^(ac|yak|kapat|sondur)\s+(fener|el feneri|isik|lamba)""")
    private val TORCH_EN = Regex(
        """\b(?:turn |switch )?(on|off)\b[^.]{0,14}\b(?:flashlight|torch)\b""" +
            """|\b(?:flashlight|torch)\b[^.]{0,10}\b(on|off)\b"""
    )
    private val TR_ON = setOf("ac", "yak")

    private fun flashlight(folded: String): Action? {
        TORCH_TR.find(folded)?.let { m ->
            return torch(m.groupValues[2] in TR_ON)
        }
        TORCH_TR_ALT.find(folded)?.let { m ->
            return torch(m.groupValues[1] in TR_ON)
        }
        TORCH_EN.find(folded)?.let { m ->
            val word = m.groupValues.drop(1).firstOrNull { it == "on" || it == "off" } ?: return null
            return torch(word == "on")
        }
        return null
    }

    private fun torch(on: Boolean) =
        action("phone_flashlight", "state" to if (on) "on" else "off")

    // ── Ses ──────────────────────────────────────────────────────────
    private val VOLUME_TR = Regex(
        """\bses(i|ini)?\b\s*(ac|yukselt|arttir|artir|kis|azalt|dusur|sustur|kapat)"""
    )
    private val VOLUME_TR_MUTE = Regex("""^(sustur|sesi kes|sessize al)$""")
    private val VOLUME_EN = Regex(
        """\bvolume\b[^.]{0,8}\b(up|down|max|mute)\b""" +
            """|\b(turn up|turn down|mute|unmute|max)\b[^.]{0,12}\bvolume\b""" +
            """|^(mute|unmute)$"""
    )

    private fun volume(folded: String): Action? {
        if (VOLUME_TR_MUTE.containsMatchIn(folded)) {
            return action("phone_volume", "action" to "mute")
        }
        VOLUME_EN.find(folded)?.let { m ->
            val w = m.value
            val act = when {
                "unmute" in w -> "unmute"
                "mute" in w -> "mute"
                "max" in w -> "max"
                "up" in w -> "up"
                "down" in w -> "down"
                else -> return@let
            }
            return action("phone_volume", "action" to act)
        }
        VOLUME_TR.find(folded)?.let { m ->
            val act = when (m.groupValues[2]) {
                "ac", "yukselt", "arttir", "artir" -> "up"
                "kis", "azalt", "dusur" -> "down"
                "sustur", "kapat" -> "mute"
                else -> return@let
            }
            return action("phone_volume", "action" to act)
        }
        return null
    }

    // ── Medya ────────────────────────────────────────────────────────
    private val MEDIA_TR = Regex(
        """\b(muzigi|muzik|sarkiyi|sarki|calani|medyayi)\b\s*""" +
            """(duraklat|durdur|oynat|baslat|devam et|gec|atla)""" +
            """|^(sonraki|onceki) (sarki|parca)"""
    )
    private val MEDIA_EN = Regex(
        """\b(pause|resume|play|skip|next|previous|prev)\b[^.]{0,14}\b(music|song|track|playback|media)\b""" +
            """|\b(music|song|track|playback)\b[^.]{0,10}\b(pause|resume|play|skip|next|previous)\b""" +
            """|^(next|previous|prev) (song|track)$"""
    )

    private fun media(folded: String): Action? {
        MEDIA_EN.find(folded)?.let { m ->
            val w = m.value
            val act = when {
                "pause" in w -> "pause"
                "resume" in w || "play" in w -> "play"
                "next" in w || "skip" in w -> "next"
                "prev" in w -> "prev"
                else -> return@let
            }
            return action("phone_media", "action" to act)
        }
        MEDIA_TR.find(folded)?.let { m ->
            val verb = m.groupValues[2].ifBlank { m.groupValues[3] }
            val act = when (verb) {
                "duraklat", "durdur" -> "pause"
                "oynat", "baslat", "devam et" -> "play"
                "gec", "atla", "sonraki" -> "next"
                "onceki" -> "prev"
                else -> return@let
            }
            return action("phone_media", "action" to act)
        }
        return null
    }

    // ── Sayaç ────────────────────────────────────────────────────────
    private val UNIT = """(?:dakika|dakikalik|dk|minutes|minute|min)"""
    private val TIMER = Regex(
        """(\d{1,3})\s*$UNIT\s*(?:sayac|zamanlayici|timer|kronometre)"""
    )
    private val TIMER_ALT = Regex(
        """(?:sayac|zamanlayici|timer)\s*(?:kur|ayarla|baslat|set|start|for)?\s*(\d{1,3})\s*$UNIT"""
    )

    private fun timer(folded: String): Action? {
        val mins = TIMER.find(folded)?.groupValues?.get(1)
            ?: TIMER_ALT.find(folded)?.groupValues?.get(1)
            ?: return null
        return action("phone_timer", "minutes" to mins)
    }

    // ── Alarm ────────────────────────────────────────────────────────
    private val ALARM = Regex("""(?:^|\s)(\d{1,2}[:.]\d{2})\s*(?:['’]?[ae])?\s*(?:icin\s+)?alarm""")
    private val ALARM_ALT = Regex(
        """^(?:alarm|set an alarm|set alarm|wake me)\s*(?:kur|ayarla|for|at|up at)?\s*""" +
            """(?:at\s*)?(\d{1,2}[:.]\d{2})"""
    )

    private fun alarm(folded: String): Action? {
        val time = ALARM.find(folded)?.groupValues?.get(1)
            ?: ALARM_ALT.find(folded)?.groupValues?.get(1)
            ?: return null
        return action("phone_set_alarm", "time" to time.replace('.', ':'), "label" to "")
    }

    // ── Yol tarifi ───────────────────────────────────────────────────
    private val NAV_SUFFIX = Regex("""^(.+?)\s*(?:yol\s*tarifi|nasil\s*giderim|navigasyon)$""")
    private val NAV_PREFIX = Regex(
        """^(?:yol\s*tarifi|navigasyon|directions? to|navigate to|drive to|take me to)\s+(.+)$"""
    )

    private fun navigate(text: String, folded: String): Action? {
        val m = NAV_SUFFIX.find(folded) ?: NAV_PREFIX.find(folded) ?: return null
        val dest = stripSuffix(slice(text, m, 1))
        return if (dest.isBlank()) null else action("phone_navigate", "destination" to dest)
    }

    // ── Ayarlar ──────────────────────────────────────────────────────
    private val SECTION = """(wifi|wi-fi|kablosuz|bluetooth|ses|sound|ekran|display|pil|battery|konum|location|uygulama|app)"""
    private val SETTINGS_TR = Regex("""\b$SECTION\s*ayar(?:lar)?i?(?:ni)?\s*(?:ac|goster|getir)""")
    private val SETTINGS_EN = Regex("""\b$SECTION\s*settings\b""")

    private fun settings(folded: String): Action? {
        val m = SETTINGS_TR.find(folded) ?: SETTINGS_EN.find(folded) ?: return null
        return action("phone_settings", "section" to m.groupValues[1])
    }

    // ── Web araması ──────────────────────────────────────────────────
    private val WEB_TR = Regex(
        """^(?:google(?:['’]?d[ae])?|internette|webde|nette)\s+(.+?)\s*(?:ara|arat|aratir|arastir)$"""
    )
    private val WEB_EN = Regex("""^(?:google|search (?:for|the web for)|look up)\s+(.+)$""")

    private fun webSearch(text: String, folded: String): Action? {
        val m = WEB_TR.find(folded) ?: WEB_EN.find(folded) ?: return null
        val q = slice(text, m, 1)
        return if (q.isBlank()) null else action("phone_web_search", "query" to q)
    }

    // ── SMS ──────────────────────────────────────────────────────────
    private val SMS = Regex(
        """^(?:sms|mesaj|text|message)\s*(?:gonder|yaz|at|to)?\s*""" +
            """(\+?[0-9\s()-]{7,})?\s*[:,-]?\s*(.*)$"""
    )

    private fun sms(text: String, folded: String): Action? {
        val m = SMS.find(folded) ?: return null
        return action(
            "phone_sms_draft",
            "number" to slice(text, m, 1),
            "text" to slice(text, m, 2),
        )
    }

    // ── Arama ────────────────────────────────────────────────────────
    // Yalnız numara: kişi adından arama, rehber izni istemeden yapılamaz.
    private val DIAL = Regex(
        """^(?:call\s+)?(\+?[0-9][0-9\s()-]{6,})\s*(?:numarasini\s*)?""" +
            """(?:ara|cevir|telefon\s*et)?$"""
    )

    private fun dial(text: String, folded: String): Action? {
        val m = DIAL.find(folded) ?: return null
        // Sadece rakam yazmak arama isteği değil; bir fiil ya da "call" şart.
        val hasVerb = Regex("""\b(ara|cevir|telefon et|call)\b""").containsMatchIn(folded)
        if (!hasVerb) return null
        val number = slice(text, m, 1).replace(Regex("""[\s()-]"""), "")
        return if (number.isBlank()) null else action("phone_dial", "number" to number)
    }

    // ── Tasker / MacroDroid ──────────────────────────────────────────
    private val TASKER = Regex(
        """^(?:tasker|makro|macro)\s*(?:gorevi|task)?\s*(?:calistir|tetikle|run|trigger)?\s*(.+)$"""
    )

    private fun tasker(text: String, folded: String): Action? {
        val m = TASKER.find(folded) ?: return null
        val task = slice(text, m, 1)
        return if (task.isBlank()) null
        else action("phone_tasker_task", "task" to task, "parameter" to "")
    }

    // ── Uygulama açma ────────────────────────────────────────────────
    private val OPEN_SUFFIX = Regex("""^(.+?)\s*(?:uygulamasini\s*)?(?:ac|baslat|calistir)$""")

    /**
     * İngilizce "open X".
     *
     * Belirteçli hedefi dışlıyoruz: "open WhatsApp" bir uygulama, "open the
     * file report.pdf" ajana ait bir istek. Bu ayrım olmadan İngilizce kalıp,
     * ajana gitmesi gereken mesajları kaçırırdı.
     */
    private val OPEN_PREFIX = Regex(
        """^(?:ac|baslat|open|launch|start)\s+(?!the |a |an |my |that |this |up )(.+)$"""
    )

    private fun openApp(text: String, folded: String): Action? {
        val m = OPEN_SUFFIX.find(folded) ?: OPEN_PREFIX.find(folded) ?: return null
        val name = stripSuffix(slice(text, m, 1))
        // Uygulama adı kısa olur; uzunsa büyük ihtimalle bir cümledir.
        return if (name.isBlank() || name.length > 30) null
        else action("phone_open_app", "app" to name)
    }

    /**
     * Türkçe ek ayıklama: "WhatsApp'ı" → "WhatsApp", "Kadıköy'e" → "Kadıköy".
     *
     * Yalnız kesme işaretinden sonrası atılır; kesme yoksa dokunulmaz —
     * "Ayarlar" gibi adların son harfini yememek için.
     */
    private fun stripSuffix(raw: String): String {
        val t = raw.trim().trim('"')
        val apos = t.indexOfLast { it == '\'' || it == '’' }
        return if (apos > 0 && t.length - apos <= 4) t.substring(0, apos).trim() else t
    }
}
