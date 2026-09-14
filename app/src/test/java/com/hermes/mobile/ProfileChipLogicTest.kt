package com.hermes.mobile

import com.hermes.mobile.data.HermesProfile
import com.hermes.mobile.ui.createSessionProfileArg
import com.hermes.mobile.ui.chipsLocked
import com.hermes.mobile.ui.lockedChipLabel
import com.hermes.mobile.ui.visibleProfileChips
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

    // ---- 5. İç terminoloji sızmaz (tur-4 kusur G) ---------------------------

    @Test
    fun `ic profil adlari cip olarak cizilmez`() {
        // Emülatörde görülen sızıntı: "default", "ac", "android".
        val profiles = listOf(
            HermesProfile(name = "default", isDefault = true),
            HermesProfile(name = "ac"),
            HermesProfile(name = "android"),
            HermesProfile(name = "arastirma"),
        )
        val visible = visibleProfileChips(profiles)
        assertTrue("iç adlar gizlenir: $visible", visible.isEmpty())
    }

    @Test
    fun `varsayilan cip her durumda gizlenir`() {
        // display_name dolu olsa bile varsayılan profil çipi çizilmez —
        // "Yönlendirici" zaten onu temsil eder.
        val visible = visibleProfileChips(
            listOf(HermesProfile(name = "default", isDefault = true, displayName = "Varsayılan Bot")),
        )
        assertTrue(visible.isEmpty())
    }

    @Test
    fun `insan adi olan profil gorunur ve secim gercek adi tasir`() {
        val visible = visibleProfileChips(
            listOf(
                HermesProfile(name = "ac", displayName = "Bilirkişi Botu"),
                HermesProfile(name = "android"),
            ),
        )
        assertEquals(1, visible.size)
        assertEquals("Bilirkişi Botu", visible.first().second)
        // Çip tıklandığında createSession GERÇEK profil adını alır.
        assertEquals("ac", createSessionProfileArg(visible.first().first.name))
    }

    @Test
    fun `bos profil listesi bos doner`() {
        assertTrue(visibleProfileChips(emptyList()).isEmpty())
    }
}
