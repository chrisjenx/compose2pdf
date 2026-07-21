package com.chrisjenx.compose2pdf.internal

import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.paragraph.FontCollection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-wide registry of the exact Skia [Typeface]s Compose resolved while laying text out.
 *
 * Skia's SVG output only carries font *names*, which is lossy: fonts loaded from Compose
 * resources/files aren't installed on the system, and platform defaults (".SF NS", "Roboto")
 * resolve through Skia's font manager rather than the filesystem. Recording the shaping
 * typefaces lets [FontResolver] embed the very fonts that produced the glyph positions, so
 * PDF output matches the layout for ANY font — no filename heuristics involved.
 *
 * Process-wide by design (like FontResolver's file path cache): a family name maps to the
 * same typeface for every render in this JVM.
 */
internal object TypefaceCaptureRegistry {

    internal data class Entry(val weight: Int, val italic: Boolean, val typeface: Typeface)

    private val byFamily = ConcurrentHashMap<String, CopyOnWriteArrayList<Entry>>()

    fun record(typeface: Typeface) {
        val style = typeface.fontStyle
        val entry = Entry(
            weight = style.weight,
            italic = style.slant != FontSlant.UPRIGHT,
            typeface = typeface,
        )
        val names = buildSet {
            add(typeface.familyName)
            typeface.familyNames.forEach { add(it.name) }
        }
        for (name in names) {
            val key = name.trim().lowercase()
            if (key.isEmpty()) continue
            val entries = byFamily.getOrPut(key) { CopyOnWriteArrayList() }
            if (entries.none { it.typeface.uniqueId == typeface.uniqueId }) entries.add(entry)
        }
    }

    /** Best captured typeface for [family], preferring exact italic match then nearest weight. */
    fun lookup(family: String, weight: Int, italic: Boolean): Typeface? {
        val entries = byFamily[family.trim().lowercase()] ?: return null
        val candidates = entries.filter { it.italic == italic }.ifEmpty { entries }
        return candidates.minByOrNull { kotlin.math.abs(it.weight - weight) }?.typeface
    }

    /** Test hook — capture is process-global, so tests may need a clean slate. */
    fun clear() = byFamily.clear()
}

/**
 * Bridges Compose's font stack to [FontResolver].
 *
 * PdfRenderer [attach]es the composition's `LocalFontFamilyResolver` while rendering; on a
 * font-cache miss FontResolver then [harvest]s every typeface Compose loaded (resource/file
 * fonts live only in the resolver's internal FontCache — they're registered in Skia's
 * font collection under opaque cache keys, not their family names) and can [findTypeface]
 * through the same Skia [FontCollection] the paragraph shaper used for system lookups.
 *
 * The resolver's loader/cache types are Compose-internal, so the walk is reflective and
 * strictly best-effort: any failure (including a future Compose version reshaping these
 * internals) just leaves FontResolver's other strategies to do their work. This mirrors the
 * reflective ComposeSceneRenderer seam — do not replace with compile-time references.
 */
internal object ComposeFontStack {

    private val logger = java.util.logging.Logger.getLogger(ComposeFontStack::class.java.name)

    // Most recent resolvers (scenes may create a fresh resolver per render); newest first.
    private val resolvers = ConcurrentLinkedDeque<FontFamily.Resolver>()
    @Volatile private var warned = false

    fun attach(resolver: FontFamily.Resolver) {
        if (resolvers.peekFirst() === resolver) return
        resolvers.remove(resolver)
        resolvers.addFirst(resolver)
        while (resolvers.size > 4) resolvers.pollLast()
    }

    /** Records every typeface Compose has loaded so far into [TypefaceCaptureRegistry]. */
    fun harvest() {
        for (resolver in resolvers) {
            try {
                val fontCache = fontCacheOf(resolver) ?: continue
                val typefacesCache = fontCache.javaClass.getDeclaredField("typefacesCache")
                    .apply { isAccessible = true }.get(fontCache) ?: continue
                val map = typefacesCache.javaClass.methods
                    .firstOrNull { it.name == "getMap\$ui_text" }?.invoke(typefacesCache) as? Map<*, *>
                    ?: continue
                map.values.filterIsInstance<Typeface>().forEach(TypefaceCaptureRegistry::record)
            } catch (t: Throwable) {
                warnOnce(t)
            }
        }
    }

    /**
     * Resolves [family] through the composition's Skia [FontCollection] — the same lookup
     * the paragraph shaper used. The result is verified to actually carry the requested
     * family name (fallback managers substitute a default for unknown families) unless a
     * generic family was requested, where the substitution IS the shaping font.
     */
    fun findTypeface(family: String, weight: Int, italic: Boolean, generic: Boolean): Typeface? {
        val slant = if (italic) FontSlant.ITALIC else FontSlant.UPRIGHT
        val style = FontStyle(weight, /* width = */ 5, slant)
        for (resolver in resolvers) {
            try {
                val fontCache = fontCacheOf(resolver) ?: continue
                val collection = fontCache.javaClass.methods
                    .firstOrNull { it.name == "getFonts\$ui_text" }?.invoke(fontCache) as? FontCollection
                    ?: continue
                val typeface = collection.findTypefaces(arrayOf(family), style).firstOrNull() ?: continue
                if (generic || nameMatches(typeface, family)) return typeface
            } catch (t: Throwable) {
                warnOnce(t)
            }
        }
        return null
    }

    private fun fontCacheOf(resolver: FontFamily.Resolver): Any? {
        val loader = resolver.javaClass.methods
            .firstOrNull { it.name == "getPlatformFontLoader\$ui_text" }?.invoke(resolver)
            ?: return null
        val getFontCache = loader.javaClass.getDeclaredMethod("getFontCache")
            .apply { isAccessible = true }
        return getFontCache.invoke(loader)
    }

    private fun nameMatches(typeface: Typeface, family: String): Boolean {
        val requested = family.trim().lowercase()
        return typeface.familyName.trim().lowercase() == requested ||
            typeface.familyNames.any { it.name.trim().lowercase() == requested }
    }

    private fun warnOnce(t: Throwable) {
        if (!warned) {
            warned = true
            logger.fine("Compose font stack introspection unavailable (${t::class.simpleName}: ${t.message}); font embedding falls back to system lookups")
        }
    }

    /** Test hook. */
    fun clear() = resolvers.clear()
}
