package com.hermes.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.GatewayWsClient
import com.hermes.mobile.data.LiveSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveState(
    val sessions: List<LiveSession> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    /** Müdahale sayfası açık olan oturum. */
    val intervening: LiveSession? = null,
    val sending: Boolean = false,
)

/** Müdahale biçimi — sunucudaki iki ayrı RPC'ye karşılık gelir. */
enum class InterventionKind {
    /** `session.steer` — turu kesmeden ekle. */
    Add,

    /** `session.redirect` — süren turu yönlendir. */
    Redirect,
}

/**
 * Canlı oturumlar — Hermes'in en ayırt edici yeteneği.
 *
 * `session.active_list` gateway sürecinde bellekte ajanı olan oturumları döner:
 * Telegram'dan gelen bir mesaj, gece çalışan bir cron, açık bir CLI/TUI oturumu.
 * Bunlara telefondan müdahale edilebilir — turu kesmeden metin eklenebilir
 * (`steer`), süren tur yönlendirilebilir (`redirect`) ya da durdurulabilir.
 *
 * Bu ViewModel WS istemcisini `ChatViewModel` ile paylaşmaz; bağlayıcı
 * `MainActivity` aynı profil için tekil istemciyi geçirir.
 */
class LiveSessionsViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private var client: GatewayWsClient? = null
    private var pollJob: Job? = null

    fun bind(gw: GatewayWsClient?) {
        if (gw === client) return
        client = gw
        pollJob?.cancel()
        if (gw == null) {
            _state.value = LiveState()
            return
        }
        refresh()
        // Çalışan oturumların durumu hızlı değişiyor; 6 saniyede bir tazele.
        pollJob = viewModelScope.launch {
            while (true) {
                delay(6_000)
                if (_state.value.intervening == null) refresh(quiet = true)
            }
        }
    }

    fun refresh(quiet: Boolean = false) {
        val gw = client ?: return
        viewModelScope.launch {
            if (!quiet) _state.update { it.copy(loading = true, error = null) }
            runCatching { gw.activeSessions() }
                .onSuccess { list ->
                    // Tur-2 K3(b): yeniden bağlanmada gateway aynı oturumu iki
                    // süreç içi kayıtla bırakabiliyor — LazyColumn key={id}
                    // çakışması FATAL. liveFeed() dbId'de ayıklıyor; bu ekran
                    // ham id kullanıyor, burada süz.
                    val distinct = list.distinctBy { it.id }
                    if (distinct.size != list.size) {
                        DiagLog.w("live", "active_list tekrarli: ${list.size} -> ${distinct.size}")
                    }
                    _state.update { it.copy(sessions = distinct, loading = false, error = null) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = e.message ?: "Canlı oturumlar alınamadı")
                    }
                }
        }
    }

    fun openIntervention(session: LiveSession) {
        _state.update { it.copy(intervening = session) }
    }

    fun closeIntervention() {
        _state.update { it.copy(intervening = null) }
    }

    /**
     * Oturuma müdahale eder.
     *
     * `Redirect` ajan tarafından desteklenmiyorsa sunucu 4010 döner — bu durumda
     * sessizce `steer`e düşülür, çünkü kullanıcının niyeti her hâlükârda mesajı
     * ulaştırmak.
     */
    fun intervene(session: LiveSession, kind: InterventionKind, text: String) {
        val gw = client ?: return
        val body = text.trim()
        if (body.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(sending = true) }
            val outcome = runCatching {
                when (kind) {
                    InterventionKind.Add -> gw.steer(session.id, body)
                    InterventionKind.Redirect -> gw.redirect(session.id, body)
                }
            }.recoverCatching { e ->
                if (kind == InterventionKind.Redirect &&
                    (e.message?.contains("4010") == true ||
                        e.message?.contains("does not support") == true)
                ) {
                    gw.steer(session.id, body) + " (yönlendirme desteklenmedi, eklendi)"
                } else throw e
            }

            outcome
                .onSuccess { status ->
                    _state.update {
                        it.copy(
                            sending = false,
                            intervening = null,
                            notice = when {
                                status.startsWith("queued") -> "Mesaj ajana iletildi"
                                status.startsWith("rejected") -> "Ajan şu an kabul etmedi"
                                else -> "Sonuç: $status"
                            },
                        )
                    }
                    refresh(quiet = true)
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(sending = false, notice = e.message ?: "Müdahale başarısız")
                    }
                }
        }
    }

    fun interrupt(session: LiveSession) {
        val gw = client ?: return
        viewModelScope.launch {
            runCatching { gw.interrupt(session.id) }
                .onSuccess { _state.update { it.copy(notice = "Oturum durduruldu") } }
                .onFailure { e ->
                    _state.update { it.copy(notice = e.message ?: "Durdurulamadı") }
                }
            refresh(quiet = true)
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }
}
