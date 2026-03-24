---
title: Text and Fonts
parent: Usage Guide
nav_order: 4
---

# Text and Fonts

compose2pdf supports rich text styling through standard Compose text APIs, with automatic font embedding for consistent rendering.

---

## Basic text styling

```kotlin
renderToPdf {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Normal text")
        Text("Bold text", fontWeight = FontWeight.Bold)
        Text("Italic text", fontStyle = FontStyle.Italic)
        Text("Bold italic", fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
        Text("Large heading", fontSize = 36.sp)
        Text("Small caption", fontSize = 10.sp)
        Text("Custom color", color = Color(0xFF1976D2))
    }
}
```

---

## Text decorations

```kotlin
Text("Underlined", textDecoration = TextDecoration.Underline)
Text("Strikethrough", textDecoration = TextDecoration.LineThrough)
Text(
    "Both decorations",
    textDecoration = TextDecoration.Underline + TextDecoration.LineThrough,
)
```

---

## Text alignment

```kotlin
Column(Modifier.fillMaxWidth()) {
    Text("Left aligned (default)")
    Text("Center aligned", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text("Right aligned", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
}
```

---

## Spacing

```kotlin
Text("Wide letter spacing", letterSpacing = 4.sp)
Text("Tall line height", lineHeight = 28.sp)
```

---

## Overflow and truncation

```kotlin
Text(
    "This very long text will be truncated with an ellipsis if it overflows...",
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)

Text(
    "This text is limited to two lines and will truncate after that...",
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
)
```

---

## Fonts

### Default: InterFontFamily

By default, `renderToPdf` uses `InterFontFamily` -- a bundled set of static Inter fonts:

| Weight | Style | File |
|:-------|:------|:-----|
| Normal | Normal | Inter-Regular.ttf |
| Bold | Normal | Inter-Bold.ttf |
| Normal | Italic | Inter-Italic.ttf |
| Bold | Italic | Inter-BoldItalic.ttf |

These are embedded and subsetted automatically, so PDFs look the same on every system.

### Using system fonts

Pass `null` to use system fonts instead:

```kotlin
val pdf = renderToPdf(defaultFontFamily = null) {
    Text("Using system fonts")
}
```

The library resolves system fonts from platform-specific directories (macOS, Linux, Windows). System fonts are embedded in the PDF via PDFBox's automatic subsetting.

### Using a custom font

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

Place font files in your `src/main/resources/fonts/` directory.

### Font resolution chain

When the converter encounters text, it resolves fonts in this order:

1. **Bundled fonts** -- Inter Regular/Bold/Italic/BoldItalic (when using `InterFontFamily`)
2. **System fonts** -- searched by font-family name in platform font directories
3. **PDF standard 14** -- Helvetica, Times-Roman, Courier (fallback; always available, never embedded)

---

{: .warning }
**Variable fonts are not supported.** PDFBox cannot render variable fonts correctly -- they are automatically skipped during font resolution. Use static `.ttf` or `.otf` font files only.

---

## See also

- [API Reference: Fonts]({{ site.baseurl }}/api/fonts) -- InterFontFamily details
- [API Reference: renderToPdf]({{ site.baseurl }}/api/render-to-pdf) -- The `defaultFontFamily` parameter
