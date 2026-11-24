# Copyright 2026 The Backrow contributors
# SPDX-License-Identifier: Apache-2.0
"""Prints the CHANGELOG.md section for one version, as the release notes (ADR-0007).

Usage: release_notes.py <version> [CHANGELOG.md]

The section starts at a heading "## [<version>]" (a date may follow) and ends at the next
"## " heading. Exits 1 if there is no such section or it is empty: a release without notes in
the repository is not published.
"""

import pathlib
import re
import sys


def notes(version: str, changelog: str) -> str | None:
    heading = re.compile(r"^## \[" + re.escape(version) + r"\](?:\s|$)")
    lines, inside = [], False
    for line in changelog.splitlines():
        if line.startswith("## "):
            if inside:
                break
            inside = bool(heading.match(line))
            continue
        if inside:
            lines.append(line)
    text = "\n".join(lines).strip()
    return text or None


def main() -> int:
    version = sys.argv[1]
    path = pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else "CHANGELOG.md")
    text = notes(version, path.read_text(encoding="utf-8"))
    if text is None:
        print(f"CHANGELOG.md has no section '## [{version}]'", file=sys.stderr)
        return 1
    print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
