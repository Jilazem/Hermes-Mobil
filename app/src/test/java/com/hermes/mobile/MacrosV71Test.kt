package com.hermes.mobile

import com.hermes.mobile.data.*
import com.hermes.mobile.ui.voiceEdgeStrength
import com.opentasker.core.engine.CooldownReservations
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class MacrosV71Test {
    @Test fun nativeImportNeverEnablesRulesOrReusesIds() {
        val rule = MacroRules.recipes().first().copy(enabled = true)
        val imported = MacroRules.import(MacroRules.export(listOf(rule))).single()
        assertFalse(imported.enabled); assertNotEquals(rule.id, imported.id)
        assertEquals(rule.actions, imported.actions)
    }
    @Test fun foreignConstraintsCannotBeSilentlyDropped() {
        val raw = """{"macro":{"m_name":"Source","m_enabled":true,"m_actionList":[{"m_classType":"HttpRequestAction"}],"m_constraintList":[{"m_classType":"DeviceLockedConstraint"}]}}"""
        val rule = MacroRules.import(raw).single()
        assertFalse(rule.enabled); assertTrue(rule.actions.isEmpty())
        assertTrue(rule.blockers.contains("DeviceLockedConstraint"))
        assertTrue(rule.original.contains("HttpRequestAction"))
        assertTrue(MacroRules.validate(rule).isNotEmpty())
    }
    @Test fun openTaskerBundlePreservesSourceAndRequiresReview() {
        val raw = """{"schemaVersion":2,"tasks":[{"name":"One","actions":[{"type":"task.run"}]}],"profiles":[],"variables":[]}"""
        val rule = MacroRules.import(raw).single()
        assertEquals(raw, rule.original); assertFalse(rule.enabled)
        assertTrue(rule.blockers.contains("task.run"))
    }
    @Test fun eachForeignRecordKeepsItsSourceAfterOtherRecordsAreDeleted() {
        val bundles = listOf(
            """{"exportVersion":7,"macroList":[{"m_name":"First"},{"m_name":"Second","m_constraintList":[{"m_classType":"DeviceLockedConstraint"}]}]}""",
            """{"schemaVersion":2,"profiles":[{"name":"Keep profile"}],"tasks":[{"name":"First"},{"name":"Second","actions":[{"type":"task.run"}]}]}""",
        )
        bundles.forEach { bundle ->
            val remaining = MacroRules.import(bundle).drop(1)
            val restored = MacroRules.import(MacroRules.export(remaining)).single()
            assertTrue("Remaining record lost its source", restored.original.contains("Second"))
            assertFalse("Deleted sibling retained as an executable item", restored.original.contains("First"))
            assertTrue(MacroRules.validate(restored).isNotEmpty())
            assertFalse(restored.enabled)
            val source = MacroRules.json.parseToJsonElement(restored.original).toString()
            assertTrue(source.contains(if (bundle.contains("exportVersion")) "exportVersion" else "Keep profile"))
        }
    }
    @Test fun invalidActionValuesAreRejectedBeforeExecution() {
        val rule = MacroRule(name = "bad", actions = listOf(MacroAction("volume", "999"), MacroAction("torch", "toggle"), MacroAction("shell", "anything")))
        assertEquals(3, MacroRules.validate(rule).size)
    }
    @Test fun presetsAreExecutableOnlyWithinSupportedVocabulary() {
        MacroRules.recipes().forEach { assertEquals(emptyList<String>(), MacroRules.validate(it)); assertFalse(it.enabled) }
    }
    @Test fun importedInputIsBoundedEvenWithoutContentLength() {
        try { MacroRules.read(ByteArray(1_048_577).inputStream()); fail("oversized input accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun cooldownAdmissionIsAtomicAndRestorable() {
        val reservation = CooldownReservations(now = { 1000L })
        val start = CountDownLatch(1); val admitted = AtomicInteger(0)
        val jobs = (1..12).map { thread { start.await(); if (reservation.reserve(7, 30).accepted) admitted.incrementAndGet() } }
        start.countDown(); jobs.forEach { it.join() }; assertEquals(1, admitted.get())
        val restored = CooldownReservations(now = { 2000L }).apply { seed(reservation.snapshot()) }
        assertFalse(restored.reserve(7, 30).accepted)
    }
    @Test fun voiceFrameHandlesBadInputAndRespondsToAmplitude() {
        assertEquals(voiceEdgeStrength(0f), voiceEdgeStrength(Float.NaN))
        assertEquals(voiceEdgeStrength(0f), voiceEdgeStrength(-9f))
        assertTrue(voiceEdgeStrength(1f) > voiceEdgeStrength(.2f))
        assertEquals(1f, voiceEdgeStrength(9f), .0001f)
    }
}
