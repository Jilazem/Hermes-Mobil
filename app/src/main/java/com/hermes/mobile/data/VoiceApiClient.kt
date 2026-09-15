package com.hermes.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** `GET /health` yanıtı. */
@Serializable
data class VoiceHealth(
    val ok: Boolean = false,
    val stt: String = "",
    val engines: Map<String, String> = emptyMap(),
    val error: String? = null,
) {
    val sttOk: Boolean get() = stt.equals("acik", true) || stt.equals("açık", true)
}

/** `POST /transcribe` yanıtı. */
@Serializable
data class TranscriptResponse(
    val text: String = "",
    val lang: String = "",
)

/** Hata gövdesi — sözleşme: `{"error": "..."}` + uygun kod. */
@Serializable
private data class ErrorBody(@SerialName("error") val error: String = "")

/**
 * Ses hattı istemcisi — sözleşmeye birebir (`voice_api` 8174).
 *
 * Okuma yolu **yazma yolundan farklı**: `/transcribe` kaydı yükler (<=60 sn,
 * yazma zaman aşımı 60 sn), `/synthesize` motoru ısıtır ve **soğukken
 * 173-187 sn** sürer → okuma zaman aşımı 300 sn ([VoiceApiEndpoints.SYNTH_TIMEOUT_MS]).
 * Tek bir OkHttp istemcisinde ikisini birlikte ayarlamak ya yüklemeyi ya da
 * sentezi bozardı; bu yüzden iki ayrı istemci var.
 *
 * Kimlik: her istekte `X-Hermes-Session-Token` ([HermesClient.SESSION_HEADER]).
 * Yanlış/eksikse sunucu 403 döner (fail-closed) — mesaj [VoiceApiEndpoints.describe].
 *
 * Adres seçimi [VoiceApiEndpoints.candidates] sırasıyla; çalışan adres
 * [working] olarak hatırlanır, bir sonraki istek onunla başlar. Başarısız
 * adresler [AddressHealth] ile artan süreyle devre dışı bırakılır (tur-10
 * dersi: ölü adresi her istekte yeniden denemek log'u yüzlerce satırla
 * dolduruyor ve kullanıcıya "yavaş bağlanıyor" olarak dönüyor).
 */
