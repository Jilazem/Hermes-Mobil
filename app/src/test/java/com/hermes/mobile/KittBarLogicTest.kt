package com.hermes.mobile

import com.hermes.mobile.data.JarvisLoopLogic
import com.hermes.mobile.ui.kittBodyVisible
import com.hermes.mobile.ui.kittStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** tur25.1 KITT düzeni: dinlemede gövde gizli, şerit + durum hep görünür. */
class KittBarLogicTest {

    @Test
    fun `dinlemede ve kapaliyda govde gizli - sadece KITT seridi`() {
        assertEquals(false, kittBodyVisible(JarvisLoopLogic.Phase.Listening))
        assertEquals(false, kittBodyVisible(JarvisLoopLogic.Phase.Off))
    }

    @Test
    fun `konusma ve islem fazlarinda govde gorunur`() {
        assertEquals(true, kittBodyVisible(JarvisLoopLogic.Phase.Speaking))
        assertEquals(true, kittBodyVisible(JarvisLoopLogic.Phase.Stt))
        assertEquals(true, kittBodyVisible(JarvisLoopLogic.Phase.WaitReply))
    }

    @Test
    fun `durum metni iki dilli ve total`() {
        val t: (String, String) -> String = { tr, en -> "$tr|$en" }
        assertEquals("Dinliyor…|Listening…", kittStatus(JarvisLoopLogic.Phase.Listening, t))
        assertEquals("Konuşuyor…|Speaking…", kittStatus(JarvisLoopLogic.Phase.Speaking, t))
    }
}