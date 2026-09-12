package com.hermes.mobile

import com.hermes.mobile.data.ShareHandoff
import com.hermes.mobile.data.ShareUploadPlan
import com.hermes.mobile.data.cleanupPaths
import com.hermes.mobile.data.planShareUpload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
