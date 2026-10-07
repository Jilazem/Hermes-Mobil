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

/** Immutable, public, Apache-2.0 model; pins bytes and SHA-256 inside the APK. */
object EmaModelStore {
    const val REVISION = "2d5c71c64446069c72404471d71ef451fbaff69d"
    private const val BASE = "https://raw.githubusercontent.com/Jilazem/Hermes-Mobil/$REVISION/"
    data class ModelFile(val name:String,val bytes:Long,val sha256:String)
    val files = listOf(
        ModelFile("config.json",584,"e8d20ee8458fc4c583af7ced3e52e151c2efd8ddac92443fa39ab73b346fb512"),
        ModelFile("text.onnx",4808338,"a5418a36318ae00048f2506c5c0e17a70da3834b59a35f3f94955f8eac2765a2"),
        ModelFile("sound.onnx",18134687,"553fb64ecd2e2178b88fff9de0b80f949601a09f132bea1528bcd0c56010502c"),
        ModelFile("decoder.onnx",12040974,"89c6b502181b39aa69f208bfe5715e6f8de2790ba69de7a57c63cbfb2be61fdf"),
    )
    val totalBytes = files.sumOf { it.bytes }
    fun directory(context:Context) = File(context.filesDir,"ema-offline/v1")
    fun present(context:Context):Boolean = files.all { File(directory(context),it.name).let { f->f.isFile && f.length()==it.bytes } }
    fun verified(dir:File):Boolean = files.all { File(dir,it.name).let { f-> f.isFile && f.length()==it.bytes && LocalTtsDownloader.sha256Of(f)==it.sha256 } }
    fun bytesOnDisk(context:Context):Long = files.sumOf { m ->
        val full=File(directory(context),m.name); val part=File(directory(context),m.name+".part")
        if(full.length()==m.bytes) m.bytes else part.length().coerceAtMost(m.bytes)
    }
    private val mutex=Mutex()
    private val http=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).build()

    suspend fun delete(context:Context) = mutex.withLock { directory(context).deleteRecursively(); Unit }

    suspend fun download(context:Context,onProgress:(Long)->Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dir=directory(context); check(dir.mkdirs() || dir.isDirectory) { "Model klasörü açılamadı" }
            var done=0L
            for (meta in files) {
                ensureActive()
                val target=File(dir,meta.name)
                if(target.length()==meta.bytes && LocalTtsDownloader.sha256Of(target)==meta.sha256) {
                    done+=meta.bytes; onProgress(done); continue
                }
                target.delete()
                val part=File(dir,meta.name+".part")
                if(part.length()>meta.bytes) part.delete()
                var offset=part.length()
                if(offset<meta.bytes) {
                    val call=http.newCall(Request.Builder().url(BASE+meta.name).apply {
                        if(offset>0) header("Range","bytes=$offset-")
                    }.build())
                    val job=currentCoroutineContext()[Job]!!
                    @OptIn(InternalCoroutinesApi::class)
                    val cancel=job.invokeOnCompletion(onCancelling=true,invokeImmediately=true) { if(job.isCancelled) call.cancel() }
                    try {
                        call.execute().use { response ->
                            if(response.code==200) offset=0
                            else if(response.code!=206 || !response.header("Content-Range").orEmpty().startsWith("bytes $offset-"))
                                throw IOException("EMA indirilemedi: HTTP ${response.code}")
                            val body=response.body ?: throw IOException("EMA indirmesi boş")
                            java.io.FileOutputStream(part,offset>0).use { output ->
                                body.byteStream().use { input ->
                                    val buffer=ByteArray(64*1024)
                                    var written=offset
                                    while(true) {
                                        ensureActive()
                                        val n=input.read(buffer); if(n<0) break
                                        if(written+n>meta.bytes) { part.delete(); throw IOException("EMA dosya boyutu geçersiz") }
                                        output.write(buffer,0,n); written+=n; onProgress(done+written)
                                    }
                                    output.fd.sync()
                                }
                            }
                        }
                    } finally { cancel.dispose() }
                }
                ensureActive()
                if(part.length()!=meta.bytes || LocalTtsDownloader.sha256Of(part)!=meta.sha256) {
                    part.delete(); throw IOException("EMA dosyası doğrulanamadı. Yeniden indir.")
                }
                check(part.renameTo(target)) { "EMA dosyası kaydedilemedi" }
                done+=meta.bytes; onProgress(done)
            }
            check(verified(dir)) { "EMA model doğrulaması başarısız" }
        }
    }
}
