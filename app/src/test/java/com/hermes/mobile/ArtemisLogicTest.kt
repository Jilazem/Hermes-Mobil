package com.hermes.mobile

import com.hermes.mobile.data.ArtemisLogic
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Google Artemis sözleşmesi — resmî artemis-client (Python) kurallarıyla aynı. */
class ArtemisLogicTest {

    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `komut onekleri`() {
        assertEquals("pil yüzdesini söyle", ArtemisLogic.parseCommand("/telefon pil yüzdesini söyle"))
        assertEquals("open maps", ArtemisLogic.parseCommand("  /Phone open maps "))
        assertEquals("x", ArtemisLogic.parseCommand("/artemis x"))
        assertNull(ArtemisLogic.parseCommand("/telefon"))
        assertNull(ArtemisLogic.parseCommand("/telefonla ara"))
        assertNull(ArtemisLogic.parseCommand("telefon aç"))
    }

    @Test
    fun `cihaz listesi iki bicim`() {
        val a = ArtemisLogic.parseDevices(Json.parseToJsonElement("""[{"serial":"192.168.1.50:37123","state":"device","model":"Pixel"}]"""))
        assertEquals("192.168.1.50:37123", a.single().serial)
        val b = ArtemisLogic.parseDevices(Json.parseToJsonElement("""{"devices":[{"device_serial":"emulator-5554","status":"BUSY"},{"state":"device"}]}"""))
        assertEquals(1, b.size)
        assertTrue(b.single().busy)
    }

    @Test
    fun `cihaz secimi tahmin yurutmez`() {
        val d = listOf(
            ArtemisLogic.Device("192.168.1.50:37123", "device", null, false),
            ArtemisLogic.Device("emulator-5554", "device", null, false),
        )
        assertEquals("elle", ArtemisLogic.pickDevice(d, "elle", "192.168.1.50"))
        assertEquals("192.168.1.50:37123", ArtemisLogic.pickDevice(d, "", "192.168.1.50"))
        assertNull(ArtemisLogic.pickDevice(d, "", "10.0.0.9"))
        assertEquals("emulator-5554", ArtemisLogic.pickDevice(d.drop(1), "", null))
    }

    @Test
    fun `gonderim yaniti`() {
        assertEquals("t1", ArtemisLogic.parseSubmit(obj("""{"status":"queued","tasks":[{"session_id":"t1"}]}"""), "x").getOrThrow())
        assertTrue(ArtemisLogic.parseSubmit(obj("""{"status":"rejected","error":"cihaz yok"}"""), "x").isFailure)
        assertEquals("cihaz yok", ArtemisLogic.parseSubmit(obj("""{"status":"rejected","error":"cihaz yok"}"""), "x").exceptionOrNull()?.message)
    }

    @Test
    fun `gorev durumu ve cikti sirasi`() {
        val t = ArtemisLogic.parseTask(obj("""{"status":"COMPLETED","result":"Pil %81","turns":4}"""), "id")
        assertTrue(t.done); assertTrue(t.succeeded)
        assertEquals("Pil %81", t.output)
        assertTrue(ArtemisLogic.resultText(t, en = false).contains("4 adım"))
        val r = ArtemisLogic.parseTask(obj("""{"status":"running","current_turn":2}"""), "id")
        assertFalse(r.done)
        val f = ArtemisLogic.parseTask(obj("""{"status":"failed","error_message":"ekran kilitli"}"""), "id")
        assertEquals("📱 Artemis başarısız: ekran kilitli", ArtemisLogic.resultText(f, en = false))
    }
}
