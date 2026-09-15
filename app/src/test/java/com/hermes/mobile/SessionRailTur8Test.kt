package com.hermes.mobile

import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.ui.RAIL_LIMIT
import com.hermes.mobile.ui.RAIL_STATUS_IDLE
import com.hermes.mobile.ui.RAIL_STATUS_WORKING
import com.hermes.mobile.ui.RecentRailSession
import com.hermes.mobile.ui.markSeenLive
import com.hermes.mobile.ui.openSessionCount
import com.hermes.mobile.ui.pushRecent
import com.hermes.mobile.ui.railEntries
import com.hermes.mobile.ui.statusOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-8 — sol ray birikimi.
 *
 * Kullanıcı şikâyeti: "2. bir session açınca sol panelde görünmüyor". Kök neden:
 * ray süzgeci yalnız `working/waiting/starting` + o anki oturumu tutuyordu;
 * sunucunun AÇIK saydığı idle oturumlar bir sonraki oturuma geçilir geçilmez
 * düşüyordu (üst başlık "4 açık oturum" derken ray tek hücre).
 *
 * Testler kararı (birleşim / dedupe / sıra / tavan / status eşlemesi / kapanış)
 * saf fonksiyonlar üzerinden kilitler.
 */
class SessionRailTur8Test {

    private fun canli(
        id: String,
        dbId: String = id,
        status: String = RAIL_STATUS_IDLE,
        lastActive: Double = 0.0,
        startedAt: Double = 0.0,
        title: String = id,
    ) = LiveSession(
        id = id,
        title = title,
        status = status,
        lastActive = lastActive,
        startedAt = startedAt,
        sessionKey = dbId,
    )

    private fun acilan(liveId: String, dbId: String, title: String, at: Long, seen: Boolean = false) =
        RecentRailSession(liveId = liveId, dbId = dbId, title = title, openedAt = at, seenLive = seen)

    /** Kullanıcının repro'su: 3 oturum sırayla açılır, üçü de idle'dır. */
    @Test
    fun `siralanan uc oturum rayda birlikte kalir`() {
        val live = listOf(
            canli("l1", "gr", lastActive = 100.0, title = "Greeting"),
            canli("l2", "al", lastActive = 200.0, title = "Altınkale"),
            canli("l3", "wh", lastActive = 300.0, title = "whatsapp"),
        )
        val recent = listOf(
            acilan("l3", "wh", "whatsapp", 3000L, seen = true),
            acilan("l2", "al", "Altınkale", 2000L, seen = true),
            acilan("l1", "gr", "Greeting", 1000L, seen = true),
        )
        val rail = railEntries(live, recent, currentSessionId = "l3")

        assertEquals("açılan üç oturum da ray'da kalmalı", 3, rail.size)
        assertEquals("geçerli oturum en üstte", "l3", rail.first().liveId)
        assertTrue("geçerli hücre işaretli", rail.first().current)
        assertEquals(
            "diğerleri son etkileşim sırasıyla",
            listOf("l3", "l2", "l1"),
            rail.map { it.liveId },
        )
    }

    /** Ray AÇIK oturumları gösterir: idle olmak ray'dan düşme sebebi DEĞİL. */
    @Test
    fun `calisan olmayan acik oturumlar da gorunur`() {
        val live = listOf(
            canli("l1", "gr", status = RAIL_STATUS_IDLE, lastActive = 100.0),
            canli("l2", "al", status = RAIL_STATUS_WORKING, lastActive = 200.0),
        )
        val rail = railEntries(live, recent = emptyList(), currentSessionId = "l1")
        assertEquals(2, rail.size)
        assertTrue(rail.first { it.liveId == "l2" }.isWorking)
        assertFalse(rail.first { it.liveId == "l1" }.busy)
    }

    /** Tur-5 dersi: aynı dbId'yi iki süreç içi kayıt paylaşırsa TEK hücre. */
    @Test
    fun `ayni dbId tasiyan iki canli kayit tek hucre olur`() {
        val live = listOf(
            canli("eskiSurec", "paylasilan", lastActive = 100.0),
            canli("yeniSurec", "paylasilan", lastActive = 900.0),
        )
        val rail = railEntries(live, emptyList(), currentSessionId = null)
        assertEquals(1, rail.size)
        assertEquals("en son etkin süreç kaydı kalır", "yeniSurec", rail.single().liveId)
    }

    /** Aynı süreç içi id iki farklı dbId ile gelirse de tek hücre. */
    @Test
    fun `ayni surec id iki kayit tek hucre olur`() {
        val live = listOf(
            canli("ayni", "dbA", lastActive = 100.0),
            canli("ayni", "dbB", lastActive = 50.0),
        )
        val rail = railEntries(live, emptyList(), currentSessionId = null)
        assertEquals(1, rail.size)
    }

