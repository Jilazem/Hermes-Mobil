package com.hermes.mobile.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.AppModule
import com.hermes.mobile.data.ModuleKind
import com.hermes.mobile.data.ModuleSchema
import com.hermes.mobile.data.ModuleStore
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Modüller ekranı: fabrika modülü (Formlar) + kullanıcının ekledikleri.
 *
 * Modül eklemek kod değiştirmez; tür seçilir ve mevcut ekrana bağlanır.
 * Serbest web modülü uygulama içi WebView açmaz — harici tarayıcıya gider
 * (token/çerez sızmasın).
 */
@Composable
fun ModulesScreen(
    modules: List<AppModule>,
    error: String?,
    onAdd: (AppModule) -> List<String>,
    onRemove: (String) -> Unit,
    onOpen: (AppModule) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(HermesColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.t2("Geri", "Back"), tint = HermesColors.TextMuted) }
            }
            Column(Modifier.weight(1f)) {
                Text("🧩 " + S.t2("Modüller", "Modules"), color = HermesColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    S.t2("${modules.size} modül · kendi kısayollarını ekle", "${modules.size} modules · add your own shortcuts"),
                    color = HermesColors.TextFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, S.t2("Modül ekle", "Add module"), tint = HermesColors.Midground) }
        }

        error?.let {
            Text(it, color = HermesColors.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(modules, key = { it.id }) { module ->
                Column(
                    Modifier.fillMaxWidth()
                        .background(HermesColors.Surface, MaterialTheme.shapes.large)
                        .border(1.dp, HermesColors.Border, MaterialTheme.shapes.large)
                        .clickable { onOpen(module) }
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(module.icon, style = MaterialTheme.typography.headlineSmall)
                        if (!module.builtIn) {
                            IconButton(onClick = { onRemove(module.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.Delete, S.t2("Modülü sil", "Delete module"), tint = HermesColors.TextFaint, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Text(
                        module.name,
                        color = HermesColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        S.t2(module.kind.labelTr, module.kind.labelEn) + if (module.builtIn) " · " + S.t2("hazır", "built-in") else "",
                        color = HermesColors.TextFaint,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddModuleDialog(
            onDismiss = { showAdd = false },
            onConfirm = { module ->
                val errors = onAdd(module)
                if (errors.isEmpty()) showAdd = false
                errors
            },
        )
    }
}

/**
 * Modül ekleme formu. Hatalar diyalog içinde kalır: geçersiz modül listeye
 * girmez, kullanıcı sebebi görür.
 */
@Composable
private fun AddModuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (AppModule) -> List<String>,
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("🧩") }
    var kind by remember { mutableStateOf(ModuleKind.FORMS) }
    var url by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(emptyList<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.t2("Modül ekle", "Add module")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = icon,
                        onValueChange = { if (it.length <= 4) icon = it },
                        label = { Text(S.t2("Simge", "Icon")) },
                        singleLine = true,
                        modifier = Modifier.padding(end = 8.dp).size(width = 84.dp, height = 58.dp),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= ModuleSchema.MAX_NAME) name = it },
                        label = { Text(S.t2("Modül adı", "Module name")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(S.t2("Tür", "Type"), color = HermesColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ModuleKind.entries.forEach { candidate ->
                        FilterChip(
                            selected = kind == candidate,
                            onClick = { kind = candidate },
                            label = { Text(S.t2(candidate.labelTr, candidate.labelEn), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                if (kind == ModuleKind.WEB) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("https://…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                errors.forEach { Text(it, color = HermesColors.Danger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val module = AppModule(
                    id = ModuleSchema.newId(),
                    name = name.trim(),
                    icon = icon.trim().ifBlank { "🧩" },
                    kind = kind,
                    url = url.trim(),
                )
                errors = onConfirm(module)
            }) { Text(S.t2("Ekle", "Add")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.t2("Vazgeç", "Cancel")) } },
    )
}

/**
 * Modül barındırıcı: modül türüne göre ekran.
 *
 * - FORMS: form merkezi (kendi geri yığınıyla)
 * - FOLLOWING: ortak takip listesi (SessionListPane)
 * - PANEL: pano sekmesine yönlendirme
 * - WEB: harici tarayıcı
 */
@Composable
fun ModuleHostScreen(
    module: AppModule,
    rows: List<DrawerRow>,
    onBack: () -> Unit,
    onOpenRow: (DrawerRow) -> Unit,
    onToggleFollow: (DrawerRow) -> Unit,
    onOpenFollowingTab: () -> Unit,
    onOpenPanelTab: () -> Unit,
) {
    val context = LocalContext.current
    when (module.kind) {
        ModuleKind.MACROS -> MacrosScreen(onBack)
        ModuleKind.RECORDINGS -> RecordingArchiveScreen(onBack)
        ModuleKind.FORMS -> FormsHubScreen(onBack = onBack)
        ModuleKind.FOLLOWING -> Column(Modifier.fillMaxSize().background(HermesColors.Background)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.t2("Geri", "Back"), tint = HermesColors.TextMuted) }
                Column(Modifier.weight(1f)) {
                    Text("${module.icon} ${module.name}", color = HermesColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(S.t2("Takip edilen oturumlar", "Followed sessions"), color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onOpenFollowingTab) { Text(S.t2("Tam ekran", "Full screen")) }
            }
            SessionListPane(
                rows = rows,
                query = "",
                onOpen = onOpenRow,
                onToggleFollow = onToggleFollow,
                compact = false,
                followedOnly = true,
                modifier = Modifier.weight(1f),
            )
        }
        ModuleKind.PANEL -> Box(Modifier.fillMaxSize().background(HermesColors.Background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${module.icon} ${module.name}", color = HermesColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(S.t2("Pano ana ekranda açılır", "Dashboard opens on the main screen"), color = HermesColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onBack) { Text(S.t2("Geri", "Back")) }
                    Button(onClick = onOpenPanelTab) { Text(S.t2("Panoya git", "Go to dashboard")) }
                }
            }
        }
        ModuleKind.WEB -> Box(Modifier.fillMaxSize().background(HermesColors.Background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${module.icon} ${module.name}", color = HermesColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(module.url, color = HermesColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onBack) { Text(S.t2("Geri", "Back")) }
                    Button(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(module.url)))
                        }
                    }) { Text(S.t2("Tarayıcıda aç", "Open in browser")) }
                }
            }
        }
    }
}

