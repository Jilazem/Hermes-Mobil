package com.hermes.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.data.JarvisIdentity
import com.hermes.mobile.data.JarvisLoopLogic
import com.hermes.mobile.ui.theme.HermesColors

/**
 * JARVIS-2 tam ekran sesli mod — tur25.1 "KITT" düzeni.
 *
 * Knight Rider KITT'i gibi: en üstte ileri-geri KAYAN kırmızı tarama şeridi
 * ("KITT barı"). Dinlemede ekranda SADECE şerit kalır (gizlilik: ne okunduğu
 * görünmez; dikkat dağılmaz). Diğer fazlarda şerit + kayan altyazı (sen/KITT).
 * Ana ekran tamamen kapalı (siyah); geri hareketi kapatır.
 *
 * Saf kararlar [kittBodyVisible]; bu dosya yalnız çizer.
 */

/** Dinleme/Kapalı'da gövde gizlenir — ekranda sadece KITT şeridi kalır. */
fun kittBodyVisible(p: JarvisLoopLogic.Phase): Boolean =
    p != JarvisLoopLogic.Phase.Listening && p != JarvisLoopLogic.Phase.Off

@Composable
fun JarvisScreen(
    phase: JarvisLoopLogic.Phase,
    level: Float,
    captions: List<JarvisLoopLogic.Caption>,
    langT: (String, String) -> String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Yeni altyazı gelince en alta kaydır (kayan akış hissi).
    LaunchedEffect(captions.size) {
        if (captions.isNotEmpty()) listState.animateScrollToItem(captions.lastIndex)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000)),
    ) {
        // En üstte: KITT şeridi (Knight Rider tarama bandı).
        KittBar(
            level = level,
            phaseLabel = kittStatus(phase, langT),
            langT = langT,
            onStop = onStop,
        )

        if (kittBodyVisible(phase)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(captions) { c ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            if (c.role == "user") langT("Sen", "You") else JarvisIdentity.NAME,
                            color = if (c.role == "user") HermesColors.TextFaint else KittRed,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            c.text,
                            color = if (c.role == "user") HermesColors.TextPrimary else HermesColors.TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/** Faz → şerit altındaki kısa durum metni. */
fun kittStatus(p: JarvisLoopLogic.Phase, langT: (String, String) -> String): String = when (p) {
    JarvisLoopLogic.Phase.Listening -> langT("Dinliyor…", "Listening…")
    JarvisLoopLogic.Phase.Stt -> langT("Yazıyor…", "Transcribing…")
    JarvisLoopLogic.Phase.WaitReply -> langT("Düşünüyor…", "Thinking…")
    JarvisLoopLogic.Phase.Speaking -> langT("Konuşuyor…", "Speaking…")
    JarvisLoopLogic.Phase.Off -> langT("Kapalı", "Off")
}

/**
 * KITT şeridi — Knight Knight Rider'daki tarama bandı: siyah zeminde soldan
 * sağa ileri-geri süzülen kırmızı parlama + "KITT" etiketi + Durdur.
 * [level] konuşurken parlama genişliğini büyütür (0..1).
 */
@Composable
private fun KittBar(
    level: Float,
    phaseLabel: String,
    langT: (String, String) -> String,
    onStop: () -> Unit,
) {
    // İleri-geri tarama: 0→1→0, ~1.4 sn tur.
    val scan by rememberInfiniteTransition(label = "kitt").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kitt-scan",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "KITT",
            color = KittRed,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
        )
        Spacer(Modifier.width(10.dp))

        // Taranan bant
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(16.dp)
                .background(Color(0xFF0B0B0D), RoundedCornerShape(3.dp)),
        ) {
            val glowW = 0.30f + 0.18f * level.coerceIn(0f, 1f)
            val shift = maxWidth * (1f - glowW) * scan
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = shift)
                    .fillMaxHeight()
                    .width(maxWidth * glowW)
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.5f to KittRed,
                            1f to Color.Transparent,
                        )
                    ),
            )
        }

        Spacer(Modifier.width(10.dp))
        Text(
            phaseLabel,
            color = HermesColors.TextFaint,
            fontSize = 11.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onStop) {
            Text(
                langT("Durdur", "Stop"),
                color = Color(0xFFFF6B6B),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Knight Rider kırmızısı — KITT tarama bandı rengi. */
private val KittRed = Color(0xFFFF2A1A)