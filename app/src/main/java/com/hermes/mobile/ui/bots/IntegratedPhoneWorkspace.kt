package com.hermes.mobile.ui.bots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hermes.mobile.data.BridgeState
import com.hermes.mobile.data.HermesAccessibilityService
import com.hermes.mobile.data.PhoneBridgeService
import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.data.SettingsStore
import com.hermes.mobile.ui.PhoneBotCard
import com.hermes.mobile.ui.S
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Local readiness only: it does not grant permission, start a task, or certify a server response. */
internal enum class IntegratedPhoneStage {
    NeedsDashboard, EmergencyStopped, NeedsAccessibility, NeedsPhoneAuthorization,
    NeedsBridge, ReadyReadOnly, Ready,
}

internal fun integratedPhoneStage(
    dashboardConfigured: Boolean,
    emergencyStopped: Boolean,
    accessibilityEnabled: Boolean,
    accessibilityRunning: Boolean,
    phoneAuthorized: Boolean,
    bridgeConnected: Boolean,
    readOnly: Boolean,
): IntegratedPhoneStage = when {
    emergencyStopped -> IntegratedPhoneStage.EmergencyStopped
    !dashboardConfigured -> IntegratedPhoneStage.NeedsDashboard
    !accessibilityEnabled || !accessibilityRunning -> IntegratedPhoneStage.NeedsAccessibility
    !phoneAuthorized -> IntegratedPhoneStage.NeedsPhoneAuthorization
    !bridgeConnected -> IntegratedPhoneStage.NeedsBridge
    readOnly -> IntegratedPhoneStage.ReadyReadOnly
    else -> IntegratedPhoneStage.Ready
}

/**
 * Mika's observe/act idea and Phongent's durable fleet are presented through
 * Hermes's existing native phone bridge. There is no external-app connection form.
 * The surrounding workspace owns scrolling and the task dispatch confirmation.
 */
