package com.hermes.mobile

import com.hermes.mobile.data.ArtemisTaskTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * K2 Artemis görev defteri — Canlı envanter ve bitiş bildiriminin temeli.
 */
class ArtemisTaskTrackerTest {

    @Before
    fun reset() {
        ArtemisTaskTracker.reset()
    }

    @Test
    fun `gonderim running olarak kaydedilir`() {
        ArtemisTaskTracker.submitted("t1", "Wi-Fi aç", now = 1000)
        val t = ArtemisTaskTracker.byId("t1")!!
        assertEquals("running", t.status)
        assertFalse(t.done)
        assertEquals(1, ArtemisTaskTracker.running().size)
    }

    @Test
    fun `progress durum tazeleme bitis zamani vermez`() {
        ArtemisTaskTracker.submitted("t1", "görev", now = 1000)
        ArtemisTaskTracker.progressed("t1", "running")
        val t = ArtemisTaskTracker.byId("t1")!!
        assertFalse(t.done)
        assertEquals("running", t.status)
    }

    @Test
    fun `bitis kaydedilir ve running listesinden duser`() {
        ArtemisTaskTracker.submitted("t1", "görev", now = 1000)
        ArtemisTaskTracker.finished("t1", "completed", now = 2000)
        val t = ArtemisTaskTracker.byId("t1")!!
        assertTrue(t.done)
        assertEquals("completed", t.status)
        assertEquals(0, ArtemisTaskTracker.running().size)
    }

    @Test
    fun `bitmis gorev defterde kalir - ne oldu sorusu cevapsiz kalmaz`() {
        ArtemisTaskTracker.submitted("t1", "görev", now = 1000)
        ArtemisTaskTracker.finished("t1", "failed", now = 2000)
        assertEquals(1, ArtemisTaskTracker.tasks.value.size)
    }

    @Test
    fun `ayni id tekrar gonderilince eskisi ezilmez ama cap korunur`() {
        ArtemisTaskTracker.submitted("t1", "ilk", now = 1)
        ArtemisTaskTracker.finished("t1", "completed", now = 2)
        ArtemisTaskTracker.submitted("t1", "ikinci", now = 3)
        // Eski kayıt durumu korunur (yinelenen id), defter şişmez.
        assertEquals(1, ArtemisTaskTracker.tasks.value.size)
        for (i in 0 until ArtemisTaskTracker.MAX_TASKS + 5) {
            ArtemisTaskTracker.submitted("x$i", "g", now = i.toLong())
        }
        assertTrue(ArtemisTaskTracker.tasks.value.size <= ArtemisTaskTracker.MAX_TASKS)
    }
}