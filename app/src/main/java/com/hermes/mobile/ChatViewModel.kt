package com.hermes.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.mobile.data.ConnectionState
import com.hermes.mobile.data.AwaitReplyService
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.GatewayWsClient
import com.hermes.mobile.data.PhoneIntent
import com.hermes.mobile.data.PhoneTools
import com.hermes.mobile.data.ShizukuBridge
import com.hermes.mobile.data.Notifier
import com.hermes.mobile.data.HermesClient
import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.ModelProvider
import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.data.ShareUploadPlan
import com.hermes.mobile.data.VoiceController
import com.hermes.mobile.data.AndroidVoicePlayer
import com.hermes.mobile.data.AndroidVoiceRecorder
import com.hermes.mobile.data.HttpVoiceTransport
import com.hermes.mobile.data.VoiceApiClient
import com.hermes.mobile.data.VoiceApiEndpoints
import com.hermes.mobile.data.VoiceMessageController
import com.hermes.mobile.data.VoicePrefs
import com.hermes.mobile.data.VoiceSpeakLogic
import com.hermes.mobile.data.VoiceStatusLogic
import com.hermes.mobile.data.VoiceTransport
import com.hermes.mobile.data.cleanupPaths
import com.hermes.mobile.data.planShareUpload
import com.hermes.mobile.data.settleShare
import com.hermes.mobile.data.resolveShareTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.hermes.mobile.ui.clearRailDismissal
import com.hermes.mobile.ui.createSessionProfileArg
import com.hermes.mobile.ui.markSeenLive
import com.hermes.mobile.ui.pushRecent
import com.hermes.mobile.ui.RecentRailSession
import com.hermes.mobile.ui.ROUTER_CHIP
import com.hermes.mobile.ui.tr
import com.hermes.mobile.ui.visibleUserMessage

/** Sohbet akışındaki tek bir görsel öğe. */
sealed interface ChatItem {
    val key: String

    data class User(override val key: String, val text: String) : ChatItem

    data class Assistant(
        override val key: String,
        val text: String,
        val streaming: Boolean = false,
    ) : ChatItem

    /**
     * Modelin düşünme metni.
     *
     * [live] = hâlâ akıyor: ekran bu bloğu katlanmadan gösterir ve metin
     * uzadıkça son satırları izler. Yanıtın ilk parçası, bir araç başlangıcı
     * ya da akışın bitişi bloğu kapatır ([live] = false) — katlanır hâle gelir.
     */
    data class Thinking(override val key: String, val text: String, val live: Boolean = false) :
        ChatItem

    data class Tool(
        override val key: String,
        val name: String,
        val state: ToolState,
        val detail: String? = null,
    ) : ChatItem

    data class Notice(override val key: String, val text: String, val isError: Boolean = false) :
        ChatItem

    /** Ajanın onay beklediği tehlikeli komut — onayla/reddet düğmeleriyle. */
    data class Approval(
        override val key: String,
        val text: String,
        val isSudo: Boolean = false,
        val answered: String? = null,
    ) : ChatItem
}

enum class ToolState { Running, Done, Failed }

/** Düşünce bloğu canlı görünümünde gösterilecek son dolu satır sayısı. */
const val LIVE_THINKING_TAIL_LINES = 4

/** Canlı görünümde uzun satırlar sondan en fazla bu kadar karakterle kırpılır. */
const val LIVE_THINKING_LINE_CLIP = 200

/**
 * Canlı düşünce metninden ekranda gösterilecek kuyruğu üretir.
 *
 * Kurallar: satırlara böl, boş satırları at, son [LIVE_THINKING_TAIL_LINES]
 * dolu satırı al; tek satır [LIVE_THINKING_LINE_CLIP] karakteri aşarsa
 * SONDAN kırp. Sonuç boşsa boş string döner (çağıran '…' önekini ekler).
 */
fun liveThinkingTail(text: String, maxLines: Int = LIVE_THINKING_TAIL_LINES): String {
    val filled = text.split('\n').map { it.trimEnd() }.filter { it.isNotBlank() }
    if (filled.isEmpty()) return ""
    return filled.takeLast(maxLines)
        .joinToString("\n") { line ->
            if (line.length > LIVE_THINKING_LINE_CLIP) line.takeLast(LIVE_THINKING_LINE_CLIP) else line
        }
}

/**
 * Gateway'in argümansız `/reasoning` çıktısını ayrıştırır.
 *
 * Beklenen satırlar: `Reasoning effort:  medium` ve `Reasoning display: on`.
 * Bilinmeyen/bozuk çıktıda effort `null` döner — çağıran paneli boş bırakır.
 */
data class ReasoningStatus(val effort: String?, val displayOn: Boolean?)

fun parseReasoningOutput(output: String): ReasoningStatus {
    var effort: String? = null
    var display: Boolean? = null
    for (rawLine in output.lineSequence()) {
        val line = rawLine.trim()
        if (line.startsWith("Reasoning effort:", ignoreCase = true)) {
            effort = line.substringAfter(':').trim().takeIf { it.isNotEmpty() }
        } else if (line.startsWith("Reasoning display:", ignoreCase = true)) {
            display = when (line.substringAfter(':').trim().lowercase()) {
                "on" -> true
                "off" -> false
                else -> null
            }
        }
    }
    return ReasoningStatus(effort, display)
}

/** Terminal ekranındaki tek satır. */
data class TerminalLine(
    val text: String,
    val isCommand: Boolean = false,
    val isError: Boolean = false,
)

/**
 * Kuyruk bildirimi için sabit anahtar.
 *
 * Sabit olması gerekiyor: her yeni mesajda yeni bir not eklemek yerine tek
 * notun sayacını güncelliyoruz, kuyruk boşalınca da onu siliyoruz.
 */
private const val QUEUE_NOTICE_KEY = "queue-notice"

/** Devam ettirilen oturumda geri yüklenecek azami mesaj sayısı. */
private const val HISTORY_LIMIT = 150

/** Gönderilmeyi bekleyen ek. */
data class PendingAttachment(
    val label: String,
    val kind: AttachmentKind,
    /** Sunucuya yüklendikten sonra dolar. */
    val remotePath: String? = null,
    val uploading: Boolean = true,
    val error: String? = null,
)

enum class AttachmentKind { Image, File }

data class ChatState(
    val items: List<ChatItem> = emptyList(),
    val connection: ConnectionState = ConnectionState.Idle,
    val sessionId: String? = null,
    val sending: Boolean = false,
    val agentBusy: Boolean = false,
    val statusLine: String? = null,
    val attachments: List<PendingAttachment> = emptyList(),
    val voiceMode: VoiceController.Mode = VoiceController.Mode.Off,
    val voicePartial: String = "",
    val handsFree: Boolean = false,
    val currentModel: String? = null,
    /**
     * Açık oturumun KONUSU (tur-4). Üst şerit artık model adını değil konuyu
     * gösterir (Telegram başlığı gibi); model seçimi ⋯ ikonuna taşındı.
     * Oturum yoksa boş — başlık "Sohbet" olur.
     */
    val topic: String = "",
    val notice: String? = null,
    /**
     * Geçmiş mesajlar sunucudan çekilirken true (KALAN-1).
     *
     * Boş sohbet ekranı ile "geçmiş yolda" durumunu ayırmak için: iskelet
     * balonlar yalnız bu bayrak açıkken ve hiç mesaj yokken çizilir.
     */
    val historyLoading: Boolean = false,
)

/**
 * Müdahale sonucu sohbete yazılan tek satır (KALAN-2) — saf fonksiyon
 * (`ChatMenuTest`). Sessiz başarısızlık yok: sunucu `queued` dışında bir şey
 * döndürdüyse kullanıcı bunu GÖRÜR.
 */
fun interventionNotice(kind: InterventionKind, status: String): String = when {
    status == "queued" && kind == InterventionKind.Redirect ->
        tr("Talimat iletildi — süren tur yönlendirildi", "Instruction sent — the running turn was redirected")
    status == "queued" ->
        tr("Talimat iletildi — ajan bir sonraki adımında görecek", "Instruction sent — the agent sees it on its next step")
    else ->
        tr("Talimat iletilemedi ($status)", "Instruction could not be sent ($status)")
}

