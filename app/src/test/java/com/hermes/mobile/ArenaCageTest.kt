package com.hermes.mobile

import com.hermes.mobile.ui.ARENA_ASSET_ROOT
import com.hermes.mobile.ui.ArenaSceneJson
import com.hermes.mobile.ui.ArenaSceneKind
import com.hermes.mobile.ui.CAGE_SCENE_TIMEOUT_MS
import com.hermes.mobile.ui.RaceBotPulse
import com.hermes.mobile.ui.arenaSceneUrl
import com.hermes.mobile.ui.raceDriveJs
import com.hermes.mobile.ui.raceLane
import com.hermes.mobile.ui.racePass
import com.hermes.mobile.ui.raceSpeedPct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-20: Arena "Kafes dövüşü" kipi + Outrun yarış veri sürücüsü —
 * saf karar katmanı (render test edilemez, KARAR test edilir — tur9/15 dersi).
 */
class ArenaCageTest {

    // ── kip kaydı ───────────────────────────────────────────────────────────

    @Test
    fun cageKindIsRegistered() {
        assertEquals("cage", ArenaSceneKind.fromId("cage").id)
        assertEquals("cage.html", ArenaSceneKind.CAGE.page)
        assertEquals("cage", ArenaSceneKind.CAGE.jsObject)
    }

    @Test
    fun cageUrlStaysInAssetRoot() {
        val url = arenaSceneUrl(ArenaSceneKind.CAGE)
        assertEquals(ARENA_ASSET_ROOT + "cage.html", url)
        assertTrue(com.hermes.mobile.ui.arenaAssetAllowed(url))
    }

    @Test
    fun cageTimeoutMatchesHeavySceneBudget() {
        // Çok nesneli üç.js sahnesi: 12 sn bütçe (tur15 ölçümüyle aynı gerekçe).
        assertEquals(12_000L, CAGE_SCENE_TIMEOUT_MS)
        assertEquals(12_000L, ArenaSceneKind.CAGE.timeoutMs)
    }

    @Test
    fun workRemainsDefaultKind() {
        // Eski kalıcı kayıtlar: default ve fromId toleransı değişmemeli.
        assertEquals(ArenaSceneKind.WORK, ArenaSceneKind.DEFAULT)
        assertEquals(ArenaSceneKind.WORK, ArenaSceneKind.fromId("cag"))
    }

    @Test
    fun jsCallForTargetsPerKind() {
        val cage = ArenaSceneJson.jsCallFor(ArenaSceneKind.CAGE, "{}")
        assertTrue(cage.startsWith("window.cage&&window.cage.setData(\""))
        val work = ArenaSceneJson.jsCall("{}")
        assertTrue(work.startsWith("window.arenaScene&&window.arenaScene.setData(\""))
        val outrun = ArenaSceneJson.jsCallFor(ArenaSceneKind.OUTRUN, "{}")
        assertTrue(outrun.startsWith("window.outrun&&window.outrun.setData(\""))
    }

    @Test
    fun jsActiveIsPerKind() {
        assertEquals(
            "window.cage&&window.cage.setActive(false)",
            ArenaSceneJson.jsActive(ArenaSceneKind.CAGE, false),
        )
        assertEquals(
            "window.arenaScene&&window.arenaScene.setActive(true)",
            ArenaSceneJson.jsActive(ArenaSceneKind.WORK, true),
        )
    }

    // ── hız ölçeği ──────────────────────────────────────────────────────────

    @Test
    fun speedScalesWithCharsPerSec() {
        assertEquals(0.35, raceSpeedPct(0.0, working = true), 1e-9)
        assertEquals(1.25, raceSpeedPct(140.0, working = true), 1e-9)
        assertEquals(1.25, raceSpeedPct(9999.0, working = true), 1e-9) // tavan
        assertTrue(raceSpeedPct(70.0, working = true) > raceSpeedPct(20.0, working = true))
    }

    @Test
    fun idleBotCrawlsAtFloor() {
        assertEquals(0.28, raceSpeedPct(9999.0, working = false), 1e-9)
    }

    @Test
    fun speedNeverNegativeOrNaN() {
        assertTrue(raceSpeedPct(-5.0, working = true) >= 0.28)
        assertTrue(raceSpeedPct(Double.NaN, working = true) >= 0.28)
        assertFalse(raceSpeedPct(Double.POSITIVE_INFINITY, working = true).isNaN())
    }

    // ── şerit ───────────────────────────────────────────────────────────────

    @Test
    fun laneIsDeterministicAndBounded() {
        val a = raceLane(1234L, "coder#r1")
        val b = raceLane(1234L, "coder#r1")
        assertEquals(a, b, 1e-12)
        for (t in 0L until 20_000L step 250L) {
            val v = raceLane(t, "coder#r1")
            assertTrue("serit siniri asildi: $v", v >= -2.6001 && v <= 2.6001)
        }
    }

    @Test
    fun laneDiffersPerId() {
        // Farklı hash fazı: aynı anda farklı şeritler (en az bir an ayrışmalı).
        var diff = false
        for (t in 0L until 20_000L step 250L) {
            if (kotlin.math.abs(raceLane(t, "coder#r1") - raceLane(t, "android#r1")) > 0.05) {
                diff = true; break
            }
        }
        assertTrue(diff)
    }

    // ── geçiş anı ───────────────────────────────────────────────────────────

    @Test
    fun passWindowExpires() {
        val p = RaceBotPulse(id = "a", charsPerSec = 10.0, working = true, passUntilMs = 1_000L)
        assertTrue(racePass(p, 900L))
        assertFalse(racePass(p, 1_100L))
    }

    // ── setDrive komutu ─────────────────────────────────────────────────────

    @Test
    fun driveCommandTargetsFastestWorkingBot() {
        val pulses = listOf(
            RaceBotPulse("a", 40.0, working = true),
            RaceBotPulse("b", 80.0, working = true),
        )
        val js = raceDriveJs(pulses, 500L)!!
        assertTrue(js.startsWith("window.outrun&&window.outrun.setDrive({"))
        // en hızlısı b: hiz = 0.35 + 80/140 = 0.921 (3 ondalık)
        assertTrue("hiz beklendi: $js", js.contains("hiz:0.921"))
        assertTrue(js.contains("gecen:false"))
    }

    @Test
    fun driveCommandFlagsPass() {
        val pulses = listOf(
            RaceBotPulse("a", 40.0, working = true, passUntilMs = 2_000L),
        )
        val js = raceDriveJs(pulses, 1_500L)!!
        assertTrue(js.contains("gecen:true"))
    }

    @Test
    fun driveCommandNullWhenNoWorkingBot() {
        assertNull(raceDriveJs(listOf(RaceBotPulse("a", 40.0, working = false)), 1L))
        assertNull(raceDriveJs(emptyList(), 1L))
    }

    @Test
    fun driveCommandIsAsciiSafe() {
        val js = raceDriveJs(listOf(RaceBotPulse("alfa#r1", 55.0, working = true)), 77L)!!
        assertTrue(js.all { it.code in 0x20..0x7e })
    }
}
