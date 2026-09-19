package com.hermes.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.AppState
import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.SessionFlags
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

/**
 * Oturum eylem menüsü öğeleri — long-press menüsü ModalBottomSheet'e taşıyınca
 * menü içeriğini UI'dan bağımsız test edilebilir kılmak için saf fonksiyon.
 *
 * `actionsEnabled`: bitmiş oturumda (isActive=false) arşivle/sil/durdur/buda
 * açık; çalışan oturumda yalnızca yeniden adlandır ve sabitle kullanılabilir.
 * (Eski DropdownMenuItem davranışının aynısı.)
 */
data class SessionMenuActions(
    val rename: Boolean = true,
    val stop: Boolean = false,
    val compress: Boolean = false,
    val archive: Boolean = false,
    val delete: Boolean = false,
    /** Menüden arşive geçildiğinde etiket değişir: Arşivle ↔ Arşivden çıkar. */
    val isArchived: Boolean = false,
)

fun sessionMenuActions(
    session: HermesSession,
    flags: SessionFlags,
): SessionMenuActions {
    val actionsEnabled = !session.isActive
    return SessionMenuActions(
        rename = true,
        stop = actionsEnabled,
        compress = actionsEnabled,
        archive = actionsEnabled,
        delete = actionsEnabled,
        isArchived = session.id in flags.archived,
    )
}


/** Gruplama başlıkları bu eşiğin altında kalkar — kısa listede gürültü olur. */
const val GROUPING_MIN = 3

/** Bayrak çözümlü başlık: `HermesSession.title`a dokunulmaz, override burada. */
fun displayTitle(session: HermesSession, flags: SessionFlags): String =
    flags.renames[session.id] ?: session.title

/** `cron_<12 hanelik hex iş id>_<tarih>_<saat>` oturum kalıbı. */
private val CRON_SESSION_ID = Regex("""^cron_([0-9a-f]{12})_""")

/**
 * `cron_2e4ea303c123_20260911_221601` → "11.09 22:16" gibi okunabilir bir
 * zaman damgası tabanı; kalıp tanınmazsa null. Kullanıcı `cron_...` ham
 * id'sini değil, işin adıyla birlikte kısa tarih/saat görsün diye.
 */
private fun cronStampBase(id: String): String? {
    val parts = id.split("_")
    // cron + hash + yyyyMMdd + HHmmss beklenir.
    if (parts.size < 4) return null
    val date = parts.getOrNull(2) ?: return null
    val time = parts.getOrNull(3) ?: return null
    if (date.length != 8 || time.length != 6) return null
    if (!date.all { it.isDigit() } || !time.all { it.isDigit() }) return null
    return "${date.takeLast(2)}.${date.substring(4, 6)} ${time.take(2)}:${time.substring(2, 4)}"
}

/**
 * Önizlemeyi tek satırlık kart başlığına çevirir: satırsonları boşa, fazla
 * boşluklar teke indirilir, `maxLen` karaktere kırpılır (kabarcık taşıyla).
 * Boş/ yok önizleme → "".
 *
 * NOT (tur-4): makine çıktısı ayıklaması [meaningfulPreview] yapar; bu fonksiyon
 * yalnız düzleştirme/kırpma yapar ve mevcut çağrılar için korunur.
 */
fun previewLine(preview: String?, maxLen: Int = 60): String {
    val flat = preview?.replace('\n', ' ')?.replace('\r', ' ')
        ?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    if (flat.length <= maxLen) return flat
    return flat.take(maxLen).trimEnd() + "…"
}

/** Başlığın hangi kuraldan geldiği — kart anatomisi ikincil satırı buna bağlı. */
enum class TopicKind { Rename, ServerTitle, GatewayTitle, CronName, Preview, Fallback }

data class SessionTopic(val text: String, val kind: TopicKind)

/**
 * Okunabilir oturum başlığı — tur-4 sırası (büyük model danışması, bağlayıcı):
 * 1. kullanıcının yerel yeniden adlandırması (`flags.renames`),
 * 2. sunucunun ANLAMLI başlığı (`title` / `title_source`),
 * 3. canlı oturumun okunaklı gateway başlığı (`displayName`),
 * 4. ilk ANLAMLI kullanıcı cümlesi (`preview`, ~40 karakter),
 * 5. `cron_<hash>_<zaman>` + bilinen cron iş adı → "İş Adı · gg.AA ss:dd",
 * 6. "Sohbet · gg.AA ss:dd" (damga çözülebiliyorsa),
 * 7. "Sohbet".
 *
 * Kişi/kanal adı ("Gökhan Uzman"), kaynak adı ("Telegram", "Masaüstü") ve ham
 * makine çıktısı (JSON/tool/cron sistem metni) KONU SAYILMAZ; ham session id'si
 * bu fonksiyondan birincil başlık olarak ASLA çıkamaz.
 * Saf fonksiyon — Compose'suz test edilebilir.
 */
