package com.hermes.mobile.data

data class FollowUpdate(val sessionId: String, val text: String)

/** İlk liste geçmiş bildirim üretmez; yalnız başarıyla alınan sonraki listeler karşılaştırılır. */
fun followedUpdates(before: List<LiveSession>?, now: List<LiveSession>, flags: SessionFlags): List<FollowUpdate> {
    if (before == null) return emptyList()
    val current = now.associateBy { it.dbId.ifBlank { it.id } }
    return before.mapNotNull { old ->
        val id = old.dbId.ifBlank { old.id }
        if ((id !in flags.followed && old.id !in flags.followed) || id in flags.hidden || old.id in flags.hidden) return@mapNotNull null
        val next = current[id]
        when {
            next == null -> FollowUpdate(id, "Canlı listeden ayrıldı")
            old.isWorking && !next.isWorking -> FollowUpdate(id, next.preview.takeIf { it.isNotBlank() } ?: "Çalışma durumu güncellendi")
            !old.isWaiting && next.isWaiting -> FollowUpdate(id, "Yanıtını bekliyor")
            else -> null
        }
    }
}
