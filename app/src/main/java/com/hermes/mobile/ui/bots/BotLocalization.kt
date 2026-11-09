package com.hermes.mobile.ui.bots

import com.hermes.mobile.data.SchedulePreset

object BotLocalization {
    fun toolDisplayName(tool: String): String = when (tool.lowercase().trim()) {
        "web_search" -> "Web Araması"
        "terminal", "bash", "shell" -> "Terminal / Komut"
        "read_file", "file_read" -> "Dosya Okuma"
        "write_file", "file_write" -> "Dosya Yazma"
        "edit_file" -> "Dosya Düzenleme"
        "phone" -> "Telefon Erişimi"
        "camera" -> "Kamera"
        "sms" -> "SMS / Mesaj"
        "contacts" -> "Rehber"
        "location" -> "Konum"
        "audio", "voice" -> "Ses / Konuşma"
        "shizuku" -> "Derin Sistem (Shizuku)"
        "browser" -> "Tarayıcı"
        "tasker" -> "Tasker Görevleri"
        "macrodroid" -> "MacroDroid"
        "notifications" -> "Bildirimler"
        "calendar" -> "Takvim"
        "clipboard" -> "Pano"
        else -> tool.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }
    }

    fun thinkModeDisplayName(mode: String): String = when (mode.lowercase().trim()) {
        "off" -> "Kapalı"
        "low" -> "Düşük"
        "medium" -> "Orta"
        "high" -> "Yüksek"
        else -> mode
    }

    fun roleDisplayName(role: String): String = when (role.lowercase().trim()) {
        "user" -> "Kullanıcı"
        "assistant" -> "Asistan"
        "system" -> "Sistem"
        else -> role
    }

    fun schedulePresetDisplayName(preset: SchedulePreset): String = when (preset) {
        SchedulePreset.HOURLY -> "Saatlik"
        SchedulePreset.DAILY -> "Günlük"
        SchedulePreset.WEEKDAYS -> "Hafta İçi"
        SchedulePreset.CUSTOM -> "Özel"
    }

    fun taskStateDisplayName(state: String): String = com.hermes.mobile.data.bots.BotWorkspaceLogic.taskLabel(state)
}
