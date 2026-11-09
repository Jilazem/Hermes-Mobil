package com.hermes.mobile

import android.app.Application
import com.hermes.mobile.ui.HomeViewMode
import com.hermes.mobile.ui.MdBlock
import com.hermes.mobile.ui.parseMarkdown
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HermesV763InteractiveTest {

    @Test
    fun testHomeViewModeValues() {
        assertEquals(HomeViewMode.Stream, HomeViewMode.valueOf("Stream"))
        assertEquals(HomeViewMode.Cards, HomeViewMode.valueOf("Cards"))
    }

    @Test
    fun testMarkdownCodeBlocksCollapsing() {
        val md = "Giris\n```python\ndef hello():\n    print(123)\n```\nKapanis"
        val blocks = parseMarkdown(md)
        val codeBlock = blocks.filterIsInstance<MdBlock.Code>().firstOrNull()
        assertNotNull(codeBlock)
        assertEquals("python", codeBlock!!.language)
        assertTrue(codeBlock.code.contains("hello()"))

        val hasError = listOf("traceback", "exception", "error:", "failed", "hata").any {
            it in codeBlock.code.lowercase()
        }
        assertFalse(hasError)

        val errSnippet = "Traceback (most recent call last):\n  File test.py\nException: Failed"
        val hasError2 = listOf("traceback", "exception", "error:", "failed", "hata").any {
            it in errSnippet.lowercase()
        }
        assertTrue(hasError2)
    }

    @Test
    fun testMarkdownImageInteraction() {
        val md = "![Diagram](https://example.com/flow.png)"
        val blocks = parseMarkdown(md)
        val imageBlock = blocks.filterIsInstance<MdBlock.Image>().firstOrNull()
        assertNotNull(imageBlock)
        assertEquals("https://example.com/flow.png", imageBlock!!.url)
        assertEquals("Diagram", imageBlock.alt)
    }

    @Test
    fun testSubagentTreeStoreAndDrawerGrouping() {
        com.hermes.mobile.data.SubagentTreeStore.clear()
        com.hermes.mobile.data.SubagentTreeStore.record("child-123456", "parent-999")
        assertEquals("parent-999", com.hermes.mobile.data.SubagentTreeStore.parentOf("child-123456"))
        assertEquals("parent-999", com.hermes.mobile.data.SubagentTreeStore.allBindings()["child-123456"])

        val parentSession = com.hermes.mobile.data.HermesSession(
            id = "parent-999",
            serverTitle = "Ana Görev Oturumu",
        )
        val childSession = com.hermes.mobile.data.HermesSession(
            id = "child-123456",
            serverTitle = "sohbet", // Generic name to be converted to Alt Görev
            parentSessionId = "parent-999",
        )

        val rows = com.hermes.mobile.ui.drawerRows(
            sessions = listOf(parentSession, childSession),
            live = emptyList(),
            liveByDbId = emptyMap(),
            flags = com.hermes.mobile.data.SessionFlags(),
            cronNames = emptyMap(),
        )

        // The child is nested under the parent row as an alt çatal ağacı
        assertEquals(1, rows.size)
        val parentRow = rows.first()
        assertEquals("parent-999", parentRow.dbId)
        assertEquals(1, parentRow.childRows.size)

        val nestedChild = parentRow.childRows.first()
        assertEquals("child-123456", nestedChild.dbId)
        assertTrue(nestedChild.isChild)
        assertEquals("Alt Görev · 123456", nestedChild.title)

        com.hermes.mobile.data.SubagentTreeStore.clear()
    }

    @Test
    fun testBotLocalizationTurkish() {
        assertEquals("Web Araması", com.hermes.mobile.ui.bots.BotLocalization.toolDisplayName("web_search"))
        assertEquals("Terminal / Komut", com.hermes.mobile.ui.bots.BotLocalization.toolDisplayName("terminal"))
        assertEquals("Kamera", com.hermes.mobile.ui.bots.BotLocalization.toolDisplayName("camera"))
        assertEquals("Pano", com.hermes.mobile.ui.bots.BotLocalization.toolDisplayName("clipboard"))

        assertEquals("Kapalı", com.hermes.mobile.ui.bots.BotLocalization.thinkModeDisplayName("off"))
        assertEquals("Yüksek", com.hermes.mobile.ui.bots.BotLocalization.thinkModeDisplayName("high"))

        assertEquals("Kullanıcı", com.hermes.mobile.ui.bots.BotLocalization.roleDisplayName("user"))
        assertEquals("Asistan", com.hermes.mobile.ui.bots.BotLocalization.roleDisplayName("assistant"))

        assertEquals("Saatlik", com.hermes.mobile.ui.bots.BotLocalization.schedulePresetDisplayName(com.hermes.mobile.data.SchedulePreset.HOURLY))
        assertEquals("Günlük", com.hermes.mobile.ui.bots.BotLocalization.schedulePresetDisplayName(com.hermes.mobile.data.SchedulePreset.DAILY))
        assertEquals("Hafta İçi", com.hermes.mobile.ui.bots.BotLocalization.schedulePresetDisplayName(com.hermes.mobile.data.SchedulePreset.WEEKDAYS))
        assertEquals("Özel", com.hermes.mobile.ui.bots.BotLocalization.schedulePresetDisplayName(com.hermes.mobile.data.SchedulePreset.CUSTOM))
    }

    @Test
    fun testServerProfileWebDashboardUrl() {
        val profile = com.hermes.mobile.data.ServerProfile(
            id = "srv-1",
            name = "Test",
            baseUrl = "http://192.168.1.101:8080",
            token = "xyz",
        )
        assertEquals("http://192.168.1.101:9120/#/", profile.webDashboardUrl)
    }
}
