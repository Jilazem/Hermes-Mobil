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

    /** Tanınmayan hash: isim üretilemez → kaynak etiketine düşer. */
    @Test
    fun taninmayanCronHashKaynakEtiketineDuser() {
        val s = sess("cron_ffffffffffff_20260911_221601", source = "cron")
        assertEquals("Zamanlanmış görev", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Desktop kaynağı (id sonu hex değil → damga yok) → "Masaüstü". */
    @Test
    fun desktopKaynagiMasaustu() {
        val s = sess("20260911_214924_desktop1", source = "desktop")
        assertEquals("Masaüstü", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Cron kaynağı ama isim haritası boş/hatasız boş gelmiş → kaynak etiketi. */
    @Test
    fun cronIsimsizKaynakEtiketi() {
        val s = sess("cron_2e4ea303c123_20260911_221601", source = "cron")
        assertEquals("Zamanlanmış görev", readableTitle(s, SessionFlags(), emptyMap()))
    }

    /** Telegram: display_name zaten title'a akar → o kullanılır, cron/harita devreye girmez. */
    @Test
    fun telegramDisplayNameKullanilir() {
        val s = sess("20260911_214924_0d9ccce1", source = "telegram", displayName = "Gökhan Uzman")
        assertEquals("Gökhan Uzman", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Cron iş adı bilinen hash'te ama id'de zaman damgası yok → yalnız iş adı. */
    @Test
    fun cronDamgasizYalnizIsmi() {
        val s = sess("cron_2e4ea303c123_", source = "cron")
        assertEquals("hermes-gunluk-yedek-02", readableTitle(s, SessionFlags(), cronNames))
    }

    /**
     * GÜNCELLENEK kural (eski test ham title'ı kabul ediyordu): hiçbir kural
     * eşleşmese bile ham id başlık olmaz — kaynak etiketi gösterilir.
     */
    @Test
    fun eslesmeYoksaHamIdDegilKaynakEtiketi() {
        val s = sess("bes_20260911_x", source = "cli")
        val t = readableTitle(s, SessionFlags(), cronNames)
        assertEquals("CLI", t)
        assertFalse("ham id birincil başlık olamaz", t.contains("bes_20260911_x"))
    }

    /** Boss'un ekran görüntüsü: TUI ham id → kaynak + dakika damgası (saniyesiz). */
    @Test
    fun tuiHamIdKaynakZamanOlur() {
        val s = sess("20260913_184051_52f76a", source = "tui")
        assertEquals("TUI · 13.09 18:40", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Kaynak YOK ama id'de geçerli damga var → "Oturum · gg.AA ss:dd". */
    @Test
    fun kaynaksizDamgaliIdOturumZaman() {
        val s = sess("20260913_184051_52f76a", source = null)
        assertEquals("Oturum · 13.09 18:40", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Ne kaynak ne damga → son çare sabit "Oturum"; ham id sızmaz. */
    @Test
    fun kaynaktaYoksaHamIdYerineOturum() {
        val s = sess("bes_20260911_x", source = null)
        assertEquals("Oturum", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Damga desenine uyan ama geçersiz tarih/saat taşıyan id uydurulmaz. */
    @Test
    fun gecersizDamgaCozulmez() {
        val s = sess("20261332_256101_52f76a", source = "tui")
        assertEquals("TUI", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Kaynak etiketleri TR: api_server → API, whatsapp → WhatsApp. */
    @Test
    fun kaynakEtiketleriTR() {
        assertEquals("API · 13.09 18:40",
            readableTitle(sess("20260913_184051_52f76a", source = "api_server"), SessionFlags(), cronNames))
        assertEquals("WhatsApp · 13.09 18:40",
            readableTitle(sess("20260913_184051_52f76a", source = "whatsapp"), SessionFlags(), cronNames))
    }

    // ---- Canlı oturumlar: LiveSessionsScreen/LiveFeed aynı zinciri kullanır ----

    private fun live(
        id: String,
        title: String = "",
        key: String = "",
    ) = LiveSession(id = id, title = title, sessionKey = key)

    /** Canlı oturumun REST karşılığı tui ise başlık "TUI · …" olur, ham id değil. */
    @Test
    fun canliOturumAyniZincir() {
        val l = live("538fa088", key = "20260913_184051_52f76a")
        val rest = sess("20260913_184051_52f76a", source = "tui")
        assertEquals("TUI · 13.09 18:40", liveSessionTitle(l, rest, SessionFlags(), cronNames))
    }

    /** Gateway canlı başlığı zaten okunaklıysa (Telegram) o korunur. */
    @Test
    fun canliBaslikOkunakliysaDurur() {
        val l = live("538fa088", title = "Gökhan Uzman", key = "20260913_184051_52f76a")
        val rest = sess("20260913_184051_52f76a", source = "telegram")
        assertEquals("Gökhan Uzman", liveSessionTitle(l, rest, SessionFlags(), cronNames))
    }

    /** Canlı oturumun REST karşılığı hiç yoksa bile ham süreç içi id düşmez. */
    @Test
    fun canliRestKarsiliksizHamIdGostermez() {
        val l = live("538fa088")
        val t = liveSessionTitle(l, null, SessionFlags(), cronNames)
        assertFalse("ham süreç içi id başlık olamaz", t.contains("538fa088"))
        assertEquals("Oturum", t)
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
