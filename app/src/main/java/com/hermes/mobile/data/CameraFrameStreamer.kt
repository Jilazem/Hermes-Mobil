package com.hermes.mobile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Kamera karelerini Gemini Live'a akıtır.
 *
 * Live API sürekli video değil, **ayrık kareler** bekliyor. Saniyede 30 kare
 * göndermek hem bant genişliğini hem kotayı boşa harcar ve modele bir fayda
 * sağlamaz; insan gözüyle "canlı" hissi için saniyede 1–2 kare yeterli.
 * Bu yüzden kareler [frameIntervalMs] ile kısılıyor.
 *
 * Kareler JPEG'e çevrilip base64 olarak gönderiliyor (bkz.
 * [LiveVoiceClient.sendVideoFrame]). Çözünürlük kasıtlı olarak düşük tutuluyor —
 * model için 768 piksel genişlik fazlasıyla yeterli, daha büyüğü yalnız
 * gecikme ekliyor.
 */
class CameraFrameStreamer(
    private val context: Context,
    private val frameIntervalMs: Long = 700,
    private val maxWidth: Int = 768,
    private val jpegQuality: Int = 70,
) {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var lastSentAt = 0L
    private var onFrame: ((ByteArray) -> Unit)? = null

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _facing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val facing: StateFlow<Int> = _facing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var boundOwner: LifecycleOwner? = null
    private var boundPreview: PreviewView? = null

    fun start(owner: LifecycleOwner, previewView: PreviewView, onFrame: (ByteArray) -> Unit) {
        this.onFrame = onFrame
        boundOwner = owner
        boundPreview = previewView
        bind()
    }

    fun switchLens() {
        _facing.value =
            if (_facing.value == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
            else CameraSelector.LENS_FACING_BACK
        bind()
    }

    private fun bind() {
        val owner = boundOwner ?: return
        val previewView = boundPreview ?: return
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener({
            val cameraProvider = runCatching { future.get() }.getOrElse {
                _error.value = "Kamera açılamadı: ${it.message}"
                return@addListener
            }
            provider = cameraProvider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                // RGBA çıktısı `toBitmap()`'i doğrudan mümkün kılıyor; YUV→JPEG
                // dönüşümünü elle yazmaya gerek kalmıyor.
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analysis.setAnalyzer(analysisExecutor) { proxy -> handleFrame(proxy) }

            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    owner,
                    CameraSelector.Builder().requireLensFacing(_facing.value).build(),
                    preview,
                    analysis,
                )
                _active.value = true
                _error.value = null
            }.onFailure {
                _error.value = "Kamera bağlanamadı: ${it.message}"
                _active.value = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun handleFrame(proxy: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastSentAt < frameIntervalMs) return
            lastSentAt = now

            val sink = onFrame ?: return
            val bitmap = runCatching { proxy.toBitmap() }.getOrNull() ?: return
            val jpeg = encode(bitmap, proxy.imageInfo.rotationDegrees)
            sink(jpeg)
        } finally {
            proxy.close()
        }
    }

    /** Döndürür, küçültür, JPEG'e sıkıştırır. */
    private fun encode(source: Bitmap, rotationDegrees: Int): ByteArray {
        var bmp = source

        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }

        if (bmp.width > maxWidth) {
            val scale = maxWidth.toFloat() / bmp.width
            bmp = Bitmap.createScaledBitmap(
                bmp,
                maxWidth,
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        }

        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            out.toByteArray()
        }
    }

    fun stop() {
        runCatching { provider?.unbindAll() }
        provider = null
        onFrame = null
        boundOwner = null
        boundPreview = null
        _active.value = false
    }

    fun release() {
        stop()
        analysisExecutor.shutdown()
    }
}