    /** Uygulama içi kayıt sunucu kaydıyla AYNI hücreye katlanır (mükerrer yok). */
    @Test
    fun `uygulama kaydi ile sunucu kaydi ciftlenmez`() {
        val live = listOf(canli("l1", "gr", status = RAIL_STATUS_WORKING, lastActive = 500.0, title = "Greeting"))
        val recent = listOf(acilan("l1", "gr", "Greeting", 900_000L))
        val rail = railEntries(live, recent, currentSessionId = "l1")
        assertEquals(1, rail.size)
        assertEquals("durum sunucudan gelir", RAIL_STATUS_WORKING, rail.single().status)
    }

    /** Sunucudan gelmeyen (REST'ten açılmış) oturum da ray'da kalır. */
    @Test
    fun `sunucuda olmayan acilan oturum kalir`() {
        val recent = listOf(acilan("db9", "db9", "Greeting · 13.09 18:40", 1_700_000_000_000L))
        val rail = railEntries(emptyList(), recent, currentSessionId = "db9")
        assertEquals(1, rail.size)
        assertEquals("Greeting · 13.09 18:40", rail.single().title)
        assertFalse("sunucu kaydı yok", rail.single().live)
        assertTrue(rail.single().current)
    }

    /** Sunucu KAPATTIĞI oturum (daha önce görülmüş, artık listede yok) ray'dan iner. */
    @Test
    fun `sunucudan dusen gorulmus kayit raydan iner`() {
        val recent = listOf(acilan("l1", "gr", "Greeting", 1000L, seen = true))
        val rail = railEntries(emptyList(), recent, currentSessionId = null, liveLoaded = true)
        assertTrue("sunucu kapatmışsa hücre kalmaz", rail.isEmpty())
    }

    /** Kanıt yokken (liste hiç gelmedi) kayıt korunur — ağ sarsıntısı ray'ı boşaltmaz. */
    @Test
    fun `canli liste gelmediyse kayit korunur`() {
        val recent = listOf(acilan("l1", "gr", "Greeting", 1000L, seen = true))
        val rail = railEntries(emptyList(), recent, currentSessionId = null, liveLoaded = false)
        assertEquals(1, rail.size)
    }

    /** Kullanıcı uzun basıp kapattıysa hücre iner; geçerli oturum yine görünür. */
    @Test
    fun `kapatilan hucre iner gecerli oturum kalir`() {
        val live = listOf(
            canli("l1", "gr", lastActive = 100.0),
            canli("l2", "al", lastActive = 200.0),
        )
        val rail = railEntries(
            live,
            emptyList(),
            currentSessionId = "l2",
            dismissed = setOf("gr"),
        )
        assertEquals("kapatılan 'gr' hücresi iner, geçerli 'al' kalır", listOf("al"), rail.map { it.key })

        val railCurrentDismissed = railEntries(
            live,
            emptyList(),
            currentSessionId = "l1",
            dismissed = setOf("gr"),
        )
        assertEquals("geçerli oturum kapatma işaretine rağmen görünür", 2, railCurrentDismissed.size)
    }

    /** Tavan: 8 açık oturumda ray 6 hücre tutar, EN YENİLER kalır. */
    @Test
    fun `tavan asilmaz ve en yeniler kalir`() {
        val live = (1..8).map { i -> canli("l$i", "db$i", lastActive = i * 100.0) }
        val rail = railEntries(live, emptyList(), currentSessionId = null)
        assertEquals(RAIL_LIMIT, rail.size)
        assertEquals(
            "en yüksek etkinlikli (en yeni) 6 tanesi",
            listOf("l8", "l7", "l6", "l5", "l4", "l3"),
            rail.map { it.liveId },
        )
    }

    /** Tavan doluyken GEÇERLİ oturum her zaman içeride. */
    @Test
    fun `tavan doluyken gecerli oturum disarida kalmaz`() {
        val live = (1..8).map { i -> canli("l$i", "db$i", lastActive = i * 100.0) }
        val rail = railEntries(live, emptyList(), currentSessionId = "l1")
        assertEquals(RAIL_LIMIT, rail.size)
        assertEquals("l1", rail.first().liveId)
        assertTrue(rail.first().current)
    }

    /** Status eşlemesi: bitmiş kayıt idle'a, bilinmeyen durum açık sayılır. */
    @Test
    fun `status eslemesi`() {
        assertEquals(RAIL_STATUS_WORKING, statusOf(canli("a", status = "working")))
        assertEquals("waiting", statusOf(canli("a", status = "waiting")))
        assertEquals("starting", statusOf(canli("a", status = "starting")))
        assertEquals(RAIL_STATUS_IDLE, statusOf(canli("a", status = "done")))
        assertEquals("bilinmeyen durum açık sayılır", RAIL_STATUS_IDLE, statusOf(canli("a", status = "???")))
        assertEquals(RAIL_STATUS_IDLE, statusOf(canli("a", status = "")))
    }

