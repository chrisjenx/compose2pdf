package com.chrisjenx.compose2pdf.internal

import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.paragraph.FontCollection
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

    /** A Skia typeface's OpenType weight (100–900) and whether it is slanted. */
    data class Style(val weight: Int, val italic: Boolean)

    private const val NORMAL_WIDTH = 5

    private val fontStyleClass: Class<*> by lazy { Class.forName("org.jetbrains.skia.FontStyle") }

    /** true when FontStyle is the >= 0.156 inline value class (members exchange a raw `int`). */
    private val isValueClass: Boolean by lazy {
        fontStyleClass.methods.any { it.name == "box-impl" && Modifier.isStatic(it.modifiers) }
    }

    // The version split is resolved once, here: each function below already speaks the right shape —
    // a boxed FontStyle with instance getters (<= 0.15x) or a raw `int` with static accessors (0.156+).

    /** (weight, slant) → the argument FontStyle-taking Skia methods expect (boxed FontStyle or raw int). */
    private val newStyle: (Int, FontSlant) -> Any by lazy { styleFactory() }

    private fun styleFactory(): (Int, FontSlant) -> Any {
        if (isValueClass) {
            val ctorImpl = fontStyleClass.methods.first {
                it.name == "constructor-impl" && Modifier.isStatic(it.modifiers) && it.parameterCount == 3
            }
            return { weight, slant -> ctorImpl.invokeUnwrapped(null, weight, NORMAL_WIDTH, slant)!! }
        }
        val ctor = fontStyleClass.getConstructor(
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, FontSlant::class.java,
        )
        return { weight, slant -> ctor.newInstanceUnwrapped(weight, NORMAL_WIDTH, slant) }
    }

    private val weightOf: (Any?) -> Any? by lazy { accessor("getWeight") }
    private val slantOf: (Any?) -> Any? by lazy { accessor("getSlant") }

    /** Instance getter on the boxed class (`getWeight`) or static `getWeight-<hash|impl>(int)`. */
    private fun accessor(baseName: String): (Any?) -> Any? {
        if (!isValueClass) {
            val getter = fontStyleClass.getMethod(baseName)
            return { style -> getter.invokeUnwrapped(style) }
        }
        val impl = fontStyleClass.methods.first {
            Modifier.isStatic(it.modifiers) && it.parameterCount == 1 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType && it.name.startsWith("$baseName-")
        }
        return { style -> impl.invokeUnwrapped(null, style) }
    }

    private val typefaceGetFontStyle: Method by lazy { mangledOrPlain(Typeface::class.java, "getFontStyle", 0) }
    private val collectionFindTypefaces: Method by lazy { mangledOrPlain(FontCollection::class.java, "findTypefaces", 2) }
    private val mgrMatchFamilyStyle: Method by lazy { mangledOrPlain(FontMgr::class.java, "matchFamilyStyle", 2) }

    /** The public instance method named [name] (pre-0.156) or `name-<hash>` (0.156+) with [paramCount] params. */
    private fun mangledOrPlain(owner: Class<*>, name: String, paramCount: Int): Method =
        owner.methods.first {
            !Modifier.isStatic(it.modifiers) && it.parameterCount == paramCount &&
                (it.name == name || it.name.startsWith("$name-"))
        }

    private fun styleArg(weight: Int, italic: Boolean): Any =
        newStyle(weight, if (italic) FontSlant.ITALIC else FontSlant.UPRIGHT)

    fun styleOf(typeface: Typeface): Style {
        val style = typefaceGetFontStyle.invokeUnwrapped(typeface)
        return Style(weightOf(style) as Int, slantOf(style) != FontSlant.UPRIGHT)
    }

    fun findTypefaces(collection: FontCollection, families: Array<String>, weight: Int, italic: Boolean): Array<Typeface?> {
        @Suppress("UNCHECKED_CAST")
        return collectionFindTypefaces.invokeUnwrapped(collection, families, styleArg(weight, italic)) as Array<Typeface?>
    }

    fun matchFamilyStyle(mgr: FontMgr, family: String, weight: Int, italic: Boolean): Typeface? =
        mgrMatchFamilyStyle.invokeUnwrapped(mgr, family, styleArg(weight, italic)) as Typeface?
}
