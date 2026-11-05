package com.chrisjenx.compose2pdf.internal

import com.chrisjenx.compose2pdf.Compose2PdfException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Ensures Compose's Skiko graphics/text implementations are registered before any scene is built.
 *
 * CMP 1.13 moved this registration out of `ServiceLoader` into the `@InternalComposeUiApi`
 * `androidx.compose.ui.platform.registerSkikoComposeImplementation()` (registers `SkikoGraphics`,
 * `SkikoText` and `SkikoGraphicsCompatImpl` into their process-global registries), which only the
 * `ImageComposeScene` / `ComposeContainer` constructors call. compose2pdf's vector path builds a
 * `CanvasLayersComposeScene` directly (see [ComposeSceneRenderer]), so without this every render
 * fails with "No Compose UI graphics implementation is registered". 1.13 offers no public
 * equivalent.
 *
 * The function does not exist before 1.13 (registration was implicit), and compose2pdf ships ONE
 * binary spanning 1.11+, so it is resolved by reflection: present → invoked, absent → no-op.
 * Registering the same instances again is a no-op in Compose, so [ensureRegistered] runs before every
 * render rather than once per process — that keeps it correct even if something in the host
 * process calls `clearSkikoComposeImplementation()` between renders.
 */
internal object SkikoComposeRuntime {

    private const val RUNTIME_CLASS = "androidx.compose.ui.platform.ComposeUiSkikoRuntime_nonAndroidKt"
    private const val REGISTER = "registerSkikoComposeImplementation"

    /** The static no-arg register function, or null on CMP < 1.13 where registration is implicit. */
    private val register: Method? by lazy {
        val cls = classOrNull(RUNTIME_CLASS) ?: return@lazy null
        cls.methods.firstOrNull { it.name == REGISTER && it.parameterCount == 0 && Modifier.isStatic(it.modifiers) }
            ?: throw Compose2PdfException(
                "compose2pdf found $RUNTIME_CLASS but not a static no-arg $REGISTER(). The installed " +
                    "Compose Multiplatform version may have reshaped its Skiko runtime registration. " +
                    "Please file an issue including your CMP version.",
            )
    }

    fun ensureRegistered() {
        register?.invokeUnwrapped(null)
    }
}
