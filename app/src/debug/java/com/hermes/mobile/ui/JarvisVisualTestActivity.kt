package com.hermes.mobile.ui

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.hermes.mobile.data.JarvisPhase
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.HermesTheme
import kotlinx.coroutines.delay

/**
 * Tur-23 kanıt yüzeyi (yalnız DEBUG; launcher'da yok — adb ile explicit açılır).
 *
 * Gerçek `JarvisVisualizer` bileşenini 4 fazın her birinde ve verilen
 * seviyede çizer — faz/seviye intent extra'larıyla sabitlenebilir:
 *   --es phase idle|listening|thinking|speaking  --ef level 0.7
 * Speaking fazında `--es animate 1` verilirse LEVEL 120 ms'de bir 0..1
 * arasında dolaştırılır → iki ardışık ekran görüntüsü FARKLI olur
 * (animasyonun canlı kanıtı, kare-diff ile ölçülür).
 */
class JarvisVisualTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phaseName = intent.getStringExtra("phase") ?: "idle"
        val level = intent.getFloatExtra("level", 0.5f)
        val animate = intent.getStringExtra("animate") == "1"
        // Tur-23 r1 (LOW-5): her intent açılışında satırlı iz — kanıt betiği
        // log.txt'yi bu satırdan doldurur (ekrandaki Text ayrı, ama OCR'siz
        // doğrulanabilir tek kaynak logcat).
        android.util.Log.i(
            "JARVIS-VISUAL-TEST",
            "phase=$phaseName level=$level animate=$animate",
        )
        val phase = when (phaseName.lowercase()) {
            "listening" -> JarvisPhase.Listening
            "thinking" -> JarvisPhase.Thinking
            "speaking" -> JarvisPhase.Speaking
            else -> JarvisPhase.Idle
        }

        val root = FrameLayout(this)
        root.addView(
            ComposeView(this).apply {
                setContent {
                    HermesTheme {
                        var shownLevel by remember { mutableFloatStateOf(level) }
                        if (animate) {
                            LaunchedEffect(Unit) {
                                var k = 0
                                while (true) {
                                    delay(120)
                                    k++
                                    shownLevel = (k % 10) / 9f
                                }
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xFF06171B)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                JarvisVisualizer(
                                    phase = phase,
                                    level = if (animate) shownLevel else level,
                                )
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    "JARVIS-VISUAL-TEST phase=$phaseName level=$shownLevel",
                                    color = HermesColors.TextPrimary,
                                    style = TextStyle.Default,
                                )
                            }
                        }
                    }
                }
            },
        )
        setContentView(root)
    }
}