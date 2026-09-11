package com.hermes.mobile

import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.ui.readableTitle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `readableTitle` — oturumlara okunabilir isim verme mantığı. Cron oturumları
 * `cron_<hash>_<tarih>_<saat>` ham id'leriyle geldiği için kullanıcıya ham id
 * göstermek yerine iş adı + kısa zaman damgası üretiyoruz; sıralama:
 * rename > sunucu başlığı > cron iş adı > kaynak etiketi > eski title.
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

    /** Desktop kaynağı → "Masaüstü". */
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
        val s = sess("20260911_214924_0d9ccce1", source = "telegram", displayName = "Alex Demo")
        assertEquals("Alex Demo", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Cron iş adı bilinen hash'te ama id'de zaman damgası yok → yalnız iş adı. */
    @Test
    fun cronDamgasizYalnizIsmi() {
        val s = sess("cron_2e4ea303c123_", source = "cron")
        assertEquals("hermes-gunluk-yedek-02", readableTitle(s, SessionFlags(), cronNames))
    }

    /** Hiçbir kural eşleşmezse eski davranış: ham title (= id) gösterilir. */
    @Test
    fun eslesmeYoksaEskiTitle() {
        val s = sess("bes_20260911_x", source = "cli")
        assertEquals("bes_20260911_x", readableTitle(s, SessionFlags(), cronNames))
    }
}
