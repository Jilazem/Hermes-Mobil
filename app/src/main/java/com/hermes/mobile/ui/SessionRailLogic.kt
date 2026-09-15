package com.hermes.mobile.ui

import com.hermes.mobile.data.LiveSession

/**
 * Sol ray (oturum şeridi) model kararı — Tur-8.
 *
 * Kullanıcı şikâyeti: "2. bir session açınca sol panelde görünmüyor". Kök neden:
 * ray yalnız `working/waiting/starting` + o anki oturumu süzüyordu; sunucunun
 * AÇIK saydığı (idle) oturumlar bir sonraki oturuma geçilir geçilmez düşüyordu
 * (üst başlık "4 açık oturum" derken ray tek hücre gösteriyordu).
 *
 * Yeni kural: ray = SUNUCUNUN AÇIK LİSTESİ ∪ UYGULAMA İÇİ SON AÇILANLAR
 * (∪ geçerli oturum). Sunucu sahiptir: bir oturum daha önce listede görülüp
 * sonra düşmüşse "kapandı" demektir ve ray'dan iner; hiç görülmemiş bir kayıt
 * (REST'ten açılan geçmiş oturum) ise kullanıcı kapatana kadar kalır.
 *
 * Saf fonksiyonlar — Compose'a bağlı değil, birim testle doğrulanır
 * (`SessionRailTur8Test`).
 */

/** Ray tavanı: kullanıcı 5 oturumla test ediyor, 6 rahat sığar (48dp × 6). */
const val RAIL_LIMIT = 6

/** Uygulama içi "son açılanlar" halkasının boyu — ray tavanından büyük ki
 *  kapanan/elenen kayıt yerine yenisi gelebilsin. */
const val RAIL_RECENT_MAX = 12

const val RAIL_STATUS_WORKING = "working"
const val RAIL_STATUS_WAITING = "waiting"
const val RAIL_STATUS_STARTING = "starting"
const val RAIL_STATUS_IDLE = "idle"
const val RAIL_STATUS_DONE = "done"

/**
 * Uygulama içi "son açılanlar" kaydı — kullanıcı bir oturuma bağlandığında
 * (ray, Oturumlar paneli, paylaşım hedefi) yazılır.
 *
 * @param liveId gateway süreç içi kimliği (steer/activate bunu bekler); geçmiş
 *   oturumda boş olabilir.
 * @param dbId veritabanı kimliği (REST `/messages` bunu bekler).
 * @param title açılışta ÇÖZÜLMÜŞ başlık (ham id asla).
 * @param openedAt `System.currentTimeMillis()` — sıralama için (sunucunun
 *   `last_active` saniyesiyle aynı ölçeğe indirilir).
 * @param seenLive bu kayıt sunucunun açık listesinde en az bir kez görüldü mü:
 *   görüldüyse ve şimdi yoksa sunucu kapatmıştır → ray'dan iner.
 */
data class RecentRailSession(
    val liveId: String = "",
    val dbId: String = "",
    val title: String = "",
    val openedAt: Long = 0L,
    val seenLive: Boolean = false,
) {
    val key: String get() = dbId.ifBlank { liveId }
}

/** Ray hücresi — canlı ve uygulama içi kayıtların birleşik görünümü. */
data class RailEntry(
    /** Kararlı anahtar (dbId, yoksa süreç içi id): seçim/eleme bununla. */
    val key: String,
    val liveId: String,
    val dbId: String,
    val title: String,
    /** working · waiting · starting · idle · done */
    val status: String,
    /** Sunucunun açık listesinde mi (aksi: yalnız uygulama içi kayıt). */
    val live: Boolean,
    val current: Boolean,
) {
    val isWorking: Boolean get() = status == RAIL_STATUS_WORKING
    val isWaiting: Boolean get() = status == RAIL_STATUS_WAITING
    val isStarting: Boolean get() = status == RAIL_STATUS_STARTING

    /** Müdahale gerektiren (çalışan) hücreler vurgulanır. */
    val busy: Boolean get() = isWorking || isWaiting || isStarting
}

private data class RailCandidate(val key: String, var entry: RailEntry, val activity: Double)

