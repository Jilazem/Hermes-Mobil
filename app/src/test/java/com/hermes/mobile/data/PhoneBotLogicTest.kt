package com.hermes.mobile.data

import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telefon botu kartı metin mantığı: ekran önizlemesi, başlık çözümü,
 * salt-okunur anahtarının güvenli yönde serbest / tehlikeli yönde onaylı
 * olması ve durum satırı.
 */
class PhoneBotLogicTest {

    private val dump = """
        {"package":"com.whatsapp","activity":"ChatActivity","tree":{
          "text":"Sohbetler",
          "children":[
            {"text":"Ali","children":[{"text":"Tamam"},{"text":"Tamam"}]},
            {"desc":"Geri","children":[]},
            {"text":"Bu satır çok uzun ${"x".repeat(PhoneBotLogic.MAX_LINE_CHARS)}"}
          ]
        }}
    """.trimIndent()

    @Test
    fun onizlemeTekrarlariTekGosterir(): Unit {
        val lines = PhoneBotLogic.previewLines(dump)
        assertEquals(1, lines.count { it == "Tamam" })
        assertTrue("Sohbetler" in lines)
        assertTrue("Ali" in lines)
    }

    @Test
    fun onizlemeLimitVeUzunSatir(): Unit {
        val lines = PhoneBotLogic.previewLines(dump, limit = 3)
        assertEquals(3, lines.size)
        assertFalse(lines.any { it.length > PhoneBotLogic.MAX_LINE_CHARS })
    }

    @Test
    fun bosVeyaBozukDumpBosListe(): Unit {
        assertTrue(PhoneBotLogic.previewLines(null).isEmpty())
        assertTrue(PhoneBotLogic.previewLines("").isEmpty())
        assertTrue(PhoneBotLogic.previewLines("{bozuk").isEmpty())
        assertTrue(PhoneBotLogic.previewLines("""{"tree":{}}""").isEmpty())
        assertTrue(PhoneBotLogic.previewLines(dump, limit = 0).isEmpty())
    }

    @Test
    fun ekranBasligiPaketVeAktivite(): Unit {
        assertEquals("com.whatsapp · ChatActivity", PhoneBotLogic.screenHeader(dump))
        assertEquals("com.ornek", PhoneBotLogic.screenHeader("""{"package":"com.ornek","tree":{}}"""))
        assertNull(PhoneBotLogic.screenHeader("""{"activity":"X"}"""))
        assertNull(PhoneBotLogic.screenHeader(null))
    }

    @Test
    fun yetenekEtiketiSaltOkunurSayar(): Unit {
        assertTrue(PhoneBotLogic.capabilityLabel(mayUsePhone = false, readOnly = false, fullControl = true).startsWith("Kapalı"))
        assertTrue(PhoneBotLogic.capabilityLabel(true, readOnly = true, fullControl = true).startsWith("Salt-okunur"))
        assertTrue(PhoneBotLogic.capabilityLabel(true, readOnly = false, fullControl = true).startsWith("Tam kontrol"))
        assertTrue(PhoneBotLogic.capabilityLabel(true, readOnly = false, fullControl = false).startsWith("Okuma + yazma"))
    }

    @Test
    fun saltOkunuraDonusOnayIstemezYazmaIster(): Unit {
        assertFalse(PhoneBotLogic.readOnlyToggleNeedsConfirm(targetReadOnly = true))
        assertTrue(PhoneBotLogic.readOnlyToggleNeedsConfirm(targetReadOnly = false))
    }

    @Test
    fun saltOkunurModdaYazmaReddedilirOkumaSerbest(): Unit {
        val writeTool = FullControl.WRITE_TOOLS.first()
        val readTool = FullControl.READ_TOOLS.first()
        assertEquals(FullControl.WHY_READ_ONLY, FullControl.guardReason(true, readOnly = true, fullControl = true, tool = writeTool))
        assertNull(FullControl.guardReason(true, readOnly = true, fullControl = true, tool = readTool))
        assertNull(FullControl.guardReason(true, readOnly = false, fullControl = true, tool = writeTool))
    }

    @Test
    fun durumSatiriUcDurum(): Unit {
        assertEquals("Bağlı · ekran okunuyor", PhoneBotLogic.statusLine(enabled = true, running = true))
        assertEquals("İzin var, servis henüz bağlanmadı", PhoneBotLogic.statusLine(enabled = true, running = false))
        assertEquals("Erişilebilirlik izni yok", PhoneBotLogic.statusLine(enabled = false, running = false))
    }

    // ---- Otomatik yazma / ACİL DURDUR / denetim günlüğü (patron kararı 21.10) ----

