#!/usr/bin/env python3
"""Check the release source of truth and prevent stale 'current version' claims.

Historical version headings are intentionally allowed. Only the two live-status
fields in README.md and AGENTS.md must point to the Gradle source of truth.
"""
from pathlib import Path
import re
import sys

REQUIRED_HEADINGS = ("## 新增", "## 修复", "## 验证", "## 已知限制")


def inspect(root: Path) -> list[str]:
    errors = []
    gradle_path = root / "app/build.gradle.kts"
    if not gradle_path.is_file():
        return ["Missing app/build.gradle.kts"]
    gradle = gradle_path.read_text(encoding="utf-8")
    name_match = re.search(r'^\s*versionName\s*=\s*"(\d+\.\d+\.\d+)"\s*$', gradle, re.M)
    code_match = re.search(r'^\s*versionCode\s*=\s*([1-9]\d*)\s*$', gradle, re.M)
    if not name_match or not code_match:
        return ["Missing or invalid versionName/versionCode in app/build.gradle.kts"]

    name = name_match.group(1)
    release_notes = root / f"docs/releases/v{name}.md"
    if not release_notes.is_file():
        errors.append(f"Missing release notes for Gradle version: {release_notes.relative_to(root)}")
    else:
        notes = release_notes.read_text(encoding="utf-8")
        for heading in REQUIRED_HEADINGS:
            if not re.search(r"^" + re.escape(heading) + r"\s*$", notes, re.M):
                errors.append(f"Release notes v{name} lack {heading}")

    for file_name, field in (("AGENTS.md", "当前开发版本"), ("README.md", "版本")):
        path = root / file_name
        if not path.is_file():
            errors.append(f"Missing {file_name}")
            continue
        content = path.read_text(encoding="utf-8")
        lines = re.findall(r"^\s*-\s*" + re.escape(field) + r"[：:]\s*(.*)$", content, re.M)
        if len(lines) != 1:
            errors.append(f"{file_name}: expected exactly one live {field} field")
            continue
        live = lines[0]
        if re.search(r"(?<!\d)\d+\.\d+\.\d+(?!\d)", live):
            errors.append(f"{file_name}: hardcoded current version can become stale")
        if "app/build.gradle.kts" not in live or "Releases" not in live:
            errors.append(f"{file_name}: live version must link to Gradle and GitHub Releases")
    return errors


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    errors = inspect(root)
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    if errors:
        return 1
    print("Release baseline documentation: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
