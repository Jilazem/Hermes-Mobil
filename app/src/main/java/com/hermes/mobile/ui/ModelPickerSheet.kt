package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.data.ModelProvider
import com.hermes.mobile.data.RECOMMENDED_MODELS
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle
import com.hermes.mobile.data.DemoMask

/** Seçicide bir satırın taşıdığı her şey — tek yerde toplanınca sıralama kolaylaşıyor. */
private data class ModelRow(
    val provider: String,
    val model: String,
    /** Önerilen listesinden geliyorsa gösterilecek ad, yoksa modelin kendi adı. */
    val label: String,
    val note: String,
    val seconds: Double?,
    val local: Boolean,
    val usable: Boolean,
    val broken: Boolean,
    val hidden: Boolean,
    val uses: Int,
) {
    val key: String get() = "$provider/$model"
}

/**
 * Model seçici — sabitlenenler, sık kullanılanlar, önerilenler, sonra tümü.
 *
 * `/api/model/options` sağlayıcı başına yüzlerce model dönebiliyor (Nous Portal
 * tek başına 40+) ve çoğu kullanılamıyor. Sıralama bilinçli olarak üç katmanlı:
 * kullanıcının **sabitlediği** (elle seçim) → **sık kullandığı** (kendi
 * alışkanlığı) → **ölçülmüş öneriler** (herkes için aynı). Aşağıda ham liste.
 *
 * Her satır uzun basılınca gizlenir/geri gelir; sabitleme ise sağdaki raptiye.
 */
