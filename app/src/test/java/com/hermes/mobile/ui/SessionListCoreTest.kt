package com.hermes.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ortak liste çekirdeği: çekmece "Takip" sekmesi, tam ekran Takip ve modül
 * listesi aynı süzme/sıralamayı kullanır — çalışan önce, sonra yeniden eskiye.
 */
class SessionListCoreTest {

    private fun row(
        key: String,
        title: String = key,
        preview: String = "",
        epoch: Double = 0.0,
        working: Boolean = false,
        followed: Boolean = false,
        archived: Boolean = false,
    ) = DrawerRow(
        key = key,
        liveId = key,
        dbId = key,
        title = title,
        preview = preview,
        epochSeconds = epoch,
        dot = "",
        working = working,
        pinned = false,
        archived = archived,
        current = false,
        liveSession = null,
        followed = followed,
    )

    private val rows = listOf(
        row("a", title = "Dava notu", epoch = 100.0, followed = true),
        row("b", title = "Parsel", preview = "ada 12", epoch = 300.0, working = true, followed = true),
        row("c", title = "Arşiv", epoch = 200.0, archived = true),
        row("d", title = "Takipsiz", epoch = 400.0),
    )

    @Test
    fun followedYalnizTakipEdilenler(): Unit {
        assertEquals(listOf("b", "a"), followedRows(rows).map { it.key })
    }

    @Test
    fun calisanOnceSiralanir(): Unit {
        assertEquals(listOf("b", "a"), followedRows(rows).map { it.key })
    }

    @Test
    fun sorguBaslikVeOnizlemedeAranir(): Unit {
        assertEquals(listOf("a"), followedRows(rows, "dava").map { it.key })
        assertEquals(listOf("b"), filterSessionRows(rows, "ada").map { it.key })
        assertEquals(listOf("b", "a"), filterSessionRows(rows, "  ").filter { it.followed }.map { it.key })
    }

    @Test
    fun arsivFiltresiYalnizArsivliler(): Unit {
        assertEquals(listOf("c"), filterSessionRows(rows, archivedOnly = true).map { it.key })
    }

    @Test
    fun takipVeArsivKesisimi(): Unit {
        val both = filterSessionRows(rows, followedOnly = true, archivedOnly = true)
        assertTrue(both.isEmpty())
    }

    @Test
    fun buyukKucukHarfDuyarsiz(): Unit {
        assertEquals(listOf("a"), followedRows(rows, "DAVA").map { it.key })
    }

    @Test
    fun bosListeBosDoner(): Unit {
        assertTrue(followedRows(emptyList()).isEmpty())
    }
}
