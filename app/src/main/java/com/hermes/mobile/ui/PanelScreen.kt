package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.data.DemoMask
import com.hermes.mobile.PanelSection
import com.hermes.mobile.PanelState
import com.hermes.mobile.data.CronJob
import com.hermes.mobile.data.FileEntry
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle

private fun iconFor(section: PanelSection): ImageVector = when (section) {
    PanelSection.Status -> Icons.Default.Home
    PanelSection.Terminal -> Icons.Default.Terminal
    PanelSection.Spark -> Icons.Default.Bolt
    PanelSection.Files -> Icons.Default.Folder
    PanelSection.Logs -> Icons.Default.Subject
    PanelSection.Diag -> Icons.Default.BugReport
    PanelSection.Cron -> Icons.Default.Schedule
    PanelSection.Skills -> Icons.Default.Tune
    PanelSection.Mcp -> Icons.Default.Cable
    PanelSection.Webhooks -> Icons.Default.Link
    PanelSection.Pairing -> Icons.Default.Verified
    PanelSection.Config -> Icons.Default.Settings
}

@Composable
private fun labelFor(section: PanelSection): String = when (section) {
    PanelSection.Status -> S.t2("Durum", "Status")
    PanelSection.Terminal -> "Terminal"
    PanelSection.Spark -> "Spark"
    PanelSection.Files -> S.t2("Dosyalar", "Files")
    PanelSection.Logs -> S.t2("Günlükler", "Logs")
    PanelSection.Diag -> S.t2("Tanılama", "Diagnostics")
    PanelSection.Cron -> "Cron"
    PanelSection.Skills -> S.t2("Yetenekler", "Skills")
    PanelSection.Mcp -> "MCP"
    PanelSection.Webhooks -> S.t2("Web kancaları", "Webhooks")
    PanelSection.Pairing -> S.t2("Eşleştirme", "Pairing")
    PanelSection.Config -> S.t2("Yapılandırma", "Config")
}

@Composable
private fun hintFor(section: PanelSection): String = when (section) {
    PanelSection.Status -> S.t2(
        "Sunucu durumu, gateway, sunucu profilleri",
        "Server status, gateway, server profiles",
    )
    PanelSection.Terminal -> S.t2(
        "Slash komutu ya da ajana görev",
        "Slash command, or a task for the agent",
    )
    PanelSection.Spark -> S.t2(
        "node1 · node2 — GPU, CPU, bellek, sıcaklık",
        "node1 · node2 — GPU, CPU, memory, temps",
    )
    PanelSection.Files -> S.t2(
        "Yönetilen kökteki dosyalar — dokun, aç",
        "Files under the managed root — tap to open",
    )
    PanelSection.Logs -> S.t2("Ajan ve gateway günlükleri", "Agent and gateway logs")
    PanelSection.Diag -> S.t2("Uygulamanın hata kaydı", "This app's error log")
    PanelSection.Cron -> S.t2(
        "Zamanlanmış işler ve sonraki çalışma",
        "Scheduled jobs and next run",
    )
    PanelSection.Skills -> S.t2("Ajanın yüklü yetenekleri", "Skills installed on the agent")
    PanelSection.Mcp -> S.t2("Bağlı MCP sunucuları", "Connected MCP servers")
    PanelSection.Webhooks -> S.t2("Dışarıdan tetikleme uçları", "Inbound trigger endpoints")
    PanelSection.Pairing -> S.t2("Cihaz eşleştirme istekleri", "Device pairing requests")
    PanelSection.Config -> S.t2("Ham yapılandırma (salt okunur)", "Raw config (read-only)")
}

/**
 * Pano — Hermes Desktop'ın yan menüsündeki bölümlerin mobil karşılığı.
 *
 * Bölüm başına ayrı sekme açmak yerine tek liste + iç gezinme: alt çubukta
 * altı sekme zaten var, sekiz tane daha eklemek kullanılmaz hale getirirdi.
 */
