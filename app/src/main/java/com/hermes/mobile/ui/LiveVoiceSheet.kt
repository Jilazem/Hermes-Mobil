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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.camera.view.PreviewView
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hermes.mobile.CameraState
import com.hermes.mobile.LiveVoiceState
import com.hermes.mobile.data.AudioRouter
import com.hermes.mobile.data.LiveVoiceClient
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle
import com.hermes.mobile.data.DemoMask

/**
 * Gemini Live sesli sohbet ekranı.
 *
 * Uygulamanın diğer sesli kipinden farkı: ses doğrudan modele gidiyor, arada
 * metne çevrilmiyor. Bu yüzden asistan konuşurken sözünü kesebiliyorsun.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LiveVoiceSheet(
    state: LiveVoiceState,
    camera: CameraState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    /** Tam ekran kamerayı açar. */
    onOpenCamera: () -> Unit = {},
    onStopCamera: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onNeedCameraPermission: () -> Boolean = { true },
    onCycleRoute: () -> Unit = {},
    onStartDriving: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onStop(); onDismiss() },
        sheetState = sheetState,
        containerColor = HermesColors.Background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(min = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                S.voiceTitle,
                color = HermesColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                when (state.state) {
                    LiveVoiceClient.State.Idle -> S.voiceTapToStart
                    LiveVoiceClient.State.Connecting -> S.voiceConnecting
                    LiveVoiceClient.State.Listening -> S.voiceListening
                    LiveVoiceClient.State.Speaking -> S.voiceSpeaking
                    LiveVoiceClient.State.Error -> "Hata"
                },
                color = when (state.state) {
                    LiveVoiceClient.State.Listening -> HermesColors.Online
                    LiveVoiceClient.State.Speaking -> HermesColors.Midground
                    LiveVoiceClient.State.Error -> HermesColors.Danger
                    else -> HermesColors.TextMuted
                },
                fontSize = 12.sp,
            )

            Spacer(Modifier.height(10.dp))

            // Ses çıkışı — varsayılan hoparlör; dokundukça sıradaki cihaza geçer.
            Row(
                Modifier
                    .background(HermesColors.SurfaceDim, RoundedCornerShape(20.dp))
                    .clickable(onClick = onCycleRoute)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
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
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(state.route.label, color = HermesColors.TextSecondary, fontSize = 12.sp)
                if (state.routeOptions.size > 1) {
                    Spacer(Modifier.width(6.dp))
                    Text(S.voiceChange, color = HermesColors.TextFaint, fontSize = 10.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Kamera açıkken önizleme küreyi değil ekranı kaplar — asıl bakılan
            // şey görüntü olur; küre küçük bir denetime iner.
            // Kamera artık burada değil: önizleme tam ekrana taşındı.
            // Gerekçe CameraScreen'in başında.
            MicOrb(state, onStart, onStop)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CameraButton(
                    enabled = true,
                    onClick = { if (onNeedCameraPermission()) onOpenCamera() },
                )
                DrivingButton(onClick = onStartDriving)
            }

            Spacer(Modifier.height(20.dp))

            state.error?.let { err ->
                HermesCard(Modifier.fillMaxWidth()) {
                    Text(err, color = HermesColors.Danger, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(12.dp))
            }

            if (state.userText.isNotBlank()) {
                TranscriptBlock("Sen", state.userText, HermesColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
            }
            if (state.modelText.isNotBlank()) {
                TranscriptBlock("Hermes", state.modelText, HermesColors.Midground)
            }

            Spacer(Modifier.weight(1f))

            Column(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    // Tanitim kipinde adres maskelenmeli: yayinlanan bir ekran
                    // goruntusunde ev agi adresi (ws://192.168.x.x) gorunuyordu.
                    DemoMask.text(state.relayUrl).ifBlank { S.noRelay },
                    style = MonoTextStyle,
                    color = HermesColors.TextFaint,
                )
                Text(
                    if (state.usingOwnKey) S.ownKey
                    else S.keyOnServer,
                    color = HermesColors.TextFaint,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** Mikrofon küresi — seviyeyle nefes alır, dokununca başlat/durdur. */
@Composable
private fun MicOrb(state: LiveVoiceState, onStart: () -> Unit, onStop: () -> Unit) {
    val running = state.isRunning
    val target = when {
        !running -> 0f
        state.state == LiveVoiceClient.State.Speaking -> 0.55f
        else -> state.level
    }
    val amplitude by animateFloatAsState(targetValue = target, label = "mic-level")
    val ring = (108f + amplitude * 46f).dp

    Box(contentAlignment = Alignment.Center) {
        // Seviye halkası
        Box(
            Modifier
                .size(ring)
                .background(
                    when (state.state) {
                        LiveVoiceClient.State.Speaking -> HermesColors.Midground.copy(alpha = 0.14f)
                        LiveVoiceClient.State.Listening -> HermesColors.Online.copy(alpha = 0.16f)
                        else -> HermesColors.Surface
                    },
                    CircleShape,
                )
        )
        Box(
            Modifier
                .size(96.dp)
                .background(
                    if (running) HermesColors.Surface else HermesColors.SurfaceDim,
                    CircleShape,
                )
                .border(
                    2.dp,
                    when (state.state) {
                        LiveVoiceClient.State.Speaking -> HermesColors.Midground
                        LiveVoiceClient.State.Listening -> HermesColors.Online
                        LiveVoiceClient.State.Error -> HermesColors.Danger
                        else -> HermesColors.BorderStrong
                    },
                    CircleShape,
                )
                .clickable { if (running) onStop() else onStart() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when {
                    !running -> Icons.Default.Mic
                    state.state == LiveVoiceClient.State.Speaking -> Icons.Default.GraphicEq
                    else -> Icons.Default.Close
                },
                contentDescription = if (running) "Durdur" else S.start,
                tint = if (running) HermesColors.Midground else HermesColors.TextMuted,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun TranscriptBlock(who: String, text: String, color: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .heightIn(max = 150.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(who, color = HermesColors.TextFaint, fontSize = 10.sp)
        Spacer(Modifier.height(3.dp))
        Text(text, color = color, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** Kamerayı başlatma düğmesi — sesli oturum kapalıysa onu da açar. */
@Composable
private fun CameraButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .background(HermesColors.SurfaceDim, RoundedCornerShape(10.dp))
            .border(1.dp, HermesColors.BorderStrong, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.PhotoCamera,
            contentDescription = null,
            tint = HermesColors.Midground,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(S.voiceShowCamera, color = HermesColors.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun MicOrbSmall(state: LiveVoiceState, onStart: () -> Unit, onStop: () -> Unit) {
    val running = state.isRunning
    Row(
        Modifier
            .background(HermesColors.SurfaceDim, RoundedCornerShape(24.dp))
            .border(
                1.dp,
                when (state.state) {
                    LiveVoiceClient.State.Speaking -> HermesColors.Midground
                    LiveVoiceClient.State.Listening -> HermesColors.Online
                    else -> HermesColors.BorderStrong
                },
                RoundedCornerShape(24.dp),
            )
            .clickable { if (running) onStop() else onStart() }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (running) Icons.Default.Close else Icons.Default.Mic,
            contentDescription = null,
            tint = if (running) HermesColors.Midground else HermesColors.TextMuted,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            if (running) "Sesi kapat" else S.unmute,
            color = HermesColors.TextSecondary,
            fontSize = 12.sp,
        )
    }
}

/** Sürüş kipine geçiş — ekran kapalıyken de dinlemeye devam eder. */
@Composable
private fun DrivingButton(onClick: () -> Unit) {
    Row(
        Modifier
            .background(HermesColors.SurfaceDim, RoundedCornerShape(10.dp))
            .border(1.dp, HermesColors.BorderStrong, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.DirectionsCar,
            contentDescription = null,
            tint = HermesColors.Midground,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(S.voiceDriving, color = HermesColors.TextSecondary, fontSize = 13.sp)
    }
}
