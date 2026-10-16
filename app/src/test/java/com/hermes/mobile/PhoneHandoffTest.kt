package com.hermes.mobile

import com.hermes.mobile.data.FullControl
import com.hermes.mobile.ui.tr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K1 madde 3 — `phone_handoff`: guardReason deseni korunur, devir sessiz
 * değildir (bildirim + kip değişimi açık yazılır).
 */
class PhoneHandoffTest {

    @Test
    fun `phone_handoff tam kontrol aracidir ve kanal acikken duyurulur`() {
        assertTrue(FullControl.isFullControlTool(FullControl.HANDOFF))
        assertTrue(FullControl.HANDOFF in FullControl.ALL_TOOLS)
        assertTrue(
            FullControl.advertise(mayUsePhone = true, fullControl = true)
                .contains(FullControl.HANDOFF),
        )
        // Kapalı kanalda hiçbir araç duyurulmaz.
        assertTrue(FullControl.advertise(mayUsePhone = false, fullControl = true).isEmpty())
    }

    @Test
    fun `handoff salt-okunur kipte reddedilmez, yazma araclari reddedilir`() {
        // guardReason deseni: tek sebep, sıralı — değişmedi.
        assertNull(
            FullControl.guardReason(
                mayUsePhone = true, readOnly = true, fullControl = true,
                tool = FullControl.HANDOFF,
            ),
        )
        assertEquals(
            FullControl.WHY_READ_ONLY,
            FullControl.guardReason(
                mayUsePhone = true, readOnly = true, fullControl = true,
                tool = FullControl.TAP,
            ),
        )
        assertEquals(
            FullControl.WHY_AGENT_OFF,
            FullControl.guardReason(
                mayUsePhone = false, readOnly = true, fullControl = true,
                tool = FullControl.HANDOFF,
            ),
        )
        assertEquals(
            FullControl.WHY_FULL_CONTROL_OFF,
            FullControl.guardReason(
                mayUsePhone = true, readOnly = false, fullControl = false,
                tool = FullControl.HANDOFF,
            ),
        )
    }

    @Test
    fun `handoff bildirimi ekranda sen yap der ve kip degisimini soyler`() {
        val text = FullControl.handoffNotice("2FA kodu bekleniyor")
        assertEquals(
            tr(
                "2FA kodu bekleniyor — 2FA/şifre gibi adımı ekranda sen yap. " +
                    "Salt-okunur kip açıldı: ajan dokunmayacak. Kontrolü geri vermek için " +
                    "Ayarlar → Telefon denetimi'nden «Yalnız okunur»u kapat.",
                "2FA kodu bekleniyor — please complete this step (2FA/password) on the screen " +
                    "yourself. Read-only mode is on: the agent will not touch the phone. Give " +
                    "control back in Settings → Phone control by turning «Read-only» off.",
            ),
            text,
        )
        // Sebepsiz çağrı da tam cümle döner; neden boşken ek parantez yok.
        val bare = FullControl.handoffNotice("")
        assertTrue(bare.contains(tr("ekranda sen yap", "on the screen yourself")))
        assertFalse(bare.startsWith(" — "))
    }
}