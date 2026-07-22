---
title: Fonts
parent: API Reference
nav_order: 7
---

# Fonts

compose2pdf embeds whatever fonts Compose lays the text out with — bundled Inter,
custom `Font(resource)`/`Font(file)` families, or platform system fonts — automatically.

---

## InterFontFamily

```kotlin
val InterFontFamily: FontFamily
```

Bundled [Inter](https://rsms.me/inter/) font family using static (non-variable) font files.

### Included fonts

| Weight | Style | File |
|:-------|:------|:-----|
| `FontWeight.Normal` | `FontStyle.Normal` | Inter-Regular.ttf |
| `FontWeight.Bold` | `FontStyle.Normal` | Inter-Bold.ttf |
| `FontWeight.Normal` | `FontStyle.Italic` | Inter-Italic.ttf |
| `FontWeight.Bold` | `FontStyle.Italic` | Inter-BoldItalic.ttf |

This is the default `defaultFontFamily` for `renderToPdf`. When used, both Compose layout and PDFBox font embedding use the same font files, eliminating any rendering mismatch.

---

## Font resolution chain

When the SVG-to-PDF converter encounters text, it resolves fonts in this order:

1. **Bundled fonts** -- Inter Regular, Bold, Italic, BoldItalic (when using `InterFontFamily`)
2. **Captured typefaces** -- the exact fonts Compose loaded while laying out this content. Fonts declared via `Font(resource)`/`Font(file)` (including through theme typography) are captured here and embedded byte-exactly; they never need to be installed on the machine.
3. **The composition's Skia font collection** -- the same lookup Compose's text shaper used, covering system fonts and glyph-fallback runs.
4. **Skia's system font manager** -- by family name (results are verified to carry the requested family, so an unknown name can't silently embed a substitute).
5. **Platform font directories** -- filename search:
   - macOS: `/Library/Fonts/`, `~/Library/Fonts/`, `/System/Library/Fonts/`
   - Linux: `/usr/share/fonts/`, `~/.local/share/fonts/`
   - Windows: `C:\Windows\Fonts\`
6. **PDF standard 14** -- Helvetica, Times-Roman, Courier and their variants (last resort; never embedded). Substituted glyphs wider than the space the layout measured are horizontally compressed so they cannot overlap the next character, and a warning naming the unresolved family is logged.

---

## Using system fonts

Pass `null` to skip the bundled fonts:

```kotlin
val pdf = renderToPdf(defaultFontFamily = null) {
    Text("System font")
}
```

---

## Custom fonts

Supply your own `FontFamily`:

```kotlin
val myFont = FontFamily(
    Font(resource = "fonts/MyFont-Regular.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/MyFont-Bold.ttf", weight = FontWeight.Bold),
)

val pdf = renderToPdf(defaultFontFamily = myFont) {
    Text("Custom font!")
}
```

Font files should be in `src/main/resources/`. The font does not need to be installed
on the machine — the loaded data itself is subset and embedded, which makes
resource-loaded fonts the most reliable choice for headless server rendering.

---

{: .warning }
**Variable fonts embed at their default instance only.** PDFBox cannot instantiate
variable-font axes. A variable font's default (regular) instance embeds correctly,
but instances styled away from the default on `wght`/`wdth`/`slnt`/`ital` — e.g.
bold of a variable-only family — fall back to a compressed standard font. The
filesystem search additionally skips files with an `fvar` table. Prefer static
`.ttf`/`.otf` files per weight.

---

## See also

- [Usage: Text and Fonts]({{ site.baseurl }}/usage/text-and-fonts) -- Text styling and font configuration
- [renderToPdf]({{ site.baseurl }}/api/render-to-pdf) -- The `defaultFontFamily` parameter
