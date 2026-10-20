package com.hermes.mobile.data

import com.hermes.mobile.BuildConfig
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
 * `GET /api/sessions/{id}`, `POST /api/stop`.
 *
 * Artemis LAN kapısı (iş #1020): Artemis'in kendi kimlik doğrulaması yoktur;
 * bu yüzden istemci tarafında iki kip vardır:
 *  - **strict (release, [BuildConfig.ARTEMIS_STRICT])**: yalnız `https://` +
 *    private/loopback host + zorunlu token. `http://` ya da kimliksiz istek
 *    inşa anında ([ArtemisPolicyViolation]) reddedilir — hiçbir paket ağa
 *    çıkmadan önce.
 *  - **debug (strict=false)**: mevcut davranış — private LAN adresinde düz
 *    `http` + kimliksiz erişim serbest.
 * Token varsa her iki kipte de `Authorization: Bearer` başlığında taşınır
 * (URL'de sır yok).
 */
class ArtemisClient(
    baseUrl: String,
    authToken: String? = null,
    strict: Boolean = BuildConfig.ARTEMIS_STRICT,
) {

    val base: String = stripQueryAndFragment(
        baseUrl.trim().let { if (it.contains("://")) it else "http://$it" },
    )

    private val token: String = authToken.orEmpty().trim()
    private val strictMode: Boolean = strict

    init {
        // İnşa anı kapısı: geçersiz uçta istemci hiç var olamaz.
        enforce(base, token, strictMode)
    }

    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun call(method: String, path: String, body: JsonObject? = null): JsonElement =
        withContext(Dispatchers.IO) {
            // Çalışma anı kapısı: politika her istekte yeniden doğrulanır;
            // ihlalde istek daha OkHttp'ye varmadan düşer.
            val req = buildRequest(base, token, strictMode, method, path, body)
            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw ArtemisHttpException(res.code, "Artemis $path → HTTP ${res.code} ${text.take(160)}")
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

    suspend fun task(id: String): ArtemisLogic.Task = try {
        ArtemisLogic.parseTask(call("GET", "/api/sessions/$id").jsonObject, id)
    } catch (e: ArtemisHttpException) {
        if (e.code != 404) throw e
        // The official client also consults the scheduler during admission.
        ArtemisLogic.parseLiveTask(call("GET", "/api/status").jsonObject, id)
    }

    suspend fun stop(id: String) {
        val response = call("POST", "/api/stop", buildJsonObject { put("session_id", id) }).jsonObject
        check(response["status"]?.toString()?.trim('"')?.lowercase() == "stopped") {
            "Artemis görevin durduğunu doğrulamadı; görev sürüyor olabilir."
        }
    }

    companion object {

        /**
         * Uç politikası. Her iki kipte de: http/https şeması, userInfo yok,
         * private/loopback host. Strict'te ek olarak: https zorunlu + token
         * zorunlu. İhlalde [ArtemisPolicyViolation].
         */
        internal fun enforce(base: String, token: String, strict: Boolean) {
            val uri = runCatching { java.net.URI(base) }.getOrNull()
            val schemeOk = uri != null && if (strict) {
                uri.scheme == "https"
            } else {
                uri.scheme in setOf("http", "https")
            }
            val host = uri?.host
            val hostOk = host != null && isPrivateHost(host)
            val tokenOk = !strict || token.isNotBlank()
            if (uri == null || !schemeOk || uri.userInfo != null || !hostOk || !tokenOk) {
                throw ArtemisPolicyViolation(
                    buildString {
                        append("Artemis endpoint rejected (strict=").append(strict).append("): ")
                        if (uri == null) append("unparseable URL; ")
                        if (!schemeOk) append(if (strict) "https:// required, " else "http/https required, ")
                        if (uri?.userInfo != null) append("userinfo not allowed, ")
                        if (!hostOk) append("private/loopback host required, ")
                        if (!tokenOk) append("token required, ")
                    }.trimEnd(',', ' '),
                )
            }
        }

        /**
         * Politikayı denetleyip isteği kurar — token **yalnız**
         * `Authorization: Bearer` başlığında, URL'de hiçbir iz yok. `call` her
         * seferinde bunu kullanır; testler de bu yüzden gerçek istek
         * başlıklarını ağa çıkmadan doğrulayabilir.
         */
        internal fun buildRequest(
            base: String,
            token: String,
            strict: Boolean,
            method: String,
            path: String,
            body: JsonObject?,
        ): Request {
            val bearer = token.trim()
            enforce(base, bearer, strict)
            val rb = body?.toString()?.toRequestBody("application/json".toMediaType())
            val b = Request.Builder()
                .url(base + path)
                .method(method, rb)
                .header("Accept", "application/json")
            if (bearer.isNotBlank()) b.header("Authorization", "Bearer $bearer")
            return b.build()
        }
    }
}

/** Uç politikası ihlali (http/kimliksiz istek release'de buraya düşer). */
class ArtemisPolicyViolation(message: String) : IllegalArgumentException(message)

private class ArtemisHttpException(val code: Int, message: String) : IOException(message)