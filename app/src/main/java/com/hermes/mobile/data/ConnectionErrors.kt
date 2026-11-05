package com.hermes.mobile.data

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * "Bağlantıyı test et" sonucu. [detail] kullanıcıya gösterilir, [technical]
 * teşhis/log içindir (HTTP 401, DNS, TIMEOUT…). Token hiçbir alanda yer almaz.
 */
data class ConnectionTest(
    val ok: Boolean,
    val httpCode: Int?,
    val latencyMs: Long,
    val detail: String,
    val technical: String,
) {
    /** Ekranda tek satır: "HTTP 200 · 43 ms · model: glm-5.3" ya da hata cümlesi. */
    fun line(): String {
        val kod = httpCode?.let { "HTTP $it" } ?: technical
        return "$kod · ${latencyMs} ms · $detail"
    }
}

/**
 * J1: bağlantı hatalarını **tek yerde** sınıflandırır.
 *
 * Regresyonun belirtisi "sunucuya bağlanamıyorum"du ama ekranda ne DNS, ne
 * zaman aşımı, ne 401 ayırt edilebiliyordu; kullanıcı token mi adres mi diye
 * tahmin ediyordu. [code] teknik kodu (log/teşhis), [describe] kullanıcıya
 * gösterilecek cümleyi üretir. Saf fonksiyonlar — birim test edilir.
 */
object ConnectionErrors {

    /** Teknik kod: DNS, CONN_REFUSED, UNREACHABLE, TIMEOUT, TLS, NETWORK, HTTP 401… */
    fun code(t: Throwable): String {
        var cur: Throwable? = t
        var guard = 0
        while (cur != null && guard++ < 8) {
            when (cur) {
                is HermesApiException -> return "HTTP ${cur.code}"
                is UnknownHostException -> return "DNS"
                is ConnectException -> return "CONN_REFUSED"
                is NoRouteToHostException -> return "UNREACHABLE"
                is SocketTimeoutException -> return "TIMEOUT"
                is SSLException -> return "TLS"
                is SocketException -> return "NETWORK"
            }
            val next = cur.cause
            cur = if (next === cur) null else next
        }
        return "IO"
    }

    /** Kullanıcıya gösterilecek cümle (arayüz dilinde). */
    fun describe(t: Throwable): String {
        if (t is HermesApiException) return describeHttp(t.code)
        val kod = code(t)
        val base = when (kod) {
            "DNS" -> com.hermes.mobile.ui.tr(
                "Sunucu adı çözülemedi (DNS) — adreste yazım hatası ya da ağ dışındasınız",
                "Server name could not be resolved (DNS) — check the address or your network",
            )
            "CONN_REFUSED" -> com.hermes.mobile.ui.tr(
                "Bağlantı reddedildi — sunucuda Hermes panosu çalışmıyor olabilir (port kapalı)",
                "Connection refused — the Hermes dashboard may not be running (port closed)",
            )
            "UNREACHABLE" -> com.hermes.mobile.ui.tr(
                "Sunucuya ağ üzerinden ulaşılamıyor — aynı ağda mısınız?",
                "Server unreachable over the network — are you on the same network?",
            )
            "TIMEOUT" -> com.hermes.mobile.ui.tr(
                "Sunucu zamanında yanıt vermedi (zaman aşımı) — ağ yavaş ya da sunucu meşgul",
                "Server did not answer in time (timeout) — slow network or busy server",
            )
            "TLS" -> com.hermes.mobile.ui.tr(
                "TLS/sertifika hatası — https adresinde sertifika geçersiz",
                "TLS/certificate error — invalid certificate on the https address",
            )
            "NETWORK" -> com.hermes.mobile.ui.tr(
                "Ağ bağlantısı koptu — Wi-Fi/veri bağlantısını kontrol edin",
                "Network connection dropped — check Wi-Fi/mobile data",
            )
            else -> com.hermes.mobile.ui.tr(
                "Bağlantı kurulamadı",
                "Could not connect",
            )
        }
        val ayrinti = t.message?.take(120)?.takeIf { it.isNotBlank() }
        return if (ayrinti != null) "$base · $kod ($ayrinti)" else "$base · $kod"
    }

    /** HTTP durum kodunun anlamı — token mı adres mi sunucu mu? */
    fun describeHttp(code: Int): String = when (code) {
        401, 403 -> com.hermes.mobile.ui.tr(
            "Token reddedildi (HTTP $code) — tokeni kopyalayıp yeniden girin",
            "Token rejected (HTTP $code) — paste the token again",
        )
        404 -> com.hermes.mobile.ui.tr(
            "Adres/port yanlış ya da bu uç sunucuda yok (HTTP 404)",
            "Wrong address/port, or this endpoint is missing on the server (HTTP 404)",
        )
        429, 502, 503, 504 -> com.hermes.mobile.ui.tr(
            "Sunucu geçici olarak meşgul (HTTP $code) — birkaç saniye sonra yeniden deneyin",
            "Server temporarily busy (HTTP $code) — retry in a few seconds",
        )
        in 500..599 -> com.hermes.mobile.ui.tr(
            "Sunucu iç hatası (HTTP $code) — sunucu loglarına bakın",
            "Server-side error (HTTP $code) — check the server logs",
        )
        else -> com.hermes.mobile.ui.tr("Sunucu hatası (HTTP $code)", "Server error (HTTP $code)")
    }
}
