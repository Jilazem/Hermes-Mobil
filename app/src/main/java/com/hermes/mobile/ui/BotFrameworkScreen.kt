package com.hermes.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hermes.mobile.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** UI-SPEC.md: custom bots are separate from the gateway profile roster. */
@Composable
fun BotFrameworkScreen(onOpenSettings: () -> Unit = {}, onOpenMola: () -> Unit = {}, profile: ServerProfile? = null, hermesProfiles: List<HermesProfile> = emptyList(), onRefreshHermes: () -> Unit = {}, onOpenHermes: (String, HermesSession?) -> Unit = { _, _ -> }) {
    com.hermes.mobile.ui.bots.BotWorkspaceScreen(onOpenSettings, onOpenMola, profile, hermesProfiles, onRefreshHermes, onOpenHermes)
}

@Composable
internal fun LegacyBotFrameworkScreen(onOpenSettings: () -> Unit = {}) {
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
    var showFleet by remember { mutableStateOf(false) }
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var showPhoneControl by rememberSaveable { mutableStateOf(false) }
    val phoneStore = remember { SettingsStore(context) }
    val phoneSettings by phoneStore.settings.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var phoneServiceRunning by remember { mutableStateOf(HermesAccessibilityService.isRunning()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val running = HermesAccessibilityService.isRunning()
                if (running != phoneServiceRunning) {
                    phoneServiceRunning = running
                }
                delay(1_000)
            }
        }
    }
    val phoneAutomationActive = phoneSettings.agentMayUsePhone && !phoneSettings.agentReadOnly &&
        !phoneSettings.agentEmergencyStop && phoneServiceRunning &&
        (phoneSettings.agentAutoWrite || phoneSettings.fullControl)
    LaunchedEffect(phoneAutomationActive) {
        if (phoneAutomationActive) showPhoneControl = true
    }
    val operationFailed = S.t2("İşlem başarısız", "Operation failed")
    val exportSaved = S.t2("Dışa aktarma dosyaya kaydedildi", "Export saved to file")
    val oversizedFile = S.t2("Dosya 1 MB sınırını aşıyor", "File exceeds the 1 MB limit")
    val importCompleted = S.t2("Bot yeni kimlikle içe aktarıldı", "Bot imported with a new ID")
    val scope = rememberCoroutineScope()
    fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: operationFailed }
            finally { busy = false }
        }
    }
    BackHandler(enabled = !busy && (editing != null || selected != null)) {
        if (editing != null) editing = null else { selected = null; session = null; history = emptyList() }
    }
    suspend fun refresh() { bots = requireNotNull(api).request("GET", "/api/bots").rows("bots") }
    suspend fun connect() {
        val client = BotApiClient(endpoint.trim(), token.trim())
        val roster = client.request("GET", "/api/bots")
        require(roster["bots"] is JsonArray) { "Bu adres Bot API sözleşmesini sunmuyor. Gateway yerine özel bot sunucusunu seçin." }
        // Optional catalogs must not hide an otherwise usable server.
        val warnings = mutableListOf<String>()
        suspend fun optionalCatalog(path: String, key: String): List<JsonObject> = try {
            client.request("GET", path).rows(key)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { warnings += e.message.orEmpty(); emptyList() }
        val loadedTemplates = optionalCatalog("/api/templates", "templates")
        val loadedTools = optionalCatalog("/api/tools", "tools")
        api = client; bots = roster.rows("bots"); templates = loadedTemplates; catalog = loadedTools
        prefs.edit().putString("endpoint", endpoint.trim()).putString("token", token.trim()).apply()
        status = warnings.joinToString("\n")
    }
    LaunchedEffect(Unit) {
        if (endpoint.isNotBlank() && token.isNotBlank()) act { connect() }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) act {
            withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(exportText.toByteArray(Charsets.UTF_8)) } }
            status = exportSaved
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
                    require(bytes.size <= 1_000_000) { oversizedFile }
                    bytes.toString(Charsets.UTF_8)
                }
            }
            requireNotNull(api).request("POST", "/api/bots/import", Json.parseToJsonElement(text))
            refresh(); status = importCompleted
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(S.t2("Botlar", "Bots"), style = MaterialTheme.typography.headlineSmall)
        Text(S.t2("Botlarını ve görevlerini tek yerden yönet.", "Manage your bots and tasks in one place."),
            style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                showPhoneControl = !showPhoneControl
            }) {
                Text(S.t2("Telefon kontrolü", "Phone control") + if (showPhoneControl) " ▴" else " ▾")
            }
            // The emergency switch remains directly reachable even when details are collapsed.
            if (!showPhoneControl) TextButton(onClick = {
                val next = !phoneStore.current().agentEmergencyStop
                phoneStore.update { it.copy(agentEmergencyStop = next) }
                PhoneAuditLog.recordDecision(context, if (next) "ACİL DURDUR açıldı" else "ACİL DURDUR kaldırıldı")
            }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(if (phoneSettings.agentEmergencyStop) S.t2("DURDUR AÇIK · kaldır", "STOP ON · clear")
                    else S.t2("ACİL DURDUR", "EMERGENCY STOP"))
            }
        }
        if (showPhoneControl) PhoneBotCard(onOpenSettings = onOpenSettings)
        if (api == null) {
            OutlinedTextField(endpoint, { endpoint = it }, label = { Text(S.t2("Bot API adresi", "Bot API address")) }, enabled = !busy)
            OutlinedTextField(token, { token = it }, label = { Text(S.t2("Erişim tokenı", "Access token")) }, visualTransformation = PasswordVisualTransformation(), enabled = !busy)
            Button(enabled = !busy && endpoint.isNotBlank() && token.isNotBlank(), onClick = { act { connect() } }) { Text(S.t2("Bağlan", "Connect")) }
        } else {
            TextButton(enabled = !busy, onClick = { api = null; editing = null; selected = null; session = null; history = emptyList() }) { Text(S.t2("Sunucuyu değiştir", "Change server")) }
            when {
                editing != null -> BotDefinitionEditor(requireNotNull(editing), catalog, busy, error,
                    onCancel = { editing = null }, onSave = { definition -> act {
                        val id = editing?.text("id").orEmpty()
                        requireNotNull(api).request(if (id.isEmpty()) "POST" else "PATCH", "/api/bots" + if (id.isEmpty()) "" else "/${botApiSegment(id)}", definition)
                        editing = null; selected = null; session = null; refresh()
                    } })
                selected != null -> {
                    Text("${selected!!.text("emoji")} ${selected!!.text("name")}")
                    Row {
                        TextButton(enabled = !busy, onClick = { selected = null; session = null }) { Text(S.t2("Liste", "List")) }
                        TextButton(enabled = !busy, onClick = { session = null }) { Text(S.t2("Yeni sohbet", "New chat")) }
                        TextButton(onClick = { personaInfo = true }) { Text("Persona") }
                    }
                    history.forEach { old -> TextButton(enabled = !busy, onClick = { act {
                        session = botChatSession(requireNotNull(api).request("GET", "/api/sessions/${botApiSegment(botSessionId(old))}"))
                    } }) { Text(S.t2("Oturum · ${old.text("created_at")}", "Session · ${old.text("created_at")}")) } }
                    session?.let { botVisibleMessages(it).forEach { msg ->
                        Text(msg.text("role"), style = MaterialTheme.typography.labelMedium)
                        MarkdownText(msg.text("content").ifBlank { msg.text("text") })
                    } }
                    var message by remember(selected) { mutableStateOf("") }
                    OutlinedTextField(message, { message = it }, label = { Text(S.t2("Mesaj", "Message")) }, enabled = !busy)
                    Button(enabled = !busy && message.isNotBlank(), onClick = { act {
                        val result = requireNotNull(api).request("POST", "/api/bots/${botApiSegment(selected!!.text("id"))}/chat", buildJsonObject {
                            put("message", message)
                            session?.let { put("session_id", it.text("session_id").ifBlank { it.text("id") }) }
                        })
                        val confirmed = botChatSession(result)
                        require(botSessionId(confirmed).isNotBlank() && confirmed["messages"] is JsonArray) { "Sunucu sohbet sonucunu doğrulamadı" }
                        session = confirmed; message = ""
                        history = requireNotNull(api).request("GET", "/api/sessions?bot_id=${botApiSegment(selected!!.text("id"))}").rows("sessions")
                    } }) { Text(S.t2("Gönder", "Send")) }
                }
                else -> {
                    Row {
                        FilterChip(selected = !showFleet, onClick = { showFleet = false }, label = { Text(S.t2("Botlarım", "My bots")) })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = showFleet, onClick = { showFleet = true }, label = { Text(S.t2("Filo ve kuyruk", "Fleet & queue")) })
                    }
                    if (showFleet) key(api?.storageKey) {
                        BotFleetPanel(bots = bots, api = api, onOpenApprovals = onOpenSettings)
                    } else {
                    Row {
                        Button(enabled = !busy, onClick = { editing = JsonObject(emptyMap()) }) { Text(S.t2("＋ Yeni bot", "＋ New bot")) }
                        TextButton(enabled = !busy, onClick = { importFile.launch(arrayOf("application/json", "text/plain")) }) { Text(S.t2("İçe aktar", "Import")) }
                        TextButton(enabled = !busy, onClick = { act { refresh() } }) { Text(S.t2("Yenile", "Refresh")) }
                    }
                    if (bots.isEmpty()) {
                        Text(S.t2("Henüz botun yok.", "You have no bots yet."))
                        OutlinedButton(enabled = !busy, onClick = { showTemplates = true }) {
                            Text(S.t2("Şablondan başla", "Start from a template"))
                        }
                    }
                    bots.forEach { bot -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text("${bot.text("emoji")} ${bot.text("name")}")
                        Text(bot.text("persona").take(80))
                        Text((bot["tools"] as? JsonArray)?.joinToString { it.jsonPrimitive.content }.orEmpty())
                        Row {
                            TextButton(enabled = !busy, onClick = { act {
                                history = requireNotNull(api).request("GET", "/api/sessions?bot_id=${botApiSegment(bot.text("id"))}").rows("sessions")
                                selected = bot; session = null
                            } }) { Text(S.t2("Sohbet", "Chat")) }
                            TextButton(enabled = !busy, onClick = { editing = bot }) { Text(S.t2("Düzenle", "Edit")) }
                            TextButton(enabled = !busy, onClick = { deleting = bot }) { Text(S.t2("Sil", "Delete")) }
                        }
                        TextButton(enabled = !busy, onClick = { act {
                            exportText = requireNotNull(api).request("GET", "/api/bots/${botApiSegment(bot.text("id"))}/export").toString()
                            saveFile.launch("bot.hermes-bot.json")
                        } }) { Text(S.t2("Dışa aktar", "Export")) }
                    } } }
                    TextButton(enabled = !busy, onClick = { showTemplates = !showTemplates }) {
                        Text(S.t2("Hazır bot şablonları", "Bot templates") + if (showTemplates) " ▴" else " ▾")
                    }
                    if (showTemplates) {
                        if (templates.isEmpty()) {
                            Text(S.t2("Şablon listesi alınamadı.", "The template list is unavailable."))
                            TextButton(enabled = !busy, onClick = { act {
                                templates = requireNotNull(api).request("GET", "/api/templates").rows("templates")
                            } }) { Text(S.t2("Yeniden dene", "Try again")) }
                        }
                        templates.forEach { template -> TextButton(enabled = !busy, onClick = { act {
                            requireNotNull(api).request("POST", "/api/bots/from-template", buildJsonObject { put("key", template.text("key")) })
                            refresh(); showTemplates = false
                        } }) { Text(template.text("name")) } }
                    }
                    }
                }
            }
        }
        if (busy) CircularProgressIndicator()
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        if (status.isNotEmpty()) Text(status)
    }
    deleting?.let { bot -> AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text(S.t2("Bot ve oturumları silinsin mi?", "Delete the bot and its sessions?")) },
        confirmButton = { TextButton(enabled = !busy, onClick = { act {
            requireNotNull(api).request("DELETE", "/api/bots/${botApiSegment(bot.text("id"))}"); deleting = null; refresh()
        } }) { Text(S.t2("Sil", "Delete")) } }, dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text(S.t2("Vazgeç", "Cancel")) } }) }
    if (personaInfo) AlertDialog(onDismissRequest = { personaInfo = false }, title = { Text(S.t2("Persona · sunucunun enjekte ettiği talimat", "Persona · instruction injected by the server")) },
        text = { Text(session?.text("system_prompt") ?: S.t2("İlk mesajdan sonra gösterilir", "Shown after the first message")) }, confirmButton = { TextButton(onClick = { personaInfo = false }) { Text(S.t2("Kapat", "Close")) } })
}

