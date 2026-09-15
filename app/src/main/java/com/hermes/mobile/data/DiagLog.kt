package com.hermes.mobile.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Tanılama kaydı — uygulamanın tek hata/olay defteri.
 *
 * Neden var: bu uygulamanın hatalarının çoğu **sessiz**. Soket kapanıyor,
 * kullanıcı "Düşünüyor" görüyor; JSON şeması değişiyor, liste boş geliyor;
 * röle adresi yanlış türetiliyor, "yeniden bağlanılıyor (1)" yazıyor. Hiçbiri
 * yığında iz bırakmıyor ve `adb logcat` yalnız telefon kabloya bağlıyken
 * okunabiliyor. Uzaktaki bir telefonda ne olduğunu ekran görüntüsünden
 * anlamaya çalışmak yerine burada topluyoruz.
 *
 * Tasarım kararları:
 * - **Sırlar ayıklanır** ([redact]). Bu kayıt paylaşılmak için var; token ya da
 *   API anahtarı içermesi onu paylaşılamaz hale getirirdi. Ayıklama yazma
 *   anında yapılıyor — sonradan filtrelemeye güvenmek, bir yolu atlamak
 *   demektir.
 * - **Sınırlı.** Bellekte son [MAX_MEMORY] kayıt, diskte [MAX_FILE_BYTES] +
 *   tek yedek. Sınırsız kayıt, telefonu dolduran bir hata döngüsü demek.
 * - **ERROR ve üstü hemen diske yazılır.** Süreç öldürüldüğünde (arka planda
 *   donma, çökme) tamponda kalan kayıt kaybolur; asıl ilgilendiğimiz kayıtlar
 *   da tam o anda oluşuyor.
 * - **Çökme yakalayıcı zincirlenir** — varsayılan işleyici yine çağrılır, yoksa
 *   Android'in kendi çökme kaydı ve "uygulama durdu" akışı bozulur.
 */
object DiagLog {

    enum class Level { DEBUG, INFO, WARN, ERROR, CRASH }

    data class Entry(
        val at: Long,
        val level: Level,
        val tag: String,
        val message: String,
    ) {
        val clock: String get() = TIME.format(Date(at))
        /** Panoya/paylaşıma giden tek satırlık biçim. */
        fun line(): String = "${STAMP.format(Date(at))} ${level.name.take(1)} [$tag] $message"
    }

