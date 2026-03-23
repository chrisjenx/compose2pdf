package com.chrisjenx.compose2pdf.test

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class FidelityResult(
    val name: String,
    val category: String,
    val description: String,
    // Vector metrics
    val vectorRmse: Double,
    val vectorSsim: Double,
    val vectorExactMatch: Double,
    val vectorMaxError: Double,
    val vectorStatus: Status,
    // Raster metrics
    val rasterRmse: Double,
    val rasterSsim: Double,
    val rasterExactMatch: Double,
    val rasterMaxError: Double,
    val rasterStatus: Status,
    // Image paths (relative to report dir)
    val composePath: String,
    val vectorPath: String,
    val rasterPath: String,
    val vectorDiffPath: String,
    val rasterDiffPath: String,
    // PDF file paths (relative to report dir)
    val vectorPdfPath: String = "",
    val rasterPdfPath: String = "",
) {
    val rowStatus: Status
        get() {
            val statuses = listOf(vectorStatus, rasterStatus)
            return when {
                statuses.any { it == Status.FAIL } -> Status.FAIL
                statuses.any { it == Status.WARN } -> Status.WARN
                else -> Status.PASS
            }
        }
}

enum class Status(val label: String, val cssClass: String) {
    PASS("PASS", "pass"),
    WARN("WARN", "warn"),
    FAIL("FAIL", "fail"),
    SKIPPED("Skipped", "skipped"),
}

fun vectorStatus(rmse: Double, threshold: Double): Status = when {
    rmse <= 0.05 -> Status.PASS
    rmse <= threshold -> Status.WARN
    else -> Status.FAIL
}

fun rasterStatus(exactMatch: Double): Status = when {
    exactMatch >= 0.999 -> Status.PASS
    exactMatch >= 0.99 -> Status.WARN
    else -> Status.FAIL
}

private fun escapeHtml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private val categoryColors = mapOf(
    "basic" to "#607D8B",
    "text" to "#2196F3",
    "shapes" to "#9C27B0",
    "layout" to "#FF9800",
    "visual" to "#E91E63",
    "composite" to "#4CAF50",
    "edge-case" to "#795548",
    "document" to "#FF5722",
    "page-size" to "#009688",
)

