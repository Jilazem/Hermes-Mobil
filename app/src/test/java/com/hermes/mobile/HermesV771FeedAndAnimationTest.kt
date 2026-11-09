package com.hermes.mobile

import android.app.Application
import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.data.SubagentTreeStore
import com.hermes.mobile.ui.DrawerRow
import com.hermes.mobile.ui.HomeFilter
import com.hermes.mobile.ui.SessionOrigin
import com.hermes.mobile.ui.bots.AnimationIntensity
import com.hermes.mobile.ui.bots.BotMascotMood
import com.hermes.mobile.ui.classifySessionOrigin
import com.hermes.mobile.ui.drawerRows
import com.hermes.mobile.ui.homeFeed
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HermesV771FeedAndAnimationTest {

    @Before
    fun setup() {
        SubagentTreeStore.clear()
    }

    private fun testRow(
        key: String,
        title: String,
        epochSeconds: Double = 1710000000.0,
        working: Boolean = false,
        pinned: Boolean = false,
        dot: String = "grey",
        parentId: String? = null,
        isChild: Boolean = false,
        childRows: List<DrawerRow> = emptyList(),
    ) = DrawerRow(
        key = key,
        liveId = "",
        dbId = key,
        title = title,
        preview = "Önizleme $key",
        epochSeconds = epochSeconds,
        dot = dot,
        working = working,
        pinned = pinned,
        archived = false,
        current = false,
        liveSession = null,
        parentId = parentId,
        isChild = isChild,
        childRows = childRows,
    )

    @Test
    fun testSessionOriginClassification() {
        // 1. Subagent child
        val childRow = testRow("sub-1", "Alt Görev · Test", parentId = "parent-1", isChild = true)
        assertEquals(SessionOrigin.SUBAGENT_CHILD, classifySessionOrigin(childRow, "user"))

        // 2. Scheduled automation (cron / scheduler)
        val cronRow = testRow("cron-1", "Zamanlanmış Rapor")
        assertEquals(SessionOrigin.SCHEDULED_AUTOMATION, classifySessionOrigin(cronRow, "cron"))
        assertEquals(SessionOrigin.SCHEDULED_AUTOMATION, classifySessionOrigin(cronRow, "scheduler"))

        // 3. System internal (doctor / watchdog / test / health)
        val systemRow = testRow("sys-1", "Sistem Denetimi")
        assertEquals(SessionOrigin.SYSTEM_INTERNAL, classifySessionOrigin(systemRow, "doctor"))
        assertEquals(SessionOrigin.SYSTEM_INTERNAL, classifySessionOrigin(systemRow, "system"))
        assertEquals(SessionOrigin.SYSTEM_INTERNAL, classifySessionOrigin(systemRow, "watchdog"))

        // 4. User created / User initiated bot tasks
        val userRow = testRow("user-1", "Mobil Geliştirme")
        assertEquals(SessionOrigin.USER_CREATED, classifySessionOrigin(userRow, "user"))
        assertEquals(SessionOrigin.USER_CREATED, classifySessionOrigin(userRow, "chat"))

        val botTaskRow = testRow("bot-1", "CD Coder Görevi")
        assertEquals(SessionOrigin.USER_INITIATED_BOT_TASK, classifySessionOrigin(botTaskRow, "telegram"))

        // 5. Unknown origin
        val unknownRow = testRow("unk-1", "Sohbet")
        assertEquals(SessionOrigin.UNKNOWN, classifySessionOrigin(unknownRow, null))
    }

    @Test
    fun testMainFeedFiltersOutSubagentsAndSystemTasksByDefault() {
        // Senaryo: 2 Ana Kullanıcı Konusu + 10 Alt Ajan (5 + 5) + 2 Sistem Testi + 2 Cron Bildirimi
        val parent1 = testRow("main-1", "Hermes Mobil Geliştirme", working = true, dot = "green")
        val parent2 = testRow("main-2", "RaporPro Denetimi", working = false, dot = "green")

        val subagentsP1 = (1..5).map { i ->
            testRow("sub-p1-$i", "Alt Görev $i", parentId = "main-1", isChild = true)
        }
        val subagentsP2 = (1..5).map { i ->
            testRow("sub-p2-$i", "Alt Görev ${i + 5}", parentId = "main-2", isChild = true)
        }

        // Subagents are nested under their respective parents
        val parent1WithChildren = parent1.copy(childRows = subagentsP1)
        val parent2WithChildren = parent2.copy(childRows = subagentsP2)

        val system1 = testRow("sys-1", "Hermes Doctor Kontrolü", dot = "grey")
        val system2 = testRow("sys-2", "Watchdog Kontrolü", dot = "grey")
        val cron1 = testRow("cron-1", "Yedek Alma Bildirimi", dot = "grey")
        val cron2 = testRow("cron-2", "Veritabanı Temizliği", dot = "grey")

        val allRows = listOf(parent1WithChildren, parent2WithChildren) + subagentsP1 + subagentsP2 + listOf(system1, system2, cron1, cron2)

        val sources = mapOf(
            "main-1" to "user",
            "main-2" to "user",
            "sys-1" to "doctor",
            "sys-2" to "system",
            "cron-1" to "cron",
            "cron-2" to "cron",
        )
        val sourceOf: (DrawerRow) -> String? = { sources[it.dbId] }

        // 1. Varsayılan mod (showSystemAndSubagents = false):
        // Ana akışta YALNIZCA 2 ana kullanıcı konusu görünmelidir!
        val defaultFeed = homeFeed(allRows, HomeFilter.All, "", sourceOf, showSystemAndSubagents = false)
        assertEquals(2, defaultFeed.size)
        assertEquals(listOf("main-1", "main-2"), defaultFeed.map { it.key })

        // Alt ajanlar ana konunun altında eksiksiz yaşamaktadır
        val firstParent = defaultFeed.first { it.key == "main-1" }
        assertEquals(5, firstParent.childRows.size)

        val secondParent = defaultFeed.first { it.key == "main-2" }
        assertEquals(5, secondParent.childRows.size)

        // 2. Ayar açıldığında (showSystemAndSubagents = true):
        // Kullanıcı tüm faaliyetleri görmek istediğinde sistem oturumları da listeye dahil olur
        val expandedFeed = homeFeed(allRows, HomeFilter.All, "", sourceOf, showSystemAndSubagents = true)
        assertTrue(expandedFeed.size > 2)
        assertTrue(expandedFeed.any { it.key == "sys-1" })
    }

    @Test
    fun testDrawerRowsAltAjanNestingIntegration() {
        // REST Session'ları üzerinden tam ağaç hiyerarşisi doğrulaması
        val parent = HermesSession(
            id = "sess-parent",
            serverTitle = "Hermes Mobil v7.7.1 Geliştirme",
            startedAt = 1710000000.0,
        )

        // 10 alt ajan (5 çalışan, 5 tamamlanan)
        val children = (1..10).map { i ->
            HermesSession(
                id = "sess-child-$i",
                serverTitle = "Alt Görev $i",
                parentSessionId = "sess-parent",
                startedAt = 1710000000.0 + i,
                endedAt = if (i > 5) 1710000010.0 + i else null, // 5 tanesi tamamlandı
            )
        }

        val liveChildren = (1..5).map { i ->
            LiveSession(id = "sess-child-$i", status = "working")
        }
        val liveMap = liveChildren.associateBy { it.id }

        // Kayıtları SubagentTreeStore ile de bağla
        children.forEach { SubagentTreeStore.record(it.id, "sess-parent") }

        val rows = drawerRows(
            sessions = listOf(parent) + children,
            live = liveChildren,
            liveByDbId = liveMap,
            flags = SessionFlags(),
            cronNames = emptyMap(),
        )

        // Ana listede yalnızca tek ana oturum olmalı, 10 alt oturum bağımsız listelenmemeli
        assertEquals(1, rows.size)
        val mainRow = rows.first()
        assertEquals("sess-parent", mainRow.dbId)
        assertEquals(10, mainRow.childRows.size)

        // Çalışan alt ajan sayısı
        val workingCount = mainRow.childRows.count { it.working || it.dot == "green" }
        assertEquals(5, workingCount)
    }

    @Test
    fun testBotMascotMoodsAndAnimationSettings() {
        // WAITING mood eklendi mi ve geçerli mi?
        val waiting = BotMascotMood.WAITING
        assertEquals("WAITING", waiting.name)

        assertEquals("IDLE", BotMascotMood.IDLE.name)
        assertEquals("THINKING", BotMascotMood.THINKING.name)
        assertEquals("WORKING", BotMascotMood.WORKING.name)
        assertEquals("SUCCESS", BotMascotMood.SUCCESS.name)
        assertEquals("ERROR", BotMascotMood.ERROR.name)
        assertEquals("SPEAKING", BotMascotMood.SPEAKING.name)

        // Yoğunluk modları
        assertEquals("OFF", AnimationIntensity.OFF.name)
        assertEquals("LIGHT", AnimationIntensity.LIGHT.name)
        assertEquals("NORMAL", AnimationIntensity.NORMAL.name)
    }
}
