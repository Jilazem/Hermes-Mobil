package com.hermes.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Etkin renk şeması.
 *
 * Başlangıçta renkler `object HermesColors` içinde sabitti; tema desteği gelince
 * her ekranı değiştirmemek için aynı isim `CompositionLocal` üzerinden okunan bir
 * proxy'ye dönüştürüldü. Böylece `HermesColors.Background` yazan yüzlerce satır
 * olduğu gibi çalışmaya devam ediyor ama artık seçili temayı gösteriyor.
 */
private val LocalHermesColors = compositionLocalOf { BUILTIN_THEMES.first().toColors() }

/**
 * Tur18 FR-001: settings.fontScale değeri (HermesTheme'den provision edilir) —
 * sabit TextStyle üreten MonoTextStyle gibi CompositionLocal-tabanlı stiller de
 * böylece gerçekten ölçeklenir (B3: "yarıda kalan fontScale" kapanışı).
 */
val LocalFontScale = compositionLocalOf { 1f }

object HermesColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.surface
    val SurfaceDim: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.surfaceDim
    val Border: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.border
    val BorderStrong: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.borderStrong
    val Midground: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.midground
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.textSecondary
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.textMuted
    val TextFaint: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.textFaint
    val Online: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.online
    val Busy: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.busy
    val Offline: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.textFaint
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.danger

    // --- tur-17 (B1, TASARIM-RAPORU.md §3): 6 yeni semantik rol proxy'si ---
    val SurfaceCard: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.surfaceCard
    val SurfaceOverlay: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.surfaceOverlay
    val Focus: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.focus
    /** Dolgulu aksan CTA üstü metin (koyu zemin; WCAG AA hesabı Themes.kt'de). */
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.onAccent
    val Skeleton: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.skeleton
    val BubbleUser: Color @Composable @ReadOnlyComposable get() = LocalHermesColors.current.bubbleUser
}

val MonoTextStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() {
        val fs = LocalFontScale.current
        return TextStyle(
            fontFamily = FontFamily.Monospace,
            // Tur18 FR-001: mono caption(12) tabanini tasir; fontScale burada da
            // uygulanir (artik sonda kalan yari-olcekli hali degil).
            fontSize = 12.sp * fs,
            lineHeight = 12.sp * 1.2f * fs,
            color = LocalHermesColors.current.textMuted,
        )
    }

/**
 * Tur18 FR-001 — 6 adım tipografi ölçeği (TASARIM-RAPORU.md §3.2, B3).
 *
 * Ölçek noktaları sabittir; `fontScale` settings.fontScale ile çarpılır, sistem
 * font ölçeği (density) sp birimlerinin doğası gereği ayrıca işler. Böylece
 * "kullanıcı yazı boyutunu büyütünce bazı sabit .sp'ler ölçeklenmiyor" sınıfı
 * hata kapanır: ekranlar artık rol kullanır, rol = scale taşır.
 *
 * Adımlar (sp, fontScale=1): micro 11 / caption 12 / body 14 / prose 15 /
 * title 17 / display 22. Satır aralığı: tight 1.2 (title, display),
 * body 1.43 (14→20.02), prose 1.45 (15→21.75 — tur3 22sp bandı korunur).
 *
 * Map (Material3 slot): micro→labelSmall, caption→bodySmall, body→bodyMedium,
 * prose→bodyLarge (prose tanımı = bodyLarge override), title→titleMedium,
 * display→headlineSmall.
 */
fun hermesTypography(fontScale: Float): Typography {
    fun s(v: Float) = (v * fontScale).sp
    return Typography(
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = s(11f),                 // micro
            lineHeight = s(11f * 1.2f),        // 13.2
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = s(12f),                 // caption
            lineHeight = s(12f * 1.2f),        // 14.4
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = s(14f),                 // body
            lineHeight = s(14f * 1.43f),       // 20.02
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = s(15f),                 // prose (SOHBET GOVDESI — tur3 standardi)
            lineHeight = s(15f * 1.45f),       // 21.75 — 22sp bandi korunur
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = s(17f),                 // title
            lineHeight = s(17f * 1.2f),        // 20.4
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = s(22f),                 // display (tek: bos durum / buyuk istatistik)
            lineHeight = s(22f * 1.2f),        // 26.4
        ),
    )
}

/**
 * Tur18 FR-003 — 4 adım köşe yarıçapı ölçeği (TASARIM-RAPORU.md §3.4, B4).
 *
 * sm=4 (chip, iç eleman), md=10 (kart, balon, input), lg=16 (sheet, dialog),
 * full=999 (pill). Eski 15 değerlik karışım bu 4'e eritildi (dönüşüm
 * haritası: denetim/tur18/RAPOR.md). Material3 slot yerleşimi:
 * extraSmall=sm4, small=sm4, medium=md10, large=lg16, extraLarge=lg16.
 */
val HermesShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
)

/**
 * Ham dp erişimi: Material3 Shapes slot'unda "pill" yok; pill (full) gereken
 * yer — durum rozeti, profil çipi, ray hapı — RoundedCornerShape(HermesRadius.full)
 * veya CircleShape kullanır (tur18: RoundedCornerShape(50) %50 → CircleShape).
 */
object HermesRadius {
    val sm = 4.dp
    val md = 10.dp
    val lg = 16.dp
    val full = 999.dp
}

@Composable
fun HermesTheme(
    palette: HermesPalette = BUILTIN_THEMES.first(),
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colors = palette.toColors()

    // Tur-17 B1 (TASARIM-RAPORU.md §4-B1): tek darkColorScheme kırıldı —
    // palet isLight işareti ya da arka plan luminansına göre dallanır.
    // nous (açık) seçiliyken Material3 bileşenleri (DropdownMenu, TextField,
    // dialog, ripple) artık koyu default'larla çizilmez.
    val isLight = palette.lightTheme()
    val scheme = if (isLight) lightColorScheme(
        primary = colors.midground,
        onPrimary = colors.onAccent,
        secondary = colors.borderStrong,
        onSecondary = colors.textPrimary,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceDim,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.border,
        outlineVariant = colors.border,
        error = colors.danger,
        onError = colors.background,
    ) else darkColorScheme(
        primary = colors.midground,
        // B2: dolgulu aksan üstü metin artik on-accent (eski: background).
        onPrimary = colors.onAccent,
        secondary = colors.borderStrong,
        onSecondary = colors.textPrimary,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceDim,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.border,
        outlineVariant = colors.border,
        error = colors.danger,
        onError = colors.background,
    )

    // Tur18 FR-001: 3 rollük yarımda kalmış ölçek yerine 6 adımlık tam ölçek.
    val typography = hermesTypography(fontScale)

    // Tur22 madde-6: cihaz animasyon ölçeği (Ayarlar → Animasyon = 0) tek
    // noktadan okunup CompositionLocal ile tüm ağaca verilir; Motion.kt'deki
    // her geçiş bu bayrağa bakınca prefers-reduced-motion'a uyar.
    val reducedMotion = com.hermes.mobile.ui.rememberPrefersReducedMotion()

    // B1 çağdaşı: enableEdgeToEdge kullanılıyor — sistem ikonları yalnız
    // cihaz koyu/acık ayarına bakıyor. Uygulama ici tema ACIK ise (nous)
    // durum/gezinme cubugu ikonlari koyuya cevrilir; koyuda eski hali.
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = isLight
                isAppearanceLightNavigationBars = isLight
            }
        }
    }

    CompositionLocalProvider(
        LocalHermesColors provides colors,
        LocalFontScale provides fontScale,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = HermesShapes,
            content = content,
        )
    }
}
