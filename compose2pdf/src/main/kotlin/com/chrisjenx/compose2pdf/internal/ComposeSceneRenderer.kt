@file:OptIn(InternalComposeUiApi::class)

package com.chrisjenx.compose2pdf.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.chrisjenx.compose2pdf.Compose2PdfException
import kotlinx.coroutines.Dispatchers
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.CoroutineContext
import org.jetbrains.skia.Canvas

/**
 * Compose scene driver that renders [content] onto a Skia [Canvas] for vector extraction.
 *
 * The only Compose API able to draw scene commands onto an arbitrary Skia canvas is the
 * `@InternalComposeUiApi` `androidx.compose.ui.scene` package. It has no binary-compatibility
 * guarantee and was reshaped in CMP 1.12 (a host-owned `FrameRecomposer` +
 * `measureAndLayout`/`draw`, replacing the pre-1.12 `coroutineContext`/`invalidate` +
 * `render(canvas, nanoTime)`). compose2pdf ships ONE binary, so this driver resolves the scene
 * API by reflection at runtime, detecting the shape by structure (presence of `FrameRecomposer`
 * and factory arity) rather than a version string. One jar therefore runs on 1.11 and 1.12+.
 *
 * Everything stable stays typed; only the divergent construction/drive calls are reflective. If
 * neither known shape resolves, [drawContent] fails fast with a descriptive [Compose2PdfException].
 */
internal object ComposeSceneRenderer {

    fun drawContent(
        canvas: Canvas,
        widthPx: Int,
        heightPx: Int,
        density: Density,
        content: @Composable () -> Unit,
    ) {
        val composeCanvas: Any = canvas.asComposeCanvas()
        // The factory takes a boxed IntSize (mangled name, but the param is the value-class object,
        // not an unboxed long); passing it through Any args boxes it to the IntSize wrapper.
        driver.render(density, IntSize(widthPx, heightPx), composeCanvas, content)
    }

    // Detect + cache the strategy once. FrameRecomposer only exists on the >= 1.12 shape.
    private val driver: SceneDriver by lazy {
        if (classOrNull(FRAME_RECOMPOSER) != null) NextDriver else LegacyDriver
    }

    private const val FACTORY_CLASS = "androidx.compose.ui.scene.CanvasLayersComposeScene_skikoKt"
    private const val COMPOSE_SCENE = "androidx.compose.ui.scene.ComposeScene"
    private const val FRAME_RECOMPOSER = "androidx.compose.ui.platform.FrameRecomposer"
    private const val PLATFORM_CONTEXT_EMPTY = "androidx.compose.ui.platform.PlatformContext\$Empty"

    private val NO_OP: () -> Unit = {}

    private fun classOrNull(name: String): Class<*>? =
        try {
            Class.forName(name)
        } catch (e: ClassNotFoundException) {
            null
        }

    private fun fail(what: String, cause: Throwable? = null): Nothing =
        throw Compose2PdfException(
            "compose2pdf could not drive the Compose scene via reflection: $what. The installed " +
                "Compose Multiplatform version may have reshaped its internal " +
                "CanvasLayersComposeScene API. Please file an issue including your CMP version.",
            cause,
        )

    /** The single static factory on [FACTORY_CLASS] returning a ComposeScene with [paramCount] params. */
    private fun factory(paramCount: Int): Method {
        val cls = classOrNull(FACTORY_CLASS) ?: fail("$FACTORY_CLASS not found")
        return cls.declaredMethods.firstOrNull { m ->
            Modifier.isStatic(m.modifiers) &&
                m.name.startsWith("CanvasLayersComposeScene") &&
                !m.name.contains("\$default") &&
                m.returnType.name == COMPOSE_SCENE &&
                m.parameterCount == paramCount
        } ?: fail("no ${paramCount}-arg CanvasLayersComposeScene factory on $FACTORY_CLASS")
    }

