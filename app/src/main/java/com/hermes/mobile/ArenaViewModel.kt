package com.hermes.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.mobile.data.ArenaAnswer
import com.hermes.mobile.data.ArenaMode
import com.hermes.mobile.data.ArenaPrompts
import com.hermes.mobile.data.GatewayWsClient
import com.hermes.mobile.data.HermesClient
import com.hermes.mobile.data.HermesProfile
import com.hermes.mobile.data.ServerProfileStore
import com.hermes.mobile.ui.ArenaFigure
import com.hermes.mobile.ui.ArenaFigureState
import com.hermes.mobile.ui.arenaFigureId
import com.hermes.mobile.ui.arenaSynthFigureId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Arena'nın tek seferki sonuç durumu. */
sealed interface ArenaPhase {
    /** Henüz başlanmadı. */
    data object Idle : ArenaPhase

    /** Kurulum ekranı dolu ama başlatılmadı. */
    data object Ready : ArenaPhase

    /** Botlar çalışıyor. */
    data class Running(
        val mode: ArenaMode,
        val activeSessions: List<String> = emptyList(),
    ) : ArenaPhase

    /** Tamamlandı — cevaplar ve tur metaları. */
    data class Done(
        val answers: List<ArenaAnswer>,
        val synthesisAnswer: ArenaAnswer? = null,
        val mode: ArenaMode,
    ) : ArenaPhase
}

data class ArenaState(
    val profiles: List<HermesProfile> = emptyList(),
    val selectedProfiles: List<String> = emptyList(),
    val topic: String = "",
    val mode: ArenaMode = ArenaMode.SINGLE,
    val phase: ArenaPhase = ArenaPhase.Idle,
    val loadingProfiles: Boolean = false,
    val error: String? = null,
    /**
     * Sahne figürleri (tur-9): Arena 3D sahnesinde her bot bir figür.
     * Kimlikler fazlar arasında kararlıdır (bkz. `ui/ArenaSceneModel.kt`).
     */
    val figures: List<ArenaFigure> = emptyList(),
    /** "Durdur" ile mi bitti — sahne sakince beklemeye döner (animasyon donmaz). */
    val stopped: Boolean = false,
)

/**
 * Bot Arena ViewModel — 3 kip: Tek bot, Kapışma, Beyin fırtınası.
 *
 * Her bot gateway'e ayrı `session.create` ile bağlanır; tur 1 paralel,
 * kapışma tur 2 ve sentez sıralı çalışır. Durdur → tüm oturumlara
 * `interrupt` (session.interrupt) gönderilir.
 */
class ArenaViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ArenaState())
    val state: StateFlow<ArenaState> = _state.asStateFlow()

    private var runningJob: Job? = null
    private var activeGw: GatewayWsClient? = null
    private var activeSessions = mutableListOf<String>()

    /**
     * Sahne figürleri (tur-9) — sıra korunur, kimlik kararlı.
     *
     * Not: yazımlar ana iş parçacığında (viewModelScope) yapılır; aynı bot
     * kapışmada iki kez (tur 1 + tur 2) ve beyin fırtınasında ayrıca sentez
     * figürü olarak yer alır → kimlikler tur ekiyle ayrılır.
     */
    private val figureStates = LinkedHashMap<String, ArenaFigure>()

    private fun round1Badge() = com.hermes.mobile.ui.tr("Tur 1", "Round 1")

    private fun round2Badge() = com.hermes.mobile.ui.tr("Tur 2", "Round 2")

    private fun synthBadge() = com.hermes.mobile.ui.tr("Sentez", "Synthesis")

    /** Figürün durumunu yazar ve state'e yayar (sahne bu listeden çizilir). */
    private fun markFigure(id: String, name: String, state: ArenaFigureState, badge: String?) {
        figureStates[id] = ArenaFigure(id = id, name = name, state = state, badge = badge)
        val snapshot = figureStates.values.toList()
        _state.update { it.copy(figures = snapshot) }
    }

    /** Tüm figürleri verilen duruma çeker (durdurma/kesinti). */
    private fun markAll(state: ArenaFigureState, onlyUnfinished: Boolean = true) {
        figureStates.keys.toList().forEach { k ->
            val f = figureStates.getValue(k)
            if (!onlyUnfinished || f.state != ArenaFigureState.DONE) {
                figureStates[k] = f.copy(state = state)
            }
        }
        val snapshot = figureStates.values.toList()
        _state.update { it.copy(figures = snapshot) }
    }

    init {
        refreshProfiles()
    }

    /** Aktif sunucudan profilleri getirir (wt-bot-atama ile aynı uç). */
    fun refreshProfiles() {
        val store = ServerProfileStore(getApplication())
        val active = store.active() ?: run {
            // FR-002: sessiz dönüş yerine görünür neden — "sunucu yok" durumu
            // "gateway bağlı değil" sanılıyordu.
            _state.update {
                it.copy(
                    loadingProfiles = false,
                    // FR-002: sessiz dönüş yerine görünür neden — "sunucu yok" durumu
                    // "gateway bağlı değil" sanılıyordu. tr(): dil sızıntısı yasak.
                    error = com.hermes.mobile.ui.tr(
                        "Sunucu seçilmemiş — önce Ayarlar > Sunucular'dan bağlan",
                        "No server selected — connect first via Settings > Servers",
                    ),
                )
            }
            return
        }
        _state.update { it.copy(loadingProfiles = true) }
        viewModelScope.launch {
            runCatching { HermesClient(active).profiles() }
                .onSuccess { profs ->
                    // Başarılı yükleme eski hatayı da taşımamalı: aksi halde
                    // retry sonrası hata şeridi ekranda asılı kalıyor.
                    _state.update { it.copy(profiles = profs, loadingProfiles = false, error = null) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loadingProfiles = false, error = e.message ?: "Profiller yüklenemedi")
                    }
                }
        }
    }

    fun setTopic(topic: String) {
        _state.update { it.copy(topic = topic, error = null) }
    }

    fun setMode(mode: ArenaMode) {
        _state.update { it.copy(mode = mode) }
    }

    /** Bot çipi: profil adını seç/dışla (maks 4). */
    fun toggleProfile(name: String) {
        _state.update { st ->
            val selected = if (name in st.selectedProfiles) st.selectedProfiles - name
            else if (st.selectedProfiles.size >= 4) st.selectedProfiles
            else st.selectedProfiles + name
            st.copy(selectedProfiles = selected)
        }
    }

    /**
     * Arena'yı başlatır.
     *
     * Boş konu reddedilir, en az 1 bot şart, gateway bağlı olmalı.
     *
     * @return null başarılı, yoksa hata mesajı
     */
    fun startArena(gateway: GatewayWsClient?): String? {
        val st = _state.value
        val topic = st.topic.trim()
        if (topic.isEmpty()) return "Konu boş olamaz"
        if (st.selectedProfiles.isEmpty()) return "En az bir bot seç"
        // FR-002: gateway istemcisi henüz yok (bağlantı kurulmadı/kapatıldı).
        // Sabit yanıltıcı metin yerine ne olduğu + ne yapılacağı söylenir; WS
        // hatası (ör. 502) varsa ChatViewModel başlığındaki durum noktasında
        // gerçek neden (ConnectionError.reason) zaten görünür.
        val gw = gateway ?: return "Gateway bağlantısı yok — sohbet sekmesinde " +
            "durum noktasına dokun, bağlanınca tekrar dene"

        activeGw = gw
        activeSessions.clear()
        // Sahne: seçili botlar önce "bekliyor" olarak doğar (tur-9).
        figureStates.clear()
        st.selectedProfiles.forEach { bot ->
            val fid = arenaFigureId(bot, 1)
            figureStates[fid] = ArenaFigure(id = fid, name = bot, state = ArenaFigureState.WAITING, badge = round1Badge())
        }
        _state.update {
            it.copy(
                phase = ArenaPhase.Running(st.mode),
                error = null,
                figures = figureStates.values.toList(),
                stopped = false,
            )
        }

        runningJob = viewModelScope.launch {
            runCatching {
                when (st.mode) {
                    ArenaMode.SINGLE -> runSingle(gw, st.selectedProfiles, topic)
                    ArenaMode.BATTLE -> runBattle(gw, st.selectedProfiles, topic)
                    ArenaMode.BRAINSTORM -> runBrainstorm(gw, st.selectedProfiles, topic)
                }
            }.onFailure { e ->
                // Kesinti: bitmemiş figürler kırmızıya döner (sahne "hata" gösterir).
                markAll(ArenaFigureState.ERROR)
                _state.update {
                    it.copy(phase = ArenaPhase.Done(emptyList(), null, it.mode), error = e.message ?: "Bilinmeyen hata")
                }
            }
        }
        return null
    }

    // ── Tur-20: yarış nabız ölçümü (Outrun veri sürücüsü) ────────────────────
    // awaitAnswer'ın 250 ms'lik örnekleme pencereleri: delta karakter sayacı +
    // tool.start geçiş zamanı. UI 250 ms'de bir sampleRace() çağırır; saf
    // kararlar RaceDriveModel'de (testli).
    private class RaceWindow {
        @Volatile var count: Long = 0
        @Volatile var since: Long = System.currentTimeMillis()
        @Volatile var passUntilMs: Long = 0L
    }

    private val raceWindows = java.util.concurrent.ConcurrentHashMap<String, RaceWindow>()

    private fun raceNote(fid: String, chars: Int) {
        raceWindows.getOrPut(fid) { RaceWindow() }.count += chars.coerceAtLeast(0)
    }

    private fun racePass(fid: String) {
        raceWindows.getOrPut(fid) { RaceWindow() }.passUntilMs =
            System.currentTimeMillis() + 240L
    }

    /**
     * Şu an ÇALIŞAN figürlerin yarış nabzı (karakter/s, pencere ortalaması).
     * Pencere her örneklemede döner (UI 250 ms'de bir çağırır).
     */
    fun sampleRace(nowMs: Long): List<com.hermes.mobile.ui.RaceBotPulse> =
        figureStates.values.filter { it.state == ArenaFigureState.WORKING }.map { f ->
            val w = raceWindows[f.id]
            val elapsed = ((nowMs - (w?.since ?: nowMs)) / 1000.0).coerceAtLeast(0.2)
            val count = w?.count ?: 0L
            if (w != null) { w.count = 0; w.since = nowMs }
            com.hermes.mobile.ui.RaceBotPulse(
                id = f.id,
                charsPerSec = count / elapsed,
                working = true,
                passUntilMs = w?.passUntilMs ?: 0L,
            )
        }

    /** Tur 2 / sentez / durdur: eski pencereleri temizle (sıfır nabız kalmasın). */
    private fun raceReset() {
        raceWindows.clear()
    }

    /** Tüm aktif oturumlara interrupt gönderir ve state'i Done'a çeker. */
    fun stop() {
        activeGw?.let { gw ->
            viewModelScope.launch {
                activeSessions.forEach { sid ->
                    runCatching { gw.interrupt(sid) }
                }
            }
        }
        runningJob?.cancel()
        // Tur-9: sahne "sakince durur" — figürler SİLİNMEZ, beklemeye döner
        // (animasyon donmaz, idle nefesine iner).
        markAll(ArenaFigureState.WAITING, onlyUnfinished = false)
        _state.update {
            it.copy(
                phase = ArenaPhase.Done(emptyList(), null, it.mode),
                error = "Durduruldu",
                stopped = true,
            )
        }
    }

    fun clear() {
        runningJob?.cancel()
        activeSessions.clear()
        figureStates.clear()
        raceReset()
        _state.update { ArenaState(profiles = it.profiles, mode = it.mode) }
    }

    // ── Kip implementasyonları ──────────────────────────────────────────────

    /** Tek bot: her seçili profil konuya cevap verir. */
    private suspend fun runSingle(gw: GatewayWsClient, bots: List<String>, topic: String) {
        val answers = mutableListOf<ArenaAnswer>()
        bots.forEach { bot ->
            val fid = arenaFigureId(bot, 1)
            markFigure(fid, bot, ArenaFigureState.WORKING, round1Badge())
            val sid = gw.createSession(bot)
            activeSessions += sid
            val text = try {
                awaitAnswer(gw, sid, ArenaPrompts.round1(topic), raceFid = fid)
            } catch (e: Throwable) {
                markFigure(fid, bot, ArenaFigureState.ERROR, round1Badge())
                throw e
            }
            markFigure(fid, bot, ArenaFigureState.DONE, round1Badge())
            answers += ArenaAnswer(bot, round = 1, text = text)
        }
        finish(answers)
    }

    /** Kapışma: tur 1 bağımsız → tur 2 her bot diğerlerini görüp revize eder. */
    private suspend fun runBattle(gw: GatewayWsClient, bots: List<String>, topic: String) {
        val r1 = runRound1(gw, bots, ArenaPrompts.round1(topic), topic)
        val r2 = mutableListOf<ArenaAnswer>()
        bots.forEach { bot ->
            val fid = arenaFigureId(bot, 2)
            markFigure(fid, bot, ArenaFigureState.WORKING, round2Badge())
            val self = r1.first { it.bot == bot }.text
            val others = r1.filter { it.bot != bot }.associate { it.bot to it.text }
            val prompt = ArenaPrompts.battleRound2(topic, self, others)
            val sid = gw.createSession(bot)
            activeSessions += sid
            val text = try {
                awaitAnswer(gw, sid, prompt, raceFid = fid)
            } catch (e: Throwable) {
                markFigure(fid, bot, ArenaFigureState.ERROR, round2Badge())
                throw e
            }
            markFigure(fid, bot, ArenaFigureState.DONE, round2Badge())
            r2 += ArenaAnswer(bot, round = 2, text = text)
        }
        finish(r1 + r2)
    }

    /** Beyin fırtınası: bağımsız fikirler → sentez botu 5 maddelik final verir. */
    private suspend fun runBrainstorm(gw: GatewayWsClient, bots: List<String>, topic: String) {
        val ideas = runRound1(gw, bots, ArenaPrompts.brainstormIdeas(topic), topic)
        val synthBot = bots.first()
        val synthPrompt = ArenaPrompts.synthesis(topic, ideas.associate { it.bot to it.text })
        val fid = arenaSynthFigureId(synthBot)
        markFigure(fid, synthBot, ArenaFigureState.WORKING, synthBadge())
        val sid = gw.createSession(synthBot)
        activeSessions += sid
        val synthText = try {
            awaitAnswer(gw, sid, synthPrompt, raceFid = fid)
        } catch (e: Throwable) {
            markFigure(fid, synthBot, ArenaFigureState.ERROR, synthBadge())
            throw e
        }
        markFigure(fid, synthBot, ArenaFigureState.DONE, synthBadge())
        finish(ideas + ArenaAnswer(synthBot, round = 2, text = synthText))
    }

    /** Tur 1: tüm botlar paralel çalışır, her biri kendi oturumunda. */
    private suspend fun runRound1(
        gw: GatewayWsClient,
        bots: List<String>,
        prompt: String,
        topic: String,
    ): List<ArenaAnswer> = coroutineScope {
        bots.map { bot ->
            async {
                val fid = arenaFigureId(bot, 1)
                markFigure(fid, bot, ArenaFigureState.WORKING, round1Badge())
                val sid = gw.createSession(bot)
                activeSessions += sid
                val text = try {
                    awaitAnswer(gw, sid, prompt, raceFid = fid)
                } catch (e: Throwable) {
                    markFigure(fid, bot, ArenaFigureState.ERROR, round1Badge())
                    throw e
                }
                markFigure(fid, bot, ArenaFigureState.DONE, round1Badge())
                ArenaAnswer(bot, round = 1, text = text)
            }
        }.awaitAll()
    }

    private suspend fun awaitAnswer(
        gw: GatewayWsClient,
        sid: String,
        prompt: String,
        timeoutMs: Long = 90_000,
        raceFid: String? = null,
    ): String {
        val buf = StringBuilder()
        val done = CompletableDeferred<String>()
        val job = viewModelScope.launch {
            gw.events.collect { e ->
                if (e.sessionId != sid) return@collect
                when (e.type) {
                    "message.delta" -> {
                        val t = e.text.orEmpty()
                        buf.append(t)
                        // Tur-20: yarış nabzı — delta karakterleri sayaçlanır.
                        raceFid?.let { raceNote(it, t.length) }
                    }
                    "tool.start" ->
                        // Tur-20: araç çağrısı = geçiş anı (sol şeride geçiş sembolü).
                        raceFid?.let { racePass(it) }
                    "message.complete" ->
                        done.complete(buf.toString().ifBlank { e.text.orEmpty() })
                    "error" ->
                        done.completeExceptionally(IllegalStateException(e.text ?: "AI hatası"))
                }
            }
        }
        try {
            gw.submitPrompt(sid, prompt)
            val answer = withTimeoutOrNull(timeoutMs) { done.await() }
                ?: run {
                    runCatching { gw.interrupt(sid) }
                    throw IllegalStateException("AI yanıt vermedi")
                }
            return answer
        } finally {
            job.cancel()
        }
    }

    private fun finish(answers: List<ArenaAnswer>) {
        val mode = _state.value.mode
        val synthAnswer = if (mode == ArenaMode.BRAINSTORM && answers.size > 1)
            answers.last() else null
        _state.update {
            it.copy(
                phase = ArenaPhase.Done(
                    answers = answers,
                    synthesisAnswer = synthAnswer,
                    mode = mode,
                )
            )
        }
    }
}
