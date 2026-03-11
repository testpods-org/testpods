"""Generate a changelog entry from git history."""
# /// script
# requires-python = ">=3.10"
# ///

import re
import subprocess
import sys
from datetime import date
from pathlib import Path

# Conventional commit prefix -> changelog category
PREFIX_MAP = {
    "feat": "Added",
    "fix": "Fixed",
    "refactor": "Changed",
    "chore": "Changed",
    "docs": "Changed",
    "style": "Changed",
    "perf": "Changed",
    "test": "Changed",
    "ci": "Changed",
    "build": "Changed",
}

# Output order for categories
CATEGORY_ORDER = ["Added", "Fixed", "Changed", "Removed"]

# Matches: "feat(scope)!: message" or "feat!: message" or "feat: message"
CONVENTIONAL_RE = re.compile(r"^(\w+)(?:\([^)]*\))?!?:\s*(.+)$")

TAG_PREFIX = "testpods-agent/v"


def categorize_commit(message: str) -> tuple[str, str]:
    """Parse a commit message into (category, description)."""
    m = CONVENTIONAL_RE.match(message)
    if m:
        prefix, desc = m.group(1), m.group(2)
        category = PREFIX_MAP.get(prefix, "Changed")
        return category, desc
    return "Changed", message


def format_changelog_section(
    version: str, date_str: str, commits: list[tuple[str, str]]
) -> str:
    """Format categorized commits into a Keep-a-Changelog section."""
    by_category: dict[str, list[str]] = {}
    for category, desc in commits:
        by_category.setdefault(category, []).append(desc)

    lines = [f"## [{version}] - {date_str}", ""]
    for cat in CATEGORY_ORDER:
        if cat in by_category:
            lines.append(f"### {cat}")
            for desc in by_category[cat]:
                lines.append(f"- {desc}")
            lines.append("")

    return "\n".join(lines)


def insert_into_changelog(changelog_path: str, section: str) -> None:
    """Insert a new version section into CHANGELOG.md after [Unreleased]."""
    p = Path(changelog_path)
    text = p.read_text()

    # Find the [Unreleased] header and clear everything between it and the next ## or ---
    pattern = r"(## \[Unreleased\])\n.*?(?=\n## |\n---|\Z)"
    replacement = rf"\1\n\n{section}"
    new_text = re.sub(pattern, replacement, text, count=1, flags=re.DOTALL)

    if new_text == text:
        raise ValueError("Could not find ## [Unreleased] section in changelog")

    p.write_text(new_text)


def get_last_tag() -> str | None:
    """Get the most recent testpods-agent version tag, or None if no tags exist."""
    result = subprocess.run(
        ["git", "tag", "-l", f"{TAG_PREFIX}*", "--sort=-v:refname"],
        capture_output=True,
        text=True,
    )
    tags = result.stdout.strip().splitlines()
    return tags[0] if tags else None


def get_commits_since(tag: str | None) -> list[str]:
    """Get oneline commit messages since a tag (or all commits if None)."""
    if tag:
        cmd = ["git", "log", "--oneline", "--no-decorate", f"{tag}..HEAD"]
    else:
        cmd = ["git", "log", "--oneline", "--no-decorate"]
    result = subprocess.run(cmd, capture_output=True, text=True)
    lines = result.stdout.strip().splitlines()
    # Strip the short SHA prefix from each line
    return [line.split(" ", 1)[1] if " " in line else line for line in lines if line]


def main() -> None:
    if len(sys.argv) < 2:
        print("Usage: generate_changelog.py <version> [--dry-run]", file=sys.stderr)
        sys.exit(1)

    version = sys.argv[1]
    dry_run = "--dry-run" in sys.argv

    last_tag = get_last_tag()
    commits_raw = get_commits_since(last_tag)

    if not commits_raw:
        print("No commits found since last tag.", file=sys.stderr)
        sys.exit(1)

    commits = [categorize_commit(msg) for msg in commits_raw]
    section = format_changelog_section(version, date.today().isoformat(), commits)

    if dry_run:
        print(section)
    else:
        repo_root = Path(__file__).resolve().parent.parent.parent
        changelog = repo_root / "CHANGELOG.md"
        insert_into_changelog(str(changelog), section)
        print(f"Updated {changelog.relative_to(repo_root)} with {version} entry")
        since = last_tag or "initial commit"
        print(f"  {len(commits_raw)} commits since {since}")


if __name__ == "__main__":
    main()
