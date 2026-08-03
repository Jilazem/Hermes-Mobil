package com.hermes.mobile.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Son görülen oturum listesinin yerel kopyası.
 *
 * Neden: sunucuya ulaşılamadığında Oturumlar sekmesi tamamen boşalıyordu ve
 * kullanıcı "hangi konuşmalarım vardı" sorusuna bile cevap alamıyordu. Ağ
 * yoksa **eski ama doğru** bilgi göstermek, hiçbir şey göstermemekten iyi —
 * yeter ki bunun bayat olduğu söylensin.
 *
 * Neden Room değil: tek bir listeyi saklıyoruz, sorgu yok, ilişki yok, göç
 * yok. Room bu iş için bir bağımlılık, bir şema ve bir derleyici eklentisi
 * demek olurdu. Tek dosyaya JSON yazmak aynı sonucu veriyor.
 *
 * Sunucu başına ayrı dosya: profil değiştirince başka makinenin oturumlarını
 * göstermek yanlış olurdu.
 */
class SessionCache(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val dir = File(context.filesDir, "cache").apply { mkdirs() }

    private fun file(profileId: String) = File(dir, "sessions-${profileId.take(40)}.json")

    /** Listeyi ve alındığı anı yazar. Hata yutuluyor: önbellek en iyi çaba. */
    fun save(profileId: String, sessions: List<HermesSession>) {
        if (sessions.isEmpty()) return
        runCatching {
            val payload = CachedSessions(System.currentTimeMillis(), sessions.take(MAX))
            file(profileId).writeText(json.encodeToString(CachedSessions.serializer(), payload))
        }
    }

    fun load(profileId: String): CachedSessions? = runCatching {
        val f = file(profileId)
        if (!f.exists()) return@runCatching null
        json.decodeFromString(CachedSessions.serializer(), f.readText())
    }.getOrNull()

    fun clear(profileId: String) {
        runCatching { file(profileId).delete() }
    }

    private companion object {
        /** Telefonda daha fazlası kaydırılarak bile okunmuyor. */
        const val MAX = 100
    }
}

@kotlinx.serialization.Serializable
data class CachedSessions(
    /** Epoch ms — arayüz "ne kadar bayat" diyebilsin diye. */
    val fetchedAt: Long,
    val sessions: List<HermesSession>,
)
