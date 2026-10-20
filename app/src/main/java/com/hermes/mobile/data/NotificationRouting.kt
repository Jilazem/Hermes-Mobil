package com.hermes.mobile.data

/**
 * Bildirim yönlendirme — V5-2 madde 6 (sistem/cron bildirimleri sohbete karışmaz).
 *
 * "Bildirimler" sekmesi cron ve sistem bildirimlerine özelleşirken yanıt ve
 * takip bildirimleri de aynı kutuda tutulur; tür ayrımı filtre ve sessize
 * alma kararlarını tek yerden besler. Saf fonksiyonlar JVM testi görür.
 */
object NoticeKind {
    const val REPLY = "reply"
    const val FOLLOW = "follow"
    const val AGENT = "agent"
    const val CRON = "cron"
    const val SYSTEM = "system"

    /** Filtre/sessiz menüsünde gösterilen türler (sıra menü sırasıdır). */
    val ALL = listOf(CRON, SYSTEM, REPLY, FOLLOW, AGENT)
}

private val CRON_MARKERS = listOf(
    "[cron",
    "scheduled cron job",
    "scheduled job",
    "zamanlanmış görev",
    "cron_",
    "cron job",
)

private val SYSTEM_MARKERS = listOf(
    "sistem bildirimi",
    "system notice",
    "gateway",
    "housekeeping",
)

/**
 * Bir bildirimin türünü kaynağından ve içeriğinden çıkarır.
 *
 * [source] canlı oturumun kaynağı ("cron", "telegram", "cli"…); yoksa
 * metin işaretlerinden (SessionText ile aynı aile) sınıflandırılır.
 * Bilinmeyen her şey yanıt sayılır — sessizce "sistem"e kaydırmak gerçek
 * cevapları gizlerdi.
 */
fun classifyNoticeKind(source: String?, title: String = "", text: String = ""): String {
    val src = source.orEmpty().lowercase()
    val hay = (title + " " + text).lowercase()
    return when {
        src.contains("cron") || CRON_MARKERS.any { hay.contains(it) } -> NoticeKind.CRON
        src.contains("system") || src.contains("gateway") ||
            SYSTEM_MARKERS.any { hay.contains(it) } -> NoticeKind.SYSTEM
        src.contains("agent") -> NoticeKind.AGENT
        src.contains("follow") -> NoticeKind.FOLLOW
        else -> NoticeKind.REPLY
    }
}

/**
 * Olay açık oturumun dışından mı? (cron/Telegram/CLI oturumları)
 *
 * Aktif konuşma dışında bir oturumun `message.*` olayları sohbet akışına
 * girmez; [ChatViewModel] bunları bildirim kutusuna yönlendirir.
 */
fun isForeignSession(
    eventSessionId: String?,
    openSessionId: String?,
    storedSessionId: String?,
): Boolean {
    val sid = eventSessionId?.takeIf { it.isNotBlank() } ?: return false
    return sid != openSessionId && sid != storedSessionId
}

/**
 * Aynı olayın (yeniden bağlanma, çift `complete`) tek bildirimde kalması
 * için deterministik kimlik: oturum + metin özeti.
 */
fun foreignNoticeId(sessionId: String?, text: String): String {
    val body = text.trim()
    val digest = body.hashCode().toUInt().toString(16)
    return "evt-${sessionId.orEmpty()}-$digest"
}

/** Filtre penceresi: seçili türler boşsa hepsi görünür. */
fun noticeVisible(notice: ActivityNotice, kinds: Set<String>, unreadOnly: Boolean): Boolean =
    (kinds.isEmpty() || notice.kind in kinds) && (!unreadOnly || !notice.read)

/**
 * Sekme rozeti: sessize alınan türler okunmamış olsa bile sayacı şişirmez —
 * kullanıcı zaten duymak istemediğini bildirmiştir.
 */
fun unreadNoticeCount(items: List<ActivityNotice>, mutedKinds: Set<String>): Int =
    items.count { !it.read && it.kind !in mutedKinds }

fun isNoticeMuted(kind: String, mutedKinds: Set<String>): Boolean = kind in mutedKinds