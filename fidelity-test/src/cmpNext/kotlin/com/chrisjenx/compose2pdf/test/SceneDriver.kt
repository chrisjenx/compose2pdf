@file:OptIn(InternalComposeUiApi::class)

package com.chrisjenx.compose2pdf.test

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
 * Fidelity-test scene driver for Compose Multiplatform ≥ 1.12 (`CanvasLayersComposeScene` now takes a
 * `FrameRecomposer`; rendering is `performFrame` → `measureAndLayout` → `draw(canvas)`).
 *
 * This is a test-module twin of the production `compose2pdf` `cmpNext` `ComposeSceneRenderer` —
 * duplicated only because the production seam is `internal` and not visible across the module
 * boundary. Selected by `fidelity-test/build.gradle.kts` (`src/cmpNext/kotlin`) for CMP ≥ 1.12.
 * Keep in sync with the `cmpLegacy` variant.
 */
internal fun drawComposeSceneToCanvas(
    canvas: Canvas,
    widthPx: Int,
    heightPx: Int,
    density: Density,
    content: @Composable () -> Unit,
) {
    registerSkikoIfNeeded()
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

/**
 * CMP 1.13+ no longer registers Skiko graphics/text via ServiceLoader; only ImageComposeScene /
 * ComposeContainer call `registerSkikoComposeImplementation()`, so a bare CanvasLayersComposeScene
 * must. Reflective (absent before 1.13, which this variant also covers) and idempotent — mirrors
 * the production `SkikoComposeRuntime`.
 */
private fun registerSkikoIfNeeded() {
    val cls = try {
        Class.forName("androidx.compose.ui.platform.ComposeUiSkikoRuntime_nonAndroidKt")
    } catch (e: ClassNotFoundException) {
        return
    }
    try {
        cls.getMethod("registerSkikoComposeImplementation").invoke(null)
    } catch (e: java.lang.reflect.InvocationTargetException) {
        throw e.targetException
    }
}
