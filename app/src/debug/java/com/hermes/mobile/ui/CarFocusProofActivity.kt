package com.hermes.mobile.ui

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hermes.mobile.data.DrivingModeService
import kotlinx.coroutines.*
import java.io.File

/** Move the phone to Home during the delay to verify focus while the app is not top. */
class CarFocusProofActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val foreground=intent.getBooleanExtra("foreground",false)
        val view=TextView(this).apply { textSize=18f; setPadding(32,64,32,32) }
        setContentView(view)
        val file=File(filesDir,if(foreground) "car-focus-foreground.txt" else "car-focus-background.txt")
        fun step(text:String) { file.appendText(text+"\n");view.text=file.readText() }
        file.writeText("")
        lifecycleScope.launch {
            val manager=getSystemService(AudioManager::class.java)
            val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener {}.build()
            try {
                if(foreground) {
                    withTimeout(5_000) { DrivingModeService.startAndAwait(this@CarFocusProofActivity,true) }
                    step("FOREGROUND_READY")
                }
                step("MOVE_TO_HOME")
                delay(8_000)
                val result=manager.requestAudioFocus(request)
                step("FOCUS_RESULT=$result")
            } catch(e:Exception) { step("FAIL ${e.javaClass.simpleName}") }
            finally {
                manager.abandonAudioFocusRequest(request)
                if(foreground) DrivingModeService.stop(this@CarFocusProofActivity)
                step("CLEANUP_DONE")
            }
        }
    }
}
