package com.hermes.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.hermes.mobile.car.HermesPanel
import com.hermes.mobile.car.PanelState
import com.hermes.mobile.car.HermesPanelDataSource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun HermesPanelPreviewScreen(onBack: () -> Unit, sessionId: String? = null, onOpenGuide: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var state by remember { mutableStateOf(PanelState()) }
    BackHandler(onBack = onBack)
    LaunchedEffect(context, lifecycle, sessionId) {
        state = PanelState(selectedSession = sessionId != null)
        val source = HermesPanelDataSource(context, sessionId)
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                state = source.load()
                delay(15_000)
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF091424))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(S.t2("Telefon önizlemesi", "Phone preview"), color = Color(0xFFA6BAD1), modifier = Modifier.weight(1f))
            onOpenGuide?.let { guide -> TextButton(onClick = guide) { Text(S.t2("Rehber", "Guide"), color = Color(0xFF7AE2DC)) } }
            TextButton(onClick = onBack) { Text(S.t2("Kapat", "Close"), color = Color(0xFF7AE2DC)) }
        }
        Box(Modifier.weight(1f)) { HermesPanel(state) }
    }
}
