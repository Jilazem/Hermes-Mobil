package com.hermes.mobile

import com.hermes.mobile.data.ArtemisTaskInfo
import com.hermes.mobile.data.BridgeState
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.ui.liveInventory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K2 Canlı envanter — "ne çalışıyor?" tek görünümü.
 *
 * Kilitlenen davranış: köprü satırı daima ilk ve renk semantiği
 * drawerStatusDot ile aynı; veri yoksa uydurma satır YOK; ham oturum id
 * satır başlığı olarak ASLA geçmez (displayLabel filtresi).
 */
class LiveInventoryTest {

    private fun live(id: String, status: String) = LiveSession(
        id = id,
        title = "",
        status = status,
    )

    @Test
    fun `kopru satiri daima ilk - bagli yesil`() {
        val rows = liveInventory(BridgeState.CONNECTED, emptyList(), emptyList())
        assertEquals(1, rows.size)
        assertEquals("bridge", rows[0].kind)
        assertEquals("green", rows[0].dot)
    }

    @Test
    fun `kopru durumlari renk semantigi`() {
        assertEquals(
            "yellow",
            liveInventory(BridgeState.RETRYING, emptyList(), emptyList())[0].dot,
        )
        assertEquals(
            "yellow",
            liveInventory(BridgeState.TAKEN_OVER, emptyList(), emptyList())[0].dot,
        )
        assertEquals(
            "grey",
            liveInventory(BridgeState.OFF, emptyList(), emptyList())[0].dot,
        )
    }

    @Test
    fun `bos veride uydurma satir yok - yalniz kopru`() {
        val rows = liveInventory(BridgeState.OFF, emptyList(), emptyList())
        assertEquals(listOf("bridge"), rows.map { it.kind })
    }

    @Test
    fun `calisan oturum yesil, bekleyen sari, digeri gri`() {
        val rows = liveInventory(
            BridgeState.CONNECTED,
            listOf(live("a1", "working"), live("b2", "waiting"), live("c3", "idle")),
            emptyList(),
        )
        val chats = rows.filter { it.kind == "chat" }
        assertEquals(3, chats.size)
        // Çalışan önce; renkler DrawerLiveRow ile birebir (green/yellow/grey).
        assertEquals("green", chats[0].dot)
        assertEquals("yellow", chats[1].dot)
        assertEquals("grey", chats[2].dot)
    }

    @Test
    fun `artemis gorevi calisiyorsa yesil, bitince gri - calisan once`() {
        val tasks = listOf(
            ArtemisTaskInfo("t1", "Ayarları aç", "completed", startedAt = 1, finishedAt = 2),
            ArtemisTaskInfo("t2", "Wi-Fi aç", "running", startedAt = 3),
        )
        val rows = liveInventory(BridgeState.CONNECTED, emptyList(), tasks)
        val art = rows.filter { it.kind == "artemis" }
        assertEquals(2, art.size)
        assertEquals("green", art[0].dot)   // running önce
        assertEquals("grey", art[1].dot)
        assertTrue(art[0].detail.contains("Wi-Fi aç"))
    }

    @Test
    fun `ham oturum id baslik olmaz`() {
        val rows = liveInventory(
            BridgeState.CONNECTED,
            listOf(live("20260913_184051_52f76a", "working")),
            emptyList(),
            liveTitle = { it.id },   // başlık olarak ham id verilmiş olsa bile
        )
        val chat = rows.first { it.kind == "chat" }
        assertEquals("?", chat.label)
    }

    @Test
    fun `cevrimici baslik cozumlenir`() {
        val rows = liveInventory(
            BridgeState.CONNECTED,
            listOf(live("x1", "working")),
            emptyList(),
            liveTitle = { "Sohbet: ev" },
        )
        assertEquals("Sohbet: ev", rows.first { it.kind == "chat" }.label)
    }
}