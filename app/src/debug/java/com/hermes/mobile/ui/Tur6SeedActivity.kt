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
 *   adb shell am start -n com.hermes.mobile.v2/com.hermes.mobile.ui.Tur6SeedActivity \
 *       --es url http://10.0.2.2:9180 --es name Tur6 \
 *       --ez full true --ez readonly false --ez agent true
 */
class Tur6SeedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent?.getStringExtra("url")?.trim().orEmpty()
        val name = intent?.getStringExtra("name")?.trim().takeUnless { it.isNullOrBlank() } ?: "Tur6"
        val agent = intent?.getBooleanExtra("agent", true) ?: true
        val readonly = intent?.getBooleanExtra("readonly", false) ?: false
        val full = intent?.getBooleanExtra("full", true) ?: true

        val tokenFile = File(filesDir, "tur6_token.txt")
        val token = tokenFile.takeIf { it.exists() }?.readText()?.trim().orEmpty()
        if (token.isBlank()) {
            DiagLog.w("tur6seed", "token dosyasi yok: ${tokenFile.absolutePath}")
        }

        SettingsStore(this).update {
            it.copy(
                phoneTools = true,
                agentMayUsePhone = agent,
                agentReadOnly = readonly,
                fullControl = full,
            )
        }

        if (url.isNotBlank() && token.isNotBlank()) {
            val store = ServerProfileStore(this)
            val profile = store.active()?.copy(name = name, baseUrl = url, token = token)
                ?: ServerProfile(name = name, baseUrl = url, token = token)
            store.upsert(profile)
            store.setActiveId(profile.id)
        }

        if (agent) PhoneBridgeService.start(this) else PhoneBridgeService.stop(this)
        com.hermes.mobile.data.HermesAccessibilityService.refreshNotice(this)

        // Token'ın KENDİSİ değil, yalnız varlığı ve uzunluğu log'lanıyor.
        DiagLog.i(
            "tur6seed",
            "seed tamam: url=$url agent=$agent readonly=$readonly full=$full " +
                "token=${if (token.isBlank()) "yok" else "var(${token.length} karakter)"}",
        )
        finish()
    }
}
