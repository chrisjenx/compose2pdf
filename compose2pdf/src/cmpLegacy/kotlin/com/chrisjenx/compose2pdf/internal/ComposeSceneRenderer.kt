@file:OptIn(InternalComposeUiApi::class)

package com.chrisjenx.compose2pdf.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Canvas

/**
 * Compose scene driver for Compose Multiplatform ≤ 1.11.
 *
 * Uses the pre-1.12 `CanvasLayersComposeScene` API: the scene owns its own recomposition via the
 * `coroutineContext` + `invalidate` parameters, and a single `render(canvas, nanoTime)` call
 * composes, lays out, and draws in one pass.
 *
 * Selected by `build.gradle.kts` (`src/cmpLegacy/kotlin`) when the resolved Compose version is
 * < 1.12. Kept in sync with the `cmpNext` variant — only one is ever on the compile path, and both
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
        val scene = CanvasLayersComposeScene(
            density = density,
            size = IntSize(widthPx, heightPx),
            coroutineContext = Dispatchers.Unconfined,
            invalidate = {},
        )
        try {
            scene.setContent(content)
            scene.render(canvas.asComposeCanvas(), nanoTime = 0)
        } finally {
            scene.close()
        }
    }
}
