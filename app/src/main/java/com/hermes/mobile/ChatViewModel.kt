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
import com.hermes.mobile.data.ModelProvider
import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.data.VoiceController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.hermes.mobile.ui.tr

/** Sohbet akışındaki tek bir görsel öğe. */
sealed interface ChatItem {
    val key: String

    data class User(override val key: String, val text: String) : ChatItem

    data class Assistant(
        override val key: String,
        val text: String,
        val streaming: Boolean = false,
    ) : ChatItem

    data class Thinking(override val key: String, val text: String) : ChatItem

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
    val notice: String? = null,
)

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

    private var profile: ServerProfile? = null
    private var profileId: String? = null
    private var eventJob: Job? = null
    private var stateJob: Job? = null
    private var seq = 0
    private var streamingKey: String? = null
    private var thinkingKey: String? = null

    val voice = VoiceController(app)

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
        viewModelScope.launch {
            _state.collect { st ->
                if (st.agentBusy) AwaitReplyService.start(getApplication())
                else AwaitReplyService.stop(getApplication())
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
                connection = ConnectionState.Error("Önce Sunucular sekmesinden token girin")
            )
            return
        }
        if (profile.id == profileId && client != null) return

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
            gw.events.collect { event -> handleEvent(event.type, event.text, event.toolName) }
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
                val sid = _state.value.sessionId ?: gw.createSession(activeProfile).also { id ->
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
                val sid = _state.value.sessionId ?: gw.createSession(activeProfile).also { id ->
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

    /** Bir model başarısız olduğunda çağrılır; ayarlara "bozuk" diye yazılır. */
    var onModelBroken: ((String) -> Unit)? = null

    /** Doğrulanmış model seçimini kalıcı yapmak için (ayarlar deposuna yazar). */
    var onModelChosen: ((provider: String, model: String) -> Unit)? = null

    /** Uygulama açılışında ayarlardan yüklenen son seçim — "sağlayıcı|model". */
    var preferredModel: String = ""

    /** Yeni oturumların açılacağı Hermes profili; boşsa varsayılan. */
    var activeProfile: String = ""

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
                val sid = _state.value.sessionId ?: gw.createSession(activeProfile).also { id ->
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
                val sid = _state.value.sessionId ?: gw.createSession(activeProfile).also { id ->
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
                val sid = _state.value.sessionId ?: gw.createSession(activeProfile).also { id ->
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

        streamingKey = null
        thinkingKey = null
        _state.update {
            ChatState(
                connection = it.connection,
                currentModel = it.currentModel,
                sessionId = liveId,
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
                    )
                }
                return@launch
            }

            // Uzun oturumlar (361 mesaj görüldü) tek seferde çizilince hem
            // yavaş hem okunmaz; son kısmı yeterli.
            val trimmed = if (all.size > HISTORY_LIMIT) all.takeLast(HISTORY_LIMIT) else all
            val restored = trimmed.mapNotNull { m ->
                when {
                    m.isUser && !m.content.isNullOrBlank() ->
                        ChatItem.User(nextKey("u"), m.content)
                    m.isAssistant && !m.content.isNullOrBlank() ->
                        ChatItem.Assistant(nextKey("a"), m.content)
                    m.isTool ->
                        ChatItem.Tool(nextKey("t"), m.toolName ?: "araç", ToolState.Done, m.content)
                    else -> null
                }
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

            _state.update { st -> st.copy(items = header + restored + footer) }
        }
    }

    private fun handleEvent(type: String, text: String?, toolName: String?) {
        when (type) {
            "message.start" -> {
                streamingKey = nextKey("a")
                _state.update {
                    it.copy(
                        items = it.items + ChatItem.Assistant(streamingKey!!, "", streaming = true),
                        agentBusy = true,
                    )
                }
            }

            "message.delta" -> appendToStream(text.orEmpty())

            "message.complete" -> {
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
        val key = thinkingKey ?: nextKey("th").also { newKey ->
            thinkingKey = newKey
            _state.update { it.copy(items = it.items + ChatItem.Thinking(newKey, "")) }
        }
        _state.update { st ->
            st.copy(
                items = st.items.map { item ->
                    if (item is ChatItem.Thinking && item.key == key) {
                        item.copy(text = item.text + chunk)
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
