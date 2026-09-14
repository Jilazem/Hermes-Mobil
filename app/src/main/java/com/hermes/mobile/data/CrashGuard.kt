package com.hermes.mobile.data

import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Uygulama seviyesinde çökme muhafızı (tur-2 K3).
 *
 * Telefonda `adb logcat` yok; kullanıcı "sik sik kapaniyor" dediğinde
 * elimizde hiçbir iz olmaması en pahalı durum. Bu nesne:
 *
 * 1. Her yakalanmamaz istisnayı bağlamla (aktif oturum + ekran) DiagLog'a
 *    yazar — [reportText] saf fonksiyondur, JVM'de test edilir.
 * 2. Süreç ölmeden önce [lastCrash] StateFlow'una işler; uygulama bir dahaki
 *    açılışta kullanıcı "çöktü" yerine ne olduğunu GÖRÜR (banner).
 * 3. [handler] — uzun ömürlü CoroutineScope'lara verilen
 *    CoroutineExceptionHandler: bir launch içindeki hata sessizce yutulmak
 *    yerine iz bırakır (scope'un kendisini öldürmez; SupervisorJob zaten
 *    kardeş işleri korur).
 *
 * Zincir korunur: DiagLog.installCrashHandler kendi işleyicisini kurmuş olur;
 * biz ONUN üstüne bağlam katmanı olarak geçer, sistemi öldüren varsayılan
 * işleyiciyi ASLA ezmeden en son ona iletiriz.
 */
object CrashGuard {

    /** Aktif oturum kimliği — AppViewModel/ChatViewModel bağlar; null = yok. */
    @Volatile
    var sessionProvider: () -> String? = { null }

    /** Şu anki ekran etiketi — MainActivity bağlar ("Sohbet", "Oturumlar"…). */
    @Volatile
    var screenProvider: () -> String = { "?" }

    /** Son çökmenin okunabilir özeti; null = bu süreçte çökme kaydedilmedi. */
    @Volatile
    var lastCrash: String? = null

    /** Çökme kurtarma izi — bir sonraki açılışta banner göstermek için. */
    fun markRecovered(summary: String) { lastCrash = summary }

    /**
     * Bir önceki sürecin son CRASH satırını diag.log'dan bulur ve
     * [lastCrash] yapar — çökme süreci öldürdüğü için bellekte hiçbir şey
     * kalmaz; kalıcı iz yalnız diskte. HermesApp.onCreate'ta çağrılır.
     * (Satır biçimi DiagLog.Entry.line(): "… C [crash] mesaj".)
     */
    fun recoverFromDiagLog(filesDir: java.io.File) {
        runCatching {
            val f = java.io.File(filesDir, "diag.log")
            if (!f.exists()) return
            var found: String? = null
            f.forEachLine { line ->
                if (line.contains(" C [crash]")) {
                    found = line.substringAfter("]").trim()
                        .lineSequence().firstOrNull()?.take(200)
                }
            }
            found?.let { markRecovered("Onceki açilis bir cokmeyle kapandi: $it") }
        }
    }

    /**
     * Çökme kaydının tek satır-üstü gövdesi (saf — test hedefi).
     * Biçim: `thread=<ad> session=<id|-> screen=<ad> :: <tür>: <mesaj>`
     * Yığın izi [stackTrace] ile ayrı eklenir; DiagLog satır limiti yok.
     */
    fun reportText(threadName: String, error: Throwable, session: String?, screen: String?): String =
        "thread=$threadName session=${session ?: "-"} screen=${screen ?: "?"} :: " +
            "${error.javaClass.name}: ${error.message}"

    /** Yığının ilk [maxFrames] çerçevesini ` at …` satırlarına çevirir (saf). */
    fun stackTrace(error: Throwable, maxFrames: Int = 15): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        return sw.toString().lineSequence()
            .filter { it.trimStart().startsWith("at ") }
            .take(maxFrames)
            .joinToString("\n") { it.trim() }
    }

    /**
     * Uzun ömürlü scope'lara ver: `CoroutineScope(SupervisorJob() + IO + handler)`.
     * Controller dışı iş parçacıklarındaki yakalanmamış hata burada DiagLog'a
     * düşer; uygulama yaşıyorsa yaşamaya devam eder (kritik değilse).
     */
    val handler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        runCatching {
            DiagLog.e(
                "crash",
                "coroutine unhandled: " + reportText(
                    Thread.currentThread().name, t, sessionProvider(), screenProvider(),
                ) + "\n" + stackTrace(t),
            )
            lastCrash = "Arka plan hatası: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    /**
     * HermesApp.onCreate'ta bir kez çağır. Mevcut varsayılan işleyiciyi
     * (DiagLog'un kurduğu) koruyup üstüne bağlam + banner katmanı ekler.
     */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = reportText(
                    thread.name, error, sessionProvider(), screenProvider(),
                ) + "\n" + stackTrace(error)
                DiagLog.e("crash", "uncaught $text")
                lastCrash = "${error.javaClass.simpleName}: ${error.message}" +
                    (screenProvider().let { if (it != "?") " · $it ekranında" else "" })
            }
            // Zincir sonsuza: DiagLog → (bizden önceki varsa o) → sistem.
            runCatching { previous?.uncaughtException(thread, error) }
        }
    }
}
