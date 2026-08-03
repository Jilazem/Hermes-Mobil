package com.hermes.mobile.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Uygulama ön planda değilken gelen ajan yanıtlarını bildirime çevirir —
 * Telegram'daki "mesaj geldi" hissinin karşılığı.
 *
 * Sınır: bildirim ancak süreç yaşarken atılabilir. Android süreci arka planda
 * öldürürse WS de ölür; kalıcı push için FCM ya da sürekli ön plan servisi
 * gerekirdi, ikisi de bu aşamada istenmedi (pil + karmaşıklık). Pratikte
 * "uygulamadan çıktım, cevap gelince haber ver" senaryosunu karşılıyor.
 */
object Notifier {

    private const val CHANNEL_REPLIES = "hermes_replies"
    private var nextId = 1000

    /**
     * Uygulama görünür mü? `MainActivity.onStart/onStop` günceller.
     * Görünürken bildirim atılmaz — kullanıcı yanıtı zaten ekranda görüyor.
     */
    @Volatile
    var appVisible: Boolean = false

    private fun channel(context: Context): NotificationManager {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REPLIES,
                "Ajan yanıtları",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Uygulama kapalıyken gelen Hermes yanıtları" }
        )
        return nm
    }

    /**
     * @param sessionId yanıtın gideceği oturum; null ise yeni oturum açılır
     * @param force uygulama görünürken bile göster (bildirimden yanıtın sonucu)
     */
    fun agentReply(
        context: Context,
        text: String,
        sessionId: String? = null,
        force: Boolean = false,
    ) {
        if (appVisible && !force) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, com.hermes.mobile.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Bildirimden doğrudan yanıt — Telegram'daki gibi uygulamayı açmadan.
        val remoteInput = androidx.core.app.RemoteInput.Builder(ReplyService.KEY_REPLY)
            .setLabel("Yanıtla…")
            .build()
        val replyIntent = Intent(context, ReplyService::class.java)
            .putExtra(ReplyService.EXTRA_SESSION, sessionId)
        val replyPending = PendingIntent.getService(
            context,
            nextId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Yanıtla", replyPending,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val short = text.trim().take(300)
        val n = NotificationCompat.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Hermes")
            .setContentText(short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(short))
            .setContentIntent(open)
            .addAction(replyAction)
            .setAutoCancel(true)
            .build()

        channel(context).notify(nextId++, n)
    }
}
