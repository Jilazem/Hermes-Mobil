package com.hermes.mobile.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.hermes.mobile.assistant.EmaVoice
import kotlinx.coroutines.*

/** A foreground reply service owns playback; loss of focus cancels and drains it. */
class NotificationEmaSpeech(private val context: Context, private val speech: EmaSpeech) {
    suspend fun speak(text: String) = coroutineScope {
        val audio = context.getSystemService(AudioManager::class.java)
        val done = CompletableDeferred<Unit>()
        val ready = CompletableDeferred<Unit>()
        val voice = EmaVoice(context, speech)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener({ focus ->
                if (focus == AudioManager.AUDIOFOCUS_GAIN) ready.complete(Unit)
                if (focus == AudioManager.AUDIOFOCUS_LOSS || focus == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    voice.stop()
                    val lost = IllegalStateException("Ses odağı bırakıldı")
                    ready.completeExceptionally(lost); done.completeExceptionally(lost)
                }
            }, Handler(Looper.getMainLooper())).build()
        try {
            when (audio.requestAudioFocus(request)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> ready.complete(Unit)
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> Unit
                else -> error("Ses odağı alınamadı")
            }
            withTimeout(10_000) { ready.await() }
            voice.onDone = { done.complete(Unit) }
            voice.onError = { done.completeExceptionally(IllegalStateException(it)) }
            // Each synthesis request stays under EMA's per-request text limit.
            text.chunked(800).forEach(voice::say)
            voice.finish()
            withTimeout(180_000) { done.await() }
        } finally { voice.release(); audio.abandonAudioFocusRequest(request) }
    }
}