/**
 * Ray hücrelerini üretir.
 *
 * @param live sunucunun açık listesi (`session.active_list`) — durum ne olursa
 *   olsun TÜMÜ adaydır (idle dahil; "açık" demek budur).
 * @param recent uygulama içi son açılanlar, EN YENİ ÖNCE.
 * @param currentSessionId açık sohbetin kimliği (süreç içi id ya da dbId).
 * @param liveLoaded `active_list` en az bir kez başarıyla geldi mi: false iken
 *   "sunucuda yok" kanıt sayılmaz (ağ sarsıntısında ray boşalmasın).
 * @param dismissed kullanıcının uzun basıp kapattığı anahtarlar (geçerli
 *   oturum yine gösterilir).
 * @param titleOf canlı kaydın okunabilir başlığı (`liveSessionTitle` zinciri).
 */
fun railEntries(
    live: List<LiveSession>,
    recent: List<RecentRailSession>,
    currentSessionId: String?,
    liveLoaded: Boolean = true,
    dismissed: Set<String> = emptySet(),
    titleOf: (LiveSession) -> String = { it.title },
    limit: Int = RAIL_LIMIT,
): List<RailEntry> {
    // 1) Sunucu listesini tekille: aynı dbId'yi iki süreç içi kayıt
    //    paylaşabiliyor (tur-5 dersi) — en son etkin olan kalır. Ayrıca süreç
    //    içi id'nin kendisi de tekilleştirilir (aynı oturum iki kayıtla gelir).
    val seen = HashSet<String>()
    val liveOrdered = live
        .filter { it.id.isNotBlank() || it.dbId.isNotBlank() }
        .sortedWith(
            compareByDescending<LiveSession> { it.lastActive }
                .thenByDescending { it.startedAt }
                .thenBy { it.id },
        )
        .filter { s ->
            val dbOk = s.dbId.isBlank() || seen.add(s.dbId)
            val liveOk = s.id.isBlank() || seen.add(s.id)
            dbOk && liveOk
        }

    val byKey = HashMap<String, LiveSession>()
    liveOrdered.forEach { s ->
        if (s.dbId.isNotBlank()) byKey.putIfAbsent(s.dbId, s)
        if (s.id.isNotBlank()) byKey.putIfAbsent(s.id, s)
    }

    val used = HashSet<String>()
    val out = ArrayList<RailCandidate>()

    fun currentOf(dbId: String, liveId: String): Boolean =
        currentSessionId != null &&
            (currentSessionId == dbId || currentSessionId == liveId)

    // 2) Uygulama içi son açılanlar (en yeni önce) — sunucu listesinde
    //    karşılığı varsa durum oradan alınır.
    recent.forEach { r ->
        val l = byKey[r.dbId] ?: byKey[r.liveId]
        val dbId = l?.dbId?.ifBlank { r.dbId } ?: r.dbId.ifBlank { r.liveId }
        val liveId = l?.id ?: r.liveId
        val idKey = dbId.ifBlank { liveId }
        if (idKey.isBlank()) return@forEach
        if (dbId.isNotEmpty() && dbId in used) return@forEach
        if (liveId.isNotEmpty() && liveId in used) return@forEach
        val isCurrent = currentOf(dbId, liveId)
        // Sunucuda görülmüş ve şimdi listede yok → kapanmış (kanıt varsa iner).
        if (l == null && r.seenLive && liveLoaded && !isCurrent) return@forEach
        if (dbId.isNotEmpty()) used.add(dbId)
        if (liveId.isNotEmpty()) used.add(liveId)
        val status = when {
            l != null -> statusOf(l)
            else -> RAIL_STATUS_DONE
        }
        val title = (l?.let { titleOf(it) }).orEmpty().ifBlank { r.title }
        out.add(
            RailCandidate(
                key = idKey,
                entry = RailEntry(
                    key = idKey,
                    liveId = liveId,
                    dbId = dbId.ifEmpty { liveId },
                    title = title,
                    status = status,
                    live = l != null,
                    current = isCurrent,
                ),
                activity = maxOf(
                    l?.let { maxOf(it.lastActive, it.startedAt) } ?: 0.0,
                    r.openedAt / 1000.0,
                ),
            ),
        )
    }

    // 3) Sunucunun açık listesinde olup uygulamada henüz açılmamış oturumlar
    //    (Telegram/cron/CLI'dan başlamış işler).
    liveOrdered.forEach { s ->
        if (s.dbId in used || s.id in used) return@forEach
        if (s.dbId.isNotEmpty()) used.add(s.dbId)
        if (s.id.isNotEmpty()) used.add(s.id)
        val key = s.dbId.ifBlank { s.id }
        out.add(
            RailCandidate(
                key = key,
                entry = RailEntry(
                    key = key,
                    liveId = s.id,
                    dbId = s.dbId.ifBlank { s.id },
                    title = titleOf(s),
                    status = statusOf(s),
                    live = true,
                    current = currentOf(s.dbId.ifBlank { s.id }, s.id),
                ),
                activity = maxOf(s.lastActive, s.startedAt),
            ),
        )
    }

    // 4) Kullanıcı kapatmış hücreler düşer — geçerli oturum hariç (yeniden
    //    açılan oturum hem buradan çıkar hem listeye geri girer).
    val kept = out.filter { c ->
        val e = c.entry
        e.current || (c.key !in dismissed && e.dbId !in dismissed && e.liveId !in dismissed)
    }

    // 5) Sıra: en son etkileşim üstte; geçerli oturum her zaman en üstte.
    val ordered = kept.sortedWith(
        compareByDescending<RailCandidate> { it.entry.current }
            .thenByDescending { it.activity },
    )

    return ordered.map { it.entry }.take(limit.coerceAtLeast(0))
}

