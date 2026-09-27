package com.hermes.mobile

import com.hermes.mobile.car.MirrorLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Android Auto yansıtma: araç ↔ telefon koordinat eşlemesi ve sürüş koruması. */
class MirrorLogicTest {

    // Dikey telefon 1080x2400, yatay araç 1920x720 → ölçek 0.3, yan bantlar 798 px.
    @Test
    fun `dikey telefon yatay aracta ortalanir ve dokunus tasinir`() {
        val f = MirrorLogic.fit(1920, 720, 1080, 2400)
        assertEquals(0.3f, f.scale, 1e-4f)
        assertEquals(798f, f.offsetX, 1e-3f)
        assertEquals(0f, f.offsetY, 1e-3f)
        assertEquals(540 to 1200, MirrorLogic.carToPhone(960f, 360f, 1920, 720, 1080, 2400))
        assertEquals(0 to 0, MirrorLogic.carToPhone(798f, 0f, 1920, 720, 1080, 2400))
    }

    @Test
    fun `siyah banda dokunus yok sayilir`() {
        assertNull(MirrorLogic.carToPhone(100f, 360f, 1920, 720, 1080, 2400))
        assertNull(MirrorLogic.carToPhone(1900f, 360f, 1920, 720, 1080, 2400))
    }

    @Test
    fun `kaydirma parmak yonunde ve ekrana kirpilir`() {
        // Parmak yukarı (distanceY pozitif) → telefonda da yukarı kaydırma: y2 < y1.
        val s = MirrorLogic.scrollSwipe(0f, 60f, 1920, 720, 1080, 2400)!!
        assertEquals(540, s[0]); assertEquals(1200, s[1])
        assertEquals(1000, s[3])
        assertNull(MirrorLogic.scrollSwipe(1f, 1f, 1920, 720, 1080, 2400))
        val big = MirrorLogic.scrollSwipe(0f, 10000f, 1920, 720, 1080, 2400)!!
        assertEquals(1, big[3])
    }

    @Test
    fun `savurma baskin eksende`() {
        val v = MirrorLogic.flingSwipe(0f, -3000f, 1080, 2400)!!
        assertTrue(v[3] < v[1])
        assertNull(MirrorLogic.flingSwipe(50f, 50f, 1080, 2400))
    }

    @Test
    fun `surus korumasi`() {
        assertTrue(MirrorLogic.pausedForDriving(true, 10f))
        assertFalse(MirrorLogic.pausedForDriving(true, 0.5f))
        assertFalse(MirrorLogic.pausedForDriving(true, null))
        assertFalse(MirrorLogic.pausedForDriving(false, 30f))
    }
}
