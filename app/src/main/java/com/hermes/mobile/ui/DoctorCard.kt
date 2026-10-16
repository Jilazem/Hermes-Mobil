package com.hermes.mobile.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.hermes.mobile.data.BridgeState
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.DoctorLogic
import com.hermes.mobile.data.HermesAccessibilityService
import com.hermes.mobile.data.HermesClient
import com.hermes.mobile.data.HermesNotificationListener
import com.hermes.mobile.data.PhoneBridgeService
import com.hermes.mobile.data.ProbeResult
import com.hermes.mobile.data.ServerProfileStore
import com.hermes.mobile.data.ShizukuBridge
import com.hermes.mobile.ui.theme.HermesColors
import kotlinx.coroutines.launch

/**
 * K2 — "Doctor" kartı: telefon-tarafı önkoşul zincirinin TEK dokunuşlu taraması.
 *
 * phone-harness `doctor` deseninin Hermes-Mobil karşılığı: erişilebilirlik,
 * bildirim erişimi, Shizuku, köprü WS, sunucu erişimi ve Android Auto geliştirici
 * modu tek listede ✓ / ✗ / ? ile görünür; her eksik satırda kullanıcının
 * atabileceği **tek** adım ("şimdi yap") var. Kararlar saf [DoctorLogic]'te;
 * tarama sonucu [DiagLog]'a da yazılır (paylaşılabilir teşhis kaydı).
 *
 * Doğrulama sınırı: Android Auto geliştirici modu uygulamadan okunamaz —
 * satır "?" gösterir ve DOĞRULANAMADI olarak raporlanır.
 */