@Composable
fun IntegratedPhoneWorkspace(
    profile: ServerProfile?,
    onOpenSettings: () -> Unit,
    onOpenMola: () -> Unit,
    onCreateTask: (String) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val store = remember(context) { SettingsStore(context) }
    val settings by store.settings.collectAsStateWithLifecycle()
    val bridge by PhoneBridgeService.stateFlow.collectAsStateWithLifecycle()
    var accessibilityEnabled by remember(context) { mutableStateOf(HermesAccessibilityService.isEnabled(context)) }
    var accessibilityRunning by remember { mutableStateOf(HermesAccessibilityService.isRunning()) }
    var draft by rememberSaveable(profile?.id) { mutableStateOf("") }
    val configured = profile != null && profile.token.isNotBlank() && profile.candidates.any(String::isNotBlank)
    LaunchedEffect(context, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                accessibilityEnabled = HermesAccessibilityService.isEnabled(context)
                accessibilityRunning = HermesAccessibilityService.isRunning()
                delay(2_000)
            }
        }
    }
    val stage = integratedPhoneStage(
        dashboardConfigured = configured,
        emergencyStopped = settings.agentEmergencyStop,
        accessibilityEnabled = accessibilityEnabled,
        accessibilityRunning = accessibilityRunning,
        phoneAuthorized = settings.agentMayUsePhone,
        bridgeConnected = bridge == BridgeState.CONNECTED,
        readOnly = settings.agentReadOnly,
    )
    val canPreparePhoneTask = stage == IntegratedPhoneStage.Ready || stage == IntegratedPhoneStage.ReadyReadOnly
    var advanced by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedCard(onClick=onOpenMola,modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.DirectionsCar,null)
                Text("Mola · Android Auto",style=MaterialTheme.typography.titleSmall)
                TextButton(onClick=onOpenMola) { Text("Aç") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Telefon asistanın", style = MaterialTheme.typography.titleSmall)
                Text("Sesle konuş · seçtiğin ekranı Özetle / Açıkla", style=MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Button(onClick = {
                    val intent = com.hermes.mobile.data.AssistantRole.requestIntent(context)
                        ?: com.hermes.mobile.data.AssistantRole.settingsIntent()
                    runCatching { context.startActivity(intent) }
                }) { Text("Asistanı seç",style=MaterialTheme.typography.labelMedium) }
                TextButton(onClick = {
                    if (!com.hermes.mobile.assistant.HermesVoiceInteractionService.show()) onOpenSettings()
                }) { Text("Konuş",style=MaterialTheme.typography.labelMedium) }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Hub, null, tint = MaterialTheme.colorScheme.primary)
                    Text(S.t2("Tek bağlantı · Hermes", "One connection · Hermes"),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Text(profile?.name?.takeIf(String::isNotBlank) ?: S.t2("Dashboard profili seç", "Choose a Dashboard profile"),
                    style = MaterialTheme.typography.titleMedium)
                Text(S.t2(
                    "Telefon ajanı, görev filosu ve Mola, sohbette seçtiğin Dashboard bağlantısını kullanır.",
                    "The phone agent, task fleet and Mola use the Dashboard connection selected for chat.",
                ), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!configured) TextButton(onClick = onOpenSettings) {
                    Text(S.t2("Hermes bağlantısını ayarla", "Set up Hermes connection"))
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.PhoneAndroid, null)
                    Text(S.t2("Telefon ajanı", "Phone agent"), style = MaterialTheme.typography.titleLarge)
                }
                Text(S.t2("Mika'dan esinlenen gözlem ve eylem akışı, bu telefonun Hermes servisleriyle çalışır.",
                    "The observe-and-act workflow inspired by Mika uses this phone's Hermes services."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(when (stage) {
                    IntegratedPhoneStage.EmergencyStopped -> S.t2("ACİL DURDUR açık · yeni telefon görevi aktarılamaz.", "EMERGENCY STOP on · new phone tasks are blocked.")
                    IntegratedPhoneStage.NeedsDashboard -> S.t2("İlk adım: sohbet için bir Dashboard bağlantısı seç.", "First, select a Dashboard connection for chat.")
                    IntegratedPhoneStage.NeedsAccessibility -> S.t2("Android erişilebilirlik izni gerekiyor; izin verildiğinde durum burada yenilenir.", "Android accessibility permission is required; this status refreshes after granting it.")
                    IntegratedPhoneStage.NeedsPhoneAuthorization -> S.t2("Telefon erişimi kapalı. Telefon ayarlarında ajana izin ver.", "Phone access is off. Authorize the agent in phone settings.")
                    IntegratedPhoneStage.NeedsBridge -> S.t2("Telefon köprüsü henüz bağlı değil.", "The phone bridge is not connected yet.")
                    IntegratedPhoneStage.ReadyReadOnly -> S.t2("Telefon bağlı · salt okunur yetkiler geçerli.", "Phone connected · read-only permissions apply.")
                    IntegratedPhoneStage.Ready -> S.t2("Telefon bağlı · eylemler mevcut telefon izinlerine göre yürütülür.", "Phone connected · actions follow the existing phone permissions.")
                }, color = if (settings.agentEmergencyStop) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text(S.t2("Köprü: ", "Bridge: ") + when (bridge) {
                    BridgeState.OFF -> S.t2("kapalı", "off")
                    BridgeState.CONNECTING -> S.t2("bağlanıyor", "connecting")
                    BridgeState.CONNECTED -> S.t2("bağlı", "connected")
                    BridgeState.RETRYING -> S.t2("yeniden bağlanıyor", "reconnecting")
                    BridgeState.TAKEN_OVER -> S.t2("başka cihaz devraldı", "taken over by another device")
                }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!accessibilityEnabled || !accessibilityRunning) FilledTonalButton(
                    onClick = { HermesAccessibilityService.openPermissionSettings(context) }, modifier = Modifier.fillMaxWidth(),
                ) { Text(S.t2("Android telefon iznini aç", "Open Android phone permission")) }
                if (configured && settings.agentMayUsePhone && bridge != BridgeState.CONNECTED) TextButton(
                    onClick = { PhoneBridgeService.reconnect(context) }, modifier = Modifier.fillMaxWidth(),
                ) { Text(S.t2("Telefon bağlantısını yeniden kur", "Reconnect this phone")) }
                TextButton(onClick = onOpenSettings) { Text(S.t2("Telefon yetkileri", "Phone permissions")) }
            }
        }
        // Existing native controls retain the emergency switch, audit log and actual policy gates.
        TextButton(onClick={advanced=!advanced}) {Text(if(advanced) "Ayrıntıları gizle" else "Telefon denetimi ve görev ayrıntıları")}
        if(advanced) {
        key(accessibilityEnabled, accessibilityRunning) { PhoneBotCard(onOpenSettings) }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.CheckCircle, null)
                    Text(S.t2("Görev filosu", "Task fleet"), style = MaterialTheme.typography.titleLarge)
                }
                Text(S.t2("Phongent'ten esinlenen kalıcı kuyruk, görevi ve sonucunu izler. Taslağını Görevler bölümünde inceleyip gönder.",
                    "The durable queue inspired by Phongent tracks tasks and results. Review and send your draft in Tasks."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(draft, { draft = it.take(4000) }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                    label = { Text(S.t2("Bu telefonda yapılacak iş", "Task for this phone")) },
                    placeholder = { Text(S.t2("Örn. ekrandaki bilgileri oku ve özetle", "For example, read and summarize the screen")) })
                Button(enabled = canPreparePhoneTask && draft.isNotBlank(), onClick = {
                    onCreateTask(draft.trim())
                }, modifier = Modifier.fillMaxWidth()) { Text(S.t2("Görev taslağını filoya aktar", "Move task draft to fleet")) }
                Text(S.t2("Aktarmak işi başlatmaz; gönderim ve sonuç Görevler bölümünde izlenir.",
                    "Moving a draft does not start it; sending and results are tracked in Tasks."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
        Spacer(Modifier.height(8.dp))
    }
}
