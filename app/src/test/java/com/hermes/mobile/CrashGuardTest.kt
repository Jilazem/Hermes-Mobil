package com.hermes.mobile

import com.hermes.mobile.data.CrashGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-2 K3(a) / FR-003 — çökme handler'ının metin üretimi SAF olarak test
 * edilir: "coktu" yerine gorunur hata. Handler'ın kendisi (Thread/StateFlow)
 * JVM'de ağır; burada yalnız reportText + stackTrace + recoverFromDiagLog
 * satır ayrıştırması doğrulanır.
 */
class CrashGuardTest {

    @Test
    fun reportTextThreadOturumEkranIcerir() {
        val err = IllegalStateException("bozuk cerceve")
        val text = CrashGuard.reportText("OkHttp", err, "sid-123", "Sohbet")
        assertTrue(text, text.contains("thread=OkHttp"))
        assertTrue(text, text.contains("session=sid-123"))
        assertTrue(text, text.contains("screen=Sohbet"))
        assertTrue(text, text.contains("java.lang.IllegalStateException"))
        assertTrue(text, text.contains("bozuk cerceve"))
    }

    @Test
    fun reportTextOturumYoksaTire() {
        val text = CrashGuard.reportText("main", RuntimeException("x"), null, null)
        assertTrue(text, text.contains("session=-"))
        assertTrue(text, text.contains("screen=?"))
    }

    /** Yığın en fazla maxFrames çerçeve taşır ve `at ` satırlarıdır. */
    @Test
    fun stackTraceSinirliCerCeve() {
        val deep = try {
            // derin yığın üret
            fun recurse(n: Int): Int = if (n == 0) 1 / 0 else 1 + recurse(n - 1)
            recurse(40)
            RuntimeException("ulasilmaz")
        } catch (e: ArithmeticException) {
            e
        }
        val trace = CrashGuard.stackTrace(deep, maxFrames = 5)
        val lines = trace.lines().filter { it.isNotBlank() }
        assertTrue("en az 1 cerceve", lines.isNotEmpty())
        assertTrue("en fazla 5", lines.size <= 5)
        assertTrue("at ile baslar", lines.first().startsWith("at "))
    }

    /** diag.log CRASH satırı biçimini banner özetine çevirir. */
    @Test
    fun recoverSatirAyristirma() {
        // Gerçek DiagLog satır biçimi: "MM-dd HH:mm:ss.SSS C [crash] thread=… :: …"
        val dir = java.nio.file.Files.createTempDirectory("diag").toFile()
        val log = java.io.File(dir, "diag.log")
        log.writeText(
            "09-14 20:25:01.000 I [app] started\n" +
                "09-14 20:25:05.123 E [ws] failed http=-\n" +
                "09-14 20:25:06.000 C [crash] thread=main session=sid-1 screen=Sohbet :: java.lang.IllegalStateException: kotu\n" +
                "09-14 20:25:06.100 I [app] started\n",
        )
        CrashGuard.lastCrash = null
        CrashGuard.recoverFromDiagLog(dir)
        val recovered = CrashGuard.lastCrash
        assertTrue("cokme bulundu", recovered != null)
        assertTrue(recovered ?: "", recovered!!.contains("IllegalStateException"))
        assertTrue(recovered ?: "", recovered.contains("Onceki açilis"))
        CrashGuard.lastCrash = null
        dir.deleteRecursively()
    }

    /** CRASH yoksa lastCrash null kalır. */
    @Test
    fun recoverCrashYoksaNull() {
        val dir = java.nio.file.Files.createTempDirectory("diag2").toFile()
        java.io.File(dir, "diag.log").writeText("09-14 20:25:01.000 I [app] started\n")
        CrashGuard.lastCrash = null
        CrashGuard.recoverFromDiagLog(dir)
        assertEquals(null, CrashGuard.lastCrash)
        dir.deleteRecursively()
    }
}
