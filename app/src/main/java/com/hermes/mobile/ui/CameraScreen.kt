package com.hermes.mobile.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.hermes.mobile.CameraState
import com.hermes.mobile.LiveVoiceState
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Tam ekran kamera — "gördüğümü anlat" kipi.
 *
 * Önizleme daha önce canlı ses sayfasının (ModalBottomSheet) içindeydi ve
 * gerçek cihazda **siyah** kalıyordu: ModalBottomSheet içeriğini ayrı bir
 * pencerede çiziyor, `PreviewView` ise SurfaceView/TextureView ile o pencerenin
 * donanım katmanına güvenmek zorunda. Ayrı pencerede bu bağ kopuyordu.
 * Emülatörde sorun görünmüyordu, o yüzden uzun süre yakalanamadı.
 *
 * Çözüm önizlemeyi ana pencereye almak. Yan faydası da var: sürüş kipi gibi
 * tam ekran olunca telefonu bir şeye doğrultup konuşmak gerçekten rahat.
 */
@Composable
fun CameraScreen(
    voice: LiveVoiceState,
    camera: CameraState,
    onStartCamera: (PreviewView) -> Unit,
    onStopCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            // Ana pencerede SurfaceView daha ucuz ve daha az gecikmeli; tam
            // ekranda kırpma derdi de olmadığı için COMPATIBLE'a gerek yok.
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(Unit) {
        onStartCamera(previewView)
        onDispose { onStopCamera() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = S.camClose,
                    tint = Color.White,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .clickable(onClick = onClose)
                        .padding(9.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    camera.error ?: "${camera.framesSent} ${S.camFrames}",
                    color = if (camera.error != null) HermesColors.Danger else Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.medium)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Cameraswitch,
                    contentDescription = S.camFlip,
                    tint = Color.White,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .clickable(onClick = onSwitchCamera)
                        .padding(9.dp),
                )
            }
        }

        // Konuşulanın canlı dökümü — kamerayı bir şeye doğrultmuşken ekrana
        // bakmadan da ne anlaşıldığını görmek gerekiyor.
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            voice.modelText.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                        .padding(12.dp),
                )
                Spacer(Modifier.height(14.dp))
            }

            val live = voice.isRunning
            Row(
                Modifier
                    .background(
                        if (live) HermesColors.Danger.copy(alpha = 0.85f)
                        else HermesColors.Midground,
                        MaterialTheme.shapes.large,
                    )
                    .clickable { if (live) onStopVoice() else onStartVoice() }
                    .padding(horizontal = 24.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    if (live) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (live) Color.White else HermesColors.Background,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    if (live) S.camStop else S.camStart,
                    color = if (live) Color.White else HermesColors.Background,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
