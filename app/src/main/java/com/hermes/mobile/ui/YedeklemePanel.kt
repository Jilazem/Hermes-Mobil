package com.hermes.mobile.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.YedekDosya
import com.hermes.mobile.data.YedekMantik
import com.hermes.mobile.data.YedekModu
import com.hermes.mobile.data.YedekOkuma
import com.hermes.mobile.data.YedekStore
import com.hermes.mobile.ui.theme.HermesColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * K22 — Ayarlar → Yedekle / Geri Yükle paneli.
 *
 * Export: SAF CreateDocument ile kullanıcı dosya yerini seçer — önerilen ad
 * `hermes-mobil-yedek-YYYYMMDD-HHmm.json`. Yedek TOKENSIZdır (YedekStore).
 * Import: SAF OpenDocument → bozuk dosyada anlaşılır hata (çökme yok) →
 * önizleme (kaç bot/prompt/profil/bayrak/tema) → çakışma politikası:
 * Birleştir / Üzerine yaz.
 */
@Composable
fun YedeklemePanel(
    /** Cihazdaki yedeklenebilir veriyi toplar (AppViewModel.yedekVerisi). */
    onTopla: () -> YedekDosya,
    /** Seçilen çakışma politikasıyla yedeği cihaza uygular. */
    onUygula: (YedekDosya, YedekModu) -> Unit,
) {
    val context = LocalContext.current
    val yedekStore = remember { YedekStore(context) }
    val scope = rememberCoroutineScope()

    var durum by remember { mutableStateOf("") }
    /** İç aktarım onay diyaloğunun arkasındaki dosya; null = diyaloğu kapalı. */
    var bekleyen by remember { mutableStateOf<YedekDosya?>(null) }
    var secilenMod by remember { mutableStateOf(YedekModu.Birlestir) }

    fun bildir(mesaj: String) {
        durum = mesaj
        Toast.makeText(context, mesaj, Toast.LENGTH_LONG).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) {
            durum = tr("Yedek iptal edildi", "Backup cancelled")
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            val sonuc = yedekStore.export(uri, onTopla())
            withContext(Dispatchers.Main) {
                sonuc.fold(
                    onSuccess = {
                        bildir(tr("Yedek kaydedildi ✓", "Backup saved ✓"))
                    },
                    onFailure = {
                        val m = it.message ?: ""
                        bildir(tr("Yedek yazılamadı: $m", "Backup failed: $m"))
                    },
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            durum = tr("İç aktarım iptal edildi", "Import cancelled")
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            val metin = yedekStore.importText(uri).fold(
                onSuccess = { it },
                onFailure = { e ->
                    withContext(Dispatchers.Main) {
                        bildir(tr("Dosya okunamadı: ${e.message}", "Could not read file: ${e.message}"))
                    }
                    return@launch
                },
            )
            when (val okuma = YedekMantik.parse(metin)) {
                is YedekOkuma.Hata -> withContext(Dispatchers.Main) {
                    // Bozuk/eski/yeni-sürümlü dosyada çökme yok — anlaşılır mesaj.
                    bildir(okuma.mesaj)
                }
                is YedekOkuma.Tamam -> withContext(Dispatchers.Main) {
                    secilenMod = YedekModu.Birlestir
                    bekleyen = okuma.dosya
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .border(1.dp, HermesColors.Border, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            S.t2("Yedekle / Geri Yükle", "Backup / Restore"),
            color = HermesColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            S.t2(
                "Bot tanımları, kayıtlı prompt'lar, profil/bağlantı ayarları, " +
                    "oturum bayrakları ve tercihler tek JSON dosyasına yazılır. " +
                    "Gateway token'ları yedekLENMEZ; geri yükleme sonrası yeniden girilir.",
                "Bot identities, saved prompts, profile/connection settings, session " +
                    "flags and preferences are written to a single JSON file. Gateway " +
                    "tokens are NOT backed up; re-enter them after a restore.",
            ),
            color = HermesColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val damga = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    exportLauncher.launch("hermes-mobil-yedek-$damga.json")
                },
            ) { Text(S.t2("Yedekle", "Backup")) }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
            ) { Text(S.t2("Geri Yükle", "Restore")) }
        }
        if (durum.isNotBlank()) {
            Text(durum, color = HermesColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }

    bekleyen?.let { dosya ->
        val s = YedekMantik.sayim(dosya)
        AlertDialog(
            onDismissRequest = { bekleyen = null },
            title = { Text(S.t2("Yedek dosyası", "Backup file")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        S.t2(
                            "${s.botSayisi} bot · ${s.promptSayisi} prompt · ${s.profilSayisi} profil · " +
                                "${s.bayrakKumesiSayisi} bayrak kümesi · ${s.temaSayisi} özel tema",
                            "${s.botSayisi} bots · ${s.promptSayisi} prompts · ${s.profilSayisi} profiles · " +
                                "${s.bayrakKumesiSayisi} flag sets · ${s.temaSayisi} custom themes",
                        ),
                        color = HermesColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        S.t2(
                            "Bu yedek token içermez. Çakışma politikasını seçin:",
                            "This backup contains no tokens. Choose the collision policy:",
                        ),
                        color = HermesColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(2.dp))
                    PolicyRow(
                        secili = secilenMod == YedekModu.Birlestir,
                        baslik = S.t2("Birleştir", "Merge"),
                        aciklama = S.t2(
                            "Cihazdaki kayıtlar korunur; eksikler yedekten eklenir.",
                            "Existing records are kept; missing ones are added from the backup.",
                        ),
                        onSelect = { secilenMod = YedekModu.Birlestir },
                    )
                    PolicyRow(
                        secili = secilenMod == YedekModu.UzerineYaz,
                        baslik = S.t2("Üzerine yaz", "Overwrite"),
                        aciklama = S.t2(
                            "Cihazdaki karşılıklar silinir, yedekteki yüklenir (token'lar boş gelir).",
                            "Existing counterparts are replaced by the backup (tokens come empty).",
                        ),
                        onSelect = { secilenMod = YedekModu.UzerineYaz },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUygula(dosya, secilenMod)
                    bekleyen = null
                    bildir(tr("Geri yükleme tamamlandı ✓", "Restore finished ✓"))
                }) { Text(S.t2("Uygula", "Apply")) }
            },
            dismissButton = {
                TextButton(onClick = { bekleyen = null }) { Text(S.t2("Vazgeç", "Cancel")) }
            },
        )
    }
}

@Composable
private fun PolicyRow(
    secili: Boolean,
    baslik: String,
    aciklama: String,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = secili, onClick = onSelect)
        Column {
            Text(baslik, color = HermesColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Text(aciklama, color = HermesColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
