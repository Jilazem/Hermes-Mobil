package com.hermes.mobile.data

/**
 * Android Auto araç sohbeti (bildirim-mesajlaşma) durumu — SAF katman, Android'siz.
 *
 * Neden var (V5-3 kök nedeni): AA'nın resmî kuralı "kullanıcı yalnız aktif AA
 * oturumu SIRASINDA gelen bildirimlerin mesaj geçmişini görebilir". Eski akışta
 * araç konuşması tek seferlik tek satırlık ("Araç sohbeti hazır…") bildirimdi ve
 * sohbet ilerledikçe ASLA güncellenmiyordu; AA ekranında akış bu yüzden hiç
 * düşmüyordu ("yeni mesajınız yok"). Artık her mesaj aynı konuşmaya akıtılır ve
 * konuşma her mesajda yeniden bildirilir.
 *
 * Kalıcılık: süreç belleğinde (tek aktif araç konuşması). Süreç ölürse konuşma
 * sıfırlanır — AA tarafında eski bildirim görünür kalır.
 */
object CarConversation {

    /** Konuşmada tutulan en fazla mesaj (eski olanlar düşer). */
    const val MAX_MESSAGES = 25
    /** Tek mesajın en fazla uzunluğu — AA okunabilirliği için. */
    const val MAX_TEXT = 4_000

    data class Msg(val isUser: Boolean, val text: String, val time: Long)

    private val lock = Any()
    private var key: String? = null
    private var title: String = ""
    private val msgs = ArrayDeque<Msg>()

    fun keyOf(profileId: String?, sessionId: String?): String =
        "${profileId.orEmpty()}/${sessionId.orEmpty()}"

    /** Zaman damgası epoch saniye ya da milisaniye olabilir; güvenli dönüşüm. */
    fun timeOf(ts: Double?, fallback: Long): Long = when {
        ts == null -> fallback
        ts > 1e12 -> ts.toLong()
        ts > 1e9 -> (ts * 1000).toLong()
        else -> fallback
    }

    /** Konuşmayı başlatır; [history] ile sohbet akışı baştan AA'ya taşınır. */
    fun start(profileId: String?, sessionId: String?, title: String?, history: List<Msg>): List<Msg> =
        synchronized(lock) {
            key = keyOf(profileId, sessionId)
            this.title = title?.trim()?.take(60).orEmpty()
            msgs.clear()
            history.filter { it.text.isNotBlank() }.takeLast(MAX_MESSAGES).forEach { appendLocked(it) }
            msgs.toList()
        }

    /**
     * Aktif konuşmaya mesaj ekler. Konuşma farklı bir oturumdaysa null döner
     * (akış o konuşmaya düşmez — sessizce yok sayılır).
     */
    fun append(profileId: String?, sessionId: String?, msg: Msg): List<Msg>? = synchronized(lock) {
        if (key == null || key != keyOf(profileId, sessionId)) return null
        if (msg.text.isBlank()) return msgs.toList()
        appendLocked(msg)
        msgs.toList()
    }

    fun isActive(profileId: String?, sessionId: String?): Boolean = synchronized(lock) {
        key != null && key == keyOf(profileId, sessionId)
    }

    fun currentTitle(): String = synchronized(lock) { title }

    fun messages(): List<Msg> = synchronized(lock) { msgs.toList() }

    /** One atomic read: never pair another conversation's messages with an old key check. */
    fun messagesFor(profileId: String?, sessionId: String?): List<Msg>? = synchronized(lock) {
        if (key != null && key == keyOf(profileId, sessionId)) msgs.toList() else null
    }

    /** Konuşmayı kapatır (okundu/ekran kapatıldı). */
    fun close(profileId: String?, sessionId: String?) = synchronized(lock) {
        if (key == keyOf(profileId, sessionId)) {
            key = null
            title = ""
            msgs.clear()
        }
    }

    private fun appendLocked(msg: Msg) {
        msgs.addLast(msg.copy(text = msg.text.trim().take(MAX_TEXT)))
        while (msgs.size > MAX_MESSAGES) msgs.removeFirst()
    }
}
