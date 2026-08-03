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

    /** Sondaki eğik çizgileri temizlenmiş taban adres. */
    val normalizedUrl: String get() = baseUrl.trimEnd('/')

    val isHttps: Boolean get() = normalizedUrl.startsWith("https://", ignoreCase = true)

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
