package com.hermes.mobile.ui

import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.data.SessionFlags
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

/**
 * Tur-16 — oturum çekmecesi karar mantığı (saf, Compose'suz, birim testli).
 *
 * Ekran kararları:
 *  - Liste = Tümü (oturumlar + hiç REST kaydı olmayan canlı oturumlar),
 *    Çalışanlar yalnız arama varken veya kullanıcı "Canlı" filtresini seçince.
 *  - Sıra: Sabitlenmiş → Bugün → Dün → Son 7 gün → Daha eski.
 *  - Arama başlık + son mesaj metninde anlılık (anlık) süzer; canlı
 *    oturumların REST kaydı varsa preview/sohbet eşlemesiyle aranır.
 *
 * Ham id yasağı (FR-001 tur-15 kalıbı): satırda gösterilecek her metin
 * [displayLabel] dan geçer — ham oturum id kalıbı çözülürse "? " düşer,
 * `20260913_184051_52f76a` / `cron_<hash>_...` ASLA başlık olmaz.
 */

/** Sohbette gösterilecek başlık — ham id sızıntısını "?" ile keser. */
fun displayLabel(title: String): String {
    val t = title.trim()
    if (t.isEmpty()) return "?"
    if (RAW_ID_LABEL.matches(t)) return "?"
    return t
}

/**
 * Ham süreç içi/cron/DB oturum kimliği kalıbı:
 * 20260913_184051_52f76a · 20260913_184051 · cron_&lt;hex&gt; ·
 * cron_&lt;hex&gt;_20260911_221601 · 8-32 karakterlik salt hex süreç içi id.
 */
private val RAW_ID_LABEL = Regex(
    """^(?:\d{8}_\d{6}(?:_[0-9a-f]+)?|cron_[0-9a-f]{4,}(?:_\d{8}_\d{6})?|[0-9a-f]{8,32})$""",
    RegexOption.IGNORE_CASE,
)

/** Çekmece çekirdek satırı — REST kaydı, canlı durum, çözümlenmiş başlık. */
data class DrawerRow(
    val key: String,
    val liveId: String,
    val dbId: String,
    val title: String,
    /** meaningfulPreview ile ayıklanmış tek satır (boş olabilir). */
    val preview: String,
    /** epoch saniye (sıralama/saat) — 0 bilinmiyor. */
    val epochSeconds: Double,
    /** boş · green · yellow · grey (çalışan/bekleyen/bitti). */
    val dot: String,
    val working: Boolean,
    val pinned: Boolean,
    val archived: Boolean,
    /** Satır şu an sohbette açık oturum mu. */
    val current: Boolean,
    /** Canlı liste kaydı (canlı sekmesi süzmesi için). */
    val liveSession: LiveSession?,
)

/** Çekmece satırı türleri (LazyColumn key'leri çakışmasın diye ayrı önek). */
sealed interface DrawerItem {
    val key: String

    data class Header(override val key: String, val labelTr: String, val labelEn: String) : DrawerItem
    data class RowItem(override val key: String, val row: DrawerRow) : DrawerItem
}

/** Zaman grupları — sıra bu liste tertibinde. */
enum class DrawerGroup(val labelTr: String, val labelEn: String) {
    Pinned("Sabitlenmiş", "Pinned"),
    Today("Bugün", "Today"),
    Yesterday("Dün", "Yesterday"),
    Last7("Son 7 gün", "Previous 7 days"),
    Earlier("Daha eski", "Earlier"),
}

/**
 * Zaman grubu (bugün/dün/eşikleri) — testte `today` sabitlenerek deterministik
 * kılınır. Üretimde `today = LocalDate.now()`.
 */
fun drawerTimeGroup(
    epochSeconds: Double,
    today: LocalDate = LocalDate.now(),
): DrawerGroup {
    if (epochSeconds <= 0) return DrawerGroup.Earlier
    val day = Instant.ofEpochSecond(floor(epochSeconds).toLong())
        .atZone(ZoneId.systemDefault()).toLocalDate()
    return when {
        day == today -> DrawerGroup.Today
        day == today.minusDays(1) -> DrawerGroup.Yesterday
        // Dünden 6 gün öncesine kadar = son 7 gün (bugün dâhil 7 günlük pencere).
        day.isAfter(today.minusDays(7)) -> DrawerGroup.Last7
        else -> DrawerGroup.Earlier
    }
}

