package com.hermes.mobile.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import kotlinx.serialization.json.JsonObject

/**
 * "Tam kontrol" araçlarının dağıtıcısı.
 *
 * [PhoneTools]'un kardeşi: aynı sözleşmeyi (ad + JSON argüman) uyguluyor ama
 * erişilebilirlik servisine dayanan komutlar burada. Ayrı tutuluyor çünkü
 * bu katmanın ön koşulu farklı (erişilebilirlik izni) ve izin yokken
 * verilecek hata mesajı da farklı olmalı.
 *
 * **Sessiz başarı yok:** servis yoksa, izin verilmemişse ya da hedef
 * bulunamazsa `ok=false` döner. Ajan "yaptım" diyemez.
 */
class FullControlTools(private val context: Context) {

    /** Ajanın çağırabileceği araç mı? */
    fun handles(name: String) = FullControl.isFullControlTool(name)

    fun execute(name: String, args: JsonObject): FullControl.Outcome {
        // Uygulama yönetimi erişilebilirlik gerektirmiyor: paket yöneticisi
        // ile yapılıyor. Bu yüzden servis kontrolünden ÖNCE geliyor.
        when (name) {
            FullControl.APPS -> return listApps()
            FullControl.APP_START -> return startApp(args.str("app") ?: args.str("package").orEmpty())
            FullControl.APP_STOP -> return stopApp(args.str("app") ?: args.str("package").orEmpty())
        }

        val svc = HermesAccessibilityService.instanceOrNull()
            ?: return FullControl.fail(
                "Erişilebilirlik servisi çalışmıyor: Hermes tam kontrol izni verilmemiş ya da " +
                    "servis kapalı. Ayarlar → Erişilebilirlik → Hermes'ten açılmalı " +
                    "(uygulama bunu programatik olarak veremiyor).",
            )

        return try {
            when (name) {
                FullControl.DUMP -> svc.dumpScreen()
                FullControl.TAP -> svc.tap(args, long = false)
                FullControl.LONG_PRESS -> svc.tap(args, long = true)
                FullControl.SWIPE -> svc.swipe(args)
                FullControl.TYPE -> svc.typeText(args)
                FullControl.KEY -> svc.key(args)
                FullControl.GLOBAL -> svc.globalNamed(args.str("action") ?: args.str("name").orEmpty())
                FullControl.SCREENSHOT -> svc.screenshot()
                else -> FullControl.fail("Bilinmeyen tam kontrol aracı: $name")
            }
        } catch (e: Exception) {
            DiagLog.e("a11y", "$name başarısız", e)
            FullControl.fail("$name başarısız: ${e.message}")
        }
    }

    // ── Uygulama yönetimi ────────────────────────────────────────────

    /**
     * Başlatılabilir uygulamaların listesi.
     *
     * Yalnız `LAUNCHER` kategorisindeki girdiler: sistem servisleri ve
     * arka plan bileşenleri listelenirse ajan yüzlerce anlamsız paket
     * arasında doğru adı bulamaz.
     */
    private fun listApps(): FullControl.Outcome {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = try {
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            return FullControl.fail("Uygulama listesi okunamadı: ${e.message}")
        }
        if (apps.isEmpty()) return FullControl.fail("Listelenecek başlatılabilir uygulama yok.")
        val rows = apps
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val listed = rows.take(120)
        val sb = StringBuilder()
        sb.append(rows.size).append(" başlatılabilir uygulama")
        if (rows.size > listed.size) sb.append(" (ilk ").append(listed.size).append(")")
        sb.append(":\n")
        listed.forEach { (pkg, label) -> sb.append("• ").append(label).append(" — ").append(pkg).append('\n') }
        return FullControl.done(sb.toString().trimEnd())
    }

