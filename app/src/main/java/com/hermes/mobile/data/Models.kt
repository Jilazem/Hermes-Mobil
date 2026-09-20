package com.hermes.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Hermes dashboard API modelleri.
 *
 * Hepsi kısmi: `Json { ignoreUnknownKeys = true }` ile ayrıştırılır, böylece
 * Hermes güncellemesi yeni alan eklediğinde uygulama kırılmaz. Sözleşme canlı
 * the server 0.19.0 sunucusuna karşı doğrulandı.
 */

@Serializable
data class PlatformState(
    val state: String = "unknown",
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** GET /api/status */
@Serializable
data class HermesStatus(
    val version: String = "",
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("gateway_running") val gatewayRunning: Boolean = false,
    @SerialName("gateway_state") val gatewayState: String = "unknown",
    @SerialName("gateway_platforms") val gatewayPlatforms: Map<String, PlatformState> = emptyMap(),
    @SerialName("gateway_busy") val gatewayBusy: Boolean = false,
    @SerialName("active_agents") val activeAgents: Int = 0,
    @SerialName("can_update_hermes") val canUpdateHermes: Boolean = false,
)

@Serializable
data class MemoryStats(
    val total: Long = 0,
    val available: Long = 0,
    val used: Long = 0,
    val percent: Double = 0.0,
)

@Serializable
data class DiskStats(
    val total: Long = 0,
    val used: Long = 0,
    val free: Long = 0,
    val percent: Double = 0.0,
)

/** GET /api/system/stats */
@Serializable
data class SystemStats(
    val os: String = "",
    val hostname: String = "",
    val arch: String = "",
    @SerialName("hermes_version") val hermesVersion: String = "",
    @SerialName("cpu_count") val cpuCount: Int = 0,
    @SerialName("cpu_percent") val cpuPercent: Double = 0.0,
    val memory: MemoryStats = MemoryStats(),
    val disk: DiskStats = DiskStats(),
    @SerialName("uptime_seconds") val uptimeSeconds: Double = 0.0,
)

/** GET /api/sessions → sessions[] */
@Serializable
data class HermesSession(
    val id: String = "",
    val source: String? = null,
    val model: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    /**
     * Sunucunun oturum başlığı (`GET /api/sessions` → `title`).
     *
     * Başlık çoğu oturumda HAM ID ile aynı geliyor (kullanıcı rename yapmadıysa)
     * — bu yüzden okuyan taraf `!= id` kontrolüyle kullanmalı; yokla ayrımı
     * ekranlar yapar ([com.hermes.mobile.ui.sessionCardTitles]).
     */
    @SerialName("title") val serverTitle: String? = null,
    /** İlk mesajdan kısa önizleme — konusuz oturum kartının birinci satırı. */
    val preview: String? = null,
    /** Sunucunun insan-dostu etkinlik damgası ("5 dk önce" gibi; şimdilik yedek). */
    @SerialName("last_activity_description") val lastActivityDescription: String? = null,
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("ended_at") val endedAt: Double? = null,
    @SerialName("end_reason") val endReason: String? = null,
    // Tur-2 K3(b): sunucu bos sayaclari acik `null` yollayabiliyor. Duz
    // `Int = 0` explicit JSON null'da FATAL decode hatasi veriyor
    // (isLenient null'u non-null tipe atamaz) — "sik sik kapanma"nin sessiz
    // yollarindan biri. Nullable ham + 0'a inen erisimci (SessionMessage
    // toolCalls deseniyle ayni savunma). Named-arg cagrisi olmayan alanlar.
    @SerialName("message_count") private val messageCountRaw: Int? = null,
    @SerialName("tool_call_count") private val toolCallCountRaw: Int? = null,
    @SerialName("input_tokens") private val inputTokensRaw: Long? = null,
    @SerialName("output_tokens") private val outputTokensRaw: Long? = null,
    val cwd: String? = null,
    /**
     * Tur-21 JEV rozeti — masaüstü `jev_gate`/`jev_guard` kararının oturum
     * bazlı görünümü. **Gateway şu an bu alanı göndermiyor**; JSON'da yoksa
     * null kalır ve rozet ÇİZİLMEZ (uydurma yok — sözleşme notu
     * `denetim/tur21/JEV-BACKEND-SOZLESMES.md`).
     *
     * Kabul: "gec"→yeşil · "gozlem"/"log-only"→sarı · "iade"→kırmızı
     * (çözümleyici: [com.hermes.mobile.data.JevBadgeLogic.parse]).
     */
    val jev: String? = null,
) {
    val isActive: Boolean get() = endedAt == null
    val title: String get() = displayName?.takeIf { it.isNotBlank() } ?: id

    val messageCount: Int get() = messageCountRaw ?: 0
    val toolCallCount: Int get() = toolCallCountRaw ?: 0
    val inputTokens: Long get() = inputTokensRaw ?: 0L
    val outputTokens: Long get() = outputTokensRaw ?: 0L
}

@Serializable
data class SessionsResponse(val sessions: List<HermesSession> = emptyList())

/**
 * `session.active_list` → sessions[]
 *
 * Tarihsel DB kaydı DEĞİL — gateway sürecinde belleğinde ajanı olan, şu anda
 * bağlanılabilir oturumlar. Telegram'dan, cron'dan, CLI'dan başlatılmış olanlar
 * da burada görünür ve müdahale edilebilir.
 */
@Serializable
data class LiveSession(
    val id: String = "",
    val title: String = "",
    val preview: String = "",
    /** waiting · starting · working · idle */
    val status: String = "idle",
    val model: String = "",
    val current: Boolean = false,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("last_active") val lastActive: Double = 0.0,
    @SerialName("started_at") val startedAt: Double = 0.0,
    @SerialName("session_key") val sessionKey: String = "",
) {
    /**
     * REST uçları (`/api/sessions/{id}/messages`) veritabanı kimliğini ister;
     * `id` ise gateway'in **süreç içi** anahtarı (örn. `538fa088`) ve RPC'ler
     * (steer/redirect/interrupt/activate) onu bekler. İkisini karıştırmak
     * 404'e yol açıyor — bu yüzden ayrı erişimci.
     */
    val dbId: String get() = sessionKey.ifBlank { id }

    val isWorking: Boolean get() = status == "working"
    val isWaiting: Boolean get() = status == "waiting"
    val isStarting: Boolean get() = status == "starting"

    /** Müdahale (steer/redirect) yalnız iş yaparken anlamlı. */
    val canIntervene: Boolean get() = isWorking || isWaiting || isStarting
}

@Serializable
data class ActiveSessionsResponse(val sessions: List<LiveSession> = emptyList())

/**
 * GET /api/profiles → profiles[]
 *
 * Her profil kendi `~/.hermes/profiles/<ad>` dizininde yaşıyor: kendi SOUL.md'si
 * (sistem promptu), kendi becerileri, kendi .env'i. Yani profil seçmek promptu
 * ve araç setini birlikte değiştiriyor — ayrı bir "prompt profili" tutmaya gerek yok.
 */
@Serializable
data class HermesProfile(
    val name: String = "",
    val path: String = "",
    @SerialName("is_default") val isDefault: Boolean = false,
    val model: String? = null,
    val provider: String? = null,
    @SerialName("skill_count") val skillCount: Int = 0,
    @SerialName("gateway_running") val gatewayRunning: Boolean = false,
    /** Sunucunun insan-okur görünen adı (`display_name`) — çiplerde iç ad yerine bu. */
    @SerialName("display_name") val displayName: String = "",
    val description: String = "",
)

@Serializable
data class ProfilesResponse(val profiles: List<HermesProfile> = emptyList())

@Serializable
data class ActiveProfile(
    val active: String = "",
    val current: String = "",
)

/** GET /api/model/options → providers[] */
@Serializable
data class ModelPricing(
    val input: String? = null,
    val output: String? = null,
    val free: Boolean = false,
)

@Serializable
data class ModelProvider(
    val slug: String = "",
    val name: String = "",
    @SerialName("is_current") val isCurrent: Boolean = false,
    val models: List<String> = emptyList(),
    /**
     * Hesabın kullanamadığı modeller — genelde kredi gerektirenler.
     *
     * Bunu görmezden gelmek pahalıya patladı: kredisiz bir Nous modeli seçilince
     * API 404 döndü, gateway 3 denemeden sonra çöktü ve dışarıdan "model
     * değişmedi + sunucu düştü" gibi göründü. Seçici artık bunları kilitliyor.
     */
    @SerialName("unavailable_models") val unavailableModels: List<String> = emptyList(),
    val pricing: Map<String, ModelPricing> = emptyMap(),
) {
    fun isUsable(model: String): Boolean = model !in unavailableModels

    /** Kısa etiket: "ücretsiz", "$1.60/$8.00" ya da boş. */
    fun priceLabel(model: String): String {
        val p = pricing[model] ?: return ""
        if (p.free) return "ücretsiz"
        val i = p.input ?: return ""
        val o = p.output ?: return i
        return "$i / $o"
    }
}

@Serializable
data class ModelOptions(val providers: List<ModelProvider> = emptyList())

/** GET /api/model/info */
@Serializable
data class ModelInfo(
    val provider: String? = null,
    val model: String? = null,
)

/** POST /api/model/set */
@Serializable
data class ModelSetRequest(
    val scope: String = "main",
    val provider: String,
    val model: String,
)

/** POST /api/chat/image-upload */
@Serializable
data class ChatImageUploadRequest(
    @SerialName("data_url") val dataUrl: String,
    val filename: String? = null,
)

@Serializable
data class ChatImageUploadResult(
    /** Gateway'in görebildiği mutlak yol — `/image <path>` ile gönderilir. */
    val path: String = "",
    val bytes: Long = 0,
    val name: String = "",
    @SerialName("mime_type") val mimeType: String = "",
)

@Serializable
data class ToolCallFunction(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
data class ToolCall(
    val id: String? = null,
    @SerialName("function") private val functionRaw: ToolCallFunction? = null,
) {
    val function: ToolCallFunction get() = functionRaw ?: ToolCallFunction()
}

/**
 * GET /api/sessions/{id}/messages → messages[]
 *
 * Sunucu boş alanları `null` olarak yolluyor (liste değil) — bu yüzden koleksiyon
 * alanları nullable tutulup erişim tarafında boş listeye çevriliyor.
 */
@Serializable
data class SessionMessage(
    val role: String = "",
    val content: String? = null,
    @SerialName("tool_calls") private val toolCallsRaw: List<ToolCall>? = null,
    @SerialName("tool_name") val toolName: String? = null,
    val reasoning: String? = null,
    val timestamp: Double? = null,
) {
    val toolCalls: List<ToolCall> get() = toolCallsRaw ?: emptyList()

    val isUser: Boolean get() = role == "user"
    val isAssistant: Boolean get() = role == "assistant"
    val isTool: Boolean get() = role == "tool"
    val isSystem: Boolean get() = role == "system"
}

@Serializable
data class SessionMessagesResponse(
    @SerialName("session_id") val sessionId: String = "",
    val messages: List<SessionMessage> = emptyList(),
)

/**
 * `session.history` RPC'sinin öğesi — REST `/messages` ile **aynı şekil değil**.
 *
 * Sunucu tarafında `_history_to_messages` şunu üretiyor:
 *   user/assistant → `{"role", "text", (reasoning…)}`   ← `content` YOK, `text` var
 *   tool           → `{"role":"tool", "name", "context"}` ← `tool_name` YOK, gövde YOK
 *
 * Bu farkı gözden kaçırmak, geçmişi yüklerken tüm metin mesajlarının boş
 * görünmesine ve araç adlarının "araç"a düşmesine yol açıyordu.
 */
@Serializable
data class HistoryMessage(
    val role: String = "",
    val text: String? = null,
    val name: String? = null,
    val context: String? = null,
    val reasoning: String? = null,
) {
    /** Ortak render yoluna sokmak için `SessionMessage`'a çevirir. */
    fun toSessionMessage(): SessionMessage = SessionMessage(
        role = role,
        content = if (role == "tool") context else text,
        toolName = name,
        reasoning = reasoning,
    )
}

@Serializable
data class SessionHistoryResponse(
    val count: Int = 0,
    val messages: List<HistoryMessage> = emptyList(),
)

/**
 * GET /api/sessions/stats
 *
 * Token doğrulaması için de kullanılır: `/api/status` herkese açıktır
 * (health-check), bu uç ise token ister. Yanlış tokenle 401 döner.
 */
@Serializable
data class SessionStats(
    val total: Int = 0,
    val archived: Int = 0,
    val messages: Long = 0,
    @SerialName("by_source") val bySource: Map<String, Int> = emptyMap(),
)

// ── Pano bölümleri ────────────────────────────────────────────────────

@Serializable
data class FileEntry(
    val name: String = "",
    val path: String = "",
    @SerialName("is_directory") val isDirectory: Boolean = false,
    val size: Long? = null,
    val mtime: Double? = null,
    @SerialName("mime_type") val mimeType: String? = null,
)

@Serializable
data class FileListing(
    val path: String = "",
    /** Kök dizindeyken null — "yukarı" düğmesi buna bakıyor. */
    val parent: String? = null,
    val entries: List<FileEntry> = emptyList(),
)

@Serializable
data class LogResponse(
    val file: String = "",
    val lines: List<String> = emptyList(),
)

// ── Bakım (maintenance) — hermes doctor / update uçları ───────────────────

/**
 * `POST /api/maintenance/doctor` / `POST /api/maintenance/update` yanıtı.
 *
 * Bu uçlar **ayrılmış (detached)** bir süreç başlatır: yanıt, sunucunun
 * işlemi bekleyip döndüğü ANLAMINA GELMEZ — `ok: true` yalnızca "çocuk
 * süreç kuruldu" demektir. Bitiş kodu `maintenanceStatus()` ile okunur.
 */
@Serializable
data class MaintenanceStartResponse(
    val ok: Boolean = false,
    val pid: Int? = null,
    val name: String = "",
    val startedAt: String? = null,
    val logPath: String? = null,
    val error: String? = null,
    val message: String? = null,
    val alreadyRunning: Boolean = false,
)

/** `GET /api/maintenance/status` yanıtı. */
@Serializable
data class MaintenanceStatusResponse(
    val running: Boolean = false,
    val lastKind: String? = null,
    val exitCode: Int? = null,
    val pid: Int? = null,
    val logPath: String? = null,
    val startedAt: String? = null,
)

@Serializable
data class CronSchedule(
    val kind: String = "",
    val expr: String = "",
    val display: String = "",
)

@Serializable
data class CronJob(
    val id: String = "",
    val name: String = "",
    val prompt: String = "",
    val enabled: Boolean = true,
    val state: String = "",
    val schedule: CronSchedule? = null,
    @SerialName("schedule_display") val scheduleDisplay: String = "",
    @SerialName("next_run_at") val nextRunAt: String? = null,
    @SerialName("last_run_at") val lastRunAt: String? = null,
    @SerialName("last_status") val lastStatus: String? = null,
    val script: String? = null,
    val model: String? = null,
)

/**
 * Cron işlerinin YALNIZ kimlik+isim özü. Oturumları okunabilir
 * isimlendirmek için tam [CronJob] çekmeye gerek yok; `HermesClient.cronJobs`
 * sunucunun iki yanıt shape'ini (dizi ya da `{"jobs":[...]}`) tolere eder.
 */
@Serializable
data class CronJobInfo(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class SkillEntry(
    val name: String = "",
    /**
     * Serbest metin — ama sunucu bunu düz dize, `null` **ya da nesne** olarak
     * gönderiyor (ör. `{"Tüm web erişim işlemleri": "..."}`). Tipini sabitlemek
     * tek bir aykırı kayıtta 200 yeteneğin tamamını çözümlenemez yapıyordu.
     * Ham tutup [descriptionText] ile düzleştiriyoruz.
     */
    val description: JsonElement? = null,
    /** Kategorisiz yetenekler var; sunucu açık `null` gönderiyor. */
    val category: String? = null,
    val enabled: Boolean = true,
    val usage: Int = 0,
    val provenance: String? = null,
) {
    val descriptionText: String get() = flattenJson(description)
}

/**
 * Şekli belli olmayan JSON'u okunabilir tek satıra indirir.
 *
 * Bu uç şema-kararlı değil: aynı alan kayıttan kayıta dize, nesne ya da dizi
 * olabiliyor. Alan alan tip düzeltmek yerine burada savunma yapıyoruz.
 */
internal fun flattenJson(e: JsonElement?): String = runCatching {
    when (e) {
        null, is kotlinx.serialization.json.JsonNull -> ""
        is JsonPrimitive -> e.content
        is JsonArray -> e.joinToString(" · ") { flattenJson(it) }
        is JsonObject -> e.entries.joinToString(" · ") { (k, v) ->
            val value = flattenJson(v)
            if (value.isBlank()) k else "$k: $value"
        }
        else -> ""
    }
}.getOrDefault("")

@Serializable
data class McpServer(
    val name: String = "",
    val transport: String = "",
    val url: String? = null,
    val command: String? = null,
    val enabled: Boolean = true,
    /**
     * İki biçimde geliyor: ya `null`, ya `{"include": [...]}` gibi bir süzgeç
     * nesnesi. Dizi olarak beklemek tüm listeyi çözümlenemez yapıyordu, o yüzden
     * ham JSON tutuluyor ve araç adları [toolNames] ile güvenle çıkarılıyor.
     */
    val tools: JsonElement? = null,
) {
    /** Süzgeçte adı geçen araçlar; şekil beklenmedikse boş liste. */
    val toolNames: List<String>
        get() = runCatching {
            when (val t = tools) {
                is JsonArray -> t.mapNotNull { (it as? JsonPrimitive)?.content }
                is JsonObject -> (t["include"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content }
                    .orEmpty()
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
}

@Serializable
data class McpServersResponse(val servers: List<McpServer> = emptyList())

@Serializable
data class WebhookInfo(
    val enabled: Boolean = false,
    @SerialName("base_url") val baseUrl: String = "",
    val subscriptions: List<JsonElement> = emptyList(),
)

@Serializable
data class PairingInfo(
    val pending: List<JsonElement> = emptyList(),
    val approved: List<JsonElement> = emptyList(),
)

// ── sparkDash (DGX Spark izleme) ──────────────────────────────────────

@Serializable
data class SparkHardware(
    val device: String = "",
    val cpuModel: String = "",
    val cpuCores: Int = 0,
    val totalMemoryGB: Double = 0.0,
    val gpuChip: String = "",
)

@Serializable
data class SparkGpuPower(
    val draw: Double = 0.0,
    val limit: Double = 0.0,
)

@Serializable
data class SparkGpu(
    val temperature: Double = 0.0,
    val usage: Double = 0.0,
    /** `power` düz sayı değil nesne — draw/limit/systemDraw taşıyor. */
    val power: SparkGpuPower = SparkGpuPower(),
)

@Serializable
data class SparkCpu(
    val usage: Double = 0.0,
    val temperature: Double = 0.0,
)

@Serializable
data class SparkRam(
    /** MB cinsinden. */
    val used: Long = 0,
    val total: Long = 0,
    // sparkDash "percentage" diyor, "percent" değil.
    @SerialName("percentage") val percent: Double = 0.0,
)

@Serializable
data class SparkMetricValues(
    val gpu: SparkGpu = SparkGpu(),
    val cpu: SparkCpu = SparkCpu(),
    val ram: SparkRam = SparkRam(),
)

@Serializable
data class SparkMetrics(
    val id: String = "",
    val name: String = "",
    val online: Boolean = false,
    val uptime: Long = 0,
    val hardware: SparkHardware = SparkHardware(),
    val metrics: SparkMetricValues = SparkMetricValues(),
)

@Serializable
data class SparkEntry(val id: String = "", val name: String = "")

@Serializable
data class SparksResponse(val sparks: List<SparkEntry> = emptyList())
