package com.hermes.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hermes.mobile.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** UI-SPEC.md: custom bots are separate from the gateway profile roster. */
@Composable
fun BotFrameworkScreen() {
    val context = LocalContext.current
    val prefs = remember {
        EncryptedSharedPreferences.create(context, "bot-framework", MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "").orEmpty()) }
    var token by remember { mutableStateOf(prefs.getString("token", "").orEmpty()) }
    var api by remember { mutableStateOf<BotApiClient?>(null) }
    var bots by remember { mutableStateOf(emptyList<JsonObject>()) }
    var templates by remember { mutableStateOf(emptyList<JsonObject>()) }
    var catalog by remember { mutableStateOf(emptyList<JsonObject>()) }
    var editing by remember { mutableStateOf<JsonObject?>(null) }
    var selected by remember { mutableStateOf<JsonObject?>(null) }
    var session by remember { mutableStateOf<JsonObject?>(null) }
    var history by remember { mutableStateOf(emptyList<JsonObject>()) }
    var deleting by remember { mutableStateOf<JsonObject?>(null) }
    var personaInfo by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var exportText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "İşlem başarısız" }
            finally { busy = false }
        }
    }
    BackHandler(enabled = !busy && (editing != null || selected != null)) {
        if (editing != null) editing = null else { selected = null; session = null; history = emptyList() }
    }
    suspend fun refresh() { bots = requireNotNull(api).request("GET", "/api/bots").rows("bots") }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) act {
            withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(exportText.toByteArray(Charsets.UTF_8)) } }
            status = "Dışa aktarma dosyaya kaydedildi"
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) act {
            val text = withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openInputStream(uri)).use {
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= 1_000_000) {
                        val count = it.read(buffer, 0, minOf(buffer.size, 1_000_001 - output.size()))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    val bytes = output.toByteArray()
                    require(bytes.size <= 1_000_000) { "Dosya 1 MB sınırını aşıyor" }
                    bytes.toString(Charsets.UTF_8)
                }
            }
            requireNotNull(api).request("POST", "/api/bots/import", Json.parseToJsonElement(text))
            refresh(); status = "Bot yeni kimlikle içe aktarıldı"
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Botlar", style = MaterialTheme.typography.headlineSmall)
        Text("Özel bot sunucusu · gateway/telefon relay adresi değildir. Yanıtlayıcı sunucuda belirlenir; çevrimdışı demo gerçek model değildir.")
        if (api == null) {
            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Bot API adresi") }, enabled = !busy)
            OutlinedTextField(token, { token = it }, label = { Text("Erişim tokenı") }, visualTransformation = PasswordVisualTransformation(), enabled = !busy)
            Button(enabled = !busy, onClick = { act {
                val client = BotApiClient(endpoint, token)
                val loadedBots = client.request("GET", "/api/bots").rows("bots")
                val loadedTemplates = client.request("GET", "/api/templates").rows("templates")
                val loadedTools = client.request("GET", "/api/tools").rows("tools")
                api = client; bots = loadedBots; templates = loadedTemplates; catalog = loadedTools
                prefs.edit().putString("endpoint", endpoint).putString("token", token).apply()
            } }) { Text("Bağlan") }
        } else {
            TextButton(enabled = !busy, onClick = { api = null; editing = null; selected = null; session = null; history = emptyList() }) { Text("Sunucuyu değiştir") }
            when {
                editing != null -> BotDefinitionEditor(requireNotNull(editing), catalog, busy, error,
                    onCancel = { editing = null }, onSave = { definition -> act {
                        val id = editing?.text("id").orEmpty()
                        requireNotNull(api).request(if (id.isEmpty()) "POST" else "PATCH", "/api/bots" + if (id.isEmpty()) "" else "/$id", definition)
                        editing = null; selected = null; session = null; refresh()
                    } })
                selected != null -> {
                    Text("${selected!!.text("emoji")} ${selected!!.text("name")}")
                    Row {
                        TextButton(enabled = !busy, onClick = { selected = null; session = null }) { Text("Liste") }
                        TextButton(enabled = !busy, onClick = { session = null }) { Text("Yeni sohbet") }
                        TextButton(onClick = { personaInfo = true }) { Text("Persona") }
                    }
                    history.forEach { old -> TextButton(enabled = !busy, onClick = { act {
                        session = requireNotNull(api).request("GET", "/api/sessions/${old.text("id")}")["session"]!!.jsonObject
                    } }) { Text("Oturum · ${old.text("created_at")}") } }
                    session?.let { botVisibleMessages(it).forEach { msg -> Text("${msg.text("role")}: ${msg.text("content")}") } }
                    var message by remember(selected) { mutableStateOf("") }
                    OutlinedTextField(message, { message = it }, label = { Text("Mesaj") }, enabled = !busy)
                    Button(enabled = !busy && message.isNotBlank(), onClick = { act {
                        val result = requireNotNull(api).request("POST", "/api/bots/${selected!!.text("id")}/chat", buildJsonObject {
                            put("message", message)
                            session?.let { put("session_id", it.text("session_id").ifBlank { it.text("id") }) }
                        })
                        session = result; message = ""
                        history = requireNotNull(api).request("GET", "/api/sessions?bot_id=${selected!!.text("id")}").rows("sessions")
                    } }) { Text("Gönder") }
                }
                else -> {
                    Row {
                        Button(enabled = !busy, onClick = { editing = JsonObject(emptyMap()) }) { Text("＋ Yeni bot") }
                        TextButton(enabled = !busy, onClick = { importFile.launch(arrayOf("application/json", "text/plain")) }) { Text("İçe aktar") }
                        TextButton(enabled = !busy, onClick = { act { refresh() } }) { Text("Yenile") }
                    }
                    if (bots.isEmpty()) Text("Henüz bot yok — şablondan başla")
                    templates.forEach { template -> TextButton(enabled = !busy, onClick = { act {
                        requireNotNull(api).request("POST", "/api/bots/from-template", buildJsonObject { put("key", template.text("key")) })
                        refresh()
                    } }) { Text("Şablon: ${template.text("name")}") } }
                    bots.forEach { bot -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text("${bot.text("emoji")} ${bot.text("name")}")
                        Text(bot.text("persona").take(80))
                        Text((bot["tools"] as? JsonArray)?.joinToString { it.jsonPrimitive.content }.orEmpty())
                        Row {
                            TextButton(enabled = !busy, onClick = { act {
                                history = requireNotNull(api).request("GET", "/api/sessions?bot_id=${bot.text("id")}").rows("sessions")
                                selected = bot; session = null
                            } }) { Text("Sohbet") }
                            TextButton(enabled = !busy, onClick = { editing = bot }) { Text("Düzenle") }
                            TextButton(enabled = !busy, onClick = { deleting = bot }) { Text("Sil") }
                        }
                        TextButton(enabled = !busy, onClick = { act {
                            exportText = requireNotNull(api).request("GET", "/api/bots/${bot.text("id")}/export").toString()
                            saveFile.launch("bot.hermes-bot.json")
                        } }) { Text("Dışa aktar") }
                    } } }
                }
            }
        }
        if (busy) CircularProgressIndicator()
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        if (status.isNotEmpty()) Text(status)
    }
    deleting?.let { bot -> AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("Bot ve oturumları silinsin mi?") },
        confirmButton = { TextButton(enabled = !busy, onClick = { act {
            requireNotNull(api).request("DELETE", "/api/bots/${bot.text("id")}"); deleting = null; refresh()
        } }) { Text("Sil") } }, dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("Vazgeç") } }) }
    if (personaInfo) AlertDialog(onDismissRequest = { personaInfo = false }, title = { Text("Persona · sunucunun enjekte ettiği talimat") },
        text = { Text(session?.text("system_prompt") ?: "İlk mesajdan sonra gösterilir") }, confirmButton = { TextButton(onClick = { personaInfo = false }) { Text("Kapat") } })
}

