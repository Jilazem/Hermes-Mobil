package com.hermes.mobile.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap

/**
 * Alt ajan çatal ve oturum ağacı ilişkilerini cihazda saklar ve çözümler.
 * Ana oturum -> alt oturumlar (çocuklar) ve çocuk -> ana oturum eşlemeleri.
 *
 * 9120 web konsolu ile uyumlu: alt ajan çalışmaları bağımsız "sohbet" oturumları
 * olarak listeyi kirletmez; ana oturumun altında ağaç dalı (çatal) olarak gruplanır.
 */
class SubagentTreeStore(context: Context? = null) {
    private val prefs = context?.getSharedPreferences("subagent-tree-store", Context.MODE_PRIVATE)

    companion object {
        private val memoryBindings = ConcurrentHashMap<String, String>() // childSessionId -> parentSessionId

        fun record(childSessionId: String, parentSessionId: String) {
            val c = childSessionId.trim()
            val p = parentSessionId.trim()
            if (c.isBlank() || p.isBlank() || c == p) return
            memoryBindings[c] = p
        }

        fun parentOf(childSessionId: String): String? = memoryBindings[childSessionId.trim()]

        fun allBindings(): Map<String, String> = HashMap(memoryBindings)

        @VisibleForTesting
        fun clear() {
            memoryBindings.clear()
        }
    }

    init {
        prefs?.all?.forEach { (child, parent) ->
            if (parent is String && child.isNotBlank() && parent.isNotBlank()) {
                memoryBindings[child] = parent
            }
        }
    }

    fun remember(childSessionId: String, parentSessionId: String) {
        val c = childSessionId.trim()
        val p = parentSessionId.trim()
        if (c.isBlank() || p.isBlank() || c == p) return
        record(c, p)
        prefs?.edit()?.putString(c, p)?.apply()
    }

    fun parent(childSessionId: String): String? = parentOf(childSessionId) ?: prefs?.getString(childSessionId, null)

    fun children(parentSessionId: String): Set<String> {
        val p = parentSessionId.trim()
        val inMem = memoryBindings.filterValues { it == p }.keys
        val inPrefs = prefs?.all?.filterValues { it == p }?.keys ?: emptySet()
        return inMem + inPrefs
    }
}
