package com.hermes.mobile.data.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One authenticated request, no redirect and no automatic write replay. */
class RemoteAgentHttp(private val connection: RemoteAgentConnection) {
    private val base = normalizeRemoteEndpoint(connection.baseUrl)
    private val http = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(65, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS).build()
    private val readHttp = http.newBuilder().connectionPool(ConnectionPool())
        .retryOnConnectionFailure(true).build()

    suspend fun request(
        method: String,
        path: String,
        payload: JsonObject? = null,
        mutation: Boolean = method.uppercase(Locale.ROOT) != "GET",
        requireAuth: Boolean = true,
    ): JsonObject {
        require(path.startsWith('/') && !path.startsWith("//") && '\\' !in path && '#' !in path) { "Geçersiz servis yolu." }
        if (requireAuth) require(connection.token.isNotBlank() && connection.token.none { it.isISOControl() }) {
            "Uzak cihazın erişim tokenını girin."
        }
        val verb = method.uppercase(Locale.ROOT)
        val body = if (verb == "GET" || verb == "HEAD") null else
            (payload ?: buildJsonObject {}).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val builder = Request.Builder().url(base + path)
            .header("Accept", "application/json").header("X-Mika-Client", "Hermes Mobile v7")
            .method(verb, body)
        if (requireAuth) builder.header("Authorization", "Bearer ${connection.token}")
        return suspendCancellableCoroutine { continuation ->
            val call = (if (verb == "GET" || verb == "HEAD") readHttp else http).newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        if (mutation) RemoteOutcomeUnknownException(error)
                        else IOException("Uzak cihaza ulaşılamadı; adresi, ağı ve servisin açık olduğunu kontrol edin.", error),
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { reply ->
                        val result = runCatching {
                            if (mutation && (reply.code >= 500 || reply.code in 300..399 || reply.code == 408))
                                throw RemoteOutcomeUnknownException()
                            val body = reply.body ?: throw RemoteProtocolException("Uzak cihaz boş yanıt verdi.")
                            require(body.contentLength() <= MAX_RESPONSE_BYTES) { "Uzak cihaz yanıtı boyut sınırını aştı." }
                            val bytes = ByteArrayOutputStream()
                            body.byteStream().use { input ->
                                val chunk = ByteArray(8192)
                                while (true) {
                                    val count = input.read(chunk, 0, minOf(chunk.size, MAX_RESPONSE_BYTES + 1 - bytes.size()))
                                    if (count < 0) break
                                    bytes.write(chunk, 0, count)
                                    if (bytes.size() > MAX_RESPONSE_BYTES) throw RemoteProtocolException("Uzak cihaz yanıtı boyut sınırını aştı.")
                                }
                            }
                            val decoded = runCatching { Json.parseToJsonElement(bytes.toString("UTF-8")) as? JsonObject }.getOrNull()
                            if (!reply.isSuccessful) {
                                val code = ((decoded?.get("error") as? JsonObject)?.get("code") as? JsonPrimitive)
                                    ?.contentOrNull?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,64}")) }
                                throw RemoteApiException(reply.code, code)
                            }
                            decoded ?: throw RemoteProtocolException("Bu adres beklenen JSON nesnesini sunmuyor.")
                        }.recoverCatching { error ->
                            if (mutation && error !is RemoteApiException && error !is RemoteOutcomeUnknownException)
                                throw RemoteOutcomeUnknownException(error)
                            throw error
                        }
                        if (continuation.isActive) result
                            .onSuccess { continuation.resume(it) }
                            .onFailure { continuation.resumeWithException(it) }
                    }
                }
            })
        }
    }

    private companion object { const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024 }
}
