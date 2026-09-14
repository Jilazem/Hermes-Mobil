package com.hermes.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.HermesTheme

/**
 * Görsel kanıt ekranı (yalnız debug, src/debug manifest): mesaj renderinin
 * Telegram standardını screencap ile doğrulamak için sabit markdown örnekleri.
 * Launcher'da değil; adb ile explicit açılır:
 *   adb shell am start -n <pkg>/com.hermes.mobile.ui.MarkdownRenderDebugActivity \
 *       --ez liste true
 */
class MarkdownRenderDebugActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val showList = intent?.getBooleanExtra("liste", true) ?: true
        setContent {
            HermesTheme {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxSize()
                        .background(HermesColors.Background)
                        .padding(horizontal = 10.dp, vertical = 24.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (showList) Balon(LISTE_ORNEGI)
                        if (showList) {
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
                            Balon(KOD_ORNEGI)
                        }
                    }
                }
            }
        }
    }

    /** Sohbet balonu taklidi: bot balonu ~%92 genişlik, 12-16dp iç padding. */
    @Composable
    private fun Balon(markdown: String, etiket: String = "") {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .background(HermesColors.Surface, RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            if (etiket.isNotBlank()) {
                Text(
                    etiket,
                    color = HermesColors.TextFaint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            MarkdownText(markdown)
        }
    }

    companion object {
        /** FR-002/FR-003 kanıtı: sarılan maddeler + bloklar arası boşluk. */
        val LISTE_ORNEGI = """
            ### Değerleme raporu kontrol listesi

            Aşağıdaki maddeleri tek tek doğrula ve sonucu kısa yaz:

            - Parsec 23 pafta sınırları TKGM web servisinden indirildi ve kapalı poligon kontrolü yapıldı
            - Bina kat alanları kat planıyla karşılaştırıldı; 3. katta 4 m² fark var, gerekçe soruldu
              devam satırı buraya kadar uzar ve tire hizasından sarımalı sola kaymamalıdır
              ikinci bir devam satırı daha eklendi hizalama sabit kalmalı
            - Emsal üç dosya ortalaması alındı; ortalama 42.500 TL/m² çıktı

            Kapanış paragrafı blok olarak ayrı gelir ve maddelerden boşlukla ayrılır.
        """.trimIndent()

        /** FR-004 kanıtı: koyu panel + sol vurgu + KODU KOPYALA footer. */
        val KOD_ORNEGI = """
            Kod bloğu stili bu panelde görünüyor:

            ```kotlin
            fun hesapla(alan: Double, birim: Double): Double {
                val toplam = alan * birim
                return toplam.roundToNearest(0.5)
            }
            println(hesapla(250.0, 42500.0))
            ```

            Sonrası yine normal gövde metni — **kalın** ve `satır içi kod` akışı bozulmamalı.
        """.trimIndent()
    }
}
