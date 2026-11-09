package com.hermes.mobile.data

import java.io.IOException

/** The Dashboard adds live-only sessions to every page; row count is NOT the page stride. */
internal suspend fun collectSessionPages(fetch: suspend (Int) -> SessionsResponse): List<HermesSession> {
    val sessions=linkedMapOf<String,HermesSession>()
    var offset=0
    repeat(1000) {
        val page=fetch(offset)
        if(page.offset!=null && page.offset!=offset) throw IOException("Sunucu sohbet sayfasını ilerletmedi")
        page.sessions.filter { it.id.isNotBlank() }.forEach { sessions.putIfAbsent(it.id,it) }
        val total=page.total ?: return sessions.values.toList() // Older non-paginated servers.
        val stride=page.limit ?: return sessions.values.toList()
        if(total<0 || stride<=0) throw IOException("Sunucunun sohbet sayfalama bilgisi geçersiz")
        val next=offset.toLong()+stride
        if(next>=total) return sessions.values.toList()
        if(page.sessions.isEmpty()) throw IOException("Sohbet listesi eksik döndü; yeniden yenile")
        offset=next.toInt()
    }
    throw IOException("Sohbet listesi tamamlanamadı; sunucu sayfalamasını kontrol et")
}