fun generateFidelityReport(results: List<FidelityResult>, outputFile: File) {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    val passCount = results.count { it.rowStatus == Status.PASS }
    val warnCount = results.count { it.rowStatus == Status.WARN }
    val failCount = results.count { it.rowStatus == Status.FAIL }

    val vectorMeanRmse = results.map { it.vectorRmse }.average()
    val vectorMeanSsim = results.map { it.vectorSsim }.average()
    val vectorMeanMatch = results.map { it.vectorExactMatch }.average()
    val rasterMeanRmse = results.map { it.rasterRmse }.average()
    val rasterMeanSsim = results.map { it.rasterSsim }.average()
    val rasterMeanMatch = results.map { it.rasterExactMatch }.average()

    val categories = results.map { it.category }.distinct().sorted()

    val html = buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("<html lang='en'><head><meta charset='utf-8'>")
        appendLine("<meta name='viewport' content='width=device-width, initial-scale=1'>")
        appendLine("<title>compose2pdf Fidelity Report</title>")
        appendLine("<style>")
        appendLine(
            """
* { box-sizing: border-box; }
body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    margin: 0; padding: 20px; background: #fafafa; color: #333;
}
h1 { margin: 0 0 4px 0; font-size: 24px; }
.timestamp { color: #888; font-size: 13px; margin: 0 0 16px 0; }
.summary {
    display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-start;
    margin-bottom: 16px; padding: 16px; background: #fff;
    border: 1px solid #e0e0e0; border-radius: 8px;
}
.badges { display: flex; gap: 8px; align-items: center; }
.badge {
    display: inline-block; padding: 4px 12px; border-radius: 4px;
    font-weight: 700; font-size: 14px; color: #fff;
}
.badge.pass { background: #4CAF50; }
.badge.warn { background: #FF9800; }
.badge.fail { background: #f44336; }
.badge.total { background: #607D8B; }
.badge.skipped { background: #9E9E9E; }
.stats-table { border-collapse: collapse; font-size: 13px; }
.stats-table th, .stats-table td { padding: 4px 12px; text-align: right; }
.stats-table th { text-align: left; font-weight: 600; }
.stats-table td { font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace; }
.filters {
    display: flex; flex-wrap: wrap; gap: 12px; align-items: center;
    margin-bottom: 16px; padding: 12px 16px; background: #fff;
    border: 1px solid #e0e0e0; border-radius: 8px;
}
.filter-group { display: flex; align-items: center; gap: 4px; }
.filter-group label { font-weight: 600; font-size: 13px; margin-right: 4px; }
.filter-btn {
    padding: 4px 10px; border: 1px solid #ccc; border-radius: 4px;
    background: #fff; cursor: pointer; font-size: 12px;
}
.filter-btn:hover { background: #f0f0f0; }
.filter-btn.active { background: #333; color: #fff; border-color: #333; }
.sort-select {
    padding: 4px 8px; border: 1px solid #ccc; border-radius: 4px;
    font-size: 12px; background: #fff;
}
table#fidelity-table {
    width: 100%; border-collapse: collapse; background: #fff;
    border: 1px solid #e0e0e0; border-radius: 8px; overflow: hidden;
}
#fidelity-table th {
    background: #f5f5f5; font-weight: 600; font-size: 12px;
    text-transform: uppercase; letter-spacing: 0.5px;
    padding: 10px 8px; border-bottom: 2px solid #ddd; text-align: center;
}
#fidelity-table td {
    padding: 8px; vertical-align: middle; text-align: center;
    border-bottom: 1px solid #eee;
}
#fidelity-table td.fixture-cell { text-align: left; min-width: 160px; }
.fixture-name { font-weight: 600; font-size: 14px; }
.fixture-desc { font-size: 11px; color: #888; margin-top: 2px; }
.cat-badge {
    display: inline-block; padding: 2px 8px; border-radius: 3px;
    font-size: 10px; font-weight: 600; color: #fff; margin-top: 4px;
}
.thumb {
    max-width: 160px; max-height: 220px; object-fit: contain;
    border: 1px solid #eee; border-radius: 4px; cursor: pointer;
    transition: transform 0.1s;
}
.thumb:hover { transform: scale(1.03); box-shadow: 0 2px 8px rgba(0,0,0,0.15); }
.pdf-link {
    display: block; text-align: center; font-size: 11px; margin-top: 4px;
    color: #1976D2; text-decoration: none;
}
.pdf-link:hover { text-decoration: underline; }
.metrics { text-align: left !important; font-size: 12px; }
.metrics table { border-collapse: collapse; width: 100%; }
#fidelity-table .metrics td { padding: 2px 6px; border: none; font-family: 'SF Mono', Monaco, monospace; font-size: 11px; }
#fidelity-table .metrics td:first-child { font-weight: 600; color: #666; white-space: nowrap; }
.pass-text { color: #2E7D32; font-weight: 700; }
.warn-text { color: #E65100; font-weight: 700; }
.fail-text { color: #C62828; font-weight: 700; }
.skipped-text { color: #9E9E9E; font-weight: 700; }
.row-pass { background: #f0fff0; }
.row-warn { background: #fffff0; }
.row-fail { background: #fff0f0; }
.modal {
    display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%;
    background: rgba(0,0,0,0.85); z-index: 1000;
    justify-content: center; align-items: center; cursor: pointer;
}
.modal img {
    max-width: 90vw; max-height: 90vh; object-fit: contain;
    border-radius: 4px; box-shadow: 0 4px 32px rgba(0,0,0,0.5);
}
            """.trimIndent(),
        )
        appendLine("</style></head><body>")
        appendLine("<h1>compose2pdf Fidelity Report</h1>")
        appendLine("<p class='timestamp'>Generated: $timestamp &middot; ${results.size} fixtures</p>")

        // Summary
        appendLine("<div class='summary'>")
        appendLine("<div class='badges'>")
        appendLine("<span class='badge pass'>$passCount PASS</span>")
        appendLine("<span class='badge warn'>$warnCount WARN</span>")
        appendLine("<span class='badge fail'>$failCount FAIL</span>")
        appendLine("<span class='badge total'>${results.size} total</span>")
        appendLine("</div>")
        appendLine("<table class='stats-table'>")
        appendLine("<tr><th></th><th>Vector</th><th>Raster</th></tr>")
        appendLine("<tr><td>Mean RMSE</td><td>${"%.4f".format(vectorMeanRmse)}</td><td>${"%.4f".format(rasterMeanRmse)}</td></tr>")
        appendLine("<tr><td>Mean SSIM</td><td>${"%.4f".format(vectorMeanSsim)}</td><td>${"%.4f".format(rasterMeanSsim)}</td></tr>")
        appendLine("<tr><td>Mean Match%</td><td>${"%.2f".format(vectorMeanMatch * 100)}%</td><td>${"%.2f".format(rasterMeanMatch * 100)}%</td></tr>")
        appendLine("</table>")
        appendLine("</div>")

        // Filters
        appendLine("<div class='filters'>")
        appendLine("<div class='filter-group'><label>Category:</label>")
        appendLine("<button class='filter-btn active' onclick='setCategory(\"all\", this)'>All</button>")
        for (cat in categories) {
            appendLine("<button class='filter-btn' onclick='setCategory(\"${escapeHtml(cat)}\", this)'>${escapeHtml(cat.replaceFirstChar { it.uppercase() })}</button>")
        }
        appendLine("</div>")
        appendLine("<div class='filter-group'><label>Status:</label>")
        appendLine("<button class='filter-btn active' onclick='setStatus(\"all\", this)'>All</button>")
        appendLine("<button class='filter-btn' onclick='setStatus(\"pass\", this)'>Pass</button>")
        appendLine("<button class='filter-btn' onclick='setStatus(\"warn\", this)'>Warn</button>")
        appendLine("<button class='filter-btn' onclick='setStatus(\"fail\", this)'>Fail</button>")
        appendLine("</div>")
        appendLine("<div class='filter-group'><label>Sort:</label>")
        appendLine("<select class='sort-select' onchange='sortTable(this.value)'>")
        appendLine("<option value='name'>Name</option>")
        appendLine("<option value='worst'>Worst Metric</option>")
        appendLine("</select></div>")
        appendLine("</div>")

        // Table
        appendLine("<table id='fidelity-table'>")
        appendLine("<thead><tr>")
        appendLine("<th>Fixture</th>")
        appendLine("<th>Compose (ref)</th>")
        appendLine("<th>Vector PDF</th>")
        appendLine("<th>Vector Diff</th>")
        appendLine("<th>Raster PDF</th>")
        appendLine("<th>Raster Diff</th>")
        appendLine("<th>Vector Metrics</th>")
        appendLine("<th>Raster Metrics</th>")
        appendLine("</tr></thead>")
        appendLine("<tbody>")

        for (result in results) {
            val worstMetric = maxOf(result.vectorRmse, result.rasterRmse)
            val rowClass = "row-${result.rowStatus.cssClass}"
            val catColor = categoryColors[result.category] ?: "#607D8B"

            appendLine("<tr class='$rowClass' data-category='${escapeHtml(result.category)}' data-status='${result.rowStatus.cssClass}' data-name='${escapeHtml(result.name)}' data-worst-metric='${"%.6f".format(worstMetric)}'>")

            // Fixture info
            appendLine("<td class='fixture-cell'>")
            appendLine("<div class='fixture-name'>${escapeHtml(result.name)}</div>")
            if (result.description.isNotEmpty()) {
                appendLine("<div class='fixture-desc'>${escapeHtml(result.description)}</div>")
            }
            appendLine("<span class='cat-badge' style='background:$catColor'>${escapeHtml(result.category)}</span>")
            appendLine("</td>")

            // Compose reference
            appendLine("<td><img class='thumb' src='${escapeHtml(result.composePath)}' alt='${escapeHtml(result.name)} compose' onclick='openModal(this.src)'></td>")

            // Vector
            appendLine("<td><img class='thumb' src='${escapeHtml(result.vectorPath)}' alt='${escapeHtml(result.name)} vector' onclick='openModal(this.src)'>")
            if (result.vectorPdfPath.isNotEmpty()) {
                appendLine("<a href='${escapeHtml(result.vectorPdfPath)}' class='pdf-link' target='_blank'>Open PDF</a>")
            }
            appendLine("</td>")
            appendLine("<td><img class='thumb' src='${escapeHtml(result.vectorDiffPath)}' alt='${escapeHtml(result.name)} vector diff' onclick='openModal(this.src)'></td>")

            // Raster
            appendLine("<td><img class='thumb' src='${escapeHtml(result.rasterPath)}' alt='${escapeHtml(result.name)} raster' onclick='openModal(this.src)'>")
            if (result.rasterPdfPath.isNotEmpty()) {
                appendLine("<a href='${escapeHtml(result.rasterPdfPath)}' class='pdf-link' target='_blank'>Open PDF</a>")
            }
            appendLine("</td>")
            appendLine("<td><img class='thumb' src='${escapeHtml(result.rasterDiffPath)}' alt='${escapeHtml(result.name)} raster diff' onclick='openModal(this.src)'></td>")

            // Vector metrics
            val vStatusClass = "${result.vectorStatus.cssClass}-text"
            appendLine("<td class='metrics'><table>")
            appendLine("<tr><td>RMSE</td><td>${"%.4f".format(result.vectorRmse)}</td></tr>")
            appendLine("<tr><td>SSIM</td><td>${"%.4f".format(result.vectorSsim)}</td></tr>")
            appendLine("<tr><td>Match</td><td>${"%.2f".format(result.vectorExactMatch * 100)}%</td></tr>")
            appendLine("<tr><td>MaxErr</td><td>${"%.4f".format(result.vectorMaxError)}</td></tr>")
            appendLine("<tr><td>Status</td><td class='$vStatusClass'>${result.vectorStatus.label}</td></tr>")
            appendLine("</table></td>")

            // Raster metrics
            val rStatusClass = "${result.rasterStatus.cssClass}-text"
            appendLine("<td class='metrics'><table>")
            appendLine("<tr><td>RMSE</td><td>${"%.4f".format(result.rasterRmse)}</td></tr>")
            appendLine("<tr><td>SSIM</td><td>${"%.4f".format(result.rasterSsim)}</td></tr>")
            appendLine("<tr><td>Match</td><td>${"%.2f".format(result.rasterExactMatch * 100)}%</td></tr>")
            appendLine("<tr><td>MaxErr</td><td>${"%.4f".format(result.rasterMaxError)}</td></tr>")
            appendLine("<tr><td>Status</td><td class='$rStatusClass'>${result.rasterStatus.label}</td></tr>")
            appendLine("</table></td>")

            appendLine("</tr>")
        }

        appendLine("</tbody></table>")

        // Modal
        appendLine("<div id='modal' class='modal' onclick='closeModal()'><img id='modal-img' src=''></div>")

        // JavaScript
        appendLine("<script>")
        appendLine(
            """
let activeCategory = 'all';
let activeStatus = 'all';
function filterRows() {
    document.querySelectorAll('#fidelity-table > tbody > tr').forEach(function(row) {
        var cat = row.getAttribute('data-category');
        var status = row.getAttribute('data-status');
        var catMatch = activeCategory === 'all' || cat === activeCategory;
        var statusMatch = activeStatus === 'all' || status === activeStatus;
        row.style.display = (catMatch && statusMatch) ? '' : 'none';
    });
}
function setCategory(cat, btn) {
    activeCategory = cat;
    btn.parentElement.querySelectorAll('.filter-btn').forEach(function(b) { b.classList.remove('active'); });
    btn.classList.add('active');
    filterRows();
}
function setStatus(status, btn) {
    activeStatus = status;
    btn.parentElement.querySelectorAll('.filter-btn').forEach(function(b) { b.classList.remove('active'); });
    btn.classList.add('active');
    filterRows();
}
function sortTable(key) {
    var tbody = document.querySelector('#fidelity-table tbody');
    var rows = Array.from(tbody.querySelectorAll('tr'));
    rows.sort(function(a, b) {
        if (key === 'name') return a.getAttribute('data-name').localeCompare(b.getAttribute('data-name'));
        if (key === 'worst') return parseFloat(b.getAttribute('data-worst-metric')) - parseFloat(a.getAttribute('data-worst-metric'));
        return 0;
    });
    rows.forEach(function(row) { tbody.appendChild(row); });
}
function openModal(src) {
    document.getElementById('modal-img').src = src;
    document.getElementById('modal').style.display = 'flex';
}
function closeModal() {
    document.getElementById('modal').style.display = 'none';
}
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') closeModal();
});
            """.trimIndent(),
        )
        appendLine("</script>")
        appendLine("</body></html>")
    }

    outputFile.parentFile.mkdirs()
    outputFile.writeText(html)
}
