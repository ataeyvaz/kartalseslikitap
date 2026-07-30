package com.kartal.seslikitap.domain.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadGeometryTest {

    @Test
    fun `karisik siradaki noktalar saat yonunde siralanir`() {
        val scrambled = listOf(
            PagePoint(100.0, 300.0), // sol-alt
            PagePoint(300.0, 100.0), // sağ-üst
            PagePoint(100.0, 100.0), // sol-üst
            PagePoint(300.0, 300.0), // sağ-alt
        )

        val quad = QuadGeometry.order(scrambled)

        assertEquals(PagePoint(100.0, 100.0), quad.topLeft)
        assertEquals(PagePoint(300.0, 100.0), quad.topRight)
        assertEquals(PagePoint(300.0, 300.0), quad.bottomRight)
        assertEquals(PagePoint(100.0, 300.0), quad.bottomLeft)
    }

    @Test
    fun `dortgen alani dogru hesaplanir`() {
        val quad = rectangle(width = 200.0, height = 100.0)
        assertEquals(20_000.0, QuadGeometry.area(quad), 0.001)
    }

    @Test
    fun `hedef boyut uzun kenarlardan alinir`() {
        // Perspektif nedeniyle alt kenar üstten kısa; hedef genişlik uzun olanı almalı.
        val quad = PageQuad(
            topLeft = PagePoint(0.0, 0.0),
            topRight = PagePoint(400.0, 0.0),
            bottomRight = PagePoint(350.0, 300.0),
            bottomLeft = PagePoint(50.0, 300.0),
        )

        val (width, height) = QuadGeometry.targetSize(quad)

        assertEquals(400, width)
        assertEquals(304, height)
    }

    @Test
    fun `sayfayi kaplayan dortgen makul kabul edilir`() {
        val quad = rectangle(width = 900.0, height = 1200.0)
        assertTrue(QuadGeometry.isPlausiblePage(quad, imageWidth = 1000, imageHeight = 1400))
    }

    @Test
    fun `cok kucuk dortgen reddedilir`() {
        // Masadaki bir kart veya gölge: kadrajın yüzde birkaçı.
        val quad = rectangle(width = 150.0, height = 150.0)
        assertFalse(QuadGeometry.isPlausiblePage(quad, imageWidth = 1000, imageHeight = 1400))
    }

    @Test
    fun `asiri carpik dortgen reddedilir`() {
        val skewed = PageQuad(
            topLeft = PagePoint(0.0, 0.0),
            topRight = PagePoint(1000.0, 0.0),
            bottomRight = PagePoint(200.0, 1200.0),
            bottomLeft = PagePoint(0.0, 700.0),
        )
        assertFalse(QuadGeometry.isPlausiblePage(skewed, imageWidth = 1000, imageHeight = 1400))
    }

    @Test
    fun `hafif egik cekim kabul edilir`() {
        // Elde çekimde tipik olan birkaç derecelik eğiklik kırpmayı engellememeli.
        val slightlyTilted = PageQuad(
            topLeft = PagePoint(40.0, 30.0),
            topRight = PagePoint(940.0, 70.0),
            bottomRight = PagePoint(910.0, 1300.0),
            bottomLeft = PagePoint(20.0, 1260.0),
        )
        assertTrue(QuadGeometry.isPlausiblePage(slightlyTilted, imageWidth = 1000, imageHeight = 1400))
    }

    @Test
    fun `dort noktadan az girdi reddedilir`() {
        val error = runCatching {
            QuadGeometry.order(listOf(PagePoint(0.0, 0.0), PagePoint(1.0, 1.0)))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    private fun rectangle(width: Double, height: Double) = PageQuad(
        topLeft = PagePoint(0.0, 0.0),
        topRight = PagePoint(width, 0.0),
        bottomRight = PagePoint(width, height),
        bottomLeft = PagePoint(0.0, height),
    )
}
