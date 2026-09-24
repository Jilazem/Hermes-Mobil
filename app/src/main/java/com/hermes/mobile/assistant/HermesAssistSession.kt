package com.hermes.mobile.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.text.InputType
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hermes.mobile.MainActivity
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.SettingsStore
import com.hermes.mobile.ui.JarvisOverlay
import com.hermes.mobile.ui.theme.HermesTheme
import com.hermes.mobile.ui.theme.themeById

/**
 * Hermes'in "varsayılan dijital asistan" servisi.
 *
 * Kullanıcı Hermes'i Ayarlar → Varsayılan uygulamalar → Dijital asistan
 * olarak seçince yan tuşa/ana tuşa basılı tutma, köşeden kaydırma gibi
 * asistan hareketleri Google yerine BURAYA gelir; sistem [HermesAssistSession]
 * panelini açar. Uyandırma kelimesi servisi de paneli [show] ile açar.
 */
class HermesVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        instance = this
        DiagLog.i("jarvis", "Hermes varsayılan asistan olarak hazır")
    }

    override fun onShutdown() {
        instance = null
        super.onShutdown()
    }

    companion object {
        @Volatile var instance: HermesVoiceInteractionService? = null
            private set

        /** Paneli açar (yalnız Hermes varsayılan asistansa). */
        fun show(): Boolean {
            val s = instance ?: return false
            return runCatching { s.showSession(Bundle(), 0) }.isSuccess
        }

        fun isActive(context: Context): Boolean = runCatching {
            isActiveService(context, ComponentName(context, HermesVoiceInteractionService::class.java))
        }.getOrDefault(false)
    }
}

class HermesSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = HermesAssistSession(this)
}

/**
 * Jarvis paneli — ekranı KAPLAMAZ: pencere saydam, yalnız alttaki kart
 * dokunmaya yanıt verir ([onComputeInsets]); arkadaki uygulama kullanılmaya
 * devam eder. Üst kenarda KITT tarayıcı çizgisi asistanın durumunu gösterir.
 * Panel açıkken ekran kararmaz.
 */
class HermesAssistSession(context: Context) :
    VoiceInteractionSession(context), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private lateinit var engine: JarvisEngine
    private val card = Rect()
    private var root: View? = null

    override fun onCreate() {
        super.onCreate()
        saved.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        engine = JarvisEngine(context) { hide() }
        window.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
            w.decorView.setViewTreeLifecycleOwner(this)
            w.decorView.setViewTreeSavedStateRegistryOwner(this)
            w.decorView.setViewTreeViewModelStoreOwner(this)
        }
    }

    override fun onCreateContentView(): View {
        val s = SettingsStore(context).settings.value
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@HermesAssistSession)
            setViewTreeSavedStateRegistryOwner(this@HermesAssistSession)
            setViewTreeViewModelStoreOwner(this@HermesAssistSession)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val st by engine.state.collectAsStateWithLifecycle()
                HermesTheme(palette = themeById(s.themeId)) {
                    JarvisOverlay(
                        state = st,
                        onMic = engine::tapMic,
                        onClose = { hide() },
                        onNewTopic = engine::newTopic,
                        onOpenChat = ::openChat,
                        onAsk = engine::ask,
                        onCardBounds = { l, t, r, b ->
                            card.set(l, t, r, b)
                            root?.requestLayout()
                        },
                    )
                }
            }
        }.also { root = it }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        engine.start()
    }

    override fun onHide() {
        engine.stop()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onHide()
    }

    override fun onDestroy() {
        engine.release()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    /** Dokunma yalnız kartta — kalan ekran alttaki uygulamaya geçer. */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!card.isEmpty) {
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.set(card)
        }
    }

    override fun onBackPressed() {
        hide()
    }

    // ── Ekran bağlamı ("ekranda ne var", "bunu özetle") ────────────────

    override fun onHandleAssist(state: AssistState) {
        if (Build.VERSION.SDK_INT >= 29 && state.isFocused) {
            takeScreen(state.assistStructure, state.assistContent)
        }
    }

    @Deprecated("API 29 öncesi")
    @Suppress("DEPRECATION")
    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        takeScreen(structure, content)
    }

    private fun takeScreen(structure: AssistStructure?, @Suppress("UNUSED_PARAMETER") content: AssistContent?) {
        structure ?: return
        val sb = StringBuilder()
        for (i in 0 until structure.windowNodeCount) {
            collect(structure.getWindowNodeAt(i).rootViewNode, sb)
            if (sb.length > JarvisLogic.MAX_SCREEN_CHARS) break
        }
        engine.screenText = sb.toString()
        engine.screenApp = structure.activityComponent?.packageName.orEmpty()
    }

    private fun collect(node: AssistStructure.ViewNode?, sb: StringBuilder) {
        node ?: return
        if (sb.length > JarvisLogic.MAX_SCREEN_CHARS) return
        val password = (node.inputType and InputType.TYPE_MASK_VARIATION).let {
            it == InputType.TYPE_TEXT_VARIATION_PASSWORD || it == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                it == InputType.TYPE_NUMBER_VARIATION_PASSWORD || it == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        if (!password) {
            (node.text ?: node.contentDescription)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                sb.append(it).append('\n')
            }
        }
        for (i in 0 until node.childCount) collect(node.getChildAt(i), sb)
    }

    /** Asistan sohbetini uygulamada aç (onay/uzun yanıt için). */
    private fun openChat() {
        val sid = engine.lastSessionId
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_SESSION, sid)
        runCatching { context.startActivity(i) }
        hide()
    }
}
