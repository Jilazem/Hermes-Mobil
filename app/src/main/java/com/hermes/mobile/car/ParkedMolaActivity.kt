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
import com.hermes.mobile.data.MirrorProjectionService
import com.hermes.mobile.data.ScreenMirror
import kotlinx.coroutines.*

/** Android 15+ parked launcher. No distractionOptimized flag or motion-policy bypass. */
class ParkedMolaActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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

    private fun rememberPage(value: String) {
        page = value
        profile?.let { saved -> runCatching { profiles.setLastPage(saved.id, value) }; profile = saved.copy(lastPage = value) }
    }
    private fun loadCarProfile() {
        profileJob?.cancel()
        profileJob = scope.launch {
            profile = try { profiles.resolve(MolaConnectedCars.read(this@ParkedMolaActivity), learnCarAudio =
                intent.getBooleanExtra("hermes_car_entry", false) || intent.hasCategory("android.intent.category.CAR_LAUNCHER")) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            if (page != "home") rememberPage(page)
            if (page == "home" && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) home()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9,15,34))
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view,insets ->
            val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            insets
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val toolbar = LinearLayout(this)
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label; setOnClickListener { action() }; minHeight = 56
        }
        toolbar.addView(button("Mola") { home() }, LinearLayout.LayoutParams(0,-2,1f))
        toolbar.addView(button("YouTube") { youtube() }, LinearLayout.LayoutParams(0,-2,1f))
        toolbar.addView(button("Yansıt") { startMirror() }, LinearLayout.LayoutParams(0,-2,1f))
        toolbar.addView(button("Oyun") { game() }, LinearLayout.LayoutParams(0,-2,1f))
        root.addView(toolbar)
        status = TextView(this).apply { setTextColor(Color.rgb(180,199,226)); textSize=14f; setPadding(12,4,12,4) }
        root.addView(status)
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        androidx.core.view.ViewCompat.requestApplyInsets(root)
        scope.launch { ScreenMirror.status.collect { bindMirror() } }
        if (intent.component?.className?.endsWith(".YouTubeAlias")==true) youtube() else home()
        onBackPressedDispatcher.addCallback(this, object:androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullScreen!=null) exitFullScreen()
                else if (web?.canGoBack()==true) web?.goBack()
                else if (web!=null || mirror!=null || body.getChildAt(0) is MolaBuddyGameView) home() else finish()
            }
        })
    }

    private fun clear() {
        exitFullScreen()
        web?.let { it.stopLoading(); it.onPause(); it.loadUrl("about:blank"); it.destroy() }; web=null
        mirror=null
        if (sharing) { sharing=false; ScreenMirror.release(); runCatching { MirrorProjectionService.stop(this) } }
        body.removeAllViews()
    }

    private fun home() {
        clear()
        page = "home"
        status.text = "Park halinde kullanım · YouTube ve telefon ekranı"
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,16,24,16) }
        content.addView(TextView(this).apply {
            text="Hermes · Mola Park\nYouTube, Pofuduk mini oyunu ve telefon ekranı. Yansıtma için telefonda paylaşım iznini onayla. Ses mevcut araç ses bağlantısını kullanır."
            setTextColor(Color.WHITE); textSize=20f
        })
        content.addView(Button(this).apply { text="Hermes sohbetini aç"; setOnClickListener {
            startActivity(Intent(this@ParkedMolaActivity,MainActivity::class.java))
        } })
        profile?.let { saved ->
            content.addView(TextView(this).apply {
                text = "Araç: ${saved.name} · kayıtlı bağlantı"; setTextColor(Color.rgb(122,226,220)); textSize = 14f
            })
            if (saved.lastPage != "home") content.addView(Button(this).apply {
                text = "Son seçime dön: " + when(saved.lastPage) { "youtube" -> "YouTube"; "mirror" -> "Yansıt"; else -> "Oyun" }
                setOnClickListener { when(saved.lastPage) { "youtube" -> youtube(); "mirror" -> startMirror(); else -> game() } }
            })
        }
        body.addView(content)
    }

    @Suppress("SetJavaScriptEnabled")
    private fun youtube() {
        clear(); rememberPage("youtube")
        status.text="YouTube · park halinde · oynatmak için videoya dokun"
        val view=WebView(this)
        web=view
        view.settings.javaScriptEnabled=true
        view.settings.domStorageEnabled=true
        view.settings.mediaPlaybackRequiresUserGesture=true
        view.settings.allowFileAccess=false
        view.settings.allowContentAccess=false
        view.settings.setSupportMultipleWindows(false)
        view.settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        view.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView, request:WebResourceRequest):Boolean {
                val allowed=MolaWebPolicy.allows(request.url)
                if (!allowed && request.isForMainFrame) status.text="Bu bağlantı YouTube dışına çıkıyor. Telefonda açabilirsin."
                return !allowed
            }
        }
        view.webChromeClient=object:WebChromeClient() {
            override fun onShowCustomView(view:View, callback:CustomViewCallback) {
                if (fullScreen!=null) { callback.onCustomViewHidden(); return }
                fullScreen=view; fullScreenCallback=callback; body.addView(view,FrameLayout.LayoutParams(-1,-1))
            }
            override fun onHideCustomView() { exitFullScreen() }
        }
        body.addView(view,FrameLayout.LayoutParams(-1,-1))
        view.loadUrl("https://m.youtube.com")
    }

    private fun game() {
        clear(); rememberPage("game")
        status.text="Pofuduk’u yakala · yalnız park halinde · her dokunuş bir puan"
        body.addView(MolaBuddyGameView(this),FrameLayout.LayoutParams(-1,-1))
    }

    private fun exitFullScreen() {
        val view=fullScreen; val callback=fullScreenCallback
        fullScreen=null; fullScreenCallback=null
        if (view!=null) body.removeView(view)
        callback?.onCustomViewHidden()
    }

    private fun startMirror() {
        clear(); rememberPage("mirror"); sharing=true
        status.text="Telefonda ekran paylaşımını onayla. Bu görünüm ekranı gösterir; telefon kontrolü için Mola uyumlu giriş kullanılabilir."
        val surface=SurfaceView(this); mirror=surface
        body.addView(surface,FrameLayout.LayoutParams(-1,-1))
        surface.holder.addCallback(object:SurfaceHolder.Callback {
            override fun surfaceCreated(holder:SurfaceHolder) = Unit
            override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int) { bindMirror() }
            override fun surfaceDestroyed(holder:SurfaceHolder) { ScreenMirror.detach() }
        })
        ScreenMirror.requestFromCar(this)
    }
    private fun bindMirror() {
        val view=mirror ?: return
        if (sharing && view.holder.surface.isValid && view.width>0 && view.height>0 && ScreenMirror.hasPermission) {
            if (ScreenMirror.attach(view.holder.surface,view.width,view.height,resources.displayMetrics.densityDpi))
                status.text="Telefon ekranı paylaşılıyor · Mola düğmesiyle durdur"
        }
    }
    override fun onStart() { super.onStart(); loadCarProfile() }
    override fun onStop() { profileJob?.cancel(); clear(); super.onStop() }
    override fun onRestart() { super.onRestart(); home() }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent);setIntent(intent)
        pendingEntry=if(intent.component?.className?.endsWith(".YouTubeAlias")==true) "youtube" else "home"
    }
    override fun onResume() {
        super.onResume()
        val entry=pendingEntry ?: return
        pendingEntry=null
        if(entry=="youtube") youtube() else home()
    }
    override fun onDestroy() { clear(); scope.cancel(); super.onDestroy() }
}

internal object MolaWebPolicy {
    fun allows(uri:android.net.Uri):Boolean {
        val host=uri.host?.lowercase(java.util.Locale.ROOT).orEmpty()
        return uri.scheme=="https" && uri.userInfo==null && (uri.port==-1 || uri.port==443) &&
            (host=="youtube.com" || host.endsWith(".youtube.com") || host=="youtu.be")
    }
}
