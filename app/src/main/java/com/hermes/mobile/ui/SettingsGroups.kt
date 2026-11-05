package com.hermes.mobile.ui

/** Each destination belongs to one visible group; privacy is never hidden behind Developer. */
internal enum class SettingsGroup { Everyday, Assistant, Connections, Privacy, Advanced }
internal object SettingsGroups {
    val destinations = linkedMapOf(
        SettingsGroup.Everyday to listOf(SettingsCategory.General, SettingsCategory.Appearance, SettingsCategory.Chat),
        SettingsGroup.Assistant to listOf(SettingsCategory.Voice, SettingsCategory.Live, SettingsCategory.Assistant, SettingsCategory.Botlarim),
        SettingsGroup.Connections to listOf(SettingsCategory.Server, SettingsCategory.Phone),
        SettingsGroup.Privacy to listOf(SettingsCategory.Privacy, SettingsCategory.OnayMerkezi, SettingsCategory.Yedekleme),
        SettingsGroup.Advanced to listOf(SettingsCategory.Developer),
    )
}
