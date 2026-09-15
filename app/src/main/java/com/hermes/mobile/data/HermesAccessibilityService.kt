package com.hermes.mobile.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * "Tam kontrol" katmanının cihaz tarafı.
 *
 * Ajanın ekranı okuyabilmesi, dokunabilmesi, yazabilmesi, jest yapabilmesi ve
 * ekran görüntüsü alabilmesi erişilebilirlik servisiyle oluyor. Kararların
 * hepsi burada, telefonda:
 *
 * - Servis **kendi kendine çalışmıyor**: kullanıcı Ayarlar → Erişilebilirlik'ten
 *   açmak zorunda. Uygulama bu izni programatik olarak veremez (Android'in
 *   bilinçli kısıtı) — ayar ekranı yalnız kısayol sunuyor, durum dürüstçe
 *   gösteriliyor.
 * - Üç anahtar birlikte gerekli: "Ajan telefonu kullanabilsin" + "Tam kontrol"
 *   açık olmalı, yazma eylemleri için salt-okunur **kapalı** olmalı. Karar
 *   [FullControl.guardReason]'da, tek yerde.
 * - Servis ayaktayken **kalıcı bildirim** durur; gizli bir uzaktan erişim
 *   kanalı olmamalı. Her eylem [DiagLog]'a yazılır.
 *
 * Kapsam dışı (bilerek): SMS gönderme, arama başlatma, uygulama kaldırma,
 * fabrika ayarları, kilitli ekranda PIN girişi. Bu katman telefonu
 * *kullanabiliyor*, geri alınamaz işler yapmıyor.
 */
class HermesAccessibilityService : AccessibilityService() {

    /** Ağaç dolaşırken üst sınır — patolojik bir pencere ağacı servisi kilitlemesin. */
    private val maxWalk = FullControl.MAX_NODES * 4

    /** Son pencere olayı: paket → o paketin son aktivitesi. */
    private val activityOf = HashMap<String, String>()
    private var lastPackage: String = ""
    private var lastActivity: String = ""

