package com.hermes.mobile.ui.bots

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hermes.mobile.data.*
import com.hermes.mobile.data.bots.*
import com.hermes.mobile.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BotWorkspaceScreen(onOpenSettings: () -> Unit, onOpenMola: () -> Unit, profile: ServerProfile? = null, hermesProfiles: List<HermesProfile> = emptyList(), onRefreshHermes: () -> Unit = {}, onOpenHermes: (String, HermesSession?) -> Unit = { _, _ -> }) {
    key(profile?.id, profile?.token, profile?.candidates) { BotWorkspaceContent(profile, onOpenSettings, onOpenMola, hermesProfiles, onRefreshHermes, onOpenHermes) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BotWorkspaceContent(profile: ServerProfile?, onOpenSettings: () -> Unit, onOpenMola: () -> Unit, hermesProfiles: List<HermesProfile>, onRefreshHermes: () -> Unit, onOpenHermes: (String, HermesSession?) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { EncryptedSharedPreferences.create(context, "bot-framework",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM) }
    val phoneStore = remember { SettingsStore(context) }
    val phone by phoneStore.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val endpoint = profile?.let { it.activeUrl ?: it.normalizedUrl }.orEmpty()
    val token = profile?.token.orEmpty()
    var api by remember { mutableStateOf<BotApiClient?>(null) }
    var modelProviders by remember { mutableStateOf(emptyList<JsonObject>()) }
    var bots by remember { mutableStateOf(emptyList<JsonObject>()) }
    var templates by remember { mutableStateOf(emptyList<JsonObject>()) }
    var catalog by remember { mutableStateOf(emptyList<JsonObject>()) }
    var tab by rememberSaveable { mutableIntStateOf(3) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf(emptyMap<String, String>()) }
    var editing by remember { mutableStateOf<JsonObject?>(null) }
    var detail by remember { mutableStateOf<JsonObject?>(null) }
    var selected by remember { mutableStateOf<JsonObject?>(null) }
    var session by remember { mutableStateOf<JsonObject?>(null) }
    var history by remember { mutableStateOf(emptyList<JsonObject>()) }
    var deleting by remember { mutableStateOf<JsonObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var connectSheet by remember { mutableStateOf(false) }
    var templateSheet by remember { mutableStateOf(false) }
    var showPhone by rememberSaveable { mutableStateOf(false) }
    var taskTarget by remember { mutableStateOf("") }
    var taskDraft by remember { mutableStateOf("") }
    var taskGroup by remember { mutableStateOf("") }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }
    val cardBounds = remember { mutableStateMapOf<String,Rect>() }
    var exportText by remember { mutableStateOf("") }
    var inspect by remember { mutableStateOf<ChatTextEntry?>(null) }
    val fleetPrefs = remember(api?.storageKey) { api?.let { client ->
        EncryptedSharedPreferences.create(context, "bot-fleet-${client.storageKey}",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    } }
    fun readGroups() { groups = runCatching { Json.parseToJsonElement(fleetPrefs?.getString("groups", "{}").orEmpty()).jsonObject
        .mapValues { (_, v) -> v.jsonPrimitive.content } }.getOrDefault(emptyMap()) }
    LaunchedEffect(fleetPrefs, tab) { readGroups() }
    fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "İşlem tamamlanamadı." }
            finally { busy = false }
        }
    }
    suspend fun refresh() { bots = requireNotNull(api).request("GET", "/api/bots").rows("bots") }
    suspend fun connect() {
        val candidate = BotApiClient.fromDashboard(requireNotNull(profile) { "Önce Hermes Dashboard bağlantısını seçin." })
        val result = candidate.request("GET", "/api/bots")
        require(result["bots"] is JsonArray) { "Bu adres ajan listesini sunmuyor; Bot API adresini kontrol edin." }
        suspend fun optional(path: String, key: String): List<JsonObject> = try { candidate.request("GET", path).rows(key) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        val nextTemplates = optional("/api/templates", "templates")
        val nextCatalog = optional("/api/tools", "tools")
        val nextModels = try { candidate.availableModelProviders() }
            catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        api = candidate; bots = result.rows("bots"); templates = nextTemplates; catalog = nextCatalog; modelProviders = nextModels
        connectSheet = false
    }
    suspend fun openChat(bot: JsonObject) {
        val client = requireNotNull(api)
        val rows = client.request("GET", "/api/sessions?bot_id=${botApiSegment(bot.text("id"))}").rows("sessions")
        val latest = BotWorkspaceLogic.latestSessionId(rows.map { botSessionId(it) to
            ((it["updated_at"] as? JsonPrimitive)?.doubleOrNull ?: (it["created_at"] as? JsonPrimitive)?.doubleOrNull ?: 0.0) })
        val loaded = latest?.let { checkedBotConversation(client.request("GET", "/api/sessions/${botApiSegment(it)}"), bot.text("id"), it) }
        history = rows; selected = bot; session = loaded; editing = null
    }
    LaunchedEffect(tab) { if (tab != 3 && api == null && endpoint.isNotBlank() && token.isNotBlank()) act { connect() } }
    BackHandler(enabled = !busy && (editing != null || selected != null || detail != null)) {
        when { detail != null -> detail = null; editing != null -> editing = null; else -> { selected = null; session = null; history = emptyList() } }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) act { withContext(Dispatchers.IO) {
            requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(exportText.toByteArray(Charsets.UTF_8)) }
        }; notice = "Ajan dosyası kaydedildi." }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) act {
            val content = withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    val bytes = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (bytes.size() <= 1_000_000) { val n = input.read(buffer, 0, minOf(buffer.size, 1_000_001 - bytes.size())); if (n < 0) break; bytes.write(buffer, 0, n) }
                    require(bytes.size() <= 1_000_000) { "Dosya 1 MB sınırını aşıyor." }; bytes.toString("UTF-8")
                }
            }
            requireNotNull(api).request("POST", "/api/bots/import", Json.parseToJsonElement(content)); refresh(); notice = "Ajan yeni kimlikle içe aktarıldı."
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (editing != null || selected != null) IconButton(enabled = !busy, onClick = { editing = null; selected = null; session = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.t2("Atölyeye dön", "Back to workspace"))
            }
            Column(Modifier.weight(1f)) {
                Text(S.t2("Bot arkadaşlarım", "My companions"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (api == null) S.t2("Ekibini ve cihazlarını bir araya getir", "Bring your agents and devices together") else
                    S.t2("${bots.size} arkadaş", "${bots.size} companions"), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { connectSheet = true }) { Icon(Icons.Default.Dns, S.t2("Ajan sunucusu", "Agent server")) }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Tune, S.t2("Ayarlar", "Settings")) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) WorkspaceNotice(error, error = true, onClose = { error = "" })
        if (notice.isNotBlank()) WorkspaceNotice(notice, onClose = { notice = "" })
        if (editing == null && selected == null) PrimaryTabRow(selectedTabIndex = tab) {
            listOf(S.t2("Ajanlar", "Agents"), S.t2("Görevler", "Tasks"), S.t2("Cihazlar", "Devices"), "Hermes").forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title,style=MaterialTheme.typography.labelLarge) })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                tab == 3 -> HermesProfileBots(profile, hermesProfiles, onRefreshHermes, onOpenHermes)
                editing != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (editing!!.text("id").isBlank()) S.t2("Yeni ajan", "New agent") else S.t2("Ajanı düzenle", "Edit agent"), style = MaterialTheme.typography.titleLarge)
                    BotDefinitionEditor(requireNotNull(editing), catalog, busy, error, onCancel = { editing = null }, availableProviders = modelProviders, onSave = { definition -> act {
                        val id = editing?.text("id").orEmpty()
                        requireNotNull(api).request(if (id.isEmpty()) "POST" else "PATCH", "/api/bots" + if (id.isEmpty()) "" else "/${botApiSegment(id)}", definition)
                        editing = null; refresh()
                    } })
                }
                selected != null -> key(api?.storageKey, selected!!.text("id")) {
                    BotConversationPane(requireNotNull(selected), session, history, busy, api, prefs,
                        onSession = { session = it }, onHistory = { history = it }, onAct = ::act,
                        onInspect = { inspect = it }, onTask = { draft -> taskTarget = selected!!.text("id"); taskDraft = draft; selected = null; session = null; tab = 1 })
                }
                tab == 2 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    IntegratedPhoneWorkspace(profile, onOpenSettings, onOpenMola) { draft ->
                        act {
                            var target = bots.firstOrNull { it.text("name") == "Telefon Ajanı" }
                            if (target == null) {
                                requireNotNull(api).request("POST", "/api/bots/from-template", buildJsonObject { put("key", "telefon") })
                                refresh()
                                target = bots.firstOrNull { it.text("name") == "Telefon Ajanı" }
                            }
                            taskTarget = requireNotNull(target) { "Telefon ajanı kaydı doğrulanamadı." }.text("id")
                            taskDraft = draft; tab = 1
                        }
                    }
                }
                api == null -> Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Hub, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(18.dp)); Text(S.t2("Ekibini bağla", "Connect your team"), style = MaterialTheme.typography.titleLarge)
                    Text(S.t2("Ajanlarını ve geçmiş konuşmalarını kendi sunucundan getir.", "Load your agents and conversations from your own server."),
                        Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { connectSheet = true }) { Text(S.t2("Sunucuya bağlan", "Connect server")) }
                    TextButton(onClick = { tab = 2 }) { Text(S.t2("Bu telefonun ajanını aç", "Open this phone's agent")) }
                }
                tab == 1 -> key(api?.storageKey) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                        BotFleetPanel(bots, api, onOpenSettings, initialBotId = taskTarget, initialInstruction = taskDraft, initialGroup = taskGroup)
                    }
                }
                else -> {
                    val summaries = bots.map { AgentSummary(it.text("id"), it.text("name"), it.text("persona"), it.text("model"), groups[it.text("id")].orEmpty()) }
                    val filteredIds = BotWorkspaceLogic.filter(summaries, query, selectedGroup).map { it.id }.toSet()
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (dragging!=null) "Arkadaşının üstüne bırak" else "Basılı tut, üst üste koy, ekip kur",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                FilledTonalIconButton(enabled = !busy, onClick = { templateSheet=true }) { Icon(Icons.Default.Add, S.t2("Yeni arkadaş", "New companion")) }
                            }
                        }
                        if (bots.size>3 || query.isNotBlank()) item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(S.t2("Bot arkadaşını ara", "Find a companion")) }) }
                        if (groups.values.any(String::isNotBlank)) item { FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            FilterChip(selectedGroup == null, { selectedGroup = null }, label = { Text(S.t2("Tümü", "All")) })
                            groups.values.filter(String::isNotBlank).distinct().sorted().forEach { group -> FilterChip(selectedGroup == group, { selectedGroup = group }, label = { Text(group) }) }
                        } }
                        selectedGroup?.let { team -> if (bots.count { groups[it.text("id")]==team }>1) item {
                            FilledTonalButton(onClick={ taskGroup=team; taskTarget=""; taskDraft=""; tab=1 },modifier=Modifier.fillMaxWidth()) { Text("$team · Ekibe görev ver") }
                        } }
                        if (bots.isEmpty()) item { EmptyWorkspaceCard(S.t2("İlk ajanını oluştur", "Create your first agent"), S.t2("Hazır bir uzmanla başlayabilir ya da kendi talimatını yazabilirsin.", "Start from an expert template or write your own instructions.")) { templateSheet = true } }
                        else if (filteredIds.isEmpty()) item { EmptyWorkspaceCard(S.t2("Eşleşen ajan yok", "No matching agents"), S.t2("Aramayı veya grup filtresini değiştir.", "Change the search or group filter.")) { query = ""; selectedGroup = null } }
                        items(bots.filter { it.text("id") in filteredIds }, key = { it.text("id") }) { bot ->
                            val id=bot.text("id")
                            DisposableEffect(id) { onDispose { cardBounds.remove(id) } }
                            Box(Modifier.onGloballyPositioned { cardBounds[id]=it.boundsInRoot() }
                                .zIndex(if (dragging==id) 1f else 0f)
                                .graphicsLayer { if (dragging==id) { translationX=dragDelta.x; translationY=dragDelta.y; alpha=.8f; scaleX=1.03f; scaleY=1.03f } }
                                .pointerInput(id) { detectDragGesturesAfterLongPress(
                                    onDragStart={ dragging=id; dragDelta=Offset.Zero; dragPoint=cardBounds[id]?.center ?: Offset.Zero },
                                    onDragCancel={ dragging=null; dragDelta=Offset.Zero },
                                    onDragEnd={
                                        val target=cardBounds.entries.firstOrNull { it.key!=id && it.value.contains(dragPoint) }?.key
                                        if (target!=null && fleetPrefs!=null) {
                                            val next=BotTeamLogic.stack(id,target,bots.map { it.text("id") }.toSet(),groups)
                                            fleetPrefs.edit().putString("groups",JsonObject(next.mapValues { JsonPrimitive(it.value) }).toString()).apply()
                                            groups=next; selectedGroup=next[target]; notice="Ekip kuruldu. Henüz görev gönderilmedi."
                                        }
                                        dragging=null; dragDelta=Offset.Zero
                                    }, onDrag={ change,delta -> change.consume(); dragDelta+=delta; dragPoint+=delta }
                                ) }) {
                                AgentWorkspaceCard(bot, groups[id], onDetails = { detail = bot }, onChat = { act { openChat(bot) } },
                                    onTask = { taskGroup=groups[id].orEmpty(); taskTarget=id; taskDraft=""; tab=1 })
                            }
                        }
                        item { Text(S.t2("Bir fikirle başla", "Start with an idea"), style = MaterialTheme.typography.titleMedium) }
                        items(TASK_RECIPES, key = { "recipe-${it.id}" }) { recipe ->
                            OutlinedCard(onClick = { taskDraft = recipe.draft; taskTarget = bots.firstOrNull()?.text("id").orEmpty(); tab = 1 }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(recipe.title); Text(recipe.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Icon(Icons.Default.ChevronRight, null) }
                            }
                        }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { templateSheet = true }) { Text(S.t2("Bot arkadaşlarını keşfet", "Discover bot companions")) }
                            TextButton(enabled = !busy, onClick = { importFile.launch(arrayOf("application/json", "text/plain")) }) { Text(S.t2("İçe aktar", "Import")) }
                            IconButton(enabled = !busy, onClick = { act { refresh() } }) { Icon(Icons.Default.Refresh, S.t2("Yenile", "Refresh")) }
                        } }
                    }
                }
            }
        }
    }
    if (connectSheet) ModalBottomSheet(onDismissRequest = { if (!busy) connectSheet = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(S.t2("Ajan çalışma alanı", "Agent workspace"), style = MaterialTheme.typography.titleLarge)
            Text("Botlar, telefon ajanı ve Mola seçili Hermes Dashboard bağlantısını kullanır.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(profile?.name ?: "Hermes bağlantısı seçilmedi")
            Text(endpoint, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { connectSheet = false; onOpenSettings() }) { Text("Hermes bağlantısını yönet") }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(enabled = !busy && endpoint.isNotBlank() && token.isNotBlank(), onClick = { act { connect(); selected = null; editing = null; session = null } }, modifier = Modifier.fillMaxWidth()) { Text(S.t2("Bağlan ve doğrula", "Connect and verify")) }
            Spacer(Modifier.height(14.dp))
        }
    }
    if (templateSheet) ModalBottomSheet(onDismissRequest = { templateSheet = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Minik arkadaşlar", style = MaterialTheme.typography.titleMedium)
            listOf(
                Triple("Pofuduk","İşi küçük adımlara böler","Kullanıcının işini küçük, uygulanabilir adımlara ayır. Öncelik ve eksik bilgileri açıkça belirt."),
                Triple("Mırmır","Açıkları ve hataları bulur","İstenen işi dikkatle denetle. Hataları ve belirsizlikleri somut örneklerle göster; doğrulamadığını açıkça belirt."),
                Triple("Çıtır","Yeni fikirler üretir","Kısa, farklı ve uygulanabilir üç fikir üret. Kullanıcının isteğine bağlı kal; yapılmamış işi yapılmış gibi anlatma.")
            ).forEach { (name,role,persona) ->
                OutlinedCard(onClick={ act {
                    requireNotNull(api).request("POST","/api/bots/from-template",buildJsonObject {
                        put("key","asistan"); put("overrides",buildJsonObject {
                            put("name",name);put("emoji","🐾");put("persona","Sen $name adlı sıcak, kısa ve Türkçe konuşan bir yardımcı botsun. $persona")
                            put("tools",JsonArray(emptyList()));put("think_mode","low");put("welcome_message","Merhaba! Birlikte ne yapalım?")
                        })
                    });refresh();templateSheet=false
                } },enabled=!busy && api!=null,modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        CompanionFace(name,Modifier.size(42.dp))
                        Column(Modifier.padding(start=10.dp)) {Text(name,style=MaterialTheme.typography.bodyLarge);Text(role,style=MaterialTheme.typography.bodySmall)}
                    }
                }
            }
            TextButton(onClick={templateSheet=false;editing=JsonObject(emptyMap())}) {Text("Kendim oluşturayım")}
            Text("Diğer uzmanlar",style=MaterialTheme.typography.titleSmall)
            if (templates.isEmpty()) { Text(S.t2("Sunucudan şablon alınamadı; kendi ajanını oluşturabilirsin.", "Templates are unavailable; you can create your own agent."))
                Button(onClick = { templateSheet = false; editing = JsonObject(emptyMap()) }) { Text(S.t2("Kendim oluşturayım", "Create my own")) } }
            templates.forEach { template -> OutlinedCard(onClick = { act {
                requireNotNull(api).request("POST", "/api/bots/from-template", buildJsonObject { put("key", template.text("key")) }); refresh(); templateSheet = false
            } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(15.dp)) { Text(template.text("name"), style = MaterialTheme.typography.titleMedium)
                template.text("description").takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) } } } }
            Spacer(Modifier.height(14.dp))
        }
    }
    detail?.let { bot -> var groupName by remember(bot) { mutableStateOf(groups[bot.text("id")].orEmpty()) }
        ModalBottomSheet(onDismissRequest = { detail = null }) { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${bot.text("emoji")} ${bot.text("name")}", style = MaterialTheme.typography.titleLarge)
            MarkdownText(bot.text("persona"))
            Text(listOf(bot.text("provider"), bot.text("model")).filter(String::isNotBlank).joinToString(" / ")
                .ifBlank { S.t2("Oturumun modeli", "Session model") }, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (bot["tools"] as? JsonArray)?.forEach { Text(it.jsonPrimitive.content, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small).padding(7.dp)) } }
            OutlinedTextField(groupName, { groupName = it.take(40) }, Modifier.fillMaxWidth(), label = { Text(S.t2("Grup", "Group")) }, supportingText = { Text(S.t2("Bu telefondaki çalışma düzenin", "Your local workspace organization")) })
            TextButton(onClick = { val next = groups.toMutableMap(); if (groupName.isBlank()) next.remove(bot.text("id")) else next[bot.text("id")] = groupName.trim()
                fleetPrefs?.edit()?.putString("groups", JsonObject(next.mapValues { JsonPrimitive(it.value) }).toString())?.apply(); groups = next; notice = "Grup kaydedildi." }) { Text(S.t2("Grubu kaydet", "Save group")) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { detail = null; act { openChat(bot) } }) { Text(S.t2("Sohbet aç", "Open chat")) }
                OutlinedButton(onClick = { detail = null; taskTarget = bot.text("id"); taskDraft = ""; tab = 1 }) { Text(S.t2("Görev ver", "Assign task")) }
                TextButton(onClick = { detail = null; editing = bot }) { Text(S.t2("Düzenle", "Edit")) }
                TextButton(enabled = !busy, onClick = { detail = null; act { exportText = requireNotNull(api).request("GET", "/api/bots/${botApiSegment(bot.text("id"))}/export").toString(); saveFile.launch("bot.hermes-bot.json") } }) { Text(S.t2("Dışa aktar", "Export")) }
                TextButton(onClick = { detail = null; deleting = bot }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(S.t2("Ajanı sil", "Delete agent")) }
            }
            Spacer(Modifier.height(16.dp))
        } }
    }
    deleting?.let { bot -> AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text(S.t2("Ajan ve konuşmaları silinsin mi?", "Delete this agent and conversations?")) }, text = { Text(bot.text("name")) },
        confirmButton = { TextButton(enabled = !busy, onClick = { act { requireNotNull(api).request("DELETE", "/api/bots/${botApiSegment(bot.text("id"))}"); deleting = null; refresh() } }) { Text(S.t2("Sil", "Delete")) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(S.t2("Vazgeç", "Cancel")) } }) }
    inspect?.let { entry -> SelectableMessageSheet(entry = entry, pinned = false, onDismiss = { inspect = null }) }
}

@Composable
internal fun WorkspaceNotice(text: String, error: Boolean = false, onClose: () -> Unit = {}) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, S.t2("Kapat", "Close"), Modifier.size(18.dp)) } }
    }
}

@Composable
private fun EmptyWorkspaceCard(title: String, body: String, action: () -> Unit) { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = action) { Text(S.t2("Başlayalım", "Get started")) }
} } }

@Composable
private fun AgentWorkspaceCard(bot: JsonObject, group: String?, onDetails: () -> Unit, onChat: () -> Unit, onTask: () -> Unit) {
    Card(onClick = onChat, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompanionFace(bot.text("id"), Modifier.size(48.dp))
            Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(bot.text("name"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(group?.takeIf(String::isNotBlank) ?: S.t2("Sohbet ve görevler", "Chat and tasks"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
            IconButton(onClick = onDetails) { Icon(Icons.Default.MoreHoriz, "Arkadaş ayarları") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { TextButton(onClick = onChat) { Text(S.t2("Sohbet", "Chat"),style=MaterialTheme.typography.labelLarge) }
            TextButton(onClick = onTask) { Text(if(group.isNullOrBlank()) "Görev ver" else "Ekiple görev",style=MaterialTheme.typography.labelLarge) } }
    } }
}
