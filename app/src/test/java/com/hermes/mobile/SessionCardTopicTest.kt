package com.hermes.mobile

import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.ui.cardSubtitle
import com.hermes.mobile.ui.previewLine
import com.hermes.mobile.ui.readableTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tur-2 K2 — oturum kartı konusu (boss: "konu nedir belli degil").
 *
 * Zincir: rename > sunucu title (ham id değilse) > cron iş adı > PREVIEW
 * (ilk ~60 karakter, tek satır) > kaynak+damga. Ham id hiçbir çıktıda
 * birincil başlık olamaz (3aa0300 sözleşmesi).
 */
class SessionCardTopicTest {

    private fun sess(
        id: String,
        source: String? = null,
        displayName: String? = null,
        serverTitle: String? = null,
        preview: String? = null,
    ) = HermesSession(
        id = id,
        source = source,
        displayName = displayName,
        serverTitle = serverTitle,
        preview = preview,
    )

    /** BOSS'un örneği: sunucu title gerçek bir konu taşıyor → kart onu gösterir. */
    @Test
    fun titleVarKonuOlarakGosterilir() {
        val s = sess(
            "20260914_202501_desktop1",
            source = "desktop",
            serverTitle = "Icra kiymet takdir raporlarini sablona gore yaz",
        )
        assertEquals(
            "Icra kiymet takdir raporlarini sablona gore yaz",
            readableTitle(s, SessionFlags(), emptyMap()),
        )
    }

    /** title ham id ile AYNIYSA konu sayılmaz — preview devreye girer. */
    @Test
    fun titleHamIdIsePreviewKullanilir() {
        val s = sess(
            "20260914_202501_abc123",
            source = "tui",
            serverTitle = "20260914_202501_abc123",
            preview = "Hermes update",
        )
        assertEquals("Hermes update", readableTitle(s, SessionFlags(), emptyMap()))
    }

    /** title yok, preview var → konu preview'dan (ilk mesaj). */
    @Test
    fun titleYokPreviewVar() {
        val s = sess(
            "20260914_202501_abc123",
            source = "tui",
            preview = "Kamulaştırma raporundaki trees sayisini kontrol et",
        )
        assertEquals(
            "Kamulaştırma raporundaki trees sayisini kontrol et",
            readableTitle(s, SessionFlags(), emptyMap()),
        )
    }

    /** İkisi de yok → eski davranış: kaynak + damga (ham id DEĞİL). */
    @Test
    fun ikisiDeYokKaynakDamga() {
        val s = sess("20260914_202501_abc123", source = "desktop")
        assertEquals("Masaüstü · 14.09 20:25", readableTitle(s, SessionFlags(), emptyMap()))
    }

    /** Rename her zaman kazanır — preview/title ikisini de ezer. */
    @Test
    fun renameHerZamanOncelikli() {
        val s = sess("x1", serverTitle = "Sunucu başlığı", preview = "önizleme")
        val flags = SessionFlags(renames = mapOf("x1" to "Benim adım"))
        assertEquals("Benim adım", readableTitle(s, flags, emptyMap()))
    }

    /** Cron iş adı preview'dan önce gelir (bilinen hash → iş adı). */
    @Test
    fun cronIsAdiPreviewdanOnce() {
        val s = sess(
            "cron_2e4ea303c123_20260911_221601",
            source = "cron",
            preview = "yedek aldım",
        )
        assertEquals(
            "hermes-gunluk-yedek-02 · 11.09 22:16",
            readableTitle(s, SessionFlags(), mapOf("2e4ea303c123" to "hermes-gunluk-yedek-02")),
        )
    }

    /** Ham id sözleşmesi: hiçbir girdide başlık ham id olamaz. */
    @Test
    fun hamIdAslaBaslikOlamaz() {
        val s = sess("bes_20260911_x")
        val t = readableTitle(s, SessionFlags(), emptyMap())
        assertFalse(t.contains("bes_20260911_x"))
        assertEquals("Oturum", t)
    }

    /** Preview tek satıra düzleştirilir: satırsonu → boşluk. */
    @Test
    fun previewTekSatiraDuzlesir() {
        assertEquals("bir iki uc", previewLine("bir\n  iki\r\nuc"))
    }

    /** Preview 60 karakterde kırpılır, kabarcık taşla. */
    @Test
    fun previewKirkilir() {
        val long = "x".repeat(100)
        val out = previewLine(long)
        assertEquals(61, out.length)
        assertEquals('…', out.last())
    }

    /** Kısa preview aynen döner. */
    @Test
    fun previewKisaAynen() {
        assertEquals("Hermes update", previewLine("Hermes update"))
        assertEquals("", previewLine(null))
        assertEquals("", previewLine("   "))
    }

    /** Konu varsa alt satır "kaynak · damga"; yoksa null (yinelenmesin). */
    @Test
    fun subtitleYalnizKonuVarsa() {
        val withTopic = sess("20260914_202501_abc123", source = "tui", preview = "konu su")
        assertEquals("TUI · 14.09 20:25", cardSubtitle(withTopic, SessionFlags(), emptyMap()))

        val fallback = sess("20260914_202501_abc123", source = "tui")
        assertNull(cardSubtitle(fallback, SessionFlags(), emptyMap()))
    }
}
