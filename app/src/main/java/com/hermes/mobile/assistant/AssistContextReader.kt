package com.hermes.mobile.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View

internal object AssistContextReader {
    /** Iterative and bounded: malformed/deep accessibility trees cannot exhaust the stack. */
    fun read(structure: AssistStructure?, content: AssistContent?): String {
        val text = StringBuilder()
        val nodes = java.util.ArrayDeque<AssistStructure.ViewNode>()
        if (structure != null) for (index in 0 until structure.windowNodeCount.coerceAtMost(20))
            nodes.addLast(structure.getWindowNodeAt(index).rootViewNode)
        var visited = 0
        while (nodes.isNotEmpty() && visited++ < 2000 && text.length < JarvisLogic.MAX_SCREEN_CHARS) {
            val node = nodes.removeFirst()
            if (node.isAssistBlocked || node.visibility != View.VISIBLE) continue
            if (!sensitiveInput(node.inputType)) {
                val value = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
                if (value.isNotBlank()) text.append(value.take(JarvisLogic.MAX_SCREEN_CHARS - text.length)).append('\n')
            }
            for (index in 0 until node.childCount.coerceAtMost(2000 - nodes.size)) nodes.addLast(node.getChildAt(index))
        }
        val uri = content?.webUri?.toString()?.let(::publicPageUrl)
        return listOfNotNull(uri?.let { "Sayfa: $it" }, text.toString().trim().takeIf(String::isNotBlank))
            .joinToString("\n").take(JarvisLogic.MAX_SCREEN_CHARS)
    }
    fun sensitiveInput(type: Int): Boolean {
        val variation = type and InputType.TYPE_MASK_VARIATION
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            InputType.TYPE_CLASS_TEXT -> variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
            else -> false
        }
    }
    /** Keep page identity, excluding query/fragment credentials and embedded user information. */
    fun publicPageUrl(raw: String): String? = runCatching {
        val uri = java.net.URI(raw)
        if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        java.net.URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toASCIIString().take(1000)
    }.getOrNull()
}
