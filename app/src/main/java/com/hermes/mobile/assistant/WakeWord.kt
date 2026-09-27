package com.hermes.mobile.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hermes.mobile.MainActivity
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * "Hey Jarvis" model dosyaları — openWakeWord v0.5.1 resmî sürümünden indirilir,
 * SHA256 ile doğrulanır (bozuk dosya motora gitmez). Toplam ~3,7 MB.
 * Lisans: openWakeWord kodu Apache-2.0; hazır modeller CC BY-NC-SA 4.0
 * (kişisel, ticari olmayan kullanım) — bu yüzden APK'ya gömülmez.
 */
object WakeWordModels {
    private const val BASE = "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1"

    data class F(val name: String, val sha256: String, val bytes: Long)

    val FILES = listOf(
        F("melspectrogram.tflite", "96fa0adccb6e8cf95cb14465409a1a2898ee4a96a85bb9ed3c7eb0e68bf163e8", 1_092_516L),
        F("embedding_model.tflite", "c0aea21eb84a4ce90a08c870da41b7a7173b45269e6a3207c71d67c40f3a59d8", 1_330_312L),
        F("hey_jarvis_v0.1.tflite", "14bff778604985e1b5c19f0f7bbe477a69cf281d8db34b232b3b972411f710e2", 1_278_912L),
    )

    fun dir(context: Context) = File(context.filesDir, "wakeword")

    fun ready(context: Context): Boolean =
        FILES.all { File(dir(context), it.name).let { f -> f.exists() && f.length() == it.bytes } }

    /** Eksikleri indirir; hash tutmazsa siler ve hata fırlatır. */
    suspend fun ensure(context: Context) = withContext(Dispatchers.IO) {
        val d = dir(context).apply { mkdirs() }
        val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        for (f in FILES) {
            val target = File(d, f.name)
            if (target.exists() && target.length() == f.bytes && sha(target) == f.sha256) continue
            val tmp = File(d, f.name + ".part")
            http.newCall(Request.Builder().url("$BASE/${f.name}").build()).execute().use { res ->
                if (!res.isSuccessful) throw IOException("İndirme HTTP ${res.code}: ${f.name}")
                res.body?.byteStream()?.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                    ?: throw IOException("Boş yanıt: ${f.name}")
            }
            if (sha(tmp) != f.sha256) {
                tmp.delete()
                throw IOException("Doğrulama tutmadı: ${f.name}")
            }
            if (!tmp.renameTo(target)) throw IOException("Adlandırılamadı: ${f.name}")
        }
        DiagLog.i("wakeword", "model hazır")
    }

