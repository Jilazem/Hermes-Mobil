package com.hermes.mobile

import com.hermes.mobile.data.ModelSwitchReply
import com.hermes.mobile.data.isCurrentModelChoice
import com.hermes.mobile.data.modelSwitchAccepted
import com.hermes.mobile.data.modelSwitchReply
import com.hermes.mobile.data.modelSwitchSavedGlobally
import com.hermes.mobile.data.reasoningSwitchAccepted
import org.junit.Assert.*
import org.junit.Test

class ModelChoiceContractTest {
    @Test fun `Turkish gateway confirmation is accepted without English or checkmark`() {
        assertTrue(modelSwitchAccepted("Model `deepseek/deepseek-v4.1-flash-1017` olarak değiştirildi\nSağlayıcı: Nous Portal"))
    }

    @Test fun `English and old gateway confirmations remain compatible`() {
        assertTrue(modelSwitchAccepted("Model switched to `gpt-5.5`\nProvider: OpenAI"))
        assertTrue(modelSwitchAccepted("✓ Model switched: glm-5.3"))
    }

    @Test fun `unrelated success and failed or cancelled switches never count as selection`() {
        listOf("✓ uploaded", "✓ operation failed", "Error: provider not found",
            "Hata: model bulunamadı", "Model switch cancelled. Current model unchanged (old).",
            "Model değişikliği iptal edildi", "", "Applies from your next message.")
            .forEach { assertFalse(it, modelSwitchAccepted(it)) }
    }

    @Test fun `deferred model choice is acknowledged but not reported as immediate application`() {
        val out = "Model switched to `new`\nProvider: local\nApplies from your next message."
        assertEquals(ModelSwitchReply.Deferred, modelSwitchReply(out))
        assertEquals(ModelSwitchReply.Applied, modelSwitchReply("Model `new` olarak değiştirildi"))
    }

    @Test fun `a model id containing generic error keywords is still a valid confirmation`() {
        assertTrue(modelSwitchAccepted("Model switched to `credit-balance-error-model`\nProvider: local"))
    }

    @Test fun `session switch success does not imply global config was saved`() {
        val sessionOnly = "Model switched to `new`\nWarning: config.yaml not updated (disk full)\n_(session only — add `--global` to persist)_"
        assertTrue(modelSwitchAccepted(sessionOnly))
        assertFalse(modelSwitchSavedGlobally(sessionOnly))
        assertTrue(modelSwitchSavedGlobally("Model `new` olarak değiştirildi\nconfig.yaml'a kaydedildi (`--global`)"))
    }

    @Test fun `identical model ids on different providers cannot both be selected`() {
        assertTrue(isCurrentModelChoice("nous", "same-model", "nous", "same-model"))
        assertFalse(isCurrentModelChoice("openrouter", "same-model", "nous", "same-model"))
        assertFalse(isCurrentModelChoice("nous", "same-model", null, "same-model"))
    }

    @Test fun `reasoning acknowledgement must confirm the requested effort`() {
        assertTrue(reasoningSwitchAccepted("🧠 ✓ Reasoning effort set to `high` (session only — add `--global` to persist)\n_(takes effect on next message)_", "high"))
        assertTrue(reasoningSwitchAccepted("🧠 ✓ Akıl yürütme gücü `low` olarak ayarlandı (yalnızca bu oturum)", "low"))
        assertFalse(reasoningSwitchAccepted("⚠️ Unknown argument: `high`\n**Valid levels:** none, low, medium, high", "high"))
        assertFalse(reasoningSwitchAccepted("🧠 ✓ Reasoning effort set to `low` (session only)", "high"))
    }

    @Test fun `modern EN and TR reasoning status and acknowledgement parse without stale preferences`() {
        val en = parseReasoningOutput("🧠 **Reasoning Settings**\n\n**Effort:** `medium (default)`\n**Display:** on ✓")
        assertEquals("medium", en.effort)
        assertEquals(true, en.displayOn)
        val tr = parseReasoningOutput("🧠 **Akıl Yürütme Ayarları**\n\n**Güç:** `none (devre dışı)`\n**Görüntüleme:** kapalı")
        assertEquals("none", tr.effort)
        assertEquals(false, tr.displayOn)
        assertEquals("high", parseReasoningOutput("🧠 ✓ Akıl yürütme gücü `high` olarak ayarlandı (yalnızca bu oturum)").effort)
    }
}
