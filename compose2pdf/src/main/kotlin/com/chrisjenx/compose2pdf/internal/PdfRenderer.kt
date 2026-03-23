@file:OptIn(InternalComposeUiApi::class)

package com.chrisjenx.compose2pdf.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.material.ProvideTextStyle
import androidx.compose.ui.text.TextStyle
import com.chrisjenx.compose2pdf.LocalPdfLinkCollector
import com.chrisjenx.compose2pdf.PdfFontFamily
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
        useBundledFont: Boolean,
        content: @Composable () -> Unit,
    ): ByteArray {
        return renderMultiPage(
            pageCount = 1,
            config = config,
            density = density,
            mode = mode,
            useBundledFont = useBundledFont,
            content = { content() },
        )
    }

    fun renderMultiPage(
        pageCount: Int,
        config: PdfPageConfig,
        density: Density,
        mode: RenderMode,
        useBundledFont: Boolean,
        content: @Composable (pageIndex: Int) -> Unit,
    ): ByteArray {
        require(pageCount > 0) { "pageCount must be positive, was $pageCount" }
        return when (mode) {
            RenderMode.VECTOR -> renderVector(pageCount, config, density, useBundledFont, content)
            RenderMode.RASTER -> renderRaster(pageCount, config, density, useBundledFont, content)
        }
    }

    // --- Vector path: Compose → PictureRecorder → SVGCanvas → SVG → PDF ---

    private fun renderVector(
        pageCount: Int,
        config: PdfPageConfig,
        density: Density,
        useBundledFont: Boolean,
        content: @Composable (pageIndex: Int) -> Unit,
    ): ByteArray {
        val pxW = (config.contentWidth.value * density.density).toInt()
        val pxH = (config.contentHeight.value * density.density).toInt()

        val pdfDoc = PDDocument()
        val fontCache = mutableMapOf<String, org.apache.pdfbox.pdmodel.font.PDFont>()
        try {
            for (pageIndex in 0 until pageCount) {
                val linkCollector = PdfLinkCollector()
                val svg = ComposeToSvg.render(pxW, pxH, density) {
                    CompositionLocalProvider(
                        LocalPdfLinkCollector provides linkCollector,
                    ) {
                        if (useBundledFont) {
                            ProvideTextStyle(TextStyle(fontFamily = PdfFontFamily)) {
                                content(pageIndex)
                            }
                        } else {
                            content(pageIndex)
                        }
                    }
                }
                SvgToPdfConverter.addPage(pdfDoc, svg, config.width.value, config.height.value, fontCache)
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
        useBundledFont: Boolean,
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
                        CompositionLocalProvider(
                            LocalPdfLinkCollector provides linkCollector,
                        ) {
                            if (useBundledFont) {
                                ProvideTextStyle(TextStyle(fontFamily = PdfFontFamily)) {
                                    content(pageIndex)
                                }
                            } else {
                                content(pageIndex)
                            }
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
        val pageHeight = config.height.value
        val marginLeft = config.margins.left.value
        val marginTop = config.margins.top.value

        for (link in links) {
            val annotation = PDAnnotationLink()
            val action = PDActionURI()
            action.uri = link.href
            annotation.action = action

            // Convert from Compose coordinates (Y-down from content origin)
            // to PDF coordinates (Y-up from page origin)
            val llx = marginLeft + link.x
            val lly = pageHeight - marginTop - link.y - link.height
            annotation.rectangle = PDRectangle(llx, lly, link.width, link.height)

            // Invisible border (the content provides visual styling)
            val borderStyle = PDBorderStyleDictionary()
            borderStyle.width = 0f
            annotation.borderStyle = borderStyle

            page.annotations.add(annotation)
        }
    }
}
