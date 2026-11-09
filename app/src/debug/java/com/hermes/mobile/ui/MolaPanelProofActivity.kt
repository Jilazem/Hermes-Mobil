package com.hermes.mobile.ui

import android.app.Presentation
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hermes.mobile.car.HermesPanel
import com.hermes.mobile.car.PanelConnection
import com.hermes.mobile.car.PanelContentLogic
import com.hermes.mobile.car.PanelState
import java.io.File

/** Debug-only real virtual display/Presentation proof; not an Android Auto host test. */
class MolaPanelProofActivity : ComponentActivity() {
    private var display: VirtualDisplay? = null
    private var panel: Presentation? = null
    private lateinit var proof: File
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        proof = File(filesDir, "mola-panel-proof.txt").apply { writeText("") }
        status = TextView(this).apply { setPadding(20, 20, 20, 20) }
        val surface = SurfaceView(this)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(surface, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                if (display != null || width <= 0 || height <= 0) return
                try {
                    val dm = getSystemService(DisplayManager::class.java)
                    display = checkNotNull(dm.createVirtualDisplay(
                        "mola-proof", width, height, resources.displayMetrics.densityDpi,
                        holder.surface, PanelContentLogic.DISPLAY_FLAGS,
                    ))
                    val vd = checkNotNull(display)
                    panel = Presentation(createDisplayContext(vd.display), vd.display).apply {
                        val view = ComposeView(context).apply {
                            setViewTreeLifecycleOwner(this@MolaPanelProofActivity)
                            setViewTreeSavedStateRegistryOwner(this@MolaPanelProofActivity)
                            setViewTreeViewModelStoreOwner(this@MolaPanelProofActivity)
                            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                            setContent {
                                HermesPanel(PanelState(
                                    server = "Hermes · demo", loaded = true, demo = true,
                                    connection = PanelConnection.NoProfile,
                                    activeSession = "Mola görsel doğrulama",
                                    lastMessage = "Bu ekran gerçek bir Android VirtualDisplay üzerinde çiziliyor. Araç host'u ayrıca test edilmelidir.",
                                ))
                            }
                        }
                        setContentView(view)
                        show()
                    }
                    step("DISPLAY_FLAGS=${PanelContentLogic.DISPLAY_FLAGS}")
                    step("PRESENTATION_SHOWN=${panel?.isShowing == true}")
                    step("MEDIA_PROJECTION_REQUESTED=false")
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isDestroyed || !holder.surface.isValid) return@postDelayed
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        PixelCopy.request(holder.surface, bitmap, { code ->
                            try {
                                check(code == PixelCopy.SUCCESS) { "PixelCopy=$code" }
                                val rendered = bitmap.getPixel(width / 2, height / 2) != android.graphics.Color.BLACK
                                check(rendered) { "blank frame" }
                                File(filesDir, "mola-panel-proof.png").outputStream().use {
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                                }
                                step("FRAME_CAPTURED=true")
                                step("RESULT=PASS (Android virtual display; Android Auto host not tested)")
                            } catch (e: Exception) { step("RESULT=FAIL ${e.javaClass.simpleName}") }
                            finally { bitmap.recycle() }
                        }, Handler(Looper.getMainLooper()))
                    }, 2000)
                } catch (e: Exception) { step("RESULT=FAIL ${e.javaClass.simpleName}") }
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) { release() }
        })
    }

    private fun step(line: String) { status.append("$line\n"); proof.appendText("$line\n") }
    private fun release() { panel?.dismiss(); panel = null; display?.release(); display = null }
    override fun onDestroy() { release(); super.onDestroy() }
}
