package com.hermes.mobile.data

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.hermes.mobile.ui.tr
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Telefonu **okuma** yeteneği.
 *
 * [PhoneTools] baştan beri yalnız yazma tarafını yapıyordu: uygulama aç, ara,
 * yol tarifi başlat. Ama bir asistanın işe yaraması için telefon hakkında bir
 * şeyler *bilmesi* gerekiyor — "Ahmet'i ara" demek numara ezberlemeyi
 * gerektirmemeli, "bugün ne kaçırdım" bir soru olarak sorulabilmeli.
 *
 * Tasarım kararları:
 * - **İzin yoksa sessizce boş dönmüyoruz.** Kullanıcıya neyin eksik olduğunu
 *   söyleyen bir metin dönüyor; "bir şey bulamadım" ile "bakamadım" karıştığı
 *   sürece kimse izni vermeyi akıl edemiyor.
 * - **Okunan veri telefondan çıkmıyor** — sonuç metni ajana gidiyor, ham
 *   rehber/takvim dökümü değil. Eşleşen birkaç kayıt, o kadar.
 * - Konum için **son bilinen konum** kullanılıyor: canlı sabitleme saniyeler
 *   sürüyor ve sesli komutta o gecikme konuşmayı bozuyor. Yaş bilgisi
 *   sonuca yazılıyor ki ajan eskiliğini bilsin.
 */
class PhoneRead(private val context: Context) {

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // ── Rehber ────────────────────────────────────────────────────────

    data class Contact(val name: String, val number: String)

    /**
     * Ada göre kişi arar. Birden fazla eşleşme olabilir — çağıran taraf
     * hangisini kullanacağına karar veriyor, biz seçmiyoruz.
     */
    fun findContacts(query: String, limit: Int = 5): List<Contact> {
        if (query.isBlank() || !has(Manifest.permission.READ_CONTACTS)) return emptyList()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        // LIKE '%ad%' — kullanıcı "ahmet" diyor, rehberde "Ahmet Yılmaz" var.
        val where = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%${query.trim()}%")
        val out = LinkedHashMap<String, Contact>()
        runCatching {
            context.contentResolver.query(uri, cols, where, args, null)?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1)?.replace(" ", "") ?: continue
                    // Aynı kişinin iş/cep kayıtları ayrı satır; ad başına bir tane.
                    out.getOrPut(name) { Contact(name, number) }
                }
            }
        }
        return out.values.toList()
    }

    fun contactsSummary(query: String): String {
        if (!has(Manifest.permission.READ_CONTACTS)) return permissionHint("contacts")
        val found = findContacts(query)
        return when {
            found.isEmpty() -> tr("\"$query\" için kişi bulunamadı.", "No contact found for \"$query\".")
            found.size == 1 -> "${found[0].name} — ${found[0].number}"
            else -> found.joinToString("\n") { "${it.name} — ${it.number}" }
        }
    }

    // ── Takvim ────────────────────────────────────────────────────────

    /** Önümüzdeki [hours] saatteki etkinlikler. */
    fun upcomingEvents(hours: Int = 24, limit: Int = 10): String {
        if (!has(Manifest.permission.READ_CALENDAR)) return permissionHint("calendar")
        val now = System.currentTimeMillis()
        val until = now + TimeUnit.HOURS.toMillis(hours.toLong())
        val cols = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
        )
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString()).appendPath(until.toString()).build()
        val rows = mutableListOf<String>()
        runCatching {
            context.contentResolver.query(uri, cols, null, null, CalendarContract.Instances.BEGIN)
                ?.use { c ->
                    while (c.moveToNext() && rows.size < limit) {
                        val title = c.getString(0)?.takeIf { it.isNotBlank() }
                            ?: tr("(başlıksız)", "(untitled)")
                        val begin = c.getLong(1)
                        val place = c.getString(2)?.takeIf { it.isNotBlank() }
                        val allDay = c.getInt(3) == 1
                        val clock = if (allDay) tr("tüm gün", "all day")
                        else android.text.format.DateFormat.format("HH:mm", begin).toString()
                        rows += "$clock  $title" + (place?.let { " · $it" } ?: "")
                    }
                }
        }.onFailure {
            return tr("Takvim okunamadı: ${it.message}", "Couldn't read the calendar: ${it.message}")
        }
        return if (rows.isEmpty())
            tr("Önümüzdeki $hours saatte etkinlik yok.", "Nothing scheduled in the next $hours hours.")
        else rows.joinToString("\n")
    }

    // ── Konum ─────────────────────────────────────────────────────────

    fun whereAmI(): String {
        val fine = has(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = has(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) return permissionHint("location")

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return tr("Konum servisi yok.", "No location service.")

        // Sağlayıcılar arasında en taze olanı: GPS kapalıysa ağ konumu gelir.
        val best = runCatching {
            lm.getProviders(true).mapNotNull { p ->
                @Suppress("MissingPermission")
                lm.getLastKnownLocation(p)
            }.maxByOrNull { it.time }
        }.getOrNull() ?: return tr(
            "Son bilinen konum yok — haritayı bir kez açmak yeter.",
            "No last known location — opening a map once is enough.",
        )

        val yas = (System.currentTimeMillis() - best.time) / 60_000
        val tazelik = when {
            yas < 2 -> tr("az önce", "just now")
            yas < 60 -> tr("$yas dk önce", "$yas min ago")
            else -> tr("${yas / 60} sa önce", "${yas / 60} h ago")
        }
        return "${describe(best)} ($tazelik)"
    }

    private fun describe(loc: Location): String {
        val koordinat = String.format(Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude)
        // Geocoder ağ gerektiriyor ve sık başarısız oluyor; koordinat her
        // durumda dönsün, adres varsa üstüne eklensin.
        val adres = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(loc.latitude, loc.longitude, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
        }.getOrNull()
        return adres?.let { "$it  ($koordinat)" } ?: koordinat
    }

    // ── Pano ──────────────────────────────────────────────────────────

    fun clipboardRead(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return tr("Panoya erişilemedi.", "Couldn't access the clipboard.")
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        return text?.takeIf { it.isNotBlank() }
            ?: tr("Pano boş.", "Clipboard is empty.")
    }

    fun clipboardWrite(text: String): String {
        if (text.isBlank()) return tr("Yazılacak metin boş.", "Nothing to copy.")
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return tr("Panoya erişilemedi.", "Couldn't access the clipboard.")
        cm.setPrimaryClip(ClipData.newPlainText("hermes", text))
        return tr("Panoya kopyalandı.", "Copied to the clipboard.")
    }

    // ── Ortak ─────────────────────────────────────────────────────────

    /**
     * İzin eksikse ne yapılacağını **söyleyen** metin. Boş sonuç dönmek,
     * kullanıcıya "bulamadım" gibi görünüyor ve izin verilmesi gerektiği
     * hiç anlaşılmıyordu.
     */
    private fun permissionHint(kind: String): String = when (kind) {
        "contacts" -> tr(
            "Rehber izni yok. Ayarlar → Telefon denetimi'nden verebilirsin.",
            "No contacts permission. You can grant it in Settings → Phone control.",
        )
        "calendar" -> tr(
            "Takvim izni yok. Ayarlar → Telefon denetimi'nden verebilirsin.",
            "No calendar permission. You can grant it in Settings → Phone control.",
        )
        "location" -> tr(
            "Konum izni yok. Ayarlar → Telefon denetimi'nden verebilirsin.",
            "No location permission. You can grant it in Settings → Phone control.",
        )
        else -> tr("İzin yok.", "Permission not granted.")
    }
}
