@file:OptIn(InternalComposeUiApi::class)

package com.chrisjenx.compose2pdf.test

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Canvas

/**
 * Fidelity-test scene driver for Compose Multiplatform ≤ 1.11 (pre-1.12 `CanvasLayersComposeScene`
 * API: `coroutineContext`/`invalidate` + `render(canvas, nanoTime)`).
 *
 * This is a test-module twin of the production `compose2pdf` `cmpLegacy` `ComposeSceneRenderer` —
 * duplicated only because the production seam is `internal` and not visible across the module
 * boundary. Selected by `fidelity-test/build.gradle.kts` (`src/cmpLegacy/kotlin`) for CMP < 1.12.
 * Keep in sync with the `cmpNext` variant.
 */
internal fun drawComposeSceneToCanvas(
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
