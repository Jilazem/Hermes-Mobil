package com.hermes.mobile.data

/**
 * "EMA / Sistem" ses seçimi.
 *
 * Kök neden: ses hattı yalnız EMA'ya bağlıydı ve EMA'da hata olduğunda
 * (`allowAndroidFallback = false`) hiç ses çıkmıyordu — kullanıcı "ses
 * çalışmıyor" diyordu. Artık kullanıcı Ayarlar'dan telefonun kendi TTS
 * motorunu (tr-TR) seçebilir; EMA yapılandırılmamışsa da sessizlik yerine
 * sistem sesi devreye girer.
 *
 * Saf mantık: JVM testinde doğrudan sınanır.
 */
object EmaSpeechChoice {

    const val EMA = "ema"
    const val SYSTEM = "sistem"

    enum class Route { EMA, SYSTEM }

    /**
     * @param pref Ayarlardaki `emaPlayback` değeri ("ema" | "sistem" | boş).
     * @param emaConfigured EMA hattı (çevrimdışı model ya da sunucu ucu) hazır mı.
     */
    fun route(pref: String, emaConfigured: Boolean): Route = when {
        pref.trim().lowercase() == SYSTEM -> Route.SYSTEM
        emaConfigured -> Route.EMA
        // EMA hiç yapılandırılmadı: sessiz kalmak yerine telefon sesi.
        else -> Route.SYSTEM
    }

    fun label(route: Route): String = when (route) {
        Route.EMA -> "EMA"
        Route.SYSTEM -> "Sistem"
    }

    /** Tanılama satırı: seçim ve gerçek yön aynı yerde görünür. */
    fun describe(pref: String, emaConfigured: Boolean): String {
        val r = route(pref, emaConfigured)
        return when {
            pref.trim().lowercase() == SYSTEM -> "ses: Sistem (telefon TTS)"
            r == Route.EMA -> "ses: EMA"
            else -> "ses: Sistem (EMA yapılandırılmadı)"
        }
    }
}
