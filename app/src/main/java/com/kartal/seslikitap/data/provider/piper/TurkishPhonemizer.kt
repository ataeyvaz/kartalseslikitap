package com.kartal.seslikitap.data.provider.piper

import java.math.BigInteger
import java.text.Normalizer
import java.util.Locale

/**
 * Türkçe telaffuz: metni okunacak biçime getirir ve Piper sesinin beklediği ses birimlerine (IPA) çevirir.
 *
 * Sözcük masaüstü uygulamasındaki `sozcuk/pronunciation.py`'nin **birebir** karşılığıdır. "Ata" sesi o Python
 * koduyla üretilmiş ses birimleriyle eğitildi; buradaki çıktı bir harf bile farklı olursa model o yeri yanlış
 * okur. Bu yüzden kurallar "düzeltilmez", Python'la aynı tutulur: `TurkishPhonemizerGoldenTest` Python'un
 * ürettiği altın dosyayla karşılaştırır. Python tarafı değişirse altın dosya yeniden üretilmelidir.
 *
 * Python'dan taşırken korunan davranışlar:
 * - Python `re` Unicode'dur: `\w`, `\b`, `\d`, `\s` için desenler `(?U)` ile derlenir.
 * - Python'da `"" in "ei"` doğrudur (boş dize her dizede bulunur); sözcük başındaki "ğ", "â", "ken" kuralları
 *   buna dayanır → [pyIn].
 * - Türkçe küçük harf: önce `I → ı`, `İ → i`, sonra genel küçültme ([lowerTr]).
 */
object TurkishPhonemizer {

    /**
     * Python `re` Unicode'dur (`\w`, `\b`, `\d`, `\s`). Bilgisayardaki Java'da (birim testleri) bunun için `(?U)`
     * gerekir; Android ise düzenli ifadeleri ICU ile çalıştırır — orada bu sınıflar zaten Unicode'dur ve `(?U)`
     * bayrağı geçersiz sayılabilir. Bu yüzden bayrak yalnız Android dışında eklenir.
     * (Nesne başlatma sırası: bu değer desenlerden önce tanımlı olmalı.)
     */
    private val ON_ANDROID: Boolean =
        System.getProperty("java.vm.vendor").orEmpty().contains("Android", ignoreCase = true) ||
            System.getProperty("java.vm.name").orEmpty().contains("Dalvik", ignoreCase = true)

    private fun uRegex(pattern: String) = Regex(if (ON_ANDROID) pattern else "(?U)$pattern")

    private const val VOWELS = "aeıioöuüâîû"
    private const val FRONT = "eiöüîâ"      // â: önceki ünsüzü incelttiği için ince sayılır ("lâle", "kâr")

    private val UNSTRESSED_WORDS = setOf("ve", "de", "da", "ki", "mi", "mı", "mu", "mü", "ile", "ise", "te", "ta")

    /** Kendi vurgusunu taşıyan sözcükler: sözcük → vurgulu hecenin sırası (0 = ilk hece). */
    private val LEXICAL_STRESS = mapOf(
        "şimdi" to 0, "sonra" to 0, "belki" to 0, "yarın" to 0, "bugün" to 0, "nasıl" to 0, "niçin" to 0,
        "nerede" to 0, "nereye" to 0, "nereden" to 0, "hangi" to 0, "çünkü" to 0, "ama" to 0, "fakat" to 0,
        "ancak" to 0, "yalnız" to 0, "sadece" to 0, "şöyle" to 0, "böyle" to 0, "öyle" to 0, "hemen" to 0,
        "henüz" to 0, "yine" to 0, "bazen" to 0, "galiba" to 0, "tabii" to 0, "lütfen" to 0, "merhaba" to 0,
        "anne" to 0, "baba" to 0, "abla" to 0, "amca" to 0, "teyze" to 0, "dayı" to 0, "değil" to 0, "acaba" to 0,
        "aslında" to 0, "herhalde" to 0, "gerçekten" to 1, "evet" to 0, "hayır" to 0, "peki" to 0, "haydi" to 0,
        "hadi" to 0, "eğer" to 0, "sanki" to 0, "önce" to 0, "kimse" to 0, "herkes" to 0, "biraz" to 0, "bazı" to 0,
        "neden" to 0, "niye" to 0, "rağmen" to 0, "göre" to 0, "gibi" to 0, "diye" to 0, "hiçbir" to 0,
        "vardır" to 0, "yoktur" to 0, "budur" to 0, "şudur" to 0, "odur" to 0, "ankara" to 0, "istanbul" to 1,
        "izmir" to 0, "bursa" to 0, "antalya" to 1, "türkiye" to 0, "avrupa" to 1, "almanya" to 1, "fransa" to 0,
        "italya" to 1, "amerika" to 1, "ispanya" to 1, "rusya" to 0, "londra" to 0, "paris" to 0, "efendim" to 1,
        "asla" to 0, "yukarı" to 0, "aşağı" to 0, "ileri" to 0, "geri" to 0, "dışarı" to 0, "içeri" to 0,
    )

