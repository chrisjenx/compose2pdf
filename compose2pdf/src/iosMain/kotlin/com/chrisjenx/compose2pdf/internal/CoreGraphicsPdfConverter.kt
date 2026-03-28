@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.chrisjenx.compose2pdf.internal

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.set
import platform.CoreFoundation.CFAttributedStringCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDataRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGAffineTransformMake
import platform.CoreGraphics.CGColorCreate
import platform.CoreGraphics.CGColorRelease
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextAddCurveToPoint
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextAddRect
import platform.CoreGraphics.CGContextBeginPath
import platform.CoreGraphics.CGContextClip
import platform.CoreGraphics.CGContextClosePath
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextDrawPath
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetAlpha
import platform.CoreGraphics.CGContextSetLineCap
import platform.CoreGraphics.CGContextSetLineDash
import platform.CoreGraphics.CGContextSetLineJoin
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGContextSetRGBStrokeColor
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGDataConsumerCreateWithCFData
import platform.CoreGraphics.CGDataConsumerRelease
import platform.CoreGraphics.CGFloat
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGPDFContextBeginPage
import platform.CoreGraphics.CGPDFContextClose
import platform.CoreGraphics.CGPDFContextCreate
import platform.CoreGraphics.CGPDFContextEndPage
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGLineCap
import platform.CoreGraphics.CGLineJoin
import platform.CoreGraphics.CGPathDrawingMode
import platform.CoreGraphics.CGPoint
import platform.CoreText.CTFontCreateWithName
import platform.CoreText.CTFontDrawGlyphs
import platform.CoreText.CTFontGetGlyphsForCharacters
import platform.CoreText.CTLineCreateWithAttributedString
import platform.CoreText.CTLineDraw
import platform.CoreText.kCTFontAttributeName
import platform.CoreText.kCTForegroundColorAttributeName
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSMutableData
import platform.Foundation.create
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Converts Skia-generated SVG to vector PDF using Core Graphics on iOS.
 *
 * Equivalent to [SvgToPdfConverter] on JVM (which uses PDFBox), this converter
 * handles SVG elements produced by Skia's SVGCanvas: shapes (rect, circle, ellipse,
 * line, polyline, polygon, path), text, groups, definitions (defs/use), clipping,
 * transforms, fills, strokes, and opacity.
 *
 * Uses [SvgParser] to parse SVG XML into [SvgElement] trees, then renders each
 * element to a Core Graphics PDF context.
 */
internal object CoreGraphicsPdfConverter {

    /**
     * Renders a single SVG string as a single PDF page.
     *
     * @param svg The SVG string to render.
     * @param pageWidthPt Page width in PDF points.
     * @param pageHeightPt Page height in PDF points.
     * @return The rendered PDF as a ByteArray.
     */
    fun renderSinglePage(
        svg: String,
        pageWidthPt: Float,
        pageHeightPt: Float,
    ): ByteArray {
        val (svgRoot, defs) = SvgParser.parse(svg)
        return createPdf(pageWidthPt, pageHeightPt) { ctx ->
            val svgWidth = svgRoot.attributes["width"]?.toDoubleOrNull() ?: pageWidthPt.toDouble()
            val svgHeight = svgRoot.attributes["height"]?.toDoubleOrNull() ?: pageHeightPt.toDouble()
            val scaleX = pageWidthPt / svgWidth
            val scaleY = pageHeightPt / svgHeight

            beginPage(ctx, pageWidthPt, pageHeightPt)
            // PDF: bottom-left origin (Y-up). SVG: top-left origin (Y-down). Flip Y and scale.
            applySvgToPageTransform(ctx, scaleX, scaleY, pageHeightPt.toDouble())
            PageRenderer(ctx, defs).renderChildren(svgRoot)
            endPage(ctx)
        }
    }

    /**
     * Renders a tall SVG as multiple auto-paginated PDF pages, slicing vertically
     * with proper margins, clipping, and page offsets.
     *
     * @param svg The tall SVG string to render.
     * @param layout Page dimensions and margin layout.
     * @param totalContentHeightPt Total content height in PDF points.
     * @param density The density used during Compose rendering.
     * @param maxPages Maximum number of pages to generate.
     * @return The rendered PDF as a ByteArray.
     */
    fun renderAutoPages(
        svg: String,
        layout: PageLayout,
        totalContentHeightPt: Float,
        density: Float,
        maxPages: Int,
    ): ByteArray {
        val (svgRoot, defs) = SvgParser.parse(svg)
        val pageCount = kotlin.math.ceil(totalContentHeightPt.toDouble() / layout.contentHeightPt)
            .toInt().coerceIn(1, maxPages)

        return createPdf(layout.pageWidthPt, layout.pageHeightPt) { ctx ->
            for (pageIndex in 0 until pageCount) {
                addPageSlice(ctx, svgRoot, defs, layout, pageIndex, density)
            }
        }
    }

