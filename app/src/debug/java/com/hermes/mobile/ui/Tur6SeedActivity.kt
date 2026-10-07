package com.hermes.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.PhoneBridgeService
import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.data.ServerProfileStore
import com.hermes.mobile.data.SettingsStore
import java.io.File

/**
 * Emülatör doğrulaması için ayar tohumlayıcı — **yalnız debug** (src/debug).
 *
 * Neden var: "Tam kontrol" zinciri (uygulama → köprü → erişilebilirlik) üç
 * ayrı anahtara ve bir sunucu profiline bağlı. Bunları her turda elle
 * dokunarak kurmak hem yavaş hem de yazım hatasına açık; ayrıca token'ı
 * ekrana yazmak gerekirdi.
 *
 * Burada token **dosyadan** okunuyor: `filesDir/tur6_token.txt`. Böylece
 * gizli değer komut satırına, kabuk geçmişine ya da log'a hiç girmez.
 * Aktivite release APK'ya merge OLMAZ — src/debug source set'i yalnız
 * debug variant'ında derlenir.
 *
 * Kullanım:
 *   adb shell am start -n com.hermes.mobile.v3/com.hermes.mobile.ui.Tur6SeedActivity \
 *       --es url http://10.0.2.2:9180 --es name Tur6 \
 *       --ez full true --ez readonly false --ez agent true
 */
class Tur6SeedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent?.getStringExtra("url")?.trim().orEmpty()
        val name = intent?.getStringExtra("name")?.trim().takeUnless { it.isNullOrBlank() } ?: "Tur6"
        val profileOnly = intent?.getBooleanExtra("profile_only", false) ?: false
        val remote = intent?.getStringExtra("remote")?.trim().orEmpty()
        val agent = intent?.getBooleanExtra("agent", true) ?: true
        val readonly = intent?.getBooleanExtra("readonly", false) ?: false
        val full = intent?.getBooleanExtra("full", true) ?: true
        // Araç testi kullanıcının mevcut sohbetine deneme mesajı eklememeli.
        // Yalnız debug: test seçimini yedekle, bitince aynen geri yükle.
        val sessionBackup = File(filesDir, "car-test-session-backup")
        when (intent?.getStringExtra("test_session")) {
            "begin" -> {
                check(!sessionBackup.exists()) { "Önceki test sohbeti geri yüklenmeli" }
                sessionBackup.writeText(SettingsStore(this).settings.value.lastSession)
                SettingsStore(this).update { it.copy(lastSession = "") }
            }
            "restore" -> if (sessionBackup.exists()) {
                val saved = sessionBackup.readText()
                SettingsStore(this).update { it.copy(lastSession = saved) }
                sessionBackup.delete()
            }
        }
        // Köprü adresi (tur-7). Boşsa profil adresinden türetilir (ev ağında
        // 9180). SANDBOX doğrulaması için şart: uygulama aksi hâlde sabit 9180'e
        // gidip CANLI köprüyü meşgul ediyordu.
        val bridge = intent?.getStringExtra("bridge")?.trim().orEmpty()

        val tokenFile = File(filesDir, "tur6_token.txt")
        val token = tokenFile.takeIf { it.exists() }?.readText()?.trim().orEmpty()
        if (token.isBlank()) {
            DiagLog.w("tur6seed", "token dosyasi yok: ${tokenFile.absolutePath}")
        }

        val lang = intent?.getStringExtra("lang")
        if (lang in listOf("tr", "en", "auto")) SettingsStore(this).update { it.copy(uiLang = lang!!) }
        tokenFile.delete()
        val emaFile = File(filesDir, "ema-setup-token")
        val emaToken = emaFile.takeIf { it.isFile }?.readText()?.trim().orEmpty()
        emaFile.delete()
        if (emaToken.isNotBlank()) SettingsStore(this).update { it.copy(emaToken = emaToken) }
        if (!profileOnly) SettingsStore(this).update {
            it.copy(
                phoneTools = true,
                agentMayUsePhone = agent,
                agentReadOnly = readonly,
                fullControl = full,
            )
        }

        val store = ServerProfileStore(this)
        val active = store.active()
        val effectiveToken = token.ifBlank { active?.token.orEmpty() }
        if ((url.isNotBlank() || active != null) && effectiveToken.isNotBlank()) {
            val profile = active?.copy(
                name = if (intent.hasExtra("name")) name else active.name,
                baseUrl = url.ifBlank { active.baseUrl }, token = effectiveToken,
                remoteUrl = remote.ifBlank { active.remoteUrl },
                bridgeUrl = bridge.ifBlank { active.bridgeUrl },
            ) ?: ServerProfile(name = name, baseUrl = url, token = effectiveToken, bridgeUrl = bridge, remoteUrl = remote)
            store.upsert(profile); store.setActiveId(profile.id)
        }
        if (!profileOnly) {
            if (agent) PhoneBridgeService.start(this) else PhoneBridgeService.stop(this)
        }
        val current = store.active()
        File(filesDir, "hermes-setup-proof.json").writeText(kotlinx.serialization.json.buildJsonObject {
            put("base_url", kotlinx.serialization.json.JsonPrimitive(current?.normalizedUrl.orEmpty()))
            put("remote_url", kotlinx.serialization.json.JsonPrimitive(current?.normalizedRemote.orEmpty()))
            put("profile_token_present", kotlinx.serialization.json.JsonPrimitive(current?.token?.isNotBlank() == true))
            put("ema_token_present", kotlinx.serialization.json.JsonPrimitive(SettingsStore(this@Tur6SeedActivity).settings.value.emaToken.isNotBlank()))
            put("last_model", kotlinx.serialization.json.JsonPrimitive(SettingsStore(this@Tur6SeedActivity).settings.value.lastModel))
            put("ema_mode", kotlinx.serialization.json.JsonPrimitive(SettingsStore(this@Tur6SeedActivity).settings.value.emaMode))
            put("downloaded_model", kotlinx.serialization.json.JsonPrimitive(com.hermes.mobile.data.EmaModelStore.present(this@Tur6SeedActivity)))
        }.toString())

        com.hermes.mobile.data.HermesAccessibilityService.refreshNotice(this)

        // Token'ın KENDİSİ değil, yalnız varlığı ve uzunluğu log'lanıyor.
        DiagLog.i(
            "tur6seed",
            "seed tamam: url=$url bridge=$bridge agent=$agent readonly=$readonly full=$full " +
                "token=${if (token.isBlank()) "yok" else "var(${token.length} karakter)"}",
        )
        finish()
    }
}
