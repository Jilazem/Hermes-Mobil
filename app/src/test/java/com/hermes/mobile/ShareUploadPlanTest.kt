package com.hermes.mobile

import com.hermes.mobile.data.ShareHandoff
import com.hermes.mobile.data.ShareUploadPlan
import com.hermes.mobile.data.cleanupPaths
import com.hermes.mobile.data.planShareUpload
import com.hermes.mobile.data.settleShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Yükleme kararı + vekil→MainActivity el sıkışma sözleşmesi (HIGH-2 kapanışı).
 *
 * Kod denetimi HIGH-1'in dersi: hedef kararı test edilirken "dosya
 * yüklenecek mi, hangi yoldan, temizlik kimde" kararı test edilmemişti ve
 * dosya sessizce kayboluyordu. Bu testler niyet→yükleme planı kablosunu saf
 * katmanda sabitler; [ShareStagingTest] de kopyanın gerçek dosya davranışını.
 */
class ShareUploadPlanTest {

    @Test
    fun `dosya yoksa plan None ve temizlik yolu yok`() {
        val p = planShareUpload(stagedPath = null, fileNote = null)
        assertEquals(ShareUploadPlan.None, p)
        assertTrue(p.cleanupPaths().isEmpty())
    }

    @Test
    fun `dosya etiketi ve kopya varsa Upload plani dogar`() {
        val p = planShareUpload(
            stagedPath = "/data/user/0/com.hermes.mobile.v2/cache/share_inbox/ab12_rapor.pdf",
            fileNote = "rapor.pdf (1.2 MB)",
        )
        assertTrue("Upload beklenirken $p", p is ShareUploadPlan.Upload)
        p as ShareUploadPlan.Upload
        assertEquals("rapor.pdf", p.name)
        assertEquals("/data/user/0/com.hermes.mobile.v2/cache/share_inbox/ab12_rapor.pdf", p.stagedPath)
        // Temizlik zorunlu: gönderim sonrası cache'te kopya kalmamalı.
        assertEquals(listOf(p.stagedPath), p.cleanupPaths())
    }

    @Test
    fun `kopya okunamazsa Unreadable - sessiz kayip yerine uyarı`() {
        val p = planShareUpload(
            stagedPath = "/cache/share_inbox/bozuk.bin",
            fileNote = "video.mp4 (40 MB)",
            readable = false,
        )
        assertTrue("Unreadable beklenirken $p", p is ShareUploadPlan.Unreadable)
        p as ShareUploadPlan.Unreadable
        assertEquals("video.mp4", p.name)
        // Yüklenemez ama yarım kopya yine de silinmeli (cache birikmesin).
        assertEquals(listOf("/cache/share_inbox/bozuk.bin"), p.cleanupPaths())
    }

    @Test
    fun `etiket var kopya yoksa Unreadable`() {
        // Vekil staging'i başaramadı: MainActivity yalnız etiketi görür —
        // plan yine Unreadable üretmeli, Upload ASLA (sahte yükleme yok).
        val p = planShareUpload(stagedPath = null, fileNote = "not.txt (2 KB)")
        assertTrue(p is ShareUploadPlan.Unreadable)
        assertEquals("not.txt", (p as ShareUploadPlan.Unreadable).name)
        assertTrue(p.cleanupPaths().isEmpty())
    }

    @Test
    fun `ad cikarma parantezli ve parantezsiz girdilerde saglam`() {
        val up = planShareUpload("/tmp/x/y", "fotoğraf kare (3 KB)") as ShareUploadPlan.Upload
        assertEquals("fotoğraf kare", up.name)
        // Parantez yoksa ad olduğu gibi kalır.
        val bare = planShareUpload("/tmp/x/y", "duz-adi.txt") as ShareUploadPlan.Upload
        assertEquals("duz-adi.txt", bare.name)
    }

