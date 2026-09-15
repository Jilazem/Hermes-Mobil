package com.hermes.mobile.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Köprü devralma / geri çekilme politikası — saf mantık, Android'e bağlı değil.
 *
 * Neden ayrı duruyor: [PhoneBridgeService] bir Android `Service`'tir, JVM birim
 * testinde koşmaz. Kararı (hangi close kodu ne yapar, kaç sn beklenir) buraya
 * alınca doğrudan sınanabiliyor.
 *
 * Sahada ölçülen sorun (2026-09-15, canlı relay log'u): relay tek yuva tutuyor;
 * yeni bir cihaz bağlanınca eskisi düşürülüyor. Düşürülen istemci bunu "ağ
 * hatası" sanıp anında geri döndüğü için iki cihaz birbirini deviriyor —
 * ölçüm: gerçek telefonda ~82 sn'lik, 2026-09-09'da saatte ~1500 devirlik
 * ping-pong. Ayırt edici bir sinyal olmadan "yeniden bağlanma" kararı
 * verilemez; ayrımı bu nesne yapıyor.
 */
object BridgePolicy {

    /** Uygulama özel aralığından (4000–4999) devralma kodu. */
    const val CODE_TAKEOVER = 4001

    /** Relay'in devralma karesindeki `event` değeri. */
    const val EVENT_TAKEN_OVER = "taken_over"

    /**
     * Artan geri çekilme: 2 → 5 → 15 → 30 → 60 sn, sonra 60'ta tavan.
     *
     * Önceki hâli üsseldi (2·2^n, 300 sn tavan). İki cihazlık bir ping-pong'da
     * üssel büyüme işe yaramıyor çünkü her başarılı bağlanma sayacı sıfırlıyor;
     * kısa ve öngörülebilir bir merdiven hem döngüyü yavaşlatıyor hem de
     * kullanıcı "Yeniden bağlan"a bastığında uzun beklemeye sokmuyor.
     */
    val BACKOFF_MS = longArrayOf(2_000L, 5_000L, 15_000L, 30_000L, 60_000L)

    /** Denemesiz (0) hâl: hemen dene. Sonrası merdiven, tavan 60 sn. */
    fun backoffMs(attempt: Int): Long =
        if (attempt <= 0) 0L else BACKOFF_MS[minOf(attempt, BACKOFF_MS.size) - 1]

    enum class Decision { RETRY, TAKEN_OVER, STOP }

    /**
     * Bir kapanış olayında ne yapılacağı.
     *
     * @param code              close kodu (yoksa -1)
     * @param takenOverSignalled daha önce devralma bildirimi geldi mi
     * @param closedByUser      kullanıcı/servis kapattı mı
     */
    fun decision(code: Int, takenOverSignalled: Boolean, closedByUser: Boolean): Decision = when {
        closedByUser -> Decision.STOP
        takenOverSignalled || code == CODE_TAKEOVER -> Decision.TAKEN_OVER
        else -> Decision.RETRY
    }

    /**
     * Gelen metin karesi bir devralma bildirimi mi?
     *
     * Bozuk/ilgisiz kare sessizce `false` döner: bu çağrı her gelen çerçevede
     * yapılıyor, burada istisna fırlatmak mesaj yolunu düşürürdü.
     */
    fun isTakeoverFrame(text: String): Boolean =
        runCatching {
            json.parseToJsonElement(text).jsonObject["event"]
                ?.jsonPrimitive?.content == EVENT_TAKEN_OVER
        }.getOrDefault(false)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
}

/** Köprünün kullanıcıya görünen durumu. */
enum class BridgeState {
    /** Kapalı: "Ajan telefonu kullanabilsin" ayarı kapalı ya da servis durdu. */
    OFF,

    /** Bağlanma denemesi sürüyor. */
    CONNECTING,

    /** Bağlı ve ajan telefonu çağırabilir. */
    CONNECTED,

    /** Koptu; artan geri çekilmeyle yeniden denenecek. */
    RETRYING,

    /** Başka bir cihaz devraldı; kendiliğinden yeniden bağlanma DURDURULDU. */
    TAKEN_OVER,
}
