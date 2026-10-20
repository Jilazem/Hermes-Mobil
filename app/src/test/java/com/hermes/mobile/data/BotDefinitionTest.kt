package com.hermes.mobile.data

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class BotDefinitionTest {
    @Test fun blankModelPreservesInheritance() {
        val bot = botDefinition("Test", "🤖", "Persona", "", "off", "Hello", setOf("read_file"))
        assertEquals("", bot.text("model"))
        assertEquals("off", bot.text("think_mode"))
        assertFalse(bot.containsKey("token"))
    }
    @Test(expected = IllegalArgumentException::class) fun blankPersonaRejected() {
        botDefinition("Test", "", " ", "", "off", "", emptySet())
    }
    @Test(expected = IllegalArgumentException::class) fun nameLimitRejected() {
        botDefinition("a".repeat(49), "", "x", "", "off", "", emptySet())
    }
    @Test fun emojiCountsCodePoints() {
        assertEquals("🤖".repeat(8), botDefinition("T", "🤖".repeat(8), "P", "", "high", "", emptySet()).text("emoji"))
    }
    @Test fun systemHiddenWelcomeOnce() {
        val session = Json.parseToJsonElement("""{"messages":[{"role":"system","content":"secret persona"},{"role":"assistant","content":"welcome"},{"role":"user","content":"Hi"}]}""").jsonObject
        assertEquals(2, botVisibleMessages(session).size)
        assertEquals("welcome", botVisibleMessages(session).first().text("content"))
    }
    @Test fun supplementaryCharactersMatchBackendLimits() {
        val bot = botDefinition("T", "", "P", "🤖".repeat(120), "off", "🤖".repeat(500), emptySet())
        assertEquals("🤖".repeat(500), bot.text("welcome_message"))
    }
    @Test(expected = IllegalArgumentException::class) fun welcomeLimitRejected() {
        botDefinition("T", "", "P", "", "off", "🤖".repeat(501), emptySet())
    }
    @Test(expected = IllegalArgumentException::class) fun tokenRequired() { BotApiClient("http://localhost:8765", "") }
    @Test(expected = IllegalArgumentException::class) fun credentialUrlRejected() { BotApiClient("http://user:pass@localhost:8765", "test") }
}
