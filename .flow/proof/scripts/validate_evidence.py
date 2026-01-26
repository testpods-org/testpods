#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = ["pydantic", "pyyaml"]
# ///
"""Validate an evidence.json file against the EvidenceReport schema.

This script parses evidence.json and validates it against the strict
Pydantic models in proof.schemas.evidence_schema.

Exit codes:
    0 - Valid evidence file
    1 - Invalid evidence (validation errors)
    2 - Parse error (file not found, invalid JSON)

Usage:
    ./proof/scripts/validate_evidence.py <evidence-path>
    cat evidence.json | ./proof/scripts/validate_evidence.py -

Example:
    ./proof/scripts/validate_evidence.py flows/my-flow/proof/evidence.json
    cat evidence.json | ./proof/scripts/validate_evidence.py -
"""

from __future__ import annotations

import json
import sys
from collections.abc import Mapping
from pathlib import Path
from typing import Any

# Add project root to sys.path for proof package imports
project_root = Path(__file__).parent.parent.parent
sys.path.insert(0, str(project_root))

from pydantic import ValidationError  # noqa: E402

from proof.schemas.evidence_schema import EvidenceReport  # noqa: E402

# Exit codes
EXIT_VALID = 0
EXIT_INVALID = 1
EXIT_PARSE_ERROR = 2


def validate_semantic(report: EvidenceReport) -> list[str]:
    """Perform semantic validations beyond Pydantic structural validation.

    Args:
        report: A valid EvidenceReport.

    Returns:
        List of error messages (empty if valid).
    """
    errors: list[str] = []

    # Check for duplicate demo names
    demo_names: list[str] = []
    for demo in report.cli_evidence:
        if demo.name in demo_names:
            errors.append(f"Duplicate demo name: '{demo.name}'")
        demo_names.append(demo.name)

    # Validate summary statistics consistency
    actual_total_demos = len(report.cli_evidence)
    if report.summary.total_demos != actual_total_demos:
        errors.append(f"Summary total_demos ({report.summary.total_demos}) doesn't match actual demo count ({actual_total_demos})")

    actual_passed_demos = sum(1 for demo in report.cli_evidence if demo.passed)
    if report.summary.passed_demos != actual_passed_demos:
        errors.append(f"Summary passed_demos ({report.summary.passed_demos}) doesn't match actual passed count ({actual_passed_demos})")

    actual_failed_demos = sum(1 for demo in report.cli_evidence if not demo.passed)
    if report.summary.failed_demos != actual_failed_demos:
        errors.append(f"Summary failed_demos ({report.summary.failed_demos}) doesn't match actual failed count ({actual_failed_demos})")

    # Validate command counts
    actual_total_commands = sum(len(demo.commands) for demo in report.cli_evidence)
    if report.summary.total_commands != actual_total_commands:
        errors.append(
            f"Summary total_commands ({report.summary.total_commands}) doesn't match actual command count ({actual_total_commands})"
        )

    # Count passed/failed commands based on demo.passed and pattern matching
    # This must be consistent with api.py's _command_passed() method
    actual_passed_commands = 0
    actual_failed_commands = 0
    for demo in report.cli_evidence:
        for cmd in demo.commands:
            # A command is considered passed if:
            # 1. The parent demo passed (demo.passed is True)
            # 2. AND no pattern was expected OR pattern_matched is True
            if not demo.passed:
                # If demo failed, all its commands are counted as failed
                actual_failed_commands += 1
            elif cmd.pattern_matched is False:
                actual_failed_commands += 1
            else:
                actual_passed_commands += 1

    if report.summary.passed_commands != actual_passed_commands:
        errors.append(
            f"Summary passed_commands ({report.summary.passed_commands}) doesn't match calculated passed count ({actual_passed_commands})"
        )

    if report.summary.failed_commands != actual_failed_commands:
        errors.append(
            f"Summary failed_commands ({report.summary.failed_commands}) doesn't match calculated failed count ({actual_failed_commands})"
        )

    # Validate demo passed/failed consistency with command results
    for demo in report.cli_evidence:
        has_failed_command = any(cmd.pattern_matched is False for cmd in demo.commands)
        if demo.passed and has_failed_command:
            errors.append(f"Demo '{demo.name}' is marked as passed but has failed commands")
        if not demo.passed and not has_failed_command and demo.failure_reason is None:
            errors.append(f"Demo '{demo.name}' is marked as failed but has no failed commands and no failure_reason")

    return errors


def validate_evidence_from_data(data: Mapping[str, Any]) -> tuple[int, str]:
    """Validate evidence data against EvidenceReport schema.

    Args:
        data: Parsed JSON data.

    Returns:
        Tuple of (exit_code, message).
    """
    # Validate against EvidenceReport
    try:
        report = EvidenceReport.model_validate(data)
    except ValidationError as e:
        error_messages = []
        for error in e.errors():
            loc = ".".join(str(x) for x in error["loc"])
            msg = error["msg"]
            error_messages.append(f"  - {loc}: {msg}")
        return EXIT_INVALID, "Validation errors:\n" + "\n".join(error_messages)

    # Semantic validation
    semantic_errors = validate_semantic(report)
    if semantic_errors:
        return EXIT_INVALID, "Semantic errors:\n" + "\n".join(f"  - {e}" for e in semantic_errors)

    return EXIT_VALID, "Evidence file is valid"


def validate_evidence(evidence_path: Path | None = None) -> tuple[int, str]:
    """Validate an evidence.json file or stdin.

    Args:
        evidence_path: Path to the evidence.json file, or None to read from stdin.

    Returns:
        Tuple of (exit_code, message).
    """
    # Read content
    try:
        if evidence_path is None:
            # Read from stdin
            content = sys.stdin.read()
        else:
            if not evidence_path.exists():
                return EXIT_PARSE_ERROR, f"File not found: {evidence_path}"
            content = evidence_path.read_text(encoding="utf-8")
    except OSError as e:
        return EXIT_PARSE_ERROR, f"Failed to read input: {e}"

    # Parse JSON
    try:
        data = json.loads(content)
    except json.JSONDecodeError as e:
        return EXIT_PARSE_ERROR, f"Invalid JSON: {e}"

    if not isinstance(data, dict):
        return EXIT_PARSE_ERROR, "JSON root must be an object"

    return validate_evidence_from_data(data)


def main() -> None:
    """Main entry point."""
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <evidence-path>", file=sys.stderr)
        print(f"       {sys.argv[0]} -  (read from stdin)", file=sys.stderr)
        sys.exit(EXIT_PARSE_ERROR)

    arg = sys.argv[1]
    if arg == "-":
        evidence_path = None
    else:
        evidence_path = Path(arg)

    exit_code, message = validate_evidence(evidence_path)

    if exit_code == EXIT_VALID:
        print(message)
    else:
        print(message, file=sys.stderr)

    sys.exit(exit_code)


if __name__ == "__main__":
    main()
