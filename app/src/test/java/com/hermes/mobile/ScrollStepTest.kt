package com.hermes.mobile

import com.hermes.mobile.data.FullControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K2 `screen.scroll_step` — momentum'suz içerik kaydırma + bitiş algılama.
 *
 * Kilitlenen davranış: yön İÇERİK yönüdür (`down` = aşağısı = parmak yukarı),
 * adım sabit ve sınırlıdır, jest yavaştır (fling yok), bitiş = içerik
 * kıpırdamadı. `swipe`'ın parmak yönü semantiğiyle karışmamalı.
 */
class ScrollStepTest {

    private val w = 1080
    private val h = 2400

    @Test
    fun `down icerik yonu parmaga cevirilir - parmak yukari kayar`() {
        val g = FullControl.parseScrollStep("down", null, w, h)!!
        assertTrue("jest aşağıdan yukarı olmalı", g.y1 > g.y2)
        assertEquals(w / 2, g.x1)
    }

    @Test
    fun `up icerik yonu parmak asagi kayar`() {
        val g = FullControl.parseScrollStep("up", null, w, h)!!
        assertTrue("jest yukarıdan aşağı olmalı", g.y1 < g.y2)
    }

    @Test
    fun `turkce yon adlari ayni jeste gider`() {
        val down = FullControl.parseScrollStep("Aşağı", null, w, h)!!
        val downAscii = FullControl.parseScrollStep("asagi", null, w, h)!!
        assertEquals(down, downAscii)
        val up = FullControl.parseScrollStep("yukari", null, w, h)!!
        val upTr = FullControl.parseScrollStep("YUKARI", null, w, h)!!
        assertEquals(up, upTr)
    }

    @Test
    fun `adim orani sinirli - asiri istek tavana cekilir`() {
        val small = FullControl.parseScrollStep("down", "0.2", w, h)!!
        val big = FullControl.parseScrollStep("down", "5", w, h)!!
        val distSmall = small.y1 - small.y2
        val distBig = big.y1 - big.y2
        assertTrue(distSmall < distBig)
        // Tavan: ekran yüksekliğinin %90'ı.
        assertTrue(distBig <= (h * 0.9f).toInt() + 1)
    }

    @Test
    fun `varsayilan adim ekranin yuzde altmisidir`() {
        val g = FullControl.parseScrollStep("down", null, w, h)!!
        val dist = g.y1 - g.y2
        assertTrue(dist >= (h * 0.55f).toInt() && dist <= (h * 0.65f).toInt())
    }

    @Test
    fun `jest yavastir - fling momentum yok`() {
        val g = FullControl.parseScrollStep("down", null, w, h)!!
        assertTrue("momentum için yavaş çizim gerek", g.durationMs >= 400)
    }

    @Test
    fun `bilinmeyen yon ve bozuk ekran olcusu null doner`() {
        assertNull(FullControl.parseScrollStep("sola", null, w, h))
        assertNull(FullControl.parseScrollStep("", null, w, h))
        assertNull(FullControl.parseScrollStep("down", null, 0, h))
        assertNull(FullControl.parseScrollStep("down", null, w, 0))
    }

    @Test
    fun `bitis algilama - etiketler ayniysa icerik kipirdamadi`() {
        val before = listOf("Ali", "Veli", "Ayşe")
        val afterSame = listOf("Ali", "Veli", "Ayşe")
        assertFalse(FullControl.scrollMoved(before, afterSame))
        assertTrue(FullControl.scrollMoved(before, listOf("Ayşe", "Fatma")))
    }

    @Test
    fun `swipe semantigi bozulmadi - parmak yonu kaldi`() {
        // swipe hâlâ parmak yönü: "up" parmak yukarı (0.75h → 0.25h).
        val g = FullControl.directionGesture("up", w, h)!!
        assertTrue(g.y1 > g.y2)
        // scroll_step "down" ile aynı jesti üretir ama semantik ayrıdır:
        // biri parmak yönü, diğeri içerik yönü — sabitler karışmamalı.
        val s = FullControl.parseScrollStep("down", null, w, h)!!
        assertTrue(FullControl.CONTENT_DOWN.contains("down"))
        assertFalse(FullControl.CONTENT_DOWN.contains("up"))
        assertEquals(FullControl.SCROLL_STEP_DURATION_MS, s.durationMs)
    }
}