fun sessionTopic(
    session: HermesSession,
    flags: SessionFlags,
    cronNames: Map<String, String>,
    en: Boolean = false,
): SessionTopic {
    val chatWord = if (en) "Chat" else "Sohbet"
    flags.renames[session.id]?.takeIf { it.isNotBlank() }?.let {
        return SessionTopic(it, TopicKind.Rename)
    }
    session.serverTitle?.takeIf { meaningfulTopic(it, session.id) }?.let {
        return SessionTopic(it.trim(), TopicKind.ServerTitle)
    }
    session.displayName?.takeIf { meaningfulTopic(it, session.id) }?.let {
        return SessionTopic(it.trim(), TopicKind.GatewayTitle)
    }
    topicFromPreview(session.preview).takeIf { it.isNotBlank() }?.let {
        return SessionTopic(it, TopicKind.Preview)
    }
    CRON_SESSION_ID.find(session.id)?.groupValues?.get(1)
        ?.let { hash -> cronNames[hash]?.takeIf { it.isNotBlank() } }
        ?.let { jobName ->
            val stamp = cronStampBase(session.id) ?: stampFromRawId(session.id)
            return SessionTopic(
                if (stamp != null) "$jobName · $stamp" else jobName,
                TopicKind.CronName,
            )
        }
    val stamp = stampFromRawId(session.id) ?: cronStampBase(session.id)
    return if (stamp != null) SessionTopic("$chatWord · $stamp", TopicKind.Fallback)
    else SessionTopic(chatWord, TopicKind.Fallback)
}

/** Başlık adayı konu mudur? Ham id, kişi/kaynak adı ve makine çıktısı değilse evet. */
private fun meaningfulTopic(candidate: String, sessionId: String): Boolean {
    val t = candidate.trim()
    if (t.isBlank() || t == sessionId) return false
    if (isMachineNoise(t)) return false
    if (isGenericIdentityTitle(t)) return false
    return true
}

fun readableTitle(
    session: HermesSession,
    flags: SessionFlags,
    cronNames: Map<String, String>,
    en: Boolean = false,
): String = sessionTopic(session, flags, cronNames, en).text

/**
 * Kart ikincil satırı: başlık GERÇEK bir konuysa (rename / sunucu başlığı /
 * canlı gateway başlığı / ilk kullanıcı cümlesi / cron iş adı) altında soluk
 * DAMGA gösterilir; başlık zaten zaman damgası yedeğiyse yinelenmesin diye
 * null döner.
 *
 * Tur-5 (KALAN-5): kaynak ADI bu satırdan ÇIKARILDI. Kaynak artık kartın
 * solundaki tek 12 dp ikonla anlatılıyor; adı metin olarak da yazmak aynı
 * bilgiyi iki kez veriyordu ("📅 Zamanlanmış görev", "✈ Telegram · 15.09 10:20")
 * ve liste gürültülü görünüyordu. Ikonun okunur adı `contentDescription`'da
 * kaldığı için erişilebilirlik kaybı yok.
 */
fun cardSubtitle(
    session: HermesSession,
    flags: SessionFlags,
    cronNames: Map<String, String>,
    en: Boolean = false,
): String? {
    val topic = sessionTopic(session, flags, cronNames, en)
    if (topic.kind == TopicKind.Fallback) return null
    return stampFromRawId(session.id)
}

/**
 * Görünecek oturumlar: gizlenenler (silinenler) çıkar, arşiv gösterim tercihi
 * uygulanır; sabitlenenler en üste, sonra başlangıç zamanına göre eskiler.
 */
fun visibleSessions(
    sessions: List<HermesSession>,
    flags: SessionFlags,
    showArchived: Boolean,
): List<HermesSession> =
    sessions
        .filter { it.id !in flags.hidden && (showArchived || it.id !in flags.archived) }
        .sortedWith(
            compareByDescending<HermesSession> { it.id in flags.pinned }
                .thenByDescending { it.startedAt ?: 0.0 },
        )

