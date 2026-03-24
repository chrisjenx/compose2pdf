---
title: Links
parent: Usage Guide
nav_order: 8
---

# PDF Link Annotations

Add clickable URLs to your PDFs with the `PdfLink` composable.

---

![Links example]({{ site.baseurl }}/assets/images/07-links.png){: .rounded .shadow .mb-4 }
*PDF link annotations example — [download PDF]({{ site.baseurl }}/assets/pdfs/07-links.pdf)*

---

## Basic text link

```kotlin
PdfLink(href = "https://example.com") {
    Text(
        "Visit Example.com",
        color = Color.Blue,
        textDecoration = TextDecoration.Underline,
    )
}
```

When the PDF is opened, clicking the text area opens the URL in a browser.

---

## Button-style link

Wrap any content -- not just text:

```kotlin
PdfLink(href = "https://example.com/get-started") {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1565C0))
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text("Get Started", color = Color.White, fontWeight = FontWeight.Bold)
    }
}
```

---

## Inline links

Mix links with surrounding text using `Row`:

```kotlin
Row {
    Text("Read our ")
    PdfLink(href = "https://example.com/terms") {
        Text("Terms of Service", color = Color.Blue, textDecoration = TextDecoration.Underline)
    }
    Text(" and ")
    PdfLink(href = "https://example.com/privacy") {
        Text("Privacy Policy", color = Color.Blue, textDecoration = TextDecoration.Underline)
    }
    Text(".")
}
```

---

## Large clickable area

The entire bounds of the content become the clickable region:

```kotlin
PdfLink(href = "https://example.com") {
    Box(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color(0xFFF0F0F0), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Click anywhere in this box", fontSize = 18.sp)
    }
}
```

---

## Email links

```kotlin
PdfLink(href = "mailto:contact@example.com") {
    Text("contact@example.com", color = Color.Blue)
}
```

---

## Navigation links in headers

```kotlin
Row(
    Modifier
        .fillMaxWidth()
        .background(Color(0xFF1565C0))
        .padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    PdfLink(href = "https://example.com/docs") {
        Text("Docs", color = Color.White)
    }
    PdfLink(href = "https://example.com/pricing") {
        Text("Pricing", color = Color.White)
    }
    PdfLink(href = "https://example.com/support") {
        Text("Support", color = Color.White)
    }
}
```

---

## How it works

1. During `renderToPdf`, a `PdfLinkCollector` is provided via `CompositionLocal`
2. `PdfLink` uses `onGloballyPositioned` to measure the bounds of its content
3. After rendering, the collected bounds are converted to PDF coordinates and added as `PDAnnotationLink` objects with invisible borders
4. PDF viewers render these as clickable regions

**Outside of `renderToPdf`**, `PdfLink` is a no-op wrapper -- safe to use in shared composables that render both on screen and to PDF.

---

{: .note }
The `href` parameter must not be blank. An `IllegalArgumentException` is thrown if you pass an empty or blank string.

---

## See also

- [API Reference: PdfLink]({{ site.baseurl }}/api/pdf-link) -- Full composable documentation
- [Example: Invoice]({{ site.baseurl }}/examples/invoice) -- Links in a real-world document
