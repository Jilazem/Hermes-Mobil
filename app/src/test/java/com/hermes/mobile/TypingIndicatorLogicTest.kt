package com.hermes.mobile

import com.hermes.mobile.ui.typingIndicatorVisible
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TUR-29A madde 4 — AITypingIndicator benzeri durum göstergesi karar mantığı.
 * Saf fonksiyon: ajan meşgul AMA hiçbir şey akmıyorsa göster.
 */
class TypingIndicatorLogicTest {

    /** Meşgul + akan assistant yok + canlı düşünme yok → göster. */
    @Test
    fun beklerkenGosterir() {
        val items = listOf(
            ChatItem.User("u1", "selam"),
            ChatItem.Tool("t1", "Bash", ToolState.Running),
        )
        assertTrue(typingIndicatorVisible(agentBusy = true, items = items))
    }

    /** Assistant akarken gizlenir (imleç zaten var). */
    @Test
    fun akisSirasindaGizler() {
        val items = listOf(ChatItem.Assistant("a1", "kısmi yanıt", streaming = true))
        assertFalse(typingIndicatorVisible(agentBusy = true, items = items))
    }

    /** Canlı düşünme sırasında gizlenir (kendi canlı bloğu var). */
    @Test
    fun canliDusunmeSirasindaGizler() {
        val items = listOf(ChatItem.Thinking("th1", "düşünüyorum", live = true))
        assertFalse(typingIndicatorVisible(agentBusy = true, items = items))
    }

    /** Ajan boştayken (onay bekliyor, hata) asla göstermez. */
    @Test
    fun bosAjandaGizler() {
        val items = listOf(ChatItem.User("u1", "selam"))
        assertFalse(typingIndicatorVisible(agentBusy = false, items = items))
    }
}
