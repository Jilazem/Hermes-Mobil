package com.hermes.mobile

import com.hermes.mobile.data.ConnectionJournal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-10 / F2 — bağlantı olayı defteri (kopma nedenleri özeti).
 */
class ConnectionJournalTest {

    @After
    fun tearDown() = ConnectionJournal.clear()

    @Test
    fun `pong hatasi pong-timeout olarak siniflanir`() {
        assertEquals(
            ConnectionJournal.KIND_PONG,
            ConnectionJournal.classify(
                "SocketTimeoutException: sent ping but didn't receive pong within 40000ms"
            ),
        )
        assertEquals(
            ConnectionJournal.KIND_PONG,
            ConnectionJournal.classify("didn't receive PONG within 20000ms"),
        )
    }

    @Test
    fun `kimlik ve devralma ayri siniflanir`() {
        assertEquals(ConnectionJournal.KIND_AUTH, ConnectionJournal.classify("Token reddedildi (401)"))
        assertEquals(ConnectionJournal.KIND_AUTH, ConnectionJournal.classify("HTTP 403 forbidden"))
        assertEquals(ConnectionJournal.KIND_TAKEOVER, ConnectionJournal.classify("taken_over"))
        assertEquals(ConnectionJournal.KIND_TAKEOVER, ConnectionJournal.classify("close 4001"))
        assertEquals(ConnectionJournal.KIND_CLIENT, ConnectionJournal.classify("client closed"))
        assertEquals(ConnectionJournal.KIND_NETWORK, ConnectionJournal.classify("closed code=1006"))
        assertEquals(ConnectionJournal.KIND_SERVER, ConnectionJournal.classify("closed code=1011"))
    }

    @Test
    fun `bos ya da bilinmeyen neden unknown olur`() {
        assertEquals(ConnectionJournal.KIND_UNKNOWN, ConnectionJournal.classify(null))
        assertEquals(ConnectionJournal.KIND_UNKNOWN, ConnectionJournal.classify(" "))
        assertEquals(ConnectionJournal.KIND_UNKNOWN, ConnectionJournal.classify("garip bir sey"))
    }

    @Test
    fun `kayit tutulur ve kronolojik doner`() {
        ConnectionJournal.record("ws", "pong", at = 1_000L)
        ConnectionJournal.record("bridge", "closed code=1006", at = 2_000L)
        val all = ConnectionJournal.recent()
        assertEquals(2, all.size)
        assertEquals("ws", all[0].channel)
        assertEquals(ConnectionJournal.KIND_PONG, all[0].kind)
        assertEquals(ConnectionJournal.KIND_NETWORK, all[1].kind)
    }

    @Test
    fun `kanal suzgeci calisir`() {
        ConnectionJournal.record("ws", "pong", at = 1_000L)
        ConnectionJournal.record("bridge", "pong", at = 2_000L)
        ConnectionJournal.record("ws", "pong", at = 3_000L)
        assertEquals(2, ConnectionJournal.recent(5, "ws").size)
        assertEquals(1, ConnectionJournal.recent(5, "bridge").size)
        assertTrue(ConnectionJournal.recent(5, "relay").isEmpty())
    }

    @Test
    fun `ozet son kopmalari ve bes dakika sayisini verir`() {
        ConnectionJournal.record("ws", "pong", at = 100_000L)
        ConnectionJournal.record("ws", "pong", at = 200_000L)
        ConnectionJournal.record("ws", "closed code=1006", at = 400_000L)
        val s = ConnectionJournal.summary("ws", n = 3, now = 400_000L)
        assertTrue("özet kanal içermeli: $s", s.contains("ws=pong-timeout"))
        assertTrue("özet tür içermeli: $s", s.contains("ws=network-lost"))
        assertTrue("5 dk sayacı olmalı: $s", s.contains("son 5 dk 3"))
    }

    @Test
    fun `bes dakikadan eski olaylar sayaca girmez`() {
        ConnectionJournal.record("ws", "pong", at = 0L)
        val s = ConnectionJournal.summary("ws", now = 10 * 60_000L)
        assertTrue("eski olay sayılmamalı: $s", s.contains("son 5 dk 0"))
        assertTrue(s.contains("ws=pong-timeout"))
    }

    @Test
    fun `bos defter ozeti acik soyler`() {
        assertTrue(ConnectionJournal.summary("ws").contains("kayit yok"))
        assertTrue(ConnectionJournal.dumpSection().isBlank())
    }

    @Test
    fun `defter sinirlidir`() {
        for (i in 1..60) ConnectionJournal.record("ws", "pong", at = i.toLong())
        assertEquals(40, ConnectionJournal.recent(100).size)
        // En yeni kayıt korunur, en eskisi düşer.
        assertEquals(60L, ConnectionJournal.recent(1).first().at)
    }

    @Test
    fun `token sizintisi ayiklanir`() {
        ConnectionJournal.record(
            "ws",
            "failed",
            detail = "wss://host/api/ws?token=supersecretvalue&x=1",
            at = 1L,
        )
        val line = ConnectionJournal.recent(1).first().detail
        assertTrue("token maskelenmeli: $line", !line.contains("supersecretvalue"))
        assertTrue(line.contains("token=***"))
    }

    @Test
    fun `dump bolumu satir basina bir olay yazar`() {
        ConnectionJournal.record("ws", "pong", at = 1_000L)
        ConnectionJournal.record("relay", "closed code=1006", at = 2_000L)
        val dump = ConnectionJournal.dumpSection()
        assertTrue(dump.contains("connection journal"))
        assertTrue(dump.contains("[ws] pong-timeout"))
        assertTrue(dump.contains("[relay] network-lost"))
        assertEquals(3, dump.trim().lines().size)
    }

    @Test
    fun `clear defteri bosaltir`() {
        ConnectionJournal.record("ws", "pong", at = 1L)
        ConnectionJournal.clear()
        assertTrue(ConnectionJournal.recent().isEmpty())
    }
}
