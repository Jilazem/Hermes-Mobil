package com.hermes.mobile

import com.hermes.mobile.ui.StatusTone
import com.hermes.mobile.ui.cardActions
import com.hermes.mobile.ui.statusPill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-4 kusur E/F + durum etiketleri (büyük model danışması, bağlayıcı).
 *
 * E: buton seti iki ekranda farklıydı ("Konuşmaya devam et | Müdahale | Döküm |
 *    Dur" vs "Devam | Müdahale | Dur"). Tek kural: liste kartında buton YIĞINI
 *    yok, satırın tamamı sohbeti açar, Döküm SABİT konumda, çalışan oturumda
 *    tek birincil eylem "Dur".
 * F: kart altındaki 2 satırlık kullanım kılavuzu kaldırıldı — bunun yerine
 *    eylem kümesi kendini anlatır (buton yoksa kılavuz da yok).
 * Durum etiketleri: boşta/bitmiş → ETİKETSİZ; "Canlı" kelimesi KULLANILMAZ.
 */
class SessionCardAnatomyTest {

    @Test
    fun `calisan oturumda tek birincil eylem Dur`() {
        val a = cardActions(working = true)
        assertTrue(a.stop)
        assertTrue("müdahale ikincil ikon olarak kalır", a.steer)
        assertFalse("devam butonu çalışan kartta olmaz", a.continuePrimary)
        assertTrue("Döküm konumu sabit — her durumda var", a.transcript)
    }

    @Test
    fun `bosta oturumda eylem yigini yok, satir sohbeti acar`() {
        val a = cardActions(working = false)
        assertFalse(a.stop)
        assertFalse(a.steer)
        assertTrue("satırın tamamı sohbeti açar", a.continuePrimary)
        assertTrue(a.transcript)
    }

    @Test
    fun `durum etiketi sinyal — bosta ve bitmis etiketsiz`() {
        assertNull("boşta etiket yazılmaz (Telegram idle demez)", statusPill("idle"))
        assertNull("bitmiş oturum etiketsiz", statusPill("done"))
    }

    @Test
    fun `calisan ve bekleyen durumlari etiketlidir`() {
        val working = statusPill("working")
        assertEquals(StatusTone.Live, working?.tone)
        assertEquals("yazıyor…", working?.labelTr)
        assertEquals("typing…", working?.labelEn)

        assertEquals(StatusTone.Waiting, statusPill("waiting")?.tone)
        assertEquals(StatusTone.Starting, statusPill("starting")?.tone)
    }

    @Test
    fun `canli kelimesi hicbir etikette gecmez`() {
        listOf("idle", "working", "waiting", "starting", "done").forEach { s ->
            val p = statusPill(s)
            assertFalse("'Canlı' kelimesi kullanılmaz ($s)", p?.labelTr == "Canlı")
            assertFalse("'live' kelimesi kullanılmaz ($s)", p?.labelEn?.equals("live", true) == true)
        }
    }
}
