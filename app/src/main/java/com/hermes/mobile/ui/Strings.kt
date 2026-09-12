package com.hermes.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/**
 * Arayüz metinleri — Türkçe ve İngilizce.
 *
 * Neden `strings.xml` değil: metinlerin çoğu Compose içinde satır içi yazılmış
 * ve çekirdek 536 dizeyi kaynak dosyasına taşımak tek seferde yapılacak bir iş
 * değil. Bu katman **kademeli** geçişe izin veriyor: çevrilen yüzeyler buradan
 * okuyor, çevrilmeyenler olduğu gibi kalıyor. Yeni metin yazarken buraya
 * eklemek gerekiyor.
 *
 * Varsayılan cihaz diline bakıyor; kullanıcı Ayarlar'dan sabitleyebiliyor.
 * Hizmet katmanı (Compose dışı, ör. `PhoneTools`) [lang] değişkenini okuyor
 * çünkü orada CompositionLocal yok.
 */
enum class Lang { TR, EN }

/** Compose dışındaki katmanlar için — ayarlar değişince güncellenir. */
@Volatile
var serviceLang: Lang = Lang.TR

val LocalLang = compositionLocalOf { Lang.TR }

object S {
    val lang: Lang
        @Composable @ReadOnlyComposable get() = LocalLang.current

    // ── Alt gezinme ──────────────────────────────────────────────────
    val tabChat: String @Composable get() = t("Sohbet", "Chat")
    val tabSessions: String @Composable get() = t("Oturumlar", "Sessions")
    val tabPanel: String @Composable get() = t("Pano", "Dashboard")
    val tabArena: String @Composable get() = t("Arena", "Arena")
    val tabSettings: String @Composable get() = t("Ayarlar", "Settings")

    // ── Sohbet ───────────────────────────────────────────────────────
    val chatTitle: String @Composable get() = t("Sohbet", "Chat")
    val emptyTitle: String @Composable get() = t("Hermes'e yaz", "Message Hermes")
    val emptyHint: String
        @Composable get() = t("Yaz, konuş ya da dosya ekle.", "Type, talk, or attach a file.")
    val composerHint: String @Composable get() = t("Mesaj yaz…", "Message…")
    val composerOffline: String
        @Composable get() = t("Mesaj yaz — bağlanınca gönderilir", "Type — sends when reconnected")
    val listening: String @Composable get() = t("Dinliyorum…", "Listening…")
    val phoneSectionTitle: String
        @Composable get() = t("Telefonu da kullanabilirim", "I can use the phone too")
    val connected: String @Composable get() = t("bağlı", "connected")
    val connecting: String @Composable get() = t("bağlanıyor…", "connecting…")
    val closed: String @Composable get() = t("kapalı", "disconnected")
    val notReady: String @Composable get() = t("hazır değil", "not ready")

    // ── Canlı ses ────────────────────────────────────────────────────
    val voiceTitle: String @Composable get() = t("Canlı ses", "Live voice")
    val voiceTapToStart: String @Composable get() = t("Başlatmak için dokun", "Tap to start")
    val voiceShowCamera: String @Composable get() = t("Kamerayı göster", "Show camera")
    val voiceDriving: String @Composable get() = t("Sürüş kipi", "Driving mode")
    val voiceSpeaker: String @Composable get() = t("Hoparlör", "Speaker")
    val voiceEarpiece: String @Composable get() = t("Kulaklık", "Earpiece")
    val voiceBluetooth: String @Composable get() = t("Bluetooth", "Bluetooth")
    val voiceChange: String @Composable get() = t("değiştir", "change")
    val voiceKeyOnServer: String
        @Composable get() = t("anahtar sunucuda kalıyor", "key stays on the server")

    // ── Kamera ───────────────────────────────────────────────────────
    val camStart: String @Composable get() = t("Konuşmaya başla", "Start talking")
    val camStop: String @Composable get() = t("Konuşmayı bitir", "Stop talking")
    val camFrames: String @Composable get() = t("kare", "frames")
    val camClose: String @Composable get() = t("Kapat", "Close")
    val camFlip: String @Composable get() = t("Kamerayı çevir", "Flip camera")

    // ── Sürüş kipi ───────────────────────────────────────────────────
    val driveTitle: String @Composable get() = t("Sürüş kipi", "Driving mode")
    val driveHint: String
        @Composable get() = t(
            "Ekran kapalıyken de dinlemeye devam eder",
            "Keeps listening with the screen off",
        )
    val driveExit: String @Composable get() = t("Çık", "Exit")
    val driveTapToTalk: String @Composable get() = t("Konuşmak için dokun", "Tap to talk")
    val driveStop: String @Composable get() = t("Durdur", "Stop")

    // ── Pano ─────────────────────────────────────────────────────────
    val panelTitle: String @Composable get() = t("Pano", "Dashboard")
    val panelSubtitle: String
        @Composable get() = t("Sunucunun tüm bölümleri", "Every section of your server")
    val back: String @Composable get() = t("Geri", "Back")
    val refresh: String @Composable get() = t("Yenile", "Refresh")


    // ── Sürüş kipi / canlı ses durum sözcükleri ──────────────────────
    val stStart: String @Composable get() = t("BAŞLAT", "START")
    val stConnecting: String @Composable get() = t("BAĞLANIYOR", "CONNECTING")
    val stListening: String @Composable get() = t("DİNLİYORUM", "LISTENING")
    val stSpeaking: String @Composable get() = t("KONUŞUYOR", "SPEAKING")
    val audioOut: String @Composable get() = t("Ses çıkışı", "Audio output")
    val driveExitLong: String @Composable get() = t("Sürüş kipinden çık", "Exit driving mode")
    val driveScreenOff: String
        @Composable get() = t(
            "Ekranı kapatabilirsin — dinlemeye devam eder",
            "You can turn the screen off — it keeps listening",
        )
    val start: String @Composable get() = t("Başlat", "Start")
    val voiceConnecting: String @Composable get() = t("Bağlanıyor…", "Connecting…")
    val voiceListening: String
        @Composable get() = t("Dinliyorum — konuşabilirsin", "Listening — go ahead")
    val voiceSpeaking: String
        @Composable get() = t("Yanıtlıyor — sözünü kesebilirsin", "Answering — you can interrupt")
    val noRelay: String @Composable get() = t("röle adresi yok", "no relay address")
    val ownKey: String @Composable get() = t("kendi anahtarın kullanılıyor", "using your own key")
    val keyOnServer: String
        @Composable get() = t("anahtar sunucuda kalıyor", "key stays on the server")
    val unmute: String @Composable get() = t("Sesi aç", "Unmute")

    /**
     * Çağrı yerinde iki dili birlikte yazmak için.
     *
     * Anahtar üretmeye değmeyecek, tek yerde kullanılan metinler için — her
     * bölüm adı için ayrı bir alan tanımlamak dosyayı şişiriyordu.
     */
    @Composable
    fun t2(tr: String, en: String): String = t(tr, en)

    @Composable
    private fun t(tr: String, en: String): String = if (LocalLang.current == Lang.EN) en else tr
}

/** Compose dışı çağrılar için aynı seçim. */
fun tr(tr: String, en: String): String = if (serviceLang == Lang.EN) en else tr
