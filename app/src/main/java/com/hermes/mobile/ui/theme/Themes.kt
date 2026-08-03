package com.hermes.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Hermes tema paleti.
 *
 * Renk adları Desktop'ın `themes/presets.ts` dosyasıyla eşleşiyor ki aynı temayı
 * iki yüzeyde de tanıyabilelim. Sunucu tarafındaki "skin"ler
 * (`$HERMES_HOME/skins/<ad>.yaml`) de bu yapıya çevriliyor — böylece Hermes bir
 * prompt'tan skin ürettiğinde telefon da aynı görünüme geçebiliyor.
 *
 * Hex olarak saklanıyor çünkü kullanıcı kendi temasını yazıp içe/dışa
 * aktarabilmeli; `Color` doğrudan serileştirilemez.
 */
@Serializable
data class HermesPalette(
    val id: String,
    val label: String,
    val background: String,
    val surface: String,
    val surfaceDim: String,
    val border: String,
    val borderStrong: String,
    val accent: String,
    val textPrimary: String,
    val textSecondary: String,
    val textMuted: String,
    val textFaint: String,
    val online: String = "#4ADE80",
    val busy: String = "#FBBF24",
    val danger: String = "#F87171",
    /** Sunucudan gelen skin'ler işaretlenir; yerleşiklerden ayırt etmek için. */
    val fromServer: Boolean = false,
) {
    fun toColors(): HermesColorScheme = HermesColorScheme(
        background = background.toColorOrNull() ?: Color(0xFF041C1C),
        surface = surface.toColorOrNull() ?: Color(0xFF0A2B2A),
        surfaceDim = surfaceDim.toColorOrNull() ?: Color(0xFF062322),
        border = border.toColorOrNull() ?: Color(0xFF1D4A48),
        borderStrong = borderStrong.toColorOrNull() ?: Color(0xFF2A5F5C),
        midground = accent.toColorOrNull() ?: Color(0xFFFFE6CB),
        textPrimary = textPrimary.toColorOrNull() ?: Color(0xFFFFE6CB),
        textSecondary = textSecondary.toColorOrNull() ?: Color(0xFFC8D8D6),
        textMuted = textMuted.toColorOrNull() ?: Color(0xFF7F9E9B),
        textFaint = textFaint.toColorOrNull() ?: Color(0xFF587B78),
        online = online.toColorOrNull() ?: Color(0xFF4ADE80),
        busy = busy.toColorOrNull() ?: Color(0xFFFBBF24),
        danger = danger.toColorOrNull() ?: Color(0xFFF87171),
    )
}

data class HermesColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceDim: Color,
    val border: Color,
    val borderStrong: Color,
    val midground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textFaint: Color,
    val online: Color,
    val busy: Color,
    val danger: Color,
)

/** `#RRGGBB` ya da `#AARRGGBB` ayrıştırır; bozuksa null. */
fun String.toColorOrNull(): Color? {
    val hex = trim().removePrefix("#")
    return runCatching {
        when (hex.length) {
            6 -> Color(("FF$hex").toLong(16))
            8 -> Color(hex.toLong(16))
            else -> null
        }
    }.getOrNull()
}

/**
 * Yerleşik temalar.
 *
 * `hermes` dashboard'ın LENS_0 paleti (varsayılan). Diğer altısı Desktop'ın
 * yerleşikleriyle aynı adları ve arka plan/metin renklerini taşıyor; ara tonlar
 * mobilde okunaklı olacak biçimde türetildi.
 */
val BUILTIN_THEMES: List<HermesPalette> = listOf(
    HermesPalette(
        id = "hermes", label = "Hermes Teal",
        background = "#041C1C", surface = "#0A2B2A", surfaceDim = "#062322",
        border = "#1D4A48", borderStrong = "#2A5F5C", accent = "#FFE6CB",
        textPrimary = "#FFE6CB", textSecondary = "#C8D8D6",
        textMuted = "#7F9E9B", textFaint = "#587B78",
    ),
    HermesPalette(
        id = "midnight", label = "Midnight",
        background = "#08081C", surface = "#0D0D28", surfaceDim = "#0A0A22",
        border = "#13133A", borderStrong = "#22225C", accent = "#A99CFF",
        textPrimary = "#DDD6FF", textSecondary = "#B8B2E0",
        textMuted = "#7C7AB0", textFaint = "#565490",
    ),
    HermesPalette(
        id = "ember", label = "Ember",
        background = "#160800", surface = "#1E0E04", surfaceDim = "#1A0B02",
        border = "#2A1408", borderStrong = "#452210", accent = "#FF9A4D",
        textPrimary = "#FFD8B0", textSecondary = "#D9AE86",
        textMuted = "#AA7A56", textFaint = "#7A553A",
    ),
    HermesPalette(
        id = "mono", label = "Mono",
        background = "#0E0E0E", surface = "#141414", surfaceDim = "#111111",
        border = "#1E1E1E", borderStrong = "#333333", accent = "#EAEAEA",
        textPrimary = "#EAEAEA", textSecondary = "#B4B4B4",
        textMuted = "#808080", textFaint = "#5A5A5A",
    ),
    HermesPalette(
        id = "cyberpunk", label = "Cyberpunk",
        background = "#000A00", surface = "#001200", surfaceDim = "#000E00",
        border = "#001A00", borderStrong = "#0A3312", accent = "#00FF41",
        textPrimary = "#00FF41", textSecondary = "#3ECF63",
        textMuted = "#1A8A30", textFaint = "#116020",
    ),
    HermesPalette(
        id = "slate", label = "Slate",
        background = "#0D1117", surface = "#161B22", surfaceDim = "#11161D",
        border = "#21262D", borderStrong = "#30363D", accent = "#58A6FF",
        textPrimary = "#C9D1D9", textSecondary = "#A6B0BB",
        textMuted = "#8B949E", textFaint = "#6E7681",
    ),
    HermesPalette(
        id = "nous", label = "Nous (açık)",
        background = "#F8FAFF", surface = "#FFFFFF", surfaceDim = "#EEF2FB",
        border = "#DDE3F0", borderStrong = "#C2CCE0", accent = "#3B5BDB",
        textPrimary = "#17171A", textSecondary = "#3C3C48",
        textMuted = "#666678", textFaint = "#9A9AAB",
    ),
)

fun themeById(id: String, extras: List<HermesPalette> = emptyList()): HermesPalette =
    (extras + BUILTIN_THEMES).firstOrNull { it.id == id } ?: BUILTIN_THEMES.first()
