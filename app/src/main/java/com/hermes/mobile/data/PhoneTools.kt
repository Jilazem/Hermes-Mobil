package com.hermes.mobile.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.view.KeyEvent
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import com.hermes.mobile.ui.tr

/**
 * Telefon araçları — sesli asistanın cihazı kullanabilmesi.
 *
 * Gemini Live'a `hermes_ask` ile birlikte bu araçlar da tanıtılıyor. Röle
 * `toolCall` çerçevelerini telefona da iletiyor; `phone_*` çağrılarını burada
 * çalıştırıp `toolResponse` gönderiyoruz.
 *
 * **Kademe 1 — intent:** uygulama aç, ara, SMS taslağı, yol tarifi, alarm,
 * takvim, web araması. Çoğu izin istemiyor.
 *
 * **Kademe 2 — Tasker / MacroDroid köprüsü:** kullanıcının **zaten yazdığı**
 * makroları tetikler. Tasker `net.dinglisch.android.taskerm.ACTION_TASK`
 * yayınıyla ("Allow External Access" açık olmalı); MacroDroid ise webhook
 * ile — uygulamalar arası izin bile gerekmiyor.
 *
 * **Güvenlik:** geri alınamaz eylemler (arama başlatma, SMS gönderme) burada
 * **asla sessizce yapılmaz.** Arama için çevirici açılır, SMS için taslak
 * hazırlanır; son dokunuşu kullanıcı yapar. Erişilebilirlik servisi (kademe 3)
 * bu sürümde yok.
 */
