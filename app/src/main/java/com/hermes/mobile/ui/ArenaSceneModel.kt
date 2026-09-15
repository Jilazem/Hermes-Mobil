package com.hermes.mobile.ui

import com.hermes.mobile.ArenaPhase
import com.hermes.mobile.ArenaState
import com.hermes.mobile.data.LiveSession

/**
 * Arena 3D sahnesinin **saf** (Compose/WebView'dan bağımsız) çekirdeği.
 *
 * Sahne WebView içinde three.js ile çizilir; Kotlin tarafı yalnız iki iş yapar:
 *  1. `ArenaState` (+ canlı oturumlar) → JSON (faz / figür / durum / rozet)
 *  2. JSON → `evaluateJavascript` çağrısı (JS string kaçışı)
 *
 * Buradaki her fonksiyon yan etkisizdir; bu yüzden birim testleriyle kilitlenir.
 * (Tur-9 dersi: render katmanı test edilemez, KARARI test edilir.)
 */

/** Figürün sahnedeki durumu — JS sözlüğüyle birebir aynı tel adları. */
enum class ArenaFigureState(val wire: String) {
    /** Sakin/nefes alan figür. */
    WAITING("waiting"),

    /** Aktif üretim: enerji + titreşim. */
    WORKING("working"),

    /** Onay parlaması. */
    DONE("done"),

    /** Kırmızı kesinti. */
    ERROR("error"),
}

/** Sahnede bir figür: üst etikette adı, rozetinde turu/sentezi. */
data class ArenaFigure(
    val id: String,
    val name: String,
    val state: ArenaFigureState,
    val badge: String? = null,
)

/** Sahne fazı — JS tarafı bunu kamera/ışık/arena halkası için kullanır. */
enum class ArenaScenePhase(val wire: String) {
    IDLE("idle"),
    READY("ready"),
    RUNNING("running"),
    DONE("done"),

    /** "Durdur" sonrası: animasyon donmaz, figürler sakin beklemeye döner. */
    STOPPED("stopped"),
}

/** Rozet/etiket metinleri dile göre çözülür (UI katmanı doldurur). */
data class ArenaSceneLabels(
    val live: String,
    val round1: String,
    val round2: String,
    val synthesis: String,
) {
    companion object {
        val TR = ArenaSceneLabels(live = "Canlı", round1 = "Tur 1", round2 = "Tur 2", synthesis = "Sentez")
        val EN = ArenaSceneLabels(live = "Live", round1 = "Round 1", round2 = "Round 2", synthesis = "Synthesis")
    }
}

/** Sahne paleti — Arena ekranının koyu temasıyla bütünleşik. */
data class ArenaSceneTheme(
    val background: String,
    val grid: String,
    val accent: String,
    val ok: String,
    val danger: String,
) {
    companion object {
        val DARK = ArenaSceneTheme(
            background = "#0b0f13",
            grid = "#1b2a36",
            accent = "#5ec8ff",
            ok = "#4ade80",
            danger = "#ff4d5e",
        )
    }
}

// ── Kimlikler ───────────────────────────────────────────────────────────────
// Figür kimliği fazlar arasında KARARLI olmalı: çalışırken "working" olan figür
// bittiğinde aynı kimlikle "done"a geçer, aksi halde JS onu silip yenisini
// doğururdu (onay parlaması/figure continuity kaybolurdu).

/** Tur bazlı figür kimliği: `alfa#r1`, `alfa#r2`. */
fun arenaFigureId(bot: String, round: Int): String = "$bot#r$round"

/** Sentez figürü kimliği: `alfa#synth`. */
fun arenaSynthFigureId(bot: String): String = "$bot#synth"

/** Tur rozeti metni. */
fun arenaRoundBadge(round: Int, labels: ArenaSceneLabels): String = when (round) {
    1 -> labels.round1
    2 -> labels.round2
    else -> "${labels.round1}: $round"
}

/** Arena durumundan sahne fazı. */
fun arenaScenePhase(st: ArenaState): ArenaScenePhase = when (st.phase) {
    is ArenaPhase.Idle -> ArenaScenePhase.IDLE
    is ArenaPhase.Ready -> ArenaScenePhase.READY
    is ArenaPhase.Running -> ArenaScenePhase.RUNNING
    is ArenaPhase.Done -> if (st.stopped) ArenaScenePhase.STOPPED else ArenaScenePhase.DONE
}

