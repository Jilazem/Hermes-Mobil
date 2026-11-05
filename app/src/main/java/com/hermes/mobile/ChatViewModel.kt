package com.hermes.mobile

import com.hermes.mobile.data.emaEnabled
import com.hermes.mobile.data.modelSwitchAccepted
import com.hermes.mobile.data.modelSwitchReply
import com.hermes.mobile.data.modelSwitchSavedGlobally
import com.hermes.mobile.data.ModelSwitchReply
import com.hermes.mobile.data.reasoningSwitchAccepted

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.mobile.data.ConnectionState
import com.hermes.mobile.data.ChatIsolation
import com.hermes.mobile.data.NoticeKind
import com.hermes.mobile.data.classifyNoticeKind
import com.hermes.mobile.data.foreignNoticeId
import com.hermes.mobile.data.isForeignSession
import com.hermes.mobile.data.isCzipReadRef
import com.hermes.mobile.data.CzipAutoLogic
import com.hermes.mobile.data.CzipAutoState
import com.hermes.mobile.data.AwaitReplyService
import com.hermes.mobile.data.AssistantModeLogic
import com.hermes.mobile.data.ApprovalCenter
import com.hermes.mobile.data.ApprovalEntry
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.GatewayLink
import com.hermes.mobile.data.GatewayWsClient
import com.hermes.mobile.data.JarvisIdentity
import com.hermes.mobile.data.JarvisLoopController
import com.hermes.mobile.data.JarvisLoopLogic
import com.hermes.mobile.data.LiveModelLogic
import com.hermes.mobile.data.LocalModelClient
import com.hermes.mobile.data.LocalModelException
import com.hermes.mobile.data.LocalModelLogic
import com.hermes.mobile.data.LocalTtsDownloader
import com.hermes.mobile.data.LocalTtsEngine
import com.hermes.mobile.data.LocalTtsLogic
import com.hermes.mobile.data.AndroidLocalSynth
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.hermes.mobile.ui.createSessionProfileArg
import com.hermes.mobile.ui.ROUTER_CHIP
import com.hermes.mobile.ui.tr

/** Sohbet akışındaki tek bir görsel öğe. */
sealed interface ChatItem {
    val key: String

    data class User(override val key: String, val text: String, val ts: Double? = null) : ChatItem

    data class Assistant(
        override val key: String,
        val text: String,
        val streaming: Boolean = false,
        val ts: Double? = null,
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
        /** Gateway `prompt_id` — `approval.settled` gelince kart da kapanır. */
        val promptId: String? = null,
    ) : ChatItem
}

enum class ToolState { Running, Done, Failed }

/** Düşünce bloğu canlı görünümünde gösterilecek son dolu satır sayısı. */
const val LIVE_THINKING_TAIL_LINES = 4

/**
 * K18B-G3 denetim koşulu — onay kartını listeye TEK SEFERDE ekler.
 *
 * `ApprovalCenter.submitFromEvent` aynı `promptId` BEKLEMEDEyken MEVCUT kaydı
 * (aynı id) döndürür; kart koşulsuz eklenirse ChatScreen'in keyed listesi
 * (`itemsIndexed key = key`) duplicate key ile SERT çöker. Aynı anahtarlı
 * kart zaten varsa liste dokunulmadan döner — kartın mevcut answered/text
 * hâli korunur, yeni teslim kartı şişirmez.
 */
