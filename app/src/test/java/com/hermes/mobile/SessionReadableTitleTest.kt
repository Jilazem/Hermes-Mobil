package com.hermes.mobile

import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.ui.liveSessionTitle
import com.hermes.mobile.ui.readableTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * `readableTitle` — oturumlara okunabilir isim verme mantığı. Cron oturumları
 * `cron_<hash>_<tarih>_<saat>`, TUI/CLI oturumları `20260913_184051_52f76a`
 * ham id'leriyle geldiği için kullanıcıya ham id göstermek yerine kaynak etiketi
 * + kısa zaman damgası üretiyoruz; sıralama:
 * rename > sunucu başlığı > cron iş adı > kaynak+damga > "Oturum".
 *
 * Kural (boss şikâyeti: "session adları anlaşılmaz"): hiçbir çıktıda ham
 * session id'si birincil başlık OLAMAZ.
 */
class SessionReadableTitleTest {

    private val cronNames = mapOf("2e4ea303c123" to "hermes-gunluk-yedek-02")

    private fun sess(
        id: String,
        source: String? = null,
        displayName: String? = null,
    ) = HermesSession(id = id, source = source, displayName = displayName)

    /** Bilinen cron hash'i → iş adı + zaman damgası tabanı. */
    @Test
    fun cronHashIsmiCozulur() {
        val s = sess("cron_2e4ea303c123_20260911_221601", source = "cron")
        assertEquals("hermes-gunluk-yedek-02 · 11.09 22:16", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Kullanıcının yeniden adlandırması her şeyi ezer — cron adı dahil. */
    @Test
    fun renameOnceliklidir() {
        val s = sess("cron_2e4ea303c123_20260911_221601", source = "cron")
        val flags = SessionFlags(renames = mapOf(s.id to "Yedekler"))
        assertEquals("Yedekler", readableTitle(s, flags, cronNames))
    }

    /** Tanınmayan hash: isim üretilemez → zaman damgası tabanlı "Sohbet · …". */
    @Test
    fun taninmayanCronHashSohbetDamgasinaDuser() {
        val s = sess("cron_ffffffffffff_20260911_221601", source = "cron")
        assertEquals("Sohbet · 11.09 22:16", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Desktop kaynağı (id sonu hex değil → damga yok) → "Sohbet". */
    @Test
    fun desktopKaynagiBasligaSizmaz() {
        // Tur-4: kaynak adı ("Masaüstü") artık başlık DEĞİL — kartta rozet olarak
        // yaşar; başlık konu/damga zincirinden gelir.
        val s = sess("20260911_214924_desktop1", source = "desktop")
        assertEquals("Sohbet", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Cron kaynağı ama isim haritası boş/hatasız boş gelmiş → damga tabanı. */
    @Test
    fun cronIsimsizDamgaTabani() {
        val s = sess("cron_2e4ea303c123_20260911_221601", source = "cron")
        assertEquals("Sohbet · 11.09 22:16", readableTitle(s, SessionFlags(), emptyMap()))
    }

    /**
     * Tur-4 (kusur C): Telegram `display_name` KİŞİ adıdır, KONU değildir.
     * Boss şikâyeti: "oturum adlarından konu anlaşılmıyor, 'Gökhan Uzman' kalıyor".
     */
    @Test
    fun telegramKisiAdiBaslikOlmaz() {
        val s = sess("20260911_214924_0d9ccce1", source = "telegram", displayName = "Gökhan Uzman")
        val t = readableTitle(s, SessionFlags(), cronNames)
        assertEquals("Sohbet · 11.09 21:49", t)
        assertFalse("kişi adı başlık olamaz", t.contains("Gökhan Uzman"))
    }

    /** Sunucu başlığı ANLAMLI ise konu odur (kişi adından önce gelir). */
    @Test
    fun telegramSunucuBasligiKonuOlarakKullanilir() {
        val s = HermesSession(
            id = "20260911_214924_0d9ccce1",
            source = "telegram",
            displayName = "Gökhan Uzman",
            serverTitle = "Korkuteli 112 ada 7 parsel değer",
        )
        assertEquals("Korkuteli 112 ada 7 parsel değer", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Cron iş adı bilinen hash'te ama id'de zaman damgası yok → yalnız iş adı. */
    @Test
    fun cronDamgasizYalnizIsmi() {
        val s = sess("cron_2e4ea303c123_", source = "cron")
        assertEquals("hermes-gunluk-yedek-02", readableTitle(s, SessionFlags(), cronNames))
    }

    /**
     * GÜNCELLENEN kural (tur-4): kaynak etiketi başlık olmaz; ham id de olmaz.
     */
    @Test
    fun eslesmeYoksaHamIdVeKaynakDegilSohbet() {
        val s = sess("bes_20260911_x", source = "cli")
        val t = readableTitle(s, SessionFlags(), cronNames)
        assertEquals("Sohbet", t)
        assertFalse("ham id birincil başlık olamaz", t.contains("bes_20260911_x"))
    }

    /** Boss'un ekran görüntüsü: TUI ham id → konu/kişi yoksa "Sohbet · gg.AA ss:dd". */
    @Test
    fun tuiHamIdSohbetZamanOlur() {
        val s = sess("20260913_184051_52f76a", source = "tui")
        assertEquals("Sohbet · 13.09 18:40", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Kaynak YOK ama id'de geçerli damga var → "Sohbet · gg.AA ss:dd". */
    @Test
    fun kaynaksizDamgaliIdSohbetZaman() {
        val s = sess("20260913_184051_52f76a", source = null)
        assertEquals("Sohbet · 13.09 18:40", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Ne konu ne damga → son çare sabit "Sohbet"; ham id sızmaz. */
    @Test
    fun kaynaktaYoksaSonCareSohbet() {
        val s = sess("bes_20260911_x", source = null)
        assertEquals("Sohbet", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Damga desenine uyan ama geçersiz tarih/saat taşıyan id uydurulmaz. */
    @Test
    fun gecersizDamgaCozulmez() {
        val s = sess("20261332_256101_52f76a", source = "tui")
        assertEquals("Sohbet", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Kaynak etiketleri artık başlığa girmez (rozet olarak kartta yaşar). */
    @Test
    fun kaynakEtiketleriBasligaGirmez() {
        assertEquals("Sohbet · 13.09 18:40",
            readableTitle(sess("20260913_184051_52f76a", source = "api_server"), SessionFlags(), cronNames))
        assertEquals("Sohbet · 13.09 18:40",
            readableTitle(sess("20260913_184051_52f76a", source = "whatsapp"), SessionFlags(), cronNames))
    }

    // ---- Canlı oturumlar: LiveSessionsScreen/LiveFeed aynı zinciri kullanır ----

    private fun live(
        id: String,
        title: String = "",
        key: String = "",
    ) = LiveSession(id = id, title = title, sessionKey = key)

    /** Canlı oturumun REST karşılığı tui ise başlık "Sohbet · …" olur, ham id değil. */
    @Test
    fun canliOturumAyniZincir() {
        val l = live("538fa088", key = "20260913_184051_52f76a")
        val rest = sess("20260913_184051_52f76a", source = "tui")
        assertEquals("Sohbet · 13.09 18:40", liveSessionTitle(l, rest, SessionFlags(), cronNames))
    }

    /**
     * Tur-4 (kusur C): gateway canlı başlığı KİŞİ adıysa ("Gökhan Uzman") konu
     * sayılmaz — damga tabanına düşer.
     */
    @Test
    fun canliKisiAdiBaslikOlmaz() {
        val l = live("538fa088", title = "Gökhan Uzman", key = "20260913_184051_52f76a")
        val rest = sess("20260913_184051_52f76a", source = "telegram")
        val t = liveSessionTitle(l, rest, SessionFlags(), cronNames)
        assertEquals("Sohbet · 13.09 18:40", t)
        assertFalse(t.contains("Gökhan Uzman"))
    }

    /** Canlı oturumun REST karşılığı hiç yoksa bile ham süreç içi id düşmez. */
    @Test
    fun canliRestKarsiliksizHamIdGostermez() {
        val l = live("538fa088")
        val t = liveSessionTitle(l, null, SessionFlags(), cronNames)
        assertFalse("ham süreç içi id başlık olamaz", t.contains("538fa088"))
        assertEquals("Sohbet", t)
    }

    /** Yeniden adlandırma canlı oturumda da geçerli (dbId ya da süreç içi id). */
    @Test
    fun canliRenameUygulanir() {
        val l = live("538fa088", key = "20260913_184051_52f76a")
        val flags = SessionFlags(renames = mapOf("20260913_184051_52f76a" to "Gece bekçisi"))
        assertEquals("Gece bekçisi",
            liveSessionTitle(l, sess("20260913_184051_52f76a", source = "tui"), flags, cronNames))
    }
}
