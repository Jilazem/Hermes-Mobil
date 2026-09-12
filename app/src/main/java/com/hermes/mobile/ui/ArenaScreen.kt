package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.ArenaViewModel
import com.hermes.mobile.data.ArenaAnswer
import com.hermes.mobile.data.ArenaMode
import com.hermes.mobile.data.GatewayWsClient
import com.hermes.mobile.ui.MarkdownText
import com.hermes.mobile.ui.tr

/**
 * Bot Arena — 5. sekme.
 *
 * Kurulum ekranı: konu girişi, 1-4 bot çipi (profil listesi),
 * kip seçimi (Tek bot / Kapışma / Beyin fırtınası),
 * sonuç: bot kartları (ad+t/s+Markdown cevap), tur rozetleri, 'Durdur'.
 */
@Composable
fun ArenaScreen(
    arenaViewModel: ArenaViewModel,
    gateway: GatewayWsClient?,
) {
    val st by arenaViewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Başlık
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                S.t2("Bot Arena", "Bot Arena"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            when (st.phase) {
                is com.hermes.mobile.ArenaPhase.Running -> TextButton(
                    onClick = { arenaViewModel.stop() }
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        S.t2("Durdur", "Stop"),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }

                else -> TextButton(onClick = { arenaViewModel.clear() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(S.t2("Temizle", "Clear"), fontSize = 13.sp)
                }
            }
        }

        // Hata bildirimi
        st.error?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Konu girişi
        TextField(
            value = st.topic,
            onValueChange = { arenaViewModel.setTopic(it) },
            label = { Text(S.t2("Konu / görev", "Topic / task")) },
            placeholder = {
                Text(S.t2("Arena görevi — örn. \"API error rate'ı düşür\"", "Arena task — e.g. \"reduce API error rate\""))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Kip seçimi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            modeChip(ArenaMode.SINGLE, S.t2("Tek bot", "Single"), st.mode,
                { arenaViewModel.setMode(ArenaMode.SINGLE) })
            modeChip(ArenaMode.BATTLE, S.t2("Kapışma", "Battle"), st.mode,
                { arenaViewModel.setMode(ArenaMode.BATTLE) })
            modeChip(ArenaMode.BRAINSTORM, S.t2("Beyin fırtınası", "Brainstorm"), st.mode,
                { arenaViewModel.setMode(ArenaMode.BRAINSTORM) })
        }

        // Bot çipleri
        Text(
            S.t2("Botlar (maks 4)", "Bots (max 4)"),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val profiles = st.profiles
        if (profiles.isEmpty()) {
            Text(
                S.t2("Profil yüklenemedi — gateway bağlı değil",
                    "Profiles not loaded — gateway not connected"),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                profiles.take(8).forEach { p ->
                    val selected = p.name in st.selectedProfiles
                    val canAdd = selected || st.selectedProfiles.size < 4
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .border(
                                width = 1.dp,
                                color = if (selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .background(
                                if (selected)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else
                                    MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable(enabled = canAdd) {
                                arenaViewModel.toggleProfile(p.name)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (selected) Icons.Default.AutoAwesome
                                else Icons.Default.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(p.name, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Başlat düğmesi
        OutlinedButton(
            onClick = { arenaViewModel.startArena(gateway) },
            enabled = st.phase !is com.hermes.mobile.ArenaPhase.Running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(S.t2("Arena'yı Başlat", "Start Arena"))
        }

        // Sonuç kartları
        when (val phase = st.phase) {
            is com.hermes.mobile.ArenaPhase.Running -> {
                Text(
                    S.t2("Botlar çalışıyor…", "Bots running…"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is com.hermes.mobile.ArenaPhase.Done -> {
                val allAnswers: List<ArenaAnswer> =
                    phase.answers + listOfNotNull(
                        phase.synthesisAnswer?.let { it.copy(bot = "[Sentez]", round = 3) }
                    )
                if (allAnswers.isEmpty()) {
                    Text(
                        S.t2("Cevap yok — durduruldu veya hata oluştu.",
                            "No answers — stopped or error."),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        items(allAnswers) { ans -> BotCard(ans) }
                    }
                }
            }

            else -> {}
        }
    }
}

@Composable
private fun RowScope.modeChip(
    mode: ArenaMode,
    label: String,
    active: ArenaMode,
    onClick: () -> Unit,
) {
    val isActive = mode == active
    Box(
        modifier = Modifier
            .height(32.dp)
            .border(
                width = 1.dp,
                color = if (isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .background(
                if (isActive)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Bot kartı: ad, tur rozeti, Markdown cevap. */
@Composable
private fun BotCard(ans: ArenaAnswer) {
    val roundLabel = when (ans.round) {
        1 -> tr("Tur 1", "Round 1")
        2 -> if (ans.bot == "[Sentez]") tr("Sentez", "Synthesis")
        else tr("Tur 2", "Round 2")
        else -> tr("Tur ${ans.round}", "Round ${ans.round}")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp),
            )
            .background(
                MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                ans.bot,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(6.dp))
            // Tur rozeti
            Box(
                modifier = Modifier
                    .background(
                        color = when (ans.round) {
                            1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            2 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        },
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(roundLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.weight(1f).width(0.dp))
            if (!ans.ok) {
                Text(
                    "Hata: " + (ans.error ?: "?"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        MarkdownText(ans.text)
    }
}
