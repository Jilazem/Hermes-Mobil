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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.InterventionKind
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.data.DemoMask
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle

/**
 * Müdahale diyaloğu — iki farklı sunucu davranışı arasında açık seçim yaptırır.
 *
 * "Ekle" (`steer`) turu bölmez, ajan bir sonraki iterasyonda görür.
 * "Yönlendir" (`redirect`) süren turu değiştirir; her ajan desteklemez.
 *
 * Tur-16: Eski Canlı ekranıyla birlikte tek kullanımda kalmasın diye ayrı
 * dosyaya taşındı — oturum çekmecesinin Canlı sekmesi de aynı diyaloğu açar.
 */
@Composable
fun InterventionDialog(
    session: LiveSession,
    title: String = session.title.ifBlank { "Oturum" },
    sending: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (InterventionKind, String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(InterventionKind.Add) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HermesColors.Surface,
        titleContentColor = HermesColors.TextPrimary,
        textContentColor = HermesColors.TextSecondary,
        title = { Text(S.t2("Çalışan ajana müdahale", "Steer the running agent")) },
        text = {
            Column {
                Text(
                    DemoMask.name(DemoMask.Kind.SESSION, title),
                    style = MonoTextStyle,
                    color = HermesColors.TextMuted,
                )
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindOption(
                        title = S.t2("Ekle", "Append"),
                        detail = S.t2("Turu kesmez", "Does not cut the turn"),
                        selected = kind == InterventionKind.Add,
                        onClick = { kind = InterventionKind.Add },
                        modifier = Modifier.weight(1f),
                    )
                    KindOption(
                        title = S.t2("Yönlendir", "Redirect"),
                        detail = S.t2("Turu değiştirir", "Changes the current turn"),
                        selected = kind == InterventionKind.Redirect,
                        onClick = { kind = InterventionKind.Redirect },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            if (kind == InterventionKind.Add)
                                S.t2("Şu dosyayı da kontrol et…", "Also check this file…")
                            else
                                S.t2("Bunun yerine önce raporu özetle…", "Instead, summarize the report first…"),
                            color = HermesColors.TextFaint,
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (kind == InterventionKind.Add)
                        S.t2(
                            "Mesaj sonraki araç sonucuna iliştirilir; ajan bir sonraki adımında görür.",
                            "The message rides the next tool result; the agent sees it on its next step.",
                        )
                    else
                        S.t2(
                            "Süren tur yönlendirilir, yapılan iş korunur. Her ajan desteklemez — " +
                                "desteklemezse otomatik olarak eklemeye düşülür.",
                            "The running turn is redirected, finished work is kept. Not every agent " +
                                "supports it — falls back to appending.",
                        ),
                    color = HermesColors.TextFaint,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && !sending,
                onClick = { onSubmit(kind, text) },
            ) {
                Text(
                    if (sending) S.t2("Gönderiliyor…", "Sending…") else S.t2("Gönder", "Send"),
                    color = HermesColors.Midground,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.t2("Vazgeç", "Cancel")) } },
    )
}

@Composable
private fun KindOption(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(
                if (selected) HermesColors.SurfaceDim else HermesColors.Surface,
                RoundedCornerShape(9.dp),
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) HermesColors.Midground else HermesColors.Border,
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(
            title,
            color = if (selected) HermesColors.Midground else HermesColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(detail, color = HermesColors.TextFaint, fontSize = 10.sp)
    }
}
