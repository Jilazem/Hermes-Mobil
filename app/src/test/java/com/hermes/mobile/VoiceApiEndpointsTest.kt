package com.hermes.mobile

import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.data.VoiceApiEndpoints
import com.hermes.mobile.data.VoiceHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-11 — `voice_api` adres adayları, URL kurulumu ve hata metinleri.
 *
 * Sözleşme (`000-TEMP/ses-api-sozlesmesi.md`): yerel `http://<host>:8174`,
 * dış `https://<host>/voice-api`, kimlik `X-Hermes-Session-Token`, uçlar
 * `/health`, `/transcribe`, `/synthesize`.
 */
class VoiceApiEndpointsTest {

    private val t: (String, String) -> String = { tr, _ -> tr }

    private fun profile(base: String, remote: String = "") = ServerProfile(
        name = "test",
        baseUrl = base,
        remoteUrl = remote,
        token = "tok",
    )

    @Test
    fun `ev agi adresi dogrudan 8174 portuna gider`() {
        val p = profile("http://192.168.1.101:9150")
        assertEquals(listOf("http://192.168.1.101:8174"), VoiceApiEndpoints.candidates(p))
    }

    @Test
    fun `dis adres voice-api yolundan gider`() {
        val p = profile("https://hermes.example.pro")
        assertEquals(listOf("https://hermes.example.pro/voice-api"), VoiceApiEndpoints.candidates(p))
    }

    @Test
    fun `LAN once uzak sonra - sira korunur`() {
        val p = profile("http://192.168.1.101:9150", remote = "https://hermes.example.pro")
        assertEquals(
            listOf(
                "http://192.168.1.101:8174",
                "https://hermes.example.pro/voice-api",
            ),
            VoiceApiEndpoints.candidates(p),
        )
    }

    @Test
    fun `elle verilen adres tek basina kazanir`() {
        val p = profile("http://192.168.1.101:9150")
        assertEquals(
            listOf("http://10.0.2.2:8174"),
            VoiceApiEndpoints.candidates(p, explicit = "http://10.0.2.2:8174"),
        )
    }

    @Test
    fun `elle verilen adreste sondaki egik cizgi kirpilir`() {
        val p = profile("http://192.168.1.101:9150")
        assertEquals(
            listOf("http://10.0.2.2:8174"),
            VoiceApiEndpoints.candidates(p, explicit = "http://10.0.2.2:8174/"),
        )
    }

    @Test
    fun `son calisan adres ilk aday olur`() {
        val p = profile("http://192.168.1.101:9150", remote = "https://hermes.example.pro")
        assertEquals(
            listOf("https://hermes.example.pro/voice-api", "http://192.168.1.101:8174"),
            VoiceApiEndpoints.candidates(p, preferred = "https://hermes.example.pro/voice-api"),
        )
    }

    @Test
    fun `hatirlanan lan adresi voice-api tekrari uretmez`() {
        // /voice-api zaten ekli verilirse ikinci kez eklenmemeli (tur-10 dersi).
        val p = profile("https://hermes.example.pro")
        val out = VoiceApiEndpoints.candidates(p, preferred = "https://hermes.example.pro/voice-api")
        assertEquals(listOf("https://hermes.example.pro/voice-api"), out)
        assertFalse(out.any { it.contains("/voice-api/voice-api") })
    }

    @Test
    fun `hatirlanan 8174 adresi normalize edilir`() {
        assertEquals(
            "http://192.168.1.101",
            VoiceApiEndpoints.normalizePreferred("http://192.168.1.101:8174"),
        )
        assertEquals(
            "https://hermes.example.pro",
            VoiceApiEndpoints.normalizePreferred("https://hermes.example.pro/voice-api/"),
        )
        assertEquals("", VoiceApiEndpoints.normalizePreferred("  "))
    }

    @Test
    fun `adres bos ya da bozuksa aday uretilmez`() {
        val p = profile("")
        assertTrue(VoiceApiEndpoints.candidates(p).isEmpty())
    }

    @Test
    fun `uc url leri dogru kurulur`() {
        val base = "http://192.168.1.101:8174"
        assertEquals("$base/health", VoiceApiEndpoints.health(base))
        assertEquals("$base/transcribe", VoiceApiEndpoints.transcribe(base))
        assertEquals("$base/synthesize", VoiceApiEndpoints.synthesize(base))
    }

