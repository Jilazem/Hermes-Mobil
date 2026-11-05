package com.hermes.mobile.car

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

data class MolaCarProfile(val id: String, val name: String, val lastPage: String = "home")
data class MolaCarDevice(val id: String, val name: String, val carAudio: Boolean = false)

/** Local preferences only. Never stores Bluetooth addresses, tokens or screen-share grants. */
class MolaCarProfiles(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("mola_car_profiles", Context.MODE_PRIVATE))
    fun all(): List<MolaCarProfile> = runCatching {
        val array = JSONArray(prefs.getString("profiles", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val p = array.optJSONObject(index) ?: return@mapNotNull null
            val id = p.optString("id")
            if (!id.matches(Regex("[a-f0-9]{64}"))) return@mapNotNull null
            MolaCarProfile(id, p.optString("name").take(60).ifBlank { "Aracım" }, page(p.optString("lastPage")))
        }.distinctBy { it.id }.take(20)
    }.getOrDefault(emptyList())

    fun remember(device: MolaCarDevice): MolaCarProfile? = synchronized(lock) {
        all().firstOrNull { it.id == device.id }?.let { return@synchronized it }
        if (!device.id.matches(Regex("[a-f0-9]{64}"))) return@synchronized null
        val old = all()
        if (old.size >= 20) return@synchronized null
        val profile = MolaCarProfile(device.id, device.name.take(60).ifBlank { "Aracım" })
        write(old + profile)
        profile
    }

    /** An unknown USB display is never guessed to be the previously connected vehicle. */
    fun resolve(connected: List<MolaCarDevice>, learnCarAudio: Boolean = false): MolaCarProfile? = synchronized(lock) {
        val devices = connected.distinctBy { it.id }
        val known = all().filter { p -> devices.any { it.id == p.id } }
        if (known.size == 1) return@synchronized known.single()
        if (known.isNotEmpty() || !learnCarAudio || devices.size != 1 || !devices.single().carAudio) return@synchronized null
        remember(devices.single())
    }

    fun setLastPage(id: String, value: String) = synchronized(lock) {
        write(all().map { if (it.id == id) it.copy(lastPage = page(value)) else it })
    }
    fun forget(id: String) = synchronized(lock) { write(all().filterNot { it.id == id }) }

    private fun write(profiles: List<MolaCarProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("lastPage", page(it.lastPage))) }
        check(prefs.edit().putString("profiles", array.toString()).commit()) { "Araç profili kaydedilemedi" }
    }
    companion object {
        private val lock = Any()
        fun page(value: String) = value.takeIf { it in setOf("home", "youtube", "mirror", "game") } ?: "home"
        fun deviceId(address: String): String? {
            val normalized = address.trim().uppercase(Locale.ROOT)
            if (!normalized.matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")) ||
                normalized in setOf("00:00:00:00:00:00", "02:00:00:00:00:00")) return null
            return MessageDigest.getInstance("SHA-256").digest(("hermes-mola-car-v1:" + normalized).toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
