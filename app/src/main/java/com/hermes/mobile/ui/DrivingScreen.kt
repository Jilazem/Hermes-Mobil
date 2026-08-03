package com.hermes.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.LiveVoiceState
import com.hermes.mobile.data.AudioRouter
import com.hermes.mobile.data.LiveVoiceClient
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Sürüş kipi — tamamen sesle çalışır, ekrana bakmayı gerektirmez.
 *
 * Tasarım ilkesi: sürerken gözün yolda olacak. Bu yüzden burada küçük denetim,
 * liste, kaydırma yok; yalnız çok büyük bir durum göstergesi, tek bir devasa
 * dokunma hedefi ve okunaklı altyazı var. Renk tek başına durumu anlatıyor —
 * yeşil dinliyor, krem konuşuyor — ki göz ucuyla bakış yetsin.
 */
@Composable
fun DrivingScreen(
    state: LiveVoiceState,
    onToggle: () -> Unit,
    onCycleRoute: () -> Unit,
    onExit: () -> Unit,
) {
    val accent = when (state.state) {
        LiveVoiceClient.State.Listening -> HermesColors.Online
        LiveVoiceClient.State.Speaking -> HermesColors.Midground
        LiveVoiceClient.State.Error -> HermesColors.Danger
        else -> HermesColors.TextMuted
    }

    val statusText = when (state.state) {
        LiveVoiceClient.State.Idle -> S.stStart
        LiveVoiceClient.State.Connecting -> S.stConnecting
        LiveVoiceClient.State.Listening -> S.stListening
        LiveVoiceClient.State.Speaking -> S.stSpeaking
        LiveVoiceClient.State.Error -> "HATA"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(HermesColors.Background)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Üst şerit — çıkış ve ses çıkışı. Sürüşte ihtiyaç duyulan tek iki şey.
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .background(HermesColors.SurfaceDim, RoundedCornerShape(22.dp))
                    .clickable(onClick = onCycleRoute)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when (state.route) {
                        AudioRouter.Route.Bluetooth -> Icons.Default.Bluetooth
                        AudioRouter.Route.Wired -> Icons.Default.Headset
                        AudioRouter.Route.Earpiece -> Icons.Default.PhoneInTalk
                        AudioRouter.Route.Speaker -> Icons.Default.VolumeUp
                    },
                    contentDescription = S.audioOut,
                    tint = HermesColors.Midground,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(state.route.label, color = HermesColors.TextSecondary, fontSize = 15.sp)
            }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .size(52.dp)
                    .background(HermesColors.SurfaceDim, CircleShape)
                    .clickable(onClick = onExit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = S.driveExitLong,
                    tint = HermesColors.TextMuted,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Devasa dokunma hedefi — sürüşte nişan almadan basılabilmeli.
        BigOrb(state, accent, onToggle)

        Spacer(Modifier.height(26.dp))
        Text(
            statusText,
            color = accent,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        // Altyazı — göz ucuyla okunabilecek kadar büyük, kaydırmasız.
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                state.error?.let {
                    Text(
                        it,
                        color = HermesColors.Danger,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                    )
                }
                if (state.userText.isNotBlank()) {
                    Text(
                        state.userText,
                        color = HermesColors.TextFaint,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (state.modelText.isNotBlank()) {
                    Text(
                        state.modelText,
                        color = HermesColors.TextSecondary,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 29.sp,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            S.driveScreenOff,
            color = HermesColors.TextFaint,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 22.dp),
        )
    }
}

@Composable
private fun BigOrb(state: LiveVoiceState, accent: androidx.compose.ui.graphics.Color, onToggle: () -> Unit) {
    val running = state.isRunning
    val target = when {
        !running -> 0f
        state.state == LiveVoiceClient.State.Speaking -> 0.6f
        else -> state.level
    }
    val amplitude by animateFloatAsState(targetValue = target, label = "driving-level")

    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size((210 + amplitude * 70).dp)
                .background(accent.copy(alpha = 0.13f), CircleShape)
        )
        Box(
            Modifier
                .size(186.dp)
                .background(HermesColors.Surface, CircleShape)
                .border(4.dp, accent, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (running) Icons.Default.Close else Icons.Default.Mic,
                contentDescription = if (running) "Durdur" else S.start,
                tint = accent,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}