    /** Kendi vurgusunu taşıyan sözcüklere gelip vurguyu değiştirmeyen ekler ("ÖNceden", "ANnesi", "BAbalar"). */
    private val STRESS_SUFFIX = Regex(
        "(?:l[ae]r)?(?:[ıiuü]m[ıiuü]z|[ıiuü]n[ıiuü]z|s[ıiuü]|[ıiuü]m|[ıiuü]n|m|n|[ıiuü])?" +
            "(?:n?[dt][ae](?:n|ki)?|[ny]?[ae]|[ny]?[ıiuü]|n?[ıiuü]n|y?l[ae]|ki)?" +
            "(?:s[ıiuü]n(?:[ıiuü]z)?|y?[ıiuü][mz]|[dt][ıiuü]r)?$",
    )

    /** Sondaki l'si ince okunan alıntı sözcükler. */
    private val THIN_L_WORDS = setOf(
        "hal", "hâl", "rol", "gol", "alkol", "futbol", "voleybol", "basketbol", "kontrol", "protokol", "petrol",
        "usul", "kabul", "meşgul", "mahsul", "hayal", "ihtimal", "istiklal", "misal", "kemal", "cemal", "celal",
        "hilal", "sual", "idrak", "ilan", "lal", "kalp", "hayali", "hala", "hâlâ",
    )

    /** -ma/-me ile bitip ek alan adlar (olumsuzluk eki sanılmasın: "sinemadan"). */
    private val MA_NOUNS = listOf(
        "sinema", "tema", "drama", "firma", "forma", "pijama", "lama", "kama", "dama", "panorama", "karizma",
        "dogma", "prizma", "sigma", "magma", "astma", "gama", "zama", "kinema", "ödeme", "cümle",
    )

    /** "-ca/-ce" ile biten ama eki olmayan sözcükler (vurgu sonda kalır). */
    private val CA_EXCEPTIONS = setOf("düşünce", "karınca", "gonca", "kanca", "hınca")

    private val NEG = Regex(
        "m[ae](?:d[ıiuü]|m[ıiuü]ş|z|y[ae]c[ae]k|s[ae]|s[ıi]n|" +
            "y[ıi]n|d[ae]n|y[ae]n|d[ıi]k|y[ıi][mz])(?:m|n|k|[ıi]n|[ıi]z|lar|ler|n[ıi]z|s[ıi]n|s[ıi]n[ıi]z|" +
            "d[ıi]r|ken|s[ae]|m[ıi]ş|d[ıi]|[ıi]m|y[ıi]z|y[ıi]m)?$",
    )

    /** Türkçe küçük harf: I → ı, İ → i. */
    fun lowerTr(text: String): String = text.replace("I", "ı").replace("İ", "i").lowercase(Locale.ROOT)

    /** Python'un `a in b` davranışı: boş dize her dizede bulunur. */
    private fun pyIn(needle: String, haystack: String): Boolean = needle.isEmpty() || haystack.contains(needle)

    private fun isVowel(c: Char) = VOWELS.indexOf(c) >= 0

    private fun isVowel(s: String) = s.length == 1 && isVowel(s[0])   // Python: `s in VOWELS` (küme)

    // =========================================================================
    // Sayılar
    // =========================================================================

