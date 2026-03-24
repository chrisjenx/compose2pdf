---
title: Exceptions
parent: API Reference
nav_order: 8
---

# Compose2PdfException

Exception thrown when PDF rendering fails.

```kotlin
class Compose2PdfException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
```

---

## When thrown

`renderToPdf` catches unexpected exceptions during rendering and wraps them in `Compose2PdfException` with a descriptive message and the original exception as the `cause`.

```kotlin
try {
    val pdf = renderToPdf { content() }
} catch (e: Compose2PdfException) {
    println("Rendering failed: ${e.message}")
    println("Cause: ${e.cause}")
}
```

---

## Exception behavior

| Exception type | Behavior |
|:---------------|:---------|
| `Compose2PdfException` | Re-thrown as-is (not double-wrapped) |
| `IllegalArgumentException` | Re-thrown as-is (precondition failures) |
| All other exceptions | Wrapped in `Compose2PdfException` |

`IllegalArgumentException` is intentionally **not wrapped** -- these indicate programming errors like invalid page counts or blank link URLs, and should be caught separately if needed.

---

## See also

- [renderToPdf]({{ site.baseurl }}/api/render-to-pdf) -- Functions that throw this exception
- [Troubleshooting]({{ site.baseurl }}/guides/troubleshooting) -- Common error scenarios
