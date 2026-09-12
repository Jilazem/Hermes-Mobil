package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.AppState
import com.hermes.mobile.LiveState
import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.InterventionKind
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Oturumlar — şu an çalışanlar ve geçmiş, tek sekmede.
 *
 * Önceden "Canlı" ve "Geçmiş" ayrı sekmelerdeydi. İkisi de oturum listesi;
 * ayrı durmaları alt çubuğu yedi sekmeye çıkarıyor ve kullanıcıyı "hangisine
 * bakayım" sorusuyla bırakıyordu. Üstteki iki düğme aynı işi yapıyor, yer
 * kaplamadan.
 *
 * Sıralama bilinçli: **çalışanlar önce**. Müdahale etmek istediğin oturum
 * neredeyse her zaman şu an çalışan olandır.
 */
@Composable
fun WorkScreen(
    state: AppState,
    live: LiveState,
    onRefreshLive: () -> Unit,
    onIntervene: (LiveSession) -> Unit,
    onInterrupt: (LiveSession) -> Unit,
    onOpenLive: (LiveSession) -> Unit,
    onContinueLive: (LiveSession) -> Unit,
    onCloseIntervention: () -> Unit,
    onSubmitIntervention: (LiveSession, InterventionKind, String) -> Unit,
    onOpenPast: (HermesSession) -> Unit,
    onContinuePast: (HermesSession) -> Unit,
    onRefreshSessions: () -> Unit = {},
    onTogglePin: (String) -> Unit = {},
    onSetArchived: (String, Boolean) -> Unit = { _, _ -> },
    onRenamePast: (String, String) -> Unit = { _, _ -> },
    onDeletePast: (String) -> Unit = {},
    /** Oturum eylem menüsü: '/stop' slashExec. */
    onStopPast: (HermesSession) -> Unit = {},
    /** Oturum eylem menüsü: '/compress' slashExec. */
    onBudaPast: (HermesSession) -> Unit = {},
) {
    var showLive by remember { mutableStateOf(true) }
    val runningCount = live.sessions.size

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        ) {
            Text(
                S.t2("Oturumlar", "Sessions"),
                color = HermesColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Tab(
                label = if (runningCount > 0) S.t2("Çalışan ($runningCount)", "Working ($runningCount)")
                    else S.t2("Çalışan", "Working"),
                selected = showLive,
                modifier = Modifier.weight(1f),
            ) { showLive = true }
            Tab(
                label = S.t2("Geçmiş", "History"),
                selected = !showLive,
                modifier = Modifier.weight(1f),
            ) { showLive = false }
        }

        Box(Modifier.weight(1f)) {
            if (showLive) {
                LiveSessionsScreen(
                    state = live,
                    onRefresh = onRefreshLive,
                    onIntervene = onIntervene,
                    onInterrupt = onInterrupt,
                    onOpen = onOpenLive,
                    onContinue = onContinueLive,
                    onCloseIntervention = onCloseIntervention,
                    onSubmitIntervention = onSubmitIntervention,
                )
            } else {
                SessionsScreen(
                    state = state,
                    onOpen = onOpenPast,
                    onContinue = onContinuePast,
                    onTogglePin = onTogglePin,
                    onSetArchived = onSetArchived,
                    onRename = onRenamePast,
                    onDelete = onDeletePast,
                    onRefreshSessions = onRefreshSessions,
                    onStop = onStopPast,
                    onBuda = onBudaPast,
                )
            }
        }
    }
}

@Composable
private fun Tab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .background(
                if (selected) HermesColors.Midground else HermesColors.SurfaceDim,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            color = if (selected) HermesColors.Background else HermesColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
