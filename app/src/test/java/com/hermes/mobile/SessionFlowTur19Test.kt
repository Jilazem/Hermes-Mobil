package com.hermes.mobile

import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.ui.DrawerRow
import com.hermes.mobile.ui.QuickReplyPhase
import com.hermes.mobile.ui.activeAgentCount
import com.hermes.mobile.ui.canQuickReply
import com.hermes.mobile.ui.drawerFeed
import com.hermes.mobile.ui.drawerRows
import com.hermes.mobile.ui.quickReplyIsSuccess
import com.hermes.mobile.ui.quickReplyOnResult
import com.hermes.mobile.ui.quickReplyOnSubmit
import com.hermes.mobile.ui.quickReplyOnType
import com.hermes.mobile.ui.quickReplySendEnabled
import com.hermes.mobile.ui.rateLabel
import com.hermes.mobile.ui.sparkRate
import com.hermes.mobile.ui.sparkWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.io.File

/**
 * Tur-19 — "Tümü" genel akışı, hızlı yanıt durum makinesi ve eşzamanlılık
 * istatistiği karar mantığı (SC-001: ≥8 yeni birim testi).
 */
class SessionFlowTur19Test {

    private fun epoch(daysAgo: Long, hour: Int = 12): Double =
        LocalDate.now().minusDays(daysAgo).atTime(hour, 0)
            .atZone(ZoneId.systemDefault()).toEpochSecond().toDouble()

    private fun sess(
        id: String,
        startedAt: Double? = null,
        ended: Boolean = true,
        name: String? = null,
        preview: String? = null,
    ) = HermesSession(
        id = id,
        displayName = name,
        preview = preview,
        startedAt = startedAt,
        endedAt = if (ended) (startedAt ?: 0.0) + 1 else null,
    )

    private fun live(id: String, status: String, dbId: String = "") = LiveSession(
        id = id,
        sessionKey = dbId,
        status = status,
    )

    private fun row(
        key: String,
        dot: String = "grey",
        working: Boolean = false,
        live: LiveSession? = null,
    ) = DrawerRow(
        key = key,
        liveId = live?.id.orEmpty(),
        dbId = key,
        title = "Başlık $key",
        preview = "önizleme $key",
        epochSeconds = epoch(0),
        dot = dot,
        working = working,
        pinned = false,
        archived = false,
        current = false,
        liveSession = live,
    )

    // ---- 1) FR-001: genel akış sırası --------------------------------------

    @Test
    fun `akış kronolojik en yeni önce sıralanır`() {
        val rows = drawerRows(
            sessions = listOf(
                sess("eski", epoch(5), name = "Eski"),
                sess("yeni", epoch(0), name = "Yeni"),
                sess("orta", epoch(2), name = "Orta"),
            ),
            live = emptyList(),
            liveByDbId = emptyMap(),
            flags = SessionFlags(),
            cronNames = emptyMap(),
        )
        val feed = drawerFeed(rows, query = "")
        assertEquals(listOf("yeni", "orta", "eski"), feed.map { it.dbId })
    }

    @Test
    fun `akışta grup başlığı YOKTUR - düz liste döner`() {
        // Tümü akışı DrawerRow listesi döndürür (DrawerItem Header İÇERMEZ) —
        // tür düzeyinde garanti: dönüş tipi List<DrawerRow>, başlık taşıyamaz.
        val feed = drawerFeed(listOf(row("a")), query = "")
        assertEquals(1, feed.size)
    }

    @Test
    fun `akışta arama başlık ve önizlemede süzer`() {
        val rows = listOf(row("a"), row("b"))
        val byTitle = drawerFeed(rows, query = "Başlık b")
        assertEquals(listOf("b"), byTitle.map { it.key })
        val byPreview = drawerFeed(rows, query = "önizleme a")
        assertEquals(listOf("a"), byPreview.map { it.key })
        assertEquals(emptyList<String>(), drawerFeed(rows, query = "yok").map { it.key })
    }

    @Test
    fun `akış boş girdide boş döner - başlık uydurma yok`() {
        assertEquals(emptyList<DrawerRow>(), drawerFeed(emptyList(), ""))
    }

    @Test
    fun `akışta ham id sızıntısı YOK - tüm başlıklar displayLabel filtresinden geçer`() {
        // Ham süreç içi id'ler başlık olmamalı; drawerRows displayLabel'den
        // geçiriyor — akış o satırları olduğu gibi taşır, filtrelenmiş hâliyle.
        val rows = drawerRows(
            sessions = listOf(sess("20260913_184051_52f76a", epoch(0))),
            live = emptyList(),
            liveByDbId = emptyMap(),
            flags = SessionFlags(),
            cronNames = emptyMap(),
        )
        val feed = drawerFeed(rows, "")
        assertEquals(1, feed.size)
        val title = feed.first().title
        // Ham id başlık OLMAZ: ya "?" düşer ya insan-dostu yedek ("Sohbet · …").
        assertFalse(title.contains("52f76a"))
        assertFalse(title.contains("20260913_184051"))
        // D-11: üretilen rapor — ham-id sızıntı denetimi çıktısı (SABİT ad:
        // her yeniden koşu aynı dosyayı tazeler; denetim için deterministik).
        // (tur17 kalıbı: gradle test CWD'si app/ olabilir — repo kokunu bul.)
        val repoRoot = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").exists() }
            ?: File(System.getProperty("user.dir"))
        val outDir = File(repoRoot, "denetim/tur19").apply { mkdirs() }
        val outFile = File(outDir, "ham-id-sizinti-denetimi.txt")
        outFile.writeText(
            "tur19 ham-id denetimi: 1 satır, başlık filtreli (sızan=0), doğrulama=assertFalse(ham id alt dizileri)\n",
        )
        assertTrue("denetim çıktısı yazılmalı", outFile.exists())
    }

