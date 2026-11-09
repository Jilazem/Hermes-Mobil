package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.mobile.data.HermesAccessibilityService
import com.hermes.mobile.data.PhoneAuditLog
import com.hermes.mobile.data.PhoneBotLogic
import com.hermes.mobile.data.SettingsStore
import com.hermes.mobile.data.withAutoWrite
import com.hermes.mobile.ui.theme.HermesColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Telefon botu kartı — bot çerçevesi ekranının üstünde durur.
 *
 * Patron kararı (21.10.2026): **otomatik yazma varsayılan açık** — ajanın
 * dokunma/yazma eylemleri onay diyaloğu beklemeden uygulanır. Güvenlik üç
 * katmanla sağlanır: (1) erişilebilirlik servisi kapalıyken otomatik mod
 * açılamaz, (2) **ACİL DURDUR** anahtarı her zaman görünür ve açıldığı anda
 * tüm yazmalar durur (okuma sürer), (3) her yazma eylemi yerel denetim
 * günlüğüne yazılır — kartta son [PhoneBotLogic.MAX_AUDIT] kayıt görünür.
 */
@Composable
fun PhoneBotCard(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val settings by store.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(HermesAccessibilityService.isEnabled(context)) }
    var preview by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var audit by remember { mutableStateOf(PhoneAuditLog.read(context)) }

    val running = HermesAccessibilityService.instanceOrNull() != null
    val lines = PhoneBotLogic.previewLines(preview)
    val header = PhoneBotLogic.screenHeader(preview)
    val blockReason = PhoneBotLogic.autoWriteBlockReason(enabled && running, settings.agentEmergencyStop)

    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .background(HermesColors.Surface, MaterialTheme.shapes.large)
            .border(1.dp, HermesColors.Border, MaterialTheme.shapes.large)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📱", style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    S.t2("Telefon botu", "Phone bot"),
                    color = HermesColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    PhoneBotLogic.statusLine(enabled, running),
                    color = if (enabled) HermesColors.TextFaint else HermesColors.Danger,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Switch(
                checked = settings.agentAutoWrite,
                enabled = enabled && running && !settings.agentEmergencyStop,
                onCheckedChange = { target ->
                    if (target && blockReason != null) {
                        previewError = blockReason
                    } else {
                        previewError = null
                        store.update { it.withAutoWrite(target, enabled && running) }
                    }
                },
            )
        }

        Text(
            PhoneBotLogic.capabilityLabel(settings.agentMayUsePhone, settings.agentReadOnly, settings.fullControl),
            color = HermesColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )

        if (settings.agentEmergencyStop) TextButton(onClick = {
            store.update { it.copy(agentEmergencyStop = false) }
        }) { Text("Telefon erişimini sürdür") }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = enabled && running && !busy, onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { HermesAccessibilityService.instanceOrNull()?.dumpScreen() }
                    }
                    busy = false
                    val outcome = result.getOrNull()
                    when {
                        result.isFailure -> previewError = result.exceptionOrNull()?.message ?: "Okuma hatası"
                        outcome == null -> previewError = "Erişilebilirlik servisi bağlı değil"
                        !outcome.ok -> previewError = outcome.text
                        else -> {
                            previewError = null
                            preview = outcome.text
                        }
                    }
                }
            }) { Text(S.t2("Ekranı oku", "Read screen")) }
            if (!enabled) {
                TextButton(onClick = { HermesAccessibilityService.openPermissionSettings(context) }) {
                    Text(S.t2("İzin ver", "Grant permission"))
                }
            }
            TextButton(onClick = onOpenSettings) { Text(S.t2("Ayarlar", "Settings")) }
        }

        previewError?.let {
            Text(it, color = HermesColors.Danger, style = MaterialTheme.typography.bodySmall)
        }

        header?.let {
            Text(it, color = HermesColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        if (lines.isNotEmpty()) {
            Text(
                lines.joinToString(" · "),
                color = HermesColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
            )
        }

        // Yerel denetim günlüğü — son 20 kayıt, yalnız cihazda.
        Text(
            S.t2(
                "Denetim günlüğü · son ${PhoneBotLogic.MAX_AUDIT} kayıt",
                "Audit log · last ${PhoneBotLogic.MAX_AUDIT} entries",
            ),
            color = HermesColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
        if (audit.isEmpty()) {
            Text(
                S.t2("Henüz yazma eylemi yok", "No write action yet"),
                color = HermesColors.TextFaint,
                style = MaterialTheme.typography.labelSmall,
            )
        } else {
            audit.asReversed().take(PhoneBotLogic.MAX_AUDIT).forEach { entry ->
                Text(
                    entry,
                    color = HermesColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Text(
            S.t2(
                "Otomatik yazma varsayılan açık: ajan onay beklemeden dokunur. Servis kapalıyken açılamaz; " +
                    "ACİL DURDUR tüm yazmayı anında keser ve her eylem burada kayıtlıdır.",
                "Auto-write is on by default: the agent acts without approval. It cannot be enabled while the " +
                    "service is off; EMERGENCY STOP halts all writes instantly and every action is logged here.",
            ),
            color = HermesColors.TextFaint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Kart durumunu yeniler — ekrana dönüşte izin değişmiş olabilir. */
@Composable
fun rememberPhoneBotEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { HermesAccessibilityService.isEnabled(context) }
}
