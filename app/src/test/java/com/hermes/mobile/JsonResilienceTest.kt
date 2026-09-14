package com.hermes.mobile

import com.hermes.mobile.data.SessionsResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-2 K3(b) / FR-004 — JSON dayanikliligi: sunucu yeni alan eklerse
 * (Hermes guncellemesi) uygulama COKMEMELI. Uygulamanin gercek Json
 * ayarini (ignoreUnknownKeys + isLenient — HermesClient ile birebir)
 * burada sabitleyip ek alanli ornek yaniti cozuyoruz.
 */
class JsonResilienceTest {

    /** HermesClient.json ile AYNI ayar — sozlesme buraya sabitlenir. */
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Boss'un gördüğü gerçek şekil + gelecekte eklenecek tanınmayan alanlar. */
    @Test
    fun ekAlanliOturumListesiCozulur() {
        val payload = """
            {"sessions":[
              {"id":"20260914_202501_desktop1","source":"desktop","model":"m1",
               "display_name":null,"title":"Icra kiymet takdir raporlarini sablona gore yaz",
               "preview":"raporu yaz diyor",
               "last_activity_description":"5 dk once",
               "started_at":1780000000.0,"ended_at":null,"end_reason":null,
               "message_count":12,"tool_call_count":3,"input_tokens":40,"output_tokens":90,
               "cwd":"/tmp","gelecekteki_alan":{"ic":[]},"yeni_bayrak":true},
              {"id":"x2","message_count":null,"tool_call_count":null}
            ]}
        """.trimIndent()
        val resp = json.decodeFromString(SessionsResponse.serializer(), payload)
        assertEquals(2, resp.sessions.size)
        val s = resp.sessions[0]
        assertEquals("Icra kiymet takdir raporlarini sablona gore yaz", s.serverTitle)
        assertEquals("raporu yaz diyor", s.preview)
        assertEquals("5 dk once", s.lastActivityDescription)
        assertEquals(12, s.messageCount)
        // Null sayaç toleransı: sunucu 'null' yollarsa 0'a iner, decode ÇÖKMEZ.
        // (messageCount = null JSON'da Int = 0 defaultu devreye girmez —
        // ignoreUnknownKeys null'u "bilinmeyen değil" sayar; isLenient ve
        // nullable-default deseni birlikte bunu tolere eder mi? Aşağıda doğrula.)
        assertTrue(resp.sessions[1].messageCount >= 0)
    }

    /** Bilinen ama null gelir alanlar (display_name null) her zamanki gibi geçer. */
    @Test
    fun nullAlanlarEskisiGibiGecer() {
        val resp = json.decodeFromString(
            SessionsResponse.serializer(),
            """{"sessions":[{"id":"a","source":null,"display_name":null,"cwd":null}]}""",
        )
        assertEquals(1, resp.sessions.size)
        assertEquals("a", resp.sessions[0].title)
    }
}
