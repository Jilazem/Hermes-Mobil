package com.hermes.mobile.data

import android.content.Context
import ai.onnxruntime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.*
import java.util.Random
import kotlin.math.roundToInt

/** EMA's actual text / four-step acoustic / waveform decoder, entirely on CPU.
 * One session set is reused; inference is serial and bounded to short pieces.
 * No network client, server token or Android TTS exists in this engine.
 */
object EmaOfflineEngine {
    private val mutex = Mutex()
    private val env by lazy { OrtEnvironment.getEnvironment() }
    private var root: String? = null
    private var sessions: List<OrtSession> = emptyList()
    private var vocab = emptyList<String>()
    private var hidden = 0
    private var latent = 64
    private var steps = 4

    private fun load(context: Context) {
        val dir = EmaModelStore.directory(context)
        if (root == dir.absolutePath && sessions.isNotEmpty()) return
        check(EmaModelStore.verified(dir)) { "EMA modeli indirilmemiş veya bozuk. Ayarlar → Ses → EMA modelini indir." }
        val cfg = Json.parseToJsonElement(File(dir,"config.json").readText()).jsonObject
        vocab = cfg.getValue("vocab").jsonArray.map { it.jsonPrimitive.content }
        hidden = cfg.getValue("hidden_dim").jsonPrimitive.int
        latent = cfg.getValue("latent_dim").jsonPrimitive.int
        steps = cfg.getValue("times").jsonArray.size
        val opened = mutableListOf<OrtSession>()
        try {
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1)
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                for (name in listOf("text","sound","decoder")) opened += env.createSession(File(dir,"$name.onnx").absolutePath,options)
            }
            sessions.forEach { it.close() }; sessions = opened; root = dir.absolutePath
        } catch (e: Exception) { opened.forEach { it.close() }; throw e }
    }

    suspend fun ready(context: Context): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { load(context); true }
    }

    suspend fun delete(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            sessions.forEach { it.close() }; sessions = emptyList(); root = null
            EmaModelStore.delete(context)
        }
    }

    suspend fun stream(context: Context, text: String, onPcm: (ByteArray,Int)->Unit) = withContext(Dispatchers.Default) {
        mutex.withLock {
            ensureActive(); load(context)
            val job = currentCoroutineContext()[Job]!!
            val normalized = EmaText.normalize(text.take(3000),vocab.toSet())
            val pieces = EmaText.pieces(normalized)
            check(pieces.isNotEmpty()) { "Okunacak metin boş" }
            for (piece in pieces) {
                job.ensureActive()
                generate(piece,job,onPcm)
                onPcm(ByteArray(4800),48000) // 50 ms boundary; no remote synthesis.
            }
        }
    }

    private fun tensor(values: FloatArray, vararg shape: Long) = OnnxTensor.createTensor(env,FloatBuffer.wrap(values),shape)
    private fun tensor(values: LongArray, vararg shape: Long) = OnnxTensor.createTensor(env,LongBuffer.wrap(values),shape)
    private fun mask(n: Int) = OnnxTensor.createTensor(env, ByteBuffer.wrap(ByteArray(n){1}),longArrayOf(1,n.toLong()),OnnxJavaType.BOOL)
    private fun floats(value: OnnxValue): FloatArray = (value as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }

    @OptIn(InternalCoroutinesApi::class)
    private fun run(session: OrtSession, input: Map<String,OnnxTensor>, job: Job): OrtSession.Result {
        OrtSession.RunOptions().use { options ->
            val cancel = job.invokeOnCompletion(onCancelling=true, invokeImmediately=true) { if (job.isCancelled) options.setTerminate(true) }
            try { job.ensureActive(); return session.run(input,options) }
            finally { cancel.dispose() }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun generate(text: String, job: Job, onPcm: (ByteArray,Int)->Unit) {
        val length = text.length
        val ids = LongArray(length) { vocab.indexOf(text[it].toString()).coerceAtLeast(1).toLong() }
        val starts = text.indices.filter { text[it]!=' ' && (it==0 || text[it-1]==' ') }
        val word = LongArray(length); val wstart = LongArray(length)
        starts.forEachIndexed { w, start ->
            val end = starts.getOrNull(w+1) ?: length
            for (i in (if(w==0) 0 else start) until end) { word[i]=w.toLong(); wstart[i]=(if(w==0) 0 else start).toLong() }
        }
        val inputs = mapOf("ids" to tensor(ids,1,length.toLong()), "mask" to mask(length))
        val (h,dur) = try { run(sessions[0],inputs,job).use { floats(it[0]) to floats(it[1]) } }
            finally { inputs.values.forEach { it.close() } }
        val totals = FloatArray(starts.size)
        dur.forEachIndexed { i,d -> totals[word[i].toInt()]+=d }
        // Torch round uses ties-to-even; match its word timeline rather than roundToInt.
        val counts = IntArray(totals.size) { Math.rint(totals[it].toDouble()).toInt().coerceIn(1,250) }
        val frames = counts.sum()
        check(frames in 1..750) { "EMA ses parçası çok uzun" }
        val fw = LongArray(frames); val fp = FloatArray(frames)
        var offset = 0
        counts.forEachIndexed { w,count ->
            repeat(count) { f -> if(offset+f<frames) { fw[offset+f]=w.toLong(); fp[offset+f]=f.toFloat()/count } }
            offset+=count
        }
        val random = Random(0)
        val noise = FloatArray(steps*frames*latent) { random.nextGaussian().toFloat() }
        val sound = mapOf("h" to tensor(h,1,length.toLong(),hidden.toLong()),"dur" to tensor(dur,1,length.toLong()),
            "mask" to mask(length),"cw" to tensor(word,1,length.toLong()),"wstart" to tensor(wstart,1,length.toLong()),
            "fw" to tensor(fw,1,frames.toLong()),"fp" to tensor(fp,1,frames.toLong()),"fmask" to mask(frames),
            "noise" to tensor(noise,1,steps.toLong(),frames.toLong(),latent.toLong()))
        val z = try { run(sessions[1],sound,job).use { floats(it[0]) } } finally { sound.values.forEach { it.close() } }
        var start = 0
        while(start<frames) {
            job.ensureActive()
            val end = minOf(frames,start+if(start==0)25 else 100)
            val a = maxOf(0,start-8); val b = minOf(frames,end+8)
            tensor(z.copyOfRange(a*latent,b*latent),1,(b-a).toLong(),latent.toLong()).use { t ->
                run(sessions[2],mapOf("z" to t),job).use { result ->
                    job.ensureActive()
                    val audio = floats(result[0])
                    val pcm = ByteBuffer.allocate((end-start)*1920*2).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in (start-a)*1920 until (end-a)*1920) {
                        check(audio[i].isFinite()) { "EMA geçersiz ses üretti" }
                        pcm.putShort((audio[i].coerceIn(-1f,1f)*32767).roundToInt().toShort())
                    }
                    onPcm(pcm.array(),48000)
                }
            }
            start=end
        }
    }
}

class EmaOfflineSpeech(private val context: Context): EmaSpeech {
    override suspend fun health() = EmaOfflineEngine.ready(context)
    override suspend fun stream(text: String,onPcm:(ByteArray,Int)->Unit) = EmaOfflineEngine.stream(context,text,onPcm)
    override suspend fun speak(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        stream(text) { pcm,_ -> out.write(pcm) }
        val data=out.toByteArray()
        val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(data.size+36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(48000).putInt(96000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(data.size)
        return header.array()+data
    }
}
