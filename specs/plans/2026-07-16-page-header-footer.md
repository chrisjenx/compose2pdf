# Per-Page Header/Footer Slots Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optional per-page `header`/`footer` composable slots on the auto-pagination `renderToPdf` overloads, receiving `PdfPageInfo(pageIndex, pageCount)`, with a uniform measured band reserved on every page.

**Architecture:** Measure slot heights once up front, shrink the body's content area, paginate/slice the body exactly as today (adjusted `PageLayout`), then stamp per-page slot renders into the reserved top/bottom bands. When both slots are `null`, dispatch to today's code paths untouched. A confirmed raster last-slice stretch bug is fixed first because the band math builds on the same code.

**Tech Stack:** Kotlin 2.3 JVM, Compose Multiplatform Desktop, Apache PDFBox 3, Gradle. Spec: `specs/2026-07-16-page-header-footer-design.md`.

## Global Constraints

- **Null-slot fast path**: when `header == null && footer == null`, execution must take exactly today's code path (guarded dispatch at the top of `PdfRenderer.renderSinglePage`). Task 2 pins this with a golden.
- **Public API additions are ONLY**: `PdfPageInfo` (plain class, NOT a data class) and the `header`/`footer` parameters on the two auto-pagination `renderToPdf` overloads. Everything else stays `internal`.
- **Rounding discipline**: band heights are measured in px; pt values derive from px (`pt = px / density.density`); the body's effective px height derives from the effective config via `(contentHeight.value * density.density).toInt()` — the same formula the public `PaginatedColumn` uses — so pagination and slicing agree exactly.
- Slot measurement sentinel is `PdfPageInfo(pageIndex = 0, pageCount = 2)` (so `if (pageCount > 1)` footers measure their real height).
- Package `com.chrisjenx.compose2pdf` (public) / `com.chrisjenx.compose2pdf.internal` (implementation). Tests use kotlin-test. Existing code style: PDFBox types fully qualified inline in `PdfRenderer` (e.g. `org.apache.pdfbox.pdmodel.font.PDFont`) — match it.
- Work on the existing worktree branch `worktree-feature-page-header-footer`; commit after every task.
- Run tests with `./gradlew :compose2pdf:test --tests '<pattern>'`; fidelity with `./gradlew :fidelity-test:test` (add `--rerun-tasks` if Gradle skips them).

## File Structure

| File | Responsibility |
|---|---|
| `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/PdfPageInfo.kt` | **Create** — public page-info value passed to slots |
| `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/Compose2Pdf.kt` | **Modify** — `header`/`footer` params on the 2 auto overloads |
| `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/internal/PdfRenderer.kt` | **Modify** — raster slice fix; slot measurement, guard, effective config, with-slots vector/raster paths, stamping, link offsets |
| `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/internal/SvgToPdfConverter.kt` | **Modify** — `drawSvgOnPage` (stamp onto existing page), `addAutoPages` returns emitted page count |
| `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/RasterSliceTest.kt` | **Create** — Task 1 tests |
| `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/NullSlotRegressionTest.kt` + `compose2pdf/src/test/resources/golden/` | **Create** — Task 2 golden pin |
| `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/PdfPageInfoTest.kt` | **Create** — Task 3 tests |
| `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/SvgConverterPageTest.kt` | **Create** — Task 4 tests |
| `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/HeaderFooterVectorTest.kt` | **Create** — Task 5 tests |
| `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/HeaderFooterRasterTest.kt` | **Create** — Task 6 tests |
| `fidelity-test/src/test/kotlin/com/chrisjenx/compose2pdf/test/HeaderFooterFidelityTest.kt` | **Create** — Task 7 |
| `examples/src/main/kotlin/com/chrisjenx/compose2pdf/examples/13_HeaderFooter.kt` + `Main.kt` | **Create/Modify** — Task 7 |
| `docs/usage/auto-pagination.md`, `docs/usage/index.md`, `README.md`, `CLAUDE.md`, `docs/changelog.md` | **Modify** — Task 8 |

Shared test helper (defined in Task 1, reused by Tasks 5/6 — each test file gets its own private copy to keep files self-contained):

```kotlin
/** Renders page [pageIndex] at 72 DPI (1pt = 1px) and returns the ARGB pixel at the
 * horizontal center of the content area, [frac] of the way down it. */
private fun pixelAt(bytes: ByteArray, pageIndex: Int, frac: Float, config: PdfPageConfig): Int {
    Loader.loadPDF(bytes).use { doc ->
        val img = PDFRenderer(doc).renderImageWithDPI(pageIndex, 72f)
        val x = (config.margins.left.value + config.contentWidth.value / 2).toInt()
        val y = (config.margins.top.value + config.contentHeight.value * frac).toInt()
        return img.getRGB(x, y)
    }
}

private fun Int.isRed() = ((this shr 16) and 0xFF) > 200 && ((this shr 8) and 0xFF) < 80
private fun Int.isBlue() = (this and 0xFF) > 200 && ((this shr 16) and 0xFF) < 80
```

---

### Task 1: Fix raster last-partial-slice stretch bug

Confirmed at runtime (see spec): `renderAutoRaster`'s last partial bitmap slice is drawn into `addBitmapPage`'s fixed full-content-height dest rect and stretched vertically.

**Files:**
- Modify: `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/internal/PdfRenderer.kt` (`addBitmapPage` ~line 311, `renderAutoRaster` ~line 194)
- Test: `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/RasterSliceTest.kt`

**Interfaces:**
- Produces: `addBitmapPage(doc: PDDocument, config: PdfPageConfig, bitmap: BufferedImage, topPt: Float = config.margins.top.value, heightPt: Float = config.contentHeight.value)` — Task 6 passes explicit `topPt`/`heightPt` for band-shifted body slices.

- [ ] **Step 1: Write the failing test**

Create `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/RasterSliceTest.kt`:

```kotlin
package com.chrisjenx.compose2pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RasterSliceTest {

    private val config = PdfPageConfig.A4WithMargins // contentHeight = 698pt

    private fun pixelAt(bytes: ByteArray, pageIndex: Int, frac: Float): Int {
        Loader.loadPDF(bytes).use { doc ->
            val img = PDFRenderer(doc).renderImageWithDPI(pageIndex, 72f)
            val x = (config.margins.left.value + config.contentWidth.value / 2).toInt()
            val y = (config.margins.top.value + config.contentHeight.value * frac).toInt()
            return img.getRGB(x, y)
        }
    }

    private fun Int.isRed() = ((this shr 16) and 0xFF) > 200 && ((this shr 8) and 0xFF) < 80

    @Test
    fun `last partial raster slice is not stretched`() {
        // 1047dp = 1.5 pages: page 2 holds 349dp of content (top half); the rest must stay blank
        val bytes = renderToPdf(config = config, mode = RenderMode.RASTER) {
            Box(Modifier.fillMaxWidth().height(1047.dp).background(Color.Red))
        }
        Loader.loadPDF(bytes).use { doc -> assertEquals(2, doc.numberOfPages) }
        assertTrue(pixelAt(bytes, 1, 0.25f).isRed(), "top half of page 2 should contain content")
        assertFalse(pixelAt(bytes, 1, 0.75f).isRed(), "bottom half of page 2 must be blank, not stretched content")
        assertFalse(pixelAt(bytes, 1, 0.95f).isRed(), "bottom of page 2 must be blank, not stretched content")
    }

    @Test
    fun `full raster slices keep exact content-area geometry`() {
        // 1396dp = exactly 2 full pages; both pages fully covered
        val bytes = renderToPdf(config = config, mode = RenderMode.RASTER) {
            Box(Modifier.fillMaxWidth().height(1396.dp).background(Color.Red))
        }
        for (page in 0..1) {
            assertTrue(pixelAt(bytes, page, 0.05f).isRed(), "page $page top should be red")
            assertTrue(pixelAt(bytes, page, 0.95f).isRed(), "page $page bottom should be red")
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :compose2pdf:test --tests '*RasterSliceTest*'`
Expected: `last partial raster slice is not stretched` FAILS ("bottom half of page 2 must be blank"); the full-slice test PASSES.

- [ ] **Step 3: Implement the fix**

In `PdfRenderer.kt`, replace `addBitmapPage` with a top-aligned, explicit-height version:

```kotlin
    private fun addBitmapPage(
        doc: PDDocument,
        config: PdfPageConfig,
        bitmap: BufferedImage,
        topPt: Float = config.margins.top.value,
        heightPt: Float = config.contentHeight.value,
    ) {
        val mediaBox = PDRectangle(config.width.value, config.height.value)
        val page = PDPage(mediaBox)
        doc.addPage(page)

        val pdImage = LosslessFactory.createFromImage(doc, bitmap)
        val contentStream = PDPageContentStream(doc, page)
        try {
            contentStream.drawImage(
                pdImage,
                config.margins.left.value,
                config.height.value - topPt - heightPt,
                config.contentWidth.value,
                heightPt,
            )
        } finally {
            contentStream.close()
        }
    }
```

