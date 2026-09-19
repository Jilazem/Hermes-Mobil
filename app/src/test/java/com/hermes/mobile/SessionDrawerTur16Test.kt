package com.hermes.mobile

import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.ui.DrawerGroup
import com.hermes.mobile.ui.DrawerItem
import com.hermes.mobile.ui.drawerListItems
import com.hermes.mobile.ui.drawerRows
import com.hermes.mobile.ui.drawerStatusDot
import com.hermes.mobile.ui.drawerTimeGroup
import com.hermes.mobile.ui.displayLabel
import com.hermes.mobile.ui.lastSessionToRestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tur-16 — oturum çekmecesi karar mantığı (SC-001: 6+ yeni birim testi).
 *
 * Gruplama, arama filtresi, sabitleme sırası, arşiv/geri-al, son oturum
 * kalıcılığı, ham id yasağı, durum noktası — hepsi saf
 * `SessionDrawerLogic.kt` üzerinden.
 */
class SessionDrawerTur16Test {

    private fun epoch(daysAgo: Long, hour: Int = 12): Double =
        LocalDate.now().minusDays(daysAgo).atTime(hour, 0)
            .atZone(ZoneId.systemDefault()).toEpochSecond().toDouble()

    private fun sess(
        id: String,
        startedAt: Double? = null,
        ended: Boolean = true,
        name: String? = null,
        preview: String? = null,
    ) = HermesSession(
        id = id,
        displayName = name,
        preview = preview,
        startedAt = startedAt,
        endedAt = if (ended) (startedAt ?: 0.0) + 1 else null,
    )

    // ---- 1) Zaman grupları: Bugün / Dün / Son 7 gün / Daha eski ------------

    @Test
    fun `zaman gruplari tur16 esikleriyle dogru`() {
        val today = LocalDate.of(2026, 9, 19)
        fun e(d: LocalDate): Double = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toEpochSecond().toDouble()
        assertEquals(DrawerGroup.Today, drawerTimeGroup(e(today), today))
        assertEquals(DrawerGroup.Yesterday, drawerTimeGroup(e(today.minusDays(1)), today))
        assertEquals(DrawerGroup.Last7, drawerTimeGroup(e(today.minusDays(2)), today))
        assertEquals(DrawerGroup.Last7, drawerTimeGroup(e(today.minusDays(6)), today))
        assertEquals(DrawerGroup.Earlier, drawerTimeGroup(e(today.minusDays(7)), today))
        assertEquals(DrawerGroup.Earlier, drawerTimeGroup(0.0, today))
    }

    // ---- 2) Satır üretimi: canlı birleşimi + sıra --------------------------

    @Test
    fun `satirlarda sabit en ustte sonra yeni eskiye`() {
        val rows = drawerRows(
            sessions = listOf(
                sess("eski", epoch(3), name = "Eski iş"),
                sess("yeni", epoch(0), name = "Yeni iş"),
                sess("sabit", epoch(9), name = "Sabit iş"),
            ),
            live = emptyList(),
            liveByDbId = emptyMap(),
            flags = SessionFlags(pinned = setOf("sabit")),
            cronNames = emptyMap(),
        )
        assertEquals(listOf("sabit", "yeni", "eski"), rows.map { it.dbId })
        assertTrue(rows.first().pinned)
    }

    @Test
    fun `canli kaydın durumlari satira yansir`() {
        val l = LiveSession(
            id = "abc123",
            sessionKey = "abc123",
            status = "working",
            lastActive = epoch(0),
            startedAt = epoch(0),
        )
        val rows = drawerRows(
            sessions = emptyList(),
            live = listOf(l),
            liveByDbId = emptyMap(),
            flags = SessionFlags(),
            cronNames = emptyMap(),
        )
        assertEquals(1, rows.size)
        assertEquals("green", rows.first().dot)
        assertTrue(rows.first().working)
    }

