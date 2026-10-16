package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.ArtemisTaskTracker
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.data.PhoneBridgeService
import com.hermes.mobile.ui.theme.HermesColors

/**
 * K2 — "Canlı" envanter kartı: sohbet oturumları + köprü WS + Artemis görevleri
 * tek görünümde ("ne çalışıyor?"). Çekmecenin Canlı sekmesinin en üstünde
 * durur; satırlar [liveInventory] ile üretilir, durum noktaları
 * [drawerStatusDot] renk semantiğindendir.
 */
@Composable
fun LiveInventoryCard(
    liveSessions: List<LiveSession>,
    liveTitleOf: (LiveSession) -> String,
    modifier: Modifier = Modifier,
) {
    val bridge by PhoneBridgeService.stateFlow.collectAsState()
    val tasks by ArtemisTaskTracker.tasks.collectAsState()
    val rows = liveInventory(bridge, liveSessions, tasks, liveTitleOf)

    Column(
        modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            S.t2("Canlı — ne çalışıyor", "Live — what is running"),
            color = HermesColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.size(4.dp))
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InventoryDot(row.dot)
                Spacer(Modifier.width(8.dp))
                Text(
                    row.label,
                    color = HermesColors.TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(110.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    row.detail,
                    color = HermesColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** drawerStatusDot renk adı → görsel nokta ("" = bilinmiyor, çizilmez). */
@Composable
private fun InventoryDot(dot: String) {
    val color = when (dot) {
        "green" -> HermesColors.Online
        "yellow" -> HermesColors.Busy
        "grey" -> HermesColors.Offline
        else -> return
    }
    Spacer(Modifier.size(7.dp).clip(CircleShape).background(color))
}