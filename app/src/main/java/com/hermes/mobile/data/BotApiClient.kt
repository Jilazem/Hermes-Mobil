package com.hermes.mobile.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Dedicated bot-framework endpoint, not the gateway or phone relay. */
class BotApiClient(base: String, private val token: String, private val dashboard: DashboardWorkspaceTransport? = null) {
    private val baseUrl = base.trim().trimEnd('/')
    // Keep queues, session cursors and read markers separate for each server/account.
    val storageKey: String = dashboard?.config?.scopeKey ?: java.security.MessageDigest.getInstance("SHA-256")
        .digest("$baseUrl\n$token".toByteArray(Charsets.UTF_8))
        .take(12).joinToString("") { "%02x".format(it) }
    init {
        val uri = java.net.URI(baseUrl)
        require(uri.scheme in setOf("http", "https") && uri.host != null &&
            uri.userInfo == null && uri.query == null && uri.fragment == null) { "Geçerli HTTP(S) bot sunucusu gerekli" }
        require(token.isNotBlank()) { "Bot sunucusu erişim tokenı gerekli" }
    }
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        // A stale pooled socket must never force a non-idempotent chat write to be replayed.
        .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(130, TimeUnit.SECONDS)
        .callTimeout(140, TimeUnit.SECONDS).build()
    private val readHttp = http.newBuilder().connectionPool(ConnectionPool())
        .retryOnConnectionFailure(true).build()

    suspend fun request(method: String, path: String, payload: JsonElement? = null): JsonObject {
        require(path.startsWith("/api/"))
        dashboard?.let {
            try { return it.request(method, "/mobile-api/bot$path", payload) }
            catch (error: DashboardWorkspaceApiException) { throw BotApiException(error.statusCode, error.message.orEmpty()) }
            catch (error: DashboardWorkspaceOutcomeUnknownException) { throw BotWriteOutcomeUnknownException(error) }
        }
        val mutation = method.uppercase(java.util.Locale.ROOT) !in setOf("GET", "HEAD")
        val body = payload?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(baseUrl + path).header("Authorization", "Bearer $token")
            .method(method, body).build()
        return suspendCancellableCoroutine { continuation ->
            val call = (if (mutation) http else readHttp).newCall(req)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    if (continuation.isActive) continuation.resumeWithException(if (mutation) BotWriteOutcomeUnknownException(e) else e)
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        runCatching {
                            if (mutation && (it.code >= 500 || it.code in 300..399 || it.code == 408))
                                throw BotWriteOutcomeUnknownException()
                            val text = it.body?.string().orEmpty()
                            val data = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                            if (!it.isSuccessful) throw BotApiException(it.code, "Bot isteği reddedildi (HTTP ${it.code}).")
                            if (text.isBlank()) JsonObject(emptyMap())
                            else requireNotNull(data) { "Bot sunucusu beklenen JSON nesnesini döndürmedi" }
                        }.recoverCatching { error ->
                            if (mutation && error !is BotApiException && error !is BotWriteOutcomeUnknownException)
                                throw BotWriteOutcomeUnknownException(error)
                            throw error
                        }.onSuccess { data -> if (continuation.isActive) continuation.resume(data) }
                            .onFailure { error -> if (continuation.isActive) continuation.resumeWithException(error) }
                    }
                }
            })
        }
    }
    companion object {
        fun fromDashboard(profile: ServerProfile): BotApiClient = BotApiClient(
            profile.activeUrl ?: profile.normalizedUrl, profile.token, DashboardWorkspaceTransport(profile))
    }
}

class BotApiException(val statusCode: Int, message: String) : java.io.IOException(message)
class BotWriteOutcomeUnknownException(cause: Throwable? = null) : java.io.IOException(
    "Mesaj ulaşmış olabilir; sonucu doğrulanamadı. Yeniden göndermeden önce sohbet geçmişini kontrol edin.", cause,
)

fun botApiSegment(id: String): String = java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20")

fun botChatSession(response: JsonObject): JsonObject = (response["session"] as? JsonObject) ?: response
fun botSessionId(session: JsonObject): String = session.text("session_id").ifBlank { session.text("id") }

/** This backend has no per-message timestamps; compare actual assistant messages. */
fun botAssistantMessageKeys(sessions: List<JsonObject>): Set<String> = buildSet {
    sessions.forEach { session ->
        val occurrences = mutableMapOf<String, Int>()
        botVisibleMessages(session).filter { it.text("role") == "assistant" }.forEach { message ->
            val content = message.text("content").ifBlank { message.text("text") }
            val occurrence = occurrences.getOrDefault(content, 0)
            occurrences[content] = occurrence + 1
            val source = "${botSessionId(session)}\n$content\n$occurrence"
            add(java.security.MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) })
        }
    }
}

fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.rows(key: String): List<JsonObject> = (get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
fun botVisibleMessages(session: JsonObject): List<JsonObject> = session.rows("messages").filter { it.text("role") != "system" }
fun botDefinition(name: String, emoji: String, persona: String, model: String, think: String, welcome: String, tools: Set<String>): JsonObject {
    require(name.isNotBlank() && name.codePointCount(0, name.length) <= 48) { "Ad 1–48 karakter olmalı" }
    require(persona.isNotBlank() && persona.codePointCount(0, persona.length) <= 8000) { "Persona 1–8000 karakter olmalı" }
    require(emoji.codePointCount(0, emoji.length) <= 8 && model.codePointCount(0, model.length) <= 120 && welcome.codePointCount(0, welcome.length) <= 500 && tools.size <= 12) { "Alan sınırı aşıldı" }
    require(think in setOf("off", "low", "medium", "high"))
    return buildJsonObject {
        put("name", name); put("emoji", emoji); put("persona", persona); put("model", model)
        put("think_mode", think); put("welcome_message", welcome); put("tools", JsonArray(tools.map(::JsonPrimitive)))
    }
}
