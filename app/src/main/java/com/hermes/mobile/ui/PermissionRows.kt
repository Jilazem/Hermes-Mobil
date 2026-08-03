package com.hermes.mobile.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.hermes.mobile.data.HermesNotificationListener
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Telefonu okuma erişimleri — verilenler ve verilmeyenler tek listede.
 *
 * Neden ayrı bir bölüm: bu yetenekler izin olmadan **sessizce** işe yaramıyor.
 * Kullanıcı "Ahmet'i ara" diyor, "kişi bulunamadı" cevabı alıyor ve sorunun
 * rehber izni olduğunu anlamıyor. Burada ne eksik olduğu görünüyor ve tek
 * dokunuşla veriliyor.
 *
 * Hiçbiri zorunlu değil: verilmeyen izin yalnız ilgili aracı devre dışı
 * bırakıyor, uygulamanın kalanı çalışmaya devam ediyor.
 */
@Composable
fun ReadAccessRows() {
    val context = LocalContext.current

    // Kullanıcı sistem ayarlarına gidip dönünce liste tazelenmeli. Compose
    // kendiliğinden bilmiyor: izin durumu Compose state'i değil.
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { tick++ }

    @Suppress("UNUSED_EXPRESSION") tick   // durum okumalarını tick'e bağlar

    Column(Modifier.fillMaxWidth()) {
        Text(
            S.t2(
                "Sesli asistanın telefon hakkında bir şey bilebilmesi için. " +
                    "Vermediğin izin yalnız o özelliği kapatır.",
                "So the assistant can actually know something about your phone. " +
                    "Anything you don't grant just disables that one capability.",
            ),
            color = HermesColors.TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(6.dp))

        AccessRow(
            title = S.t2("Rehber", "Contacts"),
            why = S.t2(
                "\"Ahmet'i ara\" — numara ezberlemeden.",
                "\"Call Ahmet\" — without memorising numbers.",
            ),
            granted = granted(context, Manifest.permission.READ_CONTACTS),
        ) { launcher.launch(arrayOf(Manifest.permission.READ_CONTACTS)) }

        AccessRow(
            title = S.t2("Takvim", "Calendar"),
            why = S.t2("\"Bugün ne var?\"", "\"What's on today?\""),
            granted = granted(context, Manifest.permission.READ_CALENDAR),
        ) { launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR)) }

        AccessRow(
            title = S.t2("Konum", "Location"),
            why = S.t2(
                "\"Neredeyim?\" ve yol tarifi hesabı.",
                "\"Where am I?\" and travel estimates.",
            ),
            granted = granted(context, Manifest.permission.ACCESS_COARSE_LOCATION),
        ) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }

        // Bildirim erişimi normal bir izin değil; sistem ayarlarına gidiliyor.
        AccessRow(
            title = S.t2("Bildirim erişimi", "Notification access"),
            why = S.t2(
                "\"Bugün ne kaçırdım?\" — Android bunu ayrı bir ekrandan veriyor.",
                "\"What did I miss?\" — Android grants this on its own screen.",
            ),
            granted = HermesNotificationListener.accessGranted(context),
        ) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun AccessRow(
    title: String,
    why: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .background(HermesColors.SurfaceDim, RoundedCornerShape(10.dp))
            // Verilmiş bir izni geri almak uygulamadan mümkün değil; satır
            // yalnız eksikken tıklanabilir olsun ki boş bir dokunuş olmasın.
            .clickable(enabled = !granted, onClick = onGrant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = HermesColors.TextPrimary, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(why, color = HermesColors.TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.size(8.dp))
        if (granted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(7.dp).background(HermesColors.Online, CircleShape),
                )
                Text(S.t2("verildi", "granted"), color = HermesColors.Online, fontSize = 11.sp)
            }
        } else {
            Text(
                S.t2("izin ver", "grant"),
                color = HermesColors.Midground,
                fontSize = 12.sp,
            )
        }
    }
}
