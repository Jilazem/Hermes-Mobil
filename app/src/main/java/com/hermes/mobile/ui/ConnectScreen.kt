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
import androidx.compose.material.icons.Icons
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
                Column(Modifier.weight(1f)) {
                    Text("Sunucular", color = HermesColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Text("Kayıtlı Hermes profilleri", color = HermesColors.TextMuted, fontSize = 12.sp)
                }
                TextButton(onClick = onRefresh) { Text("Yenile", color = HermesColors.Midground) }
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
                onClick = { editing = ServerProfile(name = "", baseUrl = "http://", token = "") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sunucu ekle")
            }
        }

        item {
            HermesCard(Modifier.fillMaxWidth()) {
                SectionLabel("Token nereden alınır")
                Spacer(Modifier.height(6.dp))
                Text(
                    "the server üzerinde:",
                    color = HermesColors.TextSecondary,
                    fontSize = 12.sp,
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
            title = { Text("Sunucu silinsin mi?") },
            text = { Text("\"${target.name}\" profili ve tokeni cihazdan kaldırılacak.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    confirmDelete = null
                }) { Text("Sil", color = HermesColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Vazgeç") }
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
        is ProbeResult.Ok -> HermesColors.Online to "çevrimiçi · ${probe.latencyMs} ms"
        is ProbeResult.Fail -> HermesColors.Offline to probe.reason
        null -> HermesColors.Busy to "yoklanıyor…"
    }

    HermesCard(Modifier.fillMaxWidth().clickable(onClick = onSelect)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(dotColor)
            Spacer(Modifier.width(8.dp))
            Text(
                profile.name.ifBlank { "(adsız)" },
                color = if (isActive) HermesColors.Midground else HermesColors.TextSecondary,
                fontSize = 15.sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            )
            if (isActive) {
                Spacer(Modifier.width(8.dp))
                Text("aktif", color = HermesColors.Online, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Düzenle", tint = HermesColors.TextMuted, modifier = Modifier.width(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Sil", tint = HermesColors.TextMuted, modifier = Modifier.width(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(profile.normalizedUrl, style = MonoTextStyle, color = HermesColors.TextMuted)
        if (profile.normalizedRemote.isNotBlank()) {
            Text(
                "uzak: ${profile.normalizedRemote}",
                style = MonoTextStyle,
                color = HermesColors.TextFaint,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(statusText, color = if (probe is ProbeResult.Ok) HermesColors.Online else HermesColors.TextFaint, fontSize = 11.sp)
        if (profile.note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(profile.note, color = HermesColors.TextFaint, fontSize = 11.sp)
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
        title = { Text(if (initial.name.isBlank()) "Sunucu ekle" else "Sunucuyu düzenle") },
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
                    label = { Text("Ad") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Adres") },
                    placeholder = { Text("http://192.168.1.10:9150") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Session token") },
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
                    label = { Text("Uzak adres (ev dışı)") },
                    placeholder = { Text("http://100.x.y.z:9150") },
                    singleLine = true,
                )
                Text(
                    "LAN adresine ulaşılamazsa buraya düşer. Tailscale IP'si ya da " +
                        "tünel adresi yaz.",
                    color = HermesColors.TextFaint,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Not (isteğe bağlı)") },
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
            ) { Text("Kaydet", color = HermesColors.Midground) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
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
        title = { Text("Sunucu silinsin mi?") },
        text = { Text("\"${profile.name}\" profili ve tokeni cihazdan kaldırılacak.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sil", color = HermesColors.Danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
