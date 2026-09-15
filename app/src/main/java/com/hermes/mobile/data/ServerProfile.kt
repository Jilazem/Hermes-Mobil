package com.hermes.mobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Bir Hermes sunucusu. Token gizli veri olduğu için tüm liste
 * EncryptedSharedPreferences içinde saklanır — düz dosyaya asla yazılmaz.
 */
private fun String.toWs(): String = this
    .replaceFirst("https://", "wss://", ignoreCase = true)
    .replaceFirst("http://", "ws://", ignoreCase = true)

@Serializable
data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Örn. "http://192.168.1.10:9150" — sondaki eğik çizgi olmadan. */
    val baseUrl: String,
    /** HERMES_DASHBOARD_SESSION_TOKEN (token modu). */
    val token: String,
    val note: String = "",
    /**
     * Ev ağı dışından erişim adresi (Tailscale, Cloudflare tüneli, DDNS…).
     *
     * `baseUrl` genelde `192.168.1.x` gibi özel bir adres; mobil veriden
     * erişilemiyor ("failed to connect ... after 15000ms"). Bu alan doluysa
     * LAN'a ulaşılamadığında otomatik olarak buraya düşülür.
     */
    val remoteUrl: String = "",
    /**
     * Gemini Live rölesi. Boşsa Hermes adresinin host'u + 9170 varsayılır —
     * röle sunucuda Hermes'le aynı makinede koşuyor.
     */
    val relayUrl: String = "",
    /**
     * Telefon köprüsü (WS). Boşsa Hermes adresinin host'u + 9180 varsayılır.
     *
     * Neden gerekli: köprü portu sabit 9180'e gömülüydü, yani profildeki
     * adres portu yok sayılıyordu — 9280'deki bir sandbox köprüye (ya da
     * taşınmış gerçek porta) hiç ulaşılamıyor, sessizce canlı 9180'e
     * gidiliyordu. Açık verilen adres her şeyi ezer (relayUrl ile aynı kural).
     */
    val bridgeUrl: String = "",
    /**
     * Kullanıcının kendi Gemini anahtarı (isteğe bağlı). Boşsa röle sunucudaki
     * anahtarı kullanır; dolu olması anahtarın telefonda durması demektir.
     */
    val geminiApiKey: String = "",
) {
    val effectiveRelayUrl: String
        get() = relayUrl.trim().trimEnd('/').ifBlank {
            val base = activeUrl ?: normalizedUrl
            val uri = runCatching { java.net.URI(base) }.getOrNull()
            val host = uri?.host
            when {
                host.isNullOrBlank() -> ""
                // Ev ağında röleye doğrudan gidiyoruz — vekile gerek yok.
                isPrivateHost(host) -> "ws://$host:9170"
                // Dışarıdan yalnız 443 açık: 9170 yönlendirilmiş değil ve
                // yönlendirilemez de (alan adı yönlendiricinin bulut vekiline
                // çözülüyor, WAN'da ayrı port açılamıyor). Bunun yerine röle
                // Hermes'in kendi ters vekilinde bir yol olarak duruyor:
                // Caddy `:9150` bloğunda `handle_path /live-relay/*` →
                // `127.0.0.1:9170`. TLS'i bulut vekili sonlandırdığı için
                // şema `wss`; port yazmıyoruz, 443 zaten varsayılan.
                else -> {
                    val port = uri.port.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
                    "wss://$host$port/live-relay"
                }
            }
        }

    /** RFC1918 + localhost + .local — "aynı ağdayım" denebilecek adresler. */
    private fun isPrivateHost(host: String): Boolean {
        if (host.equals("localhost", true) || host.endsWith(".local", true)) return true
        val o = host.split(".").mapNotNull { it.toIntOrNull() }
        if (o.size != 4) return false
        return o[0] == 10 || o[0] == 127 ||
            (o[0] == 192 && o[1] == 168) ||
            (o[0] == 172 && o[1] in 16..31)
    }

    /**
     * Sondaki eğik çizgileri temizlenmiş taban adres.
     *
     * Kullanıcı şemasız yazabilir ("10.0.2.2:9199") — dialog'ın önerisi de
     * "http://" ile başlıyor ama kullanıcı yazmayabiliyor. Semasız adres
     * OkHttp'de FATAL EXCEPTION üretiyor (GatewayWsClient.openSocket,
     * 260913 emülatör kanıtı); burada http:// ekleyerek çökme sınıfını
     * kapatıyoruz. https kullanıcısı şemayı zaten kendisi yazar.
     */
    val normalizedUrl: String
        get() = baseUrl.trim().let { raw ->
            var it = raw.trimEnd('/', ':')
            // Kirli sema temizliği (2026-09-14 emülatör kanıtı): alan
            // "http://" önyüklüydü ve kullanıcı adresi başına yazınca
            // "httphttp://host" oluşuyor; toWs() ortadaki 'http://'yi
            // eşleştirip 'httpws://' üretiyor ve okhttp FATAL çökertiyordu.
            // Son "://" esas alınır; temiz sema ('http', 'https' — büyük/küçük
            // harf korunur, downgrade YOK) aynen bırakılır.
            val idx = it.lastIndexOf("://")
            if (idx > 0) {
                val schemeRaw = it.substring(0, idx)
                val scheme = when {
                    schemeRaw.equals("https", ignoreCase = true) ||
                        schemeRaw.equals("http", ignoreCase = true) -> schemeRaw
                    schemeRaw.endsWith("https", ignoreCase = true) -> "https"
                    schemeRaw.endsWith("http", ignoreCase = true) -> "http"
                    else -> schemeRaw
                }
                it = scheme + "://" + it.substring(idx + 3)
            }
            if (it.isNotEmpty() && !it.contains("://")) "http://$it" else it
        }

    val isHttps: Boolean get() = normalizedUrl.startsWith("https://", ignoreCase = true)

    /**
     * Köprü WebSocket adresi. Açık adres verilmişse o; yoksa etkin host +
     * [PHONE_BRIDGE_LAN_PORT]. Token burada eklenmez — çağıran kendi
     * kaçışlamasını yapar.
     */
    val effectiveBridgeUrl: String
        get() {
            val explicit = bridgeUrl.trim().trimEnd('/')
            if (explicit.isNotBlank()) return explicit
            val base = activeUrl ?: normalizedUrl
            val uri = runCatching { java.net.URI(base) }.getOrNull() ?: return ""
            val host = uri.host ?: return ""
            return if (isPrivateHost(host)) {
                "ws://$host:$PHONE_BRIDGE_LAN_PORT/phone"
            } else {
                val port = uri.port.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
                "wss://$host$port/phone-bridge/phone"
            }
        }

    /** Röle adresi — açık verilmemişse etkin host'tan türetilir. */

    val normalizedRemote: String get() = remoteUrl.trim().trimEnd('/')

    /**
     * Denenecek adresler, sırayla. LAN önce çünkü hızlı ve tünel kotası yakmıyor.
     * Çalışan adres [activeUrl] ile hatırlanır.
     */
    val candidates: List<String>
        get() = listOfNotNull(
            normalizedUrl.takeIf { it.isNotBlank() },
            normalizedRemote.takeIf { it.isNotBlank() },
        )

    /** ws:// veya wss:// karşılığı. */
    val wsBase: String get() = (activeUrl ?: normalizedUrl).toWs()

    companion object {
        /**
         * Son başarılı adres — profil kimliğine göre.
         *
         * Kalıcı değil: ağ değişince (evden çıkınca) yeniden keşfedilmeli.
         * Süreç boyunca tutmak, her istekte LAN zaman aşımını beklememizi önlüyor.
         */
        private val working = mutableMapOf<String, String>()

        fun remember(profileId: String, url: String) { working[profileId] = url }
        fun forget(profileId: String) { working.remove(profileId) }
        fun recall(profileId: String): String? = working[profileId]
    }

    val activeUrl: String? get() = recall(id)?.takeIf { it in candidates }
}

