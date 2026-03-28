---
title: PdfLink
parent: API Reference
nav_order: 4
---

# PdfLink

Composable that wraps content in a clickable URL annotation for PDF output.

```kotlin
@Composable
fun PdfLink(
    href: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
```

---

## Parameters

| Parameter | Type | Default | Description |
|:----------|:-----|:--------|:------------|
| `href` | `String` | -- | Target URL for the link (must not be blank) |
| `modifier` | `Modifier` | `Modifier` | Modifier applied to the link wrapper |
| `content` | `@Composable () -> Unit` | -- | Content that forms the clickable region |

---

## Behavior

**Inside `renderToPdf`:** The bounds of `content` are measured via `onGloballyPositioned` and recorded. After rendering, a `PDAnnotationLink` is added to the PDF page at that position with an invisible border.

**Outside `renderToPdf`:** This is a **no-op wrapper** -- the content renders normally with no link behavior. This makes it safe to use in composables shared between screen UI and PDF output.

---

## Validation

Throws `IllegalArgumentException` if `href` is blank or empty.

---

## Examples

```kotlin
// Text link
PdfLink(href = "https://example.com") {
    Text("Click me", color = Color.Blue, textDecoration = TextDecoration.Underline)
}

// Button-style link
PdfLink(href = "https://example.com") {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Blue)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text("Get Started", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

// Email link
PdfLink(href = "mailto:hello@example.com") {
    Text("hello@example.com", color = Color.Blue)
}
```

---

## Platform support

| Platform | Status | Notes |
|:---------|:-------|:------|
| **JVM** | Full support | PDF link annotations added via PDFBox `PDAnnotationLink` |
| **Android** | Not supported | `android.graphics.pdf.PdfDocument` has no annotation API. `PdfLink` is a no-op (content renders, but no clickable link in the PDF) |
| **iOS** | Not supported | Core Graphics PDF context does not write link annotations. `PdfLink` is a no-op |

The `PdfLink` composable is defined in `commonMain` and is safe to use in shared code — it simply has no effect on platforms that don't support PDF annotations.

---

## See also

- [Usage: Links]({{ site.baseurl }}/usage/links) -- All link patterns with examples
