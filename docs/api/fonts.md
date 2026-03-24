---
title: Fonts
parent: API Reference
nav_order: 7
---

# Fonts

compose2pdf ships with bundled Inter fonts and supports system font resolution.

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
2. **System fonts** -- Searched by font-family name in platform-specific directories:
   - macOS: `/Library/Fonts/`, `~/Library/Fonts/`, `/System/Library/Fonts/`
   - Linux: `/usr/share/fonts/`, `~/.local/share/fonts/`
   - Windows: `C:\Windows\Fonts\`
3. **PDF standard 14** -- Helvetica, Times-Roman, Courier and their variants (always available, never embedded)

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

Font files should be in `src/main/resources/`.

---

{: .warning }
**Variable fonts are not supported.** The library's `FontResolver` detects fonts with the `fvar` OpenType table and skips them automatically. PDFBox cannot render variable fonts at specific axis values. Use static `.ttf` or `.otf` files only.

---

## See also

- [Usage: Text and Fonts]({{ site.baseurl }}/usage/text-and-fonts) -- Text styling and font configuration
- [renderToPdf]({{ site.baseurl }}/api/render-to-pdf) -- The `defaultFontFamily` parameter
