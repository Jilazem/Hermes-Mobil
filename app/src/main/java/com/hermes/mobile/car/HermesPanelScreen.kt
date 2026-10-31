package com.hermes.mobile.car

import android.app.Presentation
import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.ServerProfileStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Hermes ekranı (onaysız)" — Mola tarzı tam ekran panel.
 *
 * MediaProjection KULLANMAZ, izin istemez: araç host'unun verdiği serbest
 * çizim yüzeyine [DisplayManager.createVirtualDisplay] ile sanal bir ekran
 * açar, o ekranın bağlamında [Presentation] penceresi kurar ve Hermes
 * arayüzünü doğrudan oraya basar. Telefonun kendi ekranı okunmadığı için
 * ekran kaydı izni gerekmez; [MirrorScreen] consent'li yedek yol olarak kalır.
 *
 * Yaşam döngüsü: yüzey yokken / ekran dormant iken sanal ekran ve
 * Presentation bırakılır ([release]); yüzey geri gelince ya da ekran öne
 * gelince yeniden kurulur.
 */
class HermesPanelScreen(carContext: CarContext) :
    Screen(carContext),
    SurfaceCallback,
    DefaultLifecycleObserver {

    private var container: SurfaceContainer? = null
    private var shownFor: SurfaceContainer? = null
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var host: PanelHost? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        runCatching { carContext.getCarService(AppManager::class.java).setSurfaceCallback(this) }
            .onFailure { DiagLog.e(TAG, "setSurfaceCallback başarısız: ${it.message}") }
    }

    override fun onStart(owner: LifecycleOwner) {
        // Dormant moddan dönüş: yüzey hâlâ elimizdeyse ekranı yeniden kur.
        container?.let { ensureShown(it) }
    }

    override fun onStop(owner: LifecycleOwner) {
        release()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        release()
        runCatching { carContext.getCarService(AppManager::class.java).setSurfaceCallback(null) }
    }

    // ── Yüzey ───────────────────────────────────────────────────────────

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        this.container = container
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) ensureShown(container)
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        // N3: host yeni bir yüzey verdikten sonra eski yüzeyin "destroyed"
        // çağrısını geç verebilir. Gelen container sakladığımızla aynı
        // değilse (nesne ya da Surface kimliği) canlı paneli yıkma.
        val stored = this.container
        val storedSurface = stored?.surface
        val incomingSurface = container.surface
        val same = stored != null && (
            stored === container ||
                (storedSurface != null && incomingSurface != null && storedSurface === incomingSurface)
            )
        if (!same) return
        this.container = null
        release()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) = Unit

    override fun onStableAreaChanged(stableArea: Rect) = Unit

    // Panel statik: araç host'u kaydırmayı yutar, dokunuşu biz işlemiyoruz.
    override fun onClick(x: Float, y: Float) = Unit

    override fun onScroll(distanceX: Float, distanceY: Float) = Unit

    override fun onFling(velocityX: Float, velocityY: Float) = Unit

    /** Sanal ekranı kurar; aynı yüzey için ikinci kez kurmaz. */
    private fun ensureShown(c: SurfaceContainer) {
        // N2: dormanstan dönüşte host aynı SurfaceContainer'ı geri verebilir;
        // yüzey bu arada geçersizleşmişse createVirtualDisplay patlar ve panel
        // bir sonraki onSurfaceAvailable'a kadar boş kalırdı. Geçersizse
        // container'ı bırak ve yeni yüzeyi bekle.
        val surface = c.surface
        if (surface == null || !surface.isValid) {
            this.container = null
            return
        }
        if (c.width <= 0 || c.height <= 0) return
        if (presentation != null && shownFor === c) return

        release()

        // N1: kaynakları yerelde kur. show() (ör. BadTokenException) ya da
        // önceki bir adım istisna atarsa alanlar henüz atanmamış olacağı için
        // release() onları göremez; bu yüzden catch yereldeki kaynakları
        // kendisi bırakır.
        var vd: VirtualDisplay? = null
        var panel: Presentation? = null
        var panelHost: PanelHost? = null
        try {
            val dm = carContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                ?: error("DisplayManager yok")
            vd = dm.createVirtualDisplay(
                TAG,
                c.width,
                c.height,
                if (c.dpi > 0) c.dpi else 160,
                surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            ) ?: error("createVirtualDisplay null döndü")

            val displayContext = carContext.createDisplayContext(vd.display)
            panel = Presentation(displayContext, vd.display).apply { setCancelable(false) }
            panelHost = PanelHost(displayContext, carContext)
            val view = runCatching { panelHost.start() }.getOrElse { err ->
                DiagLog.w(TAG, "Compose panel kurulamadı, View paneline düşüldü: ${err.message}")
                panelHost.startFallback()
            }

            panel.setContentView(view)

            // N1: alanları show() çağrısından ÖNCE ata; show() patlarsa catch
            // alanları da sıfırlayıp yereldeki kaynakları bırakır.
            display = vd
            presentation = panel
            host = panelHost
            shownFor = c

            panel.show()
            DiagLog.i(TAG, "Sanal ekran kuruldu ${c.width}x${c.height} dpi=${c.dpi}")
        } catch (t: Throwable) {
            runCatching { panel?.dismiss() }
            runCatching { panelHost?.stop() }
            runCatching { vd?.release() }
            display = null
            presentation = null
            host = null
            shownFor = null
            DiagLog.e(TAG, "Sanal ekran kurulamadı: ${t.message}")
        }
    }

    /** Sanal ekranı ve Presentation'ı bırakır (yüzey yok / dormant). */
    private fun release() {
        runCatching { presentation?.dismiss() }
        presentation = null
        host?.let { h -> runCatching { h.stop() } }
        host = null
        runCatching { display?.release() }
        display = null
        shownFor = null
    }

    // ── Şablon ──────────────────────────────────────────────────────────

    override fun onGetTemplate(): Template =
        NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Geri")
                            .setOnClickListener { screenManager.pop() }
                            .build()
                    )
                    .build()
            )
            .setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            .setPanModeListener { /* panel statik; harita yok, sürükleme yutulur */ }
            .build()

    companion object {
        private const val TAG = "car-panel"
    }
}

