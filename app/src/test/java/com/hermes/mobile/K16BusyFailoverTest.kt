package com.hermes.mobile

import com.hermes.mobile.data.HermesApiException
import com.hermes.mobile.data.LocalModelException
import com.hermes.mobile.data.LocalModelLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K16 — patron önceliği: "yoğunluk" patronu engellemesin.
 *
 * Kural: 429/502/503 = uç DOYGUN (sıra dolu), adres ÖLÜ değil → istemci
 * sıradaki (uzak) adrese düşer, AddressHealth zehirlenmez, kullanıcıya
 * SEBEP bildirilir. Erişilemezlik/ölü uçta eski kilit davranışı korunur.
 */
class K16BusyFailoverTest {

    private fun t(tr: String, en: String): String = tr

    @Test
    fun `429 502 503 yogun kodudur`() {
        assertTrue(LocalModelLogic.isBusyCode(429))
        assertTrue(LocalModelLogic.isBusyCode(502))
        assertTrue(LocalModelLogic.isBusyCode(503))
    }

    @Test
    fun `diger kodlar yogun degildir`() {
        assertFalse(LocalModelLogic.isBusyCode(401))
        assertFalse(LocalModelLogic.isBusyCode(404))
        assertFalse(LocalModelLogic.isBusyCode(500))
        assertFalse(LocalModelLogic.isBusyCode(200))
    }

    @Test
    fun `HermesApiException busy kodunu tasiyor`() {
        assertTrue(HermesApiException(503, "/api/status", "").isBusy)
        assertTrue(HermesApiException(429, "/api/status", "").isBusy)
        assertFalse(HermesApiException(401, "/api/status", "").isBusy)
        assertFalse(HermesApiException(500, "/api/status", "").isBusy)
    }

    @Test
    fun `LocalModelException yalniz doygunlukta busyCode tasir`() {
        val busy = LocalModelException("Yerel model yoğun (503)", busyCode = 503)
        assertEquals(503, busy.busyCode)
        val dead = LocalModelException("Yerel node'a ulaşılamadı")
        assertNull(dead.busyCode)
    }

    @Test
    fun `busy bildirimi kodu ve uzak ucu soyluyor`() {
        val tr = LocalModelLogic.busyFallbackNotice(503, ::t)
        assertTrue(tr.contains("503"))
        assertTrue(tr.contains("uzak"))
        assertTrue(tr.contains("yoğun"))
        assertNotNull(tr)
    }

    @Test
    fun `httpError doygunlukta yogun diyor`() {
        assertTrue(LocalModelLogic.httpError(503, null, ::t).contains("yoğun"))
        assertTrue(LocalModelLogic.httpError(429, null, ::t).contains("yoğun"))
        // 500 hâlâ genel model hatası — yoğun değil.
        assertTrue(LocalModelLogic.httpError(500, null, ::t).contains("hatası"))
    }
}
