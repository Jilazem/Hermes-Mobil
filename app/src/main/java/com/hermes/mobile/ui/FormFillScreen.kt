package com.hermes.mobile.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.hermes.mobile.data.FormField
import com.hermes.mobile.data.FormFieldType
import com.hermes.mobile.data.FormRecord
import com.hermes.mobile.data.FormSchema
import com.hermes.mobile.data.FormStore
import com.hermes.mobile.data.FormTemplate
import com.hermes.mobile.ui.theme.HermesColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Şablonu doldurur: alanlar türüne göre çizilir, zorunlular kayıtta denetlenir.
 *
 * Otomatik taslak: her değişiklik `filesDir/forms/drafts/<şablon>.json`'a
 * yazılır; kayıt başarılı olunca taslak silinir. Böylece uygulama öldürülse
 * bile girilen veri kaybolmaz (plan 2026-10-21 · taslak).
 */
@Composable
fun FormFillScreen(
    template: FormTemplate,
    store: FormStore,
    onBack: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    var values by remember(template.id) { mutableStateOf(store.draft(template.id)) }
    var errors by remember(template.id) { mutableStateOf(emptyList<String>()) }
    var saved by remember(template.id) { mutableStateOf<FormRecord?>(null) }
    val scope = rememberCoroutineScope()
    val saveFailedText = S.t2("Kayıt yazılamadı", "Record could not be written")

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) values = values + (PENDING_PHOTO_ID to uri.toString())
    }
    // Fotoğraf alanı seçimi: hangi alanın seçici açtığını tutar.
    var pendingPhotoField by remember { mutableStateOf<FormField?>(null) }

    LaunchedEffect(values) {
        if (saved == null) {
            withContext(Dispatchers.IO) { store.saveDraft(template.id, values) }
        }
    }

    Column(
        Modifier.fillMaxSize().background(HermesColors.Background),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.t2("Geri", "Back"), tint = HermesColors.TextMuted)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${template.icon} ${template.name}",
                    color = HermesColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    S.t2("${template.fields.size} alan · taslak otomatik", "${template.fields.size} fields · draft autosaved"),
                    color = HermesColors.TextFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            TextButton(onClick = onOpenRecords) { Text(S.t2("Kayıtlar", "Records")) }
        }

        val record = saved
        if (record != null) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("✅", style = MaterialTheme.typography.headlineLarge)
                Text(
                    S.t2("Kayıt alındı", "Record saved"),
                    color = HermesColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    S.t2("${record.values.size} alan dolu · ${template.name}", "${record.values.size} fields filled · ${template.name}"),
                    color = HermesColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = {
                    saved = null
                    values = emptyMap()
                    errors = emptyList()
                }) { Text(S.t2("Yeni kayıt", "New record")) }
                TextButton(onClick = onOpenRecords) { Text(S.t2("Kayıtları gör", "View records")) }
            }
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            template.fields.forEach { field ->
                FormFieldInput(
                    field = field,
                    value = if (field.type == FormFieldType.PHOTO) values[field.id].orEmpty() else values[field.id].orEmpty(),
                    enabled = true,
                    onChange = { values = values + (field.id to it) },
                    onPickPhoto = { pendingPhotoField = field; photoPicker.launch("image/*") },
                )
            }
            if (errors.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().background(HermesColors.Danger.copy(alpha = 0.12f), MaterialTheme.shapes.medium).padding(12.dp)) {
                    errors.forEach {
                        Text(it, color = HermesColors.Danger, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                values = emptyMap()
                store.clearDraft(template.id)
                errors = emptyList()
            }) { Text(S.t2("Temizle", "Clear")) }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val problems = FormSchema.validateValues(template, values)
                    if (problems.isNotEmpty()) {
                        errors = problems
                        return@Button
                    }
                    val saveFailed = saveFailedText
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            store.saveRecord(template.id, template, values)
                        }
                        if (result == null) {
                            errors = listOf(store.lastError ?: saveFailed)
                        } else {
                            errors = emptyList()
                            saved = result
                        }
                    }
                },
            ) { Text(S.t2("Kaydet", "Save")) }
        }
    }

    // Seçici döndüğünde değeri doğru alana yaz (uri callback alanı bilmiyor).
    LaunchedEffect(pendingPhotoField, values[PENDING_PHOTO_ID]) {
        val field = pendingPhotoField ?: return@LaunchedEffect
        val uri = values[PENDING_PHOTO_ID] ?: return@LaunchedEffect
        if (uri.isNotBlank()) {
            values = values + (field.id to uri) - PENDING_PHOTO_ID
            pendingPhotoField = null
        }
    }
}