/**
 * Kalıcı durum noktası kararı (renk adı; UI palet eşlemesi SessionDrawer'da):
 *  - boş: bayraksız, kaydı hiç olmamış gibi (boş id) — çizilmez
 *  - green: çalışıyor / bekliyor / REST'te açık
 *  - yellow: bekliyor ama REST açık değil (istemek yerine ayrım)
 *  - grey: sonlanmış
 */
fun drawerStatusDot(
    working: Boolean,
    waitingOrStarting: Boolean,
    sessionActive: Boolean,
    sessionRecord: Boolean,
): String = when {
    working -> "green"
    waitingOrStarting && !sessionActive -> "yellow"
    waitingOrStarting || sessionActive -> "green"
    !sessionRecord -> ""
    else -> "grey"
}

/**
 * Çekmece satırlarını üretir.
 *
 * @param sessions REST kayıtları (AppViewModel.sessions — polling ezer, bayrak
 *   mapper'ı burada uygulanır: hidden her zaman düşer, archived tercih).
 * @param live gateway açık listesi; REST eşi olmayan kayıtlar da satır olur.
 * @param liveByDbId REST id → canlı kayıt eşlemesi.
 * @param currentSessionId sohbetteki oturum (süreç içi id ya da dbId).
 *
 * Sıra: Sabitlenmiş → Bugün → Dün → Son 7 gün → Daha eski; grup içinde en
 * yeni önce, sabitlenmiş grup içinde sabitlenme sırası korunur (giriş sırası).
 * Arşiv tercihi: `showArchived=false` iken arşivli satırlar çizilmez (tur-14
 * SessionsScreen davranışıyla tutarlı — FR-009 regresyon yok).
 */
fun drawerRows(
    sessions: List<HermesSession>,
    live: List<LiveSession>,
    liveByDbId: Map<String, LiveSession>,
    flags: SessionFlags,
    cronNames: Map<String, String>,
    showArchived: Boolean = false,
    currentSessionId: String? = null,
): List<DrawerRow> {
    val used = HashSet<String>()
    val out = ArrayList<DrawerRow>()

    sessions
        .filter { it.id !in flags.hidden }
        .filter { showArchived || it.id !in flags.archived }
        .forEach { s ->
            val l = liveByDbId[s.id]
            l?.id?.let { used.add(it) }
            used.add(s.id)
            val title = displayLabel(readableTitle(s, flags, cronNames))
            val pv = meaningfulPreview(s.preview, 80)
            out.add(
                DrawerRow(
                    key = s.id,
                    liveId = l?.id.orEmpty(),
                    dbId = s.id,
                    title = title,
                    // Başlık zaten önizlemeden türediyse satırda yinelenmesin
                    // (tur-4 SessionRow kuralı).
                    preview = if (pv.isNotBlank() && pv != title) pv else "",
                    epochSeconds = maxOf(
                        s.startedAt ?: 0.0,
                        l?.let { maxOf(it.lastActive, it.startedAt) } ?: 0.0,
                    ),
                    dot = drawerStatusDot(
                        working = l?.isWorking == true,
                        waitingOrStarting = l?.isWaiting == true || l?.isStarting == true,
                        sessionActive = s.isActive || l != null,
                        sessionRecord = true,
                    ),
                    working = l?.isWorking == true || s.isActive,
                    pinned = s.id in flags.pinned,
                    archived = s.id in flags.archived,
                    current = currentSessionId != null &&
                        (currentSessionId == s.id || currentSessionId == l?.id),
                    liveSession = l,
                ),
            )
        }

    // REST eşi olmayan canlı kayıtlar (gateway belleğinde, DB'siz oturumlar).
    live.forEach { l ->
        if (l.id in used || l.dbId in used) return@forEach
        if (l.id.isNotBlank()) used.add(l.id)
        used.add(l.dbId)
        val title = displayLabel(
            liveSessionTitle(l, null, flags, cronNames)
        )
        val pv = meaningfulPreview(l.preview, 80)
        out.add(
            DrawerRow(
                key = "live-${l.dbId.ifBlank { l.id }}",
                liveId = l.id,
                dbId = l.dbId,
                title = title,
                preview = if (pv.isNotBlank() && pv != title) pv else "",
                epochSeconds = maxOf(l.lastActive, l.startedAt),
                dot = drawerStatusDot(
                    working = l.isWorking,
                    waitingOrStarting = l.isWaiting || l.isStarting,
                    sessionActive = true,
                    sessionRecord = true,
                ),
                working = l.isWorking,
                pinned = l.dbId in flags.pinned,
                archived = false,
                current = currentSessionId != null &&
                    (currentSessionId == l.dbId || currentSessionId == l.id),
                liveSession = l,
            ),
        )
    }

    // Sıra: grup tertibi, grup içinde en yeni önce (sabitlenmiş: giriş sırası).
    return out.sortedWith(
        compareByDescending<DrawerRow> { it.pinned }
            .thenByDescending { it.epochSeconds },
    )
}

