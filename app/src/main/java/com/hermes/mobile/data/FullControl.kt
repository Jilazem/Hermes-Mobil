package com.hermes.mobile.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * "Tam kontrol" katmanının **saf** çekirdeği.
 *
 * Ekran okuma, dokunma, yazma ve jest komutlarının tamamı burada çözülüyor;
 * Android'e dokunan kısım yalnız [HermesAccessibilityService] içinde. Böylece
 * ayrıştırma/koruma/boyut kuralları cihazsız birim testleriyle koşabiliyor —
 * bu katmanda yanlış bir eşleşme gerçek bir dokunuşa dönüşür, o yüzden
 * test edilebilirlik isteğe bağlı değil.
 *
 * Karar mercii hâlâ telefondur: buradaki [guardReason] hangi aracın
 * çalışacağını üç anahtara bakarak söylüyor (ajan erişimi, tam kontrol,
 * salt-okunur). Köprü yalnız taşıyor.
 */
object FullControl {

    // ── Araç adları ──────────────────────────────────────────────────
    const val DUMP = "screen.dump"
    const val TAP = "tap"
    const val LONG_PRESS = "long_press"
    const val SWIPE = "swipe"
    const val TYPE = "type"
    const val KEY = "key"
    const val GLOBAL = "global"
    const val SCREENSHOT = "screenshot"
    const val APPS = "apps.list"
    const val APP_START = "app.start"
    const val APP_STOP = "app.stop"

    /**
     * Yalnız **okuyan** araçlar.
     *
     * Salt-okunur kipte bunlar çalışmaya devam eder: ajan telefonu görebilsin
     * ama değiştirmesin demek, görmeyi de yasaklamak değil. Ekran görüntüsü ve
     * pencere ağacı bu kapsamda.
     */
    val READ_TOOLS = setOf(DUMP, SCREENSHOT, APPS)

    /**
     * Cihazı **değiştiren** araçlar — salt-okunur kipte reddedilir.
     *
     * Geri alınamaz eylemler (SMS gönderme, arama başlatma, uygulama kaldırma,
     * fabrika ayarları, kilitli ekranda PIN girme) bilerek YOK; son dokunuş
     * her zaman kullanıcıda kalıyor.
     */
    val WRITE_TOOLS = setOf(
        TAP, LONG_PRESS, SWIPE, TYPE, KEY, GLOBAL, APP_START, APP_STOP,
    )

    val ALL_TOOLS: Set<String> = READ_TOOLS + WRITE_TOOLS

    fun isFullControlTool(name: String) = name in ALL_TOOLS

    /**
     * Ajana duyurulacak ek araçlar.
     *
     * İki anahtar birden gerekiyor: kanal (ajan telefonu kullanabilsin) ve
     * tam kontrol. Kapalı bir aracı duyurmak, modelin onu denemesine ve
     * boşuna "yapamıyorum" demesine yol açıyor. Karar burada, tek yerde.
     */
    fun advertise(mayUsePhone: Boolean, fullControl: Boolean): Set<String> =
        if (mayUsePhone && fullControl) ALL_TOOLS else emptySet()

    /**
     * Araç sonucu.
     *
     * Köprü `ok` alanını ajanın "yaptım" ile "yapamadım"ı ayırması için
     * kullanıyor; başarısız bir eylem sessizce başarılı görünmemeli.
     */
    data class Outcome(val ok: Boolean, val text: String)

    fun done(text: String) = Outcome(true, text)
    fun fail(text: String) = Outcome(false, text)

    // ── Koruma ───────────────────────────────────────────────────────

    /** Reddedilme sebepleri — köprünün `denied:` metniyle aynı dil. */
    const val WHY_AGENT_OFF = "agent access is off"
    const val WHY_FULL_CONTROL_OFF = "full control is off"
    const val WHY_READ_ONLY = "read-only mode is on"

