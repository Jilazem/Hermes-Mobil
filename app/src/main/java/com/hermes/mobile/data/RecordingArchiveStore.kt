package com.hermes.mobile.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

@Serializable data class ArchivedRecording(
    val id: String, val name: String, val mime: String, val importedAt: Long,
    val durationMs: Long, val bytes: Long, val sha256: String,
)

/** Copies only user-selected recordings. The recorder's original file is never changed. */
class RecordingArchiveStore(private val context: Context) {
    private val directory = File(context.filesDir,"recording-archive").apply { mkdirs() }
    private val json = Json { encodeDefaults = true }
    fun list(): List<ArchivedRecording> = directory.listFiles().orEmpty().filter { it.extension=="json" }.mapNotNull { file ->
        runCatching { json.decodeFromString<ArchivedRecording>(file.readText()) }.getOrNull()
            ?.takeIf { validId(it.id) && File(directory,"${it.id}.audio").isFile }
    }.sortedByDescending { it.importedAt }
    fun file(id: String): File {
        require(validId(id)) { "Kayıt kimliği geçersiz" }
        return File(directory,"$id.audio")
    }
    @Synchronized fun import(uri: Uri): ArchivedRecording {
        require(list().size < 200) { "Arşivde en fazla 200 kayıt olabilir" }
        val remaining = MAX_ARCHIVE_BYTES - directory.listFiles().orEmpty().sumOf { it.length() }
        require(remaining > 0) { "Yerel kayıt arşivi 1 GB sınırına ulaştı" }
        val id=UUID.randomUUID().toString();val temporary=File(directory,"$id.tmp")
        val name = context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
            if(cursor.moveToFirst()) cursor.getString(0) else null
        }?.replace(Regex("[\\r\\n\\t]")," ")?.take(160)?.ifBlank { null } ?: "Görüşme kaydı"
        var count=0L;val hash=MessageDigest.getInstance("SHA-256")
        var committed=false
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer=ByteArray(65536)
                    while(true) {
                        val size=input.read(buffer);if(size<0)break
                        count+=size;checkCapacity(count,remaining)
                        if(Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("İçe aktarma durduruldu")
                        output.write(buffer,0,size);hash.update(buffer,0,size)
                    }
                    output.fd.sync()
                }
            }
            require(count>0) { "Ses dosyası boş" }
            val metadata=MediaMetadataRetriever()
            val duration:Long;val mime:String
            try {
                metadata.setDataSource(temporary.absolutePath)
                require(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)=="yes") { "Dosyada okunabilir ses yok" }
                duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                mime=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.takeIf { it.startsWith("audio/") }
                    ?: context.contentResolver.getType(uri)?.takeIf { it.startsWith("audio/") } ?: "audio/*"
                require(duration>0) { "Ses kaydının süresi okunamadı" }
            } finally { metadata.release() }
            require(temporary.renameTo(file(id))) { "Kayıt arşive alınamadı" }
            val info=ArchivedRecording(id,name,mime,System.currentTimeMillis(),duration,count,
                hash.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') })
            val atomic=AtomicFile(File(directory,"$id.json"));val stream=atomic.startWrite()
            try { stream.write(json.encodeToString(info).toByteArray(Charsets.UTF_8));atomic.finishWrite(stream) }
            catch(error:Throwable) { atomic.failWrite(stream);throw error }
            committed=true
            return info
        } finally {
            temporary.delete()
            if(!committed) { file(id).delete();AtomicFile(File(directory,"$id.json")).delete() }
        }
    }
    fun remove(id:String) {
        require(validId(id))
        check(!file(id).exists() || file(id).delete()) { "Arşiv kopyası silinemedi" }
        File(directory,"$id.json").delete()
    }
    companion object {
        const val MAX_BYTES=256L*1024*1024
        const val MAX_ARCHIVE_BYTES=1024L*1024*1024
        internal fun checkCapacity(incoming:Long,remaining:Long) {
            require(incoming in 0..MAX_BYTES) { "Tek ses kaydı en fazla 256 MB olabilir" }
            // Reserve space for the small metadata record as well as the audio.
            require(incoming <= remaining - 4096) { "Yerel kayıt arşivi 1 GB sınırını aşamaz" }
        }
        fun validId(id:String)=id.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
    }
}
