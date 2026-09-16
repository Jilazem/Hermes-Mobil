package com.hermes.mobile

import com.hermes.mobile.ChatItem
import com.hermes.mobile.ui.needsDaySeparator
import com.hermes.mobile.ui.daySeparatorLabel
import com.hermes.mobile.ui.clockLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Tur-14: sohbet gün ayraçları — saf mantık testleri.
 */
class ChatDaySeparatorTest {

    private fun ts(year: Int, month: Int, day: Int, hour: Int = 12, min: Int = 0): Double =
        LocalDateTime.of(year, month, day, hour, min)
            .atZone(ZoneId.systemDefault()).toInstant().epochSecond.toDouble()

    @Test
    fun ilkDamlaliOgreAyracAlir() {
        assertTrue(needsDaySeparator(null, ts(2026, 9, 16)))
    }

    @Test
    fun ayniGunIkinciOgreAyracAlmaz() {
        val a = ts(2026, 9, 16, 9)
        val b = ts(2026, 9, 16, 18)
        assertFalse(needsDaySeparator(a, b))
    }

    @Test
    fun gunDegisimiAyracUretir() {
        val a = ts(2026, 9, 15, 23)
        val b = ts(2026, 9, 16, 1)
        assertTrue(needsDaySeparator(a, b))
    }

    @Test
    fun damgasizOgreAyracAlmaz() {
        assertFalse(needsDaySeparator(null, null))
        assertFalse(needsDaySeparator(ts(2026, 9, 16), null))
        assertFalse(needsDaySeparator(null, 0.0))
    }

    @Test
    fun bugunVeDunEtiketlenirEskiTarihYazilir() {
        val now = System.currentTimeMillis()
        val bugun = now / 1000.0
        val dun = bugun - 86_400
        assertEquals("Bugün", daySeparatorLabel(bugun, now))
        assertEquals("Dün", daySeparatorLabel(dun, now))
        // 100 gün öncesi: gerçek tarih yazılır.
        val eski = bugun - 100 * 86_400
        val d = java.time.Instant.ofEpochSecond(eski.toLong())
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val beklenen = "%02d.%02d.%04d".format(d.dayOfMonth, d.monthValue, d.year)
        assertEquals(beklenen, daySeparatorLabel(eski, now))
    }

    @Test
    fun saatDamgasiHHmm() {
        val t = ts(2026, 9, 16, 14, 5)
        assertEquals("14:05", clockLabel(t))
        assertEquals(null, clockLabel(null))
        assertEquals(null, clockLabel(0.0))
    }

    @Test
    fun chatItemTsAlanlariVarsayilanNull() {
        val u: ChatItem = ChatItem.User("u1", "merhaba")
        val a: ChatItem = ChatItem.Assistant("a1", "selam")
        assertEquals(null, (u as ChatItem.User).ts)
        assertEquals(null, (a as ChatItem.Assistant).ts)
    }
}
