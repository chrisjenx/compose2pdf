package com.chrisjenx.compose2pdf.internal

import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

// Shared helpers for compose2pdf's version-bridging reflective seams (ComposeSceneRenderer,
// SkikoComposeRuntime, SkiaFontStyleCompat).

internal fun classOrNull(name: String): Class<*>? =
    try {
        Class.forName(name)
    } catch (e: ClassNotFoundException) {
        null
    }

// invoke/newInstance wrap exceptions thrown by the target in InvocationTargetException; unwrap so
// Compose's/Skia's own exceptions (e.g. IllegalArgumentException for a negative density) propagate
// with their real type, matching what a direct call would throw.
internal fun Method.invokeUnwrapped(receiver: Any?, vararg args: Any?): Any? =
    try {
        invoke(receiver, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }

internal fun Constructor<*>.newInstanceUnwrapped(vararg args: Any?): Any =
    try {
        newInstance(*args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