    @Test
    fun otomatikYazmaUcKosulBirlikteCalisir(): Unit {
        assertTrue(PhoneBotLogic.autoWriteActive(autoWrite = true, emergencyStop = false, accessibilityEnabled = true))
        assertFalse(PhoneBotLogic.autoWriteActive(true, emergencyStop = true, accessibilityEnabled = true))
        assertFalse(PhoneBotLogic.autoWriteActive(true, emergencyStop = false, accessibilityEnabled = false))
        assertFalse(PhoneBotLogic.autoWriteActive(autoWrite = false, emergencyStop = false, accessibilityEnabled = true))
    }

    @Test
    fun otomatikYazmaEngelSebebi(): Unit {
        assertEquals(PhoneBotLogic.WHY_EMERGENCY_STOP, PhoneBotLogic.autoWriteBlockReason(accessibilityEnabled = true, emergencyStop = true))
        assertEquals(PhoneBotLogic.WHY_NO_ACCESSIBILITY, PhoneBotLogic.autoWriteBlockReason(accessibilityEnabled = false, emergencyStop = false))
        assertNull(PhoneBotLogic.autoWriteBlockReason(accessibilityEnabled = true, emergencyStop = false))
    }

    @Test
    fun acilDurdurSatiri(): Unit {
        assertTrue(PhoneBotLogic.emergencyStopLine(true).startsWith("ACİL DURDUR AÇIK"))
        assertTrue(PhoneBotLogic.emergencyStopLine(false).startsWith("Yazma açık"))
    }

    @Test
    fun yazmaAraclariYazmaSayilirOkumaSayilmaz(): Unit {
        assertTrue(PhoneBotLogic.isWriteTool(FullControl.TAP))
        assertTrue(PhoneBotLogic.isWriteTool("phone_open_app"))
        assertFalse(PhoneBotLogic.isWriteTool(FullControl.DUMP))
        assertFalse(PhoneBotLogic.isWriteTool("phone_status"))
        assertTrue(PhoneBotLogic.isWriteTool("phone_task"))
        assertTrue(PhoneBotLogic.isWriteTool("phone_reply"))
    }

    private fun enabledPhone() = AppSettings(
        agentMayUsePhone = true, agentReadOnly = false, fullControl = true,
        agentAutoWrite = true, agentMayReply = true, artemisUrl = "http://127.0.0.1:8000",
    )

    @Test fun emergencyCoversArtemisAndReplyButReadToolsRemainAvailable() {
        val stopped = enabledPhone().copy(agentEmergencyStop = true)
        listOf("phone_task", "phone_reply", FullControl.TAP, "phone_open_app").forEach {
            assertEquals(it, PhoneBotLogic.WHY_EMERGENCY_STOP, PhoneBotLogic.commandBlockReason(stopped, it))
            assertFalse(it in PhoneBotLogic.advertisedTools(stopped))
        }
        listOf("phone_status", FullControl.DUMP).forEach {
            assertNull(PhoneBotLogic.commandBlockReason(stopped, it))
            assertTrue(it in PhoneBotLogic.advertisedTools(stopped))
        }
    }

    @Test fun autoWriteOffBlocksWritesWithoutRevokingReadAccess() {
        val settings = enabledPhone().copy(agentAutoWrite = false)
        assertEquals("otomatik yazma kapalı", PhoneBotLogic.commandBlockReason(settings, "phone_task"))
        assertEquals("otomatik yazma kapalı", PhoneBotLogic.commandBlockReason(settings, "phone_reply"))
        assertEquals("otomatik yazma kapalı", PhoneBotLogic.commandBlockReason(settings, FullControl.TYPE))
        assertTrue("phone_status" in PhoneBotLogic.advertisedTools(settings))
        assertTrue(FullControl.DUMP in PhoneBotLogic.advertisedTools(settings))
        assertFalse("phone_reply" in PhoneBotLogic.advertisedTools(settings))
    }

    @Test fun existingPhonePermissionsRemainIndependent() {
        val on = enabledPhone()
        assertEquals(FullControl.WHY_AGENT_OFF,
            PhoneBotLogic.commandBlockReason(on.copy(agentMayUsePhone = false), "phone_status"))
        assertEquals(FullControl.WHY_READ_ONLY,
            PhoneBotLogic.commandBlockReason(on.copy(agentReadOnly = true), "phone_task"))
        assertEquals(FullControl.WHY_FULL_CONTROL_OFF,
            PhoneBotLogic.commandBlockReason(on.copy(fullControl = false), "phone_task"))
        assertEquals("sohbete yanıt izni kapalı",
            PhoneBotLogic.commandBlockReason(on.copy(agentMayReply = false), "phone_reply"))
        assertNull(PhoneBotLogic.commandBlockReason(on.copy(agentMayReply = false), "phone_task"))
        assertEquals("tool not enabled", PhoneBotLogic.commandBlockReason(on, "phone_shell"))
    }