/**
 * Liste + arama + sekme süzmesi + grup başlıkları.
 *
 *  - `query` boş değilken: düz liste (başlıksız), başlık + önizleme + model +
 *    kaynakta anlılık arama.
 *  - `liveOnly` (Canlı sekmesi): yalnız çalışan/bekleyen/REST-açık satırlar.
 *  - Tümü sekmesi ve `rows.size < GROUPING_MIN`: başlıksız düz liste
 *    (tur-14 GROUPING_MIN eşiği korunur).
 *  - Aksi: Sabitlenmiş / Bugün / Dün / Son 7 gün / Daha eski başlıkları
 *    (yalnız dolu gruplar çizilir).
 */
fun drawerListItems(
    rows: List<DrawerRow>,
    query: String,
    liveOnly: Boolean,
    grouping: Boolean,
    today: LocalDate = LocalDate.now(),
): List<DrawerItem> {
    val q = query.trim()
    var list = rows
    if (liveOnly) {
        list = list.filter { it.working || it.dot.isNotEmpty() && it.dot != "grey" }
    }
    if (q.isNotBlank()) {
        val ql = q.lowercase()
        list = list.filter {
            it.title.lowercase().contains(ql) ||
                it.preview.lowercase().contains(ql)
        }
    }
    if (!grouping || q.isNotBlank() || list.size < GROUPING_MIN) {
        return list.map { DrawerItem.RowItem("r-${it.key}", it) }
    }
    val out = ArrayList<DrawerItem>()
    val pinned = list.filter { it.pinned }
    val rest = list.filter { !it.pinned }
    if (pinned.isNotEmpty()) {
        out += DrawerItem.Header("hdr-Pinned", DrawerGroup.Pinned.labelTr, DrawerGroup.Pinned.labelEn)
        pinned.forEach { out += DrawerItem.RowItem("r-${it.key}", it) }
    }
    for (g in listOf(DrawerGroup.Today, DrawerGroup.Yesterday, DrawerGroup.Last7, DrawerGroup.Earlier)) {
        val items = rest.filter { drawerTimeGroup(it.epochSeconds, today) == g }
        if (items.isNotEmpty()) {
            out += DrawerItem.Header("hdr-${g.name}", g.labelTr, g.labelEn)
            items.forEach { out += DrawerItem.RowItem("r-${it.key}", it) }
        }
    }
    return out
}

/**
 * Son oturumu hatırlama — geri yükleme kararı (saf):
 *  - kayıt boş → null (yeni sohbet kalsın)
 *  - kayıt sohbette zaten açıksa → null (gereksiz yeniden bağlanma yok)
 *  - kayıt listede VARSA ya da canlıysa → bağlan
 *  - kayıt listede YOKSA (silinen/arşivlenmiş/eşleşmeyen) → null
 */
fun lastSessionToRestore(
    lastId: String,
    rows: List<DrawerRow>,
    currentSessionId: String?,
): DrawerRow? {
    if (lastId.isBlank()) return null
    if (currentSessionId == lastId) return null
    val row = rows.firstOrNull { it.dbId == lastId || it.liveId == lastId || it.key == lastId }
        ?: return null
    if (currentSessionId != null && row.current) return null
    return row
}
