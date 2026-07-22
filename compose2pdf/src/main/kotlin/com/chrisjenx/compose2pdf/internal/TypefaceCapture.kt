package com.chrisjenx.compose2pdf.internal

import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.paragraph.FontCollection
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * Bridges Compose's font stack to [FontResolver] so the PDF embeds the exact typefaces the
 * composition shaped the text with, and resolves system families the way Skia's shaper does.
 *
 * Skia's SVG output carries only font *names*, which is lossy: fonts loaded from Compose
 * resources/files aren't installed on the system, and platform defaults (".SF NS", "Roboto")
 * resolve through Skia's font manager rather than the filesystem. PdfRenderer [attach]es the
 * composition's `LocalFontFamilyResolver` while rendering; FontResolver then [lookupCaptured]s
 * the typefaces Compose actually loaded (resource/file fonts live only in the resolver's
 * internal FontCache, registered under opaque cache keys rather than their family names) and
 * can [findTypeface]/[matchSystemTypeface] a family through the same Skia machinery the
 * paragraph shaper used.
 *
 * Capture is scoped to the render in flight: only the current resolver is retained (renders
 * are sequential — measurement, body, and slot passes all run before their SVG is converted),
 * so a typeface a *previous* render loaded under a family name can never shadow a *different*
 * font the current render loaded under the same name, and a completed render's FontCache is
 * released when the next render attaches.
 *
 * This is a *soft-fail, name-coupled* reflective seam — a cousin of [ComposeSceneRenderer]'s,
 * but deliberately different in its failure mode: it hard-codes Compose-internal member names
 * (`getPlatformFontLoader$ui_text`, `getFontCache`, `typefacesCache`, `getMap$ui_text`,
 * `getFonts$ui_text`) and, on any reflection failure, degrades to FontResolver's other
 * strategies (system lookup, standard-14) rather than throwing. Scene rendering has no such
 * fallback, so it fails fast; font embedding does, so it fails soft. Do not replace with
 * compile-time references.
 */
internal object ComposeFontStack {

    private val logger = java.util.logging.Logger.getLogger(ComposeFontStack::class.java.name)

    // The resolver for the render currently in flight, held strongly so its FontCache
    // (with resource/file typefaces) survives from composition to the later SVG→PDF
    // conversion. Replaced on the next render, releasing the prior render's font data.
    @Volatile private var current: FontFamily.Resolver? = null

    // The typefaces and FontCollection extracted from a resolver's FontCache, reflected once
    // and memoized so repeated cache-miss lookups within a render don't re-run reflection.
    // WeakHashMap keys → the entry is released together with its resolver.
    private class ResolverFonts(val typefaces: List<Typeface>, val collection: FontCollection?)
    private val fontsByResolver = Collections.synchronizedMap(WeakHashMap<FontFamily.Resolver, ResolverFonts>())

    @Volatile private var warned = false

    fun attach(resolver: FontFamily.Resolver) {
        current = resolver
    }

    /**
     * Best typeface the current render loaded for [family], preferring exact italic match
     * then nearest weight. Covers resource/file fonts that exist only in the FontCache under
     * opaque keys, so a plain family-name query can't find them.
     */
    fun lookupCaptured(family: String, weight: Int, italic: Boolean): Typeface? {
        val resolver = current ?: return null
        val requested = family.trim().lowercase()
        val matches = fontsOf(resolver).typefaces.filter { nameMatches(it, requested) }
        if (matches.isEmpty()) return null
        val candidates = matches
            .filter { (it.fontStyle.slant != FontSlant.UPRIGHT) == italic }
            .ifEmpty { matches }
        return candidates.minByOrNull { abs(it.fontStyle.weight - weight) }
    }

    /**
     * Resolves [family] through the composition's Skia [FontCollection] — the same lookup
     * the paragraph shaper used for system fonts and glyph-fallback runs.
     */
    fun findTypeface(family: String, weight: Int, italic: Boolean, generic: Boolean): Typeface? {
        val resolver = current ?: return null
        val collection = fontsOf(resolver).collection ?: return null
        return try {
            collection.findTypefaces(arrayOf(family), fontStyleOf(weight, italic))
                .firstOrNull()
                ?.takeIf { generic || nameMatches(it, family.trim().lowercase()) }
        } catch (t: Throwable) {
            warnOnce(t)
            null
        }
    }

    /**
     * Resolves [family] through Skia's default font manager — the matcher Skia uses for a run
     * this process never composed (e.g. direct SVG conversion). The result is verified to
     * actually carry the requested family name ([generic] families exempt), so an unknown
     * family can't silently embed whatever the manager substitutes.
     */
    fun matchSystemTypeface(family: String, weight: Int, italic: Boolean, generic: Boolean): Typeface? {
        return try {
            FontMgr.default.matchFamilyStyle(family, fontStyleOf(weight, italic))
                ?.takeIf { generic || nameMatches(it, family.trim().lowercase()) }
        } catch (t: Throwable) {
            warnOnce(t)
            null
        }
    }

    private fun fontStyleOf(weight: Int, italic: Boolean): FontStyle =
        FontStyle(weight, /* width = */ 5, if (italic) FontSlant.ITALIC else FontSlant.UPRIGHT)

    /** The typefaces and FontCollection in [resolver]'s FontCache, reflected once and memoized. */
    private fun fontsOf(resolver: FontFamily.Resolver): ResolverFonts =
        fontsByResolver.getOrPut(resolver) {
            try {
                val fontCache = fontCacheOf(resolver) ?: return@getOrPut ResolverFonts(emptyList(), null)
                ResolverFonts(typefacesIn(fontCache), collectionIn(fontCache))
            } catch (t: Throwable) {
                warnOnce(t)
                ResolverFonts(emptyList(), null)
            }
        }

    private fun typefacesIn(fontCache: Any): List<Typeface> {
        val typefacesCache = fontCache.javaClass.getDeclaredField("typefacesCache")
            .apply { isAccessible = true }.get(fontCache) ?: return emptyList()
        val map = typefacesCache.javaClass.methods
            .firstOrNull { it.name == "getMap\$ui_text" }?.invoke(typefacesCache) as? Map<*, *>
            ?: return emptyList()
        return map.values.filterIsInstance<Typeface>().toList()
    }

    private fun collectionIn(fontCache: Any): FontCollection? =
        fontCache.javaClass.methods
            .firstOrNull { it.name == "getFonts\$ui_text" }?.invoke(fontCache) as? FontCollection

    private fun fontCacheOf(resolver: FontFamily.Resolver): Any? {
        val loader = resolver.javaClass.methods
            .firstOrNull { it.name == "getPlatformFontLoader\$ui_text" }?.invoke(resolver)
            ?: return null
        return loader.javaClass.getDeclaredMethod("getFontCache")
            .apply { isAccessible = true }.invoke(loader)
    }

    private fun nameMatches(typeface: Typeface, requestedLower: String): Boolean =
        typeface.familyName.trim().lowercase() == requestedLower ||
            typeface.familyNames.any { it.name.trim().lowercase() == requestedLower }

    private fun warnOnce(t: Throwable) {
        if (!warned) {
            warned = true
            logger.fine("Compose font stack introspection unavailable (${t::class.simpleName}: ${t.message}); font embedding falls back to system lookups")
        }
    }

    /** Test hook — capture is process-scoped, so tests may need a clean slate. */
    fun clear() {
        current = null
        fontsByResolver.clear()
    }
}