(Defaults reproduce today's full-slice geometry exactly: `height - margins.top - contentHeight == margins.bottom`.)

In `renderAutoRaster`, change the slice loop's `addBitmapPage(doc, config, slice)` call to pass a proportional height (exact for full slices):

```kotlin
            val slice = fullBitmap.getSubimage(0, sliceTop, contentWidthPx, sliceHeight)
            addBitmapPage(
                doc, config, slice,
                heightPt = config.contentHeight.value * sliceHeight / contentHeightPx,
            )
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :compose2pdf:test --tests '*RasterSliceTest*'`
Expected: both tests PASS.

- [ ] **Step 5: Run the full unit suite (no regressions)**

Run: `./gradlew :compose2pdf:test`
Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add compose2pdf/src
git commit -m "fix: draw last partial raster slice at proportional height instead of stretching"
```

---

### Task 2: Pin the null-slot render path with golden PDFs

Guards the "byte-identical when slots are null" guarantee for the rest of the branch. Compares decoded page content streams (NOT raw PDF bytes — PDFBox embeds a time-seeded document ID). Fixture is shapes-only (no text) so output is deterministic across platforms.

**Files:**
- Create: `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/NullSlotRegressionTest.kt`
- Create (generated): `compose2pdf/src/test/resources/golden/null-slot-vector.pdf`, `compose2pdf/src/test/resources/golden/null-slot-raster.pdf`

**Interfaces:**
- Consumes: Task 1's fixed raster geometry (goldens MUST be generated after Task 1).
- Produces: a failing signal for any later task that perturbs the null-slot path.

- [ ] **Step 1: Write the test (self-generating golden)**

Create `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/NullSlotRegressionTest.kt`:

```kotlin
package com.chrisjenx.compose2pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.apache.pdfbox.Loader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Pins the header/footer-less render path: with no slots, output must be identical
 * to the baseline captured before the header/footer feature. Compares decoded page
 * content streams because PDFBox writes a time-seeded document ID into every save.
 * Shapes-only fixture: no text, so the streams are platform-deterministic.
 *
 * If this fails after an INTENTIONAL rendering change, delete the golden files and
 * re-run the test twice to regenerate and verify.
 */
class NullSlotRegressionTest {

    private val goldenDir = File("src/test/resources/golden")

    private val fixture: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(400.dp).background(Color(0xFF1565C0)))
        Box(Modifier.fillMaxWidth().height(400.dp).background(Color(0xFFE3F2FD)))
        Box(Modifier.fillMaxWidth().height(1047.dp).background(Color(0xFFB71C1C))) // forces a partial last slice
    }

    private fun check(mode: RenderMode, goldenName: String) {
        val bytes = renderToPdf(config = PdfPageConfig.A4WithMargins, mode = mode) { fixture() }
        val goldenFile = File(goldenDir, goldenName)
        if (!goldenFile.exists()) {
            goldenDir.mkdirs()
            goldenFile.writeBytes(bytes)
            fail("Golden $goldenName was missing — generated it. Commit the file and re-run.")
        }
        Loader.loadPDF(goldenFile.readBytes()).use { golden ->
            Loader.loadPDF(bytes).use { fresh ->
                assertEquals(golden.numberOfPages, fresh.numberOfPages, "$mode page count changed")
                for (i in 0 until golden.numberOfPages) {
                    assertContentEquals(
                        golden.getPage(i).contents.readBytes(),
                        fresh.getPage(i).contents.readBytes(),
                        "$mode page $i content stream differs from pre-slots baseline",
                    )
                }
            }
        }
    }

    @Test
    fun `null-slot vector output matches baseline content streams`() = check(RenderMode.VECTOR, "null-slot-vector.pdf")

    @Test
    fun `null-slot raster output matches baseline content streams`() = check(RenderMode.RASTER, "null-slot-raster.pdf")
}
```

- [ ] **Step 2: Run to generate goldens (expected failure), then run again to verify**

Run: `./gradlew :compose2pdf:test --tests '*NullSlotRegressionTest*'`
Expected: both tests FAIL with "Golden ... was missing — generated it."

Run again: `./gradlew :compose2pdf:test --tests '*NullSlotRegressionTest*' --rerun-tasks`
Expected: both tests PASS.

- [ ] **Step 3: Commit (goldens included)**

```bash
git add compose2pdf/src/test
git commit -m "test: pin null-slot render path with golden content-stream baselines"
```

---

### Task 3: `PdfPageInfo` public class

**Files:**
- Create: `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/PdfPageInfo.kt`
- Test: `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/PdfPageInfoTest.kt`

**Interfaces:**
- Produces: `class PdfPageInfo(val pageIndex: Int, val pageCount: Int)` with `val pageNumber: Int get() = pageIndex + 1`. Tasks 5/6 construct it; the public API (Task 5) exposes it in slot lambdas.

- [ ] **Step 1: Write the failing test**

Create `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/PdfPageInfoTest.kt`:

```kotlin
package com.chrisjenx.compose2pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PdfPageInfoTest {

    @Test
    fun `pageNumber is one-based`() {
        assertEquals(1, PdfPageInfo(pageIndex = 0, pageCount = 3).pageNumber)
        assertEquals(3, PdfPageInfo(pageIndex = 2, pageCount = 3).pageNumber)
    }

    @Test
    fun `rejects invalid values`() {
        assertFailsWith<IllegalArgumentException> { PdfPageInfo(pageIndex = -1, pageCount = 1) }
        assertFailsWith<IllegalArgumentException> { PdfPageInfo(pageIndex = 0, pageCount = 0) }
        assertFailsWith<IllegalArgumentException> { PdfPageInfo(pageIndex = 2, pageCount = 2) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :compose2pdf:test --tests '*PdfPageInfoTest*'`
Expected: COMPILATION FAILURE — `PdfPageInfo` unresolved.

- [ ] **Step 3: Write the implementation**

Create `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/PdfPageInfo.kt`:

```kotlin
package com.chrisjenx.compose2pdf

/**
 * Page information passed to `header`/`footer` slots during [renderToPdf].
 *
 * Deliberately a plain class (not a `data class`) so fields can be added later
 * without breaking binary compatibility.
 *
 * @property pageIndex Zero-based index of the page being rendered.
 * @property pageCount Total number of emitted pages. If auto-pagination truncates
 *   at its page cap, this is the emitted (truncated) count.
 */
class PdfPageInfo(
    val pageIndex: Int,
    val pageCount: Int,
) {
    /** One-based page number, for display: `"Page $pageNumber of $pageCount"`. */
    val pageNumber: Int get() = pageIndex + 1

    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative, was $pageIndex" }
        require(pageCount > 0) { "pageCount must be positive, was $pageCount" }
        require(pageIndex < pageCount) { "pageIndex ($pageIndex) must be < pageCount ($pageCount)" }
    }

    override fun toString(): String = "PdfPageInfo(pageIndex=$pageIndex, pageCount=$pageCount)"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :compose2pdf:test --tests '*PdfPageInfoTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add compose2pdf/src
git commit -m "feat: add public PdfPageInfo for header/footer slots"
```

---

### Task 4: Converter support — `drawSvgOnPage` + `addAutoPages` returns page count

**Files:**
- Modify: `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/internal/SvgToPdfConverter.kt` (`addAutoPages` ~line 63, `renderSvgToContentArea` ~line 91)
- Test: `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/SvgConverterPageTest.kt`

**Interfaces:**
- Consumes: existing `parseSvg`, `PageRenderer`, `CoordinateTransform.contentAreaMatrix`, `PageLayout`.
- Produces:
  - `fun addAutoPages(...): Int` — same params, now returns the emitted (clamped) page count. Task 5 uses it for `PdfPageInfo`.
  - `fun drawSvgOnPage(pdfDoc: PDDocument, page: PDPage, svg: String, layout: PageLayout, density: Float, fontCache: MutableMap<String, PDFont> = mutableMapOf(), imageCache: MutableMap<String, PDImageXObject> = mutableMapOf())` — draws an SVG into the content area defined by `layout` on an EXISTING page (appending to its content stream, clipped to the band). Tasks 5 uses it to stamp slots.

- [ ] **Step 1: Write the failing tests**

Create `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/SvgConverterPageTest.kt`:

```kotlin
package com.chrisjenx.compose2pdf

import com.chrisjenx.compose2pdf.internal.PageLayout
import com.chrisjenx.compose2pdf.internal.SvgToPdfConverter
import org.apache.pdfbox.pdmodel.PDDocument
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
                fontCache = mutableMapOf(), imageCache = mutableMapOf(),
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
                fontCache = mutableMapOf(), imageCache = mutableMapOf(),
            )
            assertEquals(2, count)
            assertEquals(2, doc.numberOfPages)
        }
    }

    @Test
    fun `drawSvgOnPage appends to an existing page instead of adding one`() {
        PDDocument().use { doc ->
            SvgToPdfConverter.addPage(doc, rectSvg(100, 100), PageLayout.full(200f, 300f), 1f)
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :compose2pdf:test --tests '*SvgConverterPageTest*'`
Expected: COMPILATION FAILURE — `drawSvgOnPage` unresolved, and `addAutoPages` returns `Unit` (assertEquals on Int fails to compile).

- [ ] **Step 3: Implement**

In `SvgToPdfConverter.kt`:

(a) Change `addAutoPages` to return the count (KDoc: add `@return the number of pages emitted (clamped to [maxPages])`):

```kotlin
    fun addAutoPages(
        pdfDoc: PDDocument,
        svg: String,
        layout: PageLayout,
        totalContentHeightPt: Float,
        density: Float,
        maxPages: Int,
        fontCache: MutableMap<String, PDFont>,
        imageCache: MutableMap<String, PDImageXObject>,
    ): Int {
        val (svgRoot, defs) = parseSvg(svg)
        val pageCount = kotlin.math.ceil(totalContentHeightPt.toDouble() / layout.contentHeightPt)
            .toInt().coerceIn(1, maxPages)

        for (pageIndex in 0 until pageCount) {
            renderSvgToContentArea(
                pdfDoc = pdfDoc,
                svgRoot = svgRoot,
                defs = defs,
                layout = layout,
                density = density,
                verticalOffsetPt = pageIndex * layout.contentHeightPt,
                fontCache = fontCache,
                imageCache = imageCache,
            )
        }
        return pageCount
    }
```

(b) Extract the content-stream body of `renderSvgToContentArea` into a shared `drawSvgContent`, and add `drawSvgOnPage`:

```kotlin
    /**
     * Draws [svg] into the content area defined by [layout] on an EXISTING [page],
     * appending to its content stream and clipping to the area. Used to stamp
     * header/footer bands onto already-emitted pages.
     */
    fun drawSvgOnPage(
        pdfDoc: PDDocument,
        page: PDPage,
        svg: String,
        layout: PageLayout,
        density: Float,
        fontCache: MutableMap<String, PDFont> = mutableMapOf(),
        imageCache: MutableMap<String, PDImageXObject> = mutableMapOf(),
    ) {
        val (svgRoot, defs) = parseSvg(svg)
        val cs = PDPageContentStream(pdfDoc, page, PDPageContentStream.AppendMode.APPEND, true, true)
        try {
            drawSvgContent(cs, pdfDoc, svgRoot, defs, layout, density, verticalOffsetPt = 0f, fontCache, imageCache)
        } finally {
            cs.close()
        }
    }

    private fun renderSvgToContentArea(
        pdfDoc: PDDocument,
        svgRoot: Element,
        defs: Map<String, Element>,
        layout: PageLayout,
        density: Float,
        verticalOffsetPt: Float,
        fontCache: MutableMap<String, PDFont>,
        imageCache: MutableMap<String, PDImageXObject>,
    ) {
        val mediaBox = PDRectangle(layout.pageWidthPt, layout.pageHeightPt)
        val page = PDPage(mediaBox)
        pdfDoc.addPage(page)

        val cs = PDPageContentStream(pdfDoc, page)
        try {
            drawSvgContent(cs, pdfDoc, svgRoot, defs, layout, density, verticalOffsetPt, fontCache, imageCache)
        } finally {
            cs.close()
        }
    }

    /** Clips to [layout]'s content area and renders the parsed SVG into it. */
    private fun drawSvgContent(
        cs: PDPageContentStream,
        pdfDoc: PDDocument,
        svgRoot: Element,
        defs: Map<String, Element>,
        layout: PageLayout,
        density: Float,
        verticalOffsetPt: Float,
        fontCache: MutableMap<String, PDFont>,
        imageCache: MutableMap<String, PDImageXObject>,
    ) {
        val marginBottom = layout.pageHeightPt - layout.marginTopPt - layout.contentHeightPt
        cs.addRect(layout.marginLeftPt, marginBottom, layout.contentWidthPt, layout.contentHeightPt)
        cs.clip()

        val scale = 1f / density
        cs.transform(
            CoordinateTransform.contentAreaMatrix(
                scale,
                layout.marginLeftPt,
                layout.marginTopPt,
                layout.pageHeightPt,
                verticalOffsetPt,
            )
        )

        PageRenderer(cs, pdfDoc, defs, fontCache, imageCache).renderChildren(svgRoot)
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :compose2pdf:test --tests '*SvgConverterPageTest*'`
Expected: PASS.

- [ ] **Step 5: Run full unit suite + regression pin**

Run: `./gradlew :compose2pdf:test`
Expected: all PASS (including `NullSlotRegressionTest` — the refactor must not change emitted streams).

- [ ] **Step 6: Commit**

```bash
git add compose2pdf/src
git commit -m "feat: drawSvgOnPage for band stamping; addAutoPages returns emitted page count"
```

---

### Task 5: Vector header/footer path + public API wiring

The core task: slot measurement, guard, effective config, with-slots vector rendering (auto multi-page, single-page fallback, SINGLE_PAGE), per-page stamping, link offsets, and the `header`/`footer` parameters on both public overloads.

**Files:**
- Modify: `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/internal/PdfRenderer.kt`
- Modify: `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/Compose2Pdf.kt`
- Test: `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/HeaderFooterVectorTest.kt`

**Interfaces:**
- Consumes: `PdfPageInfo` (Task 3); `SvgToPdfConverter.drawSvgOnPage` + `addAutoPages(): Int` (Task 4); existing `ComposeToSvg.render/measureContentHeight/renderWithMeasurement`, `PageLayout`, `WrapContent`, `PdfLinkCollector`.
- Produces (used by Task 6):
  - `internal class SlotBands(val headerPx: Int, val footerPx: Int, val headerPt: Float, val footerPt: Float, val effectiveConfig: PdfPageConfig)`
  - `PdfRenderer.measureSlotBands(config, density, defaultFontFamily, header, footer): SlotBands` (internal)
  - `renderSinglePage(..., header: (@Composable (PdfPageInfo) -> Unit)? = null, footer: (@Composable (PdfPageInfo) -> Unit)? = null, content)` — the null-slot guard dispatch.
  - `addLinkToPage(page, pageHeightPt: Float, marginLeftPt: Float, marginTopPt: Float, href, x, y, width, height)` and `distributeLinks(doc, config, links, contentHeightPt, marginTopPt = config.margins.top.value)` — offset-parameterized link plumbing.
  - Public: `renderToPdf(outputStream/…, header = …, footer = …) { }` on both auto overloads.

- [ ] **Step 1: Write the failing tests**

Create `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/HeaderFooterVectorTest.kt`:

```kotlin
package com.chrisjenx.compose2pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HeaderFooterVectorTest {

    private val config = PdfPageConfig.A4WithMargins // page 595x842pt, margins 72pt, content 451x698pt
    private val mode = RenderMode.VECTOR

    // Band geometry at 72 DPI (1pt = 1px), y measured from page top:
    // header band: 72..112 (40dp header) — sample y=92
    // footer band: 842-72-30=740..770 (30dp footer) — sample y=755
    private fun pagePixel(bytes: ByteArray, pageIndex: Int, y: Int): Int {
        Loader.loadPDF(bytes).use { doc ->
            val img = PDFRenderer(doc).renderImageWithDPI(pageIndex, 72f)
            val x = (config.margins.left.value + config.contentWidth.value / 2).toInt()
            return img.getRGB(x, y)
        }
    }

    private fun Int.isRed() = ((this shr 16) and 0xFF) > 200 && ((this shr 8) and 0xFF) < 80
    private fun Int.isBlue() = (this and 0xFF) > 200 && ((this shr 16) and 0xFF) < 80

    private val redHeader: @Composable (PdfPageInfo) -> Unit = {
        Box(Modifier.fillMaxWidth().height(40.dp).background(Color.Red))
    }
    private val blueFooter: @Composable (PdfPageInfo) -> Unit = {
        Box(Modifier.fillMaxWidth().height(30.dp).background(Color.Blue))
    }

    /** 12 x 200dp children; effective page = 698-40-30 = 628dp -> 3 children/page -> 4 pages. */
    private val multiPageBody: @Composable () -> Unit = {
        repeat(12) { Spacer(Modifier.fillMaxWidth().height(200.dp)) }
    }

    @Test
    fun `slots are stamped on every page with correct band geometry`() {
        val bytes = renderToPdf(config = config, mode = mode, header = redHeader, footer = blueFooter) {
            multiPageBody()
        }
        val pageCount = Loader.loadPDF(bytes).use { it.numberOfPages }
        assertEquals(4, pageCount)
        for (page in 0 until pageCount) {
            assertTrue(pagePixel(bytes, page, 92).isRed(), "page $page: header band should be red")
            assertTrue(pagePixel(bytes, page, 755).isBlue(), "page $page: footer band should be blue")
        }
    }

    @Test
    fun `slots receive pageIndex and pageCount for every page`() {
        val received = mutableListOf<Pair<Int, Int>>()
        renderToPdf(config = config, mode = mode, footer = { info ->
            received += info.pageIndex to info.pageCount
            Text("Page ${info.pageNumber} of ${info.pageCount}")
        }) {
            multiPageBody()
        }
        // Measurement composes a (0, 2) sentinel; stamping must compose the real values.
        for (i in 0 until 4) {
            assertTrue(i to 4 in received, "footer should have been composed with ($i, 4); got $received")
        }
    }

    @Test
    fun `single-page content still gets slots with pageCount 1`() {
        val received = mutableListOf<Pair<Int, Int>>()
        val bytes = renderToPdf(config = config, mode = mode, header = redHeader, footer = { info ->
            received += info.pageIndex to info.pageCount
            Box(Modifier.fillMaxWidth().height(30.dp).background(Color.Blue))
        }) {
            Text("Short content")
        }
        assertEquals(1, Loader.loadPDF(bytes).use { it.numberOfPages })
        assertTrue(pagePixel(bytes, 0, 92).isRed(), "header should be stamped on the single page")
        assertTrue(pagePixel(bytes, 0, 755).isBlue(), "footer should be stamped on the single page")
        assertTrue(0 to 1 in received, "footer should have been composed with (0, 1); got $received")
    }

    @Test
    fun `SINGLE_PAGE pagination stamps slots and clips body to effective area`() {
        val bytes = renderToPdf(
            config = config, mode = mode, pagination = PdfPagination.SINGLE_PAGE,
            header = redHeader, footer = blueFooter,
        ) {
            Box(Modifier.fillMaxWidth().height(2000.dp).background(Color(0xFF9E9E9E)))
        }
        assertEquals(1, Loader.loadPDF(bytes).use { it.numberOfPages })
        assertTrue(pagePixel(bytes, 0, 92).isRed(), "header band must not be overdrawn by body")
        assertTrue(pagePixel(bytes, 0, 755).isBlue(), "footer band must not be overdrawn by body")
    }

    @Test
    fun `body content never overlaps the bands`() {
        // Gray body fills every page fully; bands must still win their reserved space
        val bytes = renderToPdf(config = config, mode = mode, header = redHeader, footer = blueFooter) {
            repeat(4) { Box(Modifier.fillMaxWidth().height(628.dp).background(Color(0xFF9E9E9E))) }
        }
        val pageCount = Loader.loadPDF(bytes).use { it.numberOfPages }
        for (page in 0 until pageCount) {
            assertTrue(pagePixel(bytes, page, 92).isRed(), "page $page: header area is reserved")
            assertTrue(pagePixel(bytes, page, 755).isBlue(), "page $page: footer area is reserved")
        }
    }

    @Test
    fun `zero-height slot reserves no band and is skipped`() {
        // Footer composes nothing -> measures 0px -> no band, page math identical to no-footer
        val bytes = renderToPdf(config = config, mode = mode, footer = { /* renders nothing */ }) {
            // 698dp effective page (no band): 3 x 200dp fit, 4th pushed -> 12 children = 4 pages
            repeat(12) { Spacer(Modifier.fillMaxWidth().height(200.dp)) }
        }
        assertEquals(4, Loader.loadPDF(bytes).use { it.numberOfPages })
    }

    @Test
    fun `slots too tall for the page throw IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            renderToPdf(config = config, mode = mode, header = {
                Box(Modifier.fillMaxWidth().fillMaxHeight())
            }) {
                Text("body")
            }
        }
    }

    @Test
    fun `PdfLink in footer produces an annotation on every page inside the band`() {
        val bytes = renderToPdf(config = config, mode = mode, footer = {
            PdfLink("https://example.com") { Text("example.com") }
        }) {
            multiPageBody()
        }
        Loader.loadPDF(bytes).use { doc ->
            // 698 - 0 - footer band; footer top is at 842-72-footerPt; PDF y-up: band spans [72, 72+footerPt]
            for (i in 0 until doc.numberOfPages) {
                val annotations = doc.getPage(i).annotations
                assertTrue(annotations.isNotEmpty(), "page $i should have a link annotation")
                val rect = annotations.first().rectangle
                assertTrue(
                    rect.lowerLeftY >= 60f && rect.upperRightY <= 120f,
                    "page $i: link rect should sit in the footer band, was [${rect.lowerLeftY}, ${rect.upperRightY}]",
                )
            }
        }
    }

    @Test
    fun `paginated column inside providers breaks at the effective page height`() {
        // Public PaginatedColumn reads LocalPdfPageConfig; with slots it must see the
        // effective (reduced) content height or children split across boundaries.
        val bytes = renderToPdf(config = config, mode = mode, header = redHeader, footer = blueFooter) {
            Box { // hides children from the automatic outer PaginatedColumn
                PaginatedColumn {
                    repeat(12) { Spacer(Modifier.fillMaxWidth().height(200.dp)) }
                }
            }
        }
        // Same math as multiPageBody: 3 x 200dp per 628dp effective page -> 4 pages
        assertEquals(4, Loader.loadPDF(bytes).use { it.numberOfPages })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :compose2pdf:test --tests '*HeaderFooterVectorTest*'`
Expected: COMPILATION FAILURE — no `header`/`footer` parameters on `renderToPdf`.

- [ ] **Step 3: Implement the renderer machinery**

In `PdfRenderer.kt`:

(a) Add imports: `androidx.compose.ui.unit.dp`, `com.chrisjenx.compose2pdf.PdfPageInfo`.

(b) Add the bands holder and measurement (below `measureForPagination`):

```kotlin
    /** Measured header/footer bands plus the page config with margins inflated by them. */
    internal class SlotBands(
        val headerPx: Int,
        val footerPx: Int,
        val headerPt: Float,
        val footerPt: Float,
        val effectiveConfig: PdfPageConfig,
    )

    /**
     * Measures slot heights (px-first; pt derived from px) and builds the effective config.
     * The effective config is provided through LocalPdfPageConfig so the public
     * PaginatedColumn breaks at the reduced content height.
     */
    internal fun measureSlotBands(
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        header: (@Composable (PdfPageInfo) -> Unit)?,
        footer: (@Composable (PdfPageInfo) -> Unit)?,
    ): SlotBands {
        val contentWidthPx = config.contentWidthPx(density)
        val contentHeightPx = config.contentHeightPx(density)
        val headerPx = measureSlotHeight(header, contentWidthPx, contentHeightPx, density, defaultFontFamily, config)
        val footerPx = measureSlotHeight(footer, contentWidthPx, contentHeightPx, density, defaultFontFamily, config)
        require(headerPx + footerPx < contentHeightPx) {
            "Header (${headerPx}px) and footer (${footerPx}px) leave no room for page content " +
                "(content area is ${contentHeightPx}px tall at density ${density.density})"
        }
        val headerPt = headerPx / density.density
        val footerPt = footerPx / density.density
        val effectiveConfig = config.copy(
            margins = config.margins.copy(
                top = config.margins.top + headerPt.dp,
                bottom = config.margins.bottom + footerPt.dp,
            )
        )
        return SlotBands(headerPx, footerPx, headerPt, footerPt, effectiveConfig)
    }

    private fun measureSlotHeight(
        slot: (@Composable (PdfPageInfo) -> Unit)?,
        contentWidthPx: Int,
        maxHeightPx: Int,
        density: Density,
        defaultFontFamily: FontFamily?,
        config: PdfPageConfig,
    ): Int {
        if (slot == null) return 0
        // pageCount = 2 sentinel: a footer guarded by `if (pageCount > 1)` must still
        // measure its real height. Slot height must be stable across pages; content
        // taller than the measured band is clipped at stamp time.
        val sentinel = PdfPageInfo(pageIndex = 0, pageCount = 2)
        return ComposeToSvg.measureContentHeight(contentWidthPx, maxHeightPx, density) {
            WrapContent(defaultFontFamily, null, config) { slot(sentinel) }
        }
    }

    /** PageLayout for the body area between the bands. Content height derives from px. */
    private fun bodyLayout(config: PdfPageConfig, density: Density, bands: SlotBands): PageLayout {
        val effectivePx = bands.effectiveConfig.contentHeightPx(density)
        return PageLayout(
            pageWidthPt = config.width.value,
            pageHeightPt = config.height.value,
            contentWidthPt = config.contentWidth.value,
            contentHeightPt = effectivePx / density.density,
            marginLeftPt = config.margins.left.value,
            marginTopPt = config.margins.top.value + bands.headerPt,
        )
    }

    /** PageLayout describing a slot band ([bandHeightPt] tall, [marginTopPt] from the page top). */
    private fun slotLayout(config: PdfPageConfig, bandHeightPt: Float, marginTopPt: Float) = PageLayout(
        pageWidthPt = config.width.value,
        pageHeightPt = config.height.value,
        contentWidthPt = config.contentWidth.value,
        contentHeightPt = bandHeightPt,
        marginLeftPt = config.margins.left.value,
        marginTopPt = marginTopPt,
    )
```

(c) Change `renderSinglePage` to the guarded dispatch (existing body becomes the null-slot branch verbatim):

```kotlin
    fun renderSinglePage(
        config: PdfPageConfig,
        density: Density,
        mode: RenderMode,
        defaultFontFamily: FontFamily?,
        pagination: PdfPagination,
        header: (@Composable (PdfPageInfo) -> Unit)? = null,
        footer: (@Composable (PdfPageInfo) -> Unit)? = null,
        content: @Composable () -> Unit,
    ): PDDocument {
        if (header == null && footer == null) {
            // Fast path: exactly the pre-slots pipeline (fidelity guarantee — see CLAUDE.md gotchas).
            return when (pagination) {
                PdfPagination.SINGLE_PAGE -> renderMultiPage(
                    pageCount = 1,
                    config = config,
                    density = density,
                    mode = mode,
                    defaultFontFamily = defaultFontFamily,
                    content = { content() },
                )
                PdfPagination.AUTO -> when (mode) {
                    RenderMode.VECTOR -> renderAutoVector(config, density, defaultFontFamily, content)
                    RenderMode.RASTER -> renderAutoRaster(config, density, defaultFontFamily, content)
                }
            }
        }
        val bands = measureSlotBands(config, density, defaultFontFamily, header, footer)
        return when (mode) {
            RenderMode.VECTOR -> renderVectorWithSlots(config, density, defaultFontFamily, pagination, header, footer, bands, content)
            RenderMode.RASTER -> renderRasterWithSlots(config, density, defaultFontFamily, pagination, header, footer, bands, content)
        }
    }
```

For this task, add a temporary raster stub so the file compiles (Task 6 replaces it):

```kotlin
    private fun renderRasterWithSlots(
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        pagination: PdfPagination,
        header: (@Composable (PdfPageInfo) -> Unit)?,
        footer: (@Composable (PdfPageInfo) -> Unit)?,
        bands: SlotBands,
        content: @Composable () -> Unit,
    ): PDDocument = throw Compose2PdfException("header/footer raster support lands in the next task")
```

(d) Add the vector with-slots path (below `renderAutoVector`):

```kotlin
    private fun renderVectorWithSlots(
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        pagination: PdfPagination,
        header: (@Composable (PdfPageInfo) -> Unit)?,
        footer: (@Composable (PdfPageInfo) -> Unit)?,
        bands: SlotBands,
        content: @Composable () -> Unit,
    ): PDDocument {
        val contentWidthPx = config.contentWidthPx(density)
        val effectivePx = bands.effectiveConfig.contentHeightPx(density)
        val layout = bodyLayout(config, density, bands)

        val pdfDoc = PDDocument()
        val fontCache = mutableMapOf<String, org.apache.pdfbox.pdmodel.font.PDFont>()
        val imageCache = mutableMapOf<String, org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject>()
        val bodyLinks = PdfLinkCollector()

        val singlePage = pagination == PdfPagination.SINGLE_PAGE || run {
            val measuredHeightPx = ComposeToSvg.measureContentHeight(contentWidthPx, MAX_MEASURE_HEIGHT_PX, density) {
                WrapContent(defaultFontFamily, null, bands.effectiveConfig) {
                    PaginatedColumn(contentHeightPx = effectivePx) { content() }
                }
            }
            measuredHeightPx <= effectivePx || measuredHeightPx >= MAX_MEASURE_HEIGHT_PX
        }

        val pageCount: Int
        if (singlePage) {
            val svg = ComposeToSvg.render(contentWidthPx, effectivePx, density) {
                WrapContent(defaultFontFamily, bodyLinks, bands.effectiveConfig) { content() }
            }
            SvgToPdfConverter.addPage(pdfDoc, svg, layout, density.density, fontCache, imageCache)
            pageCount = 1
        } else {
            val result = ComposeToSvg.renderWithMeasurement(contentWidthPx, MAX_MEASURE_HEIGHT_PX, density) {
                WrapContent(defaultFontFamily, bodyLinks, bands.effectiveConfig) {
                    PaginatedColumn(contentHeightPx = effectivePx) { content() }
                }
            }
            val totalContentHeightPt = result.measuredHeightPx.coerceAtLeast(1) / density.density
            val estimatedPages = kotlin.math.ceil(totalContentHeightPt.toDouble() / layout.contentHeightPt).toInt()
            if (estimatedPages > MAX_AUTO_PAGES) {
                logger.warning(
                    "Auto-pagination truncated: content requires ~$estimatedPages pages but max is $MAX_AUTO_PAGES"
                )
            }
            pageCount = SvgToPdfConverter.addAutoPages(
                pdfDoc, result.svg, layout, totalContentHeightPt,
                density.density, MAX_AUTO_PAGES, fontCache, imageCache,
            )
        }

        distributeLinks(pdfDoc, config, bodyLinks.links, layout.contentHeightPt, marginTopPt = layout.marginTopPt)
        stampSlotsVector(pdfDoc, config, density, defaultFontFamily, header, footer, bands, pageCount, fontCache, imageCache)
        return pdfDoc
    }

    private fun stampSlotsVector(
        pdfDoc: PDDocument,
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        header: (@Composable (PdfPageInfo) -> Unit)?,
        footer: (@Composable (PdfPageInfo) -> Unit)?,
        bands: SlotBands,
        pageCount: Int,
        fontCache: MutableMap<String, org.apache.pdfbox.pdmodel.font.PDFont>,
        imageCache: MutableMap<String, org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject>,
    ) {
        val headerLayout = slotLayout(config, bands.headerPt, marginTopPt = config.margins.top.value)
        // Footer anchors to the bottom margin so px->pt rounding slop lands in the body gap, not off-page.
        val footerLayout = slotLayout(
            config, bands.footerPt,
            marginTopPt = config.height.value - config.margins.bottom.value - bands.footerPt,
        )
        for (pageIndex in 0 until pageCount) {
            val info = PdfPageInfo(pageIndex, pageCount)
            val page = pdfDoc.getPage(pageIndex)
            if (header != null && bands.headerPx > 0) {
                stampSlotVector(pdfDoc, page, config, density, defaultFontFamily, header, info, bands.headerPx, headerLayout, fontCache, imageCache)
            }
            if (footer != null && bands.footerPx > 0) {
                stampSlotVector(pdfDoc, page, config, density, defaultFontFamily, footer, info, bands.footerPx, footerLayout, fontCache, imageCache)
            }
        }
    }

    private fun stampSlotVector(
        pdfDoc: PDDocument,
        page: PDPage,
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        slot: @Composable (PdfPageInfo) -> Unit,
        info: PdfPageInfo,
        bandHeightPx: Int,
        bandLayout: PageLayout,
        fontCache: MutableMap<String, org.apache.pdfbox.pdmodel.font.PDFont>,
        imageCache: MutableMap<String, org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject>,
    ) {
        val linkCollector = PdfLinkCollector()
        val svg = ComposeToSvg.render(config.contentWidthPx(density), bandHeightPx, density) {
            WrapContent(defaultFontFamily, linkCollector, config) { slot(info) }
        }
        SvgToPdfConverter.drawSvgOnPage(pdfDoc, page, svg, bandLayout, density.density, fontCache, imageCache)
        for (link in linkCollector.links) {
            addLinkToPage(
                page, bandLayout.pageHeightPt, bandLayout.marginLeftPt, bandLayout.marginTopPt,
                link.href, link.x, link.y, link.width, link.height,
            )
        }
    }
```

(e) Parameterize the link plumbing offsets. Replace `addLinkToPage` and `distributeLinks`, and update `addLinkAnnotations`:

```kotlin
    private fun addLinkAnnotations(
        page: PDPage,
        config: PdfPageConfig,
        links: List<PdfLinkAnnotation>,
    ) {
        if (links.isEmpty()) return
        for (link in links) {
            addLinkToPage(
                page, config.height.value, config.margins.left.value, config.margins.top.value,
                link.href, link.x, link.y, link.width, link.height,
            )
        }
    }

    private fun distributeLinks(
        doc: PDDocument,
        config: PdfPageConfig,
        links: List<PdfLinkAnnotation>,
        contentHeightPt: Float,
        marginTopPt: Float = config.margins.top.value,
    ) {
        if (links.isEmpty()) return
        for (link in links) {
            val pageIndex = (link.y / contentHeightPt).toInt()
                .coerceIn(0, doc.numberOfPages - 1)
            val adjustedY = link.y - pageIndex * contentHeightPt
            addLinkToPage(
                doc.getPage(pageIndex), config.height.value, config.margins.left.value, marginTopPt,
                link.href, link.x, adjustedY, link.width, link.height,
            )
        }
    }

    private fun addLinkToPage(
        page: PDPage,
        pageHeightPt: Float,
        marginLeftPt: Float,
        marginTopPt: Float,
        href: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        val annotation = PDAnnotationLink()
        val action = PDActionURI()
        action.uri = href
        annotation.action = action

        annotation.rectangle = CoordinateTransform.svgToPdfRect(
            svgX = x,
            svgY = y,
            width = width,
            height = height,
            pageHeight = pageHeightPt,
            marginLeft = marginLeftPt,
            marginTop = marginTopPt,
        )

        val borderStyle = PDBorderStyleDictionary()
        borderStyle.width = 0f
        annotation.borderStyle = borderStyle

        page.annotations.add(annotation)
    }
```

- [ ] **Step 4: Wire the public API**

In `Compose2Pdf.kt`, add to BOTH auto-pagination overloads (the `OutputStream` one at ~line 42 and the `ByteArray` one at ~line 90), between `pagination` and `content`:

```kotlin
    header: (@Composable (PdfPageInfo) -> Unit)? = null,
    footer: (@Composable (PdfPageInfo) -> Unit)? = null,
```

The `OutputStream` overload forwards them:

```kotlin
        val doc = PdfRenderer.renderSinglePage(config, density, mode, defaultFontFamily, pagination, header, footer, content)
```

The `ByteArray` overload forwards to the `OutputStream` one:

```kotlin
    renderToPdf(baos, config, density, mode, defaultFontFamily, pagination, header, footer, content)
```

Add KDoc to both (same text, adapted):

```kotlin
 * @param header Optional composable stamped at the top of every page, above the content
 *   area. Receives [PdfPageInfo]. Its height is measured once (with a `pageCount = 2`
 *   sentinel, so `if (pageCount > 1)` footers still reserve space) and that height is
 *   reserved uniformly on every page — slot height must be stable across pages; taller
 *   content is clipped to the band. Inside the body, [LocalPdfPageConfig] reflects the
 *   content area reduced by the bands.
 * @param footer Optional composable stamped at the bottom of every page. Same rules as [header].
```

And extend the existing `@throws Compose2PdfException` docs with:

```kotlin
 * @throws IllegalArgumentException if the measured header + footer heights leave no room for content.
```

- [ ] **Step 5: Run the vector tests**

Run: `./gradlew :compose2pdf:test --tests '*HeaderFooterVectorTest*'`
Expected: all 8 PASS.

- [ ] **Step 6: Run the full unit suite (regression pin must hold)**

Run: `./gradlew :compose2pdf:test`
Expected: all PASS — especially `NullSlotRegressionTest` (null-slot dispatch unchanged) and the existing link/pagination tests (link-plumbing refactor is behavior-preserving).

- [ ] **Step 7: Commit**

```bash
git add compose2pdf/src
git commit -m "feat: per-page header/footer slots (vector) with PdfPageInfo and band stamping"
```

---

### Task 6: Raster header/footer path

**Files:**
- Modify: `compose2pdf/src/main/kotlin/com/chrisjenx/compose2pdf/internal/PdfRenderer.kt` (replace the Task 5 stub)
- Test: `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/HeaderFooterRasterTest.kt`

**Interfaces:**
- Consumes: `SlotBands`/`measureSlotBands`, `bodyLayout` math, `addBitmapPage(topPt, heightPt)` (Task 1), `addLinkToPage` offsets (Task 5), `renderComposeToBitmap`, `PdfPageInfo`.
- Produces: `renderRasterWithSlots(...)` — full raster support behind the same public API.

- [ ] **Step 1: Write the failing tests**

Create `compose2pdf/src/test/kotlin/com/chrisjenx/compose2pdf/HeaderFooterRasterTest.kt`:

```kotlin
package com.chrisjenx.compose2pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeaderFooterRasterTest {

    private val config = PdfPageConfig.A4WithMargins
    private val mode = RenderMode.RASTER

    private fun pagePixel(bytes: ByteArray, pageIndex: Int, y: Int): Int {
        Loader.loadPDF(bytes).use { doc ->
            val img = PDFRenderer(doc).renderImageWithDPI(pageIndex, 72f)
            val x = (config.margins.left.value + config.contentWidth.value / 2).toInt()
            return img.getRGB(x, y)
        }
    }

    private fun Int.isRed() = ((this shr 16) and 0xFF) > 200 && ((this shr 8) and 0xFF) < 80
    private fun Int.isBlue() = (this and 0xFF) > 200 && ((this shr 16) and 0xFF) < 80
    private fun Int.isGray() = ((this shr 16) and 0xFF) in 120..200

    private val redHeader: @Composable (PdfPageInfo) -> Unit = {
        Box(Modifier.fillMaxWidth().height(40.dp).background(Color.Red))
    }
    private val blueFooter: @Composable (PdfPageInfo) -> Unit = {
        Box(Modifier.fillMaxWidth().height(30.dp).background(Color.Blue))
    }

    @Test
    fun `raster slots are stamped on every page with correct band geometry`() {
        val received = mutableListOf<Pair<Int, Int>>()
        val bytes = renderToPdf(config = config, mode = mode, header = redHeader, footer = { info ->
            received += info.pageIndex to info.pageCount
            Box(Modifier.fillMaxWidth().height(30.dp).background(Color.Blue))
        }) {
            // 12 x 200dp children; effective page = 628dp -> 3/page -> 4 pages
            repeat(12) { Spacer(Modifier.fillMaxWidth().height(200.dp)) }
        }
        val pageCount = Loader.loadPDF(bytes).use { it.numberOfPages }
        assertEquals(4, pageCount)
        for (page in 0 until pageCount) {
            assertTrue(pagePixel(bytes, page, 92).isRed(), "page $page: header band should be red")
            assertTrue(pagePixel(bytes, page, 755).isBlue(), "page $page: footer band should be blue")
            assertTrue(page to 4 in received, "footer should have been composed with ($page, 4)")
        }
    }

    @Test
    fun `raster partial last slice with bands is not stretched`() {
        // Effective page = 628dp. 942dp body = 1.5 effective pages: page 2 holds 314dp.
        val bytes = renderToPdf(config = config, mode = mode, header = redHeader, footer = blueFooter) {
            Box(Modifier.fillMaxWidth().height(942.dp).background(Color(0xFF9E9E9E)))
        }
        assertEquals(2, Loader.loadPDF(bytes).use { it.numberOfPages })
        // Page 2 body band spans y=112..740; its content (314dp) ends at y=112+314=426.
        assertTrue(pagePixel(bytes, 1, 200).isGray(), "page 2: top of body band should have content")
        assertFalse(pagePixel(bytes, 1, 600).isGray(), "page 2: lower body band must be blank, not stretched")
        assertTrue(pagePixel(bytes, 1, 755).isBlue(), "page 2: footer still stamped")
    }

    @Test
    fun `raster single page gets slots with pageCount 1`() {
        val received = mutableListOf<Pair<Int, Int>>()
        val bytes = renderToPdf(config = config, mode = mode, header = redHeader, footer = { info ->
            received += info.pageIndex to info.pageCount
            Box(Modifier.fillMaxWidth().height(30.dp).background(Color.Blue))
        }) {
            Text("Short content")
        }
        assertEquals(1, Loader.loadPDF(bytes).use { it.numberOfPages })
        assertTrue(pagePixel(bytes, 0, 92).isRed())
        assertTrue(pagePixel(bytes, 0, 755).isBlue())
        assertTrue(0 to 1 in received)
    }

    @Test
    fun `raster SINGLE_PAGE pagination stamps slots`() {
        val bytes = renderToPdf(
            config = config, mode = mode, pagination = PdfPagination.SINGLE_PAGE,
            header = redHeader, footer = blueFooter,
        ) {
            Box(Modifier.fillMaxWidth().height(2000.dp).background(Color(0xFF9E9E9E)))
        }
        assertEquals(1, Loader.loadPDF(bytes).use { it.numberOfPages })
        assertTrue(pagePixel(bytes, 0, 92).isRed(), "header band must not be overdrawn by body")
        assertTrue(pagePixel(bytes, 0, 755).isBlue(), "footer band must not be overdrawn by body")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :compose2pdf:test --tests '*HeaderFooterRasterTest*'`
Expected: FAIL — every test hits the Task 5 stub's `Compose2PdfException("header/footer raster support lands in the next task")`.

- [ ] **Step 3: Implement**

In `PdfRenderer.kt`, replace the stub with:

```kotlin
    private fun renderRasterWithSlots(
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        pagination: PdfPagination,
        header: (@Composable (PdfPageInfo) -> Unit)?,
        footer: (@Composable (PdfPageInfo) -> Unit)?,
        bands: SlotBands,
        content: @Composable () -> Unit,
    ): PDDocument {
        val contentWidthPx = config.contentWidthPx(density)
        val effectivePx = bands.effectiveConfig.contentHeightPx(density)
        val bodyTopPt = config.margins.top.value + bands.headerPt
        val bodyHeightPt = effectivePx / density.density
        val bodyLinks = PdfLinkCollector()
        val doc = PDDocument()

        val measuredHeightPx = if (pagination == PdfPagination.SINGLE_PAGE) effectivePx else {
            ComposeToSvg.measureContentHeight(contentWidthPx, MAX_MEASURE_HEIGHT_PX, density) {
                WrapContent(defaultFontFamily, null, bands.effectiveConfig) {
                    PaginatedColumn(contentHeightPx = effectivePx) { content() }
                }
            }
        }

        if (measuredHeightPx <= effectivePx || measuredHeightPx >= MAX_MEASURE_HEIGHT_PX) {
            val bitmap = renderComposeToBitmap(contentWidthPx, effectivePx, density) {
                WrapContent(defaultFontFamily, bodyLinks, bands.effectiveConfig) { content() }
            }
            addBitmapPage(doc, config, bitmap, topPt = bodyTopPt, heightPt = bodyHeightPt)
        } else {
            val fullBitmap = renderComposeToBitmap(contentWidthPx, measuredHeightPx, density) {
                WrapContent(defaultFontFamily, bodyLinks, bands.effectiveConfig) {
                    PaginatedColumn(contentHeightPx = effectivePx) { content() }
                }
            }
            val rawPageCount = kotlin.math.ceil(measuredHeightPx.toFloat() / effectivePx.toFloat()).toInt()
            if (rawPageCount > MAX_AUTO_PAGES) {
                logger.warning(
                    "Auto-pagination truncated: content requires ~$rawPageCount pages but max is $MAX_AUTO_PAGES"
                )
            }
            val pageCount = rawPageCount.coerceIn(1, MAX_AUTO_PAGES)
            for (pageIndex in 0 until pageCount) {
                val sliceTop = pageIndex * effectivePx
                val sliceHeight = minOf(effectivePx, measuredHeightPx - sliceTop)
                if (sliceHeight <= 0) break
                val slice = fullBitmap.getSubimage(0, sliceTop, contentWidthPx, sliceHeight)
                addBitmapPage(
                    doc, config, slice,
                    topPt = bodyTopPt,
                    heightPt = bodyHeightPt * sliceHeight / effectivePx,
                )
            }
        }

        distributeLinks(doc, config, bodyLinks.links, bodyHeightPt, marginTopPt = bodyTopPt)
        stampSlotsRaster(doc, config, density, defaultFontFamily, header, footer, bands, doc.numberOfPages)
        return doc
    }

    private fun stampSlotsRaster(
        doc: PDDocument,
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        header: (@Composable (PdfPageInfo) -> Unit)?,
        footer: (@Composable (PdfPageInfo) -> Unit)?,
        bands: SlotBands,
        pageCount: Int,
    ) {
        for (pageIndex in 0 until pageCount) {
            val info = PdfPageInfo(pageIndex, pageCount)
            val page = doc.getPage(pageIndex)
            if (header != null && bands.headerPx > 0) {
                stampSlotRaster(
                    doc, page, config, density, defaultFontFamily, header, info,
                    bandHeightPx = bands.headerPx,
                    topPt = config.margins.top.value,
                    heightPt = bands.headerPt,
                )
            }
            if (footer != null && bands.footerPx > 0) {
                stampSlotRaster(
                    doc, page, config, density, defaultFontFamily, footer, info,
                    bandHeightPx = bands.footerPx,
                    topPt = config.height.value - config.margins.bottom.value - bands.footerPt,
                    heightPt = bands.footerPt,
                )
            }
        }
    }

    private fun stampSlotRaster(
        doc: PDDocument,
        page: PDPage,
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        slot: @Composable (PdfPageInfo) -> Unit,
        info: PdfPageInfo,
        bandHeightPx: Int,
        topPt: Float,
        heightPt: Float,
    ) {
        val linkCollector = PdfLinkCollector()
        val bitmap = renderComposeToBitmap(config.contentWidthPx(density), bandHeightPx, density) {
            WrapContent(defaultFontFamily, linkCollector, config) { slot(info) }
        }
        val pdImage = LosslessFactory.createFromImage(doc, bitmap)
        val cs = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
        try {
            cs.drawImage(
                pdImage,
                config.margins.left.value,
                config.height.value - topPt - heightPt,
                config.contentWidth.value,
                heightPt,
            )
        } finally {
            cs.close()
        }
        for (link in linkCollector.links) {
            addLinkToPage(
                page, config.height.value, config.margins.left.value, topPt,
                link.href, link.x, link.y, link.width, link.height,
            )
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :compose2pdf:test --tests '*HeaderFooterRasterTest*'`
Expected: all 4 PASS.

- [ ] **Step 5: Run the full unit suite**

Run: `./gradlew :compose2pdf:test`
Expected: all PASS (regression pin still green).

- [ ] **Step 6: Commit**

```bash
git add compose2pdf/src
git commit -m "feat: per-page header/footer slots for raster mode"
```

---

### Task 7: Fidelity test + runnable example

**Files:**
- Create: `fidelity-test/src/test/kotlin/com/chrisjenx/compose2pdf/test/HeaderFooterFidelityTest.kt`
- Create: `examples/src/main/kotlin/com/chrisjenx/compose2pdf/examples/13_HeaderFooter.kt`
- Modify: `examples/src/main/kotlin/com/chrisjenx/compose2pdf/examples/Main.kt` (~line 37: add `::headerFooter` after `::paginatedColumnExample`)

**Interfaces:**
- Consumes: public API from Tasks 5/6; fidelity helpers `rasterizePdf(doc, dpi, page)`, `saveImage(image, dir, name)`, `BufferedImage.isWhitishAt(x, y)` from `TestHelpers.kt`; `ExampleOutput(name, sourceFile, pdfBytes)` from `Main.kt`.

- [ ] **Step 1: Write the fidelity test**

Create `fidelity-test/src/test/kotlin/com/chrisjenx/compose2pdf/test/HeaderFooterFidelityTest.kt`:

```kotlin
package com.chrisjenx.compose2pdf.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.PdfPageInfo
import com.chrisjenx.compose2pdf.RenderMode
import com.chrisjenx.compose2pdf.renderToPdf
import org.apache.pdfbox.Loader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeaderFooterFidelityTest {

    private val config = PdfPageConfig.A4WithMargins
    private val density = Density(2f)
    private val renderDpi = 144f // 2px per pt

    private val reportDir = File("build/reports/fidelity")
    private val imagesDir = File(reportDir, "images")

    private val header: @Composable (PdfPageInfo) -> Unit = {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1565C0)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Acme Corp", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }

    private val footer: @Composable (PdfPageInfo) -> Unit = { info ->
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFE3F2FD)).padding(6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text("Page ${info.pageNumber} of ${info.pageCount}", fontSize = 9.sp, color = Color(0xFF555555))
        }
    }

    @Test
    fun `header and footer bands render on every page in both modes`() {
        imagesDir.mkdirs()

        for (mode in RenderMode.entries) {
            val modeName = mode.name.lowercase()
            val pdfBytes = renderToPdf(
                config = config, density = density, mode = mode,
                header = header, footer = footer,
            ) {
                for (i in 1..40) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Row $i - fidelity data item", Modifier.weight(2f), fontSize = 11.sp)
                        Text("${i * 42}", Modifier.weight(1f), fontSize = 11.sp)
                    }
                }
            }
            File(imagesDir, "header-footer-$modeName.pdf").writeBytes(pdfBytes)

            Loader.loadPDF(pdfBytes).use { doc ->
                assertTrue(doc.numberOfPages >= 2, "$mode: expected multi-page output, got ${doc.numberOfPages}")
                for (i in 0 until doc.numberOfPages) {
                    val img = rasterizePdf(doc, renderDpi, page = i)
                    saveImage(img, imagesDir, "header-footer-$modeName-p$i.png")

                    // At 144 DPI: 1pt = 2px. Margins are 72pt -> 144px. Sample the band centers.
                    val xMid = img.width / 2
                    val headerY = 144 + 20 // ~10pt into the header band
                    val footerY = img.height - 144 - 20 // ~10pt above the bottom margin
                    assertFalse(img.isWhitishAt(xMid, headerY), "$mode page $i: header band should be drawn")
                    assertFalse(img.isWhitishAt(xMid, footerY), "$mode page $i: footer band should be drawn")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run the fidelity test**

Run: `./gradlew :fidelity-test:test --tests '*HeaderFooterFidelityTest*'`
Expected: PASS (both modes). Then run the FULL fidelity suite to confirm existing fixtures are untouched:

Run: `./gradlew :fidelity-test:test --rerun-tasks`
Expected: all PASS.

- [ ] **Step 3: Write the example**

Create `examples/src/main/kotlin/com/chrisjenx/compose2pdf/examples/13_HeaderFooter.kt` (open a neighboring example, e.g. `11_AutoPagination.kt`, and match its imports/file layout exactly):

```kotlin
package com.chrisjenx.compose2pdf.examples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.renderToPdf

fun headerFooter(): ExampleOutput {
    // --- snippet start ---
    val pdfBytes = renderToPdf(
        config = PdfPageConfig.A4WithMargins,
        // Stamped at the top of every page; height is measured once and reserved uniformly
        header = {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF1565C0)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Acme Corp — Q2 Report", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        // Receives PdfPageInfo for page numbering
        footer = { info ->
            Row(
                Modifier.fillMaxWidth().padding(6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Page ${info.pageNumber} of ${info.pageCount}", fontSize = 9.sp, color = Color.Gray)
            }
        },
    ) {
        for (i in 1..60) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Line item $i", Modifier.weight(2f), fontSize = 11.sp)
                Text("$${i * 42}.00", Modifier.weight(1f), fontSize = 11.sp)
            }
        }
    }
    // --- snippet end ---
    return ExampleOutput("13-header-footer", "13_HeaderFooter.kt", pdfBytes)
}
```

In `Main.kt`, add to the `examples` list after `::paginatedColumnExample`:

```kotlin
        ::headerFooter,
```

- [ ] **Step 4: Run the examples**

Run: `./gradlew :examples:run`
Expected: output lists `13-header-footer.pdf` with 2+ pages, no ERROR lines. Spot-check `examples/build/output/images/13-header-footer-1.png` (header band on page 1, "Page 1 of N" at the bottom).

- [ ] **Step 5: Commit**

```bash
git add fidelity-test/src examples/src
git commit -m "test: header/footer fidelity coverage; add header/footer example"
```

---

### Task 8: Documentation

**Files:**
- Modify: `docs/usage/auto-pagination.md` (add a "Headers and footers" section)
- Modify: `docs/usage/index.md` (~line 33, Topics table: extend the Auto-pagination row description)
- Modify: `README.md` (API/features listing — add the slots to wherever `renderToPdf` parameters/features are described)
- Modify: `CLAUDE.md` (Public API section + Gotchas)
- Modify: `docs/changelog.md` (new entry, following the existing version-section format)

**Interfaces:**
- Consumes: final API from Tasks 3/5/6. No code interfaces produced.

- [ ] **Step 1: docs/usage/auto-pagination.md — add section**

Append this section (before any "See also"/final section if present, otherwise at the end; keep the page's existing heading style):

```markdown
---

## Headers and footers

Add a repeated header and/or footer band to every page with the `header` and `footer`
slots. Both receive a `PdfPageInfo` with `pageIndex` (zero-based), `pageCount`, and a
convenience `pageNumber` (one-based):

```kotlin
val pdf = renderToPdf(
    config = PdfPageConfig.A4WithMargins,
    header = {
        Row(Modifier.fillMaxWidth().background(Color(0xFF1565C0)).padding(10.dp)) {
            Text("Acme Corp", color = Color.White, fontWeight = FontWeight.Bold)
        }
    },
    footer = { info ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Page ${info.pageNumber} of ${info.pageCount}", fontSize = 9.sp)
        }
    },
) {
    // body content — auto-paginated between the bands
}
```

### How the space works

- Each slot's height is **measured once** and that height is reserved uniformly on
  **every** page. The body content area shrinks accordingly, and `LocalPdfPageConfig`
  (and therefore `PaginatedColumn`) reflects the reduced area automatically.
- Slot height must be **stable across pages** — content taller than the measured band
  is clipped. Measurement uses a `pageCount = 2` sentinel, so a footer wrapped in
  `if (info.pageCount > 1) { ... }` still reserves its space.
- A header + footer that leave no room for content throw `IllegalArgumentException`.
- Slots work in both `VECTOR` and `RASTER` modes, with `PdfPagination.SINGLE_PAGE`,
  and on single-page documents (`pageCount == 1`).
- `PdfLink` works inside slots.
- Want a large masthead only on page 1? Keep it in the body content — the `header`
  slot is for the repeated band. If pagination truncates at the 100-page cap,
  `pageCount` reflects the emitted pages.
```

- [ ] **Step 2: docs/usage/index.md — update the Topics table row**

Change the Auto-pagination row description to:

```markdown
| [Auto-pagination]({{ site.baseurl }}/usage/auto-pagination) | Automatic page splitting, per-page headers/footers with page numbers |
```

- [ ] **Step 3: README.md**

Find the section listing `renderToPdf` capabilities/API (grep `renderToPdf(`; usages near lines 10/77/93/105) and add a short feature mention with a minimal snippet in the auto-pagination area:

```kotlin
// Per-page header/footer bands with page numbers
val pdf = renderToPdf(
    config = PdfPageConfig.A4WithMargins,
    footer = { info -> Text("Page ${info.pageNumber} of ${info.pageCount}") },
) { /* content */ }
```

- [ ] **Step 4: CLAUDE.md**

In `## Public API`, update the first two signatures and the types list:

```kotlin
renderToPdf(config, density, mode, defaultFontFamily, pagination, header, footer) { content } → ByteArray  // auto-paginates by default
renderToPdf(outputStream, config, density, mode, defaultFontFamily, pagination, header, footer) { content }  // streaming variant
```

Add `PdfPageInfo` to the Types line. In `### Auto-pagination` gotchas add:

```markdown
- **Header/footer slots reserve uniform bands** — slot heights measured once with a `PdfPageInfo(0, 2)` sentinel; height must be stable across pages (taller content is clipped). With slots present, `LocalPdfPageConfig` exposes the *effective* (band-reduced) content area, which is what keeps the public `PaginatedColumn` breaking at the right height. Null slots take the exact pre-slots code path (fidelity guarantee), pinned by `NullSlotRegressionTest` golden files — if an intentional render change breaks it, delete `compose2pdf/src/test/resources/golden/` and re-run twice.
```

In `### Rendering` gotchas add:

```markdown
- **Raster slices draw top-aligned at proportional height** — `addBitmapPage(topPt, heightPt)`; a partial last slice must never be stretched to the full content rect.
```

- [ ] **Step 5: docs/changelog.md**

Read the file and prepend an entry under the current unreleased/next version heading, matching the existing heading and bullet style, with exactly these two bullets:

```markdown
- **Per-page headers and footers** — new `header`/`footer` slots on the auto-pagination `renderToPdf` overloads. Slots receive `PdfPageInfo` (`pageIndex`, `pageCount`, `pageNumber`) for "Page X of Y" numbering; slot height is measured once and reserved uniformly on every page.
- **Fixed**: raster auto-pagination no longer stretches the last partial page slice to the full content height.
```

- [ ] **Step 6: Verify docs build (optional, if bundler available) and run full check**

Run: `./gradlew :compose2pdf:test :fidelity-test:test`
Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add docs README.md CLAUDE.md
git commit -m "docs: document per-page header/footer slots"
```

---

## Final verification (after all tasks)

- [ ] `./gradlew :compose2pdf:build :fidelity-test:test :examples:run` — everything green.
- [ ] Confirm `git log` shows one commit per task on `worktree-feature-page-header-footer`.
- [ ] Push the branch and open a draft PR referencing `specs/2026-07-16-page-header-footer-design.md`.