    private val screenshotExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "hermes-screenshot").apply { isDaemon = true }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo?.apply {
            // Yalnızca son pencere bilgisini tutuyoruz; olay başına iş yok.
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        DiagLog.i("a11y", "erişilebilirlik servisi bağlandı")
        refreshNotice(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString().orEmpty()
        if (pkg.isNotBlank()) lastPackage = pkg
        val cls = e.className?.toString().orEmpty()
        if (cls.isNotBlank()) {
            lastActivity = cls
            // Aktivite paket başına saklanıyor: klavye gibi yan pencereler
            // "son aktivite" alanını ezip ekranın kimliğini kaybettiriyordu.
            if (pkg.isNotBlank()) activityOf[pkg] = cls
        }
    }

    override fun onInterrupt() = Unit

    /** Öndeki paket — uygulama durdurma kararı bunu kullanıyor. */
    fun foregroundPackage(): String =
        lastPackage.ifBlank { root()?.packageName?.toString().orEmpty() }

    override fun onDestroy() {
        instance = null
        screenshotExecutor.shutdownNow()
        cancelNotice(this)
        DiagLog.w("a11y", "erişilebilirlik servisi koptu")
        super.onDestroy()
    }

    // ── Ağaç okuma ───────────────────────────────────────────────────

    /**
     * Etkin pencerenin kökü.
     *
     * `rootInActiveWindow` klavye açıkken IME penceresini döndürebiliyor ve o
     * ağaçta ekranın içeriği yok. Bu yüzden önce **etkin uygulama penceresi**
     * aranıyor; bulunamazsa eski yola düşülüyor.
     */
    private fun root(): AccessibilityNodeInfo? = try {
        val active = windows?.firstOrNull {
            it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION &&
                it.isActive
        } ?: windows?.firstOrNull {
            it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
        }
        active?.root ?: rootInActiveWindow
    } catch (e: Exception) {
        null
    }

    /**
     * Erişilebilirlik ağacını değişmez [FullControl.UiNode] modeline çevirir.
     *
     * Dönüştürme bilinçli: komutların geri kalanı Android nesnelerine
     * dokunmadan, test edilebilir saf kod olarak çalışsın.
     */
    private fun snapshot(
        node: AccessibilityNodeInfo,
        depth: Int,
        counter: IntArray,
    ): FullControl.UiNode {
        val r = android.graphics.Rect()
        node.getBoundsInScreen(r)
        val children = mutableListOf<FullControl.UiNode>()
        if (depth < 40) {
            for (i in 0 until node.childCount) {
                if (counter[0] >= maxWalk) break
                val child = node.getChild(i) ?: continue
                counter[0]++
                children += snapshot(child, depth + 1, counter)
            }
        }
        return FullControl.UiNode(
            text = node.text?.toString().orEmpty().trim(),
            desc = node.contentDescription?.toString().orEmpty().trim(),
            id = node.viewIdResourceName.orEmpty(),
            cls = node.className?.toString().orEmpty(),
            bounds = FullControl.Bounds(r.left, r.top, r.right, r.bottom),
            clickable = node.isClickable,
            editable = node.isEditable ||
                node.className?.toString()?.contains("EditText") == true,
            scrollable = node.isScrollable,
            focused = node.isFocused,
            visible = node.isVisibleToUser,
            children = children,
        )
    }

    private fun snapshot(): FullControl.UiNode? {
        val r = root() ?: return null
        return snapshot(r, 0, intArrayOf(0))
    }

    // ── Komutlar ─────────────────────────────────────────────────────

    /** `screen.dump` — pencere ağacı + paket/aktivite + insan özeti. */
    fun dumpScreen(): FullControl.Outcome {
        val rootNode = root() ?: return FullControl.fail(
            "Ekran okunamadı: erişilebilirlik servisi etkin pencerenin içeriğine ulaşamıyor. " +
                "Ayarlar → Erişilebilirlik → Hermes tam kontrol açık mı kontrol et.",
        )
        val snap = snapshot(rootNode, 0, intArrayOf(0))
        // Paket, ağacın kendi penceresinden okunuyor: olay akışı klavye gibi
        // yan pencerelerin paketini son yazan olabiliyor ve ajan "hangi
        // ekrandayım" sorusuna yanlış cevap alırdı.
        val pkg = rootNode.packageName?.toString().orEmpty().ifBlank { lastPackage }
        val activity = activityOf[pkg].orEmpty()
        return FullControl.done(FullControl.dump(snap, pkg, activity))
    }

    /** `tap` / `long_press` — koordinat, metin ya da id. */
    fun tap(args: JsonObject, long: Boolean): FullControl.Outcome {
        val target = FullControl.parseTap(
            x = args.str("x"), y = args.str("y"),
            text = args.str("text"), id = args.str("id"),
        )
        val duration = if (long) 700L else 60L

        if (target is FullControl.TapTarget.Point) {
            if (!press(target.x, target.y, duration)) {
                return FullControl.fail("(${target.x},${target.y}) noktasına dokunulamadı.")
            }
            Thread.sleep(250)
            return FullControl.done(
                "(${target.x},${target.y}) noktasına ${if (long) "uzun " else ""}basıldı · ${brief()}",
            )
        }
        if (target is FullControl.TapTarget.Invalid) {
            return FullControl.fail(
                "Hedef verilmedi: x/y koordinatı, metin ya da görünüm kimliği gerekli.",
            )
        }

        val snap = snapshot() ?: return FullControl.fail("Ekran okunamadı, hedef bulunamadı.")
        val node = when (target) {
            is FullControl.TapTarget.Label -> FullControl.matchLabel(snap, target.query)
            is FullControl.TapTarget.ViewId -> FullControl.matchId(snap, target.id)
            else -> null
        } ?: return FullControl.fail(
            "Ekranda bulunamadı: " + when (target) {
                is FullControl.TapTarget.Label -> "\"${target.query}\" metin/açıklaması"
                is FullControl.TapTarget.ViewId -> "id=${target.id}"
                else -> "?"
            } + ". Önce screen.dump ile ekranı oku.",
        )

        val label = node.label.ifBlank { node.id }
        // Önce gerçek düğüm eylemi: kaydırma kabuğuna değil öğenin kendisine
        // basmak için tıklanabilir üst öğeye kadar çıkıyoruz.
        if (!long && clickByNode(node)) {
            Thread.sleep(250)
            return FullControl.done("\"$label\" öğesine basıldı (düğüm eylemi) · ${brief()}")
        }
        if (!press(node.bounds.centerX, node.bounds.centerY, duration)) {
            return FullControl.fail("\"$label\" öğesine dokunulamadı (jest reddedildi).")
        }
        Thread.sleep(250)
        return FullControl.done(
            "\"$label\" öğesine (${node.bounds.centerX},${node.bounds.centerY}) " +
                "${if (long) "uzun " else ""}basıldı · ${brief()}",
        )
    }

    /**
     * Tıklanabilir düğüm eylemi.
     *
     * Düğümün kendisi tıklanabilir değilse (metin düğümleri genelde değil)
     * üst öğelere bakıyoruz — dokunmayı jeste çevirmek yalnız bu yollar
     * tükendiğinde yapılır.
     */
    private fun clickByNode(node: FullControl.UiNode): Boolean {
        var cur: AccessibilityNodeInfo? = findLive(node) ?: return false
        var hops = 0
        while (cur != null && hops < 6) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent
            hops++
        }
        return false
    }

    /**
     * Değişmez modelden canlı düğümü bulur.
     *
     * Aynı sınır kutusunu paylaşan birden fazla düğüm olabiliyor: bir metin
     * alanı ile onu saran kapsayıcı neredeyse her zaman aynı dikdörtgene
     * sahip. Sınırla yetinmek yanlış düğümü seçer ve `SET_TEXT` sessizce
     * reddedilir. Bu yüzden eşleşme **puanlanıyor**: id, sınıf, metin,
     * düzenlenebilirlik ve tıklanabilirlik aynıysa düğüm daha değerli.
     */
    private fun findLive(target: FullControl.UiNode): AccessibilityNodeInfo? {
        val r = root() ?: return null
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(r)
        var visited = 0
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0
        while (stack.isNotEmpty() && visited < maxWalk) {
            val n = stack.removeLast()
            visited++
            val rect = android.graphics.Rect()
            n.getBoundsInScreen(rect)
            if (rect.left == target.bounds.left && rect.top == target.bounds.top &&
                rect.right == target.bounds.right && rect.bottom == target.bounds.bottom
            ) {
                var score = 1
                if (target.id.isNotBlank() && n.viewIdResourceName == target.id) score += 4
                if (target.cls.isNotBlank() && n.className?.toString() == target.cls) score += 2
                if (n.text?.toString()?.trim() == target.text) score += 2
                if (n.isEditable == target.editable) score += 2
                if (n.isClickable == target.clickable) score += 1
                if (score > bestScore) {
                    bestScore = score
                    best = n
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.addLast(it) }
        }
        return best
    }

    /** Ağaçta ilk düzenlenebilir düğüm — `type` yedeği. */
    private fun firstEditable(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 20) return null
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            if (c.isEditable) return c
            firstEditable(c, depth + 1)?.let { return it }
        }
        return null
    }

    /** `swipe` — koordinat ya da yön kısayolu. */
    fun swipe(args: JsonObject): FullControl.Outcome {
        val snap = snapshot()
        val size = snap?.let { FullControl.screenSize(it) }
        val w = size?.substringBefore('x')?.toIntOrNull() ?: 0
        val h = size?.substringAfter('x')?.toIntOrNull() ?: 0
        if (w <= 0 || h <= 0) {
            return FullControl.fail("Ekran ölçüsü okunamadı, kaydırma yapılamadı.")
        }
        val g = FullControl.parseSwipe(
            x1 = args.str("x1"), y1 = args.str("y1"),
            x2 = args.str("x2"), y2 = args.str("y2"),
            duration = args.str("duration"), direction = args.str("direction"),
            width = w, height = h,
        ) ?: return FullControl.fail(
            "Kaydırma yönü anlaşılmadı. direction: yukari/asagi/sol/sag ya da x1,y1,x2,y2 ver.",
        )
        val before = topLabels(snap)
        if (!drag(g)) return FullControl.fail("Kaydırma jesti reddedildi.")
        // Küçük bir bekleme olmadan ikinci döküm eski hâli gösterebilir.
        Thread.sleep(400)
        val after = topLabels(snapshot())
        val changed = before != after
        return FullControl.done(
            "Kaydırıldı (${g.x1},${g.y1}) → (${g.x2},${g.y2}) ${g.durationMs}ms · " +
                if (changed) "içerik değişti: ${trim(after)}"
                else "içerik aynı görünüyor (liste sonu olabilir)",
        )
    }

    private fun topLabels(snap: FullControl.UiNode?): List<String> =
        if (snap == null) emptyList() else FullControl.visibleLabels(snap, 12)

    private fun trim(labels: List<String>): String =
        labels.take(6).joinToString(" | ").take(240)

    /** `type` — odaklı/düzenlenebilir alana metin. */
    fun typeText(args: JsonObject): FullControl.Outcome {
        val text = args.str("text")
        if (text.isNullOrEmpty()) return FullControl.fail("Yazılacak metin verilmedi.")
        val snap = snapshot() ?: return FullControl.fail("Ekran okunamadı.")
        val target = FullControl.editableTarget(snap)
            ?: return FullControl.fail(
                "Ekranda yazı alanı yok. Önce bir arama/mesaj alanına dokunup tekrar dene.",
            )
        // Puanlı eşleşme doğru düğümü verse de, kimi uygulamalar metni saran
        // bir kapsayıcıyı düzenlenebilir gibi bildiriyor ve `SET_TEXT`'i
        // reddediyor. Sıralı deneme: önce eşleşen düğüm, sonra içindeki
        // gerçek metin alanı. Her adımın sonucu çağırana yazılıyor.
        val found = findLive(target)
            ?: return FullControl.fail("Yazı alanı canlı ağaçta bulunamadı.")
        val candidates = listOfNotNull(found, firstEditable(found)).distinct()
        val label = target.label.ifBlank { target.id.ifBlank { "yazı alanı" } }
        val append = args.str("mode").equals("append", true)
        val value = if (append) target.text + text else text

        val bundle = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value,
            )
        }
        for (live in candidates) {
            if (!live.isEditable && live !== found) continue
            val okNode = live.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            if (okNode) {
                Thread.sleep(250)
                val now = FullControl.editableTarget(snapshot() ?: target)?.text.orEmpty()
                return FullControl.done(
                    "\"$label\" alanına yazıldı: \"$value\" · alan şimdi: \"$now\"",
                )
            }
        }

        // Yedek yol: panoya koyup yapıştır. Bazı uygulamalar SET_TEXT'i
        // reddediyor ama YAPIŞTIR'ı kabul ediyor.
        for (live in candidates) {
            val pasted = runCatching {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("hermes", value))
                live.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }.getOrDefault(false)
            if (pasted) {
                Thread.sleep(250)
                val now = FullControl.editableTarget(snapshot() ?: target)?.text.orEmpty()
                return FullControl.done(
                    "\"$label\" alanına pano üzerinden yapıştırıldı · alan şimdi: \"$now\"",
                )
            }
        }
        return FullControl.fail(
            "\"$label\" alanına yazılamadı: alan SET_TEXT ve YAPIŞTIR'ı reddetti " +
                "(bazı arama alanları yalnız klavye girişini kabul ediyor).",
        )
    }

    /** `key` — enter / backspace / space / tab / escape. */
    fun key(args: JsonObject): FullControl.Outcome {
        val name = args.str("key").orEmpty()
        val kind = FullControl.keyKind(name)
            ?: return FullControl.fail(
                "Bilinmeyen tuş: \"$name\". enter/backspace/space/tab/escape.",
            )
        if (kind !in FullControl.SUPPORTED_KEYS) {
            return FullControl.fail(
                "Tuş desteklenmiyor: ${kind.name.lowercase()}. Desteklenenler: " +
                    FullControl.SUPPORTED_KEYS.joinToString(", ") { it.name.lowercase() } + ".",
            )
        }
        val snap = snapshot() ?: return FullControl.fail("Ekran okunamadı.")
        val target = FullControl.editableTarget(snap)

        when (kind) {
            FullControl.KeyKind.ESCAPE -> {
                if (!global(FullControl.GlobalKind.BACK)) {
                    return FullControl.fail("ESC → geri uygulanamadı.")
                }
                Thread.sleep(350)
                return FullControl.done("ESC gönderildi (geri) · ${brief()}")
            }
            FullControl.KeyKind.ENTER -> {
                if (Build.VERSION.SDK_INT < 30) {
                    return FullControl.fail(
                        "Enter tuşu Android 11+ gerektiriyor (bu cihaz ${Build.VERSION.SDK_INT}).",
                    )
                }
                val live = target?.let { findLive(it) }
                    ?: return FullControl.fail("Odaklı yazı alanı yok, Enter gönderilemedi.")
                // `ACTION_IME_ENTER` SDK'da yalnız AccessibilityAction olarak
                // var (düz int sabiti yok); `getId()` API 21'den beri açık.
                val done = live.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id,
                )
                Thread.sleep(350)
                return if (done) {
                    FullControl.done("Enter gönderildi · ${brief()}")
                } else {
                    FullControl.fail("Enter gönderilemedi (alan IME eylemini reddetti).")
                }
            }
            FullControl.KeyKind.BACKSPACE, FullControl.KeyKind.DELETE -> {
                val live = target?.let { findLive(it) }
                    ?: return FullControl.fail("Odaklı yazı alanı yok, silme yapılamadı.")
                val cur = target.text
                if (cur.isEmpty()) return FullControl.fail("Yazı alanı zaten boş.")
                val next = if (kind == FullControl.KeyKind.DELETE) "" else cur.dropLast(1)
                val done = live.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    android.os.Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            next,
                        )
                    },
                )
                if (!done) return FullControl.fail("Silme uygulanamadı (alan SET_TEXT'i reddetti).")
                Thread.sleep(200)
                val now = FullControl.editableTarget(snapshot() ?: snap)?.text.orEmpty()
                return FullControl.done(
                    (if (kind == FullControl.KeyKind.DELETE) "Tümü silindi" else "Son karakter silindi") +
                        " · alan şimdi: \"$now\"",
                )
            }
            FullControl.KeyKind.SPACE, FullControl.KeyKind.TAB -> {
                val live = target?.let { findLive(it) }
                    ?: return FullControl.fail("Odaklı yazı alanı yok.")
                val add = if (kind == FullControl.KeyKind.SPACE) " " else "\t"
                val done = live.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    android.os.Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            target.text + add,
                        )
                    },
                )
                if (!done) return FullControl.fail("${kind.name.lowercase()} uygulanamadı.")
                return FullControl.done("${kind.name.lowercase()} eklendi.")
            }
            else -> return FullControl.fail("Tuş desteklenmiyor: ${kind.name.lowercase()}.")
        }
    }

    /** `global` — geri / ana ekran / son uygulamalar / bildirim paneli. */
    fun global(kind: FullControl.GlobalKind): Boolean {
        val action = when (kind) {
            FullControl.GlobalKind.BACK -> GLOBAL_ACTION_BACK
            FullControl.GlobalKind.HOME -> GLOBAL_ACTION_HOME
            FullControl.GlobalKind.RECENTS -> GLOBAL_ACTION_RECENTS
            FullControl.GlobalKind.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            FullControl.GlobalKind.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
            FullControl.GlobalKind.LOCK ->
                if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else return false
        }
        return runCatching { performGlobalAction(action) }.getOrDefault(false)
    }

    fun globalNamed(name: String): FullControl.Outcome {
        val kind = FullControl.globalKind(name)
            ?: return FullControl.fail(
                "Bilinmeyen global eylem: \"$name\". geri/home/recents/bildirimler/hizliayarlar.",
            )
        if (!global(kind)) return FullControl.fail("Global eylem uygulanamadı: ${kind.name.lowercase()}.")
        Thread.sleep(500)
        return FullControl.done("${kind.name.lowercase()} uygulandı · ${brief()}")
    }

    /** Kısa durum özeti — her cevabın sonunda "sonuç ne oldu" bilgisi. */
    private fun brief(): String {
        val snap = snapshot() ?: return "ekran okunamadı"
        return FullControl.summary(
            snap,
            lastPackage.ifBlank { "?" },
            lastActivity,
            maxLabels = 4,
        )
    }

    // ── Ekran görüntüsü ──────────────────────────────────────────────

    /**
     * JPEG + base64.
     *
     * `takeScreenshot` Android 11 (API 30) ve üstünde var. Kare hedefi
     * aşarsa [FullControl.shrinkSteps] planına göre küçültülüp yeniden
     * kodlanıyor. Sonuç: `{"ok":…,"image_base64":…,"width":…,"height":…}`.
     */
    fun screenshot(): FullControl.Outcome {
        if (Build.VERSION.SDK_INT < 30) {
            return FullControl.fail(
                "Ekran görüntüsü Android 11+ gerektiriyor (bu cihaz ${Build.VERSION.SDK_INT}).",
            )
        }
        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null
        var failure: String? = null

        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            screenshotExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    runCatching {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        // Donanım bitmap'i JPEG'e kodlanamaz; kopyası alınmalı.
                        bitmap = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        result.hardwareBuffer.close()
                    }.onFailure { failure = it.message }
                    latch.countDown()
                }

                override fun onFailure(errorCode: Int) {
                    failure = "takeScreenshot hata kodu $errorCode"
                    latch.countDown()
                }
            },
        )

        if (!latch.await(6, TimeUnit.SECONDS)) {
            return FullControl.fail("Ekran görüntüsü 6 sn içinde gelmedi.")
        }
        val shot = bitmap
            ?: return FullControl.fail("Ekran görüntüsü alınamadı: ${failure ?: "bilinmeyen hata"}")

        val first = encode(shot, 1.0f, 85)
            ?: return FullControl.fail("Kare JPEG'e çevrilemedi.")
        var bytes = first
        var usedScale = 1.0f
        if (first.size > FullControl.SCREENSHOT_TARGET_BYTES) {
            for ((scale, quality) in FullControl.shrinkSteps(first.size)) {
                val next = encode(shot, scale, quality) ?: continue
                bytes = next
                usedScale = scale
                if (next.size <= FullControl.SCREENSHOT_TARGET_BYTES) break
            }
        }
        val w = max(1, (shot.width * usedScale).toInt())
        val h = max(1, (shot.height * usedScale).toInt())
        DiagLog.i("a11y", "screenshot ${bytes.size}B ${w}x$h")

        return FullControl.done(
            buildJsonObject {
                put("bytes", JsonPrimitive(bytes.size))
                put("width", JsonPrimitive(w))
                put("height", JsonPrimitive(h))
                put("scale", JsonPrimitive(usedScale.toDouble()))
                put(
                    "image_base64",
                    JsonPrimitive(Base64.encodeToString(bytes, Base64.NO_WRAP)),
                )
            }.toString(),
        )
    }

    private fun encode(bitmap: Bitmap, scale: Float, quality: Int): ByteArray? = runCatching {
        val bmp = if (scale >= 0.999f) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * scale).toInt()),
                max(1, (bitmap.height * scale).toInt()),
                true,
            )
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }.getOrNull()

    // ── Jestler ──────────────────────────────────────────────────────

    /**
     * Jestle dokunma.
     *
     * `dispatchGesture` erişilebilirlik servisinin kendi yolu; `input tap`
     * gibi kabuk/root yetkisi gerektirmiyor.
     */
    private fun press(x: Int, y: Int, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatch(GestureDescription.Builder().addStroke(stroke).build(), 3)
    }

    /** İki noktalı kaydırma jesti. */
    private fun drag(g: FullControl.Gesture): Boolean {
        val path = Path().apply {
            moveTo(g.x1.toFloat(), g.y1.toFloat())
            lineTo(g.x2.toFloat(), g.y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, g.durationMs)
        return dispatch(GestureDescription.Builder().addStroke(stroke).build(), 4)
    }

    private fun dispatch(gesture: GestureDescription, waitSeconds: Long): Boolean {
        val latch = CountDownLatch(1)
        var done = false
        val sent = runCatching {
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) {
                        done = true
                        latch.countDown()
                    }

                    override fun onCancelled(g: GestureDescription?) {
                        done = false
                        latch.countDown()
                    }
                },
                null,
            )
        }.getOrDefault(false)
        if (!sent) return false
        latch.await(waitSeconds, TimeUnit.SECONDS)
        return done
    }

    // ── Bildirimler ──────────────────────────────────────────────────

    companion object {
        private const val NOTICE_ID = 4814
        private const val NOTICE_CHANNEL = "hermes-full-control"

        @Volatile
        private var instance: HermesAccessibilityService? = null

        /** Servis ayakta mı — yoksa komutlar sessizce başarısız olmamalı. */
        fun isRunning(): Boolean = instance != null

        /**
         * Ayarlarda izin verilmiş mi.
         *
         * [isRunning]'den farkı: izin verilmiş ama sistem servisi henüz
         * bağlamamış olabilir. İki ayrı soru, ikisi de cevaplanmalı.
         */
        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as? android.view.accessibility.AccessibilityManager ?: return false
            val want = "${context.packageName}/${HermesAccessibilityService::class.java.name}"
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.id == want }
        }

        /** Erişilebilirlik izni sihirbazı: sistem ayar sayfası. */
        fun openPermissionSettings(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }

        fun instanceOrNull(): HermesAccessibilityService? = instance

        private fun notifier(context: Context): NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        /** "Tam kontrol açık" kalıcı bildirimi — ayara göre göster/kaldır. */
        fun refreshNotice(context: Context) {
            val s = SettingsStore(context).settings.value
            if (s.fullControl && s.agentMayUsePhone) showNotice(context) else cancelNotice(context)
        }

        private fun showNotice(context: Context) {
            val nm = notifier(context)
            nm.createNotificationChannel(
                NotificationChannel(
                    NOTICE_CHANNEL,
                    com.hermes.mobile.ui.tr("Tam kontrol", "Full control"),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, com.hermes.mobile.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n: Notification = NotificationCompat.Builder(context, NOTICE_CHANNEL)
                .setSmallIcon(com.hermes.mobile.R.drawable.ic_stat_hermes)
                .setContentTitle(
                    com.hermes.mobile.ui.tr("Hermes tam kontrol aktif", "Hermes full control is on"),
                )
                .setContentText(
                    com.hermes.mobile.ui.tr("kapatmak için dokun", "tap to turn it off"),
                )
                .setOngoing(true)
                .setContentIntent(open)
                .build()
            runCatching { nm.notify(NOTICE_ID, n) }
        }

        fun cancelNotice(context: Context) {
            runCatching { notifier(context).cancel(NOTICE_ID) }
        }
    }
}

/** JSON gövdesinden metin argümanı. */
fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
