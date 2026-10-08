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

    /** Ajanın kendi haberleri — yanıt bildiriminden ayrı kimlik. */
    private const val AGENT_MSG_ID = 4814

    private const val CHANNEL_REPLIES = "hermes_replies"
    private val nextId = java.util.concurrent.atomic.AtomicInteger(1000)

    /**
     * Uygulama görünür mü? `MainActivity.onStart/onStop` günceller.
     * Görünürken bildirim atılmaz — kullanıcı yanıtı zaten ekranda görüyor.
     */
    @Volatile
    var appVisible: Boolean = false
    @Volatile var visibleSession: Pair<String, String?>? = null

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

    // ── JARVIS-2 (tur24): kalıcı "Jarvis dinliyor" ───────────────────

    private const val JARVIS_LISTEN_ID = 4815
    private const val CHANNEL_JARVIS = "hermes_jarvis"

    /**
     * Döngü açıkken kalıcı bildirim — mikrofonun açık OLDUĞUNU kullanıcı
     * her zaman bilsin (görev maddesi 4; arka plan kaydı yok, döngü yalnız
     * ekran açıkken koşar ama durum çubuğu dürüstlük gereği bildirir).
     */
    fun jarvisListening(context: Context, on: Boolean) {
        val nm = channel(context)
        if (!on) {
            nm.cancel(JARVIS_LISTEN_ID)
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_JARVIS,
                "Jarvis döngüsü",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Sürekli sesli sohbet döngüsü açıkken kalır" }
        )
        val n = NotificationCompat.Builder(context, CHANNEL_JARVIS)
            .setSmallIcon(com.hermes.mobile.R.drawable.ic_stat_hermes)
            .setContentTitle("Jarvis dinliyor")
            .setContentText("Sürekli sesli sohbet açık — kapatmak için uygulamayı aç")
            .setOngoing(true)
            .build()
        runCatching { nm.notify(JARVIS_LISTEN_ID, n) }
    }

    /**
     * Ajanın kendiliğinden gönderdiği bildirim.
     *
     * [agentReply]'den ayrı: o, kullanıcının sorduğu bir şeyin cevabı ve
     * uygulama önplandayken gizleniyor. Bu ise ajanın kendi başlattığı bir
     * haber ("yedekleme bitti", "toplantıya 10 dakika") — kullanıcı
     * uygulamaya bakıyor olsa bile görünmesi gerekiyor, çünkü sohbette
     * karşılığı olan bir mesaj yok.
     */
    fun agentMessage(context: Context, title: String, text: String) {
        val profileId = ServerProfileStore(context).activeId().orEmpty()
        if (text.isNotBlank()) ActivityInbox.get(context).add(ActivityNotice(profileId = profileId, title = title, text = text.take(4_000), kind = "agent"))
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, com.hermes.mobile.MainActivity::class.java)
                .putExtra("hermes_action", "notifications")
                .setData(android.net.Uri.parse("hermes://notifications/agent"))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        channel(context).notify(
            AGENT_MSG_ID,
            NotificationCompat.Builder(context, CHANNEL_REPLIES)
                .setSmallIcon(com.hermes.mobile.R.drawable.ic_stat_hermes)
                .setContentTitle(title)
                .setContentText(text.take(240))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(1_500)))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
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
        profileId: String? = null,
        title: String = "Hermes",
        carVoice: Boolean = false,
    ) {
        val targetProfile = profileId ?: ServerProfileStore(context).activeId().orEmpty()
        if (text.isNotBlank()) ActivityInbox.get(context).add(ActivityNotice(profileId = targetProfile,
            sessionId = sessionId, title = title.ifBlank { "Hermes" }, text = text.take(4_000),
            read = appVisible && visibleSession == (targetProfile to sessionId)))
        if (appVisible && !force) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, com.hermes.mobile.MainActivity::class.java)
                .putExtra(com.hermes.mobile.MainActivity.EXTRA_OPEN_SESSION, sessionId)
                .putExtra(com.hermes.mobile.MainActivity.EXTRA_OPEN_PROFILE, targetProfile)
                .putExtra("hermes_action", "notifications")
                .setData(android.net.Uri.parse("hermes://reply/${android.net.Uri.encode(targetProfile)}/${android.net.Uri.encode(sessionId.orEmpty())}"))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Bildirimden doğrudan yanıt — Telegram'daki gibi uygulamayı açmadan.
        val remoteInput = androidx.core.app.RemoteInput.Builder(ReplyService.KEY_REPLY)
            .setLabel("Yanıtla…")
            .build()
        val conversation = "${android.net.Uri.encode(targetProfile)}/${android.net.Uri.encode(sessionId.orEmpty())}"
        val replyIntent = Intent(context, ReplyService::class.java)
            .setAction(ReplyService.ACTION_REPLY)
            .setData(android.net.Uri.parse("hermes://notification-reply/$conversation/${if (carVoice) "ema" else "quiet"}"))
            .putExtra(ReplyService.EXTRA_CAR_VOICE, carVoice)
            .putExtra(ReplyService.EXTRA_SESSION, sessionId)
            .putExtra(ReplyService.EXTRA_PROFILE, targetProfile)
        val replyPending = PendingIntent.getService(
            context,
            0,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        // Android Auto şartları: yanıt eylemi SEMANTIC_ACTION_REPLY + arayüz
        // açmaz; ayrıca görünmez bir "okundu" eylemi; MessagingStyle. Böylece
        // araç ekranında Hermes bir mesajlaşma uygulaması gibi görünür, yanıtı
        // sesle okunur ve sesle cevap verilebilir (cevap ReplyService'e gelir).
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Yanıtla", replyPending,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()

        val tag = if (carVoice) "car:$conversation" else null
        val id = if (carVoice) 4870 else nextId.incrementAndGet()
        val markRead = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view, "Okundu",
            PendingIntent.getBroadcast(
                context, id,
                Intent(context, MarkReadReceiver::class.java)
                    .setData(android.net.Uri.parse("hermes://notification-read/$conversation/$id"))
                    .putExtra(MarkReadReceiver.EXTRA_ID, id)
                    .putExtra(MarkReadReceiver.EXTRA_TAG, tag),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val short = text.trim().take(4_000)
        val me = androidx.core.app.Person.Builder().setName("Sen").setKey("me").build()
        val hermes = androidx.core.app.Person.Builder().setName("Hermes").setKey("hermes").setBot(true).build()
        val style = NotificationCompat.MessagingStyle(me)
            .addMessage(short, System.currentTimeMillis(), hermes)
        val n = NotificationCompat.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Hermes")
            .setContentText(short)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .addAction(replyAction)
            .addInvisibleAction(markRead)
            .setAutoCancel(true)
            .build()

        channel(context).notify(tag, id, n)
    }
}


/** Android Auto "okundu" eylemi — Hermes yanıt bildirimini kaldırır. */
class MarkReadReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id >= 0) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(intent.getStringExtra(EXTRA_TAG), id)
        }
    }

    companion object {
        const val EXTRA_ID = "hermes_notification_id"
        const val EXTRA_TAG = "hermes_notification_tag"
    }
}
