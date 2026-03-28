package com.chrisjenx.compose2pdf

import com.chrisjenx.compose2pdf.internal.SvgColor
import com.chrisjenx.compose2pdf.internal.SvgColorParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SvgColorParserTest {

    private fun assertColor(r: Float, g: Float, b: Float, input: String) {
        val color = SvgColorParser.parse(input)
        assertNotNull(color, "Expected color for '$input' but got null")
        assertEquals(r, color.r, 0.01f, "Red mismatch for '$input'")
        assertEquals(g, color.g, 0.01f, "Green mismatch for '$input'")
        assertEquals(b, color.b, 0.01f, "Blue mismatch for '$input'")
    }

    // --- Hex colors ---

    @Test
    fun `parse 6-digit hex`() {
        assertColor(1f, 0f, 0f, "#FF0000")
        assertColor(0f, 1f, 0f, "#00FF00")
        assertColor(0f, 0f, 1f, "#0000FF")
    }

    @Test
    fun `parse 3-digit hex`() {
        assertColor(1f, 0f, 0f, "#F00")
        assertColor(0f, 1f, 0f, "#0F0")
        assertColor(0f, 0f, 1f, "#00F")
    }

    @Test
    fun `parse 8-digit hex ignores alpha`() {
        assertColor(1f, 0f, 0f, "#FF000080")
    }

    @Test
    fun `parse 4-digit hex ignores alpha`() {
        assertColor(1f, 0f, 0f, "#F008")
    }

    @Test
    fun `hex is case insensitive`() {
        assertColor(1f, 0f, 0f, "#ff0000")
        assertColor(1f, 0f, 0f, "#FF0000")
        assertColor(1f, 0f, 0f, "#Ff0000")
    }

    @Test
    fun `invalid hex returns null`() {
        assertNull(SvgColorParser.parse("#GG0000"))
        assertNull(SvgColorParser.parse("#12"))
        assertNull(SvgColorParser.parse("#1"))
        assertNull(SvgColorParser.parse("#"))
        assertNull(SvgColorParser.parse("#1234567890"))
    }

    // --- rgb() function ---

    @Test
    fun `parse rgb with integers`() {
        assertColor(1f, 0f, 0f, "rgb(255, 0, 0)")
        assertColor(0f, 0.502f, 0f, "rgb(0, 128, 0)")
    }

    @Test
    fun `parse rgb with percentages`() {
        assertColor(1f, 0.5f, 0f, "rgb(100%, 50%, 0%)")
    }

    @Test
    fun `parse rgb with floats`() {
        assertColor(1.0f, 0.5f, 0.0f, "rgb(1.0, 0.5, 0.0)")
    }

    @Test
    fun `parse rgb space separated`() {
        assertColor(1f, 0f, 0f, "rgb(255 0 0)")
    }

    @Test
    fun `parse rgb with slash alpha`() {
        // Alpha is ignored but parsing should succeed
        assertColor(1f, 0f, 0f, "rgb(255 0 0 / 0.5)")
    }

    @Test
    fun `parse rgba function`() {
        assertColor(1f, 0f, 0f, "rgba(255, 0, 0, 0.5)")
    }

    @Test
    fun `rgb with too few parts returns null`() {
        assertNull(SvgColorParser.parse("rgb(255, 0)"))
        assertNull(SvgColorParser.parse("rgb(255)"))
        assertNull(SvgColorParser.parse("rgb()"))
    }

    @Test
    fun `rgb with invalid components returns null`() {
        assertNull(SvgColorParser.parse("rgb(abc, 0, 0)"))
        assertNull(SvgColorParser.parse("rgb(, , )"))
    }

    // --- Named colors ---

    @Test
    fun `parse named colors`() {
        assertColor(0f, 0f, 0f, "black")
        assertColor(1f, 1f, 1f, "white")
        assertColor(1f, 0f, 0f, "red")
        assertColor(0f, 0f, 1f, "blue")
        assertColor(0f, 0.502f, 0f, "green")
        assertColor(1f, 1f, 0f, "yellow")
        assertColor(0f, 1f, 1f, "cyan")
        assertColor(0f, 1f, 1f, "aqua")
        assertColor(1f, 0f, 1f, "magenta")
        assertColor(1f, 0f, 1f, "fuchsia")
        assertColor(0.502f, 0.502f, 0.502f, "gray")
        assertColor(0.502f, 0.502f, 0.502f, "grey")
        assertColor(1f, 0.647f, 0f, "orange")
        assertColor(0.502f, 0f, 0.502f, "purple")
    }

    @Test
    fun `named colors are case insensitive`() {
        assertColor(1f, 0f, 0f, "Red")
        assertColor(1f, 0f, 0f, "RED")
    }

    @Test
    fun `unknown named color returns null`() {
        assertNull(SvgColorParser.parse("chartreuse"))
        assertNull(SvgColorParser.parse("hotpink"))
    }

    // --- Edge cases ---

    @Test
    fun `empty string returns null`() {
        assertNull(SvgColorParser.parse(""))
    }

    @Test
    fun `whitespace is trimmed`() {
        assertColor(1f, 0f, 0f, "  #FF0000  ")
        assertColor(1f, 0f, 0f, "  red  ")
    }

    @Test
    fun `none returns null`() {
        assertNull(SvgColorParser.parse("none"))
    }

    @Test
    fun `transparent returns null`() {
        assertNull(SvgColorParser.parse("transparent"))
    }

    @Test
    fun `currentColor returns null`() {
        assertNull(SvgColorParser.parse("currentColor"))
    }

    @Test
    fun `mid-range hex values`() {
        // #808080 = 128/255 ≈ 0.502
        assertColor(0.502f, 0.502f, 0.502f, "#808080")
    }

    @Test
    fun `black hex`() {
        assertColor(0f, 0f, 0f, "#000000")
        assertColor(0f, 0f, 0f, "#000")
    }

    @Test
    fun `white hex`() {
        assertColor(1f, 1f, 1f, "#FFFFFF")
        assertColor(1f, 1f, 1f, "#FFF")
    }
}
