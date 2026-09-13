package com.hermes.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Hermes dashboard REST istemcisi.
 *
 * Kimlik doğrulama, Hermes Desktop'ın kullandığı sözleşmenin aynısı:
 * her isteğe `X-Hermes-Session-Token: <token>` başlığı eklenir
 * (bkz. ~/.hermes/CANONICAL-PORTS.lock.md). Token asla loglanmaz.
 */
class HermesClient(private val profile: ServerProfile) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Denenecek adresler: son çalışan önce, sonra kalanlar.
     *
     * Ev dışındayken LAN adresi 15 sn zaman aşımına düşüyor; çalışan adresi
     * hatırlayıp öne almak her isteği o kadar bekletmemizi önlüyor.
     */
    private fun urlOrder(): List<String> {
        val all = profile.candidates
        val last = profile.activeUrl
        return if (last != null) listOf(last) + all.filterNot { it == last } else all
    }

    private fun request(base: String, path: String): Request.Builder =
        Request.Builder()
            .url(base + path)
            .header(SESSION_HEADER, profile.token)
            .header("Accept", "application/json")

    private suspend fun getRaw(path: String): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (base in urlOrder()) {
            try {
                http.newCall(request(base, path).get().build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    // HTTP hatası adresin yanlış olduğu anlamına gelmez (401 gibi) —
                    // ulaşabildiysek bu adres çalışıyordur, diğerini denemeye gerek yok.
                    ServerProfile.remember(profile.id, base)
                    if (!res.isSuccessful) {
                        DiagLog.w("http", "GET $path -> ${res.code} · ${body.take(160)}")
                        throw HermesApiException(res.code, path, body.take(300))
                    }
                    return@withContext body
                }
            } catch (e: HermesApiException) {
                throw e
            } catch (e: IOException) {
                // Hangi adresin denendiğini bilmek şart: LAN mı uzak mı
                // düştüğünü ayırt etmenin başka yolu yok.
                DiagLog.w("http", "GET $path · $base unreachable: ${e.message}")
                lastError = e
            }
        }
        ServerProfile.forget(profile.id)
        DiagLog.e("http", "GET $path - no address reachable")
        throw lastError ?: IOException("Ulaşılabilir adres yok")
    }

    /** PUT — postRaw ile aynı adres sırası ve hata işleyişi. */
    private suspend fun putRaw(path: String, jsonBody: String): String =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null
            for (base in urlOrder()) {
                try {
                    val req = request(base, path)
                        .put(jsonBody.toRequestBody("application/json".toMediaType()))
                        .build()
                    http.newCall(req).execute().use { res ->
                        val body = res.body?.string().orEmpty()
                        ServerProfile.remember(profile.id, base)
                        if (!res.isSuccessful) throw HermesApiException(res.code, path, body.take(300))
                        return@withContext body
                    }
                } catch (e: IOException) {
                    lastError = e
                }
            }
            throw lastError ?: IOException("sunucuya ulaşılamadı")
        }

    private suspend fun postRaw(path: String, jsonBody: String = "{}"): String =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null
            for (base in urlOrder()) {
                try {
                    val body = jsonBody.toRequestBody(JSON_MEDIA)
                    http.newCall(request(base, path).post(body).build()).execute().use { res ->
                        val text = res.body?.string().orEmpty()
                        ServerProfile.remember(profile.id, base)
                        if (!res.isSuccessful) {
                            throw HermesApiException(res.code, path, text.take(300))
                        }
                        return@withContext text
                    }
                } catch (e: HermesApiException) {
                    throw e
                } catch (e: IOException) {
                    lastError = e
                }
            }
            ServerProfile.forget(profile.id)
            throw lastError ?: IOException("Ulaşılabilir adres yok")
        }

    suspend fun status(): HermesStatus = json.decodeFromString(getRaw("/api/status"))

    suspend fun systemStats(): SystemStats = json.decodeFromString(getRaw("/api/system/stats"))

    suspend fun sessions(): List<HermesSession> =
        json.decodeFromString<SessionsResponse>(getRaw("/api/sessions")).sessions

    suspend fun sessionStats(): SessionStats = json.decodeFromString(getRaw("/api/sessions/stats"))

    /** Bir oturumun mesaj dökümü. Uzun oturumlarda ağır olabilir. */
    suspend fun sessionMessages(id: String): List<SessionMessage> {
        val encoded = java.net.URLEncoder.encode(id, "UTF-8")
        return json.decodeFromString<SessionMessagesResponse>(
            getRaw("/api/sessions/$encoded/messages")
        ).messages
    }

    suspend fun profiles(): List<HermesProfile> =
        json.decodeFromString<ProfilesResponse>(getRaw("/api/profiles")).profiles

    suspend fun activeProfile(): ActiveProfile =
        json.decodeFromString(getRaw("/api/profiles/active"))

    /** Yönetim profilini değiştirir — listeler ve ayarlar bu profile bakar. */
    suspend fun setActiveProfile(name: String) {
        postRaw("/api/profiles/active", """{"name":"$name"}""")
    }

    suspend fun modelOptions(): ModelOptions = json.decodeFromString(getRaw("/api/model/options"))

    suspend fun modelInfo(): ModelInfo = json.decodeFromString(getRaw("/api/model/info"))

    suspend fun setModel(provider: String, model: String) {
        postRaw(
            "/api/model/set",
            json.encodeToString(
                ModelSetRequest.serializer(),
                ModelSetRequest(provider = provider, model = model),
            ),
        )
    }

    /**
     * Görseli gateway'in okuyabildiği `HERMES_HOME/images/` altına yazar.
     *
     * Dönen yol daha sonra `/image <path>` komutuyla ajana verilir — dashboard'ın
     * yapıştırma akışının aynısı (`web/src/lib/chatImagePaste.ts`).
     */
    suspend fun uploadChatImage(dataUrl: String, filename: String): ChatImageUploadResult =
        json.decodeFromString(
            postRaw(
                "/api/chat/image-upload",
                json.encodeToString(
                    ChatImageUploadRequest.serializer(),
                    ChatImageUploadRequest(dataUrl = dataUrl, filename = filename),
                ),
            )
        )

    /** Görsel olmayan dosyaları yönetilen dosya alanına yükler. */
    suspend fun uploadManagedFile(
        bytes: ByteArray,
        filename: String,
        targetDir: String = "uploads",
    ): String = withContext(Dispatchers.IO) {
        uploadManagedFileBlocking(bytes, filename, targetDir)
    }

    /**
     * Dosyayı DISKTEN yükler (HIGH-1 teli): paylaşım staging kopyası gibi
     * büyük içeriklerde readBytes ile belleğe almak OOM riski (denetmen #5);
     * OkHttp asRequestBody dosyayı parça parça akıtır.
     */
    suspend fun uploadManagedFile(file: java.io.File, filename: String, targetDir: String = "uploads"): String =
        withContext(Dispatchers.IO) {
            val safeName = filename.replace(Regex("""[^A-Za-z0-9._-]+"""), "_")
            val target = "$targetDir/$safeName"
            val body = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("path", target)
                .addFormDataPart("overwrite", "true")
                .addFormDataPart(
                    "file",
                    safeName,
                    file.asRequestBody("application/octet-stream".toMediaType()),
                )
                .build()
            val base = profile.activeUrl ?: profile.normalizedUrl
            http.newCall(request(base, "/api/files/upload-stream").post(body).build()).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw HermesApiException(res.code, "/api/files/upload-stream", text.take(300))
                target
            }
        }

    private fun uploadManagedFileBlocking(bytes: ByteArray, filename: String, targetDir: String): String {
        val safeName = filename.replace(Regex("""[^A-Za-z0-9._-]+"""), "_")
        val target = "$targetDir/$safeName"
        val body = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("path", target)
            .addFormDataPart("overwrite", "true")
            .addFormDataPart(
                "file",
                safeName,
                bytes.toRequestBody("application/octet-stream".toMediaType()),
            )
            .build()
        val base = profile.activeUrl ?: profile.normalizedUrl
        return http.newCall(request(base, "/api/files/upload-stream").post(body).build()).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw HermesApiException(res.code, "/api/files/upload-stream", text.take(300))
            target
        }
    }

    // ── Pano bölümleri ────────────────────────────────────────────────

    /**
     * Yönetilen kök altındaki dizin listesi.
     *
     * `path` boşsa sunucu kökten başlıyor. Kök dışına çıkan yollar 403 dönüyor —
     * bu kasıtlı, sunucu dosya sistemini olduğu gibi açmıyor.
     */
    suspend fun files(path: String? = null): FileListing {
        val q = path?.takeIf { it.isNotBlank() }
            ?.let { "?path=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
        return json.decodeFromString(getRaw("/api/files$q"))
    }

    suspend fun logs(file: String = "agent", lines: Int = 200): LogResponse =
        json.decodeFromString(getRaw("/api/logs?file=$file&lines=$lines"))

    // ── Bakım (maintenance): detached `hermes doctor` / `hermes update` ──

    /**
     * `hermes doctor` (ve `--fix`) detached olarak başlatır. `fix = true`
     * sunucuya `{"fix": true}` olarak gider ve `hermes doctor --fix` çalışır.
     *
     * @return çocuk sürecin PID'si / "zaten çalışıyor" durumu — bitiş kodu
     *   için [maintenanceStatus] ile yoklama gerekir.
     */
    suspend fun maintenanceDoctor(fix: Boolean = false): MaintenanceStartResponse =
        json.decodeFromString(postRaw("/api/maintenance/doctor", """{"fix":$fix}"""))

    /** `hermes update` detached olarak başlatır. Aynı yoklama sözleşmesi. */
    suspend fun maintenanceUpdate(): MaintenanceStartResponse =
        json.decodeFromString(postRaw("/api/maintenance/update"))

    /** Son bakım çalıştırmasının durumu: `running`, `lastKind`, `exitCode`… */
    suspend fun maintenanceStatus(): MaintenanceStatusResponse =
        json.decodeFromString(getRaw("/api/maintenance/status"))

    /**
     * Bakım logunun son `lines` satırı. `logPath` sunucunun kaydettiği
     * dosya; yol yerine `GET /api/maintenance/log` uçtan okunur — mobil
     * tarafın sunucu dosya sistemine erişimi olmadığı için bu zorunlu.
     */
    suspend fun maintenanceLog(lines: Int = 200): LogResponse =
        json.decodeFromString(getRaw("/api/maintenance/log?lines=$lines"))

    /** Panel'in tam cron listesi (prompt, durum, zamanlama alanlarıyla). */
    suspend fun cronJobsFull(): List<CronJob> = json.decodeFromString(getRaw("/api/cron/jobs"))

    /**
     * Cron işlerinin kimlik+isim özü — oturum isimlendirmesi için. Sunucu
     * bu ucu ya düz dizi ya da `{"jobs":[...]}` nesnesi olarak döndürüyor;
     * iki shape de tolere edilir, tek bozuk kayıt listenin tamamını düşürmez.
     */
    suspend fun cronJobs(): List<CronJobInfo> {
        val element: JsonElement = Json.parseToJsonElement(getRaw("/api/cron/jobs"))
        val arr = when (element) {
            is JsonArray -> element
            // Sarmalı nesne: "jobs" anahtarı esas, yedek anahtarlara da bakılır.
            is JsonObject -> element["jobs"] as? JsonArray
                ?: element["data"] as? JsonArray
                ?: return emptyList()
            else -> return emptyList()
        }
        return arr.mapNotNull {
            runCatching { json.decodeFromJsonElement<CronJobInfo>(it) }.getOrNull()
        }.filter { it.id.isNotBlank() }
    }

    /**
     * Zamanlanmış işi duraklat / devam ettir.
     *
     * Yalnız bu ikisi var: `run` ve `toggle` uçları 405 dönüyor, `PUT` ise
     * tam gövde istiyor. Zamanlama düzenlemek için panoyu kullanmak gerekiyor —
     * telefondan yarım bir gövde göndermek işi bozabilirdi.
     */
    suspend fun cronPause(id: String) {
        postRaw("/api/cron/jobs/${java.net.URLEncoder.encode(id, "UTF-8")}/pause")
    }

    suspend fun cronResume(id: String) {
        postRaw("/api/cron/jobs/${java.net.URLEncoder.encode(id, "UTF-8")}/resume")
    }

    /**
     * İşi hemen çalıştırır.
     *
     * Uç adı `trigger` — `run` değil (o 405 dönüyor). Gerçekten iş başlatıyor,
     * bu yüzden arayüz önce onay soruyor: bir cron işi pahalı olabilir.
     */
    suspend fun cronTrigger(id: String) {
        postRaw("/api/cron/jobs/${java.net.URLEncoder.encode(id, "UTF-8")}/trigger")
    }

    /**
     * Zamanlamayı değiştirir.
     *
     * Gövde şeması sunucu kaynağından okundu (`CronJobUpdate { updates: dict }`),
     * deneme yanılmayla değil: canlı bir işe yarım gövde göndermek onu bozardı.
     * Yalnız `schedule` alanını yolluyoruz; kalan alanlar sunucuda korunuyor.
     */
    suspend fun cronSetSchedule(id: String, schedule: String) {
        val body = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put(
                    "updates",
                    kotlinx.serialization.json.buildJsonObject {
                        put("schedule", kotlinx.serialization.json.JsonPrimitive(schedule))
                    },
                )
            },
        )
        putRaw("/api/cron/jobs/${java.net.URLEncoder.encode(id, "UTF-8")}", body)
    }

    suspend fun skills(): List<SkillEntry> = json.decodeFromString(getRaw("/api/skills"))

    suspend fun mcpServers(): List<McpServer> =
        json.decodeFromString<McpServersResponse>(getRaw("/api/mcp/servers")).servers

    suspend fun webhooks(): WebhookInfo = json.decodeFromString(getRaw("/api/webhooks"))

    suspend fun pairing(): PairingInfo = json.decodeFromString(getRaw("/api/pairing"))

    /** Ham yapılandırma — düzenleme yok, yalnız okuma. */
    suspend fun configRaw(): String = getRaw("/api/config")

    suspend fun restartGateway(): String = postRaw("/api/gateway/restart")

    suspend fun startGateway(): String = postRaw("/api/gateway/start")

    suspend fun stopGateway(): String = postRaw("/api/gateway/stop")

    /**
     * Profilin ulaşılabilirliğini VE tokenin geçerliliğini ölçer.
     *
     * `/api/status` herkese açık olduğu için tek başına yeterli değil —
     * yanlış tokenle de 200 döner. Bu yüzden ardından token isteyen
     * `/api/sessions/stats` çağrılır; asıl kimlik kararını o verir.
     */
    suspend fun probe(): ProbeResult = try {
        val started = System.currentTimeMillis()
        val st = status()
        sessionStats()
        ProbeResult.Ok(st, System.currentTimeMillis() - started)
    } catch (e: HermesApiException) {
        ProbeResult.Fail(
            when (e.code) {
                401, 403 -> "Token reddedildi (${e.code})"
                404 -> "Adres bulunamadı — dashboard portu doğru mu?"
                else -> "Sunucu hatası ${e.code}"
            }
        )
    } catch (e: IOException) {
        val hint = if (profile.normalizedRemote.isBlank())
            " (ev ağı dışındaysan Sunucular'dan uzak adres ekle)" else ""
        ProbeResult.Fail("Ulaşılamıyor$hint")
    } catch (e: Exception) {
        ProbeResult.Fail("Beklenmeyen yanıt — ${e.message ?: e::class.simpleName}")
    }

    companion object {
        const val SESSION_HEADER = "X-Hermes-Session-Token"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

class HermesApiException(val code: Int, val path: String, val bodySnippet: String) :
    IOException("HTTP $code · $path")

sealed interface ProbeResult {
    data class Ok(val status: HermesStatus, val latencyMs: Long) : ProbeResult
    data class Fail(val reason: String) : ProbeResult
}
