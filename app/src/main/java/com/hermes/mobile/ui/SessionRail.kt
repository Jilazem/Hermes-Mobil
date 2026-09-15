package com.hermes.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
 * Oturum rayı: sohbet ekranının solunda 48dp'lik kalıcı ikon şeridi.
 *
 * Amaç oturumlar arası geçişi panel açmadan bir dokunuşa indirmek. Ray iki
 * grup taşır: yeni sohbet (`onNewChat` — "+" hücresi) ve AÇIK oturumlar.
 *
 * Tur-8 düzeltmesi: ray artık yalnız `working` oturumları değil, sunucunun
 * AÇIK saydığı TÜM oturumları (idle dahil) gösterir ve uygulama içi "son
 * açılanlar" ile birleştirir. Eski süzgeç (`isWorking || isWaiting ||
 * isStarting || current`) yüzünden 2. oturuma geçildiği anda 1. oturum
 * düşüyordu: üst başlık "4 açık oturum" derken ray tek hücre kalıyordu.
 * Birleştirme/dedupe/sıra/tavan kararı `railEntries` (SessionRailLogic.kt)
 * içinde ve birim testlidir.
 *
 * Hücre kapanışı: UZUN BAS → hücre ray'dan iner (kullanıcı kapatana kadar).
 * Sunucu oturumu kapatırsa (açık listesinden düşerse) hücre kendiliğinden
 * iner; geçerli oturum ise her koşulda görünür.
 */
@Composable
fun SessionRail(
    currentSessionId: String?,
    live: LiveState,
    onNewChat: () -> Unit,
    onSelect: (RailEntry) -> Unit,
    /** Uzun basma: hücreyi ray'dan indirir (kullanıcı kapatana kadar). */
    onDismiss: (RailEntry) -> Unit = {},
    /** Uygulama içi son açılanlar (en yeni önce) — ChatViewModel tutar. */
    recent: List<RecentRailSession> = emptyList(),
    /** Kullanıcının kapattığı hücre anahtarları. */
    dismissed: Set<String> = emptySet(),
    /** `active_list` en az bir kez başarıyla geldi mi (kanıt yoksa "kapandı"
     *  hükmü verilmez). */
    liveLoaded: Boolean = true,
    /** FR-001: hücre etiketi/erişilebilirlik adı ham id OLMAMALI — çağıran
     *  `liveSessionTitle` zincirini geçirir; boş gelirse "?" gösterilir. */
    titleOf: (LiveSession) -> String = { it.title },
    modifier: Modifier = Modifier,
) {
    // Tur-8: birleşim + dedupe (liveId VE dbId) + sıra + tavan tek saf
    // fonksiyonda (test: SessionRailTur8Test).
    val shown = railEntries(
        live = live.sessions,
        recent = recent,
        currentSessionId = currentSessionId,
        liveLoaded = liveLoaded,
        dismissed = dismissed,
        titleOf = titleOf,
    )
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
            onLongClick = null,
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
                RailItem(
                    selected = s.current,
                    working = s.isWorking,
                    waiting = s.isWaiting || s.isStarting,
                    label = railLabel(s.title),
                    contentDescription = s.title,
                    onClick = { onSelect(s) },
                    onLongClick = { onDismiss(s) },
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
 *  bölgesi tüm 48dp'yi kapsar. Uzun basma hücreyi ray'dan indirir (yeni sohbet
 *  "+" hücresinde uzun basma yok). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailItem(
    selected: Boolean,
    working: Boolean,
    label: String,
    contentDescription: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    waiting: Boolean = false,
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
            // combinedClickable: uzun basma = hücreyi kapat (tur-8).
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onLongClick = onLongClick,
                        onClick = onClick,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onClick() }
                },
            )
            .padding(4.dp)
            .clip(shape)
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surface
                },
            )
            .then(
                if (working || waiting) {
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
                    waiting -> MaterialTheme.colorScheme.tertiary
                    selected -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}