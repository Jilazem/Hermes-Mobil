package com.hermes.mobile.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.hermes.mobile.data.EmaAudioLogic
import com.hermes.mobile.data.EmaSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * EMA cümle kuyruğu — PCM'i indikçe çalar.
 *
 * Onarım (iş #1021 K2): EMA hattı hata verdiğinde ses TAMAMEN susuyordu
 * (`allowAndroidFallback = false`). Artık son çare telefonun TTS motoru:
 * hata yine görünür ([speechError]) ama kullanıcı sessizlikte kalmıyor.
 * Ayrıca 16-bit PCM parçaları çift bayt sınırında tutulur ve örnek hızı
 * değişirse çıkış hattı yeniden kurulur (EMA 48000 ↔ yedek Piper 22050).
 */
class EmaVoice(context: Context, private val client: EmaSpeech) : FileQueueVoice(context) {
    override val allowAndroidFallback = true
    override fun speechError() = if (client is com.hermes.mobile.data.EmaOfflineSpeech)
        "EMA sesi üretilemedi. Ses ayarlarından modeli denetle veya yeniden indir." else super.speechError()
    @Volatile private var track: AudioTrack? = null
    private val outputLock = Any()

    override suspend fun synth(text: String, target: File): File =
        throw UnsupportedOperationException("EMA streams directly")

    override suspend fun speakSentence(text: String, target: File) = withContext(Dispatchers.IO) {
        var output: AudioTrack? = null
        var trackRate = 0
        var frames = 0L
        // 16-bit PCM: tek bayt kalan parça bir sonraki parçaya taşınır.
        var carry: ByteArray? = null
        val job = currentCoroutineContext()[Job]!!
        try {
            client.stream(text) { pcm, rate ->
                job.ensureActive()
                val buf = carry?.let { it + pcm } ?: pcm
                val usable = buf.size - (buf.size % 2)
                carry = if (usable < buf.size) buf.copyOfRange(usable, buf.size) else null
                if (usable == 0) return@stream

                val player = synchronized(outputLock) {
                    job.ensureActive()
                    val existing = output
                    if (existing != null && trackRate != rate) {
                        // Motor değişti (EMA ↔ yedek ses): eski hattı kapat,
                        // yeni örnek hızında temiz hat kur; sayaç sıfırlanır.
                        runCatching { existing.stop() }
                        runCatching { existing.release() }
                        output = null
                        track = null
                        frames = 0L
                    }
                    output ?: AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(maxOf(AudioTrack.getMinBufferSize(rate,
                            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2, 8192))
                        .setTransferMode(AudioTrack.MODE_STREAM).build().also {
                            output = it; track = it; trackRate = rate; it.play()
                        }
                }
                var offset = 0
                var progressedAt = SystemClock.elapsedRealtime()
                while (offset < usable) {
                    job.ensureActive()
                    val written = player.write(buf, offset, usable - offset, AudioTrack.WRITE_NON_BLOCKING)
                    if (written < 0) throw IllegalStateException("EMA sesi çalınamadı (kod $written)")
                    if (written == 0) {
                        check(SystemClock.elapsedRealtime() - progressedAt < 10_000) { "Ses çıkışı yanıt vermiyor" }
                        Thread.sleep(5)
                        continue
                    }
                    progressedAt = SystemClock.elapsedRealtime()
                    offset += written
                    frames += written / 2
                }
                onLevel?.invoke(AndroidTtsVoice.pcm16Level(buf.copyOf(usable)))
            }
            // Ağ tarafı bittiğinde hoparlör kuyruğu hâlâ doludur; boşalt.
            val player = output ?: throw IllegalStateException("EMA ses yanıtı boş")
            val deadline = SystemClock.elapsedRealtime() + EmaAudioLogic.drainDeadlineMs(frames, trackRate)
            while ((player.playbackHeadPosition.toLong() and 0xffffffffL) < frames) {
                currentCoroutineContext().ensureActive()
                check(SystemClock.elapsedRealtime() < deadline) { "Ses çıkışı tamamlanamadı" }
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
