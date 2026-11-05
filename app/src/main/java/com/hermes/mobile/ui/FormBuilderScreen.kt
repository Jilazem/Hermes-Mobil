package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.FormField
import com.hermes.mobile.data.FormFieldType
import com.hermes.mobile.data.FormSchema
import com.hermes.mobile.data.FormStore
import com.hermes.mobile.data.FormTemplate
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Şablon düzenleyici — yeni şablon veya var olanı düzenleme.
 *
 * Kaydetmeden önce [FormSchema.validate] çalışır; hata varsa şablon diske
 * yazılmaz ve sebep ekranda listelenir. Böylece "kaydettim ama doldururken
 * boş alan çıktı" durumu oluşmaz.
 */
@Composable
fun FormBuilderScreen(
    template: FormTemplate?,
    store: FormStore,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(template?.name.orEmpty()) }
    var icon by remember { mutableStateOf(template?.icon ?: "📋") }
    var fields by remember { mutableStateOf(template?.fields.orEmpty()) }
    var errors by remember { mutableStateOf(emptyList<String>()) }
    var showIconPicker by remember { mutableStateOf(false) }

    val icons = listOf("📋", "🧭", "🗺️", "🛠️", "📐", "📸", "🧾", "⚖️", "🏠", "📝")

    Column(Modifier.fillMaxSize().background(HermesColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.t2("Geri", "Back"), tint = HermesColors.TextMuted) }
            Column(Modifier.weight(1f)) {
                Text(
                    if (template == null) S.t2("Yeni şablon", "New template") else S.t2("Şablonu düzenle", "Edit template"),
                    color = HermesColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    S.t2("${fields.size} alan", "${fields.size} fields"),
                    color = HermesColors.TextFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            TextButton(onClick = {
                val candidate = FormTemplate(
                    id = template?.id ?: FormSchema.newId("tpl"),
                    name = name.trim(),
                    icon = icon,
                    fields = fields,
                    builtIn = template?.builtIn ?: false,
                )
                val problems = store.saveTemplate(candidate)
                if (problems.isEmpty()) {
                    errors = emptyList()
                    onDone()
                } else errors = problems
            }) { Text(S.t2("Kaydet", "Save")) }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { showIconPicker = true }) {
                        Text(icon, style = MaterialTheme.typography.headlineSmall)
                    }
                    DropdownMenu(expanded = showIconPicker, onDismissRequest = { showIconPicker = false }) {
                        icons.forEach { candidate ->
                            DropdownMenuItem(text = { Text(candidate, style = MaterialTheme.typography.headlineSmall) }, onClick = {
                                icon = candidate
                                showIconPicker = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= FormSchema.MAX_NAME) name = it },
                    label = { Text(S.t2("Şablon adı", "Template name")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            fields.forEachIndexed { index, field ->
                FormFieldEditor(
                    index = index,
                    field = field,
                    canMoveUp = index > 0,
                    canMoveDown = index < fields.lastIndex,
                    onChange = { updated -> fields = fields.toMutableList().also { it[index] = updated } },
                    onDelete = { fields = fields.filterIndexed { i, _ -> i != index } },
                    onMoveUp = {
                        val list = fields.toMutableList()
                        val tmp = list[index - 1]
                        list[index - 1] = list[index]
                        list[index] = tmp
                        fields = list
                    },
                    onMoveDown = {
                        val list = fields.toMutableList()
                        val tmp = list[index + 1]
                        list[index + 1] = list[index]
                        list[index] = tmp
                        fields = list
                    },
                )
            }

            TextButton(onClick = {
                if (fields.size < FormSchema.MAX_FIELDS) {
                    fields = fields + FormField(
                        id = FormSchema.newId(),
                        type = FormFieldType.TEXT,
                        label = "",
                    )
                }
            }) { Text("＋ " + S.t2("Alan ekle", "Add field")) }

            if (errors.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().background(HermesColors.Danger.copy(alpha = 0.12f), MaterialTheme.shapes.medium).padding(12.dp)) {
                    errors.forEach { Text(it, color = HermesColors.Danger, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun FormFieldEditor(
    index: Int,
    field: FormField,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (FormField) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var typeMenu by remember { mutableStateOf(false) }
    var optionsText by remember(field.id) {
        mutableStateOf(field.options.joinToString(", "))
    }
    Column(
        Modifier.fillMaxWidth()
            .background(HermesColors.Surface, MaterialTheme.shapes.medium)
            .border(1.dp, HermesColors.Border, MaterialTheme.shapes.medium)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}.",
                color = HermesColors.TextFaint,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 6.dp),
            )
            OutlinedTextField(
                value = field.label,
                onValueChange = { if (it.length <= FormSchema.MAX_LABEL) onChange(field.copy(label = it)) },
                label = { Text(S.t2("Etiket", "Label")) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(enabled = canMoveUp, onClick = onMoveUp) {
                Icon(Icons.Default.KeyboardArrowUp, S.t2("Yukarı taşı", "Move up"), modifier = Modifier.size(20.dp))
            }
            IconButton(enabled = canMoveDown, onClick = onMoveDown) {
                Icon(Icons.Default.KeyboardArrowDown, S.t2("Aşağı taşı", "Move down"), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, S.t2("Alanı sil", "Delete field"), tint = HermesColors.Danger, modifier = Modifier.size(20.dp))
            }
        }

        Box {
            Row(
                Modifier.fillMaxWidth().clickable { typeMenu = true }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(S.t2("Tür: ${field.type.labelTr}", "Type: ${field.type.labelEn}"), color = HermesColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null, tint = HermesColors.TextMuted)
            }
            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                FormFieldType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(S.t2(type.labelTr, type.labelEn)) },
                        onClick = {
                            onChange(
                                field.copy(
                                    type = type,
                                    options = if (type == FormFieldType.CHOICE) field.options else emptyList(),
                                ),
                            )
                            typeMenu = false
                        },
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.t2("Zorunlu", "Required"), color = HermesColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Switch(checked = field.required, onCheckedChange = { onChange(field.copy(required = it)) })
        }

        if (field.type == FormFieldType.CHOICE) {
            OutlinedTextField(
                value = optionsText,
                onValueChange = { text ->
                    optionsText = text
                    onChange(field.copy(options = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }))
                },
                label = { Text(S.t2("Seçenekler (virgülle)", "Options (comma separated)")) },
                placeholder = { Text(S.t2("Nem, Çatlak, Kırık", "Damp, Crack, Broken")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (field.type != FormFieldType.CHOICE && field.type != FormFieldType.PHOTO && field.type != FormFieldType.SIGNATURE) {
            OutlinedTextField(
                value = field.hint,
                onValueChange = { onChange(field.copy(hint = it)) },
                label = { Text(S.t2("İpucu (isteğe bağlı)", "Hint (optional)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Önizleme: düzenleyicide görünen kontrol, doldurma ekranındakiyle aynı.
        FormFieldInput(
            field = field.copy(label = field.label.ifBlank { S.t2("Etiketsiz alan", "Unnamed field") }),
            value = "",
            enabled = false,
            onChange = {},
            onPickPhoto = null,
        )
    }
}

/** Şablon kartı — modül ekranı ve şablon listesinde ortak. */
@Composable
fun TemplateCard(
    template: FormTemplate,
    onFill: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(HermesColors.Surface, MaterialTheme.shapes.large)
            .border(1.dp, HermesColors.Border, MaterialTheme.shapes.large)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(template.icon, style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    template.name,
                    color = HermesColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    S.t2(
                        "${template.fields.size} alan" + if (template.builtIn) " · hazır" else "",
                        "${template.fields.size} fields" + if (template.builtIn) " · built-in" else "",
                    ),
                    color = HermesColors.TextFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onFill, modifier = Modifier.weight(1f)) { Text(S.t2("Doldur", "Fill")) }
            TextButton(onClick = onEdit) { Text(S.t2("Düzenle", "Edit")) }
            if (onDelete != null) TextButton(onClick = onDelete) { Text(S.t2("Sil", "Delete"), color = HermesColors.Danger) }
        }
    }
}
