package com.hermes.mobile.remote

import com.hermes.mobile.data.remote.*
import org.junit.Assert.*
import org.junit.Test

class RemoteEndpointPolicyTest {
    @Test fun preservesProxyPrefixAndSupportsPrivateVpnAddresses() {
        assertEquals("https://relay.example/device/a", normalizeRemoteEndpoint(" https://relay.example/device/a/ "))
        assertEquals("http://100.64.1.2:8765/phone", normalizeRemoteEndpoint("http://100.64.1.2:8765/phone/"))
        assertEquals("http://localhost:8765", normalizeRemoteEndpoint("http://localhost:8765/"))
        assertEquals("http://[fd00::1]:8765", normalizeRemoteEndpoint("http://[fd00::1]:8765/"))
    }

    @Test fun rejectsCleartextPublicHostsAndCredentialsInUrls() {
        listOf("http://8.8.8.8:8765", "http://example.com", "http://127.0.0.1.evil.example",
            "https://user:secret@example.com", "https://example.com?token=secret", "https://example.com#secret",
            "file:///tmp/key", "javascript:alert(1)").forEach { value ->
            assertTrue(value, runCatching { normalizeRemoteEndpoint(value) }.isFailure)
        }
    }

    @Test fun connectionDiagnosticNeverContainsTheToken() {
        val connection = RemoteAgentConnection(label = "Telefon", kind = RemoteAgentKind.MIKA,
            baseUrl = "http://127.0.0.1:8765", token = "secret-fixture")
        assertFalse(connection.toString().contains("secret-fixture"))
        assertTrue(connection.toString().contains("[hidden]"))
    }
}