    // PlatformContext.Empty is a stateless `class` (not a Kotlin `object`) with a public no-arg
    // constructor in both 1.11 and 1.12, so instantiate it rather than reading an INSTANCE field.
    private fun platformContextEmpty(): Any {
        val cls = classOrNull(PLATFORM_CONTEXT_EMPTY) ?: fail("$PLATFORM_CONTEXT_EMPTY not found")
        return cls.getDeclaredConstructor().newInstance()
    }

    /** Public method (incl. inherited) by simple name + arity, skipping Kotlin `$default` bridges. */
    private fun method(target: Any, name: String, arity: Int): Method =
        target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == arity }
            ?: fail("no $name/$arity on ${target.javaClass.name}")

    // invoke/newInstance wrap exceptions thrown by the target in InvocationTargetException; unwrap so
    // Compose's own exceptions (e.g. IllegalArgumentException for a negative density) propagate with
    // their real type, matching the pre-reflection direct calls.
    private fun Method.call(receiver: Any?, vararg args: Any?): Any? =
        try {
            invoke(receiver, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }

    private fun Constructor<*>.make(vararg args: Any?): Any =
        try {
            newInstance(*args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }

    /** setContent is (content) on <= 1.11 and (compositionContext, content) on >= 1.12. */
    private fun setContent(scene: Any, content: @Composable () -> Unit) {
        val m = scene.javaClass.methods.firstOrNull {
            it.name == "setContent" && it.parameterCount in 1..2
        } ?: fail("no setContent on ${scene.javaClass.name}")
        if (m.parameterCount == 1) {
            m.call(scene, content)
        } else {
            m.call(scene, null, content) // CompositionContext defaults to null
        }
    }

    private fun close(target: Any) {
        (target as? AutoCloseable)?.close() ?: method(target, "close", 0).call(target)
    }

    private sealed interface SceneDriver {
        fun render(density: Density, size: IntSize, composeCanvas: Any, content: @Composable () -> Unit)
    }

    /** CMP <= 1.11: factory(density, layoutDir, size, coroutineContext, platformContext, invalidate); render(canvas, nanoTime). */
    private object LegacyDriver : SceneDriver {
        override fun render(density: Density, size: IntSize, composeCanvas: Any, content: @Composable () -> Unit) {
            val scene = factory(paramCount = 6).call(
                null,
                density,
                LayoutDirection.Ltr,
                size,
                Dispatchers.Unconfined,
                platformContextEmpty(),
                NO_OP,
            ) ?: fail("CanvasLayersComposeScene factory returned null")
            try {
                setContent(scene, content)
                method(scene, "render", 2).call(scene, composeCanvas, 0L)
            } finally {
                close(scene)
            }
        }
    }

    /** CMP >= 1.12: FrameRecomposer(ctx, onError); factory(recomposer, density, layoutDir, size, platformContext, x, y); performFrame -> measureAndLayout -> draw(canvas). */
    private object NextDriver : SceneDriver {
        override fun render(density: Density, size: IntSize, composeCanvas: Any, content: @Composable () -> Unit) {
            val frClass = classOrNull(FRAME_RECOMPOSER) ?: fail("$FRAME_RECOMPOSER not found")
            val frameRecomposer = (frClass.constructors.firstOrNull { it.parameterCount == 2 }
                ?: fail("no 2-arg FrameRecomposer constructor"))
                .make(Dispatchers.Unconfined, NO_OP)
            try {
                val scene = factory(paramCount = 7).call(
                    null,
                    frameRecomposer,
                    density,
                    LayoutDirection.Ltr,
                    size,
                    platformContextEmpty(),
                    NO_OP,
                    NO_OP,
                ) ?: fail("CanvasLayersComposeScene factory returned null")
                try {
                    setContent(scene, content)
                    method(frameRecomposer, "performFrame", 1).call(frameRecomposer, 0L)
                    method(scene, "measureAndLayout", 0).call(scene)
                    method(scene, "draw", 1).call(scene, composeCanvas)
                } finally {
                    close(scene)
                }
            } finally {
                close(frameRecomposer)
            }
        }
    }
}
