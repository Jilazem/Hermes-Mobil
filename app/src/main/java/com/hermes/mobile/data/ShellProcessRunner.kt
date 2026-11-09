package com.hermes.mobile.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** No command parsing here: bounded concurrent draining and truthful exit handling. */
internal object ShellProcessRunner {
    fun run(process: Process, timeoutMs: Long = 30_000, limit: Int = 65_536, cancelled: () -> Boolean = { false }): String {
        require(timeoutMs > 0 && limit > 0)
        val readers = Executors.newFixedThreadPool(2) { work -> Thread(work,"hermes-shell-reader").apply { isDaemon = true } }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        fun remaining() = (deadline - System.nanoTime()).coerceAtLeast(1)
        val output = readers.submit<String> { read(process.inputStream,limit) }
        val error = readers.submit<String> { read(process.errorStream,limit) }
        try {
            while (true) {
                if (cancelled()) throw kotlinx.coroutines.CancellationException("Telefon komutu iptal edildi")
                if (System.nanoTime() >= deadline) throw IOException("Telefon komutu zaman aşımına uğradı")
                if (output.isDone) output.get()
                if (error.isDone) error.get()
                if (process.waitFor(50,TimeUnit.MILLISECONDS)) break
            }
            val stdout = output.get(remaining(),TimeUnit.NANOSECONDS)
            val stderr = error.get(remaining(),TimeUnit.NANOSECONDS)
            val code = process.exitValue()
            if (code != 0) throw IOException("Komut başarısız (çıkış $code)" + stderr.trim().take(500).takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty())
            return listOf(stdout,stderr).filter(String::isNotBlank).joinToString("\n").trim().ifBlank { "tamam" }
        } catch (error: ExecutionException) { throw error.cause ?: error }
        catch (error: TimeoutException) { throw IOException("Telefon komutunun çıktısı zaman aşımına uğradı",error) }
        finally {
            runCatching { process.destroy() }
            runCatching { process.inputStream.close() };runCatching { process.errorStream.close() };runCatching { process.outputStream.close() }
            readers.shutdownNow()
        }
    }
    private fun read(stream: InputStream, limit: Int): String = stream.use {
        val bytes = ByteArrayOutputStream();val buffer = ByteArray(4096)
        while (true) {
            val count = it.read(buffer);if (count < 0) break
            if (bytes.size() + count > limit) throw IOException("Telefon komutu çıktısı çok büyük")
            bytes.write(buffer,0,count)
        }
        bytes.toString("UTF-8")
    }
}
