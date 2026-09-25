package com.kartal.seslikitap.data.provider.piper

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.kartal.seslikitap.domain.model.NarratorGender
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Uygulamayla gelen Piper (VITS) ses modeli: `assets/sesler/<ad>.onnx` + `<ad>.onnx.json`.
 *
 * Sözcük masaüstü uygulamasındaki `read_aloud.Voice`'un karşılığıdır: metin [TurkishPhonemizer] ile ses
 * birimlerine, ses birimleri modelin kimlik tablosuna çevrilir, onnxruntime sesi üretir.
 *
 * Model (~63 MB) ilk kullanımda assets'ten uygulamanın özel klasörüne bir kez kopyalanır: onnxruntime dosya
 * yolundan açınca belleğe tümüyle kopyalamak gerekmez.
 */
@Singleton
class PiperVoice @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val id: String = VOICE_ID

    private val mutex = Mutex()
    private var loaded: Loaded? = null

    /** Ses modeli uygulamada var mı (modelsiz derlemede sağlayıcı listelenmez). */
    fun isBundled(): Boolean = runCatching { context.assets.list(ASSET_DIR)?.contains("$VOICE_ID.onnx") == true }
        .getOrDefault(false)

    val displayName: String get() = "Ata (erkek)"
    val gender: NarratorGender get() = NarratorGender.MALE

    suspend fun sampleRate(): Int = load().sampleRate

    /**
     * Metni sese çevirir: 16 bit mono örnekler. [speed] 1.0 = sesin varsayılan hızı (json'daki `length_scale`,
     * "Ata" için %25 yavaş); 1.2 daha hızlı, 0.8 daha yavaş.
     */
    suspend fun synthesize(text: String, speed: Float = 1.0f): ShortArray {
        val model = load()
        val phonemes = TurkishPhonemizer.phonemize(text)
        if (phonemes.isEmpty()) return ShortArray(0)
        val ids = TurkishPhonemizer.phonemeIds(phonemes, model.idMap)
        val env = OrtEnvironment.getEnvironment()
        val scales = floatArrayOf(model.noiseScale, model.lengthScale / speed, model.noiseW)
        val inputs = mutableMapOf<String, OnnxTensor>()
        try {
            inputs["input"] = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong()))
            inputs["input_lengths"] = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(ids.size.toLong())), longArrayOf(1))
            inputs["scales"] = OnnxTensor.createTensor(env, FloatBuffer.wrap(scales), longArrayOf(3))
            if (model.session.inputNames.size > 3) {
                inputs["sid"] = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(0)), longArrayOf(1))
            }
            model.session.run(inputs).use { result ->
                val output = (result[0] as OnnxTensor).floatBuffer
                val audio = FloatArray(output.remaining()).also { output.get(it) }
                return toPcm16(audio)
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private fun toPcm16(audio: FloatArray): ShortArray {
        var peak = 0f
        for (sample in audio) peak = maxOf(peak, abs(sample))
        val gain = if (peak > 1f) 1f / peak else 1f
        return ShortArray(audio.size) { (audio[it] * gain).coerceIn(-1f, 1f).times(32767f).toInt().toShort() }
    }

    private suspend fun load(): Loaded = mutex.withLock {
        loaded ?: run {
            val config = Json.parseToJsonElement(
                context.assets.open("$ASSET_DIR/$VOICE_ID.onnx.json").bufferedReader(Charsets.UTF_8).use { it.readText() },
            ).jsonObject
            val inference = config["inference"]?.jsonObject
            val model = modelFile()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
            }
            Loaded(
                session = OrtEnvironment.getEnvironment().createSession(model.absolutePath, options),
                sampleRate = config.getValue("audio").jsonObject.getValue("sample_rate").jsonPrimitive.int,
                idMap = config.getValue("phoneme_id_map").jsonObject.mapValues { (_, ids) ->
                    ids.jsonArray.map { it.jsonPrimitive.long }
                },
                noiseScale = inference.float("noise_scale", 0.667f),
                lengthScale = inference.float("length_scale", 1.0f),
                noiseW = inference.float("noise_w", 0.8f),
            ).also { loaded = it }
        }
    }

    /** Modeli (sürüm değiştiyse yeniden) uygulamanın özel klasörüne kopyalar. */
    private fun modelFile(): File {
        val assetPath = "$ASSET_DIR/$VOICE_ID.onnx"
        val size = context.assets.openFd(assetPath).use { it.length }
        val target = File(File(context.noBackupFilesDir, ASSET_DIR).apply { mkdirs() }, "$VOICE_ID.onnx")
        if (!target.exists() || target.length() != size) {
            val temp = File(target.parentFile, "${target.name}.tmp")
            context.assets.open(assetPath).use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        }
        return target
    }

    private fun JsonObject?.float(key: String, default: Float): Float =
        this?.get(key)?.jsonPrimitive?.float ?: default

    private class Loaded(
        val session: OrtSession,
        val sampleRate: Int,
        val idMap: Map<String, List<Long>>,
        val noiseScale: Float,
        val lengthScale: Float,
        val noiseW: Float,
    )

    companion object {
        const val VOICE_ID = "ata"
        const val ASSET_DIR = "sesler"
    }
}
