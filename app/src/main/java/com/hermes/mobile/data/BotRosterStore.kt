package com.hermes.mobile.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
private data class BotRosterList(val bots: List<BotCard> = emptyList())

/**
 * K18B-G1 — Kalıcı bot kimlikleri deposu: cihazda JSON dosyası.
 *
 * Neden Room/SQLCipher değil: SavedPromptsStore'daki aynı gerekçe — tek bir
 * JSON yığını, sorgu yok, ilişki yok; depoda bu altyapı zaten yok (kayıtlı
 * ölçü, PLAN.md Riskler R1). Gizli veri taşınmadığı için şifreleme katmanı da
 * gerekmez; token/anahtar [ServerProfile]'tadır, bu dosyaya girmez.
 *
 * Yazmalar senkron ve kilitli: liste insan ölçeği (onlarca kart), tek dosya
 * yazımı mikro-saniyeler sürer. Bozuk dosya yüklemeyi kırmaz — [yukle] boş
 * listeye düşer, sonraki [kaydet] yığını yeniden kurar (en iyi çaba ilkesi).
 */
class BotRosterStore(private val dir: File) {

    constructor(context: Context) : this(File(context.filesDir, "cache").apply { mkdirs() })

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
    private val lock = Any()

    /** Testin de bildiği sabit ad — bozuk dosya senaryosu buna yazar. */
    private val file = File(dir, "bot-roster.json")

    /**
     * K22 — yedek GERİ YÜKLEME: listeyi bütünüyle yazar (üzerine-yaz modu
     * eski kartları silebilsin diye). Geçersiz kartlar yazılmaz — kaydet'in
     * fail-closed kuralı burada da geçerli.
     */
    fun degistir(cards: List<BotCard>) = synchronized(lock) {
        write(cards.filter { BotRosterLogic.validate(it.name, it.personality) == null })
    }

    /** Kartlar kaydetme sırasıyla; görüntü sırasını [BotRosterLogic.sortedForDisplay] verir. */
    fun yukle(): List<BotCard> = synchronized(lock) { read() }

    /**
     * Kaydeder ya da aynı [BotCard.id] ile günceller. Geçersiz kartta
     * (boş/uzun ad) YAZMAZ, false döner (fail-closed — bozuk kayıt diske
     * düşmez). Aynı adla başka kart olabilir; kullanıcı sorumluluğunda.
     */
    fun kaydet(card: BotCard): Boolean {
        if (BotRosterLogic.validate(card.name, card.personality) != null) return false
        synchronized(lock) {
            val cur = read().filterNot { it.id == card.id }
            write(cur + card)
        }
        return true
    }

    fun sil(id: String) = synchronized(lock) {
        write(read().filterNot { it.id == id })
    }

    fun find(id: String): BotCard? = synchronized(lock) {
        read().firstOrNull { it.id == id }
    }

    /**
     * Çoğaltır: benzersiz adla yeni kart ("(kopya)", "(kopya 2)"…) döner.
     * Kaynak yoksa null (yarış: kart silinmiş olabilir).
     */
    fun duplicate(id: String, now: Long = System.currentTimeMillis()): BotCard? =
        synchronized(lock) {
            val items = read()
            val source = items.firstOrNull { it.id == id } ?: return@synchronized null
            val copy = BotRosterLogic.duplicateCopy(
                source = source,
                existingNames = items.map { it.name }.toSet(),
                newId = UUID.randomUUID().toString(),
                now = now,
            )
            write(items + copy)
            copy
        }

    /**
     * Pin aç/kapa — kayıt yoksa sessiz geçer (yarış). Sıra dosyada değişmez;
     * görüntü sıralaması [BotRosterLogic.sortedForDisplay] sorumluluğundadır.
     */
    fun setPinned(id: String, pinned: Boolean, now: Long = System.currentTimeMillis()) =
        synchronized(lock) {
            val items = read()
            if (items.none { it.id == id }) return@synchronized
            write(items.map {
                if (it.id == id) it.copy(pinned = pinned, updatedAt = now) else it
            })
        }

    /** Kilitle korunan her yerden çağrılır; dosya yoksa/bozuksa boş liste. */
    private fun read(): List<BotCard> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        json.decodeFromString(BotRosterList.serializer(), file.readText()).bots
    }.getOrDefault(emptyList())

    private fun write(items: List<BotCard>) {
        runCatching {
            file.writeText(json.encodeToString(BotRosterList.serializer(), BotRosterList(items)))
        }
    }
}