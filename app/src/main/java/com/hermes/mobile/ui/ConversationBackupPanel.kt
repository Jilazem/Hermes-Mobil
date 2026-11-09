package com.hermes.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConversationBackupPanel(profile: ServerProfile?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { ConversationArchiveStore(context) }
    var sessions by remember(profile?.id) { mutableStateOf(emptyList<HermesSession>()) }
    var selected by remember(profile?.id) { mutableStateOf(emptySet<String>()) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var archives by remember { mutableStateOf(store.list()) }
    var viewing by remember { mutableStateOf<ConversationArchive?>(null) }
    var viewedChat by remember { mutableStateOf<ArchivedConversation?>(null) }
    fun refresh() {
        val server = profile ?: return
        scope.launch {
            busy = true
            try { sessions = HermesClient(server).sessions().filter { it.id.isNotBlank() }.distinctBy { it.id }; status = "${sessions.size} sohbet bulundu" }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status = "Sohbet listesi alınamadı; Dashboard bağlantısını kontrol et." }
            finally { busy = false }
        }
    }
    LaunchedEffect(profile?.id) { refresh() }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val raw = pending
        if (uri != null && raw != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(raw) }
                    store.save(raw)
                }
                archives = store.list(); pending = null; status = "Seçili sohbetler kaydedildi; yerel arşivden çevrimdışı okuyabilirsin."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status = "Yedek yazımı tamamlanamadı. Hazır yedeği yeniden dışa aktarabilirsin." }
            finally { busy = false }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val info = withContext(Dispatchers.IO) {
                    val raw = requireNotNull(context.contentResolver.openInputStream(uri)).use(ConversationArchiveCodec::read)
                    store.save(raw)
                }
                archives = store.list(); viewing = withContext(Dispatchers.IO) { store.open(info.id) }; viewedChat = null
                status = "Yedek doğrulandı; ${info.conversations} sohbet yerel arşive alındı."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { status = "Yedek açılamadı: ${error.message?.take(180) ?: "geçersiz dosya"}" }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Seçtiğin sohbetleri yedekle", style = MaterialTheme.typography.titleMedium)
        Text("Mesajlar, zaman damgaları ve metindeki bağlantılar korunur. Ek dosyaların kendisi bu JSON'a eklenmez. Yedek şifrelenmez; kaynak sohbetler değiştirilmez.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = ::refresh, enabled = !busy && profile != null) { Text("Listeyi yenile") }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json","text/plain")) }, enabled = !busy) { Text("Yedeği aç") }
        }
        if (profile == null) Text("Sunucudan sohbet seçmek için önce Dashboard bağlantısını ayarla. Var olan yedeğini yine açabilirsin.")
        OutlinedTextField(query,{query=it},label={Text("Yedeklenecek sohbeti ara")},singleLine=true,modifier=Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=380.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            items(sessions.filter { query.isBlank() || it.title.contains(query,true) || it.serverTitle.orEmpty().contains(query,true) },key={it.id}) { session ->
                val title = session.serverTitle?.takeIf { it.isNotBlank() && it != session.id } ?: session.title
                Row(Modifier.fillMaxWidth().clickable(enabled=!busy) {
                    selected = if(session.id in selected) selected-session.id else if(selected.size<25) selected+session.id else selected
                }.padding(6.dp)) {
                    Checkbox(session.id in selected,onCheckedChange=null)
                    Column(Modifier.weight(1f).padding(start=8.dp)) { Text(title,maxLines=2);Text("${session.messageCount} mesaj",style=MaterialTheme.typography.labelSmall) }
                }
            }
        }
        Button(enabled=!busy && selected.isNotEmpty(),onClick={
            val server=profile ?: return@Button
            val chosen=sessions.filter { it.id in selected }
            scope.launch {
                busy=true;pending=null
                try {
                    val ready=mutableListOf<ArchivedConversation>();var failed=0
                    for ((index,chat) in chosen.withIndex()) {
                        status="Sohbetler okunuyor · ${index+1}/${chosen.size}"
                        try {
                            val messages=HermesClient(server).sessionMessages(chat.id)
                            check(messages.isNotEmpty() || chat.messageCount==0) { "Sohbet içeriği alınamadı" }
                            ready+=ArchivedConversation(chat.id,chat.serverTitle?.takeIf { it.isNotBlank() && it!=chat.id } ?: chat.title,messages)
                        } catch (cancelled:CancellationException) { throw cancelled }
                        catch (_:Exception) { failed++ }
                    }
                    if(ready.isEmpty()) status="Seçili sohbetler okunamadı; kaynaklarda değişiklik yapılmadı."
                    else {
                        pending=withContext(Dispatchers.Default) { ConversationArchiveCodec.encode(ConversationArchive(createdAt=System.currentTimeMillis(),sourceName=server.name,conversations=ready)) }
                        status="${ready.size} sohbet hazır." + if(failed>0) " $failed sohbet alınamadı; dosyada yalnız hazır sohbetler olacak." else ""
                    }
                } catch (cancelled:CancellationException) { throw cancelled }
                catch(error:Exception) { status=error.message ?: "Yedek hazırlanamadı" }
                finally { busy=false }
            }
        }) { Text("Seçilenleri hazırla (${selected.size}/25)") }
        if(pending!=null) OutlinedButton(enabled=!busy,onClick={exporter.launch("Hermes-Sohbetler-${System.currentTimeMillis()}.json")}) { Text("Hazır sohbet yedeğini kaydet") }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(status.isNotBlank()) Text(status,style=MaterialTheme.typography.bodySmall)
        Text("Yerel sohbet arşivin",style=MaterialTheme.typography.titleMedium)
        if(archives.isEmpty()) Text("Henüz yerel sohbet yedeği yok.",style=MaterialTheme.typography.bodySmall)
        archives.forEach { info -> TextButton(enabled=!busy,onClick={scope.launch {
            runCatching { withContext(Dispatchers.IO) { store.open(info.id) } }.onSuccess { viewing=it;viewedChat=null }
                .onFailure { status="Yerel yedek doğrulanamadı; dosya korunuyor." }
        }}) { Text("${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale.getDefault()).format(java.util.Date(info.createdAt))} · ${info.conversations} sohbet · ${info.messages} mesaj") } }
    }
    viewing?.let { archive -> AlertDialog(onDismissRequest={viewing=null;viewedChat=null},
        title={Text(viewedChat?.title ?: "Çevrimdışı sohbet arşivi")},
        text={LazyColumn(Modifier.fillMaxWidth().heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            val chat=viewedChat
            if(chat==null) items(archive.conversations,key={it.id}) { saved -> TextButton(onClick={viewedChat=saved}) { Text(saved.title) } }
            else items(chat.messages.withIndex().toList(),key={it.index}) { (_,message) ->
                Column { Text(message.role,style=MaterialTheme.typography.labelSmall)
                    SelectionContainer { Text(message.content.orEmpty(),style=MaterialTheme.typography.bodyMedium) }
                }
            }
        }},confirmButton={TextButton(onClick={if(viewedChat!=null)viewedChat=null else viewing=null}) {Text(if(viewedChat!=null)"Sohbet listesi" else "Kapat")}})
    }
}
