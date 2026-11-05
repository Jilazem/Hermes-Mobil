package com.hermes.mobile.ui.bots

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.mobile.data.*
import com.hermes.mobile.data.bots.PhongentTaskJournal
import com.hermes.mobile.data.remote.*
import com.hermes.mobile.ui.MarkdownText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun DeviceLocalCards(onSettings: () -> Unit, onMola: () -> Unit, stopped: Boolean, phoneOpen: Boolean, togglePhone: () -> Unit) {
    Text("Kontrol noktaları", style = MaterialTheme.typography.titleLarge)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bu telefon", style = MaterialTheme.typography.titleMedium)
            Text(when {
                stopped -> "Acil durdur açık: yeni telefon yazmaları engellenir."
                HermesAccessibilityService.isRunning() -> "Telefon kontrol servisi bağlı."
                else -> "Telefon kontrol servisi bağlı değil."
            })
            Row { TextButton(onClick = togglePhone) { Text(if (phoneOpen) "Ayrıntıları kapat" else "Telefon denetimi") }
                TextButton(onClick = onSettings) { Text("Ayarlar") } }
        }
    }
    OutlinedCard(onClick = onMola, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.DirectionsCar, null)
            Column { Text("Mola · araç çalışma alanı", style = MaterialTheme.typography.titleMedium)
                Text("Kurulum rehberi, bağlantı tanısı ve telefon önizlemesi.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoteDevicesSection() {
    val context = LocalContext.current
    val registryResult = remember { runCatching { RemoteAgentConnectionStore(context) } }
    val registry = registryResult.getOrNull()
    if (registry == null) { Text("Cihaz kayıtları okunamadı. Ayarları kontrol edin.", color = MaterialTheme.colorScheme.error); return }
    val connections by registry.connections.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = connections.firstOrNull { it.id == selectedId }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Uzak cihazlar", style = MaterialTheme.typography.titleLarge)
        FilledTonalIconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "Mika veya Phongent cihazı ekle") }
    }
    if (connections.isEmpty()) Text("Mika'nın uzak erişimini veya Phongent'in yürütücü cihazını bağla. Erişim bilgileri bu telefonda şifreli saklanır.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    connections.forEach { connection ->
        OutlinedCard(onClick = { selectedId = connection.id }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.PhoneAndroid, null)
                Column(Modifier.weight(1f)) {
                    Text(connection.label, style = MaterialTheme.typography.titleMedium)
                    Text("${connection.kind} · ${connection.baseUrl}", style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.ChevronRight, "Cihazı aç")
            }
        }
    }
    if (adding) AddRemoteDeviceSheet(onDismiss = { adding = false }, onSave = { registry.upsert(it); selectedId = it.id; adding = false })
    selected?.let { connection ->
        ModalBottomSheet(onDismissRequest = { selectedId = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            key(connection.id) { RemoteDevicePane(connection, onRemove = { registry.remove(connection.id); selectedId = null }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRemoteDeviceSheet(onDismiss: () -> Unit, onSave: (RemoteAgentConnection) -> Unit) {
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(RemoteAgentKind.MIKA) }
    var label by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var pairId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true; notice = ""
        scope.launch { try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { notice = e.message ?: "Bağlantı doğrulanamadı." } finally { busy = false } }
    }
    fun candidate() = RemoteAgentConnection(label = label.ifBlank { kind.name }, kind = kind, baseUrl = address.trim(), token = token.trim())
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Bir yürütücü bağla", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { RemoteAgentKind.entries.forEach { option ->
                FilterChip(selected = kind == option, enabled = !busy, onClick = { kind = option; pairId = null; notice = "" }, label = { Text(option.name) })
            } }
            Text(if (kind == RemoteAgentKind.MIKA) "Mika → Ayarlar → Uzak erişim bölümündeki adres ve tokenı kullan. Telefon uygulamayı çalıştırıyor olmalı."
                else "Phongent yürütücüsündeki LAN adresini gir. Yeni eşleştirme için o cihazdaki bildirimi onayla.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("Cihaz adı") }, singleLine = true, enabled = !busy)
            OutlinedTextField(address, { address = it; pairId = null }, Modifier.fillMaxWidth(), label = { Text("Servis adresi") }, placeholder = { Text("http://192.168.1.20:8765") }, singleLine = true, enabled = !busy)
            OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("Erişim tokenı") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy)
            if (kind == RemoteAgentKind.PHONGENT) {
                OutlinedButton(enabled = !busy && address.isNotBlank(), onClick = { action {
                    val client = PhongentRemoteClient(candidate())
                    val state = if (pairId == null) client.beginPair("Hermes Mobil 7") else client.pollPair(pairId!!)
                    pairId = state.requestId
                    if (state.status == "approved") { token = state.token.orEmpty(); notice = "Cihaz izin verdi. Bağlantıyı doğrulayıp kaydedebilirsin." }
                    else if (state.status in setOf("denied", "expired")) {
                        pairId = null
                        notice = "Eşleştirme reddedildi veya süresi doldu. Cihaz hazır olduğunda yeni izin isteyebilirsin."
                    } else notice = "Cihaz onayı bekleniyor. Bildirimi onayladıktan sonra sonucu yeniden kontrol et."
                } }) { Text(if (pairId == null) "Cihazdan eşleştirme izni iste" else "Eşleştirme sonucunu kontrol et") }
            }
            if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(enabled = !busy && address.isNotBlank() && token.isNotBlank(), onClick = { action {
                val connection = candidate()
                if (kind == RemoteAgentKind.MIKA) MikaRemoteClient(connection).capabilities() else PhongentRemoteClient(connection).state()
                onSave(connection)
            } }, modifier = Modifier.fillMaxWidth()) { Text("Doğrula ve kaydet") }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RemoteDevicePane(connection: RemoteAgentConnection, onRemove: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mika = remember(connection) { if (connection.kind == RemoteAgentKind.MIKA) MikaRemoteClient(connection) else null }
    val phongent = remember(connection) { if (connection.kind == RemoteAgentKind.PHONGENT) PhongentRemoteClient(connection) else null }
    val journal = remember(connection) { if (phongent != null) PhongentTaskJournal(context, connection) else null }
    val journalRows = journal?.tasks?.collectAsStateWithLifecycle()?.value.orEmpty()
    val unresolved = journalRows.filter { it.state in com.hermes.mobile.data.bots.DurableControllerQueue.ACTIVE }
    var tasks by remember { mutableStateOf(emptyList<RemoteAgentTask>()) }
    var state by remember { mutableStateOf<PhongentState?>(null) }
    var busy by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }
    var continuing by rememberSaveable { mutableStateOf<String?>(null) }
    var controlText by rememberSaveable { mutableStateOf("") }
    var uncertain by rememberSaveable { mutableStateOf(false) }
    var removeConfirm by remember { mutableStateOf(false) }
    suspend fun refresh() {
        if (mika != null) { mika.capabilities(); tasks = mika.list() }
        else { state = requireNotNull(phongent).state(); tasks = if (state?.taskQueueVersion == 1) phongent.tasks() else emptyList(); journal?.reconcile(tasks) }
        connected = true
    }
    fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true; notice = ""
        scope.launch { try { block() } catch (e: CancellationException) { throw e }
            catch (e: RemoteOutcomeUnknownException) { uncertain = true; notice = e.message.orEmpty() }
            catch (e: Exception) { notice = e.message ?: "Cihaz işlemi tamamlanamadı." }
            finally { busy = false } }
    }
    LaunchedEffect(connection.id) { act { refresh() } }
    Column(Modifier.fillMaxWidth().heightIn(max = 740.dp).verticalScroll(rememberScrollState()).padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(connection.label, style = MaterialTheme.typography.titleLarge)
        Text("${connection.kind} · ${connection.baseUrl}", style = MaterialTheme.typography.bodySmall)
        Row { TextButton(enabled = !busy, onClick = { act { connected = false; refresh() } }) { Text("Durumu yenile") }
            TextButton(enabled = !busy, onClick = { removeConfirm = true }) { Text("Bağlantıyı kaldır") } }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (notice.isNotBlank()) WorkspaceNotice(notice)
        if (uncertain) OutlinedCard { Column(Modifier.padding(12.dp)) {
            Text("Son isteğin sonucu belirsiz. Önce cihazdaki görev listesini kontrol et; otomatik tekrar gönderilmiyor.")
            TextButton(enabled = !busy, onClick = { act { refresh(); uncertain = false } }) { Text("Cihaz listesini yeniden kontrol et") }
        } }
        unresolved.filter { it.state in setOf("queued", "uncertain") }.forEach { pending ->
            OutlinedCard { Column(Modifier.padding(12.dp)) {
                Text("Yerel gönderim kaydı · ${pending.id.take(8)} · ${pending.state}")
                Text(pending.text, maxLines = 3)
                if (pending.state == "queued") TextButton(enabled = !busy, onClick = { journal?.discardQueued(pending.id) }) {
                    Text("Gönderilmemiş kaydı kaldır")
                } else TextButton(enabled = !busy, onClick = { act {
                    refresh()
                    requireNotNull(journal).acknowledgeAbsent(pending.id, tasks)
                    uncertain = false
                    notice = "Güncel listede bulunmayan yerel kayıt kapatıldı. Eski istek yeniden gönderilmedi."
                } }) { Text("Listede yoksa yerel kaydı kapat") }
            } }
        }
        continuing?.let { Text("Seçili sohbet: $it", style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { continuing = null }) { Text("Yeni sohbet kullan") } }
        OutlinedTextField(goal, { goal = it }, Modifier.fillMaxWidth(), label = { Text("Cihaza görev ver") }, maxLines = 5, enabled = !busy)
        Button(enabled = connected && !busy && !uncertain && unresolved.isEmpty() && goal.isNotBlank() && (mika != null || state?.taskQueueVersion == 1), onClick = {
            val sent = goal.trim()
            act {
                if (mika != null) {
                    val task = mika.start(sent, continuing); tasks = listOf(task) + tasks.filterNot { it.id == task.id }
                    continuing = task.conversation
                } else {
                    val prepared = requireNotNull(journal).prepare(sent, continuing ?: state?.conversationId.orEmpty())
                    try {
                        val task = requireNotNull(phongent).submit(sent, prepared.conversation, prepared.id)
                        journal.received(task); tasks = listOf(task) + tasks.filterNot { it.id == task.id }; continuing = task.conversation
                    } catch (e: Exception) {
                        if (e is RemoteApiException || e is IllegalArgumentException || e is IllegalStateException)
                            journal.reject(prepared.id, "Cihaz gönderimi reddetti; yeni deneme kullanıcıya bırakıldı.")
                        else journal.unknown(prepared.id)
                        throw e
                    }
                }
                goal = ""; notice = "Görev cihazdan alındı; tamamlanma için durumunu izle."
            }
        }) { Text("Gönder ve izle") }
        if (phongent != null && state?.taskQueueVersion != 1 && connected) Text("Bu yürütücü görev kuyruğu v1 sunmuyor. Durum okunabilir; bu ekrandan yeni iş gönderilmez.")
        if (phongent != null && state?.phase in setOf("running", "waiting", "confirmation", "takeover")) {
            TextButton(enabled = !busy, onClick = { act {
                val receipt = phongent.stop(state!!.runId)
                notice = if (receipt.confirmedTerminal) "Cihaz durdurulduğunu doğruladı." else "Durdurma isteği alındı; sonucu yenileyerek doğrula."
                refresh()
            } }) { Text("Çalışan üretimi durdur") }
        }
        if (phongent != null && state?.canResume == true) TextButton(enabled = !busy, onClick = { act {
            phongent.resume(state!!.runId); refresh(); notice = "Bekletilen işe devam isteği iletildi."
        } }) { Text("Bekletilen işe devam et") }
        OutlinedTextField(controlText, { controlText = it }, Modifier.fillMaxWidth(), label = { Text("Yanıt / ek yönlendirme") }, enabled = !busy)
        Text("Görev geçmişi", style = MaterialTheme.typography.titleMedium)
        if (tasks.isEmpty()) Text(if (connected) "Cihaz görev döndürmedi." else "Cihaz bağlantısı henüz doğrulanmadı.")
        tasks.take(50).forEach { task ->
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.goal.ifBlank { "Görev ${task.id.take(8)}" }, style = MaterialTheme.typography.titleMedium)
                Text("Durum: ${task.status}", color = MaterialTheme.colorScheme.primary)
                task.progress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                task.question?.let { Text(it) }
                task.answer?.let { MarkdownText(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(enabled = !busy, onClick = { continuing = task.conversation }) { Text("Bu sohbette devam") }
                    if (mika != null && task.answerable) TextButton(enabled = !busy && controlText.isNotBlank(), onClick = { act {
                        mika.answer(task.id, controlText); controlText = ""; refresh()
                    } }) { Text("Soruyu yanıtla") }
                    if (mika != null && task.status in setOf("running", "starting")) TextButton(enabled = !busy && controlText.isNotBlank(), onClick = { act {
                        mika.steer(task.id, controlText); controlText = ""; refresh()
                    } }) { Text("Yönlendir") }
                }
                if (mika != null && !MikaTaskStatus.finished(task.status)) TextButton(enabled = !busy, onClick = { act {
                    val receipt = mika.cancel(task.id)
                    notice = if (receipt.confirmedTerminal) "İptal doğrulandı." else "İptal istendi; terminal sonuç ayrıca okunmalı."
                    refresh()
                } }) { Text("İptal iste") }
                if (phongent != null && task.status == "queued") TextButton(enabled = !busy, onClick = { act {
                    phongent.cancelQueued(task.id); refresh()
                } }) { Text("Kuyruktan iptal et") }
            } }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (removeConfirm) AlertDialog(onDismissRequest = { removeConfirm = false }, title = { Text("Cihaz bağlantısı kaldırılsın mı?") },
        text = { Text("Çalışan görev varsa cihazda sürer. Yeniden bağlanmak için erişim bilgileri gerekir.") },
        confirmButton = { TextButton(onClick = onRemove) { Text("Bağlantıyı kaldır") } },
        dismissButton = { TextButton(onClick = { removeConfirm = false }) { Text("Vazgeç") } })
}
