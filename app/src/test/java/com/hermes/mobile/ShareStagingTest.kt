package com.hermes.mobile

import com.hermes.mobile.data.ShareStaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

/**
 * ShareStaging: paylaşım dosyasının cache'e akış kopyası — gerçek dosyayla
 * doğrulanan JVM testi (HIGH-1/HIGH-2: "kopya var mı, byte byte aynı mı,
 * başarısızlıkta yarım dosya kalıyor mu, bayat süpürme çalışıyor mu").
 *
 * Vekil Android içerik sağlayıcısını yalnız InputStream'e çevirip buraya
 * verir; bu yüzden akış sözleşmesinin gerçek dosya testi yeterli katmandır.
 */
class ShareStagingTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `kopya bayt bayt ayni ve adi token ile temizlenmis`() {
        val dir = ShareStaging.inboxDir(tmp.root)
        val payload = ByteArray(20_000) { (it % 251).toByte() }
        val out = ShareStaging.stage(
            input = ByteArrayInputStream(payload),
            dir = dir,
            token = "deadbeef-1234-5678",
            displayName = "Aylık Rapor (taslak).pdf",
        )
        assertNotNull("staging başarısız", out)
        out!!
        assertTrue(out.isFile)
        assertArrayEqualsSafe(payload, out.readBytes())
        // token'ın ilk 8 karakteri + sanitize edilmiş ad
        assertTrue("ad: ${out.name}", out.name.startsWith("deadbeef_"))
        assertFalse("yol enjeksiyonu sızmasın", out.name.contains("/"))
    }

    @Test
    fun `null akis ve bos akis kopya uretmez`() {
        val dir = ShareStaging.inboxDir(tmp.root)
        assertNull(ShareStaging.stage(null, dir, "tok12345", "a.bin"))
        assertNull(ShareStaging.stage(ByteArrayInputStream(ByteArray(0)), dir, "tok12345", "a.bin"))
        // başarısızlık sonrası dizinde yarım dosya kalmamalı
        assertEquals(0, dir.list()?.size ?: 0)
    }

    @Test
    fun `boyut sinirini asan akis yarisda kesilir ve yarim kopya silinir`() {
        val dir = ShareStaging.inboxDir(tmp.root)
        val big = ByteArray(64 * 1024)
        val result = ShareStaging.stage(
            ByteArrayInputStream(big), dir, "tok12345", "dev.bin",
            maxBytes = 1024L,
        )
        assertNull("sınır aşımında kopya kabul edilmemeli", result)
        assertEquals("yarım kopya temizlenmeli", 0, dir.list()?.size ?: 0)
    }

    @Test
    fun `bayat kopyalar supurulur, yeniler kalir`() {
        val dir = ShareStaging.inboxDir(tmp.root)
        val stale = dir.resolve("eski_dosya.bin").apply { writeBytes(byteArrayOf(1)) }
        val fresh = dir.resolve("yeni_dosya.bin").apply { writeBytes(byteArrayOf(2)) }
        // 2 saat önce üretilmiş gibi işaretle
        val now = System.currentTimeMillis()
        stale.setLastModified(now - 2 * 3_600_000L)
        fresh.setLastModified(now - 60_000L)

        val purged = ShareStaging.purgeStale(dir, olderThanMs = 3_600_000L, now = now)
        assertEquals(1, purged)
        assertFalse("bayat silinmeli", stale.exists())
        assertTrue("taze kalmalı", fresh.exists())
    }

    @Test
    fun `sanitize yol ve bos ad tuzaklarini yutar`() {
        // Üst dizin taşması: yalnız SON bileşen alınır, '..' ve '/' sızamaz.
        val esc = ShareStaging.sanitize("../../etc/passwd")
        assertEquals("passwd", esc)
        // boş/garanti adı
        assertEquals("dosya.bin", ShareStaging.sanitize("///"))
        // Windows yolu yalnız son bileşenden gelir
        assertEquals("foto.jpg", ShareStaging.sanitize("C:\\Users\\x\\foto.jpg"))
    }

    private fun assertArrayEqualsSafe(expected: ByteArray, actual: ByteArray) {
        assertEquals("bayt sayısı", expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals("bayt $i", expected[i], actual[i])
        }
    }
}
