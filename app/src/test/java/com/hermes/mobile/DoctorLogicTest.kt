package com.hermes.mobile

import com.hermes.mobile.data.DoctorLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K2 Doctor — saf tarama mantığı.
 *
 * Kilitlenen davranış: her alan ya ✓ ya ✗ ya "?" döner; eksik her satırda
 * kullanıcının atabileceği TEK aksiyon vardır; Android Auto geliştirici modu
 * asla "çalışıyor" demez (okunamaz — DOĞRULANAMADI).
 */
class DoctorLogicTest {

    private fun allOk() = DoctorLogic.checks(
        a11yEnabled = true,
        a11yRunning = true,
        notificationAccess = true,
        shizuku = DoctorLogic.ShizukuStatus.READY,
        bridge = DoctorLogic.BridgeStatus.CONNECTED,
        serverOk = true,
        serverDetail = "ulaşılabilir · 42 ms",
        androidAutoInstalled = true,
    )

    @Test
    fun `her zaman tam alan listesi ve tutarlı anahtarlar`() {
        val checks = allOk()
        assertEquals(DoctorLogic.KEYS, checks.map { it.key })
    }

    @Test
    fun `hepsi hazirken bile android auto bilinmiyor`() {
        val checks = allOk()
        // Gelistirici modu uygulamadan okunamaz → satir asla OK olamaz.
        assertTrue(checks.all { it.key == "android_auto" || it.status == DoctorLogic.Status.OK })
        assertEquals(
            DoctorLogic.Status.UNKNOWN,
            checks.first { it.key == "android_auto" }.status,
        )
    }

    @Test
    fun `ozet sayilari dogru`() {
        assertEquals("Doctor: 5/6 tamam · 1 bilinmiyor", DoctorLogic.summary(allOk()))
    }

    @Test
    fun `erişilebilirlik izni yoksa FAIL + ayar acma aksiyonu`() {
        val checks = DoctorLogic.checks(
            a11yEnabled = false,
            a11yRunning = false,
            notificationAccess = true,
            shizuku = DoctorLogic.ShizukuStatus.READY,
            bridge = DoctorLogic.BridgeStatus.CONNECTED,
            serverOk = true,
            serverDetail = "ok",
            androidAutoInstalled = false,
        )
        val a11y = checks.first { it.key == "a11y" }
        assertEquals(DoctorLogic.Status.FAIL, a11y.status)
        assertEquals(DoctorLogic.Fix.OPEN_ACCESSIBILITY, a11y.fix)
    }

    @Test
    fun `izin var ama servis kapaliysa FAIL`() {
        val checks = DoctorLogic.checks(
            a11yEnabled = true,
            a11yRunning = false,
            notificationAccess = true,
            shizuku = DoctorLogic.ShizukuStatus.READY,
            bridge = DoctorLogic.BridgeStatus.CONNECTED,
            serverOk = true,
            serverDetail = "ok",
            androidAutoInstalled = true,
        )
        assertEquals(DoctorLogic.Status.FAIL, checks.first { it.key == "a11y" }.status)
    }

    @Test
    fun `shizuku izin istege bagli - her durumda tek aksiyon`() {
        listOf(
            DoctorLogic.ShizukuStatus.NEEDS_PERMISSION,
            DoctorLogic.ShizukuStatus.UNAVAILABLE,
        ).forEach { st ->
            val checks = DoctorLogic.checks(
                a11yEnabled = true, a11yRunning = true, notificationAccess = true,
                shizuku = st,
                bridge = DoctorLogic.BridgeStatus.CONNECTED,
                serverOk = true, serverDetail = "ok", androidAutoInstalled = true,
            )
            val row = checks.first { it.key == "shizuku" }
            assertEquals(DoctorLogic.Status.FAIL, row.status)
            assertEquals(DoctorLogic.Fix.OPEN_SHIZUKU, row.fix)
        }
    }

    @Test
    fun `kopru devralinmissa FAIL + yeniden baglan aksiyonu`() {
        val checks = DoctorLogic.checks(
            a11yEnabled = true, a11yRunning = true, notificationAccess = true,
            shizuku = DoctorLogic.ShizukuStatus.READY,
            bridge = DoctorLogic.BridgeStatus.TAKEN_OVER,
            serverOk = true, serverDetail = "ok", androidAutoInstalled = true,
        )
        val row = checks.first { it.key == "bridge" }
        assertEquals(DoctorLogic.Status.FAIL, row.status)
        assertEquals(DoctorLogic.Fix.RECONNECT_BRIDGE, row.fix)
    }

    @Test
    fun `sunucu yoklanmadiysa UNKNOWN + sunucu aksiyonu`() {
        val checks = DoctorLogic.checks(
            a11yEnabled = true, a11yRunning = true, notificationAccess = true,
            shizuku = DoctorLogic.ShizukuStatus.READY,
            bridge = DoctorLogic.BridgeStatus.CONNECTED,
            serverOk = null, serverDetail = "",
            androidAutoInstalled = true,
        )
        val row = checks.first { it.key == "server" }
        assertEquals(DoctorLogic.Status.UNKNOWN, row.status)
        assertEquals(DoctorLogic.Fix.OPEN_SERVERS, row.fix)
    }

    @Test
    fun `sunucu ulasilamazsa FAIL`() {
        val checks = DoctorLogic.checks(
            a11yEnabled = true, a11yRunning = true, notificationAccess = true,
            shizuku = DoctorLogic.ShizukuStatus.READY,
            bridge = DoctorLogic.BridgeStatus.CONNECTED,
            serverOk = false, serverDetail = "Ulaşılamıyor",
            androidAutoInstalled = true,
        )
        assertEquals(DoctorLogic.Status.FAIL, checks.first { it.key == "server" }.status)
    }

    @Test
    fun `eksik her satirda aksiyon var, tam satirda yok`() {
        val checks = DoctorLogic.checks(
            a11yEnabled = false, a11yRunning = false, notificationAccess = false,
            shizuku = DoctorLogic.ShizukuStatus.UNAVAILABLE,
            bridge = DoctorLogic.BridgeStatus.OFF,
            serverOk = false, serverDetail = "yok",
            androidAutoInstalled = false,
        )
        checks.forEach {
            if (it.status == DoctorLogic.Status.OK) {
                assertEquals(DoctorLogic.Fix.NONE, it.fix)
            } else {
                assertTrue("eksik aksiyon: ${it.key}", it.fix != DoctorLogic.Fix.NONE)
            }
        }
        assertEquals("Doctor: 0/6 tamam · 6 eksik", DoctorLogic.summary(checks))
    }

    @Test
    fun `diag satirlari ozet + sorunlu alanlari icerir`() {
        val checks = DoctorLogic.checks(
            a11yEnabled = false, a11yRunning = false, notificationAccess = true,
            shizuku = DoctorLogic.ShizukuStatus.READY,
            bridge = DoctorLogic.BridgeStatus.CONNECTED,
            serverOk = true, serverDetail = "ok",
            androidAutoInstalled = true,
        )
        val lines = DoctorLogic.diagLines(checks)
        assertTrue(lines[0].startsWith("Doctor:"))
        assertTrue(lines.any { it.startsWith("eksik a11y:") })
        assertTrue(lines.any { it.startsWith("bilinmiyor android_auto:") })
    }

    @Test
    fun `isaretler`() {
        assertEquals("✓", DoctorLogic.mark(DoctorLogic.Status.OK))
        assertEquals("✗", DoctorLogic.mark(DoctorLogic.Status.FAIL))
        assertEquals("?", DoctorLogic.mark(DoctorLogic.Status.UNKNOWN))
    }
}