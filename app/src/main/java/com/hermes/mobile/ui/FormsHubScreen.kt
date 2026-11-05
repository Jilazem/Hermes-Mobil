package com.hermes.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.hermes.mobile.data.FormRecord
import com.hermes.mobile.data.FormStore
import com.hermes.mobile.data.FormTemplate
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Formlar modülünün kökü: şablon listesi → doldur / düzenle / kayıtlar.
 *
 * Modül ekleme (plan 2026-10-21) bu ekranı "Formlar" modülü olarak açar;
 * sol çekmecedeki takip paneliyle karışmasın diye kendi geri yığını var.
 */
@Composable
fun FormsHubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { FormStore(context) }
    var templates by remember { mutableStateOf(store.templates()) }
    var tab by remember { mutableStateOf(0) }
    var route by remember { mutableStateOf<FormsRoute>(FormsRoute.Hub) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        templates = store.templates()
        error = store.lastError
    }

    when (val current = route) {
        is FormsRoute.Hub -> HubContent(
            templates = templates,
            tab = tab,
            error = error,
            onTab = { tab = it },
            onBack = onBack,
            onNew = { route = FormsRoute.Build(null) },
            onFill = { route = FormsRoute.Fill(it) },
            onEdit = { route = FormsRoute.Build(it) },
            onDelete = { template ->
                if (store.deleteTemplate(template.id)) refresh() else error = store.lastError ?: "Silinemedi"
            },
            onRecords = { route = FormsRoute.Records(it) },
        )
        is FormsRoute.Build -> FormBuilderScreen(
            template = current.template,
            store = store,
            onDone = { refresh(); route = FormsRoute.Hub },
            onBack = { route = FormsRoute.Hub },
        )
        is FormsRoute.Fill -> FormFillScreen(
            template = current.template,
            store = store,
            onBack = { refresh(); route = FormsRoute.Hub },
            onOpenRecords = { route = FormsRoute.Records(current.template) },
        )
        is FormsRoute.Records -> RecordsScreen(
            template = current.template,
            store = store,
            onBack = { route = FormsRoute.Hub },
            onFill = { route = FormsRoute.Fill(current.template) },
        )
    }
}

/** Hub içi gezinme — geri tuşu tek adım geri gider (modül içi yığın). */
private sealed interface FormsRoute {
    data object Hub : FormsRoute
    data class Build(val template: FormTemplate?) : FormsRoute
    data class Fill(val template: FormTemplate) : FormsRoute
    data class Records(val template: FormTemplate) : FormsRoute
}

@Composable
private fun HubContent(
    templates: List<FormTemplate>,
    tab: Int,
    error: String?,
    onTab: (Int) -> Unit,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onFill: (FormTemplate) -> Unit,
    onEdit: (FormTemplate) -> Unit,
    onDelete: (FormTemplate) -> Unit,
    onRecords: (FormTemplate) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(HermesColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.t2("Geri", "Back"), tint = HermesColors.TextMuted) }
            Column(Modifier.weight(1f)) {
                Text("📋 " + S.t2("Formlar", "Forms"), color = HermesColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    S.t2("${templates.size} şablon · doldur, kaydet, dışa aktar", "${templates.size} templates · fill, save, export"),
                    color = HermesColors.TextFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onNew) { Text("＋ " + S.t2("Yeni", "New")) }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = tab == 0, onClick = { onTab(0) }, label = { Text(S.t2("Şablonlar", "Templates"), style = MaterialTheme.typography.labelSmall) })
            FilterChip(selected = tab == 1, onClick = { onTab(1) }, label = { Text(S.t2("Doldur", "Fill"), style = MaterialTheme.typography.labelSmall) })
            FilterChip(selected = tab == 2, onClick = { onTab(2) }, label = { Text(S.t2("Kayıtlar", "Records"), style = MaterialTheme.typography.labelSmall) })
        }

        error?.let {
            Text(it, color = HermesColors.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        }

        when (tab) {
            1 -> FillPicker(templates, onFill)
            2 -> RecordsPicker(templates, onRecords)
            else -> TemplateList(templates, onFill, onEdit, onDelete, onNew)
        }
    }
}

