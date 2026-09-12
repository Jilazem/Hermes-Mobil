package com.hermes.mobile

import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.ui.feedSourceLabel
import com.hermes.mobile.ui.liveFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Canlı akış "Tümü" birleştirme mantığı — canlı + geçmiş tek listede.
 * Sıra ve çift-kayıt önleme kuralları burada sabitleniyor; ekran bunları
 * yalnız çiziyor.
 */
class LiveFeedTest {

    private fun live(
        id: String,
        key: String = "",
        status: String = "working",
        last: Double = 0.0,
        preview: String = "",
        title: String = "",
    ) = LiveSession(
        id = id,
        title = title,
        preview = preview,
        status = status,
        lastActive = last,
        sessionKey = key,
    )

    private fun past(id: String, started: Double?, ended: Boolean = true, source: String? = null) =
        HermesSession(
            id = id,
            source = source,
            startedAt = started,
            endedAt = if (ended) (started ?: 0.0) + 5 else null,
        )

    @Test
    fun `canlilar ustte sirali gecmisler altta`() {
        val entries = liveFeed(
            live = listOf(
                live("L1", last = 100.0),
                live("L2", last = 300.0),
            ),
            sessions = listOf(past("P1", 50.0)),
        )
        assertEquals(listOf("L2", "L1", "P1"), entries.map { it.dbId })
        assertTrue(entries[0].live)
        assertFalse(entries[2].live)
    }

    @Test
    fun `canli ile eslesen gecmis kayit tek satira_dusur`() {
        // session_key = P (canlı L, db kaydı P). Aynı oturum iki kez listelenmemeli.
        val entries = liveFeed(
            live = listOf(live("L", key = "P", last = 10.0, preview = "çalışıyor")),
            sessions = listOf(past("P", 5.0, source = "cli")),
        )
        assertEquals(1, entries.size)
        assertEquals("P", entries[0].dbId)
        assertTrue("canlı taraf kazanmalı", entries[0].live)
        assertEquals("çalışıyor", entries[0].preview)
        // canlı oturumun mavi rozeti kaynak için db kaydından gelir
        assertEquals("cli", entries[0].source)
        assertEquals("L", entries[0].liveId)
    }

    @Test
    fun `bitmemis_gecmis_kaydi_canli_sayilmaz_ama_yer_almaz`() {
        // ended_at == null: DB canlı görünüyor, gateway'de yok → boşta sayılır
        // ama çift kayıt yaratmaz.
        val entries = liveFeed(
            live = emptyList(),
            sessions = listOf(past("P9", 40.0, ended = false)),
        )
        assertEquals(1, entries.size)
        assertEquals("idle", entries[0].status)
        assertFalse(entries[0].live)
        assertNull(entries[0].liveSession)
    }

    @Test
    fun `bos_girdi_bos_liste`() {
        assertTrue(liveFeed(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `kaynak_etiketi_dil_kontrolu`() {
        assertEquals("Telegram", feedSourceLabel("telegram"))
        assertEquals("API", feedSourceLabel("api"))
        assertEquals("Zamanlanmış görev", feedSourceLabel("cron"))
        assertEquals("Bilinmeyen kaynak", feedSourceLabel(null))
        // tanınmayan kaynak ham yazılır — uydurma etiket yok.
        assertEquals("yandex", feedSourceLabel("yandex"))
    }
}
