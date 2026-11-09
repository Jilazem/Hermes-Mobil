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
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.hermes.mobile.data.SettingsStore
import com.hermes.mobile.ui.S
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Hermes'in kendi araç paneli — CarAppService yüzeyine kendi içeriğini tam ekran çizer.
 *
 * Tam ekran sürüş kipinde (`molaFullScreenDriving = true`, varsayılan AÇIK):
 * YouTube, telefon yansıtma, oyun ve diğer dikkat dağıtıcı öğeler gizlenir;
 * Mola arayüzü Android Auto ekranında tam ekran açılır ve sürüş sırasında
 * kesintisiz çalışır.
 */
class HermesPanelScreen(
    carContext: CarContext,
    private val selectedSessionId: String? = null,
    private val fullScreenDriving: Boolean = runCatching { SettingsStore(carContext).current().molaFullScreenDriving }.getOrDefault(true),
) :
    Screen(carContext),
    SurfaceCallback,
    DefaultLifecycleObserver {

    private var container: SurfaceContainer? = null
    private var shownFor: SurfaceContainer? = null
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var host: PanelHost? = null
    private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val actions = MolaSessionActions(carContext, actionScope) { line -> host?.showNotice(line); invalidate() }
    private val voice = CarVoiceSession(carContext) { line, _ ->
        host?.showNotice(line)
        android.os.Handler(android.os.Looper.getMainLooper()).post { invalidate() }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        runCatching { carContext.getCarService(AppManager::class.java).setSurfaceCallback(this) }
            .onFailure { DiagLog.e(TAG, "setSurfaceCallback başarısız: ${it.message}") }
    }

    override fun onStart(owner: LifecycleOwner) {
        container?.let { ensureShown(it) }
    }

    override fun onStop(owner: LifecycleOwner) {
        actions.silence()
        release()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        voice.release()
        actions.release()
        actionScope.cancel()
        release()
        runCatching { carContext.getCarService(AppManager::class.java).setSurfaceCallback(null) }
    }

    // ── Yüzey ───────────────────────────────────────────────────────────

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        this.container = container
        MolaHostObservation.update(MolaHostState.SurfaceAvailable)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) ensureShown(container)
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
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

    override fun onClick(x: Float, y: Float) = Unit

    override fun onScroll(distanceX: Float, distanceY: Float) = Unit

    override fun onFling(velocityX: Float, velocityY: Float) = Unit

    /** Sanal ekranı kurar; aynı yüzey için ikinci kez kurmaz. */
    private fun ensureShown(c: SurfaceContainer) {
        val surface = c.surface
        if (surface == null || !surface.isValid) {
            this.container = null
            return
        }
        if (c.width <= 0 || c.height <= 0) return
        if (presentation != null && shownFor === c) return

        release()

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
                PanelContentLogic.DISPLAY_FLAGS,
            ) ?: error("createVirtualDisplay null döndü")

            val displayContext = carContext.createDisplayContext(vd.display)
            panel = Presentation(displayContext, vd.display).apply { setCancelable(false) }
            panelHost = PanelHost(displayContext, carContext, selectedSessionId, fullScreenDriving) { invalidate() }
            val view = runCatching { panelHost.start() }.getOrElse { err ->
                DiagLog.w(TAG, "Compose panel kurulamadı, View paneline düşüldü: ${err.message}")
                panelHost.startFallback()
            }

            panel.setContentView(view)

            display = vd
            presentation = panel
            host = panelHost
            shownFor = c

            panel.show()
            MolaHostObservation.update(MolaHostState.Rendering)
            DiagLog.i(TAG, "Sanal ekran kuruldu ${c.width}x${c.height} dpi=${c.dpi} fullScreen=$fullScreenDriving")
        } catch (t: Throwable) {
            runCatching { panel?.dismiss() }
            runCatching { panelHost?.stop() }
            runCatching { vd?.release() }
            display = null
            presentation = null
            host = null
            shownFor = null
            MolaHostObservation.update(MolaHostState.Failed)
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
        MolaHostObservation.update(MolaHostState.Released)
    }

    // ── Şablon ──────────────────────────────────────────────────────────

    override fun onGetTemplate(): Template {
        val state = host?.currentState
        fun icon(resource: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, resource)).build()
        val strip = ActionStrip.Builder()
        if (fullScreenDriving && screenManager.screenStack.size <= 1) {
            strip.addAction(
                Action.Builder()
                    .setTitle(if (voice.busy) "Durdur" else "Konuş")
                    .setOnClickListener { voice.toggle() }
                    .build()
            )
            strip.addAction(
                Action.Builder()
                    .setTitle("Oturumlar")
                    .setOnClickListener { screenManager.push(HermesCarScreen(carContext)) }
                    .build()
            )
        } else {
            strip.addAction(
                Action.Builder()
                    .setTitle("Geri")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            strip.addAction(
                Action.Builder()
                    .setTitle(if (voice.busy) "Durdur" else "Konuş")
                    .setOnClickListener { voice.toggle() }
                    .build()
            )
        }
        if (!state?.lastAssistantMessage.isNullOrBlank()) strip.addAction(Action.Builder()
            .setIcon(icon(android.R.drawable.ic_lock_silent_mode_off))
            .setOnClickListener { actions.readLastAnswer(host?.currentState?.lastAssistantMessage) }.build())
        if (!state?.activeSessionId.isNullOrBlank() && !fullScreenDriving) strip.addAction(Action.Builder()
            .setIcon(icon(android.R.drawable.ic_media_pause))
            .setOnClickListener { host?.currentState?.let { actions.stopSelected(it.profileId, it.activeSessionId) } }.build())
        strip.addAction(Action.Builder().setIcon(icon(android.R.drawable.ic_popup_sync))
            .setOnClickListener { host?.refresh() }.build())
        return NavigationTemplate.Builder()
            .setActionStrip(strip.build())
            .setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            .setPanModeListener { /* panel statik; harita yok, sürükleme yutulur */ }
            .build()
    }

    companion object {
        private const val TAG = "car-panel"
    }
}

