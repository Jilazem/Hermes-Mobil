package com.hermes.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.mobile.data.GatewayWsClient
import com.hermes.mobile.data.HermesClient
import com.hermes.mobile.data.HermesProfile
import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.HermesStatus
import com.hermes.mobile.data.ProbeResult
import com.hermes.mobile.data.SavedPrompt
import com.hermes.mobile.data.SavedPromptsStore
import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.data.ServerProfileStore
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.data.SessionFlagsStore
import com.hermes.mobile.data.SettingsStore
import com.hermes.mobile.data.SessionMessage
import com.hermes.mobile.data.SystemStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.hermes.mobile.data.SessionCache

data class SessionDetailState(
    val sessionId: String = "",
    val session: HermesSession? = null,
    val messages: List<SessionMessage> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

data class AppState(
    val profiles: List<ServerProfile> = emptyList(),
    val activeId: String? = null,
    val probes: Map<String, ProbeResult> = emptyMap(),
    val status: HermesStatus? = null,
    val stats: SystemStats? = null,
    val sessions: List<HermesSession> = emptyList(),
    /**
     * Liste sunucudan degil yerel onbellekten geliyorsa, alindigi an
     * (epoch ms). Bayat veriyi bayat oldugunu soylemeden gostermek,
     * hic gostermemekten daha kotu olurdu.
     */
    val sessionsCachedAt: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /**
     * Sabitle / arşivle / sil / yeniden adlandır bayrakları. Sunucuda mutation
     * ucu olmadığı için istemci-taraflı; polling listeyi ezse de bunlar kalır
     * (detay: SessionFlagsStore).
     */
    val flags: SessionFlags = SessionFlags(),
    /** Pull-to-refresh spinner'ı — polling'in `loading`'ından ayrı bayrak. */
    val pullRefreshing: Boolean = false,
) {
    val active: ServerProfile? get() = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    val activeProbe: ProbeResult? get() = active?.let { probes[it.id] }
    val isConnected: Boolean get() = activeProbe is ProbeResult.Ok
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    /** Cevrimdisi oturum listesi -- ayrintisi SessionCache'te. */
    private val sessionCache = SessionCache(app)

    /** Sabitle/arşivle/gizle/yeniden adlandır bayrakları — SessionCache kalıbı. */
    private val flagsStore = SessionFlagsStore(app)

    /** Kayıtlı promptlar — SavedPromptsStore (SessionFlagsStore kalıbı, cihazda). */
    private val promptsStore = SavedPromptsStore(app)

    private val _savedPrompts = MutableStateFlow<List<SavedPrompt>>(emptyList())
    val savedPrompts: StateFlow<List<SavedPrompt>> = _savedPrompts.asStateFlow()

    init {
        // Depo küçük; açılışta bir kez diskten okunur, sonrası mutasyonlarla gelir.
        _savedPrompts.value = promptsStore.yukle()
    }

    fun kaydetPrompt(etiket: String, metin: String): SavedPrompt {
        val p = promptsStore.kaydet(etiket, metin)
        _savedPrompts.value = promptsStore.yukle()
        return p
    }

    fun guncellePrompt(id: String, etiket: String, metin: String) {
        promptsStore.guncelle(id, etiket, metin)
        _savedPrompts.value = promptsStore.yukle()
    }

    fun silPrompt(id: String) {
        promptsStore.sil(id)
        _savedPrompts.value = promptsStore.yukle()
    }

    private val store = ServerProfileStore(app)

    val settingsStore = SettingsStore(app)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var pollJob: Job? = null

    private val _profiles = MutableStateFlow<List<HermesProfile>>(emptyList())
    val profiles: StateFlow<List<HermesProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow("")
    val activeProfile: StateFlow<String> = _activeProfile.asStateFlow()

    private val _profilesLoading = MutableStateFlow(false)
    val profilesLoading: StateFlow<Boolean> = _profilesLoading.asStateFlow()

    fun loadProfiles() {
        val p = _state.value.active ?: return
        viewModelScope.launch {
            _profilesLoading.value = true
            val client = HermesClient(p)
            runCatching { client.profiles() }.onSuccess { _profiles.value = it }
            runCatching { client.activeProfile() }
                .onSuccess { _activeProfile.value = it.active.ifBlank { it.current } }
            _profilesLoading.value = false
        }
    }

    /** Profili hem yönetim yüzeyinde hem yeni oturumlarda etkin yapar. */
    fun selectHermesProfile(name: String, onApplied: (String) -> Unit) {
        val p = _state.value.active ?: return
        viewModelScope.launch {
            runCatching { HermesClient(p).setActiveProfile(name) }
                .onSuccess {
                    _activeProfile.value = name
                    onApplied(name)
                    loadActiveDetails()
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "Profil değiştirilemedi") }
                }
        }
    }

    private val _detail = MutableStateFlow<SessionDetailState?>(null)
    val detail: StateFlow<SessionDetailState?> = _detail.asStateFlow()

    /**
     * Canlı oturumların geçmişi gateway belleğinde durur ve REST'te görünmez;
     * `LiveSessionsViewModel` ile aynı soket buraya da geçirilir.
     */
    private var gateway: GatewayWsClient? = null

    fun bindGateway(gw: GatewayWsClient?) {
        gateway = gw
    }

    /** Bir oturumu açar ve mesaj dökümünü yükler. */
    fun openSession(session: HermesSession, liveId: String? = null) {
        _detail.value = SessionDetailState(sessionId = session.id, session = session)
        val profile = _state.value.active ?: return
        viewModelScope.launch {
            runCatching {
                // Canlı oturumsa önce bellekteki geçmiş, yoksa veritabanı.
                val fromLive = liveId?.let { id ->
                    gateway?.let { gw -> runCatching { gw.sessionHistory(id) }.getOrNull() }
                }.orEmpty()
                fromLive.ifEmpty { HermesClient(profile).sessionMessages(session.id) }
            }
                .onSuccess { messages ->
                    _detail.update { it?.copy(messages = messages, loading = false) }
                }
                .onFailure { e ->
                    _detail.update {
                        it?.copy(loading = false, error = e.message ?: "Mesajlar okunamadı")
                    }
                }
        }
    }

    /** Yalnız id bilinen (canlı listeden gelen) bir oturumu açar. */
    fun openSessionById(id: String, title: String, liveId: String? = null) {
        openSession(HermesSession(id = id, displayName = title.ifBlank { null }), liveId)
    }

    fun closeSession() {
        _detail.value = null
    }

    init {
        seedDefaultProfileIfEmpty()
        reloadProfiles()
        refreshAll()
        startPolling()
    }

    /**
     * İlk açılışta the server profilini hazır getirir — token boş bırakılır,
     * kullanıcı QR okutarak veya elle girerek tamamlar.
     */
    private fun seedDefaultProfileIfEmpty() {
        if (store.list().isNotEmpty()) return
        val server = ServerProfile(
            name = "the server",
            baseUrl = "http://192.168.1.10:9150",
            token = "",
            note = "Caddy → 127.0.0.1:9120 · token ~/.hermes/.env",
        )
        store.upsert(server)
        store.setActiveId(server.id)
    }

    private fun reloadProfiles() {
        _state.update { it.copy(profiles = store.list(), activeId = store.activeId()) }
        loadFlags()
    }

    /** Aktif profilin bayraklarını diskten geri yükler (profil değişince sıfırlanmaz). */
    private fun loadFlags() {
        val id = _state.value.active?.id ?: return
        _state.update { it.copy(flags = flagsStore.load(id)) }
    }

    /** Bir bayrak mutasyonunu diske yazar + state'e işler. */
    private fun mutateFlags(transform: (SessionFlags) -> SessionFlags) {
        val profileId = _state.value.active?.id ?: return
        val yeni = transform(_state.value.flags)
        flagsStore.save(profileId, yeni)
        _state.update { it.copy(flags = yeni) }
    }

    fun togglePin(id: String) = mutateFlags { f ->
        f.copy(pinned = if (id in f.pinned) f.pinned - id else f.pinned + id)
    }

    fun setArchived(id: String, archived: Boolean) = mutateFlags { f ->
        f.copy(archived = if (archived) f.archived + id else f.archived - id)
    }

    fun renameSession(id: String, title: String) {
        val temiz = title.trim().take(60)
        if (temiz.isEmpty()) return
        mutateFlags { f -> f.copy(renames = f.renames + (id to temiz)) }
    }

    /** Silme = yerel gizleme (sunucu DELETE ucu yok); bkz. SessionFlagsStore. */
    fun deleteSession(id: String) = mutateFlags { f ->
        f.copy(hidden = f.hidden + id, pinned = f.pinned - id, archived = f.archived - id)
    }


    fun selectProfile(id: String) {
        store.setActiveId(id)
        reloadProfiles()
        refreshAll()
    }

    fun saveProfile(profile: ServerProfile) {
        store.upsert(profile)
        if (store.activeId() == null) store.setActiveId(profile.id)
        reloadProfiles()
        viewModelScope.launch { probe(profile) }
    }

    fun deleteProfile(id: String) {
        store.delete(id)
        reloadProfiles()
    }

    private suspend fun probe(profile: ServerProfile) {
        val result = if (profile.token.isBlank()) {
            ProbeResult.Fail("Token girilmemiş")
        } else {
            HermesClient(profile).probe()
        }
        _state.update { it.copy(probes = it.probes + (profile.id to result)) }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            _state.value.profiles.forEach { probe(it) }
            loadActiveDetails()
            _state.update { it.copy(loading = false) }
        }
    }

    /**
     * Pull-to-refresh: hafif yenileme. refreshAll tüm profilleri probe eder ve
     * `loading`'i yakar; çekiştirme yalnız aktif profilin detayını çeker.
     * Spinner bayrağı polling'den ayrı (`pullRefreshing`) — yoksa 15 sn'lik
     * polling her turda spinner tetikler gibi karışır.
     */
    fun refreshSessions() {
        if (_state.value.pullRefreshing) return
        viewModelScope.launch {
            _state.update { it.copy(pullRefreshing = true) }
            loadActiveDetails()
            _state.update { it.copy(pullRefreshing = false) }
        }
    }

    private suspend fun loadActiveDetails() {
        val profile = _state.value.active ?: return
        if (profile.token.isBlank()) return
        val client = HermesClient(profile)
        runCatching {
            val status = client.status()
            val stats = client.systemStats()
            val sessions = client.sessions()
            sessionCache.save(profile.id, sessions)
            _state.update {
                it.copy(
                    status = status, stats = stats, sessions = sessions,
                    error = null, sessionsCachedAt = null,
                )
            }
        }.onFailure { e ->
            // Sunucuya ulasilamiyor: elde ne varsa goster. Onceden liste
            // tamamen bosaliyordu ve kullanici "hangi konusmalarim vardi"
            // sorusuna bile cevap alamiyordu.
            val cached = sessionCache.load(profile.id)
            _state.update {
                if (cached != null && it.sessions.isEmpty()) {
                    it.copy(
                        sessions = cached.sessions,
                        sessionsCachedAt = cached.fetchedAt,
                        error = e.message ?: "Veri alınamadı",
                    )
                } else {
                    it.copy(error = e.message ?: "Veri alınamadı")
                }
            }
        }
    }

    /** Aktif profil çevrimiçiyken 15 saniyede bir durumu tazeler. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(15_000)
                val profile = _state.value.active ?: continue
                if (profile.token.isBlank()) continue
                probe(profile)
                loadActiveDetails()
            }
        }
    }

    fun gatewayAction(action: GatewayAction) {
        val profile = _state.value.active ?: return
        viewModelScope.launch {
            val client = HermesClient(profile)
            runCatching {
                when (action) {
                    GatewayAction.Start -> client.startGateway()
                    GatewayAction.Stop -> client.stopGateway()
                    GatewayAction.Restart -> client.restartGateway()
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "İşlem başarısız") }
            }
            loadActiveDetails()
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

enum class GatewayAction { Start, Stop, Restart }
