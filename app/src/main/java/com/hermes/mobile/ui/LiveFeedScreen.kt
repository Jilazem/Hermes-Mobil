package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.data.DemoMask
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle

/**
 * Canlı akış — "Tümü" sekmesi. Telegram'daki oturum listesi gibi: her oturum
 * tek akışta, kaynağı (hangi bot/kanal) ve şu an ne yaptığıyla.
 *
 * Üstte bellekte ajanı olanlar (canlı rozetli, steer/durdurma etkin), altta
 * en yeni geçmiş kayıtları. Satıra dokunmak dökümü açar; "Devam" sohbeti bu
 * oturuma bağlar — canlı ya da geçmiş fark etmez.
 */
@Composable
fun LiveFeedScreen(
    entries: List<LiveFeedEntry>,
    flags: SessionFlags,
    cronNames: Map<String, String>,
    onRefresh: () -> Unit,
    onIntervene: (LiveFeedEntry) -> Unit,
    onInterrupt: (LiveFeedEntry) -> Unit,
    onOpen: (LiveFeedEntry) -> Unit,
    onContinue: (LiveFeedEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        S.t2("Tüm akış", "All activity"),
                        color = HermesColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        S.t2(
                            "${entries.count { it.live }} canlı · ${entries.size} oturum",
                            "${entries.count { it.live }} live · ${entries.size} sessions",
                        ),
                        color = HermesColors.TextMuted,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, S.t2("Yenile", "Refresh"), tint = HermesColors.Midground)
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                HermesCard(Modifier.fillMaxWidth()) {
                    Text(
                        S.t2("Henüz oturum yok.", "No sessions yet."),
                        color = HermesColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        items(entries, key = { it.dbId }) { e ->
            LiveFeedRow(
                entry = e,
                // Canlı satır: liveSessionTitle (REST karşılığı + rename + cron
                // dahil) zincirinden; geçmiş satır: readableTitle. İkisi aynı
                // mantık — ham id hiçbir satırda başlık olmaz.
                title = e.liveSession?.let {
                    liveSessionTitle(it, e.pastSession, flags, cronNames)
                } ?: e.pastSession?.let { readableTitle(it, flags, cronNames) }
                    ?: "Oturum",
                onIntervene = { onIntervene(e) },
                onInterrupt = { onInterrupt(e) },
                onOpen = { onOpen(e) },
                onContinue = { onContinue(e) },
            )
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun LiveFeedRow(
    entry: LiveFeedEntry,
    title: String,
    onIntervene: () -> Unit,
    onInterrupt: () -> Unit,
    onOpen: () -> Unit,
    onContinue: () -> Unit,
) {
    val (dot, statusLabel) = when (entry.status) {
        "working" -> HermesColors.Online to S.t2("çalışıyor", "working")
        "waiting" -> HermesColors.Busy to S.t2("yanıt bekliyor", "awaiting reply")
        "starting" -> HermesColors.Busy to S.t2("başlıyor", "starting")
        "idle" -> HermesColors.Offline to S.t2("boşta", "idle")
        else -> HermesColors.TextFaint to S.t2("bitti", "done")
    }
    HermesCard(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(dot)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    DemoMask.name(DemoMask.Kind.SESSION, title),
                    color = HermesColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                // "hangi bot hangi oturumda" — kaynak rozeti + canlılık.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        feedSourceLabel(entry.source),
                        style = MonoTextStyle,
                        color = HermesColors.TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .background(HermesColors.SurfaceDim, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                    if (entry.live) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            S.t2("canlı", "live"),
                            color = HermesColors.Online,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Text(statusLabel, color = dot, fontSize = 11.sp)
        }

        if (entry.preview.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                DemoMask.description(entry.preview),
                color = HermesColors.TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedActionChip(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = S.t2("Devam", "Continue"),
                enabled = true,
                primary = true,
                onClick = onContinue,
                modifier = Modifier.weight(1f),
            )
            if (entry.live) {
                FeedActionChip(
                    icon = Icons.Default.PlaylistAdd,
                    label = S.t2("Müdahale", "Steer"),
                    enabled = entry.status != "idle",
                    onClick = onIntervene,
                    modifier = Modifier.weight(1f),
                )
                FeedActionChip(
                    icon = Icons.Default.Stop,
                    label = S.t2("Dur", "Stop"),
                    enabled = entry.status != "idle",
                    danger = true,
                    onClick = onInterrupt,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** LiveSessionsScreen'in private ActionChip'i — aynı görünüm, bu dosyada kopya. */
@Composable
private fun FeedActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    primary: Boolean = false,
) {
    val tint = when {
        !enabled -> HermesColors.TextFaint
        primary -> HermesColors.Background
        danger -> HermesColors.Danger
        else -> HermesColors.Midground
    }
    Row(
        modifier
            .background(
                if (primary) HermesColors.Midground else HermesColors.SurfaceDim,
                RoundedCornerShape(9.dp),
            )
            .border(1.dp, HermesColors.Border, RoundedCornerShape(9.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, fontSize = 11.sp)
    }
}
