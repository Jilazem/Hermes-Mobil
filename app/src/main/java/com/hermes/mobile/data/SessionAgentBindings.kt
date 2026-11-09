package com.hermes.mobile.data

import android.content.Context

/** Durable profile ownership is scoped to the configured server, never to a display title. */
internal class SessionAgentBindings(context: Context) {
    private val prefs = context.getSharedPreferences("session-agent-bindings", Context.MODE_PRIVATE)
    private fun key(server: String, stored: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest("${server.length}:$server$stored".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun profile(server: String, stored: String): String? = prefs.getString(key(server, stored), null)
    fun remember(server: String, stored: String, agent: String) {
        if (stored.isNotBlank() && agent.isNotBlank()) prefs.edit().putString(key(server, stored), agent).apply()
    }
}
