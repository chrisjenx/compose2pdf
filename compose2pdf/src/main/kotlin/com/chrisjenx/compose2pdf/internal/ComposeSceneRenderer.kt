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
 * Everything stable stays typed; only the divergent construction/drive calls are reflective, and
 * the reflection handles are resolved once (they are instance-independent) and reused per render.
 * If neither known shape resolves, [drawContent] fails fast with a descriptive [Compose2PdfException].
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

    private val composeSceneClass: Class<*> by lazy {
        classOrNull(COMPOSE_SCENE) ?: fail("$COMPOSE_SCENE not found")
    }

    // PlatformContext.Empty is a stateless `class` (public no-arg ctor) in both 1.11 and 1.12; one
    // shared empty instance is reused across renders (a scene keeps no mutable state on it).
    private val platformContext: Any by lazy {
        val cls = classOrNull(PLATFORM_CONTEXT_EMPTY) ?: fail("$PLATFORM_CONTEXT_EMPTY not found")
        cls.getDeclaredConstructor().make()
    }

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
    private fun findFactory(paramCount: Int): Method {
        val cls = classOrNull(FACTORY_CLASS) ?: fail("$FACTORY_CLASS not found")
        return cls.declaredMethods.firstOrNull { m ->
            Modifier.isStatic(m.modifiers) &&
                m.name.startsWith("CanvasLayersComposeScene") &&
                !m.name.contains("\$default") &&
                m.returnType.name == COMPOSE_SCENE &&
                m.parameterCount == paramCount
        } ?: fail("no ${paramCount}-arg CanvasLayersComposeScene factory on $FACTORY_CLASS")
    }

    /** A public method declared on the ComposeScene interface, by name + arity. */
    private fun sceneMethod(name: String, arity: Int): Method =
        composeSceneClass.methods.firstOrNull { it.name == name && it.parameterCount == arity }
            ?: fail("no $name/$arity on $COMPOSE_SCENE")

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

    private fun close(target: Any) {
        (target as? AutoCloseable)?.close() ?: fail("expected AutoCloseable, got ${target.javaClass.name}")
    }

    private sealed interface SceneDriver {
        fun render(density: Density, size: IntSize, composeCanvas: Any, content: @Composable () -> Unit)
    }

    /** CMP <= 1.11: factory(density, layoutDir, size, coroutineContext, platformContext, invalidate); render(canvas, nanoTime). */
    private object LegacyDriver : SceneDriver {
        private val sceneFactory by lazy { findFactory(paramCount = 6) }
        private val sceneSetContent by lazy { sceneMethod("setContent", 1) }
        private val sceneRender by lazy { sceneMethod("render", 2) }

        override fun render(density: Density, size: IntSize, composeCanvas: Any, content: @Composable () -> Unit) {
            val scene = sceneFactory.call(
                null, density, LayoutDirection.Ltr, size, Dispatchers.Unconfined, platformContext, NO_OP,
            ) ?: fail("CanvasLayersComposeScene factory returned null")
            try {
                sceneSetContent.call(scene, content)
                sceneRender.call(scene, composeCanvas, 0L)
            } finally {
                close(scene)
            }
        }
    }

    /**
     * CMP >= 1.12: a host-owned FrameRecomposer(coroutineContext, onError) replaces the legacy
     * coroutineContext/invalidate params, and the single render() call becomes explicit
     * performFrame -> measureAndLayout -> draw(canvas). The factory's two trailing Function0 params
     * (invalidate + a callback) are no-ops for one-shot offscreen rendering.
     */
    private object NextDriver : SceneDriver {
        private val recomposerCtor by lazy {
            val cls = classOrNull(FRAME_RECOMPOSER) ?: fail("$FRAME_RECOMPOSER not found")
            cls.constructors.firstOrNull { it.parameterCount == 2 } ?: fail("no 2-arg FrameRecomposer constructor")
        }
        private val recomposerPerformFrame by lazy {
            recomposerCtor.declaringClass.methods.firstOrNull { it.name == "performFrame" && it.parameterCount == 1 }
                ?: fail("no performFrame/1 on $FRAME_RECOMPOSER")
        }
        private val sceneFactory by lazy { findFactory(paramCount = 7) }
        private val sceneSetContent by lazy { sceneMethod("setContent", 2) }
        private val sceneMeasureAndLayout by lazy { sceneMethod("measureAndLayout", 0) }
        private val sceneDraw by lazy { sceneMethod("draw", 1) }

        override fun render(density: Density, size: IntSize, composeCanvas: Any, content: @Composable () -> Unit) {
            val frameRecomposer = recomposerCtor.make(Dispatchers.Unconfined, NO_OP)
            try {
                val scene = sceneFactory.call(
                    null, frameRecomposer, density, LayoutDirection.Ltr, size, platformContext, NO_OP, NO_OP,
                ) ?: fail("CanvasLayersComposeScene factory returned null")
                try {
                    sceneSetContent.call(scene, null, content) // CompositionContext defaults to null
                    recomposerPerformFrame.call(frameRecomposer, 0L)
                    sceneMeasureAndLayout.call(scene)
                    sceneDraw.call(scene, composeCanvas)
                } finally {
                    close(scene)
                }
            } finally {
                close(frameRecomposer)
            }
        }
    }
}
