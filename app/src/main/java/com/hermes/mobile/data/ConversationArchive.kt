package com.hermes.mobile.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

@Serializable data class ArchivedConversation(val id: String, val title: String, val messages: List<SessionMessage>)
@Serializable data class ConversationArchive(
    val format: String = "hermes-conversations", val version: Int = 1,
    val createdAt: Long, val sourceName: String, val conversations: List<ArchivedConversation>,
)
@Serializable data class ConversationArchiveEnvelope(val archive: ConversationArchive, val sha256: String)
@Serializable data class ConversationArchiveInfo(val id: String, val createdAt: Long, val conversations: Int, val messages: Int)

/** Selected messages only. No server credentials, executable import or changes to source sessions. */
object ConversationArchiveCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    const val MAX_CONVERSATIONS = 25
    private val json = Json { encodeDefaults = true }
    fun digest(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
    private fun validate(value: ConversationArchive) {
        require(value.format == "hermes-conversations" && value.version == 1) { "Sohbet yedeği biçimi/sürümü desteklenmiyor" }
        require(value.conversations.size in 1..MAX_CONVERSATIONS) { "Bir yedekte 1–25 sohbet seç" }
        require(value.conversations.map { it.id }.distinct().size == value.conversations.size) { "Tekrarlanan sohbet kimliği" }
        require(value.conversations.all { it.id.isNotBlank() && it.id.length <= 500 && it.title.length <= 1000 }) { "Sohbet başlığı/kimliği geçersiz" }
        require(value.conversations.sumOf { it.messages.size } <= 20_000) { "Yedek çok büyük; daha az sohbet seç" }
        require(value.conversations.all { chat -> chat.messages.all { it.role in setOf("user","assistant","system","tool") } }) { "Bilinmeyen mesaj türü" }
    }
    fun encode(value: ConversationArchive): ByteArray {
        validate(value)
        val payload = json.encodeToString(value).toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_BYTES - 512) { "Yedek çok büyük; sohbetleri ayrı dosyalara ayır" }
        return json.encodeToString(ConversationArchiveEnvelope(value,digest(payload))).toByteArray(Charsets.UTF_8)
            .also { require(it.size <= MAX_BYTES) { "Yedek boyut sınırını aştı" } }
    }
    fun decode(raw: ByteArray): ConversationArchiveEnvelope {
        require(raw.size <= MAX_BYTES) { "Yedek en çok 32 MB olabilir" }
        val value = json.decodeFromString<ConversationArchiveEnvelope>(raw.toString(Charsets.UTF_8))
        validate(value.archive)
        require(value.sha256 == digest(json.encodeToString(value.archive).toByteArray(Charsets.UTF_8))) { "Yedek bütünlüğü doğrulanamadı" }
        return value
    }
    fun read(input: InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "Yedek en çok 32 MB olabilir" }
            output.write(buffer,0,count)
        }
        return output.toByteArray()
    }
}

class ConversationArchiveStore(context: Context) {
    private val directory = File(context.filesDir,"conversation-archives").apply { mkdirs() }
    private val json = Json { encodeDefaults = true }
    @Synchronized fun save(raw: ByteArray): ConversationArchiveInfo {
        val envelope = ConversationArchiveCodec.decode(raw)
        val info = ConversationArchiveInfo(envelope.sha256, envelope.archive.createdAt,
            envelope.archive.conversations.size,envelope.archive.conversations.sumOf { it.messages.size })
        require(list().size < 100 || File(directory,"${info.id}.json").exists()) { "En fazla 100 yerel sohbet yedeği" }
        val body=File(directory,"${info.id}.json")
        val existed=body.exists()
        atomic(body,raw)
        try { atomic(File(directory,"${info.id}.meta"),json.encodeToString(info).toByteArray(Charsets.UTF_8)) }
        catch(error:Throwable) {
            if(!existed) AtomicFile(body).delete()
            throw error
        }
        return info
    }
    fun list(): List<ConversationArchiveInfo> = directory.listFiles().orEmpty().filter { it.extension == "meta" }.mapNotNull { file ->
        runCatching { json.decodeFromString<ConversationArchiveInfo>(file.readText()) }
            .getOrNull()?.takeIf { it.id.matches(Regex("[a-f0-9]{64}")) && File(directory,"${it.id}.json").isFile }
    }.sortedByDescending { it.createdAt }
    fun open(id: String): ConversationArchive {
        require(id.matches(Regex("[a-f0-9]{64}")))
        return File(directory,"$id.json").inputStream().use { ConversationArchiveCodec.decode(ConversationArchiveCodec.read(it)).archive }
    }
    private fun atomic(file: File, bytes: ByteArray) {
        check(!file.exists() || file.isFile) { "Arşiv hedefi yazılabilir bir dosya değil" }
        val atomic = AtomicFile(file); val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
        check(atomic.readFully().contentEquals(bytes)) { "Arşiv kaydı doğrulanamadı" }
    }
}
