package com.hermes.mobile.data

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Telefon ekranını Android Auto'ya yansıtma — ekran yakalama (MediaProjection)
 * tarafı.
 *
 * Akış: [MirrorConsentActivity] sistemin "ekranı paylaş" iznini ister →
 * [MirrorProjectionService] (mediaProjection türünde ön plan servisi) izni
 * canlı tutar → araç ekranı açılınca [attach] telefonun görüntüsünü doğrudan
 * aracın çizim yüzeyine (Surface) aktarır; kopya/kodlama yok, gecikme düşük.
 *
 * Android 14+: bir izinle YALNIZ BİR sanal ekran açılabilir. Bu yüzden sanal
 * ekran bir kez kurulur; araç yüzeyi değişince [VirtualDisplay.setSurface] +
 * [VirtualDisplay.resize] ile yeniden bağlanır. İzin kullanıcı ya da sistem
 * tarafından durdurulursa her şey bırakılır ve yeniden izin gerekir.
 */
object ScreenMirror {

    enum class Status { NoPermission, Ready, Mirroring }

    private val _status = MutableStateFlow(Status.NoPermission)
    val status: StateFlow<Status> = _status.asStateFlow()

    @Volatile private var projection: MediaProjection? = null
    @Volatile private var display: VirtualDisplay? = null
    private val main = Handler(Looper.getMainLooper())

    val hasPermission: Boolean get() = projection != null

    internal fun onProjection(p: MediaProjection) {
        release()
        // Android 14+: sanal ekrandan ÖNCE geri çağrı kaydı zorunlu.
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                DiagLog.i("mirror", "ekran paylaşımı durduruldu (sistem/kullanıcı)")
                display?.release()
                display = null
                projection = null
                _status.value = Status.NoPermission
            }
        }, main)
        projection = p
        _status.value = Status.Ready
    }

    /** Araç yüzeyine bağlan. İzin yoksa false. */
    fun attach(surface: Surface, width: Int, height: Int, dpi: Int): Boolean {
        val p = projection ?: return false
        val vd = display
        runCatching {
            if (vd == null) {
                display = p.createVirtualDisplay(
                    "HermesAuto", width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface, null, main,
                )
            } else {
                vd.resize(width, height, dpi)
                vd.surface = surface
            }
        }.onFailure {
            DiagLog.e("mirror", "sanal ekran kurulamadı", it)
            return false
        }
        _status.value = Status.Mirroring
        DiagLog.i("mirror", "yansıtma başladı ${width}x$height@$dpi")
        return true
    }

    /** Araç yüzeyi gitti ya da sürüş nedeniyle duraklatıldı — izin korunur. */
    fun detach() {
        runCatching { display?.surface = null }
        if (projection != null) _status.value = Status.Ready
    }

    /** Her şeyi bırak (Durdur düğmesi ya da yeni izin). */
    fun release() {
        runCatching { display?.release() }
        display = null
        runCatching { projection?.stop() }
        projection = null
        _status.value = Status.NoPermission
    }

    /** Telefon ekranının GERÇEK boyutu (şu anki dönüşle). */
    @Suppress("DEPRECATION")
    fun phoneSize(context: Context): Pair<Int, Int> {
        val dm = context.getSystemService(DisplayManager::class.java)
        val m = DisplayMetrics()
        dm.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(m)
        return m.widthPixels to m.heightPixels
    }

    /**
     * Araçtaki "Başlat": telefonda izin penceresini DOĞRUDAN açar (Car App
     * Library araç bağlamından telefon ekranında etkinlik başlatmaya izin
     * veriyor — MirrorMobile'ın yolu). Olmazsa bildirime düşer.
     */
    fun requestFromCar(carContext: Context): Boolean {
        val opened = runCatching {
            carContext.startActivity(
                Intent(carContext, MirrorConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.ActivityOptions.makeBasic()
                    .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                    .toBundle(),
            )
        }.isSuccess
        if (!opened) postConsentNotification(carContext)
        return opened
    }

    /** Telefonda izin ekranını açan bildirim (araçtan "izin iste" dendiğinde). */
    fun postConsentNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(MirrorProjectionService.CHANNEL, "Android Auto yansıtma", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val pi = PendingIntent.getActivity(
            context, 7710,
            Intent(context, MirrorConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(
            7710,
            NotificationCompat.Builder(context, MirrorProjectionService.CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Ekranı araca yansıt")
                .setContentText("Dokun → \"Tüm ekran\"ı seçip izin ver")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }
}

/** Sistemin ekran paylaşma iznini isteyen görünmez ekran. */
class MirrorConsentActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) {
            val i = Intent(this, MirrorProjectionService::class.java)
                .putExtra(MirrorProjectionService.EXTRA_CODE, r.resultCode)
                .putExtra(MirrorProjectionService.EXTRA_DATA, r.data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } else {
            DiagLog.w("mirror", "ekran paylaşım izni verilmedi")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        launcher.launch(mpm.createScreenCaptureIntent())
    }
}

/**
 * Ekran paylaşım iznini canlı tutan ön plan servisi. Android 10+ ekran
 * yakalama yalnız "mediaProjection" türünde bir ön plan servisi açıkken
 * mümkün; açıkken kalıcı bildirim durur (gizli yakalama yok). Yansıtma
 * sürerken ekran kararmasın diye ekran-açık kilidi tutar.
 */
class MirrorProjectionService : Service() {

    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ScreenMirror.release()
            stopSelf()
            return START_NOT_STICKY
        }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Sıra zorunlu (Android 14+): önce mediaProjection türünde ön plan,
        // SONRA getMediaProjection.
        val n = notification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(FG_ID, n)
        }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = runCatching { mpm.getMediaProjection(code, data) }.getOrNull()
        if (p == null) {
            DiagLog.w("mirror", "MediaProjection alınamadı")
            stopSelf()
            return START_NOT_STICKY
        }
        ScreenMirror.onProjection(p)
        @Suppress("DEPRECATION")
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE, "hermes:mirror")
            .apply { acquire(4 * 60 * 60 * 1000L) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { wake?.takeIf { it.isHeld }?.release() }
        ScreenMirror.release()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Android Auto yansıtma", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(
            this, 7711,
            Intent(this, MirrorProjectionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Ekran Android Auto'ya yansıtılabilir")
            .setContentText("Araçta \"Hermes Ekran\"ı aç. Durdurmak için dokun.")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Durdur", stop)
            .build()
    }

    companion object {
        const val CHANNEL = "hermes-mirror"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "com.hermes.mobile.MIRROR_STOP"
        private const val FG_ID = 7712

        fun start(context: Context) {
            context.startActivity(
                Intent(context, MirrorConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MirrorProjectionService::class.java).setAction(ACTION_STOP))
        }
    }
}