@Composable
internal fun BotDefinitionEditor(bot: JsonObject, catalog: List<JsonObject>, busy: Boolean, serverError: String, onCancel: () -> Unit, onSave: (JsonObject) -> Unit, availableProviders: List<JsonObject> = emptyList()) {
    var name by remember(bot) { mutableStateOf(bot.text("name")) }
    var emoji by remember(bot) { mutableStateOf(bot.text("emoji").ifBlank { "🤖" }) }
    var persona by remember(bot) { mutableStateOf(bot.text("persona")) }
    var model by remember(bot) { mutableStateOf(bot.text("model")) }
    var provider by remember(bot) { mutableStateOf(bot.text("provider")) }
    var showModelList by remember(bot) { mutableStateOf(false) }
    var modelSearch by remember(bot) { mutableStateOf("") }
    var think by remember(bot) { mutableStateOf(bot.text("think_mode").ifBlank { "off" }) }
    var welcome by remember(bot) { mutableStateOf(bot.text("welcome_message")) }
    var tools by remember(bot) { mutableStateOf((bot["tools"] as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet().orEmpty()) }
    var validation by remember { mutableStateOf("") }
    OutlinedTextField(name, { name = it }, label = { Text(S.t2("Ad · 1–48", "Name · 1–48")) }, enabled = !busy)
    OutlinedTextField(emoji, { emoji = it }, label = { Text(S.t2("Emoji · en çok 8", "Emoji · up to 8")) }, enabled = !busy)
    OutlinedTextField(persona, { persona = it }, label = { Text(S.t2("Persona · ${persona.codePointCount(0, persona.length)}/8000", "Persona · ${persona.codePointCount(0, persona.length)}/8000")) }, enabled = !busy)
    if (serverError.isNotBlank() || validation.isNotBlank()) Text(validation.ifBlank { serverError }, color = MaterialTheme.colorScheme.error)
    Text(S.t2("Yetenekler · ${tools.size}/12", "Skills · ${tools.size}/12"))
    catalog.forEach { tool -> Row {
        Checkbox(checked = tool.text("key") in tools, enabled = !busy, onCheckedChange = { checked ->
            tools = if (checked) tools + tool.text("key") else tools - tool.text("key")
        }); Text(tool.text("label"))
    } }
    Text(S.t2("Botun yapay zekâ modeli · yalnız yeni sohbetlerde geçerli", "Bot model · applies to new conversations only"))
    if (availableProviders.isNotEmpty()) {
        TextButton(enabled = !busy, onClick = { showModelList = !showModelList }) {
            Text(S.t2("Sunucudaki modellerden seç", "Choose from server models") + if (showModelList) " ▴" else " ▾")
        }
        if (showModelList) {
            OutlinedTextField(modelSearch, { modelSearch = it }, singleLine = true,
                label = { Text(S.t2("Model veya sağlayıcı ara", "Search models or providers")) }, enabled = !busy)
            val choices = availableProviders.flatMap { row ->
                val slug = row.text("slug")
                (row["models"] as? JsonArray)?.mapNotNull { item ->
                    (item as? JsonPrimitive)?.contentOrNull?.let { slug to it }
                }.orEmpty()
            }.filter { (slug, name) ->
                modelSearch.isBlank() || slug.contains(modelSearch, ignoreCase = true) ||
                    name.contains(modelSearch, ignoreCase = true)
            }.take(30)
            Column(Modifier.heightIn(max = 228.dp).verticalScroll(rememberScrollState())) {
                choices.forEach { (slug, name) ->
                    TextButton(enabled = !busy, onClick = { provider = slug; model = name; showModelList = false }) {
                        Text("$slug / $name")
                    }
                }
                if (choices.isEmpty()) Text(S.t2("Eşleşen model bulunamadı", "No matching model"))
            }
        }
    }
    OutlinedTextField(provider, { provider = it }, singleLine = true,
        label = { Text(S.t2("Sağlayıcı · boş = profil sağlayıcısı", "Provider · blank = profile provider")) }, enabled = !busy)
    OutlinedTextField(model, { model = it }, singleLine = true,
        label = { Text(S.t2("Model · boş = oturum varsayılanı", "Model · blank = session default")) }, enabled = !busy)
    Row { listOf("off", "low", "medium", "high").forEach { mode -> FilterChip(selected = think == mode, enabled = !busy, onClick = { think = mode }, label = { Text(mode) }) } }
    OutlinedTextField(welcome, { welcome = it }, label = { Text(S.t2("Karşılama · en çok 500", "Welcome · up to 500")) }, enabled = !busy)
    Row {
        Button(enabled = !busy, onClick = {
            try { onSave(botDefinition(name, emoji, persona, model, think, welcome, tools, provider.trim())) }
            catch (e: IllegalArgumentException) { validation = e.message.orEmpty() }
        }) { Text(S.t2("Kaydet", "Save")) }
        TextButton(enabled = !busy, onClick = onCancel) { Text(S.t2("Vazgeç", "Cancel")) }
    }
}
