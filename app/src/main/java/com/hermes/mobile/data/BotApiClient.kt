package com.hermes.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Dedicated bot-framework endpoint, not the gateway or phone relay. */
class BotApiClient(base: String, private val token: String) {
    private val baseUrl = base.trim().trimEnd('/')
    init {
        val uri = java.net.URI(baseUrl)
        require(uri.scheme in setOf("http", "https") && uri.host != null &&
            uri.userInfo == null && uri.query == null && uri.fragment == null) { "Geçerli HTTP(S) bot sunucusu gerekli" }
        require(token.isNotBlank()) { "Bot sunucusu erişim tokenı gerekli" }
    }
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(130, TimeUnit.SECONDS)
        .callTimeout(140, TimeUnit.SECONDS).build()

    suspend fun request(method: String, path: String, payload: JsonElement? = null): JsonObject = withContext(Dispatchers.IO) {
        require(path.startsWith("/api/"))
        val body = payload?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(baseUrl + path).header("Authorization", "Bearer $token")
            .method(method, body).build()
        http.newCall(req).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val data = if (text.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(text).jsonObject
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: ${data.text("message").ifBlank { "Bot isteği başarısız" }}")
            data
        }
    }
}

fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.rows(key: String): List<JsonObject> = (get(key) as? JsonArray)?.map { it.jsonObject }.orEmpty()
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
