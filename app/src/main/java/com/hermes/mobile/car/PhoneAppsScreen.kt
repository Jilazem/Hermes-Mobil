package com.hermes.mobile.car

import android.content.Intent
import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.hermes.mobile.data.ScreenMirror
import com.hermes.mobile.data.SettingsStore
import com.hermes.mobile.data.PhoneBotLogic
import com.hermes.mobile.data.FullControl

/** Uses installed launcher activities; never invents packages or reports a successful car launch. */
class PhoneAppsScreen(carContext: CarContext) : Screen(carContext) {
    private var page = 0
    private val recent = carContext.getSharedPreferences("mola-recent-apps", 0)
    private val apps by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        carContext.packageManager.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .map { it.activityInfo.packageName to it.loadLabel(carContext.packageManager).toString() }
            .sortedBy { it.second.lowercase() }
    }
    override fun onGetTemplate(): Template {
        val last = recent.getString("packages", "").orEmpty().split('|').filter(String::isNotBlank)
        val ordered = apps.sortedWith(compareBy<Pair<String,String>> { last.indexOf(it.first).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }.thenBy { it.second.lowercase() })
        val rows = ItemList.Builder()
        val pageSize = runCatching { carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) }.getOrDefault(6).coerceIn(1, 20)
        ordered.drop(page * pageSize).take(pageSize).forEach { (pkg, label) ->
            rows.addItem(Row.Builder().setTitle(label).addText(if (pkg in last) "Son kullanılan" else "Telefonda yüklü")
                .setOnClickListener {
                    val blocked = PhoneBotLogic.commandBlockReason(SettingsStore(carContext).current(), FullControl.APP_START)
                    if (blocked != null) {
                        CarToast.makeText(carContext, "Telefon kontrolü kapalı: $blocked", CarToast.LENGTH_LONG).show()
                    } else if (!ScreenMirror.hasPermission) {
                        ScreenMirror.requestFromCar(carContext)
                        CarToast.makeText(carContext, "Önce telefonda ekran paylaşımına izin ver.", CarToast.LENGTH_LONG).show()
                    } else {
                        val launch = carContext.packageManager.getLaunchIntentForPackage(pkg)
                        if (launch != null) runCatching {
                            carContext.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            recent.edit().putString("packages", (listOf(pkg) + last).distinct().take(6).joinToString("|")).apply()
                            screenManager.push(MirrorScreen(carContext))
                        }.onFailure { CarToast.makeText(carContext, "Uygulama açılamadı; telefondan açmayı dene.", CarToast.LENGTH_LONG).show() }
                    }
                }.build())
        }
        val builder = ListTemplate.Builder().setTitle("Telefon uygulamaları · ${page + 1}")
            .setHeaderAction(Action.BACK).setSingleList(rows.setNoItemsMessage("Başlatılabilir uygulama bulunamadı").build())
        if (ordered.size > pageSize) builder.setActionStrip(ActionStrip.Builder().addAction(
            Action.Builder().setTitle("Sonraki sayfa").setOnClickListener {
                page = (page + 1) % ((ordered.size + pageSize - 1) / pageSize); invalidate()
            }.build()).build())
        return builder.build()
    }
}
