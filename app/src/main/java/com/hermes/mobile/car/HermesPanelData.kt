package com.hermes.mobile.car

import android.content.Context
import android.hardware.display.DisplayManager
import com.hermes.mobile.data.HermesClient
import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.ServerProfileStore
import com.hermes.mobile.data.SessionMessage
import com.hermes.mobile.data.CarConversation
import com.hermes.mobile.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A private presentation renders only Hermes content, without screen capture. */
internal object PanelContentLogic {
    // Mola's flags=10 means PRESENTATION (2) | OWN_CONTENT_ONLY (8), not PUBLIC (1).
    const val DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY

    fun session(sessions: List<HermesSession>, selectedId: String?): HermesSession? =
        if (selectedId != null) sessions.firstOrNull { it.id == selectedId }
        else sessions.filter { it.isActive && it.id.isNotBlank() }.maxByOrNull { it.startedAt ?: 0.0 }

    fun lastMessage(messages: List<SessionMessage>): String? = messages.lastOrNull {
        (it.isAssistant || it.isUser) && !it.content.isNullOrBlank()
    }?.content?.replace(Regex("\\s+"), " ")?.trim()?.take(400)

    fun lastAssistantMessage(messages: List<SessionMessage>): String? =
        lastMessage(messages.filter { it.isAssistant })

    fun savedSession(profileId: String, saved: String): String? =
        saved.takeIf { it.startsWith("$profileId|") }?.substringAfter('|')?.takeIf { it.isNotBlank() }
}

enum class PanelConnection { Loading, NoProfile, NoKey, Connected, Unavailable }

/** Read-only, profile-scoped source shared by the phone preview and car display. */
internal class HermesPanelDataSource(context: Context, private val selectedId: String? = null) {
    private val store = ServerProfileStore(context.applicationContext)
    private val settings = SettingsStore(context.applicationContext)
    private var selectedProfileId: String? = null

    suspend fun load(): PanelState = withContext(Dispatchers.IO) {
        val profile = try {
            store.active()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext PanelState(loaded = true, connection = PanelConnection.Unavailable)
        } ?: return@withContext PanelState(
            loaded = true, connection = PanelConnection.NoProfile,
        )
        val desiredId = selectedId ?: PanelContentLogic.savedSession(profile.id, settings.current().lastSession)
        if (selectedId != null) {
            if (selectedProfileId != null && selectedProfileId != profile.id) {
                return@withContext PanelState(loaded = true, selectedSession = true, connection = PanelConnection.Unavailable)
            }
            selectedProfileId = profile.id
        }
        val initial = PanelState(
            server = profile.name, url = profile.baseUrl, hasToken = profile.token.isNotBlank(),
            loaded = true, selectedSession = desiredId != null, profileId = profile.id,
        )
        if (profile.token.isBlank()) return@withContext initial.copy(connection = PanelConnection.NoKey)
        val client = HermesClient(profile)
        try {
            val session = PanelContentLogic.session(client.sessions(), desiredId)
            val messages = if (session != null) {
                try {
                    CarConversation.messagesFor(profile.id, session.id)?.map {
                        SessionMessage(role = if (it.isUser) "user" else "assistant", content = it.text)
                    }?.takeIf { it.isNotEmpty() } ?: client.sessionMessages(session.id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@withContext initial.copy(
                        connection = PanelConnection.Connected,
                        activeSession = session.serverTitle?.takeIf { it.isNotBlank() } ?: session.title,
                        activeSessionId = session.id,
                        messageUnavailable = true,
                    )
                }
            } else emptyList()
            initial.copy(
                connection = PanelConnection.Connected,
                activeSession = session?.let { it.serverTitle?.takeIf(String::isNotBlank) ?: it.title },
                activeSessionId = session?.id,
                lastMessage = PanelContentLogic.lastMessage(messages),
                lastAssistantMessage = PanelContentLogic.lastAssistantMessage(messages),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Do not retain another profile/session's last message or expose response bodies.
            initial.copy(connection = PanelConnection.Unavailable)
        }
    }
}
