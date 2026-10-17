package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hermes.mobile.data.BotCard
import com.hermes.mobile.data.BotRosterLogic
import com.hermes.mobile.data.BotRosterStore
import com.hermes.mobile.data.BotValidationError
import com.hermes.mobile.data.HermesProfile
import com.hermes.mobile.data.ModelProvider
import com.hermes.mobile.ui.theme.HermesColors

/**
 * K18B-G1 — KALICI BOT KİMLİKLERİ (roster).
 *
 * Bot kartı: ad, kişilik, model (sağlayıcı+model), pin/duplicate. "Bu botla
 * devam et" aynı profil + modeli geri seçer ([onContinue] → MainActivity
 * ChatViewModel.selectedProfileValue + selectModel + yeni oturum). Arena'daki
 * geçici bot seçimine DOKUNULMAZ — bu ayrı bir katmandır.
 *
 * Esin: MausBot roster (pin/duplicate — Apache-2.0) ve Holt karakter-bazlı
 * yapı (AGPL-3.0) YALNIZ FİKİR; kod kopyalanmadı (CREDITS.md).
 *
 * Gizlilik: kartlar yalnız bu cihazda ([BotRosterStore]); kişilik metni
 * kullanıcının kendi girdisidir, sunucuya ayrı bir alan olarak gönderilmez.
 */
