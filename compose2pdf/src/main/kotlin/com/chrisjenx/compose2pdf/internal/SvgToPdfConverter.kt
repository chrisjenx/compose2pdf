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
import kotlin.math.*

/**
 * Converts Skia-generated SVG to vector PDF using PDFBox.
 *
 * Handles SVG elements produced by Skia's SVGCanvas: shapes (rect, circle, ellipse,
 * line, polyline, polygon, path), text, groups, definitions (defs/use), clipping,
 * transforms, fills, strokes, and opacity.
 */
internal object SvgToPdfConverter {

    fun addPage(
        pdfDoc: PDDocument,
        svg: String,
        pageWidthPt: Float,
        pageHeightPt: Float,
        fontCache: MutableMap<String, PDFont> = mutableMapOf(),
    ) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val xmlDoc = factory.newDocumentBuilder().parse(svg.byteInputStream())
        val svgRoot = xmlDoc.documentElement

        val svgWidth = svgRoot.getAttribute("width").toFloatOrNull() ?: pageWidthPt
        val svgHeight = svgRoot.getAttribute("height").toFloatOrNull() ?: pageHeightPt
        val scaleX = pageWidthPt / svgWidth
        val scaleY = pageHeightPt / svgHeight

        val mediaBox = PDRectangle(pageWidthPt, pageHeightPt)
        val page = PDPage(mediaBox)
        pdfDoc.addPage(page)

        val defs = mutableMapOf<String, Element>()
        collectDefs(svgRoot, defs)

