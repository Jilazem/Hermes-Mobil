package com.hermes.mobile.data

/**
 * Tur-21 — sesli asistanda MODEL SEÇİCİ karar katmanı (saf, JVM testli).
 *
 * İki sağlayıcı:
 *  - GEMINI: mevcut canlı ses hattı (relay üzerinden; bozulmaz, dokunulmadı).
 *  - YEREL (node1): LAN'daki OpenAI-uyumlu uç (`/v1/chat/completions`,
 *    `GET /v1/models`). Model registry: Qwen/Qwen3.8-Flash-Next
 *    (node1 192.168.1.99:8888 — 20.09.2026 canlı ölçümü ile doğrulandı).
 *
 * Model kilidi kuralı (kullanıcı onaylı, kalıcı): kullanıcı YEREL'i seçtiyse
 * bağlantı kopması / model adı farkı halinde HATA gösterilir ve YEREL'de
 * kalınır — sessiz Gemini'ye geçiş YOK. Geçiş yalnız kullanıcının kendi
 * dokunuşuyla olur.
 */
object LiveModelLogic {

    enum class Provider(val id: String) {
        GEMINI("gemini"),
        YEREL("yerel"),
        ;

        companion object {
            /** Mevcut davranış bozulmasın: Gemini KALDIRILDI; yerel seçilebilir. */
            fun fromId(raw: String?): Provider =
                entries.firstOrNull { it.id == raw?.trim()?.lowercase() } ?: GEMINI
        }
    }

    /** Durum noktası — Ayarlar segmentinin sağındaki canlı işaret. */
    enum class Health { Unknown, Ok, Down }

    /** Sağlayıcı seçenekleri (segment satırı). */
    fun options(t: (String, String) -> String): List<Pair<String, String>> = listOf(
        Provider.YEREL.id to t("Yerel (node1)", "Local (node1)"),
        Provider.GEMINI.id to "Gemini",
    )

    /**
     * Yerel seçenek için durum noktası etiketi (tooltip/içerik açıklamaları).
     */
    fun healthLabel(h: Health, t: (String, String) -> String): String = when (h) {
        Health.Ok -> t("Bağlı", "Connected")
        Health.Down -> t("Bağlı değil — dokun, hatayı göster", "Not connected — tap to see the error")
        Health.Unknown -> t("Denenmedi", "Not checked yet")
    }

    /**
     * Model kilidi: istenen sağlayıcı YEREL ise ve sağlık bozuksa
     * (Down/bilinmeyen) sohbet YEREL'de KALIR ve hata gösterilir —
     * otomatik Gemini GEÇİŞİ YAPILMAZ.
     *
     * @return gerçekten kullanılacak sağlayıcı + gösterilecek hata (varsa).
     */
    data class Resolve(val provider: Provider, val error: String?)

    fun resolveActive(
        selected: Provider,
        health: Health,
        localConfigured: Boolean,
        t: (String, String) -> String,
    ): Resolve = when {
        selected == Provider.GEMINI -> Resolve(Provider.GEMINI, null)
        !localConfigured -> Resolve(
            Provider.YEREL,
            t(
                "Yerel adres girilmemiş — Ayarlar → Sesli asistan'dan node adresini yaz",
                "No local address set — enter the node address in Settings → Voice assistant",
            ),
        )
        health == Health.Ok -> Resolve(Provider.YEREL, null)
        else -> Resolve(
            Provider.YEREL,
            t(
                "Yerel node'a ulaşılamıyor (kilitli: sessiz Gemini'ye geçilmez). " +
                    "Aynı ağda mısın — kontrol edip 'Yenile'ye bas.",
                "Local node unreachable (locked: no silent switch to Gemini). " +
                    "Check the network, then tap Refresh.",
            ),
        )
    }

    /** Yerel seçenekte durum noktası rengi adı (UI palet eşlemesi UI'da kalır). */
    fun dotColor(health: Health): String = when (health) {
        Health.Ok -> "green"
        Health.Down -> "red"
        Health.Unknown -> "grey"
    }
}
