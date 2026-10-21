package com.hermes.mobile.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Immutable, public, Apache-2.0 model; pins bytes and SHA-256 inside the APK.
 *
 * The pinned revision is a historical commit of the upstream repo; that commit
 * may disappear (repo renamed/removed). Therefore download() walks a source
 * chain — pinned raw URL, then branch raw URLs, then the paired Hermes gateway
 * (/api/files/download) — and accepts bytes only if SHA-256 matches the pin.
 * No source is trusted: the hash is the single acceptance gate.
 */
object EmaModelStore {
    const val REVISION = "2d5c71c64446069c72404471d71ef451fbaff69d"
    private const val RAW = "https://raw.githubusercontent.com/Jilazem/Hermes-Mobil"
    /** GitHub refs tried in order; all bytes still must match the pinned SHA. */
    private val REFS = listOf(REVISION, "main", "master")
    /** Path of the model dir inside the gateway's managed-files root. */
    private const val SERVER_DIR = "ema-offline/v1"

    data class ModelFile(val name: String, val bytes: Long, val sha256: String)

    val files = listOf(
        ModelFile("config.json", 584, "e8d20ee8458fc4c583af7ced3e52e151c2efd8ddac92443fa39ab73b346fb512"),
        ModelFile("text.onnx", 4808338, "a5418a36318ae00048f2506c5c0e17a70da3834b59a35f3f94955f8eac2765a2"),
        ModelFile("sound.onnx", 18134687, "553fb64ecd2e2178b88fff9de0b80f949601a09f132bea1528bcd0c56010502c"),
        ModelFile("decoder.onnx", 12040974, "89c6b502181b39aa69f208bfe5715e6f8de2790ba69de7a57c63cbfb2be61fdf"),
    )
    val totalBytes = files.sumOf { it.bytes }

    /** One gateway the phone may pull model bytes from (token never logged). */
    data class Gateway(val base: String, val token: String) {
        /** Display label: origin only, no token, no query string. */
        fun label(): String = runCatching {
            val u = java.net.URI(base)
            "${u.host ?: base}${if (u.port > 0) ":" + u.port else ""}"
        }.getOrDefault(base)
    }

    /** Per-file attempt record, shown to the user when everything fails. */
    data class Attempt(val source: String, val result: String)

    class DownloadFailure(val fileName: String, val attempts: List<Attempt>, cause: Throwable? = null) :
        IOException(
            buildString {
                append(fileName).append(" indirilemedi")
                if (attempts.isNotEmpty()) {
                    append(" · denenen kaynaklar: ")
                    append(attempts.joinToString("; ") { "${it.source} → ${it.result}" })
                }
                cause?.message?.takeIf { it.isNotBlank() }?.let { append(" · son hata: ").append(it) }
            },
            cause,
        )

    fun directory(context: Context) = File(context.filesDir, "ema-offline/v1")

    fun present(context: Context): Boolean =
        files.all { File(directory(context), it.name).let { f -> f.isFile && f.length() == it.bytes } }

    fun verified(dir: File): Boolean = files.all { m ->
        File(dir, m.name).let { f ->
            f.isFile && f.length() == m.bytes && LocalTtsDownloader.sha256Of(f) == m.sha256
        }
    }

    fun bytesOnDisk(context: Context): Long = files.sumOf { m ->
        val full = File(directory(context), m.name)
        val part = File(directory(context), m.name + ".part")
        if (full.length() == m.bytes) m.bytes else part.length().coerceAtMost(m.bytes)
    }

    /** Gateways derived from the paired server profiles (active first). */
    fun gateways(context: Context): List<Gateway> {
        val store = ServerProfileStore(context)
        val active = store.active()
        val bases = LinkedHashSet<String>()
        active?.let { p ->
            bases += p.activeUrl ?: p.normalizedUrl
            bases += p.candidates
        }
        store.list().forEach { p -> bases += p.candidates }
        val token = active?.token.orEmpty().ifBlank {
            SettingsStore(context).settings.value.emaToken
        }
        return bases.filter { it.isNotBlank() }.map { Gateway(it.trimEnd('/'), token) }
    }

    private val mutex = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun delete(context: Context) = mutex.withLock {
        directory(context).deleteRecursively(); Unit
    }

