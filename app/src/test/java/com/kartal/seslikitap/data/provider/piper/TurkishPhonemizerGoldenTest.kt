package com.kartal.seslikitap.data.provider.piper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TurkishPhonemizer]'ı Sözcük'ün `pronunciation.py`'sinin çıktılarıyla karşılaştırır.
 *
 * "Ata" sesi Python'un ürettiği ses birimleriyle eğitildi; Kotlin tek bir harf farklı üretirse model o yeri
 * yanlış okur. Altın dosya: `src/test/resources/piper/altin.jsonl` (üretici: aynı klasördeki `altin_uret.py`).
 */
class TurkishPhonemizerGoldenTest {

    private val lines: List<JsonObject> by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("piper/altin.jsonl")
            ?: error("piper/altin.jsonl bulunamadı")
        stream.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
            .map { Json.parseToJsonElement(it).jsonObject }
    }

    @Test
    fun `ses birimleri Python ile birebir ayni`() {
        val cases = lines.filter { "t" in it }
        assertTrue("altın dosyada metin yok", cases.size > 1000)
        val failures = cases.mapNotNull { case ->
            val text = case.getValue("t").jsonPrimitive.content
            val expected = case.getValue("p").jsonArray.map { it.jsonPrimitive.content }
            val actual = runCatching { TurkishPhonemizer.phonemize(text) }.getOrElse { listOf("HATA: $it") }
            if (actual == expected) null else "«$text»\n  python: ${expected.joinToString("")}\n  kotlin: ${actual.joinToString("")}"
        }
        assertEquals(
            "${failures.size}/${cases.size} metin farklı:\n" + failures.take(40).joinToString("\n"),
            0,
            failures.size,
        )
    }

    @Test
    fun `cumle bolme Python ile ayni`() {
        val cases = lines.filter { "s" in it }
        assertTrue("altın dosyada cümle bölme örneği yok", cases.isNotEmpty())
        val failures = cases.mapNotNull { case ->
            val text = case.getValue("s").jsonPrimitive.content
            val expected = case.getValue("c").jsonArray.map { it.jsonPrimitive.content }
            val actual = TurkishPhonemizer.sentences(text).map { text.substring(it) }
            if (actual == expected) null else "«${text.take(80)}»\n  python: $expected\n  kotlin: $actual"
        }
        assertEquals(
            "${failures.size}/${cases.size} bölme farklı:\n" + failures.take(10).joinToString("\n"),
            0,
            failures.size,
        )
    }

    @Test
    fun `ses birimi kimlikleri Piper kodlamasinda`() {
        val idMap = mapOf("^" to listOf(1L), "_" to listOf(0L), "$" to listOf(2L), "a" to listOf(14L), "b" to listOf(15L))
        val ids = TurkishPhonemizer.phonemeIds(listOf("a", "b", "?yok", "a"), idMap)
        assertEquals(listOf(1L, 0L, 14L, 0L, 15L, 0L, 14L, 0L, 2L), ids.toList())
    }
}
