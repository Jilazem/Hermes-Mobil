package com.hermes.mobile

import com.hermes.mobile.data.MessageInboxLogic
import com.hermes.mobile.data.MessageInboxLogic.Item
import com.hermes.mobile.data.MessageInboxLogic.Pick
import com.hermes.mobile.data.PhoneIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V3 mesajlaşma: Türkçe komut kalıpları + yanıt hedefi seçimi (yanlış kişiye gitmemeli). */
class MessagingIntentTest {

    private fun p(s: String) = PhoneIntent.parse(s)

    @Test
    fun `okuma kaliplari`() {
        assertEquals("phone_messages", p("WhatsApp mesajlarımı oku")?.tool)
        assertEquals("whatsapp", p("whatsapp mesajlarımı oku")?.args?.get("app"))
        assertEquals("phone_messages", p("gelen mesajları oku")?.tool)
        assertEquals("telegram", p("telegramda ne var")?.args?.get("app"))
        val who = p("Ayşe ne yazmış")!!
        assertEquals("phone_messages", who.tool)
        assertEquals("Ayşe", who.args["chat"])
    }

    @Test
    fun `yanit kaliplari ozgun metni korur`() {
        val a = p("Ali'ye WhatsApp'tan geliyorum yaz")!!
        assertEquals("phone_reply", a.tool)
        assertEquals("Ali", a.args["chat"]); assertEquals("whatsapp", a.args["app"]); assertEquals("geliyorum", a.args["text"])
        val b = p("whatsapp'tan Ayşe'ye \"5 dk'ya oradayım\" diye cevap ver")!!
        assertEquals("Ayşe", b.args["chat"]); assertEquals("5 dk'ya oradayım", b.args["text"])
        val c = p("Mehmet'e tamam yaz")!!
        assertEquals("phone_reply", c.tool); assertEquals("Mehmet", c.args["chat"]); assertEquals("tamam", c.args["text"])
    }

    @Test
    fun `kesme isaretsiz ve uygulamasiz not mesaja donusmez`() {
        assertNotEquals("phone_reply", p("ekmek al yaz")?.tool)
        assertNotEquals("phone_reply", p("markete git yaz")?.tool)
    }

    private fun item(chat: String, app: String = "WhatsApp", reply: Boolean = true) =
        Item(key = "$app/$chat", app = app, chat = chat, lines = listOf("x"), time = 0, canReply = reply)

    @Test
    fun `hedef secimi tam eslesme kazanir belirsizde gondermez`() {
        val items = listOf(item("Ali Veli"), item("Ali"), item("Alişan"), item("Ayşe", app = "Telegram"))
        assertEquals("Ali", (MessageInboxLogic.pickReplyTarget(items, "", "ali") as Pick.One).item.chat)
        assertTrue(MessageInboxLogic.pickReplyTarget(items.drop(1).take(0) + listOf(item("Ali Veli"), item("Ali Can")), "", "ali") is Pick.Ambiguous)
        assertEquals("Ayşe", (MessageInboxLogic.pickReplyTarget(items, "telegram", "ayse") as Pick.One).item.chat)
        assertTrue(MessageInboxLogic.pickReplyTarget(items, "whatsapp", "ayşe") is Pick.None)
        assertTrue(MessageInboxLogic.pickReplyTarget(listOf(item("Ali", reply = false)), "", "ali") is Pick.None)
    }

    @Test
    fun `turkce katlama ve bicim`() {
        assertEquals("ayse isik", MessageInboxLogic.norm("  AYŞE   IŞIK "))
        assertEquals("istanbul", MessageInboxLogic.norm("İstanbul"))
        assertTrue(MessageInboxLogic.format(listOf(item("Ali")), "").contains("WhatsApp · Ali"))
    }
}
