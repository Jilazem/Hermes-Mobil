package com.hermes.mobile.data

import com.hermes.mobile.ui.tr

/**
 * [MessageInbox]'ın SAF karar mantığı: hangi sohbet, hangi uygulama, nasıl
 * biçimlenir. Yanlış kişiye giden mesaj geri alınamaz; o yüzden eşleşme
 * kuralları burada, testli: tam eşleşme her zaman kazanır, belirsizse
 * gönderilmez ([Pick.Ambiguous]).
 */
object MessageInboxLogic {

    data class Item(
        val key: String,
        val app: String,
        val chat: String,
        val lines: List<String>,
        val time: Long,
        val canReply: Boolean,
    )

    sealed interface Pick {
        data object None : Pick
        data class One(val item: Item) : Pick
        data class Ambiguous(val options: List<Item>) : Pick
    }

    /** Türkçe katlama + küçük harf ("Ayşe" == "ayse", "WhatsApp" == "whatsapp"). */
    fun norm(s: String): String = buildString(s.length) {
        for (c in s.trim().lowercase()) append(
            when (c) {
                'ç' -> 'c'; 'ğ' -> 'g'; 'ı' -> 'i'; 'İ', 'i' -> 'i'; 'ö' -> 'o'; 'ş' -> 's'; 'ü' -> 'u'
                else -> c
            },
        )
    }.replace("\u0307", "").replace(Regex("\\s+"), " ")

    /** "whatsapp", "wp", "wa", "telegram", "sms", "mesaj" → uygulama adı süzgeci. */
    fun appMatches(item: Item, app: String): Boolean {
        val q = norm(app)
        if (q.isEmpty()) return true
        val a = norm(item.app)
        return when (q) {
            "wp", "wa", "whatsapp", "whatsap", "watsap", "vatsap" -> a.startsWith("whatsapp")
            "sms", "mesaj", "mesajlar", "kisa mesaj" -> a == "mesajlar"
            "tg", "telegram" -> a.startsWith("telegram")
            else -> a.contains(q)
        }
    }

    fun filter(items: List<Item>, app: String, chat: String): List<Item> {
        val c = norm(chat)
        return items.filter { appMatches(it, app) && (c.isEmpty() || norm(it.chat).contains(c)) }
    }

    /**
     * Yanıt hedefi. Sıra: tam ad eşleşmesi → tek kısmi eşleşme → belirsiz.
     * Yalnız yanıt eylemi olan bildirimler aday.
     */
    fun pickReplyTarget(items: List<Item>, app: String, chat: String): Pick {
        val c = norm(chat)
        if (c.isEmpty()) return Pick.None
        val pool = items.filter { it.canReply && appMatches(it, app) }
        pool.filter { norm(it.chat) == c }.let { exact ->
            if (exact.size == 1) return Pick.One(exact.single())
            if (exact.size > 1) return Pick.Ambiguous(exact)
        }
        val partial = pool.filter { norm(it.chat).contains(c) || norm(it.chat).split(" ").any { w -> w == c } }
        return when (partial.size) {
            0 -> Pick.None
            1 -> Pick.One(partial.single())
            else -> Pick.Ambiguous(partial)
        }
    }

    /** Okunacak metin — ajan ve sesli okuma için sade. */
    fun format(items: List<Item>, app: String = ""): String {
        if (items.isEmpty()) {
            return if (app.isBlank()) tr("Okunmamış mesaj yok.", "No unread messages.")
            else tr("$app için okunmamış mesaj yok.", "No unread $app messages.")
        }
        return items.joinToString("\n\n") { i ->
            val head = "${i.app} · ${i.chat}" + if (i.canReply) "" else tr(" (yanıtlanamaz)", " (no reply)")
            head + "\n" + i.lines.joinToString("\n") { "  $it" }
        }
    }
}
