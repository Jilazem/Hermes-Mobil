package com.hermes.mobile

import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.ui.GROUPING_MIN
import com.hermes.mobile.ui.SessionListItem
import com.hermes.mobile.ui.TimeGroup
import com.hermes.mobile.ui.displayTitle
import com.hermes.mobile.ui.sessionListItems
import com.hermes.mobile.ui.timeGroup
import com.hermes.mobile.ui.visibleSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Oturum listesi mantığı — bayrak mapper'ı, zaman gruplaması ve başlık
 * akışı. Bunlar UI'dan bağımsız saf fonksiyonlar; polling listeyi ezip
 * geçtiği için sıralama/gizleme davranışının burada sabitlenmesi önemli.
 */
class SessionFlagsLogicTest {

    private fun sess(
        id: String,
        startedAt: Double? = null,
        ended: Boolean = true,
        name: String? = null,
    ) = HermesSession(
        id = id,
        displayName = name,
        startedAt = startedAt,
        endedAt = if (ended) (startedAt ?: 0.0) + 1 else null,
    )

    private fun epoch(daysAgo: Long, hour: Int = 12): Double =
        LocalDate.now().minusDays(daysAgo).atTime(hour, 0)
            .atZone(ZoneId.systemDefault()).toEpochSecond().toDouble()

    // ---- zaman gruplaması -------------------------------------------------

    @Test
    fun `null ve sifir zaman Oncekilere duser`() {
        assertEquals(TimeGroup.Oncekiler, timeGroup(null))
        assertEquals(TimeGroup.Oncekiler, timeGroup(0.0))
        assertEquals(TimeGroup.Oncekiler, timeGroup(-5.0))
    }

    @Test
    fun `bugun dun ve oncesi dogru gruba duser`() {
        // Gün ortası saat seçiliyor: gün sınırındaki saat kayması testte
        // dalgalanma üretmesin.
        assertEquals(TimeGroup.Bugun, timeGroup(epoch(0)))
        assertEquals(TimeGroup.Dun, timeGroup(epoch(1)))
        assertEquals(TimeGroup.Oncekiler, timeGroup(epoch(2)))
        assertEquals(TimeGroup.Oncekiler, timeGroup(epoch(365)))
    }

    // ---- görünürlük mapper'ı ----------------------------------------------

    @Test
    fun `hidden her zaman, archived tercihe gore elenir`() {
        val sessions = listOf(sess("a"), sess("b"), sess("c"))
        val flags = SessionFlags(hidden = setOf("a"), archived = setOf("b"))

        val kapali = visibleSessions(sessions, flags, showArchived = false)
        assertEquals(listOf("c"), kapali.map { it.id })

        val acik = visibleSessions(sessions, flags, showArchived = true)
        assertEquals(setOf("b", "c"), acik.map { it.id }.toSet())
        assertFalse(acik.any { it.id == "a" })
    }

    @Test
    fun `sabitlenen en ustte, sonra yeniden eskiye`() {
        val sessions = listOf(
            sess("eski", epoch(5)),
            sess("yeni", epoch(0)),
            sess("sabit", epoch(9)),
        )
        val flags = SessionFlags(pinned = setOf("sabit"))
        val sonuc = visibleSessions(sessions, flags, showArchived = false)
        assertEquals(listOf("sabit", "yeni", "eski"), sonuc.map { it.id })
    }

    @Test
    fun `rename basligi overrideler, title'a dokunmaz`() {
        val s = sess("x", name = "Orijinal")
        assertEquals("Orijinal", displayTitle(s, SessionFlags()))
        assertEquals("Yeni ad", displayTitle(s, SessionFlags(renames = mapOf("x" to "Yeni ad"))))
        assertEquals("Orijinal", s.title)
    }

    // ---- başlık akışı -------------------------------------------------------

    @Test
    fun `esik altinda baslik yok`() {
        val shown = listOf(sess("a", epoch(0)), sess("b", epoch(1)))
        assertTrue(shown.size < GROUPING_MIN)
        val items = sessionListItems(shown, SessionFlags(), grouping = true)
        assertTrue(items.none { it is SessionListItem.Header })
        assertEquals(2, items.size)
    }

    @Test
    fun `sabitlenenler uste, zaman gruplari sirali baslik alir`() {
        val shown = listOf(
            sess("pin", epoch(30)),          // sabitlenen → kendi başlığı
            sess("t1", epoch(0)),
            sess("t2", epoch(0)),
            sess("d1", epoch(1)),
        )
        val flags = SessionFlags(pinned = setOf("pin"))
        val items = sessionListItems(shown, flags, grouping = true)
        val basliklar = items.filterIsInstance<SessionListItem.Header>().map { it.key }
        assertEquals(
            listOf("hdr-Pinned", "hdr-Bugun", "hdr-Dun"),
            basliklar,
        )
        // Boş grup (Öncekiler) başlık almaz; boş olmayan tek oturumlu grup alır.
        assertTrue(items.none { it.key == "hdr-Oncekiler" })
    }

    @Test
    fun `sorgu aktifken baslik kalkar, sabitleme sirasi kalir`() {
        val shown = listOf(sess("pin", epoch(30)), sess("t1", epoch(0)), sess("t2", epoch(0)))
        val flags = SessionFlags(pinned = setOf("pin"))
        val items = sessionListItems(shown, flags, grouping = false)
        assertTrue(items.none { it is SessionListItem.Header })
        assertEquals(listOf("pin", "t1", "t2"), items.map { (it as SessionListItem.Session).session.id })
    }

    @Test
    fun `arsivdeki sabitlenen arsiv acikken sabitlenenler basliginda kalir`() {
        val shown = listOf(sess("a", epoch(0)), sess("b", epoch(1)), sess("c", epoch(2)))
        val flags = SessionFlags(pinned = setOf("c"), archived = setOf("c"))
        val items = sessionListItems(shown, flags, grouping = true)
        val ilk = items.first() as SessionListItem.Header
        assertEquals("hdr-Pinned", ilk.key)
    }
}
