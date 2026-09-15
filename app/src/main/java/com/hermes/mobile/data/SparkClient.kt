package com.hermes.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
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
 *
 * ── Tur-10 (F4): 403'ün kökü ve düzeltme ──────────────────────────────────
 * Sahada `GET /api/sparks → 403` görülüyordu. Mac'te ölçülen (bkz.
 * `denetim/tur10/spark-probe.json`):
 *  - `GET /spark-api/api/sparks` → **403** (Caddy `@spark_ok` kapısı
 *    `X-Hermes-Session-Token`'ı `SPARK_GATE_TOKEN` ile karşılaştırıyor; o
 *    değişken Caddy'nin LaunchAgent ortamında **tanımlı değil** → hiçbir istek
 *    eşleşmiyor)
 *  - `GET /api/sparks` (panoda) → **404** (böyle bir uç yok)
 *  - `GET http://192.168.1.101:5555/api/sparks` (LAN, doğrudan) → **200** ✓
 *
 * Yani uygulamanın yapabileceği: **adayları sırayla denemek** (tek sağlıklı
 * adres — [SparkEndpoints]), çalışanı hatırlamak ve çalışmayan durumda
 * **ne yapılacağını söyleyen** bir hata üretmek. `/spark-api` kapısının
 * anahtarı sunucu tarafındadır; canlı sunucuya bu görevde dokunulmaz.
 */
class SparkClient(
    private val baseUrls: List<String>,
    private val token: String = "",
) {

    /** Tek adresle kuran çağrılar için (ayardaki açık adres). */
    constructor(baseUrl: String, token: String = "") : this(listOf(baseUrl), token)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        // Uzak Spark'lar SSH üzerinden okunuyor; ilk çağrı yavaş olabiliyor.
        // Yine de askıda kalmış bir vekile 25 sn'den fazla beklemek anlamsız.
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    /** Bu istemcide çalıştığı DOĞRULANMIŞ taban adres. */
    @Volatile
    var working: String? = null
        private set

    private val failures = mutableListOf<SparkEndpoints.Attempt>()

    suspend fun sparks(): List<SparkEntry> =
        json.decodeFromString<SparksResponse>(get("/api/sparks")).sparks

    suspend fun metrics(id: String): SparkMetrics =
        json.decodeFromString(get("/api/sparks/$id/metrics"))

    /**
     * Aday adresleri sırayla dener; ilk 2xx kazanan olur ve hatırlanır.
     *
     * Hepsi başarısızsa [SparkUnavailableException] fırlar; mesajı
     * [SparkEndpoints.describe] üretir (403 → "sunucu tarafı iş" gibi).
     */
    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val bases = candidateOrder()
        failures.clear()
        var lastError: Exception? = null
        for (base in bases) {
            val url = base.trimEnd('/') + path
            try {
                val req = Request.Builder().url(url).get()
                if (token.isNotBlank()) req.header(HermesClient.SESSION_HEADER, token)
                http.newCall(req.build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        DiagLog.w("spark", "GET $path · $base -> ${res.code}")
                        failures += SparkEndpoints.Attempt(base, res.code, body.take(120))
                        lastError = HermesApiException(res.code, path, body.take(200))
                        return@use
                    }
                    if (working != base) {
                        working = base
                        DiagLog.i("spark", "calisan spark adresi: $base")
                    }
                    return@withContext body
                }
            } catch (e: IOException) {
                DiagLog.w("spark", "GET $path · $base ulasilamadi: ${e.message}")
                failures += SparkEndpoints.Attempt(base, -1, e.message.orEmpty())
                lastError = e
            }
        }
        val message = SparkEndpoints.describe(failures.toList(), ::lang)
        DiagLog.e("spark", "$path basarisiz - $message")
        throw SparkUnavailableException(message, lastError)
    }

    /**
     * Çalışan adres öne alınır; kalan adaylar sırayla.
     *
     * Sağlık kaydı [AddressHealth] üzerinden profil bazında tutulmaz — Spark
     * adresleri Hermes adresinden türetilir ve küçük bir liste (≤2) olduğu için
     * sıralamayı burada tutmak yeterli.
     */
    private fun candidateOrder(): List<String> {
        val uniq = baseUrls.map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.distinct()
        if (uniq.isEmpty()) return emptyList()
        val head = working?.takeIf { it in uniq }
        return if (head != null) listOf(head) + uniq.filterNot { it == head } else uniq
    }

    /** Saf katman dil bilmez; dil çözümü buradan enjekte edilir. */
    private fun lang(tr: String, en: String): String =
        com.hermes.mobile.ui.tr(tr, en)
}

/**
 * Spark panosu yüklenemedi. [message] kullanıcıya olduğu gibi gösterilir
 * (hangi adreslerin denendiğini ve ne yapılacağını söyler).
 */
class SparkUnavailableException(message: String, val rootCause: Throwable? = null) :
    IOException(message)
