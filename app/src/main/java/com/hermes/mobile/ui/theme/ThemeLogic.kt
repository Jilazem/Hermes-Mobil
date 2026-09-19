package com.hermes.mobile.ui.theme

/**
 * Tur-17 B1/B2 — tema karar matematiği (saf, Compose'suz, birim testli).
 *
 * Tasarım kaynağı: TASARIM-RAPORU.md §3.1 (token seti, koyu "Hermes Teal 2.0" +
 * açık "Nous 2.0"), §4 B1/B2.
 *
 * - B1: `darkColorScheme` tek çağrısı kırıldı; paletin arka plan parlaklığı
 *   (veya paletin `isLight` işareti) `lightColorScheme` dallanmasını seçtirir.
 * - B2: aksan ≠ textPrimary kuralı + dolgulu CTA'da on-accent zemin hesabı
 *   (WCAG AA ≥ 4.5 başlık-dışı metin).
 *
 * Renkler burada düz `0xAARRGGBB` Long olarak taşınır — Compose Color JVM
 * birim testinde gereksiz risk; ayrıştırma tek noktadan ([hexToArgb]).
 */

/** `#RRGGBB` / `#AARRGGBB` → 0xAARRGGBB Long; bozuksa null. */
fun hexToArgb(hex: String): Long? {
    val h = hex.trim().removePrefix("#")
    return when (h.length) {
        6 -> "FF$h".toLongOrNull(16)
        8 -> h.toLongOrNull(16)
        else -> null
    }
}

/** ARGB kanalları. */
fun argbAlpha(argb: Long): Int = ((argb shr 24) and 0xFF).toInt()
fun argbRed(argb: Long): Int = ((argb shr 16) and 0xFF).toInt()
fun argbGreen(argb: Long): Int = ((argb shr 8) and 0xFF).toInt()
fun argbBlue(argb: Long): Int = (argb and 0xFF).toInt()

