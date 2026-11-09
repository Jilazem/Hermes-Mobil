package com.hermes.mobile.data.bots

import java.util.Locale

data class AgentSummary(val id: String, val name: String, val description: String, val model: String, val group: String)
data class TaskRecipe(val id: String, val title: String, val subtitle: String, val draft: String)

val TASK_RECIPES = listOf(
    TaskRecipe("research", "Araştır", "Kaynaklarıyla incele", "Şu konuyu güncel ve güvenilir kaynaklarla araştır: \n\nKaynak bağlantılarını, bulguları ve doğrulanamayan noktaları ayrı yaz."),
    TaskRecipe("compare", "Karşılaştır", "Seçenekleri aynı ölçütle tart", "Şu seçenekleri karşılaştır: \n\nÖlçütlerim: \nKarşılaştırma tablosu ve gerekçeli bir öneri hazırla; eksik verileri belirt."),
    TaskRecipe("brief", "Özet çıkar", "Karar vermeyi kolaylaştır", "Aşağıdaki metni incele: \n\nKısa bir özet, önemli tarihler ve yapılacak işleri çıkar. Metinde bulunmayan bilgi ekleme."),
    TaskRecipe("plan", "Planla", "Bir sonraki adımı netleştir", "Şu hedef için uygulanabilir bir plan hazırla: \n\nAdımları sırala, eksik bilgi ve bağımlılıkları belirt, ilk yapılacak işi açıkça yaz."),
)

object BotWorkspaceLogic {
    fun filter(agents: List<AgentSummary>, query: String, group: String?): List<AgentSummary> {
        val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotBlank)
        return agents.filter { agent ->
            (group == null || agent.group == group) && terms.all { term ->
                listOf(agent.name, agent.description, agent.model, agent.group).any { it.lowercase(Locale.ROOT).contains(term) }
            }
        }
    }

    fun latestSessionId(sessions: List<Pair<String, Double>>): String? = sessions.filter { it.first.isNotBlank() }.maxByOrNull { it.second }?.first

    fun taskLabel(status: String): String = when (status) {
        "queued", "ready" -> "Kuyrukta"
        "starting", "dispatching", "accepted" -> "İletildi"
        "running", "working" -> "Çalışıyor"
        "done", "completed" -> "Tamamlandı"
        "failed" -> "Başarısız"
        "cancelled", "stopped" -> "İptal edildi"
        "paused" -> "Duraklatıldı"
        "waiting", "waiting_for_answer" -> "Yanıt bekliyor"
        "takeover", "waiting_on_phone", "confirmation" -> "Telefonda işlem gerekiyor"
        "idle" -> "Yeni görev bekliyor"
        else -> "Doğrulanmalı"
    }
}
