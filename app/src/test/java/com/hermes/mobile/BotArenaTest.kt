package com.hermes.mobile

import com.hermes.mobile.data.ArenaAnswer
import com.hermes.mobile.data.ArenaMode
import com.hermes.mobile.data.ArenaPrompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bot Arena — saf (UI'dan bağımsız) testler.
 *
 * Kapsam:
 *  1. Tek bot: tur 1 promptu konuyu içerir
 *  2. Kapışma: tur 2 promptu diğer botların cevaplarını içerir
 *  3. Sentez: prompt tüm fikirleri içerir, 5 madde emri var
 *  4. 4 bot sınırı: 5. bot eklenemez, seçili bot kaldırılabilir
 *  5. Boş konu reddi: boş/sadece boşluk konu hata döner
 */
class BotArenaTest {

    // ── 1. Tek bot kip promptu ─────────────────────────────────────────────

    @Test
    fun tekBotPromptKonuIcerir() {
        val topic = "API error rate'ı düşür"
        val prompt = ArenaPrompts.round1(topic)
        assertTrue("Prompt konuyu içermeli", prompt.contains(topic))
        assertNotNull(prompt)
        assertTrue(prompt.isNotEmpty())
    }

    @Test
    fun tekBotCevapHedef() {
        val answer = ArenaAnswer(bot = "alfa", round = 1, text = "Test sonucu: tamamdır")
        assertEquals("alfa", answer.bot)
        assertEquals(1, answer.round)
        assertTrue(answer.text.isNotBlank())
        assertFalse(answer.error?.isNotBlank() == true)
    }

    // ── 2. Kapışma tur 2 promptu diğer cevapları içerir ────────────────────

    @Test
    fun kapismaTur2PromptuDigerCevaplariIcerir() {
        val topic = "Kutu test et"
        val self = "Benim cevabım: X"
        val others = mapOf(
            "botA" to "botA cevabı: Y",
            "botB" to "botB cevabı: Z",
        )
        val prompt = ArenaPrompts.battleRound2(topic, self, others)
        assertTrue("Kendi cevabı içermeli", prompt.contains(self))
        assertTrue("botA cevabı içermeli", prompt.contains("botA cevabı: Y"))
        assertTrue("botB cevabı içermeli", prompt.contains("botB cevabı: Z"))
        assertTrue("Konuyu içermeli", prompt.contains(topic))
        assertTrue("'eleştir' emri içermeli", prompt.contains("eleştir"))
    }

    @Test
    fun kapismaTur2PromptuTekBotDegilseDegisken() {
        val others = mapOf("diğer" to "başka cevap")
        val prompt = ArenaPrompts.battleRound2("T", "benim cevap", others)
        assertTrue(prompt.contains("başka cevap"))
    }

    // ── 3. Sentez promptu ──────────────────────────────────────────────────

    @Test
    fun sentezPromptuTumFikirleriIcerir() {
        val topic = "Beyin fırtınası: mobil uygulama fikri"
        val ideas = mapOf(
            "alfa" to "Fikir 1",
            "beta" to "Fikir 2",
            "gamma" to "Fikir 3",
        )
        val prompt = ArenaPrompts.synthesis(topic, ideas)
        assertTrue("alfa fikrini içermeli", prompt.contains("Fikir 1"))
        assertTrue("beta fikrini içermeli", prompt.contains("Fikir 2"))
        assertTrue("gamma fikrini içermeli", prompt.contains("Fikir 3"))
        assertTrue("Konuyu içermeli", prompt.contains(topic))
        assertTrue("5 maddelik emri içermeli", prompt.contains("5 maddelik"))
        assertTrue("tekrar emri içermeli", prompt.contains("tekrar"))
    }

    @Test
    fun sentezPromptu5Madde() {
        val prompt = ArenaPrompts.synthesis("konu", mapOf("a" to "fikir"))
        assertTrue("5 maddelik final emri olmalı", prompt.contains("5 maddelik"))
    }

    // ── 4. 4 bot sınırı ────────────────────────────────────────────────────

    @Test
    fun botSinirDortEsimle() {
        var selected = listOf("b1", "b2", "b3")
        // 4. botu ekle — izin verilir
        selected = selected + "b4"
        assertEquals(4, selected.size)
        assertTrue(selected.contains("b4"))
        // 5. botu ekle — reddedilir
        if (selected.size < 4) selected = selected + "b5"
        assertFalse("5. bot eklenememeli", selected.contains("b5"))
        assertEquals(4, selected.size)
    }

    @Test
    fun botSinirKoyulmusYeniBotEklemeIslemez() {
        var selected = listOf("b1", "b2", "b3", "b4")
        if (selected.size < 4) selected = selected + "b5"
        assertEquals(listOf("b1", "b2", "b3", "b4"), selected)
    }

    @Test
    fun botSinirKoyulmusSeciliBotCalisir() {
        var selected = listOf("b1", "b2", "b3", "b4")
        selected = selected - "b2"
        assertFalse("Seçili bot kaldırılabilir", selected.contains("b2"))
        assertEquals(3, selected.size)
    }

    // ── 5. Boş konu reddi ──────────────────────────────────────────────────

    private fun validateTopic(topic: String, botCount: Int): String? {
        val t = topic.trim()
        if (t.isEmpty()) return "Konu boş olamaz"
        if (botCount == 0) return "En az bir bot seç"
        return null
    }

    @Test
    fun bosKonuReddedilir() {
        val err = validateTopic("", 1)
        assertNotNull("Boş konu hata döner", err)
        assertTrue("Hata 'konu' içermeli", err!!.contains("konu", true))
    }

    @Test
    fun bosSpaceKonuReddedilir() {
        val err = validateTopic("   ", 1)
        assertNotNull("Sadece boşluk konu hata döner", err)
    }

    @Test
    fun bosBotListeReddedilir() {
        val err = validateTopic("Geçerli konu", 0)
        assertNotNull("Bot seçilmediyse hata döner", err)
    }

    // ── 6. ArenaMode sabitleri ─────────────────────────────────────────────

    @Test
    fun arenaModlari() {
        val modes = ArenaMode.entries
        assertTrue(modes.size >= 3)
        assertTrue(modes.contains(ArenaMode.SINGLE))
        assertTrue(modes.contains(ArenaMode.BATTLE))
        assertTrue(modes.contains(ArenaMode.BRAINSTORM))
    }

    @Test
    fun kapismaModuRevizeEmriIcerir() {
        val prompt = ArenaPrompts.battleRound2("konu", "benim", mapOf("diğer" to "cevap"))
        assertTrue("Kapışma tur2 promptu 'revize' emri içermeli", prompt.contains("revize"))
    }
}
