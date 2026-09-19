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
    /**
     * Tur-17 B1 — açık tema işareti. null = işaret yok; şema dallanması arka
     * plan luminansıyla karar verir ([resolveIsLight]). Varsayılanlı alan
     * olduğu için eski skin yaml/JSON'ları bozulmadan deserialize olur.
     */
    val isLight: Boolean? = null,
) {
    /** B1 kararı: bu palet açık şemayla mı çizilir? (saf, ThemeLogic üzerinden) */
    fun lightTheme(): Boolean =
        resolveIsLight(isLight, hexToArgb(background) ?: FALLBACK_BACKGROUND_ARGB)

    fun toColors(): HermesColorScheme {
        val bgArgb = hexToArgb(background) ?: FALLBACK_BACKGROUND_ARGB
        val accentArgb = hexToArgb(accent) ?: FALLBACK_ACCENT_ARGB
        // Tur-17 B1: 6 yeni rol (§3) 13 çekirdek rolden türetilir — preset'lerde
        // yeni hex alanı YOK; eşleme tek noktadan, skin'ler eski haliyle çalışır.
        val surfaceColor = surface.toColorOrNull() ?: Color(0xFF0A2327)
        return HermesColorScheme(
            background = background.toColorOrNull() ?: Color(FALLBACK_BACKGROUND_ARGB),
            surface = surfaceColor,
            surfaceDim = surfaceDim.toColorOrNull() ?: Color(0xFF071D21),
            border = border.toColorOrNull() ?: Color(0xFF1B3A3D),
            borderStrong = borderStrong.toColorOrNull() ?: Color(0xFF275255),
            midground = accent.toColorOrNull() ?: Color(FALLBACK_ACCENT_ARGB),
            textPrimary = textPrimary.toColorOrNull() ?: Color(0xFFF2EFE6),
            textSecondary = textSecondary.toColorOrNull() ?: Color(0xFFC4D3D0),
            textMuted = textMuted.toColorOrNull() ?: Color(0xFF7F9A97),
            textFaint = textFaint.toColorOrNull() ?: Color(0xFF5A7773),
            online = online.toColorOrNull() ?: Color(0xFF4ADE80),
            busy = busy.toColorOrNull() ?: Color(0xFFFBBF24),
            danger = danger.toColorOrNull() ?: Color(0xFFF87171),
            // --- tur-17 yeni 6 rol (TASARIM-RAPORU.md §3) ---
            surfaceCard = surfaceColor,
            surfaceOverlay = Color(argbWithAlpha(bgArgb, 0xB3)),
            focus = accent.toColorOrNull() ?: Color(FALLBACK_ACCENT_ARGB),
            onAccent = Color(onColorFor(accentArgb, bgArgb)),
            skeleton = Color(
                mixArgb(
                    hexToArgb(surfaceDim) ?: FALLBACK_SURFACE_DIM_ARGB,
                    hexToArgb(border) ?: FALLBACK_BORDER_ARGB,
                ),
            ),
            bubbleUser = surfaceColor,
        )
    }
}

// Tema-dışı bozuk hex durumunda düşülen güvenli varsayılanlar (§3.1 koyu set).
internal const val FALLBACK_BACKGROUND_ARGB = 0xFF04171AL
internal const val FALLBACK_ACCENT_ARGB = 0xFF4FD8C0L
internal const val FALLBACK_SURFACE_DIM_ARGB = 0xFF071D21L
internal const val FALLBACK_BORDER_ARGB = 0xFF1B3A3DL

