package com.hermes.mobile.car

import com.hermes.mobile.data.SessionMessage

/**
 * Araç ekranındaki mesaj akışı — Android'den bağımsız, test edilebilir kurallar.
 *
 * Sürüşte okunabilirlik için: tek satır, kısa özet, en yeni mesaj en üstte
 * değil EN ALTTA (sohbet yönü korunur); araç host'u listeyi yukarıdan aşağı
 * çizer, bu yüzden son [limit] mesaj alınır ve sıra bozulmaz.
 */
object CarChatLogic {

    /** Listede gösterilecek en fazla mesaj. */
    const val MAX_ROWS = 30

    /** Özet satırının en fazla karakteri (araç host'u zaten kırpar). */
    const val SNIPPET_CHARS = 140

    /** Bir araç satırı: kim + tek satır özet + tam metin (ayrıntı ekranı için). */
    data class Row(
        val sender: String,
        val snippet: String,
        val fullText: String,
        val role: String,
    ) {
        val isUser: Boolean get() = role == "user"
    }

    /**
     * Sohbet dökümünden araç listesi üretir.
     *
     * Atlananlar: sistem/tool kayıtları, boş içerik, yalnız araç çağrısı olan
     * asistan turları. Bunlar araçta okunacak mesaj değildir; listeyi
     * doldurup gerçek konuşmayı ekrandan iterdi.
     */
    fun rows(messages: List<SessionMessage>, limit: Int = MAX_ROWS): List<Row> {
        if (limit <= 0) return emptyList()
        val visible = messages.mapNotNull { m ->
            val text = m.content?.trim().orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            val role = when {
                m.isUser -> "user"
                m.isAssistant -> "assistant"
                else -> return@mapNotNull null
            }
            Row(
                sender = if (role == "user") "Sen" else "Hermes",
                snippet = snippet(text),
                fullText = text,
                role = role,
            )
        }
        return visible.takeLast(limit)
    }

    /** Çok satırlı metni araç satırına sığan tek satıra indirir. */
    fun snippet(text: String): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        if (flat.length <= SNIPPET_CHARS) return flat
        val cut = flat.take(SNIPPET_CHARS)
        // Kelime ortasında kesmemek için son boşluğa çekilir.
        val lastSpace = cut.lastIndexOf(' ')
        val body = if (lastSpace > SNIPPET_CHARS / 2) cut.take(lastSpace) else cut
        return body.trimEnd() + "…"
    }

    /** Liste boşken gösterilecek dürüst mesaj (uydurma içerik yok). */
    fun emptyText(loading: Boolean, error: String?): String = when {
        loading -> "Mesajlar yükleniyor…"
        error != null -> error
        else -> "Bu oturumda okunacak mesaj yok"
    }

    /** Liste başlığı: oturum başlığı boş/kimlikle aynıysa "Sohbet". */
    fun title(sessionTitle: String?, sessionId: String): String {
        val t = sessionTitle?.trim().orEmpty()
        return if (t.isEmpty() || t == sessionId) "Sohbet" else t.take(60)
    }
}