    private const val MAX_MEMORY = 400
    private const val MAX_FILE_BYTES = 256 * 1024L
    private val TIME = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val STAMP = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "diag-log").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }

    private val lock = Any()
    private val ring = ArrayDeque<Entry>()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private var dir: File? = null
    private val pending = StringBuilder()

    /** Uygulama açılışında bir kez. */
    fun init(context: Context) {
        synchronized(lock) {
            if (dir != null) return
            dir = context.filesDir
        }
        installCrashHandler()
        i("app", "started · Android ${Build.VERSION.SDK_INT} · ${Build.MANUFACTURER} ${Build.MODEL}")
    }

    fun d(tag: String, msg: String) = add(Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = add(Level.INFO, tag, msg)
    fun w(tag: String, msg: String) = add(Level.WARN, tag, msg)
    fun e(tag: String, msg: String) = add(Level.ERROR, tag, msg)

    /** İstisnayı türü + mesajı + ilk birkaç yığın çerçevesiyle kaydeder. */
    fun e(tag: String, msg: String, t: Throwable) {
        val frames = t.stackTrace.take(4).joinToString(" ← ") {
            "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
        }
        add(Level.ERROR, tag, "$msg — ${t.javaClass.simpleName}: ${t.message} · $frames")
    }

    private fun add(level: Level, tag: String, msg: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, redact(msg))
        val snapshot: List<Entry>
        synchronized(lock) {
            ring.addLast(entry)
            while (ring.size > MAX_MEMORY) ring.removeFirst()
            snapshot = ring.toList()
            pending.append(entry.line()).append('\n')
        }
        _entries.value = snapshot.asReversed()   // en yeni üstte
        // Her kayıtta boşaltıyoruz. "20 kayıtta bir" denemesi, süreç arka planda
        // dondurulduğunda (bu uygulamanın en sık sorunu) tampondaki kayıtları
        // kaybediyordu — yani tam ilgilendiğimiz anı. Maliyet düşük: [flush]
        // tamponun tamamını tek yazımda boşaltıyor, hızlı gelen kayıtlar
        // kendiliğinden gruplanıyor ve yazma arka plan iş parçacığında.
        flush()
    }

    /**
     * Sırları ayıkla. Fazla ayıklamak, az ayıklamaktan iyidir: bir token'ı
     * kaçırmak kaydı paylaşılamaz kılar, `***` görmek yalnız can sıkar.
     */
    internal fun redact(s: String): String {
        var out = s
        // ?token=… / &key=… / apiKey=… — sorgu parametreleri.
        out = Regex(
            "(?i)\\b(token|key|api_key|apikey|password|secret|auth)=([^&\\s\"'<>]+)"
        ).replace(out) { "${it.groupValues[1]}=***" }
        // Başlık biçimi: "X-Hermes-Session-Token: …", "Authorization: Bearer …"
        // Şema sözcüğü (Bearer/Token/Basic) isteğe bağlı olarak yutulmalı;
        // yoksa `\S+` yalnız "Bearer"ı eşleyip **tokeni açıkta bırakıyor**.
        out = Regex(
            "(?i)\\b(x-hermes-session-token|authorization)\\s*[:=]\\s*" +
                "(?:(?:bearer|token|basic)\\s+)?\\S+"
        ).replace(out) { "${it.groupValues[1]}: ***" }
        // Google API anahtarı — sabit önek, ayıklaması kolay.
        out = Regex("AIza[0-9A-Za-z_\\-]{10,}").replace(out, "AIza***")
        return out
    }

    private fun flush() {
        val chunk: String
        synchronized(lock) {
            if (pending.isEmpty()) return
            chunk = pending.toString()
            pending.setLength(0)
        }
        val d = dir ?: return
        io.execute {
            runCatching {
                val f = File(d, "diag.log")
                if (f.length() > MAX_FILE_BYTES) {
                    // Tek yedek tutuyoruz: iki dosyayla en kötü durumda 512 KB.
                    File(d, "diag.log.1").delete()
                    f.renameTo(File(d, "diag.log.1"))
                }
                f.appendText(chunk)
            }
        }
    }

    /** Paylaşıma/panoya giden tam metin — dosyadaki geçmiş + bellektekiler. */
    fun dump(): String {
        flush()
        val d = dir
        val head = buildString {
            append("# Hermes Mobile diagnostics log\n")
            append("# ").append(STAMP.format(Date())).append('\n')
            append("# Android ").append(Build.VERSION.SDK_INT)
                .append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append('\n')
            append("# Secrets are redacted (token/key -> ***)\n\n")
        }
        val past = runCatching {
            d?.let { root ->
                listOf(File(root, "diag.log.1"), File(root, "diag.log"))
                    .filter { it.exists() }
                    .joinToString("") { it.readText() }
            }.orEmpty()
        }.getOrDefault("")
        if (past.isNotBlank()) return head + past
        val memory = synchronized(lock) { ring.joinToString("\n") { it.line() } }
        // Tur-10 (F2): bağlantı kopma defteri kayda gömülür — paylaşılan tanı
        // metninde "kaç kez, hangi kanal, neden koptu" tek bakışta görünsün.
        val journal = ConnectionJournal.dumpSection()
        return if (journal.isBlank()) head + memory else head + memory + "\n" + journal
    }

    fun clear() {
        synchronized(lock) {
            ring.clear()
            pending.setLength(0)
        }
        _entries.value = emptyList()
        val d = dir ?: return
        io.execute {
            runCatching {
                File(d, "diag.log").delete()
                File(d, "diag.log.1").delete()
            }
        }
    }

    /** Çökmeyi kaydet, sonra zinciri sürdür. */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = error.stackTrace.take(12).joinToString("\n  at ") {
                    "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})"
                }
                add(
                    Level.CRASH,
                    "crash",
                    "on thread ${thread.name}: " +
                        "${error.javaClass.name}: ${error.message}\n  at $trace",
                )
                flush()
                // Diske gerçekten yazılsın: süreç birazdan ölüyor.
                io.submit { }.get(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
