package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.AppState
import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle

@Composable
fun SessionsScreen(
    state: AppState,
    onOpen: (HermesSession) -> Unit,
    onContinue: (HermesSession) -> Unit = {},
) {
    val sessions = state.sessions.sortedByDescending { it.startedAt ?: 0.0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Oturumlar", color = HermesColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${sessions.count { it.isActive }} etkin · ${sessions.size} toplam",
                        color = HermesColors.TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        if (sessions.isEmpty()) {
            item {
                HermesCard(Modifier.fillMaxWidth()) {
                    Text(
                        if (state.isConnected) "Henüz oturum yok." else "Sunucuya bağlanınca oturumlar burada listelenir.",
                        color = HermesColors.TextMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        items(sessions, key = { it.id }) { session ->
            SessionRow(
                session,
                onClick = { onOpen(session) },
                onContinue = { onContinue(session) },
            )
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SessionRow(
    session: HermesSession,
    onClick: () -> Unit,
    onContinue: () -> Unit = {},
) {
    HermesCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (session.isActive) HermesColors.Online else HermesColors.Offline, size = 7)
            Spacer(Modifier.width(8.dp))
            Text(
                session.title,
                color = HermesColors.TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(formatRelative(session.startedAt), color = HermesColors.TextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        session.model?.let {
            Text(it, style = MonoTextStyle, color = HermesColors.TextMuted)
            Spacer(Modifier.height(4.dp))
        }
        Row {
            Meta("${session.messageCount} mesaj")
            Spacer(Modifier.width(12.dp))
            Meta("${session.toolCallCount} araç")
            Spacer(Modifier.width(12.dp))
            Meta("${(session.inputTokens + session.outputTokens) / 1000}k token")
            session.source?.let {
                Spacer(Modifier.width(12.dp))
                Meta(it)
            }
        }

        // Geçmiş salt-okunur değil artık: buradan konuşmaya devam edilebiliyor.
        Spacer(Modifier.height(9.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(HermesColors.Midground, RoundedCornerShape(8.dp))
                .clickable(onClick = onContinue)
                .padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Konuşmaya devam et", color = HermesColors.Background, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Meta(text: String) {
    Text(text, color = HermesColors.TextFaint, fontSize = 10.sp)
}