    /** Downloads all four files, walking the source chain per file.
     * @throws DownloadFailure with per-file HTTP codes and attempted sources.
     */
    suspend fun download(
        context: Context,
        gateways: List<Gateway> = gateways(context),
        onProgress: (Long) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dir = directory(context)
            check(dir.mkdirs() || dir.isDirectory) { "Model klasörü açılamadı: ${dir.absolutePath}" }
            var done = 0L
            for (meta in files) {
                ensureActive()
                val target = File(dir, meta.name)
                if (target.length() == meta.bytes && LocalTtsDownloader.sha256Of(target) == meta.sha256) {
                    done += meta.bytes; onProgress(done); continue
                }
                target.delete()
                val part = File(dir, meta.name + ".part")
                if (part.length() > meta.bytes) part.delete()
                val attempts = mutableListOf<Attempt>()
                var ok = false
                for (src in sources(meta.name, gateways)) {
                    ensureActive()
                    try {
                        fetch(src, meta, part, done, onProgress)
                        if (part.length() != meta.bytes) {
                            attempts += Attempt(src.label, "boyut ${part.length()} ≠ ${meta.bytes}")
                            part.delete(); continue
                        }
                        val sha = LocalTtsDownloader.sha256Of(part)
                        if (sha != meta.sha256) {
                            attempts += Attempt(src.label, "sha256 uyuşmadı (${sha?.take(12)}…)")
                            part.delete(); continue
                        }
                        check(part.renameTo(target)) { "EMA dosyası kaydedilemedi: ${meta.name}" }
                        ok = true
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        attempts += Attempt(src.label, e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
                        DiagLog.w("ema", "${meta.name} · ${src.label} → ${attempts.last().result}")
                    }
                }
                if (!ok) throw DownloadFailure(meta.name, attempts)
                done += meta.bytes; onProgress(done)
            }
            check(verified(dir)) { "EMA model doğrulaması başarısız: dosyalar indi ama SHA-256 tutmuyor" }
        }
    }

    private data class Source(val label: String, val url: String, val headers: Map<String, String>)

    private fun sources(name: String, gateways: List<Gateway>): List<Source> {
        val list = mutableListOf<Source>()
        for (ref in REFS) list += Source(
            if (ref == REVISION) "github@${ref.take(7)}" else "github@$ref",
            "$RAW/$ref/$name",
            emptyMap(),
        )
        for (gw in gateways) list += Source(
            "gateway ${gw.label()}",
            "${gw.base}/api/files/download?path=$SERVER_DIR/$name&token=${gw.token}",
            buildMap {
                if (gw.token.isNotBlank()) put("X-Hermes-Session-Token", gw.token)
                put("Accept", "application/octet-stream")
            },
        )
        return list
    }

    private suspend fun fetch(
        src: Source,
        meta: ModelFile,
        part: File,
        done: Long,
        onProgress: (Long) -> Unit,
    ) {
        var offset = part.length()
        val request = Request.Builder().url(src.url).apply {
            src.headers.forEach { (k, v) -> header(k, v) }
            if (offset > 0) header("Range", "bytes=$offset-")
        }.build()
        val call = http.newCall(request)
        val job = currentCoroutineContext()[Job]!!
        @OptIn(InternalCoroutinesApi::class)
        val cancel = job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) {
            if (job.isCancelled) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (response.code == 200) {
                    offset = 0
                } else if (response.code == 206) {
                    val range = response.header("Content-Range").orEmpty()
                    if (!range.startsWith("bytes $offset-")) {
                        throw IOException("HTTP 206 ama Content-Range '$range' beklenen 'bytes $offset-' değil")
                    }
                } else {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("HTTP ${response.code} · gövde boş")
                java.io.FileOutputStream(part, offset > 0).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var written = offset
                        while (true) {
                            job.ensureActive()
                            val n = input.read(buffer); if (n < 0) break
                            if (written + n > meta.bytes) {
                                part.delete()
                                throw IOException("sunucu ${meta.bytes} bayttan fazla gönderdi")
                            }
                            output.write(buffer, 0, n); written += n
                            onProgress(done + written)
                        }
                        output.fd.sync()
                    }
                }
            }
        } finally {
            cancel.dispose()
        }
    }
}
