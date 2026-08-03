package com.hermes.mobile

import com.hermes.mobile.data.PhoneIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Niyet çözümleyicinin asıl riski **yanlış yakalama**: ajana gitmesi gereken bir
 * soruyu telefon eylemi sanıp sohbeti kaçırmak. O yüzden testlerin yarısı
 * "bunu yakalamamalı" tarafında.
 */
class PhoneIntentTest {

    private fun tool(s: String) = PhoneIntent.parse(s)?.tool
    private fun arg(s: String, k: String) = PhoneIntent.parse(s)?.args?.get(k)

    @Test
    fun `uygulama acar`() {
        assertEquals("phone_open_app", tool("WhatsApp aç"))
        assertEquals("WhatsApp", arg("WhatsApp'ı aç", "app"))
        assertEquals("Spotify", arg("Spotify uygulamasını başlat", "app"))
        assertEquals("Telegram", arg("aç Telegram", "app"))
    }

    @Test
    fun `yol tarifi verir`() {
        assertEquals("phone_navigate", tool("Kadıköy'e yol tarifi"))
        assertEquals("Kadıköy", arg("Kadıköy'e yol tarifi", "destination"))
        assertEquals("phone_navigate", tool("yol tarifi Ankara"))
    }

    @Test
    fun `alarm kurar`() {
        assertEquals("phone_set_alarm", tool("07:30 alarm"))
        assertEquals("07:30", arg("07:30'a alarm kur", "time"))
        assertEquals("07:30", arg("alarm kur 07.30", "time"))
    }

    @Test
    fun `ayarlari acar`() {
        assertEquals("phone_settings", tool("wifi ayarlarını aç"))
        assertEquals("bluetooth", arg("bluetooth ayarlarını aç", "section"))
    }

    @Test
    fun `web aramasi yapar`() {
        assertEquals("phone_web_search", tool("Google'da hava durumu tahmini ara"))
        assertEquals("hava durumu tahmini", arg("Google'da hava durumu tahmini ara", "query"))
    }

    @Test
    fun `numara cevirir ama isimden aramaz`() {
        assertEquals("phone_dial", tool("05321234567 ara"))
        assertEquals("05321234567", arg("0532 123 45 67 ara", "number"))
        // Rehber izni istemeden isimden arama yapılmamalı.
        assertNull(tool("Ali'yi ara"))
    }

    @Test
    fun `sorulari ajana birakir`() {
        assertNull(tool("wifi ayarlarını nasıl açarım"))
        assertNull(tool("WhatsApp'ı açabilir misin"))
        assertNull(tool("bu dosyayı açar mısın"))
    }

    @Test
    fun `normal istekleri ajana birakir`() {
        assertNull(tool("gateway durumunu özetle"))
        assertNull(tool("son rapor dosyasını oku ve bana özet yaz"))
        assertNull(tool("bugünkü cron işlerinin sonucunu göster"))
        assertNull(tool("merhaba"))
    }

    @Test
    fun `turkce karakter olmadan da anlar`() {
        // Telefonda çoğu kişi böyle yazıyor; bu yüzden ASCII katlaması var.
        assertEquals("phone_settings", tool("wifi ayarlarini ac"))
        assertEquals("phone_open_app", tool("WhatsApp ac"))
        assertEquals("phone_navigate", tool("Kadikoy'e yol tarifi"))
        assertEquals("phone_open_app", tool("Spotify uygulamasini baslat"))
        assertNull(tool("wifi ayarlarini nasil acarim"))
    }

    @Test
    fun `argumani ozgun metinden keser`() {
        // Katlama yalnız eşleştirme için; hedef bozulmadan geçmeli.
        assertEquals("Kadıköy", arg("Kadıköy'e yol tarifi", "destination"))
        assertEquals("Şişli", arg("Şişli'ye yol tarifi", "destination"))
    }

