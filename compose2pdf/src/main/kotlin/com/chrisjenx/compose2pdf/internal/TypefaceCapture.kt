package com.chrisjenx.compose2pdf.internal

import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.paragraph.FontCollection
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * Bridges Compose's font stack to [FontResolver] so the PDF embeds the exact typefaces the
 * composition shaped the text with.
 *
 * Skia's SVG output carries only font *names*, which is lossy: fonts loaded from Compose
 * resources/files aren't installed on the system, and platform defaults (".SF NS", "Roboto")
 * resolve through Skia's font manager rather than the filesystem. PdfRenderer [attach]es the
 * composition's `LocalFontFamilyResolver` while rendering; FontResolver then [lookupCaptured]s
 * the typefaces Compose actually loaded (resource/file fonts live only in the resolver's
 * internal FontCache, registered under opaque cache keys rather than their family names) and
 * can [findTypeface] through the same Skia [FontCollection] the paragraph shaper used for
 * system lookups.
 *
 * Capture is scoped to the render in flight, not accumulated for the JVM lifetime: only the
 * current resolver is held strongly (so its FontCache survives from composition to the later
 * SVG→PDF conversion), while resolvers from earlier renders are held weakly so their font
 * data is released once their render completes — and so a typeface a *previous* render loaded
 * under a family name can never shadow a *different* font the current render loaded under the
 * same name.
 *
 * The resolver's loader/cache types are Compose-internal, so the walk is reflective and
 * strictly best-effort: any failure (including a future Compose version reshaping these
 * internals) just leaves FontResolver's other strategies to do their work. This mirrors the
 * reflective ComposeSceneRenderer seam — do not replace with compile-time references.
 */
internal object ComposeFontStack {

    private val logger = java.util.logging.Logger.getLogger(ComposeFontStack::class.java.name)

    private val lock = Any()
    // The resolver for the render currently in flight — held strongly so its FontCache
    // (with resource/file typefaces) stays alive between composition and PDF conversion.
    private var current: FontFamily.Resolver? = null
    // Resolvers from earlier renders, weakly held so their font caches can be collected.
    private val previous = ArrayDeque<WeakReference<FontFamily.Resolver>>()

    // Typefaces harvested from each resolver's FontCache, memoized so repeated cache-miss
    // lookups within one render don't re-run reflection. WeakHashMap keys → the list is
    // released together with its resolver.
    private val harvested = Collections.synchronizedMap(WeakHashMap<FontFamily.Resolver, List<Typeface>>())

    @Volatile private var warned = false

    fun attach(resolver: FontFamily.Resolver) {
        synchronized(lock) {
            if (current === resolver) return
            current?.let {
                previous.addFirst(WeakReference(it))
                while (previous.size > MAX_PREVIOUS) previous.removeLast()
            }
            current = resolver
        }
    }

    /** Current + still-live earlier resolvers, current first; prunes collected weak refs. */
    private fun liveResolvers(): List<FontFamily.Resolver> = synchronized(lock) {
        buildList {
            current?.let { add(it) }
            val it = previous.iterator()
            while (it.hasNext()) {
                val r = it.next().get()
                if (r == null) it.remove() else add(r)
            }
        }
    }

    /**
     * Best typeface Compose loaded for [family], preferring the current render's resolver,
     * then exact italic match, then nearest weight. Covers resource/file fonts that exist
     * only in the FontCache under opaque keys, so a plain family-name query can't find them.
     */
    fun lookupCaptured(family: String, weight: Int, italic: Boolean): Typeface? {
        val requested = family.trim().lowercase()
        for (resolver in liveResolvers()) {
            val matches = typefacesOf(resolver).filter { nameMatches(it, requested) }
            if (matches.isEmpty()) continue
            val candidates = matches
                .filter { (it.fontStyle.slant != FontSlant.UPRIGHT) == italic }
                .ifEmpty { matches }
            return candidates.minByOrNull { abs(it.fontStyle.weight - weight) }
        }
        return null
    }

    /**
     * Resolves [family] through the composition's Skia [FontCollection] — the same lookup
     * the paragraph shaper used. The result is verified to actually carry the requested
     * family name (fallback managers substitute a default for unknown families) unless a
     * generic family was requested, where the substitution IS the font Skia shaped with.
     */
    fun findTypeface(family: String, weight: Int, italic: Boolean, generic: Boolean): Typeface? {
        val requested = family.trim().lowercase()
        val slant = if (italic) FontSlant.ITALIC else FontSlant.UPRIGHT
        val style = FontStyle(weight, /* width = */ 5, slant)
        for (resolver in liveResolvers()) {
            try {
                val collection = fontCollectionOf(resolver) ?: continue
                val typeface = collection.findTypefaces(arrayOf(family), style).firstOrNull() ?: continue
                if (generic || nameMatches(typeface, requested)) return typeface
            } catch (t: Throwable) {
                warnOnce(t)
            }
        }
        return null
    }

    /** Typefaces in [resolver]'s FontCache, harvested reflectively once and memoized. */
    private fun typefacesOf(resolver: FontFamily.Resolver): List<Typeface> =
        harvested.getOrPut(resolver) {
            try {
                val fontCache = fontCacheOf(resolver) ?: return@getOrPut emptyList()
                val typefacesCache = fontCache.javaClass.getDeclaredField("typefacesCache")
                    .apply { isAccessible = true }.get(fontCache) ?: return@getOrPut emptyList()
                val map = typefacesCache.javaClass.methods
                    .firstOrNull { it.name == "getMap\$ui_text" }?.invoke(typefacesCache) as? Map<*, *>
                    ?: return@getOrPut emptyList()
                map.values.filterIsInstance<Typeface>().toList()
            } catch (t: Throwable) {
                warnOnce(t)
                emptyList()
            }
        }

    private fun fontCollectionOf(resolver: FontFamily.Resolver): FontCollection? {
        val fontCache = fontCacheOf(resolver) ?: return null
        return fontCache.javaClass.methods
            .firstOrNull { it.name == "getFonts\$ui_text" }?.invoke(fontCache) as? FontCollection
    }

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
        synchronized(lock) {
            current = null
            previous.clear()
        }
        harvested.clear()
    }

    private const val MAX_PREVIOUS = 3
}
