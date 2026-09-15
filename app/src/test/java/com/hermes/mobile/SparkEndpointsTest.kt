package com.hermes.mobile

import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.data.SparkEndpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-10 / F4 — Spark adres adayları ve hata metni.
 *
 * Mac ölçümü (`denetim/tur10/spark-probe.json`): LAN'da `:5555` → 200,
 * `/spark-api` (dış) → 403, `/api/sparks` → 404.
 */
class SparkEndpointsTest {

    private val t: (String, String) -> String = { tr, _ -> tr }

    private fun profile(base: String, remote: String = "", spark: String = "") = ServerProfile(
        name = "test",
        baseUrl = base,
        remoteUrl = remote,
        token = "tok",
        bridgeUrl = spark,   // kullanılmayan alan; kopmalı değil
    )

    @Test
    fun `ev agi adresi dogrudan 5555 portuna gider`() {
        val p = profile("http://192.168.1.101:9150")
        assertEquals(listOf("http://192.168.1.101:5555"), SparkEndpoints.candidates(p))
    }

    @Test
    fun `dis adres spark-api yolundan gider`() {
        val p = profile("https://hermes.example.pro")
        assertEquals(listOf("https://hermes.example.pro/spark-api"), SparkEndpoints.candidates(p))
    }

    @Test
    fun `LAN sonra uzak - sira korunur`() {
        val p = profile("http://192.168.1.101:9150", remote = "https://hermes.example.pro")
        assertEquals(
            listOf("http://192.168.1.101:5555", "https://hermes.example.pro/spark-api"),
            SparkEndpoints.candidates(p),
        )
    }

    @Test
    fun `calisan adres one alinir`() {
        val p = profile("http://192.168.1.101:9150", remote = "https://hermes.example.pro")
        assertEquals(
            listOf("https://hermes.example.pro/spark-api", "http://192.168.1.101:5555"),
            SparkEndpoints.candidates(p, preferred = "https://hermes.example.pro/spark-api"),
        )
    }

    @Test
    fun `ayardaki acik adres tek basina kazanir`() {
        val p = profile("http://192.168.1.101:9150", remote = "https://hermes.example.pro")
        assertEquals(
            listOf("http://10.0.0.9:5555"),
            SparkEndpoints.candidates(p, explicit = "http://10.0.0.9:5555/"),
        )
    }

    @Test
    fun `turetilmis bicimde verilen son calisan adres de one alinir`() {
        val p = profile("http://192.168.1.101:9150", remote = "https://hermes.example.pro")
        // LAN biçimi
        assertEquals(
            listOf("http://192.168.1.101:5555", "https://hermes.example.pro/spark-api"),
            SparkEndpoints.candidates(p, preferred = "http://192.168.1.101:5555"),
        )
    }

    @Test
    fun `ayni host iki kez uretilmez`() {
        val p = profile("http://192.168.1.101:9150", remote = "http://192.168.1.101:9150")
        assertEquals(listOf("http://192.168.1.101:5555"), SparkEndpoints.candidates(p))
    }

    @Test
    fun `403 sunucu tarafi is notu verir`() {
        val msg = SparkEndpoints.describe(
            listOf(SparkEndpoints.Attempt("https://hermes.example.pro/spark-api", 403, "forbidden")),
            t,
        )
        assertTrue("403 için sunucu tarafı iş denmeli: $msg", msg.contains("SPARK_GATE_TOKEN"))
        assertTrue(msg.contains("403"))
        assertTrue(msg.contains("hermes.example.pro/spark-api"))
    }

    @Test
    fun `404 sparkDash ucu yok der`() {
        val msg = SparkEndpoints.describe(
            listOf(SparkEndpoints.Attempt("http://192.168.1.101:5555", 404, "{}")),
            t,
        )
        assertTrue("404 için uç yok denmeli: $msg", msg.contains("sparkDash"))
        assertTrue(msg.contains("404"))
    }

    @Test
    fun `hicbiri ulasilamazsa adres ipucu verir`() {
        val msg = SparkEndpoints.describe(
            listOf(
                SparkEndpoints.Attempt("http://192.168.1.101:5555", -1, "timeout"),
                SparkEndpoints.Attempt("https://hermes.example.pro/spark-api", -1, "no route"),
            ),
            t,
        )
        assertTrue("ulaşılamıyor denmeli: $msg", msg.contains("ulaşılamıyor"))
        assertTrue(msg.contains("5555"))
        assertTrue(msg.contains("/spark-api"))
    }

    @Test
    fun `deneme listesi bos ise adres sorulur`() {
        val msg = SparkEndpoints.describe(emptyList(), t)
        assertTrue(msg.contains("tanımlı değil"))
    }

    @Test
    fun `karisik hata durumunda kapı hatasi oncelikli`() {
        val msg = SparkEndpoints.describe(
            listOf(
                SparkEndpoints.Attempt("http://192.168.1.101:5555", -1, "timeout"),
                SparkEndpoints.Attempt("https://hermes.example.pro/spark-api", 403, "forbidden"),
            ),
            t,
        )
        assertTrue("403 öncelikli anlatılmalı: $msg", msg.contains("SPARK_GATE_TOKEN"))
    }

    @Test
    fun `hata metni denenen her adresi ve kodu icerir`() {
        val msg = SparkEndpoints.describe(
            listOf(
                SparkEndpoints.Attempt("http://a:5555", -1, "x"),
                SparkEndpoints.Attempt("http://b:5555", 500, "boom"),
            ),
            t,
        )
        assertTrue(msg.contains("http://a:5555"))
        assertTrue(msg.contains("http://b:5555"))
        assertTrue(msg.contains("HTTP 500"))
    }

    @Test
    fun `kod metni okunur`() {
        assertEquals("HTTP 403", SparkEndpoints.codeText(403))
        assertEquals("ulaşılamadı", SparkEndpoints.codeText(-1))
        assertEquals("ulaşılamadı", SparkEndpoints.codeText(0))
    }

    @Test
    fun `attempt basari ve erisim durumunu bilir`() {
        assertTrue(SparkEndpoints.Attempt("x", 200).ok)
        assertTrue(SparkEndpoints.Attempt("x", 200).reachable)
        assertTrue(!SparkEndpoints.Attempt("x", 403).ok && SparkEndpoints.Attempt("x", 403).reachable)
        assertTrue(!SparkEndpoints.Attempt("x", -1).reachable)
    }
}
