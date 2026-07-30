package com.kartal.seslikitap.domain.correction

/**
 * Sözlük tabanlı, deterministik metin düzeltici.
 *
 * Tek bir güvenlik kuralı üzerine kurulu: **bir kelime ancak sözlükte yoksa ve düzeltilmiş
 * hâli sözlükte varsa değiştirilir.** Model yok, tahmin yok, uydurma yok; aynı girdi her
 * zaman aynı çıktıyı verir ve her değişiklik raporlanır.
 *
 * Dokunulmayanlar (kasıtlı olarak muhafazakâr):
 *  - Sözlükte bulunan kelimeler
 *  - Cümle başında olmayan büyük harfli kelimeler (özel isim olma ihtimali)
 *  - Rakam veya noktalama içeren belirteçler
 *  - Birden çok makul adayın yarıştığı kelimeler
 */
class DictionaryCorrector(
    private val lexicon: WordLexicon,
    private val minimumCandidateFrequency: Long = DEFAULT_MIN_FREQUENCY,
) {

    fun correct(text: String): CorrectionResult {
        if (text.isBlank() || lexicon.size == 0) return CorrectionResult.unchanged(text)

        val corrections = mutableListOf<Correction>()
        val output = StringBuilder(text.length)
        // Aynı kelime bir sayfada onlarca kez geçer; aday üretimi kelime başına bir kez yapılır.
        val memo = HashMap<String, TurkishOcrConfusions.Candidate?>()
        var index = 0
        var atSentenceStart = true

        while (index < text.length) {
            val char = text[index]
            if (!char.isLetterOrDigit()) {
                output.append(char)
                if (char in SENTENCE_TERMINATORS) atSentenceStart = true
                index++
                continue
            }

            val end = findTokenEnd(text, index)
            val token = text.substring(index, end)
            val replacement = correctToken(token, atSentenceStart, memo)

            if (replacement != null && replacement.text != token) {
                corrections += Correction(token, replacement.text, replacement.reason)
                output.append(replacement.text)
            } else {
                output.append(token)
            }

            atSentenceStart = false
            index = end
        }

        return CorrectionResult(output.toString(), corrections)
    }

    /**
     * Belirteci harf **ve rakamların** kesintisiz dizisi olarak alır.
     *
     * Rakamda bölmek kritik bir hata olurdu: "sarki1" harflerden ayrılsaydı geriye kalan
     * "sarki" düzeltilir ve "şarkı1" gibi bir çıktı üretilirdi. Oysa rakam içeren belirteç
     * (kod, ölçü, tarih) hiç dokunulmaması gereken şeydir.
     */
    private fun findTokenEnd(text: String, start: Int): Int {
        var end = start
        while (end < text.length) {
            val char = text[end]
            when {
                char.isLetterOrDigit() -> end++
                // Kesme işareti yalnızca iki karakter arasındaysa belirtecin parçasıdır ("Ali'nin").
                char in WORD_INTERNAL_CHARS &&
                    end + 1 < text.length &&
                    text[end + 1].isLetterOrDigit() -> end++

                else -> return end
            }
        }
        return end
    }

    private fun correctToken(
        token: String,
        atSentenceStart: Boolean,
        memo: MutableMap<String, TurkishOcrConfusions.Candidate?>,
    ): TurkishOcrConfusions.Candidate? {
        // Rakam veya karışık içerik: kod, tarih, ölçü olabilir; dokunma.
        if (token.any { it.isDigit() }) return null
        if (token.any { !it.isLetter() && it !in WORD_INTERNAL_CHARS }) return null

        // Cümle ortasında büyük harf = büyük ihtimalle özel isim; sözlükte olmaması normaldir.
        if (!atSentenceStart && token.firstOrNull()?.isUpperCase() == true) return null

        val lowercased = TurkishOcrConfusions.lowercase(token)
        val (word, suffix) = splitApostrophe(lowercased)
        if (word.length < MIN_CORRECTABLE_LENGTH) return null

        val best = if (memo.containsKey(word)) {
            memo[word]
        } else {
            findBestCandidate(word).also { memo[word] = it }
        } ?: return null

        val corrected = TurkishOcrConfusions.applyCasing(token, best.text + suffix)
        return TurkishOcrConfusions.Candidate(corrected, best.reason)
    }

    /** Aday üretimi ve sözlük taraması; kelime başına bir kez yapılır (bkz. memo). */
    private fun findBestCandidate(word: String): TurkishOcrConfusions.Candidate? {
        if (lexicon.contains(word)) return null

        val candidates = TurkishOcrConfusions.candidatesFor(word)
            .filter { lexicon.contains(it.text) }
            .sortedByDescending { lexicon.frequency(it.text) }

        val best = candidates.firstOrNull() ?: return null
        if (lexicon.frequency(best.text) < minimumCandidateFrequency) return null

        // İki aday da yaygınsa hangisinin doğru olduğunu bilemeyiz; karışmaktansa bırak.
        val runnerUp = candidates.getOrNull(1)
        if (runnerUp != null &&
            lexicon.frequency(runnerUp.text) * AMBIGUITY_RATIO > lexicon.frequency(best.text)
        ) {
            return null
        }

        return best
    }

    /** "kelimenin" gibi eklerde kesmeden sonrası düzeltmeye girmez, aynen korunur. */
    private fun splitApostrophe(word: String): Pair<String, String> {
        val apostrophe = word.indexOfFirst { it in WORD_INTERNAL_CHARS }
        return if (apostrophe < 0) word to "" else word.substring(0, apostrophe) to word.substring(apostrophe)
    }

    private companion object {
        val WORD_INTERNAL_CHARS = charArrayOf('\'', '’')
        val SENTENCE_TERMINATORS = charArrayOf('.', '!', '?', '\n')

        /** Bu sıklığın altındaki adaylar "yaygın kelime" sayılmaz, düzeltme hedefi olamaz. */
        const val DEFAULT_MIN_FREQUENCY = 200L

        /** İkinci adayın sıklığı birincinin bu katından büyükse karar belirsiz sayılır. */
        const val AMBIGUITY_RATIO = 4L

        const val MIN_CORRECTABLE_LENGTH = 3
    }
}
