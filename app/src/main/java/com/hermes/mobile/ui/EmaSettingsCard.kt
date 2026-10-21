package com.hermes.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.*
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.assistant.EmaVoice
import kotlinx.coroutines.*

/** The model lives in private app storage and survives ordinary APK updates. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmaSettingsCard(settings:AppSettings,onUpdate:((AppSettings)->AppSettings)->Unit) {
    val context=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    var downloaded by remember { mutableStateOf(EmaModelStore.present(context)) }
    var bytes by remember { mutableLongStateOf(EmaModelStore.bytesOnDisk(context)) }
    var total by remember { mutableLongStateOf(EmaModelStore.totalBytes) }
    var fallbackReady by remember {
        mutableStateOf(LocalTtsLogic.filesPresent(LocalTtsLogic.modelDir(context.filesDir)))
    }
    var task by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var voice by remember { mutableStateOf<EmaVoice?>(null) }
    var advanced by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { voice?.release() } }
    // EMA modeli yok ama yedek ses hazır: kullanıcı bunu kalıcı satırda görür.
    val fallbackActive = !downloaded && fallbackReady
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("EMA Lightning",style=MaterialTheme.typography.titleLarge,color=HermesColors.TextPrimary)
        Text("Modeli bir kez indir. Ses telefonunda üretilir; internet ve Mac gerekmez. Hermes’le sohbet ve sunucu araçları için bağlantı gerekir.",
            style=MaterialTheme.typography.bodyMedium,color=HermesColors.TextSecondary)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
            FilterChip(selected=settings.emaMode=="offline",onClick={ onUpdate { it.copy(emaMode="offline") } },label={Text("Telefonda · çevrimdışı")})
            FilterChip(selected=settings.emaMode!="offline",onClick={ onUpdate { it.copy(emaMode="server") } },label={Text("Hermes sunucusunda")})
        }
        Text(if(downloaded) "Model indirildi · 35 MB" else "EMA modeli · 35 MB",color=HermesColors.TextSecondary)
        if(fallbackActive) Text("EMA çevrimdışı modeli yok — yedek ses etkin",color=HermesColors.Busy)
        if(task?.isActive==true) {
            LinearProgressIndicator(progress={ (bytes.toFloat()/total.coerceAtLeast(1L)).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
            Text("%${(bytes*100/total.coerceAtLeast(1L)).coerceIn(0,100)} · ${bytes/1_000_000} / ${total/1_000_000} MB",color=HermesColors.TextMuted)
            OutlinedButton(onClick={ task?.cancel(); message="İndirme durdu. Devam ettiğinde kaldığı yerden sürer." }) { Text("İndirmeyi durdur") }
        } else {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(!downloaded) Button(onClick={
                    message=null
                    task=scope.launch {
                        try {
                            EmaModelStore.download(context) { progress -> bytes=progress }
                            withContext(Dispatchers.IO) { EmaOfflineEngine.ready(context) }
                            downloaded=true; total=EmaModelStore.totalBytes
                            onUpdate { it.copy(emaMode="offline") }; message="EMA hazır. Artık ses telefonunda üretiliyor."
                        } catch(e:CancellationException) { throw e }
                        catch(e:Exception) {
                            // Sessiz kalma yok: EMA kaynakları tükendi → yedek (Piper tr) sesi indir.
                            message="EMA modeli indirilemedi: ${e.message ?: "bilinmeyen hata"} · Yedek ses (Piper tr) indiriliyor…"
                            bytes=0L; total=LocalTtsLogic.TOTAL_BYTES
                            try {
                                LocalTtsDownloader(context) { done,_ -> bytes=done }.ensureModel()
                                val loaded=withContext(Dispatchers.IO) { LocalTtsEngine(context).ensureLoaded() }
                                fallbackReady=LocalTtsLogic.filesPresent(LocalTtsLogic.modelDir(context.filesDir))
                                if(loaded && fallbackReady) {
                                    onUpdate { it.copy(emaMode="offline") }
                                    message="EMA çevrimdışı modeli yok — yedek ses etkin"
                                } else message="EMA çevrimdışı modeli yok — yedek ses indi ama motor açılamadı. Tanılama ekranına bak."
                            } catch(e2:CancellationException) { throw e2 }
                            catch(e2:Exception) {
                                message="EMA modeli indirilemedi: ${e.message ?: "?"} · Yedek ses de indirilemedi: ${e2.message ?: "?"}"
                            }
                        }
                    }
                }) { Text(if(bytes>0) "İndirmeye devam et" else "EMA modelini indir") }
                else {
                    Button(onClick={
                        voice?.release()
                        voice=EmaVoice(context,EmaOfflineSpeech(context)).also { v ->
                            v.onDone={ message="Çevrimdışı ses denemesi tamamlandı." }
                            v.onError={ message=it }
                            v.say("Merhaba dünya. Ben Hermes. Bu sesi telefonunda, internet olmadan üretiyorum."); v.finish()
                        }
                    }) { Text("Sesi dene") }
                    OutlinedButton(onClick={ voice?.release(); voice=null }) { Text("Durdur") }
                }
            }
            if(downloaded) TextButton(onClick={
                voice?.release(); voice=null
                task=scope.launch { EmaOfflineEngine.delete(context); downloaded=false; bytes=0; message="EMA modeli kaldırıldı." }
            }) { Text("Modeli telefondan kaldır") }
        }
        message?.let { Text(it,color=HermesColors.TextSecondary,style=MaterialTheme.typography.bodySmall) }
        if(settings.emaMode!="offline") {
            Text("Ev ve dış bağlantı adresleri kayıtlı Hermes profilinden seçilir. EMA anahtarını yalnızca bir kez gir.",color=HermesColors.TextSecondary,style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value=settings.emaToken,onValueChange={ v->onUpdate { it.copy(emaToken=v.trim()) } },
                label={Text("EMA erişim anahtarı")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            TextButton(onClick={ advanced=!advanced }) { Text(if(advanced) "Özel adresi gizle" else "Özel EMA adresi") }
            if(advanced) OutlinedTextField(value=settings.emaUrl,onValueChange={ v->onUpdate { it.copy(emaUrl=v.trim()) } },
                label={Text("EMA adresi · boşsa otomatik")},singleLine=true,modifier=Modifier.fillMaxWidth())
        }
    }
}
