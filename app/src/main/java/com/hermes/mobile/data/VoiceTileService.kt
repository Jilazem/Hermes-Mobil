package com.hermes.mobile.data

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.hermes.mobile.MainActivity

/**
 * Hızlı Ayarlar döşemesi — perdeyi indir, dokun, konuş.
 *
 * Telefon asistanı gibi kullanabilmenin en kısa yolu: uygulamayı bulmak,
 * açmak, sekmeye gitmek yerine tek dokunuş. Kilit ekranından da çalışır
 * (`isLocked` durumunda sistem önce kilidi açtırır).
 */
@RequiresApi(Build.VERSION_CODES.N)
class VoiceTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("hermes_action", "voice")

        // Android 14+ döşemeden doğrudan `startActivity` çağrısını engelliyor;
        // PendingIntent'li aşırı yükleme tek desteklenen yol.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
