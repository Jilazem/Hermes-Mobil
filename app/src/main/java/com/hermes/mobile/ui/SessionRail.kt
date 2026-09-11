package com.hermes.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.LiveState
import com.hermes.mobile.data.LiveSession

/**
 * Oturum rayı: sohbet ekranının solunda 44dp'lik kalıcı ikon şeridi.
 *
 * Amaç oturumlar arası geçişi panel açmadan bir dokunuşa indirmek. Ray iki
 * grup taşır: şu anki sohbet (tek, `onNewChat` yeni oturum açar) ve gateway
 * üzerinden görülen ÇALIŞAN canlı oturumlar. Bitmiş oturumlar rayda yığılmasın
 * diye listelenmez — hepsine Oturumlar panelinden erişiliyor.
 *
 * Bildirim tepsisindeki 3-5 eşzamanlı iş gözlemi: 6+ canlı oturum pratikte
 * seyrek; sınır yine de 6 ile kesilir, fazlası Oturumlar'a bırakılır.
 */
@Composable
fun SessionRail(
    currentSessionId: String?,
    live: LiveState,
    onNewChat: () -> Unit,
    onSelect: (LiveSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    val working = live.sessions.filter { it.isWorking || it.isWaiting || it.isStarting }.take(6)
    Column(
        modifier = modifier
            .width(48.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(top = 60.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailItem(
            selected = currentSessionId == null,
            working = false,
            label = "+",
            contentDescription = null,
            onClick = onNewChat,
        )
        if (working.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .size(width = 20.dp, height = 1.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
            working.forEach { s ->
                RailItem(
                    selected = currentSessionId == s.dbId,
                    working = s.isWorking,
                    label = s.title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    contentDescription = s.title,
                    onClick = { onSelect(s) },
                )
            }
        }
    }
}

/** Ray hücresi: 48dp dokunma alanı (spec erişilebilirlik asgari), 40dp görsel
 *  hap, aktifken dolgu, çalışırken nabız. clickable padding'den ÖNCE: dokunma
 *  bölgesi tüm 48dp'yi kapsar. */
@Composable
private fun RailItem(
    selected: Boolean,
    working: Boolean,
    label: String,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "rail-pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "rail-pulse-alpha",
    )
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(4.dp)
            .clip(shape)
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surface
                },
            )
            .then(
                if (working) {
                    Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.tertiary.copy(alpha = alpha),
                        shape,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (contentDescription == null) {
            Icon(
                Icons.Rounded.ChatBubbleOutline,
                contentDescription = S.t2("Yeni sohbet", "New chat"),
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    working -> MaterialTheme.colorScheme.tertiary
                    selected -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}