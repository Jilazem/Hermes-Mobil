package com.hermes.mobile.car

import android.content.Context
import android.os.Build

data class MolaPrerequisites(val autoVersion: String?, val installer: String?) {
    val visibilityAdvice: String get() = when {
        autoVersion == null -> "Android Auto bu telefonda bulunamadı. Önce Android Auto kurulumunu ve güncellemesini kontrol et."
        installer != "com.android.vending" -> "Hermes’i telefonda bir kez açıp Android Auto bağlantısını yenile. Park halinde Hermes açılışı Mola Park olmalıdır. ‘Mesaj gelmedi’ eski bildirim girişidir. Referans Mola’nın Shizuku / KingInstaller yöntemi cihaz sürümüne bağlıdır; Shizuku’nun kurulu olması tek başına yeterli değildir."
        else -> "Google Play kurulum kaydı var. Android Auto → Başlatıcıyı özelleştir bölümünü, uygulama güncellemesini ve host uyumluluğunu kontrol et. Kurulum kaydı tek başına araç kabulü değildir."
    }
    companion object {
        const val AUTO_PACKAGE = "com.google.android.projection.gearhead"
        @Suppress("DEPRECATION")
        fun read(context: Context): MolaPrerequisites {
            val pm = context.packageManager
            val version = runCatching { pm.getPackageInfo(AUTO_PACKAGE, 0).versionName ?: "Sürüm bilinmiyor" }.getOrNull()
            val installer = runCatching {
                if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(context.packageName).installingPackageName
                else pm.getInstallerPackageName(context.packageName)
            }.getOrNull()
            return MolaPrerequisites(version, installer)
        }
    }
}
