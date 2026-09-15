package com.hermes.mobile.data

/**
 * Adres sağlığı — **ölü adresin bedelini bir kez ödetir**.
 *
 * Sahada ölçülen sorun (2026-09-15 v2 logu, SM-S918B): uygulama her istekte
 * önce LAN adayını (`192.168.101.10:9150`, `192.168.1.10:9150` — ikisi de ölü)
 * deniyor, 8 sn zaman aşımına düşüyor, sonra uzak adrese geçiyordu. Log'da
 * **140 `unreachable`** satırı var; kullanıcı bunu "uygulama yavaş/bağlanmıyor"
 * olarak görüyor.
 *
 * Çözüm (kullanıcı verisi SİLİNMEZ, yalnız deneme sırası akıllanır):
 *  - Art arda başarısız olan adres artan süreyle **devre dışı** bırakılır
 *    (30 sn → 2 dk → 5 dk → 15 dk).
 *  - Bir denemede **tek sağlıklı adres** seçilir; hepsi devre dışıysa
 *    en erken açılacak olan tek başına denenir (sonsuz beklemek yok).
 *  - Başarılı adres devre dışı listesinden çıkar ve sayaç sıfırlanır.
 *
 * Süreç-ömrü ile sınırlıdır (kalıcı değil): ağ değiştiğinde (evden çıkınca)
 * adres yeniden denenmelidir.
 */
object AddressHealth {

    /** İlk başarısızlıktan sonra bile adres hemen tamamen unutulmaz. */
    private val LADDER_MS = longArrayOf(30_000L, 120_000L, 300_000L, 900_000L)

    /** Aynı anda tutulan en fazla kayıt (sızıntı olmasın). */
    private const val MAX_ENTRIES = 64

    data class Status(
        val url: String,
        val failures: Int,
        val cooldownUntil: Long,
        val lastReason: String? = null,
    ) {
        fun coolingDown(now: Long): Boolean = cooldownUntil > now
        fun remainingMs(now: Long): Long = (cooldownUntil - now).coerceAtLeast(0L)
    }

    private val lock = Any()
    private val states = LinkedHashMap<String, Status>()

    /** Devre dışı olduğu için DENENMEYEN adres sayısı (tanı için sayaç). */
    @Volatile
    var skipped: Int = 0
        private set

    fun cooldownMs(failures: Int): Long =
        if (failures <= 0) 0L else LADDER_MS[minOf(failures, LADDER_MS.size) - 1]

    private fun key(profileId: String, url: String) = "$profileId|$url"

    @Synchronized
    fun status(profileId: String, url: String): Status =
        synchronized(lock) {
            states[key(profileId, url)] ?: Status(url, 0, 0L)
        }

    @Synchronized
    fun statuses(profileId: String, urls: List<String>, now: Long = System.currentTimeMillis()): List<Status> =
        urls.distinct().map { url ->
            val s = status(profileId, url)
            if (s.cooldownUntil in 1..now) s.copy(cooldownUntil = 0L) else s
        }

    /**
     * Başarısızlığı kaydeder ve adresin yeni durumunu döner.
     * Sayı arttıkça devre dışı süresi uzar.
     */
    @Synchronized
    fun noteFailure(
        profileId: String,
        url: String,
        reason: String? = null,
        now: Long = System.currentTimeMillis(),
    ): Status {
        val k = key(profileId, url)
        val prev = synchronized(lock) { states[k] } ?: Status(url, 0, 0L)
        val failures = prev.failures + 1
        val next = Status(
            url = url,
            failures = failures,
            cooldownUntil = now + cooldownMs(failures),
            lastReason = reason?.take(120) ?: prev.lastReason,
        )
        synchronized(lock) {
            states.remove(k)
            states[k] = next
            while (states.size > MAX_ENTRIES) {
                val oldest = states.keys.firstOrNull() ?: break
                states.remove(oldest)
            }
        }
        return next
    }

    /** Başarı: sayaç ve devre dışılık sıfırlanır. */
    @Synchronized
    fun noteSuccess(profileId: String, url: String): Boolean {
        val k = key(profileId, url)
        val prev = synchronized(lock) { states.remove(k) }
        return prev != null && prev.failures > 0
    }

    /**
     * Denenecek adres sırası — **tek sağlıklı adres** kuralıyla.
     *
     * @param preferred son çalışan adres (varsa öne alınır)
     */
    @Synchronized
    fun plan(
        profileId: String,
        candidates: List<String>,
        preferred: String? = null,
        now: Long = System.currentTimeMillis(),
    ): List<String> {
        val uniq = candidates.filter { it.isNotBlank() }.distinct()
        if (uniq.isEmpty()) return emptyList()
        val live = uniq.filter { !status(profileId, it).coolingDown(now) }
        if (live.isEmpty()) {
            // Hepsi devre dışı: en erken açılacak olanı tek başına dene.
            // (Hiç denememek, kullanıcıyı "bağlanmıyor" ekranında bırakırdı.)
            val picked = uniq.minByOrNull { status(profileId, it).cooldownUntil } ?: uniq.first()
            skipped += uniq.size - 1
            return listOf(picked)
        }
        skipped += uniq.size - live.size
        val head = preferred?.takeIf { it in live }
        return if (head != null) listOf(head) + live.filterNot { it == head } else live
    }

    /** Ayarlar ekranındaki "adresleri şimdi dene" düğmesi. */
    @Synchronized
    fun reset(profileId: String) {
        synchronized(lock) {
            states.keys.filter { it.startsWith("$profileId|") }.forEach { states.remove(it) }
        }
    }

    @Synchronized
    fun clear() {
        synchronized(lock) { states.clear() }
        skipped = 0
    }
}
