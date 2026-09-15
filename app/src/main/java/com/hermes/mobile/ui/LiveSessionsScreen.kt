package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle
import com.hermes.mobile.data.DemoMask

/**
 * Canlı oturumlar — gateway'de şu an çalışan ajanlar.
 *
 * Telegram'dan gelen bir istek, gece koşan bir cron, açık bir CLI oturumu…
 * hepsi burada görünür ve **çalışırken** müdahale edilebilir.
 *
 * Başlık çözümü Oturumlar listesiyle AYNI saf fonksiyondan geçer
 * (`liveSessionTitle` → `readableTitle`): ham süreç içi id asla başlık olmaz.
 */
@Composable
fun LiveSessionsScreen(
    state: LiveState,
    restSessions: List<HermesSession> = emptyList(),
    flags: SessionFlags = SessionFlags(),
    cronNames: Map<String, String> = emptyMap(),
    onRefresh: () -> Unit,
    onIntervene: (LiveSession) -> Unit,
    onInterrupt: (LiveSession) -> Unit,
    onOpen: (LiveSession) -> Unit,
    onContinue: (LiveSession) -> Unit,
    onCloseIntervention: () -> Unit,
    onSubmitIntervention: (LiveSession, InterventionKind, String) -> Unit,
) {
    // Canlı oturumun REST karşılığı (kaynak etiketi + cron hash'i burada).
    val restById = remember(restSessions) { restSessions.associateBy { it.id } }
    val titleOf = remember(flags, cronNames, restById) {
        { l: LiveSession ->
            liveSessionTitle(
                l, restById[l.dbId], flags, cronNames,
                en = serviceLang == Lang.EN,
            )
        }
    }
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
                            // Tur-8: sayac ray ile AYNI kümeyi saysın diye tek
                            // kaynak — openSessionCount (SessionRailLogic.kt).
                            "${state.sessions.count { it.isWorking }} çalışıyor · " +
                                "${openSessionCount(state.sessions)} açık oturum",
                            "${state.sessions.count { it.isWorking }} working · " +
                                "${openSessionCount(state.sessions)} open sessions",
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
                title = titleOf(session),
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
            title = titleOf(target),
            sending = state.sending,
            onDismiss = onCloseIntervention,
            onSubmit = { kind, text -> onSubmitIntervention(target, kind, text) },
        )
    }
}

@Composable
private fun LiveSessionCard(
    session: LiveSession,
    title: String,
    onIntervene: () -> Unit,
    onInterrupt: () -> Unit,
    onOpen: () -> Unit,
    onContinue: () -> Unit,
) {
    // Tur-4: durum ETİKET DEĞİL SİNYAL — boşta olanın etiketi olmaz.
    val pill = statusPill(if (session.isWorking) "working" else if (session.isWaiting) "waiting"
        else if (session.isStarting) "starting" else "idle")
    val dot = when (pill?.tone) {
        StatusTone.Live -> HermesColors.Online
        StatusTone.Waiting, StatusTone.Starting -> HermesColors.Busy
        null -> HermesColors.Offline
    }
    val card = cardActions(working = session.canIntervene)
    // Önizleme: ham JSON/tool/sistem metni ASLA — makine çıktısıysa boş kalır,
    // çalışan oturumda yerine "yazıyor…" sinyali zaten üstte duruyor.
    val preview = meaningfulPreview(session.preview)

    HermesCard(
        Modifier
            .fillMaxWidth()
            .clickable { onContinue() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(dot)
            Spacer(Modifier.width(8.dp))
            Text(
                DemoMask.name(DemoMask.Kind.SESSION, title),
                color = HermesColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                formatRelative(session.lastActive),
                color = HermesColors.TextMuted,
                fontSize = 11.sp,
            )
            // "Döküm" SABİT konum (tur-4 E): her kartta aynı yerde, sağdaki ikon.
            IconButton(onClick = onOpen, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.Article,
                    contentDescription = S.t2("Döküm", "Transcript"),
                    tint = HermesColors.TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (preview.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                DemoMask.description(preview),
                color = HermesColors.TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                S.t2("${session.messageCount} mesaj", "${session.messageCount} messages"),
                color = HermesColors.TextFaint,
                fontSize = 10.sp,
            )
            Spacer(Modifier.width(8.dp))
            pill?.let {
                Text(
                    S.t2(it.labelTr, it.labelEn),
                    color = dot,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.weight(1f))
            // Çalışan oturumda TEK birincil eylem "Dur"; müdahale ikincil ikon.
            // (Eski tam genişlikli buton yığını ve kart altı kılavuz SİLİNDİ.)
            if (card.steer) {
                IconButton(onClick = onIntervene, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.PlaylistAdd,
                        contentDescription = S.t2("Müdahale", "Steer"),
                        tint = HermesColors.Midground,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (card.stop) {
                TextButton(onClick = onInterrupt, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(S.t2("Dur", "Stop"), color = HermesColors.Danger, fontSize = 12.sp)
                }
            }
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
    title: String = session.title.ifBlank { "Oturum" },
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
                    DemoMask.name(DemoMask.Kind.SESSION, title),
                    style = MonoTextStyle,
                    color = HermesColors.TextMuted,
                )
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindOption(
                        title = S.t2("Ekle", "Append"),
                        detail = S.t2("Turu kesmez", "Does not cut the turn"),
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
                                S.t2("Şu dosyayı da kontrol et…", "Also check this file…")
                            else
                                S.t2("Bunun yerine önce raporu özetle…", "Instead, summarize the report first…"),
                            color = HermesColors.TextFaint,
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (kind == InterventionKind.Add)
                        S.t2(
                            "Mesaj sonraki araç sonucuna iliştirilir; ajan bir sonraki adımında görür.",
                            "The message rides the next tool result; the agent sees it on its next step.",
                        )
                    else
                        S.t2(
                            "Süren tur yönlendirilir, yapılan iş korunur. Her ajan desteklemez — " +
                                "desteklemezse otomatik olarak eklemeye düşülür.",
                            "The running turn is redirected, finished work is kept. Not every agent " +
                                "supports it — falls back to appending.",
                        ),
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
