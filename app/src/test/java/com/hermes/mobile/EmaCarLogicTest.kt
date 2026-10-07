package com.hermes.mobile

import com.hermes.mobile.car.CarSpeechLogic
import com.hermes.mobile.data.JarvisLoopLogic
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EmaCarLogicTest {
    private fun block(value: Short = 0): ByteArray = ByteBuffer.allocate(1600)
        .order(ByteOrder.LITTLE_ENDIAN).apply { repeat(800) { putShort(value) } }.array()

    @Test fun `car capture stops after speech and 1500ms silence`() {
        val logic = CarSpeechLogic()
        repeat(4) { assertFalse(logic.push(block(1000), 1600)) }
        assertTrue(logic.heardSpeech)
        repeat(29) { assertFalse(logic.push(block(), 1600)) }
        assertTrue(logic.push(block(), 1600))
    }

    @Test fun `car capture stops after six seconds without speech`() {
        val logic = CarSpeechLogic()
        repeat(119) { assertFalse(logic.push(block(), 1600)) }
        assertTrue(logic.push(block(), 1600))
        assertFalse(logic.heardSpeech)
    }

    @Test fun `continuous speech is bounded to fifteen seconds`() {
        val logic = CarSpeechLogic()
        repeat(299) { assertFalse(logic.push(block(1000), 1600)) }
        assertTrue(logic.push(block(1000), 1600))
    }

    @Test fun `wav has exact mono PCM16 sizes and capture rate`() {
        val pcm = block(1234)
        val wav = CarSpeechLogic.wav(pcm, 16000)
        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav.copyOfRange(0, 4)))
        assertEquals(wav.size - 8, buffer.getInt(4))
        assertEquals(1, buffer.getShort(22).toInt())
        assertEquals(16000, buffer.getInt(24))
        assertEquals(16, buffer.getShort(34).toInt())
        assertEquals(pcm.size, buffer.getInt(40))
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }

    @Test fun `EMA selection stays EMA even when other voices are healthy`() {
        val engines = mapOf("chatterbox" to "acik", "kadin" to "acik", "yerel" to "acik")
        assertNull(JarvisLoopLogic.resolveLoopEngine("ema", engines))
        assertEquals("ema", JarvisLoopLogic.resolveLoopEngine("ema", engines + ("ema" to "hazir")))
        assertNull(JarvisLoopLogic.nextEngineOn("ema", engines))
    }
}
