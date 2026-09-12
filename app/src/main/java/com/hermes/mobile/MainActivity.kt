package com.hermes.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.mobile.ui.CameraScreen
import com.hermes.mobile.ui.ChatScreen
import com.hermes.mobile.ui.ArenaScreen
import com.hermes.mobile.ui.CommandPalette
import com.hermes.mobile.ui.ConnectScreen
import com.hermes.mobile.ui.FileRef
import com.hermes.mobile.ui.downloadUrl
import com.hermes.mobile.ui.DrivingScreen
import com.hermes.mobile.ui.HomeScreen
import com.hermes.mobile.ui.LiveSessionsScreen
import com.hermes.mobile.ui.LiveVoiceSheet
import com.hermes.mobile.ui.ModelPickerSheet
import com.hermes.mobile.ui.PanelScreen
import com.hermes.mobile.ui.ProfileSheet
import com.hermes.mobile.ui.TerminalScreen
import com.hermes.mobile.ui.SessionDetailScreen
import com.hermes.mobile.ui.SessionsScreen
import com.hermes.mobile.ui.SessionRail
import com.hermes.mobile.ui.WorkScreen
import com.hermes.mobile.ui.SettingsScreen
import com.hermes.mobile.ui.ShareTargetScreen
import com.hermes.mobile.ui.theme.themeById
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.HermesTheme
import com.hermes.mobile.ui.Lang
import com.hermes.mobile.ui.LocalLang
import com.hermes.mobile.ui.S
import com.hermes.mobile.ui.serviceLang

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()
    private val liveViewModel: LiveSessionsViewModel by viewModels()
    private val voiceViewModel: LiveVoiceViewModel by viewModels()
    private val panelViewModel: PanelViewModel by viewModels()
    private val arenaViewModel: ArenaViewModel by viewModels()

    /** Sistem foto seçici — Android 13+ izin istemez. */
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(::attachImageFromUri) }

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::attachFileFromUri) }

    private val requestMic = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) chatViewModel.startDictation() }

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* izin verilince kullanıcı düğmeye yeniden basar */ }

    private val requestNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* sürüş kipi bildirimi için; reddedilse de servis çalışır */ }

    private fun ensureNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun ensureCameraPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestCamera.launch(Manifest.permission.CAMERA)
        return granted
    }

    private fun ensureMicPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestMic.launch(Manifest.permission.RECORD_AUDIO)
        return granted
    }

    /** Görseli data URL'e çevirip yükler — sunucunun beklediği biçim. */
    private fun attachImageFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val mime = contentResolver.getType(uri) ?: "image/png"
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Görsel okunamadı")
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:$mime;base64,$b64" to displayName(uri, "gorsel.png")
            }.onSuccess { (dataUrl, name) ->
                withContext(Dispatchers.Main) { chatViewModel.attachImage(dataUrl, name) }
            }
        }
    }

    private fun attachFileFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Dosya okunamadı")
                bytes to displayName(uri, "dosya.bin")
            }.onSuccess { (bytes, name) ->
                withContext(Dispatchers.Main) { chatViewModel.attachFile(bytes, name) }
            }
        }
    }

    private fun displayName(uri: Uri, fallback: String): String =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        } ?: uri.lastPathSegment ?: fallback

    /**
     * Ajanın ürettiği dosyayı sistem tarayıcısına/indiricisine açar.
     *
     * Sunucu `/api/files/download` ucunda tokeni sorgu parametresi olarak kabul
     * ediyor, böylece harici uygulamaya özel başlık geçirmek gerekmiyor.
     * ⚠️ Yol tam kodlanmalı (`/` dahil) — yarım kodlanmış yol 404 dönüyor.
     */
    private fun openServerFile(profile: com.hermes.mobile.data.ServerProfile?, ref: FileRef) {
        val p = profile ?: return
        val base = p.activeUrl ?: p.normalizedUrl
        if (base.isBlank() || p.token.isBlank()) return
        val url = downloadUrl(base, p.token, ref.path)
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    /**
     * Başka uygulamadan gelen paylaşımı karşılar (WhatsApp, Telegram, galeri,
     * tarayıcı…).
     *
     * Metin taslağa düşer; görsel/dosya doğrudan yüklenmeye başlar — kullanıcı
     * uygulamaya geldiğinde ek hazır olur, yalnız ne isteyeceğini yazar.
     */
    private fun handleShareIntent(intent: Intent?) {
        val i = intent ?: return
        // ShareProxyActivity'den gelen ekstraplar: hedef seçim ekranı açılır.
        if (i.getBooleanExtra(ShareProxyActivity.EXTRA_IS_SHARE, false)) {
            handleShareHandoff(i)
            return
        }
        when (i.action) {
            Intent.ACTION_SEND -> {
                i.getStringExtra(Intent.EXTRA_TEXT)?.let(chatViewModel::shareText)
                @Suppress("DEPRECATION")
                val uri = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                uri?.let { ingestShared(it, i.type) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val uris = i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                // Her ek ayrı ayrı yüklendiği için sıra korunuyor.
                uris.forEach { ingestShared(it, i.type) }
            }
        }
    }

    /**
     * ShareProxyActivity'den gelen paylaşım niyetini hedef seçim ekranıyla
     * karşılar. Kullanıcı oturum seçim sayfasında "Yeni konu" ya da son 10
     * oturumdan birini seçtikten sonra metin/dosya oraya düşer.
     */
    private fun handleShareHandoff(intent: Intent) {
        val sharedText = intent.getStringExtra(ShareProxyActivity.EXTRA_SHARED_TEXT)
        val sharedFile = intent.getStringExtra(ShareProxyActivity.EXTRA_SHARED_FILE)
        // Varsayılan hedef: yeni konu. Kullanıcı ShareTargetScreen'de
        // değiştirebilir. Varsayılan hedefi belirle; UI oturum listesini
        // gösterir.
        chatViewModel.pickShareTarget(
            sessionId = null,
            sharedText = sharedText,
            sharedFile = sharedFile,
        )
        // Dosya adı+boyutu ekleme bilgisi [HermesClient.uploadFile]
        // gerçekte çalıştığında paylaşım hedefinin bir parçasıdır; burada
        // yalnız taşınır, yüklemesi ChatViewModel tarafında yapılır.
    }

    /**
     * Başlatma kısayolları ve asistan hareketi.
     *
     * `ACTION_ASSIST` / `ACTION_VOICE_COMMAND`: Hermes, Ayarlar → Varsayılan
     * uygulamalar → Dijital asistan listesine girer; ana ekrandan basılı
     * tutunca doğrudan canlı ses açılır. Kısayollar (`hermes_action` ekstrası)
     * uygulama simgesine uzun basınca çıkar.
     */
    private fun handleActionIntent(intent: Intent?) {
        val i = intent ?: return
        val action = when {
            i.action == Intent.ACTION_ASSIST || i.action == "android.intent.action.VOICE_COMMAND" ->
                "voice"
            else -> i.getStringExtra("hermes_action")
        } ?: return
        i.removeExtra("hermes_action")
        chatViewModel.pendingAction.value = action
    }

    private fun ingestShared(uri: Uri, type: String?) {
        val mime = type ?: contentResolver.getType(uri)
        if (mime?.startsWith("image/") == true) attachImageFromUri(uri)
        else attachFileFromUri(uri)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        handleActionIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        handleActionIntent(intent)
        chatViewModel.shizuku.attach()
        // Canlı ses aynı köprüyü kullansın; iki ayrı dinleyici kaydı gereksiz.
        voiceViewModel.shizuku = chatViewModel.shizuku
        setContent {
            val settings by viewModel.settingsStore.settings.collectAsStateWithLifecycle()
            val customThemes by viewModel.settingsStore.customThemes.collectAsStateWithLifecycle()

            // Dil: ayarda sabitlenmediyse cihaz diline uy.
            val lang = when (settings.uiLang) {
                "en" -> Lang.EN
                "tr" -> Lang.TR
                else -> if (resources.configuration.locales[0].language == "tr") Lang.TR else Lang.EN
            }
            // Compose dışı katman (PhoneTools sonuç metinleri) burayı okuyor.
            serviceLang = lang
            // Ayni sekilde Compose disi bir global: goruntu katmanindaki
            // maskeleme PanelScreen/HomeScreen icinden dogrudan okunuyor.
            com.hermes.mobile.data.DemoMask.enabled = settings.demoMask
            // Ajan kanalı ayara bağlı: açıksa servis ayakta, kapalıysa
            // bağlantı kapanıyor. Kullanıcı anahtarı kapattığında kanalın
            // gerçekten kapanması gerekiyor, yalnız araç listesinin boşalması
            // yetmez.
            if (settings.agentMayUsePhone) {
                com.hermes.mobile.data.PhoneBridgeService.start(this)
            } else {
                com.hermes.mobile.data.PhoneBridgeService.stop(this)
            }

            HermesTheme(
                palette = themeById(settings.themeId, customThemes),
                fontScale = settings.fontScale,
            ) {
              androidx.compose.runtime.CompositionLocalProvider(LocalLang provides lang) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val detail by viewModel.detail.collectAsStateWithLifecycle()
                val chat by chatViewModel.state.collectAsStateWithLifecycle()

                val gateway by chatViewModel.gateway.collectAsStateWithLifecycle()
                val live by liveViewModel.state.collectAsStateWithLifecycle()

                // Aktif profil değiştiğinde sohbet soketi yeniden kurulur.
                LaunchedEffect(state.active?.id, state.active?.token) {
                    // Kayıtlı oturum bu profile aitse bağlantı kurulunca geri dön.
                    val saved = settings.lastSession.split("|", limit = 2)
                    chatViewModel.restoreSessionId =
                        if (saved.size == 2 && saved[0] == state.active?.id && saved[1].isNotBlank())
                            saved[1] else null
                    chatViewModel.bind(state.active)
                    voiceViewModel.bind(state.active)
                    panelViewModel.bind(state.active)
                }

                // Canlı-oturum ekranı sohbetle aynı soketi paylaşır.
                // Ayarlar değişince canlı ses bir sonraki oturumda yeni
                // kişiliği/sesi kullanır.
                LaunchedEffect(settings) {
                    voiceViewModel.settings = settings
                    chatViewModel.preferredModel = settings.lastModel
                    // Bot (profil) ataması kalıcı — son kullanılan çip
                    // açılışta geri yüklenir.
                    chatViewModel.selectedProfileValue = settings.selectedProfile
                        .ifBlank { null }
                    panelViewModel.sparkUrl = settings.sparkUrl
                }

                // Model denenip başarısız olursa kalıcı olarak "bozuk" işaretlenir;
                // seçici bir daha göstermez.
                LaunchedEffect(Unit) {
                    chatViewModel.onSessionChanged = { sid ->
                        viewModel.settingsStore.update { st ->
                            val pid = state.active?.id
                            st.copy(
                                lastSession = if (sid.isBlank() || pid == null) "" else "$pid|$sid"
                            )
                        }
                    }
                    chatViewModel.onModelBroken = { key ->
                        viewModel.settingsStore.update {
                            it.copy(brokenModels = it.brokenModels + key)
                        }
                    }
                    chatViewModel.onModelChosen = { prov, model ->
                        viewModel.settingsStore.update { it.copy(lastModel = "$prov|$model") }
                    }
                    // Bot (profil) ataması kalıcı — çipten seçilen profil
                    // ayarlara yazılır; sonraki açılışta geri yüklenir.
                    chatViewModel.onProfileChipSelected = { name ->
                        viewModel.settingsStore.update {
                            it.copy(selectedProfile = name.orEmpty())
                        }
                    }
                }

                LaunchedEffect(gateway) {
                    liveViewModel.bind(gateway)
                    viewModel.bindGateway(gateway)
                    // Bot (profil) ataması: profilleri sohbet açılır açılmaz
                    // çek — yalnız ProfilSheet açıldığında çekilmesin.
                    viewModel.loadProfiles()
                }

                HermesApp(
                    onFinish = { finish() },
                    state = state,
                    detail = detail,
                    chat = chat,
                    live = live,
                    viewModel = viewModel,
                    chatViewModel = chatViewModel,
                    liveViewModel = liveViewModel,
                    voiceViewModel = voiceViewModel,
                    panelViewModel = panelViewModel,
                    arenaViewModel = arenaViewModel,
                    onPickImage = { pickImage.launch("image/*") },
                    onPickFile = { pickFile.launch(arrayOf("*/*")) },
                    onNeedMic = ::ensureMicPermission,
                    onNeedCamera = ::ensureCameraPermission,
                    onNeedNotification = ::ensureNotificationPermission,
                    onOpenFile = { ref -> openServerFile(state.active, ref) },
                    settings = settings,
                    customThemes = customThemes,
                )
              }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        com.hermes.mobile.data.Notifier.appVisible = true
        // Shizuku her yeniden başlatmada elle açılıyor; öne gelince durumu
        // tazelemek gerekiyor, yoksa "çalışmıyor" yazılı kalıyor.
        chatViewModel.shizuku.refresh()
    }

    override fun onStop() {
        super.onStop()
        com.hermes.mobile.data.Notifier.appVisible = false
    }

    override fun onDestroy() {
        chatViewModel.shizuku.detach()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAll()
        // Arka plandayken kaçırılmış yanıt varsa tamamla.
        chatViewModel.syncPending()
    }
}

/**
 * Alt çubuk — dört sekme.
 *
 * Önce yedi taneydi (Sohbet · Canlı · Geçmiş · Durum · Pano · Terminal ·
 * Ayarlar) ve "derli toplu değil" geri bildirimini aldı. Birleştirmeler:
 * Canlı + Geçmiş → **Oturumlar** (ikisi de oturum listesi), Durum ve Terminal
 * → **Pano** içinde bölüm (ikisi de sunucu yönetimi). Böylece etiketler tek
 * satıra sığıyor ve dokunma alanları büyüyor.
 */
private enum class Tab(val icon: ImageVector) {
    Chat(Icons.AutoMirrored.Filled.Message),
    Work(Icons.AutoMirrored.Filled.List),
    Panel(Icons.Default.GridView),
    Arena(Icons.Default.Bolt),
    Settings(Icons.Default.Tune),
}

/** Etiketler dile göre çözülüyor, o yüzden enum sabiti olamaz. */
@Composable
private fun Tab.label(): String = when (this) {
    Tab.Chat -> S.tabChat
    Tab.Work -> S.tabSessions
    Tab.Panel -> S.tabPanel
    Tab.Arena -> S.tabArena
    Tab.Settings -> S.tabSettings
}

@Composable
private fun HermesApp(
    onFinish: () -> Unit,
    state: AppState,
    detail: SessionDetailState?,
    chat: ChatState,
    live: LiveState,
    viewModel: AppViewModel,
    chatViewModel: ChatViewModel,
    liveViewModel: LiveSessionsViewModel,
    voiceViewModel: LiveVoiceViewModel,
    panelViewModel: PanelViewModel,
    arenaViewModel: ArenaViewModel,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onNeedMic: () -> Boolean,
    onNeedCamera: () -> Boolean,
    onNeedNotification: () -> Unit,
    onOpenFile: (FileRef) -> Unit,
    settings: com.hermes.mobile.data.AppSettings,
    customThemes: List<com.hermes.mobile.ui.theme.HermesPalette>,
) {
    var tab by remember { mutableStateOf(Tab.Chat) }
    val panel by panelViewModel.state.collectAsStateWithLifecycle()
    val shizukuState by chatViewModel.shizuku.state.collectAsStateWithLifecycle()
    val sharedText by chatViewModel.sharedText.collectAsStateWithLifecycle()
    // Paylaşım hedefi seçim ekranı (ShareProxyActivity'den gelir).
    val shareTargetVisible by chatViewModel.shareTargetVisible.collectAsStateWithLifecycle()
    val shareTextSnippet by chatViewModel.pendingShareText.collectAsStateWithLifecycle()
    val shareFileNote by chatViewModel.pendingShareFile.collectAsStateWithLifecycle()

    // Paylaşım geldiğinde hangi sekmede olursak olalım sohbete geç.
    LaunchedEffect(sharedText) {
        if (sharedText != null) tab = Tab.Chat
    }

    var modelSheet by remember { mutableStateOf(false) }
    var commandSheet by remember { mutableStateOf(false) }
    var reasoningSheet by remember { mutableStateOf(false) }
    val reasoningStatus by chatViewModel.reasoningStatus.collectAsStateWithLifecycle()
    var voiceSheet by remember { mutableStateOf(false) }
    var cameraFullScreen by remember { mutableStateOf(false) }
    var exitDialog by remember { mutableStateOf(false) }
    /** Sohbet bir oturum listesinden mi açıldı? Geri tuşu o zaman çıkış
     *  yerine listeye döndürmeli (kullanıcı isteği, 2026-09-11). */
    var backToSessions by remember { mutableStateOf(false) }

    // Kısayol / asistan hareketi: hangi ekranda olursak olalım isteneni aç.
    val pendingAction by chatViewModel.pendingAction.collectAsStateWithLifecycle()
    LaunchedEffect(pendingAction) {
        when (pendingAction) {
            "voice" -> { tab = Tab.Chat; voiceSheet = true }
            "driving" -> {
                onNeedNotification()
                voiceViewModel.startDriving()
            }
            "new" -> { tab = Tab.Chat; backToSessions = false; chatViewModel.newSession() }
            "camera" -> { tab = Tab.Chat; if (onNeedCamera()) cameraFullScreen = true }
        }
        if (pendingAction != null) chatViewModel.pendingAction.value = null
    }
    var profileSheet by remember { mutableStateOf(false) }
    val hermesProfiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeHermesProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val profilesLoading by viewModel.profilesLoading.collectAsStateWithLifecycle()
    val terminalLines by chatViewModel.terminal.collectAsStateWithLifecycle()
    val terminalBusy by chatViewModel.terminalBusy.collectAsStateWithLifecycle()
    val selectedProfile by chatViewModel.selectedProfile.collectAsStateWithLifecycle()
    val sessionProfile by chatViewModel.sessionProfile.collectAsStateWithLifecycle()
    val voice by voiceViewModel.state.collectAsStateWithLifecycle()
    val camera by voiceViewModel.cameraState.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val providers by chatViewModel.modelProviders.collectAsStateWithLifecycle()
    val modelsLoading by chatViewModel.modelsLoading.collectAsStateWithLifecycle()
    val savedPrompts by viewModel.savedPrompts.collectAsStateWithLifecycle()

    // Oturum detayı açıkken donanım geri tuşu listeye döner.
    // (Sıra önemli: Compose'da son kayıtlı BackHandler kazanır — bu blok
    // kök-çıkış handler'ından ÖNCE kayıtlı kalmalı. 2026-09-11)
    BackHandler(enabled = cameraFullScreen) { cameraFullScreen = false }
    BackHandler(enabled = panel.section != null) { panelViewModel.close() }
    BackHandler(enabled = detail != null) { viewModel.closeSession() }
    // Bir listeden açılmış sohbet: geri, listeye dönsün — uygulamadan çıkmasın.
    // (Kök-çıkış handler'ından SONRA kayıtlı olmalı: Compose'ta son kaydedilen
    // enabled handler kazanır.)
    BackHandler(enabled = backToSessions && detail == null && !cameraFullScreen && panel.section == null) {
        backToSessions = false
        tab = Tab.Work
    }
    // Kök ekranda geri = çıkış; yanlışlıkla basınca sohbet kaybolmasın diye
    // (ayarlardan kapatılabilir) önce sorulur.
    BackHandler(
        enabled = settings.confirmExit && detail == null && !cameraFullScreen &&
            panel.section == null && !voice.driving && !backToSessions
    ) { exitDialog = true }

    if (exitDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { exitDialog = false },
            containerColor = HermesColors.Surface,
            title = { Text("Çıkılsın mı?", color = HermesColors.TextPrimary) },
            text = {
                Text(
                    "Sohbet oturumun kayıtlı — tekrar açınca kaldığın yerden devam edersin.",
                    color = HermesColors.TextMuted,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { exitDialog = false; onFinish() }
                ) { Text("Çık", color = HermesColors.Danger) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { exitDialog = false }
                ) { Text("Kal", color = HermesColors.TextSecondary) }
            },
        )
    }
    // Tam ekran kipler geri tuşuyla kapansın — sistem davranışı bu.
    // (camera/panel handler'ları yukarıda kök-çıkıştan önce kayıtlı; burada
    // tekrar kaydedilmemeli — son kaydeden kazanır kuralı çıkışı öne alırdı.)

    Scaffold(
        containerColor = HermesColors.Background,
        bottomBar = {
            // Sürüş kipinde sekme çubuğu da gizlenir — ekranda yalnız
            // büyük durum göstergesi kalmalı.
            if (detail == null && !voice.driving && !cameraFullScreen) {
                NavigationBar(containerColor = HermesColors.Surface) {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Icon(entry.icon, contentDescription = entry.label()) },
                            label = {
                                Text(entry.label(), fontSize = 11.sp, maxLines = 1, softWrap = false)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = HermesColors.Background,
                                selectedTextColor = HermesColors.Midground,
                                indicatorColor = HermesColors.Midground,
                                unselectedIconColor = HermesColors.TextFaint,
                                unselectedTextColor = HermesColors.TextFaint,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        // Sürüş kipi tüm arayüzün üstünde tam ekran açılır; sürerken sekme
        // çubuğu bile görünmemeli.
        // Kamera da sürüş kipi gibi tam ekran. Önizleme daha önce canlı ses
        // sayfasının içindeydi ve gerçek cihazda siyah kalıyordu — gerekçe
        // CameraScreen'in başında.
        if (cameraFullScreen) {
            CameraScreen(
                voice = voice,
                camera = camera,
                onStartCamera = { pv -> voiceViewModel.startCamera(lifecycleOwner, pv) },
                onStopCamera = voiceViewModel::stopCamera,
                onSwitchCamera = voiceViewModel::switchCamera,
                onStartVoice = voiceViewModel::start,
                onStopVoice = voiceViewModel::stop,
                onClose = { cameraFullScreen = false },
            )
            return@Scaffold
        }

        if (voice.driving) {
            DrivingScreen(
                state = voice,
                onToggle = {
                    if (voice.isRunning) voiceViewModel.stop() else voiceViewModel.start()
                },
                onCycleRoute = voiceViewModel::cycleAudioRoute,
                onExit = voiceViewModel::stopDriving,
            )
            return@Scaffold
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(HermesColors.Background)
                .padding(innerPadding)
        ) {
            if (detail != null) {
                SessionDetailScreen(detail, onBack = viewModel::closeSession)
            } else if (shareTargetVisible) {
                ShareTargetScreen(
                    textSnippet = shareTextSnippet,
                    fileNote = shareFileNote,
                    recentSessions = state.sessions.take(10),
                    onNewTopic = {
                        chatViewModel.pickShareTarget(
                            sessionId = null,
                            sharedText = shareTextSnippet,
                            sharedFile = shareFileNote,
                        )
                        chatViewModel.consumePendingShare()
                    },
                    onPickSession = { s ->
                        chatViewModel.pickShareTarget(
                            sessionId = s.id,
                            sharedText = shareTextSnippet,
                            sharedFile = shareFileNote,
                        )
                        chatViewModel.consumePendingShare()
                        chatViewModel.continueSession(
                            liveId = s.id,
                            dbId = s.id,
                            title = s.title.ifBlank { s.id },
                        )
                    },
                    onCancel = chatViewModel::consumePendingShare,
                )
            } else {
                when (tab) {
                    Tab.Chat -> Row(Modifier.fillMaxSize()) {
                        // Oturum rayı: panel açmadan oturum değişimi (C-4).
                        SessionRail(
                            currentSessionId = chat.sessionId,
                            live = live,
                            onNewChat = { backToSessions = false; chatViewModel.newSession() },
                            onSelect = { s ->
                                chatViewModel.continueSession(
                                    liveId = s.id,
                                    dbId = s.dbId,
                                    title = s.title.ifBlank { s.dbId },
                                )
                            },
                        )
                        ChatScreen(
                        sharedText = sharedText,
                        onSharedTextConsumed = chatViewModel::consumeSharedText,
                        state = chat,
                        speed = chatViewModel.speed,
                        onSend = chatViewModel::send,
                        onNewSession = chatViewModel::newSession,
                        onStop = chatViewModel::stopGeneration,
                        onDictate = { if (onNeedMic()) chatViewModel.startDictation() },
                        onToggleHandsFree = {
                            // Kulaklık düğmesi artık Gemini Live'ı açıyor —
                            // eski STT/TTS döngüsünden çok daha iyi.
                            if (onNeedMic()) voiceSheet = true
                        },
                        onPickImage = onPickImage,
                        onPickFile = onPickFile,
                        onRemoveAttachment = chatViewModel::removeAttachment,
                        onOpenModelPicker = {
                            chatViewModel.loadModels()
                            modelSheet = true
                        },
                        onSuggestion = chatViewModel::send,
                        onApproval = { key, ok -> chatViewModel.answerApproval(key, ok) },
                        onOpenCommands = { commandSheet = true },
                        onOpenProfiles = {
                            viewModel.loadProfiles()
                            profileSheet = true
                        },
                        onOpenReasoning = {
                            reasoningSheet = true
                            // Sunucunun bildirdiği aktif çabayı öne al — panel
                            // yerel tahmin UYGULAMAZ (SAF), sunucu okuma belirler.
                            chatViewModel.refreshReasoning()
                        },
                        onReasoningLevel = { lvl ->
                            viewModel.settingsStore.update { st -> st.copy(reasoningLevel = lvl) }
                            chatViewModel.setReasoningEffort(lvl)
                        },
                        reasoningLevel = reasoningStatus.effort ?: settings.reasoningLevel,
                        showLiveThinking = settings.showLiveThinking,
                        onShowLiveThinking = { on ->
                            viewModel.settingsStore.update { st -> st.copy(showLiveThinking = on) }
                        },
                        activeProfileName = activeHermesProfile,
                        onOpenFile = onOpenFile,
                        savedPrompts = savedPrompts,
                        onSavePrompt = viewModel::kaydetPrompt,
                        onUpdatePrompt = viewModel::guncellePrompt,
                        onDeletePrompt = viewModel::silPrompt,
                        onImprovePrompt = chatViewModel::improvePrompt,
                        profiles = hermesProfiles,
                        selectedProfile = selectedProfile.orEmpty(),
                        currentProfile = sessionProfile,
                        onProfileChipClick = { name ->
                            chatViewModel.selectedProfileValue =
                                name.takeIf { it != com.hermes.mobile.ui.ROUTER_CHIP }
                        },
                        )
                    }
                    Tab.Work -> WorkScreen(
                        state = state,
                        live = live,
                        onRefreshLive = { liveViewModel.refresh() },
                        onIntervene = liveViewModel::openIntervention,
                        onInterrupt = liveViewModel::interrupt,
                        onOpenLive = { session ->
                            // Döküm REST'ten gelir → veritabanı kimliği gerekir,
                            // gateway'in süreç içi `id`si değil (404 sebebi buydu).
                            viewModel.openSessionById(session.dbId, session.title, liveId = session.id)
                        },
                        onContinueLive = { session ->
                            chatViewModel.continueSession(
                                liveId = session.id,
                                dbId = session.dbId,
                                title = session.title.ifBlank { session.dbId },
                            )
                            backToSessions = true
                            tab = Tab.Chat
                        },
                        onCloseIntervention = liveViewModel::closeIntervention,
                        onSubmitIntervention = liveViewModel::intervene,
                        onOpenPast = viewModel::openSession,
                        onContinuePast = { s ->
                            chatViewModel.continueSession(
                                liveId = s.id,
                                dbId = s.id,
                                title = s.title,
                            )
                            backToSessions = true
                            tab = Tab.Chat
                        },
                        onRefreshSessions = viewModel::refreshSessions,
                        onTogglePin = viewModel::togglePin,
                        onSetArchived = viewModel::setArchived,
                        onRenamePast = viewModel::renameSession,
                        onDeletePast = viewModel::deleteSession,
                        onStopPast = { s -> viewModel.stopSession(s.id) },
                        onBudaPast = { s -> viewModel.compressSession(s.id) },
                    )
                    Tab.Panel -> PanelScreen(
                        state = panel,
                        onOpen = panelViewModel::open,
                        onClose = panelViewModel::close,
                        onRefresh = panelViewModel::refresh,
                        onBrowse = panelViewModel::browse,
                        onSelectLogFile = panelViewModel::selectLogFile,
                        onOpenFile = onOpenFile,
                        onToggleCron = panelViewModel::toggleCron,
                        onSetCronSchedule = panelViewModel::setCronSchedule,
                        onTriggerCron = panelViewModel::triggerCron,
                        sparkEnabled = settings.sparkEnabled,
                        statusContent = {
                            HomeScreen(
                                state = state,
                                onGatewayAction = viewModel::gatewayAction,
                                onSelectProfile = viewModel::selectProfile,
                                onSaveProfile = viewModel::saveProfile,
                                onDeleteProfile = viewModel::deleteProfile,
                                onRefresh = viewModel::refreshAll,
                            )
                        },
                        terminalContent = {
                            TerminalScreen(
                                lines = terminalLines,
                                busy = terminalBusy,
                                connected = chat.connection is com.hermes.mobile.data.ConnectionState.Open,
                                onRun = chatViewModel::runTerminal,
                                onClear = chatViewModel::clearTerminal,
                            )
                        },
                    )
                    Tab.Arena -> ArenaScreen(
                        arenaViewModel = arenaViewModel,
                        gateway = chatViewModel.gateway.value,
                    )
                    Tab.Settings -> SettingsScreen(
                        shizukuState = shizukuState,
                        onRequestShizuku = chatViewModel.shizuku::requestPermission,
                        settings = settings,
                        customThemes = customThemes,
                        onUpdate = viewModel.settingsStore::update,
                        onSaveTheme = viewModel.settingsStore::saveTheme,
                        onDeleteTheme = viewModel.settingsStore::deleteTheme,
                        onImportTheme = viewModel.settingsStore::importTheme,
                        onExportTheme = viewModel.settingsStore::exportTheme,
                        activeProfile = state.active,
                    )
                }
            }
        }

        if (profileSheet) {
            ProfileSheet(
                profiles = hermesProfiles,
                activeName = activeHermesProfile,
                loading = profilesLoading,
                onSelect = { p ->
                    viewModel.selectHermesProfile(p.name) { name ->
                        // Yeni oturumlar bu profilin evinde açılsın; mevcut
                        // oturum eski profilde kalır, o yüzden sıfırlanır.
                        chatViewModel.activeProfile = name
                        chatViewModel.newSession()
                    }
                    profileSheet = false
                },
                onDismiss = { profileSheet = false },
            )
        }

        if (voiceSheet) {
            LiveVoiceSheet(
                state = voice,
                camera = camera,
                onStart = voiceViewModel::start,
                onStop = voiceViewModel::stop,
                onDismiss = { voiceSheet = false },
                onOpenCamera = { voiceSheet = false; cameraFullScreen = true },
                onNeedCameraPermission = onNeedCamera,
                onCycleRoute = voiceViewModel::cycleAudioRoute,
                onStartDriving = {
                    onNeedNotification()
                    voiceSheet = false
                    voiceViewModel.startDriving()
                },
            )
        }

        if (commandSheet) {
            CommandPalette(
                onRun = chatViewModel::runSlash,
                onDismiss = { commandSheet = false },
            )
        }

        if (modelSheet) {
            ModelPickerSheet(
                providers = providers,
                currentModel = chat.currentModel,
                loading = modelsLoading,
                brokenModels = settings.brokenModels,
                hiddenModels = settings.hiddenModels,
                pinnedModels = settings.pinnedModels,
                modelUsage = settings.modelUsage,
                showBroken = settings.showBrokenModels,
                onTogglePin = { key ->
                    viewModel.settingsStore.update { st ->
                        st.copy(
                            pinnedModels = if (key in st.pinnedModels)
                                st.pinnedModels - key else st.pinnedModels + key
                        )
                    }
                },
                onToggleHidden = { key ->
                    viewModel.settingsStore.update { st ->
                        // Elle geri getirmek otomatik "bozuk" damgasını da siler:
                        // kullanıcı "bu çalışıyor" diyorsa ona güveniyoruz.
                        if (key in st.hiddenModels || key in st.brokenModels)
                            st.copy(
                                hiddenModels = st.hiddenModels - key,
                                brokenModels = st.brokenModels - key,
                            )
                        else st.copy(hiddenModels = st.hiddenModels + key)
                    }
                },
                onToggleShowBroken = {
                    viewModel.settingsStore.update {
                        it.copy(showBrokenModels = !it.showBrokenModels)
                    }
                },
                onSelect = { provider, model, persist ->
                    chatViewModel.selectModel(provider, model, persist)
                    // Sıklık sayacı: "sık kullanılan" bölümü buradan besleniyor.
                    viewModel.settingsStore.update { st ->
                        val key = "$provider/$model"
                        st.copy(modelUsage = st.modelUsage + (key to (st.modelUsage[key] ?: 0) + 1))
                    }
                    modelSheet = false
                },
                onDismiss = { modelSheet = false },
            )
        }
    }
}
