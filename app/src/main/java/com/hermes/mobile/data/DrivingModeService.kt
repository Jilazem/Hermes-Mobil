package com.hermes.mobile.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.hermes.mobile.MainActivity
import com.hermes.mobile.R
import com.hermes.mobile.ui.tr

/**
 * Sürüş kipi ön plan servisi.
 *
 * Android 14'ten (API 34) itibaren uygulama ön planda değilken mikrofon
 * kullanmak için **`microphone` tipinde bir ön plan servisi** şart. Ekran
 * kapandığında ya da kullanıcı başka uygulamaya geçtiğinde sesli oturumun
 * devam etmesi buna bağlı — sürüşte ekrana bakılmayacağı için bu zorunlu.
 *
 * Servis sesi kendisi yönetmiyor; yalnız süreci ayakta ve mikrofonu meşru
 * tutuyor. Ses akışının sahibi `LiveVoiceViewModel`.
 */
class DrivingModeService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    /** Sürüş kipi mi (ekran kapalı, büyük arayüz) yoksa arka plan dinlemesi mi. */
    private var driving = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        driving = intent?.getBooleanExtra(EXTRA_DRIVING, false) ?: false
        startForegroundCompat()
        // Wake lock yalnız sürüş kipinde: normal dinlemede ekran kapanınca
        // işlemciyi zorla ayakta tutmak pili boşuna yiyor.
        if (driving) acquireWakeLock()
        // Sistem öldürürse yeniden başlatma: kullanıcı sürüş kipini açıkça
        // kapatmadıkça ses oturumu sürmeli.
        return START_STICKY
    }

    private fun startForegroundCompat() {
        createChannel()

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, DrivingModeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        // Aynı servis iki durumu karşılıyor: sürüş kipi ve "başka uygulama
        // açıkken dinlemeye devam". Bildirim metni hangisi olduğunu söylüyor.
        val title =
            if (driving) tr("Sürüş kipi açık", "Driving mode on")
            else tr("Hermes dinliyor", "Hermes is listening")
        val body =
            if (driving) tr("Hermes dinliyor — konuşabilirsin", "Listening — go ahead")
            else tr(
                "Başka uygulamada da konuşmaya devam edebilirsin",
                "Keep talking even while you use other apps",
            )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_stat_hermes)
            .setContentIntent(open)
            .addAction(0, tr("Durdur", "Stop"), stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Ekran kapalıyken işlemcinin uyumaması için kısmi kilit.
     *
     * Süresiz kilit pil yakar; 4 saatlik üst sınır konuyor — makul bir yolculuk
     * süresinden uzun, ama unutulup gece boyu pil bitirmesini engelliyor.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hermes:driving").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Sürüş kipi",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Sürerken sesli asistan açıkken gösterilir"
                setShowBadge(false)
            }
        )
    }

    override fun onDestroy() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "hermes_driving"
        private const val NOTIFICATION_ID = 4711
        const val ACTION_STOP = "com.hermes.mobile.DRIVING_STOP"

        const val EXTRA_DRIVING = "driving"

        fun start(context: Context, driving: Boolean = false) {
            val intent = Intent(context, DrivingModeService::class.java)
                .putExtra(EXTRA_DRIVING, driving)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DrivingModeService::class.java))
        }
    }
}
