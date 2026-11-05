package com.hermes.mobile

import com.hermes.mobile.data.MediaKind
import com.hermes.mobile.ui.cacheServerMedia
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class ServerMediaV76Test {
    private class TestServer(private val handler: (String, List<String>) -> ByteArray) : AutoCloseable {
        private val socket = ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
        val url get() = "http://127.0.0.1:${socket.localPort}"
        private val thread = Thread {
            while (!socket.isClosed) try {
                socket.accept().use { connection ->
                    connection.soTimeout = 3000
                    val reader = connection.getInputStream().bufferedReader()
                    val first = reader.readLine().orEmpty()
                    val headers = mutableListOf<String>()
                    while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; headers += line }
                    connection.getOutputStream().write(handler(first, headers))
                }
            } catch (_: java.io.IOException) { }
        }.apply { isDaemon = true; start() }
        override fun close() { socket.close(); thread.join(3000) }
    }
    private fun response(status: String, body: ByteArray = byteArrayOf(), extra: String = "", length: Long = body.size.toLong()) =
        "HTTP/1.1 $status\r\nContent-Length: $length\r\nConnection: close\r\n$extra\r\n".toByteArray(Charsets.ISO_8859_1) + body
    @Test fun authenticatedDownloadDoesNotForwardTokenToRedirectTarget() {
        val targetHits = AtomicInteger()
        val receivedToken = AtomicReference<String?>()
        val target = TestServer { _, _ -> targetHits.incrementAndGet(); response("200 OK") }
        val source = TestServer { _, headers ->
            receivedToken.set(headers.firstOrNull { it.startsWith("X-Hermes-Session-Token:", true) }?.substringAfter(':')?.trim())
            response("302 Found", extra = "Location: ${target.url}/stolen\r\n")
        }
        val dir = Files.createTempDirectory("server-media").toFile()
        try {
            assertTrue(runCatching { cacheServerMedia(dir, "${source.url}/api/media", "dummy-token", MediaKind.HTML) }.isFailure)
            assertEquals("dummy-token", receivedToken.get())
            assertEquals(0, targetHits.get())
            assertTrue(java.io.File(dir, "conversation-media").listFiles().orEmpty().isEmpty())
        } finally { source.close(); target.close(); dir.deleteRecursively() }
    }
    @Test fun boundedDownloadPreservesBytesAndDeletesOversizedResponse() {
        val dir = Files.createTempDirectory("bounded-media").toFile()
        val content = "<h1>Hermes test</h1>".toByteArray()
        val source = TestServer { first, _ -> if (first.contains("/large")) response("200 OK", length = 2L * 1024 * 1024 + 1) else response("200 OK", content) }
        try {
            val file = cacheServerMedia(dir, "${source.url}/ok", "dummy-token", MediaKind.HTML)
            assertArrayEquals(content, file.readBytes()); file.delete()
            assertTrue(runCatching { cacheServerMedia(dir, "${source.url}/large", "dummy-token", MediaKind.HTML) }.isFailure)
            assertTrue(java.io.File(dir, "conversation-media").listFiles().orEmpty().isEmpty())
        } finally { source.close(); dir.deleteRecursively() }
    }
}