    // -- PDF document lifecycle --

    /**
     * Creates a PDF document, executes the rendering block, and returns the result as ByteArray.
     */
    private inline fun createPdf(
        pageWidthPt: Float,
        pageHeightPt: Float,
        block: (CGContextRef) -> Unit,
    ): ByteArray = memScoped {
        val mutableData = NSMutableData()
        @Suppress("UNCHECKED_CAST")
        val cfData = CFBridgingRetain(mutableData) as CFMutableDataRef
        val consumer = CGDataConsumerCreateWithCFData(cfData)

        val rect = CGRectMake(0.0, 0.0, pageWidthPt.toDouble(), pageHeightPt.toDouble())

        val pdfContext = CGPDFContextCreate(consumer, rect.ptr, null)
            ?: run {
                CGDataConsumerRelease(consumer)
                CFRelease(cfData)
                throw IllegalStateException("Failed to create CGPDFContext")
            }

        try {
            block(pdfContext)
            CGPDFContextClose(pdfContext)
        } finally {
            CFRelease(pdfContext)
            CGDataConsumerRelease(consumer)
        }

        // Extract bytes from NSMutableData
        val bytes = mutableData.toByteArray()
        CFRelease(cfData)
        bytes
    }

    private fun beginPage(ctx: CGContextRef, widthPt: Float, heightPt: Float) {
        // CGPDFContextBeginPage with null page dict uses the context's default media box
        CGPDFContextBeginPage(ctx, null)
    }

    private fun endPage(ctx: CGContextRef) {
        CGPDFContextEndPage(ctx)
    }

    /**
     * Applies the initial SVG-to-PDF coordinate transform.
     * SVG Y-down -> PDF Y-up: scale(sx, -sy) then translate(0, -pageHeight/sy)
     * Combined as matrix: (sx, 0, 0, -sy, 0, pageHeight)
     */
    private fun applySvgToPageTransform(
        ctx: CGContextRef,
        scaleX: Double,
        scaleY: Double,
        pageHeightPt: Double,
    ) {
        CGContextConcatCTM(ctx, CGAffineTransformMake(scaleX, 0.0, 0.0, -scaleY, 0.0, pageHeightPt))
    }

    private fun addPageSlice(
        ctx: CGContextRef,
        svgRoot: SvgElement,
        defs: Map<String, SvgElement>,
        layout: PageLayout,
        pageIndex: Int,
        density: Float,
    ) {
        beginPage(ctx, layout.pageWidthPt, layout.pageHeightPt)

        CGContextSaveGState(ctx)

        // Clip to content area (in PDF Y-up coordinates)
        val marginBottom = layout.pageHeightPt - layout.marginTopPt - layout.contentHeightPt
        CGContextAddRect(
            ctx,
            CGRectMake(
                layout.marginLeftPt.toDouble(),
                marginBottom.toDouble(),
                layout.contentWidthPt.toDouble(),
                layout.contentHeightPt.toDouble(),
            )
        )
        CGContextClip(ctx)

        // Apply content area transform: scale + Y-flip + margin offset + vertical pagination
        val scale = 1.0 / density
        val verticalOffsetPt = pageIndex * layout.contentHeightPt
        CGContextConcatCTM(
            ctx,
            CGAffineTransformMake(
                scale.toDouble(), 0.0,
                0.0, -scale.toDouble(),
                layout.marginLeftPt.toDouble(),
                (layout.pageHeightPt - layout.marginTopPt + verticalOffsetPt).toDouble(),
            )
        )

        PageRenderer(ctx, defs).renderChildren(svgRoot)
        CGContextRestoreGState(ctx)

        endPage(ctx)
    }

    // -- NSMutableData extension --

    private fun NSMutableData.toByteArray(): ByteArray {
        val length = this.length.toInt()
        if (length == 0) return ByteArray(0)
        @Suppress("UNCHECKED_CAST")
        val cfData = CFBridgingRetain(this) as platform.CoreFoundation.CFDataRef
        val ptr = CFDataGetBytePtr(cfData)
        val result = ByteArray(length)
        if (ptr != null) {
            for (i in 0 until length) {
                result[i] = ptr[i].toByte()
            }
        }
        CFRelease(cfData)
        return result
    }

    // -- Shape drawing helpers --

    /** Bezier approximation constant for circles/ellipses: 4 * (sqrt(2) - 1) / 3 */
    private const val KAPPA: CGFloat = 0.5522847498