class VoiceApiClient(
    private val baseUrls: List<String>,
    private val token: String = "",
    /** Adres sağlığı kaydının anahtarı — profil kimliği (kanal ayrı tutulur). */
    private val profileId: String = "voice",
    /** Sentez okuma zaman aşımı — testte kısaltılabilir (varsayılan 300 sn). */
    private val synthTimeoutMs: Long = VoiceApiEndpoints.SYNTH_TIMEOUT_MS,
) {

    /** Tek adresle kuran çağrılar için. */
    constructor(baseUrl: String, token: String = "") : this(listOf(baseUrl), token)

    /** JSON gövdesi — sözleşme: `{"text": "...", "engine": "kahya"}`. */
    @Serializable
    data class SynthRequest(val text: String, val engine: String)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val readHttp = client(VoiceApiEndpoints.SYNTH_TIMEOUT_MS + 30_000L, synthTimeoutMs)
    private val writeHttp = client(60_000L, 90_000L)

    /** Bu istemcide çalıştığı DOĞRULANMIŞ taban adres. */
    @Volatile
    var working: String? = null
        private set

    private fun client(callTimeoutMs: Long, readTimeoutMs: Long): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
            .build()

    suspend fun health(): VoiceHealth = probe("/health") { body ->
        json.decodeFromString<VoiceHealth>(body)
    }

    /** Kaydı metne çevirir; hata durumunda [VoiceApiException] fırlar. */
    suspend fun transcribe(
        audio: ByteArray,
        fileName: String = "kayit.ogg",
        mime: String = "audio/ogg",
    ): String = withContext(Dispatchers.IO) {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", fileName, audio.toRequestBody(mime.toMediaType()))
            .build()
        val body = post("/transcribe", form, writeHttp)
        json.decodeFromString<TranscriptResponse>(body.toString(Charsets.UTF_8)).text
    }

    /** Metni sese çevirir; `audio/ogg` baytları döner. */
    suspend fun synthesize(text: String, engine: VoiceSpeakLogic.Engine): ByteArray =
        withContext(Dispatchers.IO) {
            val payload = json.encodeToString(
                SynthRequest(text = text, engine = engine.id),
            ).toRequestBody(JSON_MEDIA)
            post("/synthesize", payload, readHttp)
        }

    // ── İç işleyiş ────────────────────────────────────────────────────

    private suspend fun post(
        path: String,
        body: okhttp3.RequestBody,
        client: OkHttpClient,
    ): ByteArray = withContext(Dispatchers.IO) {
        val (res, payload) = execute(path, client) { base ->
            Request.Builder()
                .url(VoiceApiEndpoints.join(base, path))
                .post(body)
                .applyToken()
                .build()
        }
        payload ?: throw VoiceApiException(
            "Ses ucu boş yanıt döndü (${VoiceApiEndpoints.join(res, path)})",
            code = 200,
            path = path,
        )
    }

    /** Metin yolları için ince sarmalayıcı — gövdeyi String olarak döndürür. */
    private suspend fun probe(
        path: String,
        parse: (String) -> VoiceHealth,
    ): VoiceHealth = withContext(Dispatchers.IO) {
        val (_, payload) = execute(path, writeHttp) { base ->
            Request.Builder()
                .url(VoiceApiEndpoints.join(base, path))
                .get()
                .applyToken()
                .build()
        }
        // Gövde bayt gelir (aynı yürütücü her iki yolu da besliyor): /health
        // JSON'dur, metne çevrilir. Boş gövde = motor kapalı sayılır.
        parse(payload?.toString(Charsets.UTF_8) ?: "{}")
    }

    private fun Request.Builder.applyToken(): Request.Builder =
        if (token.isNotBlank()) header(HermesClient.SESSION_HEADER, token) else this

    /**
     * Aday adresleri sırayla dener; ilk 2xx kazanan olur ve hatırlanır.
     *
     * @return (kazanan taban adres, gövde baytları ya da null)
     */
    private fun execute(
        path: String,
        client: OkHttpClient,
        build: (String) -> Request,
    ): Pair<String, ByteArray?> {
        val failures = mutableListOf<VoiceApiEndpoints.Attempt>()
        var lastError: Exception? = null
        for (base in candidateOrder()) {
            val cooling = AddressHealth.status(profileId, base)
            if (cooling.coolingDown(System.currentTimeMillis())) {
                val why = cooling.lastReason ?: "devre disi"
                DiagLog.d("voice", "$path · $base devre disi ($why, ${cooling.remainingMs(System.currentTimeMillis())} ms)")
                failures += VoiceApiEndpoints.Attempt(base, -1, why)
                continue
            }
            try {
                client.newCall(build(base)).execute().use { res ->
                    val bytes = res.body?.bytes()
                    if (!res.isSuccessful) {
                        val snippet = bytes?.toString(Charsets.UTF_8)?.take(200).orEmpty()
                        DiagLog.w("voice", "$path · $base -> ${res.code} $snippet")
                        AddressHealth.noteFailure(profileId, base, "HTTP ${res.code}")
                        failures += VoiceApiEndpoints.Attempt(base, res.code, snippet)
                        lastError = VoiceApiException(snippet, res.code, path)
                        return@use
                    }
                    if (working != base) {
                        working = base
                        DiagLog.i("voice", "calisan ses ucu: $base")
                    }
                    AddressHealth.noteSuccess(profileId, base)
                    return base to bytes
                }
            } catch (e: IOException) {
                DiagLog.w("voice", "$path · $base ulasilamadi: ${e.message}")
                AddressHealth.noteFailure(profileId, base, e.message.orEmpty())
                failures += VoiceApiEndpoints.Attempt(base, -1, e.message.orEmpty())
                lastError = e
            }
        }
        val message = VoiceApiEndpoints.describe(failures.toList()) { tr, en ->
            com.hermes.mobile.ui.tr(tr, en)
        }
        DiagLog.e("voice", "$path basarisiz - $message")
        throw VoiceApiException(message, failures.firstOrNull { it.code > 0 }?.code ?: -1, path, lastError)
    }

    /**
     * Çalışan adres öne alınır; kalan adaylar sırayla.
     *
     * Aynı anda **tek sağlıklı adres** denenir (tur-10 deseni).
     */
    private fun candidateOrder(): List<String> {
        val uniq = baseUrls.map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.distinct()
        if (uniq.isEmpty()) return emptyList()
        val head = working?.takeIf { it in uniq }
        val ordered = if (head != null) listOf(head) + uniq.filterNot { it == head } else uniq
        return AddressHealth.plan(profileId, ordered)
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * Ses hattı hatası. [message] kullanıcıya olduğu gibi gösterilir: hangi
 * adreslerin denendiğini ve ne yapılacağını söyler (403 → token tazele gibi).
 */
class VoiceApiException(
    message: String,
    val code: Int = -1,
    val path: String = "",
    val rootCause: Throwable? = null,
) : IOException(message)

/**
 * Sentez/transkripsiyon taşıyıcısı — [VoiceMessageController] bu arayüzü
 * kullanır, böylece akış (kayıt → metin → önbellek → çalma) Android olmadan
 * JVM testinde koşabilir (`VoiceMessageFlowTest`).
 */
interface VoiceTransport {
    suspend fun health(): VoiceHealth
    suspend fun transcribe(audio: ByteArray, fileName: String, mime: String): String
    suspend fun synthesize(text: String, engine: VoiceSpeakLogic.Engine): ByteArray
    /** Bu taşıyıcıda çalıştığı doğrulanmış taban adres (hatırlamak için). */
    val working: String?
}

class HttpVoiceTransport(private val client: VoiceApiClient) : VoiceTransport {
    override suspend fun health(): VoiceHealth = client.health()
    override suspend fun transcribe(audio: ByteArray, fileName: String, mime: String): String =
        client.transcribe(audio, fileName, mime)
    override suspend fun synthesize(text: String, engine: VoiceSpeakLogic.Engine): ByteArray =
        client.synthesize(text, engine)
    override val working: String? get() = client.working
}