    private fun sha(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { inp ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = inp.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * openWakeWord akışı (TFLite) — bu ortamda Python'da birebir aynısı ile
 * doğrulandı (tools/wakeword/oww_manual.py): 1280 örnek → mel (8×32, x/10+2)
 * → son 76 mel → gömme (96) → son 16 gömme → "hey jarvis" puanı (0..1).
 */
class WakeWordEngine(context: Context) : AutoCloseable {
    private val d = WakeWordModels.dir(context)
    private val opts = Interpreter.Options().setNumThreads(1)
    private val mel = Interpreter(File(d, "melspectrogram.tflite"), opts)
    private val emb = Interpreter(File(d, "embedding_model.tflite"), opts)
    private val ww = Interpreter(File(d, "hey_jarvis_v0.1.tflite"), opts)

    private val window = FloatArray(WakeWordLogic.MEL_WINDOW)
    private var filled = 0
    private val mels = Array(WakeWordLogic.EMB_WINDOW) { FloatArray(WakeWordLogic.MEL_BINS) { 1f } }
    private val feats = Array(WakeWordLogic.FEATURE_FRAMES) { FloatArray(WakeWordLogic.EMB_DIM) }
    private var frames = 0

    private fun buf(floats: Int): ByteBuffer = ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder())
    private val melIn = buf(WakeWordLogic.MEL_WINDOW)
    private val melOut = buf(WakeWordLogic.MEL_FRAMES_PER_CHUNK * WakeWordLogic.MEL_BINS)
    private val embIn = buf(WakeWordLogic.EMB_WINDOW * WakeWordLogic.MEL_BINS)
    private val embOut = buf(WakeWordLogic.EMB_DIM)
    private val wwIn = buf(WakeWordLogic.FEATURE_FRAMES * WakeWordLogic.EMB_DIM)
    private val wwOut = buf(1)

    init {
        mel.resizeInput(0, intArrayOf(1, WakeWordLogic.MEL_WINDOW), true)
        mel.allocateTensors()
        emb.resizeInput(0, intArrayOf(1, WakeWordLogic.EMB_WINDOW, WakeWordLogic.MEL_BINS, 1), true)
        emb.allocateTensors()
        ww.allocateTensors()
    }

    fun reset() {
        window.fill(0f); filled = 0; frames = 0
        mels.forEach { it.fill(1f) }
        feats.forEach { it.fill(0f) }
    }

    /** Bir 80 ms parçası işler, puanı döner (ısınma sırasında 0). */
    fun process(chunk: ShortArray): Float {
        filled = WakeWordLogic.slide(window, filled, chunk)
        if (filled < WakeWordLogic.MEL_WINDOW) return 0f

        melIn.rewind(); window.forEach { melIn.putFloat(it) }; melIn.rewind()
        melOut.rewind(); mel.run(melIn, melOut); melOut.rewind()
        val rows = List(WakeWordLogic.MEL_FRAMES_PER_CHUNK) {
            FloatArray(WakeWordLogic.MEL_BINS) { WakeWordLogic.melTransform(melOut.getFloat()) }
        }
        WakeWordLogic.pushRows(mels, rows)

        embIn.rewind(); mels.forEach { r -> r.forEach { embIn.putFloat(it) } }; embIn.rewind()
        embOut.rewind(); emb.run(embIn, embOut); embOut.rewind()
        WakeWordLogic.pushRows(feats, listOf(FloatArray(WakeWordLogic.EMB_DIM) { embOut.getFloat() }))
        frames++

        wwIn.rewind(); feats.forEach { r -> r.forEach { wwIn.putFloat(it) } }; wwIn.rewind()
        wwOut.rewind(); ww.run(wwIn, wwOut); wwOut.rewind()
        val score = wwOut.getFloat()
        return if (frames >= WakeWordLogic.WARMUP_FRAMES) score else 0f
    }

    override fun close() {
        runCatching { mel.close() }; runCatching { emb.close() }; runCatching { ww.close() }
    }
}

/** Jarvis paneli açıkken mikrofonu bırak (tanıyıcı kullanacak), kapanınca geri al. */
object WakeWordControl {
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()
    fun pause() { _paused.value = true }
    fun resume() { _paused.value = false }

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    internal fun setRunning(v: Boolean) { _running.value = v }
}

/**
 * "Hey Jarvis" dinleme servisi — mikrofon türünde ön plan servisi (kalıcı
 * bildirim; gizli dinleme yok). Ses telefonda işlenir, hiçbir yere gitmez.
 * Algılayınca: Hermes varsayılan asistansa Jarvis paneli, değilse bildirim.
 */
class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val n = notification()
        if (Build.VERSION.SDK_INT >= 30) startForeground(FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(FG_ID, n)
        if (loop?.isActive != true) loop = scope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            DiagLog.w("wakeword", "mikrofon izni yok — durdu"); stopSelf(); return
        }
        if (!WakeWordModels.ready(this)) {
            runCatching { WakeWordModels.ensure(this) }.onFailure {
                DiagLog.w("wakeword", "model indirilemedi: ${it.message}"); stopSelf(); return
            }
        }
        val engine = runCatching { WakeWordEngine(this) }.getOrElse {
            DiagLog.e("wakeword", "motor açılamadı", it); stopSelf(); return
        }
        WakeWordControl.setRunning(true)
        val sens = WakeWordLogic.Sensitivity.fromId(SettingsStore(this).settings.value.wakeWordSensitivity)
        val detector = WakeWordLogic.Detector(sens.threshold, sens.consecutive)
        val chunk = ShortArray(WakeWordLogic.CHUNK)
        try {
            while (scope.isActive) {
                if (WakeWordControl.paused.value) { delay(300); continue }
                val rec = openRecorder()
                if (rec == null) { delay(2_000); continue }
                engine.reset(); detector.reset()
                try {
                    rec.startRecording()
                    while (scope.isActive && !WakeWordControl.paused.value) {
                        var got = 0
                        while (got < chunk.size) {
                            val r = rec.read(chunk, got, chunk.size - got)
                            if (r <= 0) break
                            got += r
                        }
                        if (got < chunk.size) break
                        if (detector.feed(engine.process(chunk))) {
                            DiagLog.i("wakeword", "Hey Jarvis algılandı")
                            WakeWordControl.pause()
                            withContext(Dispatchers.Main) { onWake() }
                        }
                    }
                } finally {
                    runCatching { rec.stop() }
                    rec.release()
                }
            }
        } finally {
            engine.close()
            WakeWordControl.setRunning(false)
        }
    }

    private fun openRecorder(): AudioRecord? = runCatching {
        val min = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, 16_000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(min, WakeWordLogic.CHUNK * 2 * 4),
        ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
    }.getOrNull()

    private fun onWake() {
        if (HermesVoiceInteractionService.show()) return
        // Varsayılan asistan değilsek arka plandan ekran açamayız: dokunulacak bildirim.
        WakeWordControl.resume()
        val pi = PendingIntent.getActivity(
            this, 7801,
            Intent(this, MainActivity::class.java).putExtra("hermes_action", "jarvis")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        getSystemService(NotificationManager::class.java).notify(
            7802,
            NotificationCompat.Builder(this, CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Jarvis")
                .setContentText("Buradayım — konuşmak için dokun (Hermes'i varsayılan asistan yaparsan doğrudan açılır)")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }

    override fun onDestroy() {
        scope.cancel()
        WakeWordControl.setRunning(false)
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Hey Jarvis dinleme", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERT, "Jarvis çağrısı", NotificationManager.IMPORTANCE_HIGH))
        val stop = PendingIntent.getService(
            this, 7803, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("\"Hey Jarvis\" dinleniyor")
            .setContentText("Ses telefonda işlenir, hiçbir yere gönderilmez.")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Kapat", stop)
            .build()
    }

    companion object {
        private const val CHANNEL = "hermes-wakeword"
        private const val CHANNEL_ALERT = "hermes-wakeword-alert"
        private const val FG_ID = 7800
        const val ACTION_STOP = "com.hermes.mobile.WAKEWORD_STOP"

        /** Uygulama ÖNDEYKEN çağrılmalı (Android 14+: mikrofon servisi arka plandan başlatılamaz). */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java)) }
                .onFailure { DiagLog.w("wakeword", "başlatılamadı: ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WakeWordService::class.java)) }
        }
    }
}
