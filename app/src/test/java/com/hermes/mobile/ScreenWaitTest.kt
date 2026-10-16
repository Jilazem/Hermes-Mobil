package com.hermes.mobile

import com.hermes.mobile.data.FullControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K2 `screen.wait` — bekle-doğrula sözleşmesi (saf katman).
 *
 * Kilitlenen davranış: en az bir koşul zorunlu, süre saniye cinsinden ve
 * tavanlı (sonsuz bekleme YOK), `stable` tek okumayla "duruldu" sayılmaz,
 * birden çok koşul birlikte gerekir.
 */
class ScreenWaitTest {

    private fun spec(
        text: String? = null,
        app: String? = null,
        stable: String? = null,
        timeout: String? = null,
    ) = FullControl.parseWait(text, app, stable, timeout)

    private fun state(
        labels: List<String> = emptyList(),
        foreground: String = "",
        stablePolls: Int = 0,
    ) = FullControl.WaitState(labels, foreground, stablePolls)

    @Test
    fun `kosulsuz cagri reddedilir`() {
        assertNull(spec())
        assertNull(spec(text = "  ", app = "", stable = "false"))
    }

    @Test
    fun `varsayilan sure 10 saniye`() {
        val s = spec(text = "Tamam")
        assertNotNull(s)
        assertEquals(10_000L, s!!.timeoutMs)
    }

    @Test
    fun `sure saniye cinsinden ve tavanli`() {
        assertEquals(3_000L, spec(text = "x", timeout = "3")!!.timeoutMs)
        // Tavan 30 sn — daha büyük istek asla daha uzun beklemez (bloke yok).
        assertEquals(30_000L, spec(text = "x", timeout = "600")!!.timeoutMs)
        // En az 1 sn; 0 bile 1 sn'ye çekilir.
        assertEquals(1_000L, spec(text = "x", timeout = "0")!!.timeoutMs)
    }

    @Test
    fun `stable istegi bayrak olarak okunur`() {
        assertEquals(true, spec(stable = "true")!!.stable)
        assertEquals(true, spec(stable = "evet")!!.stable)
        assertEquals(false, spec(text = "x", stable = "hayir")!!.stable)
    }

    @Test
    fun `text kosulu gorunen etiketlerde gecince saglanir`() {
        val s = spec(text = "onayla")!!
        assertTrue(FullControl.waitSatisfied(s, state(labels = listOf("İptal", "Onayla"))))
        // Harf duyarsız + kısmi eşleşme.
        assertTrue(FullControl.waitSatisfied(s, state(labels = listOf("ONAYLA ve devam"))))
        assertFalse(FullControl.waitSatisfied(s, state(labels = listOf("İptal"))))
    }

    @Test
    fun `app kosulu ondaki pakette gecince saglanir`() {
        val s = spec(app = "com.android.settings")!!
        assertTrue(
            FullControl.waitSatisfied(s, state(foreground = "com.android.settings/.Main")),
        )
        assertFalse(FullControl.waitSatisfied(s, state(foreground = "com.whatsapp")))
    }

    @Test
    fun `stable tek okumayla sayilmaz`() {
        val s = spec(stable = "true")!!
        assertFalse(FullControl.waitSatisfied(s, state(stablePolls = 0)))
        assertFalse(FullControl.waitSatisfied(s, state(stablePolls = 2)))
        assertTrue(FullControl.waitSatisfied(s, state(stablePolls = FullControl.WAIT_STABLE_POLLS)))
    }

    @Test
    fun `birden cok kosul birlikte gerekir`() {
        val s = spec(text = "Tamam", app = "com.android.settings")!!
        // text var ama app yok → hayır.
        assertFalse(
            FullControl.waitSatisfied(
                s, state(labels = listOf("Tamam"), foreground = "com.whatsapp"),
            ),
        )
        // app var ama text yok → hayır.
        assertFalse(
            FullControl.waitSatisfied(
                s, state(labels = listOf("Ayarlar"), foreground = "com.android.settings"),
            ),
        )
        // ikisi birden → evet.
        assertTrue(
            FullControl.waitSatisfied(
                s, state(labels = listOf("Tamam"), foreground = "com.android.settings"),
            ),
        )
    }

    @Test
    fun `araclar dogru kumele ayrilir`() {
        // wait yalnız okur; scroll_step eylemdir (salt-okunur kipte reddedilir).
        assertTrue(FullControl.WAIT in FullControl.READ_TOOLS)
        assertTrue(FullControl.SCROLL_STEP in FullControl.WRITE_TOOLS)
        assertEquals(null, FullControl.guardReason(true, true, true, FullControl.WAIT))
        assertEquals(
            FullControl.WHY_READ_ONLY,
            FullControl.guardReason(true, true, true, FullControl.SCROLL_STEP),
        )
    }
}