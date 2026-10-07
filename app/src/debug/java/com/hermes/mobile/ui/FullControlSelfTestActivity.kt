package com.hermes.mobile.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hermes.mobile.data.FullControl
import com.hermes.mobile.data.FullControlTools
import com.hermes.mobile.data.HermesAccessibilityService
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/** Tests only this disposable form; does not read contacts, messages or other apps. */
class FullControlSelfTestActivity : ComponentActivity() {
    private lateinit var editor: EditText
    private lateinit var result: TextView
    private var clicks = 0
    private val lines = mutableListOf<String>()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        editor = EditText(this).apply { hint = "EMA deneme alanı" }
        result = TextView(this).apply { textSize = 15f }
        val button = Button(this).apply {
            text = "EMA Test Düğmesi"
            setOnClickListener { ++clicks }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 72, 32, 32)
            addView(button); addView(editor); addView(result)
        })
        lifecycleScope.launch {
            try {
                delay(1500)
                val tools = FullControlTools(this@FullControlSelfTestActivity)
                if (intent.getBooleanExtra("denied", false)) {
                    check(HermesAccessibilityService.instanceOrNull() == null)
                    val outcome = withContext(Dispatchers.IO) { tools.execute(FullControl.TAP,
                        buildJsonObject { put("text", "EMA Test Düğmesi") }) }
                    check(!outcome.ok && clicks == 0)
                    step("PASS unavailable accessibility returns failure without action")
                } else {
                    check(HermesAccessibilityService.instanceOrNull() != null) { "Erişilebilirlik izni gerekli" }
                    suspend fun execute(name: String, args: kotlinx.serialization.json.JsonObject) {
                        val outcome = withContext(Dispatchers.IO) { tools.execute(name, args) }
                        check(outcome.ok) { "$name failed" }
                    }
                    execute(FullControl.DUMP, buildJsonObject {})
                    step("PASS screen.dump")
                    execute(FullControl.TAP, buildJsonObject { put("text", "EMA Test Düğmesi") })
                    check(clicks == 1)
                    step("PASS tap confirmed by button click")
                    execute(FullControl.TYPE, buildJsonObject { put("text", "Hermes EMA denemesi") })
                    check(editor.text.toString() == "Hermes EMA denemesi")
                    step("PASS type confirmed in actual text field")
                    execute(FullControl.TAP, buildJsonObject { put("text", "EMA Test Düğmesi") })
                    check(clicks == 2)
                    step("PASS repeated tap")
                    check(FullControl.guardReason(true, true, true, FullControl.TYPE) == FullControl.WHY_READ_ONLY)
                    check(FullControl.guardReason(false, false, true, FullControl.TAP) == FullControl.WHY_AGENT_OFF)
                    step("PASS read-only and agent-off guards")
                }
                step("RESULT PASS")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { step("RESULT FAIL ${e.message}") }
        }
    }

    private fun step(line: String) {
        lines += line
        result.text = lines.joinToString("\n")
        File(filesDir, "full-control-selftest.txt").writeText(lines.joinToString("\n"))
    }
}
