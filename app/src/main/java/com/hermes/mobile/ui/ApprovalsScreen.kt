package com.hermes.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.mobile.data.ApprovalCenter
import com.hermes.mobile.data.ApprovalDecision
import com.hermes.mobile.data.ApprovalEntry
import com.hermes.mobile.data.ApprovalGuard
import com.hermes.mobile.data.ApprovalVoice
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle

/**
 * K18B-G3 — MERKEZİ ONAY KUTUSU ("Senin onayın bekleniyor").
 *
 * Sohbet içindeki onay kartları (`/approve`, `/deny`) tek ekranda toplanır:
 * ne istiyor, hangi araç, hangi argüman özeti + Onayla/Reddet + sesli onay.
 * Esin: MausBot "Waiting on your yes" (Apache-2.0, YALNIZ FİKİR — kod
 * kopyalanmadı; bkz. CREDITS.md). Kelivo'nun araç-bazlı onayı da yalnız fikir.
 *
 * "Bazı eylemler durmaz" kartı MausBot'un "approval coverage garanti değildir"
 * uyarısının karşılığı: hangi eylem sınıfının onaysız devam ettiği AÇIKÇA
 * görünür ([ApprovalGuard.passThroughClasses] — guardReason deseni).
 *
 * Cevap yolu değişmez: [onAnswer] → ChatViewModel.answerApproval → gateway
 * `slash.exec` (`/approve` / `/deny [sebep]`).
 */
