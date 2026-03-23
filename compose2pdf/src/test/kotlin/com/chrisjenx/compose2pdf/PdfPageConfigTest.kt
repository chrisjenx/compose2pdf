package com.chrisjenx.compose2pdf

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `Normal margins preset values`() {
        val m = PdfMargins.Normal
        assertEquals(24.dp, m.top)
        assertEquals(24.dp, m.bottom)
        assertEquals(24.dp, m.left)
        assertEquals(24.dp, m.right)
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
    fun `A4 with Normal margins content area`() {
        val config = PdfPageConfig.A4.copy(margins = PdfMargins.Normal)
        assertEquals(595.dp - 48.dp, config.contentWidth)
        assertEquals(842.dp - 48.dp, config.contentHeight)
    }
}
