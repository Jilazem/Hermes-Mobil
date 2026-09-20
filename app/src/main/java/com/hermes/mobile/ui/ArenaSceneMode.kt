package com.hermes.mobile.ui

/**
 * Tur-15: Arena sahne **kipi** — "İş sahnesi" (tur-9 arena3d) | "Outrun yarış".
 *
 * Buradaki her şey saf (Compose/WebView'dan bağımsız) ve birim testleriyle
 * kilitlenir: kip seçimi + kalıcılık kimliği, varlık yolu/URL inşası, JS
 * komutları ve yaşam döngüsü durum makinesi. WebView katmanı yalnız bu
 * kararları uygular (tur-9 dersi: render test edilemez, KARAR test edilir).
 */

/** İzin verilen tek varlık kökü — iki sahne de aynı klasörü (ve aynı three.min.js'i) paylaşır. */
const val ARENA_ASSET_ROOT = "file:///android_asset/arena/"

/** Outrun ilk kare için tanınan süre: yazılım GL'de (emülatör) shader derlemesi 4 sn'yi aşabiliyor. */
const val OUTRUN_SCENE_TIMEOUT_MS = 12_000L

/** Tur-20: kafes dövüşü de çok nesneli üç.js sahnesi — aynı geniş zaman aşımı. */
const val CAGE_SCENE_TIMEOUT_MS = 12_000L

enum class ArenaSceneKind(
    /** AppSettings'e yazılan kalıcı kimlik — DEĞİŞTİRME (eski kayıtlar bununla okunur). */
    val id: String,
    /** `ARENA_ASSET_ROOT` altındaki sayfa. */
    val page: String,
    /** Sayfanın dış API nesnesi (`window.<jsObject>`). */
    val jsObject: String,
    val timeoutMs: Long,
) {
    WORK("work", "arena3d.html", "arenaScene", ARENA_SCENE_TIMEOUT_MS),
    OUTRUN("outrun", "outrun.html", "outrun", OUTRUN_SCENE_TIMEOUT_MS),
    CAGE("cage", "cage.html", "cage", CAGE_SCENE_TIMEOUT_MS),
    ;

    companion object {
        val DEFAULT = WORK

        /** Kayıtlı kimlikten kip; boş/bilinmeyen/bozuk değer varsayılana (iş sahnesi) düşer. */
        fun fromId(id: String?): ArenaSceneKind =
            entries.firstOrNull { it.id == id?.trim()?.lowercase() } ?: DEFAULT
    }
}

/** Seçim → AppSettings değeri (tek yazım noktası). */
fun arenaSceneModeValue(kind: ArenaSceneKind): String = kind.id

/**
 * Varlık yolu çözümlemesi: yalnız düz dosya adı kabul edilir.
 * Klasör ayırıcı, `..`, şema veya boş ad → null (kökten dışarı çıkılamaz).
 */
fun arenaAssetPath(page: String): String? {
    val p = page.trim()
    if (p.isEmpty() || p.startsWith(".") || p.contains('/') || p.contains('\\') || p.contains(':')) return null
    return ARENA_ASSET_ROOT + p
}

/**
 * Sahne URL'si. `params` yalnız teşhis içindir (ör. `fps=1`); üründe boş geçilir.
 * Anahtarlar alfabetik sıralanır → URL deterministik; değerler yüzde-kodlanır.
 */
fun arenaSceneUrl(kind: ArenaSceneKind, params: Map<String, String> = emptyMap()): String {
    val base = requireNotNull(arenaAssetPath(kind.page)) { "gecersiz sahne sayfasi: ${kind.page}" }
    if (params.isEmpty()) return base
    val q = params.toSortedMap().entries
        .filter { it.key.isNotBlank() }
        .joinToString("&") { (k, v) -> urlEncode(k) + "=" + urlEncode(v) }
    return if (q.isEmpty()) base else "$base?$q"
}

/** WebView istek süzgeci: yalnız arena varlık kökü; `..` ile kaçış reddedilir. */
fun arenaAssetAllowed(url: String?): Boolean {
    val u = url ?: return false
    if (!u.startsWith(ARENA_ASSET_ROOT)) return false
    val path = u.removePrefix(ARENA_ASSET_ROOT).substringBefore('?').substringBefore('#')
    return path.isNotEmpty() && !path.split('/').any { it == ".." || it == "." } &&
        !path.contains("%2e", ignoreCase = true)
}

private fun urlEncode(s: String): String {
    val sb = StringBuilder(s.length)
    for (b in s.toByteArray(Charsets.UTF_8)) {
        val c = b.toInt() and 0xff
        val ch = c.toChar()
        if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
            sb.append(ch)
        } else {
            sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xf])
        }
    }
    return sb.toString()
}

// ── JS komutları ───────────────────────────────────────────────────────────

/** Render döngüsünü aç/kapa — sayfa henüz hazır değilse no-op (`a&&a.f()` koruması). */
fun arenaJsActive(kind: ArenaSceneKind, active: Boolean): String =
    "window.${kind.jsObject}&&window.${kind.jsObject}.setActive($active)"

/** Outrun oyununu duraklat (duraklatma ekranı görünür; "Devam" kullanıcıya ait). */
fun outrunJsPause(): String = "window.outrun&&window.outrun.pause()"

// ── Yaşam döngüsü durum makinesi ───────────────────────────────────────────

/** Sahnedeki oyun/sayfa durumu (JS olaylarından beslenir). */
enum class ArenaGameState { LOADING, MENU, RUNNING, PAUSED, OVER, NO_WEBGL, ERROR }