internal fun mergeApprovalCard(
    items: List<ChatItem>,
    entry: ApprovalEntry,
    isSudo: Boolean,
): List<ChatItem> =
    if (items.any { it.key == entry.id }) items
    else items + ChatItem.Approval(
        key = entry.id,
        text = entry.request,
        isSudo = isSudo,
        promptId = entry.promptId,
    )

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
        val line = rawLine.trim().replace("**", "").replace("`", "")
        if (line.startsWith("Reasoning effort:", ignoreCase = true)) {
            effort = line.substringAfter(':').trim().takeIf { it.isNotEmpty() }
        } else if (line.startsWith("Reasoning display:", ignoreCase = true)) {
            display = when (line.substringAfter(':').trim().lowercase()) {
                "on" -> true
                "off" -> false
                else -> null
            }
        } else if (line.startsWith("Effort:", ignoreCase = true) || line.startsWith("Güç:", ignoreCase = true)) {
            effort = line.substringAfter(':').trim().substringBefore(' ').takeIf { it.isNotEmpty() }
        } else if (line.startsWith("Display:", ignoreCase = true) || line.startsWith("Görüntüleme:", ignoreCase = true)) {
            display = when (line.substringAfter(':').trim().substringBefore(' ').lowercase()) {
                "on", "açık" -> true
                "off", "kapalı" -> false
                else -> null
            }
        } else {
            Regex("(?:Reasoning effort set to|Akıl yürütme gücü)\\s+(none|minimal|low|medium|high|xhigh|max|ultra)(?:\\s|$)",
                RegexOption.IGNORE_CASE).find(line)?.let { effort = it.groupValues[1].lowercase() }
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

/**
 * Yerel modele (yerel düğüm) giden istekte taşınan azami ÖNCEKİ tur sayısı (tur-27).
 * Son N user/assistant balonu sohbet akışından kesilir; bu turun kendisi hariç.
 */
private const val LOCAL_HISTORY_LIMIT = 20

/** Gönderilmeyi bekleyen ek. */
data class PendingAttachment(
    val label: String,
    val kind: AttachmentKind,
    /** Sunucuya yüklendikten sonra dolar (eski HTTP yolu; artık boş). */
    val remotePath: String? = null,
    val uploading: Boolean = true,
    val error: String? = null,
    /**
     * V5-1B — içerik URI'sinden okunan ham baytların base64'ü. Ek, gönderim
     * anında gateway sözleşmesiyle (`image.attach_bytes` / `file.attach`)
     * iliştirilir; dosya yolu yerelleştirme sorunu böylece kalmaz.
     */
    val contentBase64: String? = null,
    /** İçerik türü (`image/png`, `application/pdf`…). */
    val mime: String = "application/octet-stream",
) {
    /** Gönderime hazır mı: bayt var, hata yok. */
    val ready: Boolean get() = contentBase64 != null && error == null
}

enum class AttachmentKind { Image, File }

data class ChatState(
    val items: List<ChatItem> = emptyList(),
    val connection: ConnectionState = ConnectionState.Idle,
    val sessionId: String? = null,
    val storedSessionId: String? = null,
    val sending: Boolean = false,
    val agentBusy: Boolean = false,
    val statusLine: String? = null,
    val attachments: List<PendingAttachment> = emptyList(),
    val voiceMode: VoiceController.Mode = VoiceController.Mode.Off,
    val voicePartial: String = "",
    val handsFree: Boolean = false,
    val currentModel: String? = null,
    val currentProvider: String? = null,
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
    /**
     * Telefon asistanı modu (tur-13).
     *
     * ASSIST hareketiyle (ya da canlı ses sheet'inden "Yerel (Kahya)" seçimiyle)
     * açılır: sohbet ekranı öne gelir, bas-konuş öne çıkar ve gönderilen sesli
     * mesajın yanıtı kendiliğinden seslendirilir. Normal sohbet davranışı
     * (metin önce girdiye düşer, ses kullanıcı dokununca başlar) DEĞİŞMEZ.
     */
    val assistantMode: Boolean = false,
    val steerQueued: Boolean = false,
    val subagents: List<com.hermes.mobile.data.SubagentActivity> = emptyList(),
)

/**
 * V5-1B — `data:` URL'ini (mime, base64) çiftine ayrıştırır; saf fonksiyon
 * (JVM testi `V51BAttachmentTest`). Geçersizse null.
 */
fun splitDataUrl(dataUrl: String): Pair<String, String>? {
    if (!dataUrl.startsWith("data:")) return null
    val comma = dataUrl.indexOf(',')
    if (comma <= 5) return null
    val meta = dataUrl.substring(5, comma)
    val mime = meta.substringBefore(';').ifBlank { "application/octet-stream" }
    val b64 = dataUrl.substring(comma + 1)
    return if (b64.isBlank()) null else mime to b64
}

/** V5-1B — `file.attach` için veri URL'i (saf; test edilir). */
fun attachmentDataUrl(mime: String, base64: String): String =
    "data:${mime.ifBlank { "application/octet-stream" }};base64,$base64"

/**
 * K24 — steer durumundan çip kararı: yalnız `queued` kuyruğa alınmış sayılır
 * (saf fonksiyon, ForkSteerK24Test).
 */
fun steerQueuedAfter(status: String): Boolean = status == "queued"

/**
 * K24 — çatallama bildirimi (saf fonksiyon): yeni oturumun adıyla tek satır.
 */
fun forkNotice(title: String): String = tr("Çatallandı: $title", "Forked: $title")

/**
 * K24 — çatallama başarısızlık bildirimi (saf fonksiyon): sessiz başarısızlık yok.
 */
fun forkFailedNotice(reason: String): String =
    tr("Çatallanamadı ($reason)", "Could not fork ($reason)")

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

    // Tur-16: sol ray (tur-8) KALDIRILDI — oturum listesi artık sohbetin sol
    // çekmecesinde (SessionDrawer); ray durum akışları ve helper'ları ölüydü.

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
     * Staging kopyasını ek olarak sohbete alır (V5-1B).
     *
     * Eski yol dosyayı hemen HTTP ile yükleyip göreli `uploads/…` yolunu
     * saklıyordu; gönderimde bu yol sunucuda çözülmüyordu. Artık baytlar
     * yerelde base64'e alınır, gönderim anında `file.attach` ile gateway'e
     * iliştirilir; kopya hemen silinir (cache birikmez).
     */
    fun attachShareFile(plan: ShareUploadPlan.Upload) {
        val file = java.io.File(plan.stagedPath)
        if (!file.isFile) {
            DiagLog.w("ShareUpload", "staging kopyası yok: ${plan.stagedPath}")
            _shareWarning.value = tr("Dosya kopyası okunamadı: ", "File copy unreadable: ") + plan.name
            return
        }
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                require(bytes.isNotEmpty()) { "boş dosya" }
                bytes
            }.onSuccess { bytes ->
                DiagLog.i("ShareUpload", "eklendi: ${plan.name} (${bytes.size} B)")
                attachFile(bytes, plan.name, mime = guessMime(plan.name))
            }.onFailure { e ->
                DiagLog.e("ShareUpload", "okuma başarısız: ${plan.name}", e)
                _shareWarning.value = tr("Dosya okunamadı: ", "Could not read file: ") + plan.name
            }
            runCatching { file.delete() }
        }
    }

    /** Dosya adından kaba MIME; sunucu içeriğe göre yine doğrular. */
    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
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
    private var sessionLoadJob: Job? = null
    /** M1c: "yeni sohbet" gerçek gateway oturumunu kurarken bu iş üzerinde koşar. */
    private var newSessionJob: Job? = null
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
     * Tur-21 yerel ses motoru + indirme yöneticisi (Piper/sherpa-onnx).
     * Motor TEMBEL: yalnız yerel sentez istenirse yüklenir (Dispatchers.IO).
     */
    private val localTtsEngine = LocalTtsEngine(app)

    /**
     * Tur-21 yerel LLM istemcisi — Ayarlar'daki adrese göre KURULUR (null =
     * adres yok). Sessiz geçiş yok: hata kullanıcıya gösterilir, Gemini'ye
     * düşmez (model kilidi).
     */
    @Volatile
    var localModelClient: LocalModelClient? = null

    /**
     * Tur-21: sesli asistanda seçili beyin (`AppSettings.liveProvider`
     * çözümlemesi). VARSAYILAN gemini — eski davranış bozulmaz.
     */
    @Volatile
    var liveProvider: LiveModelLogic.Provider = LiveModelLogic.Provider.GEMINI

    /** V3 Google Artemis ayarları (MainActivity settings'ten yazar). */
    @Volatile var artemisUrl: String = ""
    @Volatile var artemisToken: String = ""
    @Volatile var artemisProfile: String = "flash"
    @Volatile var artemisDevice: String = ""
    private var artemisTaskId: String? = null

    /** Son yerel sağlık okuması (Ayarlar durum noktası). */
    private val _liveLocalHealth = MutableStateFlow(LiveModelLogic.Health.Unknown)
    val liveLocalHealth: StateFlow<LiveModelLogic.Health> = _liveLocalHealth.asStateFlow()

    /** Yerel TTS indirme durum akışı (Ayarlar kartı). */
    private val _localTts = MutableStateFlow(LocalTtsLogic.State())
    val localTts: StateFlow<LocalTtsLogic.State> = _localTts.asStateFlow()

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
        localSynth = AndroidLocalSynth(localTtsEngine),
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
            voiceMsg.engine = if (value.emaUrl.isNotBlank()) VoiceSpeakLogic.Engine.EMA else value.engine
            jarvisLoop.setPreferredEngine(if (value.emaUrl.isNotBlank()) "ema" else value.engine.id)
        }

    /**
     * Tur-13: asistan akışında yanıtı kendiliğinden oku (ayar, varsayılan açık).
     *
     * Ayrı tutuluyor çünkü [VoiceMessageController.autoSend]'ten farklı bir
     * karar: okuma yalnız asistan modunda etkili
     * ([AssistantModeLogic.shouldAutoRead]).
     */
    @Volatile
    var assistantAutoRead: Boolean = true

    // ── JARVIS-2 (tur24): sürekli sesli sohbet döngüsü ────────────────

    /**
     * `/health` motor haritası — 10 sn önbellek (görev 3). Döngü her yanıtta
     * motor durumunu sormasın; 10 sn içindeki tekrarlar önbellekten döner.
     * Hata = boş harita (fail-closed: hiçbir motor "açık" sayılmaz).
     */
    private var enginesCacheAtMs = 0L
    private var enginesCacheMap: Map<String, String> = emptyMap()

    private suspend fun loopEngines(): Map<String, String> {
        val t = System.currentTimeMillis()
        if (enginesCacheMap.isNotEmpty() && t - enginesCacheAtMs < JarvisLoopLogic.HEALTH_CACHE_MS) {
            return enginesCacheMap
        }
        val map = runCatching {
            voiceTransport()?.health()?.engines ?: emptyMap()
        }.getOrDefault(emptyMap())
        enginesCacheAtMs = t
        enginesCacheMap = map
        return map
    }

    /**
     * Döngü yürütücüsü. Mikrofon/çalma portları ChatViewModel'in TEK
     * örneklerini paylaşır — iki akış aynı anda çalamaz/yazamaz; bunu
     * karar katmanı engeller (döngü açıkken bas-konuş girdisi yutulur,
     * Speak fazında VAD kesmez — [com.hermes.mobile.data.JarvisLoopLogic]).
     */
    val jarvisLoop = JarvisLoopController(
        transport = ::voiceTransport,
        recorder = voiceRecorder,
        player = voicePlayer,
        cacheDir = { app.cacheDir },
        scope = viewModelScope,
        healthEngines = ::loopEngines,
        diag = { msg -> DiagLog.d("jarvis-loop", msg) },
    )

    /**
     * Tur24: tam ekran Jarvis açık/kapalı. Döngüyle TEK dokunuşta birlikte
     * açılır/kapanır (toggleJarvisLoop); geri hareketi closeJarvisMode ile
     * ikisini birden kapatır.
     */
    private val _jarvisMode = MutableStateFlow(false)
    val jarvisMode: StateFlow<Boolean> = _jarvisMode.asStateFlow()

    /** Tam ekran altyazısı — senkronize kayan çift rol (max 40, sonra budanır). */
    private val _captions = MutableStateFlow<List<JarvisLoopLogic.Caption>>(emptyList())
    val captions: StateFlow<List<JarvisLoopLogic.Caption>> = _captions.asStateFlow()

    fun addCaption(c: JarvisLoopLogic.Caption) {
        _captions.update { (it + c).takeLast(40) }
    }

    /** Döngüyü açar/kapatır; açarken tam ekranı da kaldırır (tek dokunuş). */
    fun toggleJarvisLoop() {
        if (jarvisLoop.state.value.phase == JarvisLoopLogic.Phase.Off) {
            // Döngü kendi motoruyla okur — eşzamanlı çalma + taze altyazı.
            voiceMsg.stopSpeaking()
            _captions.value = emptyList()
            _jarvisMode.value = !voicePrefs.emaEnabled
            jarvisLoop.start()
            Notifier.jarvisListening(getApplication(), true)
            DiagLog.i("jarvis-loop", "dongu acildi")
        } else {
            closeJarvisMode()
        }
    }

    /** Tam mod + döngü birlikte kapanır (geri hareketi, Durdur düğmesi). */
    fun closeJarvisMode() {
        voiceRouteJob?.cancel()
        jarvisLoop.stop("kullanici")
        _jarvisMode.value = false
        Notifier.jarvisListening(getApplication(), false)
        DiagLog.i("jarvis-loop", "dongu kapandi (kullanici)")
    }

    // ── (eski) Ses tercihleri bağlantısı ─────────────────────────────

    /** Çalışan ses ucu adresi hatırlandı — MainActivity ayarlara yazar. */
    var onVoiceBase: (String) -> Unit = {}

    /** Sesle yazılan metin; ChatScreen taslağa aktarır ve tüketir. */
    private val _voicePrefill = MutableStateFlow<String?>(null)
    val voicePrefill: StateFlow<String?> = _voicePrefill.asStateFlow()

    private fun voiceTransport(): VoiceTransport? {
        val p = profile ?: return com.hermes.mobile.data.EmaConfig.from(getApplication())?.let {
            com.hermes.mobile.data.EmaVoiceTransport(null, it.client())
        }
        val candidates = VoiceApiEndpoints.candidates(p, voicePrefs.url, voicePrefs.lastOk)
        val key = "${p.id}|${p.token}|${candidates.joinToString(",")}"
        val existing = voiceClient?.takeIf { voiceClientKey == key }
        val client = existing ?: VoiceApiClient(candidates, p.token, p.id)
            .also { voiceClient = it; voiceClientKey = key }
        val stt = HttpVoiceTransport(client)
        return if (voicePrefs.emaEnabled) com.hermes.mobile.data.EmaVoiceTransport(stt,
            com.hermes.mobile.data.EmaConfig.from(getApplication())?.client() ?: throw java.io.IOException("EMA bağlantısını tamamla")) else stt
    }

    init {
        voiceMsg.onTranscript = { text ->
            // Tur-13: asistan akışında metin doğrudan gönderilir (bas-konuş'un
            // amacı soruyu sormak). Normal sohbette karar kullanıcı ayarında.
            if (AssistantModeLogic.autoSendTranscript(_state.value.assistantMode, voiceMsg.autoSend)) {
                sendVoice(text)
            } else {
                _voicePrefill.value = text
            }
        }
        // Hata sessiz kalmasın: sohbet akışına bildirim düşer (DiagLog'a da
        // istemci içinden yazılıyor).
        voiceMsg.onNotice = { msg ->
            _state.update {
                it.copy(items = it.items + ChatItem.Notice(nextKey("v"), msg, isError = true))
            }
        }
        voiceMsg.onWorkingBase = { base -> onVoiceBase(base) }

        // ── JARVIS-2 (tur24): döngü callback'leri ─────────────────────
        jarvisLoop.onSend = { text -> sendVoice(text) }
        jarvisLoop.onNotice = { msg ->
            _state.update {
                it.copy(items = it.items + ChatItem.Notice(nextKey("jl"), msg, isError = true))
            }
        }
        jarvisLoop.onCaption = { role, text ->
            addCaption(JarvisLoopLogic.Caption(role, text))
        }
        jarvisLoop.onLoopClosed = { _ ->
            // Döngü kendi kendini kapattı (komut/hata/kullanici): tam ekran da
            // inerken altyazılar sonraki açılışa kadar korunur (bağlam hatırlansın).
            _jarvisMode.value = false
            Notifier.jarvisListening(getApplication(), false)
        }
    }

    /** Bas-konuş: parmak indi. */
    fun voiceHoldStart() = voiceMsg.holdStart()

    /** Bas-konuş: parmak kalktı (0,8 sn'den kısa basış atılır). */
    fun voiceHoldRelease() = voiceMsg.holdRelease()

    fun voiceCancel() = voiceMsg.cancelRecording()

    // ── Telefon asistanı (tur-13) ────────────────────────────────────

    /**
     * Asistan modunu açar — ASSIST hareketi ya da canlı ses sheet'indeki
     * "Yerel (Kahya)" seçimi buradan geçer.
     *
     * Kayıt KENDİLİĞİNDEN başlamaz: mod yalnız bas-konuş'u öne çıkarır ve
     * yanıt okumasını açar; mikrofonu kullanıcı basar. Mikrofon izni çağıran
     * taraf (Activity) ister — izin diyaloğu gerçekten gerekiyorsa.
     */
    fun enterAssistantMode() {
        _state.update { it.copy(assistantMode = true) }
        DiagLog.i("asistan", "mod acildi (yerel hat)")
    }

    /** Asistan modundan çık — normal sohbet davranışına dön. */
    fun exitAssistantMode() {
        voiceMsg.stopSpeaking()
        _state.update { it.copy(assistantMode = false) }
        DiagLog.i("asistan", "mod kapatildi")
    }

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

    // ── Tur-21: yerel TTS + yerel asistan (yerel düğüm) ─────────────────────

    /**
     * Yerel TTS disk durumunu tarar ve indirme durum akışını günceller
     * (Ayarlar kartı açılırken çağrılır; hash taraması IO'da, ~1 sn).
     */
    fun refreshLocalTtsState() {
        viewModelScope.launch {
            val disk = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = LocalTtsLogic.modelDir(getApplication<Application>().filesDir)
                    LocalTtsLogic.filesPresent(dir) &&
                        LocalTtsLogic.firstShaMismatch(dir) { sha ->
                            com.hermes.mobile.data.LocalTtsDownloader.Companion.sha256Of(sha)
                        } == null
                }.getOrDefault(false)
            }
            _localTts.value = LocalTtsLogic.startState(disk, _localTts.value.message.takeIf {
                _localTts.value.phase == LocalTtsLogic.Phase.Failed
            }.orEmpty())
        }
    }

    /** Yerel TTS modelini indirir (eksik parçalar); ilerleme StateFlow'a akar. */
    fun downloadLocalTts() {
        if (_localTts.value.phase == LocalTtsLogic.Phase.Downloading) {
            diagLocal("yerel tts indirme zaten suruyor - ikinci istek yok")
            return
        }
        _localTts.value = LocalTtsLogic.progress(0L, "hazirlaniyor")
        viewModelScope.launch {
            val app = getApplication<Application>()
            val downloader = LocalTtsDownloader(app) { done, cur ->
                _localTts.value = LocalTtsLogic.progress(done, cur)
            }
            runCatching { downloader.ensureModel() }
                .onSuccess {
                    diagLocal("yerel tts modeli tamam (63.4 MB, sha hepsi dogrulandi)")
                    _localTts.value = LocalTtsLogic.downloaded()
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    diagLocal("yerel tts indirme hatasi: ${e.message}")
                    _localTts.value = LocalTtsLogic.failed(
                        e.message ?: tr("Yerel ses indirilemedi", "Local voice download failed"),
                    )
                }
        }
    }

    /** Yerel TTS dosyalarını Ayarlar'dan siler (spec: silme kapısı). */
    fun deleteLocalTts() {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                runCatching {
                    LocalTtsLogic.deleteModel(
                        LocalTtsLogic.modelDir(getApplication<Application>().filesDir),
                    )
                }.getOrDefault(0)
            }
            diagLocal("yerel tts silindi: $n dosya")
            _localTts.value = LocalTtsLogic.startState(false)
        }
    }

    private fun diagLocal(msg: String) = DiagLog.i("localtts", msg)

    /**
     * Yerel LLM sağlık kontrolü (Ayarlar durum noktası) — hata fırlatmaz,
     * [LiveModelLogic.Health] döner ve akışa yazılır.
     */
    suspend fun probeLocalHealth(): LiveModelLogic.Health {
        val c = localModelClient
        if (c == null || !c.isConfigured()) {
            _liveLocalHealth.value = LiveModelLogic.Health.Unknown
            return LiveModelLogic.Health.Unknown
        }
        return try {
            val ok = c.healthModel() != null
            _liveLocalHealth.value = if (ok) LiveModelLogic.Health.Ok else LiveModelLogic.Health.Down
            _liveLocalHealth.value
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagLog.w("localllm", "saglik kontrolu: ${e.message}")
            _liveLocalHealth.value = LiveModelLogic.Health.Down
            LiveModelLogic.Health.Down
        }
    }

    /**
     * Yerel asistan turu (yerel düğüm) — mesaj göster, yanıtla, seslendir.
     *
     * Hata halinde: Notice (kırmızı) + Yerel'de kal; otomatik Gemini geçişi
     * YOK. Başarıda: asistan balonu + (asistan modu + otomatik okuma açıksa)
     * seslendirme mevcut yerel TTS zincirinden geçer.
     */
    private fun sendLocalAssistant(label: String) {
        val client = localModelClient
        // Tur-27 oturum sürekliliği: yerel model TURLER ARASI geçmişi görür.
        // Geçmiş YENİ kullanıcı turu EKLENMEDEN önce okunur (aksi halde bu tur
        // iki kez gider); sohbette çizilen User/Assistant balonlarından kurulur
        // (Notice/Tool/Thinking hariç), son [LOCAL_HISTORY_LIMIT] tur yeterli.
        val history = _state.value.items
            .mapNotNull { item ->
                when (item) {
                    is ChatItem.User -> "user" to item.text
                    is ChatItem.Assistant ->
                        if (item.text.isNotBlank()) "assistant" to item.text else null
                    else -> null
                }
            }
            .takeLast(LOCAL_HISTORY_LIMIT)
        _state.update {
            it.copy(
                items = it.items + ChatItem.User(nextKey("u"), label),
                sending = true,
                agentBusy = true,
                attachments = emptyList(),
            )
        }
        if (client == null || !client.isConfigured()) {
            _state.update {
                it.copy(
                    sending = false,
                    agentBusy = false,
                    items = it.items + ChatItem.Notice(
                        nextKey("n"),
                        LiveModelLogic.resolveActive(
                            LiveModelLogic.Provider.YEREL,
                            _liveLocalHealth.value,
                            false,
                            ::tr,
                        ).error.orEmpty(),
                        isError = true,
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            // Tur-23 r1 (KAPSAM-1): persona yönergesi gerçekten modele gider —
            // system mesajı JarvisIdentity.SYSTEM_PROMPT (ad, NAME'den türeme).
            runCatching {
                client.chat(
                    label,
                    settings_localModelName,
                    system = JarvisIdentity.SYSTEM_PROMPT,
                    history = history,
                )
            }
                .onSuccess { answer ->
                    DiagLog.i("localllm", "yanıt ${answer.length} kr · ${System.currentTimeMillis() - started} ms")
                    val key = nextKey("a")
                    _state.update {
                        it.copy(
                            sending = false,
                            agentBusy = false,
                            items = it.items + ChatItem.Assistant(key, answer, streaming = false),
                        )
                    }
                    Notifier.agentReply(getApplication(), answer, sessionId = _state.value.sessionId, profileId = profile?.id, title = _state.value.topic.orEmpty())
                    // Tur-27 (ses hattı): yerel yanıt da SESLENDİRİLİR —
                    // gateway yolundaki message.complete okuma kararının aynısı.
                    // Önceden bu yol hiç okumuyordu: asistan modunda/hands-free'de
                    // Jarvis döngüsünde cevap sessizce ekranda kalıyordu.
                    if (jarvisLoop.state.value.phase == JarvisLoopLogic.Phase.WaitReply) {
                        addCaption(JarvisLoopLogic.Caption("agent", answer))
                        jarvisLoop.onAgentReply(answer)
                    } else if (_state.value.handsFree && answer.isNotBlank()) {
                        voice.speak(answer)
                    } else if (
                        AssistantModeLogic.shouldAutoRead(
                            assistantMode = _state.value.assistantMode,
                            settingOn = assistantAutoRead,
                            reply = answer,
                        )
                    ) {
                        voiceMsg.speak(key, answer)
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    DiagLog.e("localllm", "tur basarisiz: ${e.message}")
                    // K16 — patron önceliği: YEREL uç YOĞUNSA (429/502/503) patron
                    // beklemesin — tur uzak uçtan (gateway) sürdürülür ve kullanıcıya
                    // SEBEBİYLE bildirilir. Ölü uç/ağ hatasında YEREL'de kalınır
                    // (tur-21 kilidi yalnız erişilemezlik için korunur).
                    val busyCode = (e as? LocalModelException)?.busyCode
                    if (busyCode != null) {
                        val noticeText = LocalModelLogic.busyFallbackNotice(busyCode, ::tr)
                        // Bildirim ikiye karşı bir: hemen akışa yazilir VE kuyruga
                        // konur — yeni oturum acilinca gecmis yuklemesi items'i
                        // sıfırladığından bildirim silinir; kuyruktaki kopya
                        // gecmis yuklemesi bitince tekrar eklenir (K16).
                        pendingLocalBusyNotice = noticeText
                        _state.update {
                            val items = it.items.toMutableList()
                            // sendLocalAssistant'ın eklediği kullanıcı balonunu geri al;
                            // send() kendi balonunu ekler (çift balon olmasın).
                            val lastUser = items.indexOfLast { x -> x is ChatItem.User }
                            if (lastUser >= 0) items.removeAt(lastUser)
                            it.copy(
                                sending = false,
                                agentBusy = false,
                                items = items + ChatItem.Notice(nextKey("n"), noticeText),
                            )
                        }
                        _liveLocalHealth.value = LiveModelLogic.Health.Down
                        send(label, fromLocalBusy = true)
                        return@onFailure
                    }
                    _state.update {
                        it.copy(
                            sending = false,
                            agentBusy = false,
                            items = it.items + ChatItem.Notice(
                                nextKey("n"),
                                e.message ?: tr(
                                    "Yerel node yanıt vermedi",
                                    "The local node did not respond",
                                ),
                                isError = true,
                            ),
                        )
                    }
                    _liveLocalHealth.value = LiveModelLogic.Health.Down
                }
        }
    }

    /** Ayarlardan gelen model adı (boşsa sözleşme varsayılanı). */
    @Volatile
    var settings_localModelName: String = LocalModelLogic.DEFAULT_MODEL

    /**
     * Açılışta geri dönülecek oturum. `MainActivity` kayıtlı değeri buraya
     * koyar; bağlantı kurulunca bir kez denenir ve temizlenir.
     */
    var restoreSessionId: String? = null

    /** Oturum değişince çağrılır — MainActivity kalıcı kayda yazar. */
    var onSessionChanged: ((String) -> Unit)? = null

    /**
     * K16: yerel asistan turu yoğunluktan uzak uca düşerken kullanıcıya
     * gösterilecek sebep bildirimi. Yeni oturumun geçmiş yüklemesi items'i
     * sıfırladığından bildirim burada kuyruklanır ve geçmiş yükleme bitince
     * akışa eklenir (kullanıcı "neden uzak uçta?" sorusunun cevabı kaybolmaz).
     */
    @Volatile
    private var pendingLocalBusyNotice: String? = null

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
    /**
     * V3 Jarvis "Sohbette aç": bağlantı hazırsa hemen bağlan, değilse (soğuk
     * açılış) bağlantı ilk açıldığında bağlanılsın.
     */
    private var pendingOpen: Triple<String, String, String?>? = null
    fun openSessionWhenReady(sid: String, title: String, targetProfileId: String? = null) {
        if (client != null && _state.value.connection is ConnectionState.Open &&
            (targetProfileId == null || profile?.id == targetProfileId)) continueSession(sid, sid, title)
        else pendingOpen = Triple(sid, title, targetProfileId)
    }

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
        // İş #1021: Ayarlar ve sohbet aynı durumu okusun diye tek kaynağa bağla.
        GatewayLink.bind(gw)

        // Soket yenilendiğinde oturumu gateway'e yeniden bağla, kaçırılan yanıtı
        // tamamla, bekleyen mesajları gönder. Bu üçü olmadan "oturum koptu".
        gw.onReconnected = {
            viewModelScope.launch { reattach(gw) }
        }

        viewModelScope.launch {
            runCatching { HermesClient(profile).modelInfo() }
                .onSuccess { info ->
                    if (client === gw) _state.update {
                        if (it.sessionId != null || it.storedSessionId != null) it
                        else if (preferredModel.contains("|")) it.copy(
                            currentModel = preferredModel.substringAfter("|"),
                            currentProvider = preferredModel.substringBefore("|"),
                        ) else it.copy(currentModel = info.model, currentProvider = info.provider)
                    }
                }
        }

        stateJob = viewModelScope.launch {
            gw.connection.collect { conn ->
                _state.update { it.copy(connection = conn) }
                // Bağlantı ilk kez açıldığında kayıtlı oturuma dön. Tek deneme:
                // oturum gateway'de artık yoksa sessizce yeni oturumla devam.
                if (conn is ConnectionState.Open) {
                    val open = pendingOpen
                    if (open != null && (open.third == null || open.third == profile.id)) {
                        pendingOpen = null
                        restoreSessionId = null
                        continueSession(open.first, open.first, open.second)
                        return@collect
                    }
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
                handleEvent(event.type, if (event.type.startsWith("tool.")) event.toolDetail else event.text, event.toolName, event.sessionId, event.payload)
            }
        }
        gw.connect()
    }

    private fun teardown() {
        modelsLoadJob?.cancel()
        modelSelectionJob?.cancel()
        reasoningChoiceJob?.cancel()
        reasoningGeneration++
        modelsLoadGeneration++
        _modelProviders.value = emptyList()
        _modelsLoading.value = false
        _modelsError.value = null
        _modelSelecting.value = false
        voiceRouteJob?.cancel()
        sessionLoadJob?.cancel()
        eventJob?.cancel()
        stateJob?.cancel()
        client?.close()
        client?.let { GatewayLink.release(it) }
        client = null
        _gateway.value = null
        profileId = null
        profile = null
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
        val output = gw.slashExec(sid, "/model ${parts[1]} --provider ${parts[0]}")
        check(modelSwitchAccepted(output)) { output.trim().ifBlank { "Kayıtlı model seçilemedi" } }
        _state.update { it.copy(currentModel = parts[1], currentProvider = parts[0]) }
    }

    private suspend fun ensureConversation(gw: GatewayWsClient): String {
        val current = _state.value
        val existing = current.sessionId
        val sid = if (existing != null) {
            gw.attachSession(existing, current.storedSessionId ?: gw.storedSessionId(existing), profile?.let { sessionAgent(it.id, current.storedSessionId ?: gw.storedSessionId(existing)) })
        } else if (current.storedSessionId != null) {
            gw.resumeSession(current.storedSessionId, profile?.let { sessionAgent(it.id, current.storedSessionId) })
        } else {
            createSessionWithProfile(gw).also { applyPreferredModel(gw, it) }
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        check(_state.value.sessionId == current.sessionId && _state.value.storedSessionId == current.storedSessionId) {
            "Gönderirken oturum değişti; mesajı seçtiğiniz konuşmada tekrar gönderin"
        }
        _state.update { it.copy(sessionId = sid, storedSessionId = gw.storedSessionId(sid)) }
        onSessionChanged?.invoke(gw.storedSessionId(sid))
        return sid
    }

    private val specialistStore by lazy { com.hermes.mobile.data.VoiceSpecialistSessions(getApplication()) }
    private val agentBindings by lazy { com.hermes.mobile.data.SessionAgentBindings(getApplication()) }
    private fun sessionAgent(server: String, stored: String): String? =
        agentBindings.profile(server, stored) ?: specialistStore.profileFor(server, stored)
    private var subagentRefreshJob: kotlinx.coroutines.Job? = null
    private var subagentRevision = 0L
    private var nextSubagentAttempt = 0L
    fun refreshSubagents() {
        if (subagentRefreshJob?.isActive == true) return
        if (android.os.SystemClock.elapsedRealtime() < nextSubagentAttempt) return
        val gw = client ?: return
        val sid = _state.value.sessionId ?: return
        if (_state.value.connection !is ConnectionState.Open) return
        val revision = subagentRevision
        subagentRefreshJob = viewModelScope.launch {
            try {
                val snapshot = gw.subagentList(sid) ?: return@launch
                if (client === gw && _state.value.sessionId == sid && subagentRevision == revision) {
                    _state.update { it.copy(subagents = com.hermes.mobile.data.mergeSubagentSnapshot(it.subagents, snapshot)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { nextSubagentAttempt = android.os.SystemClock.elapsedRealtime() + 60_000 }
        }
    }

    /** V5-2: oturum başına model/düşünme hafızası — JSON defter ([SessionChoiceStore]). */
    private val sessionChoices by lazy { com.hermes.mobile.data.SessionChoiceStore(getApplication()) }
    private var voiceRouteJob: Job? = null

    /** Continuous microphone replies use a real isolated Hermes profile, with the current chat as context. */
    fun sendVoice(text: String) {
        if (liveProvider == LiveModelLogic.Provider.YEREL &&
            _state.value.sessionId == null && _state.value.storedSessionId == null) { send(text); return }
        if (PhoneIntent.parse(text) != null) { send(text); return }
        if (voiceRouteJob?.isActive == true) return
        val server = profile
        val gw = client
        if (server == null || gw == null || _state.value.connection !is ConnectionState.Open) {
            val error = tr("Sesli asistan için sunucu bağlantısı gerekli", "Connect to the server for the voice assistant")
            _state.update { it.copy(notice = error) }
            jarvisLoop.onAgentReply(error)
            return
        }
        val original = _state.value
        if (original.agentBusy || original.historyLoading) {
            jarvisLoop.onAgentReply(tr("Açık sohbetin yanıtını bekleyelim", "Wait for the current conversation to finish"))
            return
        }
        voiceRouteJob = viewModelScope.launch {
            try {
                val source = original.storedSessionId ?: original.sessionId?.let(gw::storedSessionId)
                val selected = specialistStore.select(gw, server, source)
                check(client === gw && profile?.id == server.id &&
                    _state.value.sessionId == original.sessionId && _state.value.storedSessionId == original.storedSessionId) {
                    "Konuşma değişti; sorunu seçtiğin sohbette tekrar söyle"
                }
                if (selected != null) {
                    if (_state.value.storedSessionId != selected.stored) {
                        continueSession(selected.runtime, selected.stored, "Sesli Asistan")
                        sessionLoadJob?.join()
                    }
                    check(client === gw && _state.value.storedSessionId == selected.stored) { "Sesli konuşma değişti" }
                    _sessionProfile.value = com.hermes.mobile.data.VoiceSpecialistLogic.PROFILE
                    _state.update { it.copy(currentModel = selected.model ?: it.currentModel) }
                }
                send(text)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                val error = tr("Sesli asistana bağlanılamadı; sohbetten tekrar dene", "Voice assistant unavailable; try from chat")
                _state.update { it.copy(notice = error) }
                jarvisLoop.onAgentReply(error)
            }
        }
    }

    private suspend fun submitConversationPrompt(gw: GatewayWsClient, sid: String, prompt: String) {
        val server = profile ?: error("Sunucu profili yok")
        val stored = gw.storedSessionId(sid)
        val seed = specialistStore.pendingContext(server.id, stored)
        gw.submitPrompt(sid, seed + prompt)
        if (seed.isNotBlank()) specialistStore.submitted(server.id, stored)
    }

    fun send(text: String) {
        send(text, fromLocalBusy = false)
    }

    /**
     * @param fromLocalBusy K16: yerel asistan turu YOĞUNLUK (429/502/503)
     *   yüzünden uzak uca düşerken true — bu çağrı tekrar yerel asistana
     *   yönlendirilmesin (sonsuz döngü), doğrudan gateway'e gitsin.
     */
    private fun send(text: String, fromLocalBusy: Boolean) {
        if (_state.value.historyLoading) {
            _state.update { it.copy(notice = tr("Oturum yükleniyor; birkaç saniye bekleyin", "Session is loading; please wait")) }
            return
        }
        val body = text.trim()
        val ready = _state.value.attachments.filter { it.ready }
        if (body.isEmpty() && ready.isEmpty()) return
        // V5-3: araç sohbeti açıkken telefon tarafının mesajı da AA akışına düşsün.
        com.hermes.mobile.data.Notifier.carFlow(
            getApplication(), body,
            _state.value.storedSessionId ?: _state.value.sessionId,
            profileId, isUser = true,
        )

        // Telefon eylemi mi? Ajan sunucuda koşuyor, telefona ulaşamaz; bu işleri
        // cihazın kendisi yapmalı. Eşleşme yoksa mesaj olduğu gibi ajana gider.
        //
        // Ayardan bağımsız çalışıyor: "WhatsApp aç" diye **yazmak** zaten açık
        // rızadır. Ayar, modelin kendiliğinden telefonu kullanabildiği canlı ses
        // yolunu denetliyor — orada kararı kullanıcı değil model veriyor.
        // V3: "/telefon <görev>" → Google Artemis (sunucuda, telefonu ADB ile
        // kullanır). Ajan sohbetine gitmez; sonuç sohbete yazılır.
        if (ready.isEmpty()) {
            com.hermes.mobile.data.ArtemisLogic.parseCommand(body)?.let { goal ->
                runArtemis(goal, body)
                return
            }
        }
        if (ready.isEmpty()) {
            PhoneIntent.parse(body)?.let { intent ->
                runPhoneAction(body, intent)
                return
            }
        }

        val gw = client
        val online = gw != null && _state.value.connection is ConnectionState.Open

        // K12b: çıplak czip okuma referansı (paket/görev) sohbete İÇERİK
        // dökmez — K6 sözleşmesi: okuma masaüstünde, görünmez tek seferlik
        // oturumda yapılır; sohbete yalnız referans + kısa özet notu düşer.
        // Çevrimdışıysa normal kuyruk akışına düşer (mesaj kaybolmasın).
        if (ready.isEmpty() && online && isCzipReadRef(body)) {
            sendCzipRead(gw!!, body)
            return
        }

        val label = buildString {
            ready.forEach { appendLine("[${it.label}]") }
            append(body)
        }.trim()

        // ── Tur-21: yerel asistan (yerel düğüm) — model kilidi ─────────────────
        // Kullanıcı Ayarlar'da "Yerel düğüm"ü seçtiği sürece her tur yerel
        // uçtan geçer. Hata olursa AÇIK gösterilir ve YEREL'de kalınır —
        // sessiz Gemini'ye geçiş YOK (kullanıcı onaylı geçiş, D-04/d-06
        // disiplini: hata yutulmaz, kullanıcı formatlı mesaj üretilir).
        if (!fromLocalBusy &&
            liveProvider == LiveModelLogic.Provider.YEREL &&
            _state.value.sessionId == null && _state.value.storedSessionId == null) {
            sendLocalAssistant(label)
            return
        }

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
                val sid = ensureConversation(gw)
                // V5-1B: ekler gateway sözleşmesiyle iliştirilir —
                // `image.attach_bytes` görseli sunucunun kendi görsel köküne
                // yazar, `file.attach` gerçek `@file:` referansı döndürür.
                // Eski `/image uploads/…` + "Ek dosya: uploads/…" metni göreli
                // yol yüzünden sunucuda bulunamıyordu.
                ready.filter { it.kind == AttachmentKind.Image }.forEach { att ->
                    val b64 = att.contentBase64 ?: error("Görsel içeriği boş: ${att.label}")
                    gw.attachImageBytes(sid, b64, att.label)
                        ?: error(tr("Görsel eklenemedi: ", "Could not attach image: ") + att.label)
                }
                val fileNote = ready
                    .filter { it.kind == AttachmentKind.File }
                    .map { att ->
                        val b64 = att.contentBase64 ?: error("Dosya içeriği boş: ${att.label}")
                        val ref = gw.attachFileDataUrl(sid, "data:${att.mime};base64,$b64", att.label)
                            ?: error(tr("Dosya eklenemedi: ", "Could not attach file: ") + att.label)
                        "Ek dosya: ${att.label} → ${ref.refText}"
                    }.joinToString("\n")
                val prompt = listOf(fileNote, body).filter { it.isNotBlank() }.joinToString("\n\n")
                if (prompt.isNotBlank()) submitConversationPrompt(gw, sid, prompt)
            }.onFailure { e ->
                if (jarvisLoop.state.value.phase == JarvisLoopLogic.Phase.WaitReply) {
                    jarvisLoop.onAgentReply(tr("Mesaj gönderilemedi; bağlantıyı denetle", "Message failed; check the connection"))
                }
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
     * K12b: çıplak czip okuma referansını çalıştırır — paket/görev İÇERİĞİ
     * bu sohbete mesaj olarak DÜŞMEZ. Okuma, [improvePrompt] ile aynı
     * kalıpta görünmez tek seferlik oturumda yapılır (iki yönlü yalıtım:
     * `invisibleSessions` + aktif-oturum filtresi); sohbete yalnız
     * referans + kısa özet notu yazılır. Okuma sürerken kullanıcı başka
     * sohbete geçtiyse not O sohbete düşmez.
     */
    private fun sendCzipRead(gw: GatewayWsClient, ref: String) {
        val sidAtStart = _state.value.sessionId
        _state.update {
            it.copy(
                items = it.items + ChatItem.User(nextKey("u"), ref),
                agentBusy = true,
            )
        }
        viewModelScope.launch {
            val sonuc = runCatching { runCzipRead(gw, ref) }
            _state.update { st ->
                if (st.sessionId != sidAtStart) st // ara sohbete içerik sızmasın
                else st.copy(
                    agentBusy = false,
                    items = st.items + sonuc.fold(
                        onSuccess = { cevap ->
                            val ozet = com.hermes.mobile.data.czipReadOzet(cevap)
                            ChatItem.Notice(
                                nextKey("n"),
                                if (ozet.isBlank()) tr(
                                    "czip okundu — içerik sohbete dökülmedi (masaüstünde okundu)",
                                    "czip read done — content not dumped into the chat (read on desktop)",
                                ) else tr(
                                    "czip okundu — içerik sohbete dökülmedi (masaüstünde okundu):\n",
                                    "czip read done — content not dumped into the chat (read on desktop):\n",
                                ) + ozet,
                            )
                        },
                        onFailure = { e ->
                            ChatItem.Notice(
                                nextKey("n"),
                                tr("czip okunamadı: ", "czip read failed: ") + (e.message ?: ""),
                                isError = true,
                            )
                        },
                    ),
                )
            }
        }
    }

    /** Görünmez oturumda czip okur; yanıtı döndürür (içerik sohbete girmez). */
    private suspend fun runCzipRead(gw: GatewayWsClient, ref: String, timeoutMs: Long = 180_000): String {
        val sid = gw.createSession(activeProfile)
        invisibleSessions += sid
        try {
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
                gw.submitPrompt(sid, ref)
                return withTimeoutOrNull(timeoutMs) { tamam.await() }
                    ?: run {
                        runCatching { gw.interrupt(sid) }
                        throw IllegalStateException(tr("czip okuma zaman aşımı", "czip read timed out"))
                    }
            } finally {
                job.cancel()
            }
        } finally {
            invisibleSessions -= sid
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
        if (com.hermes.mobile.data.ArtemisLogic.parseCommand(command) != null) {
            send(command)
            return
        }
        val gw = client ?: return
        // UI-2: slash komutu uzun sürebilir; bittiğinde gerçek meşgul durumunu
        // ezmesin (koşulsuz false yerine işaretleme öncesi değeri koru).
        val wasBusy = _state.value.agentBusy
        _state.update {
            it.copy(items = it.items + ChatItem.User(nextKey("u"), command), agentBusy = true)
        }
        viewModelScope.launch {
            runCatching {
                val sid = ensureConversation(gw)
                gw.slashExec(sid, command)
            }
                .onSuccess { output ->
                    val base = command.removePrefix("/").substringBefore(' ').lowercase()
                    if (base == "new" || base == "clear") {
                        streamingKey = null
                        thinkingKey = null
                        _state.update {
                            // P6: sıfırlamada mod/etkileşim bayraklarını koru —
                            // tam ChatState sıfırlaması onları kaybediyordu.
                            ChatState(
                                connection = it.connection,
                                currentModel = it.currentModel,
                                assistantMode = it.assistantMode,
                                handsFree = it.handsFree,
                                voiceMode = it.voiceMode,
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
                                agentBusy = wasBusy,
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
                            agentBusy = wasBusy,
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

    /**
     * Bekleyen onayı yanıtlar — Telegram'daki inline düğmelerin karşılığı.
     *
     * K18B-G3: sohbet kartı ve MERKEZİ onay kutusu aynı yolu kullanır.
     * `sessionId` verilirse (kutudan) O oturuma yanıt gider; verilmezse açık
     * oturum. Kayıt zaten kapandıysa ikinci komut GİTMEZ (çift dokunuş/çift kanal).
     */
    fun answerApproval(key: String, approve: Boolean, reason: String = "", sessionId: String? = null) {
        val gw = client ?: return
        val sid = sessionId ?: _state.value.sessionId ?: return
        if (!ApprovalCenter.decide(key, approve, reason)) return
        val command = ApprovalCenter.slashCommand(approve, reason)

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
                // K18B-G3 (güçlü öneri): gönderim BAŞARISIZ — kararı geri al,
                // kartı yeniden cevaplanabilir yap. Aksi hâlde kayıt kilitli
                // kalır, ikinci dokunuş sessizce yutulur ve karar ASLA
                // iletilmez. (Kayıt Pending'e döner → `decide` tekrar kabul
                // eder, kart answered'sız kalır.)
                ApprovalCenter.reopen(key)
                _state.update {
                    it.copy(
                        items = it.items.map { item ->
                            if (item is ChatItem.Approval && item.key == key)
                                item.copy(answered = null)
                            else item
                        } + ChatItem.Notice(
                            nextKey("n"),
                            e.message ?: "Yanıt iletilemedi",
                            isError = true,
                        ),
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
                if (ChatIsolation.foreignEvent(e.sessionId, sid)) return@collect
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
        if (stopArtemis()) {
            _state.update { it.copy(statusLine = tr("📱 Artemis durduruluyor…", "📱 Stopping Artemis…")) }
            return
        }
        val gw = client
        val sid = _state.value.sessionId
        if (gw == null || sid == null) {
            _state.update { it.copy(notice = tr("Durdurulamadı: sunucuya bağlı bir sohbet yok", "Could not stop: no connected conversation")) }
            return
        }
        voice.stopSpeaking()
        viewModelScope.launch {
            try {
                gw.interrupt(sid)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (client === gw && _state.value.sessionId == sid) _state.update {
                    it.copy(notice = tr("Durdurma isteği iletilemedi: ", "Could not send stop request: ") + (e.message ?: "?"))
                }
                return@launch
            }
            if (client !== gw || _state.value.sessionId != sid) return@launch
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
                    steerQueued = steerQueuedAfter(finalStatus),
                )
            }
        }
    }

    /**
     * K24 — "Çatalla": süren oturumu o ana kadarki bağlamıyla YENİ bir saklı
     * oturuma dallandırır (gateway `session.branch`). Orijinal oturum
     * etkilenmez — sunucuda canlılığını ve süren turunu korur; mobil yeni
     * çatala geçer ve tek satır bildirim yazar. Çatal, listede normal bir
     * oturum olarak görünür (kanonik DB satırı server tarafında açılır).
     */
    fun forkSession() {
        val gw = client ?: return
        val st = _state.value
        val live = st.sessionId
        val stored = st.storedSessionId ?: live?.let { gw.storedSessionId(it) }
        if (live == null && stored == null) {
            _state.update {
                it.copy(items = it.items + ChatItem.Notice(nextKey("n"), forkFailedNotice("oturum yok"), isError = true))
            }
            return
        }

        viewModelScope.launch {
            // Aynı istek kaybolursa yeniden deneme AYNI çocuğu döndürür.
            val idem = java.util.UUID.randomUUID().toString()
            var failure: String? = null

            // 1) Canlı oturum: o ana kadarki bağlam (session.branch).
            var result = live?.let { sid ->
                runCatching { gw.forkSession(sid, idempotencyKey = idem) }
                    .onFailure { failure = it.message }
                    .getOrNull()
            }

            // 2) V5-1B: canlı değilse SAKLI oturum (Mac'te açılmış, süreç içi
            //    kaydı düşmüş olabilir) → session.branch_stored bütün geçmişi
            //    sunucuda kopyalar. Eski kod yalnız session.branch deniyordu ve
            //    pasif Mac oturumunda sessizce başarısız oluyordu.
            if (result == null && stored != null) {
                result = runCatching { gw.forkStoredSession(stored, idempotencyKey = idem) }
                    .onFailure { failure = it.message }
                    .getOrNull()
            }

            if (result == null) {
                _state.update {
                    it.copy(
                        items = it.items + ChatItem.Notice(
                            nextKey("n"), forkFailedNotice(failure ?: "bilinmeyen hata"), isError = true,
                        ),
                    )
                }
                return@launch
            }

            val title = result.title.ifBlank { st.topic.ifBlank { "Çatal" } }
            // Mobil yeni çatala geçer; banner continueSession'da Notice olur ve
            // durum sıfırlamasından korunur.
            continueSession(result.sessionId, result.storedSessionId, title, forkNotice(title))
        }
    }

    /**
     * V5-1B — ek seçici hatasını görünür kılar. Eskiden `runCatching` hatayı
     * yutuyordu: kullanıcı "foto/dosya bulunamadı" görüyor, nedenini görmüyordu.
     */
    fun reportAttachmentError(reason: String) {
        _state.update {
            it.copy(
                items = it.items + ChatItem.Notice(
                    nextKey("n"),
                    tr("Ek okunamadı: $reason", "Could not read attachment: $reason"),
                    isError = true,
                ),
            )
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

    /**
     * V5-1B — görseli yerelde base64 olarak tutar; gönderim anında
     * `image.attach_bytes` ile gateway'e iliştirilir. Eski HTTP yükleme yolu
     * göreli `uploads/…` döndürüyordu ve `/image uploads/x.png` sunucuda
     * `image not found` (4016) veriyordu — canlı kanıtla doğrulandı.
     */
    fun attachImage(dataUrl: String, filename: String) {
        val parsed = splitDataUrl(dataUrl)
            ?: run { reportAttachmentError(filename); return }
        val (mime, b64) = parsed
        if (b64.isBlank()) { reportAttachmentError(filename); return }
        val item = PendingAttachment(
            label = filename,
            kind = AttachmentKind.Image,
            uploading = false,
            contentBase64 = b64,
            mime = mime,
        )
        _state.update { it.copy(attachments = it.attachments + item) }
    }

    fun attachFile(bytes: ByteArray, filename: String, mime: String = "application/octet-stream") {
        if (bytes.isEmpty()) { reportAttachmentError(filename); return }
        val item = PendingAttachment(
            label = filename,
            kind = AttachmentKind.File,
            uploading = false,
            contentBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
            mime = mime,
        )
        _state.update { it.copy(attachments = it.attachments + item) }
    }

    private fun markAttachment(label: String, path: String?, error: String?) {
        _state.update { st ->
            st.copy(
                attachments = st.attachments.map {
                    if (it.label == label) it.copy(remotePath = path, uploading = false, error = error)
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

    private val _modelsError = MutableStateFlow<String?>(null)
    val modelsError: StateFlow<String?> = _modelsError.asStateFlow()
    private val _modelSelecting = MutableStateFlow(false)
    val modelSelecting: StateFlow<Boolean> = _modelSelecting.asStateFlow()
    private var modelsLoadJob: Job? = null
    private var modelSelectionJob: Job? = null
    private var modelsLoadGeneration = 0

    /** Refresh when opening the picker; retain cached rows when the refresh fails. */
    fun loadModels(force: Boolean = true) {
        val p = profile
        if (p == null) {
            _modelsError.value = tr("Model listesi için sunucuya bağlanın", "Connect to the server to load models")
            return
        }
        if (modelsLoadJob?.isActive == true || (!force && _modelProviders.value.isNotEmpty())) return
        val generation = ++modelsLoadGeneration
        _modelsLoading.value = true
        _modelsError.value = null
        modelsLoadJob = viewModelScope.launch {
            try {
                val options = HermesClient(p).modelOptions()
                if (this@ChatViewModel.profile == p && generation == modelsLoadGeneration) {
                    _modelProviders.value = options.providers
                    _modelsError.value = options.notice
                    if (options.providers.none { it.models.isNotEmpty() })
                        _modelsError.value = tr("Sunucu kullanılabilir model listesi göndermedi", "The server returned no models")
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == modelsLoadGeneration) _modelsError.value =
                    tr("Modeller alınamadı: ", "Could not load models: ") + (e.message ?: "?")
            } finally {
                if (generation == modelsLoadGeneration) _modelsLoading.value = false
            }
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
            profileArg?.let { agent -> profile?.let { agentBindings.remember(it.id, gw.storedSessionId(id), agent) } }
            _sessionProfile.value = profileArg
            onSessionChanged?.invoke(id)
            ensureCzipAuto(gw, id)
        }
    }

    /**
     * V5-4 CZIP AUTO tercihi — ayarlardan gelir ([AppSettings.czipAuto]),
     * varsayılan AÇIK (AGENTS.md: czip-auto her oturumda açık başlar).
     * Kapatılırsa uygulama sunucu czip ayarına dokunmaz.
     */
    var czipAutoEnabled: Boolean = true

    /** V5-4: ekrandaki czip-auto kanıt satırı bu oturum yaşamında en az bir kez yazıldı mı. */
    private var czipAutoBildirildi = false

    /**
     * V5-4 CZIP AUTO: yeni oturum açıldığında arka plan paketlemesinin
     * (czip auto) açık olduğunu doğrular; kapalıysa/tanınmıysa `oto aç` ile
     * açar ve sonucu ekranda tek satır bildirimle kanıtlar.
     *
     * Sunucu tarafı kalıcıdır (czip/ayar.json `oto=true` + czip-auto
     * `on_session_start` hook'u); bu güvence yalnız ayar sürüklenmişse
     * devreye girer. Gönderimi engellemez: ilk mesaj yola çıkarken arka
     * planda koşar ([GatewayWsClient.slashExec] bloklamaz, kendi coroutine'i).
     * Açık durumda bildirim yalnız ilk kez yazılır; sonraki oturumlarda
     * garanti sessizdir.
     */
    private fun ensureCzipAuto(gw: GatewayWsClient, sid: String) {
        if (!czipAutoEnabled) return
        viewModelScope.launch {
            val status = runCatching { gw.slashExec(sid, CzipAutoLogic.STATUS_COMMAND) }.getOrDefault("")
            var on = CzipAutoLogic.state(status) == CzipAutoState.ON
            for (command in CzipAutoLogic.ensureCommands(status)) {
                val output = runCatching { gw.slashExec(sid, command) }.getOrDefault("")
                on = CzipAutoLogic.state(output) == CzipAutoState.ON
            }
            if (on && czipAutoBildirildi) return@launch
            czipAutoBildirildi = true
            _state.update {
                it.copy(
                    items = it.items + ChatItem.Notice(
                        nextKey("n"),
                        tr(CzipAutoLogic.noticeTr(on), CzipAutoLogic.noticeEn(on)),
                    ),
                )
            }
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
        set(value) {
            field = value
            if (_state.value.sessionId == null && value.contains("|"))
                _state.update { it.copy(currentModel = value.substringAfter("|"), currentProvider = value.substringBefore("|")) }
        }

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


    fun selectModel(
        provider: String,
        model: String,
        persist: Boolean = false,
        think: String? = null,
        onResult: (Boolean) -> Unit = {},
    ) {
        if (_modelSelecting.value) { onResult(false); return }
        val gw = client
        if (gw == null) {
            _state.update { it.copy(notice = tr("Model değişmedi: önce sunucuya bağlanın", "Model unchanged: connect to the server first")) }
            onResult(false)
            return
        }
        _modelSelecting.value = true
        reasoningGeneration++
        _modelsError.value = null
        modelSelectionJob = viewModelScope.launch {
            var accepted = false
            try {
                require(provider.isNotBlank() && model.isNotBlank() &&
                    !provider.startsWith("-") && !model.startsWith("-") &&
                    provider.none { it.isWhitespace() || it == '\'' || it == '"' } &&
                    model.none { it.isWhitespace() || it == '\'' || it == '"' }) {
                    tr("Geçersiz sağlayıcı veya model kimliği", "Invalid provider or model id")
                }
                newSessionJob?.join()
                reasoningChoiceJob?.join()
                check(client === gw && gw.awaitOpen()) { tr("Gateway bağlı değil", "Gateway is disconnected") }
                val before = _state.value
                val sid = if (before.sessionId == null && before.storedSessionId == null) {
                    // Validate the pick on the real session, even before its first prompt.
                    createSessionWithProfile(gw).also { id ->
                        check(client === gw && _state.value.sessionId == before.sessionId &&
                            _state.value.storedSessionId == before.storedSessionId) { "Konuşma değişti; modeli yeniden seçin" }
                        _state.update { it.copy(sessionId = id, storedSessionId = gw.storedSessionId(id)) }
                        onSessionChanged?.invoke(gw.storedSessionId(id))
                    }
                } else ensureConversation(gw)
                val validThink = think?.takeIf { com.hermes.mobile.data.thinkSupported(model, it) }
                val command = "/model $model --provider $provider" +
                    (if (persist) " --global" else "") +
                    (validThink?.let { " --reasoning $it" } ?: "")
                val output = gw.slashExec(sid, command)
                val reply = modelSwitchReply(output)
                check(reply != ModelSwitchReply.Rejected) { output.trim().take(1200).ifBlank { "Model değiştirilemedi" } }
                check(client === gw && _state.value.sessionId == sid) { "Konuşma değişti; seçimi açık sohbette kontrol edin" }
                preferredModel = "$provider|$model"
                val message = if (reply == ModelSwitchReply.Deferred)
                    tr("$model seçildi; bir sonraki mesajdan itibaren uygulanacak", "$model selected; applies from your next message")
                else tr("Model seçildi: $model", "Model selected: $model")
                _state.update { it.copy(
                    currentModel = model,
                    currentProvider = provider,
                    notice = message + if (persist && !modelSwitchSavedGlobally(output))
                        tr(". Sunucu varsayılanı kaydedilemedi; seçim yalnız bu oturumda", ". Server default was not saved; this session only") else "",
                ) }
                sessionChoices.put(_state.value.storedSessionId ?: sid,
                    com.hermes.mobile.data.SessionChoice(provider, model, validThink))
                if (validThink != null) {
                    val status = parseReasoningOutput(output)
                    if (status.effort != null) _reasoningStatus.value = status
                }
                onModelChosen?.invoke(provider, model)
                accepted = true
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (client === gw) {
                    val error = tr("Model seçilemedi: ", "Could not select model: ") + (e.message ?: "?")
                    _modelsError.value = error
                    _state.update { it.copy(notice = error) }
                }
            } finally {
                _modelSelecting.value = false
                onResult(accepted)
            }
        }
    }

    /**
     * V5-2: oturum bazlı model/düşünme hafızası — oturum açılırken kayıtlı
     * seçim geri gelir. Kayıt YOKSA hiçbir şey olmaz; sessiz varsayılan yok.
     * Düşünme kademesi `/reasoning`'den, model [selectModel] yolundan geçer.
     */
    private fun restoreSessionChoice(storedId: String) {
        val saved = sessionChoices.get(storedId) ?: return
        val think = saved.think
        val model = saved.model
        val provider = saved.provider
        if (!model.isNullOrBlank() && !provider.isNullOrBlank() &&
            (model != _state.value.currentModel || provider != _state.value.currentProvider ||
                (think != null && think != reasoningStatus.value.effort))) {
            selectModel(provider, model, persist = false, think = think)
        } else if (model.isNullOrBlank() && think != null && think != reasoningStatus.value.effort) setReasoningEffort(think)
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
    private var reasoningChoiceJob: Job? = null
    private var reasoningGeneration = 0

    fun refreshReasoning() {
        val gw = client ?: return
        val generation = reasoningGeneration
        viewModelScope.launch {
            runCatching {
                newSessionJob?.join()
                check(client === gw && gw.awaitOpen()) { "Gateway bağlı değil" }
                val sid = ensureConversation(gw)
                val out = gw.slashExec(sid, "/reasoning")
                if (client === gw && _state.value.sessionId == sid && generation == reasoningGeneration)
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
    fun setReasoningEffort(level: String, onResult: (Boolean) -> Unit = {}) {
        val gw = client
        if (gw == null) {
            _state.update { it.copy(notice = tr("Düşünme seviyesi değişmedi: sunucuya bağlanın", "Reasoning unchanged: connect to the server")) }
            onResult(false)
            return
        }
        if (_modelSelecting.value || reasoningChoiceJob?.isActive == true) {
            _state.update { it.copy(notice = tr("Önceki model/düşünme seçiminin sonucunu bekleyin", "Wait for the previous model/reasoning selection")) }
            onResult(false)
            return
        }
        val generation = ++reasoningGeneration
        reasoningChoiceJob = viewModelScope.launch {
            var accepted = false
            try {
                require(level in listOf("none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra")) {
                    tr("Geçersiz düşünme seviyesi", "Invalid reasoning level")
                }
                newSessionJob?.join()
                check(client === gw && gw.awaitOpen()) { "Gateway bağlı değil" }
                val sid = ensureConversation(gw)
                val output = gw.slashExec(sid, "/reasoning $level")
                check(reasoningSwitchAccepted(output, level)) { output.trim().take(1000).ifBlank { "Düşünme seviyesi doğrulanamadı" } }
                check(client === gw && _state.value.sessionId == sid && generation == reasoningGeneration) {
                    "Konuşma değişti; düşünme seviyesini açık sohbette kontrol edin"
                }
                _reasoningStatus.value = ReasoningStatus(level, parseReasoningOutput(output).displayOn ?: _reasoningStatus.value.displayOn)
                val stored = _state.value.storedSessionId ?: sid
                val old = sessionChoices.get(stored) ?: com.hermes.mobile.data.SessionChoice()
                sessionChoices.put(stored, old.copy(think = level))
                _state.update { it.copy(notice = tr("Düşünme seviyesi $level; sonraki mesajda etkili", "Reasoning $level; applies from your next message")) }
                accepted = true
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (client === gw && generation == reasoningGeneration) _state.update {
                    it.copy(notice = tr("Düşünme seviyesi değiştirilemedi: ", "Could not change reasoning: ") + (e.message ?: "?"))
                }
            } finally { onResult(accepted) }
        }
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
                .recoverCatching { gw.resumeSession(_state.value.storedSessionId ?: gw.storedSessionId(sid)) }
                .onSuccess { runtimeId ->
                    if (_state.value.sessionId == sid) {
                        _state.update { it.copy(sessionId = runtimeId, storedSessionId = gw.storedSessionId(runtimeId)) }
                        onSessionChanged?.invoke(gw.storedSessionId(runtimeId))
                    }
                }
                // Bu sessizce başarısız olursa `prompt.submit` boşa gidiyor ve
                // kullanıcı sonsuza kadar "Düşünüyor" görüyor — hiçbir yerde iz
                // kalmadığı için tam olarak bu, teşhis edilemeyen şikayetti.
                .onFailure { e ->
                    DiagLog.e("chat", "could not re-attach to session", e)
                    _state.update { it.copy(notice = tr("Oturuma yeniden bağlanılamadı: ", "Could not reconnect to session: ") + e.message) }
                }
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
                val sid = ensureConversation(gw)
                submitConversationPrompt(gw, sid, body)
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
    /**
     * V3 czip taşıma: uzun oturumu (tüm geçmişi bağlama geri yüklemek yerine)
     * czip ile paketler ve YENİ oturumda harita üzerinden sürdürür.
     *
     * Akış: yeni oturum → `/czip <dbId>` (sunucudaki czip plugin'i) → çıktıdan
     * paket yolu → ilk mesaj olarak [czipHandoffPrompt]. Eski oturuma dokunulmaz.
     * Plugin yoksa/çıktıda paket yolu yoksa açık hata gösterilir, sessiz geçiş yok.
     */
    fun czipContinue(dbId: String, title: String) {
        val gw = client
        if (gw == null || _state.value.connection !is ConnectionState.Open) {
            _state.update { it.copy(notice = tr("Czip için sunucu bağlantısı gerekli", "Czip needs a server connection")) }
            return
        }
        newSession()
        _state.update {
            it.copy(
                topic = "$title · czip",
                agentBusy = true,
                items = listOf(
                    ChatItem.Notice(nextKey("n"), tr("Czip: \"$title\" paketleniyor…", "Czip: packing \"$title\"…")),
                ),
            )
        }
        viewModelScope.launch {
            runCatching {
                val sid = createSessionWithProfile(gw).also { id ->
                    _state.update { it.copy(sessionId = id) }
                    onSessionChanged?.invoke(id)
                }
                gw.slashExec(sid, "/czip $dbId")
            }.onSuccess { output ->
                val path = com.hermes.mobile.ui.czipPackPath(output)
                _state.update {
                    it.copy(
                        items = it.items + ChatItem.Assistant(nextKey("a"), output.ifBlank { "(çıktı yok)" }),
                        agentBusy = false,
                    )
                }
                if (path == null) {
                    _state.update {
                        it.copy(
                            items = it.items + ChatItem.Notice(
                                nextKey("n"),
                                tr(
                                    "Czip paket yolu bulunamadı — sunucuda czip plugin'i kurulu mu? (./install.sh, sonra Hermes'i yeniden başlat)",
                                    "Czip pack path not found — is the czip plugin installed on the server?",
                                ),
                                isError = true,
                            ),
                        )
                    }
                } else {
                    send(com.hermes.mobile.ui.czipHandoffPrompt(path, title))
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        items = it.items + ChatItem.Notice(
                            nextKey("n"),
                            tr("Czip çalıştırılamadı: ", "Czip failed: ") + (e.message ?: ""),
                            isError = true,
                        ),
                        agentBusy = false,
                    )
                }
            }
        }
    }

    /**
     * Artemis görevi: gönder → 1.5 sn aralıkla izle (en çok 30 dk) → sonucu yaz.
     * Durum satırı adım sayısını gösterir; "Durdur" [stopArtemis] ile iptal.
     */
    private fun runArtemis(goal: String, typed: String) {
        val url = artemisUrl
        if (url.isBlank()) {
            _state.update {
                it.copy(items = it.items + ChatItem.Notice(nextKey("n"),
                    tr("Artemis adresi boş — Ayarlar → Artemis", "Artemis address empty — Settings → Artemis"), isError = true))
            }
            return
        }
        val en = com.hermes.mobile.ui.serviceLang == com.hermes.mobile.ui.Lang.EN
        _state.update {
            it.copy(
                items = it.items + ChatItem.User(nextKey("u"), typed),
                agentBusy = true,
                statusLine = tr("📱 Artemis telefonu kullanıyor…", "📱 Artemis is using the phone…"),
            )
        }
        viewModelScope.launch {
            val client = com.hermes.mobile.data.ArtemisClient(url, artemisToken)
            runCatching {
                val devices = runCatching { client.devices() }.getOrDefault(emptyList())
                val serial = com.hermes.mobile.data.ArtemisLogic.pickDevice(devices, artemisDevice, phoneWifiIp())
                val id = client.submit(goal, artemisProfile, serial)
                artemisTaskId = id
                val deadline = System.currentTimeMillis() + 30 * 60_000L
                var task = client.task(id)
                while (!task.done && System.currentTimeMillis() < deadline) {
                    _state.update {
                        it.copy(statusLine = tr("📱 Artemis çalışıyor", "📱 Artemis working") +
                            (task.turns?.let { n -> tr(" · $n adım", " · step $n") } ?: ""))
                    }
                    kotlinx.coroutines.delay(1_500)
                    task = client.task(id)
                }
                task
            }.onSuccess { task ->
                _state.update {
                    it.copy(
                        items = it.items + ChatItem.Assistant(nextKey("a"),
                            if (task.done) com.hermes.mobile.data.ArtemisLogic.resultText(task, en)
                            else tr("📱 Artemis 30 dk içinde bitmedi (sunucuda sürüyor olabilir)", "📱 Artemis did not finish in 30 min")),
                        agentBusy = false,
                        statusLine = null,
                        steerQueued = false,
                    )
                }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(
                        items = it.items + ChatItem.Notice(nextKey("n"),
                            tr("Artemis'e ulaşılamadı ($url): ", "Could not reach Artemis ($url): ") + (e.message ?: ""),
                            isError = true),
                        agentBusy = false,
                        statusLine = null,
                        steerQueued = false,
                    )
                }
            }
            artemisTaskId = null
        }
    }

    /** Süren Artemis görevini iptal eder (Durdur düğmesi). */
    private fun stopArtemis(): Boolean {
        val id = artemisTaskId ?: return false
        val url = artemisUrl
        viewModelScope.launch { runCatching { com.hermes.mobile.data.ArtemisClient(url, artemisToken).stop(id) } }
        return true
    }

    /** Telefonun Wi-Fi IPv4 adresi — kablosuz ADB serisini ("ip:port") eşlemek için. */
    private fun phoneWifiIp(): String? = runCatching {
        val cm = getApplication<Application>().getSystemService(android.net.ConnectivityManager::class.java)
        cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()

    /**
     * M1c: YENİ sohbet — ekranı temizler VE gerçek gateway oturumunu
     * `?fresh=1` niyetiyle kurar (`session.create`).
     *
     * Eski davranış yalnız yerel durumu temizliyordu; gerçek oturum ilk mesajda
     * açılıyor ve yeni sohbet ile önceki oturumun soket bağı karışabiliyordu.
     * Artık yeni sohbet ANINDA kendi runtime + stored kimliğine sahip olur
     * (`?fresh=1` parametresi `/api/ws`'te yok sayılır — bağ RPC ile kurulur;
     * ayrıntı docs/G1-GATEWAY-MOBIL-API.md).
     *
     * Bağlantı yoksa ekran temiz kalır (ilk gönderim oturumu yine kurar) ama
     * kullanıcıya sessiz kalmak yasak: hata notice olarak yazılır.
     */
    fun newSession() {
        modelSelectionJob?.cancel()
        reasoningChoiceJob?.cancel()
        reasoningGeneration++
        sessionLoadJob?.cancel()
        newSessionJob?.cancel()
        _state.update { ChatState(
            connection = it.connection,
            currentModel = preferredModel.substringAfter("|", "").takeIf { m -> m.isNotBlank() },
            currentProvider = preferredModel.substringBefore("|", "").takeIf { p -> p.isNotBlank() },
        ) }
        streamingKey = null
        thinkingKey = null
        streamMeter.reset()
        _speed.value = null
        _sessionProfile.value = null
        onSessionChanged?.invoke("")
        val gw = client ?: return
        if (profile == null) return
        // Üretim sürerken soketi yeniden açmak yanıtı koparır: yeni sohbet
        // ekranı temiz kalır, gerçek oturum ilk gönderimde kurulur.
        if (_state.value.agentBusy) return
        newSessionJob = viewModelScope.launch {
            val started = System.currentTimeMillis()
            try {
                gw.connect(fresh = true)
                if (!gw.awaitOpen()) {
                    // B3 İSTENEN-4: zaman aşımında hedef adres şema+port ile görünür.
                    val hedef = profile?.endpoint?.displayWs() ?: profile?.wsBase ?: "?"
                    _state.update {
                        it.copy(
                            notice = tr(
                                "Yeni oturum açılamadı: gateway hazır değil (zaman aşımı) — hedef: $hedef",
                                "Could not open a new session: gateway not ready (timeout) — target: $hedef",
                            ),
                        )
                    }
                    return@launch
                }
                val profileArg = createSessionProfileArg(selectedProfileValue ?: ROUTER_CHIP)
                    ?: activeProfile.takeIf { it.isNotBlank() }
                val binding = gw.createSessionBinding(profile = profileArg)
                profileArg?.let { agent -> profile?.let { agentBindings.remember(it.id, binding.storedId, agent) } }
                _sessionProfile.value = profileArg
                _state.update { it.copy(sessionId = binding.runtimeId, storedSessionId = binding.storedId) }
                onSessionChanged?.invoke(binding.storedId)
                DiagLog.i(
                    "ws",
                    "yeni oturum hazır: runtime=${binding.runtimeId} stored=${binding.storedId} " +
                        "(${System.currentTimeMillis() - started} ms, fresh=1)",
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(notice = tr("Yeni oturum açılamadı: ", "Could not open a new session: ") + (e.message ?: "?"))
                }
                DiagLog.w("ws", "yeni oturum açılamadı: ${e.message}")
            }
        }
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
    fun continueSession(liveId: String, dbId: String, title: String, banner: String? = null, agentProfile: String? = null) {
        modelSelectionJob?.cancel()
        reasoningChoiceJob?.cancel()
        reasoningGeneration++
        _reasoningStatus.value = ReasoningStatus(null, null)
        val p = profile ?: return
        agentProfile?.let { agentBindings.remember(p.id, dbId.ifBlank { liveId }, it) }
        val gw = client ?: return
        // M1c: soket kapalıysa devam eden konuşma `?resume=<stored>` niyetiyle
        // açılır. Soket zaten açıksa İKİNCİ soket açılmaz — gerçek devam
        // `session.resume`/`attachSession` RPC'siyle olur (tek soket ilkesi).
        if (_state.value.connection !is ConnectionState.Open) {
            gw.connect(resumeId = dbId.ifBlank { liveId })
        }
        sessionLoadJob?.cancel()
        onSessionChanged?.invoke(dbId.ifBlank { liveId })
        // Tur-16: sol ray kaldırıldı — son açık oturum kalıcılığı artık
        // settings.lastSession + çekmece (lastSessionToRestore) üzerinden.

        streamingKey = null
        thinkingKey = null
        streamMeter.reset()
        _speed.value = null
        // B4: tek satır görünür bildirim (ör. "«X» süresi doldu, son oturum
        // açıldı") — geçmiş yüklenirken de sonrasında da görünür kalır;
        // sessiz fallback yok. Banner, tarihsel döküm listeyi yeniden
        // kurduğunda da korunur (aşağıdaki body+footer bileşimi).
        val bannerItems = if (banner.isNullOrBlank()) {
            emptyList()
        } else {
            listOf(ChatItem.Notice(nextKey("n"), banner))
        }
        // Mevcut oturuma bağlanıldı → çipler kilitlenir; profil sunucudan
        // bilinmiyor (canlı oturumlarda profili öğrenmek yok), bu yüzden
        // null ("—"). Oturum düşerse sessizce yeni oturum açılır ve profil
        // seçimi yeniden serbestleşir.
        _sessionProfile.value = agentProfile ?: sessionAgent(p.id, dbId.ifBlank { liveId })
        _state.update {
            ChatState(
                connection = it.connection,
                currentModel = null,
                currentProvider = null,
                sessionId = liveId,
                storedSessionId = dbId.ifBlank { liveId },
                // Tur-4 (P3 #8): üst şerit KONUYU gösterir — model orada durmaz.
                topic = title,
                // KALAN-1: geçmiş gelene kadar iskelet balonlar (aşağıda
                // item'lar yazılınca/boş çıkınca kapanır).
                historyLoading = true,
                items = listOf(
                    ChatItem.Notice(nextKey("n"), "\"$title\" konuşmasına bağlanıldı")
                ) + bannerItems,
            )
        }

        // V5-2: oturum bazlı model/düşünme hafızası — kayıt varsa geri gelir.
        restoreSessionChoice(dbId.ifBlank { liveId })

        sessionLoadJob = viewModelScope.launch {
            // Oturum gateway'in belleğinde hâlâ duruyor mu? Duruyorsa tam
            // süreklilik (aynı bağlam); durmuyorsa geçmişi yine gösteririz ama
            // sonraki mesaj yeni oturum açmalı — ölü kimliğe prompt göndermek
            // sessizce başarısız olurdu.
            val attached = runCatching { gw.attachSession(liveId, dbId.ifBlank { gw.storedSessionId(liveId) }, agentProfile ?: sessionAgent(p.id, dbId.ifBlank { gw.storedSessionId(liveId) })) }
            if (attached.exceptionOrNull() is kotlinx.coroutines.CancellationException) throw attached.exceptionOrNull()!!
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val runtimeId = attached.getOrNull()
            val alive = runtimeId != null
            if (runtimeId != null) {
                _state.update { it.copy(sessionId = runtimeId, storedSessionId = gw.storedSessionId(runtimeId)) }
                onSessionChanged?.invoke(gw.storedSessionId(runtimeId))
            }

            // Canlı oturumun geçmişi gateway belleğinde; REST'te görünmüyor.
            // Oturum düştüyse veritabanına düşüyoruz.
            val fromLive =
                if (runtimeId != null) runCatching { gw.sessionHistory(runtimeId) }.getOrDefault(emptyList())
                else emptyList()
            val all = fromLive.ifEmpty {
                runCatching { HermesClient(p).sessionMessages(dbId, agentProfile ?: sessionAgent(p.id, dbId)) }
                    .getOrDefault(emptyList())
            }

            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (all.isEmpty()) {
                // Ne canlı ne veritabanı — kullanıcıyı hata metniyle karşılamak
                // yerine temiz bir sohbetle başlat.
                _state.update { st ->
                    st.copy(
                        items = st.items + if (!alive) listOf(ChatItem.Notice(nextKey("n"),
                            tr("Bu oturum geri yüklenemedi: ", "Could not restore this session: ") +
                                (attached.exceptionOrNull()?.message ?: ""), isError = true)) else emptyList(),
                        historyLoading = false,
                    )
                }
                return@launch
            }

            val restored = restoreChatHistory(all, ::nextKey)
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
            val footer = ChatItem.Notice(
                nextKey("n"),
                if (alive) tr("— buradan devam —", "— continue here —")
                else tr(
                    "— oturuma bağlanılamadı; yanıt gönderirken aynı oturum tekrar denenecek —",
                    "— session unavailable; replying retries this same conversation —",
                ),
            )

            // B4: banner (tek satır bildirim) tarihsel dökümle birlikte korunur.
            _state.update { st ->
                // K16: yoğunluk düşüş bildirimi kuyruktaysa geçmiş yüklendikten
                // sonra akışa ekle — oturum açılışı items'i sıfırladığı için
                // düşüş anında yazılan kopya silinmişti.
                val queuedNotice = pendingLocalBusyNotice
                if (queuedNotice != null) pendingLocalBusyNotice = null
                val base = bannerItems + body + footer
                st.copy(
                    items = if (queuedNotice == null) base else base + ChatItem.Notice(
                        nextKey("n"),
                        queuedNotice,
                    ),
                    historyLoading = false,
                )
            }
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

    private fun handleEvent(
        type: String,
        text: String?,
        toolName: String?,
        sessionId: String? = null,
        // K18B-G3: approval.request/sudo.request payload alanları (description,
        // command, prompt_id) — GatewayEvent.payload köprülenir.
        payload: kotlinx.serialization.json.JsonObject? = null,
    ) {
        if (isFromInvisibleSession(sessionId)) return
        if (auxiliaryTitleFailure(type, text)) {
            DiagLog.w("title", "Başlık yardımcı işlemi tamamlanamadı; sohbet yanıtı korunuyor")
            _state.update { current ->
                if (current.topic.isNotBlank()) current else current.copy(topic =
                    current.items.filterIsInstance<ChatItem.User>().firstOrNull()?.text?.lineSequence()?.firstOrNull()?.take(60).orEmpty())
            }
            return
        }
        // V5-2 madde 6: başka oturumdan gelen TAMAMLANMIŞ mesajlar (cron,
        // Telegram, CLI konuşmaları) sohbet akışına KARIŞMAZ — bildirim
        // kutusuna tür sınıflandırmasıyla düşer. Akış parçaları (delta) zaten
        // yalnız aktif oturumda anlam taşır; onlar sessizce atılır.
        if (type == "message.complete" &&
            isForeignSession(sessionId, _state.value.sessionId, _state.value.storedSessionId)
        ) {
            val body = text.orEmpty().trim()
            if (body.isNotBlank()) {
                val kind = classifyNoticeKind(null, "", body)
                Notifier.agentMessage(
                    getApplication(),
                    title = when (kind) {
                        NoticeKind.CRON -> tr("Zamanlanmış görev", "Scheduled job")
                        NoticeKind.SYSTEM -> tr("Sistem bildirimi", "System notice")
                        else -> tr("Başka oturum", "Another session")
                    },
                    text = body,
                    kind = kind,
                    sessionId = sessionId,
                    profileId = profile?.id,
                    id = foreignNoticeId(sessionId, body),
                )
            }
            return
        }
        // K12b: oturum izolasyonu — gateway aynı sokete birden çok oturumun
        // olayını `session_id` etiketiyle basar (activate aboneleri yerinden
        // etmez). Etiketi yok saymak bir sohbetin yanıtını DİĞER sohbete
        // döküyordu; etiketli olay yalnız aktif sohbetin oturumundan geçer.
        if (ChatIsolation.foreignEvent(sessionId, _state.value.sessionId)) return
        if (type.startsWith("subagent.")) {
            subagentRevision++
            _state.update { it.copy(subagents = com.hermes.mobile.data.mergeSubagentActivity(it.subagents, type, payload)) }
            return
        }
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
                        steerQueued = false,
                    )
                }
                // Sesli kip açıkken yanıtı oku; TTS bitince eller-serbest döngü
                // kendiliğinden yeniden dinlemeye geçer.
                if (_state.value.handsFree && spoken.isNotBlank()) voice.speak(spoken)
                // JARVIS-2 (tur24): döngü WaitReply'de ise yanıt ONA gider —
                // okuma + altyazı + (okuma bitince) otomatik dinleme döngü
                // içinde çözülür. Asistan şeridi otomatik-okuması bu turda
                // ATLANIR (çift okuma yok); normal sohbet okuma kararı aşağıda.
                if (jarvisLoop.state.value.phase == JarvisLoopLogic.Phase.WaitReply) {
                    addCaption(JarvisLoopLogic.Caption("agent", spoken))
                    jarvisLoop.onAgentReply(spoken)
                } else if (
                    AssistantModeLogic.shouldAutoRead(
                        assistantMode = _state.value.assistantMode,
                        settingOn = assistantAutoRead,
                        reply = spoken,
                    )
                ) {
                    voiceMsg.speak(key.orEmpty(), spoken)
                }
                // Kullanıcı uygulamadan çıktıysa yanıtı bildirim olarak göster.
                if (spoken.isNotBlank()) {
                    Notifier.agentReply(
                        getApplication(),
                        spoken,
                        sessionId = _state.value.sessionId,
                        profileId = profile?.id,
                        title = _state.value.topic.orEmpty(),
                    )
                }
            }

            "thinking.delta", "reasoning.delta" -> appendToThinking(text.orEmpty())

            "status.update" -> _state.update { st ->
                val message = text?.takeIf { it.isNotBlank() }
                val duplicate = (st.items.lastOrNull() as? ChatItem.Notice)?.text == message
                st.copy(statusLine = text, items = if (message == null || duplicate) st.items
                    else st.items + ChatItem.Notice(nextKey("status"), message))
            }

            "tool.start" -> _state.update {
                // Araç çalışmaya başladı: canlı düşünce bloğu kapanır.
                sealLiveThinking()
                it.copy(
                    items = it.items + ChatItem.Tool(
                        nextKey("t"),
                        toolName ?: "araç",
                        ToolState.Running,
                        detail = text,
                    ),
                    agentBusy = true,
                )
            }

            "tool.complete" -> updateLastTool(toolName, ToolState.Done, text)
            "tool.error" -> updateLastTool(toolName, ToolState.Failed, text)

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
            // K18B-G3: aynı istek MERKEZİ onay kutusuna da düşer — kart ile
            // kutu aynı kaydı paylaşır (id ortak), çift cevap engellenir.
            "approval.request", "sudo.request" -> {
                val entry = ApprovalCenter.submitFromEvent(
                    type = type,
                    sessionId = sessionId,
                    // D-05: ham süreç içi id etiket OLMAZ — konu yoksa "Sohbet".
                    sessionTitle = _state.value.topic.ifBlank { tr("Sohbet", "Chat") },
                    text = text,
                    toolName = toolName,
                    payload = payload,
                    createdAt = System.currentTimeMillis(),
                )
                _state.update {
                    it.copy(
                        // Yeniden teslim: ApprovalCenter aynı promptId için MEVCUT
                        // entry'yi (aynı id) döndürür — kartı koşulsuz eklemek keyed
                        // listede duplicate-key çökmesidir (K18B-G3 denetim koşulu).
                        items = mergeApprovalCard(
                            it.items,
                            entry,
                            isSudo = type == "sudo.request",
                        ),
                        agentBusy = false,
                    )
                }
            }

            // K18B-G3: başka kanaldan (Telegram/TUI) yanıtlandı — merkezi
            // kutudaki kayıt ve sohbet kartı birlikte kapanır.
            "approval.settled" -> {
                val promptId = (payload?.get("prompt_id") as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content?.takeIf { it.isNotBlank() }
                if (promptId != null && ApprovalCenter.settleByPrompt(promptId)) {
                    _state.update {
                        it.copy(
                            items = it.items.map { item ->
                                if (item is ChatItem.Approval &&
                                    item.promptId == promptId && item.answered == null
                                ) {
                                    item.copy(answered = tr("Başka kanaldan yanıtlandı", "Answered elsewhere"))
                                } else item
                            }
                        )
                    }
                }
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

    private fun updateLastTool(name: String?, newState: ToolState, detail: String? = null) {
        _state.update { st ->
            val idx = st.items.indexOfLast {
                it is ChatItem.Tool && it.state == ToolState.Running &&
                    (name == null || it.name == name)
            }
            if (idx < 0) return@update st
            val updated = st.items.toMutableList()
            val old = updated[idx] as ChatItem.Tool
            updated[idx] = old.copy(state = newState, detail = listOfNotNull(old.detail, detail).distinct().joinToString("\n\n").ifBlank { null })
            st.copy(items = updated)
        }
    }

    override fun onCleared() {
        voice.release()
        teardown()
        super.onCleared()
    }
}
