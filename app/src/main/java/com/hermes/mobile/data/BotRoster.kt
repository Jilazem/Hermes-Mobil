package com.hermes.mobile.data

import kotlinx.serialization.Serializable

/**
 * K18B-G1 — Kalıcı bot kimliği (roster kartı).
 *
 * Arena'daki geçici bot seçimi her koşuda profil listesinden yapılır; bu kart
 * ise kullanıcının oluşturup sakladığı kalıcı kimliktir (MausBot roster fikri —
 * yalnız desen, kod kopyalanmadı; CREDITS.md). "Bu botla devam et" bu kayıttan
 * aynı profil + sağlayıcı/model seçimini geri yükler.
 *
 * Gizlilik: kart yalnız kullanıcı girdisini taşır (ad, kişilik). Sunucu
 * token'ı/anahtarı ASLA burada tutulmaz — onlar [ServerProfile] içindedir ve
 * EncryptedSharedPreferences'te yaşar. Bu tipin serileştirilmiş JSON'unda
 * gizli alan yoktur (BotRosterStoreTest kilitler).
 */
@Serializable
data class BotCard(
    /** Cihaz içi kimlik — düzenleme/silme/çoğaltma bununla yapılır; UI'da GÖSTERİLMEZ. */
    val id: String,
    /** Kullanıcının verdiği ad — kart başlığında görünür. */
    val name: String,
    val emoji: String = "🤖",
    /** Kişilik kısa metni — kullanıcı girdisi, cihazda kalır. */
    val personality: String = "",
    /** Hermes profil adı (`createSession(profile)` argümanı); boş = "Yönlendirici". */
    val profile: String = "",
    /** Varsayılan model sağlayıcısı (ör. "nous"); boş = mevcut model korunur. */
    val provider: String = "",
    /** Varsayılan model adı; boş = mevcut model korunur. */
    val model: String = "",
    /** Sabitlenmiş kart listede en üstte gelir. */
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** "Bu botla devam et" hedefi — sohbet seçimine geri yüklenecek üçlü. */
data class BotResumeTarget(
    /** `null` = Yönlendirici çipi (profil gönderilmez). */
    val profile: String?,
    val provider: String,
    val model: String,
)

/** Kart doğrulama hataları — UI bunları yerelleştirilmiş metne çevirir. */
enum class BotValidationError { NAME_BLANK, NAME_TOO_LONG, PERSONALITY_TOO_LONG }

/**
 * Roster saf mantığı — UI'dan bağımsız, JVM'de test edilir (D-10: eşik ve
 * adlandırma kuralları "seçim" değil "kural" olduğu için testle kilitlenir).
 */
object BotRosterLogic {

    const val MAX_NAME = 48
    const val MAX_PERSONALITY = 500

    /** Boş/kırpılmış ad boşsa ya da sınırlar aşılıyorsa hata; geçerliyse null. */
    fun validate(name: String, personality: String): BotValidationError? = when {
        name.trim().isEmpty() -> BotValidationError.NAME_BLANK
        name.trim().length > MAX_NAME -> BotValidationError.NAME_TOO_LONG
        personality.length > MAX_PERSONALITY -> BotValidationError.PERSONALITY_TOO_LONG
        else -> null
    }

    /**
     * Görüntü sırası: sabitlenenler en üstte (kendi aralarında korunan sıra),
     * diğerleri ekleme sırasıyla. Kararlı (stable) — aynı girdi aynı çıktı.
     */
    fun sortedForDisplay(cards: List<BotCard>): List<BotCard> =
        cards.filter { it.pinned } + cards.filterNot { it.pinned }

    /**
     * Çoğaltma adı: "Ad (kopya)", doluysa "Ad (kopya 2)", "Ad (kopya 3)"…
     * Taban ad MAX_NAME'a kırpılır ki ek hiç taşmasın.
     */
    fun duplicateName(baseName: String, existingNames: Set<String>): String {
        val base = baseName.trim().take(MAX_NAME - 10).ifBlank { "Bot" }
        val first = "$base (kopya)"
        if (first !in existingNames) return first
        var n = 2
        while ("$base (kopya $n)" in existingNames) n++
        return "$base (kopya $n)"
    }

    /**
     * Çoğaltma: kaynağın kimlik/zaman damgaları dışında aynısı; ad benzersiz,
     * pin AÇIK gelmez (yeni kart kullanıcının sıraya kendisi sokacağı yerde doğar).
     */
    fun duplicateCopy(
        source: BotCard,
        existingNames: Set<String>,
        newId: String,
        now: Long,
    ): BotCard = source.copy(
        id = newId,
        name = duplicateName(source.name, existingNames),
        pinned = false,
        createdAt = now,
        updatedAt = now,
    )

    /**
     * "Bu botla devam et" hedefi. Boş profil → null (Yönlendirici çipi);
     * boş model → çağıran model seçimine dokunmaz.
     */
    fun resumeTarget(card: BotCard): BotResumeTarget = BotResumeTarget(
        profile = card.profile.trim().ifBlank { null },
        provider = card.provider.trim(),
        model = card.model.trim(),
    )

    /** Kart afişi: "emoji ad" — ham id ASLA görünmez (D-05). */
    fun cardTitle(card: BotCard): String =
        (card.emoji.ifBlank { "🤖" } + " " + card.name.trim()).trim()
}