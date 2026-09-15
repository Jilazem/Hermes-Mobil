package com.hermes.mobile.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Kayıt — sözleşmeye uygun **ogg/opus 16 kHz mono**.
 *
 * Sözleşme: `POST /transcribe` multipart `audio`, tercihen ogg/opus, <=60 sn.
 * Süre tavanını [VoiceMessageController] uygular (kayıt durum makinesi);
 * burada yalnız kodlayıcı kurulumu var.
 *
 * OPUS kodlayıcısı API 29'da geldi. Altında (26-28) ogg/opus yazılamıyor;
 * **sessizce hiç kaydetmemek yerine** m4a/AAC'ye düşülür — ses hattındaki
 * whisper zaten AAC'yi çözer, kullanıcı "kayıt olmadı" ile karşılaşmaz.
 * Düşüş [RecorderPort.Recorded.mime] ile taşınır ve tanı kaydına yazılır.
 */
class AndroidVoiceRecorder(private val context: Context) : RecorderPort {

    private var recorder: MediaRecorder? = null
    private var target: File? = null

    override fun start(target: File): Boolean {
        this.target = target
        val opus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val file = if (opus) target else File(target.parentFile, target.nameWithoutExtension + ".m4a")
        return runCatching {
            val r = newRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            r.setOutputFormat(
                if (opus) MediaRecorder.OutputFormat.OGG else MediaRecorder.OutputFormat.MPEG_4,
            )
            r.setAudioEncoder(
                if (opus) MediaRecorder.AudioEncoder.OPUS else MediaRecorder.AudioEncoder.AAC,
            )
            // 16 kHz mono — konuşma için yeterli, yükleme küçük kalır.
            r.setAudioSamplingRate(16_000)
            r.setAudioChannels(1)
            r.setAudioEncodingBitRate(if (opus) 24_000 else 32_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            this.target = file
            true
        }.getOrElse { e ->
            DiagLog.e("voice", "kayit baslatilamadi", e)
            runCatching { recorder?.release() }
            recorder = null
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
        else MediaRecorder()

    override fun stop(): RecorderPort.Recorded? {
        val r = recorder ?: return null
        recorder = null
        val file = target
        return runCatching {
            // stop() istisna atarsa dosya bozuktur: yüklemeye gönderme.
            r.stop()
            r.release()
            val f = file?.takeIf { it.exists() && it.length() > 0 } ?: return null
            RecorderPort.Recorded(f, mimeOf(f))
        }.getOrElse { e ->
            DiagLog.e("voice", "kayit durdurulamadi", e)
            runCatching { r.release() }
            null
        }
    }

    override fun cancel() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        runCatching { r.release() }
    }

    private fun mimeOf(file: File): String =
        when (file.extension.lowercase()) {
            "ogg", "opus" -> "audio/ogg"
            "m4a", "mp4", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
}

/**
 * Çalma — [MediaPlayer] ile indirilen sesi çalar/durdurur.
 *
 * Ses `USAGE_MEDIA` + `CONTENT_TYPE_SPEECH`: konuşma için tonlanır, müzik
 * akışına düşer (kulaklıkta/telefon hoparlöründe aynı davranış).
 */
class AndroidVoicePlayer : PlayerPort {

    private var player: MediaPlayer? = null

    override fun play(file: File, onDone: () -> Unit, onError: (String) -> Unit): Boolean {
        stop()
        return runCatching {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener {
                stop()
                onDone()
            }
            p.setOnErrorListener { _, what, extra ->
                DiagLog.w("voice", "mediaplayer hata what=$what extra=$extra")
                stop()
                onError("Ses çalınamadı (kod $what/$extra)")
                true
            }
            p.prepare()
            p.start()
            player = p
            true
        }.getOrElse { e ->
            DiagLog.e("voice", "ses calinamadi", e)
            stop()
            onError(e.message ?: "Ses çalınamadı")
            false
        }
    }

    override fun stop() {
        val p = player ?: return
        player = null
        runCatching { if (p.isPlaying) p.stop() }
        runCatching { p.release() }
    }

    /** Çalınıyor mu — UI düğmesi geri bildirimi. */
    val playing: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    /** Ses uzunluğu (ms) — tanı satırı. */
    val durationMs: Int get() = runCatching { player?.duration ?: 0 }.getOrDefault(0)
}
