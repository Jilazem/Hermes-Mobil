package com.hermes.mobile.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Local Mac EMA service; authenticated, cancellable, no alternative voice. */
class EmaTtsClient(private val base: String, private val token: String) {
    companion object {
        private val http = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS).callTimeout(420, TimeUnit.SECONDS).build()
    }

    private fun request(path: String, payload: String? = null): Request = Request.Builder()
        .url(base.trim().trimEnd('/') + path)
        .header(HermesClient.SESSION_HEADER, token)
        .apply { if (payload != null) post(payload.toRequestBody("application/json".toMediaType())) }
        .build()

    suspend fun health(): Boolean = exchange("/health", "") { response ->
        val body = response.body?.string().orEmpty()
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
        obj?.get("ok")?.toString() == "true" && obj["engine"]?.toString() == "\"ema-lightning\""
    }

    suspend fun speak(text: String): ByteArray = exchange("/speak", text) { response ->
        if (response.header("Content-Type")?.substringBefore(';') != "audio/wav") {
            throw IOException("EMA beklenen WAV sesini döndürmedi")
        }
        val bytes = response.body?.bytes() ?: throw IOException("EMA ses yanıtı boş")
        if (bytes.size < 44 || bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) != "RIFF") {
            throw IOException("EMA WAV yanıtı geçersiz")
        }
        bytes
    }

    suspend fun stream(text: String, onPcm: (ByteArray, Int) -> Unit) = exchange("/stream", text) { response ->
        val rate = response.header("X-Audio-Sample-Rate")?.toIntOrNull()
        if (rate !in listOf(8000, 16000, 24000, 48000) ||
            response.header("X-Audio-Format") != "pcm_s16le" ||
            response.header("X-Audio-Channels") != "1") throw IOException("EMA PCM biçimi geçersiz")
        val source = response.body?.source() ?: throw IOException("EMA ses yanıtı boş")
        // The HTTP transport may split a PCM sample; retain the odd byte.
        var tail: Byte? = null
        var total = 0L
        while (!source.exhausted()) {
            val block = source.readByteArray(minOf(source.buffer.size.takeIf { it > 0 } ?: 8192, 8192))
            val bytes = if (tail == null) block else byteArrayOf(tail!!) + block
            val even = bytes.size - bytes.size % 2
            tail = if (even != bytes.size) bytes.last() else null
            if (even > 0) { onPcm(bytes.copyOf(even), rate!!); total += even }
        }
        if (tail != null || total == 0L) throw IOException("EMA PCM yanıtı eksik")
    }

    private suspend fun <T> exchange(path: String, text: String, consume: (Response) -> T): T =
        suspendCancellableCoroutine { cont ->
            val id = UUID.randomUUID().toString()
            val payload = buildJsonObject {
                put("text", text.take(3000)); put("request_id", id)
                put("sample_rate", 48000); put("seed", 0)
            }.toString()
            val call = http.newCall(request(path, payload.takeUnless { path == "/health" }))
            cont.invokeOnCancellation {
                call.cancel()
                // The producer may still be in inference; explicitly drop its remaining work.
                http.newCall(request("/cancel", buildJsonObject { put("request_id", id) }.toString()))
                    .enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) = Unit
                        override fun onResponse(call: Call, response: Response) { response.close() }
                    })
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(IOException("EMA ses servisine ulaşılamadı", e))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!cont.isActive) return
                            if (!response.isSuccessful) throw IOException("EMA ses servisi HTTP ${response.code}")
                            val result = consume(response)
                            if (cont.isActive) cont.resume(result)
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }
                }
            })
        }
}
