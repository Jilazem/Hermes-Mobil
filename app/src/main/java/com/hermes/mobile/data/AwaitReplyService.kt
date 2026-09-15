package com.hermes.mobile.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

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
 *
 * ── Tur-10: çökmenin kökü (F1) ────────────────────────────────────────────
 * Tur-5'te eklenen `running` bayrağı yarışı kapatmadı: gerçek cihazda
 * (SM-S918B, 14:09:32, session fb2e7567) çökme **tekrar** görüldü. Sebep:
 * bayrak yalnız "ikinci start"ı engelliyordu, ama asıl yarış
 * `startForegroundService` **çağrıldıktan sonra** `stopService` çağrılmasıydı —
 * sistem, servis `startForeground`'u çağırmadan yok edilirse zaman aşımını
 * yine fırlatıyor. (Ayrıca her state değişimi start/stop üretiyordu.)
 *
 * Kapanan sözleşme (karar makinesi: [AwaitLifecycle], testli):
 *  1. `onStartCommand`'ın **ilk** işi koşulsuz `startForeground` — hiçbir
 *     "artık gerek yok" kararı bu çağrıyı atlayamaz.
 *  2. Onay gelmeden `stopService` **çağrılmaz**; bunun yerine `pendingStop`
 *     işaretlenir ve servis onaydan sonra `stopSelf()` ile kapanır.
 *  3. `stop`'tan sonra gelen geç `start` yeni bir servis başlatmaz.
 *  4. Süreç içi watchdog: `startForegroundService` verildi ama 3 sn'de onay
 *     gelmediyse bayrak serbest bırakılır + tanı kaydına yazılır (sessiz
 *     kilitlenme yerine görünür olay).
 */
class AwaitReplyService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1) SİSTEM SÖZLEŞMESİ: startForegroundService'ten sonra 5 sn içinde
        //    startForeground ZORUNLU. İlk ifade bu; koşullu değil.
        val started = runCatching { startForeground(FG_ID, notification()) }
        if (started.isFailure) {
            // Bildirim/kanal kurulamadıysa servisi ayakta tutmanın anlamı yok;
            // sistemin zamanlayıcısı da hemen düşsün (tur-5 kararı korundu).
            DiagLog.e("await", "startForeground basarisiz - servis durduruluyor", started.exceptionOrNull()!!)
            runCatching { stopSelf() }
            return START_NOT_STICKY
        }

        // 2) Geç start yarışı: bu arada "bekleme bitti" dendiyse onayı ver ve
        //    hemen kapan (stopService çağrılmadığı için çökme üretilmez).
        val shouldStop = lifecycle.onForegroundConfirmed()
        if (shouldStop || !lifecycle.awaiting()) {
            DiagLog.d("await", "gec start onaylandi ve durduruldu (yaris kapatildi)")
            runCatching { stopSelf() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        lifecycle.onServiceDestroyed()
        watchdog.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun ensureChannel() {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Yanıt bekleniyor", NotificationManager.IMPORTANCE_MIN)
                    .apply { setShowBadge(false) }
            )
        }
    }

    private fun notification(): Notification {
        ensureChannel()
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

        /** start onayı için beklenen en uzun süre (sistem sınırı 5 sn). */
        private const val CONFIRM_TIMEOUT_MS = 3_000L

        private val lifecycle = AwaitLifecycle()
        private val watchdog = Handler(Looper.getMainLooper())

        /**
         * Tek giriş noktası: "yanıt bekleniyor mu?" durumunu bildirir.
         *
         * ChatViewModel bunu **durum değişiminde** çağırır; tekrar eden çağrılar
         * yeni bir `startForegroundService` üretmez (karar [AwaitLifecycle]'ta).
         */
        fun setAwaiting(context: Context, awaiting: Boolean) {
            when (lifecycle.setAwaiting(awaiting)) {
                AwaitLifecycle.Action.START -> {
                    val result = runCatching {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, AwaitReplyService::class.java),
                        )
                    }
                    if (result.isFailure) {
                        // Başlatma reddedildi (ör. arka planda FGS yasağı): bayrağı
                        // bırak, yoksa bir daha denenemez.
                        lifecycle.onServiceDestroyed()
                        DiagLog.w("await", "startForegroundService reddedildi: ${result.exceptionOrNull()?.message}")
                    } else {
                        DiagLog.d("await", "yanit bekleniyor - servis istendi")
                        scheduleWatchdog()
                    }
                }

                AwaitLifecycle.Action.STOP -> {
                    runCatching {
                        context.stopService(Intent(context, AwaitReplyService::class.java))
                    }
                    DiagLog.d("await", "yanit geldi - servis durduruldu")
                }

                AwaitLifecycle.Action.NONE -> Unit
            }
        }

        /** Tur-5 API'si — çağıranlar için kısayol. */
        fun start(context: Context) = setAwaiting(context, true)

        fun stop(context: Context) = setAwaiting(context, false)

        /**
         * Onay gelmezse bayrağı serbest bırak. Sistem 5 sn'de zaman aşımı
         * fırlatacağı için 3 sn'de uyarıp toparlanma şansı bırakıyoruz.
         */
        private fun scheduleWatchdog() {
            watchdog.postDelayed(
                {
                    if (lifecycle.isStartPending() && lifecycle.onStartTimeout(CONFIRM_TIMEOUT_MS)) {
                        DiagLog.w(
                            "await",
                            "start onayi ${CONFIRM_TIMEOUT_MS}ms icinde gelmedi - bayrak serbest birakildi",
                        )
                    }
                },
                CONFIRM_TIMEOUT_MS,
            )
        }

        /** Tanı ekranı için kısa iç durum. */
        fun healthSnapshot(): String =
            "await · awaiting=${lifecycle.awaiting()} onay=${lifecycle.isConfirmed()} " +
                "beklemede=${lifecycle.isStartPending()}"

        /** Yalnız test/tanı: süreç içi durumu sıfırlar. */
        internal fun resetForTest() = lifecycle.onServiceDestroyed()
    }
}
