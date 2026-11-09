package com.hermes.mobile.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hermes.mobile.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

internal val LocalConversationServer = staticCompositionLocalOf<ServerProfile?> { null }

@Composable
internal fun ConversationMediaScope(server: ServerProfile?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalConversationServer provides server, content = content)
}

/** Credentials never go into a URL, external browser, media decoder or cross-origin redirect. */
@Composable
internal fun ServerConversationMedia(raw: String, kind: MediaKind, label: String, onOpenLink: (String) -> Unit) {
    val server = LocalConversationServer.current
    val context = LocalContext.current
    val url = server?.let { serverMediaUrl(raw, it) }
    var requestLoad by remember(url, server?.token) { mutableIntStateOf(if (kind == MediaKind.IMAGE) 1 else 0) }
    var file by remember(url, server?.token) { mutableStateOf<File?>(null) }
    var error by remember(url, server?.token) { mutableStateOf<String?>(null) }
    var loading by remember(url, server?.token) { mutableStateOf(false) }
    LaunchedEffect(url, server?.token, requestLoad) {
        if (requestLoad == 0 || url == null || server == null) return@LaunchedEffect
        loading = true; error = null
        try {
            file = withContext(Dispatchers.IO) { cacheServerMedia(context.cacheDir, url, server.token, kind) }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = "Dosya açılamadı. Bağlantıyı ve sunucudaki dosya erişimini kontrol edin." }
        finally { loading = false }
    }
    DisposableEffect(url, server?.token) { onDispose { file?.delete() } }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label.ifBlank { raw.substringAfterLast('/') }, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            if (url == null) Text("Dosyayı açmak için Hermes sunucusuna bağlanın.", style = MaterialTheme.typography.bodySmall)
            else if (file == null) TextButton(enabled = !loading, onClick = { requestLoad++ }) {
                Text(if (loading) "Yükleniyor…" else if (error != null) "Yeniden dene" else "${when(kind) { MediaKind.AUDIO -> "Sesi"; MediaKind.VIDEO -> "Videoyu"; MediaKind.HTML -> "HTML sayfasını"; else -> "Görseli" }} aç")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            file?.let { local ->
                when (kind) {
                    MediaKind.IMAGE -> {
                        val bitmap = remember(local) { sampledServerBitmap(local) }
                        var fullScreen by remember { mutableStateOf(false) }
                        if (bitmap != null) {
                            Image(
                                bitmap.asImageBitmap(),
                                label,
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp)
                                    .clickable { fullScreen = true },
                            )
                            if (fullScreen) {
                                var scale by remember { mutableFloatStateOf(1f) }
                                var panX by remember { mutableFloatStateOf(0f) }
                                var panY by remember { mutableFloatStateOf(0f) }
                                Dialog(onDismissRequest = { fullScreen = false }) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.92f))
                                            .clickable { fullScreen = false },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Image(
                                            bitmap.asImageBitmap(),
                                            label,
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 680.dp)
                                                .pointerInput(Unit) {
                                                    detectTransformGestures { _, pan, zoom, _ ->
                                                        scale = (scale * zoom).coerceIn(1f, 4f)
                                                        if (scale > 1f) {
                                                            panX += pan.x
                                                            panY += pan.y
                                                        } else {
                                                            panX = 0f
                                                            panY = 0f
                                                        }
                                                    }
                                                }
                                                .graphicsLayer(
                                                    scaleX = scale,
                                                    scaleY = scale,
                                                    translationX = panX,
                                                    translationY = panY,
                                                ),
                                        )
                                    }
                                }
                            }
                        } else Text("Bu görsel biçimi telefonda açılamadı.")
                    }
                    MediaKind.AUDIO, MediaKind.VIDEO -> LocalConversationPlayback(local, kind == MediaKind.AUDIO)
                    MediaKind.HTML -> {
                        val html = remember(local) { local.readText(Charsets.UTF_8) }
                        HtmlDocumentPreview(html, onOpenLink)
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun LocalConversationPlayback(file: File, audio: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var view by remember(file) { mutableStateOf<VideoView?>(null) }
    var error by remember(file) { mutableStateOf(false) }
    DisposableEffect(file, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) view?.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); view?.stopPlayback() }
    }
    TextButton(onClick = { view?.let { if (it.isPlaying) it.pause() else it.start() } }) { Text("Oynat / Duraklat") }
    AndroidView(modifier = Modifier.fillMaxWidth().height(if (audio) 60.dp else 220.dp), factory = { context -> VideoView(context).apply {
        setMediaController(MediaController(context).also { it.setAnchorView(this) })
        setOnErrorListener { _, _, _ -> error = true; true }
        setVideoURI(Uri.fromFile(file))
        view = this
    } }, onRelease = { it.stopPlayback() })
    if (error) Text("Bu medya biçimi telefonda oynatılamadı.", color = MaterialTheme.colorScheme.error)
}

private fun sampledServerBitmap(file: File): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 100_000_000L) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

internal fun cacheServerMedia(cache: File, url: String, token: String, kind: MediaKind): File {
    val limit = if (kind == MediaKind.HTML) 2L * 1024 * 1024 else 64L * 1024 * 1024
    val directory = File(cache, "conversation-media").apply { mkdirs() }
    directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
    check(directory.listFiles().orEmpty().sumOf { it.length() } <= 128L * 1024 * 1024 - limit)
    val target = File.createTempFile("media-", ".bin", directory)
    try {
        val http = okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()
        val request = okhttp3.Request.Builder().url(url)
            .header("X-Hermes-Session-Token", token)
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful)
            val body = checkNotNull(response.body)
            check(body.contentLength() <= limit)
            body.byteStream().use { input -> target.outputStream().use { output ->
                val bytes = ByteArray(8192); var total = 0L
                while (true) {
                    val read = input.read(bytes); if (read < 0) break
                    total += read; check(total <= limit); output.write(bytes, 0, read)
                }
            } }
        }
        return target
    } catch (e: Exception) { target.delete(); throw e }
}