    @Test
    fun `taban sonundaki egik cizgi url i bozmaz`() {
        val base = "https://hermes.example.pro/voice-api/"
        assertEquals("https://hermes.example.pro/voice-api/health", VoiceApiEndpoints.health(base))
        assertEquals("https://hermes.example.pro/voice-api/synthesize", VoiceApiEndpoints.synthesize(base))
    }

    @Test
    fun `join yol uretir ve cift egik cizgi olmaz`() {
        assertEquals("http://h:8174/x", VoiceApiEndpoints.join("http://h:8174/", "/x"))
        assertEquals("http://h:8174/x", VoiceApiEndpoints.join("http://h:8174", "x"))
    }

    @Test
    fun `403 token mesaji uretir`() {
        val line = VoiceApiEndpoints.describe(
            listOf(VoiceApiEndpoints.Attempt("http://h:8174", 403, "forbidden")),
            t,
        )
        assertTrue(line.contains("403"))
        assertTrue(line.contains("token"))
    }

    @Test
    fun `401 de token mesajina duser`() {
        val line = VoiceApiEndpoints.describe(
            listOf(VoiceApiEndpoints.Attempt("http://h:8174", 401)),
            t,
        )
        assertTrue(line.contains("token"))
    }

    @Test
    fun `404 ses ucu yok mesaji uretir`() {
        val line = VoiceApiEndpoints.describe(
            listOf(VoiceApiEndpoints.Attempt("http://h:8174", 404)),
            t,
        )
        assertTrue(line.contains("404"))
        assertTrue(line.contains("voice_api"))
    }

    @Test
    fun `hicbir adres yoksa ayar mesaji`() {
        val line = VoiceApiEndpoints.describe(emptyList(), t)
        assertTrue(line.contains("Ses ucu"))
        assertTrue(line.contains("Ayarlar"))
    }

    @Test
    fun `erisilemeyen adres 8174 ve voice-api yi anlatir`() {
        val line = VoiceApiEndpoints.describe(
            listOf(VoiceApiEndpoints.Attempt("http://h:8174", -1, "timeout")),
            t,
        )
        assertTrue(line.contains("8174"))
        assertTrue(line.contains(VoiceApiEndpoints.EXTERNAL_PATH))
    }

    @Test
    fun `denenen adresler mesaja gomulur`() {
        val line = VoiceApiEndpoints.describe(
            listOf(
                VoiceApiEndpoints.Attempt("http://a:8174", -1),
                VoiceApiEndpoints.Attempt("https://b/voice-api", 500),
            ),
            t,
        )
        assertTrue(line.contains("http://a:8174"))
        assertTrue(line.contains("https://b/voice-api"))
        assertTrue(line.contains("500"))
    }

    @Test
    fun `attempt ok ve reachable ayrimi`() {
        assertTrue(VoiceApiEndpoints.Attempt("b", 200).ok)
        assertTrue(VoiceApiEndpoints.Attempt("b", 200).reachable)
        assertFalse(VoiceApiEndpoints.Attempt("b", 500).ok)
        assertTrue(VoiceApiEndpoints.Attempt("b", 500).reachable)
        assertFalse(VoiceApiEndpoints.Attempt("b", -1).reachable)
    }

    @Test
    fun `codeText iki bicimi ayirir`() {
        assertEquals("HTTP 403", VoiceApiEndpoints.codeText(403))
        assertEquals("ulaşılamadı", VoiceApiEndpoints.codeText(-1))
    }

    @Test
    fun `health satiri motorlari listeler`() {
        val h = VoiceHealth(ok = true, stt = "acik", engines = mapOf("kahya" to "kapali"))
        val line = VoiceApiEndpoints.healthLine(h, t)
        assertTrue(line.contains("metinleştirme açık"))
        assertTrue(line.contains("kahya=kapali"))
    }

    @Test
    fun `health motorlar kapaliysa ilk cagri uyarisi verir`() {
        val line = VoiceApiEndpoints.healthLine(VoiceHealth(ok = false), t)
        assertTrue(line.contains("motor"))
    }

    @Test
    fun `sozlesme tavanlari sabit`() {
        // Sözleşme: yükleme <=60 sn; ilk sentez soğukken 173-187 sn →
        // okuma zaman aşımı 300 sn'den kısa olamaz.
        assertEquals(60_000L, VoiceApiEndpoints.MAX_RECORD_MS)
        assertTrue(VoiceApiEndpoints.SYNTH_TIMEOUT_MS >= 300_000L)
        assertEquals(8174, VoiceApiEndpoints.LAN_PORT)
        assertEquals("/voice-api", VoiceApiEndpoints.EXTERNAL_PATH)
    }
}