    /**
     * Araç çalışabilir mi? `null` = serbest, aksi hâlde red sebebi.
     *
     * Sıra önemli: önce kanalın kapalı olup olmadığı söylenir, sonra tam
     * kontrol anahtarı, en son salt-okunur. Ajan böylece kullanıcıya
     * açması gereken **tek** şeyi söyleyebiliyor.
     */
    fun guardReason(
        mayUsePhone: Boolean,
        readOnly: Boolean,
        fullControl: Boolean,
        tool: String,
    ): String? = when {
        !mayUsePhone -> WHY_AGENT_OFF
        !fullControl -> WHY_FULL_CONTROL_OFF
        tool in WRITE_TOOLS && readOnly -> WHY_READ_ONLY
        else -> null
    }

    // ── Dokunma hedefi ───────────────────────────────────────────────

    sealed interface TapTarget {
        /** Ham ekran koordinatı. */
        data class Point(val x: Int, val y: Int) : TapTarget

        /** Görünen metin ya da içerik açıklaması. */
        data class Label(val query: String) : TapTarget

        /** Görünüm kimliği (`com.paket:id/dugme`). */
        data class ViewId(val id: String) : TapTarget

        /** Hiçbir hedef verilmedi — çağrı reddedilir, tahmin edilmez. */
        data object Invalid : TapTarget
    }

    /**
     * `tap` argümanlarını hedefe çevirir.
     *
     * Öncelik koordinat → id → metin. Koordinat verilmişse arama yapılmaz:
     * kullanıcı/ajan zaten nereye basacağını söylemiş.
     */
    fun parseTap(x: String?, y: String?, text: String?, id: String?): TapTarget {
        val xi = x?.trim()?.toIntOrNull()
        val yi = y?.trim()?.toIntOrNull()
        if (xi != null && yi != null) return TapTarget.Point(xi, yi)
        if (!id.isNullOrBlank()) return TapTarget.ViewId(id.trim())
        if (!text.isNullOrBlank()) return TapTarget.Label(text.trim())
        return TapTarget.Invalid
    }

