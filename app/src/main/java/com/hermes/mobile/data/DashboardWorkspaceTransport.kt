package com.hermes.mobile.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Stable across LAN/remote failover. Neither URLs nor raw credentials become storage names. */
fun dashboardWorkspaceScope(profile: ServerProfile): String {
    val tokenHash = MessageDigest.getInstance("SHA-256").digest(profile.token.trim().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return MessageDigest.getInstance("SHA-256").digest("${profile.id}\n$tokenHash".toByteArray(Charsets.UTF_8))
        .take(12).joinToString("") { "%02x".format(it) }
}

data class DashboardWorkspaceConfig(val profileId: String, val scopeKey: String, val candidates: List<String>) {
    companion object {
        fun from(profile: ServerProfile): DashboardWorkspaceConfig {
            require(profile.token.isNotBlank() && profile.token.none { it.isISOControl() }) { "Seçili Dashboard profilinin erişim tokenı gerekli." }
            val candidates = profile.candidates.distinct().map { candidate ->
                val url = candidate.toHttpUrlOrNull() ?: error("Seçili Dashboard adresi geçersiz.")
                require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
                    "Dashboard adresinde kullanıcı bilgisi veya token kullanmayın."
                }
                url.toString().trimEnd('/')
            }
            require(candidates.isNotEmpty()) { "Seçili Dashboard adresi gerekli." }
            return DashboardWorkspaceConfig(profile.id, dashboardWorkspaceScope(profile), candidates)
        }
    }
}

data class DashboardWorkspaceCapabilities(val apiVersion: Int, val bots: Boolean, val devices: Boolean)
class DashboardWorkspaceApiException(val statusCode: Int) : IOException(when (statusCode) {
    401, 403 -> "Seçili Dashboard profilinin erişim bilgileri kabul edilmedi (HTTP $statusCode)."
    404 -> "Bu Dashboard Mobil çalışma alanı yolunu sunmuyor; sunucu entegrasyonunu kontrol edin."
    else -> "Dashboard çalışma alanı isteği reddedildi (HTTP $statusCode)."
})
class DashboardWorkspaceProtocolException : IOException("Dashboard beklenen Mobil çalışma alanı yanıtını doğrulamadı.")
class DashboardWorkspaceOutcomeUnknownException(cause: Throwable? = null) : IOException(
    "İstek Dashboard'a ulaşmış olabilir; sonuç doğrulanamadı. Tekrar göndermeden önce geçmişi kontrol edin.", cause,
)

/** One selected Dashboard profile is the only credential source. Writes never fail over or replay. */
class DashboardWorkspaceTransport(private val profile: ServerProfile) {
    val config = DashboardWorkspaceConfig.from(profile)
    @Volatile private var selectedBase = profile.activeUrl?.takeIf { it in config.candidates } ?: config.candidates.first()
    val activeBase: String get() = selectedBase
    private val token = profile.token.trim()
    private val writeHttp = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(130, TimeUnit.SECONDS).callTimeout(140, TimeUnit.SECONDS).build()
    private val readHttp = writeHttp.newBuilder().connectionPool(ConnectionPool()).retryOnConnectionFailure(true).build()

    suspend fun capabilities(): DashboardWorkspaceCapabilities {
        val response = request("GET", "/mobile-api/capabilities")
        val version = (response["api_version"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        val bots = (response["bots"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
        val devices = (response["devices"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
        if (version != 1 || bots == null || devices == null) throw DashboardWorkspaceProtocolException()
        return DashboardWorkspaceCapabilities(version, bots, devices)
    }

    suspend fun request(method: String, path: String, payload: JsonElement? = null): JsonObject {
        require(path.startsWith("/mobile-api/") && !path.contains('\\') && '#' !in path &&
            path.substringBefore('?').split('/').none { it == ".." || it.equals("%2e%2e", true) }) { "Geçersiz Dashboard çalışma alanı yolu." }
        val verb = method.uppercase(Locale.ROOT)
        val mutation = verb !in setOf("GET", "HEAD")
        val candidates = if (mutation) listOf(selectedBase)
            else AddressHealth.plan(profile.id, config.candidates, profile.activeUrl ?: selectedBase)
        var last: IOException? = null
        for (base in candidates) {
            try {
                val result = call(base, verb, path, payload, mutation)
                selectedBase = base
                ServerProfile.remember(profile.id, base)
                AddressHealth.noteSuccess(profile.id, base)
                return result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: DashboardWorkspaceOutcomeUnknownException) { throw error }
            catch (error: DashboardWorkspaceProtocolException) { throw error }
            catch (error: DashboardWorkspaceApiException) {
                if (mutation || error.statusCode !in setOf(502, 503, 504)) throw error
                last = error
            } catch (error: IOException) {
                if (mutation) throw DashboardWorkspaceOutcomeUnknownException(error)
                AddressHealth.noteFailure(profile.id, base, "workspace unreachable")
                last = error
            }
        }
        throw last ?: IOException("Dashboard çalışma alanına ulaşılamadı.")
    }

    private suspend fun call(base: String, method: String, path: String, payload: JsonElement?, mutation: Boolean): JsonObject {
        val body = if (method in setOf("GET", "HEAD")) null else
            (payload ?: buildJsonObject {}).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(base + path).header(HermesClient.SESSION_HEADER, token)
            .header("Accept", "application/json").method(method, body).build()
        return suspendCancellableCoroutine { continuation ->
            val call = (if (mutation) writeHttp else readHttp).newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        if (mutation) DashboardWorkspaceOutcomeUnknownException(e)
                        else IOException("Dashboard bağlantısı kurulamadı.", e))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use { reply ->
                        val result = runCatching {
                            if (mutation && (reply.code >= 500 || reply.code in 300..399 || reply.code == 408))
                                throw DashboardWorkspaceOutcomeUnknownException()
                            if (!reply.isSuccessful) throw DashboardWorkspaceApiException(reply.code)
                            if (reply.code == 204) return@runCatching JsonObject(emptyMap())
                            val output = ByteArrayOutputStream()
                            reply.body?.byteStream()?.use { input ->
                                val chunk = ByteArray(8192)
                                while (true) {
                                    val count = input.read(chunk, 0, minOf(chunk.size, MAX_BYTES + 1 - output.size()))
                                    if (count < 0) break
                                    output.write(chunk, 0, count)
                                    if (output.size() > MAX_BYTES) throw DashboardWorkspaceProtocolException()
                                }
                            }
                            Json.parseToJsonElement(output.toString("UTF-8")) as? JsonObject ?: throw DashboardWorkspaceProtocolException()
                        }.recoverCatching { error ->
                            if (mutation && error !is DashboardWorkspaceApiException && error !is DashboardWorkspaceOutcomeUnknownException)
                                throw DashboardWorkspaceOutcomeUnknownException(error)
                            throw error
                        }
                        if (continuation.isActive) result.onSuccess { continuation.resume(it) }
                            .onFailure { continuation.resumeWithException(it) }
                    }
                }
            })
        }
    }
    private companion object { const val MAX_BYTES = 8 * 1024 * 1024 }
}
