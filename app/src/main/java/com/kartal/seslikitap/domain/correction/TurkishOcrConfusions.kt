package com.kartal.seslikitap.domain.correction

import java.util.Locale

/**
 * Türkçe metinlerde OCR'ın en sık karıştırdığı karakterler ve bunlardan aday üretimi.
 *
 * İki ayrı hata sınıfı var:
 *  - **İşaret kaybı:** tarayıcı noktayı/çengeli görmez, "şarkı" -> "sarki". Türkçede en yaygın
 *    OCR hatası budur; bir kelimede birden çok harfi birden etkileyebilir.
 *  - **Şekil benzerliği:** "rn" -> "m", "1" -> "l", "0" -> "o". Bunlar tek noktada olur.
 */
object TurkishOcrConfusions {

    /** Aynı harfin işaretli/işaretsiz biçimleri; her grup birbirinin adayıdır. */
    private val DIACRITIC_GROUPS = listOf(
        setOf('i', 'ı', 'î'),
        setOf('s', 'ş'),
        setOf('g', 'ğ'),
        setOf('c', 'ç'),
        setOf('o', 'ö'),
        setOf('u', 'ü', 'û'),
        setOf('a', 'â'),
    )

    private val DIACRITIC_ALTERNATIVES: Map<Char, List<Char>> =
        DIACRITIC_GROUPS.flatMap { group ->
            group.map { char -> char to (group - char).toList() }
        }.toMap()

    /** Şekilce karışan tek/çift karakter dizileri (yanlış okunan -> olması gereken). */
    private val GLYPH_SUBSTITUTIONS = listOf(
        "rn" to "m",
        "m" to "rn",
        "cl" to "d",
        "vv" to "w",
        "1" to "l",
        "1" to "ı",
        "l" to "ı",
        "ı" to "l",
        "0" to "o",
        "5" to "s",
        "8" to "b",
        "6" to "b",
        "9" to "g",
        "3" to "e",
        "ii" to "ü",
    )

    /**
     * Bir kelimenin olası doğru biçimlerini üretir. Kelimenin kendisi listeye dahil değildir.
     *
     * İşaret kombinasyonları üstel büyüdüğü için belirsiz konum sayısı sınırlanır; sınırı aşan
     * kelimelerde yalnızca şekil benzerliği adayları üretilir. Böylece uzun kelimelerde
     * binlerce aday üretip hem yavaşlamak hem de yanlış eşleşme riskini artırmaktan kaçınırız.
     */
    fun candidatesFor(word: String): List<Candidate> {
        if (word.length < MIN_WORD_LENGTH) return emptyList()

        val candidates = LinkedHashMap<String, CorrectionReason>()

        diacriticVariants(word).forEach { variant ->
            if (variant != word) candidates.putIfAbsent(variant, CorrectionReason.DIACRITIC_RESTORED)
        }
        glyphVariants(word).forEach { variant ->
            if (variant != word) candidates.putIfAbsent(variant, CorrectionReason.GLYPH_CONFUSION)
        }

        return candidates.map { (text, reason) -> Candidate(text, reason) }
    }

    private fun diacriticVariants(word: String): List<String> {
        val ambiguousPositions = word.indices.filter { DIACRITIC_ALTERNATIVES.containsKey(word[it]) }
        if (ambiguousPositions.isEmpty()) return emptyList()

        // Sınır konum sayısına değil **aday sayısına** konur: i ve u gruplarında üç seçenek
        // olduğu için 8 sesli harfli sıradan bir Türkçe kelime 3^8 = 6561 aday üretebilir.
        var estimated = 1L
        for (position in ambiguousPositions) {
            estimated *= (DIACRITIC_ALTERNATIVES.getValue(word[position]).size + 1)
            if (estimated > MAX_DIACRITIC_CANDIDATES) return emptyList()
        }

        var variants = listOf(word)
        ambiguousPositions.forEach { position ->
            val alternatives = DIACRITIC_ALTERNATIVES.getValue(word[position])
            variants = variants.flatMap { current ->
                buildList {
                    add(current)
                    alternatives.forEach { replacement ->
                        add(current.replaceRange(position, position + 1, replacement.toString()))
                    }
                }
            }
        }
        return variants
    }

    private fun glyphVariants(word: String): List<String> = buildList {
        GLYPH_SUBSTITUTIONS.forEach { (wrong, right) ->
            var index = word.indexOf(wrong)
            while (index >= 0) {
                add(word.replaceRange(index, index + wrong.length, right))
                index = word.indexOf(wrong, index + 1)
            }
        }
    }

    /** Düzeltilmiş kelimeye orijinalin büyük/küçük harf düzenini uygular. */
    fun applyCasing(original: String, corrected: String): String = when {
        original.all { !it.isLetter() || it.isUpperCase() } && original.any { it.isLetter() } ->
            corrected.uppercase(TURKISH)

        original.firstOrNull()?.isUpperCase() == true ->
            corrected.replaceFirstChar { it.titlecase(TURKISH) }

        else -> corrected
    }

    fun lowercase(word: String): String = word.lowercase(TURKISH)

    data class Candidate(val text: String, val reason: CorrectionReason)

    /** Türkçe'de i/I dönüşümü farklı çalıştığı için tüm harf işlemleri bu yerelle yapılır. */
    private val TURKISH: Locale = Locale.forLanguageTag("tr-TR")

    private const val MIN_WORD_LENGTH = 2

    /**
     * Bir kelime için üretilecek en fazla işaret adayı. Aşan kelimelerde işaret adayı
     * üretilmez (şekil adayları üretilmeye devam eder): binlerce aday hem yavaştır hem de
     * yanlış eşleşme olasılığını artırır.
     */
    private const val MAX_DIACRITIC_CANDIDATES = 256L
}
