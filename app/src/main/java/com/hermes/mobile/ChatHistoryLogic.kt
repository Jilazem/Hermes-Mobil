package com.hermes.mobile

import com.hermes.mobile.data.SessionMessage
import com.hermes.mobile.ui.visibleUserMessage

/** Sunucunun verdiği geçmişin tamamı; LazyColumn yalnız görünür satırları çizer. */
internal fun restoreChatHistory(messages: List<SessionMessage>, nextKey: (String) -> String): List<ChatItem> =
    messages.flatMap { m ->
        buildList {
            when {
                m.isUser && !m.content.isNullOrBlank() && visibleUserMessage(m.content) ->
                    add(ChatItem.User(nextKey("u"), com.hermes.mobile.data.VoiceSpecialistLogic.displayPrompt(m.content), m.timestamp))
                m.isAssistant -> {
                    m.reasoning?.takeIf { it.isNotBlank() }?.let { add(ChatItem.Thinking(nextKey("r"), it)) }
                    m.content?.takeIf { it.isNotBlank() }?.let { add(ChatItem.Assistant(nextKey("a"), it, ts = m.timestamp)) }
                    m.toolCalls.forEach { call ->
                        add(ChatItem.Tool(nextKey("t"), call.function.name ?: "araç", ToolState.Done, call.function.arguments))
                    }
                }
                m.isTool -> add(ChatItem.Tool(nextKey("t"), m.toolName ?: "araç", ToolState.Done, m.content))
            }
        }
    }
