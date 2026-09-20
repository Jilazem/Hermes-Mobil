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
import androidx.compose.material3.MaterialTheme
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
import com.hermes.mobile.data.VoiceRecordLogic
import com.hermes.mobile.ui.theme.HermesColors
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

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
     * Bas-konuş kaydının durumu (tur-11). Mikrofon düğmesi **basılı tutulunca**
     * kayıt başlar, bırakılınca `/transcribe`'a gider. Faz
     * [VoiceRecordLogic.Phase.Transcribing] iken düğme döner göstergeye döner.
     */
    voiceRecord: VoiceRecordLogic.State = VoiceRecordLogic.State(),
    onVoiceHoldStart: () -> Unit = {},
    onVoiceHoldRelease: () -> Unit = {},
    onVoiceCancel: () -> Unit = {},
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
                    .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
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
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            cmd.description,
                            color = HermesColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (cmd.needsConfirm) {
                            Text("onay", color = HermesColors.Busy, style = MaterialTheme.typography.labelSmall)
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

        // Kayıt/metinleştirme durumu — kullanıcı ne olduğunu görsün: kırmızı
        // nokta + sayaç (60 sn tavanı) + "bırakınca metne çevirir" ipucu.
        if (voiceRecord.phase != VoiceRecordLogic.Phase.Idle) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (voiceRecord.recording) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(HermesColors.Danger, CircleShape),
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    VoiceRecordLogic.recordHint(voiceRecord, ::tr),
                    color = if (voiceRecord.phase == VoiceRecordLogic.Phase.Failed)
                        HermesColors.Danger else HermesColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                if (voiceRecord.recording) {
                    Text(
                        VoiceRecordLogic.timerLabel(voiceRecord.elapsedMs),
                        color = HermesColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (voiceRecord.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 1.5.dp,
                        color = HermesColors.Busy,
                    )
                }
            }
            voiceRecord.message?.takeIf { voiceRecord.phase == VoiceRecordLogic.Phase.Failed }?.let { msg ->
                Text(
                    msg,
                    color = HermesColors.Danger,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 1.dp),
                )
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
                    // Mikrofon düğmesi artık bas-konuş (sesli mesaj) olduğu için
                    // canlı sesli sohbet menüden erişilir kaldı.
                    DropdownMenuItem(
                        text = { Text("Canlı ses (eller serbest)", color = HermesColors.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.GraphicEq, null, tint = HermesColors.Midground)
                        },
                        onClick = { attachMenu = false; onDictate() },
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
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                maxLines = 5,
                shape = MaterialTheme.shapes.large,
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

                // Boş taslakta mikrofon = BAS-KONUŞ (tur-11). Canlı sesli sohbet
                // (Gemini Live) ek menüsündeki "Canlı ses" satırına taşındı.
                else -> HoldToTalkButton(
                    state = voiceRecord,
                    enabled = enabled,
                    onHoldStart = onVoiceHoldStart,
                    onHoldRelease = onVoiceHoldRelease,
                    onCancel = onVoiceCancel,
                )
            }
        }
    }
}

/**
 * Bas-konuş düğmesi — parmak basılıyken kayıt, bırakınca `/transcribe`.
 *
 * [detectTapGestures] `onPress` + `tryAwaitRelease`: bırakılınca `true`
 * (normal), jest iptal edilirse `false` (kaydırıp çıkma → kayıt atılır).
 * 0,8 sn'den kısa basışı [VoiceRecordLogic] zaten atıyor, bu yüzden yanlışlıkla
 * dokunma yükleme üretmez.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HoldToTalkButton(
    state: VoiceRecordLogic.State,
    enabled: Boolean,
    onHoldStart: () -> Unit,
    onHoldRelease: () -> Unit,
    onCancel: () -> Unit,
) {
    val recording = state.recording
    val busy = state.busy
    Box(
        Modifier
            .size(46.dp)
            .background(
                when {
                    recording -> HermesColors.Danger
                    busy -> HermesColors.SurfaceDim
                    else -> HermesColors.SurfaceDim
                },
                MaterialTheme.shapes.medium,
            )
            .pointerInput(enabled, busy) {
                if (!enabled || busy) return@pointerInput
                detectTapGestures(
                    onPress = {
                        onHoldStart()
                        val released = tryAwaitRelease()
                        if (released) onHoldRelease() else onCancel()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(19.dp),
                strokeWidth = 2.dp,
                color = HermesColors.Busy,
            )
        } else {
            Icon(
                if (recording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (recording)
                    S.t2("Kaydı bitir", "Finish recording")
                else S.t2("Basılı tut, konuş", "Hold to talk"),
                tint = if (recording) HermesColors.Background else HermesColors.TextFaint,
            )
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
                MaterialTheme.shapes.medium,
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
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .border(
                1.dp,
                if (att.error != null) HermesColors.Danger.copy(alpha = 0.5f) else HermesColors.Border,
                MaterialTheme.shapes.medium,
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

            att.error != null -> Text("✕", color = HermesColors.Danger, style = MaterialTheme.typography.bodySmall)

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
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(120.dp),
            )
            att.error?.let {
                Text(it, color = HermesColors.Danger, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
