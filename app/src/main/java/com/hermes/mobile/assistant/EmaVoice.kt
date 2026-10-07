package com.hermes.mobile.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.hermes.mobile.data.EmaTtsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** EMA sentence queue with immediate PCM playback and cancellation. */
class EmaVoice(context: Context, private val client: EmaTtsClient) : FileQueueVoice(context) {
    override val allowAndroidFallback = false
    @Volatile private var track: AudioTrack? = null
    private val outputLock = Any()

    override suspend fun synth(text: String, target: File): File =
        throw UnsupportedOperationException("EMA streams directly")

    override suspend fun speakSentence(text: String, target: File) = withContext(Dispatchers.IO) {
        var output: AudioTrack? = null
        var frames = 0L
        var sampleRate = 48000
        val job = currentCoroutineContext()[Job]!!
        try {
            client.stream(text) { pcm, rate ->
                job.ensureActive()
                sampleRate = rate
                val player = synchronized(outputLock) {
                    job.ensureActive()
                    output ?: AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(maxOf(AudioTrack.getMinBufferSize(rate,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2, 8192))
                    .setTransferMode(AudioTrack.MODE_STREAM).build().also {
                        output = it; track = it; it.play()
                    }
                }
                var offset = 0
                var progressedAt = android.os.SystemClock.elapsedRealtime()
                while (offset < pcm.size) {
                    job.ensureActive()
                    val written = player.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                    if (written < 0) throw IllegalStateException("EMA sesi çalınamadı")
                    if (written == 0) {
                        check(android.os.SystemClock.elapsedRealtime() - progressedAt < 10_000) { "Ses çıkışı yanıt vermiyor" }
                        Thread.sleep(5)
                        continue
                    }
                    progressedAt = android.os.SystemClock.elapsedRealtime()
                    offset += written
                    frames += written / 2
                }
                onLevel?.invoke(AndroidTtsVoice.pcm16Level(pcm))
            }
            // Network completion precedes speaker completion; drain the AudioTrack.
            val player = output ?: throw IllegalStateException("EMA ses yanıtı boş")
            val deadline = android.os.SystemClock.elapsedRealtime() + frames * 1000 / sampleRate + 10_000
            while ((player.playbackHeadPosition.toLong() and 0xffffffffL) < frames) {
                currentCoroutineContext().ensureActive()
                check(android.os.SystemClock.elapsedRealtime() < deadline) { "Ses çıkışı tamamlanamadı" }
                delay(10)
            }
        } finally {
            synchronized(outputLock) {
                if (track === output) track = null
                runCatching { output?.stop() }
                runCatching { output?.release() }
            }
            onLevel?.invoke(0f)
        }
    }

    override fun stopOutput() = synchronized(outputLock) {
        val player = track
        track = null
        runCatching { player?.pause(); player?.flush(); player?.stop() }
        Unit
    }
}
