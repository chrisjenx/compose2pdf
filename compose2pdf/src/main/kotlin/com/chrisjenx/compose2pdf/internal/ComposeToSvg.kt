@file:OptIn(InternalComposeUiApi::class)

package com.chrisjenx.compose2pdf.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.OutputWStream
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGCanvas
import java.io.ByteArrayOutputStream

/**
 * Shared utility for rendering Compose content to SVG via Skia's SVGCanvas.
 * Used by both PdfRenderer (SVG → PDF) and HtmlRenderer (SVG → HTML).
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
        val recorder = PictureRecorder()
        val recordCanvas = recorder.beginRecording(
            Rect.makeWH(widthPx.toFloat(), heightPx.toFloat())
        )

        val scene = CanvasLayersComposeScene(
            density = density,
            size = IntSize(widthPx, heightPx),
            coroutineContext = Dispatchers.Unconfined,
            invalidate = {},
        )
        scene.setContent(content)
        scene.render(recordCanvas.asComposeCanvas(), nanoTime = 0)
        scene.close()

        val picture = recorder.finishRecordingAsPicture()

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
}