    // ---- 3) Arama filtresi (başlık + önizleme, anlık) ----------------------

    @Test
    fun `arama baslik ve onizlemede anlik suzer, baslik kaldirilir`() {
        val rows = drawerRows(
            sessions = listOf(
                sess("a", epoch(0), name = "Rapor düzeltmesi", preview = "PDF tarafını gözden geçir"),
                sess("b", epoch(1), name = "Boya hesabı", preview = "dış cephe 3 kat"),
            ),
            live = emptyList(),
            liveByDbId = emptyMap(),
            flags = SessionFlags(),
            cronNames = emptyMap(),
        )
        val byTitle = drawerListItems(rows, query = "rapor", liveOnly = false, grouping = true)
            .filterIsInstance<DrawerItem.RowItem>().map { it.row.dbId }
        assertEquals(listOf("a"), byTitle)

        val byPreview = drawerListItems(rows, query = "cephe", liveOnly = false, grouping = true)
            .filterIsInstance<DrawerItem.RowItem>().map { it.row.dbId }
        assertEquals(listOf("b"), byPreview)

        val none = drawerListItems(rows, query = "yok-boyle-bir-sey", liveOnly = false, grouping = true)
            .filterIsInstance<DrawerItem.RowItem>()
        assertTrue(none.isEmpty())
    }

    // ---- 4) Sabitleme sırası + grup başlıkları -----------------------------

    @Test
    fun `grup basliklari sabitlenen-sonra-zaman tertibi cizilir`() {
        val today = LocalDate.of(2026, 9, 19)
        fun e(d: LocalDate, hour: Int = 12): Double =
            d.atTime(hour, 0).atZone(ZoneId.systemDefault()).toEpochSecond().toDouble()
        val rows = listOf(
            // 4 satır — GROUPING_MIN eşiğini geçsin.
            drawerRow("s1", "Sabit", pinned = true, sec = e(today.minusDays(30))),
            drawerRow("s2", "Bugünkü", sec = e(today)),
            drawerRow("s3", "Dünkü", sec = e(today.minusDays(1))),
            drawerRow("s4", "Çok eski", sec = e(today.minusDays(60))),
        )
        val keys = drawerListItems(rows, query = "", liveOnly = false, grouping = true, today = today)
            .map { it.key }
        assertEquals(
            listOf("hdr-Pinned", "r-s1", "hdr-Today", "r-s2", "hdr-Yesterday", "r-s3", "hdr-Earlier", "r-s4"),
            keys,
        )
    }

    private fun drawerRow(
        key: String,
        title: String,
        sec: Double = 0.0,
        pinned: Boolean = false,
        dot: String = "grey",
    ) = com.hermes.mobile.ui.DrawerRow(
        key = key,
        liveId = "",
        dbId = key,
        title = title,
        preview = "",
        epochSeconds = sec,
        dot = dot,
        working = false,
        pinned = pinned,
        archived = false,
        current = false,
        liveSession = null,
    )

    // ---- 5) Arşiv filtresi + geri-al kararlılığı ---------------------------

    @Test
    fun `arsivli satir tercihe gore gelir, hidden hep elenir`() {
        val sessions = listOf(sess("x", epoch(0), name = "X"), sess("y", epoch(0), name = "Y"))
        val flags = SessionFlags(archived = setOf("x"), hidden = setOf("y"))

        val kapali = drawerRows(sessions, emptyList(), emptyMap(), flags, emptyMap(), showArchived = false)
        assertTrue(kapali.none { it.dbId == "x" || it.dbId == "y" })

        // hidden kalıcı; arşiv geri alınınca (bayrak kalkınca) satır geri gelir.
        val acik = drawerRows(sessions, emptyList(), emptyMap(), flags, emptyMap(), showArchived = true)
        assertEquals(listOf("x"), acik.map { it.dbId })
    }

    // ---- 6) Son oturumu hatırlama (FR-006) --------------------------------

