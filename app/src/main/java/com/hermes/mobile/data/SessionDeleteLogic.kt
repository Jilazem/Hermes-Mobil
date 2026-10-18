package com.hermes.mobile.data

/**
 * K10c — sohbet silme bayrak kuralları (saf fonksiyonlar; test edilir).
 *
 * Onaylı silmenin iki sonucu var:
 * - Sunucu silindi (DELETE /api/sessions/{id} 200/404): oturum sunucuda
 *   artık yok — id'ye ait TÜM yerel bayraklar temizlenir (gizleme/pin/
 *   arşiv/rename çöp kalmasın; polling geri getirmez).
 * - Sunucu silinemedi (çevrimdışı/hata): eski davranış — yalnız yerel
 *   gizleme. Oturum sunucuda YAŞAMAYA DEVAM EDER; kullanıcıya not düşülür.
 */
object SessionDeleteLogic {

    /** Sunucudan silindi: id'ye ait tüm bayrakları düşür (EMA: takip dahil). */
    fun flagsAfterServerDelete(flags: SessionFlags, id: String): SessionFlags = flags.copy(
        hidden = flags.hidden - id,
        pinned = flags.pinned - id,
        archived = flags.archived - id,
        renames = flags.renames - id,
        followed = flags.followed - id,
    )

    /** Sunucu silinemedi: yalnız yerel gizleme (K10c öncesi davranış). */
    fun flagsAfterLocalHide(flags: SessionFlags, id: String): SessionFlags = flags.copy(
        hidden = flags.hidden + id,
        pinned = flags.pinned - id,
        archived = flags.archived - id,
    )
}
