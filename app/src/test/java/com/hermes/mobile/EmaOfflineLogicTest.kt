package com.hermes.mobile

import com.hermes.mobile.data.*
import org.junit.Assert.*
import org.junit.Test

class EmaOfflineLogicTest {
    private val vocab=(" abcçdefgğhıijklmnoöpqrsştuüvwxyz.,!?;:-'\"").map { it.toString() }.toSet()
    @Test fun turkishCasingAndNumbersAreSpoken() {
        assertEquals("ışık bugün saat on dört otuz iki yüz beş türk lirası",EmaText.normalize("IŞIK BUGÜN saat 14:30 205 TL",vocab))
        assertEquals("sıfır bir altı yedi dokuz altı",EmaText.normalize("016796",vocab))
        assertEquals("yirmi bir virgül beş kilogram",EmaText.normalize("21,5 kg",vocab))
    }
    @Test fun longRepliesRemainBoundedWithoutLosingWords() {
        val input=("Bugün " + "hava güzel. ".repeat(100)).trim()
        val pieces=EmaText.pieces(input,180)
        assertTrue(pieces.all { it.length<=180 })
        assertEquals(input,pieces.joinToString(" "))
        assertTrue(EmaText.pieces("").isEmpty())
        assertTrue(EmaText.pieces("a".repeat(500),180).all { it.length<=180 })
    }
    @Test fun modelErrorsCannotBeSavedAsSuccessfulSwitches() {
        assertTrue(modelSwitchAccepted("✓ Model switched: glm-5.3"))
        assertFalse(modelSwitchAccepted("requires available credits"))
        assertFalse(modelSwitchAccepted("✓ operation failed"))
        assertFalse(modelSwitchAccepted("Error: provider not found"))
        assertFalse(modelSwitchAccepted(""))
    }
    @Test fun downloadedModeEnablesUnifiedConversationWithoutServerToken() {
        assertTrue(AppSettings(emaMode="offline").emaEnabled)
        assertTrue(VoicePrefs(emaMode="offline").emaEnabled)
        assertTrue(AppSettings(emaToken="configured").emaEnabled)
        assertFalse(AppSettings().emaEnabled)
    }
}
