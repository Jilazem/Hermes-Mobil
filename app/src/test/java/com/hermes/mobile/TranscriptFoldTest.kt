package com.hermes.mobile

import com.hermes.mobile.data.SessionMessage
import com.hermes.mobile.data.ToolCall
import com.hermes.mobile.data.ToolCallFunction
import com.hermes.mobile.ui.TranscriptRow
import com.hermes.mobile.ui.ToolEntryState
import com.hermes.mobile.ui.detailCounterText
import com.hermes.mobile.ui.foldTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-4 kusur D + H.
 *
 * D: kartta "39 mesaj" yazarken detayda "0 mesaj" yazıyordu — sayaç yüklenen
 *    KONUŞMAYI sayar; yüklenemezse/henüz yüklenmediyse hiç yazılmaz.
 * H: mesaj listesi ajan günlüğü gibiydi; asistanın nihai metni öne çıkar,
 *    düşünme + araç satırları TEK "Ayrıntı" satırına katlanır.
 */
class TranscriptFoldTest {

    private fun user(text: String) = SessionMessage(role = "user", content = text)

    private fun assistant(text: String?, reasoning: String? = null) =
        SessionMessage(role = "assistant", content = text, reasoning = reasoning)

    private fun assistantWithCalls(vararg names: String) = SessionMessage(
        role = "assistant",
        toolCallsRaw = names.map { ToolCall(functionRaw = ToolCallFunction(name = it, arguments = "{}")) },
    )

    private fun tool(name: String, body: String?) =
        SessionMessage(role = "tool", content = body, toolName = name)

    private fun system(text: String) = SessionMessage(role = "system", content = text)

    // ---- H) Sohbet = konuşma, günlük değil --------------------------------

    @Test
    fun `asistan nihai metni one cikar, dusunme tek ayrintiya katlanir`() {
        val rows = foldTranscript(
            listOf(
                user("raporu yaz"),
                assistant("Rapor hazır.", reasoning = "uzun düşünme zinciri"),
            ),
        )
        // [kullanıcı] [asistan metni] [Ayrıntı: düşünme]
        assertEquals(3, rows.size)
        assertTrue(rows[0] is TranscriptRow.Message)
        assertTrue(rows[1] is TranscriptRow.Message)
        assertEquals("Rapor hazır.", (rows[1] as TranscriptRow.Message).message.content)
        val detail = rows[2]
        assertTrue("düşünme tek Ayrıntı satırında", detail is TranscriptRow.Detail)
        val entry = (detail as TranscriptRow.Detail).entries.single()
        assertEquals("Düşünme", entry.name)
        assertTrue(entry.detail!!.contains("düşünme zinciri"))
    }

    @Test
    fun `dusunme ve araclar tek ayrinti satirinda toplanir`() {
        val rows = foldTranscript(
            listOf(
                user("raporu yaz"),
                assistant("Rapor hazır.", reasoning = "düşünme"),
                tool("execute_code", """{"status":"success"}"""),
            ),
        )
        val details = rows.filterIsInstance<TranscriptRow.Detail>()
        assertEquals("günlük tek satırda", 1, details.size)
        assertEquals(2, details.first().entries.size)
        assertEquals("Düşünme", details.first().entries.first().name)
        assertEquals("execute_code", details.first().entries[1].name)
    }

    @Test
    fun `arac cagrilari ve sonuclari ayni ayrintida`() {
        val rows = foldTranscript(
            listOf(
                assistantWithCalls("execute_code", "memory_search"),
                tool("execute_code", "cikti 1"),
                tool("execute_code", "cikti 2"),
                assistant("Bitti."),
            ),
        )
        val details = rows.filterIsInstance<TranscriptRow.Detail>()
        assertEquals("tüm günlük tek Ayrıntı satırı", 1, details.size)
        assertEquals(4, details.first().entries.size)
        val texts = rows.filterIsInstance<TranscriptRow.Message>().map { it.message.content }
        assertEquals(listOf("Bitti."), texts)
    }

    @Test
    fun `sistem mesaji one cikmaz`() {
        val rows = foldTranscript(listOf(system("sen bir asistansın"), user("selam")))
        assertEquals(2, rows.size)
        val detail = rows.first()
        assertTrue(detail is TranscriptRow.Detail)
        assertEquals("Sistem", (detail as TranscriptRow.Detail).entries.first().name)
        assertTrue(rows[1] is TranscriptRow.Message)
    }

    @Test
    fun `arac hatasi Failed olarak isaretlenir`() {
        val rows = foldTranscript(listOf(tool("execute_code", """{"error": "boom"}""")))
        val entry = rows.filterIsInstance<TranscriptRow.Detail>().first().entries.first()
        assertEquals(ToolEntryState.Failed, entry.state)
    }

    @Test
    fun `mesajsiz girdi bos liste`() {
        assertTrue(foldTranscript(emptyList()).isEmpty())
    }

    // ---- D) Sayaç tutarlılığı ---------------------------------------------

    @Test
    fun `yuklenirken sayac yazilmaz`() {
        assertEquals(null, detailCounterText(0, loading = true, error = null, loaded = 0, en = false))
        assertEquals(null, detailCounterText(39, loading = true, error = null, loaded = 39, en = false))
    }

    @Test
    fun `hata varsa sayac yazilmaz`() {
        assertEquals(
            null,
            detailCounterText(0, loading = false, error = "HTTP 500", loaded = 0, en = false),
        )
    }

    @Test
    fun `kart ve detay ayni sayiyi gosterir`() {
        // Kart "39 mesaj" diyorsa detay da 39 demeli (kusur D sözleşmesi).
        assertEquals("39 mesaj", detailCounterText(39, false, null, 39, en = false))
        // REST kaydı yoksa (canlı oturumda kayıt henüz yazılmadıysa) yüklenen sayılır.
        assertEquals("38 mesaj", detailCounterText(0, false, null, 38, en = false))
        assertEquals("39 messages", detailCounterText(39, false, null, 39, en = true))
    }

    @Test
    fun `gercekten bos oturumda sifir yazilmaz`() {
        // Yüklenmiş ama mesaj yok → satır gizlenir (kartla çelişen "0" yazılmaz).
        assertEquals(null, detailCounterText(0, false, null, 0, en = false))
    }
}
