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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.TerminalLine
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Sık kullanılan bakım komutları — yazmadan tek dokunuşla.
 *
 * Üst düzey `val` değil fonksiyon: sabit olarak tutulduğunda etiketler sınıf
 * yüklenirken bir kez çözülür ve dil değişince güncellenmezdi.
 */
@Composable
private fun quickCommands(): List<Pair<String, String>> = listOf(
    "/status" to S.t2("Durum", "Status"),
    "/platforms" to S.t2("Platformlar", "Platforms"),
    "/agents" to S.t2("Ajanlar", "Agents"),
    "/cron" to "Cron",
    "/plugins" to S.t2("Eklentiler", "Plugins"),
    "/config" to S.t2("Yapılandırma", "Config"),
    "/insights" to S.t2("Kullanım", "Usage"),
    "/reload-mcp" to S.t2("MCP yenile", "Reload MCP"),
)

/**
 * Terminal — sunucuyu telefondan yönetmek için.
 *
 * Gerçek bir PTY değil: Hermes'in `slash.exec` ucunu kullanıyor. Bunun sebebi
 * `/api/pty` bir xterm oturumu açıyor ve mobilde tam terminal emülasyonu
 * (ANSI, imleç, yeniden boyutlandırma) gerektiriyor — buradaki asıl ihtiyaç
 * "Hermes'i güncelle, durumu gör, servisi yenile" olduğu için komut/çıktı
 * döngüsü hem yeterli hem çok daha sağlam.
 *
 * Kabuk komutu çalıştırmak için `/` ile başlamayan girdi ajana gönderilir;
 * ajan kendi araçlarıyla (onay kapıları dahil) çalıştırır.
 */
@Composable
fun TerminalScreen(
    lines: List<TerminalLine>,
    busy: Boolean,
    connected: Boolean,
    onRun: (String) -> Unit,
    onClear: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Terminal",
                    color = HermesColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (connected) S.t2("slash komutu ya da ajana görev yaz",
                        "slash command, or a task for the agent")
                    else S.t2("sunucuya bağlı değil", "not connected to the server"),
                    color = if (connected) HermesColors.TextMuted else HermesColors.Danger,
                    fontSize = 11.sp,
                )
            }
            if (lines.isNotEmpty()) {
                Text(
                    S.t2("Temizle", "Clear"),
                    color = HermesColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onClear).padding(10.dp),
                )
            }
        }

        // Hızlı komutlar
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            quickCommands().forEach { (cmd, label) ->
                Row(
                    Modifier
                        .background(HermesColors.SurfaceDim, RoundedCornerShape(16.dp))
                        .border(1.dp, HermesColors.Border, RoundedCornerShape(16.dp))
                        .clickable(enabled = connected && !busy) { onRun(cmd) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(label, color = HermesColors.TextSecondary, fontSize = 11.sp)
                }
            }
        }

        Box(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (lines.isEmpty()) {
                Column(Modifier.padding(top = 40.dp)) {
                    Text(
                        S.t2("Sunucuyu buradan yönet.", "Manage the server from here."),
                        color = HermesColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        S.t2(
                            "· /status ile durumu gör\n" +
                                "· /restart ile gateway'i yeniden başlat\n" +
                                "· \"hermes güncellemesi var mı, kontrol et\" gibi düz metin\n" +
                                "  yazarsan ajan kendi araçlarıyla halleder",
                            "· /status shows how the server is doing\n" +
                                "· /restart restarts the gateway\n" +
                                "· type plain text like \"check whether a Hermes update\n" +
                                "  is available\" and the agent handles it with its tools",
                        ),
                        color = HermesColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(HermesColors.SurfaceDim, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(lines) { _, line ->
                        Text(
                            (if (line.isCommand) "❯ " else "") + line.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = when {
                                line.isCommand -> HermesColors.Midground
                                line.isError -> HermesColors.Danger
                                else -> HermesColors.TextSecondary
                            },
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                enabled = connected && !busy,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        S.t2("/status  ya da  görev yaz…", "/status  or  type a task…"),
                        color = HermesColors.TextFaint,
                        fontSize = 13.sp,
                    )
                },
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
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
            IconButton(
                onClick = { onRun(draft); draft = "" },
                enabled = connected && !busy && draft.isNotBlank(),
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        if (draft.isNotBlank() && !busy) HermesColors.Midground
                        else HermesColors.SurfaceDim,
                        RoundedCornerShape(12.dp),
                    ),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = HermesColors.Midground,
                    )
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = S.t2("Çalıştır", "Run"),
                        tint = if (draft.isNotBlank()) HermesColors.Background
                        else HermesColors.TextFaint,
                    )
                }
            }
        }
    }
}
