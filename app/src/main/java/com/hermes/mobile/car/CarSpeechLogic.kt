package com.hermes.mobile.car

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Bounded car-microphone capture with silence detection, independent of Android. */
class CarSpeechLogic(private val rate: Int = 16000) {
    private var totalSamples = 0L
    private var quietSamples = 0L
    private var voicedSamples = 0L
    val heardSpeech: Boolean get() = voicedSamples >= rate / 5

    fun push(pcm: ByteArray, size: Int): Boolean {
        val n = size / 2
        if (n == 0) return false
        var sum = 0.0
        for (i in 0 until n) {
            val v = ((pcm[2*i+1].toInt() shl 8) or (pcm[2*i].toInt() and 255)).toShort().toInt()
            sum += v.toDouble() * v
        }
        totalSamples += n
        if (sqrt(sum / n) > 400) { voicedSamples += n; quietSamples = 0 }
        else quietSamples += n
        return totalSamples >= rate * 15L ||
            (!heardSpeech && totalSamples >= rate * 6L) ||
            (heardSpeech && quietSamples >= rate * 3L / 2)
    }

    companion object {
        fun wav(pcm: ByteArray, rate: Int = 16000): ByteArray {
            require(pcm.size % 2 == 0)
            return ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVEfmt ".toByteArray())
                putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2)
                putShort(2); putShort(16); put("data".toByteArray()); putInt(pcm.size); put(pcm)
            }.array()
        }
    }
}