private class PanelHost(
    private val context: Context,
    private val carContext: CarContext,
    selectedSessionId: String?,
    private val fullScreenDriving: Boolean,
    private val changed: () -> Unit,
) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val state = MutableStateFlow(PanelState())
    private val source = HermesPanelDataSource(carContext, selectedSessionId)
    val currentState: PanelState get() = state.value
    fun showNotice(text: String) { state.value = state.value.copy(notice = text) }
    fun refresh() { scope.launch { state.value = source.load(); changed() } }
    private var started = false

    private fun ensureStarted() {
        if (started) return
        started = true
        saved.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        scope.launch {
            while (isActive) {
                val loaded = source.load()
                state.value = loaded.copy(notice = state.value.notice.takeIf { loaded.profileId == state.value.profileId })
                changed()
                delay(15_000)
            }
        }
    }

    fun start(): View {
        ensureStarted()
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@PanelHost)
            setViewTreeSavedStateRegistryOwner(this@PanelHost)
            setViewTreeViewModelStoreOwner(this@PanelHost)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val st by state.collectAsState()
                HermesPanel(st, fullScreenDriving = fullScreenDriving)
            }
        }
    }

    fun startFallback(): View {
        ensureStarted()
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
        line(if (fullScreenDriving) "HERMES · MOLA TAM EKRAN" else "HERMES · Mola", 30f, true)
        val details = TextView(context).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(PanelColors.TextSecondary.toArgbInt())
            maxLines = 7
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        root.addView(details)
        scope.launch {
            state.collect { st ->
                details.text = listOf(
                    st.server ?: "—", st.connection.name,
                    st.activeSession ?: "—", st.notice ?: st.lastAssistantMessage ?: st.lastMessage ?: "—",
                ).joinToString(10.toChar().toString())
            }
        }
        return root
    }

    fun stop() {
        runCatching { registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
        runCatching { registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        runCatching { registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY) }
        scope.cancel()
        store.clear()
    }
}