@Composable
fun PanelScreen(
    state: PanelState,
    onOpen: (PanelSection) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onBrowse: (String?) -> Unit,
    onSelectLogFile: (String) -> Unit,
    onOpenFile: (FileRef) -> Unit,
    onToggleCron: (CronJob) -> Unit = {},
    onSetCronSchedule: (CronJob, String) -> Unit = { _, _ -> },
    onTriggerCron: (CronJob) -> Unit = {},
    /** Ayardan kapatılırsa Spark bölümü listede hiç görünmez. */
    sparkEnabled: Boolean = true,
    /**
     * Durum ve Terminal kendi ekranları olarak duruyor; Pano onları yalnız
     * barındırıyor. Böylece alt çubuk dört sekmede kalıyor.
     */
    statusContent: @Composable () -> Unit = {},
    terminalContent: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.section != null) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = S.back,
                    tint = HermesColors.TextSecondary,
                    modifier = Modifier.size(38.dp).clickable(onClick = onClose).padding(8.dp),
                )
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    state.section?.let { labelFor(it) } ?: S.panelTitle,
                    color = HermesColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    state.section?.let { hintFor(it) } ?: S.panelSubtitle,
                    color = HermesColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (state.section != null) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = S.refresh,
                    tint = HermesColors.TextSecondary,
                    modifier = Modifier.size(38.dp).clickable(onClick = onRefresh).padding(8.dp),
                )
            }
        }

        state.error?.let { msg ->
            Text(
                msg,
                color = HermesColors.Danger,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Box(Modifier.weight(1f)) {
            when (state.section) {
                null -> SectionMenu(onOpen, sparkEnabled)
                PanelSection.Status -> statusContent()
                PanelSection.Terminal -> terminalContent()
                PanelSection.Spark -> SparkView(state)
                PanelSection.Files -> FilesView(state, onBrowse, onOpenFile)
                PanelSection.Logs -> LogsView(state, onSelectLogFile)
                PanelSection.Diag -> DiagView()
                PanelSection.Cron -> CronView(state, onToggleCron, onSetCronSchedule, onTriggerCron)
                PanelSection.Skills -> SkillsView(state)
                PanelSection.Mcp -> McpView(state)
                PanelSection.Webhooks -> WebhooksView(state)
                PanelSection.Pairing -> PairingView(state)
                PanelSection.Config -> MonoBlock(state.config)
            }

            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(18.dp),
                    strokeWidth = 2.dp,
                    color = HermesColors.Midground,
                )
            }
        }
    }
}

