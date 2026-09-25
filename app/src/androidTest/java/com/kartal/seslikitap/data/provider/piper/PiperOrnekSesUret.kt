package com.kartal.seslikitap.data.provider.piper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Karşılaştırma için örnek ses üretir (sınama değil, araç): Ata'nın sesiyle bir paragrafı telefonda, uygulamanın
 * kendi koduyla seslendirip `files/ornek_telefon.wav`'a yazar. Aynı paragraf Sözcük'te (bilgisayarda) de üretilip
 * iki dosya aynı hoparlörden dinlenir.
 *
 * Çalıştırma: `adb shell am instrument -w -e class com.kartal.seslikitap.data.provider.piper.PiperOrnekSesUret
 * com.kartal.seslikitap.test/androidx.test.runner.AndroidJUnitRunner`, sonra
 * `adb exec-out run-as com.kartal.seslikitap cat files/ornek_telefon.wav > ornek_telefon.wav`.
 */
@RunWith(AndroidJUnit4::class)
class PiperOrnekSesUret {

    @Test
    fun paragrafiSeslendir() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val voice = PiperVoice(context)
        val rate = voice.sampleRate()
        val pause = ShortArray((rate * 0.18f).toInt())   // cümleler arası sessizlik (uygulama ve Sözcük ile aynı)
        val pieces = TurkishPhonemizer.sentences(PARAGRAF).map { PARAGRAF.substring(it) }.filter { it.isNotBlank() }
            .flatMap { listOf(voice.synthesize(it), pause) }
        writeWav(File(context.filesDir, "ornek_telefon.wav"), pieces, rate)
    }

    private fun writeWav(file: File, pieces: List<ShortArray>, rate: Int) {
        val samples = pieces.sumOf { it.size }
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(samples * 2)
            out.write(header.array())
            pieces.forEach { pcm ->
                val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                pcm.forEach { bytes.putShort(it) }
                out.write(bytes.array())
            }
        }
    }

    companion object {
        /** Sözcük tarafındaki karşılaştırma betiği de bu metni kullanır. */
        const val PARAGRAF =
            "Sabahın ilk ışıkları köyün üzerine yavaş yavaş yayılırken, yaşlı çoban sürüsünü toplayıp yamaçtaki " +
                "otlağa doğru yola çıktı. Yol boyunca kuşların cıvıltısı, derenin şırıltısına karışıyordu. " +
                "Çocukluğunda dedesiyle birlikte yürüdüğü bu patikayı, her adımında yeniden hatırlıyordu. " +
                "Tepeye vardığında durdu, derin bir nefes aldı ve uzaklarda sislerin arasında kaybolan dağlara " +
                "uzun uzun baktı. Hayat, diye düşündü, tıpkı bu yol gibiydi: bazen dik, bazen düz, ama her zaman " +
                "insanı bir yere götüren."
    }
}
