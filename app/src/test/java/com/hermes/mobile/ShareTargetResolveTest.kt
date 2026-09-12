package com.hermes.mobile

import com.hermes.mobile.data.NEW_TOPIC_TARGET
import com.hermes.mobile.data.ShareTarget
import com.hermes.mobile.data.ShareTargetKind
import com.hermes.mobile.data.label
import com.hermes.mobile.data.resolveShareTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `resolveShareTarget` saf hedef kararının testleri.
 *
 * Paylaşım hedefi kararı UI'dan bağımsız olmalı: aynı girdilerle aynı
 * kararı üretir, state içermez. Burada karar matrisi (5 kural) sabitlenir;
 * paylaşım hedefi değişikliğinde bu testler kırmalı.
 */
class ShareTargetResolveTest {

    // ---- Kural 1: dosya var + oturum seçildi → Existing, picker açma ----

    @Test
    fun `dosya ve oturum varken Existing icerir ve picker acmaz`() {
        val r = resolveShareTarget(
            sharedText = "dosyayla birlikte not",
            sharedFile = "rapor.pdf (1.2 MB)",
            selectedSession = "sess-123",
        )
        assertEquals(ShareTargetKind.Existing, r.kind)
        assertEquals("sess-123", r.sessionId)
        assertTrue(r.hasFile)
        assertFalse(r.wantsTargetPicker)
    }

    // ---- Kural 3: yalnız metin + oturum seçildi → Existing, picker açma --

    @Test
    fun `yalniz metin ve oturum varken Existing olir ve dosya degildir`() {
        val r = resolveShareTarget(
            sharedText = "paylaşılan cümle",
            sharedFile = null,
            selectedSession = "sess-abc",
        )
        assertEquals(ShareTargetKind.Existing, r.kind)
        assertEquals("sess-abc", r.sessionId)
        assertFalse(r.hasFile)
        assertFalse(r.wantsTargetPicker)
    }

    // ---- Kural 2: dosya var + oturum yok → New, picker AÇ -------------

    @Test
    fun `dosya var oturum yoksa Yeni konusu icer ve picker acar`() {
        val r = resolveShareTarget(
            sharedText = null,
            sharedFile = "foto.png (3.4 MB)",
            selectedSession = null,
        )
        assertEquals(ShareTargetKind.New, r.kind)
        assertNull(r.sessionId)
        assertTrue(r.hasFile)
        assertTrue(r.wantsTargetPicker)
    }

    // ---- Kural 4: yalnız metin + oturum yok → New, picker AÇ ----------

    @Test
    fun `yalniz metin ve oturum yoksa Yeni konusu icer ve picker acar`() {
        val r = resolveShareTarget(
            sharedText = " WhatsApp ten gelen metin",
            sharedFile = null,
            selectedSession = null,
        )
        assertEquals(ShareTargetKind.New, r.kind)
        assertTrue(r.wantsTargetPicker)
        assertFalse(r.hasFile)
    }

    // ---- Kural 5: ne metin ne dosya → sessiz varsayılan, picker YOK ---

    @Test
    fun `bos paylasim sessiz yeni konudur ve picker acmaz`() {
        val r = resolveShareTarget(null, null, null)
        assertEquals(NEW_TOPIC_TARGET, r)
        assertFalse(r.wantsTargetPicker)
    }

    // ---- Kenar: boş karakter dizileri null ile aynı davranır ----------

    @Test
    fun `bos string girdileri null gibi sayilir`() {
        val r = resolveShareTarget("   ", "  ", " ")
        assertEquals(ShareTargetKind.New, r.kind)
        assertFalse(r.hasFile)
        assertFalse(r.wantsTargetPicker)
    }

    // ---- Etikette dosya adı korunur ------------------------------------

    @Test
    fun `etiket mevcut oturumda ad gosterir`() {
        val r = resolveShareTarget("metin", "f.pdf (1 KB)", "sess-x")
        assertTrue(r.label("tr").contains("sess-x"))
        val empty = resolveShareTarget(null, null, null)
        assertEquals("Yeni konu", empty.label("tr"))
    }
}
