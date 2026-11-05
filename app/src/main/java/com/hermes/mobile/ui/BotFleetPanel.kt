package com.hermes.mobile.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hermes.mobile.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

private fun JsonObject.number(key: String): Long = (get(key) as? JsonPrimitive)?.longOrNull ?: 0L
private fun loadMap(raw: String?): Map<String, String> = runCatching {
    Json.parseToJsonElement(raw.orEmpty()).jsonObject.mapValues { it.value.jsonPrimitive.content }
}.getOrDefault(emptyMap())
private fun saveMap(value: Map<String, String>) = JsonObject(value.mapValues { JsonPrimitive(it.value) }).toString()
private fun decodeLabels(raw: String) = raw.split('|').map(String::trim).filter(String::isNotBlank).distinct()
private fun encodeLabels(labels: List<String>) = labels.joinToString("|")
private fun decodeSchedule(raw: String): BotSchedule {
    val parts = raw.split('|')
    return BotSchedule(parts.getOrNull(0)?.toBooleanStrictOrNull() ?: false,
        runCatching { SchedulePreset.valueOf(parts.getOrNull(1).orEmpty()) }.getOrDefault(SchedulePreset.DAILY),
        parts.getOrNull(2) ?: "09:00")
}
private fun encodeSchedule(value: BotSchedule) = "${value.enabled}|${value.preset.name}|${value.time}"
private fun fleetVisibleMessages(sessions: List<JsonObject>): List<JsonObject> = sessions.flatMap(::botVisibleMessages)
private fun loadTasks(raw: String?): List<FleetTaskData> = runCatching {
    Json.parseToJsonElement(raw.orEmpty()).jsonArray.map { item -> item.jsonObject.let {
        FleetTaskData(it.number("id"), it.text("target"), it.text("text"), it.text("state"), it.rows("steps").map { s -> s.text("text") }, it.text("error").ifBlank { null }, it.text("session_id").ifBlank { null },it.text("answer").ifBlank { null })
    } }
}.getOrDefault(emptyList())
private fun saveTasks(tasks: List<FleetTaskData>) = JsonArray(tasks.map { task -> buildJsonObject {
    put("id", task.id); put("target", task.target); put("text", task.text); put("state", task.state)
    put("steps", JsonArray(task.steps.map { buildJsonObject { put("text", it) } })); task.error?.let { put("error", it) }
    task.sessionId?.let { put("session_id", it) }
    task.answer?.let { put("answer",it.take(4000)) }
} }).toString()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BotFleetPanel(bots: List<JsonObject>, api: BotApiClient?, onOpenApprovals: () -> Unit,
    initialBotId: String = "", initialInstruction: String = "", initialGroup:String="") {
    val context = LocalContext.current
    val prefs = remember(api?.storageKey) { EncryptedSharedPreferences.create(context, "bot-fleet-${api?.storageKey ?: "offline"}", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM) }
    val all = BotFleetLogic.ALL
    var group by remember { mutableStateOf(prefs.getString("group", all) ?: all) }
    var groups by remember { mutableStateOf(loadMap(prefs.getString("groups", null))) }
    var modes by remember { mutableStateOf(loadMap(prefs.getString("modes", null))) }
    var readMessages by remember { mutableStateOf(loadMap(prefs.getString("read-messages", null))) }
    var activeSessions by remember { mutableStateOf(loadMap(prefs.getString("active-sessions", null))) }
    var schedules by remember { mutableStateOf(loadMap(prefs.getString("schedules", null))) }
    var skills by remember { mutableStateOf(loadMap(prefs.getString("skills", null))) }
    var queue by remember { mutableStateOf(BotQueueState(prefs.getBoolean("paused", false), BotFleetLogic.recover(loadTasks(prefs.getString("tasks", null))))) }
    var selectedId by remember { mutableStateOf("") }
    var instruction by remember { mutableStateOf("") }
    LaunchedEffect(initialBotId, initialInstruction) {
        if (initialBotId.isNotBlank()) selectedId = initialBotId
        if (initialInstruction.isNotBlank()) instruction = initialInstruction
    }
    var newGroup by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var histories by remember { mutableStateOf<Map<String, List<JsonObject>>>(emptyMap()) }
    var editing by remember { mutableStateOf<FleetTaskData?>(null) }
    var pendingApproval by remember { mutableStateOf<FleetTaskData?>(null) }
    var pendingTeam by remember { mutableStateOf<List<FleetTaskData>?>(null) }
    LaunchedEffect(initialGroup) {
        if(initialGroup.isNotBlank()) group=initialGroup
    }
    var pendingGroupDeletion by remember { mutableStateOf<String?>(null) }
    var showLabelDialog by remember { mutableStateOf(false) }
    var skillInput by remember { mutableStateOf("") }
    var running by remember { mutableStateOf<Map<Long, Job>>(emptyMap()) }
    val mirrorStatus by ScreenMirror.status.collectAsState()
    val lastFrame by ScreenMirror.lastFrame.collectAsState()
    val scope = rememberCoroutineScope()
    val visibleIds = BotFleetLogic.visibleBotIds(bots.map { it.text("id") }, group, groups)
    val visibleBots = bots.filter { it.text("id") in visibleIds }
    val selected = bots.firstOrNull { it.text("id") == selectedId }
    val selectedMode = BotFleetLogic.modeFor(selectedId, modes)
    fun persistQueue(next: BotQueueState) { queue = next; prefs.edit().putBoolean("paused", next.paused).putString("tasks", saveTasks(next.tasks)).apply() }
    fun persist(key: String, map: Map<String, String>) { prefs.edit().putString(key, saveMap(map)).apply() }
    fun persistSchedule(id: String, value: BotSchedule) {
        if (!BotFleetLogic.validSchedule(value)) return
        val next = schedules + (id to encodeSchedule(value))
        schedules = next
        persist("schedules", next)
    }
    val scheduleTimePresets = listOf("08:00", "09:00", "12:00", "18:00", "21:00")
    val connectFirst = S.t2("Önce bot sunucusuna bağlanın", "Connect to the bot server first")
    val readyNotice = S.t2("Görev gönderim sırasında", "Task ready to send")
    suspend fun loadChat(id: String): List<JsonObject> {
        val client = requireNotNull(api)
        return client.request("GET", "/api/sessions?bot_id=${botApiSegment(id)}").rows("sessions").map { summary ->
            val sessionId = botSessionId(summary)
            if (sessionId.isBlank()) summary else botChatSession(client.request("GET", "/api/sessions/${botApiSegment(sessionId)}"))
        }
    }
    fun refreshChat(id: String) {
        if (api == null) { notice = connectFirst; return }
        scope.launch {
            try {
                val loaded = loadChat(id)
                histories = histories + (id to loaded)
                if (id !in activeSessions) loaded.firstOrNull()?.let { session ->
                    activeSessions = activeSessions + (id to botSessionId(session)); persist("active-sessions", activeSessions)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { notice = e.message.orEmpty() }
        }
    }
    val queuedNotice = S.t2("Sunucu yok veya filo molada; görev kuyruğa alındı", "Server unavailable or fleet paused; task queued")
    val completedNotice = S.t2("Bot yanıtı alındı", "Bot reply received")
    val failedNotice = S.t2("Görev başarısız", "Task failed")
    val uncertainNotice = S.t2("Yanıt izlenemedi; sunucu işi tamamlamış olabilir. Yeniden göndermeden sohbeti yenileyin.", "Reply could not be observed; the server may have completed the work. Refresh the chat before resending.")
    fun dispatch(task: FleetTaskData) {
        val next = BotFleetLogic.enqueue(queue, task)
        persistQueue(if (api == null) next.copy(tasks = next.tasks.map { if (it.id == task.id) it.copy(state = "queued") else it }) else next)
        notice = if (queue.paused || api == null) queuedNotice else readyNotice
    }
    fun startDispatch(task: FleetTaskData) {
        val client = api ?: return
        if (task.id in running || bots.none { it.text("id") == task.target }) return
        persistQueue(BotFleetLogic.start(queue, task.id, activeSessions[task.target]?.takeIf(String::isNotBlank)))
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (task.target !in activeSessions) {
                    val latest = loadChat(task.target).firstOrNull()?.let(::botSessionId).orEmpty()
                    activeSessions = activeSessions + (task.target to latest); persist("active-sessions", activeSessions)
                }
                val sessionId = activeSessions[task.target]?.takeIf(String::isNotBlank)
                val result = botChatSession(client.request("POST", "/api/bots/${botApiSegment(task.target)}/chat", buildJsonObject {
                    put("message", task.text); sessionId?.let { put("session_id", it) }
                }))
                val confirmedSession = botSessionId(result)
                require(confirmedSession.isNotBlank() && result["messages"] is JsonArray) { "Sunucu sohbet sonucunu doğrulamadı" }
                activeSessions = activeSessions + (task.target to confirmedSession); persist("active-sessions", activeSessions)
                persistQueue(BotFleetLogic.complete(queue, task.id, confirmedSession, result.rows("messages").filter { it.text("role") != "system" }.takeLast(2).map { it.text("role") },
                    result.rows("messages").lastOrNull { it.text("role")=="assistant" }?.let { it.text("content").ifBlank { it.text("text") } }))
                notice = completedNotice
                refreshChat(task.target)
            } catch (e: CancellationException) {
                persistQueue(BotFleetLogic.interrupted(queue, task.id, uncertainNotice))
                throw e
            } catch (e: BotApiException) {
                persistQueue(queue.copy(tasks = queue.tasks.map { if (it.id == task.id && it.state == "running") it.copy(state = "failed", error = e.message) else it }))
                notice = e.message ?: failedNotice
            } catch (e: Exception) {
                persistQueue(BotFleetLogic.interrupted(queue, task.id, uncertainNotice))
                notice = e.message ?: uncertainNotice
            } finally { running = running - task.id }
        }
        running = running + (task.id to job)
        job.start()
    }

    LaunchedEffect(api, queue, bots) {
        if (api != null) BotFleetLogic.readyToDispatch(queue).forEach(::startDispatch)
    }
    LaunchedEffect(selectedId, tab, histories) {
        if (tab == 0 && selectedId in histories) {
            val keys = botAssistantMessageKeys(histories[selectedId].orEmpty()).sorted().joinToString(",")
            if (readMessages[selectedId] != keys) {
                readMessages = readMessages + (selectedId to keys); persist("read-messages", readMessages)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(S.t2("Filo", "Fleet"), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(group == all, { group = all; prefs.edit().putString("group", all).apply() }, label = { Text(S.t2("Tümü", "All")) })
            groups.values.distinct().forEach { name -> FilterChip(group == name, { group = name; prefs.edit().putString("group", name).apply() }, label = { Text(name) }) }
        }
        Row {
            OutlinedTextField(newGroup, { newGroup = it }, Modifier.weight(1f), label = { Text(S.t2("Seçili botun grubu", "Selected bot group")) })
            TextButton(enabled = selectedId.isNotBlank() && newGroup.isNotBlank(), onClick = {
                val name = newGroup.trim(); val next = groups + (selectedId to name)
                groups = next; group = name; persist("groups", next); prefs.edit().putString("group", name).apply(); newGroup = ""
            }) { Text(S.t2("Ata", "Assign")) }
            TextButton(enabled = group != all, onClick = { pendingGroupDeletion = group }) {
                Text(S.t2("🗑 Grubu sil", "🗑 Delete group"))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleBots, key = { it.text("id") }) { bot ->
                val id = bot.text("id"); val current = queue.tasks.lastOrNull { it.target == id }
                val mode = BotFleetLogic.modeFor(id, modes)
                val unread = histories[id]?.let { sessions -> (botAssistantMessageKeys(sessions) - readMessages[id].orEmpty().split(',').toSet()).size.toString() } ?: "—"
                Card(Modifier.width(235.dp).combinedClickable(onClick = { selectedId = id; refreshChat(id) }, onLongClick = { selectedId = id })) { Column(Modifier.padding(12.dp)) {
                    Text("${bot.text("emoji").ifBlank { "🤖" }} ${bot.text("name")}")
                    Text(if (current == null) S.t2("Durum bilinmiyor", "Status unknown") else S.t2("Durum: ${current.state}", "Status: ${current.state}"))
                    if (current?.state == "running") LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (id == selectedId) Text(S.t2("Seçili", "Selected"), color = MaterialTheme.colorScheme.primary)
                    Text("${if (mode == BotMode.ASK) S.t2("Sor", "Ask") else S.t2("Otonom", "Autonomous")} · ${groups[id] ?: S.t2("Grupsuz", "Ungrouped")} · ${S.t2("okunmadı", "unread")}: $unread")
                    Text(current?.text ?: S.t2("Görev yok", "No task"), maxLines = 2)
                } }
            }
        }
        selected?.let { bot ->
            val id = bot.text("id"); val mode = BotFleetLogic.modeFor(id, modes)
            Row { listOf("ask" to S.t2("Sor", "Ask"), "auto" to S.t2("Otonom", "Autonomous")).forEach { (key, label) -> FilterChip((mode == BotMode.AUTO) == (key == "auto"), { val next = modes + (id to key); modes = next; persist("modes", next) }, label = { Text(label) }) }; if (mode == BotMode.ASK) TextButton(onClick = onOpenApprovals) { Text(S.t2("Onay bekleyenler", "Pending approvals")) } }
        }
        Text(S.t2("Canlı ekran", "Live screen"), style = MaterialTheme.typography.titleMedium)
        Text(when (mirrorStatus) {
            ScreenMirror.Status.NoPermission -> S.t2("Ekran paylaşımı kapalı", "Screen sharing is off")
            ScreenMirror.Status.Ready -> S.t2("Ekran paylaşımı hazır", "Screen sharing is ready")
            ScreenMirror.Status.Mirroring -> S.t2("Ekran yansıtılıyor", "Screen is being mirrored")
        })
        if (ScreenMirror.hasPermission) AndroidView(factory = { ctx -> SurfaceView(ctx).also { view -> view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) { val (w,hp)=ScreenMirror.phoneSize(ctx); ScreenMirror.attach(h.surface,w,hp,ctx.resources.displayMetrics.densityDpi); ScreenMirror.captureLastFrame(h.surface,w,hp) }
            override fun surfaceChanged(h: SurfaceHolder,f:Int,w:Int,ht:Int) { ScreenMirror.attach(h.surface,w,ht,ctx.resources.displayMetrics.densityDpi); ScreenMirror.captureLastFrame(h.surface,w,ht) }
            override fun surfaceDestroyed(h: SurfaceHolder) { ScreenMirror.detach() }
        }) } }, modifier = Modifier.fillMaxWidth().height(180.dp)) else Text(S.t2("Canlı önizleme için ekran paylaşma izni gerekli", "Screen sharing permission is required for live preview"))
        lastFrame?.let { Image(it.asImageBitmap(), S.t2("Son ekran karesi", "Last screen frame"), Modifier.fillMaxWidth().heightIn(max = 180.dp)) }
        OutlinedTextField(instruction, { instruction = it }, Modifier.fillMaxWidth(), label = { Text(S.t2("Tek cümlelik görev", "One-sentence task")) })
        Button(enabled = selected != null && instruction.isNotBlank(), onClick = {
            val bot = selected ?: return@Button
            val task = FleetTaskData(maxOf(System.currentTimeMillis(), (queue.tasks.maxOfOrNull { it.id } ?: 0L) + 1L), bot.text("id"), instruction.trim())
            instruction = ""
            if (BotFleetLogic.requiresApproval(selectedMode)) pendingApproval = task else dispatch(task)
        }) { Text(S.t2("Seçili bota gönder", "Send to selected bot")) }
        if(group!=all && visibleBots.size>1) FilledTonalButton(enabled=instruction.isNotBlank(),onClick={
            pendingTeam=com.hermes.mobile.data.bots.BotTeamLogic.tasks(group,visibleBots.map { it.text("id") },instruction,
                maxOf(System.currentTimeMillis(),(queue.tasks.maxOfOrNull { it.id } ?: 0L)+1L))
        }) { Text("$group · ${visibleBots.size} arkadaşa ortak görev") }
        Row {
            Button(enabled = !queue.paused, onClick = { persistQueue(BotFleetLogic.pause(queue)) }) { Text(S.t2("Kuyruğu duraklat", "Pause queue")) }
            TextButton(enabled = api != null, onClick = { persistQueue(BotFleetLogic.resume(queue)) }) { Text(S.t2("Devam", "Resume")) }
        }
        if (queue.paused) Text(S.t2("Yeni gönderimler bekliyor. Sunucuya iletilmiş işler yanıt vermeye devam eder.", "New sends are paused. Work already accepted by the server continues."))
        Text(S.t2("Kuyruk", "Queue"), style = MaterialTheme.typography.titleMedium)
        queue.tasks.forEach { task ->
            Card(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { if (task.state in setOf("queued", "ready")) editing = task })) {
                Column(Modifier.padding(10.dp)) {
                    Text(task.text)
                    Text(bots.firstOrNull { it.text("id")==task.target }?.text("name") ?: task.target,style=MaterialTheme.typography.labelMedium)
                    Text(S.t2("Durum: ${task.state}", "State: ${task.state}"))
                    task.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    task.answer?.takeIf(String::isNotBlank)?.let { MarkdownText(it) }
                    task.steps.forEach { Text("• $it") }
                    Row {
                        TextButton(enabled = task.state in setOf("queued", "ready"), onClick = { editing = task }) { Text(S.t2("Düzenle", "Edit")) }
                        TextButton(enabled = task.state != "running", onClick = { persistQueue(BotFleetLogic.delete(queue, task.id)) }) { Text(S.t2("Kaydı kaldır", "Remove entry")) }
                        TextButton(enabled = task.state in setOf("ready", "queued"), onClick = {
                            persistQueue(BotFleetLogic.stop(queue, task.id))
                        }) { Text(S.t2("Gönderimi iptal et", "Cancel send")) }
                    }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) { listOf(S.t2("Sohbet", "Chat"), S.t2("Görevler", "Tasks"), S.t2("Yetenekler", "Skills"), S.t2("Zamanlama", "Schedule")).forEachIndexed { i, label -> TextButton(onClick = { tab=i; selectedId.takeIf(String::isNotBlank)?.let { id -> if (i==0) refreshChat(id) } }) { Text(label) } } }
        selected?.let { bot ->
            val id = bot.text("id")
            when(tab) {
                0 -> {
                    Row {
                        TextButton(onClick = { refreshChat(id) }) { Text(S.t2("Sohbeti yenile", "Refresh chat")) }
                        TextButton(enabled = queue.tasks.none { it.target == id && it.state in setOf("running", "unknown") }, onClick = {
                            activeSessions = activeSessions + (id to ""); persist("active-sessions", activeSessions)
                            histories = histories + (id to emptyList())
                        }) { Text(S.t2("Yeni sohbet", "New chat")) }
                    }
                    fleetVisibleMessages(histories[id].orEmpty()).forEach { message ->
                        Text(message.text("role"), style = MaterialTheme.typography.labelMedium)
                        MarkdownText(message.text("content").ifBlank { message.text("text") })
                    }
                }
                1 -> queue.tasks.filter { it.target==id }.forEach { Text("${it.state}: ${it.text}") }
                2 -> {
                    val labels = decodeLabels(skills[id].orEmpty())
                    TextButton(onClick = { skillInput = ""; showLabelDialog = true }) {
                        Text(S.t2("＋ Yetenek etiketi ekle", "＋ Add skill label"))
                    }
                    labels.forEach { label ->
                        InputChip(selected = true, onClick = {
                            val nextLabels = BotFleetLogic.removeLabel(labels, label)
                            val next = skills + (id to encodeLabels(nextLabels)); skills = next; persist("skills", next)
                        }, label = { Text(label) }, trailingIcon = { Text("×") })
                    }
                    Text(bot["tools"]?.toString() ?: "—")
                }
                else -> {
                    val schedule = decodeSchedule(schedules[id].orEmpty())
                    Text(S.t2("Bu sunucu zamanlama API’si sunmuyor. Aşağıdakiler cihazda saklanan plan tercihleridir; otomatik görev çalıştırmaz.", "This server has no scheduling API. These are locally saved planning preferences; they do not run tasks automatically."))
                    Row { Text(S.t2("Etkin", "Enabled")); Switch(schedule.enabled, { enabled ->
                        persistSchedule(id, schedule.copy(enabled = enabled))
                    }) }
                    SchedulePreset.entries.forEach { preset -> FilterChip(schedule.preset == preset, {
                        persistSchedule(id, schedule.copy(preset = preset))
                    }, label={Text(preset.name)}) }
                    Text(S.t2("Hazır saat", "Ready time"))
                    scheduleTimePresets.forEach { time -> FilterChip(schedule.time == time, {
                        persistSchedule(id, schedule.copy(time = time))
                    }, label = { Text(time) }) }
                    OutlinedTextField(schedule.time, { time ->
                        val next = schedule.copy(time = time)
                        if (BotFleetLogic.validSchedule(next)) persistSchedule(id, next)
                    }, label={Text(S.t2("Saat (SS:DD)", "Time (HH:MM)"))}, isError = !BotFleetLogic.validSchedule(schedule))
                }
            }
        }
        if (notice.isNotBlank()) Text(notice)
    }
    editing?.let { task -> var text by remember(task) { mutableStateOf(task.text) }; AlertDialog(onDismissRequest={editing=null}, title={Text(S.t2("Sıradaki talimatı düzenle", "Edit queued instruction"))}, text={OutlinedTextField(text,{text=it})}, confirmButton={TextButton(onClick={persistQueue(BotFleetLogic.edit(queue,task.id,text)); editing=null}){Text(S.t2("Kaydet", "Save"))}}, dismissButton={TextButton(onClick={persistQueue(BotFleetLogic.delete(queue,task.id)); editing=null}){Text(S.t2("Sil", "Delete"))}}) }
    pendingTeam?.let { tasks -> AlertDialog(onDismissRequest={pendingTeam=null},
        title={Text("Ekibe ortak görev")},
        text={Text("${tasks.size} üyeye ayrı birer istek gönderilecek:\n"+tasks.joinToString("\n") { task -> bots.firstOrNull { it.text("id")==task.target }?.text("name") ?: task.target }+
            "\n\n"+instruction+"\n\nHer üyenin yanıtı kuyrukta ve kendi sohbetinde görünür. Üyeler birbirlerinin yanıtlarını otomatik görmez.")},
        confirmButton={TextButton(onClick={
            var next=queue
            tasks.forEach { task -> next=BotFleetLogic.enqueue(next,task) }
            if(api==null) next=next.copy(tasks=next.tasks.map { if(it.id in tasks.map { t -> t.id }) it.copy(state="queued") else it })
            persistQueue(next); pendingTeam=null; instruction=""; notice="Ekip görevi kuyruğa alındı."
        }) {Text("Ekibe gönder")}},dismissButton={TextButton(onClick={pendingTeam=null}) {Text("Vazgeç")}}) }
    pendingApproval?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingApproval = null },
            title = { Text(S.t2("Görev onayı", "Task approval")) },
            text = { Text(S.t2("Ask modundaki bu görev araç çalıştırabilir ve para harcayabilir. Gönderilsin mi?", "This Ask-mode task may run tools and spend money. Send it?")) },
            confirmButton = { TextButton(onClick = { pendingApproval = null; dispatch(task) }) { Text(S.t2("Onayla", "Approve")) } },
            dismissButton = { TextButton(onClick = { pendingApproval = null }) { Text(S.t2("Vazgeç", "Cancel")) } },
        )
    }
    pendingGroupDeletion?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingGroupDeletion = null },
            title = { Text(S.t2("Grup silinsin mi?", "Delete group?")) },
            text = { Text(S.t2("$name grubu silinecek; botlar gruptan çıkarılacak.", "$name will be deleted; bots will become ungrouped.")) },
            confirmButton = { TextButton(onClick = {
                val result = BotFleetLogic.deleteGroup(selectedGroup = group, deletedGroup = name, assignments = groups)
                groups = result.assignments; group = result.selectedGroup
                persist("groups", result.assignments); prefs.edit().putString("group", result.selectedGroup).apply()
                pendingGroupDeletion = null
            }) { Text(S.t2("Sil", "Delete")) } },
            dismissButton = { TextButton(onClick = { pendingGroupDeletion = null }) { Text(S.t2("Vazgeç", "Cancel")) } },
        )
    }
    if (showLabelDialog && selectedId.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showLabelDialog = false },
            title = { Text(S.t2("Yetenek etiketi ekle", "Add skill label")) },
            text = { OutlinedTextField(skillInput, { skillInput = it }, label = { Text(S.t2("Etiket", "Label")) }) },
            confirmButton = { TextButton(enabled = skillInput.isNotBlank(), onClick = {
                val labels = decodeLabels(skills[selectedId].orEmpty())
                val next = skills + (selectedId to encodeLabels(BotFleetLogic.addLabel(labels, skillInput)))
                skills = next; persist("skills", next); skillInput = ""; showLabelDialog = false
            }) { Text(S.t2("Ekle", "Add")) } },
            dismissButton = { TextButton(onClick = { showLabelDialog = false }) { Text(S.t2("Vazgeç", "Cancel")) } },
        )
    }
}
