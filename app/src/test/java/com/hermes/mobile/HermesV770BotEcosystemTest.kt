package com.hermes.mobile

import android.app.Application
import com.hermes.mobile.data.BotEventType
import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.LiveBotEvent
import com.hermes.mobile.data.LiveBotEventStore
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.data.SubagentTreeStore
import com.hermes.mobile.data.botDefinition
import com.hermes.mobile.ui.HomeFilter
import com.hermes.mobile.ui.SmartSessionTitle
import com.hermes.mobile.ui.bots.AnimationIntensity
import com.hermes.mobile.ui.bots.BotMascotMood
import com.hermes.mobile.ui.drawerRows
import com.hermes.mobile.ui.filterLiveEvents
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HermesV770BotEcosystemTest {

    @Before
    fun setup() {
        SubagentTreeStore.clear()
        LiveBotEventStore.clear()
    }

    @Test
    fun testSmartSessionTitleExtraction() {
        // High priority keywords extraction
        assertEquals(
            "RaporPro Kod Denetimi",
            SmartSessionTitle.deriveTitle("Sohbet", "RaporPro kod denetimi yap ve hataları listele")
        )
        assertEquals(
            "Video Studio Render Görevi",
            SmartSessionTitle.deriveTitle("chat", "Video studio render görevini başlat")
        )
        assertEquals(
            "Hermes Mobil Android Derleme",
            SmartSessionTitle.deriveTitle("?", "Hermes mobil android derleme işlemi yapılıyor")
        )
        assertEquals(
            "Bot Oluşturma",
            SmartSessionTitle.deriveTitle("", "Yeni bir bot oluşturma aşaması")
        )
        assertEquals(
            "Alt Görev — Arayüz Testi",
            SmartSessionTitle.deriveTitle("sohbet", "alt görev — arayüz testi başlatıldı")
        )

        // Custom user title must never be overridden
        assertEquals(
            "Kullanıcının Özel Başlığı",
            SmartSessionTitle.deriveTitle("Kullanıcının Özel Başlığı", "RaporPro denetimi")
        )

        // Generic fallback with timestamp
        val derived = SmartSessionTitle.deriveTitle("SOHBET", "", epochSeconds = 1710000000.0)
        assertTrue(derived.startsWith("Oturum · "))

        // Subagent fallback naming
        assertEquals(
            "Alt Görev · abcdef",
            SmartSessionTitle.deriveTitle("Sohbet", "", isSubagent = true, sessionId = "subagent-abcdef")
        )
    }

    @Test
    fun testSmartSessionTitleStatusLabel() {
        assertEquals("Çalışıyor", SmartSessionTitle.statusLabel(true, ""))
        assertEquals("Çalışıyor", SmartSessionTitle.statusLabel(false, "green"))
        assertEquals("Bekliyor", SmartSessionTitle.statusLabel(false, "yellow"))
        assertEquals("Hata", SmartSessionTitle.statusLabel(false, "red"))
        assertEquals("Tamamlandı", SmartSessionTitle.statusLabel(false, "grey"))
        assertEquals("Tamamlandı", SmartSessionTitle.statusLabel(false, ""))
    }

    @Test
    fun testDrawerRowsDeduplication() {
        // Both REST and Live have session id "sess-100"
        val restSession = HermesSession(
            id = "sess-100",
            serverTitle = "RaporPro Denetimi",
            preview = "Denetim devam ediyor",
            startedAt = 1710000000.0,
        )
        val liveSession = LiveSession(
            id = "sess-100",
            title = "RaporPro Denetimi",
            preview = "Denetim devam ediyor",
            startedAt = 1710000000.0,
        )

        val rows = drawerRows(
            sessions = listOf(restSession),
            live = listOf(liveSession),
            liveByDbId = mapOf("sess-100" to liveSession),
            flags = SessionFlags(),
            cronNames = emptyMap(),
        )

        // Must be exactly 1 row, not 2 duplicate rows
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals("sess-100", row.dbId)
        assertEquals("sess-100", row.liveId)
    }

    @Test
    fun testSubagentTreeHierarchyAndActiveCount() {
        SubagentTreeStore.record("sub-1", "main-task-1")
        SubagentTreeStore.record("sub-2", "main-task-1")

        val parentSession = HermesSession(
            id = "main-task-1",
            serverTitle = "Hermes Mobil Geliştirme",
            startedAt = 1710000000.0,
        )
        val child1 = HermesSession(
            id = "sub-1",
            serverTitle = "Android Arayüz Tasarımı",
            parentSessionId = "main-task-1",
            startedAt = 1710000010.0,
            endedAt = 1710000015.0, // Tamamlanmış alt ajan
        )
        val child2 = HermesSession(
            id = "sub-2",
            serverTitle = "WebSocket Entegrasyonu",
            parentSessionId = "main-task-1",
            startedAt = 1710000020.0,
        )

        // child2 is actively working
        val liveChild2 = LiveSession(
            id = "sub-2",
            title = "WebSocket Entegrasyonu",
            status = "working",
        )

        val rows = drawerRows(
            sessions = listOf(parentSession, child1, child2),
            live = listOf(liveChild2),
            liveByDbId = mapOf("sub-2" to liveChild2),
            flags = SessionFlags(),
            cronNames = emptyMap(),
        )

        assertEquals(1, rows.size)
        val parent = rows.first()
        assertEquals("main-task-1", parent.dbId)
        assertEquals(2, parent.childRows.size)

        val workingCount = parent.childRows.count { it.dot == "green" }
        assertEquals(1, workingCount)
    }

    @Test
    fun testLiveBotEventStoreDeltaStreamingAggregation() {
        val store = LiveBotEventStore

        // 1. Initial event
        val event1 = LiveBotEvent(
            id = "ev-1",
            sessionId = "sess-1",
            botName = "CD Coder",
            mood = BotMascotMood.WORKING,
            type = BotEventType.BOT_MESSAGE,
            title = "Mesaj üretiyor",
            content = "Kaynak kodları inceliyorum. ",
            isStreaming = true,
            status = "working",
        )
        store.appendOrUpdate(event1)
        assertEquals(1, store.events.value.size)

        // 2. Stream chunk into same event id
        val eventChunk = LiveBotEvent(
            id = "ev-1",
            sessionId = "sess-1",
            botName = "CD Coder",
            mood = BotMascotMood.WORKING,
            type = BotEventType.BOT_MESSAGE,
            title = "Mesaj üretiyor",
            content = "17 dosya kontrol edildi.",
            isStreaming = true,
            status = "working",
        )
        store.appendOrUpdate(eventChunk)
        // Must still be 1 event with concatenated content
        assertEquals(1, store.events.value.size)
        assertEquals("Kaynak kodları inceliyorum. 17 dosya kontrol edildi.", store.events.value.first().content)

        // 3. Task completed event
        val eventDone = LiveBotEvent(
            id = "ev-done",
            sessionId = "sess-1",
            botName = "CD Android",
            mood = BotMascotMood.SUCCESS,
            type = BotEventType.TASK_COMPLETED,
            title = "Görev tamamlandı",
            content = "Derleme tamamlandı. Testler başarılı.",
            status = "completed",
        )
        store.appendOrUpdate(eventDone)
        assertEquals(2, store.events.value.size)

        // Filtering
        val workingFiltered = filterLiveEvents(store.events.value, HomeFilter.Working, "")
        assertEquals(1, workingFiltered.size)

        val completedFiltered = filterLiveEvents(store.events.value, HomeFilter.Completed, "")
        assertEquals(1, completedFiltered.size)
        assertEquals("Derleme tamamlandı. Testler başarılı.", completedFiltered.first().content)
    }

    @Test
    fun testBotDefinitionExecutionTargetSerialization() {
        val def = botDefinition(
            name = "Mobil Test Botu",
            emoji = "🤖",
            persona = "Sen test botusun",
            model = "gemini",
            think = "high",
            welcome = "Merhaba",
            tools = setOf("web_search"),
            executionTarget = "hybrid",
        )
        assertEquals("hybrid", (def["execution_target"] as? JsonPrimitive)?.content)

        val defaultDef = botDefinition(
            name = "Varsayılan Bot",
            emoji = "🤖",
            persona = "Varsayılan",
            model = "hermes",
            think = "off",
            welcome = "",
            tools = emptySet(),
        )
        assertEquals("server", (defaultDef["execution_target"] as? JsonPrimitive)?.content)
    }

    @Test
    fun testBotAnimationModes() {
        val normal = AnimationIntensity.NORMAL
        val light = AnimationIntensity.LIGHT
        val off = AnimationIntensity.OFF

        assertEquals("NORMAL", normal.name)
        assertEquals("LIGHT", light.name)
        assertEquals("OFF", off.name)
    }
}
