package com.chrisjenx.compose2pdf.internal

import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.paragraph.FontCollection
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Version-agnostic access to Skia's `FontStyle`, which changed **binary shape** in skiko 0.156
 * (Compose Multiplatform 1.13):
 *
 * - skiko <= 0.15x (CMP 1.11 / 1.12): `FontStyle` is a regular class. `Typeface.getFontStyle()`
 *   returns it; `FontCollection.findTypefaces(String[], FontStyle)` and
 *   `FontMgr.matchFamilyStyle(String, FontStyle)` take it.
 * - skiko >= 0.156 (CMP 1.13): `FontStyle` is an inline value class over `Int`, so every member
 *   touching it is name-mangled (`getFontStyle-Owqf3f4(): int`, `findTypefaces-ooATI1c(String[], int)`,
 *   `matchFamilyStyle-ooATI1c(String, int)`) and its accessors are static `-impl` functions.
 *
 * compose2pdf ships ONE binary spanning both, so a typed reference compiled against either shape
 * throws `NoSuchMethodError` on the other. Like [ComposeSceneRenderer], this resolves the members
 * by structure (base name + parameter shape, never a mangling hash or version string) at runtime.
 * Callers in [ComposeFontStack] already fail soft, so any failure here degrades font embedding to
 * the other resolution strategies rather than breaking a render.
 */
internal object SkiaFontStyleCompat {

    private const val FONT_STYLE = "org.jetbrains.skia.FontStyle"
    private const val NORMAL_WIDTH = 5

    private val fontStyleClass: Class<*> by lazy { Class.forName(FONT_STYLE) }

    /** true when FontStyle is the >= 0.156 inline value class (members exchange a raw `int`). */
    private val isValueClass: Boolean by lazy {
        fontStyleClass.methods.any { it.name == "box-impl" && Modifier.isStatic(it.modifiers) }
    }

    /** Builds the argument a FontStyle-taking Skia method expects: a boxed FontStyle or its raw `int`. */
    private fun styleArg(weight: Int, italic: Boolean): Any {
        val slant = if (italic) FontSlant.ITALIC else FontSlant.UPRIGHT
        return if (isValueClass) {
            staticCtorImpl.call(null, weight, NORMAL_WIDTH, slant)!!
        } else {
            fontStyleClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, FontSlant::class.java)
                .newInstance(weight, NORMAL_WIDTH, slant)
        }
    }

    private val staticCtorImpl: Method by lazy {
        fontStyleClass.methods.first {
            it.name == "constructor-impl" && Modifier.isStatic(it.modifiers) && it.parameterCount == 3
        }
    }

    /** Instance getter on the boxed class (`getWeight`) or static `-impl` taking the raw int (`getWeight-…`). */
    private fun accessor(baseName: String): Method =
        if (isValueClass) {
            fontStyleClass.methods.first {
                Modifier.isStatic(it.modifiers) && it.parameterCount == 1 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    (it.name == "$baseName-impl" || it.name.startsWith("$baseName-"))
            }
        } else {
            fontStyleClass.getMethod(baseName)
        }

    private val getWeight: Method by lazy { accessor("getWeight") }
    private val getSlant: Method by lazy { accessor("getSlant") }

    private val typefaceGetFontStyle: Method by lazy { mangledOrPlain(Typeface::class.java, "getFontStyle", 0) }
    private val collectionFindTypefaces: Method by lazy { mangledOrPlain(FontCollection::class.java, "findTypefaces", 2) }
    private val mgrMatchFamilyStyle: Method by lazy { mangledOrPlain(FontMgr::class.java, "matchFamilyStyle", 2) }

    /** The public instance method named [name] (pre-0.156) or `name-<hash>` (0.156+) with [paramCount] params. */
    private fun mangledOrPlain(owner: Class<*>, name: String, paramCount: Int): Method =
        owner.methods.first {
            !Modifier.isStatic(it.modifiers) && it.parameterCount == paramCount &&
                (it.name == name || it.name.startsWith("$name-"))
        }

    private fun Method.call(receiver: Any?, vararg args: Any?): Any? =
        try {
            invoke(receiver, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }

    /** The OpenType weight (100–900) and italic-ness of [typeface]. */
    fun weightAndItalic(typeface: Typeface): Pair<Int, Boolean> {
        val style = typefaceGetFontStyle.call(typeface)
        val weight = if (isValueClass) getWeight.call(null, style) else getWeight.call(style)
        val slant = if (isValueClass) getSlant.call(null, style) else getSlant.call(style)
        return (weight as Int) to (slant != FontSlant.UPRIGHT)
    }

    fun findTypefaces(collection: FontCollection, families: Array<String>, weight: Int, italic: Boolean): Array<Typeface?> {
        @Suppress("UNCHECKED_CAST")
        return collectionFindTypefaces.call(collection, families, styleArg(weight, italic)) as Array<Typeface?>
    }

    fun matchFamilyStyle(mgr: FontMgr, family: String, weight: Int, italic: Boolean): Typeface? =
        mgrMatchFamilyStyle.call(mgr, family, styleArg(weight, italic)) as Typeface?
}
