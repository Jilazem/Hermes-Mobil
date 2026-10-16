package com.hermes.mobile.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * K2 — Artemis görev kaydı (Canlı envanter + bitiş bildirimi için tek defter).
 *
 * `phone_task` akışı buraya yazar: gönderimde [submitted], durum tazelemesinde
 * [progressed], sonuçta [finished]. "still running" cevabı dönen görevler
 * arka planda izlenmeye devam eder; biten görev listede kalır (son
 * [MAX_TASKS] kayıt) ki "ne oldu?" sorusu cevapsız kalmasın.
 *
 * Saf: Android yok, yalnız durum — birim testte de çalışır.
 */
data class ArtemisTaskInfo(
    val id: String,
    val goal: String,
    /** Ham Artemis durumu: running / completed / failed / cancelled … */
    val status: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
) {
    val done: Boolean get() = finishedAt != null
}

object ArtemisTaskTracker {

    const val MAX_TASKS = 20

    private val _tasks = MutableStateFlow<List<ArtemisTaskInfo>>(emptyList())
    val tasks: StateFlow<List<ArtemisTaskInfo>> = _tasks.asStateFlow()

    @Synchronized
    fun submitted(id: String, goal: String, now: Long) {
        // Yinelenen gönderim: ilk kayıt korunur, defter şişmez.
        if (_tasks.value.any { it.id == id }) return
        val entry = ArtemisTaskInfo(
            id = id,
            goal = goal.take(120),
            status = "running",
            startedAt = now,
        )
        _tasks.value = (listOf(entry) + _tasks.value).take(MAX_TASKS)
    }

    /** Durum tazelemesi (hâlâ koşuyor) — bitiş zamanı DEĞİŞMEZ. */
    @Synchronized
    fun progressed(id: String, status: String) {
        _tasks.value = _tasks.value.map {
            if (it.id == id && !it.done) it.copy(status = status) else it
        }
    }

    /** Görev bitti — [status] ham Artemis sonucu (completed / failed …). */
    @Synchronized
    fun finished(id: String, status: String, now: Long) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) it.copy(status = status, finishedAt = now) else it
        }
    }

    fun running(): List<ArtemisTaskInfo> = _tasks.value.filter { !it.done }

    fun byId(id: String): ArtemisTaskInfo? = _tasks.value.firstOrNull { it.id == id }

    /** Test ve "defteri sıfırla" için. */
    @Synchronized
    fun reset() {
        _tasks.value = emptyList()
    }
}