@file:OptIn(InternalComposeUiApi::class)

package com.chrisjenx.compose2pdf.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material.ProvideTextStyle
import androidx.compose.ui.text.TextStyle
import com.chrisjenx.compose2pdf.Compose2PdfException
import com.chrisjenx.compose2pdf.LocalPdfLinkCollector
import com.chrisjenx.compose2pdf.LocalPdfPageConfig
import com.chrisjenx.compose2pdf.PdfLinkAnnotation
import com.chrisjenx.compose2pdf.PdfLinkCollector
import com.chrisjenx.compose2pdf.PdfPageConfig
import com.chrisjenx.compose2pdf.PdfPageInfo
import com.chrisjenx.compose2pdf.PdfPagination
import com.chrisjenx.compose2pdf.RenderMode
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

internal object PdfRenderer {

    private val logger = java.util.logging.Logger.getLogger(PdfRenderer::class.java.name)

    private const val MAX_AUTO_PAGES = 100
    // Compose Constraints max dimension is ~262143px. Stay well under that limit.
    private const val MAX_MEASURE_HEIGHT_PX = 200_000
    /** Gap between a header/footer band and the body content, in PDF points. */
    private const val SLOT_BODY_GAP_PT = 10f
    /** Printable safety margin from the physical page edge to a header/footer band, in PDF points (~0.25in). */
    private const val SLOT_EDGE_INSET_PT = 18f

    /**
     * Renders single-page content to a [PDDocument]. The caller is responsible for
     * saving and closing the returned document.
     */
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

    /**
     * Measured header/footer bands plus the effective page config for the body.
     * Each effective margin is `max(configured margin, SLOT_EDGE_INSET_PT + band height +
     * SLOT_BODY_GAP_PT)` — equal to the configured margin when the band fits within it, and
     * larger only when the band + inset + gap exceeds the configured margin.
     * [effectiveContentHeightPx] is `effectiveConfig.contentHeightPx(density)`, cached here so
     * callers don't each recompute it.
     */
    internal class SlotBands(
        val headerPx: Int,
        val footerPx: Int,
        val headerPt: Float,
        val footerPt: Float,
        val effectiveConfig: PdfPageConfig,
        val effectiveContentHeightPx: Int,
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
        val bodyTopPt = effectiveMarginPt(config.margins.top.value, headerPx, headerPt)
        val bodyBottomPt = effectiveMarginPt(config.margins.bottom.value, footerPx, footerPt)
        // Compute the effective body height in px (floored, same as the body's actual render
        // resolution) BEFORE building effectiveConfig. A pt-space guard alone is not enough:
        // the body renders in px, so a sub-pixel remainder (0 < remaining pt < 1/density pt)
        // would pass a pt-space check yet floor to 0px, producing an opaque failure deep in
        // the render pipeline instead of a clear error here.
        val effectiveContentHeightPx = ((config.height.value - bodyTopPt - bodyBottomPt) * density.density).toInt()
        require(effectiveContentHeightPx > 0) {
            "Header (${headerPt}pt) and footer (${footerPt}pt) bands leave no room for page content " +
                "on a ${config.height.value}pt-tall page at density ${density.density}"
        }
        val effectiveConfig = config.copy(
            margins = config.margins.copy(top = bodyTopPt.dp, bottom = bodyBottomPt.dp)
        )
        return SlotBands(headerPx, footerPx, headerPt, footerPt, effectiveConfig, effectiveContentHeightPx)
    }

    /**
     * The effective margin for one side: equal to [configuredPt] when there's no band on that
     * side, or when the band fits within it; otherwise grown just enough to fit
     * [SLOT_EDGE_INSET_PT] + the band + [SLOT_BODY_GAP_PT].
     */
    private fun effectiveMarginPt(configuredPt: Float, bandPx: Int, bandPt: Float): Float =
        if (bandPx > 0) maxOf(configuredPt, SLOT_EDGE_INSET_PT + bandPt + SLOT_BODY_GAP_PT) else configuredPt

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
    private fun bodyLayout(density: Density, bands: SlotBands): PageLayout =
        PageLayout.from(bands.effectiveConfig).copy(
            contentHeightPt = bands.effectiveContentHeightPx / density.density,
        )

