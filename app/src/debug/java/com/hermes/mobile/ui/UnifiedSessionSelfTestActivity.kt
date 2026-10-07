package com.hermes.mobile.ui

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hermes.mobile.assistant.JarvisBrain
import com.hermes.mobile.data.EmaConfig
import com.hermes.mobile.data.ServerProfileStore
import com.hermes.mobile.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** Real gateway, two turns and process-level assistant recreation using shared session. */
class UnifiedSessionSelfTestActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val view = TextView(this).apply { setPadding(32, 72, 32, 32); textSize = 16f }
        setContentView(view)
        val lines = mutableListOf<String>()
        fun step(line: String) {
            lines += line; view.text = lines.joinToString("\n")
            File(filesDir, "unified-session-selftest.txt").writeText(lines.joinToString("\n"))
        }
        lifecycleScope.launch {
            var brain: JarvisBrain? = null
            try {
                check(EmaConfig.from(this@UnifiedSessionSelfTestActivity) != null)
                check(ServerProfileStore(this@UnifiedSessionSelfTestActivity).active() != null)
                var unexpectedTool = false
                val sink = object : JarvisBrain.Sink {
                    override fun onDelta(text: String) = Unit
                    override fun onTool(name: String) { unexpectedTool = true }
                    override fun onNeedsApproval(text: String) { unexpectedTool = true }
                }
                brain = JarvisBrain(this@UnifiedSessionSelfTestActivity, lifecycleScope)
                val first = brain.ask("Bu bir teknik bağlantı testi. Deneme kelimesi menekşe. Araç kullanma. Yalnız menekşe yaz.", sink, forceHermes = true)
                check(!unexpectedTool) { "Beklenmeyen araç çağrısı" }
                check(first.contains("menekşe", true)) { "İlk yanıt doğrulanamadı" }
                step("PASS real Hermes gateway first reply")
                val session = SettingsStore(this@UnifiedSessionSelfTestActivity).settings.value.lastSession
                check(session.substringAfter('|').isNotBlank())
                brain.release()
                brain = JarvisBrain(this@UnifiedSessionSelfTestActivity, lifecycleScope)
                val second = brain.ask("Bir önceki mesajda verdiğim deneme kelimesi neydi? Araç kullanma. Yalnız kelimeyi yaz.", sink, forceHermes = true)
                check(!unexpectedTool) { "Beklenmeyen araç çağrısı" }
                check(second.contains("menekşe", true)) { "Geçmiş bağlamı doğrulanamadı" }
                check(SettingsStore(this@UnifiedSessionSelfTestActivity).settings.value.lastSession == session)
                step("PASS recreated assistant resumed same chat session and context")
                step("RESULT PASS")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { step("RESULT FAIL ${e.javaClass.simpleName}: ${e.message}") }
            finally { brain?.release() }
        }
    }
}
