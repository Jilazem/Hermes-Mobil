package com.hermes.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.data.SavedPrompt
import com.hermes.mobile.ui.theme.HermesColors
import kotlinx.coroutines.launch

/**
 * Kayıtlı promptlar — form + liste tek sheet'te.
 *
 * Satıra dokunmak mesaj GÖNDERMEZ, taslağa yazar (chip akışının kuralı:
 * draft doluysa sonuna eklenir, üstüne yazılmaz). Uzun basma menüsü:
 * AI ile iyileştir / Düzenle / Sil.
 *
 * AI iyileştirme [onImprove] üzerinden GÖRÜNMEZ tek kullanımlık oturumda
 * koşar (ChatViewModel.improvePrompt); 30 sn'de yanıt gelmezse ya da
 * bağlantı koparsa hata satırı gösterilir ve akış kilitlenmez — kullanıcı
 * metni olduğu gibi kaydetmeye devam edebilir. Onaylamadan kayıt değişmez.
 *
 * BackHandler gerekmiyor: ModalBottomSheet kendi dismiss'ini yönetiyor.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PromptsSheet(
    prompts: List<SavedPrompt>,
    onSave: (etiket: String, metin: String) -> Unit,
    onUpdate: (id: String, etiket: String, metin: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onUsePrompt: (metin: String) -> Unit,
    /** suspend — görünmez oturumda metni iyileştirir; hata fırlatursa yakalanır. */
    onImprove: suspend (metin: String) -> String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var etiket by remember { mutableStateOf("") }
    var metin by remember { mutableStateOf("") }
    // null = yeni kayıt; doluysa form bu kaydı güncelliyor.
    var editing by remember { mutableStateOf<SavedPrompt?>(null) }
    var menuFor by remember { mutableStateOf<SavedPrompt?>(null) }
    /** İyileştirme süren kaydın id'si — satırda "iyileştiriyor…" yazılır. */
    var improvingId by remember { mutableStateOf<String?>(null) }
    /** (kayıt, orijinal, AI önerisi) — Onayla'ya kadar kayıt değişmez. */
    var proposal by remember { mutableStateOf<Triple<SavedPrompt, String, String>?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }

    // S.t2 composable — metin burada çözülüp improve() closure'ına alınıyor.
    val aiHataMetni = S.t2(
        "AI şu anda kullanılamıyor, metni olduğu gibi kaydedebilirsin",
        "AI is unavailable right now — you can keep the text as it is",
    )

    fun formuTemizle() {
        etiket = ""
        metin = ""
        editing = null
    }

    fun improve(p: SavedPrompt) {
        improvingId = p.id
        aiError = null
        scope.launch {
            // runCatching değil: iyileştirme hatası akışı kilitlememeli, mesaj
            // olarak gösterilmeli. CancellationException da buraya düşer ve
            // sheet kapanmışsa önemsizdir.
            runCatching { onImprove(p.text) }
                .onSuccess { onaylanan ->
                    val temiz = onaylanan.trim()
                    if (temiz.isNotEmpty() && temiz != p.text.trim()) proposal = Triple(p, p.text, temiz)
                    else aiError = aiHataMetni
                }
                .onFailure { aiError = aiHataMetni }
            improvingId = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HermesColors.Background,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).heightIn(max = 620.dp)) {
            Text(S.t2("Promptlar", "Prompts"), color = HermesColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(
                S.t2("Sık kullandığın istekleri kaydet, dokununca taslağa düşsün", "Save frequent asks; tap to drop one into the draft"),
                color = HermesColors.TextMuted, fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))

            // ── Form ──────────────────────────────────────────────────
            OutlinedTextField(
                value = etiket,
                onValueChange = { etiket = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(S.t2("Etiket (opsiyonel)", "Label (optional)"), color = HermesColors.TextFaint) },
                singleLine = true,
            )
            Spacer(Modifier.height(7.dp))
            OutlinedTextField(
                value = metin,
                onValueChange = { metin = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(S.t2("Prompt metni…", "Prompt text…"), color = HermesColors.TextFaint) },
                minLines = 2,
                maxLines = 6,
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val guncelleniyor = editing != null
                TextButton(
                    onClick = {
                        val e = editing
                        if (e != null) onUpdate(e.id, etiket, metin) else onSave(etiket, metin)
                        formuTemizle()
                    },
                    // Metin boşsa kayıt anlamsız — düğme susar.
                    enabled = metin.isNotBlank(),
                ) {
                    Text(
                        if (guncelleniyor) S.t2("Güncelle", "Update") else S.t2("Kaydet", "Save"),
                        color = if (metin.isNotBlank()) HermesColors.Midground else HermesColors.TextFaint,
                    )
                }
                if (editing != null) {
                    TextButton(onClick = ::formuTemizle) {
                        Text(S.t2("Vazgeç", "Cancel"), color = HermesColors.TextMuted)
                    }
                }
            }
            aiError?.let {
                Text(it, color = HermesColors.Busy, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }

            // ── AI önerisi: onaylanmadan kayda yazılmaz ───────────────
            proposal?.let { (p, eski, yeni) ->
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(HermesColors.SurfaceDim, RoundedCornerShape(10.dp))
                        .padding(11.dp),
                ) {
                    Text(S.t2("Orijinal", "Original"), color = HermesColors.TextFaint, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Text(
                        eski, color = HermesColors.TextMuted, fontSize = 11.sp,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(S.t2("AI önerisi", "AI suggestion"), color = HermesColors.Online, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Text(yeni, color = HermesColors.TextSecondary, fontSize = 12.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            onUpdate(p.id, p.label, yeni)
                            proposal = null
                        }) { Text(S.t2("Onayla: kaydet", "Approve: save"), color = HermesColors.Online) }
                        TextButton(onClick = { proposal = null }) {
                            Text(S.t2("Vazge", "Discard"), color = HermesColors.TextMuted)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Kayıtlı liste ─────────────────────────────────────────
            if (prompts.isEmpty()) {
                Text(
                    S.t2("Henüz kayıtlı prompt yok", "No saved prompts yet"),
                    color = HermesColors.TextFaint, fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(prompts, key = { it.id }) { p ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(HermesColors.SurfaceDim, RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = {
                                    // Taslağa düşür, sheet kapansın — kullanıcı
                                    // eklenmiş taslağı görsün.
                                    onUsePrompt(p.text)
                                    onDismiss()
                                },
                                onLongClick = { menuFor = p },
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                p.label, color = HermesColors.TextPrimary, fontSize = 13.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (improvingId == p.id) {
                                Spacer(Modifier.width(8.dp))
                                Text(S.t2("AI iyileştiriyor…", "AI improving…"), color = HermesColors.Busy, fontSize = 10.sp)
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            p.text, color = HermesColors.TextMuted, fontSize = 11.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Uzun basma menüsü satıra anchored — Menu PositionProvider
                    // varsayılanı sheet içinde doğru çalışıyor.
                    DropdownMenu(
                        expanded = menuFor?.id == p.id,
                        onDismissRequest = { menuFor = null },
                        containerColor = HermesColors.Surface,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    S.t2("AI ile iyileştir", "Improve with AI"),
                                    color = if (improvingId == p.id) HermesColors.TextFaint else HermesColors.TextSecondary,
                                    fontSize = 13.sp,
                                )
                            },
                            enabled = improvingId != p.id,
                            onClick = { menuFor = null; improve(p) },
                        )
                        DropdownMenuItem(
                            text = { Text(S.t2("Düzenle", "Edit"), color = HermesColors.TextSecondary, fontSize = 13.sp) },
                            onClick = {
                                menuFor = null
                                editing = p
                                etiket = p.label
                                metin = p.text
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(S.t2("Sil", "Delete"), color = HermesColors.Danger, fontSize = 13.sp) },
                            onClick = {
                                menuFor = null
                                if (editing?.id == p.id) formuTemizle()
                                if (proposal?.first?.id == p.id) proposal = null
                                onDelete(p.id)
                            },
                        )
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}
