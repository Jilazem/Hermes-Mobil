package com.hermes.mobile.data

/** A visible page can be used immediately; pinned rows never determine the next offset. */
internal data class ProfileSessionWindow(
    val rows: List<HermesSession> = emptyList(), val total: Int? = null, val nextOffset: Int? = null,
)
internal fun appendProfileSessionPage(previous: ProfileSessionWindow, page: SessionsResponse, offset: Int): ProfileSessionWindow {
    require(page.offset == null || page.offset == offset) { "Sunucu sohbet sayfasını ilerletmedi" }
    val stride = page.limit
    require(stride == null || stride > 0) { "Sunucunun sayfa boyutu geçersiz" }
    val total = page.total
    require(total == null || total >= 0) { "Sunucunun sohbet sayısı geçersiz" }
    val next = if (total != null && stride != null && offset.toLong() + stride < total) offset + stride else null
    require(next == null || page.sessions.isNotEmpty()) { "Sohbet listesi eksik döndü" }
    val rows = linkedMapOf<String, HermesSession>()
    if (offset > 0) previous.rows.forEach { rows[it.id] = it }
    page.sessions.filter { it.id.isNotBlank() }.forEach { rows.putIfAbsent(it.id, it) }
    return ProfileSessionWindow(rows.values.toList(), total, next)
}
