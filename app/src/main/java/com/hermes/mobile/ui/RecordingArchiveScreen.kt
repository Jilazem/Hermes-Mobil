package com.hermes.mobile.ui

import android.content.Intent
import android.media.MediaPlayer
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
import androidx.core.content.FileProvider
import com.hermes.mobile.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/** Visible companion to the installed recorder; importing never starts recording/transcription. */
@Composable
fun RecordingArchiveScreen(onBack:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val store=remember(context) { RecordingArchiveStore(context) }
    var records by remember { mutableStateOf(store.list()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<ArchivedRecording?>(null) }
    var removing by remember { mutableStateOf<ArchivedRecording?>(null) }
    val recorder="com.kitsumed.shizucallrecorder"
    fun stop() { runCatching { player?.release() };player=null;playing=null }
    androidx.lifecycle.compose.LifecycleStartEffect(Unit) { onStopOrDispose { stop() } }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            busy=true
            try {
                runInterruptible(Dispatchers.IO) { store.import(uri) }
                records=store.list();status="Ses kaydı arşive alındı; asıl dosya korunuyor."
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {status="Kayıt alınamadı: ${error.message?.take(160)}"}
            finally {busy=false}
        }
    }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val record=exporting;exporting=null
        if(uri!=null && record!=null) scope.launch {
            busy=true
            try {
                withContext(Dispatchers.IO) { store.file(record.id).inputStream().use { input ->
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use { input.copyTo(it) }
                } }
                status="Ses kaydı seçtiğin konuma kaydedildi."
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(_:Exception) {status="Ses dosyası dışa aktarılamadı."}
            finally {busy=false}
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick=onBack) {Text("Geri")}
        Text("Görüşme kayıtların",style=MaterialTheme.typography.headlineSmall)
        Text("Kaydı oluştur, seçtiklerini Hermes'te arşivle, dinle ve daha sonra metne çevirmek için paylaş.")
        Card {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Çağrı kaydedicisi",style=MaterialTheme.typography.titleMedium)
            val installed=runCatching { context.packageManager.getPackageInfo(recorder,0) }.isSuccess
            Text(if(installed) "ShizuCallRecorder kurulu. Görüşme kaydı onun seçili ayarları ve Shizuku yetkisiyle çalışır." else "Bu telefonda ShizuCallRecorder bulunamadı. Samsung Telefon'un kayıt özelliğini de kullanabilirsin.")
            Text("Hermes bu ekran açılınca kayıt başlatmaz. Android/OEM ve arama türüne göre iki tarafın sesi ayrıca kontrol edilmelidir.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={
                val intent=context.packageManager.getLaunchIntentForPackage(recorder)
                if(intent==null) status="Kaydedici bulunamadı; Samsung Telefon ayarlarını kullanabilirsin."
                else runCatching {context.startActivity(intent)}.onFailure {status="Kaydedici açılamadı."}
            }) {Text("Çağrı kaydedicisini aç")}
            Text("Kaydedicideki mevcut klasörünü kullan. ‘Kaydı içe aktar’ ile istediğin dosyayı seç; yeni bir eşleşme veya sunucu adresi gerekmez.",style=MaterialTheme.typography.bodySmall)
        } }
        Button(enabled=!busy,onClick={importer.launch(arrayOf("audio/*","application/ogg"))}) {Text("Kaydı içe aktar")}
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(status.isNotBlank()) Text(status,style=MaterialTheme.typography.bodySmall)
        if(records.isEmpty()) Text("Henüz arşive eklediğin ses kaydı yok.")
        records.forEach { record -> Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(record.name,style=MaterialTheme.typography.titleSmall)
            Text("${record.durationMs/60000}:${(record.durationMs/1000%60).toString().padStart(2,'0')} · ${record.bytes/1024} KB",style=MaterialTheme.typography.bodySmall)
            Row {
                TextButton(enabled=!busy,onClick={
                    if(playing==record.id) stop() else {
                        stop()
                        runCatching {
                            val next=MediaPlayer();player=next;playing=record.id
                            next.setDataSource(store.file(record.id).absolutePath)
                            next.setOnCompletionListener {stop()};next.setOnErrorListener {_,_,_->stop();status="Ses oynatılamadı";true}
                            next.setOnPreparedListener {it.start()};next.prepareAsync()
                        }.onFailure {stop();status="Kayıt açılamadı."}
                    }
                }) {Text(if(playing==record.id)"Durdur" else "Dinle")}
                TextButton(enabled=!busy,onClick={
                    runCatching {
                        val uri=FileProvider.getUriForFile(context,context.packageName+".fileprovider",store.file(record.id))
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type=record.mime;putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },"Kaydı paylaş / daha sonra transkrip et"))
                    }.onFailure {status="Paylaşım açılamadı."}
                }) {Text("Paylaş")}
                TextButton(enabled=!busy,onClick={exporting=record;exporter.launch(record.name)}) {Text("Dışa aktar")}
            }
            TextButton(enabled=!busy,onClick={removing=record}) {Text("Arşiv kopyasını kaldır")}
        } } }
        Text("Paylaş veya dışa aktar işlemi ses dosyasını verir. Otomatik bulut yükleme ve otomatik transkripsiyon yapılmaz.",style=MaterialTheme.typography.bodySmall)
    }
    removing?.let { record -> AlertDialog(onDismissRequest={removing=null},title={Text("Arşiv kopyası kaldırılsın mı?")},
        text={Text("Yalnız Hermes'teki kopya kaldırılır. Kaydedicinin asıl dosyası korunur.")},
        confirmButton={TextButton(onClick={
            if(playing==record.id)stop()
            runCatching {store.remove(record.id)}.onSuccess {records=store.list()}.onFailure {status="Kopya kaldırılamadı."}
            removing=null
        }) {Text("Kaldır")}},dismissButton={TextButton(onClick={removing=null}) {Text("Vazgeç")}}) }
}