private const val PENDING_PHOTO_ID = "__pending_photo__"

/**
 * Alan girişi — türe göre denetim. Düzenleyici ve doldurma aynı bileşeni
 * kullanır (önizleme = gerçek giriş).
 */
@Composable
fun FormFieldInput(
    field: FormField,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onPickPhoto: (() -> Unit)? = null,
) {
    val label = buildString {
        append(field.label)
        if (field.required) append(" *")
        append(" · ")
        append(field.type.labelTr)
    }
    when (field.type) {
        FormFieldType.CHOICE -> {
            Column(Modifier.fillMaxWidth()) {
                Text(label, color = HermesColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    field.options.forEach { option ->
                        FilterChip(
                            selected = value == option,
                            enabled = enabled,
                            onClick = { onChange(if (value == option) "" else option) },
                            label = { Text(option, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }
        FormFieldType.PHOTO -> {
            Column(Modifier.fillMaxWidth()) {
                Text(label, color = HermesColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(enabled = enabled && onPickPhoto != null, onClick = { onPickPhoto?.invoke() }) {
                        Text(if (value.isBlank()) S.t2("Fotoğraf seç", "Pick photo") else S.t2("Değiştir", "Change"))
                    }
                    if (value.isNotBlank()) {
                        Text(
                            value.substringAfterLast('/').take(24),
                            color = HermesColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(enabled = enabled, onClick = { onChange("") }) { Text(S.t2("Kaldır", "Remove")) }
                    }
                }
            }
        }
        FormFieldType.SIGNATURE -> {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                label = { Text(label) },
                placeholder = { Text(S.t2("Ad Soyad (ıslak imza kâğıtta)", "Name (wet signature on paper)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        else -> {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                label = { Text(label) },
                placeholder = {
                    when (field.type) {
                        FormFieldType.DATE -> Text(S.t2("GG.AA.YYYY", "DD.MM.YYYY"))
                        FormFieldType.PARSEL -> Text(S.t2("İl / İlçe / Ada / Parsel", "City / District / Block / Parcel"))
                        FormFieldType.NUMBER -> Text(S.t2("0", "0"))
                        else -> Text(field.hint.ifBlank { S.t2("Değer gir", "Enter value") })
                    }
                },
                keyboardOptions = when (field.type) {
                    FormFieldType.NUMBER -> KeyboardOptions(keyboardType = KeyboardType.Number)
                    else -> KeyboardOptions.Default
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Kayıt satırı — kayıt listesinde ve dışa aktarımda ortak biçim. */
@Composable
fun FormRecordRow(record: FormRecord, template: FormTemplate, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(HermesColors.Surface, MaterialTheme.shapes.medium)
            .border(1.dp, HermesColors.Border, MaterialTheme.shapes.medium)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(record.createdAt)),
                color = HermesColors.TextFaint,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpen) { Text(S.t2("Aç", "Open")) }
        }
        template.fields.take(4).forEach { f ->
            val v = record.values[f.id]
            if (!v.isNullOrBlank()) {
                Text(
                    "${f.label}: $v",
                    color = HermesColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Boş alan rengi (Compose'da `Color.Unspecified` yerine açık tema zemini). */
internal val FormFieldPlaceholderColor: Color @Composable get() = HermesColors.TextFaint
