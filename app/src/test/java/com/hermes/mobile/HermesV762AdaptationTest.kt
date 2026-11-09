package com.hermes.mobile

import android.app.Application
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.hermes.mobile.car.LegacyMolaActivity
import com.hermes.mobile.car.ParkedMolaActivity
import com.hermes.mobile.data.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HermesV762AdaptationTest {

    @Test
    fun `mola tam ekran surus kipi varsayilan acik ve duraklatma varsayilan kapalidir`() {
        val defaults = AppSettings()
        assertTrue(defaults.molaFullScreenDriving)
        assertFalse(defaults.mirrorPauseWhileDriving)
    }

    @Test
    fun `tam ekran surus kipinde youtube telefon ve oyun gizlenir kapatilinca geri gelir`() {
        assertTrue(ParkedMolaActivity.visibleToolbarButtons(true).isEmpty())
        assertEquals(listOf("Mola", "YouTube", "Yansıt", "Oyun"), ParkedMolaActivity.visibleToolbarButtons(false))

        val fullScreenMenu = LegacyMolaActivity.visibleMenuActions(true)
        assertTrue(fullScreenMenu.none { it.contains("YouTube") || it.contains("ekranını paylaş") })
        val classicMenu = LegacyMolaActivity.visibleMenuActions(false)
        assertTrue(classicMenu.any { it.contains("YouTube") })
        assertTrue(classicMenu.any { it.contains("ekranını paylaş") })
    }

    @Test
    fun `parked mola aktivitesi varsayilan tam ekran acilir ve youtube arac cubugu gizlenir`() {
        val app = RuntimeEnvironment.getApplication()
        val controller = Robolectric.buildActivity(ParkedMolaActivity::class.java, Intent(app, ParkedMolaActivity::class.java)).create().start().resume()
        try {
            val activity = controller.get()
            val texts = collectVisibleTexts(activity.window.decorView)
            assertTrue(texts.any { it.contains("HERMES · MOLA TAM EKRAN") })
            assertTrue(texts.none { it == "YouTube" || it == "Oyun" })
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `gorev hatti tek satir formatlama glm53 modelini ds41 yapar ve canli sure hesaplar`() {
        assertEquals("ds4.1", compactSubagentModel("node-local-models/GLM-5.3-Flash-EXL3"))
        assertEquals("ds4.1", compactSubagentModel("openrouter/deepseek-v4.1"))
        val dirty = listOf("Özet: et: ⚠ Primary auth failed — yedeğe geçiliyor: node-local-models / GLM-5.3-Flash-EXL3", "HAZIR").joinToString(10.toChar().toString())
        assertEquals("HAZIR", cleanSubagentSummary(dirty))

        val payload = Json.parseToJsonElement(
            """{"subagent_id":"1108-4ec0ca","role":"cd-denetmen","status":"running","model":"GLM-5.3-Flash-EXL3","goal":"Denetim","summary":"Özet: et: HAZIR","started_at":1000.0,"tool_count":4,"output_tokens":128}"""
        ).jsonObject
        val list = mergeSubagentActivity(emptyList(), "subagent.start", payload)
        val agent = list.single()
        assertEquals("cd-denetmen", agent.role)
        assertEquals(42L, effectiveDurationSeconds(agent, nowEpochSec = 1042.0))
        val line = formatSubagentSingleLine(agent, nowEpochSec = 1042.0)
        assertTrue(line.contains("#1108-4ec0ca (cd-denetmen)"))
        assertTrue(line.contains("[ds4.1]"))
        assertTrue(line.contains("Denetim — HAZIR"))
        assertTrue(line.contains("42sn · 4 araç · 128 tok"))
    }

    @Test
    fun `czip promt 6 bolumlu gorev sozlesmesi ve dual tier esikleri calisir`() {
        val contract = CzipAutoLogic.normalizePromptToContract("Mobil uygulamada mola tam ekran olsun")
        assertTrue(contract.contains("## 1. AMAÇ"))
        assertTrue(contract.contains("## 2. KAPSAM VE DOSYALAR"))
        assertTrue(contract.contains("## 3. KISITLAR VE KURALLAR"))
        assertTrue(contract.contains("## 4. ADIMLAR"))
        assertTrue(contract.contains("## 5. BEKLENEN ÇIKTI"))
        assertTrue(contract.contains("## 6. DOĞRULAMA"))
        assertEquals(60 to 75, CzipAutoLogic.parseDualTierThresholds("kademeler : %60 kontrol noktasi, %75 devir"))
    }

    @Test
    fun `makro izleyici boot tamamlandiginda etkinse kendiliginden baslar`() {
        val app = RuntimeEnvironment.getApplication()
        val store = MacroStore(app)
        store.enabled = false
        MacroBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadowOf(app).nextStartedService)

        store.enabled = true
        MacroBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        val started = shadowOf(app).nextStartedService
        assertNotNull(started)
        assertEquals(MacroMonitorService::class.java.name, started.component?.className)
    }

    private fun collectVisibleTexts(view: View): List<String> {
        if (view.visibility != View.VISIBLE) return emptyList()
        val out = mutableListOf<String>()
        if (view is TextView && view.text.isNotBlank()) {
            out += view.text.toString()
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                out += collectVisibleTexts(view.getChildAt(i))
            }
        }
        return out
    }
}
