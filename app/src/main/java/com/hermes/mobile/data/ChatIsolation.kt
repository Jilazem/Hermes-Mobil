package com.hermes.mobile.data

/**
 * K12b: sohbetler arası olay izolasyonu — saf kural (JVM testli).
 *
 * Gateway aynı WS bağlantısına birden çok oturumun olayını `session_id`
 * etiketiyle gönderir (`session.activate` "mevcut aboneleri yerinden
 * etmez" — tui_gateway/session_lifecycle.py). İstemci etiketi yok sayarsa
 * bir sohbetin yanıtı DİĞER sohbete düşer ("oturumlar izole değil"
 * şikâyetinin kök nedeni). Kural:
 *
 *  · Etiketsiz olay (eski gateway / global yayın) → geçer, sahipsizdir.
 *  · Aktif sohbetin oturumu yoksa (yeni sohbet, henüz açılmadı) →
 *    etiketli her olay başka sohbetindir; çizilmez.
 *  · Aksi halde yalnız `eventSessionId == currentSessionId` çizilir.
 */
object ChatIsolation {

    /** Olay başka bir sohbetin oturumundan mı geldi (çizilmeyecek mi)? */
    fun foreignEvent(eventSessionId: String?, currentSessionId: String?): Boolean {
        if (eventSessionId.isNullOrBlank()) return false
        return currentSessionId == null || eventSessionId != currentSessionId
    }
}
