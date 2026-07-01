#!/usr/bin/env python3
"""Regenerate the Compose Multiplatform compatibility tables in docs/compatibility.md
and README.md from .github/compose-versions.json — the single source of truth.

Run by the update-compose-versions workflow after it rewrites the JSON, and by CI
with --check to fail a PR whose committed tables have drifted from the JSON (e.g. a
hand-edit, or a `compose-multiplatform` pin bump in gradle/libs.versions.toml).

The generated table replaces whatever sits between the first
`<!-- BEGIN cmp-matrix -->` / `<!-- END cmp-matrix -->` marker pair in each target.

Modes:
  (default)  rewrite the tables in place. Best-effort: a docs problem is warned about,
             not fatal, so it can never block the version-bump pipeline.
  --check    write nothing; exit non-zero if any table is out of date or its markers
             are broken. Intended for CI on pull requests.
"""
import argparse
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
JSON_PATH = ROOT / ".github" / "compose-versions.json"
LIBS = ROOT / "gradle" / "libs.versions.toml"
TARGETS = [ROOT / "docs" / "compatibility.md", ROOT / "README.md"]
BEGIN, END = "<!-- BEGIN cmp-matrix -->", "<!-- END cmp-matrix -->"
BLOCK_RE = re.compile(re.escape(BEGIN) + r".*?" + re.escape(END), re.S)

_PRERELEASE_RANK = {"dev": 0, "alpha": 1, "beta": 2, "rc": 3}


def _leading_ints(base):
    """First integer of each dot-separated segment (build metadata / junk -> 0)."""
    parts = [int(m.group()) if (m := re.match(r"[0-9]+", seg)) else 0 for seg in base.split(".")]
    return (parts + [0, 0, 0])[:3]


def version_key(v):
    """Sort key (major, minor, patch, prerelease-rank, prerelease-num); a stable
    release outranks any prerelease of the same major.minor.patch."""
    base, _, pre = str(v).partition("-")
    major, minor, patch = _leading_ints(base)
    pre = pre.lower()
    if pre:
        m = re.match(r"([a-z]+)\.?([0-9]*)", pre)
        rank = _PRERELEASE_RANK.get(m.group(1), 0) if m else 0
        num = int(m.group(2)) if (m and m.group(2)) else 0
    else:
        rank, num = 9, 0  # stable > any prerelease
    return (major, minor, patch, rank, num)


def _pins():
    """Read the pinned compose-multiplatform and kotlin versions from the version catalog (one read)."""
    text = LIBS.read_text()

    def pin(name):
        m = re.search(rf'^{re.escape(name)}\s*=\s*"([^"]+)"', text, re.M)
        return m.group(1) if m else None

    return pin("compose-multiplatform"), pin("kotlin")


def build_block():
    """Build the full marker block (raises ValueError on empty/malformed JSON)."""
    data = json.loads(JSON_PATH.read_text())
    versions = data.get("versions") if isinstance(data, dict) else None
    if not isinstance(versions, list) or not versions:
        raise ValueError(f"{JSON_PATH.name}: 'versions' is missing or empty")

    rows = []
    for e in versions:
        try:
            rows.append((e["compose-version"], e["kotlin-version"]))
        except (TypeError, KeyError) as ex:
            raise ValueError(f"{JSON_PATH.name}: malformed version entry {e!r}") from ex
    rows.sort(key=lambda r: version_key(r[0]), reverse=True)

    pinned_compose, pinned_kotlin = _pins()

    lines = [
        "| Compose Multiplatform | Kotlin | Status |",
        "|:----------------------|:-------|:-------|",
    ]
    pinned_shown = False
    for cv, kv in rows:
        if cv == pinned_compose:
            # The current row reflects the shipped pin, not the matrix's CI Kotlin override.
            lines.append(f"| **{cv}** | {pinned_kotlin or kv} | CI tested (current) |")
            pinned_shown = True
        else:
            lines.append(f"| {cv} | {kv} | CI tested |")
    table = "\n".join(lines)

    # Keep the shipped version visible even after it rolls out of the tested matrix.
    note = ""
    if pinned_compose and not pinned_shown:
        note = (
            f"\n\n_The published library pins **Compose {pinned_compose}** "
            f"(Kotlin {pinned_kotlin}), which is no longer in the tested matrix above._"
        )
    return f"{BEGIN}\n{table}{note}\n{END}"


def render(text, block, where):
    """Replace the first marker block; raises ValueError unless exactly one is present."""
    new, n = BLOCK_RE.subn(lambda _: block, text, count=1)
    if n != 1:
        raise ValueError(f"{where}: expected exactly one '{BEGIN} … {END}' block, found {n}")
    return new


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true",
                    help="write nothing; exit 1 if any table is stale or its markers are broken")
    args = ap.parse_args()

    try:
        block = build_block()
    except (ValueError, OSError, json.JSONDecodeError) as ex:
        if args.check:
            sys.exit(f"ERROR: {ex}")
        # Never block the version-bump pipeline on a docs problem.
        print(f"WARNING: skipping compat-table regeneration: {ex}", file=sys.stderr)
        return

    stale, updated, problems = [], [], []
    for t in TARGETS:
        rel = t.relative_to(ROOT)
        text = t.read_text()
        try:
            new = render(text, block, rel)
        except ValueError as ex:
            problems.append(str(ex))
            continue
        if new == text:
            continue
        if args.check:
            stale.append(str(rel))
        else:
            t.write_text(new)
            updated.append(str(rel))

    if args.check:
        for p in problems:
            print(f"ERROR: {p}", file=sys.stderr)
        if stale:
            print(f"Out of date: {', '.join(stale)}", file=sys.stderr)
        if problems or stale:
            sys.exit("Run: python3 .github/scripts/render-compat-tables.py")
        print("compat tables up to date")
        return

    for p in problems:
        print(f"WARNING: {p}", file=sys.stderr)
    print("compat tables updated:", ", ".join(updated) if updated else "none (already current)")


if __name__ == "__main__":
    main()
