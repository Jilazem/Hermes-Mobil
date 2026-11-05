package com.hermes.mobile.data

enum class ModelSwitchReply { Applied, Deferred, Rejected }

/** Exact EN/TR gateway confirmations; a checkmark in an unrelated reply is not acceptance. */
fun modelSwitchReply(output: String): ModelSwitchReply {
    val lines = output.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    val first = lines.firstOrNull() ?: return ModelSwitchReply.Rejected
    val confirmation = Regex(
        "^(?:[✓✅]\\s*)?Model\\s+(?:switched\\s*(?:to\\s+|:\\s*)[^\\r\\n]+|`[^`]+`\\s+olarak\\s+değiştirildi)$",
        RegexOption.IGNORE_CASE,
    )
    if (!confirmation.matches(first)) return ModelSwitchReply.Rejected
    return if (lines.any { it == "Applies from your next message." }) ModelSwitchReply.Deferred
    else ModelSwitchReply.Applied
}

fun modelSwitchAccepted(output: String): Boolean = modelSwitchReply(output) != ModelSwitchReply.Rejected

/** Same id on two providers is two different choices. Unknown provider never marks both as selected. */
fun isCurrentModelChoice(provider: String, model: String, currentProvider: String?, currentModel: String?): Boolean =
    provider == currentProvider && model == currentModel

fun modelSwitchSavedGlobally(output: String): Boolean = output.lineSequence().map(String::trim).any {
    it == "Saved to config.yaml (`--global`)" || it == "config.yaml'a kaydedildi (`--global`)"
}

/** Requested effort must appear in the gateway's acknowledgement, not its usage/error text. */
fun reasoningSwitchAccepted(output: String, requested: String): Boolean {
    val level = Regex.escape(requested)
    val acknowledgement = Regex(
        "^(?:🧠\\s*)?(?:✓\\s*)?(?:Reasoning effort set to `$level`(?:\\s|$)|Akıl yürütme gücü `$level` olarak ayarlandı(?:\\s|$)|Reasoning effort:\\s*$level$)",
        RegexOption.IGNORE_CASE,
    )
    return output.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() }
        ?.let { acknowledgement.containsMatchIn(it) } == true
}