/**
 * Sahnedeki figürler.
 *
 * - **Idle:** arena koşusu yok → sunucunun çalışan oturumları (varsa) figür olur;
 *   veri yoksa sahne boş arena olarak kalır (uydurma figür YOK).
 * - **Ready:** seçili botlar "bekliyor".
 * - **Running:** ViewModel'in izlediği figürler (iş başladı/bitti/hata).
 * - **Done:** cevaplar — ok → bitti (onay parlaması), !ok → hata (kırmızı kesinti);
 *   kapışmada tur 2, beyin fırtınasında sentez rozetle ayrılır.
 * - **Done + stopped:** figürler silinmez, sakin beklemeye döner.
 */
fun arenaSceneFigures(
    st: ArenaState,
    live: List<LiveSession> = emptyList(),
    labels: ArenaSceneLabels = ArenaSceneLabels.TR,
): List<ArenaFigure> = when (val p = st.phase) {
    is ArenaPhase.Idle -> arenaLiveFigures(live, labels)

    is ArenaPhase.Ready -> st.selectedProfiles.map { bot ->
        ArenaFigure(id = arenaFigureId(bot, 1), name = bot, state = ArenaFigureState.WAITING)
    }

    is ArenaPhase.Running -> st.figures

    is ArenaPhase.Done -> when {
        // "Durdur" sonrası sahne sakince durur: animasyon donmaz, idle'a döner.
        st.stopped -> st.figures.map { it.copy(state = ArenaFigureState.WAITING) }

        // Cevap yok (hata) → figürler kesintiye uğramış görünür.
        p.answers.isEmpty() -> st.figures.map {
            it.copy(state = if (st.error != null) ArenaFigureState.ERROR else ArenaFigureState.WAITING)
        }

        else -> {
            val synth = p.synthesisAnswer
            p.answers.mapIndexed { i, a ->
                val isSynth = synth != null && i == p.answers.lastIndex
                ArenaFigure(
                    id = if (isSynth) arenaSynthFigureId(a.bot) else arenaFigureId(a.bot, a.round),
                    name = a.bot,
                    state = if (a.ok) ArenaFigureState.DONE else ArenaFigureState.ERROR,
                    badge = if (isSynth) labels.synthesis else arenaRoundBadge(a.round, labels),
                )
            }
        }
    }
}

/**
 * Sunucunun **çalışan oturumları** → figürler (Arena boştayken sahne canlı kalır).
 *
 * Yalnız okuma: `session.active_list` (LiveSessions kaynağı). Yazma/müdahale yok.
 * Sıra sunucu sırasıdır, en fazla 8 figür (sahne kalabalıklaşmasın).
 */
fun arenaLiveFigures(
    live: List<LiveSession>,
    labels: ArenaSceneLabels = ArenaSceneLabels.TR,
    limit: Int = 8,
): List<ArenaFigure> = live
    .filter { it.id.isNotBlank() || it.dbId.isNotBlank() }
    .distinctBy { it.dbId.ifBlank { it.id } }
    .take(limit)
    .map { s ->
        ArenaFigure(
            id = "live:" + s.dbId.ifBlank { s.id },
            name = arenaLiveName(s),
            state = if (s.isWorking) ArenaFigureState.WORKING else ArenaFigureState.WAITING,
            badge = labels.live,
        )
    }

/** Oturum adı: başlık varsa başlık, yoksa kimlik; etiket okunur kalsın diye kırpılır. */
fun arenaLiveName(s: LiveSession, maxLen: Int = 28): String {
    val n = s.title.trim().ifBlank { s.id.trim() }.ifBlank { "?" }
    return if (n.length <= maxLen) n else n.take(maxLen - 1) + "…"
}

// ── JSON köprüsü ────────────────────────────────────────────────────────────

/**
 * Sahne verisini JSON'a çevirir ve `evaluateJavascript` için JS çağrısı üretir.
 *
 * Neden elle JSON: birim testlerinde `org.json` saplamadır (Android stub) ve
 * sahne verisi küçük/şekli sabittir. Kaçış kuralları testlerle kilitlenir.
 */
object ArenaSceneJson {

