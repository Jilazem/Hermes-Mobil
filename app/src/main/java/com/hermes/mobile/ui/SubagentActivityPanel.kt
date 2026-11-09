package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.data.SubagentActivity
import com.hermes.mobile.data.cleanSubagentSummary
import com.hermes.mobile.data.compactSubagentModel
import com.hermes.mobile.data.effectiveDurationSeconds
import com.hermes.mobile.data.formatSubagentSingleLine
import com.hermes.mobile.ui.theme.HermesColors
import kotlinx.coroutines.delay

@Composable
internal fun SubagentActivityPanel(
    agents: List<SubagentActivity>,
    onOpenChildSession: ((String) -> Unit)? = null,
) {
    if (agents.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    var selectedDetailId by remember { mutableStateOf<String?>(null) }
    val anyRunning = agents.any { it.status.lowercase() in setOf("running", "working", "starting", "pending", "queued") }
    var nowEpochSec by remember { mutableStateOf(System.currentTimeMillis() / 1000.0) }
    LaunchedEffect(anyRunning) {
        while (anyRunning) {
            nowEpochSec = System.currentTimeMillis() / 1000.0
            delay(1000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        val latest = agents.last()
        val headerPreview = formatSubagentSingleLine(latest, nowEpochSec)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Görev Hattı (${agents.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = HermesColors.TextPrimary,
            )
            Text(
                text = headerPreview,
                style = MaterialTheme.typography.labelSmall,
                color = HermesColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "▴" else "▾",
                style = MaterialTheme.typography.labelMedium,
                color = HermesColors.TextMuted,
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                agents.forEach { agent ->
                    val statusLower = agent.status.lowercase()
                    val statusColor = when (statusLower) {
                        "running", "working", "starting" -> Color(0xFF3FB950)
                        "completed", "done", "success", "succeeded", "pass" -> Color(0xFF58A6FF)
                        "failed", "error" -> Color(0xFFF85149)
                        else -> Color(0xFFD29922)
                    }
                    val modelBadge = compactSubagentModel(agent.model)
                    val durSec = effectiveDurationSeconds(agent, nowEpochSec)
                    val singleLine = formatSubagentSingleLine(agent, nowEpochSec)
                    val detailOpen = selectedDetailId == agent.id

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(HermesColors.SurfaceDim, RoundedCornerShape(8.dp))
                            .clickable {
                                if (agent.childSession.isNotBlank() && onOpenChildSession != null && !detailOpen) {
                                    onOpenChildSession(agent.childSession)
                                } else {
                                    selectedDetailId = if (detailOpen) null else agent.id
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Tek satırlık şık görev satırı (Durum nokta + মডেল rozeti + İş Adı & Plan + Süre/Araç/Token)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("●", color = statusColor, fontSize = 10.sp)
                            if (modelBadge.isNotBlank()) {
                                Text(
                                    text = modelBadge,
                                    color = HermesColors.TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .background(statusColor.copy(alpha = 0.18f), RoundedCornerShape(5.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Text(
                                text = singleLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = HermesColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (agent.childSession.isNotBlank() && onOpenChildSession != null) {
                                Text(
                                    text = "Sohbet ↗",
                                    fontSize = 11.sp,
                                    color = Color(0xFF58A6FF),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable { onOpenChildSession(agent.childSession) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                            }
                            Text(
                                text = if (detailOpen) "▴" else "⋯",
                                fontSize = 11.sp,
                                color = HermesColors.TextMuted,
                                modifier = Modifier
                                    .clickable { selectedDetailId = if (detailOpen) null else agent.id }
                                    .padding(horizontal = 4.dp),
                            )
                        }

                        if (detailOpen) {
                            val metrics = listOfNotNull(
                                modelBadge.takeIf { it.isNotBlank() },
                                durSec?.let { "$it sn" },
                                agent.outputTokens?.let { "$it çıktı token" },
                                agent.inputTokens?.let { "$it girdi token" },
                                agent.toolCount?.let { "$it araç" },
                            )
                            if (metrics.isNotEmpty()) {
                                Text(metrics.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = HermesColors.TextSecondary)
                            }
                            val cleanSummary = cleanSubagentSummary(agent.summary)
                            if (cleanSummary.isNotBlank()) MarkdownText(cleanSummary)
                            if (agent.output.isNotBlank()) Text(agent.output, style = MaterialTheme.typography.bodySmall)
                            if (agent.filesRead.isNotEmpty()) Text("Okunan: " + agent.filesRead.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                            if (agent.filesWritten.isNotEmpty()) Text("Çıktılar: " + agent.filesWritten.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
