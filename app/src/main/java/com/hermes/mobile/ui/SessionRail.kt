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
    /** FR-001: hücre etiketi/erişilebilirlik adı ham id OLMAMALI — çağıran
     *  `liveSessionTitle` zincirini geçirir; boş gelirse "?" gösterilir. */
    titleOf: (LiveSession) -> String = { it.title },
    modifier: Modifier = Modifier,
) {
    // Ray ÇALIŞAN oturumları gösterir; bitmişleri listelemez. Tek istisna:
    // kullanıcı şu an bir idle oturuma bağlıysa (ChatViewModel'in tuttuğu
    // süreç içi id ya da dbId eşleşmesi) o hücre rayda kalmalı — yoksa aktif
    // konuşmanın kabarcığı bir anda kayboluyor.
    val shown = live.sessions.filter { s ->
        s.isWorking || s.isWaiting || s.isStarting ||
            (currentSessionId != null && (currentSessionId == s.id || currentSessionId == s.dbId))
    }.take(6)
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
        if (shown.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .size(width = 20.dp, height = 1.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
            shown.forEach { s ->
                // FR-001: etiket ve erişilebilirlik adı çözümlenmiş başlıktan
                // gelir (titleOf = liveSessionTitle zinciri) — ham süreç içi id
                // ya da dbId asla ray etiketi olmaz.
                val resolved = titleOf(s)
                RailItem(
                    // ChatViewModel sessionId olarak gateway'in SÜREÇ İÇİ id'sini
                    // tutuyor; dbId (session key) ayrı. İkisine göre de kıyasla —
                    // tek kıyas yanıltıcı seçime yol açıyordu.
                    selected = currentSessionId != null &&
                        (currentSessionId == s.id || currentSessionId == s.dbId),
                    working = s.isWorking,
                    label = railLabel(resolved),
                    contentDescription = resolved,
                    onClick = { onSelect(s) },
                )
            }
        }
    }
}

/** Ray etiketi: başlığın ilk İKİ karakteri, büyük harf. Boş başlık → "?".
 *  `String.uppercase()` cihazın yerel ayarından etkilenip Türkçe 'i' tuzağına
 *  düşebiliyor; karakter bazlı `Char.uppercaseChar()` (Character.uppercaseChar)
 *  yerel ayardan bağımsız tek anlamlı eşlemeyi verir.
 *  FR-001 savunması: çözüm başarısız olup ham oturum id'si (20260913_184051_52f76a
 *  veya cron_<hash>_) sızarsa "20"/"CR" gibi anlamsız etiket yerine "?" gösterilir. */
internal fun railLabel(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "?"
    if (RAW_SESSION_ID.matches(trimmed)) return "?"
    return trimmed.take(2).map { it.uppercaseChar() }.joinToString("")
}

/** Ham süreç içi/cron oturum id kalıbı (readableTitle'ın çözdüğü desenler):
 *  20260913_184051_52f76a · 20260913_184051 · cron_<hex> · cron_<hex>_<tarih>_<saat>. */
private val RAW_SESSION_ID = Regex(
    """^(?:\d{8}_\d{6}(?:_[0-9a-f]+)?|cron_[0-9a-f]{4,}(?:_\d{8}_\d{6})?)$""",
    RegexOption.IGNORE_CASE,
)

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