/**
 * Semantik şema. İlk 13 rol KORUNUR (FR-003 API kilidi: isim/imza aynı);
 * tur-17'nin 6 yeni rolü varsayılanlı parametre olarak sonda eklenir —
 * mevcut construction çağrıları kırılmaz.
 */
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
    /** Kart yüzeyi — koyuda surface ile aynı; gölge yerine 1dp border (§3.5). */
    val surfaceCard: Color = surface,
    /** Modal/sheet arkası yarı saydam karartma (§3.1 surface-overlay, %70). */
    val surfaceOverlay: Color = background,
    /** Odak halkası rengi (§3.5 shadow-focus karşılığı). */
    val focus: Color = midground,
    /** Dolgulu aksan üstü metin — koyuda koyu zemin, açıkta beyaz (§3.1). */
    val onAccent: Color = background,
    /** İskelet yükleme yüzeyi (§3.1 skeleton) — surfaceDim↔border arası ton. */
    val skeleton: Color = surfaceDim,
    /** Kullanıcı sohbet balonu zemini (§3 rol listesi bubbleUser). */
    val bubbleUser: Color = surface,
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
    // Tur-17 B2: "Hermes Teal 2.0" — TASARIM-RAPORU.md §3.1 birebir.
    // Aksan artik metinden ayrik (eski #FFE6CB = textPrimary idi → B2 ihlali).
    HermesPalette(
        id = "hermes", label = "Hermes Teal",
        background = "#04171A", surface = "#0A2327", surfaceDim = "#071D21",
        border = "#1B3A3D", borderStrong = "#275255", accent = "#4FD8C0",
        textPrimary = "#F2EFE6", textSecondary = "#C4D3D0",
        textMuted = "#7F9A97", textFaint = "#5A7773",
        isLight = false,
    ),
    // Aksan #A99CFF textPrimary #DDD6FF'a cok yakindi → koyulaştırıldı (B2: aksam ≠ metin).
    HermesPalette(
        id = "midnight", label = "Midnight",
        background = "#08081C", surface = "#0D0D28", surfaceDim = "#0A0A22",
        border = "#13133A", borderStrong = "#22225C", accent = "#8C7CFF",
        textPrimary = "#DDD6FF", textSecondary = "#B8B2E0",
        textMuted = "#7C7AB0", textFaint = "#565490",
        isLight = false,
    ),
    // Aksan #FF9A4D ile metin #FFD8B0 ton kardesiydi → koyu turuncuya cekildi.
    HermesPalette(
        id = "ember", label = "Ember",
        background = "#160800", surface = "#1E0E04", surfaceDim = "#1A0B02",
        border = "#2A1408", borderStrong = "#452210", accent = "#E06A1F",
        textPrimary = "#FFD8B0", textSecondary = "#D9AE86",
        textMuted = "#AA7A56", textFaint = "#7A553A",
        isLight = false,
    ),
    // Mono'da tam ayrim imkânsiz → aksana soguk ton verildi (§3.1 notu: hue/ton farki).
    HermesPalette(
        id = "mono", label = "Mono",
        background = "#0E0E0E", surface = "#141414", surfaceDim = "#111111",
        border = "#1E1E1E", borderStrong = "#333333", accent = "#B8C2C6",
        textPrimary = "#EAEAEA", textSecondary = "#B4B4B4",
        textMuted = "#808080", textFaint = "#5A5A5A",
        isLight = false,
    ),
    // "deneysel" (§3.1): neon yesil metin korunur; aksan neon camgibe ayrildi
    // (yesil-on-yesil ton kiligi B2'ye düser; camgil 1.55x metin ayrimi + 9.5:1 zemin).
    HermesPalette(
        id = "cyberpunk", label = "Cyberpunk",
        background = "#000A00", surface = "#001200", surfaceDim = "#000E00",
        border = "#001A00", borderStrong = "#0A3312", accent = "#00BFFF",
        textPrimary = "#00FF41", textSecondary = "#3ECF63",
        textMuted = "#1A8A30", textFaint = "#116020",
        isLight = false,
    ),
    HermesPalette(
        id = "slate", label = "Slate",
        background = "#0D1117", surface = "#161B22", surfaceDim = "#11161D",
        border = "#21262D", borderStrong = "#30363D", accent = "#58A6FF",
        textPrimary = "#C9D1D9", textSecondary = "#A6B0BB",
        textMuted = "#8B949E", textFaint = "#6E7681",
        isLight = false,
    ),
    // Tur-17 B1+B2: "Nous 2.0" — §3.1 ACIK token seti + isLight=true
    // (artik lightColorScheme ile cizilir; Material3 bilesenleri koyu default'la cizilmez).
    // Sapma: §3.1 aksan #0F8C74 "beyaz ustunde 4.6:1" iddia ediyordu; programatik
    // olcum 4.18:1 (< AA 4.5). Ayni hue ailesinden AA'yi gecen #0E836C secildi
    // (beyaz ustune 4.69:1, zemin/ayrim 4.48:1) — FR-002 "dolgu/ustu metin WCAG AA".
    HermesPalette(
        id = "nous", label = "Nous (açık)",
        background = "#F8FAFB", surface = "#FFFFFF", surfaceDim = "#EEF3F3",
        border = "#D9E2E1", borderStrong = "#B9C9C7", accent = "#0E836C",
        textPrimary = "#17232B", textSecondary = "#3F5257",
        textMuted = "#6A7B7E", textFaint = "#93A2A4",
        isLight = true,
    ),
)

fun themeById(id: String, extras: List<HermesPalette> = emptyList()): HermesPalette =
    (extras + BUILTIN_THEMES).firstOrNull { it.id == id } ?: BUILTIN_THEMES.first()