/** sRGB göreli parlaklık (WCAG 2.x formülü), 0.0–1.0. */
fun relativeLuminance(argb: Long): Double {
    fun lin(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * lin(argbRed(argb)) + 0.7152 * lin(argbGreen(argb)) + 0.0722 * lin(argbBlue(argb))
}

/** WCAG kontrast oranı (1.0–21.0); alfa yok sayılır (zemin üstü opak metin varsayımı). */
fun contrastRatio(aArgb: Long, bArgb: Long): Double {
    val la = relativeLuminance(aArgb)
    val lb = relativeLuminance(bArgb)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/**
 * B1 dallanma eşiği: arka plan parlaklığı bu değerin üstündeyse tema AÇIK sayılır.
 * Yerleşik 7 preset'te koyu arka planlar ≤0.01, nous ≈0.95 — eşik 0.20 iki
 * kümenin de uzağında, sunucu skin'lerinde de makul ayrım verir.
 */
const val LIGHT_THEME_LUMINANCE_THRESHOLD = 0.20

/** Paletin `isLight` alanı yoksa luminans kararı: arka plan açık mı? */
fun isLightByLuminance(backgroundArgb: Long): Boolean =
    relativeLuminance(backgroundArgb) > LIGHT_THEME_LUMINANCE_THRESHOLD

/**
 * B1 karar fonksiyonu: tema açık şemayla mı çizilecek?
 * Kullanıcı/skin işaretlemesi ([isLightMark]) esas; işaret yoksa ([null]) arka
 * plan luminansı karar verir.
 */
fun resolveIsLight(isLightMark: Boolean?, backgroundArgb: Long): Boolean =
    isLightMark ?: isLightByLuminance(backgroundArgb)

/**
 * B2 dolgulu CTA hesabı: [fill] rengiyle doldurulmuş yüzeyin üstüne hangi metin
 * rengi daha yüksek kontrast verir — koyu zemin ([backgroundArgb] ailesinden
 * koyulaştırılmış ton) mu, saf beyaz mı? WCAG AA'ya göre kazanan seçilir.
 */
fun onColorFor(fillArgb: Long, backgroundArgb: Long): Long {
    val darkGround = argbWithAlpha(0xFF000000L or (backgroundArgb and 0x00FFFFFFL), 255)
    val white = 0xFFFFFFFFL
    return if (contrastRatio(fillArgb, darkGround) >= contrastRatio(fillArgb, white)) darkGround else white
}

/** WCAG AA başlık-dışı metin eşiği (§3.1). */
const val WCAG_AA_NORMAL_TEXT = 4.5

/**
 * B2 dolgulu CTA çözücüsü (tur-17 denetim 1/3 bulgu B2-CTA): [onColorFor] iki
 * adaydan (koyu zemin / beyaz) iyi kontrastlısını seçer; ancak parlak dolgulu
 * (özellikle açık tema) skin'lerde iki aday da AA'nın altında kalabilir —
 * örn. soluk yeşil aksana beyaz 1.8:1, koyu zemin 2.2:1. Bu çözücü o durumda
 * kazanan adayyı doldurunun [target] (siyah/beyaz) ucuna kademeli karıştırarak
 * AA'yı geçen TON türetir; taban aday AA'yı geçiyorsa ona dokunmaz (piksel
 * regresyonu yok — 7 BUILTIN preset'te taban zaten ≥4.5).
 *
 * Sunucudan gelen kullanıcı skin'leri (customThemes) da bu yoldan geçer:
 * aksan hex'ine dokunulmaz, yalnız dolgulu CTA ÜSTÜ metin düzeltilir.
 */
fun resolveOnAccent(fillArgb: Long, backgroundArgb: Long): Long {
    val base = onColorFor(fillArgb, backgroundArgb)
    if (contrastRatio(fillArgb, base) >= WCAG_AA_NORMAL_TEXT) return base
    // Kazanan aday her iki senaryoda da koyulaştırılabilir: beyaz aday AA geçmiyorsa
    // dolgu açık-gri aralıktadır → siyaha karıştır; koyu-zemin aday AA geçmiyorsa
    // paletin zemini dolguya görece yakındır → yine siyaha (kontrast artar).
    val target = 0xFF000000L
    for (i in 1..20) {
        val candidate = mixArgb(base, target, i / 20.0)
        if (contrastRatio(fillArgb, candidate) >= WCAG_AA_NORMAL_TEXT) return candidate
    }
    return base // kurtarılamaz en iyi aday döner; çağıran test patlar, sessizlik yok
}

/** ARGB'nin alfa kanalını değiştir. */
fun argbWithAlpha(argb: Long, alpha: Int): Long =
    ((alpha.coerceIn(0, 255).toLong()) shl 24) or (argb and 0x00FFFFFFL)

/** İki rengin kanal bazında ortası (iskelet/dolgu türevleri için). */
fun mixArgb(aArgb: Long, bArgb: Long, t: Double = 0.5): Long {
    val k = t.coerceIn(0.0, 1.0)
    fun ch(a: Int, b: Int) = (a + (b - a) * k).toInt().coerceIn(0, 255).toLong()
    return 0xFF000000L or
        (ch(argbRed(aArgb), argbRed(bArgb)) shl 16) or
        (ch(argbGreen(aArgb), argbGreen(bArgb)) shl 8) or
        ch(argbBlue(aArgb), argbBlue(bArgb))
}

/**
 * B2 koruma kontrolü: aksan, gövde metninden ayrık mı?
 * Aynı hex paylaşmak yasak (§3.7 "kritik ayrıştırma"); ayrıca ayrımın gözle
 * görünür olması için en az [minContrast] (1.7 ≈ hafif ama seçilebilir) fark.
 */
fun isAccentDistinct(accentArgb: Long, textArgb: Long, minContrast: Double = 1.7): Boolean =
    contrastRatio(accentArgb, textArgb) >= minContrast
