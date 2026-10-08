package com.hermes.mobile.ui

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.hermes.mobile.ChatItem
import com.hermes.mobile.ChatViewModel
import com.hermes.mobile.assistant.JarvisBrain
import com.hermes.mobile.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File

/** Synthetic data only: real server profile, source context, assistant recreation, and microphone send path. */
class VoiceSpecialistProofActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val ui = TextView(this).apply { setPadding(24, 48, 24, 24) }; setContentView(ui)
        val proof = File(filesDir, "voice-specialist-proof.txt"); proof.writeText("")
        fun step(text: String) { ui.append("$text\n"); proof.appendText("$text\n") }
        lifecycleScope.launch {
            var gw: GatewayWsClient? = null
            var brain: JarvisBrain? = null
            val settings = SettingsStore(this@VoiceSpecialistProofActivity)
            val original = settings.settings.value
            try {
                val server = checkNotNull(ServerProfileStore(this@VoiceSpecialistProofActivity).active())
                step("SERVER_HOST=" + java.net.URI(server.normalizedUrl).host + ";REMOTE_HOST=" +
                    server.normalizedRemote.takeIf { it.isNotBlank() }?.let { java.net.URI(it).host })
                val expert = HermesClient(server).profiles().single { it.name == VoiceSpecialistLogic.PROFILE }
                check(expert.model == "gemma-4-31b" && expert.provider == "evren")
                step("PASS live Sesli Asistan profile: Evren gemma-4-31b")
                val g = GatewayWsClient(server).also { gw = it }; g.connect()
                withTimeout(20_000) { g.connection.first { it is ConnectionState.Open } }
                step("PASS gateway connected")
                val diagnostic = getSharedPreferences("voice-specialist-proof", MODE_PRIVATE)
                val previous = diagnostic.getString(server.id, null)
                val parent = if (previous == null) g.createSession(VoiceSpecialistLogic.PROFILE) else g.resumeSession(previous, VoiceSpecialistLogic.PROFILE)
                step("PASS synthetic parent created or resumed")
                val parentStored = g.storedSessionId(parent)
                diagnostic.edit().putString(server.id, parentStored).commit()
                suspend fun prompt(sid: String, text: String): String = coroutineScope {
                    val result = CompletableDeferred<String>(); val reply = NotificationReply(sid)
                    val collector = launch(start = CoroutineStart.UNDISPATCHED) { g.events.collect {
                        if (reply.accept(it)) result.complete(checkNotNull(reply.result))
                    } }
                    try { g.submitPrompt(sid, text); withTimeout(90_000) { result.await() } }
                    finally { collector.cancelAndJoin() }
                }
                val seedReply = prompt(parent, "Deneme kelimem menekşe. Araç kullanma, yalnız menekşe yaz.")
                step("SEED_MATCH=" + seedReply.contains("menekşe", true))
                check(seedReply.contains("menekşe", true))
                settings.update { it.copy(lastSession = "${server.id}|$parentStored") }
                brain = JarvisBrain(this@VoiceSpecialistProofActivity, lifecycleScope)
                var start = System.nanoTime(); var first: Double? = null; var unexpected = false
                val sink = object : JarvisBrain.Sink {
                    override fun onDelta(text: String) { if (text.isNotBlank() && first == null) first = (System.nanoTime() - start)/1e9 }
                    override fun onTool(name: String) { unexpected = true }
                    override fun onNeedsApproval(text: String) { unexpected = true }
                }
                val answer = brain.ask("Önceki sohbetimdeki deneme kelimem neydi? Araç kullanma, yalnız kelimeyi söyle.", sink, true)
                check(answer.contains("menekşe", true) && !unexpected)
                val voiceStored = checkNotNull(brain.lastSessionId)
                check(voiceStored != parentStored)
                step("PASS source context carried into separate voice session; first text seconds=$first")
                brain.release(); brain = JarvisBrain(this@VoiceSpecialistProofActivity, lifecycleScope)
                first = null; start = System.nanoTime()
                check(brain.ask("7 kere 8 kaç? Araç kullanma, yalnız sonucu söyle.", sink, true).contains("56"))
                check(!unexpected && brain.lastSessionId == voiceStored)
                step("PASS recreated assistant resumes voice session; first text seconds=$first")
                vm.bind(server)
                withTimeout(20_000) { vm.state.first { it.connection is ConnectionState.Open } }
                vm.continueSession(parent, parentStored, "Sentetik test")
                withTimeout(35_000) { vm.state.first { !it.historyLoading } }
                val mathQuestion = "8 kere 9 kaç? Bu yeni bağlantı testi ${System.nanoTime()}; araç kullanma, yalnız sonucu söyle."
                vm.sendVoice(mathQuestion)
                withTimeout(90_000) { vm.state.first { s -> s.storedSessionId == voiceStored && !s.agentBusy &&
                    s.items.drop(s.items.indexOfLast { it is ChatItem.User && it.text == mathQuestion }.takeIf { it >= 0 } ?: s.items.size)
                        .any { it is ChatItem.Assistant && !it.streaming && it.text.contains("72") } } }
                check(vm.sessionProfile.value == VoiceSpecialistLogic.PROFILE)
                check(settings.settings.value.modelsByServer == original.modelsByServer && settings.settings.value.lastModel == original.lastModel)
                step("PASS chat microphone routed to same specialist; written model preferences preserved")
                if (intent.getBooleanExtra("handoff", false)) {
                    val before = vm.state.value.items.map { it.key }.toSet()
                    vm.sendVoice("Bu YENİ görev aktarımı testi ${System.nanoTime()}; önceki test sonucu kullanılamaz. Yeni delegate_task çağrısı zorunlu. delegate_task aracını tasks=[{goal:'Önce terminal aracıyla sleep 8 komutunu çalıştır, sonra 2+2 sonucunu tek cümleyle bildir.',context:'Yalnız sentetik test; dosya veya ayar değiştirme.'}] ile bir kez çağır. Sonra aktarımın gerçekten başladığını kısa cümleyle bildir.")
                    withTimeout(75_000) { vm.state.first { st -> st.items.any {
                        it is ChatItem.Tool && it.key !in before && it.name == "delegate_task" } } }
                    step("PASS real delegate_task invoked from phone voice send path")
                    withTimeout(75_000) { vm.state.first { st -> st.items.any {
                        it is ChatItem.Assistant && it.key !in before && !it.streaming && Regex("""(?i)(sonuç|sonucu|eder|eşittir).{0,15}\b4\b|işlem tamamlandı.{0,60}\b4\b|^4[.!]?$""").containsMatchIn(it.text) } } }
                    step("PASS background completion returned into the visible phone chat")
                }
                var phoneTool = ""
                val deviceSink = object : JarvisBrain.Sink {
                    override fun onDelta(text: String) = Unit
                    override fun onTool(name: String) { phoneTool = name }
                    override fun onNeedsApproval(text: String) = Unit
                }
                val battery = brain.ask("Pil ne kadar?", deviceSink, forceHermes = true)
                check(phoneTool == "phone_status" && battery.contains("pil", true))
                step("PASS direct car brain phone-status route executes on this device")
                step("RESULT PASS")
            } catch (e: TimeoutCancellationException) { step("RESULT FAIL timeout") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { step("RESULT FAIL ${e.javaClass.simpleName}: ${DiagLog.redact(e.message.orEmpty()).take(240)}") }
            finally {
                brain?.release(); gw?.close()
                settings.update { it.copy(lastSession = original.lastSession) }
            }
        }
    }
}
