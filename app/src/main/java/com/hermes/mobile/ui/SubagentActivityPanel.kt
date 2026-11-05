package com.hermes.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.SubagentActivity

@Composable
internal fun SubagentActivityPanel(agents: List<SubagentActivity>) {
    if (agents.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text("Görev hattı · ${agents.size} alt ajan  ${if (expanded) "▴" else "▾"}")
        }
        if (expanded) Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            agents.forEach { agent ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val status = when(agent.status.lowercase()) {
                        "running", "working" -> "Çalışıyor"
                        "completed", "done", "success", "succeeded" -> "Tamamlandı"
                        "failed", "error" -> "Hata"
                        "queued", "pending" -> "Sırada"
                        "starting" -> "Başlıyor"
                        "unconfirmed" -> "Canlı listede yok"
                        "interrupted", "cancelled" -> "Durduruldu"
                        else -> agent.status
                    }
                    Text("↳ $status · ${agent.goal.ifBlank { agent.id }}", style = MaterialTheme.typography.bodyMedium)
                    val metrics = listOfNotNull(agent.model.takeIf { it.isNotBlank() },
                        agent.duration?.let { "${it.toLong()} sn" }, agent.outputTokens?.let { "$it çıktı token" },
                        agent.inputTokens?.let { "$it girdi token" }, agent.toolCount?.let { "$it araç" })
                    if (metrics.isNotEmpty()) Text(metrics.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                    if (agent.summary.isNotBlank()) MarkdownText(agent.summary)
                    if (agent.output.isNotBlank()) Text(agent.output, style = MaterialTheme.typography.bodySmall)
                    if (agent.filesRead.isNotEmpty()) Text("Okunan: " + agent.filesRead.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                    if (agent.filesWritten.isNotEmpty()) Text("Çıktılar: " + agent.filesWritten.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
