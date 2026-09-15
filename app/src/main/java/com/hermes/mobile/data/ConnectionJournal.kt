package com.hermes.mobile.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bağlantı olayı defteri — "son kopma nedenleri" tek yerde.
 *
 * Neden gerekiyor: v2 saha logunda üç kanal (`ws`, `bridge`, `relay`) kopup
 * yeniden bağlanıyor ama **hangi sıklıkta, neden ve birlikte mi** olduğu
 * satırlar arasından okunamıyordu. Kullanıcı "bağlantı kopuyor" diyor; tanı
 * kaydında ise dağınık `closed code=…` satırları var. Bu defter her kopmayı
 * sınıflandırıp son N olayın **tek satırlık özetini** tanı kaydına yazıyor —
 * hem uygulama içi [DiagLog.dump] hem canlı tanı ekranı bunu gösterir.
 *
 * Sırlar [DiagLog.redact] ile ayıklanır (neden metni URL/token içerebilir).
 */
object ConnectionJournal {

    data class Event(
        val at: Long,
        val channel: String,
        val kind: String,
        val detail: String,
    ) {
        val clock: String get() = STAMP.format(Date(at))
        fun line(): String = "$clock [$channel] $kind${if (detail.isBlank()) "" else " · $detail"}"
    }

    /** Sınıflandırılmış kopma türleri (kısa, ASCII — log aramasında kolay). */
    const val KIND_PONG = "pong-timeout"
    const val KIND_AUTH = "auth-reject"
    const val KIND_TAKEOVER = "taken-over"
    const val KIND_CLIENT = "client-closed"
    const val KIND_NETWORK = "network-lost"
    const val KIND_SERVER = "server-closed"
    const val KIND_UNKNOWN = "unknown"

    private const val MAX = 40
    private val STAMP = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val lock = Any()
    private val ring = ArrayDeque<Event>()

    /** Neden metnini kısa bir türe indirger. */
    fun classify(reason: String?): String {
        val r = reason?.lowercase(Locale.US).orEmpty()
        return when {
            r.isBlank() -> KIND_UNKNOWN
            "pong" in r || "didn't receive pong" in r || "sent ping" in r -> KIND_PONG
            "taken_over" in r || "devral" in r || "4001" in r -> KIND_TAKEOVER
            "token reddedildi" in r || "http 401" in r || "http 403" in r || "unauthor" in r -> KIND_AUTH
            "client closed" in r || "service destroyed" in r || "closedbyuser" in r -> KIND_CLIENT
            "code=1011" in r || "code=1008" in r -> KIND_SERVER
            "code=1006" in r || "unexpected end of stream" in r ||
                "failed to connect" in r || "timeout" in r -> KIND_NETWORK
            else -> KIND_UNKNOWN
        }
    }

    fun record(channel: String, reason: String?, detail: String = "", at: Long = System.currentTimeMillis()): Event {
        val safe = DiagLog.redact((detail.ifBlank { reason }.orEmpty()).replace('\n', ' ').take(160))
        val event = Event(at, channel, classify(reason ?: detail), safe)
        synchronized(lock) {
            ring.addLast(event)
            while (ring.size > MAX) ring.removeFirst()
        }
        return event
    }

    fun recent(n: Int = 5, channel: String? = null): List<Event> = synchronized(lock) {
        ring.filter { channel == null || it.channel == channel }.takeLast(n)
    }

    /**
     * Tanı kaydına yazılan tek satırlık özet.
     * Örnek: `kopma ozeti · son 5 dk 3 · ws=pong-timeout(14:09:32) ← ws=pong-timeout(14:05:10)`
     */
    fun summary(channel: String? = null, n: Int = 3, now: Long = System.currentTimeMillis()): String {
        val all = recent(MAX, channel)
        if (all.isEmpty()) return "kopma ozeti · kayit yok"
        val last5min = all.count { now - it.at <= 300_000L }
        val tail = all.takeLast(n).joinToString(" ← ") { "${it.channel}=${it.kind}(${clock.format(Date(it.at))})" }
        return "kopma ozeti · son 5 dk $last5min · $tail"
    }

    /** [DiagLog.dump] içine gömülen bölüm — paylaşılan kayıtta tam bağlam. */
    fun dumpSection(n: Int = 20): String {
        val events = recent(n)
        if (events.isEmpty()) return ""
        return buildString {
            append("## connection journal (last ").append(events.size).append(")\n")
            events.forEach { append(it.line()).append('\n') }
        }
    }

    fun clear() {
        synchronized(lock) { ring.clear() }
    }
}
