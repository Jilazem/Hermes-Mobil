package com.hermes.mobile.car

import android.content.Intent
import android.graphics.Rect
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.hermes.mobile.MainActivity
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.FullControl
import com.hermes.mobile.data.HermesAccessibilityService
import com.hermes.mobile.data.ScreenMirror
import com.hermes.mobile.data.SettingsStore

/**
 * "Hermes Ekran" — telefon ekranını Android Auto'ya yansıtır, dokunuşu geri taşır.
 *
 * Android Auto serbest çizim yüzeyini (Surface) yalnız NAVİGASYON kategorisine
 * veriyor; bu yüzden [HermesCarService] navigasyon kategorisinde ve açılış
 * ekranı bu. Görüntü
 * [ScreenMirror] (MediaProjection) ile doğrudan yüzeye akar; araçtaki dokunma,
 * kaydırma ve savurma [HermesAccessibilityService] ile telefona jest olarak
 * uygulanır (Tam kontrol gerekir; yoksa yalnız izlenir).
 *
 * Güvenlik: "Sürerken durdur" açıkken (varsayılan) araç hızı ~5 km/sa'yı
 * geçince görüntü kesilir; yalnız park hâlinde yansıtılır. Yan yüklenen
 * uygulama olduğu için Android Auto geliştirici ayarlarında "Bilinmeyen
 * kaynaklar" açık olmalı.
 */
class MirrorScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val settings = SettingsStore(carContext)
    private var surface: SurfaceContainer? = null
    private var speedMps: Float? = null
    private var paused = false
    private var scrollX = 0f
    private var scrollY = 0f
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val flushScroll = Runnable { applyScroll() }

    private val speedListener = OnCarDataAvailableListener<Speed> { s ->
        speedMps = s.displaySpeedMetersPerSecond.value ?: s.rawSpeedMetersPerSecond.value
        updatePause()
    }

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(container: SurfaceContainer) {
            surface = container
            bind()
        }

        override fun onSurfaceDestroyed(container: SurfaceContainer) {
            surface = null
            ScreenMirror.detach()
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) = Unit
        override fun onStableAreaChanged(stableArea: Rect) = Unit

        override fun onClick(x: Float, y: Float) {
            val c = surface ?: return
            val a11y = control() ?: return
            val (pw, ph) = ScreenMirror.phoneSize(carContext)
            MirrorLogic.carToPhone(x, y, c.width, c.height, pw, ph)?.let { (px, py) -> a11y.mirrorTap(px, py) }
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            // Kaydırma olayları sık gelir; 120 ms sessizlikte tek bir jest.
            scrollX += distanceX
            scrollY += distanceY
            handler.removeCallbacks(flushScroll)
            handler.postDelayed(flushScroll, 120)
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            handler.removeCallbacks(flushScroll)
            scrollX = 0f; scrollY = 0f
            val a11y = control() ?: return
            val (pw, ph) = ScreenMirror.phoneSize(carContext)
            MirrorLogic.flingSwipe(velocityX, velocityY, pw, ph)?.let { a11y.mirrorSwipe(it, 180) }
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
        // CAR_SPEED çalışma zamanı izni: yoksa araçtan iste (telefonda diyalog
        // çıkar). İzin yokken hız hiç gelmez → sürüş koruması sessizce kapalı
        // kalırdı (MirrorMobile'ın PermissionScreen dersi).
        if (ContextCompat.checkSelfPermission(carContext, CAR_SPEED) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            registerSpeed()
        } else {
            runCatching {
                carContext.requestPermissions(listOf(CAR_SPEED)) { granted, _ ->
                    if (CAR_SPEED in granted) registerSpeed()
                }
            }.onFailure { DiagLog.w("mirror", "hız izni istenemedi: ${it.message}") }
        }
        // Telefonda izin verilince/durdurulunca araç ekranı hemen güncellensin.
        owner.lifecycleScope.launch {
            ScreenMirror.status.collect {
                bind()
                invalidate()
            }
        }
    }

    private fun registerSpeed() {
        runCatching {
            carContext.getCarService(CarHardwareManager::class.java).carInfo
                .addSpeedListener(ContextCompat.getMainExecutor(carContext), speedListener)
        }.onFailure { DiagLog.w("mirror", "hız verisi alınamadı: ${it.message}") }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        runCatching {
            carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(speedListener)
        }
        handler.removeCallbacks(flushScroll)
        ScreenMirror.detach()
    }

    private fun control(): HermesAccessibilityService? {
        val svc = HermesAccessibilityService.instanceOrNull()
        if (svc == null) {
            CarToast.makeText(carContext, "Dokunmak için telefonda Tam kontrol'ü aç", CarToast.LENGTH_SHORT).show()
        }
        return svc
    }

    private fun applyScroll() {
        val c = surface ?: return
        val (pw, ph) = ScreenMirror.phoneSize(carContext)
        val swipe = MirrorLogic.scrollSwipe(scrollX, scrollY, c.width, c.height, pw, ph)
        scrollX = 0f; scrollY = 0f
        swipe?.let { control()?.mirrorSwipe(it) }
    }

    private fun bind() {
        val c = surface ?: return
        val s = c.surface ?: return
        if (paused) return
        if (ScreenMirror.attach(s, c.width, c.height, c.dpi)) invalidate()
    }

    private fun updatePause() {
        val guard = settings.settings.value.mirrorPauseWhileDriving
        val now = MirrorLogic.pausedForDriving(guard, speedMps)
        if (now == paused) return
        paused = now
        if (paused) ScreenMirror.detach() else bind()
        invalidate()
    }

    override fun onGetTemplate(): Template {
        if (!ScreenMirror.hasPermission) {
            return MessageTemplate.Builder(
                "Telefon ekranını burada görmek için \"Başlat\"a bas; telefonda çıkan pencerede " +
                    "\"Tüm ekran\"ı seçip izin ver (telefonun kilidi açık olmalı).",
            )
                .setTitle("Hermes Ekran")
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder().setTitle("Başlat").setOnClickListener {
                        val direct = ScreenMirror.requestFromCar(carContext)
                        CarToast.makeText(
                            carContext,
                            if (direct) "Telefonda izin ver" else "Telefondaki bildirime dokun",
                            CarToast.LENGTH_LONG,
                        ).show()
                    }.build(),
                )
                .addAction(
                    Action.Builder().setTitle("Durum").setOnClickListener {
                        screenManager.push(HermesCarScreen(carContext))
                    }.build(),
                )
                .build()
        }
        if (paused) {
            return MessageTemplate.Builder("Güvenlik için sürüş sırasında ekran yansıtma durduruldu. Araç durunca geri gelir.")
                .setTitle("Hermes Ekran")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }
        val actions = ActionStrip.Builder()
            .addAction(Action.Builder().setTitle("Geri").setOnClickListener {
                control()?.global(FullControl.GlobalKind.BACK)
            }.build())
            .addAction(Action.Builder().setTitle("Ana").setOnClickListener {
                control()?.global(FullControl.GlobalKind.HOME)
            }.build())
            .addAction(Action.Builder().setTitle("Hermes").setOnClickListener {
                // Telefonda Hermes'i öne getir — yansıtmada görünür.
                runCatching {
                    carContext.startActivity(
                        Intent(carContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }.build())
            .addAction(Action.Builder().setTitle("Durum").setOnClickListener {
                screenManager.push(HermesCarScreen(carContext))
            }.build())
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(actions)
            // Kaydırma olayları yalnız "kaydırma kipi"nde gelir: PAN düğmesi.
            .setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            .setPanModeListener { }
            .build()
    }
}

private const val CAR_SPEED = "com.google.android.gms.permission.CAR_SPEED"
