package com.hermes.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
}

val MonoTextStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = LocalHermesColors.current.textMuted,
    )

@Composable
fun HermesTheme(
    palette: HermesPalette = BUILTIN_THEMES.first(),
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colors = palette.toColors()

    val scheme = darkColorScheme(
        primary = colors.midground,
        onPrimary = colors.background,
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

    val base = Typography()
    val typography = base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp * fontScale),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp * fontScale),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp * fontScale),
    )

    CompositionLocalProvider(LocalHermesColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
