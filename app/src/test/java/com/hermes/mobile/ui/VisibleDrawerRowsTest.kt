package com.hermes.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * J2 (patron): çekmece "Tümü" listesi süzmesi.
 *
 * Kural: bitmiş cron/sistem bildirimleri "Tümü" (tab=0) listesine girmez;
 * devam eden (canlı) cron işi listede kalır. Arşiv sekmesi (tab=2) kullanıcının
 * bilinçli görünümüdür — hiçbir satır filtrelenmez.
 */
class VisibleDrawerRowsTest {

    private fun row(
        key: String,
        dot: String = "",
        working: Boolean = false,
        archived: Boolean = false,
    ) = DrawerRow(
        key = key,
        liveId = key,
        dbId = key,
        title = key,
        preview = "",
        epochSeconds = 0.0,
        dot = dot,
        working = working,
        pinned = false,
        archived = archived,
        current = false,
        liveSession = null,
    )

    private val rows = listOf(
        row("sohbet"),                        // normal oturum
        row("cron-bitti", dot = "grey"),      // bitmiş cron → gizlenir
        row("cron-calisiyor", working = true), // devam eden cron → kalır
        row("sistem", dot = "grey"),          // sistem bildirimi → gizlenir
    )

    private val sources = mapOf(
        "sohbet" to "chat",
        "cron-bitti" to "cron",
        "cron-calisiyor" to "cron",
        "sistem" to "system",
    )

    @Test
    fun tumuSekmesiBitmisCronuGizlerCalisaniTutar() {
        val visible = visibleDrawerRows(rows, tab = 0) { sources[it.key] }
        assertEquals(listOf("sohbet", "cron-calisiyor"), visible.map { it.key })
    }

    @Test
    fun arsivSekmesiHicbirSeyiFiltrelemez() {
        val visible = visibleDrawerRows(rows, tab = 2) { sources[it.key] }
        assertEquals(rows.map { it.key }, visible.map { it.key })
    }
}
