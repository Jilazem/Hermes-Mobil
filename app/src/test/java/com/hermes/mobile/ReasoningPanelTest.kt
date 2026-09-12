package com.hermes.mobile

import com.hermes.mobile.data.AppSettings
import com.hermes.mobile.data.SLASH_COMMANDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Düşünce panosu + canlı düşünce sözleşmesi testleri:
 *
 * - [parseReasoningOutput] (SAF ayrıtıcı)
 * - [liveThinkingTail] (canlı kuyruk çizimi)
 * - [StreamMeter] faz etiketi (düşünce/yazma aynı sayaç)
 * - [SlashCommands] `/reasoning` ipucu
 * - [AppSettings] varsayılanları (showLiveThinking açık)
 */
class ReasoningPanelTest {

    // ── parseReasoningOutput ─────────────────────────────────────────

    @Test
    fun `reasoning ciktisi basit bicimde ayrisatair`() {
        val st = parseReasoningOutput("Reasoning effort: high\nReasoning display: on")
        assertNotNull(st)
        assertEquals("high", st.effort)
        assertEquals(true, st.displayOn)
    }

    @Test
    fun `reasoning ciktisi bos metinde null degerleri dondurur`() {
        val st = parseReasoningOutput("")
        assertEquals(null, st.effort)
        assertEquals(null, st.displayOn)
    }

    @Test
    fun `reasoning ciktisi bilinmeyen display degeri null olarak ayrisatair`() {
        val st = parseReasoningOutput("Reasoning effort: low\nReasoning display: maybe")
        assertEquals("low", st.effort)
        assertEquals(null, st.displayOn)
    }

    @Test
    fun `reasoning ciktisi display kapatilmis durumda ayrisatair`() {
        val st = parseReasoningOutput("Reasoning effort: medium\nReasoning display: off")
        assertEquals("medium", st.effort)
        assertEquals(false, st.displayOn)
    }

    // ── liveThinkingTail ─────────────────────────────────────────────

    @Test
    fun `canli kuyruk son dort dolu satiri verir`() {
        val text = listOf("a", "", "b", "c", "d", "e", "f").joinToString("\n")
        assertEquals("c\nd\ne\nf", liveThinkingTail(text))
    }

    @Test
    fun `canli kuyruk bos metinde bos doner`() {
        assertEquals("", liveThinkingTail(""))
        assertEquals("", liveThinkingTail("   \n  "))
    }

    @Test
    fun `canli kuyruk tek satirlik metni bozmadan verir`() {
        assertEquals("merhaba", liveThinkingTail("merhaba"))
    }

    // ── StreamMeter faz etiketi ──────────────────────────────────────

    @Test
    fun `stream meter dusunce fazi thinking etiketiyle raporlar`() {
        val meter = StreamMeter()
        meter.delta("abc", 0L, StreamMeter.PHASE_THINKING)
        val snap = meter.snapshot(0L)
        assertEquals(StreamMeter.PHASE_THINKING, snap.phase)
    }

    @Test
    fun `stream meter faz gecisinde sayaclar devam eder`() {
        val meter = StreamMeter()
        meter.delta("aaaa", 0L, StreamMeter.PHASE_THINKING)
        meter.delta("bbbb", 600_000_000L, StreamMeter.PHASE_WRITING)
        val snap = meter.snapshot(600_000_000L)
        assertEquals(2, snap.tokens)
        assertEquals(StreamMeter.PHASE_WRITING, snap.phase)
        assertTrue(snap.active)
    }

    // ── SlashCommands ipucu ──────────────────────────────────────────

    @Test
    fun `reasoning slash komutu tam seviye ipucuyla kayitlidir`() {
        val cmd = SLASH_COMMANDS.first { it.name == "reasoning" }
        assertEquals("none | low | medium | high | xhigh | max", cmd.argumentHint)
        assertTrue(cmd.takesArgument)
    }

    // ── AppSettings varsayılanları ───────────────────────────────────

    @Test
    fun `showLiveThinking varsayilani aciktr`() {
        assertTrue(AppSettings().showLiveThinking)
    }

    @Test
    fun `reasoningLevel varsayilani nulltr`() {
        val s = AppSettings()
        assertFalse(s.showLiveThinking.not()) // ayar okunabilir
        assertEquals(null, s.reasoningLevel)
    }

    // ── Canlı bayrak sözleşmesi ──────────────────────────────────────

    @Test
    fun `canli thinking blokta live dogru, bitenden sonrasinda yanlis`() {
        val live = ChatItem.Thinking("t1", "...", live = true)
        val sealed = live.copy(live = false)
        assertTrue(live.live)
        assertFalse(sealed.live)
        val next = ChatItem.Thinking("t2", "", live = true)
        assertEquals("t2", next.key)
    }
}