@Composable
private fun SectionMenu(onOpen: (PanelSection) -> Unit, sparkEnabled: Boolean) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val sections = PanelSection.entries.filter {
            it != PanelSection.Spark || sparkEnabled
        }
        items(sections, key = { it.name }) { section ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
                    .border(1.dp, HermesColors.Border, MaterialTheme.shapes.medium)
                    .clickable { onOpen(section) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    iconFor(section),
                    contentDescription = null,
                    tint = HermesColors.Midground,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(13.dp))
                Column {
                    Text(labelFor(section), color = HermesColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    Text(hintFor(section), color = HermesColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

/**
 * DGX Spark kartları — node1 / node2.
 *
 * Ölçümler sparkDash'ten geliyor; burada yalnız okunuyor. Sıcaklık ve kullanım
 * renkleri eşiklere göre: GB10'da 80°C üstü dikkat gerektiriyor.
 */
@Composable
private fun SparkView(state: PanelState) {
    if (state.sparks.isEmpty() && !state.loading) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text(
                S.t2("Spark bulunamadı.", "No Spark found."),
                color = HermesColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "sparkDash sunucuda 5555'te çalışmalı ve makineler kayıtlı olmalı. " +
                    "Ayarlar → sparkDash'ten kapatabilir ya da adresi değiştirebilirsin.",
                color = HermesColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.sparks, key = { it.id }) { spark -> SparkCard(spark) }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SparkCard(spark: com.hermes.mobile.data.SparkMetrics) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (spark.online) HermesColors.Online else HermesColors.Danger,
                        MaterialTheme.shapes.extraSmall,
                    )
            )
            Spacer(Modifier.width(9.dp))
            Text(
                DemoMask.name(DemoMask.Kind.NODE, spark.name.ifBlank { spark.id }),
                color = HermesColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                spark.hardware.gpuChip.ifBlank { spark.hardware.cpuModel },
                color = HermesColors.TextFaint,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Spacer(Modifier.height(10.dp))
        SparkBar("GPU", spark.metrics.gpu.usage, "${spark.metrics.gpu.temperature.toInt()}°C")
        Spacer(Modifier.height(6.dp))
        SparkBar("CPU", spark.metrics.cpu.usage, "${spark.metrics.cpu.temperature.toInt()}°C")
        Spacer(Modifier.height(6.dp))
        SparkBar(
            S.t2("Bellek", "Memory"),
            spark.metrics.ram.percent,
            "${spark.metrics.ram.used / 1024} / ${spark.metrics.ram.total / 1024} GB",
        )

        if (spark.uptime > 0) {
            Spacer(Modifier.height(8.dp))
            val days = spark.uptime / 86_400
            val hours = (spark.uptime % 86_400) / 3_600
            Text(
                S.t2("çalışma süresi ", "uptime ") +
                    "${if (days > 0) "${days}${S.t2("g", "d")} " else ""}${hours}${S.t2("s", "h")}",
                color = HermesColors.TextFaint,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SparkBar(label: String, percent: Double, right: String) {
    val p = percent.coerceIn(0.0, 100.0).toFloat() / 100f
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = HermesColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text("%${percent.toInt()}", color = HermesColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(8.dp))
            Text(right, color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(HermesColors.Surface, MaterialTheme.shapes.extraSmall)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(p)
                    .height(4.dp)
                    .background(
                        when {
                            percent >= 90 -> HermesColors.Danger
                            percent >= 70 -> HermesColors.Busy
                            else -> HermesColors.Online
                        },
                        MaterialTheme.shapes.extraSmall,
                    )
            )
        }
    }
}

@Composable
private fun FilesView(
    state: PanelState,
    onBrowse: (String?) -> Unit,
    onOpenFile: (FileRef) -> Unit,
) {
    val listing = state.files
    Column(Modifier.fillMaxSize()) {
        Text(
            DemoMask.path(listing?.path) ?: "…",
            style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize, lineHeight = MaterialTheme.typography.labelSmall.lineHeight),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            listing?.parent?.let { parent ->
                item(key = "..") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onBrowse(parent) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = HermesColors.TextMuted,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(11.dp))
                        Text(S.t2("üst klasör", "parent folder"), color = HermesColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(listing?.entries.orEmpty(), key = { it.path }) { entry ->
                FileRow(entry, onBrowse, onOpenFile)
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    onBrowse: (String?) -> Unit,
    onOpenFile: (FileRef) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                if (entry.isDirectory) onBrowse(entry.path)
                else onOpenFile(FileRef(entry.path))
            }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = if (entry.isDirectory) HermesColors.Midground else HermesColors.TextFaint,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(11.dp))
        Text(
            DemoMask.name(DemoMask.Kind.FILE, entry.name),
            color = HermesColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        entry.size?.takeIf { !entry.isDirectory }?.let { size ->
            Text(formatBytes(size), color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Sunucuda tutulan günlük dosyaları — panonun kullandığı adlarla aynı. */
private val LOG_FILES = listOf("agent", "gateway", "cron", "mcp", "telegram")

@Composable
private fun LogsView(state: PanelState, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            LOG_FILES.forEach { file ->
                val on = file == state.logFile
                Text(
                    file,
                    color = if (on) HermesColors.Background else HermesColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .background(
                            if (on) HermesColors.Midground else HermesColors.SurfaceDim,
                            MaterialTheme.shapes.large,
                        )
                        .clickable { onSelect(file) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        MonoBlock(DemoMask.logs(state.logLines.joinToString("")), reverse = true)
    }
}

@Composable
private fun CronView(
    state: PanelState,
    onToggle: (CronJob) -> Unit,
    onSetSchedule: (CronJob, String) -> Unit,
    onTrigger: (CronJob) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(state.cron, key = { it.id }) { job ->
            CronCard(job, onToggle, onSetSchedule, onTrigger)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun CronCard(
    job: CronJob,
    onToggle: (CronJob) -> Unit,
    onSetSchedule: (CronJob, String) -> Unit,
    onTrigger: (CronJob) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .clickable { expanded = !expanded }
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(
                        if (job.enabled) HermesColors.Online else HermesColors.TextFaint,
                        MaterialTheme.shapes.extraSmall,
                    )
            )
            Spacer(Modifier.width(9.dp))
            Text(
                DemoMask.name(DemoMask.Kind.CRON, job.name),
                color = HermesColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(job.scheduleDisplay, style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize), color = HermesColors.TextMuted)
            Spacer(Modifier.width(8.dp))
            // Duraklat / devam — sunucuda doğrulanmış tek iki eylem.
            Text(
                if (job.enabled) S.t2("duraklat", "pause") else S.t2("devam", "resume"),
                color = if (job.enabled) HermesColors.Busy else HermesColors.Online,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(HermesColors.Surface, MaterialTheme.shapes.medium)
                    .clickable { onToggle(job) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            S.t2("sonraki: ", "next: ") +
                (job.nextRunAt?.take(16)?.replace('T', ' ') ?: "—") +
                S.t2("   son: ", "   last: ") +
                (job.lastRunAt?.take(16)?.replace('T', ' ') ?: "—"),
            style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize, lineHeight = MaterialTheme.typography.labelSmall.lineHeight),
        )
        if (expanded) {
            if (job.prompt.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(job.prompt, color = HermesColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CronAction(S.t2("Zamanlamayı değiştir", "Edit schedule")) { editing = true }
                CronAction(S.t2("Şimdi çalıştır", "Run now"), danger = true) { confirming = true }
            }
        }
    }

    if (editing) {
        var expr by remember { mutableStateOf(job.scheduleDisplay) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = HermesColors.Surface,
            title = { Text(S.t2("Zamanlama", "Schedule"), color = HermesColors.TextPrimary) },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = expr,
                        onValueChange = { expr = it },
                        singleLine = true,
                        placeholder = { Text("0 2 * * *", color = HermesColors.TextFaint) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        S.t2(
                            "Cron biçimi: dakika saat gün ay haftagünü",
                            "Cron format: minute hour day month weekday",
                        ),
                        color = HermesColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { editing = false; onSetSchedule(job, expr) }
                ) { Text(S.t2("Kaydet", "Save"), color = HermesColors.Midground) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { editing = false }) {
                    Text(S.t2("Vazgeç", "Cancel"), color = HermesColors.TextSecondary)
                }
            },
        )
    }

    // Onay şart: cron işi pahalı olabilir, yanlışlıkla tetiklenmesin.
    if (confirming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = HermesColors.Surface,
            title = {
                Text(S.t2("Şimdi çalıştırılsın mı?", "Run now?"), color = HermesColors.TextPrimary)
            },
            text = {
                Text(
                    S.t2(
                        "\"${job.name}\" hemen çalışacak. Zamanlaması değişmez.",
                        "\"${job.name}\" will run immediately. Its schedule is unchanged.",
                    ),
                    color = HermesColors.TextMuted,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { confirming = false; onTrigger(job) }
                ) { Text(S.t2("Çalıştır", "Run"), color = HermesColors.Danger) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirming = false }) {
                    Text(S.t2("Vazgeç", "Cancel"), color = HermesColors.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun CronAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (danger) HermesColors.Danger else HermesColors.Midground,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(HermesColors.Surface, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

@Composable
private fun SkillsView(state: PanelState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(state.skills, key = { it.name }) { skill ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DemoMask.name(DemoMask.Kind.SKILL, skill.name),
                        color = if (skill.enabled) HermesColors.TextPrimary else HermesColors.TextFaint,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (skill.usage > 0) {
                        Text("${skill.usage}×", color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (skill.descriptionText.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        DemoMask.description(skill.descriptionText.trim()),
                        color = HermesColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun McpView(state: PanelState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(state.mcp, key = { it.name }) { server ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(
                            if (server.enabled) HermesColors.Online else HermesColors.TextFaint,
                            MaterialTheme.shapes.extraSmall,
                        )
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        DemoMask.name(DemoMask.Kind.MCP, server.name),
                        color = HermesColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        DemoMask.text(
                            server.url ?: server.command ?: server.transport,
                        ),
                        style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize, lineHeight = MaterialTheme.typography.labelSmall.lineHeight),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                server.toolNames.size.takeIf { it > 0 }?.let { n ->
                    Text(S.t2("$n araç", "$n tools"), color = HermesColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun WebhooksView(state: PanelState) {
    val info = state.webhooks
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            if (info?.enabled == true) "Etkin" else S.t2("Kapalı", "Off"),
            color = if (info?.enabled == true) HermesColors.Online else HermesColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(info?.baseUrl.orEmpty(), style = MonoTextStyle, color = HermesColors.TextFaint)
        Spacer(Modifier.height(12.dp))
        Text(
            "${info?.subscriptions?.size ?: 0} abonelik",
            color = HermesColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PairingView(state: PanelState) {
    val info = state.pairing
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Bekleyen: ${info?.pending?.size ?: 0}", color = HermesColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text(S.t2("Onaylı: ${info?.approved?.size ?: 0}", "Approved: ${info?.approved?.size ?: 0}"), color = HermesColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        Text(
            "Eşleştirme onayı panodan yapılır — telefondan onay vermek, " +
                "cihazı yalnız telefonun elinde olmasıyla yetkilendirmek olurdu.",
            color = HermesColors.TextFaint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Uzun ham metin — günlükler ve yapılandırma için. */
@Composable
private fun MonoBlock(text: String, reverse: Boolean = false) {
    val lines = remember(text) { text.trimEnd().lines() }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        reverseLayout = reverse,
    ) {
        items(if (reverse) lines.asReversed() else lines) { line ->
            Text(
                line,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    line.contains(" ERROR") || line.contains("Traceback") -> HermesColors.Danger
                    line.contains(" WARN") -> HermesColors.Busy
                    else -> HermesColors.TextMuted
                },
            )
        }
    }
}
