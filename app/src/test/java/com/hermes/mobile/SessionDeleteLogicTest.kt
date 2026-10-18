package com.hermes.mobile

import com.hermes.mobile.data.SessionDeleteLogic
import com.hermes.mobile.data.SessionFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K10c — sohbet silme bayrak kuralları.
 *
 * Sözleşme: onaylı silmede sunucu silme BAŞARILIysa id'ye ait tüm yerel
 * bayraklar düşer (oturum sunucuda yok, gizleme bayrağı çöp olur); sunucu
 * silme BAŞARISIZSA eski davranış — yalnız yerel gizleme (oturum sunucuda
 * yaşamaya devam eder, başka bayrak temizlenmez).
 */
class SessionDeleteLogicTest {

    private val flags = SessionFlags(
        pinned = setOf("a", "b"),
        archived = setOf("b", "c"),
        hidden = setOf("d"),
        renames = mapOf("b" to "Kullanıcı başlığı", "a" to "Eski ad"),
    )

    @Test
    fun `sunucu silmesi id'ye ait tum bayraklari dusurur`() {
        val yeni = SessionDeleteLogic.flagsAfterServerDelete(flags, "b")

        assertFalse("b" in yeni.hidden)
        assertFalse("b" in yeni.pinned)
        assertFalse("b" in yeni.archived)
        assertFalse(yeni.renames.containsKey("b"))
    }

    @Test
    fun `sunucu silmesi diger id'lere dokunmaz`() {
        val yeni = SessionDeleteLogic.flagsAfterServerDelete(flags, "b")

        assertTrue("a" in yeni.pinned)
        assertTrue("c" in yeni.archived)
        assertTrue("d" in yeni.hidden)
        assertEquals("Eski ad", yeni.renames["a"])
    }

    @Test
    fun `sunucu silinemediginde yalniz yerel gizleme yapilir`() {
        val yeni = SessionDeleteLogic.flagsAfterLocalHide(flags, "b")

        assertTrue("b" in yeni.hidden)
        assertFalse("b" in yeni.pinned)
        assertFalse("b" in yeni.archived)
        // Rename korunur: oturum sunucuda yaşamaya devam ediyor, adı da öyle.
        assertEquals("Kullanıcı başlığı", yeni.renames["b"])
    }

    @Test
    fun `yerel gizleme diger id'lere dokunmaz`() {
        val yeni = SessionDeleteLogic.flagsAfterLocalHide(flags, "b")

        assertTrue("a" in yeni.pinned)
        assertTrue("c" in yeni.archived)
        assertTrue("d" in yeni.hidden)
    }

    @Test
    fun `ardisik sunucu silmeleri birikir`() {
        val bir = SessionDeleteLogic.flagsAfterServerDelete(flags, "b")
        val iki = SessionDeleteLogic.flagsAfterServerDelete(bir, "a")

        assertFalse("b" in iki.pinned)
        assertFalse("a" in iki.pinned)
        assertFalse(iki.renames.containsKey("a"))
    }
}