    private val ONES = listOf("", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz")
    private val TENS = listOf("", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan")
    private val SCALES = listOf("", "bin", "milyon", "milyar", "trilyon", "katrilyon")
    private val THOUSAND = BigInteger.valueOf(1000)

    private fun belowThousand(n: Int): List<String> {
        val words = mutableListOf<String>()
        val hundreds = n / 100
        val rest = n % 100
        if (hundreds != 0) {
            if (hundreds > 1) words += ONES[hundreds]
            words += "yüz"
        }
        val tens = rest / 10
        val ones = rest % 10
        if (tens != 0) words += TENS[tens]
        if (ones != 0) words += ONES[ones]
        return words
    }

    /** Tam sayıyı Türkçe okunuşuna çevirir: 1250 → "bin iki yüz elli". */
    fun numberWords(value: BigInteger): String {
        if (value.signum() == 0) return "sıfır"
        if (value.signum() < 0) return "eksi " + numberWords(value.negate())
        var n = value
        val groups = mutableListOf<Int>()
        while (n.signum() != 0) {
            val (q, r) = n.divideAndRemainder(THOUSAND)
            groups += r.toInt()
            n = q
        }
        if (groups.size > SCALES.size) {
            // Python'daki gibi: döngüden sonra n sıfırdır → "sıfır"
            return digits(n.toString())
        }
        val words = mutableListOf<String>()
        for (index in groups.indices.reversed()) {
            val group = groups[index]
            if (group == 0) continue
            if (index == 1 && group == 1) {
                words += "bin"       // "bir bin" denmez
            } else {
                words += belowThousand(group)
                if (index != 0) words += SCALES[index]
            }
        }
        return words.joinToString(" ")
    }

    fun numberWords(value: Long): String = numberWords(BigInteger.valueOf(value))

    /** Sıra sayısı: 3 → "üçüncü", 40 → "kırkıncı". */
    fun ordinalWords(n: BigInteger): String {
        val words = numberWords(n).split(" ").toMutableList()
        var last = words.last()
        if (last == "dört") last = "dörd"
        val suffix = if (isVowel(last.last())) {
            "nc" + harmonyHigh(last)
        } else {
            val vowel = harmonyHigh(last)
            vowel + "nc" + vowel
        }
        words[words.size - 1] = last + suffix
        return words.joinToString(" ")
    }

    /** Dar ünlü uyumu (ı, i, u, ü) — sözcüğün son ünlüsüne göre. */
    private fun harmonyHigh(word: String): String {
        for (char in word.reversed()) {
            when (char) {
                'a', 'ı' -> return "ı"
                'e', 'i' -> return "i"
                'o', 'u' -> return "u"
                'ö', 'ü' -> return "ü"
            }
        }
        return "i"
    }

    private fun digits(text: String): String =
        text.map { ONES[Character.digit(it, 10)].ifEmpty { "sıfır" } }.joinToString(" ")

    private fun parseInt(text: String): BigInteger = BigInteger(text)

    private val THOUSANDS_NUMBER = uRegex("\\d{1,3}(\\.\\d{3})+")

    /** "1.250" (binlik ayraç) ve "3,5" (ondalık) dahil sayıyı okur. */
    private fun readNumber(text: String): String {
        if (THOUSANDS_NUMBER.matches(text)) return numberWords(parseInt(text.replace(".", "")))
        val comma = text.indexOf(',')
        val whole = if (comma >= 0) text.substring(0, comma) else text
        val fraction = if (comma >= 0) text.substring(comma + 1) else ""
        if (whole.length > 15) return digits(whole)
        var words = if (!(whole.length > 1 && whole.startsWith("0"))) numberWords(parseInt(whole)) else digits(whole)
        if (fraction.isNotEmpty()) {
            val fractionWords = if (fraction.startsWith("0")) digits(fraction) else numberWords(parseInt(fraction))
            words += " virgül $fractionWords"
        }
        return words
    }

    // =========================================================================
    // Metni okunacak biçime getirme
    // =========================================================================

    private val MONTHS = listOf(
        "", "ocak", "şubat", "mart", "nisan", "mayıs", "haziran", "temmuz", "ağustos", "eylül", "ekim", "kasım",
        "aralık",
    )

    private val ABBREVIATIONS = mapOf(
        "dr." to "doktor", "prof." to "profesör", "doç." to "doçent", "av." to "avukat", "sn." to "sayın",
        "vb." to "ve benzeri", "vs." to "vesaire", "örn." to "örneğin", "bkz." to "bakınız", "no." to "numara",
        "mah." to "mahallesi", "cad." to "caddesi", "sok." to "sokağı", "blv." to "bulvarı", "apt." to "apartmanı",
        "tel." to "telefon", "a.ş." to "anonim şirketi", "ltd." to "limitet", "şti." to "şirketi", "t.c." to "te ce",
        "yy." to "yüzyıl", "m.ö." to "milattan önce", "m.s." to "milattan sonra", "s." to "sayfa",
        "yrd." to "yardımcı", "öğr." to "öğretmen",
    )

    /** Yalnızca sayıdan sonra okunan birimler. */
    private val UNITS = mapOf(
        "km" to "kilometre", "m" to "metre", "cm" to "santimetre", "mm" to "milimetre", "kg" to "kilogram",
        "g" to "gram", "mg" to "miligram", "lt" to "litre", "l" to "litre", "ml" to "mililitre", "sn" to "saniye",
        "dk" to "dakika", "sa" to "saat", "tl" to "lira", "mb" to "megabayt", "gb" to "gigabayt", "kb" to "kilobayt",
        "tb" to "terabayt", "kw" to "kilovat", "mhz" to "megahertz", "ghz" to "gigahertz", "km/sa" to "kilometre",
        "°c" to "derece",
    )

    /** Sıra önemli (Python sözlüğündeki sırayla uygulanır). */
    private val SYMBOLS = linkedMapOf(
        "&" to " ve ", "+" to " artı ", "=" to " eşittir ", "@" to " et ", "₺" to " lira ", "$" to " dolar ",
        "€" to " avro ", "£" to " sterlin ", "°" to " derece ", "×" to " çarpı ", "÷" to " bölü ", "§" to " madde ",
        "#" to " numara ",
    )

    private val LETTER_NAMES = mapOf(
        'a' to "a", 'b' to "be", 'c' to "ce", 'ç' to "çe", 'd' to "de", 'e' to "e", 'f' to "fe", 'g' to "ge",
        'ğ' to "yumuşak ge", 'h' to "he", 'ı' to "ı", 'i' to "i", 'j' to "je", 'k' to "ke", 'l' to "le", 'm' to "me",
        'n' to "ne", 'o' to "o", 'ö' to "ö", 'p' to "pe", 'q' to "kü", 'r' to "re", 's' to "se", 'ş' to "şe",
        't' to "te", 'u' to "u", 'ü' to "ü", 'v' to "ve", 'w' to "dabılyu", 'x' to "iks", 'y' to "ye", 'z' to "ze",
    )

    /** Sözcük gibi okunan kısaltmalar. */
    private val ACRONYM_WORDS = setOf(
        "nato", "odtü", "aselsan", "tübitak", "nasa", "unesco", "unicef", "yök", "tüsiad", "havelsan", "roketsan",
        "tusaş", "aşti", "iski", "aski", "buski", "tomer", "ösym",
    )

    private fun spell(acronym: String): String =
        lowerTr(acronym).map { LETTER_NAMES[it] ?: it.toString() }.joinToString(" ")

    private val CONSONANT_RUN = Regex("[^aeıioöuü]+")
    private val TWO_CONSONANTS_END = Regex("[^aeıioöuü]{2}$")

    private fun acronym(word: String, suffix: String): String {
        val low = lowerTr(word)
        val consonantRuns = CONSONANT_RUN.findAll(low).map { it.value }.toList()
        val firstTwo = low.take(2)
        val pronounceable = low in ACRONYM_WORDS || (
            low.length >= 3 && low.any(::isVowel) && firstTwo != firstTwo.filterNot(::isVowel) &&
                consonantRuns.all { it.length <= 2 } && (isVowel(low.last()) || low.last() in "nmrlkz") &&
                !TWO_CONSONANTS_END.containsMatchIn(low)
            )
        val spoken = if (pronounceable) low else spell(word)
        return spoken + lowerTr(suffix.trimStart('\'', '\u2019'))
    }

    private val DATE = uRegex("\\b(\\d{1,2})[./](\\d{1,2})[./](\\d{4})\\b")
    private val CLOCK = uRegex("\\b(\\d{1,2}):(\\d{2})\\b(?:'([a-zçğıöşü]+))?")
    private val PERCENT = uRegex("%\\s?(\\d+(?:,\\d+)?)(?:'([a-zçğıöşü]+))?")
    private val ORDINAL = uRegex("\\b(\\d+)\\.(?=\\s+[a-zçğıöşü])")
    private val UNIT = uRegex("(\\d)\\s?([A-Za-z°/]+)(?![A-Za-zçğıöşüÇĞİÖŞÜ])")
    private val NUMBER = uRegex("(\\d+(?:\\.\\d{3})*(?:,\\d+)?)(?:'([a-zçğıöşü]+))?")
    private val ABBREVIATION = uRegex("(?<![\\wçğıöşüÇĞİÖŞÜ])(?:[A-Za-zÇĞİÖŞÜçğıöşü]{1,4}\\.){1,3}")
    private val ACRONYM = uRegex("\\b([A-ZÇĞİÖŞÜ]{2,})('[a-zçğıöşü]+)?\\b")
    private val SLASH_BETWEEN_WORDS = uRegex("(?<=\\w)/(?=\\w)")
    private val SPACES = Regex("[ \\t]+")

    /** Metni sesli okunacak biçime getirir: sayılar, saat, tarih, yüzde, kısaltmalar, simgeler → sözcük. */
    fun normalize(input: String): String {
        var text = Normalizer.normalize(input, Normalizer.Form.NFC)
        text = text.replace("\u2019", "'").replace("\u2018", "'").replace("\u201c", "\"").replace("\u201d", "\"")
            .replace("\u2026", "...")
        text = text.replace("\u2013", " - ").replace("\u2014", " - ")
        // tarih: 21.09.2026 / 21/09/2026
        text = DATE.replace(text) { m ->
            val month = parseInt(m.groupValues[2]).toInt()
            if (month in 1..12) {
                "${numberWords(parseInt(m.groupValues[1]))} ${MONTHS[month]} ${numberWords(parseInt(m.groupValues[3]))}"
            } else {
                m.value
            }
        }
        // saat: 10:30, 10:30'da (09.05 değil: nokta tarih ya da ondalık olabilir)
        text = CLOCK.replace(text) { m ->
            val minute = m.groupValues[2]
            var spoken = numberWords(parseInt(m.groupValues[1]))
            if (minute != "00") {
                spoken += " " + if (minute.startsWith("0")) digits(minute) else numberWords(parseInt(minute))
            }
            spoken + (m.groups[3]?.value ?: "")
        }
        // yüzde: %50, % 50, %50'si
        text = PERCENT.replace(text) { m -> "yüzde " + readNumber(m.groupValues[1]) + (m.groups[2]?.value ?: "") }
        // sıra sayısı: "3. sayfa" (ardından küçük harfle devam eden)
        text = ORDINAL.replace(text) { m -> ordinalWords(parseInt(m.groupValues[1])) }
        // sayı + birim: "5 km", "12 TL"
        text = UNIT.replace(text) { m ->
            val name = UNITS[lowerTr(m.groupValues[2])]
            if (name != null) "${m.groupValues[1]} $name" else m.value
        }
        // sayılar (eklerle: 2026'da → iki bin yirmi altıda)
        text = NUMBER.replace(text) { m -> readNumber(m.groupValues[1]) + (m.groups[2]?.value ?: "") }
        // kısaltmalar
        text = ABBREVIATION.replace(text) { m -> ABBREVIATIONS[lowerTr(m.value)] ?: m.value }
        // büyük harfli kısaltmalar: THY'nin → te ha yenin, NATO → nato
        text = ACRONYM.replace(text) { m -> acronym(m.groupValues[1], m.groups[2]?.value ?: "") }
        for ((symbol, spoken) in SYMBOLS) text = text.replace(symbol, spoken)
        text = SLASH_BETWEEN_WORDS.replace(text, " ")
        return pyStrip(SPACES.replace(text, " "))
    }

    // =========================================================================
    // Sözcük → ses birimleri
    // =========================================================================

    private fun vowelPositions(word: String): List<Int> = word.indices.filter { isVowel(word[it]) }

    private val NEG_PRESENT = Regex("m[ıiuü]yor")
    private val PRESENT = Regex("(?<=[aeıioöuüâîû])yor")
    private val PARTICIPLE = Regex("(?:[dt][ıiuü]|[ae]c[ae])ğ")
    private val PERSON_PLURAL = Regex("s[ıiuü]n[ıiuü]z$")
    private val NECESSITY = Regex("m[ae]l[ıi](?:[ıi]m|s[ıi]n|y[ıi]z|s[ıi]n[ıi]z|l[ae]r|d[ıi]r|y[ıi]m)?$")
    private val COPULA = Regex("[dt][ıiuü]r(?:lar|ler)?$")
    private val CA_SUFFIX = Regex("(?<=[^aeıioöuüâîû])[cç][ae]$")

    /** Vurgusuz son ekler; ikinci değer: Python'daki `"s[ae]" in pattern` (hece sayısı şartı aranmaz). */
    private val UNSTRESSED_SUFFIXES = listOf(
        Regex("(?<=[rzyşın])ken$") to false,
        Regex("yl[ae]$") to false,
        Regex("(?<=l[ae]r)l[ae]$") to false,
        Regex("(?<=m[ae]k)l[ae]$") to false,
        Regex("(?<=[rz])s[ae](?:m|n|k|n[ıi]z|lar|ler)?$") to true,
        Regex("(?<=y)s[ae](?:m|n|k|n[ıi]z)?$") to true,
    )

    /** Vurgulu hecenin sırası (0 = ilk hece); vurgusuz sözcükte null. */
    private fun stressSyllable(word: String, root: String?): Int? {
        val vowels = vowelPositions(word)
        val count = vowels.size
        if (count == 0 || word in UNSTRESSED_WORDS) return null
        val last = count - 1
        if (!root.isNullOrEmpty() && root in LEXICAL_STRESS) return minOf(LEXICAL_STRESS.getValue(root), last)
        LEXICAL_STRESS[word]?.let { return minOf(it, last) }
        for (size in word.length - 1 downTo 3) {
            val stem = word.substring(0, size)
            val stemStress = LEXICAL_STRESS[stem]
            if (stemStress != null && STRESS_SUFFIX.matches(word.substring(size))) return minOf(stemStress, last)
        }
        if (count == 1) return 0

        // position (harf sırası) içeren hecenin bir öncesi
        fun before(position: Int): Int = maxOf(0, vowels.count { it < position } - 1)
        fun hasVowelBefore(position: Int) = word.substring(0, position).any(::isVowel)

        // -m(ı/i/u/ü)yor: olumsuz şimdiki zaman → olumsuzluktan önceki hece ("gelmiyor" GEL-mi-yor)
        NEG_PRESENT.find(word)?.let { if (hasVowelBefore(it.range.first)) return before(it.range.first) }
        // -yor: "yor"dan önceki hece ("geliyor" ge-Lİ-yor)
        PRESENT.find(word)?.let { return before(it.range.first) }
        // -ma/-me olumsuzluğu ("gelmedi", "yapmadan", "gelmez")
        if (MA_NOUNS.none { word.startsWith(it) }) {
            NEG.find(word)?.let { if (hasVowelBefore(it.range.first)) return before(it.range.first) }
        }
        // ortaç -dığ/-acağ: vurgu ortaç hecesinde ("okuDUğum", "anlataCAğım")
        PARTICIPLE.find(word)?.let { match ->
            val end = match.range.last + 1
            if (end < word.length) return vowels.count { it < end - 1 } - 1
        }
        // kişi ekleri -sınız/-siniz vurgusuz ("alabiLİRsiniz")
        PERSON_PLURAL.find(word)?.let { if (count >= 3) return before(it.range.first) }
        // gereklilik -malı/-meli: vurgu "ma" hecesinde ("gelMEli")
        NECESSITY.find(word)?.let { match ->
            if (count >= 3 && hasVowelBefore(match.range.first)) return vowels.count { it < match.range.first }
        }
        // -dır ve türevleri (en az üç hece: "kaldır, indir, getir" gibi fiil kökleri karışmasın)
        COPULA.find(word)?.let { if (count >= 3) return before(it.range.first) }
        // -ken, -(y)la/-(y)le, -lerle, -mekle, -sa/-se (koşul)
        for ((pattern, anyLength) in UNSTRESSED_SUFFIXES) {
            val match = pattern.find(word)
            if (match != null && (count >= 3 || anyLength)) return before(match.range.first)
        }
        // -ca/-ce eki ("Türkçe", "Almanca", "gelince", "bence")
        CA_SUFFIX.find(word)?.let { if (word !in CA_EXCEPTIONS) return before(it.range.first) }
        return last
    }

    /** index'teki ünlünün hecesi kapalı mı (ünlüler arasında tek ünsüz sonraki heceye geçer). */
    private fun closed(word: String, index: Int, vowels: List<Int>): Boolean {
        val after = word.substring(index + 1)
        if (after.startsWith("ğ")) {
            // ğ önceki ünlüyü uzatır (açık hece gibi); e/i'den sonra ünsüz önünde y gibi okunur ("çiğdem" çɪjdem)
            return word[index] in "ei" && (after.length < 2 || !isVowel(after[1]))
        }
        val following = vowels.firstOrNull { it > index } ?: return after.any { !isVowel(it) }
        val between = word.substring(index + 1, following)
        if (between.length == 2 && between[0] == between[1] && between[0] in "bcçdgkpt") {
            return false                 // çift patlamalı tek ünsüz gibi sayılır ("seyretti" sɛjretːɪ)
        }
        return between.length >= 2
    }

    private fun vowelPhone(word: String, index: Int, vowels: List<Int>, char: Char): String {
        val isLast = index == vowels.last()
        val final = isLast && index == word.length - 1
        val closed = closed(word, index, vowels)
        val lax = closed || final
        return when (char) {
            'e' -> if (closed) (if (word[index + 1] in "lmn") "æ" else "ɛ") else if (final) "ɛ" else "e"
            'i', 'î' -> if (lax) "ɪ" else "i"
            'o' -> if (lax) "ɔ" else "o"
            'u', 'û' -> if (lax || word.startsWith("ğ", index + 1)) "ʊ" else "u"
            'ü' -> if (lax) "ø" else "y"
            'ö' -> "œ"
            'ı' -> "ɯ"
            else -> "a"
        }
    }

    /** l ve g için belirleyici ünlü: hemen sonraki ünlü, yoksa önceki. */
    private fun neighbourVowel(word: String, index: Int): Char {
        for (i in index + 1 until word.length) {
            val char = word[i]
            if (isVowel(char)) return char
            if (char !in "lrğy") break   // arada başka ünsüz varsa sonraki hecenin ünlüsü belirlemez
        }
        for (i in index - 1 downTo 0) {
            if (isVowel(word[i])) return word[i]
        }
        return 'a'
    }

    private val CONSONANTS = mapOf(
        'b' to "b", 'c' to "dʒ", 'ç' to "tʃ", 'd' to "d", 'f' to "f", 'h' to "h", 'j' to "ʒ", 'm' to "m", 'n' to "n",
        'p' to "p", 's' to "s", 'ş' to "ʃ", 't' to "t", 'v' to "v", 'y' to "j", 'z' to "z", 'k' to "k",
    )

    /** Tek bir sözcüğü (küçük harf; kesme işaretli özel ad olabilir) ses birimlerine çevirir. */
    fun wordPhonemes(input: String, stress: Boolean = true): List<String> {
        var word = input
        var root: String? = null
        val apostrophe = word.indexOf('\'')
        if (apostrophe >= 0) {
            root = word.substring(0, apostrophe)
            word = root + word.substring(apostrophe + 1)
        }
        word = word.replace("q", "k").replace("w", "v").replace("x", "ks")
        word = word.filter { isVowel(it) || it in CONSONANTS || it in "glrğ" }
        if (word.isEmpty()) return emptyList()
        val vowels = vowelPositions(word)
        if (vowels.isEmpty()) return wordPhonemesList(spell(word).split(" "), stress)   // ünlüsüz: harf harf
        val stressed = if (stress) stressSyllable(word, root) else null
        val stressedIndex = stressed?.let { vowels[it] }
        val thinFinalL = (if (root.isNullOrEmpty()) word else root) in THIN_L_WORDS

        val out = mutableListOf<String>()
        var index = 0
        while (index < word.length) {
            val char = word[index]
            val prev = if (index > 0) word[index - 1].toString() else ""
            val nxt = if (index + 1 < word.length) word[index + 1].toString() else ""
            if (isVowel(char)) {
                if (index == stressedIndex) out += "ˈ"
                var phone = vowelPhone(word, index, vowels, char)
                if (char == 'â' && !pyIn(prev, "kgl")) phone = "aː"
                out += phone
            } else if (char == 'ğ') {
                val after = nxt
                if (pyIn(prev, "ei")) {
                    out += "j"
                } else if (prev == "ı") {
                    out += "ɯ"
                } else if (isVowel(prev)) {
                    if (out.isNotEmpty() && out.last().last() != 'ː') out[out.size - 1] = out.last() + "ː"
                    if (isVowel(after) && after == prev) {      // "ağa" → aː, "uğu" → uː
                        if (index + 1 == stressedIndex && "ˈ" !in out) out.add(out.size - 1, "ˈ")
                        index += 2
                        continue
                    }
                }
            } else {
                if (nxt == char.toString()) {
                    // çift ünsüz: patlamalılar uzatılır ("attı" atːɯ), sürekliler iki kez yazılır ("belli" bællɪ)
                    val afterDouble = if (index + 2 < word.length) word[index + 2].toString() else ""
                    val phone = consonant(word, index, char, prev, afterDouble, thinFinalL)
                    out += if (char in "bcçdgkpt") phone + "ː" else phone + phone
                    index += 2
                    continue
                }
                out += consonant(word, index, char, prev, nxt, thinFinalL)
            }
            index += 1
        }
        // "dʒ", "tʃ" gibi çok karakterli ses birimleri Piper'da tek tek karakter olarak geçer
        return out.flatMap { phone -> phone.map { it.toString() } }
    }

    private fun consonant(word: String, index: Int, char: Char, prev: String, nxt: String, thinFinalL: Boolean): String =
        when (char) {
            'l' -> if (thinFinalL && index >= word.length - 2) {
                "l"
            } else if (neighbourVowel(word, index) in FRONT) {
                "l"
            } else {
                "ɫ"
            }
            'r' -> {
                val afterVowel = isVowel(prev) || (prev == "ğ" && index >= 2 && isVowel(word[index - 2]))
                if (afterVowel && isVowel(nxt)) "ɾ" else "r"
            }
            'g' -> if (neighbourVowel(word, index) in FRONT) "ɟ" else "ɡ"
            'k' -> if (nxt == "â" || (word.endsWith("ken") && index == word.length - 3 && pyIn(prev, "ry"))) "c" else "k"
            else -> CONSONANTS.getValue(char)
        }

    private fun wordPhonemesList(words: List<String>, stress: Boolean): List<String> {
        val out = mutableListOf<String>()
        words.forEachIndexed { position, word ->
            if (position > 0) out += " "
            out += wordPhonemes(word, stress)
        }
        return out
    }

    // =========================================================================
    // Cümle → ses birimleri
    // =========================================================================

    private const val PUNCTUATION = ",.!?;:-()\"'"
    private val TOKEN = Regex("[a-zçğıöşüâîûqwx]+(?:'[a-zçğıöşüâîû]+)?|[,.!?;:()\"-]")

    /** Metni (okunacak biçime getirerek) Piper'ın beklediği ses birimi listesine çevirir. */
    fun phonemize(text: String): List<String> {
        val tokens = TOKEN.findAll(lowerTr(normalize(text))).map { it.value }
        val out = mutableListOf<String>()
        for (original in tokens) {
            var token = original
            if (token.length == 1 && token[0] in PUNCTUATION) {
                if (token == "-" && (out.isEmpty() || out.last() == " ")) token = ","
                while (out.isNotEmpty() && out.last() == " ") out.removeAt(out.size - 1)
                out += token
                out += " "
                continue
            }
            out += wordPhonemes(token)
            out += " "
        }
        while (out.isNotEmpty() && out.last() == " ") out.removeAt(out.size - 1)
        return out
    }

    /** Piper kodlaması: ^ _ (ses birimi _)* $ */
    fun phonemeIds(phonemes: List<String>, idMap: Map<String, List<Long>>): LongArray {
        val pad = idMap.getValue("_")
        val ids = ArrayList<Long>()
        ids += idMap.getValue("^")
        ids += pad
        for (phoneme in phonemes) {
            val id = idMap[phoneme] ?: continue
            ids += id
            ids += pad
        }
        ids += idMap.getValue("$")
        return ids.toLongArray()
    }

    // =========================================================================
    // Cümle bölme
    // =========================================================================

    private val BLOCK = Regex("[^\\n\u2029\u2028]+")
    private val SENTENCE_END = uRegex(
        "(?<=[.!?\u2026])[\"'\u201d\u2019)\\]]*\\s+(?=[\"'\u201c\u2018(\\[]?[A-ZÇĞİÖŞÜÂÎÛ0-9])",
    )
    private val NO_BREAK = setOf(
        "dr", "prof", "doç", "yrd", "av", "sn", "vb", "vs", "bkz", "örn", "no", "mah", "cad", "sok", "apt", "tel",
        "ltd", "şti", "s", "yy",
    )
    private val INITIAL = Regex("[A-ZÇĞİÖŞÜ]\\.")
    const val MAX_CHARS = 220

    /**
     * Metni sesli okuma parçalarına böler: başlangıç/bitiş aralıkları.
     * Not: Python kod noktası sayar, burada UTF-16 birimi; fark yalnız emoji gibi BMP dışı karakterli
     * çok uzun cümlelerde bölme yerini etkiler.
     */
    fun sentences(text: String): List<IntRange> {
        val spans = mutableListOf<Pair<Int, Int>>()
        for (block in BLOCK.findAll(text)) {
            val blockStart = block.range.first
            var last = blockStart
            for (match in SENTENCE_END.findAll(block.value)) {
                val cut = blockStart + match.range.first
                val piece = text.substring(last, cut)
                val word = if (pySplit(piece).isNotEmpty()) pySplit(piece).last() else ""
                if (word.trimEnd('.').lowercase(Locale.ROOT) in NO_BREAK || INITIAL.matches(word)) continue
                spans += last to cut
                last = blockStart + match.range.last + 1
            }
            spans += last to block.range.last + 1
        }
        val result = mutableListOf<IntRange>()
        for ((spanStart, end) in spans) {
            var start = spanStart
            while (end - start > MAX_CHARS) {
                val chunk = text.substring(start, start + MAX_CHARS)
                var cut = listOf(", ", "; ", ": ").maxOf { chunk.lastIndexOf(it) }
                if (cut <= 0) cut = chunk.lastIndexOf(" ")
                if (cut <= 0) break
                result += start until start + cut + 1
                start += cut + 1
                while (start < end && text[start] == ' ') start += 1
            }
            if (pyStrip(text.substring(start, end)).isNotEmpty()) result += start until end
        }
        return result
    }

    // Python'un str.isspace() tanımı (Kotlin'in isWhitespace'inden biraz farklı)
    private fun isPySpace(c: Char): Boolean =
        c in '\t'..'\r' || c in '\u001c'..'\u001f' || c == ' ' || c == '\u0085' || c == '\u00a0' ||
            c == '\u1680' || c in '\u2000'..'\u200a' || c == '\u2028' || c == '\u2029' || c == '\u202f' ||
            c == '\u205f' || c == '\u3000'

    private fun pyStrip(s: String): String = s.trim(::isPySpace)

    private fun pySplit(s: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        for (c in s) {
            if (isPySpace(c)) {
                if (current.isNotEmpty()) parts += current.toString().also { current.clear() }
            } else {
                current.append(c)
            }
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts
    }
}
