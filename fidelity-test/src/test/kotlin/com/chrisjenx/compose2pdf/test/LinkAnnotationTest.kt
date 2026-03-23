package com.chrisjenx.compose2pdf.test

import androidx.compose.ui.unit.Density
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.RenderMode
import com.chrisjenx.compose2pdf.renderToPdf
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import kotlin.test.Test
import kotlin.test.assertTrue

class LinkAnnotationTest {

    private val config = PdfPageConfig.A4
    private val density = Density(2f)

    @Test
    fun `PdfLink annotations are present in vector PDF`() {
        val pdfBytes = renderToPdf(config = config, density = density, mode = RenderMode.VECTOR) {
            PdfLinkFixture()
        }

        Loader.loadPDF(pdfBytes).use { doc ->
            val page = doc.getPage(0)
            val annotations = page.annotations.filterIsInstance<PDAnnotationLink>()
            val uris = annotations.mapNotNull { link ->
                val action = link.action
                if (action is org.apache.pdfbox.pdmodel.interactive.action.PDActionURI) {
                    action.uri
                } else null
            }

            val expectedUris = listOf(
                "https://example.com",
                "https://github.com",
                "https://a.com",
                "https://b.com",
                "https://c.com",
                "https://large-area.com",
            )
            for (expected in expectedUris) {
                assertTrue(
                    uris.contains(expected),
                    "Expected link annotation for $expected, found: $uris",
                )
            }

            // Verify all link rectangles have positive dimensions
            for (link in annotations) {
                val rect = link.rectangle
                assertTrue(rect.width > 0, "Link rectangle width must be positive: $rect")
                assertTrue(rect.height > 0, "Link rectangle height must be positive: $rect")
            }
        }
    }
}