@Composable
private fun BotDefinitionEditor(bot: JsonObject, catalog: List<JsonObject>, busy: Boolean, serverError: String, onCancel: () -> Unit, onSave: (JsonObject) -> Unit) {
    var name by remember(bot) { mutableStateOf(bot.text("name")) }
    var emoji by remember(bot) { mutableStateOf(bot.text("emoji").ifBlank { "🤖" }) }
    var persona by remember(bot) { mutableStateOf(bot.text("persona")) }
    var model by remember(bot) { mutableStateOf(bot.text("model")) }
    var think by remember(bot) { mutableStateOf(bot.text("think_mode").ifBlank { "off" }) }
    var welcome by remember(bot) { mutableStateOf(bot.text("welcome_message")) }
    var tools by remember(bot) { mutableStateOf((bot["tools"] as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet().orEmpty()) }
    var validation by remember { mutableStateOf("") }
    OutlinedTextField(name, { name = it }, label = { Text("Ad · 1–48") }, enabled = !busy)
    OutlinedTextField(emoji, { emoji = it }, label = { Text("Emoji · en çok 8") }, enabled = !busy)
    OutlinedTextField(persona, { persona = it }, label = { Text("Persona · ${persona.codePointCount(0, persona.length)}/8000") }, enabled = !busy)
    if (serverError.isNotBlank() || validation.isNotBlank()) Text(validation.ifBlank { serverError }, color = MaterialTheme.colorScheme.error)
    Text("Yetenekler · ${tools.size}/12")
    catalog.forEach { tool -> Row {
        Checkbox(checked = tool.text("key") in tools, enabled = !busy, onCheckedChange = { checked ->
            tools = if (checked) tools + tool.text("key") else tools - tool.text("key")
        }); Text(tool.text("label"))
    } }
    OutlinedTextField(model, { model = it }, label = { Text("Model · boş = oturum modeli · en çok 120") }, enabled = !busy)
    Row { listOf("off", "low", "medium", "high").forEach { mode -> FilterChip(selected = think == mode, enabled = !busy, onClick = { think = mode }, label = { Text(mode) }) } }
    OutlinedTextField(welcome, { welcome = it }, label = { Text("Karşılama · en çok 500") }, enabled = !busy)
    Row {
        Button(enabled = !busy, onClick = {
            try { onSave(botDefinition(name, emoji, persona, model, think, welcome, tools)) }
            catch (e: IllegalArgumentException) { validation = e.message.orEmpty() }
        }) { Text("Kaydet") }
        TextButton(enabled = !busy, onClick = onCancel) { Text("Vazgeç") }
    }
}
