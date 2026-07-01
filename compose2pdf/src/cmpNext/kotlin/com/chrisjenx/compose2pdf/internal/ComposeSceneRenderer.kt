@file:OptIn(InternalComposeUiApi::class)

package com.chrisjenx.compose2pdf.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Canvas

/**
 * Compose scene driver for Compose Multiplatform ≥ 1.12.
 *
 * CMP 1.12 reworked the `@InternalComposeUiApi` scene API: `CanvasLayersComposeScene` now requires a
 * host-owned [FrameRecomposer] (replacing the `coroutineContext` + `invalidate` parameters), and the
 * single `render(canvas, nanoTime)` call was removed in favour of explicit
 * `performFrame` → `measureAndLayout` → `draw(canvas)` phases. This sequence mirrors how
 * `ImageComposeScene.render()` drives a frame internally in 1.12.
 *
 * Selected by `build.gradle.kts` (`src/cmpNext/kotlin`) when the resolved Compose version is
 * ≥ 1.12. Kept in sync with the `cmpLegacy` variant — only one is ever on the compile path, and both
 * must expose the same [ComposeSceneRenderer.drawContent] signature that [ComposeToSvg] calls.
 */
internal object ComposeSceneRenderer {

    /**
     * Drives composition, layout, and draw of [content] onto the Skia [canvas] at the given
     * size and [density].
     */
    fun drawContent(
        canvas: Canvas,
        widthPx: Int,
        heightPx: Int,
        density: Density,
        content: @Composable () -> Unit,
    ) {
        val frameRecomposer = FrameRecomposer(Dispatchers.Unconfined)
        try {
            val scene = CanvasLayersComposeScene(
                frameRecomposer = frameRecomposer,
                density = density,
                size = IntSize(widthPx, heightPx),
            )
            try {
                scene.setContent(content = content)
                frameRecomposer.performFrame(0L)
                scene.measureAndLayout()
                scene.draw(canvas.asComposeCanvas())
            } finally {
                scene.close()
            }
        } finally {
            // Nested so the recomposer is always released, even if scene construction or close throws.
            frameRecomposer.close()
        }
    }
}
