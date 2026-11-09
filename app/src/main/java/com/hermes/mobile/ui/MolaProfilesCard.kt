package com.hermes.mobile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hermes.mobile.car.*
import kotlinx.coroutines.launch

@Composable
fun MolaProfilesCard() {
    val context = LocalContext.current
    val store = remember(context) { MolaCarProfiles(context) }
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(store.all()) }
    var devices by remember { mutableStateOf(emptyList<MolaCarDevice>()) }
    var permitted by remember { mutableStateOf(MolaConnectedCars.permitted(context)) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    fun refresh() { scope.launch {
        checking = true
        try {
            permitted = MolaConnectedCars.permitted(context)
            devices = MolaConnectedCars.read(context)
            profiles = store.all()
            message = if (devices.isEmpty() && permitted) "Bağlı araç ses cihazı bulunamadı. Araca bağlanınca yeniden dene." else ""
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { message = "Araç bağlantısı okunamadı; Bluetooth ve izni kontrol edip yeniden dene." }
        finally { checking = false }
    } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        if (granted) refresh() else message = "Yakındaki cihaz izni verilmedi; Mola kullanılabilir, otomatik araç tanıma kapalı kalır."
    }
    LaunchedEffect(Unit) { refresh() }
    Text("Araçlarım · bir kez kaydet", style = MaterialTheme.typography.titleMedium)
    Text("Bluetooth eşleşmesini Android Auto saklar. Kayıtlı araç yeniden bağlandığında Hermes profili otomatik tanır; yeniden eşleştirme başlatmaz.",
        style = MaterialTheme.typography.bodySmall)
    if (!permitted) OutlinedButton(onClick = {
        if (Build.VERSION.SDK_INT >= 31) permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }) { Text("Araç tanıma iznini ver") }
    else TextButton(onClick = ::refresh, enabled = !checking) { Text(if (checking) "Bağlantı okunuyor…" else "Bağlı aracı kontrol et") }
    devices.forEach { device ->
        val saved = profiles.any { it.id == device.id }
        OutlinedButton(onClick = {
            runCatching { store.remember(device) }.onSuccess {
                profiles = store.all(); message = if (it == null) "En fazla 20 araç kaydedilebilir." else "Araç kaydedildi; aynı Bluetooth bağlantısında otomatik tanınacak."
            }.onFailure { message = "Araç kaydedilemedi; yeniden dene." }
        }, enabled = !saved, modifier = Modifier.fillMaxWidth()) {
            Text(device.name + if (saved) " · kayıtlı" else " · bu benim aracım")
        }
    }
    profiles.forEach { profile ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(profile.name, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            TextButton(onClick = {
                runCatching { store.forget(profile.id) }.onSuccess { profiles = store.all() }
                    .onFailure { message = "Kayıt kaldırılamadı; yeniden dene." }
            }) { Text("Kaydı unut") }
        }
    }
    if (profiles.isEmpty()) Text("Henüz kayıtlı araç yok.", style = MaterialTheme.typography.bodySmall)
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
    Text("Araç olarak tanıtılan tek Bluetooth ses bağlantısı, araç ekranı açıldığında otomatik kaydedilir. Diğer cihazları bir kez ‘bu benim aracım’ ile seç. Bluetooth kimliği sunulmayan USB bağlantılarını farklı araçmış gibi tahmin etmeyiz. Ekran paylaşımı için Android yeniden izin isteyebilir.",
        style = MaterialTheme.typography.bodySmall)
}