    /**
     * Etiket ya da paket adıyla uygulama açar.
     *
     * Eşleşme sırası: paket adı → tam etiket (harf duyarsız) → etiket içerir.
     * Birden fazla içerir-eşleşmesi varsa **açmaz**: yanlış uygulamayı açmak
     * sessiz bir hatadır, adayları saymak daha iyidir.
     */
    private fun startApp(query: String): FullControl.Outcome {
        if (query.isBlank()) return FullControl.fail("Uygulama adı/paketi verilmedi.")
        val pm = context.packageManager
        val q = FullControl.foldTr(query)

        pm.getLaunchIntentForPackage(query)?.let { launch(it); return FullControl.done("$query açıldı.") }

        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        ).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }

        val exact = launchable.firstOrNull { FullControl.foldTr(it.second) == q }
        val loose = launchable.filter {
            FullControl.foldTr(it.second).contains(q) || it.first.contains(q, true)
        }.distinctBy { it.first }

        val target = exact ?: when {
            loose.isEmpty() -> return FullControl.fail(
                "\"$query\" adlı/etiketli uygulama bulunamadı.",
            )
            loose.size > 1 -> return FullControl.fail(
                "Birden fazla uygulama eşleşti, hangisi? " +
                    loose.take(6).joinToString(" | ") { "${it.second} (${it.first})" },
            )
            else -> loose[0]
        }

        val intent = pm.getLaunchIntentForPackage(target.first)
            ?: return FullControl.fail("${target.second} başlatılamıyor (başlatma niyeti yok).")
        launch(intent)
        return FullControl.done("${target.second} açıldı (${target.first}).")
    }

    /**
     * Uygulamayı durdurur.
     *
     * Dürüst sınır: üçüncü taraf bir uygulama, ön plandaki başka bir
     * uygulamayı sistem API'siyle zorla kapatamıyor (`force-stop` kabuk
     * yetkisi ister). Yapabildiğimiz: uygulama öndeyse ana ekrana dönmek ve
     * ardından arka plan sürecini öldürmek. Sonuçta ne olduğu açıkça
     * yazılıyor — "durduruldu" demek yanıltıcı olurdu.
     */
    private fun stopApp(query: String): FullControl.Outcome {
        if (query.isBlank()) return FullControl.fail("Uygulama adı/paketi verilmedi.")
        val pm = context.packageManager
        val pkg = resolvePackage(query)
            ?: return FullControl.fail("\"$query\" paketi bulunamadı.")

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return FullControl.fail("ActivityManager yok, uygulama durdurulamadı.")

        val foreground = HermesAccessibilityService.instanceOrNull()?.foregroundPackage()
        var wentHome = false
        if (foreground == pkg) {
            wentHome = HermesAccessibilityService.instanceOrNull()
                ?.global(FullControl.GlobalKind.HOME) == true
            if (wentHome) Thread.sleep(500)
        }

        val killed = runCatching { am.killBackgroundProcesses(pkg) }.isSuccess
        val note = buildString {
            if (wentHome) append("önce ana ekrana dönüldü; ")
            append(
                if (killed) "arka plan süreci öldürüldü"
                else "arka plan süreci öldürülemedi",
            )
            append(". Bir uygulamayı zorla kapatmak (force-stop) kabuk yetkisi ister — bu katman onu yapmıyor.")
        }
        return if (killed || wentHome) {
            FullControl.done("$pkg: $note")
        } else {
            FullControl.fail("$pkg durdurulamadı. $note")
        }
    }

    /** Paket adı ya da görünen ad çözümü. */
    private fun resolvePackage(query: String): String? {
        val pm = context.packageManager
        pm.getLaunchIntentForPackage(query)?.let { return query }
        val q = FullControl.foldTr(query)
        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        ).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        return launchable.firstOrNull { FullControl.foldTr(it.second) == q }?.first
            ?: launchable.firstOrNull { FullControl.foldTr(it.second).contains(q) }?.first
            ?: pm.getInstalledApplications(0)
                .firstOrNull { it.packageName.contains(query, true) }?.packageName
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
