package com.hermes.mobile.data

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Asistan rolünün **Android** tarafı: rolü kimin tuttuğunu okur ve sistem
 * diyaloğunun niyetini üretir. Karar [AssistantModeLogic]'te (saf, testli).
 *
 * Neden `RoleManager.getRoleHolders` YOK: o metot SDK'nın genel taslağında
 * (`platforms/android-35/android.jar`) bulunmuyor — derleme `Unresolved
 * reference 'getRoleHolders'` veriyor (javap ile doğrulandı: taslakta yalnız
 * `createRequestRoleIntent`, `isRoleAvailable`, `isRoleHeld` var). Bu yüzden
 * rol sahibi üç GENEL API'nin birleşiminden çıkarılıyor:
 *
 *  1. `isRoleHeld(ROLE_ASSISTANT)` — biz mi tutuyoruz? (kesin cevap)
 *  2. `Settings.Secure "assistant"` — sistemin kayıtlı asistan bileşeni
 *     (ör. Google), paket adı buradan çıkar
 *  3. `PackageManager.resolveActivity(ACTION_ASSIST)` — son çare
 *
 * Hiçbiri yoksa "atanmamış" denir; uydurma etiket gösterilmez.
 * API 29 altında hiçbir çağrı yapılmaz (`Unsupported`).
 */
object AssistantRole {

    /** Sistemin kayıtlı asistan bileşenini tutan ayar anahtarı. */
    private const val SECURE_ASSISTANT = "assistant"

    /** Şu anki rol durumu (rol desteklenmiyorsa `Unsupported`). */
    fun status(context: Context): AssistantModeLogic.RoleStatus =
        AssistantModeLogic.roleStatus(
            selfPackage = context.packageName,
            holders = holderCandidates(context),
            apiLevel = Build.VERSION.SDK_INT,
            selfHolds = selfHolds(context),
        )

    /** Hermes asistan rolünü tutuyor mu — API 29+ (kesin cevap). */
    fun selfHolds(context: Context): Boolean {
        val rm = roleManager(context) ?: return false
        return try {
            rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Asistan rolünü tuttuğu DÜŞÜNÜLEN paketler (en güvenilir önce).
     *
     * Sıra önemli: kayıtlı ayar, paket çözümlemeden önce gelir — Android 11+
     * paket görünürlüğünde `resolveActivity` boş dönebilir, ayar dönmez.
     */
    fun holderCandidates(context: Context): List<String> =
        listOfNotNull(secureAssistantPackage(context), resolvedAssistantPackage(context))
            .distinct()

    /** `Settings.Secure "assistant"` → paket adı (bileşen adından ayrıştırılır). */
    private fun secureAssistantPackage(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val raw = try {
            Settings.Secure.getString(context.contentResolver, SECURE_ASSISTANT)
        } catch (e: Throwable) {
            null
        } ?: return null
        val pkg = raw.substringBefore('/').trim()
        return pkg.takeIf { it.isNotEmpty() }
    }

    /** `ACTION_ASSIST` çözümlemesi — son çare ipucu. */
    private fun resolvedAssistantPackage(context: Context): String? {
        val intent = Intent(Intent.ACTION_ASSIST).addCategory(Intent.CATEGORY_DEFAULT)
        val resolved = try {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (e: Throwable) {
            null
        }
        return resolved?.activityInfo?.packageName
    }

    /**
     * Sistem rol diyaloğunun niyeti (`createRequestRoleIntent`).
     *
     * Rol desteklenmiyorsa ya da cihaz rolü sunmuyorsa `null` döner; çağıran
     * [settingsIntent] ile Ayarlar'a yönlendirir.
     */
    fun requestIntent(context: Context): Intent? {
        val rm = roleManager(context) ?: return null
        val available = try {
            rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
        } catch (e: Throwable) {
            false
        }
        if (!available) return null
        return try {
            rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
        } catch (e: Throwable) {
            null
        }
    }

    /** Elle atama: Ayarlar → Varsayılan uygulamalar. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Ayarlar ekranı da yoksa en son çare: uygulama ayarları kökü. */
    fun fallbackSettingsIntent(): Intent =
        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun roleManager(context: Context): RoleManager? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            context.getSystemService(RoleManager::class.java)
        } catch (e: Throwable) {
            null
        }
    }
}
