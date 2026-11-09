package com.hermes.mobile

import android.app.Application
import com.hermes.mobile.data.BotEventType
import com.hermes.mobile.data.GatewayEvent
import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.LiveBotEventStore
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.data.SubagentTreeStore
import com.hermes.mobile.ui.DrawerRow
import com.hermes.mobile.ui.HomeFilter
import com.hermes.mobile.ui.SessionOrigin
import com.hermes.mobile.ui.classifySessionOrigin
import com.hermes.mobile.ui.homeFeed
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HermesV781StabilityTest {

    @Before
    fun setup() {
        SubagentTreeStore.clear()
        LiveBotEventStore.clear()
    }

    private fun testRow(
        key: String,
        title: String,
        epochSeconds: Double = 1710000000.0,
        working: Boolean = false,
        pinned: Boolean = false,
        followed: Boolean = false,
        dot: String = "grey",
        parentId: String? = null,
        isChild: Boolean = false,
        childRows: List<DrawerRow> = emptyList(),
        jev: String = "",
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
        followed = followed,
        archived = false,
        current = false,
        liveSession = null,
        parentId = parentId,
        isChild = isChild,
        childRows = childRows,
        jev = jev,
    )

    // ==========================================
    // P0: CANLI AKIŞ FİLTRELEME TESTLERİ
    // ==========================================

    @Test
    fun testRunningSystemInternalTaskDoesNotLeakIntoMainFeed() {
        // ÇALIŞAN bir sistem testi (doctor, watchdog, test)
        // isLiveRow = true olsa bile varsayılan ana akışa ASLA sızmamalıdır.
        val doctorRow = testRow("sys-doc", "Hermes Doctor Kontrolü", working = true, dot = "green")
        val watchdogRow = testRow("sys-wd", "Watchdog Kontrolü", working = true, dot = "green")
        val userChat = testRow("user-chat", "Mobil Uygulama Mimarisi", working = false, dot = "grey")

        val rows = listOf(doctorRow, watchdogRow, userChat)
        val sources = mapOf(
            "sys-doc" to "doctor",
            "sys-wd" to "watchdog",
            "user-chat" to "user",
        )
        val sourceOf: (DrawerRow) -> String? = { sources[it.dbId] }

        // 1. Varsayılan ana akışta (showSystemAndSubagents = false): YALNIZCA kullanıcı konusu görünmeli
        val feed = homeFeed(rows, HomeFilter.All, "", sourceOf, showSystemAndSubagents = false)
        assertEquals(1, feed.size)
        assertEquals("user-chat", feed[0].key)

        // 2. Kullanıcı ayarı açarsa (showSystemAndSubagents = true): sistem işleri de listelenir
        val expandedFeed = homeFeed(rows, HomeFilter.All, "", sourceOf, showSystemAndSubagents = true)
        assertEquals(3, expandedFeed.size)
    }

    @Test
    fun testCompletedUserTopicIsNotHiddenInHomeFilterAll() {
        // Tamamlanmış (working=false, dot=grey) kullanıcı sohbeti "Tümü" filtresinde gizlenmemeli
        val activeChat = testRow("act-1", "Aktif Sohbet", working = true, dot = "green")
        val completedChat = testRow("comp-1", "Tamamlanan Sohbet", working = false, dot = "grey")
        val pinnedChat = testRow("pin-1", "Sabit Sohbet", working = false, pinned = true, dot = "grey")

        val rows = listOf(activeChat, completedChat, pinnedChat)
        val sourceOf: (DrawerRow) -> String? = { "user" }

        val feedAll = homeFeed(rows, HomeFilter.All, "", sourceOf, showSystemAndSubagents = false)
        assertEquals(3, feedAll.size)
        // Sıralama kuralı: Sabit → Canlı → En yeni
        assertEquals(listOf("pin-1", "act-1", "comp-1"), feedAll.map { it.key })

        val feedCompleted = homeFeed(rows, HomeFilter.Completed, "", sourceOf)
        assertEquals(2, feedCompleted.size)
        assertTrue(feedCompleted.any { it.key == "comp-1" })
        assertTrue(feedCompleted.any { it.key == "pin-1" })

        val feedWorking = homeFeed(rows, HomeFilter.Working, "", sourceOf)
        assertEquals(1, feedWorking.size)
        assertEquals("act-1", feedWorking[0].key)
    }

    @Test
    fun testSubagentChildWithIsChildFalseIsStrictlyExcludedFromTopLevel() {
        // isChild = false gelse bile parentId doluysa veya SubagentTreeStore'da kayıtlıysa
        // en üst düzey kart olamaz
        val parent = testRow("parent-task", "Ana Görev", working = true)
        val child1 = testRow("child-1", "Alt Ajan 1", isChild = false, parentId = "parent-task")
        val child2 = testRow("child-2", "Alt Ajan 2", isChild = false, parentId = null)

        // child2'yi SubagentTreeStore ile bağla
        SubagentTreeStore.record("child-2", "parent-task")

        val rows = listOf(parent, child1, child2)
        val sourceOf: (DrawerRow) -> String? = { "user" }

        val feed = homeFeed(rows, HomeFilter.All, "", sourceOf)
        assertEquals(1, feed.size)
        assertEquals("parent-task", feed[0].key)
    }

    @Test
    fun testUnknownOriginIsNotGuessedAsUserCreated() {
        // source == null olduğunda başlık ne olursa olsun UNKNOWN dönmeli, kullanıcı konusu tahmin edilmemeli
        val rowWithNiceTitle = testRow("unk-1", "Özel Proje Çözümü", working = false)
        val origin = classifySessionOrigin(rowWithNiceTitle, null)
        assertEquals(SessionOrigin.UNKNOWN, origin)
        assertFalse(origin.isUserTopic)

        // Varsayılan ana akışta görünmez (kullanıcı takip etmedikçe)
        val feed = homeFeed(listOf(rowWithNiceTitle), HomeFilter.All, "", { null }, showSystemAndSubagents = false)
        assertTrue(feed.isEmpty())

        // Kullanıcı açıkça takip ettiğinde (followed = true) görünür
        val followedRow = rowWithNiceTitle.copy(followed = true)
        val followedFeed = homeFeed(listOf(followedRow), HomeFilter.All, "", { null }, showSystemAndSubagents = false)
        assertEquals(1, followedFeed.size)
    }

    // ==========================================
    // P0: CANLI OLAY TEKİLLEŞTİRME TESTLERİ
    // ==========================================

    @Test
    fun testToolStartAndToolCompleteBindToTheExactSameCard() {
        val toolCallId = "call_bash_999888"
        val startPayload = buildJsonObject {
            put("tool_id", toolCallId)
            put("name", "bash")
            put("text", "ls -la /workspace")
        }

        // 1. Araç başlangıcı
        LiveBotEventStore.processGatewayEvent(
            GatewayEvent(
                type = "tool.start",
                sessionId = "sess-tool",
                payload = startPayload,
            )
        )

        val eventsAfterStart = LiveBotEventStore.events.value
        assertEquals(1, eventsAfterStart.size)
        val cardStart = eventsAfterStart[0]
        assertEquals("tool_sess-tool_$toolCallId", cardStart.id)
        assertEquals("working", cardStart.status)
        assertEquals(BotEventType.TOOL_CALL, cardStart.type)

        // 2. Araç bitişi (aynı tool_id ile)
        val completePayload = buildJsonObject {
            put("tool_id", toolCallId)
            put("name", "bash")
            put("text", "total 12\n-rw-r--r-- 1 root root 4096 main.kt")
        }

        LiveBotEventStore.processGatewayEvent(
            GatewayEvent(
                type = "tool.complete",
                sessionId = "sess-tool",
                payload = completePayload,
            )
        )

        // KART SAYISI 1 OLMALI (yeni kart açılmamalı, aynı kart güncellenmeli)
        val eventsAfterComplete = LiveBotEventStore.events.value
        assertEquals(1, eventsAfterComplete.size)
        val cardComplete = eventsAfterComplete[0]
        assertEquals("tool_sess-tool_$toolCallId", cardComplete.id)
        assertEquals("completed", cardComplete.status)
        assertTrue(cardComplete.terminalOutput?.contains("main.kt") == true)
    }

    @Test
    fun testMessageDeltaStreamingDeduplicationAcrossReconnect() {
        val turnId = "turn_781_alpha"
        val sid = "sess-stream"

        // 1. İlk akış: "Merhaba"
        LiveBotEventStore.processGatewayEvent(
            GatewayEvent(
                type = "message.delta",
                sessionId = sid,
                payload = buildJsonObject {
                    put("turn_id", turnId)
                    put("text", "Merhaba")
                    put("offset", 0)
                },
            )
        )

        // 2. İkinci parça: " dünya!"
        LiveBotEventStore.processGatewayEvent(
            GatewayEvent(
                type = "message.delta",
                sessionId = sid,
                payload = buildJsonObject {
                    put("turn_id", turnId)
                    put("text", " dünya!")
                    put("offset", 7)
                },
            )
        )

        var events = LiveBotEventStore.events.value
        assertEquals(1, events.size)
        assertEquals("Merhaba dünya!", events[0].content)

        // 3. Yeniden bağlantı: Sunucu re-send ile aynı " dünya!" parçasını tekrar gönderdi (offset=7)
        LiveBotEventStore.processGatewayEvent(
            GatewayEvent(
                type = "message.delta",
                sessionId = sid,
                payload = buildJsonObject {
                    put("turn_id", turnId)
                    put("text", " dünya!")
                    put("offset", 7)
                },
            )
        )

        // İÇERİK İKİYE KATLANMAMALI, TEKİL KALMALI
        events = LiveBotEventStore.events.value
        assertEquals(1, events.size)
        assertEquals("Merhaba dünya!", events[0].content)

        // 4. Turn tamamlandı
        LiveBotEventStore.processGatewayEvent(
            GatewayEvent(
                type = "message.complete",
                sessionId = sid,
                payload = buildJsonObject {
                    put("turn_id", turnId)
                    put("text", "Merhaba dünya!")
                },
            )
        )

        events = LiveBotEventStore.events.value
        assertEquals(1, events.size)
        assertEquals("completed", events[0].status)
        assertFalse(events[0].isStreaming)
    }

    @Test
    fun testSeedFromSessionsSafeSync() {
        val live = listOf(
            LiveSession(id = "live-1", status = "working", title = "Canlı Görev 1", preview = "Derleniyor..."),
        )
        val sessions = listOf(
            HermesSession(id = "hist-1", serverTitle = "Geçmiş Oturum", startedAt = 1710000000.0, endedAt = 1710000500.0),
        )

        LiveBotEventStore.seedFromSessions(sessions, live)
        val events = LiveBotEventStore.events.value
        assertEquals(2, events.size)
        assertTrue(events.any { it.id == "seed_live_live-1" && it.status == "working" })
        assertTrue(events.any { it.id == "seed_rest_hist-1" && it.status == "completed" })
    }

    // ==========================================
    // P1: 9120 / 9150 PORT VE ŞEMA MİMARİSİ
    // ==========================================

    @Test
    fun testServerProfilePortAndSchemeDerivation() {
        // 1. Standart LAN HTTP Profili (Dashboard: 9120, Webapp: 9150)
        val lanProfile = ServerProfile(
            name = "Ev LAN",
            baseUrl = "http://192.168.1.101:9150",
            token = "secret123",
        )
        assertEquals("http://192.168.1.101:9120/#/", lanProfile.webDashboardUrl)
        assertEquals("http://192.168.1.101:9150/#/", lanProfile.webappUrl)
        assertEquals("ws://192.168.1.101:9150", lanProfile.wsBase)

        // 2. Güvenli Harici TLS Profili (HTTPS şeması asla HTTP'ye düşürülmez!)
        val tlsProfile = ServerProfile(
            name = "Bulut Domain",
            baseUrl = "https://hermes.example.com",
            token = "secret456",
        )
        assertEquals("https://hermes.example.com:9120/#/", tlsProfile.webDashboardUrl)
        assertEquals("https://hermes.example.com:9150/#/", tlsProfile.webappUrl)
        assertEquals("wss://hermes.example.com", tlsProfile.wsBase)

        // 3. Özel açık URL yapılandırması verildiğinde açık adresler önceliklidir
        val customProfile = ServerProfile(
            name = "Özel Portlar",
            baseUrl = "http://10.0.0.5:9150",
            token = "secret789",
            dashboardUrl = "http://10.0.0.5:8080",
            webappUrlConfig = "http://10.0.0.5:8090",
        )
        assertEquals("http://10.0.0.5:8080/#/", customProfile.webDashboardUrl)
        assertEquals("http://10.0.0.5:8090/#/", customProfile.webappUrl)
    }
}
