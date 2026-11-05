package com.hermes.mobile

import com.hermes.mobile.data.EmaModelStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * İş #1021 KÖK NEDEN 2 — EMA üçüncü kaynağı.
 *
 * cd-coder kararı: `/api/files/download?path=ema-offline/...` yerine yeni
 * `/api/ema/model/{ad}?token=…` ucu kullanılır (sorgu token'ı kabul eder,
 * kurtarılan pinli dosyalardan okur). sha256 pin kontrolü değişmedi.
 * Ayrıca 416 → yerel `.part` silinip BAŞTAN indirilir; 404/401 nedeniyle
 * birlikte raporlanır (ekranda görünür).
 */
class EmaModelSourceTest {

    @Test
    fun `ema url mobil adapter ucunu kullanir ve sir sorguya yazilmaz`() {
        val url = EmaModelStore.emaUrl(EmaModelStore.Gateway("http://192.168.140.20:9150/", "abc123"), "config.json")
        assertEquals("http://192.168.140.20:9150/mobile-api/ema/model/config.json", url)
        assertFalse("token sorguya sızdı", url.contains("abc123"))
        assertFalse("eski uç hâlâ kullanılıyor: $url", url.contains("/api/files/download"))
        assertFalse("ema-offline yolu kaldı: $url", url.contains("ema-offline"))
    }

    @Test
    fun `token bos ise sorgu parametresi eklenmez`() {
        val url = EmaModelStore.emaUrl(EmaModelStore.Gateway("http://h:1", ""), "tokenizer.json")
        assertEquals("http://h:1/mobile-api/ema/model/tokenizer.json", url)
    }

    @Test
    fun `416 gelince part silinir ve bastan indirilir`() {
        val govde = ByteArray(584) { (it % 251).toByte() }
        HttpDublor { istekSayisi, rangeVar ->
            when {
                istekSayisi == 1 && rangeVar -> Triple(416, "Range Not Satisfiable", ByteArray(0))
                else -> Triple(200, "OK", govde)
            }
        }.use { server ->
            val dir = createTempDir()
            val part = File(dir, "config.json.part")
            part.writeBytes(ByteArray(50) { 7 }) // yarım kalmış eski indirme
            val src = EmaModelStore.Source(
                "gateway dublör · /api/ema/model",
                "http://127.0.0.1:${server.port}/api/ema/model/config.json?token=t",
                emptyMap(),
            )
            val meta = EmaModelStore.ModelFile("config.json", govde.size.toLong(), "pin")
            runBlocking { EmaModelStore.fetch(src, meta, part, 0L) {} }
            assertEquals("baştan indirilmedi", govde.size.toLong(), part.length())
            assertTrue("içerik bozuk", part.readBytes().contentEquals(govde))
            assertEquals("416 sonrası yeniden denenmedi", 2, server.istekler.size)
            assertFalse("ikinci istekte Range kaldı", server.istekler[1].contains("Range:"))
        }
    }

    @Test
    fun `404 kaynagi reddeder ve nedeni mesajda gorunur`() {
        HttpDublor { _, _ -> Triple(404, "Not Found", ByteArray(0)) }.use { server ->
            val dir = createTempDir()
            val part = File(dir, "x.part")
            val src = EmaModelStore.Source(
                "gateway dublör · /api/ema/model",
                "http://127.0.0.1:${server.port}/api/ema/model/x.bin?token=t",
                emptyMap(),
            )
            val hata = runCatching {
                runBlocking {
                    EmaModelStore.fetch(src, EmaModelStore.ModelFile("x.bin", 10, "pin"), part, 0L) {}
                }
            }.exceptionOrNull()
            val mesaj = hata?.message.orEmpty()
            assertTrue("404 mesajı yok: $mesaj", mesaj.contains("404"))
            assertTrue("kaynak adı mesajda yok: $mesaj", mesaj.contains("/api/ema/model"))
        }
    }

    @Test
    fun `401 token reddi nedeniyle raporlanir`() {
        HttpDublor { _, _ -> Triple(401, "Unauthorized", ByteArray(0)) }.use { server ->
            val dir = createTempDir()
            val src = EmaModelStore.Source(
                "gateway dublör · /api/ema/model",
                "http://127.0.0.1:${server.port}/api/ema/model/x.bin?token=t",
                emptyMap(),
            )
            val hata = runCatching {
                runBlocking {
                    EmaModelStore.fetch(src, EmaModelStore.ModelFile("x.bin", 10, "pin"), File(dir, "x.part"), 0L) {}
                }
            }.exceptionOrNull()
            assertTrue("401 mesajı yok: ${hata?.message}", hata?.message.orEmpty().contains("token reddedildi"))
        }
    }

    private fun createTempDir(): File =
        File.createTempFile("ema-test", "").let { it.delete(); it.mkdirs(); it }

    /** Ham soket HTTP dublörü; her istek için (kod, sebep, gövde) üretir. */
    private class HttpDublor(
        private val cevap: (istekSayisi: Int, rangeVar: Boolean) -> Triple<Int, String, ByteArray>,
    ) : AutoCloseable {
        private val serverSocket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = serverSocket.localPort
        val istekler = CopyOnWriteArrayList<String>()

        init {
            Thread {
                while (!serverSocket.isClosed) {
                    runCatching { serverSocket.accept().use { serve(it) } }
                }
            }.apply { isDaemon = true }.start()
        }

        private fun serve(socket: Socket) {
            val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val satir = input.readLine() ?: return
            val basliklar = generateSequence { input.readLine() }.takeWhile { it.isNotEmpty() }.toList()
            val ham = (listOf(satir) + basliklar).joinToString("\n")
            val sira = istekler.size + 1
            istekler.add(ham)
            val rangeVar = basliklar.any { it.startsWith("Range:", ignoreCase = true) }
            val (kod, sebep, govde) = cevap(sira, rangeVar)
            val out = socket.getOutputStream()
            out.write(
                ("HTTP/1.1 $kod $sebep\r\nContent-Type: application/octet-stream\r\n" +
                    "Content-Length: ${govde.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1)
            )
            if (govde.isNotEmpty()) out.write(govde)
            out.flush()
        }

        override fun close() = runCatching { serverSocket.close() }.let { }
    }
}
