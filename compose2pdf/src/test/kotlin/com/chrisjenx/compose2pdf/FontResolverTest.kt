package com.chrisjenx.compose2pdf

import com.chrisjenx.compose2pdf.internal.FontResolver
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FontResolverTest {

    /**
     * Generic/default families now resolve through Skia's font manager to the actual
     * platform font (embedded PDType0) wherever the platform provides one — matching what
     * Skia shaped the text with. The standard-14 fonts remain the terminal fallback, so
     * either type is acceptable; a substituted standard font must never be something else.
     */
    private fun assertResolvedFont(font: PDFont) {
        assertTrue(
            font is PDType0Font && font.isEmbedded || font is PDType1Font,
            "expected an embedded platform font or a standard-14 fallback, got ${font::class.simpleName} '${font.name}'",
        )
    }

    @Test
    fun `resolve with null family returns a usable font`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), null, null, null)
            assertResolvedFont(font)
        }
    }

    @Test
    fun `resolve with empty family returns a usable font`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "", null, null)
            assertResolvedFont(font)
        }
    }

    @Test
    fun `resolve with sans-serif returns a usable font`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "sans-serif", null, null)
            assertResolvedFont(font)
        }
    }

    @Test
    fun `resolve with serif returns a usable font`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "serif", null, null)
            assertResolvedFont(font)
        }
    }

    // Unresolvable family names exercise the terminal standard-14 fallback and its
    // family classification (mono → Courier, serif → Times, otherwise Helvetica).

    @Test
    fun `unresolvable mono family falls back to Courier`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "NoSuchXyzMono", null, null)
            assertIs<PDType1Font>(font)
            assertTrue(font.name.contains("Courier", ignoreCase = true))
        }
    }

    @Test
    fun `unresolvable serif family falls back to Times`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "NoSuchXyzSerif", null, null)
            assertIs<PDType1Font>(font)
            assertTrue(font.name.contains("Times", ignoreCase = true))
        }
    }

    @Test
    fun `unresolvable family bold weight selects bold variant`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "NoSuchXyzFont", "bold", null)
            assertIs<PDType1Font>(font)
            assertTrue(font.name.contains("Bold", ignoreCase = true))
        }
    }

    @Test
    fun `unresolvable family numeric weight 700 selects bold`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "NoSuchXyzFont", "700", null)
            assertIs<PDType1Font>(font)
            assertTrue(font.name.contains("Bold", ignoreCase = true))
        }
    }

    @Test
    fun `unresolvable family italic style selects italic variant`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "NoSuchXyzSerif", null, "italic")
            assertIs<PDType1Font>(font)
            assertTrue(font.name.contains("Italic", ignoreCase = true))
        }
    }

    @Test
    fun `unresolvable family oblique style selects italic variant`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "NoSuchXyzFont", null, "oblique")
            assertIs<PDType1Font>(font)
            assertTrue(font.name.contains("Oblique", ignoreCase = true))
        }
    }

    @Test
    fun `unresolvable family bold italic selects bold-italic variant`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "NoSuchXyzFont", "bold", "italic")
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
            val cache = mutableMapOf<String, PDFont>()
            val first = FontResolver.resolve(doc, cache, "Inter", null, null)
            val second = FontResolver.resolve(doc, cache, "Inter", null, null)
            assertSame(first, second, "Cache should return same font instance")
        }
    }

    @Test
    fun `unknown family in comma-separated list falls through`() {
        PDDocument().use { doc ->
            val font = FontResolver.resolve(doc, mutableMapOf(), "'NoSuchXyzFont', sans-serif", null, null)
            // NoSuchXyzFont can't resolve anywhere; sans-serif resolves to the platform
            // default (embedded) or standard Helvetica
            assertResolvedFont(font)
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
