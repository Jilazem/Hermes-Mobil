package com.hermes.mobile.data

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.hermes.mobile.ui.tr

/**
 * WhatsApp / Telegram / SMS mesajlarını okuma ve **bildirimden yanıtlama**.
 *
 * Android Auto'nun mesajlaşma yolunun aynısı: sohbet uygulamaları gelen
 * mesajı `MessagingStyle` bildirimiyle ve bir "Yanıtla" eylemiyle (RemoteInput)
 * yayınlar; o eyleme metin doldurup göndermek, uygulamayı açmadan gerçek bir
 * yanıt yollar. Ekran kilitliyken ve araçtayken de çalışır, ekrana dokunmaya
 * (Tam kontrol / Artemis) gerek kalmaz.
 *
 * Sınır: yalnız **bildirimi hâlâ duran** sohbetler yanıtlanabilir (uygulama
 * açılıp okununca bildirim kalkar). Eşleşme kararı [MessageInboxLogic]'te
 * (saf, testli); burada yalnız Android'e dokunan kısım var.
 */
object MessageInbox {

    /** Mesajlaşma paketleri → görünen kısa ad. */
    val MESSAGING_APPS = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "org.thunderdog.challegram" to "Telegram X",
        "com.google.android.apps.messaging" to "Mesajlar",
        "com.samsung.android.messaging" to "Mesajlar",
        "org.thoughtcrime.securesms" to "Signal",
        "com.facebook.orca" to "Messenger",
        "com.instagram.android" to "Instagram",
    )

    private data class Live(val item: MessageInboxLogic.Item, val reply: Notification.Action?)

    private fun collect(context: Context): List<Live>? {
        val active = HermesNotificationListener.activeOrNull() ?: return null
        return active
            .filter { it.packageName in MESSAGING_APPS }
            // WhatsApp her sohbet için bir de "özet" (group summary) bildirimi
            // koyar; içeriği "3 sohbetten 5 mesaj" — okunacak bir şey değil.
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .mapNotNull { toLive(context, it) }
            .sortedByDescending { it.item.time }
    }

    private fun toLive(context: Context, sbn: StatusBarNotification): Live? {
        val n = sbn.notification
        val ex = n.extras
        // MessagingStyle (AndroidX uyumlu okuyucu — API 26'da da çalışır):
        // sohbetin son mesajları tek tek (gönderen + metin).
        val style = androidx.core.app.NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(n)
        val title = (style?.conversationTitle ?: ex.getCharSequence(Notification.EXTRA_TITLE))
            ?.toString()?.trim().orEmpty()
        val lines = style?.messages.orEmpty()
            .mapNotNull { m ->
                val text = m.text?.toString()?.trim().orEmpty()
                if (text.isEmpty()) null
                else m.person?.name?.toString()?.let { "$it: $text" } ?: text
            }
            .ifEmpty {
                listOfNotNull(
                    (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
                        ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim()
                        ?.takeIf { it.isNotEmpty() },
                )
            }
        if (title.isEmpty() && lines.isEmpty()) return null
        val reply = n.actions?.firstOrNull { a ->
            !a.remoteInputs.isNullOrEmpty() &&
                ((android.os.Build.VERSION.SDK_INT >= 28 &&
                    a.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY) ||
                    a.remoteInputs.any { it.allowFreeFormInput })
        }
        return Live(
            MessageInboxLogic.Item(
                key = sbn.key,
                app = MESSAGING_APPS[sbn.packageName] ?: HermesNotificationListener.appLabel(context, sbn.packageName),
                chat = title,
                lines = lines.takeLast(8),
                time = sbn.postTime,
                canReply = reply != null,
            ),
            reply,
        )
    }

    private fun noAccess(context: Context): String? = when {
        !HermesNotificationListener.accessGranted(context) -> tr(
            "Bildirim erişimi yok. Ayarlar → Telefon denetimi → \"Bildirim erişimi\" ile ver.",
            "No notification access. Grant it in Settings → Phone control → \"Notification access\".",
        )
        HermesNotificationListener.activeOrNull() == null -> tr(
            "Bildirim servisi henüz bağlanmadı, birazdan tekrar dene.",
            "The notification service hasn't connected yet, try again shortly.",
        )
        else -> null
    }

    /** Okunmamış sohbetler (bildirimi duranlar), en yeni önce. */
    fun read(context: Context, app: String = "", chat: String = "", limit: Int = 10): String {
        noAccess(context)?.let { return it }
        val items = MessageInboxLogic.filter(collect(context).orEmpty().map { it.item }, app, chat)
        return MessageInboxLogic.format(items.take(limit), app)
    }

    /**
     * Sohbete bildirim üzerinden yanıt gönderir. Kişi belirsizse (birden çok
     * eşleşme) GÖNDERMEZ, adayları söyler — yanlış kişiye mesaj geri alınamaz.
     */
    fun reply(context: Context, app: String, chat: String, text: String): String {
        if (text.isBlank()) return tr("Gönderilecek metin boş.", "Message text is empty.")
        noAccess(context)?.let { return it }
        val live = collect(context).orEmpty()
        return when (val m = MessageInboxLogic.pickReplyTarget(live.map { it.item }, app, chat)) {
            is MessageInboxLogic.Pick.None -> tr(
                "\"$chat\" için yanıtlanabilir bildirim yok (sohbet açılıp okunduysa bildirim kalkmış olabilir).",
                "No replyable notification for \"$chat\" (it may have been read already).",
            )
            is MessageInboxLogic.Pick.Ambiguous -> tr(
                "Birden çok sohbet eşleşti, hangisi? ", "Several chats match, which one? ",
            ) + m.options.joinToString { "${it.app} · ${it.chat}" }
            is MessageInboxLogic.Pick.One -> {
                val action = live.first { it.item.key == m.item.key }.reply
                    ?: return tr("Bu bildirimde yanıt eylemi yok.", "This notification has no reply action.")
                send(context, action, text)
                DiagLog.i("inbox", "yanit gonderildi -> ${m.item.app} · ${m.item.chat} (${text.length} krkt)")
                tr("${m.item.app} · ${m.item.chat} kişisine gönderildi: \"$text\"",
                    "Sent to ${m.item.app} · ${m.item.chat}: \"$text\"")
            }
        }
    }

    private fun send(context: Context, action: Notification.Action, text: String) {
        val inputs: Array<RemoteInput> = action.remoteInputs
        val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        val fill = Intent()
        RemoteInput.addResultsToIntent(inputs, fill, results)
        action.actionIntent.send(context, 0, fill)
    }
}
