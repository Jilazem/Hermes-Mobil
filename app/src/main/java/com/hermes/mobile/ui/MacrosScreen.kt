package com.hermes.mobile.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hermes.mobile.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MacrosScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MacroStore(context) }
    val scope = rememberCoroutineScope()
    val initial = remember { runCatching { store.rules() } }
    var rules by remember { mutableStateOf(initial.getOrDefault(emptyList())) }
    var enabled by remember { mutableStateOf(store.enabled) }
    var message by remember { mutableStateOf(initial.exceptionOrNull()?.let { "Makro kaydı okunamadı; mevcut dosya korunuyor." }) }
    var editing by remember { mutableStateOf<MacroRule?>(null) }
    var review by remember { mutableStateOf<List<MacroRule>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<MacroRule?>(null) }
    var executionResult by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(store.history()) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        message = if (it.values.all { granted -> granted }) "İzinler hazır. Makronu çalıştırabilirsin." else "İzin verilmedi. İzin gerektirmeyen ses ve titreşim tariflerini kullanabilirsin."
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                enabled = store.enabled; history = store.history()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (store.enabled) runCatching { ContextCompat.startForegroundService(context, Intent(context, MacroMonitorService::class.java)) }
            .onFailure { store.enabled = false; enabled = false; message = "Otomatik kuralları yeniden etkinleştir." }
    }
    fun save(next: List<MacroRule>) {
        runCatching { store.save(next); rules = next }.onFailure { message = it.message ?: "Kaydedilemedi" }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val raw = context.contentResolver.openInputStream(uri)!!.use { MacroRules.read(it) }
                MacroRules.import(raw)
            } }.onSuccess { review = it }.onFailure { message = it.message ?: "Dosya okunamadı" }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(MacroRules.export(rules).toByteArray()) } } }
                .onSuccess { message = "Makrolar dışa aktarıldı; içe aktarılınca duraklatılmış açılır." }.onFailure { message = "Dışa aktarılamadı" }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("Geri") }
            Text("⚡ Makrolar", style = MaterialTheme.typography.headlineSmall)
        }
        Text("Telefonunda çalışan küçük otomasyonlar. Tetikleyiciyi seç, eylemleri düzenle, önce elle dene.")
        Text("Önce hazır bir tarif ekle → Elle dene → Çalıştır. Otomatik çalışması için tarifte Etkin ve üstte Otomatik kurallar açık olmalı.", style = MaterialTheme.typography.bodySmall)
        Card {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text("Otomatik kurallar"); Text(if (enabled) "Bildirimden de durdurabilirsin" else "Duraklatıldı · elle denemeler kullanılabilir", style = MaterialTheme.typography.bodySmall) }
                Switch(checked = enabled, onCheckedChange = { value ->
                    runCatching {
                        check(!value || androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) { "Önce bildirim iznini açın" }
                        store.enabled = value
                        if (value) ContextCompat.startForegroundService(context, Intent(context, MacroMonitorService::class.java))
                        else context.stopService(Intent(context, MacroMonitorService::class.java))
                        enabled = value
                    }.onFailure { store.enabled = false; enabled = false; message = "İzleyici başlatılamadı: ${it.message ?: it.javaClass.simpleName}" }
                })
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editing = MacroRule(name = "Yeni makro", actions = listOf(MacroAction("notify", "Makro çalıştı"))) }) { Text("Yeni makro") }
            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("İçe aktar") }
            OutlinedButton(onClick = { exporter.launch("Hermes-Makrolar.json") }, enabled = rules.isNotEmpty()) { Text("Dışa aktar") }
            TextButton(onClick = { permissions.launch(buildList {
                add(android.Manifest.permission.CAMERA)
                if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()) }) { Text("İzinleri hazırla") }
            TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }) { Text("Uygulama ayarları") }
        }
        Text("Bir tarifle başla", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MacroRules.recipes().forEach { recipe -> AssistChip(onClick = {
                val added = recipe.copy(id = java.util.UUID.randomUUID().toString())
                save(rules + added)
                if (rules.any { it.id == added.id }) message = "${added.name} eklendi. Aşağıdaki Elle dene düğmesine dokun."
            }, label = { Text("+ ${recipe.name}") }) }
        }
        rules.forEach { rule ->
            val problems = MacroRules.validate(rule)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium)
                    Text("${MacroRules.triggers[rule.trigger] ?: rule.trigger} · ${rule.actions.size} eylem · ${rule.cooldown} sn ara", style = MaterialTheme.typography.bodySmall)
                    Text(rule.source, style = MaterialTheme.typography.labelSmall)
                    if (problems.isNotEmpty()) Text("İnceleme kaydı · ${problems.joinToString(" · ").take(500)}", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (problems.isEmpty()) {
                            OutlinedButton(enabled = !running, onClick = { pending = rule }) { Text("Elle dene") }
                            TextButton(onClick = { editing = rule }) { Text("Düzenle") }
                            if (rule.trigger != "manual") FilterChip(selected = rule.enabled, onClick = { save(rules.map { if (it.id == rule.id) it.copy(enabled = !it.enabled) else it }) }, label = { Text(if (rule.enabled) "Etkin" else "Duraklatıldı") })
                        } else TextButton(onClick = { editing = MacroRule(name = rule.name.take(80) + " · uyarlama", actions = listOf(MacroAction("notify", "Makro çalıştı")), source = "${rule.source} · ayrı yerel uyarlama") }) { Text("Yerel uyarlama oluştur") }
                        TextButton(onClick = { save(rules.filterNot { it.id == rule.id }) }) { Text("Sil") }
                    }
                }
            }
        }
        Text("MacroDroid koleksiyonu", style = MaterialTheme.typography.titleMedium)
        Text("Özgün makrolar inceleme için alınır. Desteklenmeyen tetikleyiciler ve koşullar sessizce atlanmaz; otomatik çalışmaz.", style = MaterialTheme.typography.bodySmall)
        val assets = remember { context.assets.list("macros").orEmpty().filter { it.endsWith(".macro") } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            assets.forEach { file -> AssistChip(onClick = {
                runCatching { context.assets.open("macros/$file").bufferedReader().use { MacroRules.import(it.readText()) } }
                    .onSuccess { review = it }.onFailure { message = "Makro okunamadı" }
            }, label = { Text(file.removeSuffix(".macro").replace('_', ' ')) }) }
        }
        Text("OpenTasker", style = MaterialTheme.typography.titleMedium)
        Text("MIT lisanslı atomik bekleme ve yeniden tetikleme denetimi bu yürütücüde kullanılır. OpenTasker JSON paketleri inceleme kaydı olarak içe alınır; tüm OpenTasker motoru taşınmış değildir.", style = MaterialTheme.typography.bodySmall)
        Text("Son işlemler", style = MaterialTheme.typography.titleMedium)
        Text(history.ifBlank { "Henüz işlem yok" }, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { enabled = store.enabled; history = store.history() }) { Text("Durumu yenile") }
        Spacer(Modifier.height(32.dp))
    }
    editing?.let { rule -> MacroEditor(rule, onDismiss = { editing = null }, onSave = { edited ->
        val next = if (rules.any { it.id == edited.id }) rules.map { if (it.id == edited.id) edited else it } else rules + edited
        runCatching { store.save(next) }.onSuccess { rules = next; editing = null }.onFailure { message = it.message }
    }) }
    if (review.isNotEmpty()) AlertDialog(onDismissRequest = { review = emptyList() }, title = { Text("İçe aktarmayı incele") },
        text = { Text("${review.size} kayıt duraklatılmış eklenecek.\n\n" + review.take(5).joinToString("\n") { "${it.name}: ${if (MacroRules.validate(it).isEmpty()) "yerel yürütücüyle uyumlu" else "inceleme / uyarlama gerekli"}" }) },
        confirmButton = { TextButton(onClick = { val next = rules + review; runCatching { store.save(next) }.onSuccess { rules = next; review = emptyList() }.onFailure { message = it.message; review = emptyList() } }) { Text("Duraklatılmış ekle") } },
        dismissButton = { TextButton(onClick = { review = emptyList() }) { Text("Vazgeç") } })
    pending?.let { rule -> AlertDialog(onDismissRequest = { pending = null }, title = { Text(rule.name) },
        text = { Text(rule.actions.joinToString("\n") { "${MacroRules.actions[it.kind]}: ${it.value}" }) },
        confirmButton = { TextButton(onClick = { pending = null; running = true; scope.launch {
            try {
                message = withContext(Dispatchers.IO) { MacroRunner(context).execute(rule.id, manual = true) }
                executionResult = message
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { executionResult = "İşlem durdu: ${e.message ?: e.javaClass.simpleName}" }
            finally { running = false; history = store.history() }
        } }) { Text("Çalıştır") } }, dismissButton = { TextButton(onClick = { pending = null }) { Text("Vazgeç") } }) }
    executionResult?.let { result -> AlertDialog(onDismissRequest = { executionResult = null },
        title = { Text("Makro sonucu") }, text = { Text(result) },
        confirmButton = { TextButton(onClick = { executionResult = null }) { Text("Tamam") } }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MacroEditor(initial: MacroRule, onDismiss: () -> Unit, onSave: (MacroRule) -> Unit) {
    var rule by remember(initial.id) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Makroyu düzenle") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = rule.name, onValueChange = { rule = rule.copy(name = it) }, label = { Text("Ad") })
            Text("Tetikleyici")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { MacroRules.triggers.forEach { (key,label) -> FilterChip(selected = rule.trigger == key, onClick = { rule = rule.copy(trigger = key, enabled = false) }, label = { Text(label) }) } }
            rule.actions.forEachIndexed { index, action ->
                Text("${index + 1}. eylem")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { MacroRules.actions.forEach { (kind,label) -> FilterChip(selected = action.kind == kind, onClick = {
                    val value = when (kind) { "volume" -> "35"; "torch" -> "on"; "notify" -> "Makro çalıştı"; else -> "" }
                    rule = rule.copy(actions = rule.actions.mapIndexed { i,a -> if (i == index) MacroAction(kind,value) else a })
                }, label = { Text(label) }) } }
                if (action.kind != "vibrate") OutlinedTextField(value = action.value, onValueChange = { value -> rule = rule.copy(actions = rule.actions.mapIndexed { i,a -> if (i == index) a.copy(value = value) else a }) }, label = { Text(if (action.kind == "torch") "on / off" else "Değer") })
                if (rule.actions.size > 1) TextButton(onClick = { rule = rule.copy(actions = rule.actions.filterIndexed { i,_ -> i != index }) }) { Text("Eylemi kaldır") }
            }
            if (rule.actions.size < 20) TextButton(onClick = { rule = rule.copy(actions = rule.actions + MacroAction("vibrate")) }) { Text("Eylem ekle") }
            OutlinedTextField(value = rule.cooldown.toString(), onValueChange = { rule = rule.copy(cooldown = it.toIntOrNull() ?: 0) }, label = { Text("İki çalıştırma arası saniye") })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        val problems = MacroRules.validate(rule)
        if (problems.isEmpty()) onSave(rule.copy(enabled = false)) else error = problems.joinToString("\n")
    }) { Text("Duraklatılmış kaydet") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } })
}