/**
 * Boş/kırık kart eşiği (tur-14). "yalnız kaynak chip'i taşıyan contentsiz kart"
 * şikâyeti: ne başlığı ne önizlemesi olan, mesajı olmayan ve asla canlı olmamış
 * oturum listeyi doldurur ama hiçbir bilgi taşımaz. Böyle bir kart ya ANLAMLI
 * gösterilmeli ya gizlenmeli — karar: gizle (yedek başlık "Sohbet · gg.AA"
 * zaten zaman veriyor; yine de içeriksizse kart tıklanabilir boşluk olur).
 *
 * Saf — JVM testi (SessionCardAnatomyTest).
 */
fun isPlaceholderSession(s: HermesSession): Boolean {
    val title = s.serverTitle ?: s.displayName
    // Ham id ile aynı "başlık" başlık değildir (sunucu çoğu oturumda id basar);
    // "Session 11" kalıbı da DemoMask'sız generic sayaç başlığıdır.
    val titleReal = title?.takeIf { it.isNotBlank() && it != s.id && !isGenericIdentityTitle(it) }
    val hasTitle = titleReal != null
    val hasPreview = meaningfulPreview(s.preview).isNotBlank()
    val hasMessages = s.messageCount > 0
    return !hasTitle && !hasPreview && !hasMessages && !s.isActive
}

/** [visibleSessions] sonrası ikinci süzgeç: içeriksiz kartları ele. */
fun visibleSessionsFiltered(
    sessions: List<HermesSession>,
    flags: SessionFlags,
    showArchived: Boolean,
): List<HermesSession> = visibleSessions(sessions, flags, showArchived).filter { !isPlaceholderSession(it) }

/**
 * Kaynak chip'i — kart alt satırında insan-okur etiket. Ham iç ad (api_server,
 telegram_dm) kısaltılır; tanınmayan ad olduğu gibi kısa gösterilir, boşsa null.
 */
fun sourceChipLabel(source: String?): String? {
    val t = source?.trim().orEmpty()
    if (t.isEmpty()) return null
    return when (t.lowercase()) {
        "tui", "cli" -> "TUI"
        "telegram", "telegram_dm", "tg" -> "Telegram"
        "whatsapp", "wa" -> "WhatsApp"
        "cron", "scheduler" -> "Zamanlanmış"
        "api", "api_server" -> "API"
        "web" -> "Web"
        else -> t.take(12)
    }
}

/** Zaman grubu — test edilebilir, UI'dan bağımsız saf fonksiyon. */
enum class TimeGroup(val labelTr: String, val labelEn: String) {
    Bugun("Bugün", "Today"),
    Dun("Dün", "Yesterday"),
    Oncekiler("Öncekiler", "Earlier"),
}

fun timeGroup(startedAt: Double?): TimeGroup {
    if (startedAt == null || startedAt <= 0) return TimeGroup.Oncekiler
    val day = Instant.ofEpochSecond(startedAt.toLong())
        .atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (day) {
        today -> TimeGroup.Bugun
        today.minusDays(1) -> TimeGroup.Dun
        else -> TimeGroup.Oncekiler
    }
}

/** LazyColumn'ın tek satırı: ya grup başlığı ya oturum. */
sealed interface SessionListItem {
    val key: String

    data class Header(
        override val key: String,
        val labelTr: String,
        val labelEn: String,
    ) : SessionListItem

    data class Session(val session: HermesSession) : SessionListItem {
        override val key: String get() = "s-${session.id}"
    }
}

/**
 * Sıra: Sabitlenenler → Bugün → Dün → Öncekiler. Başlıksız istenirse
 * (`grouping = false`, sorgu aktifken) düz liste döner; sabitleme sırası
 * `visibleSessions`dan gelir, korunur. Yalnız dolu gruplar başlık alır.
 */
fun sessionListItems(
    shown: List<HermesSession>,
    flags: SessionFlags,
    grouping: Boolean,
): List<SessionListItem> {
    if (!grouping || shown.size < GROUPING_MIN) return shown.map { SessionListItem.Session(it) }
    val out = mutableListOf<SessionListItem>()
    val pinned = shown.filter { it.id in flags.pinned }
    val rest = shown.filter { it.id !in flags.pinned }
    if (pinned.isNotEmpty()) {
        out += SessionListItem.Header("hdr-Pinned", "Sabitlenenler", "Pinned")
        out += pinned.map { SessionListItem.Session(it) }
    }
    for (group in TimeGroup.entries) {
        val items = rest.filter { timeGroup(it.startedAt) == group }
        if (items.isNotEmpty()) {
            out += SessionListItem.Header("hdr-${group.name}", group.labelTr, group.labelEn)
            out += items.map { SessionListItem.Session(it) }
        }
    }
    return out
}