    @Test
    fun `cihaz durumunu okur`() {
        assertEquals("phone_status", tool("pil ne kadar"))
        assertEquals("phone_status", tool("sarj yuzde kac"))
        assertEquals("phone_status", tool("telefonun durumu"))
        assertEquals("ag", arg("hangi wifi'a bağlıyım", "what"))
    }

    @Test
    fun `feneri yakar sondurur`() {
        assertEquals("on", arg("feneri aç", "state"))
        assertEquals("on", arg("el feneri yak", "state"))
        assertEquals("off", arg("feneri kapat", "state"))
        assertEquals("phone_flashlight", tool("ac fener"))
    }

    @Test
    fun `sesi ayarlar`() {
        assertEquals("up", arg("sesi aç", "action"))
        assertEquals("up", arg("sesi yükselt", "action"))
        assertEquals("down", arg("sesi kıs", "action"))
        assertEquals("mute", arg("sustur", "action"))
    }

    @Test
    fun `medyayi yonetir`() {
        assertEquals("pause", arg("müziği duraklat", "action"))
        assertEquals("play", arg("müziği oynat", "action"))
        assertEquals("next", arg("sonraki şarkı", "action"))
        assertEquals("prev", arg("önceki şarkı", "action"))
    }

    @Test
    fun `sayac kurar`() {
        assertEquals("10", arg("10 dakika sayaç", "minutes"))
        assertEquals("5", arg("zamanlayıcı kur 5 dakika", "minutes"))
        // Sayaç ile alarm karışmamalı.
        assertEquals("phone_set_alarm", tool("07:30 alarm"))
    }

    // ── İngilizce ────────────────────────────────────────────────────

    @Test
    fun `english opens apps`() {
        assertEquals("phone_open_app", tool("open WhatsApp"))
        assertEquals("WhatsApp", arg("open WhatsApp", "app"))
        assertEquals("Spotify", arg("launch Spotify", "app"))
        // Belirteçli hedef uygulama değil, ajana ait bir istek.
        assertNull(tool("open the file report.pdf"))
        assertNull(tool("open my last session"))
    }

    @Test
    fun `english navigates`() {
        assertEquals("phone_navigate", tool("directions to Berlin"))
        assertEquals("Berlin", arg("directions to Berlin", "destination"))
        assertEquals("phone_navigate", tool("navigate to the airport"))
    }

    @Test
    fun `english flashlight`() {
        assertEquals("on", arg("turn on the flashlight", "state"))
        assertEquals("off", arg("turn off the flashlight", "state"))
        assertEquals("on", arg("flashlight on", "state"))
    }

    @Test
    fun `english volume and media`() {
        assertEquals("up", arg("volume up", "action"))
        assertEquals("down", arg("turn down the volume", "action"))
        assertEquals("mute", arg("mute", "action"))
        assertEquals("pause", arg("pause the music", "action"))
        assertEquals("next", arg("next track", "action"))
    }

    @Test
    fun `english status timer settings search`() {
        assertEquals("phone_status", tool("battery"))
        assertEquals("phone_status", tool("battery level"))
        assertEquals("10", arg("set a timer for 10 minutes", "minutes"))
        assertEquals("wifi", arg("open wifi settings", "section"))
        assertEquals("07:30", arg("set an alarm at 07:30", "time"))
        assertEquals("best android ide", arg("google best android ide", "query"))
    }

    @Test
    fun `english questions go to the agent`() {
        assertNull(tool("how do I turn on the flashlight"))
        assertNull(tool("what is my battery health"))
        assertNull(tool("why is the volume so low"))
        assertNull(tool("explain the battery settings"))
    }

    @Test
    fun `english agent requests are untouched`() {
        assertNull(tool("summarize the gateway status"))
        assertNull(tool("list the connected MCP tools"))
        assertNull(tool("write a report from the last file"))
    }

    @Test
    fun `uzun metni ajana birakir`() {
        val uzun = "Bu bir rapor taslağı, lütfen incele ve eksikleri tamamla, " +
            "ardından dosyayı kaydet ve bana bir özet ver aç"
        assertNull(tool(uzun))
    }
}
