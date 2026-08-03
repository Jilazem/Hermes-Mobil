package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.data.SLASH_COMMANDS
import com.hermes.mobile.data.SlashCommand
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle

/**
 * Komut paleti — Telegram bot'undaki slash komutlarının mobil karşılığı.
 *
 * Argüman isteyen komutlar için ikinci bir kutu açılır; geri alınamaz olanlar
 * (`/restart`, `/yolo`, `/stop`) onay ister.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CommandPalette(
    onRun: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var argFor by remember { mutableStateOf<SlashCommand?>(null) }
    var confirmFor by remember { mutableStateOf<SlashCommand?>(null) }

    fun launch(cmd: SlashCommand, arg: String = "") {
        val full = if (arg.isBlank()) "/${cmd.name}" else "/${cmd.name} ${arg.trim()}"
        onRun(full)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HermesColors.Background,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).heightIn(max = 620.dp)) {
            Text(
                "Komutlar",
                color = HermesColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Telegram'daki slash komutlarının aynısı",
                color = HermesColors.TextMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Komut ara…", color = HermesColors.TextFaint) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = HermesColors.TextMuted) },
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))

            val q = query.trim().lowercase().removePrefix("/")
            val matches = SLASH_COMMANDS.filter {
                q.isEmpty() || it.name.contains(q) || it.description.lowercase().contains(q)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                matches.groupBy { it.category }.forEach { (category, commands) ->
                    item(key = "hdr-${category.name}") {
                        SectionLabel(
                            category.label,
                            Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    commands.forEach { cmd ->
                        item(key = cmd.name) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(HermesColors.Surface, RoundedCornerShape(8.dp))
                                    .clickable {
                                        when {
                                            cmd.needsConfirm -> confirmFor = cmd
                                            cmd.takesArgument -> argFor = cmd
                                            else -> launch(cmd)
                                        }
                                    }
                                    .padding(horizontal = 11.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "/${cmd.name}",
                                        style = MonoTextStyle,
                                        color = HermesColors.Midground,
                                    )
                                    Text(
                                        cmd.description,
                                        color = HermesColors.TextMuted,
                                        fontSize = 11.sp,
                                    )
                                }
                                if (cmd.takesArgument) {
                                    Text("…", color = HermesColors.TextFaint, fontSize = 14.sp)
                                    Spacer(Modifier.width(4.dp))
                                }
                                if (cmd.needsConfirm) {
                                    Text("!", color = HermesColors.Danger, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }

    argFor?.let { cmd ->
        var arg by remember(cmd.name) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { argFor = null },
            containerColor = HermesColors.Surface,
            titleContentColor = HermesColors.TextPrimary,
            textContentColor = HermesColors.TextSecondary,
            title = { Text("/${cmd.name}") },
            text = {
                Column {
                    Text(cmd.description, color = HermesColors.TextMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = arg,
                        onValueChange = { arg = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(cmd.argumentHint, color = HermesColors.TextFaint) },
                        minLines = 1,
                        maxLines = 4,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { launch(cmd, arg); argFor = null }) {
                    Text("Çalıştır", color = HermesColors.Midground)
                }
            },
            dismissButton = {
                TextButton(onClick = { argFor = null }) { Text("Vazgeç") }
            },
        )
    }

    confirmFor?.let { cmd ->
        AlertDialog(
            onDismissRequest = { confirmFor = null },
            containerColor = HermesColors.Surface,
            titleContentColor = HermesColors.TextPrimary,
            textContentColor = HermesColors.TextSecondary,
            title = { Text("/${cmd.name} çalıştırılsın mı?") },
            text = { Text(cmd.description) },
            confirmButton = {
                TextButton(onClick = { launch(cmd); confirmFor = null }) {
                    Text("Evet", color = HermesColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmFor = null }) { Text("Vazgeç") }
            },
        )
    }
}
