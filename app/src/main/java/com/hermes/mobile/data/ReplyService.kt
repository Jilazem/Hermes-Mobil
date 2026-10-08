package com.hermes.mobile.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Android Auto and phone inline replies stay in the originating conversation. */
class ReplyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CrashGuard.handler)
    private val queue = Mutex()
    private var pendingRequests = 0 // Main thread only.

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.coroutineContext[Job]?.cancelChildren()
            stopSelf()
            return START_NOT_STICKY
        }
        val text = intent?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(KEY_REPLY)?.toString()?.trim()
        if (text.isNullOrBlank()) {
            if (pendingRequests == 0) stopSelf(startId)
            return START_NOT_STICKY
        }
        val session = intent.getStringExtra(EXTRA_SESSION)
        val profileId = intent.getStringExtra(EXTRA_PROFILE)
        val carVoice = intent.getBooleanExtra(EXTRA_CAR_VOICE, false)
        try { if (pendingRequests == 0) foreground(speaking = false) } catch (e: Exception) {
            DiagLog.w("reply", "Ön plan yanıt servisi başlatılamadı")
            Notifier.agentReply(this, "Yanıt gönderilemedi — sohbet ekranından yeniden dene",
                session, force = true, profileId = profileId, carVoice = carVoice)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        pendingRequests++
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { queue.withLock { withContext(Dispatchers.IO) { reply(text, session, profileId, carVoice) } } }
            finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    pendingRequests--
                    if (pendingRequests == 0) stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun reply(text: String, session: String?, profileId: String?, carVoice: Boolean) {
        val store = ServerProfileStore(this)
        val profile = if (profileId.isNullOrBlank()) store.active()
            else store.list().firstOrNull { it.id == profileId }
        var targetSession = session
        var sent = false
        var answer: String? = null
        var gateway: GatewayWsClient? = null
        try {
            check(profile != null && profile.token.isNotBlank()) { "Sunucu profili yok" }
            // Local phone commands are handled on IO, never on the service's UI thread.
            val phone = PhoneIntent.parse(text)?.takeIf { it.tool == "phone_messages" || it.tool == "phone_reply" }
            if (phone != null) {
                answer = PhoneTools(applicationContext, ShizukuBridge()).execute(phone.tool, phone.toJson())
            } else {
                val gw = GatewayWsClient(profile).also { gateway = it }
                gw.connect()
                withTimeout(20_000) { gw.connection.first { it is ConnectionState.Open } }
                val sid = if (!session.isNullOrBlank()) gw.attachSession(session) else gw.createSession(null)
                targetSession = session?.takeIf { it.isNotBlank() } ?: gw.storedSessionId(sid)
                if (session.isNullOrBlank()) {
                    val settings = SettingsStore(this).settings.value
                    val model = settings.modelsByServer[profile.id]
                        ?: settings.lastModel.takeIf { settings.modelsByServer.isEmpty() }.orEmpty()
                    val parts = model.split("|", limit = 2)
                    if (parts.size == 2) check(modelSwitchAccepted(
                        gw.slashExec(sid, "/model ${parts[1]} --provider ${parts[0]}"))) { "Kayıtlı model seçilemedi" }
                }
                val eventCounts = mutableMapOf<String, Int>()
                val response = NotificationReply(sid)
                val done = CompletableDeferred<String>()
                coroutineScope {
                    // SharedFlow has no replay; subscribe before submitting the prompt.
                    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                        gw.events.collect { event ->
                            if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                                val key = event.type + ":" + when(event.sessionId) { null -> "unscoped"; sid -> "matching"; else -> "other" }
                                eventCounts[key] = (eventCounts[key] ?: 0) + 1
                            }
                            if (response.accept(event)) done.complete(response.result!!)
                        }
                    }
                    try {
                        val question = if (carVoice) "[Sesli araç sohbeti: Yanıtını kısa, doğal Türkçe konuşma diliyle ver; liste veya kod gerekiyorsa ayrıntıyı sohbet ekranına bırak.]\n\n$text" else text
                        gw.submitPrompt(sid, question)
                        sent = true
                        answer = withTimeoutOrNull(120_000) { done.await() }
                    } finally {
                        collector.cancelAndJoin()
                        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) java.io.File(filesDir, "reply-event-proof.txt")
                            .writeText(eventCounts.entries.joinToString("\n") { "${it.key}=${it.value}" })
                    }
                }
            }
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            answer = if (sent) null else "Gönderilemedi — bağlantıyı sohbet ekranından denetle"
        }
        catch (e: Exception) {
            // Credentials and server URLs never appear in notification errors.
            answer = if (sent) null else "Gönderilemedi — bağlantıyı sohbet ekranından denetle"
        } finally { gateway?.close() }
        var message = answer ?: "Gönderildi — yanıt henüz tamamlanmadı; sohbet ekranından takip edebilirsin"
        if (carVoice && answer != null) {
            try {
                val config = checkNotNull(EmaConfig.from(this, profile)) { "Ses ayarlarından EMA’yı hazırla" }
                foreground(speaking = true)
                NotificationEmaSpeech(this, config.client()).speak(message)
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                message += "\nEMA okuması zaman aşımına uğradı. Yanıt metni kaybolmadı."
            }
            catch (e: Exception) { message += "\nEMA okuyamadı — ses ayarlarını denetle. Yanıt metni kaybolmadı." }
            finally { if (currentCoroutineContext().isActive) runCatching { foreground(speaking = false) } }
        }
        Notifier.agentReply(this, message, targetSession, force = true,
            profileId = profile?.id ?: profileId, carVoice = carVoice)
    }

    private fun foreground(speaking: Boolean) {
        val notification = progressNotification(speaking)
        if (Build.VERSION.SDK_INT >= 29) startForeground(FG_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                if (speaking) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        else startForeground(FG_ID, notification)
    }

    private fun progressNotification(speaking: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Hermes yanıtı", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, ReplyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.hermes.mobile.R.drawable.ic_stat_hermes)
            .setContentTitle(if (speaking) "Hermes konuşuyor" else "Hermes yanıtlıyor")
            .setContentText(if (speaking) "EMA ile okunuyor" else "Yanıt tamamlandığında haber verilecek")
            .addAction(android.R.drawable.ic_media_pause, "Durdur", stop)
            .setOngoing(true).build()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val KEY_REPLY = "hermes_reply_text"
        const val EXTRA_SESSION = "hermes_session_id"
        const val EXTRA_PROFILE = "hermes_profile_id"
        const val EXTRA_CAR_VOICE = "hermes_car_voice"
        const val ACTION_REPLY = "com.hermes.mobile.REPLY"
        const val ACTION_STOP = "com.hermes.mobile.STOP_REPLY"
        private const val CHANNEL = "hermes_sending"
        private const val FG_ID = 4713
    }
}
