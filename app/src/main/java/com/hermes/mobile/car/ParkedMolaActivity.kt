package com.hermes.mobile.car

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.hermes.mobile.MainActivity
import com.hermes.mobile.assistant.JarvisEngine
import com.hermes.mobile.assistant.JarvisPhase
import com.hermes.mobile.data.MirrorProjectionService
import com.hermes.mobile.data.ScreenMirror
import com.hermes.mobile.data.SettingsStore
import kotlinx.coroutines.*

/** Android 15+ Mola launcher. Supports Full-Screen Driving Mode (default ON, toggleable). */
class ParkedMolaActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var toolbar: LinearLayout
    private lateinit var body: FrameLayout
    private lateinit var status: TextView
    private var web: WebView? = null
    private var mirror: SurfaceView? = null
    private var sharing = false
    private var fullScreen: View? = null
    private var fullScreenCallback: WebChromeClient.CustomViewCallback? = null
    private var pendingEntry: String? = null
    private val profiles by lazy { MolaCarProfiles(this) }
    private var profile: MolaCarProfile? = null
    private var page = "home"
    private var profileJob: Job? = null
    private var voice: JarvisEngine? = null

    private fun isFullScreenDriving(): Boolean =
        runCatching { SettingsStore(this).current().molaFullScreenDriving }.getOrDefault(true)

    private fun setFullScreenDriving(enabled: Boolean) {
        runCatching { SettingsStore(this).update { it.copy(molaFullScreenDriving = enabled) } }
        applyChromeVisibility(enabled)
        home()
    }

    private fun applyChromeVisibility(fullScreenDriving: Boolean) {
        if (::toolbar.isInitialized) toolbar.visibility = if (fullScreenDriving) View.GONE else View.VISIBLE
        if (::status.isInitialized) status.visibility = if (fullScreenDriving) View.GONE else View.VISIBLE
    }

    private fun rememberPage(value: String) {
        page = value
        profile?.let { saved -> runCatching { profiles.setLastPage(saved.id, value) }; profile = saved.copy(lastPage = value) }
    }

    private fun loadCarProfile() {
        profileJob?.cancel()
        profileJob = scope.launch {
            profile = try {
                profiles.resolve(
                    MolaConnectedCars.read(this@ParkedMolaActivity),
                    learnCarAudio = intent.getBooleanExtra("hermes_car_entry", false) || intent.hasCategory("android.intent.category.CAR_LAUNCHER"),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (page != "home") rememberPage(page)
            if (page == "home" && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) home()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 15, 34))
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        toolbar = LinearLayout(this)
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label; setOnClickListener { action() }; minHeight = 56
        }
        toolbar.addView(button("Mola") { home() }, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(button("YouTube") { youtube() }, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(button("Yansıt") { startMirror() }, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(button("Oyun") { game() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(toolbar)
        status = TextView(this).apply { setTextColor(Color.rgb(180, 199, 226)); textSize = 14f; setPadding(12, 4, 12, 4) }
        root.addView(status)
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        androidx.core.view.ViewCompat.requestApplyInsets(root)
        scope.launch { ScreenMirror.status.collect { bindMirror() } }
        val fullScreenMode = isFullScreenDriving()
        applyChromeVisibility(fullScreenMode)
        if (!fullScreenMode && intent.component?.className?.endsWith(".YouTubeAlias") == true) youtube() else home()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullScreen != null) exitFullScreen()
                else if (web?.canGoBack() == true) web?.goBack()
                else if (web != null || mirror != null || body.getChildAt(0) is MolaBuddyGameView) home() else finish()
            }
        })
    }

    private fun clear() {
        exitFullScreen()
        web?.let { it.stopLoading(); it.onPause(); it.loadUrl("about:blank"); it.destroy() }; web = null
        mirror = null
        voice?.stop()
        if (sharing) { sharing = false; ScreenMirror.release(); runCatching { MirrorProjectionService.stop(this) } }
        body.removeAllViews()
    }

    private fun home() {
        clear()
        page = "home"
        val fullScreenMode = isFullScreenDriving()
        applyChromeVisibility(fullScreenMode)
        if (fullScreenMode) {
            renderFullScreenDrivingMola()
            return
        }
        status.text = "Park halinde kullanım · YouTube ve telefon ekranı"
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        content.addView(TextView(this).apply {
            text = "Hermes · Mola Park — YouTube, Pofuduk mini oyunu ve telefon ekranı. Yansıtma için telefonda paylaşım iznini onayla. Ses mevcut araç ses bağlantısını kullanır."
            setTextColor(Color.WHITE); textSize = 20f
        })
        content.addView(Button(this).apply {
            text = "Hermes sohbetini aç"
            setOnClickListener { startActivity(Intent(this@ParkedMolaActivity, MainActivity::class.java)) }
        })
        content.addView(Button(this).apply {
            text = "Tam Ekran Sürüş Kipini Aç (YouTube ve Telefonu Gizle)"
            setOnClickListener { setFullScreenDriving(true) }
        })
        profile?.let { saved ->
            content.addView(TextView(this).apply {
                text = "Araç: ${saved.name} · kayıtlı bağlantı"; setTextColor(Color.rgb(122, 226, 220)); textSize = 14f
            })
            if (saved.lastPage != "home") content.addView(Button(this).apply {
                text = "Son seçime dön: " + when (saved.lastPage) { "youtube" -> "YouTube"; "mirror" -> "Yansıt"; else -> "Oyun" }
                setOnClickListener { when (saved.lastPage) { "youtube" -> youtube(); "mirror" -> startMirror(); else -> game() } }
            })
        }
        body.addView(content)
    }

    /**
     * Tam ekran Mola ve sürüş kipi:
     * YouTube, telefon yansıtma, oyun ve üst araç çubuğu gizlenir;
     * Mola arayüzü tüm ekranı kaplar ve sürüş sırasında kesintisiz çalışır.
     */
    private fun renderFullScreenDrivingMola() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            setBackgroundColor(Color.rgb(9, 15, 34))
        }
        content.addView(TextView(this).apply {
            text = "HERMES · MOLA TAM EKRAN"
            setTextColor(Color.WHITE)
            textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val subHeader = TextView(this).apply {
            val carLabel = profile?.name?.let { "Araç: $it · " }.orEmpty()
            text = "${carLabel}Sürüş kipi aktif · YouTube, telefon yansıtma ve diğer araçlar gizlendi"
            setTextColor(Color.rgb(63, 185, 80))
            textSize = 15f
            setPadding(0, 6, 0, 14)
        }
        content.addView(subHeader)

        val sessionTitleView = TextView(this).apply {
            text = "Etkin oturum yükleniyor…"
            setTextColor(Color.rgb(183, 195, 207))
            textSize = 18f
            setPadding(0, 4, 0, 8)
        }
        content.addView(sessionTitleView)

        val messageView = TextView(this).apply {
            text = "Hermes yanıtı hazırlanıyor…"
            setTextColor(Color.rgb(230, 237, 243))
            textSize = 20f
            maxLines = 7
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 4, 0, 18)
        }
        content.addView(messageView, LinearLayout.LayoutParams(-1, 0, 1f))

        fun refreshPanelData() {
            scope.launch {
                val panel = runCatching { HermesPanelDataSource(this@ParkedMolaActivity).load() }.getOrNull()
                if (panel != null) {
                    sessionTitleView.text = "Oturum: ${panel.activeSession ?: "—"}"
                    messageView.text = panel.lastAssistantMessage ?: panel.lastMessage
                        ?: "Konuş düğmesine dokunarak sürüş sırasında Hermes'e sesle sorabilirsin."
                } else {
                    sessionTitleView.text = "Hermes Mola"
                    messageView.text = "Konuş düğmesine dokunarak sürüş sırasında Hermes'e sesle sorabilirsin."
                }
            }
        }
        refreshPanelData()

        fun ensureVoice(): JarvisEngine? {
            voice?.let { return it }
            return runCatching {
                JarvisEngine(this) { refreshPanelData() }.also { eng ->
                    voice = eng
                    scope.launch {
                        eng.state.collect { st ->
                            if (page == "home" && isFullScreenDriving() && st.phase != JarvisPhase.Idle) {
                                messageView.text = when (st.phase) {
                                    JarvisPhase.Listening -> "Dinliyorum… ${st.heard}"
                                    JarvisPhase.Thinking -> "Düşünüyorum… ${st.heard}"
                                    JarvisPhase.Speaking -> st.answer.take(600)
                                    JarvisPhase.Error -> st.error ?: "Sesli asistan yeniden denenebilir."
                                    JarvisPhase.Idle -> messageView.text
                                }
                            }
                        }
                    }
                }
            }.getOrNull()
        }
        if (false) {
            voice = JarvisEngine(this) { refreshPanelData() }.also { eng ->
                scope.launch {
                    eng.state.collect { st ->
                        if (page == "home" && isFullScreenDriving() && st.phase != JarvisPhase.Idle) {
                            messageView.text = when (st.phase) {
                                JarvisPhase.Listening -> "Dinliyorum… ${st.heard}"
                                JarvisPhase.Thinking -> "Düşünüyorum… ${st.heard}"
                                JarvisPhase.Speaking -> st.answer.take(600)
                                JarvisPhase.Error -> st.error ?: "Sesli asistan yeniden denenebilir."
                                JarvisPhase.Idle -> messageView.text
                            }
                        }
                    }
                }
            }
        }

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        actionsRow.addView(Button(this).apply {
            text = "Sesle Sor / Mikrofon"
            minHeight = 64
            setOnClickListener {
                val eng = ensureVoice() ?: return@setOnClickListener
                if (eng.state.value.phase == JarvisPhase.Idle) eng.start() else eng.tapMic()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        actionsRow.addView(Button(this).apply {
            text = "Yanıtı Yenile"
            minHeight = 64
            setOnClickListener { refreshPanelData() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(actionsRow)

        val secondaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        secondaryRow.addView(Button(this).apply {
            text = "Hermes Sohbetini Aç"
            setOnClickListener { startActivity(Intent(this@ParkedMolaActivity, MainActivity::class.java)) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        secondaryRow.addView(Button(this).apply {
            text = "Tam Ekran: Açık (Diğerlerini Göster)"
            setOnClickListener { setFullScreenDriving(false) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(secondaryRow)

        body.addView(content, FrameLayout.LayoutParams(-1, -1))
    }

    @Suppress("SetJavaScriptEnabled")
    private fun youtube() {
        if (isFullScreenDriving()) {
            home()
            return
        }
        clear(); rememberPage("youtube")
        status.text = "YouTube · park halinde · oynatmak için videoya dokun"
        val view = WebView(this)
        web = view
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.mediaPlaybackRequiresUserGesture = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.settings.setSupportMultipleWindows(false)
        view.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val allowed = MolaWebPolicy.allows(request.url)
                if (!allowed && request.isForMainFrame) status.text = "Bu bağlantı YouTube dışına çıkıyor. Telefonda açabilirsin."
                return !allowed
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullScreen != null) { callback.onCustomViewHidden(); return }
                fullScreen = view; fullScreenCallback = callback; body.addView(view, FrameLayout.LayoutParams(-1, -1))
            }
            override fun onHideCustomView() { exitFullScreen() }
        }
        body.addView(view, FrameLayout.LayoutParams(-1, -1))
        view.loadUrl("https://m.youtube.com")
    }

    private fun game() {
        if (isFullScreenDriving()) {
            home()
            return
        }
        clear(); rememberPage("game")
        status.text = "Pofuduk’u yakala · yalnız park halinde · her dokunuş bir puan"
        body.addView(MolaBuddyGameView(this), FrameLayout.LayoutParams(-1, -1))
    }

    private fun exitFullScreen() {
        val view = fullScreen; val callback = fullScreenCallback
        fullScreen = null; fullScreenCallback = null
        if (view != null) body.removeView(view)
        callback?.onCustomViewHidden()
    }

    private fun startMirror() {
        if (isFullScreenDriving()) {
            home()
            return
        }
        clear(); rememberPage("mirror"); sharing = true
        status.text = "Telefonda ekran paylaşımını onayla. Bu görünüm ekranı gösterir; telefon kontrolü için Mola uyumlu giriş kullanılabilir."
        val surface = SurfaceView(this); mirror = surface
        body.addView(surface, FrameLayout.LayoutParams(-1, -1))
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { bindMirror() }
            override fun surfaceDestroyed(holder: SurfaceHolder) { ScreenMirror.detach() }
        })
        ScreenMirror.requestFromCar(this)
    }

    private fun bindMirror() {
        val view = mirror ?: return
        if (sharing && view.holder.surface.isValid && view.width > 0 && view.height > 0 && ScreenMirror.hasPermission) {
            if (ScreenMirror.attach(view.holder.surface, view.width, view.height, resources.displayMetrics.densityDpi))
                status.text = "Telefon ekranı paylaşılıyor · Mola düğmesiyle durdur"
        }
    }

    override fun onStart() { super.onStart(); loadCarProfile() }
    override fun onStop() { profileJob?.cancel(); clear(); super.onStop() }
    override fun onRestart() { super.onRestart(); home() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        pendingEntry = if (!isFullScreenDriving() && intent.component?.className?.endsWith(".YouTubeAlias") == true) "youtube" else "home"
    }
    override fun onResume() {
        super.onResume()
        val entry = pendingEntry ?: return
        pendingEntry = null
        if (entry == "youtube" && !isFullScreenDriving()) youtube() else home()
    }
    override fun onDestroy() { voice?.release(); voice = null; clear(); scope.cancel(); super.onDestroy() }

    companion object {
        /** Tam ekran sürüş kipinde YouTube, Yansıt ve Oyun araç çubuğu gizlenir. */
        fun visibleToolbarButtons(fullScreenDriving: Boolean): List<String> =
            if (fullScreenDriving) emptyList() else listOf("Mola", "YouTube", "Yansıt", "Oyun")
    }
}

internal object MolaWebPolicy {
    fun allows(uri: android.net.Uri): Boolean {
        val host = uri.host?.lowercase(java.util.Locale.ROOT).orEmpty()
        return uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
            (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be")
    }
}
