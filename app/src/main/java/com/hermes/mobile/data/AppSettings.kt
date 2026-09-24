package com.hermes.mobile.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hermes.mobile.ui.theme.HermesPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Canlı ses için hazır kişilikler.
 *
 * Bunlar Gemini'ye giden sistem yönergesi. Sunucudaki `agent.personalities`
 * (Hermes'in kendi karakterleri) ayrı bir şey — bu yalnız sesli/görüntülü
 * oturumun tonunu belirliyor.
 */
@Serializable
data class VoicePersona(
    val id: String,
    val label: String,
    val instruction: String,
)

val BUILTIN_PERSONAS: List<VoicePersona> = listOf(
    VoicePersona(
        "asistan", "Genel asistan",
        "Sen Hermes'sin — kişisel yapay zekâ asistanı. Türkçe konuş. Sesli sohbette " +
            "kısa ve doğal cümleler kur; uzun listeler okuma. Emin olmadığını söyle.",
    ),
    VoicePersona(
        "domain-expert", "Domain expert",
        "You are a domain expert assistant. Keep a measured, formal tone. " +
            "When you give a figure or a fact, say where it came from. Never " +
            "invent data you are unsure of — say 'I need to verify that' " +
            "instead. For anything specific to the user's domain, answer only " +
            "through the hermes_ask tool.",
    ),
    VoicePersona(
        "doktor", "Sağlık danışmanı",
        "Sağlık konularında bilgili, sakin ve anlaşılır konuşan bir danışmansın. " +
            "Türkçe konuş, tıbbi terimleri günlük dile çevir. Genel bilgi verirsin; " +
            "teşhis koymaz, ilaç dozu önermezsin. Acil ya da ciddi belirtilerde " +
            "(göğüs ağrısı, nefes darlığı, bilinç bulanıklığı, şiddetli kanama gibi) " +
            "hemen hekime ya da 112'ye yönlendirirsin.",
    ),
    VoicePersona(
        "hukukcu", "Hukuk danışmanı",
        "Türk hukuku konusunda bilgili bir danışmansın. Türkçe konuş, madde ve " +
            "kanun adı verirken kesin ol, emin değilsen söyle. Somut hukuki tavsiye " +
            "yerine genel çerçeve çizersin ve avukata danışılmasını önerirsin.",
    ),
    VoicePersona(
        "ogretmen", "Öğretici",
        "Karmaşık konuları sabırla, adım adım anlatan bir öğretmensin. Türkçe konuş. " +
            "Önce basit bir benzetme kur, sonra ayrıntıya in. Soru sorup anladığını " +
            "kontrol et.",
    ),
    VoicePersona(
        "samimi", "Sıcak ve esprili",
        "Samimi, sıcak ve esprili bir arkadaş gibi konuşuyorsun. Türkçe, gündelik " +
            "bir dil kullan. Şakayı seversin ama işe geldiğinde ciddileşirsin. " +
            "Kaba olmadan takılırsın.",
    ),
    VoicePersona(
        "kisa", "Kısa ve net",
        "Çok kısa konuşuyorsun. Tek cümlelik cevaplar ver. Gereksiz nezaket kalıbı, " +
            "giriş cümlesi ve özet yapma. Türkçe.",
    ),
)

/** Gemini Live'ın hazır ses karakterleri. */
val LIVE_VOICES = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr")

@Serializable
data class AppSettings(
    // ── Görünüm ──────────────────────────────────────────────────────
    val themeId: String = "hermes",
    /** Sunucu skin değişince tema da değişsin mi (`skin.changed`). */
    val followServerSkin: Boolean = true,
    val fontScale: Float = 1.0f,
    val compact: Boolean = false,

    // ── Canlı ses ────────────────────────────────────────────────────
    /**
     * Sesli asistan beyni (tur-21 model seçici): `gemini` (varsayılan, mevcut
     * canlı ses hattı) ya da `yerel` (node1 OpenAI-uyumlu uç — LAN).
     * Çözümleyici: [com.hermes.mobile.data.LiveModelLogic.Provider.fromId].
     */
    val liveProvider: String = "gemini",
    /**
     * Yerel LLM adresi (tur-21) — node1 varsayılanı; ağ değişirse buradan
     * düzeltilir. Boşsa yerel seçenek "adres girilmedi" hatası verir.
     */
    val localLlmUrl: String = "http://192.168.1.99:8888",
    /** Yerel model adı (registry boşlukları için elle düzeltme kapısı). */
    val localLlmModel: String = "Qwen/Qwen3.8-Flash-Next",
    /**
     * Canlı ses modeli.
     *
     * `-latest` takma adı kullanılıyor: Google önizleme modellerini tarihe
     * göre yayınlıyor (09-2025, 12-2025 …) ve bir tarihe sabitlenmek modeli
     * zamanla eskitiyordu. Takma ad her zaman güncel kararlı sürümü veriyor.
     */
    val liveModel: String = "gemini-2.5-flash-native-audio-latest",
    val liveVoice: String = "Puck",
    val personaId: String = "asistan",
    /** Hazır kişilik yerine kendi yazdığın yönerge. */
    val customPersona: String = "",
    val liveLanguage: String = "tr-TR",

    // ── Sohbet ───────────────────────────────────────────────────────
    val expandThinking: Boolean = false,
    /** Son seçilen /reasoning çabası — "Düşünce panosu" başlangıç durumu. */
    val reasoningLevel: String? = null,
    /**
     * Düşünürken metni ekranda canlı çiz — son satırları izleyen açık blok.
     *
     * Kapalıyken düşünce bloğu hâlâ toplanır ama ekran yalnız "Düşünüyor…"
     * başlığını gösterir; yanıt başlar ya da araç çalışmaya başlayınca blok
     * katlanır hâle gelir (mevcut davranış).
     */
    val showLiveThinking: Boolean = true,
    val expandTools: Boolean = false,
    val renderMarkdown: Boolean = true,
    val historyLimit: Int = 150,

    // ── Telefon denetimi ─────────────────────────────────────────────
    /**
     * Canlı sesli asistan telefonu kullanabilsin mi (uygulama açma, çevirici,
     * SMS taslağı, yol tarifi, alarm, takvim, Tasker/MacroDroid).
     *
     * Varsayılan **açık**: telefonu asistan gibi kullanabilmek uygulamanın asıl
     * amacı. Güvenlik, kapatmakla değil eylemin kendisiyle sağlanıyor — arama
     * başlatılmaz (çevirici açılır), SMS gönderilmez (taslak açılır), yani geri
     * alınamaz her adımda son dokunuş kullanıcıya ait.
     */
    val phoneTools: Boolean = true,

    /**
     * Shizuku üzerinden derin telefon denetimi.
     *
     * Varsayılan **kapalı**: Shizuku'nun kurulu olması ve her yeniden
     * başlatmada elle başlatılması gerekiyor. Kapalıyken uygulama tamamen
     * normal çalışır — bu katman yalnız ek araçlar açıyor.
     */
    val shizukuEnabled: Boolean = false,

    // ── Genel ────────────────────────────────────────────────────────
    /**
     * Arayüz dili: "" = cihaz dili, "tr", "en".
     *
     * Sabitlenebilir olması gerekiyor: cihaz Türkçe olsa da İngilizce arayüz
     * isteyen (ya da tersi) kullanıcı var.
     */
    val uiLang: String = "",

    /** Geri tuşuyla çıkarken onay sorulsun mu. */
    val confirmExit: Boolean = true,

    /**
     * Son etkin sohbet oturumu — "profilId|oturumId".
     *
     * Uygulama kapanıp açıldığında oturum kimliği bellekteydi ve kayboluyordu;
     * her açılış yeni oturum demekti. Şimdi açılışta bu oturuma yeniden
     * bağlanıp geçmiş yükleniyor — Telegram'daki gibi konuşma kaldığı yerden
     * sürüyor.
     */
    val lastSession: String = "",

    // ── sparkDash ────────────────────────────────────────────────────
    /**
     * DGX Spark izleme panelini göster.
     *
     * Kapalıysa Pano'da bölüm hiç görünmez ve hiçbir istek atılmaz —
     * sparkDash kurulu değilse boşuna zaman aşımı beklenmesin diye.
     */
    val sparkEnabled: Boolean = true,

    /**
     * sparkDash adresi. Boşsa Hermes sunucusunun adresinden 5555 portuyla
     * türetilir. Ayrı tutuluyor çünkü sparkDash'in API'sinde kimlik
     * doğrulama yok — internete açmak bilinçli bir karar olmalı.
     */
    val sparkUrl: String = "",

    // ── Sesli mesaj (tur-11) ─────────────────────────────────────────
    /**
     * Sesle yazılan metin kendiliğinden gönderilsin mi.
     *
     * Varsayılan **KAPALI** (görev şartı): yanlış anlaşılan bir cümle
     * kendiliğinden ajana gitmesin — metin önce sohbet girdisine yazılır,
     * kullanıcı gönderir.
     */
    val voiceAutoSend: Boolean = false,

    /**
     * Seslendirme motoru: `yerel` (varsayılan, tur-21) · `kahya` · `kadin` ·
     * `chatterbox`.
     *
     * Tur-21 gizlilik kararı: telefon verisi buluta çıkmasın diye varsayılan
     * YEREL Piper (kadın, fettah) — model indirilmemişse seslendirme açık
     * hatayla uyarır, sessiz buluta geçmez.
     */
    val voiceEngine: String = "yerel",

    /**
     * Ses ucu adresi. Boş bırakılırsa sunucu adresinden türetilir:
     * ev ağında `http://<host>:8174`, dışarıda `<base>/voice-api`.
     * Elle verilirse tek başına o denenir (yerel test ucu için).
     */
    val voiceUrl: String = "",

    /** Son çalıştığı doğrulanan ses ucu — sonraki açılışta öne alınır. */
    val voiceLastOk: String = "",

    // ── Telefon asistanı (tur-13) ────────────────────────────────────
    /**
     * Asistan akışında yanıt kendiliğinden seslendirilsin mi.
     *
     * Varsayılan **AÇIK** — ama yalnız asistan bağlamında okunur:
     * `AssistantModeLogic.shouldAutoRead` bu ayarı asistan modu bayrağıyla
     * VE'liyor, normal sohbette ses başlamıyor. Kullanıcı bas-konuş yapıp
     * sorusunu sorduğunda cevabı dinlemek istiyor; ayrıca dokunması gerekmesin.
     */
    val assistantAutoRead: Boolean = true,

    // ── Arena (tur-15) ───────────────────────────────────────────────
    /**
     * Arena sahne kipi: `work` = İş sahnesi (varsayılan) · `outrun` = Outrun yarış.
     *
     * Kimlik metni `ArenaSceneKind.id`; bilinmeyen değer iş sahnesine düşer
     * (`ArenaSceneKind.fromId`), eski kayıtta alan yoksa varsayılan geçerli.
     */
    val arenaSceneMode: String = "work",


    // ── Gizlilik ─────────────────────────────────────────────────────
    val biometricLock: Boolean = false,
    val maskToken: Boolean = true,

    // ── Bağlantı ─────────────────────────────────────────────────────
    val pollSeconds: Int = 15,
    val livePollSeconds: Int = 6,

    // ── Bildirimler ──────────────────────────────────────────────────
    val notifyCron: Boolean = true,
    val notifyErrors: Boolean = true,
    val notifyApprovals: Boolean = true,

    // ── Geliştirici ──────────────────────────────────────────────────
    val showRawEvents: Boolean = false,
    /**
     * Tanitim kipi: sunucudan gelen adlari goruntude maskele.
     * Varsayilan kapali -- kullanici kendi telefonunda kendi verisini gormek
     * istiyor; bu yalnizca ekran goruntusu/video paylasirken aciliyor.
     */
    val demoMask: Boolean = false,

    // ── Ajanın telefona erişimi ──────────────────────────────────
    /**
     * Ajan telefonu KENDILIGINDEN kullanabilsin mi.
     *
     * Varsayilan kapali. Acikken telefon sunucuya giden bir baglanti
     * kuruyor ve cron/Telegram/CLI'dan calisan ajan da telefon
     * araclarini cagirabiliyor. Yazarak/konusarak verdigin komutlar bu
     * ayardan bagimsiz calisiyor -- onlar zaten acik istek.
     */
    val agentMayUsePhone: Boolean = false,
    /** Ajan telefonu gorebilsin ama degistirmesin. */
    val agentReadOnly: Boolean = true,

    /**
     * **Tam kontrol** — ekranı okuma, dokunma, yazma, jest ve ekran görüntüsü.
     *
     * Varsayılan **kapalı**: açıldığında ajan telefonu gerçekten kullanmaya
     * başlıyor, bu yüzden açık bir kullanıcı kararı olmalı. Çalışması için
     * Ayarlar → Erişilebilirlik'ten Hermes tam kontrol servisinin de
     * açılmış olması gerekir; uygulama bu izni programatik olarak veremez.
     *
     * [agentMayUsePhone] ve [agentReadOnly] ile birlikte değerlendirilir:
     * kanal kapalıysa ya da tam kontrol kapalıysa hiçbir yeni eylem çalışmaz;
     * salt-okunur kipte yalnız okuma araçları (ekran dökümü, ekran görüntüsü,
     * uygulama listesi) çalışır. Karar tek yerde: `FullControl.guardReason`.
     */
    val fullControl: Boolean = false,

    /**
     * Google Artemis daemon adresi (sunucuda, varsayılan port 8000). Sohbette
     * "/telefon <görev>" bu adrese gider; Artemis telefonu kablosuz ADB ile
     * kullanır. Boşsa özellik kapalı.
     */
    val artemisUrl: String = "http://192.168.1.101:8000",
    /**
     * Ajan (Telegram/cron/sohbet/Android Auto yanıtı) senin adına sohbetlere
     * yanıt gönderebilsin mi (`phone_reply`). Geri alınamaz bir eylem olduğu
     * için Tam kontrol'den AYRI ve varsayılan KAPALI. Uygulamada kendin
     * yazdığın "ali'ye … yaz" komutu bu ayardan bağımsız (yazmak zaten rıza).
     */
    val agentMayReply: Boolean = false,
    /** Android Auto ekran yansıtma: araç hareket ederken görüntüyü kes (varsayılan AÇIK). */
    val mirrorPauseWhileDriving: Boolean = true,
    /** "flash" (hızlı, 3-5 sn/adım) ya da "pro" (planlı, doğrulamalı). */
    val artemisProfile: String = "flash",
    /** ADB seri numarası elle (boşsa telefonun Wi-Fi IP'sinden bulunur). */
    val artemisDevice: String = "",

    /**
     * Denenip başarısız olan modeller — "sağlayıcı/model" biçiminde.
     *
     * Sunucunun `unavailable_models` listesi yalnız kredi sorununu biliyor;
     * erişilemeyen yerel sunucular (LM Studio kapalıysa) ya da bozuk
     * yapılandırmalar orada görünmüyor. Bir model seçildiğinde denenip
     * yanıt vermezse buraya yazılıyor ve listede gizleniyor.
     */
    /**
     * Son seçilen sohbet modeli — "sağlayıcı|model".
     *
     * `/model` oturum kapsamlı çalışıyor; uygulama yeniden açıldığında yeni
     * oturum sunucunun varsayılanına (Agnes) dönüyordu ve seçim kaybolmuş
     * görünüyordu. Burada saklanıp her yeni oturumda geri uygulanıyor.
     */
    val lastModel: String = "",

    val brokenModels: Set<String> = emptySet(),
    val showBrokenModels: Boolean = false,

    /**
     * Elle sabitlenen modeller — "sağlayıcı/model". Listenin en üstünde,
     * kullanım sıklığından bağımsız olarak dururlar.
     */
    val pinnedModels: List<String> = emptyList(),

    /**
     * Model başına seçilme sayısı — "sağlayıcı/model" → kaç kez.
     *
     * "Sık kullanılanlar" bölümü buradan üretiliyor. Ölçülmüş öneri listesi
     * (RECOMMENDED_MODELS) herkes için aynı; bu ise kullanıcının kendi
     * alışkanlığı, o yüzden ondan da üstte gösteriliyor.
     */
    val modelUsage: Map<String, Int> = emptyMap(),

    /** Kullanıcının elle gizlediği modeller.
     *
     * [brokenModels] otomatik (denendi-çalışmadı); bu ise bilinçli tercih —
     * "bu modeli hiç görmek istemiyorum". Ayrı tutuluyor ki otomatik liste
     * temizlendiğinde kullanıcının seçimi silinmesin.
     */
    val hiddenModels: Set<String> = emptySet(),

    /**
     * Son kullanılan prompt çipi (bot/profil ataması).
     *
     * Composer üstündeki yatay çiplerden seçilen profil. Yalnız YENİ
     * sohbetlerde `createSession(profile)` argümanı olur; mevcut oturumda
     * çipler salt-okunur, mevcut profil gösterilir.
     */
    val selectedProfile: String = "",
) {
    /** Röleye gidecek sistem yönergesi. */
    fun resolveInstruction(): String =
        customPersona.takeIf { it.isNotBlank() }
            ?: BUILTIN_PERSONAS.firstOrNull { it.id == personaId }?.instruction
            ?: BUILTIN_PERSONAS.first().instruction
}

/** Sesli mesaj tercihleri — uygulama ayarlarından çözülür (saf veri). */
data class VoicePrefs(
    /** "Otomatik gönder" — varsayılan KAPALI. */
    val autoSend: Boolean = false,
    val engine: VoiceSpeakLogic.Engine = VoiceSpeakLogic.Engine.DEFAULT,
    /** Elle verilen ses ucu adresi (boşsa adresler profilden türetilir). */
    val url: String = "",
    /** Son çalışan adres — ilk aday olur. */
    val lastOk: String = "",
    /**
     * Asistan akışında yanıt otomatik okunsun mu — varsayılan AÇIK, ama
     * yalnız asistan bağlamında etkili ([AssistantModeLogic.shouldAutoRead]).
     */
    val assistantAutoRead: Boolean = true,
)

/** Ayarlardan ses tercihlerini çözer; bilinmeyen motor adı varsayılana düşer. */
fun AppSettings.toVoicePrefs(): VoicePrefs = VoicePrefs(
    autoSend = voiceAutoSend,
    engine = VoiceSpeakLogic.Engine.fromId(voiceEngine),
    url = voiceUrl,
    lastOk = voiceLastOk,
    assistantAutoRead = assistantAutoRead,
)

private const val PREFS = "hermes_settings"
private const val KEY_SETTINGS = "settings"
private const val KEY_CUSTOM_THEMES = "custom_themes"

/**
 * Ayar deposu.
 *
 * Şifreli saklanıyor çünkü kişilik metni ve gelecekte eklenecek anahtarlar
 * kişisel olabilir; ayrıca profil deposuyla aynı güvenlik seviyesinde kalması
 * tutarlı oluyor.
 */
class SettingsStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, PREFS, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _customThemes = MutableStateFlow(loadThemes())
    val customThemes: StateFlow<List<HermesPalette>> = _customThemes.asStateFlow()

    private fun load(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return runCatching { json.decodeFromString<AppSettings>(raw) }.getOrDefault(AppSettings())
    }

    private fun loadThemes(): List<HermesPalette> {
        val raw = prefs.getString(KEY_CUSTOM_THEMES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<HermesPalette>>(raw) }
            .getOrDefault(emptyList())
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(next)).apply()
    }

    fun saveTheme(palette: HermesPalette) {
        val list = _customThemes.value.filterNot { it.id == palette.id } + palette
        _customThemes.value = list
        prefs.edit().putString(KEY_CUSTOM_THEMES, json.encodeToString(list)).apply()
    }

    fun deleteTheme(id: String) {
        val list = _customThemes.value.filterNot { it.id == id }
        _customThemes.value = list
        prefs.edit().putString(KEY_CUSTOM_THEMES, json.encodeToString(list)).apply()
        if (_settings.value.themeId == id) update { it.copy(themeId = "hermes") }
    }

    /** JSON metninden tema içe aktarır; geçersizse hata mesajı döner. */
    fun importTheme(text: String): String? = runCatching {
        val palette = json.decodeFromString<HermesPalette>(text)
        if (palette.id.isBlank()) return "Temada 'id' alanı yok"
        saveTheme(palette)
        null
    }.getOrElse { "Tema okunamadı: ${it.message}" }

    fun exportTheme(palette: HermesPalette): String = json.encodeToString(palette)
}

/**
 * "Tam kontrol" tek anahtar: açınca çalışması için gereken üç ayar birlikte
 * ayarlanır (ajan telefonu kullanabilsin + yalnız-okuma kapalı + tam kontrol).
 * Önceden üçü ayrı ayrıydı ve "Yalnız okuma" varsayılan AÇIK olduğu için
 * dokunma/yazma araçları sessizce reddediliyordu. Kapatınca yalnız tam
 * kontrol kapanır — diğer iki tercih kullanıcıda kalır.
 */
fun AppSettings.withFullControl(on: Boolean): AppSettings =
    if (on) copy(fullControl = true, agentMayUsePhone = true, agentReadOnly = false)
    else copy(fullControl = false)
