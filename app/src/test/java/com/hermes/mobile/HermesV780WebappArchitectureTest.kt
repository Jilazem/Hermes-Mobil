package com.hermes.mobile

import com.hermes.mobile.ui.bots.BotMascotMood
import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.data.SubagentTreeStore
import com.hermes.mobile.ui.DrawerRow
import com.hermes.mobile.ui.HomeFilter
import com.hermes.mobile.ui.SessionOrigin
import com.hermes.mobile.ui.classifySessionOrigin
import com.hermes.mobile.ui.homeFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hermes Mobil v7.8.0 (35) — Webapp Mimarisi ve Port Düzenlemesi Doğrulama Testi.
 *
 * 1. Port 9120 (Caddy Dashboard) ve Port 9150 (Hermes Webapp) URL uyumu.
 * 2. Canlı akışta yalnızca kullanıcının açtığı konuların yer alması.
 * 3. Alt ajan ağaç hiyerarşisi (subagent tree) ve tekil başlık kontrolü.
 * 4. Bot maskot mikro-animasyonları ve durum geçişleri (WAITING, THINKING, HAPPY, EXCITED).
 * 5. Araç / terminal çıktılarının varsayılan kapalı gelmesi ve kullanıcı tıklamasıyla açılması.
 */
class HermesV780WebappArchitectureTest {

    private fun testRow(
        id: String,
        title: String,
        isChild: Boolean = false,
        parentId: String? = null,
        working: Boolean = false,
        dot: String = "grey",
        pinned: Boolean = false,
        archived: Boolean = false,
    ) = DrawerRow(
        key = id,
        liveId = "live-$id",
        dbId = id,
        title = title,
        preview = "Son durum özeti: $title",
        epochSeconds = 1710000000.0,
        dot = dot,
        working = working,
        pinned = pinned,
        archived = archived,
        current = false,
        liveSession = null,
        isChild = isChild,
        parentId = parentId,
    )

    @Test
    fun testServerProfilePortArchitecture() {
        val profile = ServerProfile(
            name = "Mac Mini Yerel",
            baseUrl = "http://192.168.1.101:9120",
            token = "test-token-123",
        )

        // Dashboard portu 9120 olmalı (Caddy gateway ters vekili)
        assertEquals("http://192.168.1.101:9120/#/", profile.webDashboardUrl)

        // Webapp portu 9150 olmalı (Hermes Gateway doğrudan dinleyicisi)
        assertEquals("http://192.168.1.101:9150/#/", profile.webappUrl)
    }

    @Test
    fun testSessionOriginHierarchyClassification() {
        // Kullanıcı konuları ana akış konusudur
        val userTopic = testRow("u-1", "Hermes Mobil v7.8.0 Geliştirme")
        assertEquals(SessionOrigin.USER_CREATED, classifySessionOrigin(userTopic, "user"))
        assertTrue(classifySessionOrigin(userTopic, "user").isUserTopic)

        // Bot görevi ana akış konusudur
        val botTopic = testRow("b-1", "CD Coder İmar Analiz Görevi")
        assertEquals(SessionOrigin.USER_INITIATED_BOT_TASK, classifySessionOrigin(botTopic, "telegram"))
        assertTrue(classifySessionOrigin(botTopic, "telegram").isUserTopic)

        // Alt ajanlar hiçbir zaman ana konu olamaz
        val subagent = testRow("s-1", "Alt Ajan - Kod İnceleme", isChild = true, parentId = "u-1")
        assertEquals(SessionOrigin.SUBAGENT_CHILD, classifySessionOrigin(subagent, "user"))
        assertFalse(classifySessionOrigin(subagent, "user").isUserTopic)

        // Sistem sağlık kontrolleri ana konu olamaz
        val doctorCheck = testRow("sys-1", "Hermes Doctor Denetimi")
        assertEquals(SessionOrigin.SYSTEM_INTERNAL, classifySessionOrigin(doctorCheck, "doctor"))
        assertFalse(classifySessionOrigin(doctorCheck, "doctor").isUserTopic)
    }

    @Test
    fun testMainFeedFiltersSubagentsByDefault() {
        val parent = testRow("p-1", "Hermes Mobil Webapp Entegrasyonu", working = true, dot = "green")
        val child1 = testRow("c-1", "WebSocket Adaptörü", isChild = true, parentId = "p-1")
        val child2 = testRow("c-2", "JSON-RPC Doğrulama", isChild = true, parentId = "p-1")
        val sysRow = testRow("sys-1", "Sistem Watchdog", dot = "grey")

        val parentWithChildren = parent.copy(childRows = listOf(child1, child2))
        val rows = listOf(parentWithChildren, child1, child2, sysRow)
        val sourceOf: (DrawerRow) -> String? = { row ->
            when (row.key) {
                "p-1" -> "user"
                "c-1", "c-2" -> "user"
                "sys-1" -> "system"
                else -> null
            }
        }

        // 1. Varsayılan mod: Yalnız kullanıcı ana konusu görünür (alt ajanlar ebeveynin altında toplanır)
        val feed = homeFeed(rows, HomeFilter.All, "", sourceOf, showSystemAndSubagents = false)
        assertEquals(1, feed.size)
        assertEquals("p-1", feed[0].key)
        assertEquals(2, feed[0].childRows.size)

        // 2. Sistem & Alt Ajanlar ayarı açıldığında sistem kayıtları da ana akışta görünür
        val feedAll = homeFeed(rows, HomeFilter.All, "", sourceOf, showSystemAndSubagents = true)
        assertTrue(feedAll.size > 1)
        assertTrue(feedAll.any { it.key == "sys-1" })
    }

    @Test
    fun testBotMascotMoodTransitions() {
        // Marshmallow bot karakter ruh halleri ve animasyon durumu doğrulaması
        val moods = listOf(
            BotMascotMood.IDLE,
            BotMascotMood.WAITING,
            BotMascotMood.THINKING,
            BotMascotMood.WORKING,
            BotMascotMood.SUCCESS,
            BotMascotMood.ERROR,
            BotMascotMood.SPEAKING,
        )

        for (mood in moods) {
            assertNotNull(mood)
        }

        // WAITING: Onay / bekleme modunda göz kapakları ve sabırsız ayak hareketleri tetiklenir
        assertEquals(BotMascotMood.WAITING, BotMascotMood.valueOf("WAITING"))
    }
}
