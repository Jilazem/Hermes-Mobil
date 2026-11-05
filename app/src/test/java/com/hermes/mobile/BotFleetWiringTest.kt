package com.hermes.mobile

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BotFleetWiringTest {
    private fun panel(): String {
        val rel = "src/main/java/com/hermes/mobile/ui/BotFleetPanel.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun `ask mode gates dispatch behind explicit spending approval dialog`() {
        val source = panel()
        assertTrue(source.contains("requiresApproval(selectedMode)"))
        assertTrue(source.contains("pendingApproval?.let"))
        assertTrue(source.contains("para harcayabilir"))
        assertTrue(source.contains("Onayla"))
        assertTrue(source.contains("Vazgeç"))
    }

    @Test fun `group delete requires confirmation`() {
        val source = panel()
        assertTrue(source.contains("pendingGroupDeletion"))
        assertTrue(source.contains("deleteGroup("))
        assertTrue(source.contains("Grup silinsin mi?"))
    }

    @Test fun `labels use persistent add dialog and removable chips`() {
        val source = panel()
        assertTrue(source.contains("showLabelDialog"))
        assertTrue(source.contains("addLabel("))
        assertTrue(source.contains("removeLabel("))
        assertTrue(source.contains("Yetenek etiketi ekle"))
    }

    @Test fun `schedule has persistent enabled switch and ready time presets`() {
        val source = panel()
        assertTrue(source.contains("BotSchedule("))
        assertTrue(source.contains("scheduleTimePresets"))
        assertTrue(source.contains("persistSchedule"))
        assertTrue(source.contains("Switch("))
    }

    @Test fun `chat renders real visible messages and mirror status`() {
        val source = panel()
        assertTrue(source.contains("botVisibleMessages"))
        assertTrue(source.contains("ScreenMirror.status.collectAsState"))
        assertTrue(source.contains("ScreenMirror.lastFrame.collectAsState"))
    }
}
