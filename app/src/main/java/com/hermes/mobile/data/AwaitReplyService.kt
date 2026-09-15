package com.hermes.mobile.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Ajan yanıt üretirken uygulama arka plana atılırsa süreci ayakta tutar.
 *
 * Neden gerekiyor: sohbet soketi uygulama sürecinde yaşıyor. Kullanıcı ana
 * ekrana çıkınca Android süreci "cached" duruma alıp donduruyor; soket ölüyor,
 * ajan cevabı geldiğinde kimse dinlemiyor. Ölçtük — sunucuda yanıt vardı,
 * telefonda "Düşünüyor" asılı kalmıştı.
 *
 * Bu servis yalnız **yanıt beklenirken** çalışır ve yanıt gelir gelmez kendini
 * durdurur; sürekli açık bir bağlantı değil, o yüzden pil maliyeti bir istekle
 * sınırlı. Bildirim `IMPORTANCE_MIN` — kullanıcı için gürültü olmasın.
 */
class AwaitReplyService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Tur-5 bulgusu (crash buffer): nadiren
        // `ForegroundServiceDidNotStartInTimeException — Context.startForegroundService()
        // did not then call Service.startForeground()` görülüyordu. Sistem bu çağrıdan
        // sonra 5 sn içinde `startForeground` bekliyor; bildirim/kanal kurulamazsa
        // süreç ölüyor. Emniyet: kurulum başarısızsa HEMEN stopSelf — servisi
        // ayakta tutmanın bir anlamı yok, sistemin zamanlayıcısı da düşer.
        return runCatching {
            startForeground(FG_ID, notification())
            START_NOT_STICKY
        }.getOrElse {
            runCatching { stopSelf() }
            START_NOT_STICKY
        }
    }

    private fun notification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Yanıt bekleniyor", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) }
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.hermes.mobile.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Hermes çalışıyor")
            .setContentText("Yanıt gelince haber verilecek")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "hermes_await"
        private const val FG_ID = 4712

        /**
         * Aynı servis için TEKRARLI `startForegroundService` çağrısını engeller.
         *
         * Tur-5 bulgusu: ChatViewModel durumu her değiştiğinde (agentBusy true
         * kaldıkça her token/olayda) bu fonksiyon çağrılıyordu. Sistem aynı anda
         * stop/start isteklerini yarıştırınca
         * `ForegroundServiceDidNotStartInTimeException` ile süreç ölebiliyordu.
         * Bayrak süreç ömrüyle sınırlı; süreç ölürse sıfırlanır ve ilk start
         * yine geçer.
         */
        @Volatile
        private var running = false

        fun start(context: Context) {
            if (running) return
            runCatching {
                context.startForegroundService(Intent(context, AwaitReplyService::class.java))
                running = true
            }
        }

        fun stop(context: Context) {
            if (!running) return
            running = false
            runCatching { context.stopService(Intent(context, AwaitReplyService::class.java)) }
        }
    }
}
