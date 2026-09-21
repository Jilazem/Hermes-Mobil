package com.hermes.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.AppState
import com.hermes.mobile.GatewayAction
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hermes.mobile.data.ProbeResult
import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.hermes.mobile.data.DemoMask

@Composable
fun HomeScreen(
    state: AppState,
    onGatewayAction: (GatewayAction) -> Unit,
    onSelectProfile: (String) -> Unit = {},
    onSaveProfile: (ServerProfile) -> Unit = {},
    onDeleteProfile: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val profile = state.active
    var editing by remember { mutableStateOf<ServerProfile?>(null) }
    var confirmDelete by remember { mutableStateOf<ServerProfile?>(null) }
    var serversOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 2.dp)
                    .clickable { serversOpen = !serversOpen },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        profile?.name ?: S.t2("Sunucu seçilmedi", "No server selected"),
                        color = HermesColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        DemoMask.text(
                            profile?.activeUrl ?: profile?.normalizedUrl
                                ?: S.t2("Aşağıdan bir sunucu ekleyin", "Add a server below"),
                        ),
                        style = MonoTextStyle,
                        color = HermesColors.TextMuted,
                    )
                }
                Icon(
                    if (serversOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Sunucular",
                    tint = HermesColors.TextMuted,
                )
            }
        }

        // Sunucu yönetimi artık burada — ayrı sekmeye gerek yok.
        if (serversOpen || state.profiles.size > 1 || profile == null) {
            items(state.profiles, key = { "srv-" + it.id }) { p ->
                ServerRow(
                    profile = p,
                    probe = state.probes[p.id],
                    isActive = p.id == profile?.id,
                    onSelect = { onSelectProfile(p.id) },
                    onEdit = { editing = p },
                    onDelete = { confirmDelete = p },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        editing = ServerProfile(name = "", baseUrl = "http://", token = "")
                    }) { Text("Sunucu ekle", color = HermesColors.Midground, style = MaterialTheme.typography.bodyMedium) }
                    TextButton(onClick = onRefresh) {
                        Text("Yenile", color = HermesColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        state.error?.let { message ->
            item {
                HermesCard(Modifier.fillMaxWidth()) {
                    SectionLabel("Hata")
                    Spacer(Modifier.height(4.dp))
                    Text(message, color = HermesColors.Danger, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        (state.activeProbe as? ProbeResult.Fail)?.let { fail ->
            item {
                HermesCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(HermesColors.Offline)
                        Spacer(Modifier.width(8.dp))
                        Text(S.t2("Bağlanamadı", "Couldn't connect"), color = HermesColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(fail.reason, color = HermesColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        state.status?.let { status ->
            item { GatewayCard(status.gatewayState, status.version, status.releaseDate, status.activeAgents, onGatewayAction) }
            if (status.gatewayPlatforms.isNotEmpty()) {
                item {
                    HermesCard(Modifier.fillMaxWidth()) {
                        SectionLabel("Platformlar")
                        Spacer(Modifier.height(8.dp))
                        status.gatewayPlatforms.forEach { (name, platform) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                StatusDot(
                                    when (platform.state) {
                                        "connected" -> HermesColors.Online
                                        "connecting" -> HermesColors.Busy
                                        else -> HermesColors.Offline
                                    },
                                    size = 6,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(name, color = HermesColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.weight(1f))
                                Text(platform.state, color = HermesColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        state.stats?.let { stats ->
            item {
                HermesCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Sistem")
                        Spacer(Modifier.weight(1f))
                        Text(
                            DemoMask.name(DemoMask.Kind.HOST, stats.hostname) +
                                " · ${stats.arch}",
                            color = HermesColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    MeterRow("CPU", stats.cpuPercent, "${stats.cpuCount} " + S.t2("çekirdek", "cores"))
                    Spacer(Modifier.height(8.dp))
                    MeterRow(
                        S.t2("Bellek", "Memory"),
                        stats.memory.percent,
                        "${formatBytes(stats.memory.used)} / ${formatBytes(stats.memory.total)}",
                    )
                    Spacer(Modifier.height(8.dp))
                    MeterRow(
                        "Disk",
                        stats.disk.percent,
                        S.t2("${formatBytes(stats.disk.free)} boş", "${formatBytes(stats.disk.free)} free"),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(S.t2("Çalışma süresi ${formatUptime(stats.uptimeSeconds)}", "Uptime ${formatUptime(stats.uptimeSeconds)}"), color = HermesColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }

    editing?.let { draft ->
        ProfileEditorDialog(
            initial = draft,
            onDismiss = { editing = null },
            onConfirm = { onSaveProfile(it); editing = null },
        )
    }

    confirmDelete?.let { target ->
        DeleteProfileDialog(
            profile = target,
            onDismiss = { confirmDelete = null },
            onConfirm = { onDeleteProfile(target.id); confirmDelete = null },
        )
    }
}

@Composable
private fun GatewayCard(
    gatewayState: String,
    version: String,
    releaseDate: String?,
    activeAgents: Int,
    onAction: (GatewayAction) -> Unit,
) {
    val running = gatewayState == "running"
    HermesCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (running) HermesColors.Online else HermesColors.Offline)
            Spacer(Modifier.width(8.dp))
            Text("Gateway", color = HermesColors.TextPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(gatewayState, color = if (running) HermesColors.Online else HermesColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            // FR-004: bu sayı SUNUCUNUN (/api/status active_agents) bildirdiği
            // değerdir ve Oturumlar > Canlı sekmesinin gateway belleğinden saydığı
            // oturumdan farklı veri kümesidir — etiket bunu söyler, çelişki sanılmasın.
            "v$version" + (releaseDate?.let { " · $it" } ?: "") +
                " · $activeAgents etkin ajan (sunucu raporu)",
            color = HermesColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onAction(GatewayAction.Restart) }, modifier = Modifier.weight(1f)) {
                Text(S.t2("Yeniden başlat", "Restart"), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { onAction(if (running) GatewayAction.Stop else GatewayAction.Start) },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (running) "Durdur" else S.t2("Başlat", "Start"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MeterRow(label: String, percent: Double, detail: String) {
    val fraction = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
    val color = when {
        percent >= 90 -> HermesColors.Danger
        percent >= 70 -> HermesColors.Busy
        else -> HermesColors.Online
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = HermesColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Text("${percent.toInt()}%", color = color, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = color,
            trackColor = HermesColors.SurfaceDim,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(3.dp))
        Text(detail, color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
    }
}

/** Sunucu satırı — Durum ekranının içinde, ayrı sekme yerine. */
@Composable
private fun ServerRow(
    profile: ServerProfile,
    probe: ProbeResult?,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val (dot, status) = when (probe) {
        is ProbeResult.Ok -> HermesColors.Online to S.t2("çevrimiçi · ${probe.latencyMs} ms", "online · ${probe.latencyMs} ms")
        is ProbeResult.Fail -> HermesColors.Offline to probe.reason
        null -> HermesColors.Busy to S.t2("yoklanıyor…", "probing…")
    }

    // Liste satiri min 56dp (FR-004 ritim; kart govdesi zaten 48dp taban tasir).
    HermesCard(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onSelect),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(dot)
            Spacer(Modifier.width(8.dp))
            Text(
                profile.name.ifBlank { S.t2("(adsız)", "(unnamed)") },
                color = if (isActive) HermesColors.Midground else HermesColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            )
            if (isActive) {
                Spacer(Modifier.width(7.dp))
                Text("aktif", color = HermesColors.Online, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, S.t2("Düzenle", "Edit"), tint = HermesColors.TextMuted, modifier = Modifier.width(17.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Sil", tint = HermesColors.TextMuted, modifier = Modifier.width(17.dp))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            DemoMask.text(profile.normalizedUrl),
            style = MonoTextStyle,
            color = HermesColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (profile.normalizedRemote.isNotBlank()) {
            Text(
                "uzak: ${profile.normalizedRemote}",
                style = MonoTextStyle,
                color = HermesColors.TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            status,
            color = if (probe is ProbeResult.Ok) HermesColors.Online else HermesColors.TextFaint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
