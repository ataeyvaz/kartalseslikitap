package com.kartal.seslikitap.domain.usecase

import javax.inject.Inject

/**
 * Ham OCR çıktısını okunabilir/seslendirilebilir metne dönüştürür (plan Faz 1).
 *
 * Sağlayıcıdan bağımsızdır: hangi OCR motoru kullanılırsa kullanılsın aynı kurallar işler.
 * Kurallar:
 *  - Satır sonundaki tireli bölünmeleri birleştirir ("kelime-\nnin" -> "kelimenin")
 *  - Cümle ortasında bölünmüş satırları tek satıra birleştirir
 *  - Boş satırları paragraf sınırı olarak korur
 *  - Tek başına duran sayfa numarası satırlarını atar
 */
class CleanOcrTextUseCase @Inject constructor() {

    operator fun invoke(rawText: String): String {
        if (rawText.isBlank()) return ""

        val paragraphs = rawText
            .replace("\r\n", "\n")
            .split(PARAGRAPH_SEPARATOR)
            .map { paragraph -> paragraph.lines().map(String::trim).filter(::isContentLine) }
            .filter { it.isNotEmpty() }
            .map { lines -> lines.joinLines() }
            .filter { it.isNotBlank() }

        return paragraphs.joinToString("\n\n") { it.collapseSpaces() }
    }

    private fun List<String>.joinLines(): String = buildString {
        this@joinLines.forEachIndexed { index, line ->
            val isLast = index == this@joinLines.lastIndex
            when {
                // Satır sonu tiresi: kelime bölünmüş, tireyi atıp doğrudan birleştir.
                !isLast && line.endsWithSoftHyphen() -> append(line.dropLast(1))
                isLast -> append(line)
                else -> {
                    append(line)
                    append(' ')
                }
            }
        }
    }

    private fun isContentLine(line: String): Boolean = when {
        line.isBlank() -> false
        // "12", "- 12 -", "Sayfa 12" gibi satırlar seslendirmede gürültü yaratıyor.
        PAGE_NUMBER_REGEX.matches(line) -> false
        else -> true
    }

    private fun String.endsWithSoftHyphen(): Boolean =
        (endsWith('-') || endsWith('­')) && length > 1 && this[length - 2].isLetter()

    private fun String.collapseSpaces(): String = MULTI_SPACE_REGEX.replace(this, " ").trim()

    private companion object {
        val PARAGRAPH_SEPARATOR = Regex("\\n\\s*\\n")
        val MULTI_SPACE_REGEX = Regex("[ \\t]+")
        val PAGE_NUMBER_REGEX = Regex("^[-–—\\s]*(sayfa\\s*)?\\d{1,4}[-–—\\s.]*$", RegexOption.IGNORE_CASE)
    }
}