        val cs = PDPageContentStream(pdfDoc, page)
        try {
            // PDF: bottom-left origin. SVG: top-left. Flip Y and scale.
            cs.transform(Matrix(scaleX, 0f, 0f, -scaleY, 0f, pageHeightPt))
            PageRenderer(cs, pdfDoc, defs, fontCache).renderChildren(svgRoot)
        } finally {
            cs.close()
        }
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
    ) {
        // Per-page image cache: element ID → embedded PDImageXObject
        private val imageCache = mutableMapOf<String, PDImageXObject>()

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
            val w = attr(elem, "width")?.toFloatOrNull() ?: return restore()
            val h = attr(elem, "height")?.toFloatOrNull() ?: return restore()
            val rx = attr(elem, "rx")?.toFloatOrNull() ?: 0f
            val ry = attr(elem, "ry")?.toFloatOrNull() ?: rx

            if (rx > 0f || ry > 0f) {
                drawRoundedRect(x, y, w, h, rx.coerceAtMost(w / 2), ry.coerceAtMost(h / 2))
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
            val r = attr(elem, "r")?.toFloatOrNull() ?: return restore()

            drawEllipse(cx, cy, r, r)
            fillAndStroke(elem)
            cs.restoreGraphicsState()
        }

        private fun renderEllipse(elem: Element) {
            cs.saveGraphicsState()
            applyTransform(elem); applyOpacity(elem)

            val cx = attr(elem, "cx")?.toFloatOrNull() ?: 0f
            val cy = attr(elem, "cy")?.toFloatOrNull() ?: 0f
            val rx = attr(elem, "rx")?.toFloatOrNull() ?: return restore()
            val ry = attr(elem, "ry")?.toFloatOrNull() ?: return restore()

            drawEllipse(cx, cy, rx, ry)
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
                parseCssColor(strokeVal)?.let { (r, g, b) -> cs.setStrokingColor(r, g, b) }
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
                parseSvgPath(d)
                fillAndStroke(elem)
            }
            cs.restoreGraphicsState()
        }

        private fun renderText(elem: Element) {
            cs.saveGraphicsState()
            applyTransform(elem); applyOpacity(elem)

            val fontSize = attr(elem, "font-size")?.toFloatOrNull() ?: 12f
            val text = elem.textContent.trim()
            if (text.isEmpty()) return restore()

            val fillColor = attr(elem, "fill")
                ?.takeIf { it != "none" }
                ?.let { parseCssColor(it) }
                ?: Triple(0f, 0f, 0f) // SVG default: black
            cs.setNonStrokingColor(fillColor.first, fillColor.second, fillColor.third)

            val font = FontResolver.resolve(
                doc, fontCache,
                family = attr(elem, "font-family"),
                weight = attr(elem, "font-weight"),
                style = attr(elem, "font-style"),
            )

            val xAttr = attr(elem, "x") ?: ""
            val xPositions = xAttr.split(",").mapNotNull { it.trim().toFloatOrNull() }
            val yOffset = (attr(elem, "y") ?: "").split(",")
                .firstOrNull()?.trim()?.toFloatOrNull() ?: fontSize

            // Counter-flip Y for text (undo global Y-flip so glyphs render right-side up)
            cs.transform(Matrix(1f, 0f, 0f, -1f, 0f, 2f * yOffset))

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

            val width = attr(elem, "width")?.toFloatOrNull() ?: return restore()
            val height = attr(elem, "height")?.toFloatOrNull() ?: return restore()

            val href = elem.getAttribute("href").ifEmpty {
                elem.getAttributeNS("http://www.w3.org/1999/xlink", "href")
            } ?: return restore()

            val pdImage = decodeImage(href, elem.getAttribute("id")) ?: return restore()

            // In the Y-flipped coordinate system, flip the image back to right-side up.
            // Translate to (x, y+h), then scale Y by -1.
            val x = attr(elem, "x")?.toFloatOrNull() ?: 0f
            val y = attr(elem, "y")?.toFloatOrNull() ?: 0f
            cs.transform(
                Matrix(AffineTransform(1.0, 0.0, 0.0, -1.0, x.toDouble(), (y + height).toDouble()))
            )
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
                val color = fill?.let { parseCssColor(it) }
                if (color != null) {
                    cs.setNonStrokingColor(color.first, color.second, color.third)
                } else {
                    cs.setNonStrokingColor(0f, 0f, 0f) // SVG default: black
                }
            }

            if (hasStroke) {
                applyStrokeState(elem)
                parseCssColor(stroke!!)?.let { (r, g, b) -> cs.setStrokingColor(r, g, b) }
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
                        cs.transform(Matrix(AffineTransform(1.0, 0.0, tan(a), 1.0, 0.0, 0.0)))
                    }
                    "skewY" -> {
                        val a = Math.toRadians(params.getOrElse(0) { 0f }.toDouble())
                        cs.transform(Matrix(AffineTransform(1.0, tan(a), 0.0, 1.0, 0.0, 0.0)))
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
                                drawEllipse(x + w / 2, y + h / 2, w / 2, h / 2)
                            } else {
                                // Partial radius: route through SVG path parser (arcToCubic is proven)
                                parseSvgPath(buildRoundedRectPathData(x, y, w, h, crx, cry))
                            }
                        } else {
                            cs.addRect(x, y, w, h)
                        }
                    }
                    "path" -> {
                        child.getAttribute("d").takeIf { it.isNotEmpty() }?.let { parseSvgPath(it) }
                    }
                    "circle" -> {
                        val cx = child.getAttribute("cx").toFloatOrNull() ?: 0f
                        val cy = child.getAttribute("cy").toFloatOrNull() ?: 0f
                        val r = child.getAttribute("r").toFloatOrNull() ?: continue
                        drawEllipse(cx, cy, r, r)
                    }
                    "ellipse" -> {
                        val cx = child.getAttribute("cx").toFloatOrNull() ?: 0f
                        val cy = child.getAttribute("cy").toFloatOrNull() ?: 0f
                        val rx = child.getAttribute("rx").toFloatOrNull() ?: continue
                        val ry = child.getAttribute("ry").toFloatOrNull() ?: continue
                        drawEllipse(cx, cy, rx, ry)
                    }
                }
            }
            cs.clip()
        }

        // ── Drawing helpers ─────────────────────────────────────────────

        /** Draws an ellipse using 4 cubic Bezier curves (standard approximation). */
        private fun drawEllipse(cx: Float, cy: Float, rx: Float, ry: Float) {
            val kx = rx * KAPPA
            val ky = ry * KAPPA
            cs.moveTo(cx + rx, cy)
            cs.curveTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
            cs.curveTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
            cs.curveTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
            cs.curveTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy)
            cs.closePath()
        }

        /** Draws a rounded rectangle using line segments and cubic Bezier corners. */
        private fun drawRoundedRect(
            x: Float, y: Float, w: Float, h: Float, rx: Float, ry: Float,
        ) {
            val kx = rx * KAPPA
            val ky = ry * KAPPA
            // Start at top-left + rx (just past the top-left corner curve)
            cs.moveTo(x + rx, y)
            // Top edge → top-right corner
            cs.lineTo(x + w - rx, y)
            cs.curveTo(x + w - rx + kx, y, x + w, y + ry - ky, x + w, y + ry)
            // Right edge → bottom-right corner
            cs.lineTo(x + w, y + h - ry)
            cs.curveTo(x + w, y + h - ry + ky, x + w - rx + kx, y + h, x + w - rx, y + h)
            // Bottom edge → bottom-left corner
            cs.lineTo(x + rx, y + h)
            cs.curveTo(x + rx - kx, y + h, x, y + h - ry + ky, x, y + h - ry)
            // Left edge → top-left corner
            cs.lineTo(x, y + ry)
            cs.curveTo(x, y + ry - ky, x + rx - kx, y, x + rx, y)
            cs.closePath()
        }

        /** Builds SVG path data for a rounded rectangle (for use with parseSvgPath in clip paths). */
        private fun buildRoundedRectPathData(
            x: Float, y: Float, w: Float, h: Float, rx: Float, ry: Float,
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

        // ── SVG path parser ─────────────────────────────────────────────

        /**
         * Parses an SVG path data string and emits the corresponding PDFBox path operations.
         * Supports all SVG path commands including quadratic/smooth curves and arcs.
         */
        private fun parseSvgPath(d: String) {
            val tokens = PATH_TOKEN_RE.findAll(d).map { it.value }.toList()
            var i = 0
            var cx = 0f; var cy = 0f   // current point
            var sx = 0f; var sy = 0f   // subpath start (for Z)
            var lastCmd = ' '
            // Tracking for smooth curve reflection
            var lcp2x = 0f; var lcp2y = 0f // last cubic control point 2 (for S/s)
            var lqpx = 0f; var lqpy = 0f   // last quadratic control point (for T/t)

            fun nf(): Float = tokens[i++].toFloat()

            while (i < tokens.size) {
                val tok = tokens[i]
                val cmd: Char
                if (tok.length == 1 && tok[0] in PATH_COMMANDS) {
                    cmd = tok[0]
                    i++
                } else {
                    // Implicit repeat: M→L, m→l, otherwise same command
                    cmd = when (lastCmd) {
                        'M' -> 'L'
                        'm' -> 'l'
                        'Z', 'z', ' ' -> break
                        else -> lastCmd
                    }
                }

                when (cmd) {
                    'M' -> {
                        cx = nf(); cy = nf(); sx = cx; sy = cy
                        cs.moveTo(cx, cy)
                    }
                    'm' -> {
                        cx += nf(); cy += nf(); sx = cx; sy = cy
                        cs.moveTo(cx, cy)
                    }
                    'L' -> { cx = nf(); cy = nf(); cs.lineTo(cx, cy) }
                    'l' -> { cx += nf(); cy += nf(); cs.lineTo(cx, cy) }
                    'H' -> { cx = nf(); cs.lineTo(cx, cy) }
                    'h' -> { cx += nf(); cs.lineTo(cx, cy) }
                    'V' -> { cy = nf(); cs.lineTo(cx, cy) }
                    'v' -> { cy += nf(); cs.lineTo(cx, cy) }
                    'C' -> {
                        val x1 = nf(); val y1 = nf()
                        val x2 = nf(); val y2 = nf()
                        cx = nf(); cy = nf()
                        cs.curveTo(x1, y1, x2, y2, cx, cy)
                        lcp2x = x2; lcp2y = y2
                    }
                    'c' -> {
                        val x1 = cx + nf(); val y1 = cy + nf()
                        val x2 = cx + nf(); val y2 = cy + nf()
                        cx += nf(); cy += nf()
                        cs.curveTo(x1, y1, x2, y2, cx, cy)
                        lcp2x = x2; lcp2y = y2
                    }
                    'S' -> {
                        val x1 = 2 * cx - lcp2x; val y1 = 2 * cy - lcp2y
                        val x2 = nf(); val y2 = nf()
                        cx = nf(); cy = nf()
                        cs.curveTo(x1, y1, x2, y2, cx, cy)
                        lcp2x = x2; lcp2y = y2
                    }
                    's' -> {
                        val x1 = 2 * cx - lcp2x; val y1 = 2 * cy - lcp2y
                        val x2 = cx + nf(); val y2 = cy + nf()
                        cx += nf(); cy += nf()
                        cs.curveTo(x1, y1, x2, y2, cx, cy)
                        lcp2x = x2; lcp2y = y2
                    }
                    'Q' -> {
                        val qx = nf(); val qy = nf()
                        val x = nf(); val y = nf()
                        quadToCubic(cx, cy, qx, qy, x, y)
                        lqpx = qx; lqpy = qy; cx = x; cy = y
                    }
                    'q' -> {
                        val qx = cx + nf(); val qy = cy + nf()
                        val x = cx + nf(); val y = cy + nf()
                        quadToCubic(cx, cy, qx, qy, x, y)
                        lqpx = qx; lqpy = qy; cx = x; cy = y
                    }
                    'T' -> {
                        val qx = 2 * cx - lqpx; val qy = 2 * cy - lqpy
                        val x = nf(); val y = nf()
                        quadToCubic(cx, cy, qx, qy, x, y)
                        lqpx = qx; lqpy = qy; cx = x; cy = y
                    }
                    't' -> {
                        val qx = 2 * cx - lqpx; val qy = 2 * cy - lqpy
                        val x = cx + nf(); val y = cy + nf()
                        quadToCubic(cx, cy, qx, qy, x, y)
                        lqpx = qx; lqpy = qy; cx = x; cy = y
                    }
                    'A' -> {
                        val rx = nf(); val ry = nf(); val rot = nf()
                        val la = nf().toInt() != 0; val sw = nf().toInt() != 0
                        val x = nf(); val y = nf()
                        arcToCubic(cx, cy, rx, ry, rot, la, sw, x, y)
                        cx = x; cy = y
                    }
                    'a' -> {
                        val rx = nf(); val ry = nf(); val rot = nf()
                        val la = nf().toInt() != 0; val sw = nf().toInt() != 0
                        val x = cx + nf(); val y = cy + nf()
                        arcToCubic(cx, cy, rx, ry, rot, la, sw, x, y)
                        cx = x; cy = y
                    }
                    'Z', 'z' -> {
                        cs.closePath(); cx = sx; cy = sy
                    }
                }

                // Reset smooth curve control points when previous wasn't a matching type
                if (cmd !in "CcSs") { lcp2x = cx; lcp2y = cy }
                if (cmd !in "QqTt") { lqpx = cx; lqpy = cy }
                lastCmd = cmd
            }
        }

        /** Converts a quadratic Bezier to cubic Bezier (PDFBox only supports cubic). */
        private fun quadToCubic(
            x0: Float, y0: Float, qx: Float, qy: Float, x: Float, y: Float,
        ) {
            val c1x = x0 + 2f / 3f * (qx - x0)
            val c1y = y0 + 2f / 3f * (qy - y0)
            val c2x = x + 2f / 3f * (qx - x)
            val c2y = y + 2f / 3f * (qy - y)
            cs.curveTo(c1x, c1y, c2x, c2y, x, y)
        }

        /**
         * Converts an SVG arc to one or more cubic Bezier curves.
         * Implements the SVG spec endpoint-to-center arc parameterization (F.6).
         */
        private fun arcToCubic(
            x1: Float, y1: Float,
            rxIn: Float, ryIn: Float,
            xRotDeg: Float,
            largeArc: Boolean,
            sweep: Boolean,
            x2: Float, y2: Float,
        ) {
            // Degenerate: same point → no-op
            if (x1 == x2 && y1 == y2) return
            // Degenerate: zero radius → straight line
            var rx = abs(rxIn).toDouble()
            var ry = abs(ryIn).toDouble()
            if (rx == 0.0 || ry == 0.0) { cs.lineTo(x2, y2); return }

            val phi = Math.toRadians(xRotDeg.toDouble())
            val cp = cos(phi); val sp = sin(phi)

            // Step 1: Compute (x1', y1') in rotated frame
            val dx = (x1 - x2).toDouble() / 2.0
            val dy = (y1 - y2).toDouble() / 2.0
            val x1p = cp * dx + sp * dy
            val y1p = -sp * dx + cp * dy

            // Step 2: Ensure radii are large enough
            val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
            if (lambda > 1.0) {
                val s = sqrt(lambda); rx *= s; ry *= s
            }
            val rxSq = rx * rx; val rySq = ry * ry
            val x1pSq = x1p * x1p; val y1pSq = y1p * y1p

            // Step 3: Compute center point in rotated frame
            var sq = ((rxSq * rySq - rxSq * y1pSq - rySq * x1pSq) /
                (rxSq * y1pSq + rySq * x1pSq)).coerceAtLeast(0.0)
            sq = sqrt(sq)
            if (largeArc == sweep) sq = -sq
            val cxp = sq * rx * y1p / ry
            val cyp = -sq * ry * x1p / rx

            // Transform center to world coordinates
            val mx = (x1 + x2).toDouble() / 2.0
            val my = (y1 + y2).toDouble() / 2.0
            val ccx = cp * cxp - sp * cyp + mx
            val ccy = sp * cxp + cp * cyp + my

            // Step 4: Compute start angle and sweep
            val theta1 = vecAngle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
            var dTheta = vecAngle(
                (x1p - cxp) / rx, (y1p - cyp) / ry,
                (-x1p - cxp) / rx, (-y1p - cyp) / ry,
            )
            if (!sweep && dTheta > 0) dTheta -= 2 * PI
            if (sweep && dTheta < 0) dTheta += 2 * PI

            // Step 5: Split into ≤90° segments, approximate each as cubic Bezier
            val numSegs = ceil(abs(dTheta) / (PI / 2)).toInt().coerceAtLeast(1)
            val segAngle = dTheta / numSegs
            val alpha = 4.0 / 3.0 * tan(segAngle / 4.0)

            var t = theta1
            for (s in 0 until numSegs) {
                val t2 = t + segAngle
                val cosT1 = cos(t); val sinT1 = sin(t)
                val cosT2 = cos(t2); val sinT2 = sin(t2)

                // Points on the ellipse in local frame
                val ep1x = rx * cosT1; val ep1y = ry * sinT1
                val ep2x = rx * cosT2; val ep2y = ry * sinT2

                // Control points via tangent vectors
                val c1x = ep1x - alpha * rx * sinT1
                val c1y = ep1y + alpha * ry * cosT1
                val c2x = ep2x + alpha * rx * sinT2
                val c2y = ep2y - alpha * ry * cosT2

                // Transform to world coordinates (rotate by phi, translate by center)
                fun wx(ex: Double, ey: Double) = (cp * ex - sp * ey + ccx).toFloat()
                fun wy(ex: Double, ey: Double) = (sp * ex + cp * ey + ccy).toFloat()

                cs.curveTo(
                    wx(c1x, c1y), wy(c1x, c1y),
                    wx(c2x, c2y), wy(c2x, c2y),
                    wx(ep2x, ep2y), wy(ep2x, ep2y),
                )
                t = t2
            }
        }

        /** Computes the angle between two 2D vectors per SVG spec F.6.5. */
        private fun vecAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            var a = acos((dot / len).coerceIn(-1.0, 1.0))
            if (ux * vy - uy * vx < 0) a = -a
            return a
        }

        // ── Attribute resolution ────────────────────────────────────────

        /** Resolves an SVG attribute, checking `style` attribute first (CSS inline styles). */
        private fun attr(elem: Element, name: String): String? {
            val style = elem.getAttribute("style")
            if (style.isNotEmpty()) {
                for (prop in style.split(";")) {
                    val colon = prop.indexOf(':')
                    if (colon < 0) continue
                    if (prop.substring(0, colon).trim() == name) {
                        return prop.substring(colon + 1).trim().takeIf { it.isNotEmpty() }
                    }
                }
            }
            return elem.getAttribute(name).takeIf { it.isNotEmpty() }
        }

        // ── Color parsing ───────────────────────────────────────────────

        private fun parseCssColor(color: String): Triple<Float, Float, Float>? {
            val c = color.trim().lowercase()
            return when {
                c.startsWith("#") -> parseHexColor(c)
                c.startsWith("rgba(") || c.startsWith("rgb(") -> parseRgbFunc(c)
                else -> CSS_NAMED_COLORS[c]
            }
        }

        private fun parseHexColor(c: String): Triple<Float, Float, Float>? {
            fun h(start: Int, end: Int) = c.substring(start, end).toIntOrNull(16)?.div(255f)
            fun hh(pos: Int) = c.substring(pos, pos + 1).repeat(2).toIntOrNull(16)?.div(255f)
            return when (c.length) {
                9 -> { // #RRGGBBAA (alpha ignored)
                    val r = h(1, 3) ?: return null; val g = h(3, 5) ?: return null; val b = h(5, 7) ?: return null
                    Triple(r, g, b)
                }
                7 -> { // #RRGGBB
                    val r = h(1, 3) ?: return null; val g = h(3, 5) ?: return null; val b = h(5, 7) ?: return null
                    Triple(r, g, b)
                }
                5 -> { // #RGBA (alpha ignored)
                    val r = hh(1) ?: return null; val g = hh(2) ?: return null; val b = hh(3) ?: return null
                    Triple(r, g, b)
                }
                4 -> { // #RGB
                    val r = hh(1) ?: return null; val g = hh(2) ?: return null; val b = hh(3) ?: return null
                    Triple(r, g, b)
                }
                else -> null
            }
        }

        /** Parses rgb()/rgba() with comma-separated, space-separated, or percentage values. */
        private fun parseRgbFunc(c: String): Triple<Float, Float, Float>? {
            val inner = c.substringAfter("(").substringBefore(")")
            val parts = inner.replace("/", " ").split(Regex("[,\\s]+"))
                .map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 3) return null
            fun comp(s: String): Float? = when {
                s.endsWith("%") -> s.removeSuffix("%").toFloatOrNull()?.div(100f)
                "." in s -> s.toFloatOrNull() // Float 0.0–1.0
                else -> s.toFloatOrNull()?.div(255f) // Integer 0–255
            }
            val r = comp(parts[0]) ?: return null
            val g = comp(parts[1]) ?: return null
            val b = comp(parts[2]) ?: return null
            return Triple(r, g, b)
        }

        companion object {
            /** Bezier approximation constant for circles: 4 * (sqrt(2) - 1) / 3 */
            private const val KAPPA = 0.5522847498f
            private val TRANSFORM_RE =
                Regex("""(translate|scale|rotate|matrix|skewX|skewY)\(([^)]+)\)""")
            private val PATH_TOKEN_RE =
                Regex("""[MmLlHhVvCcSsQqTtAaZz]|[+-]?(?:\d+\.?\d*|\.\d+)""")
            private const val PATH_COMMANDS = "MmLlHhVvCcSsQqTtAaZz"
            private val CSS_NAMED_COLORS = mapOf(
                "black" to Triple(0f, 0f, 0f),
                "white" to Triple(1f, 1f, 1f),
                "red" to Triple(1f, 0f, 0f),
                "green" to Triple(0f, 0.502f, 0f),
                "blue" to Triple(0f, 0f, 1f),
                "yellow" to Triple(1f, 1f, 0f),
                "cyan" to Triple(0f, 1f, 1f),
                "aqua" to Triple(0f, 1f, 1f),
                "magenta" to Triple(1f, 0f, 1f),
                "fuchsia" to Triple(1f, 0f, 1f),
                "gray" to Triple(0.502f, 0.502f, 0.502f),
                "grey" to Triple(0.502f, 0.502f, 0.502f),
                "silver" to Triple(0.753f, 0.753f, 0.753f),
                "maroon" to Triple(0.502f, 0f, 0f),
                "olive" to Triple(0.502f, 0.502f, 0f),
                "lime" to Triple(0f, 1f, 0f),
                "teal" to Triple(0f, 0.502f, 0.502f),
                "navy" to Triple(0f, 0f, 0.502f),
                "orange" to Triple(1f, 0.647f, 0f),
                "purple" to Triple(0.502f, 0f, 0.502f),
                "pink" to Triple(1f, 0.753f, 0.796f),
                "brown" to Triple(0.647f, 0.165f, 0.165f),
            )
        }
    }
}
