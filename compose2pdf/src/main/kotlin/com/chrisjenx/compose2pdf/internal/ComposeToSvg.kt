package com.chrisjenx.compose2pdf.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.OutputWStream
import org.jetbrains.skia.Picture
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGCanvas
import java.io.ByteArrayOutputStream

/**
 * Shared utility for rendering Compose content to SVG via Skia's SVGCanvas.
 * Used by both PdfRenderer (SVG → PDF) and HtmlRenderer (SVG → HTML).
 *
 * The version-specific Compose scene drive (composition → layout → draw onto a Skia canvas) is
 * delegated to [ComposeSceneRenderer]. That seam has two implementations selected by the build:
 * `src/cmpLegacy` for Compose Multiplatform ≤ 1.11 and `src/cmpNext` for CMP ≥ 1.12. It exists
 * because the `@InternalComposeUiApi` `CanvasLayersComposeScene` API the renderer relies on changed
 * shape in CMP 1.12 (see each variant for details). Everything else here is version-agnostic.
 */
internal object ComposeToSvg {

    /**
     * Renders composable content to an SVG string.
     *
     * @param widthPx Width in pixels.
     * @param heightPx Height in pixels.
     * @param density Render density.
     * @param content The composable content to render.
     * @return SVG string.
     */
    fun render(
        widthPx: Int,
        heightPx: Int,
        density: Density,
        content: @Composable () -> Unit,
    ): String {
        // Step 1: Record Compose draw commands via PictureRecorder
        val picture = recordScene(widthPx, heightPx, density, content)

        // Step 2: Replay onto SVGCanvas to get vector SVG
        val baos = ByteArrayOutputStream()
        val wstream = OutputWStream(baos)
        val svgCanvas = SVGCanvas.make(
            Rect.makeWH(widthPx.toFloat(), heightPx.toFloat()),
            wstream,
            convertTextToPaths = false,
            prettyXML = false,
        )

        picture.playback(svgCanvas)
        svgCanvas.close()
        wstream.close()

        return baos.toString(Charsets.UTF_8)
    }

    /**
     * Result of rendering with measurement — contains both the SVG and the measured content height.
     */
    data class RenderResult(
        val svg: String,
        val measuredHeightPx: Int,
    )

    /**
     * Renders composable content to SVG and measures its natural height.
     *
     * The content is wrapped in a Box with [onGloballyPositioned] to capture the
     * actual measured height. The scene is created at [maxHeightPx] to allow content
     * to expand vertically.
     *
     * @param widthPx Width in pixels.
     * @param maxHeightPx Maximum height in pixels (scene height).
     * @param density Render density.
     * @param content The composable content to render.
     * @return SVG string and measured content height in pixels.
     */
    fun renderWithMeasurement(
        widthPx: Int,
        maxHeightPx: Int,
        density: Density,
        content: @Composable () -> Unit,
    ): RenderResult {
        var measuredHeight = 0
        val svg = render(widthPx, maxHeightPx, density) {
            Box(Modifier.onGloballyPositioned { coords ->
                measuredHeight = coords.size.height
            }) {
                content()
            }
        }
        return RenderResult(svg, measuredHeight)
    }

    /**
     * Measures the natural height of composable content without producing SVG output.
     *
     * Uses a PictureRecorder to drive composition and layout without allocating a bitmap
     * or generating SVG. Lightweight — suitable for measurement-only passes.
     *
     * @param widthPx Width in pixels.
     * @param maxHeightPx Maximum height in pixels (scene height).
     * @param density Render density.
     * @param content The composable content to measure.
     * @return Measured content height in pixels.
     */
    fun measureContentHeight(
        widthPx: Int,
        maxHeightPx: Int,
        density: Density,
        content: @Composable () -> Unit,
    ): Int {
        var measuredHeight = 0
        // Record only to drive composition + layout; the resulting picture is discarded (no SVG).
        recordScene(widthPx, maxHeightPx, density) {
            Box(Modifier.onGloballyPositioned { coords ->
                measuredHeight = coords.size.height
            }) {
                content()
            }
        }.close()
        return measuredHeight
    }

    /**
     * Records [content] into a Skia [Picture] at the given size/density, driving composition, layout,
     * and draw via the version-specific [ComposeSceneRenderer]. Shared by [render] (which replays the
     * picture onto an SVGCanvas) and [measureContentHeight] (which discards it after measurement).
     */
    private fun recordScene(
        widthPx: Int,
        heightPx: Int,
        density: Density,
        content: @Composable () -> Unit,
    ): Picture {
        val recorder = PictureRecorder()
        val recordCanvas = recorder.beginRecording(
            Rect.makeWH(widthPx.toFloat(), heightPx.toFloat())
        )
        ComposeSceneRenderer.drawContent(recordCanvas, widthPx, heightPx, density, content)
        return recorder.finishRecordingAsPicture()
    }
}
