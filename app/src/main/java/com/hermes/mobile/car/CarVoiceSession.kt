package com.hermes.mobile.car

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.car.app.CarContext
import androidx.car.app.media.CarAudioRecord
import androidx.core.content.ContextCompat
import com.hermes.mobile.assistant.JarvisBrain
import com.hermes.mobile.assistant.JarvisLogic
import com.hermes.mobile.assistant.JarvisVoice
import com.hermes.mobile.data.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/** Car microphone -> existing STT -> shared Hermes session -> EMA car audio. */
class CarVoiceSession(private val context: CarContext, private val changed: (String, Boolean) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val brain = JarvisBrain(context, scope)
    private var turn: Job? = null
    @Volatile private var record: CarAudioRecord? = null
    private var voice: JarvisVoice? = null
    private var focus: AudioFocusRequest? = null
    private val audio = context.getSystemService(AudioManager::class.java)
    private var generation = 0
    private var released = false
    var busy: Boolean = false
        private set

    fun toggle() {
        if (released) return
        if (busy) { cancel(); return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val token = generation
            context.requestPermissions(listOf(Manifest.permission.RECORD_AUDIO)) { granted, _ ->
                if (released || generation != token) return@requestPermissions
                if (Manifest.permission.RECORD_AUDIO in granted) begin()
                else changed("Mikrofon izni gerekli", false)
            }
            return
        }
        begin()
    }

    private fun begin() {
        if (released) return
        val config = EmaConfig.from(context)
        if (config == null) { changed("Telefonda Ayarlar → Ses bölümünden EMA bağlantısını tamamla", false); return }
        val profile = ServerProfileStore(context).active()
        if (profile == null || profile.token.isBlank()) { changed("Hermes sunucu profili ve anahtarı gerekli", false); return }
        cancel()
        val token = generation
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs).setOnAudioFocusChangeListener { state ->
                if (generation == token && (state == AudioManager.AUDIOFOCUS_LOSS || state == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)) cancel()
            }.build()
        if (audio?.requestAudioFocus(req) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            changed("Araç ses odağı alınamadı", false); return
        }
        focus = req
        busy = true
        PhoneBridgeService.start(context)
        DrivingModeService.start(context, driving = true)
        changed("Dinliyorum — tekrar dokunarak iptal et", true)
        turn = scope.launch {
            try {
                val carRecord = CarAudioRecord.create(context)
                record = carRecord
                carRecord.startRecording()
                // Stop a host that blocks instead of returning silence frames.
                val timer = launch { delay(20_000); runCatching { carRecord.stopRecording() } }
                val captured = try { withContext(Dispatchers.IO) {
                    val data = ByteArray(CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE)
                    val logic = CarSpeechLogic(CarAudioRecord.AUDIO_CONTENT_SAMPLING_RATE)
                    val out = ByteArrayOutputStream()
                    while (isActive) {
                        val n = carRecord.read(data, 0, data.size)
                        if (n < 0) break
                        if (n == 0) { delay(10); continue }
                        out.write(data, 0, n)
                        if (logic.push(data, n)) break
                    }
                    check(logic.heardSpeech) { "Konuşma duyulmadı — tekrar Konuş'a dokun" }
                    CarSpeechLogic.wav(out.toByteArray(), CarAudioRecord.AUDIO_CONTENT_SAMPLING_RATE)
                } } finally {
                    timer.cancel()
                    runCatching { carRecord.stopRecording() }
                    if (record === carRecord) record = null
                }
                changed("Söylediğini metne çeviriyorum…", true)
                val s = SettingsStore(context).settings.value
                val question = VoiceApiClient(VoiceApiEndpoints.candidates(profile, s.voiceUrl, s.voiceLastOk),
                    profile.token, profile.id).transcribe(captured, "car.wav", "audio/wav").trim()
                check(question.isNotBlank()) { "Konuşma anlaşılamadı" }
                if (JarvisLoopLogic.isStopCommand(question)) { cancel(); return@launch }
                changed("Düşünüyorum…", true)
                val speaker = com.hermes.mobile.assistant.EmaVoice(context, config.client())
                voice = speaker
                speaker.onError = { message -> scope.launch { if (generation == token) { cancel(); changed(message, false) } } }
                speaker.onDone = { scope.launch {
                    if (generation == token) finishTurn()
                } }
                val splitter = JarvisLogic.SentenceSplitter()
                brain.ask(question, object : JarvisBrain.Sink {
                    override fun onDelta(text: String) {
                        if (generation != token) return
                        splitter.push(text).forEach { speaker.say(it) }
                        changed("EMA konuşuyor…", true)
                    }
                    override fun onTool(name: String) { if (generation == token) changed("İşlem yapıyorum…", true) }
                    override fun onNeedsApproval(text: String) {
                        if (generation == token) speaker.say("Bu işlem için onayın gerekiyor. Sohbet ekranından devam edebilirsin.")
                    }
                }, forceHermes = true)
                splitter.flush()?.let { speaker.say(it) }
                speaker.finish()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == token) { cancel(); changed(e.message ?: "Araç sesli oturumu tamamlanamadı", false) }
            }
        }
    }

    fun cancel() {
        ++generation
        turn?.cancel(); turn = null
        brain.interrupt()
        runCatching { record?.stopRecording() }; record = null
        voice?.release(); voice = null
        focus?.let { audio?.abandonAudioFocusRequest(it) }; focus = null
        if (busy) DrivingModeService.stop(context)
        busy = false
        changed("Hazır — Konuş'a dokun", false)
    }

    private fun finishTurn() {
        cancel()
        if (!released && SettingsStore(context).settings.value.assistantContinuous) {
            val token = generation
            busy = true
            changed("Yanıt tamamlandı — yeniden dinleyeceğim; İptal ile durdur", true)
            turn = scope.launch { delay(600); if (generation == token && !released) begin() }
        } else changed("Yanıt tamamlandı — Konuş'a dokunabilirsin", false)
    }

    fun release() { released = true; cancel(); brain.release(); scope.cancel() }
}