@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
fun ModelPickerSheet(
    providers: List<ModelProvider>,
    currentModel: String?,
    loading: Boolean,
    brokenModels: Set<String>,
    hiddenModels: Set<String>,
    pinnedModels: List<String>,
    modelUsage: Map<String, Int>,
    showBroken: Boolean,
    onToggleShowBroken: () -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onSelect: (provider: String, model: String, persist: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var persist by remember { mutableStateOf(false) }

    val q = query.trim().lowercase()

    // Ham sağlayıcı listesini tek düz listeye indir; öneri bilgisi varsa iliştir.
    val allRows = remember(providers, brokenModels, hiddenModels, modelUsage) {
        val recByKey = RECOMMENDED_MODELS.associateBy { "${it.provider}/${it.model}" }
        providers.flatMap { p ->
            p.models.map { m ->
                val key = "${p.slug}/$m"
                val rec = recByKey[key]
                ModelRow(
                    provider = p.slug,
                    model = m,
                    label = rec?.label ?: m,
                    note = rec?.note ?: p.priceLabel(m),
                    seconds = rec?.seconds,
                    local = rec?.local == true,
                    usable = p.isUsable(m),
                    broken = key in brokenModels,
                    hidden = key in hiddenModels,
                    uses = modelUsage[key] ?: 0,
                )
            }
        }
    }

    fun matches(r: ModelRow) =
        q.isEmpty() || r.model.lowercase().contains(q) || r.label.lowercase().contains(q)

    val pinned = pinnedModels.mapNotNull { key -> allRows.firstOrNull { it.key == key } }
        .filter(::matches)

    // Sık kullanılanlar: sabitlenenler zaten üstte, tekrar etmesin.
    val frequent = allRows
        .filter { it.uses > 0 && it.key !in pinnedModels && !it.hidden && matches(it) }
        .sortedByDescending { it.uses }
        .take(5)

    val shownKeys = pinned.map { it.key }.toSet() + frequent.map { it.key }.toSet()

    val recommended = RECOMMENDED_MODELS
        .mapNotNull { rec -> allRows.firstOrNull { it.key == "${rec.provider}/${rec.model}" } }
        .filter { it.key !in shownKeys && !it.broken && !it.hidden && matches(it) }

    // Sağlayıcılar sonradan yükleniyor ve üstteki bölümler (sabitlenen / sık
    // kullanılan / önerilen) listeye **başa** ekleniyor. LazyColumn anahtarlı
    // öğelerde görünürdeki öğeyi sabit tutmaya çalıştığı için yeni satırlar
    // görüş alanının üstünde kalıp hiç görünmüyordu. Üst blok değiştiğinde
    // listeyi başa alıyoruz — sabitleme/gizleme sonrası da doğru davranış bu.
    val listState = rememberLazyListState()

    // Bölüm başlıkları burada çözülüyor: aşağıdaki `LazyColumn` içeriği
    // `LazyListScope` — @Composable değil, orada `S.t2` çağrılamıyor.
    val pinnedLabel = S.t2("Sabitlenen", "Pinned")
    val frequentLabel = S.t2("Sık kullanılan", "Frequently used")
    val recommendedLabel = S.t2("Önerilen", "Recommended")
    val recommendedHint = S.t2("bu sunucuda ölçüldü", "measured on this server")
    // Üç boyut ayrı ayrı anahtar: sabitleme bir modeli "önerilen"den alıp
    // "sabitlenen"e taşıdığında toplam değişmiyor ama liste yeniden diziliyor.
    LaunchedEffect(pinned.size, frequent.size, recommended.size) {
        listState.scrollToItem(0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HermesColors.Background,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).heightIn(max = 640.dp)) {
            Text(
                S.t2("Model seç", "Pick a model"),
                color = HermesColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                currentModel?.let { S.t2("Şu an: $it", "Now: $it") } ?: "Sunucudan okunuyor…",
                style = MonoTextStyle,
                color = HermesColors.TextMuted,
            )
            Spacer(Modifier.height(10.dp))

            // Varsayılan: yalnız bu oturum. Gateway'in `/model` komutu da böyle
            // davranıyor; `--global` ancak açıkça istenince ekleniyor.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { persist = !persist }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = persist, onCheckedChange = { persist = it })
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        S.t2("Varsayılan model yap", "Make it the default"),
                        color = HermesColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (persist) S.t2("Tüm yeni oturumlarda kullanılır", "Used for every new session")
                        else S.t2("Yalnız bu sohbette geçerli", "Applies to this chat only"),
                        color = HermesColors.TextFaint,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(S.t2("Model ara…", "Search models…"), color = HermesColors.TextFaint) },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = HermesColors.TextMuted)
                },
                singleLine = true,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                S.t2("Raptiye ile sabitle · satıra uzun bas, gizle", "Tap the pin to keep it on top · long-press a row to hide"),
                color = HermesColors.TextFaint,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            if (loading) {
                Text(S.t2("Yükleniyor…", "Loading…"), color = HermesColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {

                // Üst bölümler tek bir listeden çiziliyor. Daha önce bunu yerel bir
                // `fun section()` yapıyordu; içindeki `item()` çağrıları
                // LazyColumn'a hiç kaydolmadığı için bölümler görünmüyordu.
                val topSections = listOf(
                    Triple("pin", pinnedLabel to null, pinned),
                    Triple("sik", frequentLabel to null, frequent),
                    Triple("onerilen", recommendedLabel to recommendedHint, recommended),
                )
                topSections.forEach { (id, titleAndHint, rows) ->
                    if (rows.isNotEmpty()) {
                        val (title, hint) = titleAndHint
                        item(key = "hdr-$id") {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SectionLabel(title)
                                hint?.let {
                                    Spacer(Modifier.width(8.dp))
                                    Text(it, color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        items(rows, key = { "$id-${it.key}" }) { row ->
                            ModelRowView(
                                row = row,
                                selected = row.model == currentModel,
                                pinned = row.key in pinnedModels,
                                prominent = true,
                                onSelect = { onSelect(row.provider, row.model, persist) },
                                onTogglePin = { onTogglePin(row.key) },
                                onToggleHidden = { onToggleHidden(row.key) },
                            )
                        }
                    }
                }

                // ── Tüm modeller ─────────────────────────────────────
                item(key = "hdr-tumu") {
                    val gizli = brokenModels.size + hiddenModels.size
                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel(S.t2("Tüm modeller", "All models"))
                        Spacer(Modifier.weight(1f))
                        if (gizli > 0) {
                            Row(
                                Modifier
                                    .clickable { onToggleShowBroken() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (showBroken) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = HermesColors.TextMuted,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    if (showBroken) "gizlenenleri sakla" else S.t2("$gizli gizli — göster", "$gizli hidden — show"),
                                    color = HermesColors.TextMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }

                providers.forEach { provider ->
                    val rows = allRows
                        .filter { it.provider == provider.slug && matches(it) }
                        .filter { showBroken || (!it.broken && !it.hidden) }
                        // Kullanılabilirler üstte: kilitli modeller listeyi tıkamasın.
                        .sortedByDescending { it.usable }
                    if (rows.isEmpty()) return@forEach

                    item(key = "hdr-${provider.slug}") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionLabel(provider.name.ifBlank { provider.slug })
                            if (provider.isCurrent) {
                                Spacer(Modifier.width(8.dp))
                                Text("etkin", color = HermesColors.Online, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.weight(1f))
                            Text("${rows.size}", color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    items(rows, key = { "all-${it.key}" }) { row ->
                        ModelRowView(
                                row = row,
                                selected = row.model == currentModel,
                                pinned = row.key in pinnedModels,
                                prominent = false,
                                onSelect = { onSelect(row.provider, row.model, persist) },
                                onTogglePin = { onTogglePin(row.key) },
                            onToggleHidden = { onToggleHidden(row.key) },
                        )
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

/**
 * Tek model satırı.
 *
 * [prominent] üstteki bölümler için — kart görünümü, daha büyük yazı. Ham
 * listede yüzlerce satır olduğu için orada daha sıkışık bir biçim kullanılıyor.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ModelRowView(
    row: ModelRow,
    selected: Boolean,
    pinned: Boolean,
    prominent: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleHidden: () -> Unit,
) {
    val bg = when {
        selected -> HermesColors.Surface
        prominent -> HermesColors.SurfaceDim
        else -> HermesColors.Background
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = if (prominent) 6.dp else 0.dp)
            .background(bg, MaterialTheme.shapes.medium)
            .combinedClickable(
                enabled = row.usable,
                onClick = onSelect,
                onLongClick = onToggleHidden,
            )
            .padding(
                start = if (prominent) 12.dp else 10.dp,
                end = 4.dp,
                top = 11.dp,
                bottom = 11.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                DemoMask.model(row.label),
                color = when {
                    !row.usable -> HermesColors.TextFaint
                    selected -> HermesColors.Midground
                    prominent -> HermesColors.TextPrimary
                    else -> HermesColors.TextSecondary
                },
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                fontWeight = if (prominent) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val note = when {
                row.hidden -> "gizlendi — uzun bas, geri getir"
                row.broken -> S.t2("denendi, yanıt vermedi", "tried, no response")
                !row.usable -> S.t2("kredi gerekiyor — kullanılamaz", "needs credit — unavailable")
                row.uses > 0 -> S.t2("${row.uses} kez kullandın", "used ${row.uses} times") +
                    (row.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                else -> row.note
            }
            if (note.isNotBlank() || row.local) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.local) {
                        Text(
                            "YEREL",
                            color = HermesColors.Online,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    if (note.isNotBlank()) {
                        Text(
                            note,
                            color = when {
                                row.hidden || row.broken || !row.usable -> HermesColors.Danger
                                row.note == S.t2("ücretsiz", "free") -> HermesColors.Online
                                else -> HermesColors.TextMuted
                            },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        row.seconds?.let { sn ->
            Text(
                String.format("%.1f sn", sn),
                color = when {
                    sn <= 6 -> HermesColors.Online
                    sn <= 12 -> HermesColors.Busy
                    else -> HermesColors.TextFaint
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }

        if (!row.usable) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.Lock,
                contentDescription = S.t2("Kullanılamaz", "Unavailable"),
                tint = HermesColors.TextFaint,
                modifier = Modifier.size(15.dp),
            )
        } else if (selected) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = HermesColors.Online,
                modifier = Modifier.size(17.dp),
            )
        }

        // Raptiye her zaman en sağda ve satır seçiminden bağımsız tıklanır.
        Icon(
            if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
            contentDescription = if (pinned) S.t2("Sabiti kaldır", "Unpin") else "Sabitle",
            tint = if (pinned) HermesColors.Midground else HermesColors.TextFaint,
            modifier = Modifier
                .size(30.dp)
                .clickable(onClick = onTogglePin)
                .padding(7.dp),
        )
    }
}
