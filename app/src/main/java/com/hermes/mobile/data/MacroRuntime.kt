package com.hermes.mobile.data

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.*
import android.util.AtomicFile
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.opentasker.core.engine.CooldownReservations
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MacroStore(context: Context) {
    private val prefs = context.getSharedPreferences("hermes-macros", Context.MODE_PRIVATE)
    private val file = AtomicFile(File(context.filesDir, "macros-v1.json"))
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { check(prefs.edit().putBoolean("enabled", value).commit()) }
    fun rules(): List<MacroRule> = synchronized(lock) {
        // openRead restores an interrupted write's .bak even when the base file is absent.
        val stream = try { file.openRead() } catch (e: FileNotFoundException) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized emptyList()
            throw e
        }
        MacroRules.json.decodeFromString(stream.use { it.readBytes().toString(Charsets.UTF_8) })
    }
    fun save(rules: List<MacroRule>) = synchronized(lock) {
        rules() // Recover an interrupted write, and never replace an unreadable record.
        require(rules.size <= 100) { "En çok 100 makro saklanabilir" }
        require(rules.map { it.id }.distinct().size == rules.size) { "Makro kimlikleri benzersiz olmalı" }
        require(rules.none { it.enabled && MacroRules.validate(it).isNotEmpty() }) { "İnceleme gereken makrolar etkinleştirilemez" }
        val bytes = MacroRules.json.encodeToString(rules).toByteArray()
        require(bytes.size <= 1_048_576) { "Özgün kaynaklarla birlikte makro arşivi en çok 1 MB olabilir; mevcut kayıtlar korundu" }
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    fun deadlines() = prefs.all.filterKeys { it.startsWith("cooldown-") }.mapNotNull { (key,value) ->
        key.removePrefix("cooldown-").toLongOrNull()?.let { id -> (value as? Long)?.let { id to it } }
    }.toMap()
    fun deadline(id: Long, time: Long) { check(prefs.edit().putLong("cooldown-$id", time).commit()) }
    fun log(id: String, result: String) {
        // No action arguments, imported source text or user content in history.
        synchronized(lock) {
            val rows = prefs.getString("history", "").orEmpty().lines().filter { it.isNotBlank() }
            prefs.edit().putString("history", (listOf("${System.currentTimeMillis()} · ${id.take(8)} · ${result.take(200)}") + rows).take(30).joinToString("\n")).apply()
        }
    }
    fun history() = prefs.getString("history", "").orEmpty()
    companion object { private val lock = Any() }
}

class MacroRunner(private val context: Context, private val stopped: () -> Boolean = { Thread.currentThread().isInterrupted }) {
    private val store = MacroStore(context)
    private val cooldown = CooldownReservations(persist = store::deadline).apply { seed(store.deadlines()) }
    fun execute(id: String, manual: Boolean = false): String = synchronized(executionLock) {
        executeLocked(id, manual).also { store.log(id, it) }
    }
    private fun executeLocked(id: String, manual: Boolean): String {
        val rule = store.rules().firstOrNull { it.id == id } ?: return "Makro bulunamadı"
        val problems = MacroRules.validate(rule)
        if (problems.isNotEmpty()) return "İnceleme gerekli: ${problems.first()}"
        if (!manual && (!store.enabled || !rule.enabled)) return "Makro duraklatıldı"
        if (!manual && rule.trigger == "manual") return "Bu makro elle çalışır"
        if (stopped()) return "Acil durdurma etkin"
        // Permissions for every step are checked before reserving or performing any step.
        rule.actions.forEach { action -> permissionProblem(action)?.let { return it } }
        val key = UUID.nameUUIDFromBytes(rule.id.toByteArray()).mostSignificantBits
        cooldown.seed(store.deadlines())
        val admission = cooldown.reserve(key, rule.cooldown)
        if (!admission.accepted) return "Bekleme süresi: ${(admission.remainingMs + 999) / 1000} sn"
        var finished = 0
        try {
            rule.actions.forEach { action ->
                if (stopped()) error("Acil durdurma etkin")
                if (!manual && (!store.enabled || store.rules().none { it.id == id && it.enabled })) error("Makro duraklatıldı")
                when (action.kind) {
                    "volume" -> {
                        val audio = context.getSystemService(AudioManager::class.java)
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (max * action.value.toInt() / 100f).toInt(), 0)
                    }
                    "vibrate" -> @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                    "torch" -> {
                        val camera = context.getSystemService(CameraManager::class.java)
                        val id = camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: error("Fener bulunamadı")
                        camera.setTorchMode(id, action.value == "on")
                    }
                    "notify" -> {
                        val manager = context.getSystemService(NotificationManager::class.java)
                        manager.createNotificationChannel(NotificationChannel("hermes-macro-events", "Makro bildirimleri", NotificationManager.IMPORTANCE_DEFAULT))
                        manager.notify(id.hashCode(), NotificationCompat.Builder(context, "hermes-macro-events").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(rule.name).setContentText(action.value).setAutoCancel(true).build())
                    }
                }
                finished++
            }
            return "Tamamlandı · $finished eylem"
        } catch (e: Exception) {
            return "Durdu · $finished/${rule.actions.size} eylem; ${e.javaClass.simpleName}"
        }
    }
    fun permissionProblem(action: MacroAction): String? {
        if (action.kind == "torch" && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return "Fener için Kamera iznini uygulama ayarlarından açın"
        if (action.kind == "notify") {
            if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) return "Makro bildirimleri için bildirim izni gerekli"
            if (context.getSystemService(NotificationManager::class.java).getNotificationChannel("hermes-macro-events")?.importance == NotificationManager.IMPORTANCE_NONE) return "Makro bildirim kanalı kapalı; uygulama bildirim ayarlarından açın"
        }
        return null
    }
    companion object { private val executionLock = Any() }
}

