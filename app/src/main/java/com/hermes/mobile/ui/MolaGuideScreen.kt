package com.hermes.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.car.app.connection.CarConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hermes.mobile.car.*
import com.hermes.mobile.data.ScreenMirror
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val MolaBlue = Color(0xFF091424)
private val MolaCard = Color(0xFF12243B)
private val MolaTeal = Color(0xFF7AE2DC)
private val MolaText = Color(0xFFEDF4FC)
private val MolaMuted = Color(0xFFA6BAD1)
private val MolaAmber = Color(0xFFFFCB82)

/** Phone guide and read-only diagnostics. Opening it never starts recording or a car session. */
@Composable
fun MolaGuideScreen(onBack: () -> Unit, onPreview: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val clipboard = LocalClipboardManager.current
    val host by MolaHostObservation.state.collectAsState()
    val projection by ScreenMirror.status.collectAsState()
    var panel by remember { mutableStateOf(PanelState()) }
    var refreshing by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var microphone by remember { mutableStateOf(false) }
    var checkedAt by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    var carConnection by remember { mutableStateOf<Int?>(null) }
    var prerequisites by remember { mutableStateOf(com.hermes.mobile.car.MolaPrerequisites.read(context)) }
    val shizuku = remember { com.hermes.mobile.data.ShizukuBridge() }
    val shizukuState by shizuku.state.collectAsState()
    DisposableEffect(shizuku) { shizuku.attach(); onDispose { shizuku.detach() } }
    val connection = remember(context) { runCatching { CarConnection(context) }.getOrNull() }
    BackHandler(onBack = onBack)
    DisposableEffect(connection, owner) {
        val observer = Observer<Int> { carConnection = it }
        runCatching { connection?.type?.observe(owner, observer) }
        onDispose { connection?.type?.removeObserver(observer) }
    }
    LaunchedEffect(context, owner, refreshKey) {
        val source = HermesPanelDataSource(context)
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                refreshing = true
                prerequisites = com.hermes.mobile.car.MolaPrerequisites.read(context)
                panel = source.load()
                microphone = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                checkedAt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                refreshing = false
                delay(20_000)
            }
        }
    }
    val connected = panel.connection == PanelConnection.Connected
    val nextStep = MolaGuideLogic.nextStep(panel.connection, panel.activeSessionId != null, host)
    val serverLine = when (panel.connection) {
        PanelConnection.Loading -> S.t2("Henüz sınanmadı", "Not checked yet")
        PanelConnection.NoProfile -> S.t2("Sunucu profili ekleyin", "Add a server profile")
        PanelConnection.NoKey -> S.t2("Sunucu anahtarı eksik", "Server token missing")
        PanelConnection.Unavailable -> S.t2("Sunucuya ulaşılamadı", "Server unavailable")
        PanelConnection.Connected -> S.t2("Sunucuya erişildi", "Server reached")
    }
    val carLine = when (carConnection) {
        CarConnection.CONNECTION_TYPE_PROJECTION -> S.t2("Android Auto bağlantısı bildirildi", "Android Auto connection reported")
        CarConnection.CONNECTION_TYPE_NATIVE -> S.t2("Araç üzerinde yerel uygulama", "Running natively in the car")
        CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> S.t2("Şu anda araç bağlantısı yok", "No car connection right now")
        else -> S.t2("Araç bağlantısı henüz bildirilmedi", "Car connection not reported yet")
    }
    val hostLine = when (host) {
        MolaHostState.Rendering -> S.t2("Araç yüzeyi alındı; panel sunuldu", "Car surface received; panel presented")
        MolaHostState.SurfaceAvailable -> S.t2("Araç yüzeyi alındı; panel hazırlanıyor", "Car surface received; preparing panel")
        MolaHostState.Failed -> S.t2("Araç yüzeyinde panel açılamadı", "Panel could not open on the car surface")
        MolaHostState.Released -> S.t2("Araç paneli şu anda kapalı", "Car panel is currently closed")
        MolaHostState.NotObserved -> S.t2("Gerçek araç paneli testi bekliyor", "Real car panel test pending")
    }

    Column(Modifier.fillMaxSize().background(MolaBlue).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.t2("Geri", "Back"), tint = MolaText)
            }
            Column(Modifier.weight(1f)) {
                Text("MOLA / HERMES", color = MolaTeal, fontSize = 11.sp, letterSpacing = 2.sp)
                Text(S.t2("Yola çıkmadan önce", "Before you set off"), color = MolaText, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Default.DirectionsCar, null, tint = MolaTeal, modifier = Modifier.size(32.dp))
        }
        val settingsStore = remember(context) { com.hermes.mobile.data.SettingsStore(context) }
        val appSettings by settingsStore.settings.collectAsState()
        MolaGuideCard {
            Text(
                S.t2("Mola Tam Ekran ve Sürüş Kipi", "Mola Full-Screen & Driving Mode"),
                color = MolaText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        S.t2("Tam ekran Mola · Sürüşte çalışır (Varsayılan: Açık)", "Full-screen Mola · Works while driving (Default: ON)"),
                        color = MolaText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        S.t2(
                            "Android Auto ekranında YouTube, telefon yansıtma, oyun ve diğer her şeyi gizler; Mola arayüzünü tam ekran açar ve sürüş sırasında kesintisiz çalıştırır. İstersen kapatabilirsin.",
                            "Hides YouTube, phone mirroring, game and all other menus on Android Auto; opens Mola full-screen and keeps it running while driving. Toggle off anytime.",
                        ),
                        color = MolaMuted,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = appSettings.molaFullScreenDriving,
                    onCheckedChange = { enabled ->
                        settingsStore.update { it.copy(molaFullScreenDriving = enabled) }
                    },
                )
            }
            if (!appSettings.molaFullScreenDriving) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            S.t2("Sürüş sırasında telefon yansıtmayı duraklat", "Pause phone mirroring while driving"),
                            color = MolaText,
                            fontSize = 14.sp,
                        )
                        Text(
                            S.t2("Klasik menü açıkken araç hareket edince ekran paylaşımını keser.", "Pauses screen sharing when the car moves in classic mode."),
                            color = MolaMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = appSettings.mirrorPauseWhileDriving,
                        onCheckedChange = { pause ->
                            settingsStore.update { it.copy(mirrorPauseWhileDriving = pause) }
                        },
                    )
                }
            }
        }
        MolaGuideCard {
            Text("Android Auto’da Hermes görünmüyor mu?", color = MolaText, fontWeight = FontWeight.SemiBold)
            Text("Android Auto: ${prerequisites.autoVersion ?: "Bulunamadı"}\nHermes kurulum kaydı: ${prerequisites.installer ?: "Elle kurulum / kaynak bildirilmedi"}", color = MolaMuted, fontSize = 13.sp)
            Text(prerequisites.visibilityAdvice, color = MolaAmber, fontSize = 13.sp)
            Text("Android 15+ park girişi: Hermes → Mola Park", color = MolaText)
            Text("Güncellemeden sonra Hermes’i telefonda bir kez aç, Android Auto bağlantısını çıkarıp yeniden bağla. ‘Mesaj gelmedi’ ekranı Mola ekranı değildir.",color=MolaMuted,fontSize=13.sp)
            TextButton(onClick={context.startActivity(android.content.Intent(context,ParkedMolaActivity::class.java))}) {
                Text("Park ekranını telefonda dene",color=MolaTeal)
            }
            Text("Shizuku: " + when(shizukuState) {
                com.hermes.mobile.data.ShizukuBridge.State.Unavailable -> "Servis çalışmıyor veya erişilemiyor"
                com.hermes.mobile.data.ShizukuBridge.State.NeedsPermission -> "Çalışıyor; Hermes izni bekleniyor"
                com.hermes.mobile.data.ShizukuBridge.State.Ready -> "Hermes için hazır"
            }, color = MolaMuted)
            TextButton(onClick = {
                if (shizukuState == com.hermes.mobile.data.ShizukuBridge.State.NeedsPermission) shizuku.requestPermission()
                else context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { context.startActivity(it) }
            }) { Text(if(shizukuState == com.hermes.mobile.data.ShizukuBridge.State.NeedsPermission) "Hermes’e Shizuku izni ver" else "Shizuku’yu aç", color = MolaTeal) }
            TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://github.com/moris545454/araba-/blob/3f45ad3557a25822c2a3a9f152f32ef82f57e005/KURULUM_REHBERI_TR.md"))) }) {
                Text("Mola kurulum yöntemini incele", color = MolaTeal)
            }
            TextButton(onClick = {
                val intent = context.packageManager.getLaunchIntentForPackage(com.hermes.mobile.car.MolaPrerequisites.AUTO_PACKAGE)
                    ?: android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${com.hermes.mobile.car.MolaPrerequisites.AUTO_PACKAGE}"))
                runCatching { context.startActivity(intent) }
            }) { Text("Android Auto ayarlarını aç", color = MolaTeal) }
            TextButton(onClick = { com.hermes.mobile.data.HermesAccessibilityService.openPermissionSettings(context) }) {
                Text("Telefon kontrol iznini aç", color = MolaTeal)
            }
        }
        MolaGuideCard {
            Text(S.t2("Telefonunu araç ekranında kullan.", "Use your phone on the car display."), color = MolaText,
                fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(S.t2("Telefonu Kullan ekranı görüntüyü araç ekranına taşır. Dokunmatik kontrol için telefonda erişilebilirlik izni gerekir. Hermes sohbet paneli ayrıca kullanılabilir.",
                "Use Phone mirrors your phone. Touch control requires accessibility permission. The Hermes conversation panel is available separately."), color = MolaMuted, fontSize = 14.sp)
            Button(onClick = {
                if (!ScreenMirror.requestFromPhone(context)) android.widget.Toast.makeText(context, "Ekran paylaşım izni açılamadı", android.widget.Toast.LENGTH_LONG).show()
            }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MolaTeal, contentColor = MolaBlue)) {
                Text(if (projection == ScreenMirror.Status.NoPermission) "Ekran paylaşım iznini ver" else "Ekran paylaşım iznini yenile")
            }
            Button(onClick = onPreview, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MolaTeal, contentColor = MolaBlue)) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(S.t2("Hermes sohbet panelini önizle", "Preview the Hermes conversation panel"))
            }
            Text(S.t2("Önizleme araç bağlantısı gerektirmez; gerçek araçta çalışmayı tek başına kanıtlamaz.",
                "Preview works without a car connection; it does not prove real car operation."), color = MolaMuted, fontSize = 12.sp)
        }

        MolaGuideCard { MolaProfilesCard() }

        Text(S.t2("ÜÇ ADIMDA BAŞLA", "START IN THREE STEPS"), color = MolaTeal, fontSize = 11.sp, letterSpacing = 1.5.sp)
        MolaRouteStep(1, S.t2("Ortak Hermes bağlantısı", "Shared Hermes connection"),
            S.t2("Botlar ve sohbet paneli seçili Dashboard kaydını kullanır; burada ayrıca adres veya token girilmez. Telefon yansıtma için ayrıca bir sunucu gerekmez.",
                "Bots and the conversation panel use your selected Dashboard. No separate address or token is required. Phone mirroring needs no extra server."), nextStep == 1, connected)
        MolaRouteStep(2, S.t2("Telefonda ekran paylaşımını hazırla", "Prepare phone screen sharing"),
            S.t2("Yukarıdaki ekran paylaşımı düğmesine basıp Android penceresinde tüm ekranı seç. Araçtan dokunmak için telefonun erişilebilirlik iznini aç. Bu sürüm Android ekran paylaşımını kullanır; videodaki kablosuz yardımcı eşleştirmesi henüz eklenmedi.",
                "Grant Android's full-screen sharing permission and enable accessibility for touch control. This version uses Android screen sharing; the video's wireless helper pairing is not included."), nextStep == 2, projection != ScreenMirror.Status.NoPermission)
        MolaRouteStep(3, S.t2("Araçta Telefonu Kullan ekranını aç", "Open Use Phone in the car"),
            S.t2("Araç park halindeyken Hermes → Telefonu Kullan yolunu aç. Uygulamalar düğmesi telefondaki uygulamaları listeler; son kullanılanlar öne gelir. Desteklenen Android Auto host ve test dağıtımı gerekir; yalnız APK yan yüklemek araçta görünürlüğü kanıtlamaz.",
                "While parked, open Hermes → Use Phone. Apps lists installed launchers and recent apps. A supported host and test distribution are required; sideloading alone does not prove car availability."), nextStep == 3, projection == ScreenMirror.Status.Mirroring)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.t2("Canlı kontrol", "Live checks"), color = MolaText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { refreshKey++; copied = false }, enabled = !refreshing) {
                Text(if (refreshing) S.t2("Sınanıyor…", "Checking…") else S.t2("Yeniden sına", "Check again"), color = MolaTeal)
            }
        }
        MolaGuideCard {
            MolaCheck(S.t2("Sunucu", "Server"), serverLine, connected)
            MolaCheck(S.t2("Oturum", "Session"), panel.activeSession ?: S.t2("Seçili oturum bulunamadı", "Selected session not found"), panel.activeSessionId != null)
            MolaCheck("Android Auto", carLine, carConnection == CarConnection.CONNECTION_TYPE_PROJECTION)
            MolaCheck(S.t2("Mola araç yüzeyi", "Mola car surface"), hostLine, MolaGuideLogic.hasCarSurfaceEvidence(host))
            MolaCheck(S.t2("Mikrofon", "Microphone"), if (microphone) S.t2("İzin var · kayıt başlatılmadı", "Permission granted · no recording started")
                else S.t2("İzin yok · paneli okumaya engel değil", "No permission · reading the panel still works"), microphone)
            Text(S.t2("Bu durumlar mevcut bağlantıyı gösterir. Araçta görüntüyü ve sesi görerek/duyarak kontrol etmek ayrıca gerekir.",
                "These checks report current connection state. Confirm the picture and sound in the actual car separately."), color = MolaMuted, fontSize = 12.sp)
            checkedAt?.let { Text(S.t2("Son sunucu kontrolü: ", "Last server check: ") + it, color = MolaMuted, fontSize = 11.sp) }
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString("Hermes Mola v7\nAndroid=${android.os.Build.VERSION.RELEASE}\nAndroidAuto=${prerequisites.autoVersion ?: "missing"}\nInstaller=${prerequisites.installer ?: "unknown"}\n$serverLine\n$carLine\n$hostLine\nMicrophone=$microphone\nProjection=$projection"))
                copied = true
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (copied) S.t2("Tanı özeti kopyalandı", "Diagnostic summary copied") else S.t2("Tanı özetini kopyala", "Copy diagnostic summary"), color = MolaTeal)
            }
        }
        MolaGuideCard {
            Text(S.t2("Telefonun tüm ekranı ayrı bir yol.", "Sharing the whole phone screen is separate."), color = MolaText, fontWeight = FontWeight.SemiBold)
            Text(S.t2("Mola paneli için ekran kaydı izni gerekmez. Telefon ekranını veya başka uygulamaları yansıtmak istersen Android'in MediaProjection paylaşım izni ayrıca verilir. Araçtan dokunma için erişilebilirlik izni de gerekir.",
                "Mola's own panel needs no screen-capture permission. Mirroring your phone or other apps requires Android's separate MediaProjection sharing consent. Remote touch also requires accessibility permission."), color = MolaMuted, fontSize = 13.sp)
            Text(if (projection == ScreenMirror.Status.NoPermission) S.t2("Telefon ekranı paylaşımı şu anda kapalı.", "Phone screen sharing is currently off.")
                else S.t2("Telefon ekranı paylaşım izni açık; bu, Mola panelinden bağımsızdır.", "Phone screen sharing consent is active; it is independent of Mola."), color = MolaAmber, fontSize = 12.sp)
            TextButton(onClick = onOpenSettings) { Text(S.t2("Bağlantı ve ses ayarlarını aç", "Open connection and audio settings"), color = MolaTeal) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable private fun MolaGuideCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(MolaCard, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable private fun MolaRouteStep(number: Int, title: String, detail: String, current: Boolean, complete: Boolean) {
    Row(Modifier.fillMaxWidth().border(1.dp, if (current) MolaTeal.copy(alpha = .6f) else MolaMuted.copy(alpha = .15f), RoundedCornerShape(16.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (complete) Icon(Icons.Default.CheckCircle, null, tint = MolaTeal, modifier = Modifier.size(28.dp))
        else Text("0$number", color = if (current) MolaTeal else MolaMuted, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = MolaText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MolaMuted, fontSize = 13.sp)
        }
    }
}

@Composable private fun MolaCheck(label: String, detail: String, ready: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = MolaMuted, fontSize = 11.sp)
        Text((if (ready) "✓  " else "•  ") + detail, color = if (ready) MolaTeal else MolaAmber, fontSize = 13.sp)
    }
}