/**
 * Presentation içinde Compose barındıran sahip.
 *
 * Presentation penceresi bir Activity değildir; ComposeView
 * ViewTreeLifecycleOwner/SavedStateRegistryOwner/ViewModelStoreOwner bekler.
 * HermesAssistSession'daki çalışan desenin aynısı burada kuruluyor.
 */
private class PanelHost(
    private val context: Context,
    private val carContext: CarContext,
) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val state = MutableStateFlow(PanelState())

    fun start(): View {
        saved.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        loadProfile()
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@PanelHost)
            setViewTreeSavedStateRegistryOwner(this@PanelHost)
            setViewTreeViewModelStoreOwner(this@PanelHost)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val st by state.collectAsState()
                HermesPanel(st)
            }
        }
    }

    /** Compose kurulamazsa: aynı bilgileri düz View ile bas. */
    fun startFallback(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(PanelColors.Background.toArgbInt())
        }
        fun line(text: String, size: Float, bold: Boolean = false) {
            root.addView(
                TextView(context).apply {
                    this.text = text
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size)
                    setTextColor(PanelColors.TextPrimary.toArgbInt())
                    if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                },
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        line("HERMES · araç paneli", 30f, true)
        line("Sunucu: ${state.value.server ?: "—"}", 18f)
        line("Adres: ${state.value.url ?: "—"}", 18f)
        line("Onaysız tam ekran · MediaProjection yok", 14f)
        return root
    }

    private fun loadProfile() {
        scope.launch {
            val profile = withContext(Dispatchers.IO) {
                runCatching { ServerProfileStore(carContext).active() }.getOrNull()
            }
            state.update {
                it.copy(
                    server = profile?.name,
                    url = profile?.baseUrl,
                    hasToken = profile?.token?.isNotBlank() == true,
                    loaded = true,
                )
            }
        }
    }

    fun stop() {
        runCatching { registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
        runCatching { registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        runCatching { registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY) }
        scope.cancel()
        store.clear()
    }
}

private data class PanelState(
    val server: String? = null,
    val url: String? = null,
    val hasToken: Boolean = false,
    val loaded: Boolean = false,
)

/** Panel renkleri — ana uygulama temasından bağımsız (Presentation'da tema yok). */
private object PanelColors {
    val Background = Color(0xFF0B0F14)
    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFFB7C3CF)
    val TextMuted = Color(0xFF8A97A5)
    val TextFaint = Color(0xFF6B7785)
    val Online = Color(0xFF3FB950)
    val Busy = Color(0xFFD29922)
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255f).toInt(),
    (red * 255f).toInt(),
    (green * 255f).toInt(),
    (blue * 255f).toInt(),
)

/** Panel gövdesi — sürüşte okunur: kısa satırlar, tek ekran. */
@Composable
private fun HermesPanel(st: PanelState) {
    val clock = remember { mutableStateOf("--:--:--") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale("tr", "TR"))
        while (true) {
            clock.value = fmt.format(Date())
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PanelColors.Background)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("HERMES", color = PanelColors.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text("araç paneli", color = PanelColors.TextMuted, fontSize = 16.sp)
        }
        Text(
            if (st.loaded) "Sunucu: ${st.server ?: "profil yok"}" else "Sunucu profili okunuyor…",
            color = PanelColors.TextSecondary,
            fontSize = 20.sp,
        )
        Text(
            st.url ?: "Adres: —",
            color = PanelColors.TextMuted,
            fontSize = 18.sp,
        )
        Text(
            if (st.hasToken) "Anahtar: hazır" else "Anahtar: eksik — telefonda Ayarlar → Sunucu",
            color = if (st.hasToken) PanelColors.Online else PanelColors.Busy,
            fontSize = 18.sp,
        )
        Text("Saat: ${clock.value}", color = PanelColors.TextMuted, fontSize = 18.sp)
        Text(
            "Onaysız tam ekran · MediaProjection kullanılmıyor",
            color = PanelColors.TextFaint,
            fontSize = 14.sp,
        )
    }
}
