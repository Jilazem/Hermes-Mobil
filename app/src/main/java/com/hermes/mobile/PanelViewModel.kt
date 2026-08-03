package com.hermes.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.mobile.data.CronJob
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.FileListing
import com.hermes.mobile.data.HermesClient
import com.hermes.mobile.data.McpServer
import com.hermes.mobile.data.PairingInfo
import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.data.SkillEntry
import com.hermes.mobile.data.SparkClient
import com.hermes.mobile.data.SparkMetrics
import com.hermes.mobile.data.WebhookInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Panodaki bölümler. Sıralama panonun kendi menüsüyle aynı.
 *
 * Etiketler burada değil: dile göre çözülmesi gerekiyor, enum sabiti olamaz.
 * Bkz. `ui/PanelScreen.kt` içindeki `labelFor` / `hintFor`.
 */
enum class PanelSection {
    Status,
    Terminal,
    Spark,
    Files,
    Logs,
    /** Uygulamanın kendi hata kaydı — sunucu günlükleri değil. */
    Diag,
    Cron,
    Skills,
    Mcp,
    Webhooks,
    Pairing,
    Config,
}

data class PanelState(
    val section: PanelSection? = null,
    val loading: Boolean = false,
    val error: String? = null,

    val files: FileListing? = null,
    val logFile: String = "agent",
    val logLines: List<String> = emptyList(),
    val cron: List<CronJob> = emptyList(),
    val skills: List<SkillEntry> = emptyList(),
    val mcp: List<McpServer> = emptyList(),
    val webhooks: WebhookInfo? = null,
    val pairing: PairingInfo? = null,
    val config: String = "",
    val sparks: List<SparkMetrics> = emptyList(),
)

/**
 * Pano bölümleri — dosyalar, günlükler, cron, yetenekler, MCP, kancalar,
 * eşleştirme, yapılandırma.
 *
 * Hepsi tek ViewModel'de çünkü aynı istemciyi paylaşıyorlar ve her bölüm tek
 * bir GET'ten ibaret; bölüm başına ViewModel açmak sadece tekrar üretirdi.
 * Veri bölüm açıldığında çekiliyor — sekmeye her girişte sekiz istek atmanın
 * anlamı yok.
 */
class PanelViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PanelState())
    val state: StateFlow<PanelState> = _state.asStateFlow()

    private var profile: ServerProfile? = null

    fun bind(p: ServerProfile?) {
        if (p?.id == profile?.id && p?.token == profile?.token) return
        profile = p
        // Sunucu değişince eldeki veri başka makineye ait; temizle.
        _state.value = PanelState(section = _state.value.section)
        _state.value.section?.let(::open)
    }

    fun close() {
        _state.update { it.copy(section = null, error = null) }
    }

    fun open(section: PanelSection) {
        _state.update { it.copy(section = section, error = null) }
        refresh()
    }

    fun refresh() {
        val p = profile ?: run {
            _state.update { it.copy(error = "Sunucu profili yok") }
            return
        }
        val section = _state.value.section ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val client = HermesClient(p)
            runCatching {
                when (section) {
                    PanelSection.Files -> {
                        val listing = client.files(_state.value.files?.path)
                        _state.update { it.copy(files = listing) }
                    }
                    PanelSection.Logs -> {
                        val res = client.logs(_state.value.logFile)
                        _state.update { it.copy(logLines = res.lines) }
                    }
                    PanelSection.Cron -> {
                        val jobs = client.cronJobs()
                        _state.update { it.copy(cron = jobs) }
                    }
                    PanelSection.Skills -> {
                        val list = client.skills()
                        _state.update { it.copy(skills = list) }
                    }
                    PanelSection.Mcp -> {
                        val list = client.mcpServers()
                        _state.update { it.copy(mcp = list) }
                    }
                    PanelSection.Webhooks -> {
                        val info = client.webhooks()
                        _state.update { it.copy(webhooks = info) }
                    }
                    PanelSection.Pairing -> {
                        val info = client.pairing()
                        _state.update { it.copy(pairing = info) }
                    }
                    PanelSection.Config -> {
                        val raw = client.configRaw()
                        _state.update { it.copy(config = raw) }
                    }
                    // Durum ve Terminal kendi ViewModel'lerinden besleniyor;
                    // Tanılama ise tamamen cihazda, sunucudan çekilecek veri yok.
                    PanelSection.Status, PanelSection.Terminal, PanelSection.Diag -> Unit
                    PanelSection.Spark -> {
                        // Artık her iki ağda da adres var (LAN'da 5555, dışarıda
                        // /spark-api), o yüzden "yalnız yerel ağda" durumu kalktı.
                        val spark = SparkClient(sparkBaseUrl(p), p.token)
                        // Ölçümler ayrı ayrı çekiliyor; biri (SSH ile okunan
                        // uzak makine) yavaşsa ya da düşmüşse diğeri yine gelsin.
                        val entries = spark.sparks()
                        val failures = mutableListOf<String>()
                        val all = entries.mapNotNull { entry ->
                            runCatching { spark.metrics(entry.id) }
                                .onFailure { e ->
                                    // Sessizce yutmak, "Spark bulunamadı" gibi
                                    // yanlış bir sonuca götürüyordu.
                                    failures += "${entry.id}: ${e.message}"
                                }
                                .getOrNull()
                        }
                        _state.update {
                            it.copy(
                                sparks = all,
                                error = failures.takeIf { f -> f.isNotEmpty() }?.joinToString(" · "),
                            )
                        }
                    }
                }
            }.onFailure { e ->
                // Şema hatalarını burada yakalamak önemli: sunucu alan tipini
                // değiştirdiğinde (yetenekler/MCP'de iki kez oldu) kullanıcı
                // yalnız "İstek başarısız" görüyor, hangi alanın bozulduğunu
                // ancak istisnanın kendisi söylüyor.
                DiagLog.e("panel", "${section.name} failed to load", e)
                _state.update {
                    it.copy(
                        error = e.message ?: "İstek başarısız",
                        sparks = if (section == PanelSection.Spark) emptyList() else it.sparks,
                    )
                }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    /** Dosya gezgininde klasöre gir ya da üste çık. */
    fun browse(path: String?) {
        val p = profile ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { HermesClient(p).files(path) }
                .onSuccess { listing -> _state.update { it.copy(files = listing) } }
                .onFailure { e ->
                    _state.update {
                        // Yönetilen kökün dışı 403 dönüyor; kullanıcıya sebebi söylensin.
                        it.copy(
                            error = if (e is com.hermes.mobile.data.HermesApiException && e.code == 403)
                                "Bu klasör yönetilen kökün dışında"
                            else e.message ?: "Klasör açılamadı"
                        )
                    }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    /**
     * Ayarda adres yoksa Hermes sunucusunun ana makinesinden türet.
     *
     * ⚠️ Yalnız **yerel ağ** adresleri için. Dışarıdan bağlanırken (bulut
     * alan adı gibi) 5555'i denemek anlamsız: sparkDash'in API'sinde kimlik
     * doğrulama yok, o yüzden o port internete açılmadı ve açılmamalı.
     * Türetmeye kalkınca kullanıcı 8 saniyelik zaman aşımı + anlaşılmaz bir
     * bağlantı hatası görüyordu; şimdi sebebini söylüyoruz.
     */
    private fun sparkBaseUrl(p: ServerProfile): String {
        sparkUrl.takeIf { it.isNotBlank() }?.let { return it }
        val base = p.activeUrl ?: p.normalizedUrl
        val host = base.substringAfter("://").substringBefore("/").substringBefore(":")
        // Ev ağında doğrudan 5555. Dışarıdan Hermes'in ters vekilindeki
        // `/spark-api` yolundan: 5555 hâlâ internete kapalı, kapıyı Caddy
        // tutuyor (token başlığı + yalnız GET). Böylece kimlik doğrulaması
        // olmayan bir API dışarı açılmış olmuyor.
        return if (isPrivateHost(host)) "http://$host:5555"
        else base.trimEnd('/') + "/spark-api"
    }

    /** RFC1918 + localhost + .local — yani "aynı ağdayım" denebilecek adresler. */
    private fun isPrivateHost(host: String): Boolean {
        if (host.equals("localhost", true) || host.endsWith(".local", true)) return true
        val o = host.split(".").mapNotNull { it.toIntOrNull() }
        if (o.size != 4) return false
        return when {
            o[0] == 10 -> true
            o[0] == 127 -> true
            o[0] == 192 && o[1] == 168 -> true
            o[0] == 172 && o[1] in 16..31 -> true
            else -> false
        }
    }

    /** Ayarlardan gelen sparkDash adresi. */
    var sparkUrl: String = ""

    /**
     * Cron işini duraklatır ya da devam ettirir, sonra listeyi yeniler.
     *
     * İyimser güncelleme yapmıyoruz: sunucu isteği reddederse ekranda yanlış
     * durum kalmasın, gerçek durumu tekrar okuyoruz.
     */
    fun toggleCron(job: CronJob) {
        val p = profile ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val client = HermesClient(p)
            runCatching {
                if (job.enabled) client.cronPause(job.id) else client.cronResume(job.id)
                _state.update { it.copy(cron = client.cronJobs()) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Cron güncellenemedi") }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    /**
     * Zamanlamayı değiştirir. Gövde şeması sunucu kaynağından okundu ve kendi
     * oluşturduğum tek kullanımlık işle doğrulandı — kullanıcının işlerine
     * deneme yapılmadı.
     */
    fun setCronSchedule(job: CronJob, schedule: String) {
        val p = profile ?: return
        val expr = schedule.trim()
        if (expr.isBlank()) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val client = HermesClient(p)
            runCatching {
                client.cronSetSchedule(job.id, expr)
                _state.update { it.copy(cron = client.cronJobs()) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Zamanlama değiştirilemedi") }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    /** İşi hemen çalıştırır — arayüz önce onay alıyor. */
    fun triggerCron(job: CronJob) {
        val p = profile ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { HermesClient(p).cronTrigger(job.id) }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "İş başlatılamadı") }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    fun selectLogFile(file: String) {
        _state.update { it.copy(logFile = file) }
        refresh()
    }
}
