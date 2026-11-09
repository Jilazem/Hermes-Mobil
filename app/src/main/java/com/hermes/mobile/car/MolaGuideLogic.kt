package com.hermes.mobile.car

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local observations from the actual car Surface/Presentation callbacks. */
enum class MolaHostState { NotObserved, SurfaceAvailable, Rendering, Released, Failed }

object MolaHostObservation {
    private val current = MutableStateFlow(MolaHostState.NotObserved)
    val state = current.asStateFlow()
    internal fun update(value: MolaHostState) { current.value = value }
}

object MolaGuideLogic {
    /** A successful phone preview/server test does not imply a car host drew the panel. */
    fun hasCarSurfaceEvidence(host: MolaHostState): Boolean = host == MolaHostState.Rendering

    fun nextStep(connection: PanelConnection, sessionAvailable: Boolean, host: MolaHostState): Int = when {
        connection != PanelConnection.Connected || !sessionAvailable -> 1
        !hasCarSurfaceEvidence(host) -> 2
        else -> 3
    }
}
