# Per-Page Header/Footer Slots — Design

**Date:** 2026-07-16
**Status:** Approved (pending implementation)
**Reviewed by:** adversarial design-review agent (verdict: GO-WITH-CHANGES; all changes folded in below)

## Problem

compose2pdf auto-paginates content, but there is no way to place a repeated header or footer band on each page (e.g. "Page 2 of 5", a company name, a document reference). Auto-pagination renders the body once as a single tall SVG and slices it per page, so headers/footers cannot simply be composed into the content — they must be rendered and stamped per page.

## Requirements

1. Optional `header` and `footer` composable slots on the auto-pagination `renderToPdf` overloads (both `ByteArray` and `OutputStream` variants). Manual `renderToPdf(pages)` overloads are unchanged (callers compose each page themselves there).
2. Slots receive page info (`pageIndex`, `pageCount`) so "Page X of Y" works.
3. Slot heights are **measured once** up front and subtracted from every page's content area — a uniform band on every page. No per-page height variation (a "different first page" mode is out of scope; big first-page mastheads stay in the body flow).
4. When both slots are `null` (the default), the render path is **exactly today's code path** — the fidelity suite must be unaffected.
5. Slots work in `VECTOR` and `RASTER` modes, in `AUTO` and `SINGLE_PAGE` pagination, and in the single-page fallback.
6. `PdfLink` works inside slots.

## Public API

```kotlin
/** Page information passed to header/footer slots. */
class PdfPageInfo(
    /** Zero-based index of the page being rendered. */
    val pageIndex: Int,
    /** Total number of emitted pages. */
    val pageCount: Int,
) {
    /** One-based page number, for display ("Page $pageNumber of $pageCount"). */
    val pageNumber: Int get() = pageIndex + 1
}
```

Deliberately a **plain class, not a `data class`** — `copy`/`componentN` would freeze the constructor shape and make adding a field later (e.g. `isLastPage`) a binary break.

New parameters on the two auto-pagination overloads:

```kotlin
fun renderToPdf(
    outputStream: OutputStream,
    config: PdfPageConfig = PdfPageConfig.A4,
    density: Density = Density(2f),
    mode: RenderMode = RenderMode.VECTOR,
    defaultFontFamily: FontFamily? = InterFontFamily,
    pagination: PdfPagination = PdfPagination.AUTO,
    header: (@Composable (PdfPageInfo) -> Unit)? = null,
    footer: (@Composable (PdfPageInfo) -> Unit)? = null,
    content: @Composable () -> Unit,
)
```

(and the `ByteArray` variant identically). `PdfPageInfo` joins the public type list.

**Binary compatibility note:** adding defaulted parameters changes the JVM signatures (including the `$default` bridge), so already-compiled consumers must recompile against the new version. Accepted as part of the next minor release; no legacy overloads kept.

### Slot semantics (documented on the API)

- Slots render at the full content width.
- Slot height is measured once with a sentinel `PdfPageInfo(pageIndex = 0, pageCount = 2)` and that height is reserved on **every** page. The `pageCount = 2` sentinel exists so the natural `if (info.pageCount > 1) Text("Page …")` footer does not measure to zero height. Document loudly: **slot height must be stable across page numbers**; content taller than the measured band is clipped (hard PDF clip).
- A slot measuring zero height reserves no band and is skipped entirely at stamp time.
- Slot measurement is capped at the page content height (a `fillMaxHeight()` slot measures to the cap and trips the guard below rather than reserving an absurd band).
- `IllegalArgumentException` if `headerPx + footerPx >= contentHeightPx` (message includes the measured heights vs available height). Passes through unwrapped, per existing convention.
- Slot render failures wrap in `Compose2PdfException` like other rendering errors.
- Slots see `LocalPdfPageConfig` and may use `PdfLink`.
- If content is truncated at `MAX_AUTO_PAGES` (100), footers read "Page 100 of 100" — the count reflects **emitted** pages. Documented.

## Rendering flow

### Fast path (both slots null)

Bypass everything below; execute today's code path unchanged. This preserves the fidelity-test guarantee (see CLAUDE.md gotcha: single-page content must fall back to the original render path).

### Vector, slots present

