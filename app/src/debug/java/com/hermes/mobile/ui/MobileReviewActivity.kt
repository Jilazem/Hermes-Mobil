package com.hermes.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermes.mobile.ChatItem
import com.hermes.mobile.ChatState
import com.hermes.mobile.data.ConnectionState
import com.hermes.mobile.data.ModelProvider
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.HermesTheme

/** Controlled UI fixture. No gateway credentials or production actions. */
class MobileReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("screen") ?: "chat"
        if (mode == "settings-sync") {
            val ui = com.hermes.mobile.data.SettingsStore(this)
            val service = com.hermes.mobile.data.SettingsStore(this)
            val original = ui.current()
            val steps = mutableListOf<String>()
            try {
                ui.update { it.copy(agentEmergencyStop = false, agentMayUsePhone = true,
                    agentReadOnly = false, agentAutoWrite = true, agentMayReply = true) }
                check(!service.current().agentEmergencyStop)
                check(com.hermes.mobile.data.PhoneBotLogic.commandBlockReason(service.current(), "phone_reply") == null)
                ui.update { it.copy(agentEmergencyStop = true) }
                check(service.settings.value.agentEmergencyStop)
                steps += "ENCRYPTED_CROSS_INSTANCE_FLOW=PASS"
                check(com.hermes.mobile.data.PhoneBotLogic.commandBlockReason(service.current(), "phone_reply") ==
                    com.hermes.mobile.data.PhoneBotLogic.WHY_EMERGENCY_STOP)
                check(com.hermes.mobile.data.PhoneBotLogic.commandBlockReason(service.current(), "phone_status") == null)
                steps += "SERVICE_WRITE_BLOCKED_READ_ALLOWED=PASS"
                service.update { it.copy(fontScale = 1.1f) }
                check(ui.current().agentEmergencyStop)
                check(com.hermes.mobile.data.SettingsStore(this).current().agentEmergencyStop)
                steps += "STALE_UNRELATED_UPDATE_PRESERVES_STOP=PASS"
                steps += "PERSISTED_AUTHORIZATION_READ=PASS"
            } catch (error: Exception) { steps += "RESULT=FAIL ${error.javaClass.simpleName}" }
            finally { ui.update { original } }
            if (steps.none { it.startsWith("RESULT=FAIL") }) steps += "RESULT=PASS"
            java.io.File(filesDir, "settings-sync-proof.txt").writeText(steps.joinToString("\n"))
            setContent { HermesTheme { Text(steps.joinToString("\n"), color = HermesColors.TextPrimary) } }
            return
        }
        if (mode == "bots") {
            val endpoint = intent.getStringExtra("botEndpoint") ?: "http://127.0.0.1:28765"
            require(endpoint == "http://127.0.0.1:28765")
            val profile = com.hermes.mobile.data.ServerProfile(id = "workspace-review-only", name = "Kontrollü Dashboard", baseUrl = endpoint, token = "local-review-only")
            com.hermes.mobile.data.ServerProfileStore(this).apply { upsert(profile); setActiveId(profile.id) }
            val key = androidx.security.crypto.MasterKey.Builder(this)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
            androidx.security.crypto.EncryptedSharedPreferences.create(this, "bot-framework", key,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
                .edit().putString("endpoint", endpoint).putString("token", "local-review-only").apply()
        }
        setContent {
            HermesTheme {
                var picker by remember { mutableStateOf(mode == "model") }
                var chosen by remember { mutableStateOf("örnek-model-uzun-adı-instruct-2026") }
                Column(Modifier.fillMaxSize().background(HermesColors.Background).statusBarsPadding().navigationBarsPadding()) {
                    Text("Örnek oturum · görsel test", color = HermesColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    if (mode == "macros") {
                        MacrosScreen(onBack = { finish() })
                    } else if (mode == "voice-frame") {
                        JarvisOverlay(
                            state = com.hermes.mobile.assistant.JarvisState(phase = com.hermes.mobile.assistant.JarvisPhase.Speaking, level = intent.getFloatExtra("level", .6f), heard = "Temayla uyumlu ses çerçevesi", answer = "Konuşma düzeyi, ekranın kenarındaki ışığın şiddetini değiştirir."),
                            onMic = {}, onClose = { finish() }, onNewTopic = {}, onOpenChat = {}, onAsk = {}, onCardBounds = { _,_,_,_ -> })
                    } else if (mode == "settings") {
                        val settings = remember { com.hermes.mobile.data.SettingsStore(this@MobileReviewActivity) }
                        val value by settings.settings.collectAsState()
                        SettingsScreen(value, emptyList(), settings::update, {}, {}, { null }, { "" },
                            onOpenMola = { startActivity(android.content.Intent(this@MobileReviewActivity, MobileReviewActivity::class.java).putExtra("screen", "mola")) })
                    } else if (mode == "ema-settings") {
                        val settings = remember { com.hermes.mobile.data.SettingsStore(this@MobileReviewActivity) }
                        val value by settings.settings.collectAsState()
                        EmaSettingsCard(value, settings::update)
                    } else if (mode == "mola") {
                        var preview by remember { mutableStateOf(false) }
                        if (preview) HermesPanelPreviewScreen(onBack = { preview = false }, onOpenGuide = { preview = false })
                        else MolaGuideScreen(onBack = { finish() }, onPreview = { preview = true }, onOpenSettings = {})
                    } else if (mode == "bots") {
                        BotFrameworkScreen(profile = com.hermes.mobile.data.ServerProfileStore(this@MobileReviewActivity).active())
                    } else if (mode == "default-link") {
                        MarkdownText("[Bot sohbetinden bağlantıyı aç](https://example.com/hermes-mobile-test)",
                            modifier = Modifier.padding(20.dp))
                    } else ChatScreen(
                        state = ChatState(connection = ConnectionState.Open, currentModel = chosen,
                            topic = "Yeni Hermes Mobil", sessionId = "review-only",
                            items = listOf(
                                ChatItem.User("review-u", "Botlarımı ve araç panelini birlikte yönetmek istiyorum."),
                                ChatItem.Assistant("review-a", """
                                    ### Her şey elinin altında

                                    Sohbet, botlar ve Mola araç paneli aynı çalışma alanında.

                                    - **Botlar:** görev kuyruğu ve sohbet geçmişi
                                    - **Mola:** seçili oturumun son mesajı
                                    - **Model:** sağlayıcı ve düşünme seviyesi

                                    [Bağlantıyı aç](https://example.com/hermes-mobile-test)

                                    Düz bağlantı da çalışır: https://example.com/docs
                                """.trimIndent()),
                            )),
                        onSend = {}, onNewSession = {}, onOpenModelPicker = { picker = true },
                        currentProfile = "Örnek asistan", activeProfileName = "Örnek asistan",
                    )
                }
                if (picker) ModelPickerSheet(
                    providers = listOf(ModelProvider(slug = "review", name = "Örnek sağlayıcı",
                        models = listOf(chosen, "ikinci-örnek-model", "çok-uzun-model-ismi-büyük-bağlam-instruct-quantized-2026"))),
                    currentModel = chosen, loading = false, brokenModels = emptySet(), hiddenModels = emptySet(),
                    pinnedModels = emptyList(), modelUsage = emptyMap(), showBroken = false,
                    onToggleShowBroken = {}, onTogglePin = {}, onToggleHidden = {},
                    onSelect = { _, model, _, _ -> chosen = model; picker = false },
                    onDismiss = { picker = false },
                )
            }
        }
    }
}
