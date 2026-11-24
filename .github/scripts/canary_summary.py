# Copyright 2026 The Backrow contributors
# SPDX-License-Identifier: Apache-2.0
"""Turns the live canary's JUnit XML into a short Markdown summary (ADR-0007).

Usage: canary_summary.py <test-results-dir> <summary.md>

Writes the summary, which the canary posts to the youtube-breakage issue, and exits 1 if any
test failed, was skipped or none ran. A skipped live test in the canary means BACKROW_LIVE_TESTS
was not set, and a canary that checks nothing must not look green.

Only test names and failure messages go into the summary, never stdout or stack traces, and
anything URL-shaped is blanked out: stream URLs embed the runner's IP address.
"""

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

URL_LIKE = re.compile(r"(?:https?://|[\w.-]+\.googlevideo\.com)\S*")
MAX_MESSAGE = 500


def clean(text: str) -> str:
    text = URL_LIKE.sub("<redacted>", text or "").strip()
    text = text.replace("`", "'")
    return text[:MAX_MESSAGE] + ("…" if len(text) > MAX_MESSAGE else "")


def main() -> int:
    results_dir = pathlib.Path(sys.argv[1])
    out = pathlib.Path(sys.argv[2])
    passed, skipped, failed = [], [], []

    for path in sorted(results_dir.glob("TEST-*.xml")):
        for case in ET.parse(path).getroot().iter("testcase"):
            name = f"{case.get('classname', '').rsplit('.', 1)[-1]} > {case.get('name', '')}"
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is not None:
                failed.append((name, clean(problem.get("message") or problem.get("type") or "")))
            elif case.find("skipped") is not None:
                skipped.append(name)
            else:
                passed.append(name)

    lines = [f"**{len(passed)} passed, {len(failed)} failed, {len(skipped)} skipped**", ""]
    if not (passed or failed or skipped):
        lines.append("No test results: the build failed before the live tests ran. See the run log.")
    for name, message in failed:
        lines += [f"- ❌ `{clean(name)}`", f"  ```", f"  {message}", f"  ```"]
    for name in skipped:
        lines.append(f"- ⚠️ skipped: `{clean(name)}` (is BACKROW_LIVE_TESTS=1 set?)")
    for name in passed:
        lines.append(f"- ✅ `{clean(name)}`")
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")

    return 0 if passed and not failed and not skipped else 1


if __name__ == "__main__":
    sys.exit(main())
