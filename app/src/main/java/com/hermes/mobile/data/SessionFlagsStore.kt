package com.hermes.mobile.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Oturum bayrakları — sabitleme, arşiv, yerel silme, yeniden adlandırma.
 *
 * Neden sunucuda değil: relay'de rename/delete/pin/archive endpoint'i yok
 * (HermesClient'ta yalnız GET uçları var). Liste 15 sn'de bir sunucudan
 * yeniden indiriliyor ve bu bayraklar `HermesSession` nesnesine yazılamaz —
 * polling ezer. Bayraklar burada yaşar, render katmanındaki mapper uygular.
 *
 * Neden Room değil: SessionCache'teki aynı gerekçe — tek bir JSON yığını,
 * sorgu yok, ilişki yok. Kalıp SessionCache ile birebir aynı.
 *
 * Silme = `hidden` kümesine ekleme (yerel gizleme). Sunucuya DELETE ucu
 * geldiğinde tek satır mapper değişimiyle oraya taşınır.
 */
@kotlinx.serialization.Serializable
data class SessionFlags(
    val pinned: Set<String> = emptySet(),
    val archived: Set<String> = emptySet(),
    /** Silme: sunucu ucu yok → cihazda gizli. */
    val hidden: Set<String> = emptySet(),
    /** id → kullanıcı başlığı; `HermesSession.title`a dokunulmaz. */
    val renames: Map<String, String> = emptyMap(),
)

class SessionFlagsStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val dir = File(context.filesDir, "cache").apply { mkdirs() }

    private fun file(profileId: String) = File(dir, "flags-${profileId.take(40)}.json")

    /** Bayrakları yazar. Hata yutuluyor: bayraklar en iyi çaba (SessionCache kalıbı). */
    fun save(profileId: String, flags: SessionFlags) {
        runCatching {
            file(profileId).writeText(json.encodeToString(SessionFlags.serializer(), flags))
        }
    }

    fun load(profileId: String): SessionFlags = runCatching {
        val f = file(profileId)
        if (!f.exists()) return@runCatching SessionFlags()
        json.decodeFromString(SessionFlags.serializer(), f.readText())
    }.getOrDefault(SessionFlags())
}