/** Sunucu durum dizesi → ray durumu (bilinmeyen durum "idle" sayılır: açık). */
fun statusOf(s: LiveSession): String = when (s.status) {
    RAIL_STATUS_WORKING -> RAIL_STATUS_WORKING
    RAIL_STATUS_WAITING -> RAIL_STATUS_WAITING
    RAIL_STATUS_STARTING -> RAIL_STATUS_STARTING
    RAIL_STATUS_DONE -> RAIL_STATUS_IDLE
    else -> RAIL_STATUS_IDLE
}

/**
 * Başlıktaki "Y açık oturum" sayacı — ray ile AYNI kümeyi saysın diye tek
 * kaynak: sunucunun açık listesi, dbId'de tekilleştirilmiş.
 */
fun openSessionCount(live: List<LiveSession>): Int =
    live.filter { it.id.isNotBlank() || it.dbId.isNotBlank() }
        .distinctBy { it.dbId.ifBlank { it.id } }
        .size

/**
 * Uygulama içi halkaya kayıt ekler: EN YENİ ÖNCE, hem dbId hem süreç içi id
 * ile tekilleştirilmiş (tur-5 dersi: aynı oturum iki kimlikle gelebiliyor),
 * tavanla kesilmiş. Saf — ViewModel yalnız çağırır.
 */
fun pushRecent(
    old: List<RecentRailSession>,
    liveId: String,
    dbId: String,
    title: String,
    openedAt: Long,
    limit: Int = RAIL_RECENT_MAX,
): List<RecentRailSession> {
    if (liveId.isBlank() && dbId.isBlank()) return old
    val fresh = RecentRailSession(
        liveId = liveId,
        dbId = dbId,
        title = title,
        openedAt = openedAt,
    )
    return (
        listOf(fresh) + old.filterNot { r ->
            r.dbId == dbId || r.liveId == liveId ||
                (dbId.isNotBlank() && r.dbId == liveId) ||
                (liveId.isNotBlank() && r.liveId == dbId)
        }
        ).take(limit.coerceAtLeast(1))
}

/**
 * Yeniden açılan oturumun "kapatıldı" işaretini siler (tur-8): kullanıcı
 * kapattığı hücreyi sonradan yeniden açarsa ray'a geri gelir.
 */
fun clearRailDismissal(
    dismissed: Set<String>,
    liveId: String,
    dbId: String,
): Set<String> {
    if (dismissed.isEmpty()) return dismissed
    val out = dismissed - setOf(dbId, liveId).filter { it.isNotBlank() }.toSet()
    return if (out.isEmpty()) emptySet() else out
}

/**
 * Sunucunun açık listesinde görülen kayıtları "görüldü" diye damgalar.
 * Damga, kaydın sonradan listeden düşmesini "sunucu kapattı" olarak okumayı
 * sağlar; hiç görülmemiş kayıt (REST'ten açılmış geçmiş oturum) damgasız kalır.
 */
fun markSeenLive(
    recent: List<RecentRailSession>,
    ids: Set<String>,
): List<RecentRailSession> {
    if (ids.isEmpty() || recent.isEmpty()) return recent
    var changed = false
    val out = recent.map { r ->
        if (!r.seenLive && (r.dbId in ids || r.liveId in ids)) {
            changed = true
            r.copy(seenLive = true)
        } else {
            r
        }
    }
    return if (changed) out else recent
}
