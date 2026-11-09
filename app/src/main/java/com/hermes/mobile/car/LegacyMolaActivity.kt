package com.hermes.mobile.car

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.apps.auto.sdk.CarActivity
import com.hermes.mobile.MainActivity
import com.hermes.mobile.assistant.JarvisEngine
import com.hermes.mobile.assistant.JarvisPhase
import com.hermes.mobile.data.FullControl
import com.hermes.mobile.data.HermesAccessibilityService
import com.hermes.mobile.data.PhoneBotLogic
import com.hermes.mobile.data.ScreenMirror
import com.hermes.mobile.data.MirrorProjectionService
import com.hermes.mobile.data.SettingsStore
import kotlinx.coroutines.*

/** Original native UI on Mola's legacy host contract; supports Full-Screen Driving Mode (default ON, toggleable). */
class LegacyMolaActivity : CarActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var voice: JarvisEngine
    private var mirror: SurfaceView? = null
    private var voiceOpen = false
    private var sharing = false
    private var carName: String? = null

    private fun isFullScreenDriving(): Boolean =
        runCatching { SettingsStore(this).current().molaFullScreenDriving }.getOrDefault(true)

    private fun setFullScreenDriving(enabled: Boolean) {
        runCatching { SettingsStore(this).update { it.copy(molaFullScreenDriving = enabled) } }
        home()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setIgnoreConfigChanges(android.content.pm.ActivityInfo.CONFIG_ORIENTATION or android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.rgb(9, 15, 34))
        }
        setContentView(root)
        voice = JarvisEngine(this) { home() }
        scope.launch {
            voice.state.collect { state ->
                if (voiceOpen || isFullScreenDriving()) {
                    status.text = when (state.phase) {
                        JarvisPhase.Listening -> "Dinliyorum… ${state.heard}"
                        JarvisPhase.Thinking -> "Düşünüyorum… ${state.heard}"
                        JarvisPhase.Speaking -> state.answer.take(600)
                        JarvisPhase.Error -> state.error ?: "Sesli asistan yeniden denenebilir."
                        JarvisPhase.Idle -> if (voiceOpen) "Konuşmak için mikrofon düğmesine dokun." else status.text
                    }
                }
            }
        }
        scope.launch { ScreenMirror.status.collect { bindMirror() } }
        home()
        scope.launch {
            val profile = try {
                MolaCarProfiles(this@LegacyMolaActivity)
                    .resolve(MolaConnectedCars.read(this@LegacyMolaActivity), learnCarAudio = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            carName = profile?.name
            if (!voiceOpen && !sharing && carName != null) {
                status.text = if (isFullScreenDriving()) {
                    "Araç: $carName · Tam ekran sürüş kipi aktif (YouTube ve telefon gizli)"
                } else {
                    "Araç: $carName · kayıtlı bağlantı. Park halinde kullan."
                }
            }
        }
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text; textSize = 24f; setTextColor(Color.WHITE); setPadding(12, 8, 12, 8)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; setOnClickListener { action() }; minHeight = 58
    }

    private fun reset(label: String) {
        root.removeAllViews(); root.addView(title(label))
        status = title("").apply { textSize = 17f; setTypeface(typeface, android.graphics.Typeface.NORMAL) }
        root.addView(status)
    }

    private fun home() {
        voiceOpen = false
        voice.stop()
        stopSharing()
        val fullScreenMode = isFullScreenDriving()
        if (fullScreenMode) {
            reset("HERMES · MOLA TAM EKRAN (Sürüş Kipi)")
            status.text = carName?.let { "Araç: $it · Tam ekran sürüş kipi aktif · YouTube ve telefon gizli" }
                ?: "Tam ekran sürüş kipi aktif · Sürüş sırasında kesintisiz çalışır"
            val messageBox = TextView(this).apply {
                text = "Son oturum yanıtı yükleniyor…"
                textSize = 19f
                setTextColor(Color.rgb(230, 237, 243))
                maxLines = 6
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(12, 10, 12, 14)
            }
            root.addView(messageBox, LinearLayout.LayoutParams(-1, 0, 1f))
            fun loadLatestMessage() {
                scope.launch {
                    val panel = runCatching { HermesPanelDataSource(this@LegacyMolaActivity).load() }.getOrNull()
                    messageBox.text = panel?.lastAssistantMessage ?: panel?.lastMessage
                        ?: "Sesle Sor düğmesine dokunarak sürüş sırasında Hermes ile konuşabilirsin."
                }
            }
            loadLatestMessage()

            val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            root.addView(actions)
            actions.addView(button("Sesle Sor / Mikrofon (Sürüşte Çalışır)") {
                voiceOpen = true
                voice.start()
            })
            actions.addView(button("Son Yanıtı Yenile") { loadLatestMessage() })
            actions.addView(button("Tam Ekran Sürüş Kipi: Açık (Diğer Araçları Göster)") {
                setFullScreenDriving(false)
            })
            return
        }

        reset("Hermes · Mola uyumluluk")
        status.text = carName?.let { "Araç: $it · kayıtlı bağlantı. Park halinde kullan." }
            ?: "Seçili Hermes bağlantın kullanılır. Telefon yansıtma park halinde açılır."
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(android.widget.ScrollView(this).apply { addView(actions) }, LinearLayout.LayoutParams(-1, 0, 1f))
        actions.addView(button("Tam Ekran Sürüş Kipini Aç (YouTube ve Telefonu Gizle)") {
            setFullScreenDriving(true)
        })
        actions.addView(button("Sesle sor") {
            voiceOpen = true; reset("Hermes · Sesli asistan")
            root.addView(button("Mikrofon / Durdur") { voice.tapMic() })
            root.addView(button("Ana sayfa") { home() })
            voice.start()
        })
        actions.addView(button("Park halinde telefon ekranını paylaş") { openMirror() })
        actions.addView(button("YouTube · park halinde") { openMirror(); openYouTube() })
        actions.addView(button("Son sohbet yanıtı") {
            scope.launch {
                val panel = HermesPanelDataSource(this@LegacyMolaActivity).load()
                if (!voiceOpen && mirror == null) status.text = panel.lastAssistantMessage ?: "Seçili sohbette okunabilir yanıt bulunamadı."
            }
        })
        actions.addView(button("Hermes'i telefonda aç") {
            runCatching { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { status.text = "Telefonda Hermes simgesine dokun." }
        })
    }

    private fun openYouTube() {
        if (isFullScreenDriving()) {
            home()
            return
        }
        if (!ScreenMirror.hasPermission) {
            status.text = "Önce telefonda ekran paylaşımını onayla, ardından YouTube düğmesine dokun."
            return
        }
        val app = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"))
            .setPackage("com.google.android.youtube").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = android.app.ActivityOptions.makeBasic().setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY).toBundle()
        runCatching { startActivity(app, options) }
            .recoverCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://m.youtube.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options) }
            .onSuccess { status.text = "YouTube açma isteği telefona gönderildi. Ses, telefonun mevcut araç bağlantısını kullanır." }
            .onFailure { status.text = "Telefonda YouTube’u aç; ekran paylaşımı devam eder." }
    }

    private fun openMirror() {
        if (isFullScreenDriving()) {
            home()
            return
        }
        voiceOpen = false; voice.stop(); sharing = true
        reset("Hermes · Park halinde yansıtma")
        status.text = "Telefonda tüm ekran paylaşımını onayla. Dokunma için mevcut telefon kontrol izni gerekir."
        root.addView(button("Telefonda paylaşım iznini aç") { ScreenMirror.requestFromCar(this) })
        root.addView(button("YouTube’u telefonda aç") { openYouTube() })
        root.addView(button("Durdur ve ana sayfaya dön") { home() })
        val view = SurfaceView(this)
        mirror = view
        root.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { bindMirror() }
            override fun surfaceDestroyed(holder: SurfaceHolder) { ScreenMirror.detach() }
        })
        var downX = 0f; var downY = 0f
        view.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) { downX = event.x; downY = event.y }
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val settings = SettingsStore(this).current()
                val swipe = kotlin.math.abs(event.x - downX) + kotlin.math.abs(event.y - downY) > 20
                val kind = if (swipe) FullControl.SWIPE else FullControl.TAP
                val reason = PhoneBotLogic.commandBlockReason(settings, kind)
                val service = HermesAccessibilityService.instanceOrNull()
                if (reason != null || service == null) status.text = "Dokunma için telefonda telefon kontrol iznini aç."
                else {
                    val (w, h) = ScreenMirror.phoneSize(this)
                    val end = MirrorLogic.carToPhone(event.x, event.y, view.width, view.height, w, h)
                    val start = MirrorLogic.carToPhone(downX, downY, view.width, view.height, w, h)
                    if (end != null && start != null) {
                        if (swipe) service.mirrorSwipe(intArrayOf(start.first, start.second, end.first, end.second))
                        else service.mirrorTap(end.first, end.second)
                    }
                }
            }
            true
        }
        if (!ScreenMirror.hasPermission) ScreenMirror.requestFromCar(this)
    }

    private fun bindMirror() {
        val view = mirror ?: return
        if (sharing && view.holder.surface.isValid && view.width > 0 && view.height > 0 && ScreenMirror.hasPermission) {
            if (ScreenMirror.attach(view.holder.surface, view.width, view.height, resources.displayMetrics.densityDpi))
                status.text = "Telefon ekranı paylaşılıyor. Paylaşımı Durdur düğmesiyle bitirebilirsin."
        }
    }

    private fun stopSharing() {
        mirror = null
        if (sharing) {
            sharing = false
            ScreenMirror.release()
            runCatching { MirrorProjectionService.stop(this) }
        }
    }

    override fun onBackPressed() { home() }
    override fun onResume() {
        super.onResume()
        if (::root.isInitialized && !sharing && mirror == null && !voiceOpen) home()
    }
    override fun onStop() { if (::voice.isInitialized) voice.stop(); stopSharing(); super.onStop() }
    override fun onDestroy() { if (::voice.isInitialized) voice.release(); stopSharing(); scope.cancel(); super.onDestroy() }

    companion object {
        fun visibleMenuActions(fullScreenDriving: Boolean): List<String> =
            if (fullScreenDriving) {
                listOf("Sesle Sor / Mikrofon (Sürüşte Çalışır)", "Son Yanıtı Yenile", "Tam Ekran Sürüş Kipi: Açık (Diğer Araçları Göster)")
            } else {
                listOf(
                    "Tam Ekran Sürüş Kipini Aç (YouTube ve Telefonu Gizle)",
                    "Sesle sor",
                    "Park halinde telefon ekranını paylaş",
                    "YouTube · park halinde",
                    "Son sohbet yanıtı",
                    "Hermes'i telefonda aç",
                )
            }
    }
}
