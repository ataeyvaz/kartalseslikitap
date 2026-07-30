package com.kartal.seslikitap.domain.correction

/**
 * "Bu gerçek bir kelime mi, ne kadar yaygın?" sorusunu cevaplayan sözcük dağarcığı.
 *
 * İki ayrı bilgi taşır ve bu ayrım düzelticinin güvenliğinin temelidir:
 *  - [contains]: kelime biliniyorsa **asla dokunulmaz**. Kapsam ne kadar genişse o kadar az
 *    yanlış düzeltme olur.
 *  - [frequency]: yalnızca "hangi adaya düzeltelim" kararında kullanılır. Sıklığı bilinmeyen
 *    (0) kelimeler bilinen kelimedir ama düzeltme hedefi olamaz.
 */
class WordLexicon(private val entries: Map<String, Long>) {

    val size: Int get() = entries.size

    fun contains(word: String): Boolean = entries.containsKey(word)

    fun frequency(word: String): Long = entries[word] ?: 0L

    companion object {
        val Empty = WordLexicon(emptyMap())

        /**
         * `kelime<TAB>sıklık` biçimindeki satırları okur. Bozuk satırlar sessizce atlanır:
         * sözlükteki tek bir hatalı satır yüzünden düzeltmenin tamamen devre dışı kalması
         * kötü bir takas olur.
         */
        fun fromLines(lines: Sequence<String>): WordLexicon {
            val entries = HashMap<String, Long>(INITIAL_CAPACITY)
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                val separator = line.indexOf('\t')
                if (separator <= 0) {
                    entries[line.trim()] = 0L
                } else {
                    val word = line.substring(0, separator)
                    val frequency = line.substring(separator + 1).trim().toLongOrNull() ?: 0L
                    entries[word] = frequency
                }
            }
            return WordLexicon(entries)
        }

        private const val INITIAL_CAPACITY = 128_000
    }
}