@Composable
fun ApprovalsScreen(
    onAnswer: (sessionId: String?, key: String, approve: Boolean, reason: String) -> Unit,
    scrollable: Boolean = true,
) {
    val entries by ApprovalCenter.entries.collectAsStateWithLifecycle()
    val pending = entries.filter { it.decision == ApprovalDecision.Pending }
    val history = entries.filter { it.decision != ApprovalDecision.Pending }.asReversed()

    var voiceFeedback by remember { mutableStateOf<String?>(null) }
    // S.t2 @Composable — geri çağrılara giremez; parçalar burada hazırlanır.
    val tNotHeard = S.t2("Ses anlaşılamadı", "Could not hear")
    val tNotInterpreted = S.t2("Yorumlanmadı", "Not interpreted")
    val tSayHint = S.t2(
        "\"onayla\" ya da \"reddet [sebep]\" deyin",
        "say \"approve\" or \"deny [reason]\"",
    )
    val tNoTarget = S.t2("Hedef istek bulunamadı", "No such pending request")
    val tApproved = S.t2("onaylandı", "approved")
    val tDenied = S.t2("reddedildi", "denied")
    val tSayPrompt = S.t2("Onayla / reddet deyin", "Say approve / deny")
    val tVoiceUnavailable = S.t2(
        "Ses girişi bu cihazda kullanılamıyor",
        "Speech input is not available on this device",
    )
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val heard = result.data
            ?.getStringArrayListExtra(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
        if (heard.isNullOrBlank()) {
            voiceFeedback = "$tNotHeard — $tSayHint"
            return@rememberLauncherForActivityResult
        }
        val command = ApprovalVoice.parse(heard, pending.size)
        voiceFeedback = if (command == null) {
            "$tNotInterpreted: \"$heard\" — $tSayHint"
        } else {
            val target = pending.getOrNull(command.entryIndex)
            if (target == null) {
                tNoTarget
            } else {
                onAnswer(target.sessionId, target.id, command.approve, command.reason)
                "Ses: \"$heard\" → " +
                    (if (command.approve) tApproved else tDenied) +
                    (command.reason.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
            }
        }
    }

    val column: @Composable () -> Unit = {
        // ── Başlık + bekleyen sayısı ────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                S.t2("Onaylar", "Approvals"),
                style = MaterialTheme.typography.titleLarge,
                color = HermesColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            if (pending.isNotEmpty()) {
                Text(
                    S.t2("${pending.size} bekleyen", "${pending.size} pending"),
                    style = MaterialTheme.typography.bodySmall,
                    color = HermesColors.Busy,
                )
            }
        }
        Text(
            S.t2(
                "Riskli ajan eylemleri durdurulur; onayınızla devam eder.",
                "Risky agent actions stop here; they continue with your approval.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = HermesColors.TextMuted,
        )
        Spacer(Modifier.height(8.dp))

        // ── Sesli onay ─────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
                .border(1.dp, HermesColors.Border, MaterialTheme.shapes.medium)
                .clickable {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, tSayPrompt)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    runCatching { speechLauncher.launch(intent) }.onFailure {
                        voiceFeedback = tVoiceUnavailable
                    }
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = HermesColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                S.t2("Sesli onay", "Voice approval"),
                color = HermesColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        voiceFeedback?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = HermesColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        // ── Bekleyen istek kartları ────────────────────────────────────
        if (pending.isEmpty()) {
            Text(
                S.t2(
                    "Bekleyen onay yok — ajan riskli bir eylemde durunca burada görünür.",
                    "Nothing pending — risky agent actions appear here.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = HermesColors.TextMuted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        pending.forEach { entry ->
            PendingCard(entry = entry, onAnswer = onAnswer)
            Spacer(Modifier.height(8.dp))
        }

        // ── "Bazı eylemler durmaz" uyarısı ─────────────────────────────
        PassThroughCard()
        Spacer(Modifier.height(8.dp))

        // ── Geçmiş ─────────────────────────────────────────────────────
        if (history.isNotEmpty()) {
            Text(
                S.t2("Geçmiş", "History"),
                style = MaterialTheme.typography.titleSmall,
                color = HermesColors.TextSecondary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(6.dp))
            history.take(ApprovalCenter.HISTORY_LIMIT).forEach { entry ->
                HistoryRow(entry)
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (scrollable) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) { column() }
    } else {
        Column(Modifier.fillMaxWidth()) { column() }
    }
}

/** Bekleyen onay kartı — ne istiyor, hangi araç, hangi argüman + karar düğmeleri. */
@Composable
private fun PendingCard(
    entry: ApprovalEntry,
    onAnswer: (sessionId: String?, key: String, approve: Boolean, reason: String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .border(1.dp, HermesColors.Busy, MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = HermesColors.Busy,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (entry.isSudo) {
                    S.t2("Yükseltilmiş izin isteniyor", "Elevated permission requested")
                } else {
                    S.t2("Onay bekleniyor", "Waiting for your yes")
                },
                color = HermesColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            if (entry.sessionTitle.isNotBlank()) {
                Text(
                    entry.sessionTitle,
                    color = HermesColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            S.t2("Ne istiyor: ", "Requests: ") + entry.request,
            style = MonoTextStyle,
            color = HermesColors.TextSecondary,
        )
        entry.tool?.let { tool ->
            Spacer(Modifier.height(4.dp))
            Text(
                S.t2("Araç: ", "Tool: ") + tool,
                style = MonoTextStyle,
                color = HermesColors.TextSecondary,
            )
        }
        entry.argsSummary?.let { args ->
            Spacer(Modifier.height(2.dp))
            Text(
                S.t2("Argümanlar: ", "Args: ") + args,
                style = MonoTextStyle,
                color = HermesColors.TextSecondary,
            )
        }
        // guardReason deseni: neden DURDUĞU da yazılır (tek satır, net sebep).
        ApprovalGuard.stopReason(entry.tool, entry.isSudo)?.let { why ->
            Spacer(Modifier.height(4.dp))
            Text(
                S.t2("Neden durdu: ", "Why it stopped: ") + why,
                style = MaterialTheme.typography.bodySmall,
                color = HermesColors.TextMuted,
            )
        }
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .background(HermesColors.Midground, MaterialTheme.shapes.medium)
                    .clickable { onAnswer(entry.sessionId, entry.id, true, "") }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = HermesColors.OnAccent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    S.t2("Onayla", "Approve"),
                    color = HermesColors.OnAccent,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                Modifier
                    .weight(1f)
                    .border(1.dp, HermesColors.Danger, MaterialTheme.shapes.medium)
                    .clickable { onAnswer(entry.sessionId, entry.id, false, "") }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = HermesColors.Danger,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    S.t2("Reddet", "Deny"),
                    color = HermesColors.Danger,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * "Bazı eylemler durmaz" — onay KAPSAMININ dürüst sınırı. MausBot deseni:
 * "approval coverage garanti değildir" uyarısı; veri [ApprovalGuard]'dan
 * (guardReason dilinde), burada yalnız çizim.
 */
@Composable
private fun PassThroughCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .border(1.dp, HermesColors.Border, MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = HermesColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                S.t2("Bazı eylemler durmaz", "Some actions do not stop"),
                color = HermesColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            S.t2(
                "Bu kutu yalnız DURDURULAN (riskli) eylemleri gösterir. " +
                    "Aşağıdaki sınıflar onaysız devam eder:",
                "This box only lists STOPPED (risky) actions. These classes " +
                    "continue without approval:",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = HermesColors.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        ApprovalGuard.passThroughClasses().forEach { cls ->
            Text(
                "• ${cls.title} — ${cls.detail}",
                style = MaterialTheme.typography.bodySmall,
                color = HermesColors.TextMuted,
            )
            Spacer(Modifier.height(3.dp))
        }
    }
}

/** Geçmiş satırı — onaylanan/reddedilen kayıt. */
@Composable
private fun HistoryRow(entry: ApprovalEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .border(1.dp, HermesColors.Border, MaterialTheme.shapes.medium)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (entry.decision) {
                is ApprovalDecision.Approved -> Icons.Default.Check
                is ApprovalDecision.Denied -> Icons.Default.Close
                else -> Icons.Default.Info
            },
            contentDescription = null,
            tint = when (entry.decision) {
                is ApprovalDecision.Approved -> HermesColors.OnAccent
                is ApprovalDecision.Denied -> HermesColors.Danger
                else -> HermesColors.TextMuted
            },
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when (entry.decision) {
                    is ApprovalDecision.Approved -> S.t2("Onaylandı", "Approved")
                    is ApprovalDecision.Denied -> S.t2("Reddedildi", "Denied")
                    is ApprovalDecision.SettledElsewhere ->
                        S.t2("Başka kanaldan yanıtlandı", "Answered elsewhere")
                    ApprovalDecision.Pending -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = HermesColors.TextSecondary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                entry.request,
                style = MaterialTheme.typography.bodySmall,
                color = HermesColors.TextMuted,
                maxLines = 1,
            )
            (entry.decision as? ApprovalDecision.Denied)?.reason
                ?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        S.t2("Sebep: ", "Reason: ") + it,
                        style = MaterialTheme.typography.bodySmall,
                        color = HermesColors.TextMuted,
                    )
                }
        }
    }
}