data class PanelState(
    val server: String? = null,
    val url: String? = null,
    val hasToken: Boolean = false,
    val loaded: Boolean = false,
    val activeSession: String? = null,
    val lastMessage: String? = null,
    val demo: Boolean = false,
    val connection: PanelConnection = PanelConnection.Loading,
    val messageUnavailable: Boolean = false,
    val selectedSession: Boolean = false,
    val profileId: String? = null,
    val activeSessionId: String? = null,
    val lastAssistantMessage: String? = null,
    val notice: String? = null,
)

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

/** Panel gövdesi — sürüşte okunur: kısa satırlar, tam ekran Mola görünümü. */
@Composable
fun HermesPanel(st: PanelState, fullScreenDriving: Boolean = true) {
    val clock = remember { mutableStateOf("--:--:--") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale("tr", "TR"))
        while (true) {
            clock.value = fmt.format(Date())
            delay(1000)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(PanelColors.Background)) {
        val compact = maxHeight < 350.dp
        val bodySize = if (compact) 16.sp else 21.sp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 20.dp else 32.dp, vertical = if (compact) 12.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("HERMES", color = PanelColors.TextPrimary, fontSize = if (compact) 26.sp else 34.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (fullScreenDriving) S.t2("Mola · Tam Ekran Sürüş", "Mola · Full-Screen Driving") else "Mola",
                    color = if (fullScreenDriving) PanelColors.Online else PanelColors.TextMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(clock.value, color = PanelColors.TextMuted, fontSize = if (compact) 14.sp else 18.sp)
                if (st.demo) Text(S.t2("Görsel test", "Visual test"), color = PanelColors.Busy, fontSize = 14.sp)
            }
            Text(
                listOfNotNull(
                    st.server ?: S.t2("Sunucu profili", "Server profile"),
                    when (st.connection) {
                        PanelConnection.Loading -> S.t2("Bağlanıyor…", "Connecting…")
                        PanelConnection.NoProfile -> S.t2("Telefonda sunucu profili ekleyin", "Add a server profile on your phone")
                        PanelConnection.NoKey -> S.t2("Sunucu anahtarı eksik", "Server token is missing")
                        PanelConnection.Connected -> S.t2("Sunucuya bağlı ✓", "Connected to server ✓")
                        PanelConnection.Unavailable -> S.t2("Sunucuya ulaşılamıyor · yeniden denenecek", "Server unavailable · retrying")
                    }
                ).joinToString(" · "),
                color = if (st.connection == PanelConnection.Connected) PanelColors.Online else PanelColors.Busy,
                fontSize = if (compact) 14.sp else 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (if (st.selectedSession) S.t2("Seçili oturum: ", "Selected session: ") else S.t2("Etkin oturum: ", "Active session: ")) +
                    (st.activeSession ?: "—"),
                color = PanelColors.TextSecondary,
                fontSize = bodySize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (st.messageUnavailable) S.t2("Mesajlar alınamadı", "Messages unavailable")
                else st.notice ?: st.lastAssistantMessage ?: st.lastMessage ?: S.t2("Konuş düğmesine dokunarak Hermes'e sesle sorabilirsin.", "Tap Speak to ask Hermes by voice."),
                color = PanelColors.TextPrimary,
                fontSize = if (compact) 15.sp else 18.sp,
                maxLines = if (compact) 3 else 6,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact && !fullScreenDriving) {
                Text(
                    S.t2("Hermes araç paneli · telefon ekranı kaydedilmez", "Hermes car panel · phone screen is not recorded"),
                    color = PanelColors.TextFaint,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
