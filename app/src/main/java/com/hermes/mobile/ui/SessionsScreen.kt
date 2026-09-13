package com.hermes.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
 * Okunabilir oturum başlığı — öncelik sırası:
 * 1. kullanıcının yerel yeniden adlandırması (`flags.renames`),
 * 2. sunucudan gelen başlık (Telegram `display_name` zaten `title`a akar;
 *    ham id'den FARKLIYSA),
 * 3. `cron_<hash>_<zaman>` id'si + bilinen cron iş adı → "İş Adı · gg.AA ss:dd",
 * 4. kaynak etiketi + id'deki zaman damgası → "TUI · 13.09 18:40"
 *    (damga yoksa yalnız etiket: "CLI", "Masaüstü", "Zamanlanmış görev"…),
 * 5. kaynak yok ama damga var → "Oturum · 13.09 18:40",
 * 6. hiçbir şey yok → "Oturum". Ham session id'si BU FONKSİYONDAN
 *    birincil başlık olarak ASLA çıkamaz (boss şikâyeti: "session adları
 *    anlaşılmaz").
 * Saf fonksiyon — Compose'suz test edilebilir.
 */
fun readableTitle(
    session: HermesSession,
    flags: SessionFlags,
    cronNames: Map<String, String>,
): String {
    flags.renames[session.id]?.takeIf { it.isNotBlank() }?.let { return it }
    session.title.takeIf { it.isNotBlank() && it != session.id }?.let { return it }
    CRON_SESSION_ID.find(session.id)?.groupValues?.get(1)
        ?.let { hash -> cronNames[hash]?.takeIf { it.isNotBlank() } }
        ?.let { jobName ->
            val stamp = cronStampBase(session.id)
            return if (stamp != null) "$jobName · $stamp" else jobName
        }
    val stamp = stampFromRawId(session.id)
    val label = sessionSourceLabel(session.source)
    return when {
        label != null && stamp != null -> "$label · $stamp"
        label != null -> label
        stamp != null -> "Oturum · $stamp"
        else -> "Oturum"
    }
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

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun SessionsScreen(
    state: AppState,
    onOpen: (HermesSession) -> Unit,
    onContinue: (HermesSession) -> Unit = {},
    onTogglePin: (String) -> Unit = {},
    onSetArchived: (String, Boolean) -> Unit = { _, _ -> },
    onRename: (String, String) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    onRefreshSessions: () -> Unit = {},
    /** '/stop' slashExec ile oturumu durdurur (AlertDialog onayı ekran tarafında). */
    onStop: (HermesSession) -> Unit = {},
    /** '/compress' ile bağlamı sıkıştırır — sohbet kalır. */
    onBuda: (HermesSession) -> Unit = {},
) {
    val flags = state.flags
    val cronNames = state.cronNames
    var query by rememberSaveable { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var menuSession by remember { mutableStateOf<HermesSession?>(null) }
    var renameTarget by remember { mutableStateOf<HermesSession?>(null) }
    var deleteTarget by remember { mutableStateOf<HermesSession?>(null) }
    var stopTarget by remember { mutableStateOf<HermesSession?>(null) }
    // Bildirim metni composable tarafında çözülüyor (S.t2); burada yalnız bayrak.
    var removedNotice by remember { mutableStateOf(false) }

    // İki aşama: başlıklar için taban liste, arama için süzülmüş hâli.
    val base = remember(state.sessions, flags, showArchived) {
        visibleSessions(state.sessions, flags, showArchived)
    }
    val shown = remember(base, query, cronNames) {
        val q = query.trim()
        if (q.isBlank()) base else base.filter {
            readableTitle(it, flags, cronNames).contains(q, true) ||
                (it.model ?: "").contains(q, true) ||
                (it.source ?: "").contains(q, true)
        }
    }
    val items = remember(shown, flags, query) {
        sessionListItems(shown, flags, grouping = query.isBlank())
    }
    val archivedCount = state.sessions.count { it.id in flags.archived && it.id !in flags.hidden }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(S.t2("Oturumlar", "Sessions"), color = HermesColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${base.count { it.isActive }} ${S.t2("etkin", "active")} · ${base.size} ${S.t2("toplam", "total")}",
                    color = HermesColors.TextMuted,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        SearchField(
            query = query,
            onChange = { query = it; removedNotice = false },
        )

        if (archivedCount > 0) {
            Spacer(Modifier.height(8.dp))
            AssistChip(
                onClick = { showArchived = !showArchived },
                label = {
                    Text(
                        if (showArchived) S.t2("Arşiv gizleniyor", "Archive hidden")
                        else S.t2("Arşiv göster (${archivedCount})", "Show archive (${archivedCount})"),
                        color = if (showArchived) HermesColors.TextPrimary else HermesColors.TextMuted,
                        fontSize = 12.sp,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Archive,
                        null,
                        tint = if (showArchived) HermesColors.Midground else HermesColors.TextMuted,
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
        }

        if (removedNotice) {
            Text(
                S.t2("Oturum cihazdan kaldırıldı", "Session removed from this device"),
                color = HermesColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        PullToRefreshBox(
            isRefreshing = state.pullRefreshing,
            onRefresh = onRefreshSessions,
            state = rememberPullToRefreshState(),
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (base.isEmpty() && query.isBlank()) {
                    item(key = "empty") {
                        HermesCard(Modifier.fillMaxWidth()) {
                            Text(
                                if (state.isConnected) S.t2("Henüz oturum yok.", "No sessions yet.")
                                else S.t2("Sunucuya bağlanınca oturumlar burada listelenir.", "Sessions appear here once the server is reachable."),
                                color = HermesColors.TextMuted,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                if (query.isNotBlank() && shown.isEmpty()) {
                    item(key = "no-result") {
                        HermesCard(Modifier.fillMaxWidth()) {
                            Text(
                                S.t2("\"$query\" için oturum yok", "No sessions for \"$query\""),
                                color = HermesColors.TextSecondary,
                                fontSize = 13.sp,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { query = "" }) {
                                    Text(S.t2("Aramayı temizle", "Clear search"), color = HermesColors.Midground, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                items(items, key = { it.key }) { entry ->
                    when (entry) {
                        is SessionListItem.Header -> Text(
                            S.t2(entry.labelTr, entry.labelEn),
                            color = HermesColors.TextFaint,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )

                        is SessionListItem.Session -> {
                            val session = entry.session
                            SessionRow(
                                session = session,
                                actions = sessionMenuActions(session, flags),
                                title = readableTitle(session, flags, cronNames),
                                pinned = session.id in flags.pinned,
                                archived = session.id in flags.archived,
                                menuOpen = menuSession?.id == session.id,
                                onClick = { onOpen(session) },
                                onContinue = { onContinue(session) },
                                onLongPress = { menuSession = session },
                                onDismissMenu = { menuSession = null },
                                onTogglePin = {
                                    menuSession = null
                                    onTogglePin(session.id)
                                },
                                onRename = {
                                    menuSession = null
                                    renameTarget = session
                                },
                                onToggleArchived = {
                                    menuSession = null
                                    onSetArchived(session.id, session.id !in flags.archived)
                                },
                                onDelete = {
                                    menuSession = null
                                    deleteTarget = session
                                },
                                onStop = {
                                    menuSession = null
                                    stopTarget = session
                                },
                                onBuda = {
                                    menuSession = null
                                    onBuda(session)
                                },
                            )
                        }
                    }
                }

                if (archivedCount > 0) {
                    item(key = "archived-toggle") {
                        Text(
                            if (showArchived) S.t2("Arşivlenmişleri gizle", "Hide archived")
                            else S.t2("Arşivlenmişleri göster ($archivedCount)", "Show archived ($archivedCount)"),
                            color = HermesColors.Midground,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showArchived = !showArchived }
                                .padding(vertical = 8.dp),
                        )
                    }
                }

                item(key = "footer") { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    // Yeniden adlandırma — kayıt flags.renames'e gider, sunucuya yazılmaz.
    renameTarget?.let { target ->
        var text by remember(target) { mutableStateOf(readableTitle(target, flags, cronNames)) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = HermesColors.Surface,
            title = {
                Text(S.t2("Yeniden adlandır", "Rename"), color = HermesColors.TextPrimary, fontSize = 14.sp)
            },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(60) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HermesColors.TextPrimary,
                        unfocusedTextColor = HermesColors.TextPrimary,
                        focusedBorderColor = HermesColors.BorderStrong,
                        unfocusedBorderColor = HermesColors.Border,
                        cursorColor = HermesColors.Midground,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(target.id, text)
                        renameTarget = null
                    },
                    enabled = text.isNotBlank(),
                ) {
                    Text(S.t2("Kaydet", "Save"), color = HermesColors.Midground, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(S.t2("İptal", "Cancel"), color = HermesColors.TextMuted, fontSize = 13.sp)
                }
            },
        )
    }

    // Silme onayı — sunucu DELETE ucu yok: kaldırma cihazdan gizlemedir.
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = HermesColors.Surface,
            title = {
                Text(readableTitle(target, flags, cronNames), style = MonoTextStyle, color = HermesColors.TextPrimary, fontSize = 13.sp)
            },
            text = {
                Text(
                    S.t2("Bu oturum listeden kaldırılacak.", "This session will be removed from the list."),
                    color = HermesColors.TextSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target.id)
                        deleteTarget = null
                        removedNotice = true
                    },
                ) {
                    Text(S.t2("Kaldır", "Remove"), color = HermesColors.Danger, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(S.t2("İptal", "Cancel"), color = HermesColors.TextMuted, fontSize = 13.sp)
                }
            },
        )
    }

    // Durdurma onayı — onaylanınca ekranda '/stop' slashExec çalışır.
    stopTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { stopTarget = null },
            containerColor = HermesColors.Surface,
            title = {
                Text(readableTitle(target, flags, cronNames), style = MonoTextStyle, color = HermesColors.TextPrimary, fontSize = 13.sp)
            },
            text = {
                Text(
                    S.t2("Durdurmak istediğine emin misin?", "Are you sure you want to stop?"),
                    color = HermesColors.TextSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onStop(target)
                        stopTarget = null
                    },
                ) {
                    Text(S.t2("Durdur", "Stop"), color = HermesColors.Danger, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { stopTarget = null }) {
                    Text(S.t2("İptal", "Cancel"), color = HermesColors.TextMuted, fontSize = 13.sp)
                }
            },
        )
    }
}

/** Arama alanı — malzeme3 SearchBar değil (overlay Yerleşimi kırar); Composer kalıbı. */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(S.t2("Oturum ara…", "Search sessions…"), color = HermesColors.TextFaint, fontSize = 13.sp)
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = HermesColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = S.t2("Temizle", "Clear"),
                        tint = HermesColors.TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = HermesColors.TextPrimary,
            unfocusedTextColor = HermesColors.TextPrimary,
            focusedContainerColor = HermesColors.Surface,
            unfocusedContainerColor = HermesColors.Surface,
            focusedBorderColor = HermesColors.BorderStrong,
            unfocusedBorderColor = HermesColors.Border,
            cursorColor = HermesColors.Midground,
        ),
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SessionRow(
    session: HermesSession,
    title: String,
    pinned: Boolean,
    archived: Boolean,
    menuOpen: Boolean,
    actions: SessionMenuActions,
    onClick: () -> Unit,
    onContinue: () -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onToggleArchived: () -> Unit,
    onDelete: () -> Unit,
    onStop: () -> Unit,
    onBuda: () -> Unit,
) {
    val actionsEnabled = !session.isActive
    HermesCard(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (pinned) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = S.t2("Sabitlenmiş", "Pinned"),
                    tint = HermesColors.Midground,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            StatusDot(if (session.isActive) HermesColors.Online else HermesColors.Offline, size = 7)
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                color = HermesColors.TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(formatRelative(session.startedAt), color = HermesColors.TextMuted, fontSize = 11.sp)

            // Uzun basınca eylem menüsü ModalBottomSheet olarak açılır —
            // satırda çapa gerekmez; menü ekranın altında yükselir.
            if (menuOpen) {
                SessionActionSheet(
                    session = session,
                    pinned = pinned,
                    actions = actions,
                    onDismiss = onDismissMenu,
                    onTogglePin = onTogglePin,
                    onRename = onRename,
                    onStop = onStop,
                    onBuda = onBuda,
                    onToggleArchived = onToggleArchived,
                    onDelete = onDelete,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        session.model?.let {
            Text(it, style = MonoTextStyle, color = HermesColors.TextMuted)
            Spacer(Modifier.height(4.dp))
        }
        Row {
            Meta("${session.messageCount} ${S.t2("mesaj", "messages")}")
            Spacer(Modifier.width(12.dp))
            Meta("${session.toolCallCount} ${S.t2("araç", "tools")}")
            Spacer(Modifier.width(12.dp))
            Meta("${(session.inputTokens + session.outputTokens) / 1000}k token")
            session.source?.let {
                Spacer(Modifier.width(12.dp))
                Meta(it)
            }
        }

        // Geçmiş salt-okunur değil artık: buradan konuşmaya devam edilebiliyor.
        Spacer(Modifier.height(9.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(HermesColors.Midground, RoundedCornerShape(8.dp))
                .clickable(onClick = onContinue)
                .padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(S.t2("Konuşmaya devam et", "Continue the conversation"), color = HermesColors.Background, fontSize = 12.sp)
        }
    }
}


/**
 * Uzun basınca açılan eylem menüsü — ModalBottomSheet olarak ModalBottomSheet
 * (DropdownMenu'nun aksine) her satırda çapa gerektirmez ve açıklamalı
 * eylemler (Buda) için daha çok yer verir.
 *
 * Durdur → AlertDialog onayı; Buda → açıklama metni gösterilir.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SessionActionSheet(
    session: HermesSession,
    pinned: Boolean,
    actions: SessionMenuActions,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onStop: () -> Unit,
    onBuda: () -> Unit,
    onToggleArchived: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HermesColors.Background,
        modifier = Modifier.heightIn(max = 480.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                S.t2("Oturum eylemleri", "Session actions"),
                color = HermesColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))

            ActionSheetRow(
                icon = Icons.Default.PushPin,
                label = if (pinned) S.t2("Sabiti kaldır", "Unpin") else S.t2("Sabitle", "Pin"),
                enabled = true,
                onClick = onTogglePin,
            )
            if (actions.rename) {
                ActionSheetRow(
                    icon = Icons.Default.Edit,
                    label = S.t2("Yeniden adlandır", "Rename"),
                    enabled = true,
                    onClick = onRename,
                )
            }
            if (actions.stop) {
                ActionSheetRow(
                    icon = Icons.Default.Stop,
                    label = S.t2("Durdur", "Stop"),
                    enabled = true,
                    onClick = onStop,
                )
            }
            if (actions.compress) {
                ActionSheetRow(
                    icon = Icons.Default.Compress,
                    label = S.t2("Buda (bağlamı sıkıştır)", "Buda (compress context)"),
                    enabled = true,
                    onClick = onBuda,
                    subtitle = S.t2("Bağlamı sıkıştırır, sohbet kalır", "Compresses context; conversation is kept"),
                )
            }
            if (actions.archive) {
                ActionSheetRow(
                    icon = Icons.Default.Archive,
                    label = if (actions.isArchived) S.t2("Arşivden çıkar", "Unarchive")
                    else S.t2("Arşivle", "Archive"),
                    enabled = true,
                    onClick = onToggleArchived,
                )
            }
            if (actions.delete) {
                ActionSheetRow(
                    icon = Icons.Default.Delete,
                    label = S.t2("Sil", "Delete"),
                    enabled = true,
                    onClick = onDelete,
                    danger = true,
                )
            }
            ActionSheetRow(
                icon = Icons.Default.Close,
                label = S.t2("İptal", "Cancel"),
                enabled = true,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun ActionSheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    danger: Boolean = false,
) {
    val tint = when {
        !enabled -> HermesColors.TextFaint
        danger -> HermesColors.Danger
        else -> HermesColors.TextPrimary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = tint, fontSize = 14.sp)
            if (subtitle != null) {
                Text(subtitle, color = HermesColors.TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Meta(text: String) {
    Text(text, color = HermesColors.TextFaint, fontSize = 10.sp)
}
