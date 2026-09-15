package com.hermes.mobile.data

/**
 * Soket parametreleri ve yeniden bağlanma politikası — **saf mantık**, testli.
 *
 * Sahada ölçülen durum (v2 logu, 2026-09-15): `/ws` soketi 20 sn, köprü 40 sn
 * ping penceresiyle kopuyordu (`sent ping but didn't receive pong within
 * 20000/40000 ms`, toplam 19 olay). Kopmaların kendisi ağ yolunda (mobil↔wifi
 * geçişi + bulut vekili); uygulamanın elindeki iki kaldıraç var:
 *
 *  1. **Algılama gecikmesi** = ping aralığı. 40 sn bekleyen bir köprü, gerçekte
 *     ölü olduğu hâlde "bağlı" görünüyordu. Aralıkları normal tur (RTT) süresinin
 *     ~10 katı kalacak şekilde gözden geçirdik: ws 20→15 sn, köprü 40→20 sn.
 *     Röle **bilerek 45 sn'de bırakıldı** (uzun `hermes_ask` turu pong'u
 *     geciktiriyordu — tur-2 ölçümü). Daha kısa tutmak (ör. 5 sn) yavaş mobil
 *     hatlarda yanlış "koptu" üretir; daha uzun tutmak kullanıcıyı "Düşünüyor"
 *     ekranında bekletir.
 *  2. **Kopma sonrası iyileşme**: tek bir ağ sarsıntısında üç kanal da aynı anda
 *     düşüyor. Yakın zamanda bağlıydıysa (≤ [FLAP_WINDOW_MS]) geri çekilme
 *     tavanı [FAST_RECOVERY_CAP_MS]'e iner; ayrıca ±%25 **jitter** eklenir ki
 *     kanallar aynı saniyede yeniden bağlanıp sunucuyu dalgalandırmasın.
 *
 * Köprüde tavan daha yüksek ([BRIDGE_FLAP_CAP_MS]): tur-7'de kapatılan devirme
 * fırtınasının yavaşlatıcı merdiveni (2→5→15→30→60) korunur, yalnız 30/60 sn
 * basamakları atlanır.
 */
object SocketTuning {

    const val GATEWAY_CONNECT_SECONDS = 10L
    /** Aynı zamanda pong bütçesi (OkHttp: pong pingInterval içinde gelmeli). */
    const val GATEWAY_PING_SECONDS = 15L

    const val BRIDGE_CONNECT_SECONDS = 12L
    const val BRIDGE_PING_SECONDS = 20L

    const val RELAY_CONNECT_SECONDS = 15L
    /**
     * Rölede ping penceresi BİLEREK yüksek: uzun bir `hermes_ask` turu (ölçüldü:
     * 60 sn) pong'u ses kareleri arasında geciktirebiliyor ve pencereyi
     * kısaltmak yanlış "koptu" üretiyordu (tur-2).
     */
    const val RELAY_PING_SECONDS = 45L

    /** Bu süre içinde bağlıydıysak "sarsıntı" sayılır: hızlı iyileşme. */
    const val FLAP_WINDOW_MS = 60_000L

    const val FAST_RECOVERY_CAP_MS = 5_000L
    const val BRIDGE_FLAP_CAP_MS = 15_000L
    const val FIRST_RETRY_MS = 300L
    const val MAX_BACKOFF_MS = 30_000L

    /** ±%25 jitter. */
    const val JITTER_FRACTION = 0.25

    /**
     * `unit` ∈ [0,1) rastgele bir sayıdır; testte sabit verilir (deterministik).
     * Sonuç `base`'in ±%[JITTER_FRACTION] aralığında, en az 1 ms.
     */
    fun jittered(base: Long, unit: Double, fraction: Double = JITTER_FRACTION): Long {
        val u = unit.coerceIn(0.0, 1.0)
        val factor = 1.0 - fraction + 2.0 * fraction * u
        return (base * factor).toLong().coerceAtLeast(1L)
    }

    /**
     * `/ws` yeniden bağlanma gecikmesi.
     *
     * @param msSinceLastOpen son BAŞARILI açılıştan bu yana geçen süre (yoksa null)
     */
    fun gatewayReconnectMs(
        attempt: Int,
        msSinceLastOpen: Long?,
        unit: Double,
    ): Long {
        val base = if (attempt <= 1) FIRST_RETRY_MS
        else minOf(MAX_BACKOFF_MS, 1_000L shl minOf(attempt, 5))
        val recent = msSinceLastOpen != null && msSinceLastOpen in 0..FLAP_WINDOW_MS
        val capped = if (recent) minOf(base, FAST_RECOVERY_CAP_MS) else base
        return jittered(if (attempt <= 1) base else capped, unit)
    }

    /**
     * Köprü yeniden bağlanma gecikmesi: [BridgePolicy] merdiveni korunur,
     * yakın zamanda bağlıysak tavan [BRIDGE_FLAP_CAP_MS].
     */
    fun bridgeReconnectMs(attempt: Int, msSinceLastOpen: Long?, unit: Double): Long {
        val base = BridgePolicy.backoffMs(attempt)
        val recent = msSinceLastOpen != null && msSinceLastOpen in 0..FLAP_WINDOW_MS
        val capped = if (recent && base > BRIDGE_FLAP_CAP_MS) BRIDGE_FLAP_CAP_MS else base
        return if (capped <= 0L) 0L else jittered(capped, unit)
    }
}
