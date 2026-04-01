@file:OptIn(InternalComposeUiApi::class)

package com.chrisjenx.compose2pdf.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.material.ProvideTextStyle
import androidx.compose.ui.text.TextStyle
import com.chrisjenx.compose2pdf.Compose2PdfException
import com.chrisjenx.compose2pdf.LocalPdfLinkCollector
import com.chrisjenx.compose2pdf.LocalPdfPageConfig
import com.chrisjenx.compose2pdf.PdfLinkAnnotation
import com.chrisjenx.compose2pdf.PdfLinkCollector
import com.chrisjenx.compose2pdf.PdfPageConfig
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
        content: @Composable () -> Unit,
    ): PDDocument {
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
            addBitmapPage(doc, config, slice)
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
        val imageCache = mutableMapOf<String, org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject>()
        for (pageIndex in 0 until pageCount) {
            val linkCollector = PdfLinkCollector()
            val svg = ComposeToSvg.render(pxW, pxH, density) {
                WrapContent(defaultFontFamily, linkCollector, config) {
                    content(pageIndex)
                }
            }
            SvgToPdfConverter.addPage(pdfDoc, svg, config.width.value, config.height.value, fontCache, imageCache)
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
                config.margins.bottom.value,
                config.contentWidth.value,
                config.contentHeight.value,
            )
        } finally {
            contentStream.close()
        }
    }

    // --- Link annotations ---

    private fun addLinkAnnotations(
        page: PDPage,
        config: PdfPageConfig,
        links: List<PdfLinkAnnotation>,
    ) {
        if (links.isEmpty()) return
        for (link in links) {
            addLinkToPage(page, config, link.href, link.x, link.y, link.width, link.height)
        }
    }

    private fun distributeLinks(
        doc: PDDocument,
        config: PdfPageConfig,
        links: List<PdfLinkAnnotation>,
        contentHeightPt: Float,
    ) {
        if (links.isEmpty()) return
        for (link in links) {
            val pageIndex = (link.y / contentHeightPt).toInt()
                .coerceIn(0, doc.numberOfPages - 1)
            val adjustedY = link.y - pageIndex * contentHeightPt
            addLinkToPage(doc.getPage(pageIndex), config, link.href, link.x, adjustedY, link.width, link.height)
        }
    }

    private fun addLinkToPage(
        page: PDPage,
        config: PdfPageConfig,
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
            pageHeight = config.height.value,
            marginLeft = config.margins.left.value,
            marginTop = config.margins.top.value,
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
