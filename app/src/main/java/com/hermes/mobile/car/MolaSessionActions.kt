package com.hermes.mobile.car

import android.content.Context
import com.hermes.mobile.assistant.JarvisVoice
import com.hermes.mobile.data.GatewayWsClient
import com.hermes.mobile.data.ServerProfileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal object MolaSessionActionLogic {
    fun validateTarget(expectedProfileId: String?, activeProfileId: String?, storedSessionId: String?) {
        require(!expectedProfileId.isNullOrBlank() && expectedProfileId == activeProfileId) {
            "Sunucu profili değişti; paneli yenileyin."
        }
        require(!storedSessionId.isNullOrBlank()) { "Önce bir oturum seçin." }
    }
}

/** Explicit actions only. Reading an answer never opens the microphone or creates a chat. */
internal class MolaSessionActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val changed: (String) -> Unit,
) {
    private var speaker: JarvisVoice? = null
    private var stopJob: Job? = null
    var reading = false
        private set

    fun readLastAnswer(text: String?) {
        if (reading) { silence(); changed("Seslendirme kapatıldı."); return }
        if (text.isNullOrBlank()) { changed("Bu oturumda okunacak asistan yanıtı yok."); return }
        val voice = JarvisVoice.create(context)
        speaker = voice
        reading = true
        voice.onDone = { scope.launch { silence(); changed("Son yanıt okundu.") } }
        voice.onError = { scope.launch { silence(); changed("Yanıt seslendirilemedi; ses ayarlarını kontrol edin.") } }
        changed("Son asistan yanıtı okunuyor; tekrar Oku'ya dokunarak susturabilirsiniz.")
        voice.say(text)
        voice.finish()
    }

    fun stopSelected(expectedProfileId: String?, storedSessionId: String?) {
        if (stopJob?.isActive == true) return
        silence()
        changed("Seçili oturumun durdurma isteği gönderiliyor…")
        stopJob = scope.launch {
            var gateway: GatewayWsClient? = null
            try {
                val profile = ServerProfileStore(context).active()
                MolaSessionActionLogic.validateTarget(expectedProfileId, profile?.id, storedSessionId)
                checkNotNull(profile)
                val client = GatewayWsClient(profile).also { gateway = it }
                client.connect()
                check(client.awaitOpen()) { "Sunucu bağlantısı hazır değil." }
                // A new socket first attaches to this exact stored conversation; no create fallback.
                val runtime = client.attachSession(storedSessionId!!, storedSessionId)
                client.interrupt(runtime)
                changed("Durdurma isteği yanıtlandı. Güncel oturum durumunu yenileyin.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                changed("Durdurma doğrulanamadı. Görev sürüyor olabilir; bağlantıyı ve oturumu kontrol edin.")
            } finally { gateway?.close() }
        }
    }

    fun silence() { speaker?.release(); speaker = null; reading = false }
    fun release() { silence(); stopJob?.cancel(); stopJob = null }
}