    // ── Düğüm ağacı ──────────────────────────────────────────────────

    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val centerX get() = (left + right) / 2
        val centerY get() = (top + bottom) / 2
        val width get() = right - left
        val height get() = bottom - top
        val area get() = width * height
        fun contains(x: Int, y: Int) = x >= left && x <= right && y >= top && y <= bottom
    }

    /**
     * Ekrandaki tek bir erişilebilirlik düğümü.
     *
     * Android `AccessibilityNodeInfo`'nun kopyası — servis ağacı bir kez
     * gezip bu değişmez modele çeviriyor, sonrası saf kod.
     */
    data class UiNode(
        val text: String = "",
        val desc: String = "",
        val id: String = "",
        val cls: String = "",
        val bounds: Bounds,
        val clickable: Boolean = false,
        val editable: Boolean = false,
        val scrollable: Boolean = false,
        val focused: Boolean = false,
        /**
         * Kullanıcıya gerçekten görünüyor mu.
         *
         * Kaydırılabilir listeler ekran dışındaki satırları da ağaçta tutuyor;
         * "kaydırdım, bir şey değişti mi" sorusunun cevabı ancak görünen
         * düğümlere bakınca anlamlı oluyor.
         */
        val visible: Boolean = true,
        val children: List<UiNode> = emptyList(),
    ) {
        /** Arama için birleşik etiket — metin ve açıklama birlikte. */
        val label: String get() = listOf(text, desc).filter { it.isNotBlank() }.joinToString(" ")
    }

    /** Ağaçtaki her düğümü kökten yaprağa gezer. */
    fun walk(root: UiNode): Sequence<UiNode> = sequence {
        yield(root)
        for (child in root.children) yieldAll(walk(child))
    }

    /**
     * Metne ya da açıklamaya göre düğüm seçer.
     *
     * Sıralama kasıtlı: **tam eşleşme** > içerir; eşitlikte **tıklanabilir** >
     * tıklanamaz; sonra **küçük alan** > büyük. Küçük alan tercihi, bir
     * listede "Ayarlar" yazan satırı tüm listeyi kapsayan kapsayıcıya yeğler.
     * Aksi hâlde dokunuş doğru görünen ama işe yaramayan bir kabuğa giderdi.
     */
    fun matchLabel(root: UiNode, query: String): UiNode? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val candidates = walk(root)
            .filter { it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { it.text.contains(q, true) || it.desc.contains(q, true) }
            .toList()
        if (candidates.isEmpty()) return null
        val exact = candidates.filter {
            it.text.equals(q, true) || it.desc.equals(q, true)
        }
        val pool = exact.ifEmpty { candidates }
        return pool.sortedWith(
            compareByDescending<UiNode> { it.clickable }
                .thenByDescending { it.focused }
                .thenBy { it.bounds.area },
        ).first()
    }

    /** Görünüm kimliğine göre — tam eşleşme, sonra son-ek parça eşleşmesi. */
    fun matchId(root: UiNode, id: String): UiNode? {
        val q = id.trim()
        if (q.isEmpty()) return null
        val all = walk(root).filter { it.bounds.width > 0 && it.bounds.height > 0 }.toList()
        return all.firstOrNull { it.id.equals(q, true) }
            ?: all.firstOrNull { it.id.endsWith(":id/$q") || it.id.endsWith("/$q") }
    }

    /** Yazının gideceği alan: odaklı düzenlenebilir → ilk düzenlenebilir. */
    fun editableTarget(root: UiNode): UiNode? {
        val editables = walk(root)
            .filter { it.editable && it.bounds.width > 0 && it.bounds.height > 0 }
            .toList()
        return editables.firstOrNull { it.focused } ?: editables.firstOrNull()
    }

    // ── Jestler ──────────────────────────────────────────────────────

    data class Gesture(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val durationMs: Long)

    /** Sayfa kaydırma kısayolları — parmak hareketi yönü. */
    val SWIPE_UP = setOf("up", "yukari", "yukarı", "yuxari")
    val SWIPE_DOWN = setOf("down", "asagi", "aşağı", "asagi")
    val SWIPE_LEFT = setOf("left", "sol", "sola")
    val SWIPE_RIGHT = setOf("right", "sag", "sağ", "saga", "sağa")

    /** Yön adı verilirse ekranın oranına göre jest üretir. */
    fun directionGesture(direction: String, width: Int, height: Int): Gesture? {
        val d = foldTr(direction.trim())
        val cx = width / 2
        val cy = height / 2
        return when (d) {
            in SWIPE_UP -> Gesture(cx, (height * 0.75).toInt(), cx, (height * 0.25).toInt(), 300)
            in SWIPE_DOWN -> Gesture(cx, (height * 0.25).toInt(), cx, (height * 0.75).toInt(), 300)
            in SWIPE_LEFT -> Gesture((width * 0.85).toInt(), cy, (width * 0.15).toInt(), cy, 300)
            in SWIPE_RIGHT -> Gesture((width * 0.15).toInt(), cy, (width * 0.85).toInt(), cy, 300)
            else -> null
        }
    }

    /**
     * `swipe` argümanlarından jest üretir.
     *
     * Koordinat dördü de verilmişse doğrudan kullanılır (kaydırma süresi
     * verilebilir); aksi hâlde yön adı beklenir. İkisi de yoksa `null` —
     * rastgele bir jest üretmek ekranda istenmeyen bir yere dokunmak olurdu.
     */
    fun parseSwipe(
        x1: String?, y1: String?, x2: String?, y2: String?,
        duration: String?, direction: String?,
        width: Int, height: Int,
    ): Gesture? {
        val a = x1?.trim()?.toIntOrNull()
        val b = y1?.trim()?.toIntOrNull()
        val c = x2?.trim()?.toIntOrNull()
        val e = y2?.trim()?.toIntOrNull()
        val dur = duration?.trim()?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(50, 5000)
        if (a != null && b != null && c != null && e != null) {
            return Gesture(a, b, c, e, (dur ?: 300).toLong())
        }
        val g = directionGesture(direction.orEmpty(), width, height) ?: return null
        return if (dur != null) g.copy(durationMs = dur.toLong()) else g
    }

    // ── Tuşlar ───────────────────────────────────────────────────────

    enum class KeyKind { ENTER, BACKSPACE, SPACE, TAB, ESCAPE, DELETE, MOVE_HOME, MOVE_END, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT }

    /**
     * Gerçekten uygulanabilen tuşlar.
     *
     * Erişilebilirlik API'si yalnız bunlar için bir yol sunuyor: metin
     * eylemleri (SET_TEXT) ve IME Enter. Yön tuşları ile imleç başı/sonu
     * gönderilemiyor — desteklenmiyormuş gibi yapıp sessizce hiçbir şey
     * yapmamak yerine adıyla reddediliyor.
     */
    val SUPPORTED_KEYS = setOf(KeyKind.ENTER, KeyKind.BACKSPACE, KeyKind.SPACE, KeyKind.TAB, KeyKind.ESCAPE)

    fun keyKind(name: String): KeyKind? = when (foldTr(name.trim())) {
        "enter", "gir", "satir", "satır", "newline", "done", "tamam" -> KeyKind.ENTER
        "backspace", "sil", "geri_sil", "delete_back" -> KeyKind.BACKSPACE
        "space", "bosluk", "boşluk" -> KeyKind.SPACE
        "tab", "sekme" -> KeyKind.TAB
        "escape", "esc", "iptal" -> KeyKind.ESCAPE
        "delete", "temizle" -> KeyKind.DELETE
        "home", "bas", "baş", "satirbasi" -> KeyKind.MOVE_HOME
        "end", "son", "satirsonu" -> KeyKind.MOVE_END
        "up", "yukari", "yukarı" -> KeyKind.DPAD_UP
        "down", "asagi", "aşağı" -> KeyKind.DPAD_DOWN
        "left", "sol" -> KeyKind.DPAD_LEFT
        "right", "sag", "sağ" -> KeyKind.DPAD_RIGHT
        else -> null
    }

    // ── Global eylemler ──────────────────────────────────────────────

    enum class GlobalKind { BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, LOCK }

    fun globalKind(name: String): GlobalKind? = when (foldTr(name.trim())) {
        "back", "geri", "geridon", "gerı" -> GlobalKind.BACK
        "home", "anaekran", "ana ekran", "ana_ekran", "masaustu", "masaüstü" -> GlobalKind.HOME
        "recents", "son", "sonuygulamalar", "son uygulamalar", "son_uygulamalar", "appswitch" ->
            GlobalKind.RECENTS
        "notifications", "bildirim", "bildirimler", "panels" -> GlobalKind.NOTIFICATIONS
        "quick", "quicksettings", "hizliayarlar", "hizli ayarlar", "hızlıayarlar", "hızlı ayarlar",
        "ayarpaneli", "ayar paneli" -> GlobalKind.QUICK_SETTINGS
        "lock", "kilit", "ekranıkilitle" -> GlobalKind.LOCK
        else -> null
    }

    /**
     * Türkçe katlama: `ı/İ/ş/ğ/ü/ö/ç` ASCII'ye iner ve küçük harfe çevrilir.
     *
     * Kullanıcı ve model aynı komutu iki türlü yazıyor ("sağ" / "sag");
     * ikisi de aynı jeste gitmeli. Uzunluk korunuyor (birebir), çünkü
     * eşleşmeler konum bazlı değil ama metin karşılaştırması buna güveniyor.
     */
    fun foldTr(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when (ch) {
                    'ı', 'İ', 'i', 'I' -> 'i'
                    'ş', 'Ş' -> 's'
                    'ğ', 'Ğ' -> 'g'
                    'ü', 'Ü' -> 'u'
                    'ö', 'Ö' -> 'o'
                    'ç', 'Ç' -> 'c'
                    'â', 'Â' -> 'a'
                    else -> if (ch.isUpperCase()) ch.lowercaseChar() else ch
                },
            )
        }
        return sb.toString().lowercase()
    }

    // ── Ekran dökümü ─────────────────────────────────────────────────

    /** Döküm boyut sınırları — ajanın bağlamını tek ekranla doldurmamak için. */
    const val MAX_NODES = 400
    const val MAX_CHARS = 20_000

    /**
     * Boş kabukları atar.
     *
     * Bir Android pencere ağacı tipik olarak yüzlerce düğüm taşır ve çoğu
     * hiçbir bilgi içermeyen kapsayıcıdır. Bunlar ayıklanmazsa 20 KB'lık
     * bütçe ilk üç seviyede tükenir ve ajan ekranın asıl içeriğini hiç
     * göremez.
     */
    fun prune(node: UiNode): UiNode? {
        val kids = node.children.mapNotNull { prune(it) }
        val keeps = node.text.isNotBlank() || node.desc.isNotBlank() || node.id.isNotBlank() ||
            node.clickable || node.editable || node.scrollable || node.focused ||
            node.cls in IMPORTANT_CLASSES
        if (!keeps && kids.isEmpty()) return null
        return node.copy(children = kids)
    }

    private val IMPORTANT_CLASSES = setOf(
        "android.widget.Button", "android.widget.EditText",
        "android.widget.CheckBox", "android.widget.Switch",
        "android.widget.Spinner", "android.widget.ImageButton",
        "android.widget.RadioButton", "android.widget.SeekBar",
        "android.widget.ListView", "android.widget.RecyclerView", "android.widget.ScrollView",
    )

    /**
     * Pencere ağacını JSON'a çevirir.
     *
     * Sınır: en fazla [MAX_NODES] düğüm ve [MAX_CHARS] karakter. Sınır
     * aşılırsa ağaç kesilir ve `truncated` alanı `true` olur — sessizce
     * yarım bir döküm vermek, ajanı olmayan bir öğeyi aramaya gönderirdi.
     */
    fun dump(
        root: UiNode,
        packageName: String,
        activity: String,
        maxNodes: Int = MAX_NODES,
        maxChars: Int = MAX_CHARS,
    ): String {
        var budget = maxNodes
        var truncated = false

        fun encode(node: UiNode): JsonObject? {
            if (budget <= 0) {
                truncated = true
                return null
            }
            budget--
            val kids = buildJsonArray {
                for (child in node.children) add(encode(child) ?: continue)
            }
            return buildJsonObject {
                if (node.text.isNotBlank()) put("text", JsonPrimitive(node.text.take(300)))
                if (node.desc.isNotBlank()) put("desc", JsonPrimitive(node.desc.take(300)))
                if (node.id.isNotBlank()) put("id", JsonPrimitive(node.id.take(200)))
                if (node.cls.isNotBlank()) put("class", JsonPrimitive(node.cls.substringAfterLast('.')))
                put(
                    "bounds",
                    JsonArray(
                        listOf(
                            node.bounds.left, node.bounds.top,
                            node.bounds.right, node.bounds.bottom,
                        ).map { JsonPrimitive(it) },
                    ),
                )
                if (node.clickable) put("clickable", JsonPrimitive(true))
                if (node.editable) put("editable", JsonPrimitive(true))
                if (node.scrollable) put("scrollable", JsonPrimitive(true))
                if (node.focused) put("focused", JsonPrimitive(true))
                // Yalnız ekran dışındakiler işaretleniyor: ajan "görünür"
                // alanını her düğümde görmek zorunda değil, ama kaydırınca
                // yeni gelenleri ayırt edebilmeli.
                if (!node.visible) put("offscreen", JsonPrimitive(true))
                if (kids.isNotEmpty()) put("children", kids)
            }
        }

        val tree = encode(root)
        val nodes = walk(root).count()

        var text = buildJsonObject {
            put("ok", JsonPrimitive(true))
            put("package", JsonPrimitive(packageName))
            put("activity", JsonPrimitive(activity))
            put("nodes", JsonPrimitive(nodes))
            put("truncated", JsonPrimitive(truncated))
            put("summary", JsonPrimitive(summary(root, packageName, activity)))
            put("screen", JsonPrimitive(screenSize(root)))
            if (tree != null) put("tree", tree)
        }.toString()

        // Karakter bütçesi: ağacı atıp özeti korumak, ajanın "ekranda ne var"
        // sorusuna yine cevap verir; tamamen boş dönmek vermez.
        if (text.length > maxChars) {
            truncated = true
            text = buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("package", JsonPrimitive(packageName))
                put("activity", JsonPrimitive(activity))
                put("nodes", JsonPrimitive(nodes))
                put("truncated", JsonPrimitive(true))
                put("summary", JsonPrimitive(summary(root, packageName, activity)))
                put("screen", JsonPrimitive(screenSize(root)))
                put(
                    "note",
                    JsonPrimitive("döküm boyut sınırını aştı; ağaç kısaltıldı, özet korundu"),
                )
            }.toString()
        }
        return text
    }

    /** Ham ekran ölçüsü — jest oranları ve dokunma doğrulaması için. */
    fun screenSize(root: UiNode): String {
        val w = walk(root).maxOfOrNull { it.bounds.right } ?: 0
        val h = walk(root).maxOfOrNull { it.bounds.bottom } ?: 0
        return "${w}x$h"
    }

    /**
     * Görünen öğe etiketleri — kaydırma farkını ölçmenin temeli.
     *
     * Ekran dışı satırlar da ağaçta duruyor; onları saymak "kaydırdım ama
     * hiçbir şey değişmedi" yanılsaması üretir.
     */
    fun visibleLabels(root: UiNode, limit: Int = 12): List<String> =
        walk(root).filter { it.visible }
            .map { it.label.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(limit)
            .toList()

    /**
     * İnsan-özeti.
     *
     * Ajanın ilk bakışta "hangi ekrandayım, ne yapabilirim" sorusunu
     * cevaplar: paket, öne çıkan metinler ve tıklanabilir öğe sayısı.
     */
    fun summary(root: UiNode, packageName: String, activity: String, maxLabels: Int = 14): String {
        val all = walk(root).toList()
        val labels = visibleLabels(root, maxLabels)
        val clickable = all.count { it.clickable }
        val editable = all.count { it.editable }
        val shown = all.count { it.visible }
        val sb = StringBuilder()
        sb.append(if (packageName.isBlank()) "?" else packageName)
        if (activity.isNotBlank()) sb.append(" / ").append(activity.substringAfterLast('.'))
        sb.append(" · ").append(all.size).append(" düğüm")
        if (shown != all.size) sb.append(" (").append(shown).append(" görünür)")
        if (clickable > 0) sb.append(" · ").append(clickable).append(" tıklanabilir")
        if (editable > 0) sb.append(" · ").append(editable).append(" yazı alanı")
        if (labels.isNotEmpty()) sb.append(" · ").append(labels.joinToString(" | "))
        return sb.toString()
    }

    // ── Ekran görüntüsü ──────────────────────────────────────────────

    /** Hedef üst sınır: base64 metni ~300 KB'ı geçmesin. */
    const val SCREENSHOT_TARGET_BYTES = 300 * 1024

    /**
     * Küçültme planı.
     *
     * Ekran görüntüsü JPEG'e çevrilirken önce ölçek, sonra kalite düşürülür.
     * Tam çözünürlüklü bir telefon ekranı JPEG'i 1-2 MB tutuyor ve base64
     * bunu %33 daha şişiriyor; WebSocket karesi ve ajan bağlamı için fazla.
     * Sıra kasıtlı: küçültme metni okunmaz yapar, kalite düşürmek daha az
     * kayıp verir.
     */
    fun shrinkSteps(originalBytes: Int, target: Int = SCREENSHOT_TARGET_BYTES): List<Pair<Float, Int>> {
        if (originalBytes <= target) return emptyList()
        val ratio = target.toDouble() / originalBytes.toDouble()
        val steps = mutableListOf<Pair<Float, Int>>()
        var scale = 1.0f
        // Kare kök: alan oranı ölçeğin karesiyle düşer.
        val firstScale = kotlin.math.sqrt(ratio).toFloat().coerceIn(0.3f, 1.0f)
        listOf(firstScale, 0.7f, 0.5f, 0.35f).distinct().forEach { s ->
            if (s < scale) {
                steps += (s to 80)
                scale = s
            }
        }
        if (steps.isEmpty()) steps += (0.4f to 80)
        return steps
    }

    /** Base64 sonrası metin boyutu (≈4/3 + dolgu). Testler için ayrı duruyor. */
    fun base64Size(bytes: Int): Int = ((bytes + 2) / 3) * 4
}
