package com.hermes.mobile

import com.hermes.mobile.data.ArenaAnswer
import com.hermes.mobile.data.ArenaMode
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.ui.ARENA_SCENE_TIMEOUT_MS
import com.hermes.mobile.ui.ArenaFigure
import com.hermes.mobile.ui.ArenaFigureState
import com.hermes.mobile.ui.ArenaSceneFallbackReason
import com.hermes.mobile.ui.ArenaSceneJson
import com.hermes.mobile.ui.ArenaSceneLabels
import com.hermes.mobile.ui.ArenaScenePhase
import com.hermes.mobile.ui.ArenaSceneStatus
import com.hermes.mobile.ui.arenaFigureId
import com.hermes.mobile.ui.arenaLiveFigures
import com.hermes.mobile.ui.arenaLiveName
import com.hermes.mobile.ui.arenaRoundBadge
import com.hermes.mobile.ui.arenaSceneFallbackReason
import com.hermes.mobile.ui.arenaSceneFigures
import com.hermes.mobile.ui.arenaScenePhase
import com.hermes.mobile.ui.arenaSynthFigureId
import com.hermes.mobile.ui.arenaUseCardFallback
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-9 — Arena 3D sahnesi (three.js) saf katman testleri.
 *
 * Render katmanı (WebGL) birim testle kanıtlanamaz; burada KARAR kilitlenir:
 * faz eşlemesi, figür/durum/rozet, JSON köprüsü ve zarif geri düşme kararı.
 */
class ArenaSceneTest {

    private val tr = ArenaSceneLabels.TR

    private fun running(figures: List<ArenaFigure>) = ArenaState(
        selectedProfiles = listOf("alfa", "beta"),
        phase = ArenaPhase.Running(ArenaMode.BATTLE),
        figures = figures,
    )

    // ── 1. Faz eşlemesi ────────────────────────────────────────────────────

    @Test
    fun idleFazi() {
        assertEquals(ArenaScenePhase.IDLE, arenaScenePhase(ArenaState()))
    }

    @Test
    fun readyFazi() {
        val st = ArenaState(phase = ArenaPhase.Ready, selectedProfiles = listOf("alfa"))
        assertEquals(ArenaScenePhase.READY, arenaScenePhase(st))
    }

    @Test
    fun runningFazi() {
        assertEquals(ArenaScenePhase.RUNNING, arenaScenePhase(running(emptyList())))
    }

    @Test
    fun doneFazi() {
        val st = ArenaState(phase = ArenaPhase.Done(listOf(ArenaAnswer("alfa", 1, "x")), null, ArenaMode.SINGLE))
        assertEquals(ArenaScenePhase.DONE, arenaScenePhase(st))
    }

    @Test
    fun durduruluncaStoppedFazi() {
        val st = ArenaState(
            phase = ArenaPhase.Done(emptyList(), null, ArenaMode.SINGLE),
            error = "Durduruldu",
            stopped = true,
        )
        assertEquals(ArenaScenePhase.STOPPED, arenaScenePhase(st))
        assertNotEquals("Durdurma, hata bitişiyle karışmamalı", ArenaScenePhase.DONE, arenaScenePhase(st))
    }

    // ── 2. Figür eşlemesi ──────────────────────────────────────────────────

    @Test
    fun readyFazindaSeciliBotlarBekliyor() {
        val st = ArenaState(phase = ArenaPhase.Ready, selectedProfiles = listOf("alfa", "beta"))
        val figs = arenaSceneFigures(st, emptyList(), tr)
        assertEquals(2, figs.size)
        assertTrue(figs.all { it.state == ArenaFigureState.WAITING })
        assertEquals(listOf("alfa", "beta"), figs.map { it.name })
        assertEquals(arenaFigureId("alfa", 1), figs.first().id)
    }

    @Test
    fun runningFazindaIzlenenFigurlerAynen() {
        val tracked = listOf(
            ArenaFigure(arenaFigureId("alfa", 1), "alfa", ArenaFigureState.DONE, tr.round1),
            ArenaFigure(arenaFigureId("beta", 1), "beta", ArenaFigureState.WORKING, tr.round1),
        )
        assertEquals(tracked, arenaSceneFigures(running(tracked), emptyList(), tr))
    }

    @Test
    fun doneFazindaOkHata() {
        val answers = listOf(
            ArenaAnswer("alfa", 1, "cevap"),
            ArenaAnswer("beta", 1, "", ok = false, error = "zaman aşımı"),
        )
        val st = ArenaState(phase = ArenaPhase.Done(answers, null, ArenaMode.SINGLE))
        val figs = arenaSceneFigures(st, emptyList(), tr)
        assertEquals(ArenaFigureState.DONE, figs[0].state)
        assertEquals(ArenaFigureState.ERROR, figs[1].state)
        assertEquals(tr.round1, figs[0].badge)
    }

