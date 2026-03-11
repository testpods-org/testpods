# scripts/release/bump_versions.py
"""Bump version strings in plugin.json and pyproject.toml."""
# /// script
# requires-python = ">=3.10"
# ///

import json
import re
import sys
from pathlib import Path

SEMVER_RE = re.compile(r"^\d+\.\d+\.\d+$")


def validate_semver(version: str) -> str:
    """Validate and return a bare semver string (no v-prefix, no pre-release)."""
    if not SEMVER_RE.match(version):
        raise ValueError(
            f"Invalid semver: {version!r}. Expected format: MAJOR.MINOR.PATCH"
        )
    return version


def bump_plugin_json(path: str, version: str) -> None:
    """Update the version field in a plugin.json file."""
    p = Path(path)
    data = json.loads(p.read_text())
    data["version"] = version
    p.write_text(json.dumps(data, indent=2) + "\n")


def bump_pyproject_toml(path: str, version: str) -> None:
    """Update the version field in a pyproject.toml file (regex-based, no toml dep)."""
    p = Path(path)
    text = p.read_text()
    new_text = re.sub(
        r'^version\s*=\s*"[^"]*"',
        f'version = "{version}"',
        text,
        count=1,
        flags=re.MULTILINE,
    )
    if new_text == text:
        raise ValueError(f"Could not find version field in {path}")
    p.write_text(new_text)


def main() -> None:
    if len(sys.argv) != 2:
        print("Usage: bump_versions.py <version>", file=sys.stderr)
        sys.exit(1)

    version = validate_semver(sys.argv[1])
    repo_root = Path(__file__).resolve().parent.parent.parent

    plugin_json = repo_root / "plugin" / ".claude-plugin" / "plugin.json"
    pyproject = repo_root / "pyproject.toml"

    bump_plugin_json(str(plugin_json), version)
    bump_pyproject_toml(str(pyproject), version)

    print(f"Bumped to {version}:")
    print(f"  {plugin_json.relative_to(repo_root)}")
    print(f"  {pyproject.relative_to(repo_root)}")


if __name__ == "__main__":
    main()
