package com.hermes.mobile

import com.hermes.mobile.data.BridgePolicy
import com.hermes.mobile.data.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Köprü devralma politikası (tur-7).
 *
 * Sahada ölçülen sorun: relay tek yuva tutuyor; yeni cihaz bağlanınca eskisi
 * düşürülüyor ve düşürülen istemci bunu "ağ hatası" sanıp anında geri
 * dönüyordu. Canlı log'da 2026-09-09'da saatte ~1500 devir, 2026-09-15'te
 * gerçek telefonda sabit ~82 sn'lik ping-pong var. Bu testler düzeltmenin
 * çekirdeğini sabitliyor: 4001/`taken_over` → **yeniden bağlanma YOK**,
 * normal kopma → artan geri çekilmeyle deneme var.
 */
class BridgeTakeoverTest {

    // ── close kodu → politika ────────────────────────────────────────────

    @Test
    fun `4001 close kodu devralma sayilir`() {
        assertEquals(
            BridgePolicy.Decision.TAKEN_OVER,
            BridgePolicy.decision(code = 4001, takenOverSignalled = false, closedByUser = false),
        )
    }

    @Test
    fun `taken_over karesi gorulduyse ag hatasi bile devralma sayilir`() {
        // Kare geldikten sonra soket sessizce ölürse OkHttp onClosed yerine
        // onFailure(-, 1006) verir; bu bir devralmadır, yeniden bağlanma değil.
        assertEquals(
            BridgePolicy.Decision.TAKEN_OVER,
            BridgePolicy.decision(code = 1006, takenOverSignalled = true, closedByUser = false),
        )
        assertEquals(
            BridgePolicy.Decision.TAKEN_OVER,
            BridgePolicy.decision(code = -1, takenOverSignalled = true, closedByUser = false),
        )
    }

    @Test
    fun `normal kopmalar yeniden baglanma sayilir`() {
        for (code in listOf(-1, 1000, 1001, 1006, 1012, 1013)) {
            assertEquals(
                "kod=$code",
                BridgePolicy.Decision.RETRY,
                BridgePolicy.decision(code = code, takenOverSignalled = false, closedByUser = false),
            )
        }
    }

    @Test
    fun `kullanici kapattiysa hicbir kod yeniden baglanma baslatmaz`() {
        for (code in listOf(-1, 1000, 1006, 4001)) {
            assertEquals(
                "kod=$code",
                BridgePolicy.Decision.STOP,
                BridgePolicy.decision(code = code, takenOverSignalled = false, closedByUser = true),
            )
        }
    }

    // ── artan geri çekilme ───────────────────────────────────────────────

    @Test
    fun `geri cekilme merdiveni 2-5-15-30-60 saniye`() {
        assertEquals(2_000L, BridgePolicy.backoffMs(1))
        assertEquals(5_000L, BridgePolicy.backoffMs(2))
        assertEquals(15_000L, BridgePolicy.backoffMs(3))
        assertEquals(30_000L, BridgePolicy.backoffMs(4))
        assertEquals(60_000L, BridgePolicy.backoffMs(5))
    }

    @Test
    fun `merdiven 60 saniyede tavan yapar ve tasmaz`() {
        assertEquals(60_000L, BridgePolicy.backoffMs(6))
        assertEquals(60_000L, BridgePolicy.backoffMs(50))
        // Taşma/sıfır indeks tuzağı: büyük denemede negatif ya da 0 dönmemeli.
        assertTrue("int tasmasinda bile en az 60 sn", BridgePolicy.backoffMs(Int.MAX_VALUE) >= 60_000L)
    }

    @Test
    fun `denemesiz hal hemen dener`() {
        assertEquals(0L, BridgePolicy.backoffMs(0))
        assertEquals(0L, BridgePolicy.backoffMs(-3))
    }

    // ── devralma karesi tanıma ───────────────────────────────────────────

    @Test
    fun `relay devralma karesi taninir`() {
        val frame = """{"event": "taken_over", "reason": "devralindi: yeni cihaz baglandi", """ +
            """"code": 4001, "device": "samsung SM-S918B"}"""
        assertTrue(BridgePolicy.isTakeoverFrame(frame))
    }

    @Test
    fun `arac cagrisi karesi devralma sayilmaz`() {
        val frame = """{"id": "819c4d35ed0b", "tool": "phone_status", "args": {}}"""
        assertFalse(BridgePolicy.isTakeoverFrame(frame))
    }

    @Test
    fun `bozuk ya da ilgisiz kare istisna firlatmaz`() {
        // Bu çağrı HER gelen çerçevede yapılıyor: burada patlamak mesaj yolunu
        // düşürür ve ajanın çağrısı sessizce kaybolur.
        assertFalse(BridgePolicy.isTakeoverFrame(""))
        assertFalse(BridgePolicy.isTakeoverFrame("merhaba"))
        assertFalse(BridgePolicy.isTakeoverFrame("{"))
        assertFalse(BridgePolicy.isTakeoverFrame("[1,2,3]"))
        assertFalse(BridgePolicy.isTakeoverFrame("""{"event": "baska_bir_sey"}"""))
    }

    // ── köprü adresi (sandbox'a yönlenebilmek için) ──────────────────────

    @Test
    fun `acik kopru adresi profil adresini ezer`() {
        val p = ServerProfile(
            name = "t", baseUrl = "http://127.0.0.1:9150", token = "x",
            bridgeUrl = "ws://10.0.2.2:9280/phone",
        )
        assertEquals("ws://10.0.2.2:9280/phone", p.effectiveBridgeUrl)
    }

    @Test
    fun `ev aginda varsayilan 9180 portu kullanilir`() {
        val p = ServerProfile(name = "t", baseUrl = "http://192.168.1.10:9150", token = "x")
        assertEquals("ws://192.168.1.10:9180/phone", p.effectiveBridgeUrl)
    }

    @Test
    fun `disaridan ters vekil yolu kullanilir`() {
        val p = ServerProfile(name = "t", baseUrl = "https://hermes.ornek.tld", token = "x")
        assertEquals("wss://hermes.ornek.tld/phone-bridge/phone", p.effectiveBridgeUrl)
    }

    @Test
    fun `cozumlenemeyen adres bos doner`() {
        // Boş dönmek çağıranın "bağlanma" demesini engelliyor; uydurma adres
        // sonsuz yeniden bağlanma demek olurdu.
        val p = ServerProfile(name = "t", baseUrl = "bu bir adres degil", token = "x")
        assertEquals("", p.effectiveBridgeUrl)
    }
}