/**
 * `/api/ws` JSON-RPC üzerinden canlı sohbet.
 *
 * Olay akışı dashboard'ın kullandığıyla aynı: `message.delta` parçaları biriktirilip
 * tek bir asistan baloncuğuna yazılır, `message.complete` onu dondurur, `tool.*`
 * olayları araç kartlarını günceller.
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var client: GatewayWsClient? = null

    /**
     * Başka uygulamadan paylaşılan metin.
     *
     * Taslak normalde `ChatScreen`'in kendi state'i; paylaşım dışarıdan geldiği
     * için buradan tek seferlik bir sinyal olarak yayınlanıyor. Ekran okuyunca
     * [consumeSharedText] ile temizliyor, böylece ekran döndürmede tekrar
     * yapışmıyor.
     */
    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

    /**
     * Paylaşım hedefi geçici durumu — [pickShareTarget] tarafından doldurulur,
     * [consumePendingShare] ile temizlenir. Üç ayrı StateFlow: oturum, dosya adı,
     * metin. Hedef kararını [resolveShareTarget] saf fonksiyonu verir.
     */
    private val _pendingShareSession = MutableStateFlow<String?>(null)
    val pendingShareSession: StateFlow<String?> = _pendingShareSession.asStateFlow()

    private val _pendingShareFile = MutableStateFlow<String?>(null)
    val pendingShareFile: StateFlow<String?> = _pendingShareFile.asStateFlow()

    private val _pendingShareText = MutableStateFlow<String?>(null)
    val pendingShareText: StateFlow<String?> = _pendingShareText.asStateFlow()

    /**
     * Sol ray için uygulama içi "son açılanlar" (tur-8).
     *
     * Kullanıcı hangi yoldan bir oturuma bağlanırsa (ray hücresi, Oturumlar
     * paneli, paylaşım hedefi) buraya EN YENİ ÖNCE yazılır. Böylece sunucunun
     * açık listesinden düşen bir oturum bile — kullanıcı kapatana kadar —
     * ray'da kalır; sıra "son etkileşim" olur.
     */
    private val _recentRail = MutableStateFlow<List<RecentRailSession>>(emptyList())
    val recentRail: StateFlow<List<RecentRailSession>> = _recentRail.asStateFlow()

    /**
     * Kullanıcının ray'da UZUN BASIP kapattığı hücre anahtarları (dbId ya da
     * süreç içi id). Oturum yeniden açılırsa işaret silinir; geçerli oturum
     * zaten her koşulda görünür.
     */
    private val _railDismissed = MutableStateFlow<Set<String>>(emptySet())
    val railDismissed: StateFlow<Set<String>> = _railDismissed.asStateFlow()

    /** Ray hücresini kapat (uzun basma). */
    fun dismissRailEntry(key: String) {
        if (key.isBlank()) return
        _railDismissed.update { it + key }
    }

    /**
     * Uygulama içi halkaya bir oturum yazar (en yeni önce, tekilleştirilmiş).
     * Sıra/sayım kararı `railEntries`'te; burada yalnız kayıt tutulur.
     */
    private fun noteRecentRail(liveId: String, dbId: String, title: String) {
        if (liveId.isBlank() && dbId.isBlank()) return
        val stamp = System.currentTimeMillis()
        _recentRail.update { old ->
            pushRecent(old, liveId = liveId, dbId = dbId, title = title, openedAt = stamp)
        }
        _railDismissed.update { clearRailDismissal(it, liveId = liveId, dbId = dbId) }
    }

    /**
     * Sunucunun açık listesi her tazelendiğinde çağrılır: kayıtlar "görüldü"
     * damgası alır. Görülmüş bir kayıt listeden düşerse sunucu kapatmıştır →
     * ray'dan iner (`railEntries`). Hiç görülmemiş kayıt (REST'ten açılan
     * geçmiş oturum) kanıt yokluğunda ray'da kalır.
     */
    fun observeLiveRail(ids: List<String>) {
        val set = ids.filter { it.isNotBlank() }.toHashSet()
        if (set.isEmpty()) return
        _recentRail.update { old -> markSeenLive(old, set) }
    }

    /**
     * Vekilin staging kopyasından türeyen YÜKLEME PLANI (HIGH-1 teli).
     * [pickShareTarget] her çağrıldığında (hedef seçimi dahil) güncellenir;
     * [consumePendingShare] planı UYGULAR: Upload → gerçek yükleme,
     * Unreadable → DiagLog uyarısı; her halde staging kopyası silinir.
     */
    private val _pendingSharePlan = MutableStateFlow<ShareUploadPlan>(ShareUploadPlan.None)
    private var pendingStagedPath: String? = null

    /**
     * Paylaşım akışının kullanıcıya GÖRÜNÜR uyarısı (denetmen YENI-2):
     * okunamayan dosya / staging hatası / profil yok — sessiz DiagLog değil.
     * Chat ekranının üstünde sarı şerit çizilir; [clearShareWarning] susturur.
     */
    private val _shareWarning = MutableStateFlow<String?>(null)
    val shareWarning: StateFlow<String?> = _shareWarning.asStateFlow()
    fun clearShareWarning() { _shareWarning.value = null }

    /**
     * Ekran "Hermes'e ilet" hedefi için hedef seçim sayfasını gösterir mi?
     * Yalnız [pickShareTarget] çağrıldıktan, [consumePendingShare] bitmeden evet.
     */
    private val _shareTargetVisible = MutableStateFlow(false)
    val shareTargetVisible: StateFlow<Boolean> = _shareTargetVisible.asStateFlow()

    /**
     * Paylaşım niyeti sonucunda hedef seçim ekranını açar.
     *
     * [sessionId] == null ise "Yeni konu" seçildi; oturum kimliği verilirse o
     * oturuma bağlanılır ve metin/dosya oraya düşürülür. Ekstra metin,
     * ShareTargetScreen gösterimi bittikten sonra taslağa gönderilir.
     */
    fun pickShareTarget(
        sessionId: String?,
        sharedText: String?,
        sharedFile: String?,
        stagedPath: String? = null,
    ) {
        _pendingShareSession.value = sessionId
        // Dosya adı yalnız gerçekten varsa tutulur; gerçek yükleme staging
        // kopyasından yapılır — hedef kararını [resolveShareTarget] saf
        // fonksiyonu, yükleme kararını [planShareUpload] saf fonksiyonu verir.
        val target = resolveShareTarget(sharedText, sharedFile, sessionId)
        if (target.hasFile && !sharedFile.isNullOrBlank()) {
            _pendingShareFile.value = sharedFile
        } else {
            _pendingShareFile.value = null
        }
        // stagedPath hedef seçimi sonrası tekrar gelen çağrıda null olur;
        // ilk el sıkışmada tutulan kopya yolu geçerliliğini korur.
        if (stagedPath != null) pendingStagedPath = stagedPath
        _pendingSharePlan.value = planShareUpload(pendingStagedPath, _pendingShareFile.value)
        _pendingShareText.value = sharedText
        // Hedef seçim ekranı yalnız gerçek bir paylaşım varsa açılır.
        _shareTargetVisible.value = target.wantsTargetPicker ||
            !sharedText.isNullOrBlank() || !sharedFile.isNullOrBlank()
    }

    /** Hedef seçim ekranı kapanınca metni taslağa, dosyaya yüklemeye geçirir.
     * YALNIZ hedef-onayı yolları (Yeni konu / oturum seçimi) bunu çağırır. */
    fun consumePendingShare() = settleShareIntent(applyUpload = true)

    /**
     * Vazgeç / Geri tuşu yolu (denetmen YENİ-1): plan ASLA uygulanmaz —
     * yüklenmez, metin taslağa DÜŞMEZ; yalnız staging kopyaları silinir ve
     * ekran kapanır. İptal, yüklemenin tersi bir niyettir.
     */
    fun cancelPendingShare() = settleShareIntent(applyUpload = false)

    private fun settleShareIntent(applyUpload: Boolean) {
        val plan = _pendingSharePlan.value
        // Kararın TAMAMI saf settleShare'de (ShareUpload.kt — sözleşme testi
        // ShareUploadPlanTest); VM yalnız sonucu uygular.
        val out = settleShare(
            plan = plan,
            text = _pendingShareText.value,
            applyUpload = applyUpload,
            profilePresent = profile != null,
        )
        out.attachName?.let { name ->
            // attachShareFile staging dosyasını yükledikten SONRA siler —
            // burada silmek coroutine okumasıyla yarışa girerdi.
            if (plan is ShareUploadPlan.Upload) attachShareFile(plan)
            else DiagLog.w("ShareUpload", "tutarsız plan: attach istendi ama plan Upload değil: $name")
        }
        when (out.warnCase) {
            "unreadable" -> {
                DiagLog.w("ShareUpload", "dosya okunamadı, yüklenemedi: ${plan.unreadableName()}")
                _shareWarning.value = tr(
                    "Dosya okunamadı ve yüklenemedi: ", "Could not read file, not uploaded: ",
                ) + plan.unreadableName()
            }
            "not_connected" ->
                _shareWarning.value = tr(
                    "Sunucuya bağlı değil — dosya yüklenemedi: ",
                    "Not connected to server, file not uploaded: ",
                ) + (plan as? ShareUploadPlan.Upload)?.name.orEmpty()
        }
        out.cleanupPaths.forEach { p -> runCatching { java.io.File(p).delete() } }
        // Metin yalnız ONAY yolunda taslağa düşer; iptalde atılır.
        out.draftText?.takeIf { it.isNotBlank() }?.let { shareText(it) }
        _pendingSharePlan.value = ShareUploadPlan.None
        pendingStagedPath = null
        _pendingShareText.value = null
        _pendingShareFile.value = null
        _pendingShareSession.value = null
        _shareTargetVisible.value = false
    }

    /** Unreadable planının adı (uyarı mesajı için); diğer planlarda boş. */
    private fun ShareUploadPlan.unreadableName(): String =
        (this as? ShareUploadPlan.Unreadable)?.name.orEmpty()

    /**
     * Staging kopyasını SEÇİLEN HEDEF'in profilinde gerçekten yükler:
     * [HermesClient.uploadManagedFile] (File) → POST /api/files/upload-stream.
     * Yük ek olarak sohbet girdisinde görünür; gönderim kullanıcı onaylıdır.
     * Yükleme sonrası kopya silinir (cache birikmez — denetmen #1).
     */
    fun attachShareFile(plan: ShareUploadPlan.Upload) {
        val file = java.io.File(plan.stagedPath)
        val p = profile ?: run {
            // Kullanıcıya görünür (YENI-2): sunucu bağlantısı yoksa dosya
            // yüklenemez — sessiz DiagLog değil, uyarı şeridi.
            DiagLog.w("ShareUpload", "profil yok — ${plan.name} yüklenemedi")
            _shareWarning.value = tr(
                "Sunucuya bağlı değil — dosya yüklenemedi: ",
                "Not connected to server, file not uploaded: ",
            ) + plan.name
            runCatching { file.delete() }
            return
        }
        if (!file.isFile) {
            DiagLog.w("ShareUpload", "staging kopyası yok: ${plan.stagedPath}")
            _shareWarning.value = tr("Dosya kopyası okunamadı: ", "File copy unreadable: ") + plan.name
            return
        }
        val placeholder = PendingAttachment(plan.name, AttachmentKind.File)
        _state.update { it.copy(attachments = it.attachments + placeholder) }
        viewModelScope.launch {
            runCatching { HermesClient(p).uploadManagedFile(file, plan.name) }
                .onSuccess { path ->
                    DiagLog.i("ShareUpload", "yüklendi: ${plan.name} -> $path (${file.length()} B)")
                    markAttachment(plan.name, path, null)
                }
                .onFailure { e ->
                    DiagLog.e("ShareUpload", "yükleme başarısız: ${plan.name}", e)
                    markAttachment(plan.name, null, e.message ?: "Yüklenemedi")
                }
            // Gönderim sonrası staging temizliği (yüklemesiz de silinir).
            runCatching { file.delete() }
        }
    }

    fun shareText(text: String) {
        if (text.isBlank()) return
        _sharedText.value = text
    }

    fun consumeSharedText() {
        _sharedText.value = null
    }

    /**
     * Canlı-oturum ekranı aynı sokete ihtiyaç duyuyor; her ikisi için ayrı WS
     * açmak gateway'de iki ayrı istemci gibi görünürdü. Bağlantı kurulduğunda
     * yayınlanır, `MainActivity` bunu `LiveSessionsViewModel`'e geçirir.
     */
    private val _gateway = MutableStateFlow<GatewayWsClient?>(null)
    val gateway: StateFlow<GatewayWsClient?> = _gateway.asStateFlow()

    /**
     * Yazma hızı göstergesi — ana `state`'ten AYRI yayın: her delta penceresi
     * tüm sohbet listesini yeniden derlemesin diye sürat StateFlow'u ayrı akıyor;
     * sadece hız satırı onu topluyor.
     */
    private val _speed = MutableStateFlow<StreamMeter.Snapshot?>(null)
    val speed: StateFlow<StreamMeter.Snapshot?> = _speed.asStateFlow()
    private val streamMeter = StreamMeter()

    private var profile: ServerProfile? = null
    private var profileId: String? = null
    private var eventJob: Job? = null
    private var stateJob: Job? = null
    private var seq = 0
    private var streamingKey: String? = null
    private var thinkingKey: String? = null

    val voice = VoiceController(app)

    // ── Sesli mesaj (tur-11): kayıt → metin, metin → ses ──────────────
    private val voiceRecorder = AndroidVoiceRecorder(app)
    private val voicePlayer = AndroidVoicePlayer()
    private var voiceClient: VoiceApiClient? = null
    private var voiceClientKey = ""

    /**
     * Ses hattı denetleyicisi — kayıt durum makinesi + sentez akışı.
     *
     * Taşıyıcı **lambda** olarak veriliyor: profil ya da adres ayarı
     * değiştiğinde istemci yeniden kurulur, ham soket yeniden bağlanmaz.
     */
    val voiceMsg = VoiceMessageController(
        transport = ::voiceTransport,
        cacheDir = { app.cacheDir },
        scope = viewModelScope,
        recorder = voiceRecorder,
        player = voicePlayer,
    )

    /**
     * Ayarlardan gelen ses tercihleri.
     *
     * "Otomatik gönder" varsayılan KAPALI: sesle yazılan metin önce sohbet
     * girdisine düşer, gönderimi kullanıcı yapar.
     */
    var voicePrefs: VoicePrefs = VoicePrefs()
        set(value) {
            field = value
            voiceMsg.autoSend = value.autoSend
            voiceMsg.engine = value.engine
        }

    /** Çalışan ses ucu adresi hatırlandı — MainActivity ayarlara yazar. */
    var onVoiceBase: (String) -> Unit = {}

    /** Sesle yazılan metin; ChatScreen taslağa aktarır ve tüketir. */
    private val _voicePrefill = MutableStateFlow<String?>(null)
    val voicePrefill: StateFlow<String?> = _voicePrefill.asStateFlow()

    private fun voiceTransport(): VoiceTransport? {
        val p = profile ?: return null
        val candidates = VoiceApiEndpoints.candidates(p, voicePrefs.url, voicePrefs.lastOk)
        val key = "${p.id}|${p.token}|${candidates.joinToString(",")}"
        val existing = voiceClient?.takeIf { voiceClientKey == key }
        val client = existing ?: VoiceApiClient(candidates, p.token, p.id)
            .also { voiceClient = it; voiceClientKey = key }
        return HttpVoiceTransport(client)
    }

    init {
        voiceMsg.onTranscript = { text ->
            if (voiceMsg.autoSend) send(text) else _voicePrefill.value = text
        }
        // Hata sessiz kalmasın: sohbet akışına bildirim düşer (DiagLog'a da
        // istemci içinden yazılıyor).
        voiceMsg.onNotice = { msg ->
            _state.update {
                it.copy(items = it.items + ChatItem.Notice(nextKey("v"), msg, isError = true))
            }
        }
        voiceMsg.onWorkingBase = { base -> onVoiceBase(base) }
    }

    /** Bas-konuş: parmak indi. */
    fun voiceHoldStart() = voiceMsg.holdStart()

    /** Bas-konuş: parmak kalktı (0,8 sn'den kısa basış atılır). */
    fun voiceHoldRelease() = voiceMsg.holdRelease()

    fun voiceCancel() = voiceMsg.cancelRecording()

    /** Asistan balonunu seslendir/durdur (aynı balona ikinci dokunuş durdurur). */
    fun speak(key: String, text: String) = voiceMsg.speak(key, text)

    fun stopSpeaking() = voiceMsg.stopSpeaking()

    fun consumeVoicePrefill() {
        _voicePrefill.value = null
    }

    /** Ayarlar → Ses → "şimdi dene" satırı. */
    suspend fun voiceHealthLine(): String = voiceMsg.healthLine()

    /**
     * Tur-12: yapılandırılmış `/health` durumu (canlı satır + "Şimdi dene").
     * Hata fırlatmaz — [VoiceStatusLogic.Probe.error] doldurulur.
     */
    suspend fun voiceProbe(): VoiceStatusLogic.Probe = voiceMsg.probe()

    /** Tur-12: `Isıt` — seçili motoru kısa sabit cümleyle ön-yükler. */
    fun voiceWarm(engine: VoiceSpeakLogic.Engine) = voiceMsg.warmEngine(engine)

    /** Tur-12: motor seçimi değişti — eski "Hazır ✓" işareti sıfırlanır. */
    fun voiceWarmReset(engine: VoiceSpeakLogic.Engine) = voiceMsg.resetWarm(engine)

    /** Tur-12: `Isıt` durumu (bölümden çıkılsa da sürer). */
    val voiceWarmState: StateFlow<VoiceStatusLogic.WarmState> = voiceMsg.warm

    /**
     * Açılışta geri dönülecek oturum. `MainActivity` kayıtlı değeri buraya
     * koyar; bağlantı kurulunca bir kez denenir ve temizlenir.
     */
    var restoreSessionId: String? = null

    /** Oturum değişince çağrılır — MainActivity kalıcı kayda yazar. */
    var onSessionChanged: ((String) -> Unit)? = null

    /**
     * Shizuku köprüsü tek örnek: hem yazarak verilen komutlar hem canlı ses
     * aynı bağlantıyı kullanıyor, iki ayrı dinleyici kaydı gereksiz olurdu.
     */
    val shizuku = ShizukuBridge()

    private val phoneTools = PhoneTools(app, shizuku)

    /**
     * Bağlantı kopukken yazılan mesajlar. Soket açılınca sırayla gönderilir.
     *
     * Önceden `client ?: return` ile sessizce düşüyordu: kullanıcı yazıyor,
     * gönder'e basıyor, hiçbir şey olmuyordu. Asansörde ya da hücre-Wi-Fi
     * geçişinde bu sürekli oluyor.
     */
    private val outbox = mutableListOf<String>()

    /**
     * Kısayoldan/asistan hareketinden gelen eylem: "voice" | "driving" | "new".
     * ViewModel'de duruyor çünkü Activity kısayolla **yeniden** yaratılabiliyor;
     * Compose tarafı okuyup sıfırlıyor.
     */
    val pendingAction = MutableStateFlow<String?>(null)

    init {
        // Yanıt beklenirken süreç arka planda dondurulmasın; yanıt gelince
        // servis kapansın. Gerekçe AwaitReplyService'in başında.
        //
        // Tur-10 (F1): her state değişiminde start/stop çağırmak, sistemin
        // startForegroundService sayaçlarıyla yarışıyordu (gerçek cihazda
        // ForegroundServiceDidNotStartInTime). Artık YALNIZ `agentBusy`
        // değiştiğinde haber veriliyor; karar makinesi kalanı serileştiriyor.
        viewModelScope.launch {
            var lastBusy = false
            _state.collect { st ->
                if (st.agentBusy != lastBusy) {
                    lastBusy = st.agentBusy
                    AwaitReplyService.setAwaiting(getApplication(), lastBusy)
                }
            }
        }
        voice.initTts()
        viewModelScope.launch {
            voice.mode.collect { m -> _state.update { it.copy(voiceMode = m) } }
        }
        viewModelScope.launch {
            voice.partial.collect { p -> _state.update { it.copy(voicePartial = p) } }
        }
        viewModelScope.launch {
            voice.error.collect { e -> if (e != null) _state.update { it.copy(notice = e) } }
        }
    }

    /** Aktif profil değiştiyse bağlantıyı yeniden kurar. */
    fun bind(profile: ServerProfile?) {
        if (profile == null || profile.token.isBlank()) {
            teardown()
            _state.value = ChatState(
                // tr(): dil sızıntısı olmasın — hata metni arayüz dilinde.
                connection = ConnectionState.Error(
                    com.hermes.mobile.ui.tr(
                        "Sunucu bağlı değil — \"Sunucu ekle\" ile adres + token girin",
                        "No server connected — tap \"Add server\" and enter address + token",
                    )
                )
            )
            return
        }
        // Yalnız id karşılaştırmak yetmiyordu: kullanıcı tokeni/adresi
        // sonradan düzelttiğinde id aynı kalır ve bu ViewModel eski tokenli
        // eski soketle kalırdı — "Pano bağlı, Sohbet Token reddedildi" işte
        // böyle üretiliyordu. LiveVoiceViewModel'de aynı tuzak zaten düzeltildi.
        val sameProfile = profile.id == profileId && client != null &&
            profile.token == this.profile?.token &&
            profile.baseUrl == this.profile?.baseUrl &&
            profile.remoteUrl == this.profile?.remoteUrl &&
            profile.name == this.profile?.name
        if (sameProfile) return

        teardown()
        profileId = profile.id
        this.profile = profile
        val gw = GatewayWsClient(profile)
        client = gw
        _gateway.value = gw

        // Soket yenilendiğinde oturumu gateway'e yeniden bağla, kaçırılan yanıtı
        // tamamla, bekleyen mesajları gönder. Bu üçü olmadan "oturum koptu".
        gw.onReconnected = {
            viewModelScope.launch { reattach(gw) }
        }

        viewModelScope.launch {
            runCatching { HermesClient(profile).modelInfo() }
                .onSuccess { info ->
                    _state.update { it.copy(currentModel = info.model) }
                }
        }

        stateJob = viewModelScope.launch {
            gw.connection.collect { conn ->
                _state.update { it.copy(connection = conn) }
                // Bağlantı ilk kez açıldığında kayıtlı oturuma dön. Tek deneme:
                // oturum gateway'de artık yoksa sessizce yeni oturumla devam.
                if (conn is ConnectionState.Open) {
                    val restore = restoreSessionId
                    restoreSessionId = null
                    if (restore != null && _state.value.sessionId == null) {
                        continueSession(restore, restore, "önceki konuşma")
                    }
                }
            }
        }
        eventJob = viewModelScope.launch {
            gw.events.collect { event ->
                handleEvent(event.type, event.text, event.toolName, event.sessionId)
            }
        }
        gw.connect()
    }

    private fun teardown() {
        eventJob?.cancel()
        stateJob?.cancel()
        client?.close()
        client = null
        _gateway.value = null
        profileId = null
        streamingKey = null
        thinkingKey = null
        streamMeter.reset()
        _speed.value = null
        // Sesli mesaj: profil değişince/bağlantı düşünce çalan ses ve süren
        // kayıt kapatılır (yeni profile taşınan yarım kayıt olmasın).
        runCatching { voiceMsg.stopSpeaking() }
        runCatching { voiceMsg.cancelRecording() }
    }

    private fun nextKey(prefix: String) = "$prefix-${seq++}"

    /**
     * Yeni oturuma son seçilen modeli uygular.
     *
     * `/model` oturum kapsamlı olduğu için her yeni oturum sunucu varsayılanına
     * dönüyor; kullanıcı açısından "seçimim kayboldu" gibi görünüyordu.
     */
    private suspend fun applyPreferredModel(gw: GatewayWsClient, sid: String) {
        val pref = preferredModel
        if (pref.isBlank()) return
        val parts = pref.split("|", limit = 2)
        if (parts.size != 2) return
        runCatching { gw.slashExec(sid, "/model ${parts[1]} --provider ${parts[0]}") }
            .onSuccess { _state.update { it.copy(currentModel = parts[1]) } }
    }

    fun send(text: String) {
        val body = text.trim()
        val ready = _state.value.attachments.filter { it.remotePath != null }
        if (body.isEmpty() && ready.isEmpty()) return

        // Telefon eylemi mi? Ajan sunucuda koşuyor, telefona ulaşamaz; bu işleri
        // cihazın kendisi yapmalı. Eşleşme yoksa mesaj olduğu gibi ajana gider.
        //
        // Ayardan bağımsız çalışıyor: "WhatsApp aç" diye **yazmak** zaten açık
        // rızadır. Ayar, modelin kendiliğinden telefonu kullanabildiği canlı ses
        // yolunu denetliyor — orada kararı kullanıcı değil model veriyor.
        if (ready.isEmpty()) {
            PhoneIntent.parse(body)?.let { intent ->
                runPhoneAction(body, intent)
                return
            }
        }

        val gw = client
        val online = gw != null && _state.value.connection is ConnectionState.Open

        val label = buildString {
            ready.forEach { appendLine("[${it.label}]") }
            append(body)
        }.trim()

        // Bağlantı yoksa mesajı kaybetme: ekranda göster, kuyruğa al, bağlanınca
        // gönder. Yeniden bağlanma zaten kendiliğinden deneniyor.
        if (!online) {
            outbox += label
            _state.update {
                // Not tek: her mesajda yenisini eklemek yerine sayacı güncelle.
                it.copy(
                    items = it.items.filterNot { item -> item.key == QUEUE_NOTICE_KEY } +
                        ChatItem.User(nextKey("u"), label) +
                        ChatItem.Notice(
                            QUEUE_NOTICE_KEY,
                            tr(
                                "Bağlantı yok — bağlanınca gönderilecek (${outbox.size} bekliyor)",
                                "Offline — will send on reconnect (${outbox.size} queued)",
                            ),
                        ),
                    attachments = emptyList(),
                )
            }
            gw?.connect()
            return
        }

        _state.update {
            it.copy(
                items = it.items + ChatItem.User(nextKey("u"), label),
                sending = true,
                agentBusy = true,
                attachments = emptyList(),
            )
        }

        viewModelScope.launch {
            runCatching {
                val sid = _state.value.sessionId ?: createSessionWithProfile(gw).also { id ->
                    _state.update { it.copy(sessionId = id) }
                    onSessionChanged?.invoke(id)
                    applyPreferredModel(gw, id)
                }
                // Görseller ajana TUI'nin `/image <path>` komutuyla verilir —
                // dashboard'ın yapıştırma akışının aynısı. Diğer dosyalar yolla
                // birlikte metne gömülür; ajan kendi dosya araçlarıyla açar.
                ready.filter { it.kind == AttachmentKind.Image }.forEach { att ->
                    gw.submitPrompt(sid, "/image ${att.remotePath}")
                }
                val fileNote = ready
                    .filter { it.kind == AttachmentKind.File }
                    .joinToString("\n") { "Ek dosya: ${it.remotePath}" }
                val prompt = listOf(fileNote, body).filter { it.isNotBlank() }.joinToString("\n\n")
                if (prompt.isNotBlank()) gw.submitPrompt(sid, prompt)
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        items = it.items + ChatItem.Notice(
                            nextKey("n"),
                            e.message ?: "Gönderilemedi",
                            isError = true,
                        ),
                        agentBusy = false,
                    )
                }
            }
            _state.update { it.copy(sending = false) }
        }
    }

    /**
     * Slash komutu çalıştırır — Telegram bot'undaki komutların aynısı.
     *
     * Kullanıcı ne yazdığını görsün diye komut sohbete kullanıcı mesajı gibi
     * düşer, çıktı da altına gelir. `/new` ve `/clear` yerel akışı da temizler,
     * yoksa ekranda eski oturumun mesajları kalırdı.
     */
    fun runSlash(command: String) {
        val gw = client ?: return
        _state.update {
            it.copy(items = it.items + ChatItem.User(nextKey("u"), command), agentBusy = true)
        }
        viewModelScope.launch {
            runCatching {
                val sid = _state.value.sessionId ?: createSessionWithProfile(gw).also { id ->
                    _state.update { it.copy(sessionId = id) }
                    onSessionChanged?.invoke(id)
                }
                gw.slashExec(sid, command)
            }
                .onSuccess { output ->
                    val base = command.removePrefix("/").substringBefore(' ').lowercase()
                    if (base == "new" || base == "clear") {
                        streamingKey = null
                        thinkingKey = null
                        _state.update {
                            ChatState(
                                connection = it.connection,
                                currentModel = it.currentModel,
                                items = listOf(
                                    ChatItem.Notice(nextKey("n"), output.ifBlank { "Yeni oturum" })
                                ),
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                items = it.items + ChatItem.Assistant(
                                    nextKey("a"),
                                    output.ifBlank { "(çıktı yok)" },
                                ),
                                agentBusy = false,
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            items = it.items + ChatItem.Notice(
                                nextKey("n"),
                                e.message ?: "Komut çalıştırılamadı",
                                isError = true,
                            ),
                            agentBusy = false,
                        )
                    }
                }
        }
    }

    /**
     * Bir promptu AI ile iyileştirir — **aktif sohbete düşmeyen** görünmez
     * tek kullanımlık oturumda.
     *
     * Akış: `session.create` (kendi sid'miz) → tek `prompt.submit` →
     * `message.complete` bekle (30 sn) → kimliği görünmez listeden çıkar.
     *
     * Aktif sohbetin akışından iki yönde yalıtılır:
     *  · `invisibleSessions` — o oturumun olayları `handleEvent`'e giremez;
     *  · `_state.sessionId`/`onSessionChanged` hiçe dokunmaz, UI listesi
     *    (canlı oturumlar) açılan oturumu aktif saymaz.
     *
     * Hata/zaman aşımında exception fırlatır; çağıran sheet "AI şu anda
     * kullanılamıyor" gösterir — akış kilitlenmez, metin olduğu gibi
     * kaydedilebilir.
     */
    suspend fun improvePrompt(metin: String): String {
        val gw = client ?: throw IllegalStateException(tr("Bağlantı yok", "No connection"))
        val istek = tr(
            "Şu komutu net, eksiksiz ve tekrar kullanılabilir hale getir. " +
                "Sadece düzeltilmiş metni dön, açıklama yazma:",
            "Rewrite the following prompt to be clear, complete and reusable. " +
                "Return only the corrected text, no explanation:",
        ) + "\n\n" + metin

        val sid = gw.createSession(activeProfile)
        invisibleSessions += sid

        val birikim = StringBuilder()
        val tamam = CompletableDeferred<String>()
        val job = viewModelScope.launch {
            gw.events.collect { e ->
                if (e.sessionId != sid) return@collect
                when (e.type) {
                    "message.delta" -> birikim.append(e.text.orEmpty())
                    "message.complete" ->
                        tamam.complete(birikim.toString().ifBlank { e.text.orEmpty() })
                    "error" ->
                        tamam.completeExceptionally(
                            IllegalStateException(e.text ?: tr("AI hatası", "AI error")),
                        )
                }
            }
        }
        try {
            gw.submitPrompt(sid, istek)
            val cevap = withTimeoutOrNull(30_000) { tamam.await() }
                ?: run {
                    // Üretimi sunucuda boşuna sürmesin; sonra hata metniyle dön.
                    runCatching { gw.interrupt(sid) }
                    throw IllegalStateException(tr("AI yanıt vermedi", "AI did not reply"))
                }
            return cevap.trim()
        } finally {
            job.cancel()
            invisibleSessions -= sid
        }
    }

    /** Bekleyen onayı yanıtlar — Telegram'daki inline düğmelerin karşılığı. */
    fun answerApproval(key: String, approve: Boolean, reason: String = "") {
        val gw = client ?: return
        val sid = _state.value.sessionId ?: return
        val command = if (approve) "/approve" else
            if (reason.isBlank()) "/deny" else "/deny ${reason.trim()}"

        _state.update { st ->
            st.copy(
                items = st.items.map {
                    if (it is ChatItem.Approval && it.key == key)
                        it.copy(answered = if (approve) "Onaylandı" else "Reddedildi")
                    else it
                }
            )
        }

        viewModelScope.launch {
            runCatching { gw.slashExec(sid, command) }.onFailure { e ->
                _state.update {
                    it.copy(
                        items = it.items + ChatItem.Notice(
                            nextKey("n"),
                            e.message ?: "Yanıt iletilemedi",
                            isError = true,
                        )
                    )
                }
            }
        }
    }

    /**
     * Modeli gerçekten çalışıyor mu diye dener.
     *
     * Gateway'in "✓ switched" yanıtı yalnız yapılandırmanın yazıldığını söylüyor;
     * modelin ayakta olup olmadığını söylemiyor. LM Studio kapalıyken ya da
     * sağlayıcı erişilemezken ilk gerçek mesajda çöküyor. Küçük bir istemle
     * önceden deniyoruz; başarısızsa eski modele dönüyoruz.
     */
    private fun verifyModel(provider: String, model: String, previous: String?) {
        val gw = client ?: return
        val sid = _state.value.sessionId ?: return
        viewModelScope.launch {
            _modelCheck.value = model
            val started = System.currentTimeMillis()
            val ok = runCatching {
                withTimeoutOrNull(45_000) { probeModel(gw, sid) } == true
            }.getOrDefault(false)
            _modelCheck.value = null

            if (ok) {
                onModelChosen?.invoke(provider, model)
                val secs = (System.currentTimeMillis() - started) / 1000.0
                _state.update {
                    it.copy(
                        items = it.items + ChatItem.Notice(
                            nextKey("n"),
                            "✓ $model hazır (${"%.1f".format(secs)} sn)",
                        ),
                    )
                }
                return@launch
            }

            onModelBroken?.invoke("$provider/$model")
            // Eski modele dön ki sohbet kullanılabilir kalsın.
            previous?.let { runCatching { gw.slashExec(sid, "/model $it") } }
            _state.update {
                it.copy(
                    currentModel = previous ?: it.currentModel,
                    items = it.items + ChatItem.Notice(
                        nextKey("n"),
                        "$model yanıt vermedi — listeden gizlendi" +
                            (previous?.let { p -> ", $p modeline dönüldü" } ?: ""),
                        isError = true,
                    ),
                )
            }
        }
    }

    /** Tek turluk minik istem; yanıt gelirse model ayakta demektir. */
    private suspend fun probeModel(gw: GatewayWsClient, sid: String): Boolean {
        var done = false
        val job = viewModelScope.launch {
            gw.events.collect { e ->
                when (e.type) {
                    "message.complete" -> { done = true }
                    "error" -> { done = false; throw kotlinx.coroutines.CancellationException() }
                }
            }
        }
        return try {
            gw.submitPrompt(sid, "Sadece: OK")
            var waited = 0
            while (!done && waited < 45_000) {
                kotlinx.coroutines.delay(250)
                waited += 250
            }
            done
        } catch (e: Exception) {
            false
        } finally {
            job.cancel()
        }
    }

    /** Süren üretimi keser. */
    fun stopGeneration() {
        val gw = client ?: return
        val sid = _state.value.sessionId ?: return
        voice.stopSpeaking()
        viewModelScope.launch {
            runCatching { gw.interrupt(sid) }
            streamingKey = null
            streamMeter.reset()
            _speed.value = null
            _state.update { st ->
                st.copy(
                    agentBusy = false,
                    items = st.items.map {
                        if (it is ChatItem.Assistant && it.streaming) it.copy(streaming = false)
                        else it
                    },
                )
            }
        }
    }

    /**
     * ⋯ → "Müdahale" (KALAN-2): ÇALIŞAN ajana açık sohbetten talimat iletir.
     *
     * Canlı sekmesindeki müdahale ile aynı sözleşme: `Ekle` (`session.steer`)
     * turu kesmez, ajan bir sonraki adımında görür; `Yönlendir`
     * (`session.redirect`) süren turu çevirir. Yönlendirmeyi desteklemeyen
     * ajanlarda sunucu `queued` dışında bir durum döner — o zaman sessizce
     * `steer`e düşülür (kullanıcının niyeti her hâlükârda mesajı ulaştırmak).
     *
     * Sonuç, sohbete tek satır not olarak yazılır: kullanıcı talimatının
     * gerçekten gittiğini görür (sessiz başarısızlık yok).
     */
    fun intervene(kind: InterventionKind, text: String) {
        val gw = client ?: return
        val sid = _state.value.sessionId ?: return
        val body = text.trim()
        if (body.isEmpty()) return

        viewModelScope.launch {
            val primary = runCatching {
                if (kind == InterventionKind.Redirect) gw.redirect(sid, body)
                else gw.steer(sid, body)
            }.getOrElse { "error" }

            val finalStatus =
                if (kind == InterventionKind.Redirect && primary != "queued") {
                    runCatching { gw.steer(sid, body) }.getOrElse { "error" }
                } else {
                    primary
                }

            _state.update { st ->
                st.copy(
                    items = st.items +
                        ChatItem.Notice(nextKey("n"), interventionNotice(kind, finalStatus)),
                )
            }
        }
    }

    /**
     * Telefon eylemini çalıştırır ve sonucu sohbete araç kartı olarak yazar.
     *
     * Sunucuya hiç gitmiyor — bu yüzden çevrimdışı da çalışıyor ve anında.
     */
    private fun runPhoneAction(userText: String, intent: PhoneIntent.Action) {
        val result = phoneTools.execute(intent.tool, intent.toJson())
        _state.update {
            it.copy(
                items = it.items +
                    ChatItem.User(nextKey("u"), userText) +
                    ChatItem.Tool(nextKey("t"), intent.tool, ToolState.Done, result) +
                    // Okuma araçlarında cevabın **kendisi** istenen şey. Araç
                    // kartı içeriği katlayıp yalnız uzunluğunu gösteriyor
                    // ("88 krkt"), yani "neredeyim" sorusunun karşılığı
                    // görünmüyordu. Yazma araçlarında ("feneri aç") kart
                    // yeterli — eylem zaten göz önünde gerçekleşiyor.
                    listOfNotNull(
                        result
                            .takeIf { PhoneTools.isReadTool(intent.tool) && it.isNotBlank() }
                            ?.let { text -> ChatItem.Assistant(nextKey("a"), text) },
                    ),
            )
        }
    }

    // ── Ekler ─────────────────────────────────────────────────────────

    fun attachImage(dataUrl: String, filename: String) {
        val p = profile ?: return
        val placeholder = PendingAttachment(filename, AttachmentKind.Image)
        _state.update { it.copy(attachments = it.attachments + placeholder) }
        viewModelScope.launch {
            runCatching { HermesClient(p).uploadChatImage(dataUrl, filename) }
                .onSuccess { res -> markAttachment(filename, res.path, null) }
                .onFailure { e -> markAttachment(filename, null, e.message ?: "Yüklenemedi") }
        }
    }

    fun attachFile(bytes: ByteArray, filename: String) {
        val p = profile ?: return
        val placeholder = PendingAttachment(filename, AttachmentKind.File)
        _state.update { it.copy(attachments = it.attachments + placeholder) }
        viewModelScope.launch {
            runCatching { HermesClient(p).uploadManagedFile(bytes, filename) }
                .onSuccess { path -> markAttachment(filename, path, null) }
                .onFailure { e -> markAttachment(filename, null, e.message ?: "Yüklenemedi") }
        }
    }

    private fun markAttachment(label: String, path: String?, error: String?) {
        _state.update { st ->
            st.copy(
                attachments = st.attachments.map {
                    if (it.label == label && it.uploading)
                        it.copy(remotePath = path, uploading = false, error = error)
                    else it
                }
            )
        }
    }

    fun removeAttachment(label: String) {
        _state.update { it.copy(attachments = it.attachments.filterNot { a -> a.label == label }) }
    }

    // ── Ses ───────────────────────────────────────────────────────────

    /** Tek seferlik dikte: konuş, metin kutusuna değil doğrudan gönderilir. */
    fun startDictation() {
        voice.startListening { text -> send(text) }
    }

    fun stopDictation() = voice.stopListening()

    /** Eller-serbest sesli sohbet: yanıt bitince kendiliğinden yeniden dinler. */
    fun toggleHandsFree() {
        val next = !_state.value.handsFree
        _state.update { it.copy(handsFree = next) }
        voice.setHandsFree(next) { text -> send(text) }
    }

    // ── Model ─────────────────────────────────────────────────────────

    private val _modelProviders = MutableStateFlow<List<ModelProvider>>(emptyList())
    val modelProviders: StateFlow<List<ModelProvider>> = _modelProviders.asStateFlow()

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    fun loadModels() {
        val p = profile ?: return
        if (_modelProviders.value.isNotEmpty()) return
        viewModelScope.launch {
            _modelsLoading.value = true
            runCatching { HermesClient(p).modelOptions() }
                .onSuccess { _modelProviders.value = it.providers }
                .onFailure { e -> _state.update { it.copy(notice = e.message ?: "Modeller alınamadı") } }
            _modelsLoading.value = false
        }
    }

    /**
     * Modeli değiştirir.
     *
     * REST `/api/model/set` yalnız **config'i** yazıyor; çalışan oturum eski
     * modelde kalıyordu (kullanıcı "model değişmiyor, Agnes kalıyor" dedi).
     * Gateway'in `/model` slash komutu ise oturum kapsamlı ve anında etkili
     * (`_session_model_overrides`). Bu yüzden asıl geçiş slash ile yapılıyor;
     * [persist] verilirse `--global` eklenerek varsayılan da güncellenir.
     */
    /** Model doğrulama sonucu — UI'ın geri bildirim verebilmesi için. */
    private val _modelCheck = MutableStateFlow<String?>(null)
    val modelCheck: StateFlow<String?> = _modelCheck.asStateFlow()

    /**
     * Bot (profil) ataması: YENİ sohbet başlatırken `createSession(profile)`
     * argümanını belirler — Composer üstündeki çipten seçilen profil.
     *
     * - `selectedProfile == null` (varsayılan "Yönlendirici") → profil
     *   gönderilmez, sunucunun aktif yönlendirmesi (activeProfile) işler.
     * - Profil adı seçilirse → o profilin SOUL.md'si + beceri seti açılır.
     */
    private suspend fun createSessionWithProfile(gw: GatewayWsClient): String {
        val profileArg = createSessionProfileArg(selectedProfileValue ?: ROUTER_CHIP)
            ?: activeProfile.takeIf { it.isNotBlank() }
            ?: null
        return gw.createSession(profileArg).also { id ->
            _sessionProfile.value = profileArg
            onSessionChanged?.invoke(id)
        }
    }

    /** Bir model başarısız olduğunda çağrılır; ayarlara "bozuk" diye yazılır. */
    var onModelBroken: ((String) -> Unit)? = null

    /**
     * Bot (profil) ataması — Composer üstündeki yatay çiplerden seçilen
     * profil. `null` = varsayılan "Yönlendirici" (profil gönderilmez,
     * sunucunun aktif yönlendirmesi). YENİ sohbetlerde createSession
     * argümanı olur; mevcut oturumda çipler salt-okunur kalır.
     *
     * Kalıcılık: `onProfileChipSelected` (settings deposu) yazdırır.
     */
    private val _selectedProfile = MutableStateFlow<String?>(null)
    /**
     * Bot (profil) ataması — Composer üstündeki yatay çiplerden seçilen
     * profil. `null` = varsayılan "Yönlendirici" (profil gönderilmez,
     * sunucunun aktif yönlendirmesi). YENİ sohbetlerde createSession
     * argümanı olur; mevcut oturumda çipler salt-okunur kalır.
     *
     * Kalıcılık: [onProfileChipSelected] (settings deposu) yazdırır;
     * [selectedProfileValue] muter üzerinden değişir.
     */
    val selectedProfile: StateFlow<String?> = _selectedProfile.asStateFlow()
    var selectedProfileValue: String?
        get() = _selectedProfile.value
        set(value) {
            _selectedProfile.value = value
            onProfileChipSelected?.invoke(value)
        }

    /** Kalıcı yazım — ayarlar deposu (settings). */
    var onProfileChipSelected: ((String?) -> Unit)? = null

    /** Doğrulanmış model seçimini kalıcı yapmak için (ayarlar deposuna yazar). */
    var onModelChosen: ((provider: String, model: String) -> Unit)? = null

    /** Uygulama açılışında ayarlardan yüklenen son seçim — "sağlayıcı|model". */
    var preferredModel: String = ""

    /** Yeni oturumların açılacağı Hermes profili; boşsa varsayılan. */
    var activeProfile: String = ""

    /**
     * Mevcut oturumun profili (session.create'de gönderilen; sunucudan
     * dönmiyorsa ayarlanan). `null` = oturum yok / profil bilinmiyor.
     * Composer üstündeki çipler bu değerle kilitlenir: mevcut oturumda
     * salt-okunur ve bu profil (bilinmiyorsa "—") gösterilir.
     */
    private val _sessionProfile = MutableStateFlow<String?>(null)
    val sessionProfile: StateFlow<String?> = _sessionProfile.asStateFlow()

    private val _terminal = MutableStateFlow<List<TerminalLine>>(emptyList())
    val terminal: StateFlow<List<TerminalLine>> = _terminal.asStateFlow()

    private val _terminalBusy = MutableStateFlow(false)
    val terminalBusy: StateFlow<Boolean> = _terminalBusy.asStateFlow()

    fun clearTerminal() { _terminal.value = emptyList() }

    /**
     * Terminal komutu çalıştırır.
     *
     * `/` ile başlıyorsa slash komutu; değilse ajana görev olarak gönderilir
     * (ajan kendi araçlarıyla, onay kapıları dahil çalıştırır).
     */
    fun runTerminal(input: String) {
        val cmd = input.trim()
        if (cmd.isEmpty()) return
        val gw = client ?: return

        _terminal.update { it + TerminalLine(cmd, isCommand = true) }
        _terminalBusy.value = true

        viewModelScope.launch {
            runCatching {
                val sid = _state.value.sessionId ?: createSessionWithProfile(gw).also { id ->
                    _state.update { it.copy(sessionId = id) }
                }
                if (cmd.startsWith("/")) {
                    gw.slashExec(sid, cmd)
                } else {
                    // Düz metin: ajana görev ver, yanıtını topla.
                    gw.submitPrompt(sid, cmd)
                    "(ajana iletildi — yanıt Sohbet sekmesinde)"
                }
            }
                .onSuccess { out ->
                    _terminal.update {
                        it + TerminalLine(out.ifBlank { "(çıktı yok)" })
                    }
                }
                .onFailure { e ->
                    _terminal.update {
                        it + TerminalLine(e.message ?: "komut başarısız", isError = true)
                    }
                }
            _terminalBusy.value = false
        }
    }


    fun selectModel(provider: String, model: String, persist: Boolean = false) {
        val gw = client ?: return
        val previous = _state.value.currentModel
        viewModelScope.launch {
            runCatching {
                val sid = _state.value.sessionId ?: createSessionWithProfile(gw).also { id ->
                    _state.update { it.copy(sessionId = id) }
                }
                val cmd = buildString {
                    append("/model ").append(model)
                    append(" --provider ").append(provider)
                    if (persist) append(" --global")
                }
                gw.slashExec(sid, cmd)
            }
                .onSuccess { output ->
                    // Gateway "✓ Model switched: X" döner; hata metni de gelebilir.
                    // Kredi yetersizliği gibi durumlarda "requires available credits"
                    // geçiyor — bunu başarı sayarsak ajan ilk mesajda çöküyor.
                    val credit = output.contains("credit", ignoreCase = true) ||
                        output.contains("balance", ignoreCase = true)
                    val switched = !credit &&
                        (output.contains("switched", ignoreCase = true) || output.contains("✓"))

                    if (!switched) {
                        onModelBroken?.invoke("$provider/$model")
                        _state.update {
                            it.copy(
                                items = it.items + ChatItem.Notice(
                                    nextKey("n"),
                                    output.trim().ifBlank { "Model değiştirilemedi" },
                                    isError = true,
                                ),
                            )
                        }
                        return@onSuccess
                    }

                    _state.update {
                        it.copy(
                            currentModel = model,
                            items = it.items + ChatItem.Notice(nextKey("n"), "$model deneniyor…"),
                        )
                    }
                    verifyModel(provider, model, previous)
                }
                .onFailure { e ->
                    _state.update { it.copy(notice = e.message ?: "Model değiştirilemedi") }
                }
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    /**
     * Argümansız `/reasoning` çalıştırıp çıktıyı ayrıştırır — "Düşünce panosu".
     *
     * Sonuç ekrana düz metin değil yapı olarak düşer: [reasoningStatus] akışı
     * güncellenir, panel çiplerini buna göre seçili gösterir. `/reasoning`
     * argümanla da (`/reasoning high`) aynı yoldan geçer; argümanlı çağrıda
     * sunucu yeni çabayı onaylar, ayrıştırıcı satırı okuyamazsa `null` kalır
     * ve panel seçimi boşa düşürür (SAF: asla varsayılanı uydurmaz).
     */
    private val _reasoningStatus = MutableStateFlow(ReasoningStatus(null, null))
    val reasoningStatus: StateFlow<ReasoningStatus> = _reasoningStatus.asStateFlow()

    fun refreshReasoning() {
        val gw = client ?: return
        viewModelScope.launch {
            runCatching {
                val sid = _state.value.sessionId ?: gw.createSession(activeProfile)
                val out = gw.slashExec(sid, "/reasoning")
                _reasoningStatus.value = parseReasoningOutput(out)
            }.onFailure { e ->
                // Bağlantı düşerse paneli boşaltma — son bilineni göster;
                // yalnız gerçek parse boşluğunda null döner.
                DiagLog.w("chat", "refreshReasoning failed: ${e.message}")
            }
        }
    }

    /**
     * Düşünce çabasını yer, panelin çipleri argümansız komutu çağıran
     * [refreshReasoning] ile okunur; buradan yalnız "yazma" yarı yolu.
     */
    fun setReasoningEffort(level: String) {
        runSlash("/reasoning $level")
    }

    /**
     * Yeniden bağlandıktan sonra oturumu toparlar.
     *
     * Sıra önemli: önce oturumu gateway'e bağla (yoksa `prompt.submit` boşa
     * gider), sonra kaçırılan yanıtı çek, en son bekleyen mesajları gönder.
     */
    private suspend fun reattach(gw: GatewayWsClient) {
        val sid = _state.value.sessionId
        DiagLog.i("chat", "post-reconnect recovery · session=${sid ?: "none"} · queued=${outbox.size}")
        // `sid?.let { }` yazmıyoruz: `recoverCatching`'in lambda parametresi de
        // `it` ve dıştakini gölgeliyor — oturum kimliği yerine istisna geçerdi.
        if (sid != null) {
            runCatching { gw.activateSession(sid) }
                .recoverCatching { gw.resumeSession(sid) }
                // Bu sessizce başarısız olursa `prompt.submit` boşa gidiyor ve
                // kullanıcı sonsuza kadar "Düşünüyor" görüyor — hiçbir yerde iz
                // kalmadığı için tam olarak bu, teşhis edilemeyen şikayetti.
                .onFailure { e -> DiagLog.e("chat", "could not re-attach to session", e) }
        }
        syncPending()
        flushOutbox()
    }

    /** Bağlantı kopukken birikenleri sırayla gönderir. */
    private suspend fun flushOutbox() {
        if (outbox.isEmpty()) return
        val gw = client ?: return
        val queued = outbox.toList()
        outbox.clear()
        for (body in queued) {
            runCatching {
                val sid = _state.value.sessionId ?: createSessionWithProfile(gw).also { id ->
                    _state.update { it.copy(sessionId = id) }
                    onSessionChanged?.invoke(id)
                    applyPreferredModel(gw, id)
                }
                gw.submitPrompt(sid, body)
            }.onFailure { e ->
                // Gönderemediysek geri koy; bir dahaki bağlanmada denenir.
                DiagLog.w("chat", "queued message failed to send, re-queued: ${e.message}")
                outbox.add(0, body)
                return
            }
        }
        // Kuyruk boşaldı: "bekliyor" notu artık yanlış bilgi.
        _state.update { st ->
            st.copy(
                items = st.items.filterNot { it.key == QUEUE_NOTICE_KEY },
                agentBusy = true,
            )
        }
    }

    /**
     * Öne dönüldüğünde kaçırılan yanıtı sunucudan tamamlar.
     *
     * Arka planda soket dondurulup ölebiliyor; ajan yanıtı sunucuda üretiliyor
     * ama `message.complete` bize hiç ulaşmıyor ve ekranda "Düşünüyor" asılı
     * kalıyor. Ölçtük: sunucuda 2 mesajlık tamamlanmış oturum vardı, telefon
     * hâlâ bekliyordu. Burada geçmişi çekip son asistan mesajını yerine koyuyoruz.
     */
    fun syncPending() {
        if (!_state.value.agentBusy) return
        val sid = _state.value.sessionId ?: return
        val gw = client ?: return
        val p = profile ?: return

        viewModelScope.launch {
            val msgs = runCatching { gw.sessionHistory(sid) }.getOrDefault(emptyList())
                .ifEmpty {
                    runCatching { HermesClient(p).sessionMessages(sid) }.getOrDefault(emptyList())
                }
            val last = msgs.lastOrNull { it.isAssistant && !it.content.isNullOrBlank() }
                ?: return@launch
            val text = last.content!!.trim()

            val shown = _state.value.items.any {
                it is ChatItem.Assistant && !it.streaming && it.text.trim() == text
            }
            streamingKey = null
            thinkingKey = null
            _state.update { st ->
                st.copy(
                    items = if (shown) st.items else
                        st.items.filterNot { it is ChatItem.Assistant && it.streaming } +
                            ChatItem.Assistant(nextKey("a"), text),
                    agentBusy = false,
                    statusLine = null,
                )
            }
        }
    }

    /** Yeni bir oturum başlatır — mevcut akışı temizler. */
    fun newSession() {
        _state.update { ChatState(connection = it.connection) }
        streamingKey = null
        thinkingKey = null
        streamMeter.reset()
        _speed.value = null
        _sessionProfile.value = null
        onSessionChanged?.invoke("")
    }

    /**
     * Var olan bir oturuma bağlanıp konuşmaya kaldığı yerden devam eder.
     *
     * @param liveId gateway'in süreç içi kimliği — `prompt.submit` bunu bekler
     * @param dbId veritabanı kimliği — geçmiş mesajlar REST'ten bununla çekilir
     *
     * Telegram'dan, cron'dan ya da CLI'dan başlamış bir konuşma böylece
     * telefondan sürdürülebilir.
     */
    fun continueSession(liveId: String, dbId: String, title: String) {
        val p = profile ?: return
        val gw = client ?: return
        onSessionChanged?.invoke(liveId)
        // Tur-8: sol ray "son açılanlar" halkası — hangi yoldan gelinirse gelinsin
        // (ray, Oturumlar paneli, paylaşım hedefi) tek yerde kaydedilir.
        noteRecentRail(liveId = liveId, dbId = dbId, title = title)

        streamingKey = null
        thinkingKey = null
        streamMeter.reset()
        _speed.value = null
        // Mevcut oturuma bağlanıldı → çipler kilitlenir; profil sunucudan
        // bilinmiyor (canlı oturumlarda profili öğrenmek yok), bu yüzden
        // null ("—"). Oturum düşerse sessizce yeni oturum açılır ve profil
        // seçimi yeniden serbestleşir.
        _sessionProfile.value = null
        _state.update {
            ChatState(
                connection = it.connection,
                currentModel = it.currentModel,
                sessionId = liveId,
                // Tur-4 (P3 #8): üst şerit KONUYU gösterir — model orada durmaz.
                topic = title,
                // KALAN-1: geçmiş gelene kadar iskelet balonlar (aşağıda
                // item'lar yazılınca/boş çıkınca kapanır).
                historyLoading = true,
                items = listOf(
                    ChatItem.Notice(nextKey("n"), "\"$title\" konuşmasına bağlanıldı")
                ),
            )
        }

        viewModelScope.launch {
            // Oturum gateway'in belleğinde hâlâ duruyor mu? Duruyorsa tam
            // süreklilik (aynı bağlam); durmuyorsa geçmişi yine gösteririz ama
            // sonraki mesaj yeni oturum açmalı — ölü kimliğe prompt göndermek
            // sessizce başarısız olurdu.
            val alive = runCatching { gw.activateSession(liveId) }
                .recoverCatching { gw.resumeSession(liveId) }
                .isSuccess

            if (!alive) {
                _state.update { it.copy(sessionId = null) }
                onSessionChanged?.invoke("")
            }

            // Canlı oturumun geçmişi gateway belleğinde; REST'te görünmüyor.
            // Oturum düştüyse veritabanına düşüyoruz.
            val fromLive =
                if (alive) runCatching { gw.sessionHistory(liveId) }.getOrDefault(emptyList())
                else emptyList()
            val all = fromLive.ifEmpty {
                runCatching { HermesClient(p).sessionMessages(dbId) }
                    .getOrDefault(emptyList())
                    .ifEmpty {
                        // Süreç içi kimlik ile veritabanı kimliği farklı olabiliyor
                        // (`id` vs `session_key`). Açılışta elimizde yalnız
                        // birincisi var; eşleşmezse en son konuşmaya düşüyoruz —
                        // kullanıcının "kaldığım yer" dediği şey zaten o.
                        runCatching {
                            val client = HermesClient(p)
                            // En son başlayan, mesajı olan oturum — "kaldığım
                            // yer" bu. En büyük oturum değil.
                            val newest = client.sessions()
                                .filter { it.messageCount > 0 }
                                .maxByOrNull { it.startedAt ?: 0.0 }
                            newest?.let { client.sessionMessages(it.id) }.orEmpty()
                        }.getOrDefault(emptyList())
                    }
            }

            if (all.isEmpty()) {
                // Ne canlı ne veritabanı — kullanıcıyı hata metniyle karşılamak
                // yerine temiz bir sohbetle başlat.
                _state.update { st ->
                    st.copy(
                        items = if (alive) st.items else emptyList(),
                        sessionId = if (alive) st.sessionId else null,
                        historyLoading = false,
                    )
                }
                return@launch
            }

            // Uzun oturumlar (361 mesaj görüldü) tek seferde çizilince hem
            // yavaş hem okunmaz; son kısmı yeterli.
            val trimmed = if (all.size > HISTORY_LIMIT) all.takeLast(HISTORY_LIMIT) else all
            val restored = trimmed.mapNotNull { m ->
                when {
                    // Kusur I (tur-5): sistem/cron istemi `isUser` olarak dönüyor;
                    // ham hâliyle balon basılıyordu — sohbet değildir, çizilmez.
                    m.isUser && !m.content.isNullOrBlank() && visibleUserMessage(m.content) ->
                        ChatItem.User(nextKey("u"), m.content)
                    m.isAssistant && !m.content.isNullOrBlank() ->
                        ChatItem.Assistant(nextKey("a"), m.content)
                    m.isTool ->
                        ChatItem.Tool(nextKey("t"), m.toolName ?: "araç", ToolState.Done, m.content)
                    else -> null
                }
            }
            // Filtreden sonra hiç konuşma kalmadıysa ekran bomboş kalmasın:
            // sebebini tek satır söyle (kullanıcı "mesajlarım nerede" demesin).
            val body = if (restored.isEmpty()) {
                listOf(
                    ChatItem.Notice(
                        nextKey("n"),
                        tr(
                            "Bu oturumda gösterilecek sohbet mesajı yok — yalnız sistem/araç kayıtları var.",
                            "Nothing to show in this session — only system/tool records.",
                        ),
                    )
                )
            } else {
                restored
            }
            val header = if (all.size > trimmed.size) {
                listOf(
                    ChatItem.Notice(
                        nextKey("n"),
                        "${all.size} mesajlık geçmişin son ${trimmed.size} tanesi gösteriliyor",
                    )
                )
            } else emptyList()

            val footer = ChatItem.Notice(
                nextKey("n"),
                if (alive) tr("— buradan devam —", "— continue here —")
                else tr(
                    "— önceki oturum sunucuda kapanmış; yazınca yeni oturum açılır —",
                    "— previous session closed on the server; typing starts a new one —",
                ),
            )

            _state.update { st -> st.copy(items = header + body + footer, historyLoading = false) }
        }
    }

    /**
     * Bir olayın **görünmez** oturuma ait olup olmadığını söyler.
     *
     * [improvePrompt] kendi session_id'siyle çalışır; o oturumun
     * message.delta / message.complete olayları aktif sohbetin akışına
     * DÜŞMEMELİ — yoksa kullanıcı kendi mesajının altında AI iyileştirme
     * metnini görürdü. [invisibleSessions] görünmez oturumların kimliklerini
     * tutar; bu kümedeki bir oturumdan gelen olay atlanır.
     */
    private val invisibleSessions = mutableSetOf<String>()

    private fun isFromInvisibleSession(sessionId: String?): Boolean =
        sessionId != null && sessionId in invisibleSessions

    private fun handleEvent(type: String, text: String?, toolName: String?, sessionId: String? = null) {
        if (isFromInvisibleSession(sessionId)) return
        when (type) {
            "message.start" -> {
                // Yeni yanıt: saat sıfırdan başlar, göstergenin donmuş hali düşer.
                streamMeter.reset()
                _speed.value = null
                streamingKey = nextKey("a")
                _state.update {
                    it.copy(
                        items = it.items + ChatItem.Assistant(streamingKey!!, "", streaming = true),
                        agentBusy = true,
                    )
                }
            }

            "message.delta" -> {
                // Yanıtın ilk parçası: canlı düşünce bloğu kapanır (katlanır),
                // hız aynı StreamMeter'da kesintisiz sürer.
                sealLiveThinking()
                appendToStream(text.orEmpty())
            }

            "message.complete" -> {
                // Hız göstergesi burada DONAR: son anlık değer ekranda kalır,
                // UI 600 ms sönüşle kaldırır (ChatScreen tarafında).
                val frozenSpeed = streamMeter.finish(System.nanoTime())
                if (frozenSpeed.active) _speed.value = frozenSpeed
                val key = streamingKey
                streamingKey = null
                thinkingKey = null
                var spoken = ""
                _state.update { st ->
                    st.copy(
                        items = st.items.map { item ->
                            if (item is ChatItem.Assistant && item.key == key) {
                                val finalText =
                                    if (item.text.isBlank()) text.orEmpty() else item.text
                                spoken = finalText
                                item.copy(text = finalText, streaming = false)
                            } else if (item is ChatItem.Thinking && item.live) {
                                // Akış bitti: kalan canlı düşünce bloğu katlanır.
                                item.copy(live = false)
                            } else item
                        },
                        agentBusy = false,
                        statusLine = null,
                    )
                }
                // Sesli kip açıkken yanıtı oku; TTS bitince eller-serbest döngü
                // kendiliğinden yeniden dinlemeye geçer.
                if (_state.value.handsFree && spoken.isNotBlank()) voice.speak(spoken)
                // Kullanıcı uygulamadan çıktıysa yanıtı bildirim olarak göster.
                if (spoken.isNotBlank()) {
                    Notifier.agentReply(
                        getApplication(),
                        spoken,
                        sessionId = _state.value.sessionId,
                    )
                }
            }

            "thinking.delta", "reasoning.delta" -> appendToThinking(text.orEmpty())

            "status.update" -> _state.update { it.copy(statusLine = text) }

            "tool.start" -> _state.update {
                // Araç çalışmaya başladı: canlı düşünce bloğu kapanır.
                sealLiveThinking()
                it.copy(
                    items = it.items + ChatItem.Tool(
                        nextKey("t"),
                        toolName ?: "araç",
                        ToolState.Running,
                    ),
                    agentBusy = true,
                )
            }

            "tool.complete" -> updateLastTool(toolName, ToolState.Done)

            "error" -> {
                streamingKey = null
                // Akış hatayla kesildi: ölçer donsun, UI sönüşle kaldırsın.
                streamMeter.reset()
                _speed.value = null
                sealLiveThinking()
                _state.update {
                    it.copy(
                        items = it.items + ChatItem.Notice(
                            nextKey("n"),
                            text ?: "Ajan hatası",
                            isError = true,
                        ),
                        agentBusy = false,
                    )
                }
            }

            // Telegram'da bunlar inline düğmeyle yanıtlanıyor; burada da
            // onayla/reddet düğmeleri gösterilir (`/approve`, `/deny`).
            "approval.request", "sudo.request" -> _state.update {
                it.copy(
                    items = it.items + ChatItem.Approval(
                        nextKey("ap"),
                        text ?: "Ajan tehlikeli bir komut için onay istiyor",
                        isSudo = type == "sudo.request",
                    ),
                    agentBusy = false,
                )
            }

            "clarify.request", "secret.request" -> _state.update {
                it.copy(
                    items = it.items + ChatItem.Notice(
                        nextKey("n"),
                        text ?: "Ajan ek bilgi istiyor — yanıtı yazıp gönderin",
                    ),
                    agentBusy = false,
                )
            }
        }
    }

    private fun appendToStream(chunk: String) {
        if (chunk.isEmpty()) return
        streamMeter.delta(chunk, System.nanoTime(), StreamMeter.PHASE_WRITING)
            ?.let { _speed.value = it }

        val key = streamingKey ?: nextKey("a").also { newKey ->
            streamingKey = newKey
            _state.update {
                it.copy(items = it.items + ChatItem.Assistant(newKey, "", streaming = true))
            }
        }
        _state.update { st ->
            st.copy(
                items = st.items.map { item ->
                    if (item is ChatItem.Assistant && item.key == key) {
                        item.copy(text = item.text + chunk)
                    } else item
                }
            )
        }
    }

    private fun appendToThinking(chunk: String) {
        if (chunk.isEmpty()) return
        // Düşünme fazı da aynı StreamMeter'ı besler: kaynak farksız, tek hız
        // sayacı hem thinking.delta hem message.delta ile akar; faz geçişinde
        // reset yok, meter akışı kendinden devam ettirir. Faz etiketi bu
        // çağrıların hangi fazda olduğunu bildiklerinden ViewModel'dedir.
        streamMeter.delta(chunk, System.nanoTime(), StreamMeter.PHASE_THINKING)
            ?.let { _speed.value = it }
        val key = thinkingKey ?: nextKey("th").also { newKey ->
            thinkingKey = newKey
            _state.update {
                it.copy(items = it.items + ChatItem.Thinking(newKey, "", live = true))
            }
        }
        _state.update { st ->
            st.copy(
                items = st.items.map { item ->
                    if (item is ChatItem.Thinking && item.key == key) {
                        // Mevcut canlı öğeye ekleniyor — live=true kalır.
                        item.copy(text = item.text + chunk, live = true)
                    } else item
                }
            )
        }
    }

    /**
     * Canlı düşünce bloğunu kapatır: [live] = false + thinkingKey sıfırlanır.
     *
     * Çağrı noktaları: ilk `message.delta` (yanıt başladı), `tool.start`
     * (araç çalıştı) ve `message.complete`/hata (akış bitti). Kapatılan blok
     * katlanır; sonraki `thinking.delta` yeni bir canlı öğe açar.
     */
    private fun sealLiveThinking() {
        val key = thinkingKey ?: return
        thinkingKey = null
        _state.update { st ->
            st.copy(
                items = st.items.map { item ->
                    if (item is ChatItem.Thinking && item.key == key) {
                        item.copy(live = false)
                    } else item
                }
            )
        }
    }

    private fun updateLastTool(name: String?, newState: ToolState) {
        _state.update { st ->
            val idx = st.items.indexOfLast {
                it is ChatItem.Tool && it.state == ToolState.Running &&
                    (name == null || it.name == name)
            }
            if (idx < 0) return@update st
            val updated = st.items.toMutableList()
            updated[idx] = (updated[idx] as ChatItem.Tool).copy(state = newState)
            st.copy(items = updated)
        }
    }

    override fun onCleared() {
        voice.release()
        teardown()
        super.onCleared()
    }
}
