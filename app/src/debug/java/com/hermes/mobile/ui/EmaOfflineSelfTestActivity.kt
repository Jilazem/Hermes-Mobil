package com.hermes.mobile.ui

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hermes.mobile.assistant.*
import com.hermes.mobile.data.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/** Debug only: actual immutable download + offline inference, playback, stop. */
class EmaOfflineSelfTestActivity : ComponentActivity() {
    private val lines=mutableListOf<String>()
    private var speaker:JarvisVoice?=null
    private lateinit var output:TextView
    private fun step(s:String) { lines+=s; output.text=lines.joinToString("\n"); File(filesDir,"ema-offline-proof/result.txt").apply { parentFile?.mkdirs();writeText(lines.joinToString("\n")) }; android.util.Log.i("EmaOfflineProof",s) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        output=TextView(this).apply { setPadding(32,64,32,32);textSize=16f };setContentView(output)
        lifecycleScope.launch {
            try {
                if(intent.getBooleanExtra("download",false)) {
                    // Cancel once with data on disk, then resume through the real downloader.
                    if(!EmaModelStore.present(this@EmaOfflineSelfTestActivity)) {
                        val partial=launch {
                            EmaModelStore.download(this@EmaOfflineSelfTestActivity) { if(it>300_000) throw CancellationException("test pause") }
                        }
                        partial.join()
                        check(EmaModelStore.bytesOnDisk(this@EmaOfflineSelfTestActivity)>0)
                        step("PASS interrupted download retained progress")
                    }
                    EmaModelStore.download(this@EmaOfflineSelfTestActivity) {}
                    check(withContext(Dispatchers.IO) { EmaModelStore.verified(EmaModelStore.directory(this@EmaOfflineSelfTestActivity)) })
                    step("PASS resumed public model download; all SHA-256 verified; bytes=${EmaModelStore.totalBytes}")
                    step("RESULT DOWNLOAD PASS");return@launch
                }
                SettingsStore(this@EmaOfflineSelfTestActivity).update { it.copy(emaMode="offline") }
                val client=EmaConfig.from(this@EmaOfflineSelfTestActivity)!!.client()
                check(client is EmaOfflineSpeech);check(client.health());step("PASS downloaded EMA loaded on Android CPU")
                if(intent.getBooleanExtra("speak_only",false)) {
                    val done=CompletableDeferred<Unit>()
                    val voice=JarvisVoice.create(this@EmaOfflineSelfTestActivity).also { speaker=it }
                    voice.onDone={ done.complete(Unit) }; voice.onError={ done.completeExceptionally(IllegalStateException(it)) }
                    voice.say("Merhaba Gökhan. Ben Hermes. EMA artık telefonunda çalışıyor. Bu sesi üretirken internet veya Mac kullanmıyorum. Sesimi dilediğin zaman uygulamadan tekrar dinleyebilirsin.")
                    voice.finish(); withTimeout(60_000) { done.await() }
                    step("PASS uninterrupted offline EMA speech completed"); return@launch
                }
                val proofs=File(filesDir,"ema-offline-proof").apply { mkdirs() }
                val hashes=mutableListOf<String>()
                listOf("Merhaba Gökhan. Ben Hermes. Yeni sesim EMA.","İnternet olmadan telefonunda konuşabiliyorum.").forEachIndexed { i,text ->
                    val started=android.os.SystemClock.elapsedRealtime()
                    val wav=client.speak(text)
                    val seconds=(wav.size-44)/96000.0
                    val pcm=ByteBuffer.wrap(wav,44,wav.size-44).order(ByteOrder.LITTLE_ENDIAN)
                    var sum=0.0;var peak=0
                    while(pcm.remaining()>=2) { val sample=pcm.short.toInt();sum+=sample.toDouble()*sample;peak=maxOf(peak,kotlin.math.abs(sample)) }
                    check(seconds>0.2 && peak>100 && sum>10000)
                    hashes+=MessageDigest.getInstance("SHA-256").digest(wav).joinToString("") { "%02x".format(it) }
                    File(proofs,"voice-${i+1}.wav").writeBytes(wav)
                    step("PASS local WAV ${i+1}; bytes=${wav.size}; audio_s=$seconds; elapsed_ms=${android.os.SystemClock.elapsedRealtime()-started}; peak=$peak")
                }
                check(hashes.distinct().size==2);step("PASS distinct non-silent audio for distinct texts")
                val done=CompletableDeferred<Unit>();val errors=CompletableDeferred<String>();val completions=AtomicInteger()
                val voice=JarvisVoice.create(this@EmaOfflineSelfTestActivity).also { speaker=it }
                check(voice is EmaVoice)
                voice.onDone={ completions.incrementAndGet();done.complete(Unit) };voice.onError={ errors.complete(it) }
                voice.say("Merhaba Gökhan. Ben Hermes.");voice.say("İnternetsiz sesim hazır.");voice.finish()
                withTimeout(90_000) { done.await() };check(completions.get()==1 && !errors.isCompleted)
                step("PASS production voice factory; queued local playback completed once")
                completions.set(0)
                val audible=CompletableDeferred<Unit>()
                voice.onLevel={ if(it>0) audible.complete(Unit) }
                voice.say("Bu uzun yanıt durdurulacak. ".repeat(20));voice.finish()
                withTimeout(30_000) { audible.await() };voice.stop();delay(800)
                check(completions.get()==0 && !errors.isCompleted);step("PASS local playback cancelled without fallback or completion")
                speaker?.release();speaker=null
                step("RESULT OFFLINE PASS")
            } catch(e:Exception) { step("RESULT FAIL ${e.javaClass.simpleName}: ${e.message}") }
        }
    }
    override fun onDestroy() { speaker?.release();super.onDestroy() }
}
