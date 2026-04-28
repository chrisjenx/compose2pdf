package com.chrisjenx.compose2pdf.internal

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.awt.geom.AffineTransform
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Converts Skia-generated SVG to vector PDF using PDFBox.
 *
 * Handles SVG elements produced by Skia's SVGCanvas: shapes (rect, circle, ellipse,
 * line, polyline, polygon, path), text, groups, definitions (defs/use), clipping,
 * transforms, fills, strokes, and opacity.
 */
internal object SvgToPdfConverter {

    private val logger = java.util.logging.Logger.getLogger(SvgToPdfConverter::class.java.name)

    /**
     * Adds a single page rendering [svg] into the content area defined by [layout].
     * The SVG is rendered at content-area pixel dimensions; this function applies the
     * margin offset, clips to the content area, and converts pixels to PDF points.
     */
    fun addPage(
        pdfDoc: PDDocument,
        svg: String,
        layout: PageLayout,
        density: Float,
        fontCache: MutableMap<String, PDFont> = mutableMapOf(),
        imageCache: MutableMap<String, PDImageXObject> = mutableMapOf(),
    ) {
        val (svgRoot, defs) = parseSvg(svg)
        renderSvgToContentArea(
            pdfDoc = pdfDoc,
            svgRoot = svgRoot,
            defs = defs,
            layout = layout,
            density = density,
            verticalOffsetPt = 0f,
            fontCache = fontCache,
            imageCache = imageCache,
        )
    }

