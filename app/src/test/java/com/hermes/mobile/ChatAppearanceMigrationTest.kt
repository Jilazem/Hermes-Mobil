package com.hermes.mobile

import com.hermes.mobile.data.AppSettings
import com.hermes.mobile.data.decodeAppSettings
import com.hermes.mobile.data.migrateChatExperience
import com.hermes.mobile.ui.theme.MOBILE_BETA_THEME_ID
import org.junit.Assert.*
import org.junit.Test

class ChatAppearanceMigrationTest {
    @Test fun `new installs use new appearance version`() {
        assertEquals(MOBILE_BETA_THEME_ID, AppSettings().themeId)
        assertEquals(2, AppSettings().chatExperienceVersion)
    }

    @Test fun `old default themes upgrade once while preferences stay intact`() {
        listOf("chat", "hermes").forEach { id ->
            val old = AppSettings(themeId = id, chatExperienceVersion = 1, followServerSkin = true, fontScale = 1.15f)
            val upgraded = migrateChatExperience(old)
            assertEquals(MOBILE_BETA_THEME_ID, upgraded.themeId)
            assertEquals(2, upgraded.chatExperienceVersion)
            assertTrue(upgraded.followServerSkin)
            assertEquals(1.15f, upgraded.fontScale, 0f)
            assertEquals(upgraded, migrateChatExperience(upgraded))
        }
    }

    @Test fun `explicit builtins and custom themes including shadowed builtin ids are preserved`() {
        listOf("nous", "midnight", "my-theme", "server-skin").forEach { id ->
            assertEquals(id, migrateChatExperience(AppSettings(themeId = id, chatExperienceVersion = 1)).themeId)
        }
        assertEquals("hermes", migrateChatExperience(AppSettings(themeId = "hermes", chatExperienceVersion = 1), setOf("hermes")).themeId)
    }

    @Test fun `old serialized settings without a version still migrate despite new data class default`() {
        val old = decodeAppSettings("{\"themeId\":\"chat\",\"fontScale\":1.15,\"followServerSkin\":true}")
        assertEquals(0, old.chatExperienceVersion)
        assertEquals(MOBILE_BETA_THEME_ID, migrateChatExperience(old).themeId)
        assertTrue(migrateChatExperience(old).followServerSkin)
        val current = decodeAppSettings("{\"themeId\":\"chat\",\"chatExperienceVersion\":2}")
        assertEquals("chat", migrateChatExperience(current).themeId)
    }
}