private const val PREFS_FILE = "hermes_profiles"
private const val KEY_PROFILES = "profiles"
private const val KEY_ACTIVE = "active_id"

/**
 * Köprünün ev ağındaki varsayılan WS portu (`phone_bridge.py` →
 * `PHONE_BRIDGE_PORT`, HTTP ucu port+1). Sunucuda başka bir porta alınırsa
 * profildeki `bridgeUrl` ile ezilir.
 */
const val PHONE_BRIDGE_LAN_PORT = 9180

class ServerProfileStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun list(): List<ServerProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrDefault(emptyList())
    }

    fun save(profiles: List<ServerProfile>) {
        prefs.edit().putString(KEY_PROFILES, json.encodeToString(profiles)).apply()
    }

    fun upsert(profile: ServerProfile) {
        val current = list().toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id }
        if (idx >= 0) current[idx] = profile else current.add(profile)
        save(current)
    }

    fun delete(id: String) {
        save(list().filterNot { it.id == id })
        if (activeId() == id) setActiveId(list().firstOrNull()?.id)
    }

    fun activeId(): String? = prefs.getString(KEY_ACTIVE, null)

    fun setActiveId(id: String?) {
        prefs.edit().apply { if (id == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, id) }.apply()
    }

    fun active(): ServerProfile? {
        val profiles = list()
        return profiles.firstOrNull { it.id == activeId() } ?: profiles.firstOrNull()
    }
}
