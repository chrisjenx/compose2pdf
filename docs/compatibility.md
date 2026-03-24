---
title: Compatibility
nav_order: 3
---

# Compatibility

[![Compose Compatibility](https://github.com/chrisjenx/compose2pdf/actions/workflows/compatibility.yml/badge.svg)](https://github.com/chrisjenx/compose2pdf/actions/workflows/compatibility.yml)

compose2pdf is tested weekly against the 3 most recent Compose Multiplatform releases on macOS and Linux.

---

## Compose Multiplatform versions

| Compose Multiplatform | Kotlin | Status |
|:----------------------|:-------|:-------|
| 1.11.0-alpha04 | 2.3.20 | CI tested |
| **1.10.3** | 2.3.20 | CI tested (current) |
| 1.9.3 | 2.3.20 | CI tested |

{: .note }
This table is auto-updated weekly by CI. The compatibility workflow discovers the 3 most recent CMP releases and runs the full test suite against each.

---

## JDK versions

| JDK | Status |
|:----|:-------|
| **17** (Temurin) | CI tested |
| 21+ | Supported |

JDK 17 is the minimum — enforced by `jvmToolchain(17)` in the build. Later JDK versions are expected to work.

---

## Platform support

| Platform | Status | Notes |
|:---------|:-------|:------|
| **macOS** (arm64, x64) | CI tested | Full support |
| **Linux** (x64) | CI tested | Requires `xvfb-run` for headless Compose rendering |
| **Windows** (x64) | Supported | Not CI tested, but Compose Desktop and PDFBox both support Windows |

> compose2pdf targets **Compose Desktop** (JVM). Android, iOS, and Compose for Web are not supported.

---

## Dependencies

| Dependency | Version | Purpose |
|:-----------|:--------|:--------|
| Kotlin | 2.3.20 | Language |
| Compose Multiplatform | 1.9+ | UI framework |
| Apache PDFBox | 3.0.7 | PDF generation |
| Kotlinx Coroutines | 1.10.2 | Compose runtime dependency |

---

## How compatibility is tested

The [compatibility workflow](https://github.com/chrisjenx/compose2pdf/actions/workflows/compatibility.yml) runs on every push to `main`, every PR, and weekly on Monday at 9am UTC.

It dynamically loads Compose Multiplatform versions from [`.github/compose-versions.json`](https://github.com/chrisjenx/compose2pdf/blob/main/.github/compose-versions.json), overrides the library's pinned version, and runs the full build for each combination.

The [update-compose-versions workflow](https://github.com/chrisjenx/compose2pdf/actions/workflows/update-compose-versions.yml) automatically discovers new CMP releases weekly and updates the test matrix.

---

## See also

- [Getting Started]({{ site.baseurl }}/getting-started) -- Prerequisites and installation
- [Troubleshooting]({{ site.baseurl }}/guides/troubleshooting) -- Common issues and fixes