    fun encode(
        phase: ArenaScenePhase,
        figures: List<ArenaFigure>,
        theme: ArenaSceneTheme = ArenaSceneTheme.DARK,
    ): String {
        val sb = StringBuilder(128 + figures.size * 96)
        sb.append("{\"phase\":\"").append(phase.wire).append("\",\"theme\":{")
        sb.append("\"bg\":\"").append(str(theme.background)).append("\",")
        sb.append("\"grid\":\"").append(str(theme.grid)).append("\",")
        sb.append("\"accent\":\"").append(str(theme.accent)).append("\",")
        sb.append("\"ok\":\"").append(str(theme.ok)).append("\",")
        sb.append("\"danger\":\"").append(str(theme.danger)).append('"')
        sb.append("},\"figures\":[")
        figures.forEachIndexed { i, f ->
            if (i > 0) sb.append(',')
            sb.append(figure(f))
        }
        sb.append("]}")
        return sb.toString()
    }

    fun figure(f: ArenaFigure): String {
        val sb = StringBuilder(64)
        sb.append("{\"id\":\"").append(str(f.id)).append("\",")
        sb.append("\"name\":\"").append(str(f.name)).append("\",")
        sb.append("\"state\":\"").append(f.state.wire).append("\",")
        sb.append("\"badge\":")
        if (f.badge == null) sb.append("null") else sb.append('"').append(str(f.badge)).append('"')
        sb.append('}')
        return sb.toString()
    }

    /** JSON metin kaçışı (RFC 8259 string kuralları). */
    fun str(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                ch.code < 0x20 -> sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * `evaluateJavascript` gövdesi.
     *
     * JSON bir JS **string** olarak gömülür; bu yüzden ters bölü ve çift tırnak
     * ikinci kez kaçırılır. ASCII dışı karakterler `\uXXXX`e çevrilir: WebView
     * köprüsünde kodlama sarsılırsa etiketler bozulmasın (Türkçe adlar: "Canlı",
     * "Sentez", bot adları).
     */
    fun jsCall(json: String): String =
        "window.arenaScene&&window.arenaScene.setData(\"" + jsEscape(json) + "\")"

    /** JS string literal kaçışı (ASCII güvenli). */
    fun jsEscape(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when {
                ch == '\\' -> sb.append("\\\\")
                ch == '"' -> sb.append("\\\"")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\u2028' -> sb.append("\\u2028")
                ch == '\u2029' -> sb.append("\\u2029")
                ch.code < 0x20 || ch.code > 0x7e ->
                    sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Sahne duraklatma/aktiflik komutu — ekran görünmezken render döngüsü durur. */
    fun jsActive(active: Boolean): String =
        "window.arenaScene&&window.arenaScene.setActive($active)"
}

// ── Fallback kararı ─────────────────────────────────────────────────────────

/** Sahnenin bildirdiği durum (JS → Kotlin olayları). */
enum class ArenaSceneStatus {
    /** Sayfa/köprü yüklendi, 'ready' olayı beklemede. */
    LOADING,
    READY,

    /** Cihazda WebGL yok (JS 'nowebgl'). */
    NO_WEBGL,

    /** JS hatası / 'ready' hiç gelmedi. */
    ERROR,
}

/** Neden 3D yerine kart listesi gösteriliyor. */
enum class ArenaSceneFallbackReason { NONE, NO_WEBGL, SCRIPT_ERROR, TIMEOUT }

/** Sahne 'ready' için tanınan süre; bu süre dolunca kart listesine düşülür. */
const val ARENA_SCENE_TIMEOUT_MS = 4_000L

/**
 * Zarif geri düşme kararı — saf ve testli.
 *
 * `NONE` = sahne gösterilir (yüklenirken iskelet). Diğerleri = statik kart listesi
 * + "3D desteklenmiyor" notu. Çökme yok: WebView yalnız gizlenir.
 */
fun arenaSceneFallbackReason(
    status: ArenaSceneStatus,
    elapsedMs: Long,
    timeoutMs: Long = ARENA_SCENE_TIMEOUT_MS,
): ArenaSceneFallbackReason = when {
    status == ArenaSceneStatus.READY -> ArenaSceneFallbackReason.NONE
    status == ArenaSceneStatus.NO_WEBGL -> ArenaSceneFallbackReason.NO_WEBGL
    status == ArenaSceneStatus.ERROR -> ArenaSceneFallbackReason.SCRIPT_ERROR
    elapsedMs >= timeoutMs -> ArenaSceneFallbackReason.TIMEOUT
    else -> ArenaSceneFallbackReason.NONE
}

/** Karar → kart listesine düşülüyor mu. */
fun arenaUseCardFallback(reason: ArenaSceneFallbackReason): Boolean =
    reason != ArenaSceneFallbackReason.NONE