/** A foreground service owns this bounded queue; broadcasts never run device work on main. */
internal class MacroEventQueue(
    capacity: Int = 16,
    private val onEvent: (String, () -> Boolean) -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val executor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(capacity),
        { work -> Thread(work, "hermes-macro-events").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy())
    private fun stopped() = closed.get() || Thread.currentThread().isInterrupted
    fun submit(trigger: String): Boolean {
        if (closed.get()) return false
        return try {
            executor.execute { if (!stopped()) onEvent(trigger, ::stopped) }
            true
        } catch (_: RejectedExecutionException) { false }
    }
    override fun close() {
        closed.set(true)
        executor.shutdownNow() // Remove pending events and signal the current runner before its next step.
    }
}

class MacroMonitorService : Service() {
    private var events: MacroEventQueue? = null
    private fun handleEvent(trigger: String, workerStopped: () -> Boolean) {
        val store = MacroStore(this)
        if (workerStopped() || !store.enabled) return
        runCatching {
            val runner = MacroRunner(this, stopped = workerStopped)
            for (rule in store.rules().filter { it.enabled && it.trigger == trigger }) {
                if (workerStopped()) break
                val result = runner.execute(rule.id)
                if (!result.startsWith("Tamamlandı") && !result.startsWith("Durdu")) store.log(rule.id, result)
            }
        }.onFailure {
            if (!workerStopped()) {
                store.enabled = false
                events?.close()
                stopSelf()
            }
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val trigger = when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> "charging"
                Intent.ACTION_POWER_DISCONNECTED -> "unplugged"
                Intent.ACTION_USER_PRESENT -> "unlocked"
                Intent.ACTION_HEADSET_PLUG -> if (intent.getIntExtra("state", 0) == 1) "headset" else return
                else -> return
            }
            if (isInitialStickyBroadcast) return
            val store = MacroStore(context)
            if (!store.enabled) return
            if (events?.submit(trigger) == false) store.log("monitor", "Olay kuyruğu dolu veya durduruldu; olay atlandı")
        }
    }
    override fun onCreate() {
        super.onCreate()
        events = MacroEventQueue(onEvent = ::handleEvent)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("hermes-macro-monitor", "Makrolar çalışıyor", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, javaClass).setAction("stop"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, "hermes-macro-monitor").setSmallIcon(android.R.drawable.ic_popup_sync).setContentTitle("Hermes · Makrolar").setContentText("Etkin kurallar cihaz olaylarını bekliyor").setOngoing(true).addAction(0, "Tümünü durdur", stop).build()
        startForeground(8719, notification)
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG); addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop" || !MacroStore(this).enabled) {
            events?.close()
            MacroStore(this).enabled = false; stopSelf(); return START_NOT_STICKY
        }
        return START_STICKY
    }
    override fun onDestroy() { events?.close(); unregisterReceiver(receiver); super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}
