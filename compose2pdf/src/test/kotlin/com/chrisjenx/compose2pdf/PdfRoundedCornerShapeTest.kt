package com.chrisjenx.compose2pdf

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PdfRoundedCornerShapeTest {

    private val density = Density(2f)
    private val size = Size(200f, 100f)

    @Test
    fun `uniform corners produce Rounded outline`() {
        val shape = PdfRoundedCornerShape(12.dp, 12.dp, 12.dp, 12.dp)
        val outline = shape.createOutline(size, LayoutDirection.Ltr, density)
        assertIs<Outline.Rounded>(outline)
    }

    @Test
    fun `non-uniform corners produce Generic path outline`() {
        val shape = PdfRoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp)
        val outline = shape.createOutline(size, LayoutDirection.Ltr, density)
        assertIs<Outline.Generic>(outline)
    }

    @Test
    fun `zero corners produce Rectangle outline`() {
        val shape = PdfRoundedCornerShape(0.dp, 0.dp, 0.dp, 0.dp)
        val outline = shape.createOutline(size, LayoutDirection.Ltr, density)
        assertIs<Outline.Rectangle>(outline)
    }

    @Test
    fun `single rounded corner produces Generic outline`() {
        val shape = PdfRoundedCornerShape(topStart = 16.dp)
        val outline = shape.createOutline(size, LayoutDirection.Ltr, density)
        assertIs<Outline.Generic>(outline)
    }

    @Test
    fun `asPdfSafe on standard RoundedCornerShape with non-uniform corners`() {
        val shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp).asPdfSafe()
        val outline = shape.createOutline(size, LayoutDirection.Ltr, density)
        assertIs<Outline.Generic>(outline)
    }

    @Test
    fun `asPdfSafe on already-safe shape does not double-wrap`() {
        val shape = PdfRoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
        val wrapped = shape.asPdfSafe()
        // Both should produce identical outlines (no double wrapping)
        val outline1 = shape.createOutline(size, LayoutDirection.Ltr, density)
        val outline2 = wrapped.createOutline(size, LayoutDirection.Ltr, density)
        assertIs<Outline.Generic>(outline1)
        assertIs<Outline.Generic>(outline2)
    }

    @Test
    fun `Generic outline path is closed`() {
        val shape = PdfRoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp)
        val outline = shape.createOutline(size, LayoutDirection.Ltr, density)
        assertIs<Outline.Generic>(outline)
        // Path should have non-empty bounds (it's a closed shape)
        val bounds = outline.path.getBounds()
        assertTrue(bounds.width > 0f, "Path width should be positive")
        assertTrue(bounds.height > 0f, "Path height should be positive")
    }

    @Test
    fun `RTL layout direction still works`() {
        val shape = PdfRoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp)
        val outline = shape.createOutline(size, LayoutDirection.Rtl, density)
        // Non-uniform corners should still produce Generic
        assertIs<Outline.Generic>(outline)
    }
}
