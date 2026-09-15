package com.hermes.mobile.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Kaynak rozeti — METİN değil TEK İKON (KALAN-5).
 *
 * Tur-4'te kaynak adı kart başlığından çıkarılmıştı ama kartın alt satırında
 * hâlâ "Telegram", "Masaüstü", "API" gibi 10 sp'lik METİN rozeti duruyordu:
 * her kartta bir iç terim, KONU'nun önüne geçiyor ve liste gürültülü görünüyor.
 *
 * Yeni kural (Grok yönü: "en fazla küçük kaynak ikonu"):
 *  - kartta kaynak için yalnız 12 dp'lik sönük bir ikon çizilir,
 *  - ikonun adı `contentDescription` olarak KALIR (ekran okuyucu ve dokunma
 *    ipucu metni kaybetmesin),
 *  - tanınmayan/iç bir kaynak adı (ör. "spark", "başka-araç") için ikon da
 *    çizilmez — ham iç ad ekrana HİÇ düşmez.
 *
 * Eşleme saf fonksiyondur (SourceBadgeTest).
 */
enum class SourceIcon {
    Telegram,
    Chat,
    Schedule,
    Terminal,
    Web,
    Api,
    Desktop,
}

/** Kaynak adı → ikon türü; tanınmıyorsa/boşsa null (hiçbir şey çizilmez). */
fun sourceIcon(source: String?): SourceIcon? = when (source?.trim()?.lowercase()) {
    "telegram" -> SourceIcon.Telegram
    "whatsapp" -> SourceIcon.Chat
    "cron" -> SourceIcon.Schedule
    "cli", "tui" -> SourceIcon.Terminal
    "web", "webchat" -> SourceIcon.Web
    "api", "api_server" -> SourceIcon.Api
    "desktop", "masaüstü", "masaustu" -> SourceIcon.Desktop
    else -> null
}

private fun SourceIcon.vector(): ImageVector = when (this) {
    SourceIcon.Telegram -> Icons.Default.Send
    SourceIcon.Chat -> Icons.Default.Chat
    SourceIcon.Schedule -> Icons.Default.Schedule
    SourceIcon.Terminal -> Icons.Default.Terminal
    SourceIcon.Web -> Icons.Default.Public
    SourceIcon.Api -> Icons.Default.Cloud
    SourceIcon.Desktop -> Icons.Default.Computer
}

/**
 * Kart alt satırındaki kaynak ikonu. Kaynak tanınmıyorsa HİÇBİR ŞEY çizmez
 * (boş `Box` bile bırakmaz — çağıran `spacedBy` ile hizalar).
 */
@Composable
fun SourceBadgeIcon(
    source: String?,
    en: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val kind = sourceIcon(source) ?: return
    Icon(
        kind.vector(),
        contentDescription = feedSourceLabel(source, en),
        tint = HermesColors.TextFaint,
        modifier = modifier.size(12.dp),
    )
}