    /** Başlık sayacı ray ile AYNI kümeyi saysın: dbId'de tekilleştirilmiş. */
    @Test
    fun `acik oturum sayaci tekilleştirilmis sayar`() {
        val live = listOf(
            canli("l1", "gr"),
            canli("l2", "al"),
            canli("l2b", "al"),
        )
        assertEquals(2, openSessionCount(live))
        assertEquals(
            "sayaç ile ray aynı kümeyi görüyor (recent boşken ve tavan altında)",
            openSessionCount(live),
            railEntries(live, emptyList(), currentSessionId = null).size,
        )
    }

    /** Halka: en yeni önce, hem süreç içi id hem dbId ile tekilleştirir. */
    @Test
    fun `halka tekilleştirir ve en yeniyi one alir`() {
        var ring = emptyList<RecentRailSession>()
        ring = pushRecent(ring, "l1", "gr", "Greeting", 1000L)
        ring = pushRecent(ring, "l2", "al", "Altınkale", 2000L)
        assertEquals(listOf("al", "gr"), ring.map { it.dbId })

        // Aynı oturum farklı süreç içi id ile yeniden açılırsa MÜKERRER satır olmaz.
        ring = pushRecent(ring, "l1-yeni", "gr", "Greeting", 3000L)
        assertEquals(2, ring.size)
        assertEquals("gr", ring.first().dbId)
        assertEquals("yeni süreç içi id kaydedilir", "l1-yeni", ring.first().liveId)
        assertEquals("yeniden açılış damgayı tazeler", 3000L, ring.first().openedAt)
    }

    /** Halka tavanı ve boş kimlik koruması. */
    @Test
    fun `halka tavani ve bos kimlik`() {
        var ring = emptyList<RecentRailSession>()
        ring = pushRecent(ring, "", "", "boş", 1L)
        assertTrue("boş kimlik halkaya girmez", ring.isEmpty())
        (1..20).forEach { i -> ring = pushRecent(ring, "l$i", "db$i", "t$i", i * 10L) }
        assertEquals(com.hermes.mobile.ui.RAIL_RECENT_MAX, ring.size)
        assertEquals("t20", ring.first().title)
    }

    /** "Görüldü" damgası: sunucu listesindeki kayıtlar damgalanır, diğerleri değil. */
    @Test
    fun `goruldu damgasi yalniz listedekilere konur`() {
        val ring = listOf(
            acilan("l1", "gr", "Greeting", 1L),
            acilan("l2", "al", "Altınkale", 2L),
        )
        val marked = markSeenLive(ring, setOf("l1", "yok"))
        assertTrue(marked.first { it.dbId == "gr" }.seenLive)
        assertFalse(marked.first { it.dbId == "al" }.seenLive)
        assertEquals("liste boşsa halka aynen döner", ring, markSeenLive(ring, emptySet()))
    }

    /** Ham id ray etiketi olmaz — FR-001 savunması rayda korunuyor. */
    @Test
    fun `ray etiketi cozulmemis basliktan uretilmez`() {
        val recent = listOf(acilan("538fa088", "20260913_184051_52f76a", "20260913_184051_52f76a", 1L))
        val rail = railEntries(emptyList(), recent, currentSessionId = null)
        assertEquals(1, rail.size)
        assertEquals("?", com.hermes.mobile.ui.railLabel(rail.single().title))
    }

    /** Kapatılan hücre, oturum yeniden açılınca ray'a geri döner. */
    @Test
    fun `yeniden acilan oturumun kapatma isareti silinir`() {
        val kapatilan = setOf("gr", "l1", "baska")
        val temiz = com.hermes.mobile.ui.clearRailDismissal(kapatilan, liveId = "l1", dbId = "gr")
        assertEquals(setOf("baska"), temiz)
        assertEquals(
            "boş küme korunur",
            emptySet<String>(),
            com.hermes.mobile.ui.clearRailDismissal(emptySet(), liveId = "l1", dbId = "gr"),
        )

        // Uçtan uca: kapalı işaretli hücre ray'a döner.
        val live = listOf(canli("l1", "gr", lastActive = 10.0))
        val before = railEntries(live, emptyList(), null, dismissed = setOf("gr"))
        val after = railEntries(live, emptyList(), null, dismissed = temiz)
        assertTrue("kapatma işareti varken hücre yok", before.isEmpty())
        assertEquals("işaret silinince hücre geri gelir", 1, after.size)
    }
}