@Composable
fun DoctorCard(
    shizukuState: ShizukuBridge.State = ShizukuBridge.State.Unavailable,
    onRequestShizuku: () -> Unit = {},
    onOpenServers: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bridge by PhoneBridgeService.stateFlow.collectAsState()
    var checks by remember { mutableStateOf<List<DoctorLogic.Check>?>(null) }
    var scanning by remember { mutableStateOf(false) }

    /** "Şimdi yap" — her aksiyon tek derin bağlantı; başarısızlık sessiz değil. */
    fun runFix(fix: DoctorLogic.Fix) {
        when (fix) {
            DoctorLogic.Fix.OPEN_ACCESSIBILITY ->
                HermesAccessibilityService.openPermissionSettings(context)
            DoctorLogic.Fix.OPEN_NOTIFICATION_ACCESS -> runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            DoctorLogic.Fix.OPEN_SHIZUKU ->
                if (shizukuState == ShizukuBridge.State.NeedsPermission) {
                    onRequestShizuku()
                } else {
                    openAppOrDetails(context, "moe.shizuku.privileged.api")
                }
            DoctorLogic.Fix.RECONNECT_BRIDGE -> PhoneBridgeService.reconnect(context)
            DoctorLogic.Fix.OPEN_SERVERS -> onOpenServers()
            DoctorLogic.Fix.OPEN_ANDROID_AUTO ->
                openAppOrDetails(context, "com.google.android.projection.gearhead")
            DoctorLogic.Fix.NONE -> Unit
        }
    }

    HermesCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                S.t2("Doctor — telefon sağlık taraması", "Doctor — phone health scan"),
                color = HermesColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (scanning) S.t2("taranıyor…", "scanning…") else S.t2("Tara", "Scan"),
                color = if (scanning) HermesColors.TextFaint else HermesColors.Midground,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
                    .clickable(enabled = !scanning) {
                        scanning = true
                        scope.launch {
                            val list = scan(context, shizukuState, bridge)
                            checks = list
                            scanning = false
                            // Sonuç DiagLog'a da: "paylaşılabilir teşhis" tek kayıtta.
                            DoctorLogic.diagLines(list).forEachIndexed { i, line ->
                                if (i == 0) DiagLog.i("doctor", line) else DiagLog.w("doctor", line)
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Text(
            S.t2(
                "Ekran okuma, bildirim, Shizuku, köprü, sunucu ve Android Auto önkoşullarını " +
                    "tek listede tarar. Eksik olan tek adımda düzeltilir.",
                "Scans the screen-reading, notification, Shizuku, bridge, server and Android Auto " +
                    "prerequisites in one list. Each gap is fixable in a single step.",
            ),
            color = HermesColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )

        val list = checks
        if (list == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                S.t2(
                    "Henüz taranmadı — \"Tara\"ya dokun.",
                    "Not scanned yet — tap \"Scan\".",
                ),
                color = HermesColors.TextFaint,
                style = MaterialTheme.typography.labelSmall,
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                DoctorLogic.summary(list),
                color = HermesColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            list.forEach { check ->
                DoctorRow(check) { runFix(check.fix) }
            }
        }
    }
}

@Composable
private fun DoctorRow(
    check: DoctorLogic.Check,
    onFix: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            DoctorLogic.mark(check.status),
            color = when (check.status) {
                DoctorLogic.Status.OK -> HermesColors.Online
                DoctorLogic.Status.FAIL -> HermesColors.Danger
                DoctorLogic.Status.UNKNOWN -> HermesColors.Midground
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                doctorTitle(check.key),
                color = HermesColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                check.detail,
                color = HermesColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (check.fix != DoctorLogic.Fix.NONE && check.status != DoctorLogic.Status.OK) {
            Spacer(Modifier.width(8.dp))
            Text(
                S.t2("şimdi yap", "do it now"),
                color = HermesColors.Midground,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
                    .clickable(onClick = onFix)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun doctorTitle(key: String): String = when (key) {
    "a11y" -> S.t2("Erişilebilirlik (tam kontrol)", "Accessibility (full control)")
    "notification" -> S.t2("Bildirim erişimi", "Notification access")
    "shizuku" -> S.t2("Shizuku (derin kontrol)", "Shizuku (deep control)")
    "bridge" -> S.t2("Köprü WS (ajan kanalı)", "Bridge WS (agent channel)")
    "server" -> S.t2("Sunucu erişimi", "Server access")
    "android_auto" -> S.t2("Android Auto geliştirici modu", "Android Auto developer mode")
    else -> key
}

/** Kural adayı değil, uygulama kısayolu: paket açılmazsa uygulama bilgisi. */
private fun openAppOrDetails(context: android.content.Context, pkg: String) {
    val launched = runCatching {
        context.packageManager.getLaunchIntentForPackage(pkg)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }.isSuccess
    if (!launched) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/**
 * Altı alanın canlı taraması — UI'dan bağımsız, tek yerde.
 * Sıra: erişilebilirlik → bildirim → Shizuku → köprü → sunucu → Android Auto.
 */
private suspend fun scan(
    context: android.content.Context,
    shizukuState: ShizukuBridge.State,
    bridge: BridgeState,
): List<DoctorLogic.Check> {
    val sh = when (shizukuState) {
        ShizukuBridge.State.Ready -> DoctorLogic.ShizukuStatus.READY
        ShizukuBridge.State.NeedsPermission -> DoctorLogic.ShizukuStatus.NEEDS_PERMISSION
        ShizukuBridge.State.Unavailable -> DoctorLogic.ShizukuStatus.UNAVAILABLE
    }
    val br = when (bridge) {
        BridgeState.OFF -> DoctorLogic.BridgeStatus.OFF
        BridgeState.CONNECTING -> DoctorLogic.BridgeStatus.CONNECTING
        BridgeState.CONNECTED -> DoctorLogic.BridgeStatus.CONNECTED
        BridgeState.RETRYING -> DoctorLogic.BridgeStatus.RETRYING
        BridgeState.TAKEN_OVER -> DoctorLogic.BridgeStatus.TAKEN_OVER
    }
    val profile = ServerProfileStore(context).active()
    val (serverOk, serverDetail) = if (profile == null) {
        null to tr("sunucu profili yok", "no server profile")
    } else {
        when (val r = HermesClient(profile).probe()) {
            is ProbeResult.Ok ->
                true to tr("ulaşılabilir · ${r.latencyMs} ms", "reachable · ${r.latencyMs} ms")
            is ProbeResult.Fail -> false to r.reason
        }
    }
    val aaInstalled = runCatching {
        context.packageManager.getPackageInfo("com.google.android.projection.gearhead", 0)
    }.isSuccess
    return DoctorLogic.checks(
        a11yEnabled = HermesAccessibilityService.isEnabled(context),
        a11yRunning = HermesAccessibilityService.isRunning(),
        notificationAccess = HermesNotificationListener.accessGranted(context),
        shizuku = sh,
        bridge = br,
        serverOk = serverOk,
        serverDetail = serverDetail,
        androidAutoInstalled = aaInstalled,
    )
}