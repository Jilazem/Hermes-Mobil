package com.hermes.mobile

import com.hermes.mobile.ui.SendFeedbackLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur22 madde-2 (r1 düzeltme) — gönderme anı 1sn geçici geri bildirim kuralı.
 *
 * Repo'da Compose-test altyapısı yok (tur22 kararı): karar mantığı saf
 * katmanda kilitlenir; görsel kanıt 45-gonder-burst karelerindedir.
 */
class SendFeedbackTur22Test {

    @Test fun `pencere acik — gonderme ani 1sn gorunur`() {
        assertTrue(SendFeedbackLogic.visible(1000L, 1000L))       // t=0
        assertTrue(SendFeedbackLogic.visible(1999L, 1000L))       // t=999ms
        assertTrue(SendFeedbackLogic.visible(1500L, 1000L))       // t=yarim
    }

    @Test fun `pencere kapali — 1sn dolunca temizlenir`() {
        assertFalse(SendFeedbackLogic.visible(2000L, 1000L))      // t=1000ms tam
        assertFalse(SendFeedbackLogic.visible(2500L, 1000L))      // gecikme
    }

    @Test fun `fail-closed — saat geri sarsa takilmaz, hemen temiz`() {
        // elapsedRealtime geri sararsa (anlık durum, uç kullanici yok ama
        // fail-closed): sonsuz ✓ yerine HEMEN temizle.
        assertFalse(SendFeedbackLogic.visible(900L, 1000L))
        assertFalse(SendFeedbackLogic.visible(0L, Long.MAX_VALUE / 2))
    }

    @Test fun `pencere suresi kartla ayni — 1000ms`() {
        assertEquals(1000L, SendFeedbackLogic.FLASH_MS)
    }
}
