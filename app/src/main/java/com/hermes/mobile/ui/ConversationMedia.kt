package com.hermes.mobile.ui

import android.media.MediaPlayer
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hermes.mobile.data.safeExternalUrl

/** Playback is explicit, scoped to the visible message and stopped when leaving the app. */
@Composable
internal fun ConversationAudio(url: String, label: String) {
    val safe = safeExternalUrl(url) ?: return
    var player by remember(safe) { mutableStateOf<MediaPlayer?>(null) }
    var loading by remember(safe) { mutableStateOf(false) }
    var error by remember(safe) { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun stop() { player?.let { runCatching { it.release() } }; player = null; loading = false }
    DisposableEffect(safe, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(label.ifBlank { "Ses dosyası" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    if (player != null) stop() else {
                        error = null; loading = true
                        val candidate = MediaPlayer()
                        player = candidate
                        candidate.setOnPreparedListener {
                            if (player === it) { loading = false; it.start() }
                        }
                        candidate.setOnCompletionListener { if (player === it) stop() }
                        candidate.setOnErrorListener { current, _, _ ->
                            if (player === current) { stop(); error = "Ses açılamadı. Bağlantıyı denetleyin." }; true
                        }
                        runCatching { candidate.setDataSource(safe); candidate.prepareAsync() }
                            .onFailure { stop(); error = "Ses açılamadı. Bağlantıyı denetleyin." }
                    }
                }) { Text(if (loading) "Yükleniyor · Durdur" else if (player != null) "Durdur" else "Dinle") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Generated HTML is a document preview: no scripts, network, local files or native bridge. */
@Composable
internal fun HtmlDocumentPreview(html: String, onOpenLink: (String) -> Unit) {
    var open by remember(html) { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("HTML önizlemesini aç") }
    if (open) Dialog(onDismissRequest = { open = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { open = false }) { Text("Kapat") }
                }
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).height(500.dp),
                    factory = { context -> WebView(context).apply {
                        settings.javaScriptEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.blockNetworkLoads = true
                        settings.domStorageEnabled = false
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                if (request?.isForMainFrame == true && request.hasGesture())
                                    safeExternalUrl(request.url.toString())?.let(onOpenLink)
                                return true
                            }
                        }
                        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    } },
                    onRelease = { it.stopLoading(); it.destroy() },
                )
            }
        }
    }
}