    /** PageLayout describing a slot band ([bandHeightPt] tall, [marginTopPt] from the page top). */
    private fun slotLayout(config: PdfPageConfig, bandHeightPt: Float, marginTopPt: Float): PageLayout =
        PageLayout.from(config).copy(contentHeightPt = bandHeightPt, marginTopPt = marginTopPt)

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
        val effectivePx = bands.effectiveContentHeightPx
        val bodyTopPt = bands.effectiveConfig.margins.top.value
        val bodyHeightPt = effectivePx / density.density
        val bodyLinks = PdfLinkCollector()
        val doc = PDDocument()

        val measuredHeightPx: Int? = if (pagination == PdfPagination.SINGLE_PAGE) null
            else measureForPagination(bands.effectiveConfig, density, defaultFontFamily, content)

        if (measuredHeightPx == null) {
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

    /** Which band a stamped slot is: picks the vector image cache in [stampSlotsVector]. */
    private enum class SlotKind { HEADER, FOOTER }

    /**
     * Shared per-page iteration for the with-slots stamping paths ([stampSlotsVector] and
     * [stampSlotsRaster]): for each page, builds the real (non-sentinel) [PdfPageInfo] and
     * invokes [stampBand] for the header then the footer, skipping either when absent or
     * when its measured band is zero-height. The mode-specific drawing (raster bitmap vs.
     * vector SVG) lives in [stampBand]; [stampBand]'s [SlotKind] lets the vector path pick a
     * per-slot-kind image cache (see [stampSlotsVector]) — raster ignores it. Does NOT touch
     * the null-slot dispatch in [renderSinglePage] or the plain render paths — those stay
     * untouched.
     */
    private fun stampSlotsOnEachPage(
        doc: PDDocument,
        config: PdfPageConfig,
        bands: SlotBands,
        pageCount: Int,
        header: (@Composable (PdfPageInfo) -> Unit)?,
        footer: (@Composable (PdfPageInfo) -> Unit)?,
        stampBand: (
            page: PDPage,
            info: PdfPageInfo,
            slot: @Composable (PdfPageInfo) -> Unit,
            bandHeightPx: Int,
            topPt: Float,
            heightPt: Float,
            kind: SlotKind,
        ) -> Unit,
    ) {
        // Header sits near the top edge, SLOT_EDGE_INSET_PT below it; footer sits near the
        // bottom edge, SLOT_EDGE_INSET_PT above it. Both are page-invariant.
        val headerTopPt = SLOT_EDGE_INSET_PT
        val footerTopPt = config.height.value - SLOT_EDGE_INSET_PT - bands.footerPt
        for (pageIndex in 0 until pageCount) {
            val info = PdfPageInfo(pageIndex, pageCount)
            val page = doc.getPage(pageIndex)
            if (header != null && bands.headerPx > 0) {
                stampBand(page, info, header, bands.headerPx, headerTopPt, bands.headerPt, SlotKind.HEADER)
            }
            if (footer != null && bands.footerPx > 0) {
                stampBand(page, info, footer, bands.footerPx, footerTopPt, bands.footerPt, SlotKind.FOOTER)
            }
        }
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
        stampSlotsOnEachPage(doc, config, bands, pageCount, header, footer) { page, info, slot, bandHeightPx, topPt, heightPt, _ ->
            stampSlotRaster(doc, page, config, density, defaultFontFamily, slot, info, bandHeightPx, topPt, heightPt)
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
        val cs = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
        try {
            drawBitmapInto(cs, doc, config, bitmap, topPt, heightPt)
        } finally {
            cs.close()
        }
        stampLinks(page, config.height.value, config.margins.left.value, topPt, linkCollector.links)
    }

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
        val effectivePx = bands.effectiveContentHeightPx
        val layout = bodyLayout(density, bands)

        val pdfDoc = PDDocument()
        val fontCache = mutableMapOf<String, org.apache.pdfbox.pdmodel.font.PDFont>()
        // The body is rendered as its own SVG document (or one tall auto-paginated document),
        // separate from the header/footer bands. Skia restarts element ids per document, so
        // each document needs its OWN image cache — sharing one across documents would let a
        // later document's `img0` incorrectly resolve to an earlier document's cached image.
        // See stampSlotsVector for the header/footer caches.
        val bodyImageCache = mutableMapOf<String, org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject>()
        val bodyLinks = PdfLinkCollector()

        val measuredHeightPx: Int? = if (pagination == PdfPagination.SINGLE_PAGE) null
            else measureForPagination(bands.effectiveConfig, density, defaultFontFamily, content)

        val pageCount: Int
        if (measuredHeightPx == null) {
            val svg = ComposeToSvg.render(contentWidthPx, effectivePx, density) {
                WrapContent(defaultFontFamily, bodyLinks, bands.effectiveConfig) { content() }
            }
            SvgToPdfConverter.addPage(pdfDoc, svg, layout, density.density, fontCache, bodyImageCache)
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
            SvgToPdfConverter.addAutoPages(
                pdfDoc, result.svg, layout, totalContentHeightPt,
                density.density, MAX_AUTO_PAGES, fontCache, bodyImageCache,
            )
            pageCount = pdfDoc.numberOfPages
        }

        distributeLinks(pdfDoc, config, bodyLinks.links, layout.contentHeightPt, marginTopPt = layout.marginTopPt)
        stampSlotsVector(pdfDoc, config, density, defaultFontFamily, header, footer, bands, pageCount, fontCache)
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
    ) {
        // Header and footer are each rendered as their OWN SVG document per page (see
        // stampSlotVector), separate from the body and from each other. Each therefore gets
        // its own image cache: Skia restarts element ids per document, so a shared cache
        // could let e.g. a footer's `img0` resolve to a cached header image. fontCache stays
        // shared — it's keyed semantically by family/weight/style, not by per-document id.
        val headerImageCache = mutableMapOf<String, org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject>()
        val footerImageCache = mutableMapOf<String, org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject>()
        stampSlotsOnEachPage(pdfDoc, config, bands, pageCount, header, footer) { page, info, slot, bandHeightPx, topPt, heightPt, kind ->
            val bandLayout = slotLayout(config, bandHeightPt = heightPt, marginTopPt = topPt)
            val imageCache = if (kind == SlotKind.HEADER) headerImageCache else footerImageCache
            stampSlotVector(pdfDoc, page, config, density, defaultFontFamily, slot, info, bandHeightPx, bandLayout, fontCache, imageCache)
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
        stampLinks(page, bandLayout.pageHeightPt, bandLayout.marginLeftPt, bandLayout.marginTopPt, linkCollector.links)
    }

    /** Adds one link annotation per [links] entry, anchored at ([marginLeftPt], [marginTopPt]). */
    private fun stampLinks(
        page: PDPage,
        pageHeightPt: Float,
        marginLeftPt: Float,
        marginTopPt: Float,
        links: List<PdfLinkAnnotation>,
    ) {
        for (link in links) {
            addLinkToPage(
                page, pageHeightPt, marginLeftPt, marginTopPt,
                link.href, link.x, link.y, link.width, link.height,
            )
        }
    }

    /**
     * Renders multi-page content to a [PDDocument]. The caller is responsible for
     * saving and closing the returned document.
     */
    fun renderMultiPage(
        pageCount: Int,
        config: PdfPageConfig,
        density: Density,
        mode: RenderMode,
        defaultFontFamily: FontFamily?,
        content: @Composable (pageIndex: Int) -> Unit,
    ): PDDocument {
        require(pageCount > 0) { "pageCount must be positive, was $pageCount" }
        return when (mode) {
            RenderMode.VECTOR -> renderVector(pageCount, config, density, defaultFontFamily, content)
            RenderMode.RASTER -> renderRaster(pageCount, config, density, defaultFontFamily, content)
        }
    }

    // --- Auto-pagination ---

    /**
     * Measures content height to determine if pagination is needed.
     * Returns the measured height in pixels, or null if content fits on one page
     * or uses fillMaxHeight (should fall back to single-page rendering).
     */
    private fun measureForPagination(
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        content: @Composable () -> Unit,
    ): Int? {
        val contentWidthPx = config.contentWidthPx(density)
        val contentHeightPx = config.contentHeightPx(density)

        val measuredHeightPx = ComposeToSvg.measureContentHeight(contentWidthPx, MAX_MEASURE_HEIGHT_PX, density) {
            WrapContent(defaultFontFamily, null, config) {
                PaginatedColumn(contentHeightPx = contentHeightPx) {
                    content()
                }
            }
        }

        // Fits on one page or uses fillMaxHeight → caller should use single-page path
        if (measuredHeightPx <= contentHeightPx || measuredHeightPx >= MAX_MEASURE_HEIGHT_PX) {
            return null
        }
        return measuredHeightPx
    }

    private fun renderAutoVector(
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        content: @Composable () -> Unit,
    ): PDDocument {
        val measuredHeightPx = measureForPagination(config, density, defaultFontFamily, content)
            ?: return renderVector(1, config, density, defaultFontFamily) { content() }

        val contentWidthPx = config.contentWidthPx(density)
        val linkCollector = PdfLinkCollector()
        val result = ComposeToSvg.renderWithMeasurement(contentWidthPx, MAX_MEASURE_HEIGHT_PX, density) {
            WrapContent(defaultFontFamily, linkCollector, config) {
                PaginatedColumn(contentHeightPx = config.contentHeightPx(density)) {
                    content()
                }
            }
        }

        val pageLayout = PageLayout.from(config)
        val totalContentHeightPt = result.measuredHeightPx.coerceAtLeast(1) / density.density
        val estimatedPages = kotlin.math.ceil(totalContentHeightPt.toDouble() / pageLayout.contentHeightPt).toInt()
        if (estimatedPages > MAX_AUTO_PAGES) {
            logger.warning(
                "Auto-pagination truncated: content requires ~$estimatedPages pages but max is $MAX_AUTO_PAGES"
            )
        }

        val pdfDoc = PDDocument()
        val fontCache = mutableMapOf<String, org.apache.pdfbox.pdmodel.font.PDFont>()
        val imageCache = mutableMapOf<String, org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject>()
        SvgToPdfConverter.addAutoPages(
            pdfDoc, result.svg, pageLayout, totalContentHeightPt,
            density.density, MAX_AUTO_PAGES, fontCache, imageCache,
        )

        distributeLinks(pdfDoc, config, linkCollector.links, config.contentHeight.value)

        return pdfDoc
    }

    private fun renderAutoRaster(
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        content: @Composable () -> Unit,
    ): PDDocument {
        val measuredHeightPx = measureForPagination(config, density, defaultFontFamily, content)
            ?: return renderRaster(1, config, density, defaultFontFamily) { content() }

        val contentWidthPx = config.contentWidthPx(density)
        val contentHeightPx = config.contentHeightPx(density)
        val linkCollector = PdfLinkCollector()
        val fullBitmap = renderComposeToBitmap(contentWidthPx, measuredHeightPx, density) {
            WrapContent(defaultFontFamily, linkCollector, config) {
                PaginatedColumn(contentHeightPx = contentHeightPx) {
                    content()
                }
            }
        }

        val rawPageCount = kotlin.math.ceil(measuredHeightPx.toFloat() / contentHeightPx.toFloat()).toInt()
        if (rawPageCount > MAX_AUTO_PAGES) {
            logger.warning(
                "Auto-pagination truncated: content requires ~$rawPageCount pages but max is $MAX_AUTO_PAGES"
            )
        }
        val pageCount = rawPageCount.coerceIn(1, MAX_AUTO_PAGES)

        val doc = PDDocument()
        for (pageIndex in 0 until pageCount) {
            val sliceTop = pageIndex * contentHeightPx
            val sliceHeight = minOf(contentHeightPx, measuredHeightPx - sliceTop)
            if (sliceHeight <= 0) break
            val slice = fullBitmap.getSubimage(0, sliceTop, contentWidthPx, sliceHeight)
            addBitmapPage(
                doc, config, slice,
                heightPt = config.contentHeight.value * sliceHeight / contentHeightPx,
            )
        }

        distributeLinks(doc, config, linkCollector.links, config.contentHeight.value)

        return doc
    }

    // --- Manual multi-page ---

    private fun renderVector(
        pageCount: Int,
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        content: @Composable (pageIndex: Int) -> Unit,
    ): PDDocument {
        val pxW = config.contentWidthPx(density)
        val pxH = config.contentHeightPx(density)

        val pdfDoc = PDDocument()
        val fontCache = mutableMapOf<String, org.apache.pdfbox.pdmodel.font.PDFont>()
        val pageLayout = PageLayout.from(config)
        for (pageIndex in 0 until pageCount) {
            val linkCollector = PdfLinkCollector()
            val svg = ComposeToSvg.render(pxW, pxH, density) {
                WrapContent(defaultFontFamily, linkCollector, config) {
                    content(pageIndex)
                }
            }
            // Each page is rendered as its own separate SVG document, and Skia's SVGCanvas
            // restarts element ids (e.g. `img0`) from zero per document — so the image cache
            // must be per-page too, or page N could wrongly resolve to page N-1's image.
            val imageCache = mutableMapOf<String, org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject>()
            SvgToPdfConverter.addPage(pdfDoc, svg, pageLayout, density.density, fontCache, imageCache)
            addLinkAnnotations(pdfDoc.getPage(pageIndex), config, linkCollector.links)
        }
        return pdfDoc
    }

    private fun renderRaster(
        pageCount: Int,
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        content: @Composable (pageIndex: Int) -> Unit,
    ): PDDocument {
        val contentWidthPx = config.contentWidthPx(density)
        val contentHeightPx = config.contentHeightPx(density)

        val doc = PDDocument()
        for (pageIndex in 0 until pageCount) {
            val linkCollector = PdfLinkCollector()
            val bitmap = renderComposeToBitmap(
                width = contentWidthPx,
                height = contentHeightPx,
                density = density,
                content = {
                    WrapContent(defaultFontFamily, linkCollector, config) {
                        content(pageIndex)
                    }
                },
            )
            addBitmapPage(doc, config, bitmap)
            addLinkAnnotations(doc.getPage(pageIndex), config, linkCollector.links)
        }
        return doc
    }

    // --- Content wrapping ---

    @Composable
    private fun WrapContent(
        defaultFontFamily: FontFamily?,
        linkCollector: PdfLinkCollector?,
        config: PdfPageConfig? = null,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(
            LocalPdfLinkCollector provides linkCollector,
            LocalPdfPageConfig provides config,
        ) {
            if (defaultFontFamily != null) {
                ProvideTextStyle(TextStyle(fontFamily = defaultFontFamily)) {
                    content()
                }
            } else {
                content()
            }
        }
    }

    // --- Bitmap rendering ---

    private fun renderComposeToBitmap(
        width: Int,
        height: Int,
        density: Density,
        content: @Composable () -> Unit,
    ): BufferedImage {
        val scene = ImageComposeScene(
            width = width,
            height = height,
            density = density,
            content = content,
        )
        try {
            val image = scene.render()
            return skiaImageToBufferedImage(image)
        } finally {
            scene.close()
        }
    }

    private fun skiaImageToBufferedImage(image: org.jetbrains.skia.Image): BufferedImage {
        val data = image.encodeToData()
            ?: throw Compose2PdfException("Failed to encode Skia image to PNG data")
        return ImageIO.read(ByteArrayInputStream(data.bytes))
    }

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

        val contentStream = PDPageContentStream(doc, page)
        try {
            drawBitmapInto(contentStream, doc, config, bitmap, topPt, heightPt)
        } finally {
            contentStream.close()
        }
    }

    /** Embeds [bitmap] into the content area ([config], [topPt], [heightPt]) via [cs]. */
    private fun drawBitmapInto(
        cs: PDPageContentStream,
        doc: PDDocument,
        config: PdfPageConfig,
        bitmap: BufferedImage,
        topPt: Float,
        heightPt: Float,
    ) {
        val pdImage = LosslessFactory.createFromImage(doc, bitmap)
        cs.drawImage(
            pdImage,
            config.margins.left.value,
            config.height.value - topPt - heightPt,
            config.contentWidth.value,
            heightPt,
        )
    }

    // --- Link annotations ---

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

    // --- Helpers ---

    private fun PdfPageConfig.contentWidthPx(density: Density): Int =
        (contentWidth.value * density.density).toInt()

    private fun PdfPageConfig.contentHeightPx(density: Density): Int =
        (contentHeight.value * density.density).toInt()
}