    @Test
    fun `son oturum geri yuklenir, silinmis veya eksik kayıt yüklenmez`() {
        val rows = listOf(drawerRow("k1", "İş bir", sec = epoch(0)), drawerRow("k2", "İş iki", sec = epoch(1)))

        val r = lastSessionToRestore("k1", rows, currentSessionId = null)
        assertEquals("k1", r?.dbId)

        // Zaten açıksa yeniden bağlanma yok.
        assertNull(lastSessionToRestore("k2", rows, currentSessionId = "k2"))

        // Kayıt listede yoksa (silindi/gizlendi) restore edilmez.
        assertNull(lastSessionToRestore("silindi", rows, currentSessionId = null))

        // Boş kayıt = yeni sohbet kalsın.
        assertNull(lastSessionToRestore("", rows, currentSessionId = null))
    }

    // ---- 7) Ham id yasağı (FR-001 koruması) --------------------------------

    @Test
    fun `ham id baslik olamaz soru isareti olur`() {
        // Çıplak DB/canlı kayıtlarında başlık alanı id ile aynıdır;
        // drawerRows zinciri readableTitle'ı izler, displayLabel son kalkandır.
        assertEquals("?", displayLabel("20260913_184051_52f76a"))
        assertEquals("?", displayLabel("cron_2e4ea303c123_20260911_221601"))
        assertEquals("?", displayLabel("538fa088abcd1234"))
        assertEquals("?", displayLabel(""))
        assertEquals("Rapor", displayLabel("Rapor"))

        // Satır üretimi: anlamsız hiçbir başlık sızdırmasın — çözülmemiş
        // isimli kayıt "Sohbet · gg.AA" yedeğine düşer, ham id görünmez.
        val rows = drawerRows(
            sessions = listOf(HermesSession(id = "20260913_184051_52f76a", startedAt = epoch(0), endedAt = epoch(0))),
            live = emptyList(),
            liveByDbId = emptyMap(),
            flags = SessionFlags(),
            cronNames = emptyMap(),
        )
        assertEquals(1, rows.size)
        assertTrue("başlık ham id olamaz: ${rows.first().title}", !rows.first().title.contains("52f76a"))
    }

    // ---- 8) Durum noktası eşlemesi ----------------------------------------

    @Test
    fun `durum noktasi yesil sari gri bos kararları`() {
        assertEquals("green", drawerStatusDot(working = true, waitingOrStarting = false, sessionActive = false, sessionRecord = true))
        assertEquals("green", drawerStatusDot(working = false, waitingOrStarting = false, sessionActive = true, sessionRecord = true))
        assertEquals("yellow", drawerStatusDot(working = false, waitingOrStarting = true, sessionActive = false, sessionRecord = true))
        assertEquals("grey", drawerStatusDot(working = false, waitingOrStarting = false, sessionActive = false, sessionRecord = true))
        assertEquals("", drawerStatusDot(working = false, waitingOrStarting = false, sessionActive = false, sessionRecord = false))
    }

    // ---- 9) Canlı sekmesi süzmesi (liveOnly, arama açılınca başlık kalkar) --

    @Test
    fun `canli suzgesi aktif satirlari tutar, arama basligi kaldirir`() {
        val rows = listOf(
            drawerRow("a", "Çalışan", sec = epoch(0), dot = "green").let { it.copy(working = true) },
            drawerRow("b", "Bitmiş", sec = epoch(1), dot = "grey"),
        )
        val live = drawerListItems(rows, query = "", liveOnly = true, grouping = false)
            .filterIsInstance<DrawerItem.RowItem>().map { it.row.dbId }
        assertEquals(listOf("a"), live)

        // Arama varken grup başlığı çizilmez (düz liste).
        val grouped = drawerListItems(rows, query = "a", liveOnly = false, grouping = true)
        assertTrue(grouped.none { it is DrawerItem.Header })
    }
}