    @Test fun artemisStopRequiresActualAcknowledgement() = kotlinx.coroutines.runBlocking {
        var settings = enabledPhone()
        var calls = 0
        val stop: suspend (String) -> Unit = { id -> assertEquals("owned-task", id); calls++ }
        assertEquals(PhoneBotLogic.ArtemisEmergencyStop.NotRequested,
            PhoneBotLogic.stopArtemisForEmergency({ settings }, "owned-task", stop))
        assertEquals(0, calls)
        settings = settings.copy(agentEmergencyStop = true)
        assertEquals(PhoneBotLogic.ArtemisEmergencyStop.Confirmed,
            PhoneBotLogic.stopArtemisForEmergency({ settings }, "owned-task", stop))
        assertEquals(1, calls)
        assertEquals(PhoneBotLogic.ArtemisEmergencyStop.Unconfirmed,
            PhoneBotLogic.stopArtemisForEmergency({ settings }, "owned-task") {
                throw IllegalStateException("ACK rejected")
            })
    }

    @Test fun commandQueuedBeforeStopIsRejectedByFreshDispatchSnapshot() = kotlinx.coroutines.runBlocking {
        val state = java.util.concurrent.atomic.AtomicReference(enabledPhone())
        val queued = kotlinx.coroutines.CompletableDeferred<Unit>()
        val proceed = kotlinx.coroutines.CompletableDeferred<Unit>()
        var executions = 0
        var rejection: String? = null
        val worker = kotlinx.coroutines.CoroutineScope(coroutineContext).launch {
            assertNull(PhoneBotLogic.commandBlockReason(state.get(), "phone_task"))
            queued.complete(Unit)
            proceed.await()
            rejection = PhoneBotLogic.commandBlockReason(state.get(), "phone_task")
            if (rejection == null) executions++
        }
        queued.await()
        state.set(state.get().copy(agentEmergencyStop = true))
        proceed.complete(Unit)
        worker.join()
        assertEquals(0, executions)
        assertEquals(PhoneBotLogic.WHY_EMERGENCY_STOP, rejection)
    }

    @Test
    fun denetimSatiriBicimVeKirpma(): Unit {
        val ok = PhoneBotLogic.auditLine(0L, "phone_tap", ok = true)
        assertTrue(ok.endsWith("· phone_tap · tamam"))
        val hata = PhoneBotLogic.auditLine(0L, "phone_type", ok = false, detail = "  izin yok  ")
        assertTrue(hata.endsWith("· phone_type · hata · izin yok"))
        val uzun = PhoneBotLogic.auditLine(0L, "phone_type", ok = true, detail = "y".repeat(200))
        assertTrue(uzun.length <= 160)
    }

    @Test
    fun denetimGunluguSonYirmiKayitTutar(): Unit {
        val lines = (1..30).map { "kayit $it" }
        val kept = PhoneBotLogic.trimAudit(lines)
        assertEquals(PhoneBotLogic.MAX_AUDIT, kept.size)
        assertEquals("kayit 11", kept.first())
        assertEquals("kayit 30", kept.last())
        assertTrue(PhoneBotLogic.trimAudit(listOf(" ", ""), max = 5).isEmpty())
        assertTrue(PhoneBotLogic.trimAudit(lines, max = 0).isEmpty())
    }

    @Test
    fun otomatikYazmaAyarlariTutarlı(): Unit {
        // Varsayılan: otomatik yazma açık, acil durdur kapalı.
        assertTrue(AppSettings().agentAutoWrite)
        assertFalse(AppSettings().agentEmergencyStop)

        // Servis yokken açılamaz; mevcut ayar korunur.
        val kapali = AppSettings().copy(agentAutoWrite = false)
        assertFalse(kapali.withAutoWrite(on = true, accessibilityEnabled = false).agentAutoWrite)

        // Servis varsa açılır ve yazma izni birlikte gelir.
        val acik = kapali.withAutoWrite(on = true, accessibilityEnabled = true)
        assertTrue(acik.agentAutoWrite)
        assertFalse(acik.agentReadOnly)
        assertTrue(acik.agentMayUsePhone)

        // ACİL DURDUR ayarı otomatik anahtarını değiştirmez; ayrı kalır.
        val durdur = acik.copy(agentEmergencyStop = true)
        assertTrue(durdur.agentAutoWrite)
        assertTrue(durdur.agentEmergencyStop)
    }
}
