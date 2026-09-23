package com.hermes.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.data.JarvisIdentity
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.data.JarvisLoopLogic
import com.hermes.mobile.data.JarvisPhase

/**
 * JARVIS-2 (tur24) — TAM EKRAN Jarvis modu (görev maddesi 2).
 *
 * Merkezde JARVIS-1 reaktörü (faz + level), altında kayan altyazı (sen +
 * Jarvis), üstte durum noktası + Durdur düğmesi. Geri hareketi ekranı
 * KAPATIR — çağıran ViewModel.closeJarvisMode ile mikrofonu da bırakır
 * (yarıda kapatma güvenli, kayıt iptal — controller tarafında garanti).
 *
 * Saf kararlar burada DEĞİL: faz haritası [fromLoopPhase], durum noktası
 * metni [JarvisLoopLogic.phaseLabel]. Bu dosya yalnız çizer.
 */

/** Döngü fazından Jarvis reaktör fazı — total; yeni faz eklenirse test yakalar. */
fun fromLoopPhase(p: JarvisLoopLogic.Phase): JarvisPhase = when (p) {
    JarvisLoopLogic.Phase.Listening -> JarvisPhase.Listening
    JarvisLoopLogic.Phase.Stt -> JarvisPhase.Thinking
    JarvisLoopLogic.Phase.WaitReply -> JarvisPhase.Thinking
    JarvisLoopLogic.Phase.Speaking -> JarvisPhase.Speaking
    JarvisLoopLogic.Phase.Off -> JarvisPhase.Idle
}

@Composable
fun JarvisScreen(
    phase: JarvisLoopLogic.Phase,
    level: Float,
    captions: List<JarvisLoopLogic.Caption>,
    langT: (String, String) -> String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jPhase = fromLoopPhase(phase)
    val listState = rememberLazyListState()

    // Yeni altyazı gelince en alta kaydır (kayan akış hissi).
    LaunchedEffect(captions.size) {
        if (captions.isNotEmpty()) listState.animateScrollToItem(captions.lastIndex)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF04070D))
            .statusBarsPadding(),
    ) {
        // Üst şerit — durum noktası + etiket + Durdur.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(JarvisColors.Glow, CircleShape),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                JarvisLoopLogic.phaseLabel(
                    JarvisLoopLogic.State(phase),
                    langT,
                ),
                color = JarvisColors.Core,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onStop) {
                Text(
                    langT("Durdur", "Stop"),
                    color = Color(0xFFFF6B6B),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Merkez reaktör + altyazı, ağırlıklı sütun (küçük ekranda kaymaz).
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(0.52f),
                contentAlignment = Alignment.Center,
            ) {
                JarvisVisualizer(phase = jPhase, level = level, scale = 1.4f)
            }

            HorizontalDivider(
                color = JarvisColors.Arc.copy(alpha = 0.25f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.48f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(captions.size.let { n -> List(n) { captions[it] } }) { c ->
                    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        ) {
                            Text(
                                if (c.role == "user")
                                    langT("Sen", "You") else JarvisIdentity.NAME,
                                color = if (c.role == "user")
                                    HermesColors.TextFaint else JarvisColors.Glow,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                            )
                            Text(
                                c.text,
                                color = if (c.role == "user")
                                    HermesColors.TextPrimary else JarvisColors.Core,
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                textAlign = TextAlign.Start,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
