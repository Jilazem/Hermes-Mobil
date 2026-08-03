package com.hermes.mobile

import com.hermes.mobile.data.PhoneIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Okuma niyetleri.
 *
 * Bu desenler `QUESTION` korumasının **önünde** çalışıyor: "bugün ne
 * kaçırdım?" soru biçiminde ama cevabı telefonda, ajanda değil. Sıra
 * bozulursa hepsi sessizce ajana gitmeye başlar ve kullanıcı yalnız
 * "bilmiyorum" cevabı alır — testler o sırayı da koruyor.
 */
class PhoneReadIntentTest {

    private fun tool(s: String) = PhoneIntent.parse(s)?.tool

    @Test
    fun `bildirim sorulari cihazda cozulur`() {
        listOf(
            "bugün ne kaçırdım", "bugün ne kaçırdım?", "bildirimler",
            "bildirimlerim neler", "yeni mesaj var mı",
            "what did I miss", "any new notifications", "notifications",
        ).forEach { assertEquals(it, "phone_notifications", tool(it)) }
    }

    @Test
    fun `konum sorulari cihazda cozulur`() {
        listOf("neredeyim", "neredeyim?", "konumum ne", "where am I", "my location")
            .forEach { assertEquals(it, "phone_location", tool(it)) }
    }

    @Test
    fun `takvim okuma`() {
        listOf(
            "bugün ne var", "takvimim", "randevularım",
            "what's on my schedule", "my schedule today",
        ).forEach { assertEquals(it, "phone_calendar", tool(it)) }
    }

    @Test
    fun `yarin sorulunca 48 saate bakilir`() {
        assertEquals("48", PhoneIntent.parse("yarın ne var")?.args?.get("hours"))
        assertEquals("24", PhoneIntent.parse("bugün ne var")?.args?.get("hours"))
    }

    @Test
    fun `takvime ekleme okuma sayilmaz`() {
        // Bu `phone_add_event`in işi; okuma dalı kapmamalı.
        assertEquals("phone_add_event", tool("takvime toplantı ekle"))
    }

    @Test
    fun `pano okuma`() {
        listOf("panoda ne var", "panoyu oku", "what's in my clipboard")
            .forEach { assertEquals(it, "phone_clipboard_read", tool(it)) }
    }

    @Test
    fun `isimden numara`() {
        val a = PhoneIntent.parse("Ahmet'in numarası")
        assertEquals("phone_contacts", a?.tool)
        assertEquals("ahmet", a?.args?.get("name"))

        val b = PhoneIntent.parse("number for Ahmet")
        assertEquals("phone_contacts", b?.tool)
        assertEquals("ahmet", b?.args?.get("name"))
    }

    @Test
    fun `ajana ait sorular cihazda kalmaz`() {
        // Okuma dalını fazla genişletmek en büyük risk: ajana gitmesi gereken
        // her soruyu telefon yanıtlamaya kalkarsa asistan işe yaramaz hale gelir.
        listOf(
            "sunucunun durumu ne",
            "bugün hangi cron işleri çalıştı",
            "son raporu özetle",
            "what did the agent do yesterday",
            "summarize the latest report",
        ).forEach { assertNull(it, tool(it)) }
    }
}
