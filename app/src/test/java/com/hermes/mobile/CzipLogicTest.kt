package com.hermes.mobile

import com.hermes.mobile.data.czipMergeOnerisi
import com.hermes.mobile.data.czipReadOzet
import com.hermes.mobile.data.czipRefIn
import com.hermes.mobile.data.isCzipReadRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CzipLogic saf kuralları (V4 port) — K12b okuma referansı + K29-a
 * birleştirme önerisi (aynı czip paketini kullanan ≥2 oturum).
 */
class CzipLogicTest {

    @Test
    fun `ciplak czip okuma referanslari taninir`() {
        assertTrue(isCzipReadRef("/czip_ex wek85p"))
        assertTrue(isCzipReadRef("czip promt oku cstfmy"))
        assertTrue(isCzipReadRef("  /czip_ex wek85p  "))
    }

    @Test
    fun `referansla karisan normal mesajlar okuma sayilmaz`() {
        assertFalse(isCzipReadRef("şu paketi özetle\n/czip_ex wek85p"))
        assertFalse(isCzipReadRef("czip promt oku"))
        assertFalse(isCzipReadRef("/czip_ex"))
        assertFalse(isCzipReadRef("czip promt oku cstfmy ve sonra planı uygula"))
    }

    @Test
    fun `okuma ozeti bosluklari duzlestirir ve kirpar`() {
        assertEquals("", czipReadOzet("   \n "))
        assertEquals("merhaba dünya", czipReadOzet("merhaba\n\n  dünya"))
        val uzun = czipReadOzet("x".repeat(500))
        assertEquals(400, uzun.length)
        assertTrue(uzun.endsWith("…"))
    }

    @Test
    fun `czip referansi onizlemeden cikarilir`() {
        assertEquals("wek85p", czipRefIn("/czip_ex wek85p ile sürdü"))
        assertEquals("cstfmy", czipRefIn("czip promt oku cstfmy"))
        assertNull(czipRefIn("normal sohbet"))
        assertNull(czipRefIn(""))
    }

    @Test
    fun `ayni paketi kullanan iki oturum birlestirme onerisi uretir`() {
        val oneri = czipMergeOnerisi(
            listOf(
                "db-1" to "paketle sürdü /czip_ex wek85p",
                "db-2" to "czip promt oku wek85p",
            ),
        )
        assertTrue(oneri != null)
        assertEquals("wek85p", oneri!!.ref)
        assertEquals(listOf("db-1", "db-2"), oneri.dbIds)
    }

    @Test
    fun `tek oturum ve referanssiz satirlar oneri uretmez`() {
        assertNull(czipMergeOnerisi(listOf("db-1" to "/czip_ex wek85p")))
        assertNull(czipMergeOnerisi(listOf("db-1" to "sohbet", "db-2" to "başka sohbet")))
        assertNull(czipMergeOnerisi(emptyList()))
    }

    @Test
    fun `dbId siz satirlar ve en kalabalik grup kurali`() {
        val oneri = czipMergeOnerisi(
            listOf(
                "" to "/czip_ex a1",
                "db-1" to "/czip_ex b2",
                "db-2" to "/czip_ex b2",
                "db-3" to "/czip_ex b2",
            ),
        )
        assertEquals("b2", oneri!!.ref)
        assertEquals(listOf("db-1", "db-2", "db-3"), oneri.dbIds)
    }
}
