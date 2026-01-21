"""Utilities for validation output processing and running."""

import logging
import re
import subprocess

from flow.lib.logging_setup import log_and_print


def truncate_validation_output(
    output: str,
    max_failures_shown: int = 20,
    max_chars: int = 12000,
) -> str:
    """Truncate validation output to fit in agent context.

    Preserves:
    - Section headers (=== Running: ... ===)
    - ALL linter errors (high value, usually few)
    - First N test failures
    - Final summary line

    Truncates:
    - Long test failure lists beyond max_failures_shown
    - Detailed stack traces in short summary section

    Args:
        output: Raw validation output from scripts/validate.py
        max_failures_shown: Maximum number of FAILED test lines to keep
        max_chars: Maximum total characters (safety limit)

    Returns:
        Truncated output safe for agent context
    """
    if not output:
        return output

    # Split into sections by the separator line
    # Format: ============================================================
    section_separator = re.compile(r"^={50,}$", re.MULTILINE)

    # Find pytest section and truncate it
    lines = output.split("\n")
    result_lines: list[str] = []
    in_pytest_section = False
    failure_count = 0
    total_failures = 0
    skipped_failures = 0

    # First pass: count total failures
    for line in lines:
        if "FAILED" in line and "::" in line:  # Test failure line format
            total_failures += 1

    # Second pass: build truncated output
    i = 0
    while i < len(lines):
        line = lines[i]

        # Detect section headers
        if section_separator.match(line):
            # Check if next line is "Running: Pytest"
            if i + 1 < len(lines) and "Running: Pytest" in lines[i + 1]:
                in_pytest_section = True
            elif i + 1 < len(lines) and "Running:" in lines[i + 1]:
                in_pytest_section = False

            result_lines.append(line)
            i += 1
            continue

        if in_pytest_section:
            # Check if this is a FAILED test line
            if "FAILED" in line and "::" in line:
                failure_count += 1
                if failure_count <= max_failures_shown:
                    result_lines.append(line)
                elif failure_count == max_failures_shown + 1:
                    # Insert truncation notice
                    skipped_failures = total_failures - max_failures_shown
                    result_lines.append("")
                    result_lines.append(f"[... {skipped_failures} more FAILED tests not shown - fix the above first ...]")
                    result_lines.append("")
                # else: skip this failure line
            elif _is_summary_line(line):
                # Always keep summary lines (e.g., "78 failed, 1049 passed")
                result_lines.append(line)
            elif failure_count <= max_failures_shown:
                # Keep non-failure lines only if we haven't started truncating
                result_lines.append(line)
            elif _is_section_boundary(line, lines, i):
                # Keep section boundaries even when truncating
                result_lines.append(line)
        else:
            # Non-pytest sections: keep everything (linter errors are valuable)
            result_lines.append(line)

        i += 1

    result = "\n".join(result_lines)

    # Final safety limit
    if len(result) > max_chars:
        result = result[:max_chars] + "\n\n[... output truncated ...]"

    return result


def _is_summary_line(line: str) -> bool:
    """Check if line is a pytest summary (e.g., '=== 78 failed, 1049 passed ===')."""
    # Matches lines like: "======= 78 failed, 1049 passed in 45.2s ======="
    # Or: "FAILED tests/..." short summary lines
    if re.match(r"^=+\s*\d+\s+(failed|passed)", line):
        return True
    if re.match(r"^=+\s*(short test summary|FAILURES)", line, re.IGNORECASE):
        return True
    # Final summary line
    if "passed" in line and ("failed" in line or "error" in line) and "in " in line:
        return True
    return False


def _is_section_boundary(line: str, lines: list[str], index: int) -> bool:
    """Check if line marks a section boundary."""
    # Section separator
    if re.match(r"^={50,}$", line):
        return True
    # Status lines like "Pytest Tests: FAIL ✗"
    if re.match(r"^[\w\s]+:\s*(PASS|FAIL)", line):
        return True
    return False


def run_validation(
    logger: logging.Logger,
    is_final_step: bool = False,
    auto_format: bool = True,
    skip_tests: bool | None = None,
) -> tuple[bool, str]:
    """Run validation script and return (passed, output).

    Runs scripts/validate.py which executes linting, type checking, and tests.
    Exit code 0 means all checks passed, non-zero means failures.

    By default, auto-formats code before validation to prevent unnecessary fix
    cycles when only formatting is wrong.

    For intermediate steps, only linting and type checking are run (tests skipped).
    This allows multi-step plans where tests may fail until all related changes
    are complete. Full test suite runs only on the final step.

    Args:
        logger: Logger for recording validation runs to flow.log
        is_final_step: If True, run all tests including slow tests. If False,
            skip tests entirely (lint + type check only). Ignored if skip_tests
            is explicitly set.
        auto_format: If True, run formatter before validation (--auto-format).
        skip_tests: Explicit control over test skipping. If None (default),
            uses is_final_step logic. If True, always skip tests. If False,
            always run tests.

    Returns:
        Tuple of (passed: bool, output: str) where output contains stdout+stderr
    """
    cmd = ["uv", "run", "scripts/validate.py", "--quiet-user"]

    if auto_format:
        cmd.append("--auto-format")

    # Determine whether to skip tests
    should_skip_tests = skip_tests if skip_tests is not None else not is_final_step
    if should_skip_tests:
        cmd.append("--skip-tests")

    cmd_str = " ".join(cmd)
    log_and_print(logger, f"Running: {cmd_str}", print_prefix="    ")

    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
    )

    output = result.stdout + result.stderr
    passed = result.returncode == 0

    # Log validation result
    logger.info(f"Validation {'passed' if passed else 'failed'}")

    # Print validation output for visibility
    if output:
        print(output)

    return passed, output
