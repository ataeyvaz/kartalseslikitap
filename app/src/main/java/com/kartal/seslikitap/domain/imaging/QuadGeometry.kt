package com.kartal.seslikitap.domain.imaging

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Dörtgen geometrisi: sıralama, doğrulama ve hedef boyut hesabı.
 *
 * Bilinçli olarak saf Kotlin: OpenCV'ye bağlı olmadığı için JVM birim testleriyle
 * doğrulanabilir ve ileride farklı bir görüntü işleme motoruna geçilse de aynen kullanılır.
 */
object QuadGeometry {

    /** Kırpmanın kabul edilmesi için dörtgenin kaplaması gereken minimum alan oranı. */
    const val MIN_AREA_RATIO = 0.25

    /** Köşelerin dik açıdan kabul edilebilir maksimum sapması (derece). */
    const val MAX_ANGLE_DEVIATION_DEGREES = 40.0

    /**
     * Rastgele sıradaki 4 noktayı sol-üst, sağ-üst, sağ-alt, sol-alt sırasına dizer.
     *
     * Toplam (x+y) en küçük olan sol-üst, en büyük olan sağ-alt; fark (x-y) en büyük olan
     * sağ-üst, en küçük olan sol-alt köşedir.
     */
    fun order(points: List<PagePoint>): PageQuad {
        require(points.size == 4) { "Dörtgen için tam olarak 4 nokta gerekir, gelen: ${points.size}" }

        val topLeft = points.minBy { it.x + it.y }
        val bottomRight = points.maxBy { it.x + it.y }
        val remaining = points.filter { it !== topLeft && it !== bottomRight }
        val (topRight, bottomLeft) = if (remaining.size == 2) {
            val first = remaining.maxBy { it.x - it.y }
            val second = remaining.first { it !== first }
            first to second
        } else {
            // Dejenere durum (aynı nokta birden fazla rolde): sıralamayı x-y farkına bırak.
            val sorted = points.sortedByDescending { it.x - it.y }
            sorted.first() to sorted.last()
        }

        return PageQuad(topLeft, topRight, bottomRight, bottomLeft)
    }

    /**
     * Bulunan dörtgen gerçekten bir sayfa mı?
     *
     * Çok küçük alanlar (masadaki bir kart, gölge) ve aşırı çarpık dörtgenler (kenar tespiti
     * yanlış bir şeye kilitlenmiş) reddedilir; bu durumda ham kare OCR'a gider.
     */
    fun isPlausiblePage(quad: PageQuad, imageWidth: Int, imageHeight: Int): Boolean {
        if (imageWidth <= 0 || imageHeight <= 0) return false

        val imageArea = imageWidth.toDouble() * imageHeight.toDouble()
        if (area(quad) / imageArea < MIN_AREA_RATIO) return false

        val (width, height) = targetSize(quad)
        if (width < MIN_SIDE_PX || height < MIN_SIDE_PX) return false

        return cornerAngles(quad).all { abs(it - 90.0) <= MAX_ANGLE_DEVIATION_DEGREES }
    }

    /** Ayakkabı bağı formülüyle dörtgen alanı. */
    fun area(quad: PageQuad): Double {
        val p = quad.points
        var sum = 0.0
        for (i in p.indices) {
            val current = p[i]
            val next = p[(i + 1) % p.size]
            sum += current.x * next.y - next.x * current.y
        }
        return abs(sum) / 2.0
    }

    /**
     * Düzeltilmiş görüntünün hedef boyutu: karşılıklı kenarların uzun olanı alınır,
     * böylece perspektif nedeniyle kısalmış kenar metni sıkıştırmaz.
     */
    fun targetSize(quad: PageQuad): Pair<Int, Int> {
        val topWidth = distance(quad.topLeft, quad.topRight)
        val bottomWidth = distance(quad.bottomLeft, quad.bottomRight)
        val leftHeight = distance(quad.topLeft, quad.bottomLeft)
        val rightHeight = distance(quad.topRight, quad.bottomRight)

        return max(topWidth, bottomWidth).roundToInt() to max(leftHeight, rightHeight).roundToInt()
    }

    fun distance(a: PagePoint, b: PagePoint): Double = hypot(b.x - a.x, b.y - a.y)

    /** Dört köşedeki iç açılar (derece). */
    fun cornerAngles(quad: PageQuad): List<Double> {
        val p = quad.points
        return p.indices.map { i ->
            val previous = p[(i + p.size - 1) % p.size]
            val current = p[i]
            val next = p[(i + 1) % p.size]
            angleBetween(previous, current, next)
        }
    }

    private fun angleBetween(a: PagePoint, vertex: PagePoint, b: PagePoint): Double {
        val v1x = a.x - vertex.x
        val v1y = a.y - vertex.y
        val v2x = b.x - vertex.x
        val v2y = b.y - vertex.y
        val magnitude = hypot(v1x, v1y) * hypot(v2x, v2y)
        if (magnitude == 0.0) return 0.0
        val cos = ((v1x * v2x + v1y * v2y) / magnitude).coerceIn(-1.0, 1.0)
        return Math.toDegrees(kotlin.math.acos(cos))
    }

    private const val MIN_SIDE_PX = 120
}
