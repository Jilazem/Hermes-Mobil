package com.hermes.mobile

import com.hermes.mobile.ui.DrawerRow
import com.hermes.mobile.ui.restoreFallback
import com.hermes.mobile.ui.sessionExpiredNotice
import com.hermes.mobile.ui.tr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * B4 (CEO kararı) — süre dolan/kaybolan oturumda sessiz fallback YOK:
 * en güncel oturuma geçilir ve tek satır bildirim üretilir
 * ("«X» süresi doldu, son oturum açıldı").
 */
class SessionRestoreFallbackTest {

    private fun row(
        id: String,
        title: String,
        sec: Double,
        archived: Boolean = false,
    ) = DrawerRow(
        key = id, liveId = id, dbId = id, title = title, preview = "",
        epochSeconds = sec, dot = "", working = false, pinned = false,
        archived = archived, current = false, liveSession = null,
    )

    @Test
    fun `kayitli oturum listede yoksa en guncel satira bildirimle gecilir`() {
        val rows = listOf(row("eski", "Eski iş", 100.0), row("yeni", "Yeni iş", 200.0))
        val fb = restoreFallback("silinmis", rows, currentSessionId = null)
        assertNotNull(fb)
        assertEquals("yeni", fb!!.row.dbId)
        assertEquals(
            tr(
                "«Önceki oturum» süresi doldu, son oturum açıldı",
                "«Önceki oturum» expired — opened the last session",
            ),
            fb.notice,
        )
    }

    @Test
    fun `kayit hala geri yuklenebilirse fallback yok`() {
        val rows = listOf(row("k1", "İş bir", 100.0))
        assertNull(restoreFallback("k1", rows, currentSessionId = null))
    }

    @Test
    fun `acik oturum hicbir zaman devralinmaz`() {
        val rows = listOf(row("k2", "İş iki", 300.0))
        assertNull(restoreFallback("k1", rows, currentSessionId = "k1"))
        assertNull(restoreFallback("k1", rows, currentSessionId = "baska"))
    }

    @Test
    fun `bos kayit, bos liste ve arsivli satir fallback uretmez`() {
        val rows = listOf(row("k1", "İş bir", 100.0))
        assertNull(restoreFallback("", rows, currentSessionId = null))
        assertNull(restoreFallback("yok", emptyList(), currentSessionId = null))
        assertNull(
            restoreFallback(
                "yok",
                listOf(row("a", "Arşiv", 500.0, archived = true)),
                currentSessionId = null,
            ),
        )
    }

    @Test
    fun `bildirim sablonu CEO metnidir`() {
        assertEquals(
            tr("«X» süresi doldu, son oturum açıldı", "«X» expired — opened the last session"),
            sessionExpiredNotice("X"),
        )
    }
}