class PhoneTools(
    private val context: Context,
    /**
     * Shizuku köprüsü — verilmezse derin araçlar tanıtılmaz.
     *
     * Böylece Shizuku kapalıyken model onları hiç görmüyor ve
     * "yapamıyorum" cevabı vermek zorunda kalmıyor.
     */
    private val shizuku: ShizukuBridge? = null,
) {

    /** Okuma tarafi -- rehber, takvim, konum, pano. */
    private val read = PhoneRead(context)

    private val http = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Gemini'ye tanıtılacak araç tanımları (röleye `?phone_tools=1` ile bildirilir). */
    companion object {
        val TOOL_NAMES = setOf(
            "phone_open_app", "phone_dial", "phone_sms_draft", "phone_navigate",
            "phone_set_alarm", "phone_add_event", "phone_web_search",
            "phone_tasker_task", "phone_macrodroid_webhook", "phone_settings",
            // Cihazı yalnız "açıp kapatmak" değil gerçekten kullanmak için:
            "phone_flashlight", "phone_volume", "phone_media", "phone_status",
            "phone_timer", "phone_open_url", "phone_share",
            // Okuma tarafi: asistanin telefon hakkinda bir sey BILMESI icin.
            // Bunlar olmadan "Ahmet'i ara" numara ezberlemeyi gerektiriyor,
            // "bugun ne kacirdim" ise hic sorulamiyordu.
            "phone_contacts", "phone_notifications", "phone_calendar",
            "phone_location", "phone_clipboard_read", "phone_clipboard_write",
            // V3: WhatsApp/Telegram/SMS okuma + bildirimden yanıt.
            "phone_messages", "phone_reply",
        )

        /**
         * Shizuku gerektiren araçlar — kabuk yetkisi olmadan yapılamayanlar.
         *
         * Intent'le Wi-Fi açılamıyor (yalnız ayar sayfası açılabiliyor); bunlar
         * `svc`/`settings`/`cmd` komutlarıyla gerçekten değiştiriyor.
         */
        val SHIZUKU_TOOL_NAMES = setOf(
            "phone_wifi", "phone_bluetooth", "phone_dnd", "phone_shell",
        )

        /**
         * Sonucu **okumak icin** cagrilan araclar.
         *
         * Bunlarda donen metin istenen seyin ta kendisi; arac karti icerigi
         * katlayip "88 krkt" gosterdigi icin cevap gorunmuyordu. Yazma
         * araclari icin boyle bir sorun yok: "feneri ac" dedikten sonra
         * sonucu zaten gozunle goruyorsun.
         */
        val READ_TOOL_NAMES = setOf(
            "phone_messages",
            "phone_contacts", "phone_notifications", "phone_calendar",
            "phone_location", "phone_clipboard_read", "phone_status",
        )

        fun isReadTool(name: String) = name in READ_TOOL_NAMES

        /**
         * Ajanin KENDILIGINDEN cagirabilecegi yazma araclari.
         *
         * Bilerek dar: hicbiri geri alinamaz degil. Arama ceviriciyi
         * aciyor, SMS taslak kaliyor -- yani son dokunus hep kullanicida.
         * `phone_shell` ve Shizuku araclari burada YOK; onlar ancak
         * kullanici dogrudan isteyince calisir.
         */
        val AGENT_WRITE_TOOLS = setOf(
            "phone_notify", "phone_speak", "phone_open_app", "phone_navigate",
            "phone_clipboard_write", "phone_dial", "phone_sms_draft",
            "phone_timer", "phone_flashlight", "phone_media",
        )

        fun isPhoneTool(name: String) = name in TOOL_NAMES || name in SHIZUKU_TOOL_NAMES

        /**
         * Röleye bildirilecek araç listesi.
         *
         * Shizuku kapalıyken derin araçlar **hiç tanıtılmıyor**: model onları
         * görmezse "yapamıyorum" demek zorunda kalmıyor, boşa deneme olmuyor.
         */
        fun advertised(shizukuReady: Boolean): Set<String> =
            if (shizukuReady) TOOL_NAMES + SHIZUKU_TOOL_NAMES else TOOL_NAMES

        /** Tasker paket adı — `taskerm`, `tasker` değil. */
        const val TASKER_PKG = "net.dinglisch.android.taskerm"
    }

    /** Bir aracı çalıştırır ve modele dönecek metni üretir. */
    fun execute(name: String, args: JsonObject): String {
        fun arg(key: String): String =
            (args[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }.orEmpty()

        return try {
            when (name) {
                "phone_open_app" -> openApp(arg("app"))
                "phone_dial" -> dial(arg("number"))
                "phone_sms_draft" -> smsDraft(arg("number"), arg("text"))
                "phone_navigate" -> navigate(arg("destination"))
                "phone_set_alarm" -> setAlarm(arg("time"), arg("label"))
                "phone_add_event" -> addEvent(arg("title"), arg("when"))
                "phone_web_search" -> webSearch(arg("query"))
                "phone_tasker_task" -> taskerTask(arg("task"), arg("parameter"))
                "phone_macrodroid_webhook" -> macroDroid(arg("url"))
                "phone_settings" -> openSettings(arg("section"))
                "phone_contacts" -> read.contactsSummary(arg("name"))
                "phone_notifications" ->
                    HermesNotificationListener.summary(context)
                "phone_calendar" ->
                    read.upcomingEvents(arg("hours").toIntOrNull() ?: 24)
                "phone_location" -> read.whereAmI()
                "phone_clipboard_read" -> read.clipboardRead()
                "phone_clipboard_write" -> read.clipboardWrite(arg("text"))
                "phone_messages" ->
                    MessageInbox.read(context, arg("app"), arg("chat"), arg("limit").toIntOrNull() ?: 10)
                "phone_reply" -> MessageInbox.reply(context, arg("app"), arg("chat"), arg("text"))
                "phone_notify" -> notifyUser(arg("title"), arg("text"))
                "phone_speak" -> speak(arg("text"))
                "phone_flashlight" -> flashlight(arg("state"))
                "phone_volume" -> volume(arg("action"))
                "phone_media" -> media(arg("action"))
                "phone_status" -> status(arg("what"))
                "phone_timer" -> timer(arg("minutes"))
                "phone_open_url" -> openUrl(arg("url"))
                "phone_share" -> share(arg("text"))
                // ── Shizuku gerektirenler ────────────────────────────
                "phone_wifi" -> shell("svc wifi ${onOff(arg("state"))}", "Wi-Fi")
                "phone_bluetooth" -> shell("svc bluetooth ${onOff(arg("state"))}", "Bluetooth")
                "phone_dnd" -> dnd(arg("state"))
                "phone_shell" -> rawShell(arg("command"))
                else -> "Bilinmeyen telefon aracı: $name"
            }
        } catch (e: ActivityNotFoundException) {
            tr("Bu işlemi yapabilecek uygulama bulunamadı.", "No app can handle that.")
        } catch (e: Exception) {
            "İşlem başarısız: ${e.message}"
        }
    }

    private fun launch(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    private fun openApp(query: String): String {
        if (query.isBlank()) return "Uygulama adı verilmedi."
        val pm = context.packageManager

        // Önce paket adı gibi mi diye bak, sonra görünen adda ara.
        pm.getLaunchIntentForPackage(query)?.let {
            launch(it)
            return tr("$query açıldı.", "Opened $query.")
        }

        val match = pm.getInstalledApplications(0).firstOrNull { app ->
            pm.getApplicationLabel(app).toString().equals(query, ignoreCase = true)
        } ?: pm.getInstalledApplications(0).firstOrNull { app ->
            pm.getApplicationLabel(app).toString().contains(query, ignoreCase = true)
        }

        val intent = match?.packageName?.let { pm.getLaunchIntentForPackage(it) }
            ?: return tr("\"$query\" adlı uygulama bulunamadı.", "No app named \"$query\".")
        launch(intent)
        return tr("${pm.getApplicationLabel(match)} açıldı.", "Opened ${pm.getApplicationLabel(match)}.")
    }

    /** Çevirici açılır — arama kullanıcının onayıyla başlar. */
    /**
     * Numara ya da **isim** kabul eder.
     *
     * "Ahmet'i ara" en doğal telefon komutu ama eskiden numara gerekiyordu.
     * İsim verilirse rehberde aranır; tek eşleşme varsa çeviriciye yazılır,
     * birden fazlaysa seçimi kullanıcıya bırakırız — yanlış kişiyi aramak
     * geri alınamaz.
     *
     * Güvenlik değişmedi: çevirici **açılıyor**, arama başlatılmıyor.
     */
    private fun dial(input: String): String {
        if (input.isBlank()) return tr("Numara verilmedi.", "No number given.")
        val looksNumeric = input.all { it.isDigit() || it in "+ ()-" }
        val number = if (looksNumeric) input else {
            val matches = read.findContacts(input)
            when {
                matches.isEmpty() -> return read.contactsSummary(input)
                matches.size > 1 -> return tr(
                    "Birden fazla kişi eşleşti, hangisi?\n", "Several contacts matched, which one?\n",
                ) + matches.joinToString("\n") { "${it.name} — ${it.number}" }
                else -> matches[0].number
            }
        }
        launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        return tr("$number çeviriciye yazıldı — aramayı sen başlat.", "$number is in the dialer — you place the call.")
    }

    /** SMS taslağı hazırlanır; gönderme kullanıcıya bırakılır. */
    private fun smsDraft(recipient: String, text: String): String {
        // dial ile ayni mantik: isim verildiyse rehberden cozuluyor.
        val number = when {
            recipient.isBlank() -> ""
            recipient.all { it.isDigit() || it in "+ ()-" } -> recipient
            else -> read.findContacts(recipient).firstOrNull()?.number ?: ""
        }
        val uri = Uri.parse(if (number.isBlank()) "smsto:" else "smsto:$number")
        launch(Intent(Intent.ACTION_SENDTO, uri).apply { putExtra("sms_body", text) })
        return tr("SMS taslağı hazır — göndermeden önce kontrol et.", "SMS draft ready — check it before sending.")
    }

    private fun navigate(destination: String): String {
        if (destination.isBlank()) return "Hedef verilmedi."
        val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
        return try {
            launch(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"))
            tr("$destination için yol tarifi başlatıldı.", "Navigation started to $destination.")
        } catch (e: ActivityNotFoundException) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(destination)}")))
            tr("$destination haritada açıldı.", "Opened $destination on the map.")
        }
    }

    /** `time` "07:30" biçiminde. */
    private fun setAlarm(time: String, label: String): String {
        val parts = time.split(":", ".").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size < 2) return "Saat anlaşılmadı ($time). 07:30 gibi yaz."
        launch(
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, parts[0])
                putExtra(AlarmClock.EXTRA_MINUTES, parts[1])
                if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            }
        )
        return tr("Alarm ekranı açıldı: $time", "Alarm screen opened for $time")
    }

    private fun addEvent(title: String, whenText: String): String {
        launch(
            Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                if (whenText.isNotBlank()) {
                    putExtra(CalendarContract.Events.DESCRIPTION, whenText)
                }
            }
        )
        return tr("Takvim kaydı ekranı açıldı: $title", "Calendar entry opened: $title")
    }

    private fun webSearch(query: String): String {
        launch(Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra("query", query) })
        return tr("\"$query\" araması açıldı.", "Searching for \"$query\".")
    }

    /**
     * Tasker görevi tetikler.
     *
     * Tasker'da **Tercihler → Çeşitli → Harici Erişime İzin Ver** açık olmalı;
     * kapalıysa yayın sessizce yutulur, o yüzden kullanıcıya bunu hatırlatıyoruz.
     */
    /**
     * Tasker görevini tetikler.
     *
     * ⚠️ Doğru eylem adı `net.dinglisch.android.taskerm.ACTION_TASK` — sonundaki
     * **m** paket adından geliyor (`taskerm`). Önce `...android.tasker.ACTION_TASK`
     * yazmıştım ve yayın sessizce yutuluyordu: Tasker onu hiç dinlemiyor, hata da
     * dönmüyor, o yüzden "tetiklendi" mesajı yanıltıcı oluyordu.
     *
     * Yine de ikisini de gönderiyoruz: bazı eski sürüm/çatallar kısa adı
     * dinliyor ve fazladan bir yayının maliyeti yok.
     *
     * Tasker'da **Tercihler → Çeşitli → Harici Erişime İzin Ver** açık olmalı;
     * kapalıysa yayın yine sessizce düşer.
     */
    private fun taskerTask(task: String, parameter: String): String {
        if (task.isBlank()) return tr("Tasker görev adı verilmedi.", "No Tasker task name given.")
        listOf(
            "net.dinglisch.android.taskerm.ACTION_TASK",
            "net.dinglisch.android.tasker.ACTION_TASK",
        ).forEach { action ->
            context.sendBroadcast(
                Intent(action).apply {
                    putExtra("task_name", task)
                    if (parameter.isNotBlank()) putExtra("%par1", parameter)
                    setPackage(TASKER_PKG)
                }
            )
        }
        return tr(
            "Tasker görevi tetiklendi: $task (çalışmazsa Tasker'da " +
                "'Harici Erişime İzin Ver' açık mı bak)",
            "Triggered Tasker task: $task (if nothing happens, check " +
                "'Allow External Access' in Tasker)",
        )
    }

    /** MacroDroid webhook — uygulamalar arası izin gerekmez, düz HTTP. */
    private fun macroDroid(url: String): String {
        if (!url.startsWith("http")) return "Geçerli bir webhook adresi verilmedi."
        return runCatching {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
                "MacroDroid tetiklendi (HTTP ${res.code})"
            }
        }.getOrElse { "MacroDroid tetiklenemedi: ${it.message}" }
    }

    /**
     * El feneri. Kamera izni gerektirmez — `setTorchMode` yalnız kamera
     * kimliğine ihtiyaç duyar.
     */
    private fun flashlight(state: String): String {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { camId ->
            cm.getCameraCharacteristics(camId)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return tr("Bu cihazda el feneri yok.", "This device has no flashlight.")
        val on = state.lowercase() !in setOf("off", "kapat", "kapali", "kapalı", "0", "false")
        cm.setTorchMode(id, on)
        return if (on) tr("El feneri açıldı.", "Flashlight on.") else tr("El feneri kapatıldı.", "Flashlight off.")
    }

    /**
     * Ses düzeyi. `adjustStreamVolume` kullanıcının bastığı ses tuşuyla aynı
     * yolu izliyor, o yüzden ek izin istemiyor.
     */
    private fun volume(action: String): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val flags = AudioManager.FLAG_SHOW_UI
        when (action.lowercase()) {
            "up", "arttir", "arttır", "yukselt", "yükselt", "ac", "aç" ->
                am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, flags)
            "down", "azalt", "kis", "kıs", "dusur", "düşür" ->
                am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, flags)
            "mute", "sustur", "sessiz" ->
                am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, flags)
            "unmute", "ac_ses" ->
                am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, flags)
            "max", "sonuna" ->
                am.setStreamVolume(stream, am.getStreamMaxVolume(stream), flags)
            else -> return "Ses için: arttır / azalt / sustur / max"
        }
        val cur = am.getStreamVolume(stream)
        val max = am.getStreamMaxVolume(stream)
        return tr("Ses seviyesi $cur/$max", "Volume $cur/$max")
    }

    /**
     * Medya denetimi — çalan uygulamaya tuş olayı gönderir.
     *
     * `dispatchMediaKeyEvent` sistemin o an etkin medya oturumuna gidiyor;
     * hangi uygulamanın çaldığını bilmemize gerek yok.
     */
    private fun media(action: String): String {
        val code = when (action.lowercase()) {
            "play", "pause", "oynat", "duraklat", "durdur" ->
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next", "sonraki", "ileri" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "prev", "previous", "onceki", "önceki", "geri" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return "Medya için: oynat / duraklat / sonraki / önceki"
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return tr("Medya komutu gönderildi: $action", "Media command sent: $action")
    }

    /**
     * Cihaz durumu — pil, ağ, depolama.
     *
     * Sunucudaki ajan bu bilgilere ulaşamıyor; telefonun kendisi anlatıyor.
     */
    /**
     * Kullaniciya bildirim gosterir.
     *
     * Ajanin kullaniciya ULASMASININ en dogrudan yolu: sohbeti acmasini
     * beklemek gerekmiyor. "Yedekleme bitti", "toplantiya 10 dakika" gibi.
     */
    private fun notifyUser(title: String, text: String): String {
        if (text.isBlank()) return tr("Bildirim metni bos.", "Notification text is empty.")
        Notifier.agentMessage(context, title.ifBlank { "Hermes" }, text)
        return tr("Bildirim gosterildi.", "Notification shown.")
    }

    /** Metni sesli okur -- kullanici telefona bakamiyorken. */
    private fun speak(text: String): String {
        if (text.isBlank()) return tr("Okunacak metin bos.", "Nothing to read out.")
        VoiceController.speakOnce(context, text)
        return tr("Sesli okundu.", "Read out loud.")
    }

    private fun status(what: String): String {
        val parts = mutableListOf<String>()
        val w = what.lowercase()
        val all = w.isBlank() || w == "all" || w == "hepsi"

        if (all || "pil" in w || "batarya" in w || "sarj" in w || "şarj" in w || "battery" in w) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = bm.isCharging
            parts += tr("Pil %$level", "Battery $level%") + if (charging) tr(" (şarjda)", " (charging)") else ""
        }
        if (all || "ag" in w || "ağ" in w || "wifi" in w || "internet" in w) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            parts += when {
                caps == null -> tr("Ağ yok", "No network")
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    tr("Wi-Fi bağlı", "Wi-Fi connected")
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                    tr("Mobil veri", "Mobile data")
                else -> tr("Bağlı", "Connected")
            }
        }
        if (all || "depolama" in w || "disk" in w || "yer" in w || "storage" in w) {
            val stat = android.os.StatFs(context.filesDir.absolutePath)
            val freeGb = stat.availableBytes / 1_000_000_000.0
            parts += String.format(tr("Boş alan %.1f GB", "%.1f GB free"), freeGb)
        }
        return parts.joinToString(" · ").ifBlank { tr("Durum okunamadı.", "Could not read status.") }
    }

    private fun timer(minutes: String): String {
        val m = minutes.filter { it.isDigit() }.toIntOrNull()
            ?: return "Kaç dakika olduğunu anlayamadım."
        launch(
            Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, m * 60)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
        )
        return tr("$m dakikalık sayaç kuruldu.", "$m-minute timer set.")
    }

    private fun openUrl(url: String): String {
        val full = if (url.startsWith("http")) url else "https://$url"
        launch(Intent(Intent.ACTION_VIEW, Uri.parse(full)))
        return tr("$full açıldı.", "Opened $full.")
    }

    /** Sistem paylaşım sayfası — metni istediği uygulamaya göndersin. */
    private fun share(text: String): String {
        if (text.isBlank()) return "Paylaşılacak metin yok."
        launch(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Paylaş",
            )
        )
        return tr("Paylaşım sayfası açıldı.", "Share sheet opened.")
    }

    // ── Shizuku katmanı ──────────────────────────────────────────────

    private fun onOff(state: String): String =
        if (state.lowercase() in setOf("off", "kapat", "kapali", "kapalı", "0", "false"))
            "disable" else "enable"

    /**
     * Shizuku ile kabuk komutu çalıştırır.
     *
     * `runBlocking` bilinçli: `execute()` eşzamanlı bir sözleşmeye sahip (hem
     * Gemini araç çağrısı hem yerel niyet katmanı sonucu bekliyor) ve komutlar
     * milisaniyeler sürüyor. Bunu askıya alınabilir yapmak iki çağrı yolunu da
     * yeniden yazmayı gerektirirdi.
     */
    private fun shell(command: String, label: String): String {
        val bridge = shizuku ?: return tr(
            "Bu iş için Shizuku gerekiyor (Ayarlar → Telefon denetimi).",
            "This needs Shizuku (Settings → Phone control).",
        )
        if (!bridge.isReady) return tr(
            "Shizuku hazır değil — uygulamasını açıp servisi başlat.",
            "Shizuku isn't ready — open its app and start the service.",
        )
        val out = kotlinx.coroutines.runBlocking { bridge.exec(command) }
        return out.fold(
            onSuccess = {
                val on = command.endsWith("enable")
                tr(
                    "$label ${if (on) "açıldı" else "kapatıldı"}.",
                    "$label turned ${if (on) "on" else "off"}.",
                )
            },
            onFailure = { e ->
                tr("$label değiştirilemedi: ${e.message}", "Could not change $label: ${e.message}")
            },
        )
    }

    /** Rahatsız Etme — `cmd notification set_dnd` ile. */
    private fun dnd(state: String): String {
        val mode = if (onOff(state) == "enable") "priority" else "off"
        val bridge = shizuku ?: return tr("Shizuku gerekiyor.", "Shizuku required.")
        if (!bridge.isReady) return tr("Shizuku hazır değil.", "Shizuku isn't ready.")
        val out = kotlinx.coroutines.runBlocking {
            bridge.exec("cmd notification set_dnd $mode")
        }
        return out.fold(
            onSuccess = {
                tr(
                    if (mode == "off") "Rahatsız etme kapatıldı." else "Rahatsız etme açıldı.",
                    if (mode == "off") "Do Not Disturb off." else "Do Not Disturb on.",
                )
            },
            onFailure = { e -> tr("Değiştirilemedi: ${e.message}", "Failed: ${e.message}") },
        )
    }

    /**
     * Ham kabuk komutu.
     *
     * Bilinçli olarak **beyaz liste yok**: Shizuku'yu açan kullanıcı kabuk
     * yetkisini zaten kabul etmiş oluyor ve yarım bir filtre yanlış güven
     * yaratır. Ama bu araç yalnız Shizuku açıkken tanıtılıyor.
     */
    private fun rawShell(command: String): String {
        if (command.isBlank()) return tr("Komut verilmedi.", "No command given.")
        val bridge = shizuku ?: return tr("Shizuku gerekiyor.", "Shizuku required.")
        if (!bridge.isReady) return tr("Shizuku hazır değil.", "Shizuku isn't ready.")
        return kotlinx.coroutines.runBlocking { bridge.exec(command) }
            .fold(onSuccess = { it.take(1500) }, onFailure = { "hata: ${it.message}" })
    }

    private fun openSettings(section: String): String {
        val action = when (section.lowercase()) {
            "wifi", "wi-fi", "kablosuz" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "ses", "sound" -> Settings.ACTION_SOUND_SETTINGS
            "ekran", "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "pil", "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "konum", "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "uygulama", "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        launch(Intent(action))
        return tr("Ayarlar açıldı ($section).", "Opened $section settings.")
    }
}
