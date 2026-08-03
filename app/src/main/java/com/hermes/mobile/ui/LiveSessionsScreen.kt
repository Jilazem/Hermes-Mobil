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
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.InterventionKind
import com.hermes.mobile.LiveState
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle
import com.hermes.mobile.data.DemoMask

/**
 * Canlı oturumlar — gateway'de şu an çalışan ajanlar.
 *
 * Telegram'dan gelen bir istek, gece koşan bir cron, açık bir CLI oturumu…
 * hepsi burada görünür ve **çalışırken** müdahale edilebilir.
 */
@Composable
fun LiveSessionsScreen(
    state: LiveState,
    onRefresh: () -> Unit,
    onIntervene: (LiveSession) -> Unit,
    onInterrupt: (LiveSession) -> Unit,
    onOpen: (LiveSession) -> Unit,
    onContinue: (LiveSession) -> Unit,
    onCloseIntervention: () -> Unit,
    onSubmitIntervention: (LiveSession, InterventionKind, String) -> Unit,
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
                        S.t2("Canlı", "Live"),
                        color = HermesColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        S.t2(
                            "${state.sessions.count { it.isWorking }} çalışıyor · " +
                                "${state.sessions.size} açık oturum",
                            "${state.sessions.count { it.isWorking }} working · " +
                                "${state.sessions.size} open sessions",
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

        state.error?.let { err ->
            item {
                HermesCard(Modifier.fillMaxWidth()) {
                    Text(err, color = HermesColors.Danger, fontSize = 12.sp)
                }
            }
        }

        if (state.sessions.isEmpty() && state.error == null) {
            item {
                HermesCard(Modifier.fillMaxWidth()) {
                    Text(
                        S.t2("Şu an canlı oturum yok.", "No live sessions right now."),
                        color = HermesColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        S.t2(
                            "Telegram'dan, cron'dan ya da CLI'dan bir ajan çalışmaya " +
                                "başladığında burada görünür — ve çalışırken ona " +
                                "müdahale edebilirsin.",
                            "When an agent starts working — from Telegram, a cron job " +
                                "or the CLI — it shows up here, and you can steer it " +
                                "while it runs.",
                        ),
                        color = HermesColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }

        items(state.sessions, key = { it.id }) { session ->
            LiveSessionCard(
                session = session,
                onIntervene = { onIntervene(session) },
                onInterrupt = { onInterrupt(session) },
                onOpen = { onOpen(session) },
                onContinue = { onContinue(session) },
            )
        }

        item { Spacer(Modifier.height(20.dp)) }
    }

    state.intervening?.let { target ->
        InterventionDialog(
            session = target,
            sending = state.sending,
            onDismiss = onCloseIntervention,
            onSubmit = { kind, text -> onSubmitIntervention(target, kind, text) },
        )
    }
}

@Composable
private fun LiveSessionCard(
    session: LiveSession,
    onIntervene: () -> Unit,
    onInterrupt: () -> Unit,
    onOpen: () -> Unit,
    onContinue: () -> Unit,
) {
    val (dot, statusLabel) = when {
        session.isWorking -> HermesColors.Online to S.t2("çalışıyor", "working")
        session.isWaiting -> HermesColors.Busy to S.t2("yanıt bekliyor", "awaiting reply")
        session.isStarting -> HermesColors.Busy to S.t2("başlıyor", "starting")
        else -> HermesColors.Offline to S.t2("boşta", "idle")
    }

    HermesCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(dot)
            Spacer(Modifier.width(8.dp))
            Text(
                DemoMask.name(DemoMask.Kind.SESSION, session.title.ifBlank { session.id }),
                color = HermesColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(statusLabel, color = dot, fontSize = 11.sp)
        }

        if (session.preview.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                // Önizleme oturumun ilk mesajı — tanıtım kipinde en çok bilgi
                // sızdıran yer burası.
                DemoMask.description(session.preview),
                color = HermesColors.TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(6.dp))
        Row {
            Text(
                DemoMask.model(session.model),
                style = MonoTextStyle,
                color = HermesColors.TextFaint,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                S.t2(
                    "${session.messageCount} mesaj · ${formatRelative(session.lastActive)}",
                    "${session.messageCount} messages · ${formatRelative(session.lastActive)}",
                ),
                color = HermesColors.TextFaint,
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.height(11.dp))

        // Devam et her zaman kullanılabilir — asıl istenen bu: Telegram'dan
        // ya da cron'dan başlamış konuşmayı telefondan sürdürmek.
        ActionChip(
            icon = Icons.AutoMirrored.Filled.Chat,
            label = S.t2("Konuşmaya devam et", "Continue the conversation"),
            enabled = true,
            primary = true,
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip(
                icon = Icons.Default.PlaylistAdd,
                label = S.t2("Müdahale", "Steer"),
                enabled = session.canIntervene,
                onClick = onIntervene,
                modifier = Modifier.weight(1f),
            )
            ActionChip(
                icon = Icons.Default.Article,
                label = S.t2("Döküm", "Transcript"),
                enabled = true,
                onClick = onOpen,
                modifier = Modifier.weight(1f),
            )
            ActionChip(
                icon = Icons.Default.Stop,
                label = S.t2("Dur", "Stop"),
                enabled = session.canIntervene,
                danger = true,
                onClick = onInterrupt,
                modifier = Modifier.weight(1f),
            )
        }

        if (!session.canIntervene) {
            Spacer(Modifier.height(5.dp))
            Text(
                S.t2(
                    "Müdahale/durdurma yalnız ajan çalışırken anlamlı — " +
                        "boştaki oturuma normal mesaj yazarak devam edebilirsin.",
                    "Steering and stopping only apply while the agent is working — " +
                        "for an idle session, just send a normal message.",
                ),
                color = HermesColors.TextFaint,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun ActionChip(
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


/**
 * Müdahale sayfası — iki farklı sunucu davranışı arasında açık seçim yaptırır.
 *
 * "Ekle" (`steer`) turu bölmez, ajan bir sonraki iterasyonda görür.
 * S.t2("Yönlendir", "Redirect") (`redirect`) süren turu değiştirir; her ajan desteklemez.
 */
@Composable
fun InterventionDialog(
    session: LiveSession,
    sending: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (InterventionKind, String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(InterventionKind.Add) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HermesColors.Surface,
        titleContentColor = HermesColors.TextPrimary,
        textContentColor = HermesColors.TextSecondary,
        title = { Text(S.t2("Çalışan ajana müdahale", "Steer the running agent")) },
        text = {
            Column {
                Text(
                    DemoMask.name(
                        DemoMask.Kind.SESSION,
                        session.title.ifBlank { session.id },
                    ),
                    style = MonoTextStyle,
                    color = HermesColors.TextMuted,
                )
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindOption(
                        title = "Ekle",
                        detail = "Turu kesmez",
                        selected = kind == InterventionKind.Add,
                        onClick = { kind = InterventionKind.Add },
                        modifier = Modifier.weight(1f),
                    )
                    KindOption(
                        title = S.t2("Yönlendir", "Redirect"),
                        detail = S.t2("Turu değiştirir", "Changes the current turn"),
                        selected = kind == InterventionKind.Redirect,
                        onClick = { kind = InterventionKind.Redirect },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            if (kind == InterventionKind.Add)
                                "Şu dosyayı da kontrol et…"
                            else
                                "Bunun yerine önce raporu özetle…",
                            color = HermesColors.TextFaint,
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (kind == InterventionKind.Add)
                        "Mesaj sonraki araç sonucuna iliştirilir; ajan bir sonraki adımında görür."
                    else
                        "Süren tur yönlendirilir, yapılan iş korunur. Her ajan desteklemez — " +
                            "desteklemezse otomatik olarak eklemeye düşülür.",
                    color = HermesColors.TextFaint,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && !sending,
                onClick = { onSubmit(kind, text) },
            ) {
                Text(
                    if (sending) S.t2("Gönderiliyor…", "Sending…") else S.t2("Gönder", "Send"),
                    color = HermesColors.Midground,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.t2("Vazgeç", "Cancel")) } },
    )
}

@Composable
private fun KindOption(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(
                if (selected) HermesColors.SurfaceDim else HermesColors.Surface,
                RoundedCornerShape(9.dp),
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) HermesColors.Midground else HermesColors.Border,
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(
            title,
            color = if (selected) HermesColors.Midground else HermesColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(detail, color = HermesColors.TextFaint, fontSize = 10.sp)
    }
}
