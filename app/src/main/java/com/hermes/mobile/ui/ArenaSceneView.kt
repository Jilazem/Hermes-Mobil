package com.hermes.mobile.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hermes.mobile.data.DiagLog
import java.io.ByteArrayInputStream

/**
 * Arena 3D sahnesi — WebView içinde three.js.
 *
 * Güvenlik/ayar sözleşmesi (tur-9):
 *  - JavaScript açık, ama **yalnız yerel asset** yüklenir: `file:///android_asset/arena/`
 *    dışındaki her istek `shouldInterceptRequest`te kesilir (boş gövde döner).
 *  - `blockNetworkLoads = true`: ağ erişimi YOK (CDN yok, üçüncü taraf yok).
 *  - `allowFileAccess = false`, `allowContentAccess = false`: cihaz dosya sistemi kapalı.
 *  - WebView konsolu logcat'e düşer (JS hatası sahada görünür kalır).
 *
 * Tur-15: aynı sözleşme Outrun yarış sahnesine de uygulanır ([ArenaOutrunHost]).
 *
 * Köprü: Kotlin → JS `window.arenaScene.setData(json)` / `setActive(bool)`;
 * JS → Kotlin `__ArenaBridge.onSceneEvent(type, detail)`.
 */
private const val ARENA_SCENE_TAG = "ArenaScene"

/** Sahne sayfası — uygulama asset'i (çalışma anında indirme yok). */
val ARENA_SCENE_URL = arenaSceneUrl(ArenaSceneKind.WORK)

/** Outrun sayfasının zemin rengi (outrun.html `--bg`) — ilk karede beyaz parlama olmasın. */
private const val OUTRUN_BACKGROUND = "#0b0416"