    @Test
    fun `el sikisma yalniz nonce token ile kabul edilir`() {
        // Dış uygulama is-share koyup token'ı bilemez → ret (öneri #3).
        assertFalse(ShareHandoff.accepted(isShare = true, token = null))
        assertFalse(ShareHandoff.accepted(isShare = true, token = "  "))
        assertFalse(ShareHandoff.accepted(isShare = false, token = "herhangi"))
        assertTrue(ShareHandoff.accepted(isShare = true, token = "abc-123"))
    }

    @Test
    fun `sozlesme anahtarlari tek kaynakta durur`() {
        // Vekilin koyduğu her anahtar sözleşmede olmalı; yeni ekstrap
        // eklenirse bu test güncellemeyi hatırlatır.
        assertEquals(
            setOf(
                "hermes_is_share", "hermes_shared_text", "hermes_shared_file",
                "hermes_share_token", "hermes_staged_file",
            ),
            ShareHandoff.contractKeys,
        )
    }

    // ---- YENI-1 (denetmen2): consume/cancel SAF kapanış sözleşmesi ----

    @Test
    fun `iptal plan-upload iken yukleme URETMEZ taslak BOS kopya silinir`() {
        // Sözleşme: Vazgeç/Geri = kullanıcı onayı YOK. Upload planı bile olsa
        // attach YOK, taslak metni YOK, staging silinecekler listesinde.
        val plan = ShareUploadPlan.Upload("/tmp/share_inbox/x.txt", "x.txt", 10L)
        val out = settleShare(
            plan = plan, text = "gizli metin",
            applyUpload = false, profilePresent = true,
        )
        assertNull("iptal ASLA yükleme üretmez", out.attachName)
        assertNull("iptal taslağa metin DÜŞÜRMEZ", out.draftText)
        assertEquals("iptal staged kopyayı siler", listOf("/tmp/share_inbox/x.txt"), out.cleanupPaths)
        assertNull("iptal uyarı da üretmez (kullanıcı zaten vazgeçti)", out.warnCase)
    }

    @Test
    fun `onay plan-upload iken yukleme URETIR temizlik VM de (yaris korumasi)`() {
        val plan = ShareUploadPlan.Upload("/tmp/share_inbox/x.txt", "x.txt", 10L)
        val out = settleShare(plan, "not", applyUpload = true, profilePresent = true)
        assertEquals("x.txt", out.attachName)
        assertEquals("not", out.draftText)
        // Upload'ın temizliği attachShareFile yükledikten sonra yapılır —
        // settle çift silme yapmaz (okuma yarışı regression koruması).
        assertTrue(out.cleanupPaths.isEmpty())
        assertNull(out.warnCase)
    }

    @Test
    fun `onay profil yoksa upload yerine gorunur uyari + temizlik`() {
        val plan = ShareUploadPlan.Upload("/tmp/share_inbox/x.txt", "x.txt", 10L)
        val out = settleShare(plan, null, applyUpload = true, profilePresent = false)
        assertNull(out.attachName)
        assertEquals("not_connected", out.warnCase) // kullanıcıya görünür (YENI-2)
        assertEquals(listOf("/tmp/share_inbox/x.txt"), out.cleanupPaths)
    }

    @Test
    fun `iptal unreadable planinda bile sessiz temizlik`() {
        val plan = ShareUploadPlan.Unreadable("a.bin", stagedPaths = listOf("/tmp/a.bin"))
        val out = settleShare(plan, "metin", applyUpload = false, profilePresent = true)
        assertNull(out.attachName); assertNull(out.warnCase); assertNull(out.draftText)
        assertEquals(listOf("/tmp/a.bin"), out.cleanupPaths)
    }

    @Test
    fun `onay unreadable gorunur uyari uretir metin dusmez`() {
        val plan = ShareUploadPlan.Unreadable("a.bin", stagedPaths = emptyList())
        val out = settleShare(plan, "mesaj", applyUpload = true, profilePresent = true)
        assertNull(out.attachName)
        assertEquals("unreadable", out.warnCase)
        assertEquals("mesaj", out.draftText)
    }
}
