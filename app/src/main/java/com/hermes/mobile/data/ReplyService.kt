package com.hermes.mobile.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bildirimden gelen yanıtı Hermes'e iletir — Telegram'daki "bildirimden cevapla".
 *
 * Neden servis, neden `BroadcastReceiver` değil: alıcının `goAsync()` penceresi
 * ~10 saniye, ajan yanıtı ise çoğu zaman daha uzun sürüyor. Kısa ömürlü bir ön
 * plan servisi hem soketi açık tutuyor hem de sistemin süreci öldürmesini
 * geciktiriyor. Yanıt gelince (ya da 2 dakika dolunca) kendini durduruyor.
 */
class ReplyService : Service() {

    // Tur-2 K3(a): yakalanmayan coroutine hatasi izsiz dusmesin.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CrashGuard.handler)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(KEY_REPLY)?.toString()?.trim()
        val sessionId = intent?.getStringExtra(EXTRA_SESSION)

        if (text.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(FG_ID, progressNotification())

        scope.launch {
            val profile = ServerProfileStore(applicationContext).active()
            if (profile == null || profile.token.isBlank()) {
                Notifier.agentReply(applicationContext, "Sunucu profili yok — yanıt gönderilemedi")
                stopSelf(startId)
                return@launch
            }

            val gw = GatewayWsClient(profile)
            var reply: String? = null
            var sent = false

            val outcome = runCatching {
                gw.connect()
                // `first { }` bağlantı açılınca kendiliğinden çıkar — daha önce
                // `collect` içinden istisna fırlatarak çıkıyorduk ve akış
                // sessizce yarıda kalıyordu.
                val opened = withTimeoutOrNull(20_000) {
                    gw.connection.first { it is ConnectionState.Open }
                }
                checkNotNull(opened) { "Bağlantı açılmadı" }
                val sid = sessionId?.takeIf { it.isNotBlank() } ?: gw.createSession(null)
                // Oturum canlı değilse devam ettir; olmazsa yine de dene.
                if (sessionId != null) {
                    runCatching { gw.activateSession(sid) }
                        .recoverCatching { gw.resumeSession(sid) }
                }

                val collector = scope.launch {
                    gw.events.collect { e ->
                        if (e.type == "message.complete" || e.type == "message.delta") {
                            e.text?.takeIf { it.isNotBlank() }?.let { reply = it }
                        }
                    }
                }
                gw.submitPrompt(sid, text)
                sent = true
                // Bildirimden başlatılan ön plan servisine sistem ~30 sn izin
                // veriyor; daha uzun beklemek servisi öldürtüyordu. Yakalarsak
                // yanıtı gösteririz, yakalayamazsak uygulama açılınca zaten
                // `syncPending()` tamamlıyor.
                withTimeoutOrNull(25_000) {
                    while (reply == null) kotlinx.coroutines.delay(250)
                }
                collector.cancel()
            }

            gw.close()
            val message = when {
                reply != null -> reply!!
                sent -> "Gönderildi — yanıt uygulamada görünecek"
                else -> "Gönderilemedi: ${outcome.exceptionOrNull()?.message ?: "bilinmeyen hata"}"
            }
            Notifier.agentReply(applicationContext, message, sessionId = sessionId, force = true)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    private fun progressNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Yanıt gönderiliyor", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Hermes")
            .setContentText("Yanıtın gönderiliyor…")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val KEY_REPLY = "hermes_reply_text"
        const val EXTRA_SESSION = "hermes_session_id"
        private const val CHANNEL = "hermes_sending"
        private const val FG_ID = 4711
    }
}