enum class ArenaSceneEvent {
    // JS → Kotlin
    READY, START, PAUSE, RESUME, GAME_OVER, NO_WEBGL, ERROR,

    // Kabuk
    TAB_HIDDEN, TAB_SHOWN, APP_PAUSED, APP_RESUMED,

    /** Donanım geri tuşu (yalnız [arenaBackConsumed] true iken sahneye gelir). */
    BACK,

    /** Sayfa yeniden yüklendi (kip değişimi / WebView yeniden doğdu). */
    RELOAD,
}

/** JS olay adı → olay. Bilinmeyen (boot, score, crash, selftest…) → null: durum değişmez. */
fun arenaSceneEventOf(type: String): ArenaSceneEvent? = when (type) {
    "ready" -> ArenaSceneEvent.READY
    "start" -> ArenaSceneEvent.START
    "pause" -> ArenaSceneEvent.PAUSE
    "resume" -> ArenaSceneEvent.RESUME
    "gameover" -> ArenaSceneEvent.GAME_OVER
    "nowebgl" -> ArenaSceneEvent.NO_WEBGL
    "error" -> ArenaSceneEvent.ERROR
    else -> null
}

data class ArenaSceneRun(
    val game: ArenaGameState = ArenaGameState.LOADING,
    val tabVisible: Boolean = true,
    val appResumed: Boolean = true,
) {
    /** rAF döngüsü yalnız sekme görünür VE uygulama önde iken döner. */
    val rendering: Boolean get() = tabVisible && appResumed
}

/**
 * Saf geçiş fonksiyonu.
 *
 * Kurallar:
 *  - Sekme gizlenir / uygulama arka plana düşerse koşan yarış PAUSED olur
 *    (kabuk aynı anda `outrun.pause()` gönderir) — dönüşte skor/konum korunur,
 *    yarış duraklatma ekranında bekler; "Devam" kullanıcının dokunuşudur.
 *  - Geri tuşu koşan yarışta ÖNCE duraklatır; duraklatılmışken geri artık
 *    sahneye gelmez (kabuğun çıkış akışı işler).
 *  - Hata/WebGL yok son durumdur; yalnız RELOAD çıkarır.
 */
fun arenaSceneReduce(s: ArenaSceneRun, e: ArenaSceneEvent): ArenaSceneRun {
    val failed = s.game == ArenaGameState.NO_WEBGL || s.game == ArenaGameState.ERROR
    return when (e) {
        ArenaSceneEvent.RELOAD -> s.copy(game = ArenaGameState.LOADING)
        ArenaSceneEvent.NO_WEBGL -> s.copy(game = ArenaGameState.NO_WEBGL)
        ArenaSceneEvent.ERROR -> s.copy(game = ArenaGameState.ERROR)
        ArenaSceneEvent.TAB_HIDDEN -> pauseIfRunning(s.copy(tabVisible = false))
        ArenaSceneEvent.APP_PAUSED -> pauseIfRunning(s.copy(appResumed = false))
        ArenaSceneEvent.TAB_SHOWN -> s.copy(tabVisible = true)
        ArenaSceneEvent.APP_RESUMED -> s.copy(appResumed = true)
        else -> if (failed) s else when (e) {
            ArenaSceneEvent.READY ->
                if (s.game == ArenaGameState.LOADING) s.copy(game = ArenaGameState.MENU) else s
            ArenaSceneEvent.START -> s.copy(game = ArenaGameState.RUNNING)
            ArenaSceneEvent.PAUSE, ArenaSceneEvent.BACK -> pauseIfRunning(s)
            ArenaSceneEvent.RESUME ->
                if (s.game == ArenaGameState.PAUSED) s.copy(game = ArenaGameState.RUNNING) else s
            ArenaSceneEvent.GAME_OVER -> s.copy(game = ArenaGameState.OVER)
            else -> s
        }
    }
}

private fun pauseIfRunning(s: ArenaSceneRun): ArenaSceneRun =
    if (s.game == ArenaGameState.RUNNING) s.copy(game = ArenaGameState.PAUSED) else s

/** Geri tuşunu sahne mi tüketir: yalnız Outrun'da ve yarış koşarken (önce duraklat). */
fun arenaBackConsumed(kind: ArenaSceneKind, s: ArenaSceneRun): Boolean =
    kind == ArenaSceneKind.OUTRUN && s.game == ArenaGameState.RUNNING

/**
 * Geçişin WebView'e yansıması: `prev → next` için gönderilecek JS komutları (sırayla).
 * Duraklatma render kapanmadan ÖNCE gönderilir: son kare duraklatma ekranını göstersin.
 */
fun arenaSceneCommands(kind: ArenaSceneKind, prev: ArenaSceneRun, next: ArenaSceneRun): List<String> {
    val out = ArrayList<String>(2)
    if (kind == ArenaSceneKind.OUTRUN &&
        prev.game == ArenaGameState.RUNNING && next.game == ArenaGameState.PAUSED
    ) out += outrunJsPause()
    if (prev.rendering != next.rendering) out += arenaJsActive(kind, next.rendering)
    return out
}

/** Oyun durumu → sahne durumu (geri düşme kararı mevcut [arenaSceneFallbackReason] ile). */
fun arenaSceneStatusOf(game: ArenaGameState): ArenaSceneStatus = when (game) {
    ArenaGameState.LOADING -> ArenaSceneStatus.LOADING
    ArenaGameState.NO_WEBGL -> ArenaSceneStatus.NO_WEBGL
    ArenaGameState.ERROR -> ArenaSceneStatus.ERROR
    else -> ArenaSceneStatus.READY
}
