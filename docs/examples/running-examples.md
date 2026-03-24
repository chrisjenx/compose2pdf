---
title: Running Examples
parent: Examples
nav_order: 4
---

# Running the Examples

The compose2pdf repository includes 10 runnable examples that generate PDFs, rasterized PNGs, and code snippets.

---

## Quick start

```bash
git clone https://github.com/nickhall-ck/compose2pdf.git
cd compose2pdf
./gradlew :examples:run
```

---

## Output

The examples generate three types of output in `examples/build/output/`:

```
examples/build/output/
├── pdfs/           # Generated PDF files
├── images/         # Rasterized PNG previews (150 DPI)
└── snippets/       # Extracted code snippets
```

Sample console output:

```
══════════════════════════════════════════════════════════
  compose2pdf examples — generated 10 PDFs
══════════════════════════════════════════════════════════

  01-hello.pdf                          17.2 KB  1 page(s)
  02-page-config.pdf                    22.8 KB  1 page(s)
  03-text-styling.pdf                   35.1 KB  1 page(s)
  04-layout-basics.pdf                  28.4 KB  1 page(s)
  05-shapes.pdf                         19.6 KB  1 page(s)
  06-images.pdf                         45.2 KB  1 page(s)
  07-links.pdf                          26.3 KB  1 page(s)
  08-multi-page.pdf                     42.7 KB  3 page(s)
  09-vector-vs-raster.pdf               18.9 KB  1 page(s)
  10-invoice.pdf                        38.5 KB  1 page(s)
```

---

## All examples

| # | File | Description |
|:--|:-----|:------------|
| 01 | `01_HelloPdf.kt` | Simplest example -- text on a page |
| 02 | `02_PageConfiguration.kt` | Page presets, margins, landscape, custom sizes |
| 03 | `03_TextStyling.kt` | Font weights, styles, sizes, colors, decorations, alignment |
| 04 | `04_LayoutBasics.kt` | Column, Row, Box, weights, arrangement, padding |
| 05 | `05_ShapesAndDrawing.kt` | Backgrounds, borders, clips, Canvas drawing, PdfRoundedCornerShape |
| 06 | `06_ImagesInPdf.kt` | Bitmap embedding, sizing, circle clips, image + text layout |
| 07 | `07_LinksInPdf.kt` | Text links, button links, inline links, large areas, email links |
| 08 | `08_MultiPageDocument.kt` | 3-page report with shared headers/footers and page numbers |
| 09 | `09_VectorVsRaster.kt` | Vector vs raster mode comparison |
| 10 | `10_ProfessionalInvoice.kt` | Complete invoice with all features |

---

## How the runner works

`Main.kt` in the examples module:

1. Calls each example function (e.g., `helloPdf()`, `professionalInvoice()`)
2. Each function returns a list of `ExampleOutput(name, sourceFile, pdfBytes)`
3. The runner writes PDFs, rasterizes them to PNG at 150 DPI via PDFBox, and extracts code snippets (delimited by `// --- snippet start ---` / `// --- snippet end ---`)

---

## Adding your own example

1. Create a new file in `examples/src/main/kotlin/.../examples/` (e.g., `11_MyExample.kt`)
2. Define a function that returns `List<ExampleOutput>`:
   ```kotlin
   fun myExample() = listOf(
       ExampleOutput(
           name = "11-my-example",
           sourceFile = "11_MyExample.kt",
           pdfBytes = renderToPdf { /* content */ },
       )
   )
   ```
3. Add `::myExample` to the `examples` list in `Main.kt`
4. Wrap your showcase code in `// --- snippet start ---` / `// --- snippet end ---` for snippet extraction
5. Run `./gradlew :examples:run`