    @Test
    fun kapismadaTurRozetleri() {
        val answers = listOf(
            ArenaAnswer("alfa", 1, "t1"),
            ArenaAnswer("beta", 1, "t1"),
            ArenaAnswer("alfa", 2, "t2"),
            ArenaAnswer("beta", 2, "t2"),
        )
        val st = ArenaState(phase = ArenaPhase.Done(answers, null, ArenaMode.BATTLE))
        val figs = arenaSceneFigures(st, emptyList(), tr)
        assertEquals(4, figs.size)
        assertEquals(listOf(tr.round1, tr.round1, tr.round2, tr.round2), figs.map { it.badge })
        assertEquals(arenaFigureId("alfa", 2), figs[2].id)
    }

    @Test
    fun beyinFirtinasindaSentezRozetiVeKimligi() {
        val ideas = listOf(
            ArenaAnswer("alfa", 1, "fikir"),
            ArenaAnswer("beta", 1, "fikir"),
        )
        val synth = ArenaAnswer("alfa", 2, "final")
        val answers = ideas + synth
        val st = ArenaState(phase = ArenaPhase.Done(answers, synth, ArenaMode.BRAINSTORM))
        val figs = arenaSceneFigures(st, emptyList(), tr)
        assertEquals(3, figs.size)
        assertEquals(tr.synthesis, figs.last().badge)
        assertEquals(arenaSynthFigureId("alfa"), figs.last().id)
        assertEquals(tr.round1, figs.first().badge)
    }

    @Test
    fun durdurulanKosudaFigurlerSilinmezSakineDoner() {
        val tracked = listOf(
            ArenaFigure(arenaFigureId("alfa", 1), "alfa", ArenaFigureState.DONE, tr.round1),
            ArenaFigure(arenaFigureId("beta", 1), "beta", ArenaFigureState.WORKING, tr.round1),
        )
        val st = ArenaState(
            phase = ArenaPhase.Done(emptyList(), null, ArenaMode.SINGLE),
            error = "Durduruldu",
            stopped = true,
            figures = tracked,
        )
        val figs = arenaSceneFigures(st, emptyList(), tr)
        assertEquals("Figürler kaybolmamalı", 2, figs.size)
        assertTrue("Durdurunca hepsi sakin beklemeye döner", figs.all { it.state == ArenaFigureState.WAITING })
        assertEquals(listOf("alfa", "beta"), figs.map { it.name })
    }

    @Test
    fun cevapsizHataBitisindeFigurlerKesintiyeUgrar() {
        val tracked = listOf(ArenaFigure(arenaFigureId("alfa", 1), "alfa", ArenaFigureState.WORKING, tr.round1))
        val st = ArenaState(
            phase = ArenaPhase.Done(emptyList(), null, ArenaMode.SINGLE),
            error = "AI yanıt vermedi",
            figures = tracked,
        )
        val figs = arenaSceneFigures(st, emptyList(), tr)
        assertEquals(ArenaFigureState.ERROR, figs.single().state)
    }

    // ── 3. Boşta canlı oturumlar (LiveSessions kaynağı) ─────────────────────

    @Test
    fun idleFazindaCanliOturumlarFigurOlur() {
        val live = listOf(
            LiveSession(id = "s1", title = "hermes-ustasi-devriye", status = "working", sessionKey = "k1"),
            LiveSession(id = "s2", title = "Greeting", status = "idle", sessionKey = "k2"),
        )
        val figs = arenaSceneFigures(ArenaState(), live, tr)
        assertEquals(2, figs.size)
        assertEquals(ArenaFigureState.WORKING, figs[0].state)
        assertEquals(ArenaFigureState.WAITING, figs[1].state)
        assertTrue("Canlı rozeti taşınmalı", figs.all { it.badge == tr.live })
        assertEquals("live:k1", figs[0].id)
    }

    @Test
    fun idleFazindaCanliVeriYoksaSahneBos() {
        assertTrue(arenaSceneFigures(ArenaState(), emptyList(), tr).isEmpty())
    }

    @Test
    fun canliOturumlarDbIdIleTekillesirVeSekizleSinirlanir() {
        val dup = List(3) { LiveSession(id = "s$it", title = "t$it", sessionKey = "ayni") }
        assertEquals(1, arenaLiveFigures(dup, tr).size)

        val many = (1..12).map { LiveSession(id = "s$it", title = "oturum-$it", sessionKey = "k$it") }
        assertEquals(8, arenaLiveFigures(many, tr).size)
    }

