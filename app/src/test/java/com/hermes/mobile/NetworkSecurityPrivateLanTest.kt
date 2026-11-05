package com.hermes.mobile

import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.data.forceTlsOutsideLan
import com.hermes.mobile.data.isPrivateHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * B2 (v6.2) — "cleartext yalnız özel ağ" sözleşmesi.
 *
 * Android ağ güvenliği yapılandırması RFC1918 CIDR ifade edemez. Bu yüzden
 * platform düzeyinde cleartext yalnız debug kaynak kümesinde açılır; özel ağ
 * dışına düz metin çıkışını uygulama kapısı [forceTlsOutsideLan] engeller.
 * Bu testler iki katmanı da kilitler:
 *  1. XML: release cleartext=false + yalnız sistem CA; debug cleartext=true,
 *     kullanıcı CA yok, dosya yalnız src/debug'da.
 *  2. Kod: RFC1918/localhost/.local korunur, diğer host'lar https'e yükseltilir;
 *     ServerProfile.normalizedUrl bu kapıdan geçer.
 */
class NetworkSecurityPrivateLanTest {

    private fun source(path: String): File = File("src/$path").let {
        if (it.exists()) it else File("app/src/$path")
    }

    private fun parse(path: String) = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(source(path))

    // ---- 1. XML katmanı -------------------------------------------------

    @Test
    fun releaseConfigForbidsCleartextAndTrustsSystemOnly() {
        val doc = parse("main/res/xml/network_security_config.xml")
        val base = doc.getElementsByTagName("base-config").item(0) as org.w3c.dom.Element
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        val certs = doc.getElementsByTagName("certificates")
        assertEquals(1, certs.length)
        assertEquals("system", (certs.item(0) as org.w3c.dom.Element).getAttribute("src"))
        assertFalse(
            "release yapılandırması kullanıcı CA'sına güvenmemeli",
            source("main/res/xml/network_security_config.xml").readText().contains("src=\"user\""),
        )
    }

    @Test
    fun debugConfigPermitsCleartextOnlyInDebugSourceSet() {
        val debugFile = source("debug/res/xml/network_security_config.xml")
        assertTrue("debug ağ yapılandırması src/debug'da olmalı", debugFile.exists())

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(debugFile)
        val base = doc.getElementsByTagName("base-config").item(0) as org.w3c.dom.Element
        assertEquals("true", base.getAttribute("cleartextTrafficPermitted"))
        assertFalse(
            "debug yapılandırması kullanıcı CA'sına güvenmemeli",
            debugFile.readText().contains("src=\"user\""),
        )

        // Aynı ada sahip main dosyası cleartext'i ASLA açmaz (release'e sızmaz).
        assertFalse(
            "src/main yapılandırmasında cleartext açık olmamalı",
            source("main/res/xml/network_security_config.xml").readText()
                .contains("cleartextTrafficPermitted=\"true\""),
        )
    }

    @Test
    fun manifestBindsNetworkSecurityConfig() {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(source("main/AndroidManifest.xml"))
        val app = doc.getElementsByTagName("application").item(0) as org.w3c.dom.Element
        assertEquals(
            "@xml/network_security_config",
            app.getAttributeNS("http://schemas.android.com/apk/res/android", "networkSecurityConfig"),
        )
    }

    // ---- 2. Kod kapısı --------------------------------------------------

    @Test
    fun privateLanHostsKeepCleartext() {
        val private = listOf(
            "192.168.1.42:8000",
            "10.0.0.51:9150",
            "10.0.2.2:9199",
            "172.16.0.1",
            "172.31.255.254:8000",
            "127.0.0.1:8000",
            "localhost:9150",
            "mac-mini.local:8000",
        )
        for (host in private) {
            assertEquals("http://$host/api/chat", forceTlsOutsideLan("http://$host/api/chat"))
        }
    }

    @Test
    fun publicHostsAreUpgradedToTls() {
        val public = listOf(
            "8.8.8.8",
            "example.com",
            "example.com:8443",
            "172.15.0.1",      // RFC1918 alt sınırının bir altı
            "172.32.0.1",      // RFC1918 üst sınırının bir üstü
            "192.169.0.1",     // 192.168/16 dışı
            "11.0.0.1",
        )
        for (host in public) {
            assertEquals("https://$host/x", forceTlsOutsideLan("http://$host/x"))
        }
    }

    @Test
    fun credentialsPortsAndPathsSurviveUpgrade() {
        assertEquals(
            "https://user:pass@example.com:8080/path?q=1#frag",
            forceTlsOutsideLan("http://user:pass@example.com:8080/path?q=1#frag"),
        )
        assertEquals(
            "http://user:pass@192.168.1.2:8000/path",
            forceTlsOutsideLan("http://user:pass@192.168.1.2:8000/path"),
        )
        // https ve ws:// bu fonksiyonun konusu değil — dokunulmaz.
        assertEquals("https://example.com", forceTlsOutsideLan("https://example.com"))
        assertEquals("ws://example.com", forceTlsOutsideLan("ws://example.com"))
    }

    @Test
    fun serverProfileNormalizedUrlGoesThroughGate() {
        val lan = ServerProfile(name = "ev", baseUrl = "192.168.1.2:8000", token = "t")
        assertEquals("http://192.168.1.2:8000", lan.normalizedUrl)
        assertFalse(lan.isHttps)

        val wan = ServerProfile(name = "wan", baseUrl = "http://example.com", token = "t")
        assertEquals("https://example.com", wan.normalizedUrl)
        assertTrue(wan.isHttps)

        // Sorgu/parça temizliği kapıdan ÖNCE çalışır; sonuç yine TLS olmalı.
        val dirty = ServerProfile(name = "d", baseUrl = "http://example.com:9150/?profile=x", token = "t")
        assertEquals("https://example.com:9150", dirty.normalizedUrl)
    }

    @Test
    fun isPrivateHostBoundaries() {
        assertTrue(isPrivateHost("10.0.0.1"))
        assertTrue(isPrivateHost("127.0.0.1"))
        assertTrue(isPrivateHost("192.168.140.20"))
        assertTrue(isPrivateHost("172.16.0.1"))
        assertTrue(isPrivateHost("172.31.255.255"))
        assertTrue(isPrivateHost("localhost"))
        assertTrue(isPrivateHost("LOCALHOST"))
        assertTrue(isPrivateHost("mac-mini.local"))

        assertFalse(isPrivateHost("172.15.255.255"))
        assertFalse(isPrivateHost("172.32.0.1"))
        assertFalse(isPrivateHost("192.169.0.1"))
        assertFalse(isPrivateHost("11.0.0.1"))
        assertFalse(isPrivateHost("8.8.8.8"))
        assertFalse(isPrivateHost("example.com"))
        assertFalse(isPrivateHost("not.local.example.com"))
        assertFalse(isPrivateHost(""))
    }
}
