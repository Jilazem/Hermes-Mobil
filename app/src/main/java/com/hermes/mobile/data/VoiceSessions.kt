package com.hermes.mobile.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local leases. All Android entry points invoke this on the main thread. */
class VoiceSessionOwners {
    private data class Owner(val foreground: Boolean, val driving: Boolean, val cancel: () -> Unit)
    private val owners = linkedMapOf<String, Owner>()
    private val _paused = MutableStateFlow(false)
    val paused = _paused.asStateFlow()
    private var stopping = false
    val foreground: Boolean get() = owners.values.any { it.foreground }
    val driving: Boolean get() = owners.values.any { it.driving }
    val count: Int get() = owners.size

    fun acquire(id: String, foreground: Boolean, driving: Boolean, cancel: () -> Unit) {
        check(!stopping) { "Sesli oturum durduruluyor" }
        check(id !in owners) { "Sesli oturum zaten açık" }
        owners[id] = Owner(foreground, driving, cancel)
        _paused.value = true
    }

    fun release(id: String) {
        owners.remove(id)
        _paused.value = owners.isNotEmpty()
    }

    /** Keep leases until cleanup finishes; one failing owner must not skip the rest. */
    fun stopAll() {
        if (stopping) return
        stopping = true
        try {
            owners.values.toList().forEach { runCatching { it.cancel() } }
        } finally {
            owners.clear()
            _paused.value = false
            stopping = false
        }
    }
}

object VoiceSessions {
    val owners = VoiceSessionOwners()
}
