# scripts/release/update_marketplace.py
"""Update the claude-plugins marketplace.json for a new release."""
# /// script
# requires-python = ">=3.10"
# ///

import json
import subprocess
import sys
from pathlib import Path


def update_marketplace_json(
    path: str, plugin_name: str, version: str
) -> None:
    """Update version and ref for a plugin in marketplace.json."""
    p = Path(path)
    data = json.loads(p.read_text())

    found = False
    for plugin in data.get("plugins", []):
        if plugin.get("name") == plugin_name:
            plugin["version"] = version
            if "source" in plugin:
                plugin["source"]["ref"] = f"testpods-agent/v{version}"
            found = True
            break

    if not found:
        raise ValueError(f"Plugin {plugin_name!r} not found in {path}")

    p.write_text(json.dumps(data, indent=2) + "\n")


def main() -> None:
    if len(sys.argv) != 2:
        print("Usage: update_marketplace.py <version>", file=sys.stderr)
        sys.exit(1)

    version = sys.argv[1]
    repo_root = Path(__file__).resolve().parent.parent.parent

    # Marketplace repo is at ../../java-expert-agent-project/claude-plugins
    marketplace_root = repo_root.parent.parent / "java-expert-agent-project" / "claude-plugins"
    marketplace_json = (
        marketplace_root / ".claude-plugin" / "marketplace.json"
    )

    if not marketplace_json.exists():
        print(
            f"Marketplace not found at {marketplace_json}",
            file=sys.stderr,
        )
        print(
            "Expected claude-plugins repo at ../../java-expert-agent-project/claude-plugins",
            file=sys.stderr,
        )
        sys.exit(1)

    update_marketplace_json(str(marketplace_json), "testpods-agent", version)

    # Commit in the marketplace repo
    subprocess.run(
        ["git", "add", ".claude-plugin/marketplace.json"],
        cwd=marketplace_root,
        check=True,
    )
    subprocess.run(
        ["git", "commit", "-m", f"release: testpods-agent v{version}"],
        cwd=marketplace_root,
        check=True,
    )

    print(f"Updated marketplace for testpods-agent v{version}")
    print(f"  {marketplace_json}")


if __name__ == "__main__":
    main()
