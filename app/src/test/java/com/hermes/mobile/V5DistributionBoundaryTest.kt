package com.hermes.mobile

import com.hermes.mobile.data.ArtemisClient
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class V5DistributionBoundaryTest {
    private fun source(path: String): File = File("src/$path").let {
        if (it.exists()) it else File("app/src/$path")
    }

    @Test fun releaseUsesTlsAndSystemTrustOnly() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(source("main/res/xml/network_security_config.xml"))
        val base = doc.getElementsByTagName("base-config").item(0) as org.w3c.dom.Element
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        val certs = doc.getElementsByTagName("certificates")
        assertEquals(1, certs.length)
        assertEquals("system", (certs.item(0) as org.w3c.dom.Element).getAttribute("src"))
    }

    @Test fun lanOverrideIsDebugOnlyAndDoesNotTrustUserCa() {
        val text = source("debug/res/xml/network_security_config.xml").readText()
        assertTrue(text.contains("cleartextTrafficPermitted=\"true\""))
        assertFalse(text.contains("src=\"user\""))
    }

    @Test fun artemisRejectsPublicAndCredentialBearingEndpoints() {
        for (url in listOf("https://example.com", "http://8.8.8.8", "http://user:pass@192.168.1.2", "file:///etc/test")) {
            try { ArtemisClient(url); fail("Accepted unsafe endpoint") }
            catch (expected: IllegalArgumentException) { /* fail-closed */ }
        }
        assertEquals("http://192.168.1.2:8000", ArtemisClient("http://192.168.1.2:8000").base)
    }
}
