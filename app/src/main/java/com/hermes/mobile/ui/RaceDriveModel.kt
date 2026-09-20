package com.hermes.mobile.ui

/**
 * Tur-20: Outrun yarışının **veri sürücüsü** — saf, deterministik kararlar.
 *
 * Kullanıcı şikâyeti: tur15 arabaları "anlamsız duruş" — oyun kendi ritminde
 * koşuyor, görev akışıyla bağı yok. Bu modül Arena figürlerinden (token/s
 * hızı, araç çağrısı = geçiş anı) outrun'un `setDrive` komutunu üretir.
 * WebView katmanı yalnız uygular; kararlar burada, testlerle kilitli.
 */

/** Bir botun yarış nabzı (ViewModel 250 ms'lik pencerelerde ölçer). */
data class RaceBotPulse(
    val id: String,
    /** delta karakter/s — pencere ölçümü. */
    val charsPerSec: Double,
    val working: Boolean,
    /** geçiş flaşı bitiş zamanı (tool.start → now+240ms). */
    val passUntilMs: Long = 0L,
)

/**
 * Hız ölçeği: token/s → hız yüzdesi (1 token ≈ 4 karakter varsayımı).
 * - çalışmayan bot: 0.28 — arkadan takip hissi (yarış hiç durmaz, 5sn kuralı).
 * - 140 kr/s ≈ 35 tok/s → 1.25 (turbo BOOST_MUL 1.26'nın hemen altında).
 * Negatif/bozuk girdi tabana çekilir.
 */
fun raceSpeedPct(charsPerSec: Double, working: Boolean): Double =
    if (!working) 0.28
    else (0.35 + (if (charsPerSec.isFinite()) charsPerSec.coerceAtLeast(0.0) else 0.0) / 140.0)
        .coerceAtMost(1.25)

/**
 * Şerit hedefi: id hash'li fazla ±2.6 birim salınım — deterministik
 * (aynı id + aynı zaman damgası = aynı şerit). Araç "şeritte yürür",
 * sol şeride geçiş = tool call anında ivme (passFlash JS tarafında).
 */
fun raceLane(nowMs: Long, id: String): Double {
    var h = 0
    for (c in id) h = (h * 31 + c.code) and 0x7FFF_FFFF
    val phase = (h % 1000) / 1000.0 * 6.2831853
    return kotlin.math.sin(nowMs / 2400.0 + phase) * 2.6
}

/** Geçiş anı mı (araç çağrısı penceresi açık mı). */
fun racePass(p: RaceBotPulse, nowMs: Long): Boolean = p.passUntilMs > nowMs

// ── Örnekleme penceresi (tur20 denetim r2/MEDIUM: döngü kararı burada, testli) ──

/** Süre sıçramasına karşı taban: bundan kısa pencere 0.2 sn sayılır (çarpma dışı kalır). */
private const val MIN_ELAPSED_S = 0.2

/**
 * 250 ms'lik tek örnekleme penceresi: delta karakter sayacı + geçiş bitiş zamanı.
 * UI her örneklemede [raceSamplePulse] ile döndürür (sayacı sıfırla, pencereyi yenile).
 */
class RaceWindow(
    @Volatile var count: Long = 0,
    @Volatile var since: Long = 0L,
    @Volatile var passUntilMs: Long = 0L,
)

/**
 * Pencere → nabız + **döngü**: kr/s = count/elapsed, sonra sayaç sıfırlanır ve
 * `since = nowMs` olur (bir sonraki pencere başı). Süre tutarsızlığı (saat jitter,
 * çift hızlı örnekleme, null pencere) 0.2 sn tabanına çekilir — chars/s asla
 * dev/NaN olmaz (kabul: negatif ve 0 girdi). Pencere null ise 0 nabız, 0 geçiş.
 *
 * [id] boş/bozuk olabilir — yalnız etiket taşınır, hesap etkilenmez.
 */
fun raceSamplePulse(w: RaceWindow?, nowMs: Long, id: String): RaceBotPulse {
    val elapsed = ((nowMs - (w?.since ?: nowMs)) / 1000.0).coerceAtLeast(MIN_ELAPSED_S)
    val count = w?.count ?: 0L
    if (w != null) {
        w.count = 0
        w.since = nowMs
    }
    return RaceBotPulse(
        id = id,
        charsPerSec = count / elapsed,
        working = true,
        passUntilMs = w?.passUntilMs ?: 0L,
    )
}

private fun fmt(v: Double): String = (Math.round(v * 1000.0) / 1000.0).toString()

/**
 * Etkinlik-yenilemeli zaman aşımı kararı (tur20 denetim r2/MEDIUM 90sn-donma):
 * mutlak deadline yerine SON ETKİNLİKTEN bu yana süreye bakılır. delta/tool akışı
 * sürdüğü sürece süre yenilenir; yalnız gerçekten sessiz kalınca ateşler.
 */
fun raceActivityTimeoutFired(nowMs: Long, lastEventMs: Long, timeoutMs: Long): Boolean =
    nowMs - lastEventMs >= timeoutMs

/**
 * `setDrive` komutu — en hızlı ÇALIŞAN bot sürücüdür (tek oyunculu ray;
 * diğer botların trafiği temsil ettiği sembol diline uygun).
 *
 * Hiç çalışan bot yoksa `null` → kabuk `setDrive(null)` gönderir (sürücü
 * bırakılır, kullanıcı klasik oynanışa dönebilir — geriye uyumlu).
 */
fun raceDriveJs(pulses: List<RaceBotPulse>, nowMs: Long): String? {
    val best = pulses.filter { it.working }.maxByOrNull { it.charsPerSec } ?: return null
    val hiz = raceSpeedPct(best.charsPerSec, working = true)
    val soluk = raceLane(nowMs, best.id)
    val gecen = pulses.any { racePass(it, nowMs) }
    return "window.outrun&&window.outrun.setDrive({hiz:" + fmt(hiz) +
        ",soluk:" + fmt(soluk) + ",gecen:" + (if (gecen) "true" else "false") + "})"
}
