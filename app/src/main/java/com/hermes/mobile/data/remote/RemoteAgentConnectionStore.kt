package com.hermes.mobile.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.ref.WeakReference

/** Device credentials stay encrypted on this phone. No backup/export API contains them. */
class RemoteAgentConnectionStore internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(preferences(context.applicationContext))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutable = MutableStateFlow(synchronized(LOCK) { read() })
    val connections: StateFlow<List<RemoteAgentConnection>> = mutable.asStateFlow()
    private val listener = listener(WeakReference(this))
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }

    fun get(id: String): RemoteAgentConnection? = synchronized(LOCK) { read().firstOrNull { it.id == id } }

    fun upsert(connection: RemoteAgentConnection): RemoteAgentConnection = synchronized(LOCK) {
        require(connection.id.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) { "Geçersiz cihaz kimliği." }
        require(connection.label.trim().length in 1..64) { "Cihaz adı 1–64 karakter olmalı." }
        require(connection.token.isNotBlank() && connection.token.none { it.isISOControl() }) { "Cihaz erişim tokenı gerekli." }
        val clean = connection.copy(label = connection.label.trim(), baseUrl = normalizeRemoteEndpoint(connection.baseUrl), token = connection.token.trim())
        val list = read().filterNot { it.id == clean.id } + clean
        require(list.size <= 32) { "En fazla 32 cihaz kaydedilebilir." }
        prefs.edit().putString(KEY, json.encodeToString(list)).apply()
        mutable.value = list
        clean
    }

    fun remove(id: String) = synchronized(LOCK) {
        val list = read().filterNot { it.id == id }
        prefs.edit().putString(KEY, json.encodeToString(list)).apply()
        mutable.value = list
    }

    private fun read(): List<RemoteAgentConnection> = prefs.getString(KEY, null)?.let {
        json.decodeFromString<List<RemoteAgentConnection>>(it)
    } ?: emptyList()

    companion object {
        private const val KEY = "connections"
        private val LOCK = Any()
        @Volatile private var shared: SharedPreferences? = null
        private fun preferences(context: Context): SharedPreferences = shared ?: synchronized(LOCK) {
            shared ?: EncryptedSharedPreferences.create(context, "remote-agent-connections-v7",
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM).also { shared = it }
        }
        private fun listener(owner: WeakReference<RemoteAgentConnectionStore>) = object : SharedPreferences.OnSharedPreferenceChangeListener {
            override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
                val store = owner.get() ?: run { preferences.unregisterOnSharedPreferenceChangeListener(this); return }
                if (key == KEY || key == null) synchronized(LOCK) { store.mutable.value = store.read() }
            }
        }
    }
}
