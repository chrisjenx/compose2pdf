#!/usr/bin/env python3
"""Regenerate the Compose Multiplatform compatibility tables in docs/compatibility.md
and README.md from .github/compose-versions.json — the single source of truth.

Run by the update-compose-versions workflow after it rewrites the JSON; also safe to
run locally (`python3 .github/scripts/render-compat-tables.py`). The generated table
replaces whatever sits between the `<!-- BEGIN cmp-matrix -->` / `<!-- END cmp-matrix -->`
markers in each target file. Idempotent: no marker content change -> no file change.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
JSON = ROOT / ".github" / "compose-versions.json"
LIBS = ROOT / "gradle" / "libs.versions.toml"
TARGETS = [ROOT / "docs" / "compatibility.md", ROOT / "README.md"]
BEGIN, END = "<!-- BEGIN cmp-matrix -->", "<!-- END cmp-matrix -->"

_PRERELEASE_RANK = {"dev": 0, "alpha": 1, "beta": 2, "rc": 3}


def version_key(v):
    """Sort key: (major, minor, patch, prerelease-rank, prerelease-num).
    A stable release outranks any prerelease of the same major.minor.patch."""
    base, _, pre = v.partition("-")
    parts = [int(x) if x.isdigit() else 0 for x in base.split(".")]
    parts += [0] * (3 - len(parts))
    if pre:
        m = re.match(r"([a-z]+)(\d*)", pre)
        rank = _PRERELEASE_RANK.get(m.group(1), 0) if m else 0
        num = int(m.group(2)) if (m and m.group(2)) else 0
    else:
        rank, num = 9, 0  # stable > any prerelease
    return (parts[0], parts[1], parts[2], rank, num)


def pinned_compose():
    m = re.search(r'^compose-multiplatform\s*=\s*"([^"]+)"', LIBS.read_text(), re.M)
    return m.group(1) if m else None


def build_table():
    versions = json.loads(JSON.read_text())["versions"]
    versions = sorted(versions, key=lambda e: version_key(e["compose-version"]), reverse=True)
    pinned = pinned_compose()
    rows = [
        "| Compose Multiplatform | Kotlin | Status |",
        "|:----------------------|:-------|:-------|",
    ]
    for e in versions:
        cv, kv = e["compose-version"], e["kotlin-version"]
        if cv == pinned:
            rows.append(f"| **{cv}** | {kv} | CI tested (current) |")
        else:
            rows.append(f"| {cv} | {kv} | CI tested |")
    return "\n".join(rows)


def main():
    block = f"{BEGIN}\n{build_table()}\n{END}"
    pattern = re.compile(re.escape(BEGIN) + ".*?" + re.escape(END), re.S)
    changed = []
    for t in TARGETS:
        text = t.read_text()
        if BEGIN not in text or END not in text:
            sys.exit(f"ERROR: markers not found in {t.relative_to(ROOT)}")
        new = pattern.sub(lambda _: block, text)
        if new != text:
            t.write_text(new)
            changed.append(str(t.relative_to(ROOT)))
    print("compat tables updated:", ", ".join(changed) if changed else "none (already current)")


if __name__ == "__main__":
    main()
