package com.chrisjenx.compose2pdf

import com.chrisjenx.compose2pdf.internal.PageLayout
import com.chrisjenx.compose2pdf.internal.SvgToPdfConverter
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SvgConverterPageTest {

    private val svgNs = "http://www.w3.org/2000/svg"

    private fun rectSvg(w: Int, h: Int): String =
        """<svg xmlns="$svgNs" width="$w" height="$h"><rect x="0" y="0" width="$w" height="$h" fill="#FF0000"/></svg>"""

    @Test
    fun `addAutoPages returns emitted page count`() {
        PDDocument().use { doc ->
            val layout = PageLayout.full(100f, 100f)
            val count = SvgToPdfConverter.addAutoPages(
                doc, rectSvg(100, 250), layout,
                totalContentHeightPt = 250f, density = 1f, maxPages = 100,
                fontCache = mutableMapOf<String, PDFont>(), imageCache = mutableMapOf<String, PDImageXObject>(),
            )
            assertEquals(3, count)
            assertEquals(3, doc.numberOfPages)
        }
    }

    @Test
    fun `addAutoPages returns clamped count when truncated`() {
        PDDocument().use { doc ->
            val layout = PageLayout.full(100f, 100f)
            val count = SvgToPdfConverter.addAutoPages(
                doc, rectSvg(100, 500), layout,
                totalContentHeightPt = 500f, density = 1f, maxPages = 2,
                fontCache = mutableMapOf<String, PDFont>(), imageCache = mutableMapOf<String, PDImageXObject>(),
            )
            assertEquals(2, count)
            assertEquals(2, doc.numberOfPages)
        }
    }

    @Test
    fun `drawSvgOnPage appends to an existing page instead of adding one`() {
        PDDocument().use { doc ->
            val pageLayout = PageLayout.full(200f, 300f)
            SvgToPdfConverter.addPage(doc, rectSvg(100, 100), pageLayout, 1f)
            val page = doc.getPage(0)
            val before = page.contents.readBytes().size

            // Stamp a 40pt band 10pt from the top of the same page
            val bandLayout = PageLayout(
                pageWidthPt = 200f, pageHeightPt = 300f,
                contentWidthPt = 180f, contentHeightPt = 40f,
                marginLeftPt = 10f, marginTopPt = 10f,
            )
            SvgToPdfConverter.drawSvgOnPage(doc, page, rectSvg(180, 40), bandLayout, 1f)

            assertEquals(1, doc.numberOfPages, "must not add a new page")
            assertTrue(page.contents.readBytes().size > before, "page content stream must grow")
        }
    }
}
