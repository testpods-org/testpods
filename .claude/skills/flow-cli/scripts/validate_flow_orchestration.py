#!/usr/bin/env python3
"""Validate flow orchestration scripts for external projects.

This script validates that flow orchestration scripts:
1. Start with the required UV inline metadata header (PEP 723)
2. Have valid Python syntax
3. Accept a --dry-run flag
4. Run successfully with --dry-run

Scripts created for external projects MUST have UV metadata to declare
the flow-cli dependency. Scripts within the flow-cli repo work without
it because they run within the repo context.

Usage:
    python validate_flow_orchestration.py path/to/script.py
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

# Required header block that must appear at the start of orchestration scripts
REQUIRED_HEADER = '''#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = [
#     "flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli",
# ]
# ///
'''


def check_uv_metadata(script_path: Path) -> str | None:
    """Check script starts with exact required header.

    Args:
        script_path: Path to the script to check.

    Returns:
        Error message if check fails, None if OK.
    """
    content = script_path.read_text(encoding="utf-8")
    if not content.startswith(REQUIRED_HEADER):
        return (
            "Script must start with the required UV inline metadata header:\n\n"
            f"{REQUIRED_HEADER}"
        )
    return None


def check_compiles(script_path: Path) -> str | None:
    """Check script has valid Python syntax.

    Args:
        script_path: Path to the script to check.

    Returns:
        Error message if check fails, None if OK.
    """
    content = script_path.read_text(encoding="utf-8")
    try:
        compile(content, str(script_path), "exec")
    except SyntaxError as e:
        return f"Syntax error at line {e.lineno}: {e.msg}"
    return None


def check_imports(script_path: Path) -> str | None:
    """Try importing the script as a module to verify imports work.

    This catches issues like:
    - Missing imports (e.g., `from flow.nonexistent import foo`)
    - Import-time errors

    Note: This only catches errors at import time. Runtime errors like
    `flow.nonexistent_method()` inside functions won't be caught here
    because that code isn't executed during import. Use --dry-run execution
    and type checking (mypy/pyright) to catch those.

    Args:
        script_path: Path to the script to check.

    Returns:
        Error message if check fails, None if OK.
    """
    import importlib.util

    # Create a temporary module spec
    spec = importlib.util.spec_from_file_location("_validate_temp", script_path)
    if spec is None or spec.loader is None:
        return "Could not create module spec"

    module = importlib.util.module_from_spec(spec)

    # Add to sys.modules temporarily so relative imports work
    sys.modules["_validate_temp"] = module

    try:
        spec.loader.exec_module(module)
    except AttributeError as e:
        return f"AttributeError during import: {e}"
    except ImportError as e:
        return f"ImportError: {e}"
    except Exception:
        # Other errors might be expected (e.g., missing CLI args)
        # Only report attribute/import errors
        pass
    finally:
        sys.modules.pop("_validate_temp", None)

    return None


def check_dry_run_flag(script_path: Path) -> str | None:
    """Check that script accepts --dry-run flag.

    Args:
        script_path: Path to the script to check.

    Returns:
        Error message if check fails, None if OK.
    """
    content = script_path.read_text(encoding="utf-8")
    # Check for common patterns of --dry-run flag definition
    patterns = [
        '"--dry-run"',
        "'--dry-run'",
        "add_argument('--dry-run",
        'add_argument("--dry-run',
    ]
    for pattern in patterns:
        if pattern in content:
            return None
    return (
        "Script must accept a --dry-run flag.\n"
        "Add: parser.add_argument('--dry-run', action='store_true', help='...')"
    )


def check_dry_run_execution(script_path: Path) -> str | None:
    """Run the script with --dry-run and verify it succeeds.

    Args:
        script_path: Path to the script to check.

    Returns:
        Error message if check fails, None if OK.
    """
    try:
        result = subprocess.run(
            [sys.executable, str(script_path), "--dry-run"],
            capture_output=True,
            text=True,
            timeout=30,
        )
        if result.returncode != 0:
            error_detail = result.stderr.strip() or result.stdout.strip()
            return f"Script exited with code {result.returncode}\n{error_detail}"
    except subprocess.TimeoutExpired:
        return "Script timed out after 30 seconds"
    except FileNotFoundError:
        return "Could not execute script"
    return None


def main() -> int:
    """Run validation checks on an orchestration script.

    Returns:
        0 if all checks pass, 1 if any fail.
    """
    parser = argparse.ArgumentParser(
        description="Validate flow orchestration scripts for external projects."
    )
    parser.add_argument(
        "script_path",
        type=Path,
        help="Path to the orchestration script to validate",
    )
    args = parser.parse_args()

    script_path: Path = args.script_path

    if not script_path.exists():
        print(f"Error: File not found: {script_path}")
        return 1

    if not script_path.is_file():
        print(f"Error: Not a file: {script_path}")
        return 1

    print(f"Validating: {script_path}\n")

    # Track results
    checks = [
        ("UV metadata check", check_uv_metadata),
        ("Compilation check", check_compiles),
        ("Import validation", check_imports),
        ("--dry-run flag check", check_dry_run_flag),
        ("Dry-run execution", check_dry_run_execution),
    ]

    failed_count = 0
    skip_remaining = False

    for i, (check_name, check_func) in enumerate(checks, 1):
        print(f"[{i}/{len(checks)}] {check_name}...", end=" ")

        if skip_remaining:
            print("SKIPPED")
            continue

        error = check_func(script_path)
        if error:
            print("FAILED")
            # Indent error message
            for line in error.split("\n"):
                print(f"  - {line}")
            failed_count += 1
            # Skip remaining checks if UV metadata or compilation fails
            if i <= 2:
                skip_remaining = True
        else:
            print("PASSED")

    print()
    if failed_count > 0:
        print(f"FAILED: {failed_count} check(s) failed")
        return 1
    else:
        print("SUCCESS: All checks passed")
        return 0


if __name__ == "__main__":
    sys.exit(main())
