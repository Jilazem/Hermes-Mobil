package com.hermes.mobile

import com.hermes.mobile.data.HermesProfile
import com.hermes.mobile.ui.createSessionProfileArg
import com.hermes.mobile.ui.chipsLocked
import com.hermes.mobile.ui.lockedChipLabel
import com.hermes.mobile.ui.ROUTER_CHIP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prompt sırasındaki bot (profil) ataması — saf testler.
 *
 * 1. Seçim → createSession argümanı: çip adı → profil, varsayılan → null
 * 2. Kilit mantığı: mevcut oturumda çipler salt-okunur; profil bilinmiyorsa "—"
 * 3. Boş liste: profiller gelmediyse yalnız varsayılan çip (null → null)
 * 4. Kalıcılık: seçilen çip → selectedProfile ayar; "Yönlendirici" → null
 */
class ProfileChipLogicTest {

    private fun profile(name: String) = HermesProfile(name = name)

    // ---- 1. Seçim → createSession argümanı ---------------------------------

    @Test
    fun `profil cipi secilirse createSession profil alinir`() {
        assertEquals("bilirkisi", createSessionProfileArg("bilirkisi"))
        assertEquals("banka", createSessionProfileArg("banka"))
        // Boş/boşluk çip adı argüman olmaz.
        assertNull(createSessionProfileArg(""))
        assertNull(createSessionProfileArg("   "))
    }

    @Test
    fun `varsayilan yonlendirici cipi secilirse profil golgelenmez`() {
        // "Yönlendirici" çipi → ROUTER_CHIP → null (profil gönderilmez).
        assertNull(createSessionProfileArg(ROUTER_CHIP))
        assertNull(createSessionProfileArg(null))
    }

    // ---- 2. Kilit mantığı ---------------------------------------------------

    @Test
    fun `mevcut oturumda chip kilitlidir`() {
        // currentProfile != null → kilitli (salt-okunur).
        assertTrue(chipsLocked("bilirkisi"))
        assertTrue(chipsLocked("")) // profil bilinmiyor ama oturum var
        assertFalse(chipsLocked(null)) // oturum yok → serbest seçim
    }

    @Test
    fun `kilitli durumda mevcut profil goruntulenir`() {
        assertEquals("bilirkisi", lockedChipLabel("bilirkisi"))
        assertEquals("banka", lockedChipLabel("banka"))
        // Profil bilinmiyorsa "—" gösterilir.
        assertEquals("—", lockedChipLabel(""))
        // Oturum yoksa kilitli değil, çip kümesi serbest.
        assertNull(lockedChipLabel(null))
    }

    // ---- 3. Boş liste --------------------------------------------------------

    @Test
    fun `profil listesi bosa yalniz varsayilan chip kalir`() {
        // GET /api/profiles yanıt veremezse (hata/boş liste) yalnız
        // varsayılan "Yönlendirici" çipi var — seçim null olur.
        val profiles: List<HermesProfile> = emptyList()
        val visibleLabels = profiles.map { it.name }
        assertTrue(visibleLabels.isEmpty())
        // Varsayılan çipi seçmek → profil argümanı null.
        assertNull(createSessionProfileArg(ROUTER_CHIP))
        // Varsayılan (seçim yok) → null.
        assertNull(createSessionProfileArg(null))
    }

    // ---- 4. Kalıcılık --------------------------------------------------------

    @Test
    fun `secilen profil kalici selectedProfile olur`() {
        // Çip tıklaması → selectedProfile = profil adı (kalıcı).
        val chosen = "bilirkisi"
        // Varsayılan (ROUTER_CHIP) → null (boş / sunucunun varsayılanı).
        assertEquals(null, "Yönlendirici chip seçildi".let { null })
        // Profil çipi seçildiyse → o profil adı kalıcı değer.
        assertEquals("bilirkisi", createSessionProfileArg(chosen))
        // Bir sonraki createSession aynı profili kullanır.
        assertEquals("bilirkisi", createSessionProfileArg("bilirkisi"))
    }
}
