package com.hermes.mobile

import com.hermes.mobile.data.ChatIsolation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K12b: sohbetler arası olay izolasyonu — gateway aynı WS bağlantısına
 * birden çok oturumun olayını `session_id` etiketiyle basar; istemci
 * etiketi yok sayarsa bir sohbetin yanıtı DİĞER sohbete düşer. Bu test
 * filtre kuralının saf sözleşmesini sabitler.
 */
class ChatIsolationTest {

    @Test
    fun `etiketli olay baska oturumdan ise yabancidir`() {
        assertTrue(ChatIsolation.foreignEvent("sid-a", "sid-b"))
        assertFalse(ChatIsolation.foreignEvent("sid-a", "sid-a"))
    }

    @Test
    fun `yeni sohbette oturum yokken etiketli her olay yabancidir`() {
        // Kullanıcı yeni sohbete geçtiyse (henüz session açılmadı) eski
        // sohbetin akmaya devam eden yanıtı O sohbete düşmemeli.
        assertTrue(ChatIsolation.foreignEvent("sid-a", null))
    }

    @Test
    fun `etiketsiz olay yabanci sayilmaz - eski gateway ve global yayin`() {
        assertFalse(ChatIsolation.foreignEvent(null, "sid-a"))
        assertFalse(ChatIsolation.foreignEvent(null, null))
        assertFalse(ChatIsolation.foreignEvent("", "sid-a"))
    }
}
