package com.hermes.mobile.data

enum class BotMode { ASK, AUTO }
enum class DispatchOutcome { SENT, QUEUED_OFFLINE, FAILED }

data class RemoteBotState(val status: String, val percent: Int?, val steps: List<String>)
sealed interface BotProgress {
    data object Unknown : BotProgress
    data class Known(val status: String, val percent: Int?, val steps: List<String>) : BotProgress
}
data class FleetTaskData(
    val id: Long,
    val target: String,
    val text: String,
    val state: String = "queued",
    val steps: List<String> = emptyList(),
    val error: String? = null,
    val sessionId: String? = null,
    val answer: String? = null,
)
data class BotQueueState(val paused: Boolean = false, val tasks: List<FleetTaskData> = emptyList())
data class GroupDeletion(val selectedGroup: String, val assignments: Map<String, String>)
enum class SchedulePreset { HOURLY, DAILY, WEEKDAYS, CUSTOM }
data class BotSchedule(val enabled: Boolean = false, val preset: SchedulePreset = SchedulePreset.DAILY, val time: String = "09:00")

object BotFleetLogic {
    const val ALL = "__all__"

    fun visibleBotIds(ids: List<String>, group: String, assignments: Map<String, String>): List<String> =
        if (group == ALL) ids else ids.filter { assignments[it] == group }

    fun modeFor(id: String, modes: Map<String, String>): BotMode =
        if (modes[id] == "auto") BotMode.AUTO else BotMode.ASK

    fun deleteGroup(selectedGroup: String, deletedGroup: String, assignments: Map<String, String>) = GroupDeletion(
        selectedGroup = if (selectedGroup == deletedGroup) ALL else selectedGroup,
        assignments = assignments.filterValues { it != deletedGroup },
    )

    fun requiresApproval(mode: BotMode): Boolean = mode == BotMode.ASK

    fun addLabel(labels: List<String>, raw: String): List<String> {
        val label = raw.trim()
        return if (label.isBlank() || label in labels) labels else labels + label
    }

    fun removeLabel(labels: List<String>, label: String): List<String> = labels.filterNot { it == label }

    fun validSchedule(schedule: BotSchedule): Boolean =
        !schedule.enabled || Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(schedule.time)

    fun progress(remote: RemoteBotState?): BotProgress = remote?.let {
        BotProgress.Known(it.status.ifBlank { "unknown" }, it.percent?.coerceIn(0, 100), it.steps)
    } ?: BotProgress.Unknown

    fun unreadCount(messageTimes: List<Long>, lastRead: Long): Int = messageTimes.count { it > lastRead }

    // Pausing only controls unsent work. The custom bot API has no remote
    // pause/cancel endpoint, so an accepted request must remain observable.
    fun pause(state: BotQueueState) = state.copy(paused = true)
    fun resume(state: BotQueueState) = state.copy(
        paused = false,
        tasks = state.tasks.map { if (it.state == "queued") it.copy(state = "ready") else it },
    )
    fun enqueue(state: BotQueueState, task: FleetTaskData): BotQueueState {
        if (state.tasks.any { it.id == task.id }) return state
        return state.copy(tasks = state.tasks + task.copy(state = if (state.paused) "queued" else "ready"))
    }

    /** Preserve per-bot conversation order; different bots may run in parallel. */
    fun readyToDispatch(state: BotQueueState): List<FleetTaskData> {
        if (state.paused) return emptyList()
        val busyBots = state.tasks.filter { it.state in setOf("running", "unknown") }.map { it.target }.toSet()
        return state.tasks.filter { it.state == "ready" && it.target !in busyBots }.distinctBy { it.target }
    }

    fun start(state: BotQueueState, id: Long, sessionId: String?) = state.copy(tasks = state.tasks.map {
        if (it.id == id && it.state == "ready") it.copy(state = "running", error = null, sessionId = sessionId) else it
    })

    fun complete(state: BotQueueState, id: Long, sessionId: String, steps: List<String>, answer:String?=null) = state.copy(tasks = state.tasks.map {
        if (it.id == id && it.state == "running") it.copy(state = "done", sessionId = sessionId, steps = steps, error = null, answer=answer?.take(4000)) else it
    })

    fun interrupted(state: BotQueueState, id: Long, reason: String) = state.copy(tasks = state.tasks.map {
        if (it.id == id && it.state == "running") it.copy(state = "unknown", error = reason) else it
    })

    /** Never replay an in-flight POST after navigation, process death or reconnect. */
    fun recover(tasks: List<FleetTaskData>): List<FleetTaskData> = tasks.distinctBy { it.id }.map {
        if (it.state == "running") it.copy(state = "unknown", error = "Yanıt izlenemedi; sunucuda tamamlanmış olabilir. Sohbeti yenileyin.") else it
    }

    fun stop(state: BotQueueState, id: Long) = state.copy(tasks = state.tasks.map {
        if (it.id == id && it.state in setOf("queued", "ready")) it.copy(state = "stopped") else it
    })
    fun edit(state: BotQueueState, id: Long, text: String) = state.copy(tasks = state.tasks.map {
        if (it.id == id && it.state in setOf("queued", "ready") && text.isNotBlank()) it.copy(text = text.trim()) else it
    })
    fun delete(state: BotQueueState, id: Long) = state.copy(tasks = state.tasks.filterNot {
        it.id == id && it.state in setOf("queued", "ready", "stopped", "done", "failed", "unknown")
    })

    fun dispatchOutcome(apiAvailable: Boolean, error: Throwable?): DispatchOutcome = when {
        !apiAvailable -> DispatchOutcome.QUEUED_OFFLINE
        error != null -> DispatchOutcome.FAILED
        else -> DispatchOutcome.SENT
    }
}
