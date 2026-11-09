package com.hermes.mobile.data

/**
 * B3 (v6.3) — **adres normalizasyonu tek saf fonksiyonda**.
 *
 * Sahada iki ayrı kusur vardı:
 *
 *  1. Kullanıcı `https://host` yazınca WS adresi `ws://host/api/ws` olabiliyor
 *     (cleartext downgrade) ya da port `:0` ile kuruluyordu; OkHttp `HttpUrl`
 *     yalnız http/https kabul ettiği için dönüştürme de dağınıktı.
 *  2. Kullanıcı adresi `/api/ws` ile biten hâliyle yapıştırınca yol iki kez
 *     ekleniyor (`/api/ws/api/ws`) ve sunucu 404 dönüyordu.
 *
 * Kurallar (tek yer):
 *  - `https://host`  → HTTP `https://host`  (port 443), WS `wss://host/api/ws`
 *  - `http://host:9150` → HTTP `http://host:9150`, WS `ws://host:9150/api/ws`
 *  - şemasız adres: özel ağ (RFC1918/localhost/.local) → `http`, diğer → `https`
 *  - `ws://`/`wss://` girişi http/https karşılığına çevrilir (kullanıcı WS
 *    adresini yapıştırmış olabilir)
 *  - sondaki `/` kırpılır, `?query`/`#frag` atılır
 *  - yol zaten `/api/ws` ile bitiyorsa **tekrar eklenmez**
 *  - özel ağ dışında `http` → `https`'e yükseltilir (cleartext yalnız LAN)
 *
 * Saf fonksiyon: Android/OkHttp bağımlılığı yok, birim test edilir.
 */
data class Endpoint(
    /** Etkin şema: `http` ya da `https` (girişteki büyük harf korunur). */
    val scheme: String,
    val host: String,
    /** Etkin port — açıkça yazılmadıysa şemanın varsayılanı (443/80). */
    val port: Int,
    val explicitPort: Boolean,
    /** Sondaki eğik çizgi olmadan yol; kök için "". */
    val path: String,
    /** `şema://host[:port][yol]` — varsayılan port yazılmaz. */
    val base: String,
    val wsScheme: String,
    val wsBase: String,
    val apiWsUrl: String,
    val privateHost: Boolean,
) {
    /** Ekranda gösterilecek hedef: "wss://host:443/api/ws". */
    fun displayWs(): String = "$wsScheme://$host:$port$apiPath"

    private val apiPath: String
        get() {
            val i = apiWsUrl.indexOf("://")
            val afterAuthority = apiWsUrl.substring(i + 3).substringAfter('/', "")
            return if (afterAuthority.isEmpty()) "" else "/$afterAuthority"
        }
}

object EndpointNormalizer {

    /** `/api/ws` yolunun kanonik hâli. */
    const val API_WS_PATH = "/api/ws"

    private const val DEFAULT_HTTPS_PORT = 443
    private const val DEFAULT_HTTP_PORT = 80

    /** Yalnız şema artığı: `http:` / `https:` gibi — host/port içermeyen girdi. */
    private val SCHEME_ONLY = Regex("^[A-Za-z][A-Za-z0-9+.-]*:$")

    /**
     * Ham kullanıcı girdisini [Endpoint]'e çevirir. Boş/ayrıştırılamaz girdi → null.
     *
     * @param raw profil alanındaki adres (şema kirli olabilir: `httphttp://…`)
     */
    fun normalize(raw: String): Endpoint? {
        var it = stripQueryAndFragment(raw.trim())
        if (it.isBlank()) return null

        // Yalnız şema artığı (`http://` → trimEnd('/') ile `http:`): sondaki
        // eğik çizgiler şemayı yutmuş demektir; `http` host sanılmamalı.
        if (it.matches(SCHEME_ONLY)) return null

        // Kirli şema onarımı: son "://" esas alınır (`httphttp://host` → `http`).
        val idx = it.lastIndexOf("://")
        var schemeRaw: String? = null
        if (idx > 0) {
            schemeRaw = it.substring(0, idx)
            it = it.substring(idx + 3)
        }
        // `host:9150://` gibi kalıntı: authority'de "://" kalmamalı.
        if (it.contains("://")) it = it.substringAfterLast("://")

        val (host, portText) = splitAuthority(it.substringBefore('/'))
        if (host.isBlank()) return null
        val path = it.substringAfter('/', "").trim('/').let { p -> if (p.isEmpty()) "" else "/$p" }

        val explicitScheme = schemeRaw?.let(::cleanScheme)
        val private = isPrivateHost(host)

        var scheme = when {
            explicitScheme != null -> explicitScheme
            // Şemasız: özel ağ → http, diğer → https.
            private -> "http"
            else -> "https"
        }
        // Cleartext yalnız özel ağda: dış host'a http verilirse https'e yükselt.
        if (scheme.equals("http", true) && !private) scheme = "https"

        val explicitPort = portText.toIntOrNull()?.takeIf { it in 1..65535 }
        val port = explicitPort ?: if (scheme.equals("https", true)) DEFAULT_HTTPS_PORT else DEFAULT_HTTP_PORT

        val authority = if (explicitPort != null) "$host:$port" else host
        val base = "$scheme://$authority$path"

        val wsScheme = if (scheme.equals("https", true)) "wss" else "ws"
        val wsBase = "$wsScheme://$authority$path"
        // Yol zaten /api/ws ile bitiyorsa tekrar eklenmez.
        val apiWsUrl = if (path.endsWith(API_WS_PATH)) "$wsScheme://$authority$path"
        else "$wsScheme://$authority$path$API_WS_PATH"

        return Endpoint(
            scheme = scheme,
            host = host,
            port = port,
            explicitPort = explicitPort != null,
            path = path,
            base = base,
            wsScheme = wsScheme,
            wsBase = wsBase,
            apiWsUrl = apiWsUrl,
            privateHost = private,
        )
    }

    /** Yalnız WS tabanı (`…/api/ws` olmadan). */
    fun wsBase(raw: String): String? = normalize(raw)?.wsBase

    /** Soket açılacak tam adres: `wss://host/api/ws`. */
    fun apiWsUrl(raw: String): String? = normalize(raw)?.apiWsUrl

    /**
     * Şema temizliği: `http`/`https` (girişteki büyük harf korunur), `ws`→`http`,
     * `wss`→`https`. Tanınmayan kalıntıda son ek esas alınır (`httphttp` → `http`).
     */
    private fun cleanScheme(raw: String): String {
        val s = raw.trim()
        return when {
            s.equals("http", true) -> s
            s.equals("https", true) -> s
            s.equals("ws", true) -> "http"
            s.equals("wss", true) -> "https"
            s.endsWith("https", true) -> "https"
            s.endsWith("http", true) -> "http"
            s.endsWith("wss", true) -> "https"
            s.endsWith("ws", true) -> "http"
            else -> "http"
        }
    }

    /** `[::1]:9150`, `host:9150`, `host` ayrıştırması. */
    private fun splitAuthority(authority: String): Pair<String, String> {
        val noUser = authority.substringAfterLast('@')
        if (noUser.startsWith("[")) {
            val end = noUser.indexOf(']')
            if (end > 0) {
                val host = noUser.substring(0, end + 1)
                val rest = noUser.substring(end + 1).removePrefix(":")
                return host to rest
            }
        }
        val colon = noUser.lastIndexOf(':')
        return if (colon > 0 && noUser.count { it == ':' } == 1) {
            noUser.substring(0, colon) to noUser.substring(colon + 1)
        } else {
            noUser to ""
        }
    }
}
