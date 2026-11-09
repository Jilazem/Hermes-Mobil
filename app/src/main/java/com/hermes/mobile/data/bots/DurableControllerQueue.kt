/*
 * Adapted from mylea1/phongent, core/run/ControllerQueue.kt,
 * commit 0b57e35d4e4c8460fdd87fcd33f751f764e7423d; based on Mika.
 * Copyright 2026 wangzan101. Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 * Hermes changes: readable names, terminal validation, bounded goals and result reconciliation.
 */
package com.hermes.mobile.data.bots

import kotlinx.serialization.Serializable

@Serializable
data class ControllerTask(
    val id: String,
    val text: String,
    val conversation: String = "",
    val state: String = "queued",
    val error: String = "",
    val result: String = "",
)

/** Save-before-dispatch outbox. Uncertain work blocks replay until a real receipt is read. */
class DurableControllerQueue(initial: List<ControllerTask> = emptyList(), private val save: (List<ControllerTask>) -> Unit) {
    private var tasks = initial.distinctBy { it.id }.map {
        if (it.state in setOf("dispatching", "running")) it.copy(state = "uncertain", error = "Önceki gönderimin sonucu doğrulanmalı.") else it
    }

    @Synchronized fun snapshot(): List<ControllerTask> = tasks.toList()

    @Synchronized fun enqueue(task: ControllerTask): ControllerTask {
        tasks.firstOrNull { it.id == task.id }?.let { return it }
        require(task.id.matches(Regex("[A-Za-z0-9-]{8,80}")) && task.text.isNotBlank() && task.text.length <= 8000)
        check(tasks.count { it.state in ACTIVE } < 100) { "Görev kuyruğu dolu." }
        val next = tasks + task.copy(state = "queued")
        val retained = next.filter { it.state in TERMINAL }.takeLast(400).map { it.id }.toSet()
        change(next.filter { it.state !in TERMINAL || it.id in retained })
        return tasks.first { it.id == task.id }
    }

    @Synchronized fun claim(): ControllerTask? {
        if (tasks.any { it.state in setOf("dispatching", "running", "uncertain") }) return null
        val task = tasks.firstOrNull { it.state == "queued" } ?: return null
        update(task.id, "dispatching")
        return tasks.first { it.id == task.id }
    }

    @Synchronized fun update(id: String, state: String, error: String = "", result: String? = null) {
        require(state in ACTIVE || state in TERMINAL)
        check(tasks.any { it.id == id }) { "Görev kaydı bulunamadı." }
        change(tasks.map { if (it.id == id) it.copy(state = state, error = error, result = result ?: it.result) else it })
    }

    @Synchronized fun bindConversation(id: String, conversation: String) {
        require(conversation.isNotBlank())
        val task = tasks.firstOrNull { it.id == id } ?: error("Görev bulunamadı.")
        check(task.conversation.isEmpty() || task.conversation == conversation) { "Görev başka bir sohbete taşınamaz." }
        change(tasks.map { if (it.id == id) it.copy(conversation = conversation) else it })
    }

    @Synchronized fun cancel(id: String) {
        check(tasks.firstOrNull { it.id == id }?.state == "queued") { "Yalnız gönderilmemiş iş iptal edilebilir." }
        update(id, "cancelled")
    }

    private fun change(next: List<ControllerTask>) { save(next); tasks = next }

    companion object {
        val ACTIVE = setOf("queued", "dispatching", "running", "uncertain")
        val TERMINAL = setOf("completed", "failed", "cancelled")
    }
}