    // ---- 2) FR-002: hızlı yanıt durum makinesi ------------------------------

    @Test
    fun `canQuickReply yalnız canlı ve müdahaleye açık satırda true`() {
        assertTrue(canQuickReply(row("a", live = live("l1", "working"))))
        assertTrue(canQuickReply(row("a", live = live("l1", "waiting"))))
        assertTrue(canQuickReply(row("a", live = live("l1", "starting"))))
        assertFalse(canQuickReply(row("a", live = live("l1", "idle"))))
        // REST-only (canlısı olmayan) satır — yeni API icat edilmez: FALSE.
        assertFalse(canQuickReply(row("a")))
    }

    @Test
    fun `gönder düğmesi boş metin ve gönderim sürerken kapalı`() {
        assertFalse(quickReplySendEnabled(QuickReplyPhase.Idle, ""))
        assertFalse(quickReplySendEnabled(QuickReplyPhase.Idle, "   "))
        assertFalse(quickReplySendEnabled(QuickReplyPhase.Sending, "tamam"))
        assertTrue(quickReplySendEnabled(QuickReplyPhase.Editing, "tamam"))
        // Sent sonrası yeniden yazılabilir → tekrar gönderilebilir (Editing'e döner).
        val after = quickReplyOnType(QuickReplyPhase.Sent)
        assertEquals(QuickReplyPhase.Editing, after)
        assertTrue(quickReplySendEnabled(after, "devam"))
    }

    @Test
    fun `durum makinesi akışı Idle-Sending-Sent ve hata yolu`() {
        var p = QuickReplyPhase.Idle
        p = quickReplyOnSubmit(p, "")            // boş — geçiş yok
        assertEquals(QuickReplyPhase.Idle, p)
        p = quickReplyOnSubmit(p, "mesaj")
        assertEquals(QuickReplyPhase.Sending, p)
        p = quickReplyOnSubmit(p, "çift tık")    // sending'de yinelenen submit engelli
        assertEquals(QuickReplyPhase.Sending, p)
        assertEquals(QuickReplyPhase.Sent, quickReplyOnResult(true))
        assertEquals(QuickReplyPhase.Failed, quickReplyOnResult(false))
    }

    @Test
    fun `notice metninden başarı hükmü - iletildi true, hata false`() {
        assertTrue(quickReplyIsSuccess("Mesaj ajana iletildi"))
        assertTrue(quickReplyIsSuccess("Sonuç: queued"))
        assertFalse(quickReplyIsSuccess("Müdahale başarısız"))
        assertFalse(quickReplyIsSuccess("Ajan şu an kabul etmedi"))
    }

    // ---- 3) FR-003: istatistik ------------------------------------------------

    @Test
    fun `aktif ajan sayısı tur16 canlı filtresiyle aynı yargı`() {
        assertEquals(0, activeAgentCount(emptyList()))
        val rows = listOf(
            row("a", dot = "green", working = true),
            row("b", dot = "grey"),
            row("c", dot = "yellow"),
            row("d", dot = ""),
        )
        // b=grey(bitti) ve d=boş sayılmaz; a ve c sayılır — tur16 liveOnly
        // filtresi: working || dot.isNotEmpty() && dot != grey.
        assertEquals(2, activeAgentCount(rows))
    }

    @Test
    fun `pencere 60 örnekten fazlasını kırpar - eski uç düşer`() {
        val samples = (1..100).map { it.toDouble() }
        val w = sparkWindow(samples, maxPoints = 60)
        assertEquals(60, w.size)
        assertEquals(41.0, w.first(), 1e-9)
        assertEquals(100.0, w.last(), 1e-9)
        // Pencere içi dokunmaz:
        assertEquals(3, sparkWindow(listOf(1.0, 2.0, 3.0)).size)
    }

    @Test
    fun `pencere ortalaması boşta null - sıfır DEĞİL (uydurma yasağı)`() {
        assertNull(sparkRate(emptyList()))
        assertEquals(2.0, sparkRate(listOf(1.0, 2.0, 3.0))!!, 1e-9)
        // Ölçülmüş gerçek 0 ortalama null DEĞİLDİR — "0" gösterilir.
        assertEquals(0.0, sparkRate(listOf(0.0, 0.0))!!, 1e-9)
        assertEquals("—", rateLabel(null))
        assertEquals("0", rateLabel(0.0))
    }

    @Test
    fun `rate etiketi TR virgül ve 100 üstü tam sayı`() {
        assertEquals("8,4", rateLabel(8.42, ','))
        assertEquals("8.4", rateLabel(8.42, '.'))
        assertEquals("123", rateLabel(123.4, ','))
        assertEquals("1,2k", rateLabel(1234.0, ','))
    }
}
