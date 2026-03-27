package com.chrisjenx.compose2pdf

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PdfPageConfigTest {

    @Test
    fun `A4 preset has correct dimensions`() {
        val a4 = PdfPageConfig.A4
        assertEquals(595.dp, a4.width)
        assertEquals(842.dp, a4.height)
        assertEquals(PdfMargins.None, a4.margins)
    }

    @Test
    fun `Letter preset has correct dimensions`() {
        val letter = PdfPageConfig.Letter
        assertEquals(612.dp, letter.width)
        assertEquals(792.dp, letter.height)
    }

    @Test
    fun `A3 preset has correct dimensions`() {
        val a3 = PdfPageConfig.A3
        assertEquals(842.dp, a3.width)
        assertEquals(1191.dp, a3.height)
    }

    @Test
    fun `contentWidth subtracts horizontal margins`() {
        val config = PdfPageConfig(
            width = 600.dp,
            height = 800.dp,
            margins = PdfMargins(left = 50.dp, right = 30.dp),
        )
        assertEquals(520.dp, config.contentWidth)
    }

    @Test
    fun `contentHeight subtracts vertical margins`() {
        val config = PdfPageConfig(
            width = 600.dp,
            height = 800.dp,
            margins = PdfMargins(top = 40.dp, bottom = 60.dp),
        )
        assertEquals(700.dp, config.contentHeight)
    }

    @Test
    fun `no margins gives full page as content area`() {
        val config = PdfPageConfig(width = 500.dp, height = 700.dp)
        assertEquals(config.width, config.contentWidth)
        assertEquals(config.height, config.contentHeight)
    }

    @Test
    fun `Narrow margins preset values`() {
        val m = PdfMargins.Narrow
        assertEquals(24.dp, m.top)
        assertEquals(24.dp, m.bottom)
        assertEquals(24.dp, m.left)
        assertEquals(24.dp, m.right)
    }

    @Test
    fun `Normal margins preset values`() {
        val m = PdfMargins.Normal
        assertEquals(72.dp, m.top)
        assertEquals(72.dp, m.bottom)
        assertEquals(72.dp, m.left)
        assertEquals(72.dp, m.right)
    }

    @Test
    fun `None margins are all zero`() {
        val m = PdfMargins.None
        assertEquals(0.dp, m.top)
        assertEquals(0.dp, m.bottom)
        assertEquals(0.dp, m.left)
        assertEquals(0.dp, m.right)
    }

    @Test
    fun `A4 with Narrow margins content area`() {
        val config = PdfPageConfig.A4.copy(margins = PdfMargins.Narrow)
        assertEquals(595.dp - 48.dp, config.contentWidth)
        assertEquals(842.dp - 48.dp, config.contentHeight)
    }

    @Test
    fun `A4WithMargins uses Normal margins`() {
        assertEquals(PdfMargins.Normal, PdfPageConfig.A4WithMargins.margins)
    }

    @Test
    fun `A3WithMargins uses Normal margins`() {
        assertEquals(PdfMargins.Normal, PdfPageConfig.A3WithMargins.margins)
    }

    @Test
    fun `landscape swaps width and height`() {
        val portrait = PdfPageConfig.A4
        val landscape = portrait.landscape()
        assertEquals(portrait.height, landscape.width)
        assertEquals(portrait.width, landscape.height)
    }

    @Test
    fun `landscape rotates margins`() {
        val portrait = PdfPageConfig(
            width = 600.dp,
            height = 800.dp,
            margins = PdfMargins(top = 10.dp, bottom = 20.dp, left = 30.dp, right = 40.dp),
        )
        val landscape = portrait.landscape()
        assertEquals(30.dp, landscape.margins.top)
        assertEquals(40.dp, landscape.margins.bottom)
        assertEquals(10.dp, landscape.margins.left)
        assertEquals(20.dp, landscape.margins.right)
    }

    @Test
    fun `symmetric margins factory`() {
        val m = PdfMargins.symmetric(horizontal = 36.dp, vertical = 24.dp)
        assertEquals(24.dp, m.top)
        assertEquals(24.dp, m.bottom)
        assertEquals(36.dp, m.left)
        assertEquals(36.dp, m.right)
    }

    @Test
    fun `zero width throws`() {
        assertFailsWith<IllegalArgumentException> {
            PdfPageConfig(width = 0.dp, height = 100.dp)
        }
    }

    @Test
    fun `negative height throws`() {
        assertFailsWith<IllegalArgumentException> {
            PdfPageConfig(width = 100.dp, height = (-10).dp)
        }
    }

    @Test
    fun `margins exceeding page size throw`() {
        assertFailsWith<IllegalArgumentException> {
            PdfPageConfig(
                width = 100.dp,
                height = 200.dp,
                margins = PdfMargins(left = 60.dp, right = 60.dp),
            )
        }
    }

    @Test
    fun `negative margins throw`() {
        assertFailsWith<IllegalArgumentException> {
            PdfMargins(left = (-1).dp)
        }
    }
}