/**
 * Tek WebView fabrikası — iki sahne (iş sahnesi, Outrun) AYNI güvenlik sözleşmesiyle doğar.
 *
 * Tek fark: Outrun en iyi skoru `localStorage`da tuttuğu için DOM depolaması açık
 * (depo uygulamanın kendi WebView profilinde; ağ/dosya erişimi yine kapalı).
 * `onEvent` her zaman ana iş parçacığında çağrılır.
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createArenaWebView(
    ctx: Context,
    kind: ArenaSceneKind,
    background: String,
    onEvent: (String, String) -> Unit,
): WebView {
    val handler = Handler(Looper.getMainLooper())
    return WebView(ctx).apply {
        setBackgroundColor(Color.parseColor(background))
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = kind == ArenaSceneKind.OUTRUN
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.blockNetworkLoads = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = true

        addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onSceneEvent(type: String, detail: String) {
                    // Köprü çağrısı UI dışı iş parçacığından gelir → ana iş parçacığına taşı.
                    handler.post { onEvent(type, detail) }
                }
            },
            "__ArenaBridge",
        )

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.i(ARENA_SCENE_TAG, "sayfa yuklendi: $url")
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url?.toString().orEmpty()
                if (arenaAssetAllowed(url)) return null // yerel asset: normal akış
                Log.w(ARENA_SCENE_TAG, "engellendi (yerel asset degil): $url")
                DiagLog.w("arena3d", "engellendi: $url")
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    ByteArrayInputStream(ByteArray(0)),
                )
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?,
            ) {
                Log.w(ARENA_SCENE_TAG, "yukleme hatasi: ${error?.description}")
                handler.post { onEvent("error", error?.description?.toString().orEmpty()) }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                val line = "konsol: ${msg?.message()} @${msg?.sourceId()}:${msg?.lineNumber()}"
                Log.i(ARENA_SCENE_TAG, line)
                return true
            }
        }
    }
}

@Composable
fun ArenaSceneHost(
    json: String,
    modifier: Modifier = Modifier,
    theme: ArenaSceneTheme = ArenaSceneTheme.DARK,
    onEvent: (String, String) -> Unit = { _, _ -> },
) {
    val ctx = LocalContext.current
    val latestEvent by rememberUpdatedState(onEvent)
    val lifecycleOwner = LocalLifecycleOwner.current
    var sceneReady by remember { mutableStateOf(false) }

    // WebView BİR KEZ doğar (remember): her recomposition'da yeniden yaratılıp
    // eskisinin yok edilmesi sahneyi öldürüyordu (tur-9 ölçümü: destroy + JS hiç koşmadı).
    val web = remember {
        createArenaWebView(ctx, ArenaSceneKind.WORK, theme.background) { type, detail ->
            when (type) {
                "ready" -> sceneReady = true
                "nowebgl" -> sceneReady = false
                "error" -> sceneReady = false
                else -> {}
            }
            latestEvent(type, detail)
        }.also {
            Log.i(ARENA_SCENE_TAG, "sahne yukleniyor: $ARENA_SCENE_URL")
            it.loadUrl(ARENA_SCENE_URL)
        }
    }

    AndroidView(factory = { web }, modifier = modifier)

    // Veri köprüsü: sahne hazır olduktan sonra (ve her değişimde) JSON'u bas.
    LaunchedEffect(web, sceneReady, json) {
        if (!sceneReady) return@LaunchedEffect
        Log.i(ARENA_SCENE_TAG, "veri basiliyor (${json.length} bayt)")
        web.evaluateJavascript(ArenaSceneJson.jsCall(json), null)
    }

    // Ekran/sahne görünmezken render döngüsü DURUR (pil + ısı).
    DisposableEffect(lifecycleOwner, web) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    web.onPause()
                    web.evaluateJavascript(ArenaSceneJson.jsActive(false), null)
                }

                Lifecycle.Event.ON_RESUME -> {
                    web.onResume()
                    web.evaluateJavascript(ArenaSceneJson.jsActive(true), null)
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(web) {
        onDispose {
            Log.i(ARENA_SCENE_TAG, "sahne kapaniyor")
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
    }
}

/**
 * Tur-15: Outrun WebView'inin sahibi — sekme değişimini SAĞ atlatır.
 *
 * İş sahnesi veri güdümlüdür, sekmeye dönüşte yeniden doğması zararsız. Yarış ise
 * durum taşır (mesafe, konum): WebView sekme kompozisyonunun dışında (HermesApp
 * kapsamında) yaşar; sekme gizlenince yarış duraklatılır + render durur + WebView
 * `onPause`, dönüşte aynı sayfa kaldığı yerden (duraklatma ekranında) sürer.
 * Kip "İş sahnesi"ne alınınca [release] ile yok edilir (GPU bağlamı tutulmaz).
 *
 * Tüm geçiş kararları saf [arenaSceneReduce] / [arenaSceneCommands]'dadır.
 */
@Stable
class ArenaOutrunHolder {
    private var web: WebView? = null

    var run by mutableStateOf(ArenaSceneRun())
        private set

    /** Son sayfa yüklemesinin başladığı an (zaman aşımı kararı için); 0 = yüklenmedi. */
    var loadStartedAt by mutableLongStateOf(0L)
        private set

    internal var listener: (String, String) -> Unit = { _, _ -> }

    fun dispatch(e: ArenaSceneEvent) {
        val prev = run
        val next = arenaSceneReduce(prev, e)
        run = next
        val w = web ?: return
        arenaSceneCommands(ArenaSceneKind.OUTRUN, prev, next).forEach { w.evaluateJavascript(it, null) }
    }

    internal fun obtain(ctx: Context): WebView = web ?: createArenaWebView(
        ctx, ArenaSceneKind.OUTRUN, OUTRUN_BACKGROUND,
    ) { type, detail ->
        arenaSceneEventOf(type)?.let(::dispatch)
        listener(type, detail)
    }.also {
        web = it
        run = ArenaSceneRun()
        loadStartedAt = System.currentTimeMillis()
        val url = arenaSceneUrl(ArenaSceneKind.OUTRUN)
        Log.i(ARENA_SCENE_TAG, "outrun yukleniyor: $url")
        it.loadUrl(url)
    }

    fun release() {
        val w = web ?: return
        Log.i(ARENA_SCENE_TAG, "outrun kapaniyor")
        web = null
        (w.parent as? ViewGroup)?.removeView(w)
        w.destroy()
        run = ArenaSceneRun()
        loadStartedAt = 0L
    }
}

