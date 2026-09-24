package com.hermes.mobile

import com.hermes.mobile.assistant.WakeWordLogic
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Hey Jarvis": Python'da doğrulanan akışın saf parçaları birebir aynı olmalı. */
class WakeWordLogicTest {

    @Test
    fun `kayan pencere yeni parcayi sona ekler`() {
        val w = FloatArray(6)
        var filled = WakeWordLogic.slide(w, 0, shortArrayOf(1, 2, 3))
        assertEquals(3, filled)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f, 2f, 3f), w, 0f)
        filled = WakeWordLogic.slide(w, filled, shortArrayOf(4, 5, 6))
        filled = WakeWordLogic.slide(w, filled, shortArrayOf(7, 8, 9))
        assertEquals(6, filled)
        assertArrayEquals(floatArrayOf(4f, 5f, 6f, 7f, 8f, 9f), w, 0f)
    }

    @Test
    fun `satir tamponu en eskiyi atar`() {
        val b = Array(3) { floatArrayOf(it.toFloat()) }
        WakeWordLogic.pushRows(b, listOf(floatArrayOf(10f), floatArrayOf(11f)))
        assertArrayEquals(floatArrayOf(2f), b[0], 0f)
        assertArrayEquals(floatArrayOf(10f), b[1], 0f)
        assertArrayEquals(floatArrayOf(11f), b[2], 0f)
    }

    @Test
    fun `mel donusumu openwakeword ile ayni`() {
        assertEquals(2f, WakeWordLogic.melTransform(0f), 0f)
        assertEquals(3.5f, WakeWordLogic.melTransform(15f), 1e-6f)
    }

    @Test
    fun `dedektor art arda cerceve ister ve sogur`() {
        val d = WakeWordLogic.Detector(0.4f, 2, cooldown = 3)
        assertFalse(d.feed(0.9f))          // tek çerçeve yetmez
        assertTrue(d.feed(0.8f))           // ikinci art arda → tetik
        assertFalse(d.feed(0.9f)); assertFalse(d.feed(0.9f)); assertFalse(d.feed(0.9f)) // soğuma
        assertFalse(d.feed(0.9f))
        assertTrue(d.feed(0.9f))
        val e = WakeWordLogic.Detector(0.4f, 2)
        assertFalse(e.feed(0.9f)); assertFalse(e.feed(0.1f)); assertFalse(e.feed(0.9f)) // arada düşüş sıfırlar
    }

    @Test
    fun `hassasiyet olcumle secilen degerler`() {
        assertEquals(0.4f, WakeWordLogic.Sensitivity.fromId("normal").threshold, 0f)
        assertEquals(2, WakeWordLogic.Sensitivity.fromId(null).consecutive)
        assertEquals(WakeWordLogic.Sensitivity.STRICT, WakeWordLogic.Sensitivity.fromId("siki"))
    }
}
