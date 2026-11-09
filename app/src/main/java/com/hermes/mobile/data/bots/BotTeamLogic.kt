package com.hermes.mobile.data.bots

import com.hermes.mobile.data.FleetTaskData

object BotTeamLogic {
    /** Drop merges both entire teams; it never sends work or changes agent permissions. */
    fun stack(source:String,target:String,ids:Set<String>,groups:Map<String,String>):Map<String,String> {
        if (source==target || source !in ids || target !in ids) return groups
        val sourceTeam=groups[source]?.takeIf(String::isNotBlank)
        val targetTeam=groups[target]?.takeIf(String::isNotBlank)
        val name=targetTeam ?: sourceTeam ?: generateSequence(1) { it+1 }.map { "Minik ekip $it" }.first { it !in groups.values }
        val oldTeams=setOfNotNull(sourceTeam,targetTeam)
        val members=ids.filter { it==source || it==target || groups[it] in oldTeams }
        return groups + members.associateWith { name }
    }

    fun tasks(team:String,memberIds:List<String>,instruction:String,nextId:Long):List<FleetTaskData> {
        require(team.isNotBlank() && instruction.isNotBlank())
        val ids=memberIds.filter(String::isNotBlank).distinct()
        require(ids.size>=2 && nextId>0 && nextId<=Long.MAX_VALUE-ids.size)
        return ids.mapIndexed { index,id -> FleetTaskData(nextId+index,id,
            "Ekip: $team · ortak görev #$nextId\nKendi uzmanlığınla aşağıdaki kullanıcı isteğine katkı ver. Diğer üyelerin sonuçlarını gördüğünü varsayma.\n\n${instruction.trim()}") }
    }
}
