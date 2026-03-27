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
    // Android cross-platform comparison (optional)
    val androidPath: String = "",
    val androidDiffPath: String = "",
    val androidPdfPath: String = "",
    val androidRmse: Double = -1.0,
    val androidSsim: Double = -1.0,
    val androidExactMatch: Double = -1.0,
    val androidMaxError: Double = -1.0,
    val androidStatus: Status = Status.SKIPPED,
) {
    val hasAndroid: Boolean get() = androidStatus != Status.SKIPPED

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
.stats-table { border-collapse: collapse; font-size: 13px; }
.stats-table th, .stats-table td { padding: 4px 12px; text-align: right; }
.stats-table th { text-align: left; font-weight: 600; }
.stats-table td { font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace; }
.filters {
    display: flex; flex-wrap: wrap; gap: 12px; align-items: center;
    margin-bottom: 16px; padding: 12px 16px; background: #fff;
    border: 1px solid #e0e0e0; border-radius: 8px;
    position: sticky; top: 0; z-index: 100;
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
#cards-container {
    display: grid; grid-template-columns: repeat(auto-fill, minmax(700px, 1fr)); gap: 12px;
}
.fixture-card {
    background: #fff; border: 1px solid #e0e0e0; border-radius: 8px;
    overflow: hidden;
}
.fixture-card.status-pass { border-left: 4px solid #4CAF50; }
.fixture-card.status-warn { border-left: 4px solid #FF9800; }
.fixture-card.status-fail { border-left: 4px solid #f44336; }
.card-header {
    display: flex; align-items: center; gap: 10px; padding: 12px 16px;
    border-bottom: 1px solid #f0f0f0; flex-wrap: wrap;
}
.fixture-name { font-weight: 700; font-size: 15px; }
.fixture-desc { font-size: 12px; color: #888; flex-basis: 100%; margin-top: 2px; }
.cat-badge {
    display: inline-block; padding: 2px 8px; border-radius: 3px;
    font-size: 10px; font-weight: 600; color: #fff;
}
.status-badge {
    display: inline-block; padding: 2px 8px; border-radius: 3px;
    font-size: 10px; font-weight: 700; color: #fff; margin-left: auto;
}
.status-badge.pass { background: #4CAF50; }
.status-badge.warn { background: #FF9800; }
.status-badge.fail { background: #f44336; }
.card-body { display: flex; }
.ref-col {
    flex: 0 0 auto; padding: 12px; border-right: 1px solid #f0f0f0;
    display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.ref-label {
    font-size: 10px; font-weight: 600; text-transform: uppercase;
    letter-spacing: 0.5px; color: #999; margin-bottom: 6px;
}
.modes-col { flex: 1; min-width: 0; }
.mode-section { padding: 10px 16px; }
.mode-section + .mode-section { border-top: 1px solid #f0f0f0; }
.mode-label {
    font-size: 11px; font-weight: 700; text-transform: uppercase;
    letter-spacing: 0.5px; color: #555; margin-bottom: 8px;
}
.mode-content { display: flex; align-items: flex-start; gap: 12px; flex-wrap: wrap; }
.mode-images { display: flex; gap: 8px; align-items: flex-start; }
.img-group { text-align: center; }
.img-label { font-size: 9px; color: #aaa; text-transform: uppercase; margin-bottom: 3px; }
.thumb {
    max-width: 180px; max-height: 240px; object-fit: contain;
    border: 1px solid #eee; border-radius: 4px; cursor: pointer;
    transition: transform 0.1s; display: block;
}
.thumb:hover { transform: scale(1.03); box-shadow: 0 2px 8px rgba(0,0,0,0.15); }
.pdf-link {
    display: block; text-align: center; font-size: 10px; margin-top: 3px;
    color: #1976D2; text-decoration: none;
}
.pdf-link:hover { text-decoration: underline; }
.mode-metrics {
    font-size: 11px; font-family: 'SF Mono', Monaco, monospace;
    display: flex; flex-direction: column; gap: 2px; padding-top: 2px;
}
.metric-row { display: flex; gap: 6px; }
.metric-label { color: #888; font-weight: 600; min-width: 44px; }
.metric-value { color: #333; }
.pass-text { color: #2E7D32; font-weight: 700; }
.warn-text { color: #E65100; font-weight: 700; }
.fail-text { color: #C62828; font-weight: 700; }
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
        appendLine("<select class='sort-select' onchange='sortCards(this.value)'>")
        appendLine("<option value='name'>Name</option>")
        appendLine("<option value='worst'>Worst Metric</option>")
        appendLine("</select></div>")
        appendLine("</div>")

        // Cards
        appendLine("<div id='cards-container'>")

        for (result in results) {
            val worstMetric = maxOf(result.vectorRmse, result.rasterRmse)
            val statusClass = result.rowStatus.cssClass
            val catColor = categoryColors[result.category] ?: "#607D8B"

            appendLine("<div class='fixture-card status-$statusClass' data-category='${escapeHtml(result.category)}' data-status='$statusClass' data-name='${escapeHtml(result.name)}' data-worst-metric='$worstMetric'>")

            // Header
            appendLine("<div class='card-header'>")
            appendLine("<span class='fixture-name'>${escapeHtml(result.name)}</span>")
            appendLine("<span class='cat-badge' style='background:$catColor'>${escapeHtml(result.category)}</span>")
            appendLine("<span class='status-badge $statusClass'>${result.rowStatus.label}</span>")
            if (result.description.isNotEmpty()) {
                appendLine("<span class='fixture-desc'>${escapeHtml(result.description)}</span>")
            }
            appendLine("</div>")

            // Body
            appendLine("<div class='card-body'>")

            // Compose reference column
            appendLine("<div class='ref-col'>")
            appendLine("<div class='ref-label'>Compose</div>")
            appendLine("<img class='thumb' src='${escapeHtml(result.composePath)}' alt='${escapeHtml(result.name)} compose' onclick='openModal(this.src)'>")
            appendLine("</div>")

            // Modes column
            appendLine("<div class='modes-col'>")

            // Vector section
            val vStatusClass = "${result.vectorStatus.cssClass}-text"
            appendLine("<div class='mode-section'>")
            appendLine("<div class='mode-label'>Vector</div>")
            appendLine("<div class='mode-content'>")
            appendLine("<div class='mode-images'>")
            appendLine("<div class='img-group'><div class='img-label'>Rendered</div><img class='thumb' src='${escapeHtml(result.vectorPath)}' onclick='openModal(this.src)'>")
            if (result.vectorPdfPath.isNotEmpty()) {
                appendLine("<a href='${escapeHtml(result.vectorPdfPath)}' class='pdf-link' target='_blank'>Open PDF</a>")
            }
            appendLine("</div>")
            appendLine("<div class='img-group'><div class='img-label'>Diff</div><img class='thumb' src='${escapeHtml(result.vectorDiffPath)}' onclick='openModal(this.src)'></div>")
            appendLine("</div>")
            appendLine("<div class='mode-metrics'>")
            appendLine("<div class='metric-row'><span class='metric-label'>RMSE</span><span class='metric-value'>${"%.4f".format(result.vectorRmse)}</span></div>")
            appendLine("<div class='metric-row'><span class='metric-label'>SSIM</span><span class='metric-value'>${"%.4f".format(result.vectorSsim)}</span></div>")
            appendLine("<div class='metric-row'><span class='metric-label'>Match</span><span class='metric-value'>${"%.2f".format(result.vectorExactMatch * 100)}%</span></div>")
            appendLine("<div class='metric-row'><span class='metric-label'>MaxErr</span><span class='metric-value'>${"%.4f".format(result.vectorMaxError)}</span></div>")
            appendLine("<div class='metric-row'><span class='metric-label'>Status</span><span class='metric-value $vStatusClass'>${result.vectorStatus.label}</span></div>")
            appendLine("</div>")
            appendLine("</div></div>")

            // Raster section
            val rStatusClass = "${result.rasterStatus.cssClass}-text"
            appendLine("<div class='mode-section'>")
            appendLine("<div class='mode-label'>Raster</div>")
            appendLine("<div class='mode-content'>")
            appendLine("<div class='mode-images'>")
            appendLine("<div class='img-group'><div class='img-label'>Rendered</div><img class='thumb' src='${escapeHtml(result.rasterPath)}' onclick='openModal(this.src)'>")
            if (result.rasterPdfPath.isNotEmpty()) {
                appendLine("<a href='${escapeHtml(result.rasterPdfPath)}' class='pdf-link' target='_blank'>Open PDF</a>")
            }
            appendLine("</div>")
            appendLine("<div class='img-group'><div class='img-label'>Diff</div><img class='thumb' src='${escapeHtml(result.rasterDiffPath)}' onclick='openModal(this.src)'></div>")
            appendLine("</div>")
            appendLine("<div class='mode-metrics'>")
            appendLine("<div class='metric-row'><span class='metric-label'>RMSE</span><span class='metric-value'>${"%.4f".format(result.rasterRmse)}</span></div>")
            appendLine("<div class='metric-row'><span class='metric-label'>SSIM</span><span class='metric-value'>${"%.4f".format(result.rasterSsim)}</span></div>")
            appendLine("<div class='metric-row'><span class='metric-label'>Match</span><span class='metric-value'>${"%.2f".format(result.rasterExactMatch * 100)}%</span></div>")
            appendLine("<div class='metric-row'><span class='metric-label'>MaxErr</span><span class='metric-value'>${"%.4f".format(result.rasterMaxError)}</span></div>")
            appendLine("<div class='metric-row'><span class='metric-label'>Status</span><span class='metric-value $rStatusClass'>${result.rasterStatus.label}</span></div>")
            appendLine("</div>")
            appendLine("</div></div>")

            // Android section (if available)
            if (result.hasAndroid) {
                val aStatusClass = "${result.androidStatus.cssClass}-text"
                appendLine("<div class='mode-section'>")
                appendLine("<div class='mode-label'>Android</div>")
                appendLine("<div class='mode-content'>")
                appendLine("<div class='mode-images'>")
                appendLine("<div class='img-group'><div class='img-label'>Rendered</div><img class='thumb' src='${escapeHtml(result.androidPath)}' onclick='openModal(this.src)'>")
                if (result.androidPdfPath.isNotEmpty()) {
                    appendLine("<a href='${escapeHtml(result.androidPdfPath)}' class='pdf-link' target='_blank'>Open PDF</a>")
                }
                appendLine("</div>")
                appendLine("<div class='img-group'><div class='img-label'>Diff</div><img class='thumb' src='${escapeHtml(result.androidDiffPath)}' onclick='openModal(this.src)'></div>")
                appendLine("</div>")
                appendLine("<div class='mode-metrics'>")
                appendLine("<div class='metric-row'><span class='metric-label'>RMSE</span><span class='metric-value'>${"%.4f".format(result.androidRmse)}</span></div>")
                appendLine("<div class='metric-row'><span class='metric-label'>SSIM</span><span class='metric-value'>${"%.4f".format(result.androidSsim)}</span></div>")
                appendLine("<div class='metric-row'><span class='metric-label'>Match</span><span class='metric-value'>${"%.2f".format(result.androidExactMatch * 100)}%</span></div>")
                appendLine("<div class='metric-row'><span class='metric-label'>MaxErr</span><span class='metric-value'>${"%.4f".format(result.androidMaxError)}</span></div>")
                appendLine("<div class='metric-row'><span class='metric-label'>Status</span><span class='metric-value $aStatusClass'>${result.androidStatus.label}</span></div>")
                appendLine("</div>")
                appendLine("</div></div>")
            }

            appendLine("</div>") // modes-col
            appendLine("</div>") // card-body
            appendLine("</div>") // fixture-card
        }

        appendLine("</div>") // cards-container

        // Modal
        appendLine("<div id='modal' class='modal' onclick='closeModal()'><img id='modal-img' src=''></div>")

        // JavaScript
        appendLine("<script>")
        appendLine(
            """
let activeCategory = 'all';
let activeStatus = 'all';
function filterCards() {
    document.querySelectorAll('.fixture-card').forEach(function(card) {
        var cat = card.getAttribute('data-category');
        var status = card.getAttribute('data-status');
        var catMatch = activeCategory === 'all' || cat === activeCategory;
        var statusMatch = activeStatus === 'all' || status === activeStatus;
        card.style.display = (catMatch && statusMatch) ? '' : 'none';
    });
}
function setCategory(cat, btn) {
    activeCategory = cat;
    btn.parentElement.querySelectorAll('.filter-btn').forEach(function(b) { b.classList.remove('active'); });
    btn.classList.add('active');
    filterCards();
}
function setStatus(status, btn) {
    activeStatus = status;
    btn.parentElement.querySelectorAll('.filter-btn').forEach(function(b) { b.classList.remove('active'); });
    btn.classList.add('active');
    filterCards();
}
function sortCards(key) {
    var container = document.getElementById('cards-container');
    var cards = Array.from(container.children);
    cards.sort(function(a, b) {
        if (key === 'name') return a.getAttribute('data-name').localeCompare(b.getAttribute('data-name'));
        if (key === 'worst') return parseFloat(b.getAttribute('data-worst-metric')) - parseFloat(a.getAttribute('data-worst-metric'));
        return 0;
    });
    cards.forEach(function(card) { container.appendChild(card); });
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
