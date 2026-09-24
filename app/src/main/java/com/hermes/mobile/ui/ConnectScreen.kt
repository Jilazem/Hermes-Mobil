package com.hermes.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.AppState
import com.hermes.mobile.data.ProbeResult
import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle

@Composable
fun ConnectScreen(
    state: AppState,
    onSelect: (String) -> Unit,
    onSave: (ServerProfile) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
    /** Tam-ekran CTA'dan açıldığında geri oku; null = gömülü kullanım. */
    onBack: (() -> Unit)? = null,
) {
    var editing by remember { mutableStateOf<ServerProfile?>(null) }
    var confirmDelete by remember { mutableStateOf<ServerProfile?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = S.back,
                        tint = HermesColors.TextSecondary,
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { onBack() }
                            .padding(end = 10.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(S.t2("Sunucular", "Servers"), color = HermesColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(S.t2("Kayıtlı Hermes profilleri", "Saved Hermes profiles"), color = HermesColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onRefresh) { Text(S.refresh, color = HermesColors.Midground) }
            }
        }

        items(state.profiles, key = { it.id }) { profile ->
            ProfileRow(
                profile = profile,
                probe = state.probes[profile.id],
                isActive = profile.id == state.active?.id,
                onSelect = { onSelect(profile.id) },
                onEdit = { editing = profile },
                onDelete = { confirmDelete = profile },
            )
        }

        item {
            Button(
                onClick = { editing = ServerProfile(name = "", baseUrl = "", token = "") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(S.t2("Sunucu ekle", "Add server"))
            }
        }

        item {
            HermesCard(Modifier.fillMaxWidth()) {
                SectionLabel(S.t2("Token nereden alınır", "Where to get the token"))
                Spacer(Modifier.height(6.dp))
                Text(
                    S.t2("Sunucu üzerinde:", "On the server:"),
                    color = HermesColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "grep HERMES_DASHBOARD_SESSION_TOKEN ~/.hermes/.env",
                    style = MonoTextStyle,
                    color = HermesColors.TextMuted,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    editing?.let { draft ->
        ProfileEditorDialog(
            initial = draft,
            onDismiss = { editing = null },
            onConfirm = {
                onSave(it)
                editing = null
            },
        )
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = HermesColors.Surface,
            titleContentColor = HermesColors.TextPrimary,
            textContentColor = HermesColors.TextSecondary,
            title = { Text(S.t2("Sunucu silinsin mi?", "Delete this server?")) },
            text = { Text(S.t2("\"${target.name}\" profili ve tokeni cihazdan kaldırılacak.", "\"${target.name}\" profile and its token will be removed from this device.")) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    confirmDelete = null
                }) { Text(S.t2("Sil", "Delete"), color = HermesColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(S.t2("Vazgeç", "Cancel")) }
            },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: ServerProfile,
    probe: ProbeResult?,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val (dotColor, statusText) = when (probe) {
        is ProbeResult.Ok -> HermesColors.Online to
            S.t2("çevrimiçi", "online") + " · ${probe.latencyMs} ms"
        is ProbeResult.Fail -> HermesColors.Offline to probe.reason
        null -> HermesColors.Busy to S.t2("yoklanıyor…", "probing…")
    }

    HermesCard(Modifier.fillMaxWidth().clickable(onClick = onSelect)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(dotColor)
            Spacer(Modifier.width(8.dp))
            Text(
                profile.name.ifBlank { S.t2("(adsız)", "(unnamed)") },
                color = if (isActive) HermesColors.Midground else HermesColors.TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            )
            if (isActive) {
                Spacer(Modifier.width(8.dp))
                Text(S.t2("aktif", "active"), color = HermesColors.Online, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, S.t2("Düzenle", "Edit"), tint = HermesColors.TextMuted, modifier = Modifier.width(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, S.t2("Sil", "Delete"), tint = HermesColors.TextMuted, modifier = Modifier.width(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(profile.normalizedUrl, style = MonoTextStyle, color = HermesColors.TextMuted)
        if (profile.normalizedRemote.isNotBlank()) {
            Text(
                "${S.t2("uzak", "remote")}: ${profile.normalizedRemote}",
                style = MonoTextStyle,
                color = HermesColors.TextFaint,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(statusText, color = if (probe is ProbeResult.Ok) HermesColors.Online else HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
        if (profile.note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(profile.note, color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ProfileEditorDialog(
    initial: ServerProfile,
    onDismiss: () -> Unit,
    onConfirm: (ServerProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.baseUrl) }
    var token by remember { mutableStateOf(initial.token) }
    var note by remember { mutableStateOf(initial.note) }
    var remote by remember { mutableStateOf(initial.remoteUrl) }
    var tokenVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HermesColors.Surface,
        titleContentColor = HermesColors.TextPrimary,
        textContentColor = HermesColors.TextSecondary,
        title = { Text(if (initial.name.isBlank()) S.t2("Sunucu ekle", "Add server") else S.t2("Sunucuyu düzenle", "Edit server")) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(S.t2("Ad", "Name")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(S.t2("Adres", "Address")) },
                    placeholder = { Text("http://192.168.1.101:9150") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(S.t2("Oturum anahtarı", "Session token")) },
                    singleLine = true,
                    visualTransformation =
                        if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (tokenVisible) "Tokeni gizle" else "Tokeni göster",
                                tint = HermesColors.TextMuted,
                                modifier = Modifier.width(18.dp),
                            )
                        }
                    },
                )
                OutlinedTextField(
                    value = remote,
                    onValueChange = { remote = it },
                    label = { Text(S.t2("Uzak adres (ev dışı)", "Remote address (off-network)")) },
                    placeholder = { Text("https://hermes.winterfell07.keenetic.pro") },
                    singleLine = true,
                )
                Text(
                    S.t2(
                        "LAN adresine ulaşılamazsa buraya düşer. Alan adı (DDNS), Tailscale " +
                            "IP'si ya da tünel adresi yaz. Tarayıcıdan kopyalanan \"?profile=…\" " +
                            "kısmı otomatik atılır.",
                        "Used when the LAN address is unreachable. Write a Tailscale IP " +
                            "or tunnel address.",
                    ),
                    color = HermesColors.TextFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
                // Tur-10 (F3): "adres düzenleme netliği" — kullanıcı hangi
                // adresin denendiğini, hangisinin art arda başarısız olduğu için
                // KISA SÜRE devre dışı bırakıldığını ve ne zaman yeniden
                // deneneceğini burada görür. Adresler SİLİNMEZ; "şimdi dene"
                // düğmesi bekleyen süreyi sıfırlar.
                var healthTick by remember { mutableStateOf(0) }
                Text(
                    S.t2("Adres deneme durumu", "Address attempt status"),
                    color = HermesColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                )
                val statuses = remember(healthTick, initial.baseUrl, initial.remoteUrl) {
                    com.hermes.mobile.data.AddressHealth.statuses(initial.id, initial.candidates)
                }
                if (statuses.isEmpty()) {
                    Text(
                        S.t2("Adres girilmedi", "No address entered"),
                        color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall,
                    )
                }
                statuses.forEach { st ->
                    val now = System.currentTimeMillis()
                    val line = when {
                        st.coolingDown(now) -> S.t2(
                            "${st.url} — ${st.remainingMs(now) / 1000} sn devre dışı " +
                                "(${st.failures} başarısız deneme)",
                            "${st.url} — disabled for ${st.remainingMs(now) / 1000}s " +
                                "(${st.failures} failed attempts)",
                        )
                        st.failures > 0 -> S.t2(
                            "${st.url} — yeniden denenecek (${st.failures} hata)",
                            "${st.url} — will be retried (${st.failures} errors)",
                        )
                        else -> S.t2("${st.url} — sağlıklı", "${st.url} — healthy")
                    }
                    Text(line, color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = {
                    com.hermes.mobile.data.AddressHealth.reset(initial.id)
                    healthTick++
                }) {
                    Text(
                        S.t2("Adresleri şimdi dene", "Retry addresses now"),
                        color = HermesColors.Midground,
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(S.t2("Not (isteğe bağlı)", "Note (optional)")) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.length > 8,
                onClick = {
                    onConfirm(
                        initial.copy(
                            name = name.trim(),
                            baseUrl = url.trim(),
                            token = token.trim(),
                            note = note.trim(),
                            remoteUrl = remote.trim(),
                        )
                    )
                },
            ) { Text(S.t2("Kaydet", "Save"), color = HermesColors.Midground) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.t2("Vazgeç", "Cancel")) } },
    )
}

/** Sunucu silme onayı — hem Durum hem Sunucular ekranından kullanılır. */
@Composable
fun DeleteProfileDialog(
    profile: ServerProfile,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HermesColors.Surface,
        titleContentColor = HermesColors.TextPrimary,
        textContentColor = HermesColors.TextSecondary,
        title = { Text(S.t2("Sunucu silinsin mi?", "Delete this server?")) },
        text = { Text(S.t2("\"${profile.name}\" profili ve tokeni cihazdan kaldırılacak.", "\"${profile.name}\" profile and its token will be removed from this device.")) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(S.t2("Sil", "Delete"), color = HermesColors.Danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.t2("Vazgeç", "Cancel")) } },
    )
}
