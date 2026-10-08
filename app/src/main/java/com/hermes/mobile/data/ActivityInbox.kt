package com.hermes.mobile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class ActivityNotice(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val sessionId: String? = null,
    val title: String,
    val text: String,
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault
    val time: Long = System.currentTimeMillis(),
    val read: Boolean = false,
    val kind: String = "reply",
)

/** Profil sınırını koruyan, cihazdaki Hermes bildirim geçmişi. */
fun noticesForProfile(items: List<ActivityNotice>, profileId: String?): List<ActivityNotice> =
    items.filter { it.profileId == profileId }.sortedByDescending { it.time }

fun appendNotice(items: List<ActivityNotice>, notice: ActivityNotice): List<ActivityNotice> =
    (listOf(notice) + items.filterNot { it.id == notice.id }).take(200)

class ActivityInbox private constructor(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "hermes-notifications.json")
    private val _items = MutableStateFlow(runCatching {
        json.decodeFromString<List<ActivityNotice>>(file.readText())
    }.getOrDefault(emptyList()))
    val items = _items.asStateFlow()

    @Synchronized
    fun add(notice: ActivityNotice) = save(appendNotice(_items.value, notice))

    @Synchronized
    fun markRead(id: String) = save(_items.value.map { if (it.id == id) it.copy(read = true) else it })

    @Synchronized
    fun markProfileRead(profileId: String) = save(_items.value.map {
        if (it.profileId == profileId) it.copy(read = true) else it
    })

    @Synchronized
    fun markSessionRead(profileId: String, sessionId: String) = save(_items.value.map {
        if (it.profileId == profileId && it.sessionId == sessionId) it.copy(read = true) else it
    })

    private fun save(items: List<ActivityNotice>) {
        if (items == _items.value) return
        val temp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            temp.writeText(json.encodeToString(items))
            check(temp.renameTo(file))
        }.onFailure { DiagLog.w("inbox", "bildirim gecmisi kaydedilemedi") }
        _items.value = items
    }

    companion object {
        @Volatile private var instance: ActivityInbox? = null
        fun get(context: Context): ActivityInbox = instance ?: synchronized(this) {
            instance ?: ActivityInbox(context.applicationContext).also { instance = it }
        }
    }
}