/** Boş modül ekranı için yükseklik ayırıcı (grid hücreleri eşit kalsın). */
internal val ModuleCardMinHeight = 96.dp

/** Modül türü etiketi — modül kartı ve ekleme diyaloğunda ortak. */
fun moduleKindLabel(kind: ModuleKind, turkish: Boolean): String =
    if (turkish) kind.labelTr else kind.labelEn

/**
 * Modüller sekmesinin kökü: depo + modül içi geri yığını burada.
 *
 * MainActivity yalnız bu bileşeni çağırır; modül açma/kapatma durumu
 * sekme değişse de yaşasın diye durum burada tutulur.
 */
@Composable
fun ModulesTab(
    rows: List<DrawerRow>,
    onOpenRow: (DrawerRow) -> Unit,
    onToggleFollow: (DrawerRow) -> Unit,
    onOpenFollowingTab: () -> Unit,
    onOpenPanelTab: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ModuleStore(context) }
    var modules by remember { mutableStateOf(store.modules()) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<AppModule?>(null) }
    val removeFailedText = S.t2("Modül silinemedi (hazır modül olabilir)", "Could not delete module (maybe built-in)")

    val current = open
    if (current == null) {
        ModulesScreen(
            modules = modules,
            error = error,
            onAdd = { module ->
                val errors = store.add(module)
                if (errors.isEmpty()) {
                    modules = store.modules()
                    error = null
                } else {
                    error = errors.joinToString(" · ")
                }
                errors
            },
            onRemove = { id ->
                if (store.remove(id)) {
                    modules = store.modules()
                    error = null
                } else {
                    error = removeFailedText
                }
            },
            onOpen = { open = it },
        )
    } else {
        ModuleHostScreen(
            module = current,
            rows = rows,
            onBack = { open = null },
            onOpenRow = onOpenRow,
            onToggleFollow = onToggleFollow,
            onOpenFollowingTab = onOpenFollowingTab,
            onOpenPanelTab = onOpenPanelTab,
        )
    }
}
