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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import com.hermes.mobile.ui.LiveVoiceSheet
import com.hermes.mobile.ui.ModelPickerSheet
import com.hermes.mobile.ui.PanelScreen
import com.hermes.mobile.ui.ProfileSheet
import com.hermes.mobile.ui.TerminalScreen
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.hermes.mobile.ui.SessionDetailScreen
import com.hermes.mobile.ui.SessionDrawerContent
import com.hermes.mobile.ui.SessionStatsStrip
import com.hermes.mobile.ui.drawerListItems
import com.hermes.mobile.ui.drawerRows
import com.hermes.mobile.ui.lastSessionToRestore
import com.hermes.mobile.ui.SettingsScreen
import com.hermes.mobile.data.CrashGuard
import com.hermes.mobile.data.AssistantModeLogic
import com.hermes.mobile.data.AssistantRole
import com.hermes.mobile.data.toVoicePrefs
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.ShareHandoff
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.ui.liveSessionTitle
import com.hermes.mobile.ui.readableTitle
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

    /**
     * Tur-13: asistan modu için mikrofon izni — **sessiz** istek.
     *
     * [requestMic]'ten ayrı: onun geri çağrısı izin verilince dikte
     * oturumunu kendiliğinden başlatıyor. Asistan hareketinde istenen şey
     * yalnız iznin sorulması; kaydı kullanıcı bas-konuş ile başlatır
     * ("otomatik kayda başlama" şartı).
     */
    private val requestMicQuiet = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* izin verilince kullanıcı bas-konuş yapar */ }

    /**
     * Tur-13: `ROLE_ASSISTANT` sistem diyaloğu.
     *
     * Sonuç ne olursa olsun rol satırı TAZELENİR — kullanıcı diyaloğu
     * reddettiyse satır hâlâ doğruyu ("Şu an: Google") göstermeli.
     *
     * **Android 12+ gerçeği:** asistan rolü sistem tarafından yönetiliyor;
     * `RequestRoleActivity` ekrana gelip ANINDA kapanıyor
     * (`E RequestRoleActivity: Role is not requestable: android.app.role.ASSISTANT`
     * — emülatörde ölçüldü). Rol bizim olmadıysa sessizce dönmek kullanıcıyı
     * kilitler; Ayarlar → Varsayılan uygulamalar açılır ve adımlar gösterilir.
     */
    private val requestAssistantRole = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshAssistantRole()
        if (!AssistantRole.selfHolds(this)) {
            DiagLog.i("asistan", "rol istegi tamamlanmadi - Ayarlar yonlendirmesi")
            openDefaultAppsSettings()
        }
    }

    /** Rol durumu — Compose tarafı bu akışı okur (Ayarlar → Telefon asistanı). */
    private val assistantRole = kotlinx.coroutines.flow.MutableStateFlow(
        AssistantModeLogic.RoleStatus(AssistantModeLogic.RoleState.None)
    )

    private fun refreshAssistantRole() {
        assistantRole.value = AssistantRole.status(this)
    }

    /** "Hermes'i varsayılan asistan yap" — sistem diyaloğu, yoksa Ayarlar. */
    private fun makeDefaultAssistant() {
        val intent = AssistantRole.requestIntent(this)
        if (intent != null) {
            runCatching { requestAssistantRole.launch(intent) }
                .onFailure { openDefaultAppsSettings() }
            return
        }
        // Rol API'si yok/rol sunulmuyor: kullanıcıyı Ayarlar'a götür.
        openDefaultAppsSettings()
    }

    private fun openDefaultAppsSettings() {
        val opened = runCatching { startActivity(AssistantRole.settingsIntent()) }.isSuccess
        if (!opened) runCatching { startActivity(AssistantRole.fallbackSettingsIntent()) }
        android.widget.Toast.makeText(
            this,
            com.hermes.mobile.ui.tr(
                "Ayarlar → Varsayılan uygulamalar → Dijital asistan → Hermes Asistan",
                "Settings → Default apps → Digital assistant → Hermes Asistan",
            ),
            android.widget.Toast.LENGTH_LONG,
        ).show()
    }

    /**
     * Asistan modu: mikrofon izni (sessiz) + mod açılışı.
     *
     * Kayıt BAŞLATILMAZ — kullanıcı bas-konuş yapar. Ekranı hangi sekmenin
     * açacağı çağıranın işi (sekme durumu HermesApp'in içinde).
     */
    private fun enterAssistantMode() {
        if (AssistantModeLogic.askMicOnEnter(hasMicPermission())) {
            requestMicQuiet.launch(Manifest.permission.RECORD_AUDIO)
        }
        chatViewModel.enterAssistantMode()
    }

    private fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

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
     * Dış paylaşım girişi YALNIZ ShareProxyActivity + nonce el sıkışmasıdır
     * (denetmen YENİ-3 / öneri #3 kapanışı).
     *
     * Eski `when (i.action) ACTION_SEND` dalı TAMAMEN kaldırıldı: MainActivity
     * exported=true olduğundan explicit component intent (`am start -n
     * .../MainActivity -a SEND`) o dalı nonce'suz çalıştırabiliyordu — taslağa
     * metin enjeksiyonu + hedef seçimi olmadan EXTRA_STREAM'dan anında
     * readBytes yükleme (onaysız yükleme + OOM yüzeyi). Artık nonce'suz hiçbir
     * niyet işlem görmez; her dosya/metin paylaşımı vekil → staging → hedef
     * seçimi akışından geçer.
     */
    private fun handleShareIntent(intent: Intent?) {
        val i = intent ?: return
        // Kabul koşulu sözleşme gereği: is-share + vekilin rastgele nonce
        // token'ı (ShareHandoff.accepted). Vekil her seferinde token basar;
        // dış uygulama bilemez → enjeksiyon kapalı.
        if (ShareHandoff.accepted(
                isShare = i.getBooleanExtra(ShareHandoff.EXTRA_IS_SHARE, false),
                token = i.getStringExtra(ShareHandoff.EXTRA_SHARE_TOKEN),
            )
        ) {
            handleShareHandoff(i)
        } else if (i.action == Intent.ACTION_SEND ||
            i.action == "android.intent.action.SEND_MULTIPLE"
        ) {
            // Görünür gözlem (MS-2 sertleştirme): dışarıdan vektörü ATLAYARAK
            // explicit MainActivity intent'i atan her giriş REDDEDİLİR — logda
            // izi kalsın (sessiz düşürmek denetimi zorlaştırırdı).
            DiagLog.w("ShareHandoff", "reddedildi: nonce'suz ${i.action} doğrudan MainActivity — paylaşım yalnız ShareProxyActivity üzerinden")
        }
        // SEND_MULTIPLE bilinçli olarak YOK (denetmen önerisi #4):
        // MainActivity'nin MULTIPLE filtresi kaldırıldı, vektörde de
        // açılmadı — çoklu galeri paylaşımı menüde görünmez. Çoklu
        // destek istenirse vekile SEND_MULTIPLE + her URI için staging
        // eklenir; tek akış bilinçli tercih.
    }

    /**
     * ShareProxyActivity'den gelen paylaşım niyetini hedef seçim ekranıyla
     * karşılar. Kullanıcı oturum seçim sayfasında "Yeni konu" ya da son 10
     * oturumdan birini seçtikten sonra metin/dosya oraya düşer.
     *
     * [ShareHandoff.EXTRA_STAGED_FILE] vekilin cache'e aldığı kopyanın yoludur;
     * hedef seçilince [ChatViewModel.attachShareFile] ile GERÇEKTEN yüklenir
     * (HIGH-1 teli — yalnız etiket taşınmıyor).
     */
    private fun handleShareHandoff(intent: Intent) {
        val sharedText = intent.getStringExtra(ShareHandoff.EXTRA_SHARED_TEXT)
        val sharedFile = intent.getStringExtra(ShareHandoff.EXTRA_SHARED_FILE)
        val stagedPath = intent.getStringExtra(ShareHandoff.EXTRA_STAGED_FILE)
        // Varsayılan hedef: yeni konu. Kullanıcı ShareTargetScreen'de
        // değiştirebilir.
        chatViewModel.pickShareTarget(
            sessionId = null,
            sharedText = sharedText,
            sharedFile = sharedFile,
            stagedPath = stagedPath,
        )
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
            // Tur-13: asistan hareketi artık CANLI SES (Gemini Live) değil,
            // asistan modu. Gerekçe: kullanıcı telefonun asistan uygulamasının
            // yerine Hermes'i koymak istiyor ve o akış Google'a gitmemeli —
            // sesli yol whisper + kahya (voice_api). Canlı ses özelliği
            // duruyor, yalnız hareketin hedefi değişti.
            i.action == Intent.ACTION_ASSIST || i.action == "android.intent.action.VOICE_COMMAND" ->
                "assistant"
            else -> i.getStringExtra("hermes_action")
        } ?: return
        i.removeExtra("hermes_action")
        chatViewModel.pendingAction.value = action
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
            // "Tam kontrol" kalıcı bildirimi ayara bağlı: açıkken durur,
            // kapanınca kalkar. Ayarlar dışında her karede yeniden
            // göndermemek için yalnız anahtarlar değiştiğinde çalışıyor —
            // bu kanal gizli çalışmamalı.
            LaunchedEffect(settings.fullControl, settings.agentMayUsePhone) {
                com.hermes.mobile.data.HermesAccessibilityService.refreshNotice(this@MainActivity)
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
                    // Sesli mesaj tercihleri: otomatik gönder (varsayılan kapalı),
                    // motor (varsayılan kahya), uç adresi ve son çalışan adres.
                    chatViewModel.voicePrefs = settings.toVoicePrefs()
                    // Tur-13: asistan akışında yanıt kendiliğinden okunsun mu
                    // (varsayılan AÇIK, ama yalnız asistan modunda etkili).
                    chatViewModel.assistantAutoRead = settings.assistantAutoRead
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
                    // Çalışan ses ucu adresi hatırlanır (tur-11): sonraki açılışta
                    // ilk aday o olur, ölü adres her seferinde denenmez.
                    chatViewModel.onVoiceBase = { base ->
                        viewModel.settingsStore.update { it.copy(voiceLastOk = base) }
                    }
                }

                LaunchedEffect(gateway) {
                    liveViewModel.bind(gateway)
                    viewModel.bindGateway(gateway)
                    // Bot (profil) ataması: profilleri sohbet açılır açılmaz
                    // çek — yalnız ProfilSheet açıldığında çekilmesin.
                    viewModel.loadProfiles()
                }

                // Tur-13: asistan rolü — Activity akışından Compose değerine.
                // `onResume` ve sistem rol diyaloğu dönüşünde tazelenir.
                val assistantRoleState by assistantRole.collectAsStateWithLifecycle()

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
                    // Tur-13: asistan rolü — rol akışı Activity'de, burada değer.
                    assistantRoleState = assistantRoleState,
                    onEnterAssistantMode = ::enterAssistantMode,
                    onMakeDefaultAssistant = ::makeDefaultAssistant,
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
        // Tur-13: rol satırı — kullanıcı rolü sistem Ayarlar'ından da
        // değiştirebilir; her dönüşte tazelenir (bayat "Şu an: Google" kalmasın).
        refreshAssistantRole()
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
    /**
     * Tur-13: asistan rolü durumu (Activity `onResume`da tazeler).
     *
     * setContent lambda'sından değer olarak geçiyor: `assistantRole` akışı
     * Activity'de yaşıyor, HermesApp'in kapsamı ayrı.
     */
    assistantRoleState: com.hermes.mobile.data.AssistantModeLogic.RoleStatus =
        com.hermes.mobile.data.AssistantModeLogic.RoleStatus(
            com.hermes.mobile.data.AssistantModeLogic.RoleState.None
        ),
    /**
     * Tur-13: asistan modunu aç (mikrofon izni sessizce istenir, kayıt
     * başlamaz). Sekmeyi çağıran ayarlar — `tab` bu composable'ın içinde.
     */
    onEnterAssistantMode: () -> Unit = {},
    /** Tur-13: "Hermes'i varsayılan asistan yap" — sistem rol diyaloğu. */
    onMakeDefaultAssistant: () -> Unit = {},
) {
    var tab by remember { mutableStateOf(Tab.Chat) }
    // Tur-15: Outrun yarış WebView'i sekme kompozisyonunun DIŞINDA yaşar (sekme
    // değişince duraklar, dönüşte kaldığı yerden sürer); HermesApp kapanınca yok edilir.
    val outrunHolder = remember { com.hermes.mobile.ui.ArenaOutrunHolder() }
    androidx.compose.runtime.DisposableEffect(outrunHolder) {
        onDispose { outrunHolder.release() }
    }
    // Tur-11: sesli mesaj durumu (kayıt sayacı + seslendirme fazı) ve sesle
    // yazılan metin. HermesApp gövdesinde toplanıyor: ChatScreen çağrısı bu
    // kapsamda (setContent lambda'sındaki val'lar burada görünmez).
    val voiceMsgState by chatViewModel.voiceMsg.state.collectAsStateWithLifecycle()
    val voicePrefillState by chatViewModel.voicePrefill.collectAsStateWithLifecycle()
    // Tur-12: "Isıt" durumu — bölümden çıkılsa da ısıtma sürer, dönünce "Hazır ✓".
    val voiceWarmState by chatViewModel.voiceWarmState.collectAsStateWithLifecycle()
    val panel by panelViewModel.state.collectAsStateWithLifecycle()

    // FR-001: canlı oturum başlık zinciri TEK kaynaktan — liveSessionTitle
    // (rename > gateway başlığı > REST kaynağı > kaynak+zaman). Ham süreç içi
    // id hiçbir ekranda başlık/etiket olmaz (ray, sohbete bağlanma, döküm).
    val restById = remember(state.sessions) { state.sessions.associateBy { it.id } }
    // Tur-16: canlı oturum → REST eşlemesi (aynı dbId'ye iki süreç içi kayıt
    // düşerse son yazan kalır; canlı durum için kabul edilebilir).
    val drawerLiveByDbId = remember(live.sessions) {
        live.sessions.associateBy { it.dbId }
    }
    // Tur-16: çekmece durumları — drawerRowList bu state'leri okuduğu için
    // ÖNCE tanımlanır (ileri referans derleme hatası verirdi).
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var drawerQuery by rememberSaveable { mutableStateOf("") }
    var drawerTab by rememberSaveable { mutableStateOf(0) }
    var drawerArchived by rememberSaveable { mutableStateOf(false) }
    val drawerScope = rememberCoroutineScope()
    // Tur-16: çekmece satırları — karar mantığı SessionDrawerLogic'te (testli).
    val drawerRowList = remember(
        state.sessions, live.sessions, state.flags, state.cronNames, drawerArchived, chat.sessionId,
    ) {
        drawerRows(
            sessions = state.sessions,
            live = live.sessions,
            liveByDbId = drawerLiveByDbId,
            flags = state.flags,
            cronNames = state.cronNames,
            showArchived = drawerArchived,
            currentSessionId = chat.sessionId,
        )
    }
    val drawerItemList = remember(drawerRowList, drawerQuery) {
        drawerListItems(drawerRowList, query = drawerQuery, liveOnly = false, grouping = true)
    }
    val liveTitleOf = remember(state.flags, state.cronNames, restById) {
        { l: LiveSession ->
            liveSessionTitle(
                l, restById[l.dbId], state.flags, state.cronNames,
                // Tur-4: başlık yedeği de yerelleşir ("Sohbet · …" / "Chat · …").
                en = com.hermes.mobile.ui.serviceLang == com.hermes.mobile.ui.Lang.EN,
            )
        }
    }

    // Tur-2 K3(a): CrashGuard bağlamı — cökme satırında hangi oturumda hangi
    // ekranda olunduğu yazsın ('coktu' yerine 'Sohbet ekranında, sid=…').
    val screenLabel = when {
        detail != null -> "Oturum detayı"
        else -> when (tab) {
            Tab.Chat -> "Sohbet"
            Tab.Work -> "Oturumlar"
            Tab.Panel -> "Pano"
            Tab.Arena -> "Arena"
            Tab.Settings -> "Ayarlar"
        }
    }
    LaunchedEffect(screenLabel, chat.sessionId) {
        CrashGuard.sessionProvider = { chat.sessionId }
        CrashGuard.screenProvider = { screenLabel }
    }

    // FR-006: son açık oturumu geri getir — liste ya da canlı bir kez
    // görüldüğünde ve yalnız bir kez (kullanıcı yeni sohbete geçince
    // `onSessionChanged` settings kaydını zaten temizliyor).
    var lastSessionRestored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.lastSession, state.sessions, live.sessions) {
        if (lastSessionRestored) return@LaunchedEffect
        if (state.sessions.isEmpty() && live.sessions.isEmpty()) return@LaunchedEffect
        val pid = state.active?.id ?: return@LaunchedEffect
        val saved = settings.lastSession.split("|", limit = 2)
        if (saved.size != 2 || saved[0] != pid || saved[1].isBlank()) return@LaunchedEffect
        val row = lastSessionToRestore(saved[1], drawerRowList, chat.sessionId) ?: return@LaunchedEffect
        lastSessionRestored = true
        chatViewModel.continueSession(
            liveId = row.liveId.ifBlank { row.dbId },
            dbId = row.dbId.ifBlank { row.liveId },
            title = row.title,
        )
    }
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

    // Paylaşım hatası uyarısı (YENI-2): sessiz DiagLog değil, her sekmede
    // görünen Toast. Kullanıcı gördükten sonra tüketilir.
    val toastContext = androidx.compose.ui.platform.LocalContext.current
    val shareWarning by chatViewModel.shareWarning.collectAsStateWithLifecycle()
    LaunchedEffect(shareWarning) {
        val w = shareWarning ?: return@LaunchedEffect
        android.widget.Toast.makeText(toastContext, w, android.widget.Toast.LENGTH_LONG).show()
        chatViewModel.clearShareWarning()
    }

    // Tur-19 FR-002: hızlı yanıt/müdahale sonucu — notice geldiği an Toast ve
    // tüketilir (eski Canlı ekranı notice'u gösteriyordu; çekmece YERİNDEyken
    // tek görülebilir kanal toast'tır).
    LaunchedEffect(live.notice) {
        val n = live.notice ?: return@LaunchedEffect
        android.widget.Toast.makeText(toastContext, n, android.widget.Toast.LENGTH_SHORT).show()
        liveViewModel.clearNotice()
    }

    var modelSheet by remember { mutableStateOf(false) }
    var commandSheet by remember { mutableStateOf(false) }
    var reasoningSheet by remember { mutableStateOf(false) }
    val reasoningStatus by chatViewModel.reasoningStatus.collectAsStateWithLifecycle()
    var voiceSheet by remember { mutableStateOf(false) }
    var cameraFullScreen by remember { mutableStateOf(false) }
    var exitDialog by remember { mutableStateOf(false) }
    // Tur-16: `backToSessions` KALDIRILDI — ayrı Oturumlar sayfası yok; geri
    // tuşu artık açık çekmeceyi kapatır (ModalNavigationDrawer, FR-001d).
    // (Çekmece state'leri yukarıda, drawerRowList'ten önce tanımlı.)

    // Kısayol / asistan hareketi: hangi ekranda olursak olalım isteneni aç.
    val pendingAction by chatViewModel.pendingAction.collectAsStateWithLifecycle()
    LaunchedEffect(pendingAction) {
        when (pendingAction) {
            // Tur-13: asistan hareketi — sohbet öne gelir, bas-konuş öne çıkar,
            // mikrofon izni sorulur. Kayıt KENDİLİĞİNDEN başlamaz.
            "assistant" -> {
                tab = Tab.Chat
                onEnterAssistantMode()
            }
            "voice" -> { tab = Tab.Chat; voiceSheet = true }
            "driving" -> {
                onNeedNotification()
                voiceViewModel.startDriving()
            }
            "new" -> { tab = Tab.Chat; chatViewModel.newSession() }
            "camera" -> { tab = Tab.Chat; if (onNeedCamera()) cameraFullScreen = true }
        }
        if (pendingAction != null) chatViewModel.pendingAction.value = null
    }
    var profileSheet by remember { mutableStateOf(false) }
    /** Ayarlar→Sunucular / boş-sohbet CTA: ConnectScreen tam ekranı. */
    var serversScreen by remember { mutableStateOf(false) }
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
    // Tur-16: açık çekmece + geri = çekmeceyi kapat (SC-003d — sohbetteyken
    // geri uygulamadan ÇIKMAZ, önce çekmece iner). Kayıt sırası: kök-çıkış
    // handler'ından ÖNCE (son kaydedilen kazanır → bu handler geçerli).
    BackHandler(enabled = drawerState.isOpen && !voice.driving && !cameraFullScreen) {
        drawerScope.launch { drawerState.close() }
    }
    // Kök ekranda geri = çıkış; yanlışlıkla basınca sohbet kaybolmasın diye
    // (ayarlardan kapatılabilir) önce sorulur. Çekmece kapalıyken sorulur.
    BackHandler(
        enabled = settings.confirmExit && detail == null && !cameraFullScreen &&
            panel.section == null && !voice.driving && !drawerState.isOpen
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
                    // Tur-16: Oturumlar sekmesi listeden düştü — oturum listesi
                    // artık sohbetin soldan açtığı çekmecede (FR-007, tek ekran).
                    // `Tab.Work` enum girişi bilinçli duruyor (ekran etiketi ve
                    // geri-uyumluluk); çubukta çizilmez.
                    Tab.entries.filter { it != Tab.Work }.forEach { entry ->
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

        // Tur-16: oturum çekmecesi — sohbet ekranı arkada KALIR, liste soldan
        // kayar (Claude/Grok/Gemini). Çekmece yalnız Sohbet sekmesinde ve tam
        // ekranlar (sunucular/döküm/paylaşım hedefi) kapalıyken açılır;
        // ModalNavigationDrawer sürükleme ile açma/kapama davranışını verir
        // (FR-001). Sohbet YERİNDE değişir — NavHost rotası/sayfa eklenmedi,
        // back stack büyümez (SC-003d).
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = tab == Tab.Chat && !serversScreen && detail == null && !shareTargetVisible,
            drawerContent = {
                Box(
                    Modifier
                        .fillMaxWidth(0.85f)
                        .fillMaxHeight()
                        .padding(top = innerPadding.calculateTopPadding()),
                ) {
                    SessionDrawerContent(
                        rows = drawerRowList,
                        items = drawerItemList,
                        query = drawerQuery,
                        onQuery = { drawerQuery = it },
                        tab = drawerTab,
                        onTab = { drawerTab = it },
                        liveSessions = live.sessions,
                        liveTitleOf = liveTitleOf,
                        currentSessionId = chat.sessionId,
                        liveIntervening = live.intervening,
                        liveSending = live.sending,
                        activeProfileName = activeHermesProfile,
                        onNewChat = {
                            drawerScope.launch { drawerState.close() }
                            chatViewModel.newSession()
                        },
                        onPick = { row ->
                            // Seçim = YERİNDE oturum değişimi (rota yok).
                            chatViewModel.continueSession(
                                liveId = row.liveId.ifBlank { row.dbId },
                                dbId = row.dbId.ifBlank { row.liveId },
                                title = row.title,
                            )
                        },
                        onTogglePin = { row -> viewModel.togglePin(row.dbId.ifBlank { row.liveId }) },
                        onRename = { row, text -> viewModel.renameSession(row.dbId.ifBlank { row.liveId }, text) },
                        onSetArchived = { row, archived ->
                            viewModel.setArchived(row.dbId.ifBlank { row.liveId }, archived)
                        },
                        onUndoArchive = { row ->
                            viewModel.setArchived(row.dbId.ifBlank { row.liveId }, false)
                        },
                        onDelete = { row -> viewModel.deleteSession(row.dbId.ifBlank { row.liveId }) },
                        onOpenTranscript = { row ->
                            // Döküm (tur-14 erişimi korunur): id + başlık çözülü gider.
                            viewModel.openSessionById(
                                row.dbId.ifBlank { row.liveId },
                                row.title,
                                row.liveId.ifBlank { null },
                            )
                        },
                        onConnectLive = { session ->
                            chatViewModel.continueSession(
                                liveId = session.id,
                                dbId = session.dbId,
                                title = liveTitleOf(session),
                            )
                        },
                        // Tur-19 FR-002: hızlı yanıt — mevcut intervene (steer)
                        // yolu; sonuç live.notice üzerinden toast olur (aşağıdaki
                        // LaunchedEffect), çekmece/akış YERİNDE kalır.
                        onQuickReply = { session, text ->
                            liveViewModel.intervene(session, InterventionKind.Add, text)
                        },
                        onOpenQuickReply = liveViewModel::openQuickReply,
                        quickLive = live.quickReply,
                        onClearQuickReply = liveViewModel::clearQuickReply,
                        onInterruptLive = { session ->
                            drawerScope.launch { drawerState.close() }
                            liveViewModel.interrupt(session)
                        },
                        onOpenIntervention = { session -> liveViewModel.openIntervention(session) },
                        onCloseIntervention = liveViewModel::closeIntervention,
                        onSubmitIntervention = liveViewModel::intervene,
                        onOpenSettings = {
                            drawerScope.launch { drawerState.close() }
                            tab = Tab.Settings
                        },
                        onDismissDrawer = {
                            drawerScope.launch { drawerState.close() }
                        },
                    )
                }
            },
        ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(HermesColors.Background)
                .padding(innerPadding)
                // Tur-8 klavye düzeltmesi: Scaffold'un alt çubuğu (NavigationBar
                // + sistem çubuğu) burada padding olarak UYGULANDI. Tüketilmezse
                // içerideki `imePadding()` aynı yüksekliği İKİNCİ kez ekliyordu:
                // klavye açılınca composer klavyenin 122dp yukarısında asılı
                // kalıyor (ölçüm: composer alt kenarı y=1195, klavye üstü
                // y=1517) ve sohbet alanı 122dp kısalıyordu. Bu satır iç
                // inset sorgularından düşer → composer tam klavye üstüne oturur.
                .consumeWindowInsets(innerPadding)
        ) {
            if (serversScreen) {
                // İlk kurulum yolu: sunucu + token tek ekrandan (2026-09-14
                // emülatör denetimi bulgu-1). Profil seçilince kapatılır.
                ConnectScreen(
                    state = state,
                    onSelect = { id ->
                        viewModel.selectProfile(id)
                        serversScreen = false
                    },
                    onSave = { profile ->
                        viewModel.saveProfile(profile)
                        viewModel.selectProfile(profile.id)
                    },
                    onDelete = viewModel::deleteProfile,
                    onRefresh = viewModel::refreshAll,
                    onBack = { serversScreen = false },
                )
            } else if (detail != null) {
                SessionDetailScreen(detail, onBack = viewModel::closeSession)
            } else if (shareTargetVisible) {
                ShareTargetScreen(
                    textSnippet = shareTextSnippet,
                    fileNote = shareFileNote,
                    recentSessions = state.sessions.take(10),
                    titleOf = { s -> readableTitle(s, state.flags, state.cronNames) },
                    onNewTopic = {
                        chatViewModel.pickShareTarget(
                            sessionId = null,
                            sharedText = shareTextSnippet,
                            sharedFile = shareFileNote,
                        )
                        chatViewModel.consumePendingShare()
                    },
                    onPickSession = { s ->
                        // Sıra kritik (HIGH-1): continueSession ChatState'i
                        // sıfırlıyor (attachments dahil) — önce bağlan, sonra
                        // paylaşımı tüket ki yük çipi silinmesin.
                        chatViewModel.continueSession(
                            liveId = s.id,
                            dbId = s.id,
                            // FR-001: okunabilir başlık; ham id yedek olarak bile geçmez.
                            title = readableTitle(s, state.flags, state.cronNames),
                        )
                        chatViewModel.pickShareTarget(
                            sessionId = s.id,
                            sharedText = shareTextSnippet,
                            sharedFile = shareFileNote,
                        )
                        chatViewModel.consumePendingShare()
                    },
                    onCancel = chatViewModel::cancelPendingShare,
                )
            } else {
                when (tab) {
                    Tab.Chat -> {
                        // Tur-16: sol ray KALDIRILDI — oturum değişimi soldan
                        // açılan çekmeceden (ModalNavigationDrawer, FR-007).
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
                        onOpenServers = { serversScreen = true },
                        // KALAN-2: ⋯ → Müdahale — çalışan ajana açık sohbetten
                        // talimat (session.steer / session.redirect).
                        onIntervene = chatViewModel::intervene,
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
                        // Tur-11: bas-konuş kaydı + sesli okuma.
                        voice = voiceMsgState,
                        voicePrefill = voicePrefillState,
                        onVoicePrefillConsumed = chatViewModel::consumeVoicePrefill,
                        onVoiceHoldStart = chatViewModel::voiceHoldStart,
                        onVoiceHoldRelease = chatViewModel::voiceHoldRelease,
                        onVoiceCancel = chatViewModel::voiceCancel,
                        onSpeak = chatViewModel::speak,
                        // Tur-13: asistan şeridi — anahtarın tek kaynağı
                        // Ayarlar'daki değer; şerit onu yazar.
                        autoReadAssistant = settings.assistantAutoRead,
                        onToggleAssistantAutoRead = { v ->
                            viewModel.settingsStore.update { it.copy(assistantAutoRead = v) }
                        },
                        onExitAssistantMode = chatViewModel::exitAssistantMode,
                        // Tur-16: ☰ — oturum çekmecesini açar (FR-001).
                        onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                        // Tur-19 FR-003: eşzamanlılık istatistik şeridi (composer üstü).
                        statusStrip = {
                            SessionStatsStrip(
                                rows = drawerRowList,
                                speed = chatViewModel.speed,
                                decimalSeparator = if (com.hermes.mobile.ui.S.lang == com.hermes.mobile.ui.Lang.TR) ',' else '.',
                            )
                        },
                        )
                    }
                    // Tur-16: Work/Oturumlar sekmesi sekme çubuğunda çizilmez
                    // (giriş, enum exhaustiveness için duruyor) — oturum listesi
                    // artık sohbetin çekmecesinde (FR-007, tek ekran).
                    Tab.Work -> Unit
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
                        // Tur-9: Arena boştayken sahne sunucunun çalışan oturumlarını
                        // gösterir (LiveSessions kaynağı, salt okuma).
                        liveSessions = live.sessions,
                        // Tur-15: sahne kipi kalıcı (AppSettings) + Outrun WebView sekme
                        // değişiminden sağ çıksın diye HermesApp kapsamında tutulur.
                        sceneMode = settings.arenaSceneMode,
                        onSceneModeChange = { m ->
                            viewModel.settingsStore.update { it.copy(arenaSceneMode = m) }
                        },
                        outrunHolder = outrunHolder,
                    )
                    Tab.Settings -> SettingsScreen(
                        shizukuState = shizukuState,
                        onRequestShizuku = chatViewModel.shizuku::requestPermission,
                        settings = settings,
                        customThemes = customThemes,
                        // Tur-13: asistan rolü satırı — ölü değil, onResume'da
                        // ve sistem diyaloğu dönüşünde tazelenir.
                        assistantRole = assistantRoleState,
                        onMakeDefaultAssistant = onMakeDefaultAssistant,
                        onUpdate = viewModel.settingsStore::update,
                        onSaveTheme = viewModel.settingsStore::saveTheme,
                        onDeleteTheme = viewModel.settingsStore::deleteTheme,
                        onImportTheme = viewModel.settingsStore::importTheme,
                        onExportTheme = viewModel.settingsStore::exportTheme,
                        activeProfile = state.active,
                        onOpenServers = { serversScreen = true },
                        // KALAN-4: Ayarlar→Profiller — sohbetin ⋯ menüsüyle aynı
                        // ProfileSheet (tek seçim yolu, iki giriş noktası).
                        onOpenProfiles = {
                            viewModel.loadProfiles()
                            profileSheet = true
                        },
                        activeHermesProfile = activeHermesProfile,
                        // Tur-11: ses hattı sağlık denemesi (GET /health).
                        // Tur-12: yapılandırılmış dönüş (motor motor durum) +
                        // canlı yenileme aynı yolu kullanır; "Isıt" ayrı akış.
                        onVoiceProbe = { chatViewModel.voiceProbe() },
                        onVoiceWarm = { engine -> chatViewModel.voiceWarm(engine) },
                        onVoiceWarmReset = { engine -> chatViewModel.voiceWarmReset(engine) },
                        voiceWarmState = voiceWarmState,
                    )
                }
            }

            // Tur-2 K3(a): "sik sik kapaniyor" artik sessiz değil — çökme
            // izinden sonraki ilk açılışta kullanıcı ne olduğunu GÖRÜR
            // ("uygulama çöktü" yerine gorunur hata; FR-003).
            CrashRecoveryBanner()
        }
        } // ModalNavigationDrawer (tur-16)


        if (profileSheet) {
            val profilesError by viewModel.profilesError.collectAsStateWithLifecycle()
            ProfileSheet(
                profiles = hermesProfiles,
                activeName = activeHermesProfile,
                loading = profilesLoading,
                error = profilesError,
                onRetry = viewModel::loadProfiles,
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
                // Tur-13: "Yerel (Kahya)" seçildi — Gemini Live oturumu kapanır,
                // asistan modu (yerel hat) açılır. İki motor birlikte çalışmaz.
                onUseLocal = {
                    voiceViewModel.stop()
                    voiceSheet = false
                    tab = Tab.Chat
                    onEnterAssistantMode()
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

/**
 * Tur-2 K3(a): çökme kurtarma şeridi. Süreç çökmeyle öldüğünde bellek
 * gider — kalıcı iz diag.log'dadır ve [CrashGuard.recoverFromDiagLog] onu
 * açılışta [CrashGuard.lastCrash]'e yükler. Kullanıcı "neden kapandı"
 * sorusunu tahmin etmek yerine görür; dokununca şerit kapanır.
 */
@Composable
private fun CrashRecoveryBanner() {
    // lastCrash @Volatile — recomposition'ı StateFlow akışıyla tetiklemeyi
    // ucuz tutmak için tek seferlik remember yeter: açılışta bir kez okunur.
    var visible by remember { mutableStateOf(CrashGuard.lastCrash != null) }
    val text = CrashGuard.lastCrash
    if (!visible || text == null) return
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .background(HermesColors.Danger.copy(alpha = 0.92f))
            .clickable {
                CrashGuard.lastCrash = null
                visible = false
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "⚠ $text",
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 12.sp,
            maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
