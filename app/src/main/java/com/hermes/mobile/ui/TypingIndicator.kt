package com.hermes.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hermes.mobile.ChatItem
import com.hermes.mobile.ui.theme.HermesColors

/**
 * TUR-29A madde 4 — AITypingIndicator benzeri durum göstergesi.
 *
 * Ajan meşgul AMA hiçbir şey akmıyorsa (araç çağrısı sürüyor, ilk token
 * bekleniyor) üç nokta nabız atar; assistant/thinking akışı başlayınca
 * imleç zaten var olduğundan gösterge çekilir. Onay/klasifiye bekleme
 * agentBusy=false yaptığı için orada görünmez (kullanıcıya söz sırası).
 */

/** Saf karar — JVM testi: [TypingIndicatorLogicTest]. */
internal fun typingIndicatorVisible(agentBusy: Boolean, items: List<ChatItem>): Boolean =
    agentBusy && items.none {
        (it is ChatItem.Assistant && it.streaming) || (it is ChatItem.Thinking && it.live)
    }

/** Üç noktalı nabız: sıralı alfa dalgası; hareket azaltma açıksa statik. */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier, label: String? = null) {
    val reduced = rememberPrefersReducedMotion()
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing)),
        label = "phase",
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { dot ->
            // Faz 0..3 sürekli akar; her nokta kendi fazına yakınlıkla parlar.
            val distance = kotlin.math.abs(phase - dot)
            val wave = (1f - (distance % 3f) / 3f)
            val alpha = if (reduced) 0.45f else 0.3f + 0.7f * wave
            androidx.compose.foundation.layout.Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(HermesColors.Midground.copy(alpha = alpha)),
            )
        }
        if (label != null) {
            Text(
                label,
                color = HermesColors.TextFaint,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
