package com.hermes.mobile.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.hermes.mobile.ui.tr

/**
 * Bildirim okuma — "bugün ne kaçırdım?" sorusunun karşılığı.
 *
 * Neden ayrı bir servis: Android bildirimleri normal bir izinle vermiyor,
 * **özel erişim** istiyor (Ayarlar → Bildirim erişimi). Servis bağlandığında
 * sistem duran bildirimleri okumaya izin veriyor.
 *
 * Sınırlar bilinçli:
 * - **Hiçbir şey saklanmıyor.** Sorulduğu anda duran bildirimler okunuyor;
 *   geçmiş bir kayıt tutmak, telefondaki her bildirimin kopyasını biriktirmek
 *   demek olurdu ve buna gerek yok.
 * - **Kendi bildirimlerimiz atlanıyor** — yoksa "yanıt bekleniyor" kendi
 *   bildirimimizi özete koyuyor.
 * - **Süregelen (ongoing) bildirimler atlanıyor:** müzik çalar, VPN, dosya
 *   indirme gibi kalıcı olanlar "kaçırdığın şey" değil.
 */
class HermesNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        bagli = true
        instance = this
    }

    override fun onListenerDisconnected() {
        bagli = false
        instance = null
    }

    companion object {
        @Volatile
        private var instance: HermesNotificationListener? = null

        @Volatile
        private var bagli = false

        /** Kullanıcı bildirim erişimini verdi mi? */
        fun accessGranted(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners",
            ).orEmpty()
            val bizim = ComponentName(context, HermesNotificationListener::class.java)
            return enabled.contains(bizim.flattenToString()) ||
                enabled.contains(bizim.flattenToShortString())
        }

        /**
         * Duran bildirimlerin özeti. Erişim yoksa **ne yapılacağını söyleyen**
         * metin döner — boş liste dönmek "bildirim yok" gibi görünüyordu.
         */
        fun summary(context: Context, limit: Int = 15): String {
            if (!accessGranted(context)) {
                return tr(
                    "Bildirim erişimi yok. Ayarlar → Telefon denetimi → " +
                        "\"Bildirim erişimi\" ile verebilirsin.",
                    "No notification access. Grant it from Settings → Phone control → " +
                        "\"Notification access\".",
                )
            }
            val svc = instance ?: return tr(
                "Bildirim servisi henüz bağlanmadı, birazdan tekrar dene.",
                "The notification service hasn't connected yet, try again shortly.",
            )
            val aktif: Array<StatusBarNotification> =
                runCatching { svc.activeNotifications }.getOrNull() ?: return tr(
                    "Bildirimler okunamadı.", "Couldn't read notifications.",
                )

            val bizimPaket = context.packageName
            val satirlar = aktif
                .asSequence()
                .filter { it.packageName != bizimPaket }
                .filter { (it.notification.flags and Notification.FLAG_ONGOING_EVENT) == 0 }
                .mapNotNull { sbn ->
                    val ex = sbn.notification.extras
                    val baslik = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                    val metin = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
                        ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim()
                    if (baslik.isNullOrBlank() && metin.isNullOrBlank()) return@mapNotNull null
                    val uygulama = appLabel(context, sbn.packageName)
                    val govde = listOfNotNull(
                        baslik?.takeIf { it.isNotBlank() },
                        metin?.takeIf { it.isNotBlank() },
                    ).joinToString(" — ")
                    "$uygulama: $govde"
                }
                .distinct()
                .take(limit)
                .toList()

            return if (satirlar.isEmpty())
                tr("Bekleyen bildirim yok.", "No pending notifications.")
            else satirlar.joinToString("\n")
        }

        private fun appLabel(context: Context, pkg: String): String = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg.substringAfterLast('.'))
    }
}
