package com.hermes.mobile.car

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object MolaCarMode {
    private fun classic(context: Context) = ComponentName(context, LegacyMolaCarService::class.java)
    private fun modern(context: Context) = ComponentName(context, HermesCarService::class.java)
    fun isClassic(context: Context): Boolean = context.packageManager.getComponentEnabledSetting(classic(context)) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    fun select(context: Context, classic: Boolean): Result<Unit> = runCatching {
        val pm = context.packageManager
        val first = classic(context); val second = modern(context)
        val yes = PackageManager.COMPONENT_ENABLED_STATE_ENABLED; val no = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        // Preserve an already selected route. Re-registering components at every phone
        // launch needlessly invalidates launcher/host discovery caches.
        if (pm.getComponentEnabledSetting(first) == (if (classic) yes else no) &&
            pm.getComponentEnabledSetting(second) == (if (classic) no else yes)) return@runCatching
        if (Build.VERSION.SDK_INT >= 33) {
            pm.setComponentEnabledSettings(listOf(
                PackageManager.ComponentEnabledSetting(first, if (classic) yes else no, PackageManager.DONT_KILL_APP),
                PackageManager.ComponentEnabledSetting(second, if (classic) no else yes, PackageManager.DONT_KILL_APP)))
        } else {
            val a = pm.getComponentEnabledSetting(first); val b = pm.getComponentEnabledSetting(second)
            try {
                pm.setComponentEnabledSetting(first, if (classic) yes else no, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(second, if (classic) no else yes, PackageManager.DONT_KILL_APP)
            } catch (error: Exception) {
                pm.setComponentEnabledSetting(first,a,PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(second,b,PackageManager.DONT_KILL_APP)
                throw error
            }
        }
    }
}
