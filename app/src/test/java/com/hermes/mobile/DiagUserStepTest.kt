package com.hermes.mobile

import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.DiagUserStep
import com.hermes.mobile.ui.tr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * K1 madde 4 — DiagLog ERROR girişlerine eşleşen "kullanıcı adımı" metni.
 * Sınıflandırma saf koddur; eşleşmeyen hata genel adımla cevapsız kalmaz.
 */
class DiagUserStepTest {

    private fun kind(msg: String, tag: String = "bridge") = DiagUserStep.kindFor(tag, msg)

    @Test
    fun `red sebepleri dogru adima baglanir`() {
        assertEquals(DiagUserStep.Kind.AGENT_ACCESS, kind("denied tap (agent access is off)"))
        assertEquals(DiagUserStep.Kind.FULL_CONTROL, kind("denied tap (full control is off)"))
        assertEquals(DiagUserStep.Kind.READ_ONLY, kind("denied type (read-only mode is on)"))
    }

    @Test
    fun `sunucu ve ag hatalari siniflanir`() {
        assertEquals(DiagUserStep.Kind.ACCESSIBILITY, kind("erişilebilirlik servisi koptu", "a11y"))
        assertEquals(DiagUserStep.Kind.SERVER_SETUP, kind("no server profile, not connecting"))
        assertEquals(DiagUserStep.Kind.SERVER_SETUP, kind("no token, not connecting"))
        assertEquals(DiagUserStep.Kind.SERVER_SETUP, kind("could not derive bridge url"))
        assertEquals(DiagUserStep.Kind.TOKEN, kind("failed http=401"))
        assertEquals(DiagUserStep.Kind.TOKEN, kind("failed http=403"))
        assertEquals(DiagUserStep.Kind.SERVER_ERROR, kind("failed http=502"))
        assertEquals(DiagUserStep.Kind.NETWORK, kind("closed code=1006 reason=reset"))
        // Eşleşmeyen hata: genel adım (paylaş) — cevapsız kalmaz.
        assertEquals(DiagUserStep.Kind.SHARE_LOG, kind("bilinmeyen bir hata", "app"))
    }

    @Test
    fun `her ERROR girisi bir adim dondurur, alt seviye uretmez`() {
        val error = DiagLog.Entry(0L, DiagLog.Level.ERROR, "bridge", "failed http=502")
        assertNotNull(error.userStep)
        assertEquals(
            tr(
                "Sunucu tarafında hata var — sunucuyu kontrol et; köprü otomatik yeniden deneyecek.",
                "The server is failing — check the server; the bridge will keep retrying.",
            ),
            error.userStep,
        )
        val crash = DiagLog.Entry(0L, DiagLog.Level.CRASH, "crash", "on thread main: boom")
        assertNotNull(crash.userStep)
        assertNull(DiagLog.Entry(0L, DiagLog.Level.INFO, "bridge", "connected").userStep)
        assertNull(DiagLog.Entry(0L, DiagLog.Level.WARN, "bridge", "retrying").userStep)
    }
}