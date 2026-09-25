package com.kartal.seslikitap.data.provider.piper

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Altın testin telefonda çalışan eşi. Bilgisayardaki birim testi Java'nın düzenli ifade motorunu kullanır;
 * Android ise ICU kullanır ve telefonun dili Türkçedir. "Ata" sesi yalnız Python'un ürettiği ses birimleriyle
 * doğru okur, bu yüzden telefondaki çıktı da harfi harfine aynı olmalı. Farklar logcat'e (`PiperAltin`) yazılır.
 */
@RunWith(AndroidJUnit4::class)
class TurkishPhonemizerDeviceGoldenTest {

    private val lines: List<JsonObject> by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open("piper/altin.jsonl")
            .bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
            .map { Json.parseToJsonElement(it).jsonObject }
    }

    @Test
    fun sesBirimleriPythonIleBirebirAyni() {
        val cases = lines.filter { "t" in it }
        assertTrue("altın dosyada metin yok", cases.size > 1000)
        val failures = cases.mapNotNull { case ->
            val text = case.getValue("t").jsonPrimitive.content
            val expected = case.getValue("p").jsonArray.map { it.jsonPrimitive.content }
            val actual = runCatching { TurkishPhonemizer.phonemize(text) }.getOrElse { listOf("HATA: $it") }
            if (actual == expected) null else "«$text»\n  python: ${expected.joinToString("")}\n  kotlin: ${actual.joinToString("")}"
        }
        failures.take(60).forEach { Log.e(TAG, it) }
        Log.i(TAG, "ses birimleri: ${failures.size}/${cases.size} farklı")
        assertEquals(
            "${failures.size}/${cases.size} metin farklı:\n" + failures.take(40).joinToString("\n"),
            0,
            failures.size,
        )
    }

    @Test
    fun cumleBolmePythonIleAyni() {
        val cases = lines.filter { "s" in it }
        val failures = cases.mapNotNull { case ->
            val text = case.getValue("s").jsonPrimitive.content
            val expected = case.getValue("c").jsonArray.map { it.jsonPrimitive.content }
            val actual = runCatching { TurkishPhonemizer.sentences(text).map { text.substring(it) } }
                .getOrElse { listOf("HATA: $it") }
            if (actual == expected) null else "«${text.take(80)}»\n  python: $expected\n  kotlin: $actual"
        }
        failures.take(20).forEach { Log.e(TAG, it) }
        Log.i(TAG, "cümle bölme: ${failures.size}/${cases.size} farklı")
        assertEquals(
            "${failures.size}/${cases.size} bölme farklı:\n" + failures.take(10).joinToString("\n"),
            0,
            failures.size,
        )
    }

    private companion object {
        const val TAG = "PiperAltin"
    }
}