/** Outrun sahnesi — WebView [holder]'dan alınır; bu composable yalnız takar/söker. */
@Composable
fun ArenaOutrunHost(
    holder: ArenaOutrunHolder,
    modifier: Modifier = Modifier,
    onEvent: (String, String) -> Unit = { _, _ -> },
) {
    val ctx = LocalContext.current
    val latestEvent by rememberUpdatedState(onEvent)
    val lifecycleOwner = LocalLifecycleOwner.current
    val web = remember(holder) { holder.obtain(ctx) }

    AndroidView(
        factory = {
            // Önceki sekme ziyaretinden kalan ebeveyn varsa önce ayır (tek ebeveyn kuralı).
            (web.parent as? ViewGroup)?.removeView(web)
            web
        },
        modifier = modifier,
    )

    // Sekme/ekran görünürlüğü: gizlenince önce JS (duraklat + render kapat), sonra WebView.onPause.
    DisposableEffect(holder, web) {
        holder.listener = { t, d -> latestEvent(t, d) }
        web.onResume()
        holder.dispatch(ArenaSceneEvent.TAB_SHOWN)
        onDispose {
            holder.listener = { _, _ -> }
            holder.dispatch(ArenaSceneEvent.TAB_HIDDEN)
            web.onPause()
            (web.parent as? ViewGroup)?.removeView(web)
        }
    }

    DisposableEffect(lifecycleOwner, web) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    holder.dispatch(ArenaSceneEvent.APP_PAUSED)
                    web.onPause()
                }

                Lifecycle.Event.ON_RESUME -> {
                    web.onResume()
                    holder.dispatch(ArenaSceneEvent.APP_RESUMED)
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Geri tuşu: koşan yarışta ÖNCE duraklat; duraklatılmışken kabuğun çıkış akışı işler.
    BackHandler(enabled = arenaBackConsumed(ArenaSceneKind.OUTRUN, holder.run)) {
        holder.dispatch(ArenaSceneEvent.BACK)
    }
}

/**
 * Zarif geri düşme: WebGL yok / JS hatası / sahne zaman aşımı.
 *
 * Sahne yerine **statik kart listesi** çizilir; Arena'nın kendi işlevi (kurulum,
 * çipler, sonuç kartları) etkilenmez, çökme olmaz.
 */
@Composable
fun ArenaSceneFallback(
    figures: List<ArenaFigure>,
    reason: ArenaSceneFallbackReason,
    modifier: Modifier = Modifier,
) {
    val note = when (reason) {
        ArenaSceneFallbackReason.NO_WEBGL -> S.t2("3D desteklenmiyor (WebGL yok)", "3D not supported (no WebGL)")
        ArenaSceneFallbackReason.SCRIPT_ERROR -> S.t2("3D sahne yüklenemedi", "3D scene failed to load")
        ArenaSceneFallbackReason.TIMEOUT -> S.t2("3D sahne yanıt vermedi", "3D scene timed out")
        ArenaSceneFallbackReason.NONE -> ""
    }

    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            note,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (figures.isEmpty()) {
            Text(
                S.t2("Sahne verisi yok", "No scene data"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                figures.forEach { f -> FallbackFigureCard(f) }
            }
        }
    }
}

@Composable
private fun FallbackFigureCard(f: ArenaFigure) {
    val (label, color) = when (f.state) {
        ArenaFigureState.WAITING -> S.t2("bekliyor", "waiting") to MaterialTheme.colorScheme.onSurfaceVariant
        ArenaFigureState.WORKING -> S.t2("çalışıyor", "working") to MaterialTheme.colorScheme.primary
        ArenaFigureState.DONE -> S.t2("bitti", "done") to MaterialTheme.colorScheme.tertiary
        ArenaFigureState.ERROR -> S.t2("hata", "error") to MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .height(56.dp)
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(f.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 10.sp, color = color)
                f.badge?.let {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Sahne yüklenirken gösterilen sessiz iskelet — çökme yok, yer tutar. */
@Composable
fun ArenaSceneSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            S.t2("3D sahne hazırlanıyor…", "Preparing 3D scene…"),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
