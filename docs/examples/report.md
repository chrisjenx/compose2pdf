---
title: Multi-page Report
parent: Examples
nav_order: 3
---

# Multi-page Report

A 3-page quarterly report demonstrating multi-page documents with shared headers, footers, tables, and page-specific content.

---

## The result

| Page 1: Cover | Page 2: Data | Page 3: Summary |
|:---:|:---:|:---:|
| ![Page 1]({{ site.baseurl }}/assets/images/08-multi-page-1.png){: .rounded .shadow } | ![Page 2]({{ site.baseurl }}/assets/images/08-multi-page-2.png){: .rounded .shadow } | ![Page 3]({{ site.baseurl }}/assets/images/08-multi-page-3.png){: .rounded .shadow } |

[Download PDF]({{ site.baseurl }}/assets/pdfs/08-multi-page.pdf){: .btn .btn-outline .fs-3 }

---

## Entry point

```kotlin
val totalPages = 3

val pdfBytes = renderToPdf(
    pages = totalPages,
    config = PdfPageConfig.A4WithMargins,
) { pageIndex ->
    Column(Modifier.fillMaxSize()) {
        // Shared header
        PageHeader("Quarterly Report — Q1 2025")

        Spacer(Modifier.height(24.dp))

        // Page-specific content
        when (pageIndex) {
            0 -> CoverPage()
            1 -> DataPage()
            2 -> SummaryPage()
        }

        // Push footer to bottom
        Spacer(Modifier.weight(1f))

        // Shared footer with page number
        PageFooter(pageIndex + 1, totalPages)
    }
}
```

---

## Page structure pattern

Every page shares the same outer structure:

```
┌──────────────────────────┐
│  PageHeader (shared)     │
│──────────────────────────│
│                          │
│  Page-specific content   │
│  (CoverPage / DataPage   │
│   / SummaryPage)         │
│                          │
│  Spacer(weight=1f) ↕     │
│                          │
│──────────────────────────│
│  PageFooter (shared)     │
└──────────────────────────┘
```

The `Spacer(Modifier.weight(1f))` between content and footer expands to fill remaining space, pushing the footer to the bottom regardless of content height.

---

## Shared header

```kotlin
@Composable
fun PageHeader(title: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1565C0))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
```

---

## Shared footer with page numbers

```kotlin
@Composable
fun PageFooter(current: Int, total: Int) {
    Divider(color = Color(0xFFBDBDBD))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Acme Corp — Confidential", fontSize = 10.sp, color = Color.Gray)
        Text("Page $current of $total", fontSize = 10.sp, color = Color.Gray)
    }
}
```

---

## Page 1: Cover page

```kotlin
@Composable
fun CoverPage() {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Quarterly Report", fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Q1 2025 — January to March", fontSize = 18.sp, color = Color.Gray)
        Spacer(Modifier.height(32.dp))
        Text("Prepared by the Analytics Team", fontSize = 14.sp)
        Text("March 31, 2025", fontSize = 14.sp, color = Color.Gray)
    }
}
```

---

## Page 2: Data table

```kotlin
@Composable
fun DataPage() {
    Column {
        Text("Key Metrics", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Table header
        Row(
            Modifier.fillMaxWidth()
                .background(Color(0xFFE3F2FD))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("Metric", Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Jan", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
            Text("Feb", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
            Text("Mar", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
        }

        // Data rows with alternating backgrounds
        val data = listOf(
            listOf("Revenue", "$41,200", "$38,900", "$45,600"),
            listOf("New Customers", "124", "108", "156"),
            listOf("Churn Rate", "2.1%", "1.8%", "1.5%"),
            listOf("NPS Score", "72", "74", "78"),
            listOf("Support Tickets", "342", "298", "275"),
        )

        for ((index, row) in data.withIndex()) {
            val bg = if (index % 2 == 0) Color.Transparent else Color(0xFFFAFAFA)
            Row(
                Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(row[0], Modifier.weight(2f), fontSize = 12.sp)
                Text(row[1], Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End)
                Text(row[2], Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End)
                Text(row[3], Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End)
            }
        }
    }
}
```

---

## Page 3: Summary with bullet points

```kotlin
@Composable
fun SummaryPage() {
    Column {
        Text("Summary & Outlook", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Text("Key Highlights", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        for (highlight in listOf(
            "Revenue grew 10.7% from January to March",
            "Customer acquisition improved 25.8% in March",
            "Churn rate decreased from 2.1% to 1.5%",
            "NPS score reached all-time high of 78",
        )) {
            Text("  •  $highlight", fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(24.dp))
        Text("Q2 Priorities", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        for ((i, priority) in listOf(
            "Launch self-service portal to reduce support tickets by 20%",
            "Expand into APAC market with localized onboarding",
            "Target $50K monthly revenue by June",
        ).withIndex()) {
            Text("  ${i + 1}. $priority", fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
        }
    }
}
```

---

## Key patterns

| Pattern | How |
|:--------|:----|
| Shared header/footer | Extract into composable functions, call on every page |
| Footer at bottom | `Spacer(Modifier.weight(1f))` between content and footer |
| Page numbers | Pass `pageIndex + 1` and `totalPages` to the footer |
| Page-specific content | `when (pageIndex)` to dispatch to different composables |
| Data tables | Row with weights, alternating row backgrounds |

---

## See also

- [Usage: Multi-page]({{ site.baseurl }}/usage/multi-page) -- Multi-page patterns in depth
- [Usage: Layout]({{ site.baseurl }}/usage/layout) -- Table patterns
- [Running Examples]({{ site.baseurl }}/examples/running-examples) -- Generate this PDF locally