    /**
     * Adds auto-paginated pages from a tall SVG, slicing vertically with proper
     * margins, clipping, and page offsets.
     *
     * Parses the SVG once and renders each page slice, up to [maxPages].
     */
    fun addAutoPages(
        pdfDoc: PDDocument,
        svg: String,
        layout: PageLayout,
        totalContentHeightPt: Float,
        density: Float,
        maxPages: Int,
        fontCache: MutableMap<String, PDFont>,
        imageCache: MutableMap<String, PDImageXObject>,
    ) {
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
        } finally {
            cs.close()
        }
    }

    private val documentBuilderFactory: DocumentBuilderFactory by lazy {
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    }

    private fun parseSvg(svg: String): Pair<Element, Map<String, Element>> {
        val xmlDoc = documentBuilderFactory.newDocumentBuilder().parse(svg.byteInputStream())
        val svgRoot = xmlDoc.documentElement
        val defs = mutableMapOf<String, Element>()
        collectDefs(svgRoot, defs)
        return svgRoot to defs
    }

    private fun collectDefs(parent: Element, defs: MutableMap<String, Element>) {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val elem = node as Element
            when (elem.localName) {
                "defs" -> {
                    for (j in 0 until elem.childNodes.length) {
                        val defNode = elem.childNodes.item(j)
                        if (defNode.nodeType != Node.ELEMENT_NODE) continue
                        val defElem = defNode as Element
                        val id = defElem.getAttribute("id")
                        if (id.isNotEmpty()) defs[id] = defElem
                    }
                }
                // Skia emits <clipPath> as siblings of <g>, not inside <defs>
                "clipPath" -> {
                    val id = elem.getAttribute("id")
                    if (id.isNotEmpty()) defs[id] = elem
                }
                "g" -> collectDefs(elem, defs)
            }
        }
    }

    /**
     * Renders SVG elements to a PDFBox content stream for a single page.
     * Holds per-page rendering state (no shared mutable state on the object).
     */
    private class PageRenderer(
        private val cs: PDPageContentStream,
        private val doc: PDDocument,
        private val defs: Map<String, Element>,
        private val fontCache: MutableMap<String, PDFont>,
        private val imageCache: MutableMap<String, PDImageXObject>,
    ) {

        // ── Element dispatch ────────────────────────────────────────────

        fun renderChildren(parent: Element) {
            for (i in 0 until parent.childNodes.length) {
                val node = parent.childNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) renderElement(node as Element)
            }
        }

        private fun renderElement(elem: Element) {
            when (elem.localName) {
                "defs", "clipPath" -> {}
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

        // ── Shape elements ──────────────────────────────────────────────

        private fun renderRect(elem: Element) {
            cs.saveGraphicsState()
            applyTransform(elem); applyOpacity(elem)

            val x = attr(elem, "x")?.toFloatOrNull() ?: 0f
            val y = attr(elem, "y")?.toFloatOrNull() ?: 0f
            val w = attr(elem, "width")?.toFloatOrNull()
                ?: return restore().also { logger.warning("Skipping rect: missing or invalid width '${attr(elem, "width")}'") }
            val h = attr(elem, "height")?.toFloatOrNull()
                ?: return restore().also { logger.warning("Skipping rect: missing or invalid height '${attr(elem, "height")}'") }
            val rx = attr(elem, "rx")?.toFloatOrNull() ?: 0f
            val ry = attr(elem, "ry")?.toFloatOrNull() ?: rx

            if (rx > 0f || ry > 0f) {
                SvgShapeRenderer.drawRoundedRect(cs, x, y, w, h, rx.coerceAtMost(w / 2), ry.coerceAtMost(h / 2))
            } else {
                cs.addRect(x, y, w, h)
            }
            fillAndStroke(elem)
            cs.restoreGraphicsState()
        }

        private fun renderCircle(elem: Element) {
            cs.saveGraphicsState()
            applyTransform(elem); applyOpacity(elem)

            val cx = attr(elem, "cx")?.toFloatOrNull() ?: 0f
            val cy = attr(elem, "cy")?.toFloatOrNull() ?: 0f
            val r = attr(elem, "r")?.toFloatOrNull()
                ?: return restore().also { logger.warning("Skipping circle: missing or invalid radius '${attr(elem, "r")}'") }

            SvgShapeRenderer.drawEllipse(cs, cx, cy, r, r)
            fillAndStroke(elem)
            cs.restoreGraphicsState()
        }

        private fun renderEllipse(elem: Element) {
            cs.saveGraphicsState()
            applyTransform(elem); applyOpacity(elem)

            val cx = attr(elem, "cx")?.toFloatOrNull() ?: 0f
            val cy = attr(elem, "cy")?.toFloatOrNull() ?: 0f
            val rx = attr(elem, "rx")?.toFloatOrNull()
                ?: return restore().also { logger.warning("Skipping ellipse: missing or invalid rx '${attr(elem, "rx")}'") }
            val ry = attr(elem, "ry")?.toFloatOrNull()
                ?: return restore().also { logger.warning("Skipping ellipse: missing or invalid ry '${attr(elem, "ry")}'") }

            SvgShapeRenderer.drawEllipse(cs, cx, cy, rx, ry)
            fillAndStroke(elem)
            cs.restoreGraphicsState()
        }

        private fun renderLine(elem: Element) {
            cs.saveGraphicsState()
            applyTransform(elem); applyOpacity(elem)

            val x1 = attr(elem, "x1")?.toFloatOrNull() ?: 0f
            val y1 = attr(elem, "y1")?.toFloatOrNull() ?: 0f
            val x2 = attr(elem, "x2")?.toFloatOrNull() ?: 0f
            val y2 = attr(elem, "y2")?.toFloatOrNull() ?: 0f

            cs.moveTo(x1, y1)
            cs.lineTo(x2, y2)
            // Lines only stroke, never fill
            applyStrokeState(elem)
            attr(elem, "stroke")?.takeIf { it != "none" }?.let { strokeVal ->
                SvgColorParser.parse(strokeVal)?.let { c -> cs.setStrokingColor(c.r, c.g, c.b) }
            }
            cs.stroke()
            cs.restoreGraphicsState()
        }

        private fun renderPolyShape(elem: Element, close: Boolean) {
            cs.saveGraphicsState()
            applyTransform(elem); applyOpacity(elem)

            val points = attr(elem, "points") ?: return restore()
            val coords = points.trim().split(Regex("[,\\s]+")).mapNotNull { it.toFloatOrNull() }
            if (coords.size < 4) return restore()

            cs.moveTo(coords[0], coords[1])
            for (i in 2 until coords.size - 1 step 2) {
                cs.lineTo(coords[i], coords[i + 1])
            }
            if (close) cs.closePath()
            fillAndStroke(elem)
            cs.restoreGraphicsState()
        }

        private fun renderPath(elem: Element) {
            cs.saveGraphicsState()
            applyTransform(elem); applyOpacity(elem)

            val d = attr(elem, "d")
            if (!d.isNullOrEmpty()) {
                SvgPathParser.parse(d, cs)
                fillAndStroke(elem)
            }
            cs.restoreGraphicsState()
        }

        private fun renderText(elem: Element) {
            cs.saveGraphicsState()
            applyTransform(elem); applyOpacity(elem)

            val fontSize = attr(elem, "font-size")?.toFloatOrNull() ?: 12f
            val rawText = elem.textContent.trim()
            if (rawText.isEmpty()) return restore()

            val fillColor = attr(elem, "fill")
                ?.takeIf { it != "none" }
                ?.let { SvgColorParser.parse(it) }
                ?: SvgColor(0f, 0f, 0f) // SVG default: black
            cs.setNonStrokingColor(fillColor.r, fillColor.g, fillColor.b)

            val font = FontResolver.resolve(
                doc, fontCache,
                family = attr(elem, "font-family"),
                weight = attr(elem, "font-weight"),
                style = attr(elem, "font-style"),
            )

            val xAttr = attr(elem, "x") ?: ""
            val rawXPositions = xAttr.split(",").mapNotNull { it.trim().toFloatOrNull() }
            val yOffset = (attr(elem, "y") ?: "").split(",")
                .firstOrNull()?.trim()?.toFloatOrNull() ?: fontSize

            // Decompose Unicode ligatures that PDFBox fonts may lack cmap entries for
            val (text, xPositions) = TextNormalizer.normalize(rawText, rawXPositions, fontSize)

            // Counter-flip Y for text (undo global Y-flip so glyphs render right-side up)
            cs.transform(CoordinateTransform.textCounterFlipMatrix(yOffset))

            cs.beginText()
            cs.setFont(font, fontSize)

            if (xPositions.size > 1 && xPositions.size >= text.length) {
                // Position each glyph individually for precise placement
                for (i in text.indices) {
                    cs.newLineAtOffset(
                        if (i == 0) xPositions[0] else xPositions[i] - xPositions[i - 1],
                        if (i == 0) yOffset else 0f,
                    )
                    cs.showText(text[i].toString())
                }
            } else {
                val x0 = xPositions.firstOrNull() ?: 0f
                cs.newLineAtOffset(x0, yOffset)
                cs.showText(text)
            }
            cs.endText()
            cs.restoreGraphicsState()
        }

        private fun renderGroup(elem: Element) {
            cs.saveGraphicsState()
            applyTransform(elem); applyOpacity(elem)
            applyClipPath(elem)
            renderChildren(elem)
            cs.restoreGraphicsState()
        }

        private fun renderUse(elem: Element) {
            val href = elem.getAttribute("href").ifEmpty {
                elem.getAttributeNS("http://www.w3.org/1999/xlink", "href")
            } ?: return
            if (href.isEmpty()) return
            val def = defs[href.removePrefix("#")] ?: return

            cs.saveGraphicsState()
            applyTransform(elem)
            val x = attr(elem, "x")?.toFloatOrNull() ?: 0f
            val y = attr(elem, "y")?.toFloatOrNull() ?: 0f
            if (x != 0f || y != 0f) {
                cs.transform(Matrix(AffineTransform.getTranslateInstance(x.toDouble(), y.toDouble())))
            }
            renderElement(def)
            cs.restoreGraphicsState()
        }

        private fun renderImage(elem: Element) {
            cs.saveGraphicsState()
            applyTransform(elem); applyOpacity(elem)

            val width = attr(elem, "width")?.toFloatOrNull()
                ?: return restore().also { logger.warning("Skipping image: missing or invalid width '${attr(elem, "width")}'") }
            val height = attr(elem, "height")?.toFloatOrNull()
                ?: return restore().also { logger.warning("Skipping image: missing or invalid height '${attr(elem, "height")}'") }

            val href = elem.getAttribute("href").ifEmpty {
                elem.getAttributeNS("http://www.w3.org/1999/xlink", "href")
            } ?: return restore()

            val pdImage = decodeImage(href, elem.getAttribute("id")) ?: return restore()

            // In the Y-flipped coordinate system, flip the image back to right-side up.
            val x = attr(elem, "x")?.toFloatOrNull() ?: 0f
            val y = attr(elem, "y")?.toFloatOrNull() ?: 0f
            cs.transform(CoordinateTransform.imageCounterFlipMatrix(x, y, height))
            cs.drawImage(pdImage, 0f, 0f, width, height)
            cs.restoreGraphicsState()
        }

        private fun decodeImage(href: String, id: String): PDImageXObject? {
            if (id.isNotEmpty()) imageCache[id]?.let { return it }
            if (!href.startsWith("data:image/")) return null
            val base64Data = href.substringAfter(",", "")
            if (base64Data.isEmpty()) return null
            return try {
                val bytes = Base64.getDecoder().decode(base64Data)
                val buffered = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
                val pdImage = LosslessFactory.createFromImage(doc, buffered)
                if (id.isNotEmpty()) imageCache[id] = pdImage
                pdImage
            } catch (_: Exception) {
                null
            }
        }

        private fun restore() {
            cs.restoreGraphicsState()
        }

        // ── Fill / stroke ───────────────────────────────────────────────

        private fun fillAndStroke(elem: Element) {
            val fill = attr(elem, "fill")
            val stroke = attr(elem, "stroke")
            val hasFill = fill != "none" // SVG default fill is black
            val hasStroke = stroke != null && stroke != "none"

            if (hasFill) {
                val color = fill?.let { SvgColorParser.parse(it) }
                if (color != null) {
                    cs.setNonStrokingColor(color.r, color.g, color.b)
                } else {
                    cs.setNonStrokingColor(0f, 0f, 0f) // SVG default: black
                }
            }

            if (hasStroke) {
                applyStrokeState(elem)
                SvgColorParser.parse(stroke)?.let { c -> cs.setStrokingColor(c.r, c.g, c.b) }
            }

            val evenOdd = attr(elem, "fill-rule") == "evenodd"
            when {
                hasFill && hasStroke -> {
                    if (evenOdd) cs.fillAndStrokeEvenOdd() else cs.fillAndStroke()
                }
                hasStroke -> cs.stroke()
                hasFill -> {
                    if (evenOdd) cs.fillEvenOdd() else cs.fill()
                }
            }
        }

        private fun applyStrokeState(elem: Element) {
            attr(elem, "stroke-width")?.toFloatOrNull()?.let { cs.setLineWidth(it) }

            attr(elem, "stroke-linecap")?.let { cap ->
                cs.setLineCapStyle(
                    when (cap) {
                        "round" -> 1
                        "square" -> 2
                        else -> 0 // butt
                    }
                )
            }

            attr(elem, "stroke-linejoin")?.let { join ->
                cs.setLineJoinStyle(
                    when (join) {
                        "round" -> 1
                        "bevel" -> 2
                        else -> 0 // miter
                    }
                )
            }

            attr(elem, "stroke-dasharray")?.takeIf { it != "none" }?.let { dashStr ->
                val dashes = dashStr.split(Regex("[,\\s]+")).mapNotNull { it.toFloatOrNull() }
                if (dashes.isNotEmpty()) {
                    val phase = attr(elem, "stroke-dashoffset")?.toFloatOrNull() ?: 0f
                    cs.setLineDashPattern(dashes.toFloatArray(), phase)
                }
            }
        }

        // ── Transforms ──────────────────────────────────────────────────

        private fun applyTransform(elem: Element) {
            val transform = elem.getAttribute("transform")
            if (transform.isEmpty()) return

            // Parse and apply transforms in order (left-to-right per SVG spec)
            for (match in TRANSFORM_RE.findAll(transform)) {
                val func = match.groupValues[1]
                val params = match.groupValues[2]
                    .split(Regex("[,\\s]+"))
                    .mapNotNull { it.trim().toFloatOrNull() }

                when (func) {
                    "translate" -> {
                        val tx = params.getOrElse(0) { 0f }
                        val ty = params.getOrElse(1) { 0f }
                        cs.transform(
                            Matrix(AffineTransform.getTranslateInstance(tx.toDouble(), ty.toDouble()))
                        )
                    }
                    "scale" -> {
                        val sx = params.getOrElse(0) { 1f }
                        val sy = params.getOrElse(1) { sx }
                        cs.transform(
                            Matrix(AffineTransform.getScaleInstance(sx.toDouble(), sy.toDouble()))
                        )
                    }
                    "rotate" -> {
                        val angle = Math.toRadians(params.getOrElse(0) { 0f }.toDouble())
                        if (params.size >= 3) {
                            cs.transform(
                                Matrix(
                                    AffineTransform.getRotateInstance(
                                        angle,
                                        params[1].toDouble(),
                                        params[2].toDouble(),
                                    )
                                )
                            )
                        } else {
                            cs.transform(Matrix(AffineTransform.getRotateInstance(angle)))
                        }
                    }
                    "matrix" -> {
                        if (params.size >= 6) {
                            cs.transform(
                                Matrix(params[0], params[1], params[2], params[3], params[4], params[5])
                            )
                        }
                    }
                    "skewX" -> {
                        val a = Math.toRadians(params.getOrElse(0) { 0f }.toDouble())
                        cs.transform(Matrix(AffineTransform(1.0, 0.0, kotlin.math.tan(a), 1.0, 0.0, 0.0)))
                    }
                    "skewY" -> {
                        val a = Math.toRadians(params.getOrElse(0) { 0f }.toDouble())
                        cs.transform(Matrix(AffineTransform(1.0, kotlin.math.tan(a), 0.0, 1.0, 0.0, 0.0)))
                    }
                }
            }
        }

        // ── Opacity ─────────────────────────────────────────────────────

        private fun applyOpacity(elem: Element) {
            val opacity = attr(elem, "opacity")?.toFloatOrNull()
            val fillOp = attr(elem, "fill-opacity")?.toFloatOrNull()
            val strokeOp = attr(elem, "stroke-opacity")?.toFloatOrNull()
            if (opacity == null && fillOp == null && strokeOp == null) return

            val gs = PDExtendedGraphicsState()
            val effFill = (opacity ?: 1f) * (fillOp ?: 1f)
            val effStroke = (opacity ?: 1f) * (strokeOp ?: 1f)
            if (effFill < 1f) gs.nonStrokingAlphaConstant = effFill
            if (effStroke < 1f) gs.strokingAlphaConstant = effStroke
            cs.setGraphicsStateParameters(gs)
        }

        // ── Clipping ────────────────────────────────────────────────────

        private fun applyClipPath(elem: Element) {
            val clipRef = attr(elem, "clip-path") ?: return
            val clipId = Regex("""url\(#([^)]+)\)""").find(clipRef)?.groupValues?.get(1) ?: return
            val clipElem = defs[clipId] ?: return

            for (i in 0 until clipElem.childNodes.length) {
                val node = clipElem.childNodes.item(i)
                if (node.nodeType != Node.ELEMENT_NODE) continue
                val child = node as Element
                when (child.localName) {
                    "rect" -> {
                        val x = child.getAttribute("x").toFloatOrNull() ?: 0f
                        val y = child.getAttribute("y").toFloatOrNull() ?: 0f
                        val w = child.getAttribute("width").toFloatOrNull() ?: continue
                        val h = child.getAttribute("height").toFloatOrNull() ?: continue
                        val rx = child.getAttribute("rx").toFloatOrNull() ?: 0f
                        val ry = child.getAttribute("ry").toFloatOrNull() ?: rx
                        if (rx > 0f || ry > 0f) {
                            val crx = rx.coerceAtMost(w / 2)
                            val cry = ry.coerceAtMost(h / 2)
                            if (crx >= w / 2 && cry >= h / 2) {
                                // Full-radius = ellipse (proven to work for clip paths)
                                SvgShapeRenderer.drawEllipse(cs, x + w / 2, y + h / 2, w / 2, h / 2)
                            } else {
                                // Partial radius: route through SVG path parser (arcToCubic is proven)
                                SvgPathParser.parse(SvgShapeRenderer.buildRoundedRectPathData(x, y, w, h, crx, cry), cs)
                            }
                        } else {
                            cs.addRect(x, y, w, h)
                        }
                    }
                    "path" -> {
                        child.getAttribute("d").takeIf { it.isNotEmpty() }?.let { SvgPathParser.parse(it, cs) }
                    }
                    "circle" -> {
                        val cx = child.getAttribute("cx").toFloatOrNull() ?: 0f
                        val cy = child.getAttribute("cy").toFloatOrNull() ?: 0f
                        val r = child.getAttribute("r").toFloatOrNull() ?: continue
                        SvgShapeRenderer.drawEllipse(cs, cx, cy, r, r)
                    }
                    "ellipse" -> {
                        val cx = child.getAttribute("cx").toFloatOrNull() ?: 0f
                        val cy = child.getAttribute("cy").toFloatOrNull() ?: 0f
                        val rx = child.getAttribute("rx").toFloatOrNull() ?: continue
                        val ry = child.getAttribute("ry").toFloatOrNull() ?: continue
                        SvgShapeRenderer.drawEllipse(cs, cx, cy, rx, ry)
                    }
                }
            }
            cs.clip()
        }

        // ── Attribute resolution ────────────────────────────────────────

        // Cache for parsed inline style attributes — avoids re-parsing the same style string
        private var cachedStyleElem: Element? = null
        private var cachedStyleMap: Map<String, String>? = null

        /** Resolves an SVG attribute, checking `style` attribute first (CSS inline styles). */
        private fun attr(elem: Element, name: String): String? {
            val style = elem.getAttribute("style")
            if (style.isNotEmpty()) {
                val map = if (elem === cachedStyleElem) cachedStyleMap!! else {
                    val m = style.split(";").mapNotNull { prop ->
                        val colon = prop.indexOf(':')
                        if (colon < 0) null
                        else prop.substring(0, colon).trim() to prop.substring(colon + 1).trim()
                    }.toMap()
                    cachedStyleElem = elem
                    cachedStyleMap = m
                    m
                }
                map[name]?.takeIf { it.isNotEmpty() }?.let { return it }
            }
            return elem.getAttribute(name).takeIf { it.isNotEmpty() }
        }

        companion object {
            private val TRANSFORM_RE =
                Regex("""(translate|scale|rotate|matrix|skewX|skewY)\(([^)]+)\)""")
        }
    }
}
