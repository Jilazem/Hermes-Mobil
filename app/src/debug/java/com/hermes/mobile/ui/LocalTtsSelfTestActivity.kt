package com.hermes.mobile.ui

import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hermes.mobile.data.LocalModelClient
import com.hermes.mobile.data.LocalModelLogic
import com.hermes.mobile.data.LocalTtsEngine
import com.hermes.mobile.data.LocalTtsLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Tur-21 kanıt yüzeyi (yalnız DEBUG; launcher'da yok — adb ile explicit).
 *
 * Emülatörde gerçek motorları koşturur:
 *  1. Yerel TTS: dosyalar varsa motoru yükler, iki Türkçe cümleyi
 *     `filesDir/local-tts-kanit/cumle-1.wav` / `cumle-2.wav` üretir
 *     (sonucu ekrana yazar; dosyalar adb run-as ile çekilir).
 *  2. Yerel LLM health: `GET /v1/models` — model kilidi kontrolü
 *     (`--es llm "http://10.0.2.2:8888"` ile üs adresi verilir).
 *  3. Yerel LLM tek tur: `--es llmprompt "..."` verilirse chat dener.
 *
 * Adresler intent extra; token gerekmez (yerel uç açık, LAN).
 */
class LocalTtsSelfTestActivity : ComponentActivity() {

    private val lines = mutableListOf<String>()
    private lateinit var view: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(28, 48, 28, 28)
        }
        setContentView(ScrollView(this).apply {
            addView(LinearLayout(this@LocalTtsSelfTestActivity).apply { addView(view) })
        })

        val llmUrl = intent.getStringExtra("llm") ?: ""
        val llmPrompt = intent.getStringExtra("llmprompt") ?: ""

        step("tur21 yerel ses/llm oz-testi")
        lifecycleScope.launch {
            runTtsProof()
            if (llmUrl.isNotBlank()) runLlmHealth(llmUrl)
            if (llmUrl.isNotBlank() && llmPrompt.isNotBlank()) runLlmChat(llmUrl, llmPrompt)
            step("BITTI")
        }
    }

    private suspend fun runTtsProof() {
        val dir = LocalTtsLogic.modelDir(filesDir)
        step("model dir: ${dir.absolutePath}")
        if (!LocalTtsLogic.filesPresent(dir)) {
            step("MODEL YOK — once Ayarlar'dan indir (veya adb push)")
            return
        }
        val bad = LocalTtsLogic.firstShaMismatch(dir) { f ->
            runCatching {
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(f.readBytes()).joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }
        step(if (bad == null) "sha256: HEPSI ✓ (${LocalTtsLogic.FILES.size} dosya)" else "sha256 UYUSMADI: $bad")
        val engine = LocalTtsEngine(this)
        val t0 = System.currentTimeMillis()
        val loaded = engine.ensureLoaded()
        step("motor yukleme=${System.currentTimeMillis() - t0}ms ok=$loaded")
        if (!loaded) return
        val out = File(filesDir.parentFile, "local-tts-kanit")
        out.mkdirs()
        val sentences = listOf(
            "Merhaba, ben Hermes yerel kadın sesiyim.",
            "Bu ses tamamen telefondan üretiliyor, internet gerekmiyor.",
        )
        sentences.forEachIndexed { i, s ->
            val target = File(out, "cumle-${i + 1}.wav")
            val ts = System.currentTimeMillis()
            runCatching { engine.synthesize(s, target) }
                .onSuccess { step("sentez ${i + 1}: ${it.name} ${it.length()} bayt ${System.currentTimeMillis() - ts}ms") }
                .onFailure { step("sentez ${i + 1} HATA: ${it.message}") }
        }
        step("kanit klasoru: ${out.absolutePath}")
        engine.release()
    }

    private suspend fun runLlmHealth(url: String) {
        val client = LocalModelClient(url)
        runCatching { client.healthModel() }
            .onSuccess { step("llm health: ${if (it != null) "OK model=$it" else "MODEL KILIDI: beklenen eşleşmedi"}") }
            .onFailure { step("llm health HATA: ${it.message}") }
    }

    private suspend fun runLlmChat(url: String, prompt: String) {
        val client = LocalModelClient(url)
        runCatching { client.chat(prompt, LocalModelLogic.DEFAULT_MODEL) }
            .onSuccess { step("llm chat yaniti (${it.length} kr): ${it.take(140)}") }
            .onFailure { step("llm chat HATA: ${it.message}") }
    }

    private fun step(text: String) {
        lines += text
        runOnUiThread { view.text = lines.joinToString("\n") }
    }
}