    @Test
    fun canliOturumAdiKirpilirVeBosBaslikKimligeDuser() {
        val long = LiveSession(id = "s1", title = "cok uzun bir oturum basligi buraya", sessionKey = "k1")
        assertTrue(arenaLiveName(long).length <= 28)
        assertTrue(arenaLiveName(long).endsWith("…"))

        val empty = LiveSession(id = "s2", title = "   ", sessionKey = "k2")
        assertEquals("s2", arenaLiveName(empty))
    }

    @Test
    fun arenaKosusuVarkenCanliOturumlarSahneyeKarismaz() {
        val live = listOf(LiveSession(id = "s1", title = "canli", status = "working", sessionKey = "k1"))
        val tracked = listOf(ArenaFigure(arenaFigureId("alfa", 1), "alfa", ArenaFigureState.WORKING, tr.round1))
        val figs = arenaSceneFigures(running(tracked), live, tr)
        assertEquals(1, figs.size)
        assertEquals("alfa", figs.single().name)
    }

    // ── 4. Kimlik kararlılığı ve rozet metni ───────────────────────────────

    @Test
    fun figurKimlikleriKararli() {
        assertEquals("alfa#r1", arenaFigureId("alfa", 1))
        assertEquals("alfa#r2", arenaFigureId("alfa", 2))
        assertEquals("alfa#synth", arenaSynthFigureId("alfa"))
    }

    @Test
    fun runningdenDoneaKimliklerKorunur() {
        val tracked = listOf(
            ArenaFigure(arenaFigureId("alfa", 1), "alfa", ArenaFigureState.WORKING, tr.round1),
            ArenaFigure(arenaFigureId("beta", 1), "beta", ArenaFigureState.WORKING, tr.round1),
        )
        val answers = listOf(ArenaAnswer("alfa", 1, "a"), ArenaAnswer("beta", 1, "b"))
        val st = ArenaState(
            phase = ArenaPhase.Done(answers, null, ArenaMode.SINGLE),
            figures = tracked,
        )
        val doneIds = arenaSceneFigures(st, emptyList(), tr).map { it.id }.toSet()
        assertEquals("Kimlikler korunmalı (figür yeniden doğmaz)", setOf("alfa#r1", "beta#r1"), doneIds)
    }

    @Test
    fun turRozetiDileGore() {
        assertEquals("Tur 2", arenaRoundBadge(2, ArenaSceneLabels.TR))
        assertEquals("Round 2", arenaRoundBadge(2, ArenaSceneLabels.EN))
        assertEquals("Sentez", ArenaSceneLabels.TR.synthesis)
        assertEquals("Canlı", ArenaSceneLabels.TR.live)
    }

    // ── 5. JSON köprüsü ────────────────────────────────────────────────────

    @Test
    fun jsonGecerliVeAlanlarDogru() {
        val figs = listOf(
            ArenaFigure("alfa#r1", "alfa", ArenaFigureState.WORKING, "Tur 2"),
            ArenaFigure("beta#r1", "beta", ArenaFigureState.WAITING, null),
        )
        val json = ArenaSceneJson.encode(ArenaScenePhase.RUNNING, figs)
        val root = Json.parseToJsonElement(json).jsonObject
        assertEquals("running", root["phase"]!!.jsonPrimitive.content)

        val arr = root["figures"]!!.jsonArray
        assertEquals(2, arr.size)
        val f0 = arr[0].jsonObject
        assertEquals("alfa#r1", f0["id"]!!.jsonPrimitive.content)
        assertEquals("alfa", f0["name"]!!.jsonPrimitive.content)
        assertEquals("working", f0["state"]!!.jsonPrimitive.content)
        assertEquals("Tur 2", f0["badge"]!!.jsonPrimitive.content)

        val f1 = arr[1].jsonObject
        assertEquals("waiting", f1["state"]!!.jsonPrimitive.content)
        assertTrue("Rozetsiz figürde badge null olmalı", f1["badge"] is JsonNull)

        val theme = root["theme"]!!.jsonObject
        assertTrue(theme["bg"]!!.jsonPrimitive.content.startsWith("#"))
    }

    @Test
    fun jsonBosFigurListesiGecerli() {
        val root = Json.parseToJsonElement(
            ArenaSceneJson.encode(ArenaScenePhase.IDLE, emptyList()),
        ).jsonObject
        assertEquals("idle", root["phase"]!!.jsonPrimitive.content)
        assertEquals(0, root["figures"]!!.jsonArray.size)
    }

    @Test
    fun jsonKacislari() {
        assertEquals("a\\\"b", ArenaSceneJson.str("a\"b"))
        assertEquals("a\\\\b", ArenaSceneJson.str("a\\b"))
        assertEquals("a\\nb", ArenaSceneJson.str("a\nb"))
        assertEquals("a\\u0007b", ArenaSceneJson.str("a\u0007b"))
        // Türkçe karakterler JSON'da ham kalır (JS katmanı ASCII'ye çevirir).
        assertEquals("Canlı", ArenaSceneJson.str("Canlı"))
    }

