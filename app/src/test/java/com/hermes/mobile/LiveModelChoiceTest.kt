package com.hermes.mobile

import com.hermes.mobile.data.LiveModelLogic
import com.hermes.mobile.data.LocalModelLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-21 — sesli asistan model seçici (saf): sağlayıcı çözümleme, model
 * kilidi, yerel LLM sözleşme yardımcıları (model picker / chat gövdesi /
 * hata satırları) ve JSON kaçış/çözüm.
 */
class LiveModelChoiceTest {

    private val t: (String, String) -> String = { tr, _ -> tr }

    // ── Sağlayıcı çözümleme ───────────────────────────────────────────

    @Test
    fun `saglayici varsayilani gemini — eski davranis bozulmaz`() {
        assertEquals(LiveModelLogic.Provider.GEMINI, LiveModelLogic.Provider.fromId(null))
        assertEquals(LiveModelLogic.Provider.GEMINI, LiveModelLogic.Provider.fromId("gemini"))
        assertEquals(LiveModelLogic.Provider.GEMINI, LiveModelLogic.Provider.fromId("bozuk-deger"))
        assertEquals(LiveModelLogic.Provider.YEREL, LiveModelLogic.Provider.fromId("yerel"))
        assertEquals(LiveModelLogic.Provider.YEREL, LiveModelLogic.Provider.fromId(" YEREL "))
    }

    @Test
    fun `secenekler iki dilde ve sirali`() {
        val opts = LiveModelLogic.options(t)
        assertEquals(listOf("yerel", "gemini"), opts.map { it.first })
        assertTrue(opts[0].second.contains("Yerel"))
        assertEquals("Gemini", opts[1].second)
    }

    @Test
    fun `kilit — saglik bozukken bile YEREL'de kalir, Gemini'ye gecmez`() {
        val r = LiveModelLogic.resolveActive(
            LiveModelLogic.Provider.YEREL,
            LiveModelLogic.Health.Down,
            localConfigured = true,
            t = t,
        )
        assertEquals(LiveModelLogic.Provider.YEREL, r.provider)
        assertTrue("hata gösterilmeli", r.error != null)
        assertTrue(r.error!!.contains("sessiz Gemini'ye geçilmez") || r.error!!.contains("kontrol"))
    }

    @Test
    fun `adres yoksa ayri hata — yine YEREL'de kalir`() {
        val r = LiveModelLogic.resolveActive(
            LiveModelLogic.Provider.YEREL,
            LiveModelLogic.Health.Unknown,
            localConfigured = false,
            t = t,
        )
        assertEquals(LiveModelLogic.Provider.YEREL, r.provider)
        assertTrue(r.error!!.contains("adres"))
    }

    @Test
    fun `gemini seciliyse saglik tek etkisi yoktur`() {
        val r = LiveModelLogic.resolveActive(
            LiveModelLogic.Provider.GEMINI,
            LiveModelLogic.Health.Down,
            localConfigured = false,
            t = t,
        )
        assertEquals(LiveModelLogic.Provider.GEMINI, r.provider)
        assertNull(r.error)
    }

    @Test
    fun `durum noktasi renkleri eslemesi`() {
        assertEquals("green", LiveModelLogic.dotColor(LiveModelLogic.Health.Ok))
        assertEquals("red", LiveModelLogic.dotColor(LiveModelLogic.Health.Down))
        assertEquals("grey", LiveModelLogic.dotColor(LiveModelLogic.Health.Unknown))
    }

    @Test
    fun `saglik etiketleri`() {
        assertEquals("Bağlı", LiveModelLogic.healthLabel(LiveModelLogic.Health.Ok, t))
        assertTrue(LiveModelLogic.healthLabel(LiveModelLogic.Health.Down, t).contains("Bağlı değil"))
        assertEquals("Denenmedi", LiveModelLogic.healthLabel(LiveModelLogic.Health.Unknown, t))
    }

    // ── Yerel LLM sözleşme yardımcıları ────────────────────────────────

    @Test
    fun `registry sozlesmesi — beklenen model esleşirse secilir`() {
        val json = """{"object":"list","data":[{"id":"Qwen/Qwen3.8-Flash-Next","object":"model"}]}"""
        val served = LocalModelLogic.parseServedId(json)
        assertEquals("Qwen/Qwen3.8-Flash-Next", served)
        assertEquals(served, LocalModelLogic.pickModel(null, served))
    }

    @Test
    fun `model adi farkliysa null — sessiz baska model YOK`() {
        val served = "some/other-model"
        assertNull(LocalModelLogic.pickModel("Qwen/Qwen3.8-Flash-Next", served))
        // Ad eşleşmesi yalnız tam eşitlik (regex değil).
        assertNull(LocalModelLogic.pickModel("Qwen", "Qwen/Qwen3.8-Flash-Next"))
    }

    @Test
    fun `kullanicinin istegi baska model — uyari mesaji net`() {
        val m = LocalModelLogic.modelMismatchMessage("X", "Y", t)
        assertTrue(m.contains("beklenen \"X\"") && m.contains("servis edilen \"Y\""))
        // Kilidi kırma: mesaj otomatik geçiş ÖNERMEMELİ.
        assertTrue(!m.contains("otomatik geç"))
    }

    @Test
    fun `http hata satiri kodu tasir`() {
        assertTrue(LocalModelLogic.httpError(502, null, t).contains("502"))
        assertTrue(LocalModelLogic.httpError(404, "model not found", t).contains("404"))
        assertTrue(LocalModelLogic.httpError(404, "x".repeat(500), t).length < 250)
    }

    @Test
    fun `ulasilamama satiri adresi ve aksiyonu soyler`() {
        val m = LocalModelLogic.unreachableMessage("http://192.168.1.99:8888/v1/models", "connect refused", t)
        assertTrue(m.contains("192.168.1.99"))
        assertTrue(m.contains("aynı ağda"))
    }

    @Test
    fun `chat govdesi — kacislar dogru, icerik geri cozulur`() {
        val body = LocalModelLogic.chatBody(
            model = "Qwen/Qwen3.8-Flash-Next",
            system = "Sen Hermes'sin\nkısa \"konuş\"",
            userText = "Merhaba \"canım\"\n nasılsın?",
        )
        assertTrue(body.startsWith("{\"model\":\"Qwen/Qwen3.8-Flash-Next\""))
        assertTrue(body.contains("\\\"canım\\\""))
        // Geri çözüm — round-trip.
        val fake = "{\"choices\":[{\"message\":{\"content\":\"İyiyim\\n\\\"her şey\\\" yolunda\"}}]}"
        assertEquals("İyiyim\n\"her şey\" yolunda", LocalModelLogic.extractContent(fake))
    }

    @Test
    fun `content yoksa null — cagiran hata uretir`() {
        assertNull(LocalModelLogic.extractContent("""{"error":"patladı"}"""))
    }

    @Test
    fun `unicode kacisi cozulur`() {
        // Gerçek JSON: content = "A\u011F 99\tOK" (unicode + tab kacislari).
        val j = "{\"choices\":[{\"message\":{\"content\":\"A\\u011F 99\\tOK\"}}]}"
        assertEquals("Ağ 99\tOK", LocalModelLogic.extractContent(j))
    }

    @Test
    fun `govde ipucu 200 ile sinirli`() {
        assertEquals(200, LocalModelLogic.bodySnippet("z".repeat(999))?.length)
        assertNull(LocalModelLogic.bodySnippet("   "))
    }
}