@Composable
fun BotRosterScreen(
    profiles: List<HermesProfile>,
    providers: List<ModelProvider>,
    onContinue: (BotCard) -> Unit,
    /** Model seçiciyi açmadan önce aday listesini tazele (chat'teki loadModels ile aynı). */
    onLoadModels: () -> Unit = {},
    scrollable: Boolean = true,
) {
    val context = LocalContext.current
    val store = remember { BotRosterStore(context) }
    var cards by remember { mutableStateOf(store.yukle()) }
    var editing by remember { mutableStateOf<BotCard?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<BotCard?>(null) }

    fun refresh() { cards = store.yukle() }

    val tDeleteTitle = S.t2("Bot silinsin mi?", "Delete bot?")
    val tDeleteBody = S.t2(
        "Kart bu cihazdan silinir; sohbetleriniz etkilenmez.",
        "The card is removed from this device; your chats are unaffected.",
    )

    val column: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                S.t2("Botlarım", "My bots"),
                style = MaterialTheme.typography.titleLarge,
                color = HermesColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                S.t2("${cards.size} kart", "${cards.size} cards"),
                style = MaterialTheme.typography.bodySmall,
                color = HermesColors.TextMuted,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { editing = null; editorOpen = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = S.t2("Yeni bot", "New bot"),
                    modifier = Modifier.size(18.dp),
                    tint = HermesColors.TextPrimary,
                )
                Spacer(Modifier.width(4.dp))
                Text(S.t2("Yeni bot", "New bot"), color = HermesColors.TextPrimary)
            }
        }
        Text(
            S.t2(
                "Kalıcı bot kartları: ad, kişilik ve modeli saklar; \"devam et\" aynı " +
                    "profil ve modeli geri seçer. Kartlar yalnız bu cihazda kalır.",
                "Persistent bot cards keep name, personality and model; " +
                    "\"continue\" restores the same profile and model. " +
                    "Cards stay on this device only.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = HermesColors.TextMuted,
        )
        Spacer(Modifier.height(10.dp))

        if (cards.isEmpty()) {
            Text(
                S.t2(
                    "Henüz bot kartı yok — \"Yeni bot\" ile ilk kartını oluştur.",
                    "No bot cards yet — create your first with \"New bot\".",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = HermesColors.TextMuted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        BotRosterLogic.sortedForDisplay(cards).forEach { card ->
            BotCardRow(
                card = card,
                // D-05: profil satırı iç ad göstermez — çipteki gibi görünen
                // ad (display_name); eşleşmiyorsa "—" (ChatScreen kilidi ile aynı).
                profileLabel = card.profile.trim().ifBlank { null }?.let { pname ->
                    visibleProfileChips(profiles).firstOrNull { it.first.name == pname }?.second
                        ?: profiles.firstOrNull { it.name == pname }
                            ?.displayName?.takeIf { !isInternalProfileName(it) }
                        ?: "—"
                } ?: S.t2("Yönlendirici", "Router"),
                onContinue = { onContinue(card) },
                onEdit = { editing = card; editorOpen = true },
                onDuplicate = { store.duplicate(card.id); refresh() },
                onTogglePin = { store.setPinned(card.id, !card.pinned); refresh() },
                onDelete = { confirmDelete = card },
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(20.dp))
    }

    if (scrollable) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) { column() }
    } else {
        Column(Modifier.fillMaxWidth()) { column() }
    }

    if (editorOpen) {
        BotEditorSheet(
            editing = editing,
            profiles = profiles,
            providers = providers,
            onLoadModels = onLoadModels,
            onDismiss = { editorOpen = false },
            onSave = { card ->
                store.kaydet(card)
                refresh()
                editorOpen = false
            },
        )
    }

    confirmDelete?.let { card ->
        ConfirmDialog(
            title = tDeleteTitle,
            body = tDeleteBody,
            onConfirm = {
                store.sil(card.id)
                refresh()
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

/** Tek bot kartı — kimlik afişi, kişilik + model satırı, eylemler. */
@Composable
private fun BotCardRow(
    card: BotCard,
    profileLabel: String,
    onContinue: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .border(1.dp, HermesColors.Border, MaterialTheme.shapes.medium)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                BotRosterLogic.cardTitle(card),
                style = MaterialTheme.typography.titleSmall,
                color = HermesColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (card.pinned) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = S.t2("Sabitlenmiş", "Pinned"),
                    tint = HermesColors.Busy,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (card.personality.isNotBlank()) {
            Text(
                card.personality,
                style = MaterialTheme.typography.bodySmall,
                color = HermesColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            profileLabel +
                (if (card.model.isNotBlank()) " · ${modelLabel(card.provider, card.model)}" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = HermesColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // "Bu botla devam et" — birincil eylem.
            Row(
                Modifier
                    .background(HermesColors.Midground, MaterialTheme.shapes.small)
                    .clickable(onClick = onContinue)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = HermesColors.OnAccent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    S.t2("Bu botla devam et", "Continue with this bot"),
                    color = HermesColors.OnAccent,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.weight(1f))
            CardIcon(Icons.Default.Edit, S.t2("Düzenle", "Edit"), onEdit)
            CardIcon(Icons.Default.ContentCopy, S.t2("Çoğalt", "Duplicate"), onDuplicate)
            CardIcon(
                if (card.pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                S.t2("Sabitle", "Pin"),
                onTogglePin,
            )
            CardIcon(Icons.Default.Delete, S.t2("Sil", "Delete"), onDelete)
        }
    }
}

@Composable
private fun CardIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = HermesColors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Model afişi: "sağlayıcı/model" — kartta ve editörde aynı biçim. */
internal fun modelLabel(provider: String, model: String): String {
    val p = provider.trim()
    val m = model.trim()
    return when {
        p.isEmpty() && m.isEmpty() -> ""
        p.isEmpty() -> m
        else -> "$p/$m"
    }
}

/**
 * Bot oluştur/düzenle formu. Model seçimi mevcut [ModelPickerSheet] mantığından
 * beslenir; profil seçimi sohbet çipleriyle aynı görünür adaylardan (iç adlar
 * sızmaz — visibleProfileChips).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BotEditorSheet(
    editing: BotCard?,
    profiles: List<HermesProfile>,
    providers: List<ModelProvider>,
    onLoadModels: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (BotCard) -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var emoji by remember { mutableStateOf(editing?.emoji?.ifBlank { "🤖" } ?: "🤖") }
    var personality by remember { mutableStateOf(editing?.personality ?: "") }
    var profile by remember { mutableStateOf(editing?.profile ?: "") }
    var provider by remember { mutableStateOf(editing?.provider ?: "") }
    var model by remember { mutableStateOf(editing?.model ?: "") }
    var modelSheet by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }

    val error = BotRosterLogic.validate(name, personality)
    val tRouter = S.t2("Yönlendirici (varsayılan)", "Router (default)")
    val tError = when (error) {
        BotValidationError.NAME_BLANK -> S.t2("Ad gerekli", "Name is required")
        BotValidationError.NAME_TOO_LONG -> S.t2(
            "Ad en fazla ${BotRosterLogic.MAX_NAME} karakter",
            "Name is at most ${BotRosterLogic.MAX_NAME} characters",
        )
        BotValidationError.PERSONALITY_TOO_LONG -> S.t2(
            "Kişilik en fazla ${BotRosterLogic.MAX_PERSONALITY} karakter",
            "Personality is at most ${BotRosterLogic.MAX_PERSONALITY} characters",
        )
        null -> ""
    }

    // Neden ModalBottomSheet DEĞİL: iç içe iki modal sheet açılmıyor — model
    // seçici ikinci sheet'i cihazda görünmedi (emülatör kanıtı em-05b). Dialog
    // ayrı bir pencere açar; ModelPickerSheet (ModalBottomSheet) üstüne gelir.
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = HermesColors.Surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                if (editing == null) S.t2("Yeni bot", "New bot") else S.t2("Botu düzenle", "Edit bot"),
                style = MaterialTheme.typography.titleLarge,
                color = HermesColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(S.t2("Ad", "Name")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = emoji,
                onValueChange = { emoji = it.take(4) },
                label = { Text(S.t2("Emoji", "Emoji")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = personality,
                onValueChange = { personality = it },
                label = { Text(S.t2("Kişilik (kullanıcı girdisi, cihazda kalır)", "Personality (your input, stays on device)")) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            // Profil seçimi — görünür adlarla (iç adlar liste dışı).
            val profileOptions = listOf("" to tRouter) +
                visibleProfileChips(profiles).map { (p, label) -> p.name to label }
            ExposedDropdownMenuBox(
                expanded = profileMenu,
                onExpandedChange = { profileMenu = it },
            ) {
                OutlinedTextField(
                    value = profileOptions.firstOrNull { it.first == profile }?.second ?: tRouter,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(S.t2("Hermes profili", "Hermes profile")) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(profileMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = profileMenu,
                    onDismissRequest = { profileMenu = false },
                ) {
                    profileOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                profile = value
                                profileMenu = false
                            },
                            trailingIcon = {
                                if (value == profile) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Model seçimi — mevcut model seçiciden.
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = modelLabel(provider, model).ifBlank { S.t2("Seçilmedi", "Not set") },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(S.t2("Model", "Model")) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onLoadModels(); modelSheet = true }) {
                    Text(S.t2("Seç", "Pick"))
                }
            }

            if (error != null) {
                Text(
                    tError,
                    style = MaterialTheme.typography.bodySmall,
                    color = HermesColors.Danger,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(S.t2("Vazgeç", "Cancel"), color = HermesColors.TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier
                        .background(
                            if (error == null) HermesColors.Midground else HermesColors.SurfaceDim,
                            MaterialTheme.shapes.small,
                        )
                        .clickable(enabled = error == null) {
                            val now = System.currentTimeMillis()
                            onSave(
                                BotCard(
                                    id = editing?.id ?: java.util.UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    emoji = emoji.trim().ifBlank { "🤖" },
                                    personality = personality.trim(),
                                    profile = profile.trim(),
                                    provider = provider.trim(),
                                    model = model.trim(),
                                    pinned = editing?.pinned ?: false,
                                    createdAt = editing?.createdAt ?: now,
                                    updatedAt = now,
                                ),
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        S.t2("Kaydet", "Save"),
                        color = if (error == null) HermesColors.OnAccent else HermesColors.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        }
    }

    if (modelSheet) {
        ModelPickerSheet(
            providers = providers,
            currentModel = model,
            loading = false,
            brokenModels = emptySet(),
            hiddenModels = emptySet(),
            pinnedModels = emptyList(),
            modelUsage = emptyMap(),
            showBroken = false,
            onToggleShowBroken = {},
            onTogglePin = {},
            onToggleHidden = {},
            onSelect = { p, m, _ ->
                provider = p
                model = m
                modelSheet = false
            },
            onDismiss = { modelSheet = false },
        )
    }
}

/** Silme onayı — veri kaybı çift dokunuşla olmasın (veri kaybı = ACİL sınıfı). */
@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(S.t2("Sil", "Delete"), color = HermesColors.Danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(S.t2("Vazgeç", "Cancel"), color = HermesColors.TextSecondary)
            }
        },
    )
}