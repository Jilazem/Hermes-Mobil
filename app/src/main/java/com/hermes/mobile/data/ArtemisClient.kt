package com.hermes.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Google Artemis sunucusunun (daemon, varsayılan port 8000) HTTP istemcisi.
 *
 * Resmî `artemis-client` Python paketinin kullandığı uçlar:
 * `GET /api/status`, `GET /api/devices`, `POST /api/run`,
 * `GET /api/sessions/{id}`, `POST /api/stop`. Artemis'in kendi kimlik
 * doğrulaması yok — daemon yalnız ev ağında açılmalı (bkz. docs/ARTEMIS.md).
 */
class ArtemisClient(baseUrl: String) {

    val base: String = stripQueryAndFragment(
        baseUrl.trim().let { if (it.contains("://")) it else "http://$it" },
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    private suspend fun call(method: String, path: String, body: JsonObject? = null): JsonElement =
        withContext(Dispatchers.IO) {
            val rb = body?.toString()?.toRequestBody(jsonType)
            val req = Request.Builder().url(base + path).method(method, rb)
                .header("Accept", "application/json").build()
            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw IOException("Artemis $path → HTTP ${res.code} ${text.take(160)}")
                json.parseToJsonElement(text.ifBlank { "{}" })
            }
        }

    /** Daemon ayakta mı (hızlı canlılık denetimi). */
    suspend fun ping(): Boolean = runCatching { call("GET", "/api/status") }.isSuccess

    suspend fun devices(): List<ArtemisLogic.Device> = ArtemisLogic.parseDevices(call("GET", "/api/devices"))

    /** Görevi gönderir, kabul edilen görev kimliğini döner. */
    suspend fun submit(goal: String, profile: String, deviceSerial: String?): String {
        val id = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("goal", goal)
            put("profile", profile)
            put("session_id", id)
            put("ingress", "hermes_mobile")
            if (!deviceSerial.isNullOrBlank()) put("device_serial", deviceSerial)
        }
        return ArtemisLogic.parseSubmit(call("POST", "/api/run", payload).jsonObject, id).getOrThrow()
    }

    suspend fun task(id: String): ArtemisLogic.Task =
        ArtemisLogic.parseTask(call("GET", "/api/sessions/$id").jsonObject, id)

    suspend fun stop(id: String) {
        call("POST", "/api/stop", buildJsonObject { put("session_id", id) })
    }
}
