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
import com.chrisjenx.compose2pdf.LocalPdfLinkCollector
import com.chrisjenx.compose2pdf.PdfLinkAnnotation
import com.chrisjenx.compose2pdf.PdfLinkCollector
import com.chrisjenx.compose2pdf.PdfPageConfig
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
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal object PdfRenderer {

    fun renderSinglePage(
        config: PdfPageConfig,
        density: Density,
        mode: RenderMode,
        defaultFontFamily: FontFamily?,
        content: @Composable () -> Unit,
    ): ByteArray {
        return renderMultiPage(
            pageCount = 1,
            config = config,
            density = density,
            mode = mode,
            defaultFontFamily = defaultFontFamily,
            content = { content() },
        )
    }

    fun renderMultiPage(
        pageCount: Int,
        config: PdfPageConfig,
        density: Density,
        mode: RenderMode,
        defaultFontFamily: FontFamily?,
        content: @Composable (pageIndex: Int) -> Unit,
    ): ByteArray {
        require(pageCount > 0) { "pageCount must be positive, was $pageCount" }
        return when (mode) {
            RenderMode.VECTOR -> renderVector(pageCount, config, density, defaultFontFamily, content)
            RenderMode.RASTER -> renderRaster(pageCount, config, density, defaultFontFamily, content)
        }
    }

    // --- Vector path: Compose → PictureRecorder → SVGCanvas → SVG → PDF ---

    private fun renderVector(
        pageCount: Int,
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        content: @Composable (pageIndex: Int) -> Unit,
    ): ByteArray {
        val pxW = (config.contentWidth.value * density.density).toInt()
        val pxH = (config.contentHeight.value * density.density).toInt()

        val pdfDoc = PDDocument()
        val fontCache = mutableMapOf<String, org.apache.pdfbox.pdmodel.font.PDFont>()
        val imageCache = mutableMapOf<String, org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject>()
        try {
            for (pageIndex in 0 until pageCount) {
                val linkCollector = PdfLinkCollector()
                val svg = ComposeToSvg.render(pxW, pxH, density) {
                    WrapContent(defaultFontFamily, linkCollector) {
                        content(pageIndex)
                    }
                }
                SvgToPdfConverter.addPage(pdfDoc, svg, config.width.value, config.height.value, fontCache, imageCache)
                addLinkAnnotations(pdfDoc.getPage(pageIndex), config, linkCollector.links)
            }
            val baos = ByteArrayOutputStream()
            pdfDoc.save(baos)
            return baos.toByteArray()
        } finally {
            pdfDoc.close()
        }
    }

    // --- Raster path: Compose → ImageComposeScene → bitmap → PDF ---

    private fun renderRaster(
        pageCount: Int,
        config: PdfPageConfig,
        density: Density,
        defaultFontFamily: FontFamily?,
        content: @Composable (pageIndex: Int) -> Unit,
    ): ByteArray {
        val contentWidthPx = (config.contentWidth.value * density.density).toInt()
        val contentHeightPx = (config.contentHeight.value * density.density).toInt()

        val doc = PDDocument()
        try {
            for (pageIndex in 0 until pageCount) {
                val linkCollector = PdfLinkCollector()
                val bitmap = renderComposeToBitmap(
                    width = contentWidthPx,
                    height = contentHeightPx,
                    density = density,
                    content = {
                        WrapContent(defaultFontFamily, linkCollector) {
                            content(pageIndex)
                        }
                    },
                )
                addBitmapPage(doc, config, bitmap)
                addLinkAnnotations(doc.getPage(pageIndex), config, linkCollector.links)
            }
            val baos = ByteArrayOutputStream()
            doc.save(baos)
            return baos.toByteArray()
        } finally {
            doc.close()
        }
    }

    @Composable
    private fun WrapContent(
        defaultFontFamily: FontFamily?,
        linkCollector: PdfLinkCollector,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(LocalPdfLinkCollector provides linkCollector) {
            if (defaultFontFamily != null) {
                ProvideTextStyle(TextStyle(fontFamily = defaultFontFamily)) {
                    content()
                }
            } else {
                content()
            }
        }
    }

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
        val data = image.encodeToData() ?: error("Failed to encode Skia Image to PNG")
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
            val annotation = PDAnnotationLink()
            val action = PDActionURI()
            action.uri = link.href
            annotation.action = action

            annotation.rectangle = CoordinateTransform.svgToPdfRect(
                svgX = link.x,
                svgY = link.y,
                width = link.width,
                height = link.height,
                pageHeight = config.height.value,
                marginLeft = config.margins.left.value,
                marginTop = config.margins.top.value,
            )

            // Invisible border (the content provides visual styling)
            val borderStyle = PDBorderStyleDictionary()
            borderStyle.width = 0f
            annotation.borderStyle = borderStyle

            page.annotations.add(annotation)
        }
    }
}
