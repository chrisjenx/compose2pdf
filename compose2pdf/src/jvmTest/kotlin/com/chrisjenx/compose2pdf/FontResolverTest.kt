package com.chrisjenx.compose2pdf

import com.chrisjenx.compose2pdf.internal.FontResolver
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FontResolverTest {

    @Test
    fun `resolve with null family returns standard font`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), null, null, null)
            assertIs<PDType1Font>(font, "Null family should fall back to standard font")
        }
    }

    @Test
    fun `resolve with empty family returns standard font`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "", null, null)
            assertIs<PDType1Font>(font)
        }
    }

    @Test
    fun `resolve with sans-serif returns Helvetica`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "sans-serif", null, null)
            assertIs<PDType1Font>(font)
            assertTrue(font.name.contains("Helvetica", ignoreCase = true))
        }
    }

    @Test
    fun `resolve with serif returns Times`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "serif", null, null)
            assertIs<PDType1Font>(font)
            assertTrue(font.name.contains("Times", ignoreCase = true))
        }
    }

    @Test
    fun `resolve with monospace returns Courier`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "monospace", null, null)
            assertIs<PDType1Font>(font)
            assertTrue(font.name.contains("Courier", ignoreCase = true))
        }
    }

    @Test
    fun `resolve bold weight selects bold variant`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "sans-serif", "bold", null)
            assertIs<PDType1Font>(font)
            assertTrue(font.name.contains("Bold", ignoreCase = true))
        }
    }

    @Test
    fun `resolve numeric weight 700 selects bold`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "sans-serif", "700", null)
            assertIs<PDType1Font>(font)
            assertTrue(font.name.contains("Bold", ignoreCase = true))
        }
    }

    @Test
    fun `resolve italic style selects italic variant`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "serif", null, "italic")
            assertIs<PDType1Font>(font)
            assertTrue(font.name.contains("Italic", ignoreCase = true))
        }
    }

    @Test
    fun `resolve oblique style selects italic variant`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "sans-serif", null, "oblique")
            assertIs<PDType1Font>(font)
            assertTrue(font.name.contains("Oblique", ignoreCase = true))
        }
    }

    @Test
    fun `resolve bold italic selects bold-italic variant`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "sans-serif", "bold", "italic")
            assertIs<PDType1Font>(font)
            assertTrue(font.name.contains("Bold", ignoreCase = true))
        }
    }

    @Test
    fun `resolve Inter family returns embedded font`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "Inter", null, null)
            assertIs<PDType0Font>(font, "Bundled Inter should be embedded as PDType0Font")
        }
    }

    @Test
    fun `resolve Inter bold returns embedded font`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "Inter", "bold", null)
            assertIs<PDType0Font>(font)
        }
    }

    @Test
    fun `resolve Inter italic returns embedded font`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "Inter", null, "italic")
            assertIs<PDType0Font>(font)
        }
    }

    @Test
    fun `resolve Inter bold italic returns embedded font`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "Inter", "bold", "italic")
            assertIs<PDType0Font>(font)
        }
    }

    @Test
    fun `font cache returns same instance on second resolve`() {
        PDDocument().use { doc ->
            val cache = mutableMapOf<String, org.apache.pdfbox.pdmodel.font.PDFont>()
            val first = FontResolver.resolve(doc, cache, "Inter", null, null)
            val second = FontResolver.resolve(doc, cache, "Inter", null, null)
            assertSame(first, second, "Cache should return same font instance")
        }
    }

    @Test
    fun `comma-separated family list falls through to standard`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "'UnknownFont', sans-serif", null, null)
            // Should fall through UnknownFont (not found) to sans-serif → Helvetica
            assertIs<PDType1Font>(font)
        }
    }

    @Test
    fun `quoted family names are parsed correctly`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "'Inter', sans-serif", null, null)
            // Inter is bundled, should be found
            assertIs<PDType0Font>(font)
        }
    }
}