1. **Measure slots**: `ComposeToSvg.measureContentHeight(contentWidthPx, cap = contentHeightPx, density)` composing the slot with `PdfPageInfo(0, 2)` → `headerPx`, `footerPx` (0 for a null slot).
2. **Guard**: `require(headerPx + footerPx < contentHeightPx)`.
3. **Rounding discipline**: compute `effectiveContentHeightPx = contentHeightPx − headerPx − footerPx` in **px first**, then derive the pt values from px (`headerPt = headerPx / density.density`, etc.) — never the reverse — so pagination breaks (px) and page slicing (pt) cannot drift by sub-point amounts (hairline clipping at page bottoms).
4. **Derived page config**: build an adjusted `PdfPageConfig` whose margins are inflated by the band heights (`margins.top += headerPt`, `margins.bottom += footerPt`), so its derived `contentHeight` equals the effective content area. `WrapContent` provides **this** config through `LocalPdfPageConfig`. This is load-bearing: the public `PaginatedColumn` computes its own break interval from `LocalPdfPageConfig`, and must break at the effective height, or user content would split at every page boundary. It is also the honest semantic — the usable content area genuinely is smaller. Documented on `LocalPdfPageConfig`.
5. **Paginate body** against `effectiveContentHeightPx` at **all** `PaginatedColumn` call sites (measurement pass, vector render pass, raster pass). `PaginatedColumn` / `calculatePageBreakPositions` themselves are unchanged.
6. **Fallback threshold**: the single-page check compares `measuredHeightPx <= effectiveContentHeightPx`. When it (or fillMaxHeight detection) triggers **and slots are present**, render the body at the effective content size and add it via the standard `addPage` with the adjusted `PageLayout`, then stamp slots. (On current main, `addPage` is margin-aware and funnels through the same `renderSvgToContentArea` as the auto path, so no special single-page geometry is needed — this supersedes the review-time concern, which was based on an older revision where `addPage` stretched the SVG over the full page.)
7. **Slice body**: existing `SvgToPdfConverter.addAutoPages` with an adjusted `PageLayout` copy (`marginTopPt += headerPt`, `contentHeightPt −= headerPt + footerPt`). Reviewer verified the existing clip/offset math stays exactly correct under this substitution. `addAutoPages` is changed to **return the emitted page count** (the `ceil`/clamp formula lives only there); the renderer uses the returned count for `PdfPageInfo` and the truncation warning — no duplicated formula.
8. **Stamp slots**: for each emitted page, render the header/footer as a small SVG (`ComposeToSvg.render(contentWidthPx, slotHeightPx, density)` with the real `PdfPageInfo(pageIndex, pageCount)`, wrapped in `WrapContent` with its own `PdfLinkCollector`) and draw it into the top/bottom band via a new `SvgToPdfConverter.drawSvgOnPage(doc, page, svg, xPt, yPt, widthPt, heightPt, density, fontCache, imageCache)` — extracted from `addPage`'s drawing body, targeting an existing page at an offset, scaled uniformly at `1/density`, with an explicit `addRect`+`clip` on the band (Skia's SVG cull rect does not hard-clip). Font/image caches are shared across all pages and slot renders.
9. **Links**: body links distribute using the **effective** content height for page assignment and a Y shifted by the header band (`svgY += headerPt` is equivalent to `marginTop += headerPt` in `CoordinateTransform.svgToPdfRect` — verified). Slot links use the band's own offsets; `addLinkToPage` gains explicit offset parameters instead of reading `config.margins.top` directly.

### `SINGLE_PAGE` pagination, slots present

Supported (not an error): one page via the band path with `PdfPageInfo(0, 1)`, body clipped to the effective content area. (Without the band path this combination would silently drop the slots — explicitly handled.)

### Raster, slots present

Mirrors vector: body bitmap rendered at the full content width and sliced at `effectiveContentHeightPx`; slot bitmaps rendered per page and drawn into the bands. `addBitmapPage` gains a destination-rect-aware variant — the current fixed dest rect (`margins.left, margins.bottom, contentWidth, contentHeight`) cannot place band-reduced slices.

**Precursor — existing raster bug, confirmed at runtime (2026-07-16):** the last partial slice is drawn into that fixed full-height dest rect and is vertically stretched. Reproduction: a 1047dp red box (1.5 pages) in raster mode yields a page-2 image of 698px (half a page of content) rendered red across the *entire* content area instead of only the top half. The auto-pagination fidelity test only checks pages are non-blank, so this was never caught. Fix it (draw partial slices at proportional height, top-aligned) as a standalone change **before** building the band math on top.

## Testing

- **Unit tests** (`:compose2pdf:test`):
  - effective-height math (px-first rounding), guard exception message,
  - `PdfPageInfo` values received per page (slots that record their argument),
  - zero-height slot → no band, slot skipped,
  - `addAutoPages` returned count matches emitted pages (including the `MAX_AUTO_PAGES` clamp),
  - `SINGLE_PAGE` + slots emits one page with stamped slots.
- **Regression test**: with `header = null, footer = null`, output is equivalent to the pre-change renderer. **Not** a raw byte comparison — PDFBox embeds a time-seeded document ID on every save — compare decoded page content streams instead.
- **Fidelity tests** (`:fidelity-test:test`): new fixture — multi-page content with a header ("Acme Corp") and footer ("Page X of Y") — in both `VECTOR` and `RASTER`; verifies band placement and that body content never overlaps the bands. All existing fixtures unchanged.
- **Raster precursor**: a fidelity assertion for the last-partial-slice geometry (currently only "non-blank" is checked).
- **Examples**: add a header/footer example to `examples/`.

## Documentation

- `docs/` usage pages + README API listing gain the header/footer parameters, `PdfPageInfo`, the height-stability rule, the `pageCount = 2` measurement sentinel, and the truncation ("Page 100 of 100") behavior.
- CLAUDE.md public-API list and gotchas updated (`LocalPdfPageConfig` reflects the effective content area when slots are present).

## Out of scope

- Per-page/different-first-page band heights (non-uniform page heights in pagination).
- Slots on the manual `renderToPdf(pages)` overloads.
- Render-once XObject reuse for static slots (possible future perf optimization; current cost is 2 small scene renders per page — negligible).

## Post-review placement amendment (2026-07-20)

After visual review against real-world PDF output (Chrome print reference), the band
placement described above changed. The original design stacked bands below/above the
full configured margin (margin-inflation: `margin + gap + bandHeight`), which pushed
header/footer bands unnaturally far from the page edge. The final placement is
edge-anchored inside the margins instead: bands sit `SLOT_EDGE_INSET_PT` (18pt, ~0.25in)
from the physical page edge, with a `SLOT_BODY_GAP_PT` (10pt) gap to the body. Body
content keeps the configured margins untouched, and only grows (via `max()`) when a
band + inset + gap is taller than the configured margin allows. See commits `c0aa0cf`
and `353c14d`.
