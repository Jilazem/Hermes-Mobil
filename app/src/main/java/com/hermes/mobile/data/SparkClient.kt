package com.hermes.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * sparkDash istemcisi — DGX Spark (GB10) makinelerinin canlı ölçümleri.
 *
 * sparkDash (github.com/MiaAI-Lab/sparkDash) sunucuda 5555'te koşuyor ve
 * node1/node2 kayıtlı. Buradan yalnız **okuyoruz**: makine ekleme/silme
 * panonun kendi arayüzünde yapılıyor, telefondan yapılandırma değiştirmek
 * yanlışlıkla izlemeyi bozmanın kolay yolu olurdu.
 *
 * ⚠️ **sparkDash'in HTTP/WS API'sinde kimlik doğrulama yok** (kendi README'si
 * söylüyor). Bu yüzden 5555 hiçbir zaman internete açılmadı. Ev ağında doğrudan
 * o porta gidiliyor; dışarıdan ise Hermes'in ters vekilindeki `/spark-api`
 * yolundan geçiliyor ve **kapıyı Caddy tutuyor**: Hermes tokeni başlığı şart,
 * yalnız GET geçiyor. [token] o yüzden var — LAN'da gereksiz ama göndermek
 * zararsız, iki yolu ayrı kod yollarına bölmekten iyi.
 */
class SparkClient(
    private val baseUrl: String,
    private val token: String = "",
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        // Uzak Spark'lar SSH üzerinden okunuyor; ilk çağrı yavaş olabiliyor.
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl.trimEnd('/') + path).get()
        if (token.isNotBlank()) req.header("X-Hermes-Session-Token", token)
        http.newCall(req.build())
            .execute()
            .use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    DiagLog.w("spark", "GET $path -> ${res.code}")
                    throw HermesApiException(res.code, path, body.take(200))
                }
                body
            }
    }

    suspend fun sparks(): List<SparkEntry> =
        json.decodeFromString<SparksResponse>(get("/api/sparks")).sparks

    suspend fun metrics(id: String): SparkMetrics =
        json.decodeFromString(get("/api/sparks/$id/metrics"))
}
