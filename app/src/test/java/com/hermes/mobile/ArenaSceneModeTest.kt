package com.hermes.mobile

import com.hermes.mobile.data.AppSettings
import com.hermes.mobile.ui.ARENA_ASSET_ROOT
import com.hermes.mobile.ui.ARENA_SCENE_TIMEOUT_MS
import com.hermes.mobile.ui.ARENA_SCENE_URL
import com.hermes.mobile.ui.ArenaGameState
import com.hermes.mobile.ui.ArenaSceneEvent
import com.hermes.mobile.ui.ArenaSceneFallbackReason
import com.hermes.mobile.ui.ArenaSceneKind
import com.hermes.mobile.ui.ArenaSceneRun
import com.hermes.mobile.ui.ArenaSceneStatus
import com.hermes.mobile.ui.OUTRUN_SCENE_TIMEOUT_MS
import com.hermes.mobile.ui.arenaAssetAllowed
import com.hermes.mobile.ui.arenaAssetPath
import com.hermes.mobile.ui.arenaBackConsumed
import com.hermes.mobile.ui.arenaJsActive
import com.hermes.mobile.ui.arenaSceneCommands
import com.hermes.mobile.ui.arenaSceneEventOf
import com.hermes.mobile.ui.arenaSceneFallbackReason
import com.hermes.mobile.ui.arenaSceneModeValue
import com.hermes.mobile.ui.arenaSceneReduce
import com.hermes.mobile.ui.arenaSceneStatusOf
import com.hermes.mobile.ui.arenaSceneUrl
import com.hermes.mobile.ui.outrunJsPause
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tur-15: Arena sahne kipi (İş sahnesi | Outrun yarış) — saf karar katmanı. */
class ArenaSceneModeTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun run(vararg events: ArenaSceneEvent, from: ArenaSceneRun = ArenaSceneRun()) =
        events.fold(from) { s, e -> arenaSceneReduce(s, e) }

    // ── Kip seçimi + kalıcılık ──────────────────────────────────────────────

    @Test
    fun defaultModeIsWorkScene() {
        assertEquals(ArenaSceneKind.WORK, ArenaSceneKind.DEFAULT)
        assertEquals("work", AppSettings().arenaSceneMode)
        assertEquals(ArenaSceneKind.WORK, ArenaSceneKind.fromId(AppSettings().arenaSceneMode))
    }

    @Test
    fun fromIdToleratesUnknownBlankAndCase() {
        assertEquals(ArenaSceneKind.OUTRUN, ArenaSceneKind.fromId("outrun"))
        assertEquals(ArenaSceneKind.OUTRUN, ArenaSceneKind.fromId(" OUTRUN "))
        assertEquals(ArenaSceneKind.WORK, ArenaSceneKind.fromId(null))
        assertEquals(ArenaSceneKind.WORK, ArenaSceneKind.fromId(""))
        assertEquals(ArenaSceneKind.WORK, ArenaSceneKind.fromId("drift"))
    }

    @Test
    fun persistedIdsAreStable() {
        // Kalıcı kimlikler eski kayıtları okur — değişirse bu test kırılmalı.
        assertEquals("work", arenaSceneModeValue(ArenaSceneKind.WORK))
        assertEquals("outrun", arenaSceneModeValue(ArenaSceneKind.OUTRUN))
        ArenaSceneKind.entries.forEach {
            assertEquals(it, ArenaSceneKind.fromId(arenaSceneModeValue(it)))
        }
    }

    @Test
    fun selectionSurvivesSettingsJsonRoundTrip() {
        val saved = AppSettings().copy(arenaSceneMode = arenaSceneModeValue(ArenaSceneKind.OUTRUN))
        val back = json.decodeFromString<AppSettings>(json.encodeToString(saved))
        assertEquals(ArenaSceneKind.OUTRUN, ArenaSceneKind.fromId(back.arenaSceneMode))
    }

    @Test
    fun oldSettingsWithoutFieldFallBackToWork() {
        val old = json.decodeFromString<AppSettings>("{}")
        assertEquals(ArenaSceneKind.WORK, ArenaSceneKind.fromId(old.arenaSceneMode))
    }

    // ── Varlık yolu + URL ──────────────────────────────────────────────────

    @Test
    fun assetPathAcceptsPlainFileNamesOnly() {
        assertEquals("file:///android_asset/arena/outrun.html", arenaAssetPath("outrun.html"))
        assertNull(arenaAssetPath(""))
        assertNull(arenaAssetPath("../secret.html"))
        assertNull(arenaAssetPath("sub/x.html"))
        assertNull(arenaAssetPath("a\\b.html"))
        assertNull(arenaAssetPath("https://evil.example/x.html"))
        assertNull(arenaAssetPath(".hidden"))
    }

    @Test
    fun sceneUrlsForBothKindsShareOneRoot() {
        assertEquals("file:///android_asset/arena/arena3d.html", arenaSceneUrl(ArenaSceneKind.WORK))
        assertEquals("file:///android_asset/arena/outrun.html", arenaSceneUrl(ArenaSceneKind.OUTRUN))
        assertEquals(arenaSceneUrl(ArenaSceneKind.WORK), ARENA_SCENE_URL)
        ArenaSceneKind.entries.forEach { assertTrue(arenaSceneUrl(it).startsWith(ARENA_ASSET_ROOT)) }
    }

    @Test
    fun sceneUrlParamsAreSortedAndEncoded() {
        val url = arenaSceneUrl(ArenaSceneKind.OUTRUN, mapOf("z" to "1", "fps" to "a b&c", " " to "x"))
        assertEquals("file:///android_asset/arena/outrun.html?fps=a%20b%26c&z=1", url)
        assertEquals(arenaSceneUrl(ArenaSceneKind.OUTRUN), arenaSceneUrl(ArenaSceneKind.OUTRUN, mapOf(" " to "x")))
    }

    @Test
    fun requestFilterAllowsOnlyArenaAssets() {
        assertTrue(arenaAssetAllowed("file:///android_asset/arena/three.min.js"))
        assertTrue(arenaAssetAllowed("file:///android_asset/arena/outrun.js?v=1"))
        assertFalse(arenaAssetAllowed(null))
        assertFalse(arenaAssetAllowed("file:///android_asset/arena/"))
        assertFalse(arenaAssetAllowed("file:///android_asset/arena/../index.html"))
        assertFalse(arenaAssetAllowed("file:///android_asset/arena/%2e%2e/index.html"))
        assertFalse(arenaAssetAllowed("https://cdn.example/three.min.js"))
        assertFalse(arenaAssetAllowed("file:///data/data/com.hermes.mobile/x"))
    }

    // ── JS olayları + komutlar ─────────────────────────────────────────────

    @Test
    fun jsEventNamesMapAndUnknownsAreIgnored() {
        assertEquals(ArenaSceneEvent.READY, arenaSceneEventOf("ready"))
        assertEquals(ArenaSceneEvent.GAME_OVER, arenaSceneEventOf("gameover"))
        assertEquals(ArenaSceneEvent.PAUSE, arenaSceneEventOf("pause"))
        assertNull(arenaSceneEventOf("score"))
        assertNull(arenaSceneEventOf("boot"))
        assertNull(arenaSceneEventOf("crash"))
    }

    @Test
    fun jsCommandsTargetTheRightObject() {
        assertEquals("window.outrun&&window.outrun.setActive(false)", arenaJsActive(ArenaSceneKind.OUTRUN, false))
        assertEquals("window.arenaScene&&window.arenaScene.setActive(true)", arenaJsActive(ArenaSceneKind.WORK, true))
        assertEquals("window.outrun&&window.outrun.pause()", outrunJsPause())
    }

    @Test
    fun outrunTimeoutIsLongerThanWorkScene() {
        assertEquals(ARENA_SCENE_TIMEOUT_MS, ArenaSceneKind.WORK.timeoutMs)
        assertEquals(OUTRUN_SCENE_TIMEOUT_MS, ArenaSceneKind.OUTRUN.timeoutMs)
        assertTrue(OUTRUN_SCENE_TIMEOUT_MS > ARENA_SCENE_TIMEOUT_MS)
        // İş sahnesi süresi geçmiş ama Outrun süresi dolmamışsa yarış geri düşmez.
        val elapsed = ARENA_SCENE_TIMEOUT_MS + 1
        assertEquals(
            ArenaSceneFallbackReason.TIMEOUT,
            arenaSceneFallbackReason(ArenaSceneStatus.LOADING, elapsed, ARENA_SCENE_TIMEOUT_MS),
        )
        assertEquals(
            ArenaSceneFallbackReason.NONE,
            arenaSceneFallbackReason(ArenaSceneStatus.LOADING, elapsed, OUTRUN_SCENE_TIMEOUT_MS),
        )
    }

    // ── Yaşam döngüsü durum makinesi ───────────────────────────────────────

    @Test
    fun readyStartGameOverFlow() {
        assertEquals(ArenaGameState.MENU, run(ArenaSceneEvent.READY).game)
        assertEquals(ArenaGameState.RUNNING, run(ArenaSceneEvent.READY, ArenaSceneEvent.START).game)
        assertEquals(
            ArenaGameState.OVER,
            run(ArenaSceneEvent.READY, ArenaSceneEvent.START, ArenaSceneEvent.GAME_OVER).game,
        )
        // İkinci ready menüye geri atmaz.
        assertEquals(
            ArenaGameState.RUNNING,
            run(ArenaSceneEvent.READY, ArenaSceneEvent.START, ArenaSceneEvent.READY).game,
        )
    }

    @Test
    fun tabHideDuringRacePausesAndStopsRenderingThenShowKeepsPause() {
        val racing = run(ArenaSceneEvent.READY, ArenaSceneEvent.START)
        val hidden = arenaSceneReduce(racing, ArenaSceneEvent.TAB_HIDDEN)
        assertEquals(ArenaGameState.PAUSED, hidden.game)
        assertFalse(hidden.rendering)
        assertEquals(
            listOf(outrunJsPause(), arenaJsActive(ArenaSceneKind.OUTRUN, false)),
            arenaSceneCommands(ArenaSceneKind.OUTRUN, racing, hidden),
        )
        val shown = arenaSceneReduce(hidden, ArenaSceneEvent.TAB_SHOWN)
        assertEquals(ArenaGameState.PAUSED, shown.game) // "Devam" kullanıcının dokunuşu
        assertTrue(shown.rendering)
        assertEquals(
            listOf(arenaJsActive(ArenaSceneKind.OUTRUN, true)),
            arenaSceneCommands(ArenaSceneKind.OUTRUN, hidden, shown),
        )
        assertEquals(ArenaGameState.RUNNING, arenaSceneReduce(shown, ArenaSceneEvent.RESUME).game)
    }

    @Test
    fun appPauseInMenuStopsRenderingWithoutPauseCommand() {
        val menu = run(ArenaSceneEvent.READY)
        val bg = arenaSceneReduce(menu, ArenaSceneEvent.APP_PAUSED)
        assertEquals(ArenaGameState.MENU, bg.game)
        assertFalse(bg.rendering)
        assertEquals(
            listOf(arenaJsActive(ArenaSceneKind.OUTRUN, false)),
            arenaSceneCommands(ArenaSceneKind.OUTRUN, menu, bg),
        )
        // Sekme gizli + uygulama dönünce render hâlâ kapalı (ikisi de gerekli).
        val both = run(ArenaSceneEvent.TAB_HIDDEN, ArenaSceneEvent.APP_RESUMED, from = bg)
        assertFalse(both.rendering)
    }

    @Test
    fun backPausesRunningRaceOnceThenReleases() {
        val racing = run(ArenaSceneEvent.READY, ArenaSceneEvent.START)
        assertTrue(arenaBackConsumed(ArenaSceneKind.OUTRUN, racing))
        assertFalse(arenaBackConsumed(ArenaSceneKind.WORK, racing))
        val paused = arenaSceneReduce(racing, ArenaSceneEvent.BACK)
        assertEquals(ArenaGameState.PAUSED, paused.game)
        assertEquals(listOf(outrunJsPause()), arenaSceneCommands(ArenaSceneKind.OUTRUN, racing, paused))
        // Duraklatılmışken ikinci geri sahneye gelmez → kabuğun çıkış akışı.
        assertFalse(arenaBackConsumed(ArenaSceneKind.OUTRUN, paused))
    }

    @Test
    fun failureIsTerminalUntilReload() {
        val failed = run(ArenaSceneEvent.NO_WEBGL)
        assertEquals(ArenaGameState.NO_WEBGL, run(ArenaSceneEvent.READY, ArenaSceneEvent.START, from = failed).game)
        assertEquals(ArenaSceneStatus.NO_WEBGL, arenaSceneStatusOf(failed.game))
        val reloaded = arenaSceneReduce(run(ArenaSceneEvent.ERROR), ArenaSceneEvent.RELOAD)
        assertEquals(ArenaGameState.LOADING, reloaded.game)
        assertEquals(ArenaSceneStatus.LOADING, arenaSceneStatusOf(reloaded.game))
        assertEquals(ArenaSceneStatus.READY, arenaSceneStatusOf(ArenaGameState.PAUSED))
    }

    @Test
    fun workSceneNeverGetsOutrunPauseCommand() {
        val racing = run(ArenaSceneEvent.READY, ArenaSceneEvent.START)
        val hidden = arenaSceneReduce(racing, ArenaSceneEvent.TAB_HIDDEN)
        assertEquals(
            listOf(arenaJsActive(ArenaSceneKind.WORK, false)),
            arenaSceneCommands(ArenaSceneKind.WORK, racing, hidden),
        )
        assertTrue(arenaSceneCommands(ArenaSceneKind.OUTRUN, racing, racing).isEmpty())
    }
}
