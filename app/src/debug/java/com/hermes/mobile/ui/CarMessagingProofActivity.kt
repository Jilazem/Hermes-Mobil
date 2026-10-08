package com.hermes.mobile.ui

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.lifecycle.lifecycleScope
import com.hermes.mobile.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File

/** Debug only: inspect the real notification and trigger its actual RemoteInput action. */
class CarMessagingProofActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = TextView(this).apply { setPadding(24,48,24,24) }; setContentView(ui)
        val proof = File(filesDir, "car-messaging-proof.txt")
        fun step(line: String) { ui.append("$line\n"); proof.appendText("$line\n") }
        proof.writeText("")
        lifecycleScope.launch {
            var gateway: GatewayWsClient? = null
            try {
                val profile = checkNotNull(ServerProfileStore(this@CarMessagingProofActivity).active())
                val testReply = intent.getBooleanExtra("reply", false)
                val sid = if (testReply) {
                    val gw = GatewayWsClient(profile).also { gateway = it }
                    gw.connect()
                    withTimeout(20_000) { gw.connection.first { it is ConnectionState.Open } }
                    val saved = getSharedPreferences("car-messaging-proof", MODE_PRIVATE)
                    val previous = saved.getString(profile.id, null)
                    val runtime = if (previous == null) gw.createSession(null) else gw.attachSession(previous)
                    val stored = gw.storedSessionId(runtime)
                    saved.edit().putString(profile.id, stored).apply()
                    val settings = SettingsStore(this@CarMessagingProofActivity).settings.value
                    val model = settings.modelsByServer[profile.id] ?: settings.lastModel.takeIf { settings.modelsByServer.isEmpty() }.orEmpty()
                    val parts = model.split("|", limit = 2)
                    step("PROFILE_MODEL_SAVED=${parts.size == 2}")
                    val override = intent.getStringExtra("model")?.split("|", limit = 2)
                    val selection = override ?: parts
                    if (selection.size == 2) {
                        check(modelSwitchAccepted(gw.slashExec(runtime, "/model ${selection[1]} --provider ${selection[0]}")))
                        step("PASS selected test session model accepted")
                    }
                    stored
                } else "car-messaging-diagnostic"
                step("PASS test conversation ready")
                Notifier.agentReply(this@CarMessagingProofActivity, "Hermes araç mesajlaşma denemesi hazır.",
                    sid, force = true, profileId = profile.id, carVoice = true)
                val nm = getSystemService(NotificationManager::class.java)
                val notice = withTimeout(5_000) {
                    var found: android.service.notification.StatusBarNotification? = null
                    while (found == null) {
                        found = nm.activeNotifications.firstOrNull { it.tag?.startsWith("car:") == true &&
                            it.tag?.endsWith("/${android.net.Uri.encode(sid)}") == true }
                        if (found == null) delay(100)
                    }
                    found
                }
                val n = notice.notification
                check(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) != null)
                val action = (0 until NotificationCompat.getActionCount(n)).mapNotNull { NotificationCompat.getAction(n, it) }
                    .single { it.semanticAction == NotificationCompat.Action.SEMANTIC_ACTION_REPLY }
                check(!action.showsUserInterface && action.remoteInputs?.size == 1)
                check(NotificationCompat.getInvisibleActions(n).any {
                    it.semanticAction == NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ && !it.showsUserInterface })
                step("PASS MessagingStyle, reply RemoteInput, invisible mark-read; conversation scoped")
                if (testReply) {
                    val fill = Intent()
                    val remote = Bundle().apply { putCharSequence(ReplyService.KEY_REPLY,
                        "Bu sadece selamlaşma; hiçbir araç kullanma, komut çalıştırma veya inceleme yapma. Yalnızca şu iki kısa cümleyi söyle: Merhaba Gökhan. Seni duyuyorum.") }
                    RemoteInput.addResultsToIntent(checkNotNull(action.remoteInputs), fill, remote)
                    checkNotNull(action.actionIntent).send(this@CarMessagingProofActivity, 0, fill)
                    step("PASS actual reply PendingIntent sent; waiting for complete answer and EMA")
                    withTimeout(200_000) {
                        while (true) {
                            delay(500)
                            val updated = nm.activeNotifications.firstOrNull { it.tag?.startsWith("car:${profile.id}/") == true &&
                                it.notification.`when` >= notice.notification.`when` && it.tag != notice.tag }
                                ?: nm.activeNotifications.firstOrNull { it.tag == notice.tag }
                            val messages = updated?.notification?.let(NotificationCompat.MessagingStyle::extractMessagingStyleFromNotification)?.messages
                            val answer = messages?.lastOrNull()?.text?.toString().orEmpty()
                            if (answer != "Hermes araç mesajlaşma denemesi hazır." && answer.isNotBlank()) {
                                check(answer.contains("Merhaba Gökhan") && answer.contains("Seni duyuyorum", ignoreCase = true))
                                check(!answer.contains("EMA okuyamadı"))
                                step("PASS full two-sentence gateway response and foreground EMA playback completed")
                                break
                            }
                        }
                    }
                }
                step("RESULT PASS")
            } catch(e: Exception) { step("RESULT FAIL ${e.javaClass.simpleName}") }
            finally { gateway?.close() }
        }
    }
}