    /** Draws an ellipse using 4 cubic Bezier curves (standard approximation). */
    private fun drawEllipse(ctx: CGContextRef, cx: CGFloat, cy: CGFloat, rx: CGFloat, ry: CGFloat) {
        val kx = rx * KAPPA
        val ky = ry * KAPPA
        CGContextMoveToPoint(ctx, cx + rx, cy)
        CGContextAddCurveToPoint(ctx, cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
        CGContextAddCurveToPoint(ctx, cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
        CGContextAddCurveToPoint(ctx, cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
        CGContextAddCurveToPoint(ctx, cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy)
        CGContextClosePath(ctx)
    }

    /** Draws a rounded rectangle using line segments and cubic Bezier corners. */
    private fun drawRoundedRect(
        ctx: CGContextRef,
        x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat, rx: CGFloat, ry: CGFloat,
    ) {
        val kx = rx * KAPPA
        val ky = ry * KAPPA
        CGContextMoveToPoint(ctx, x + rx, y)
        // Top edge -> top-right corner
        CGContextAddLineToPoint(ctx, x + w - rx, y)
        CGContextAddCurveToPoint(ctx, x + w - rx + kx, y, x + w, y + ry - ky, x + w, y + ry)
        // Right edge -> bottom-right corner
        CGContextAddLineToPoint(ctx, x + w, y + h - ry)
        CGContextAddCurveToPoint(ctx, x + w, y + h - ry + ky, x + w - rx + kx, y + h, x + w - rx, y + h)
        // Bottom edge -> bottom-left corner
        CGContextAddLineToPoint(ctx, x + rx, y + h)
        CGContextAddCurveToPoint(ctx, x + rx - kx, y + h, x, y + h - ry + ky, x, y + h - ry)
        // Left edge -> top-left corner
        CGContextAddLineToPoint(ctx, x, y + ry)
        CGContextAddCurveToPoint(ctx, x, y + ry - ky, x + rx - kx, y, x + rx, y)
        CGContextClosePath(ctx)
    }

    /** Builds SVG path data for a rounded rectangle (for clipping via CoreGraphicsPathParser). */
    private fun buildRoundedRectPathData(
        x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat, rx: CGFloat, ry: CGFloat,
    ): String = buildString {
        append("M${x + rx},${y}")
        append("L${x + w - rx},${y}")
        append("A$rx,$ry,0,0,1,${x + w},${y + ry}")
        append("L${x + w},${y + h - ry}")
        append("A$rx,$ry,0,0,1,${x + w - rx},${y + h}")
        append("L${x + rx},${y + h}")
        append("A$rx,$ry,0,0,1,${x},${y + h - ry}")
        append("L${x},${y + ry}")
        append("A$rx,$ry,0,0,1,${x + rx},${y}")
        append("Z")
    }

    // ========================================================================
    // PageRenderer — renders SVG elements to a Core Graphics PDF context.
    // ========================================================================

    /**
     * Renders SVG elements to a Core Graphics PDF context for a single page.
     * Holds per-page rendering state.
     */
    private class PageRenderer(
        private val ctx: CGContextRef,
        private val defs: Map<String, SvgElement>,
    ) {

        // -- Element dispatch --

        fun renderChildren(parent: SvgElement) {
            for (child in parent.children) {
                renderElement(child)
            }
        }

        private fun renderElement(elem: SvgElement) {
            when (elem.name) {
                "defs", "clipPath" -> {} // Skip definition elements
                "rect" -> renderRect(elem)
                "circle" -> renderCircle(elem)
                "ellipse" -> renderEllipse(elem)
                "line" -> renderLine(elem)
                "polyline" -> renderPolyShape(elem, close = false)
                "polygon" -> renderPolyShape(elem, close = true)
                "path" -> renderPath(elem)
                "text" -> renderText(elem)
                "g" -> renderGroup(elem)
                "use" -> renderUse(elem)
                "image" -> renderImage(elem)
            }
        }

        // -- Shape elements --

        private fun renderRect(elem: SvgElement) {
            CGContextSaveGState(ctx)
            applyTransform(elem); applyOpacity(elem)

            val x = elem.attr("x")?.toDoubleOrNull() ?: 0.0
            val y = elem.attr("y")?.toDoubleOrNull() ?: 0.0
            val w = elem.attr("width")?.toDoubleOrNull() ?: return restore()
            val h = elem.attr("height")?.toDoubleOrNull() ?: return restore()
            val rx = elem.attr("rx")?.toDoubleOrNull() ?: 0.0
            val ry = elem.attr("ry")?.toDoubleOrNull() ?: rx

            CGContextBeginPath(ctx)
            if (rx > 0.0 || ry > 0.0) {
                drawRoundedRect(
                    ctx, x, y, w, h,
                    rx.coerceAtMost(w / 2),
                    ry.coerceAtMost(h / 2),
                )
            } else {
                CGContextAddRect(ctx, CGRectMake(x, y, w, h))
            }
            fillAndStroke(elem)
            CGContextRestoreGState(ctx)
        }

        private fun renderCircle(elem: SvgElement) {
            CGContextSaveGState(ctx)
            applyTransform(elem); applyOpacity(elem)

            val cx = elem.attr("cx")?.toDoubleOrNull() ?: 0.0
            val cy = elem.attr("cy")?.toDoubleOrNull() ?: 0.0
            val r = elem.attr("r")?.toDoubleOrNull() ?: return restore()

            CGContextBeginPath(ctx)
            drawEllipse(ctx, cx, cy, r, r)
            fillAndStroke(elem)
            CGContextRestoreGState(ctx)
        }

        private fun renderEllipse(elem: SvgElement) {
            CGContextSaveGState(ctx)
            applyTransform(elem); applyOpacity(elem)

            val cx = elem.attr("cx")?.toDoubleOrNull() ?: 0.0
            val cy = elem.attr("cy")?.toDoubleOrNull() ?: 0.0
            val rx = elem.attr("rx")?.toDoubleOrNull() ?: return restore()
            val ry = elem.attr("ry")?.toDoubleOrNull() ?: return restore()

            CGContextBeginPath(ctx)
            drawEllipse(ctx, cx, cy, rx, ry)
            fillAndStroke(elem)
            CGContextRestoreGState(ctx)
        }

        private fun renderLine(elem: SvgElement) {
            CGContextSaveGState(ctx)
            applyTransform(elem); applyOpacity(elem)

            val x1 = elem.attr("x1")?.toDoubleOrNull() ?: 0.0
            val y1 = elem.attr("y1")?.toDoubleOrNull() ?: 0.0
            val x2 = elem.attr("x2")?.toDoubleOrNull() ?: 0.0
            val y2 = elem.attr("y2")?.toDoubleOrNull() ?: 0.0

            CGContextBeginPath(ctx)
            CGContextMoveToPoint(ctx, x1, y1)
            CGContextAddLineToPoint(ctx, x2, y2)
            // Lines only stroke, never fill
            applyStrokeState(elem)
            elem.attr("stroke")?.takeIf { it != "none" }?.let { strokeVal ->
                SvgColorParser.parse(strokeVal)?.let { c ->
                    CGContextSetRGBStrokeColor(ctx, c.r.toDouble(), c.g.toDouble(), c.b.toDouble(), 1.0)
                }
            }
            CGContextDrawPath(ctx, CGPathDrawingMode.kCGPathStroke)
            CGContextRestoreGState(ctx)
        }

        private fun renderPolyShape(elem: SvgElement, close: Boolean) {
            CGContextSaveGState(ctx)
            applyTransform(elem); applyOpacity(elem)

            val points = elem.attr("points") ?: return restore()
            val coords = points.trim().split(Regex("[,\\s]+")).mapNotNull { it.toDoubleOrNull() }
            if (coords.size < 4) return restore()

            CGContextBeginPath(ctx)
            CGContextMoveToPoint(ctx, coords[0], coords[1])
            for (i in 2 until coords.size - 1 step 2) {
                CGContextAddLineToPoint(ctx, coords[i], coords[i + 1])
            }
            if (close) CGContextClosePath(ctx)
            fillAndStroke(elem)
            CGContextRestoreGState(ctx)
        }

        private fun renderPath(elem: SvgElement) {
            CGContextSaveGState(ctx)
            applyTransform(elem); applyOpacity(elem)

            val d = elem.attr("d")
            if (!d.isNullOrEmpty()) {
                CGContextBeginPath(ctx)
                CoreGraphicsPathParser.parse(d, ctx)
                fillAndStroke(elem)
            }
            CGContextRestoreGState(ctx)
        }

        private fun renderText(elem: SvgElement) {
            CGContextSaveGState(ctx)
            applyTransform(elem); applyOpacity(elem)

            val fontSize = elem.attr("font-size")?.toDoubleOrNull() ?: 12.0
            val text = elem.textContent.trim()
            if (text.isEmpty()) return restore()

            val fillColor = elem.attr("fill")
                ?.takeIf { it != "none" }
                ?.let { SvgColorParser.parse(it) }
                ?: SvgColor(0f, 0f, 0f) // SVG default: black

            // Resolve font
            val fontFamily = elem.attr("font-family")?.removeSurrounding("'")?.removeSurrounding("\"") ?: "Helvetica"
            val fontWeight = elem.attr("font-weight")
            val fontStyle = elem.attr("font-style")
            val ctFontName = resolveFontName(fontFamily, fontWeight, fontStyle)

            @Suppress("UNCHECKED_CAST")
            val ctFontNameRef = platform.Foundation.CFBridgingRetain(ctFontName as platform.Foundation.NSString) as CFStringRef
            val ctFont = CTFontCreateWithName(ctFontNameRef, fontSize, null)
            CFRelease(ctFontNameRef)
            if (ctFont == null) return restore()

            val xAttr = elem.attr("x") ?: ""
            val xPositions = xAttr.split(",").mapNotNull { it.trim().toDoubleOrNull() }
            val yOffset = (elem.attr("y") ?: "").split(",")
                .firstOrNull()?.trim()?.toDoubleOrNull() ?: fontSize

            // Counter-flip Y for text: undo global Y-flip so glyphs render right-side up
            // Matrix: (1, 0, 0, -1, 0, 2*yOffset)
            CGContextConcatCTM(ctx, CGAffineTransformMake(1.0, 0.0, 0.0, -1.0, 0.0, 2.0 * yOffset))

            if (xPositions.size > 1 && xPositions.size >= text.length) {
                // CTFontDrawGlyphs bypasses CTLine's text layout engine, placing each
                // glyph exactly at the specified coordinate without bearing adjustments.
                memScoped {
                    val count = text.length
                    val characters = allocArray<UShortVar>(count)
                    val positions = allocArray<CGPoint>(count)
                    for (i in 0 until count) {
                        characters[i] = text[i].code.toUShort()
                        positions[i].x = xPositions[i]
                        positions[i].y = yOffset
                    }
                    val glyphs = allocArray<UShortVar>(count)
                    CTFontGetGlyphsForCharacters(ctFont, characters, glyphs, count.toLong())
                    CGContextSetRGBFillColor(ctx, fillColor.r.toDouble(), fillColor.g.toDouble(), fillColor.b.toDouble(), 1.0)
                    CTFontDrawGlyphs(ctFont, glyphs, positions, count.toULong(), ctx)
                }
            } else {
                val colorSpace = CGColorSpaceCreateDeviceRGB()
                val cgColor = memScoped {
                    val components = allocArray<kotlinx.cinterop.DoubleVar>(4)
                    components[0] = fillColor.r.toDouble()
                    components[1] = fillColor.g.toDouble()
                    components[2] = fillColor.b.toDouble()
                    components[3] = 1.0
                    CGColorCreate(colorSpace, components)
                }
                val x0 = xPositions.firstOrNull() ?: 0.0
                drawTextAtPosition(ctFont, text, x0, yOffset, cgColor)
                CGColorRelease(cgColor)
                CGColorSpaceRelease(colorSpace)
            }

            CFRelease(ctFont)
            CGContextRestoreGState(ctx)
        }

        private fun drawTextAtPosition(
            ctFont: platform.CoreText.CTFontRef,
            text: String,
            x: Double,
            y: Double,
            cgColor: platform.CoreGraphics.CGColorRef?,
        ) {
            // Create attributed string with font and color
            val attrDict = CFDictionaryCreateMutable(
                kCFAllocatorDefault, 2,
                kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
            )
            @Suppress("UNCHECKED_CAST")
            CFDictionarySetValue(attrDict, kCTFontAttributeName as CFTypeRef?, ctFont as CFTypeRef?)
            if (cgColor != null) {
                @Suppress("UNCHECKED_CAST")
                CFDictionarySetValue(attrDict, kCTForegroundColorAttributeName as CFTypeRef?, cgColor as CFTypeRef?)
            }

            @Suppress("UNCHECKED_CAST")
            val textRef = platform.Foundation.CFBridgingRetain(text as platform.Foundation.NSString) as CFStringRef
            val attrString = CFAttributedStringCreate(
                kCFAllocatorDefault,
                textRef,
                attrDict,
            )
            CFRelease(textRef)

            val line = CTLineCreateWithAttributedString(attrString)

            // Position and draw
            CGContextSaveGState(ctx)
            CGContextTranslateCTM(ctx, x, y)
            CTLineDraw(line, ctx)
            CGContextRestoreGState(ctx)

            CFRelease(line)
            CFRelease(attrString)
            CFRelease(attrDict)
        }

        /**
         * Maps SVG font-family/weight/style to a Core Text font name.
         * Falls back to system font (Helvetica) if no match is found.
         */
        private fun resolveFontName(family: String, weight: String?, style: String?): String {
            val isBold = weight == "bold" || (weight?.toIntOrNull() ?: 400) >= 700
            val isItalic = style == "italic" || style == "oblique"
            return when {
                family.equals("Inter", ignoreCase = true) || family.equals("sans-serif", ignoreCase = true) -> {
                    when {
                        isBold && isItalic -> "Helvetica-BoldOblique"
                        isBold -> "Helvetica-Bold"
                        isItalic -> "Helvetica-Oblique"
                        else -> "Helvetica"
                    }
                }
                family.equals("serif", ignoreCase = true) || family.equals("Times", ignoreCase = true) -> {
                    when {
                        isBold && isItalic -> "Times-BoldItalic"
                        isBold -> "Times-Bold"
                        isItalic -> "Times-Italic"
                        else -> "Times-Roman"
                    }
                }
                family.equals("monospace", ignoreCase = true) || family.equals("Courier", ignoreCase = true) -> {
                    when {
                        isBold && isItalic -> "Courier-BoldOblique"
                        isBold -> "Courier-Bold"
                        isItalic -> "Courier-Oblique"
                        else -> "Courier"
                    }
                }
                else -> {
                    // Try to use the family name directly (may work for system-installed fonts)
                    when {
                        isBold && isItalic -> "$family-BoldItalic"
                        isBold -> "$family-Bold"
                        isItalic -> "$family-Italic"
                        else -> family
                    }
                }
            }
        }

        private fun renderGroup(elem: SvgElement) {
            CGContextSaveGState(ctx)
            applyTransform(elem); applyOpacity(elem)
            applyClipPath(elem)
            renderChildren(elem)
            CGContextRestoreGState(ctx)
        }

        private fun renderUse(elem: SvgElement) {
            val href = (elem.attributes["href"] ?: elem.attributes["xlink:href"])
                ?.takeIf { it.isNotEmpty() } ?: return
            val def = defs[href.removePrefix("#")] ?: return

            CGContextSaveGState(ctx)
            applyTransform(elem)
            val x = elem.attr("x")?.toDoubleOrNull() ?: 0.0
            val y = elem.attr("y")?.toDoubleOrNull() ?: 0.0
            if (x != 0.0 || y != 0.0) {
                CGContextTranslateCTM(ctx, x, y)
            }
            renderElement(def)
            CGContextRestoreGState(ctx)
        }

        private fun renderImage(elem: SvgElement) {
            CGContextSaveGState(ctx)
            applyTransform(elem); applyOpacity(elem)

            val width = elem.attr("width")?.toDoubleOrNull() ?: return restore()
            val height = elem.attr("height")?.toDoubleOrNull() ?: return restore()

            val href = (elem.attributes["href"] ?: elem.attributes["xlink:href"])
                ?.takeIf { it.isNotEmpty() } ?: return restore()

            val cgImage = decodeImage(href) ?: return restore()

            // Counter-flip for image: in Y-flipped coords, flip image back to right-side up
            val x = elem.attr("x")?.toDoubleOrNull() ?: 0.0
            val y = elem.attr("y")?.toDoubleOrNull() ?: 0.0
            // Matrix: (1, 0, 0, -1, x, y+height) to flip image right-side up
            CGContextConcatCTM(ctx, CGAffineTransformMake(1.0, 0.0, 0.0, -1.0, x, y + height))
            CGContextDrawImage(ctx, CGRectMake(0.0, 0.0, width, height), cgImage)

            CGImageRelease(cgImage)
            CGContextRestoreGState(ctx)
        }

        private fun decodeImage(href: String): platform.CoreGraphics.CGImageRef? {
            if (!href.startsWith("data:image/")) return null
            val base64Data = href.substringAfter(",", "")
            if (base64Data.isEmpty()) return null

            return try {
                // Decode base64
                val nsData = NSData.create(
                    base64EncodedString = base64Data,
                    options = 0u,
                ) ?: return null

                @Suppress("UNCHECKED_CAST")
                val cfData = CFBridgingRetain(nsData) as platform.CoreFoundation.CFDataRef
                val imageSource = CGImageSourceCreateWithData(cfData, null)
                val result = if (imageSource != null) {
                    CGImageSourceCreateImageAtIndex(imageSource, 0u, null)
                } else {
                    null
                }
                if (imageSource != null) CFRelease(imageSource)
                CFRelease(cfData)
                result
            } catch (_: Exception) {
                null
            }
        }

        private fun restore() {
            CGContextRestoreGState(ctx)
        }

        // -- Fill / stroke --

        private fun fillAndStroke(elem: SvgElement) {
            val fill = elem.attr("fill")
            val stroke = elem.attr("stroke")
            val hasFill = fill != "none" // SVG default fill is black
            val hasStroke = stroke != null && stroke != "none"

            if (hasFill) {
                val color = fill?.let { SvgColorParser.parse(it) }
                if (color != null) {
                    CGContextSetRGBFillColor(ctx, color.r.toDouble(), color.g.toDouble(), color.b.toDouble(), 1.0)
                } else {
                    CGContextSetRGBFillColor(ctx, 0.0, 0.0, 0.0, 1.0) // SVG default: black
                }
            }

            if (hasStroke) {
                applyStrokeState(elem)
                SvgColorParser.parse(stroke)?.let { c ->
                    CGContextSetRGBStrokeColor(ctx, c.r.toDouble(), c.g.toDouble(), c.b.toDouble(), 1.0)
                }
            }

            val evenOdd = elem.attr("fill-rule") == "evenodd"
            when {
                hasFill && hasStroke -> {
                    CGContextDrawPath(ctx, if (evenOdd) CGPathDrawingMode.kCGPathEOFillStroke else CGPathDrawingMode.kCGPathFillStroke)
                }
                hasStroke -> CGContextDrawPath(ctx, CGPathDrawingMode.kCGPathStroke)
                hasFill -> {
                    CGContextDrawPath(ctx, if (evenOdd) CGPathDrawingMode.kCGPathEOFill else CGPathDrawingMode.kCGPathFill)
                }
            }
        }

        private fun applyStrokeState(elem: SvgElement) {
            elem.attr("stroke-width")?.toDoubleOrNull()?.let {
                CGContextSetLineWidth(ctx, it)
            }

            elem.attr("stroke-linecap")?.let { cap ->
                CGContextSetLineCap(
                    ctx,
                    when (cap) {
                        "round" -> CGLineCap.kCGLineCapRound
                        "square" -> CGLineCap.kCGLineCapSquare
                        else -> CGLineCap.kCGLineCapButt
                    }
                )
            }

            elem.attr("stroke-linejoin")?.let { join ->
                CGContextSetLineJoin(
                    ctx,
                    when (join) {
                        "round" -> CGLineJoin.kCGLineJoinRound
                        "bevel" -> CGLineJoin.kCGLineJoinBevel
                        else -> CGLineJoin.kCGLineJoinMiter
                    }
                )
            }

            elem.attr("stroke-dasharray")?.takeIf { it != "none" }?.let { dashStr ->
                val dashes = dashStr.split(Regex("[,\\s]+")).mapNotNull { it.toDoubleOrNull() }
                if (dashes.isNotEmpty()) {
                    val phase = elem.attr("stroke-dashoffset")?.toDoubleOrNull() ?: 0.0
                    memScoped {
                        val dashArray = allocArray<DoubleVar>(dashes.size)
                        for (idx in dashes.indices) {
                            dashArray[idx] = dashes[idx]
                        }
                        CGContextSetLineDash(ctx, phase, dashArray, dashes.size.toULong())
                    }
                }
            }
        }

        // -- Transforms --

        private fun applyTransform(elem: SvgElement) {
            val transform = elem.attributes["transform"] ?: return
            if (transform.isEmpty()) return

            for (match in TRANSFORM_RE.findAll(transform)) {
                val func = match.groupValues[1]
                val params = match.groupValues[2]
                    .split(Regex("[,\\s]+"))
                    .mapNotNull { it.trim().toDoubleOrNull() }

                when (func) {
                    "translate" -> {
                        val tx = params.getOrElse(0) { 0.0 }
                        val ty = params.getOrElse(1) { 0.0 }
                        CGContextTranslateCTM(ctx, tx, ty)
                    }
                    "scale" -> {
                        val sx = params.getOrElse(0) { 1.0 }
                        val sy = params.getOrElse(1) { sx }
                        CGContextScaleCTM(ctx, sx, sy)
                    }
                    "rotate" -> {
                        val angle = (params.getOrElse(0) { 0.0 }) * PI / 180.0
                        if (params.size >= 3) {
                            val cx = params[1]
                            val cy = params[2]
                            CGContextTranslateCTM(ctx, cx, cy)
                            CGContextConcatCTM(ctx, CGAffineTransformMake(cos(angle), sin(angle), -sin(angle), cos(angle), 0.0, 0.0))
                            CGContextTranslateCTM(ctx, -cx, -cy)
                        } else {
                            CGContextConcatCTM(ctx, CGAffineTransformMake(cos(angle), sin(angle), -sin(angle), cos(angle), 0.0, 0.0))
                        }
                    }
                    "matrix" -> {
                        if (params.size >= 6) {
                            CGContextConcatCTM(
                                ctx,
                                CGAffineTransformMake(params[0], params[1], params[2], params[3], params[4], params[5])
                            )
                        }
                    }
                    "skewX" -> {
                        val a = (params.getOrElse(0) { 0.0 }) * PI / 180.0
                        CGContextConcatCTM(ctx, CGAffineTransformMake(1.0, 0.0, tan(a), 1.0, 0.0, 0.0))
                    }
                    "skewY" -> {
                        val a = (params.getOrElse(0) { 0.0 }) * PI / 180.0
                        CGContextConcatCTM(ctx, CGAffineTransformMake(1.0, tan(a), 0.0, 1.0, 0.0, 0.0))
                    }
                }
            }
        }

        // -- Opacity --

        private fun applyOpacity(elem: SvgElement) {
            val opacity = elem.attr("opacity")?.toDoubleOrNull()
            val fillOp = elem.attr("fill-opacity")?.toDoubleOrNull()
            val strokeOp = elem.attr("stroke-opacity")?.toDoubleOrNull()
            if (opacity == null && fillOp == null && strokeOp == null) return

            // Core Graphics doesn't have separate fill/stroke alpha like PDFBox's
            // PDExtendedGraphicsState. Use the combined effective opacity via CGContextSetAlpha.
            // This approximation is correct when only opacity or only fill-opacity is set.
            // For the rare case of different fill-opacity and stroke-opacity, we use
            // the lower value (conservative approach).
            val effFill = (opacity ?: 1.0) * (fillOp ?: 1.0)
            val effStroke = (opacity ?: 1.0) * (strokeOp ?: 1.0)
            val combinedAlpha = minOf(effFill, effStroke)
            if (combinedAlpha < 1.0) {
                CGContextSetAlpha(ctx, combinedAlpha)
            }
        }

        // -- Clipping --

        private fun applyClipPath(elem: SvgElement) {
            val clipRef = elem.attr("clip-path") ?: return
            val clipId = Regex("""url\(#([^)]+)\)""").find(clipRef)?.groupValues?.get(1) ?: return
            val clipElem = defs[clipId] ?: return

            CGContextBeginPath(ctx)

            for (child in clipElem.children) {
                when (child.name) {
                    "rect" -> {
                        val x = child.attributes["x"]?.toDoubleOrNull() ?: 0.0
                        val y = child.attributes["y"]?.toDoubleOrNull() ?: 0.0
                        val w = child.attributes["width"]?.toDoubleOrNull() ?: continue
                        val h = child.attributes["height"]?.toDoubleOrNull() ?: continue
                        val rx = child.attributes["rx"]?.toDoubleOrNull() ?: 0.0
                        val ry = child.attributes["ry"]?.toDoubleOrNull() ?: rx
                        if (rx > 0.0 || ry > 0.0) {
                            val crx = rx.coerceAtMost(w / 2)
                            val cry = ry.coerceAtMost(h / 2)
                            if (crx >= w / 2 && cry >= h / 2) {
                                // Full-radius = ellipse
                                drawEllipse(ctx, x + w / 2, y + h / 2, w / 2, h / 2)
                            } else {
                                // Partial radius: route through path parser (arcToCubic is proven)
                                CoreGraphicsPathParser.parse(
                                    buildRoundedRectPathData(x, y, w, h, crx, cry),
                                    ctx,
                                )
                            }
                        } else {
                            CGContextAddRect(ctx, CGRectMake(x, y, w, h))
                        }
                    }
                    "path" -> {
                        child.attributes["d"]?.takeIf { it.isNotEmpty() }?.let {
                            CoreGraphicsPathParser.parse(it, ctx)
                        }
                    }
                    "circle" -> {
                        val cx = child.attributes["cx"]?.toDoubleOrNull() ?: 0.0
                        val cy = child.attributes["cy"]?.toDoubleOrNull() ?: 0.0
                        val r = child.attributes["r"]?.toDoubleOrNull() ?: continue
                        drawEllipse(ctx, cx, cy, r, r)
                    }
                    "ellipse" -> {
                        val cx = child.attributes["cx"]?.toDoubleOrNull() ?: 0.0
                        val cy = child.attributes["cy"]?.toDoubleOrNull() ?: 0.0
                        val rx = child.attributes["rx"]?.toDoubleOrNull() ?: continue
                        val ry = child.attributes["ry"]?.toDoubleOrNull() ?: continue
                        drawEllipse(ctx, cx, cy, rx, ry)
                    }
                }
            }
            CGContextClip(ctx)
        }

        companion object {
            private val TRANSFORM_RE =
                Regex("""(translate|scale|rotate|matrix|skewX|skewY)\(([^)]+)\)""")
        }
    }
}
