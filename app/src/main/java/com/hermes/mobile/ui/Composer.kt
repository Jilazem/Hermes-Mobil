package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.PendingAttachment
import com.hermes.mobile.AttachmentKind
import com.hermes.mobile.data.VoiceController
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.ui.text.font.FontWeight
import com.hermes.mobile.data.SLASH_COMMANDS
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Mesaj yazma çubuğu — ek, dikte, gönder/durdur.
 *
 * Gönder düğmesi üretim sürerken durdurma düğmesine dönüşür (ChatGPT davranışı).
 * Metin boşken ve ek yokken mikrofon gösterilir; yazınca gönder'e döner.
 */
@Composable
fun ChatComposer(
    draft: String,
    enabled: Boolean,
    agentBusy: Boolean,
    attachments: List<PendingAttachment>,
    voiceMode: VoiceController.Mode,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onDictate: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onPasteUrl: () -> Unit = {},
    onOpenSnippets: () -> Unit = {},
    /**
     * Sunucuya ulaşılabiliyor mu. Yazmak buna bağlı **değil**: kopukken yazılan
     * mesaj kuyruğa girip bağlanınca gönderiliyor. Yalnız dosya/görsel ekleme
     * çevrimiçi olmayı gerektiriyor (yükleme sunucuya gidiyor).
     */
    online: Boolean = true,
) {
    var attachMenu by remember { mutableStateOf(false) }
    val hasContent = draft.isNotBlank() || attachments.any { it.remotePath != null }

    // "/" ile başlayan tek satırlık taslakta komut önerisi. Boşluktan sonrası
    // argüman sayıldığı için orada öneri kesiliyor — "/model gpt" yazarken
    // liste yolu tıkamasın.
    val slashPrefix = draft.takeIf { it.startsWith("/") && it.none { c -> c.isWhitespace() } }
    val suggestions = remember(slashPrefix) {
        slashPrefix?.removePrefix("/")?.lowercase()?.let { q ->
            SLASH_COMMANDS
                .filter { it.name.startsWith(q) }
                .take(6)
        }.orEmpty()
    }

    Column(Modifier.fillMaxWidth()) {
        if (suggestions.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(HermesColors.SurfaceDim, RoundedCornerShape(12.dp))
                    .padding(vertical = 4.dp),
            ) {
                suggestions.forEach { cmd ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Argüman isteyen komutta boşluk bırak, yazmaya devam etsin.
                                onDraftChange("/" + cmd.name + if (cmd.takesArgument) " " else "")
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "/" + cmd.name,
                            color = HermesColors.Midground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            cmd.description,
                            color = HermesColors.TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (cmd.needsConfirm) {
                            Text("onay", color = HermesColors.Busy, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        if (attachments.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEach { att -> AttachmentChip(att, onRemoveAttachment) }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box {
                IconButton(onClick = { attachMenu = true }, enabled = online) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = S.t2("Ek", "Attachment"),
                        tint = if (online) HermesColors.TextMuted else HermesColors.TextFaint,
                    )
                }
                DropdownMenu(
                    expanded = attachMenu,
                    onDismissRequest = { attachMenu = false },
                    containerColor = HermesColors.Surface,
                ) {
                    DropdownMenuItem(
                        text = { Text("Fotoğraf / görsel", color = HermesColors.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.Image, null, tint = HermesColors.Midground)
                        },
                        onClick = { attachMenu = false; onPickImage() },
                    )
                    DropdownMenuItem(
                        text = { Text("Dosya / video", color = HermesColors.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.InsertDriveFile, null, tint = HermesColors.Midground)
                        },
                        onClick = { attachMenu = false; onPickFile() },
                    )
                    DropdownMenuItem(
                        text = { Text("Panodan yapıştır", color = HermesColors.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.ContentPaste, null, tint = HermesColors.Midground)
                        },
                        onClick = { attachMenu = false; onPasteUrl() },
                    )
                    DropdownMenuItem(
                        text = { Text("Hazır komutlar", color = HermesColors.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.Bolt, null, tint = HermesColors.Midground)
                        },
                        onClick = { attachMenu = false; onOpenSnippets() },
                    )
                }
            }

            TextField(
                value = draft,
                onValueChange = onDraftChange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        when {
                            voiceMode == VoiceController.Mode.Listening -> S.listening
                            // Kopukken de yaz: bağlanınca gidecek.
                            !online -> S.composerOffline
                            else -> S.composerHint
                        },
                        color = HermesColors.TextFaint,
                        fontSize = 14.sp,
                    )
                },
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = HermesColors.Surface,
                    unfocusedContainerColor = HermesColors.Surface,
                    disabledContainerColor = HermesColors.SurfaceDim,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = HermesColors.TextPrimary,
                    unfocusedTextColor = HermesColors.TextPrimary,
                    cursorColor = HermesColors.Midground,
                ),
            )

            Spacer(Modifier.width(8.dp))

            when {
                agentBusy -> ActionButton(
                    icon = Icons.Default.Stop,
                    label = "Durdur",
                    filled = true,
                    onClick = onStop,
                )

                hasContent -> ActionButton(
                    icon = Icons.Default.ArrowUpward,
                    label = "Gönder",
                    filled = enabled,
                    enabled = enabled,
                    onClick = onSend,
                )

                else -> ActionButton(
                    icon = if (voiceMode == VoiceController.Mode.Listening)
                        Icons.Default.GraphicEq else Icons.Default.Mic,
                    label = "Sesle yaz",
                    filled = voiceMode == VoiceController.Mode.Listening,
                    enabled = enabled,
                    onClick = onDictate,
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    filled: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(46.dp)
            .background(
                if (filled) HermesColors.Midground else HermesColors.SurfaceDim,
                RoundedCornerShape(13.dp),
            ),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (filled) HermesColors.Background else HermesColors.TextFaint,
        )
    }
}

@Composable
private fun AttachmentChip(att: PendingAttachment, onRemove: (String) -> Unit) {
    Row(
        Modifier
            .background(HermesColors.SurfaceDim, RoundedCornerShape(9.dp))
            .border(
                1.dp,
                if (att.error != null) HermesColors.Danger.copy(alpha = 0.5f) else HermesColors.Border,
                RoundedCornerShape(9.dp),
            )
            .padding(start = 9.dp, end = 3.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            att.uploading -> CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 1.5.dp,
                color = HermesColors.Busy,
            )

            att.error != null -> Text("✕", color = HermesColors.Danger, fontSize = 12.sp)

            else -> Icon(
                if (att.kind == AttachmentKind.Image) Icons.Default.Image
                else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = HermesColors.Online,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(7.dp))
        Column {
            Text(
                att.label,
                color = HermesColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(120.dp),
            )
            att.error?.let {
                Text(it, color = HermesColors.Danger, fontSize = 9.sp, maxLines = 1)
            }
        }
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable { onRemove(att.label) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = S.t2("Kaldır", "Remove"),
                tint = HermesColors.TextFaint,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}