@Composable
private fun TemplateList(
    templates: List<FormTemplate>,
    onFill: (FormTemplate) -> Unit,
    onEdit: (FormTemplate) -> Unit,
    onDelete: (FormTemplate) -> Unit,
    onNew: () -> Unit,
) {
    if (templates.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(S.t2("Henüz şablon yok", "No templates yet"), color = HermesColors.TextPrimary)
                TextButton(onClick = onNew) { Text(S.t2("İlk şablonu oluştur", "Create the first template")) }
            }
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(templates, key = { it.id }) { template ->
            TemplateCard(
                template = template,
                onFill = { onFill(template) },
                onEdit = { onEdit(template) },
                onDelete = { onDelete(template) },
            )
        }
    }
}

@Composable
private fun FillPicker(templates: List<FormTemplate>, onFill: (FormTemplate) -> Unit) {
    if (templates.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(S.t2("Doldurulacak şablon yok", "No template to fill"), color = HermesColors.TextMuted)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(templates, key = { it.id }) { template ->
            Row(
                Modifier.fillMaxWidth().background(HermesColors.Surface, MaterialTheme.shapes.medium).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(template.icon, style = MaterialTheme.typography.titleMedium)
                Text(
                    template.name,
                    color = HermesColors.TextPrimary,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { onFill(template) }) { Text(S.t2("Doldur", "Fill")) }
            }
        }
    }
}

@Composable
private fun RecordsPicker(templates: List<FormTemplate>, onRecords: (FormTemplate) -> Unit) {
    if (templates.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(S.t2("Şablon yok", "No templates"), color = HermesColors.TextMuted)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(templates, key = { it.id }) { template ->
            Row(
                Modifier.fillMaxWidth().background(HermesColors.Surface, MaterialTheme.shapes.medium).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(template.icon, style = MaterialTheme.typography.titleMedium)
                Text(
                    template.name,
                    color = HermesColors.TextPrimary,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { onRecords(template) }) { Text(S.t2("Kayıtlar", "Records")) }
            }
        }
    }
}

@Composable
private fun RecordsScreen(
    template: FormTemplate,
    store: FormStore,
    onBack: () -> Unit,
    onFill: () -> Unit,
) {
    val context = LocalContext.current
    var records by remember(template.id) { mutableStateOf(store.records(template.id)) }
    var exportText by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(HermesColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.t2("Geri", "Back"), tint = HermesColors.TextMuted) }
            Column(Modifier.weight(1f)) {
                Text(
                    "${template.icon} ${template.name} · " + S.t2("kayıtlar", "records"),
                    color = HermesColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    S.t2("${records.size} kayıt", "${records.size} records"),
                    color = HermesColors.TextFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            TextButton(onClick = onFill) { Text(S.t2("Doldur", "Fill")) }
        }

        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(S.t2("Bu şablonda kayıt yok", "No records for this template"), color = HermesColors.TextPrimary)
                    TextButton(onClick = onFill) { Text(S.t2("İlk kaydı oluştur", "Create the first record")) }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records, key = { it.id }) { record ->
                    FormRecordRow(record, template, onOpen = { exportText = store.exportRecord(template, record) })
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = {
                        records = store.records(template.id)
                    }) { Text(S.t2("Yenile", "Refresh")) }
                }
            }
        }
    }

    val text = exportText
    if (text != null) {
        AlertDialog(
            onDismissRequest = { exportText = null },
            title = { Text(S.t2("Kayıt metni", "Record text")) },
            text = {
                Column {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = HermesColors.TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    copyToClipboard(context, text)
                    exportText = null
                }) { Text(S.t2("Kopyala", "Copy")) }
            },
            dismissButton = {
                TextButton(onClick = { exportText = null }) { Text(S.t2("Kapat", "Close")) }
            },
        )
    }
}

/** Panoya kopyalama — dışa aktarımın tek yolu (dosya paylaşımı sonraki tur). */
private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("Hermes form kaydı", text))
}

/** Kayıt sayacı — modül kartında "N kayıt" göstermek için. */
fun recordCount(store: FormStore, templateId: String): Int = store.records(templateId).size

/** Tek kayıt için dışa aktarım (paylaşım/dışa aktarım düğmeleri için ortak). */
fun exportRecordText(store: FormStore, template: FormTemplate, record: FormRecord): String =
    store.exportRecord(template, record)
