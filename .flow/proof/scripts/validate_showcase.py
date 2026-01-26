#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = ["pyyaml", "pydantic"]
# ///
"""Validate a ShowcaseSpec from a spec plan markdown file.

This script extracts the "## Showcase Requirements" section from a markdown
spec plan, parses the YAML code block, and validates it against ShowcaseSpec.

Exit codes:
    0 - Valid showcase specification
    1 - Invalid specification (validation errors)
    2 - Parse error (missing section, no YAML block, invalid YAML)

Usage:
    uv run proof/scripts/validate_showcase.py <spec-plan-path>

Example:
    uv run proof/scripts/validate_showcase.py specs/my-feature.md
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

# Add project root to sys.path for proof.types import
project_root = Path(__file__).parent.parent.parent
sys.path.insert(0, str(project_root))

import yaml  # noqa: E402
from pydantic import ValidationError  # noqa: E402

from proof.types import ShowcaseSpec  # noqa: E402

# Exit codes
EXIT_VALID = 0
EXIT_INVALID = 1
EXIT_PARSE_ERROR = 2


def extract_showcase_section(content: str) -> str | None:
    """Extract the ## Showcase Requirements section from markdown content.

    Args:
        content: Full markdown content.

    Returns:
        The section content (from header to next h2 or end), or None if not found.
    """
    # Find the ## Showcase Requirements header
    pattern = r"^## Showcase Requirements\s*$"
    match = re.search(pattern, content, re.MULTILINE)
    if not match:
        return None

    # Find the start of the section content
    section_start = match.end()

    # Find the next h2 header (or end of content)
    next_header = re.search(r"^## ", content[section_start:], re.MULTILINE)
    if next_header:
        section_end = section_start + next_header.start()
    else:
        section_end = len(content)

    return content[section_start:section_end]


def extract_yaml_block(section: str) -> str | None:
    """Extract a YAML code block from a section.

    Args:
        section: Markdown section content.

    Returns:
        The YAML content inside the code block, or None if not found.
    """
    # Match ```yaml or ```yml code blocks
    pattern = r"```(?:yaml|yml)\s*\n(.*?)```"
    match = re.search(pattern, section, re.DOTALL)
    if not match:
        return None
    return match.group(1).strip()


def validate_semantic(spec: ShowcaseSpec) -> list[str]:
    """Perform semantic validations beyond Pydantic structural validation.

    Args:
        spec: A valid ShowcaseSpec.

    Returns:
        List of error messages (empty if valid).
    """
    errors: list[str] = []

    # Check for duplicate demo names
    demo_names: list[str] = []
    for demo in spec.cli_demos:
        if demo.name in demo_names:
            errors.append(f"Duplicate demo name: '{demo.name}'")
        demo_names.append(demo.name)

    # Check for empty commands in demos
    for demo in spec.cli_demos:
        if not demo.commands:
            errors.append(f"Demo '{demo.name}' has no commands")

    # Check for duplicate page names across all web captures
    page_names: list[str] = []
    for capture in spec.web_captures:
        for page in capture.pages:
            if page.name in page_names:
                errors.append(f"Duplicate page name: '{page.name}'")
            page_names.append(page.name)

    return errors


def validate_showcase(spec_plan_path: Path) -> tuple[int, str]:
    """Validate a showcase specification from a spec plan file.

    Args:
        spec_plan_path: Path to the markdown spec plan file.

    Returns:
        Tuple of (exit_code, message).
    """
    # Check file exists
    if not spec_plan_path.exists():
        return EXIT_PARSE_ERROR, f"File not found: {spec_plan_path}"

    # Read content
    try:
        content = spec_plan_path.read_text(encoding="utf-8")
    except OSError as e:
        return EXIT_PARSE_ERROR, f"Failed to read file: {e}"

    # Extract showcase section
    section = extract_showcase_section(content)
    if section is None:
        return EXIT_PARSE_ERROR, "No '## Showcase Requirements' section found"

    # Extract YAML block
    yaml_content = extract_yaml_block(section)
    if yaml_content is None:
        return EXIT_PARSE_ERROR, "No YAML code block found in Showcase Requirements section"

    if not yaml_content.strip():
        return EXIT_PARSE_ERROR, "YAML code block is empty"

    # Parse YAML
    try:
        data = yaml.safe_load(yaml_content)
    except yaml.YAMLError as e:
        return EXIT_PARSE_ERROR, f"Invalid YAML: {e}"

    if data is None:
        return EXIT_PARSE_ERROR, "YAML parsed to None (empty or only comments)"

    # Validate against ShowcaseSpec
    try:
        spec = ShowcaseSpec.model_validate(data)
    except ValidationError as e:
        error_messages = []
        for error in e.errors():
            loc = ".".join(str(x) for x in error["loc"])
            msg = error["msg"]
            error_messages.append(f"  - {loc}: {msg}")
        return EXIT_INVALID, "Validation errors:\n" + "\n".join(error_messages)

    # Semantic validation
    semantic_errors = validate_semantic(spec)
    if semantic_errors:
        return EXIT_INVALID, "Semantic errors:\n" + "\n".join(f"  - {e}" for e in semantic_errors)

    return EXIT_VALID, "Showcase specification is valid"


def main() -> None:
    """Main entry point."""
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <spec-plan-path>", file=sys.stderr)
        sys.exit(EXIT_PARSE_ERROR)

    spec_plan_path = Path(sys.argv[1])
    exit_code, message = validate_showcase(spec_plan_path)

    if exit_code == EXIT_VALID:
        print(message)
    else:
        print(message, file=sys.stderr)

    sys.exit(exit_code)


if __name__ == "__main__":
    main()
