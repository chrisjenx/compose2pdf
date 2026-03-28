@file:OptIn(InternalComposeUiApi::class)

package com.chrisjenx.compose2pdf.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.OutputWStream
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGCanvas

/**
 * Renders Compose content to SVG via Skia's SVGCanvas on iOS.
 * Same logic as the JVM version but uses OutputWStream.toData() instead of ByteArrayOutputStream.
 */
internal object ComposeToSvg {

    fun render(
        widthPx: Int,
        heightPx: Int,
        density: Density,
        content: @Composable () -> Unit,
    ): String {
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

        val wstream = OutputWStream()
        val svgCanvas = SVGCanvas.make(
            Rect.makeWH(widthPx.toFloat(), heightPx.toFloat()),
            wstream,
            convertTextToPaths = false,
            prettyXML = false,
        )

        picture.playback(svgCanvas)
        svgCanvas.close()
        val data = wstream.toData()
        wstream.close()
        picture.close()

        return data.bytes.decodeToString()
    }

    data class RenderResult(
        val svg: String,
        val measuredHeightPx: Int,
    )

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

    fun measureContentHeight(
        widthPx: Int,
        maxHeightPx: Int,
        density: Density,
        content: @Composable () -> Unit,
    ): Int {
        var measuredHeight = 0
        val recorder = PictureRecorder()
        val recordCanvas = recorder.beginRecording(
            Rect.makeWH(widthPx.toFloat(), maxHeightPx.toFloat())
        )
        val scene = CanvasLayersComposeScene(
            density = density,
            size = IntSize(widthPx, maxHeightPx),
            coroutineContext = Dispatchers.Unconfined,
            invalidate = {},
        )
        scene.setContent {
            Box(Modifier.onGloballyPositioned { coords ->
                measuredHeight = coords.size.height
            }) {
                content()
            }
        }
        scene.render(recordCanvas.asComposeCanvas(), nanoTime = 0)
        scene.close()
        recorder.finishRecordingAsPicture().close()
        return measuredHeight
    }
}
