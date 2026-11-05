package com.chrisjenx.compose2pdf

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import java.io.ByteArrayOutputStream
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * CMP 1.13 moved Skiko graphics/text registration out of ServiceLoader into
 * `registerSkikoComposeImplementation()`, which only `ImageComposeScene`/`ComposeContainer` call.
 * The vector path builds a `CanvasLayersComposeScene` directly, so without an explicit
 * registration every render failed with "No Compose UI graphics implementation is registered".
 *
 * Each test clears the (process-global) registration first, so a raster test that ran earlier in
 * the same JVM can't mask the bug. The clear is reflective because CI also compiles this suite
 * against CMP 1.12 (`nextdriver-guard`), where neither function exists and registration is
 * implicit — there these tests simply assert every render path still works.
 */
class SkikoRegistrationTest {

    @BeforeTest
    fun clearRegistration() {
        val runtime = try {
            Class.forName("androidx.compose.ui.platform.ComposeUiSkikoRuntime_nonAndroidKt")
        } catch (e: ClassNotFoundException) {
            return // CMP < 1.13: nothing to clear
        }
        runtime.getMethod("clearSkikoComposeImplementation").invoke(null)
    }

    @Test
    fun `vector auto-paginated render works on a fresh registry`() =
        assertValidPdf(renderToPdf(mode = RenderMode.VECTOR) { Text("Hello") })

    @Test
    fun `vector single-page render works on a fresh registry`() =
        assertValidPdf(renderToPdf(mode = RenderMode.VECTOR, pagination = PdfPagination.SINGLE_PAGE) { Text("Hello") })

    @Test
    fun `raster auto-paginated render works on a fresh registry`() =
        assertValidPdf(renderToPdf(mode = RenderMode.RASTER) { Text("Hello") })

    @Test
    fun `raster single-page render works on a fresh registry`() =
        assertValidPdf(renderToPdf(mode = RenderMode.RASTER, pagination = PdfPagination.SINGLE_PAGE) { Text("Hello") })

    @Test
    fun `vector render with header and footer works on a fresh registry`() =
        assertValidPdf(
            renderToPdf(
                mode = RenderMode.VECTOR,
                header = { Text("Header") },
                footer = { info -> Text("Page ${info.pageNumber}") },
            ) { Column { Text("Body") } },
        )

    @Test
    fun `raster render with header and footer works on a fresh registry`() =
        assertValidPdf(
            renderToPdf(
                mode = RenderMode.RASTER,
                header = { Text("Header") },
                footer = { info -> Text("Page ${info.pageNumber}") },
            ) { Column { Text("Body") } },
        )

    @Test
    fun `manual multi-page vector render works on a fresh registry`() =
        assertValidPdf(renderToPdf(pages = 2, mode = RenderMode.VECTOR) { i -> Text("Page $i") })

    @Test
    fun `manual multi-page raster render works on a fresh registry`() =
        assertValidPdf(renderToPdf(pages = 2, mode = RenderMode.RASTER) { i -> Text("Page $i") })

    @Test
    fun `streaming vector render works on a fresh registry`() {
        val out = ByteArrayOutputStream()
        renderToPdf(out, mode = RenderMode.VECTOR) { Text("Hello") }
        assertValidPdf(out.toByteArray())
    }
}
