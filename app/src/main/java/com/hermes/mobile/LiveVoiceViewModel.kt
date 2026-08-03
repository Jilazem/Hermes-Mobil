package com.hermes.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.hermes.mobile.data.AppSettings
import com.hermes.mobile.data.AudioRouter
import com.hermes.mobile.data.CameraFrameStreamer
import com.hermes.mobile.data.DrivingModeService
import com.hermes.mobile.data.LiveVoiceClient
import com.hermes.mobile.data.PhoneTools
import com.hermes.mobile.data.ServerProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveVoiceState(
    val state: LiveVoiceClient.State = LiveVoiceClient.State.Idle,
    val userText: String = "",
    val modelText: String = "",
    val level: Float = 0f,
    val error: String? = null,
    val relayUrl: String = "",
    val usingOwnKey: Boolean = false,
    val route: AudioRouter.Route = AudioRouter.Route.Speaker,
    val routeOptions: List<AudioRouter.Route> = emptyList(),
    val driving: Boolean = false,
) {
    val isRunning: Boolean
        get() = state != LiveVoiceClient.State.Idle && state != LiveVoiceClient.State.Error
}

/** Kamera akışının durumu — sesli oturumdan bağımsız açılıp kapanabilir. */
data class CameraState(
    val active: Boolean = false,
    val framesSent: Int = 0,
    val error: String? = null,
)

/**
 * Gemini Live sesli sohbet — the server rölesi üzerinden.
 *
 * Bu, uygulamanın diğer sesli kipinden (Android STT/TTS) farklı: burada ses
 * doğrudan modele gidiyor, metne çevrilmiyor. Sözünü kesebilme ve tonlama
 * bu yüzden mümkün.
 */
class LiveVoiceViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(LiveVoiceState())
    val state: StateFlow<LiveVoiceState> = _state.asStateFlow()

    private var client: LiveVoiceClient? = null
    private var jobs = mutableListOf<Job>()
    private var profile: ServerProfile? = null
    private val router = AudioRouter(app)
    /** ChatViewModel'deki köprüyle aynı olacak şekilde dışarıdan atanıyor. */
    var shizuku: com.hermes.mobile.data.ShizukuBridge? = null
        set(value) {
            field = value
            phoneTools = PhoneTools(getApplication(), value)
        }

    private var phoneTools = PhoneTools(app)

    /** Ayarlar ekranından beslenir; her oturum açılışında okunur. */
    var settings: AppSettings = AppSettings()

    fun bind(profile: ServerProfile?) {
        // Yalnız id karşılaştırmak yetmiyordu: kullanıcı tokeni sonradan
        // girdiğinde id aynı kaldığı için bu ViewModel eski (boş tokenli)
        // profille kalıyor ve sürüş kipi "token girin" diyordu.
        val same = profile?.id == this.profile?.id &&
            profile?.token == this.profile?.token &&
            profile?.baseUrl == this.profile?.baseUrl &&
            profile?.geminiApiKey == this.profile?.geminiApiKey
        if (same && this.profile != null) return
        stop()
        this.profile = profile
        _state.update {
            it.copy(
                relayUrl = profile?.effectiveRelayUrl.orEmpty(),
                usingOwnKey = !profile?.geminiApiKey.isNullOrBlank(),
            )
        }
    }

    fun start() {
        val p = profile ?: run {
            _state.update { it.copy(error = "Sunucu profili seçilmemiş") }
            return
        }
        val relay = p.effectiveRelayUrl
        if (relay.isBlank()) {
            // Adres artık hem LAN'da hem dışarıda türetilebiliyor; buraya
            // düşmek sunucu adresinin host'unun okunamadığı anlamına gelir.
            _state.update {
                it.copy(
                    error = com.hermes.mobile.ui.tr(
                        "Röle adresi belirlenemedi: sunucu adresi çözümlenemiyor. " +
                            "Ayarlar'dan röle adresini elle girebilirsin.",
                        "Couldn't determine the relay address: the server address " +
                            "can't be parsed. You can set the relay address " +
                            "manually in Settings.",
                    )
                )
            }
            return
        }
        if (p.token.isBlank()) {
            _state.update { it.copy(error = "Önce Sunucular sekmesinden token girin") }
            return
        }

        stop()
        val c = LiveVoiceClient(
            relayBase = relay,
            hermesToken = p.token,
            apiKeyOverride = p.geminiApiKey.takeIf { it.isNotBlank() },
            router = router,
            liveModel = settings.liveModel,
            liveVoice = settings.liveVoice,
            systemInstruction = settings.resolveInstruction(),
            phoneTools = phoneTools.takeIf { settings.phoneTools },
            shizukuReady = settings.shizukuEnabled && shizuku?.isReady == true,
        )
        client = c

        jobs += viewModelScope.launch { c.state.collect { s -> _state.update { it.copy(state = s) } } }
        jobs += viewModelScope.launch { c.userTranscript.collect { t -> _state.update { it.copy(userText = t) } } }
        jobs += viewModelScope.launch { c.modelTranscript.collect { t -> _state.update { it.copy(modelText = t) } } }
        jobs += viewModelScope.launch { c.level.collect { l -> _state.update { it.copy(level = l) } } }
        jobs += viewModelScope.launch { c.error.collect { e -> _state.update { it.copy(error = e) } } }
        jobs += viewModelScope.launch { router.route.collect { r -> _state.update { it.copy(route = r) } } }
        jobs += viewModelScope.launch {
            router.available.collect { list -> _state.update { it.copy(routeOptions = list) } }
        }

        // Ön plan servisi her canlı oturumda çalışıyor, yalnız sürüş kipinde
        // değil: telefon aracı Maps/Ayarlar açtığında uygulama arka plana
        // düşüyor ve Android mikrofonu ancak bu servisle veriyor. Bu olmadan
        // "yol tarifi aç" dedikten sonra konuşmaya devam edilemiyordu.
        DrivingModeService.start(getApplication(), driving = _state.value.driving)

        _state.update { it.copy(error = null, userText = "", modelText = "") }
        c.start()
    }

    fun stop() {
        // Sürüş kipi açıkken servis kalmalı; onu `stopDriving` kapatıyor.
        if (!_state.value.driving) DrivingModeService.stop(getApplication())
        jobs.forEach { it.cancel() }
        jobs.clear()
        client?.release()
        client = null
        _state.update { it.copy(state = LiveVoiceClient.State.Idle, level = 0f) }
    }

    /** Hoparlör → kulaklık → Bluetooth → kablolu arasında döner. */
    /**
     * Sürüş kipi — ekran kapalıyken de dinlemeyi sürdürür.
     *
     * Ön plan servisi Android 14+'ta arka planda mikrofon için zorunlu; ayrıca
     * kısmi wake lock ile işlemcinin uyuması engelleniyor.
     */
    fun startDriving() {
        _state.update { it.copy(driving = true) }
        DrivingModeService.start(getApplication(), driving = true)
        if (!state.value.isRunning) start()
    }

    fun stopDriving() {
        DrivingModeService.stop(getApplication())
        _state.update { it.copy(driving = false) }
        stop()
    }

    fun cycleAudioRoute() {
        router.refreshAvailable()
        router.cycle()
    }

    fun selectAudioRoute(route: AudioRouter.Route) = router.select(route)

    fun sendText(text: String) = client?.sendText(text)

    // ── Kamera ────────────────────────────────────────────────────────

    private val camera = CameraFrameStreamer(app)

    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    val cameraStreamer: CameraFrameStreamer get() = camera

    init {
        viewModelScope.launch {
            camera.active.collect { a -> _cameraState.update { it.copy(active = a) } }
        }
        viewModelScope.launch {
            camera.error.collect { e -> _cameraState.update { it.copy(error = e) } }
        }
    }

    /**
     * Kamerayı bağlar ve kareleri Gemini'ye akıtmaya başlar.
     *
     * Sesli oturum kapalıysa kareler boşa gider — bu yüzden gerekirse önce
     * oturum açılır.
     */
    fun startCamera(owner: LifecycleOwner, previewView: PreviewView) {
        if (!state.value.isRunning) start()
        camera.start(owner, previewView) { jpeg ->
            client?.sendVideoFrame(jpeg)
            _cameraState.update { it.copy(framesSent = it.framesSent + 1) }
        }
    }

    fun stopCamera() {
        camera.stop()
        client?.clearLastFrame()
        _cameraState.update { it.copy(framesSent = 0) }
    }

    fun switchCamera() = camera.switchLens()

    fun clearError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        DrivingModeService.stop(getApplication())
        camera.release()
        stop()
        super.onCleared()
    }
}