    @Test
    fun jsonKacisliMetinYenidenAyniOkunur() {
        val ad = "bot \"x\" \\ y"
        val json = ArenaSceneJson.encode(
            ArenaScenePhase.RUNNING,
            listOf(ArenaFigure("id", ad, ArenaFigureState.DONE, "Tur 1")),
        )
        val root = Json.parseToJsonElement(json).jsonObject
        assertEquals(ad, root["figures"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun jsCagrisiSahnedeSetDataCagirir() {
        val json = ArenaSceneJson.encode(
            ArenaScenePhase.RUNNING,
            listOf(ArenaFigure("alfa#r1", "alfa", ArenaFigureState.WORKING, "Tur 1")),
        )
        val call = ArenaSceneJson.jsCall(json)
        assertTrue(call.startsWith("window.arenaScene&&window.arenaScene.setData(\""))
        assertTrue(call.endsWith("\")"))
        assertTrue("Gövde JS string olarak kaçırılmalı", call.contains("\\\"phase\\\""))
    }

    @Test
    fun jsCagrisiAsciiDisiKarakterIceremez() {
        val json = ArenaSceneJson.encode(
            ArenaScenePhase.IDLE,
            listOf(ArenaFigure("live:x", "Canlı oturum", ArenaFigureState.WORKING, "Canlı")),
        )
        val call = ArenaSceneJson.jsCall(json)
        assertTrue("Köprü gövdesi ASCII olmalı", call.all { it.code in 0x20..0x7e })
        assertTrue("Türkçe 'ı' \\u0131 olarak kaçmalı", call.contains("\\u0131"))
    }

    @Test
    fun jsKacisiTirnakVeTersBoluIkiKezKacirir() {
        assertEquals("a\\\\\\\"b", ArenaSceneJson.jsEscape("a\\\"b"))
        assertEquals("a\\nb", ArenaSceneJson.jsEscape("a\nb"))
        assertEquals("\\u2028", ArenaSceneJson.jsEscape("\u2028"))
    }

    @Test
    fun aktiflikKomutlari() {
        assertEquals("window.arenaScene&&window.arenaScene.setActive(false)", ArenaSceneJson.jsActive(false))
        assertEquals("window.arenaScene&&window.arenaScene.setActive(true)", ArenaSceneJson.jsActive(true))
    }

    // ── 6. Zarif geri düşme kararı ─────────────────────────────────────────

    @Test
    fun sahneHazirsaKartListesineDusulmez() {
        assertEquals(
            ArenaSceneFallbackReason.NONE,
            arenaSceneFallbackReason(ArenaSceneStatus.READY, 9_999),
        )
        assertFalse(arenaUseCardFallback(ArenaSceneFallbackReason.NONE))
    }

    @Test
    fun webglYoksaKartListesi() {
        val r = arenaSceneFallbackReason(ArenaSceneStatus.NO_WEBGL, 10)
        assertEquals(ArenaSceneFallbackReason.NO_WEBGL, r)
        assertTrue(arenaUseCardFallback(r))
    }

    @Test
    fun jsHatasindaKartListesi() {
        val r = arenaSceneFallbackReason(ArenaSceneStatus.ERROR, 10)
        assertEquals(ArenaSceneFallbackReason.SCRIPT_ERROR, r)
        assertTrue(arenaUseCardFallback(r))
    }

    @Test
    fun zamanAsimindaKartListesi() {
        assertEquals(
            ArenaSceneFallbackReason.TIMEOUT,
            arenaSceneFallbackReason(ArenaSceneStatus.LOADING, ARENA_SCENE_TIMEOUT_MS),
        )
        assertEquals(
            "Sinir aninda henuz karar verilmez",
            ArenaSceneFallbackReason.NONE,
            arenaSceneFallbackReason(ArenaSceneStatus.LOADING, ARENA_SCENE_TIMEOUT_MS - 1),
        )
    }

    @Test
    fun yuklenirkenHenuzKararYok() {
        assertEquals(
            ArenaSceneFallbackReason.NONE,
            arenaSceneFallbackReason(ArenaSceneStatus.LOADING, 0),
        )
    }

    @Test
    fun sahneHazirOluncaKartListesiGeriCekilir() {
        // Zaman aşımına düşmüş olsa bile 'ready' gelirse sahne gösterilir.
        assertEquals(
            ArenaSceneFallbackReason.NONE,
            arenaSceneFallbackReason(ArenaSceneStatus.READY, ARENA_SCENE_TIMEOUT_MS * 3),
        )
    }
}
