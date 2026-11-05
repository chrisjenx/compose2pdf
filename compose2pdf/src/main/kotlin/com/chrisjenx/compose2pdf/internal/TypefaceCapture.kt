package com.chrisjenx.compose2pdf.internal

import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.skia.FontMgr
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
 * (`getPlatformFontLoader$<module>`, `getFontCache`, `typefacesCache`, `getMap$<module>`,
 * `getFonts$<module>`; the module suffix changed in CMP 1.13 so it is matched loosely, and skiko's
 * `FontStyle` access goes through [SkiaFontStyleCompat]) and, on any reflection failure, degrades to FontResolver's other
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

    // The typefaces and FontCollection extracted from a resolver's FontCache, reflected once per
    // render and memoized so repeated cache-miss lookups within it don't re-run reflection.
    // WeakHashMap keys → the entry is released together with its resolver. Invalidated on every
    // [attach]: from CMP 1.13 all renders share ONE process-wide resolver (and FontCache), so a
    // memo taken by an earlier render would hide fonts this render loaded.
    private class ResolverFonts(val typefaces: List<Typeface>, val collection: FontCollection?)
    private val fontsByResolver = Collections.synchronizedMap(WeakHashMap<FontFamily.Resolver, ResolverFonts>())

    @Volatile private var warned = false

    fun attach(resolver: FontFamily.Resolver) {
        current = resolver
        fontsByResolver.remove(resolver)
    }

    /**
     * Best typeface the current render loaded for [family], preferring exact italic match
     * then nearest weight, then the most recently used (with a shared FontCache — CMP 1.13+ —
     * the cache can also hold same-named fonts from earlier renders; the render in flight
     * touched its own fonts last). Covers resource/file fonts that exist only in the FontCache under
     * opaque keys, so a plain family-name query can't find them.
     */
    fun lookupCaptured(family: String, weight: Int, italic: Boolean): Typeface? {
        val resolver = current ?: return null
        val requested = family.trim().lowercase()
        val matches = try {
            fontsOf(resolver).typefaces
                .filter { nameMatches(it, requested) }
                .map { it to SkiaFontStyleCompat.weightAndItalic(it) }
        } catch (t: Throwable) {
            warnOnce(t)
            return null
        }
        if (matches.isEmpty()) return null
        val candidates = matches
            .filter { (_, style) -> style.second == italic }
            .ifEmpty { matches }
        return candidates.minByOrNull { (_, style) -> abs(style.first - weight) }?.first
    }

    /**
     * Resolves [family] through the composition's Skia [FontCollection] — the same lookup
     * the paragraph shaper used for system fonts and glyph-fallback runs.
     */
    fun findTypeface(family: String, weight: Int, italic: Boolean, generic: Boolean): Typeface? {
        val resolver = current ?: return null
        val collection = fontsOf(resolver).collection ?: return null
        return try {
            SkiaFontStyleCompat.findTypefaces(collection, arrayOf(family), weight, italic)
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
            SkiaFontStyleCompat.matchFamilyStyle(FontMgr.default, family, weight, italic)
                ?.takeIf { generic || nameMatches(it, family.trim().lowercase()) }
        } catch (t: Throwable) {
            warnOnce(t)
            null
        }
    }

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
            .firstOrNull { it.isInternalGetter("getMap") }?.invoke(typefacesCache) as? Map<*, *>
            ?: return emptyList()
        // Most recently accessed first, so same-name/same-style ties resolve to the current
        // render's font (stable minBy in lookupCaptured keeps the first). Best-effort: without
        // the access-time map the cache's own order is kept.
        val accessTime = typefacesCache.javaClass.methods
            .firstOrNull { it.isInternalGetter("getAccessTime") }?.invoke(typefacesCache) as? Map<*, *>
        val entries = if (accessTime == null) {
            map.entries.toList()
        } else {
            map.entries.sortedByDescending { (accessTime[it.key] as? Long) ?: Long.MIN_VALUE }
        }
        return entries.mapNotNull { it.value as? Typeface }
    }

    private fun collectionIn(fontCache: Any): FontCollection? =
        fontCache.javaClass.methods
            .firstOrNull { it.isInternalGetter("getFonts") }?.invoke(fontCache) as? FontCollection

    private fun fontCacheOf(resolver: FontFamily.Resolver): Any? {
        var loader: Any = resolver.javaClass.methods
            .firstOrNull { it.isInternalGetter("getPlatformFontLoader") }?.invoke(resolver)
            ?: return null
        // Up to CMP 1.12 the platform loader IS the SkiaFontLoader owning the FontCache. From 1.13
        // it is a PlatformFontLoaderAdapter wrapping it as `backend`; unwrap until a loader
        // declares getFontCache (bounded, so an unexpected shape can't loop).
        repeat(MAX_LOADER_UNWRAP) {
            loader.javaClass.declaredMethods
                .firstOrNull { it.name == "getFontCache" && it.parameterCount == 0 }
                ?.let { return it.apply { isAccessible = true }.invoke(loader) }
            loader = loader.javaClass.methods
                .firstOrNull { it.name == "getBackend" && it.parameterCount == 0 }
                ?.invoke(loader) ?: return null
        }
        return null
    }

    private const val MAX_LOADER_UNWRAP = 3

    /**
     * A no-arg getter for a Kotlin `internal` member: JVM name `<base>$<module>`. The module suffix
     * is matched loosely because it moved between versions (`$ui_text` up to CMP 1.12;
     * `$org_jetbrains_compose_ui_ui_text` / `$org_jetbrains_compose_ui_ui_skiko` from 1.13).
     */
    private fun java.lang.reflect.Method.isInternalGetter(base: String): Boolean =
        parameterCount == 0 && name.startsWith("$base$")

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
