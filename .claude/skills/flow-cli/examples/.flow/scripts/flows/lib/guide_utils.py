"""Review guide generation utilities for orchestration scripts.

This module provides functionality to generate review guides after flow completion,
including both static template content and agent-enriched testing guidance.
"""

from __future__ import annotations

import logging
from datetime import UTC, datetime
from pathlib import Path

from flow import AgentInfo, Flow
from flow.lib.logging_setup import log_and_print
from flow.types import ExitReason

try:
    # Try relative import first (when used as a package)
    from .git_utils import format_commits, get_flow_commits
except ImportError:
    # Fall back to direct import (when lib dir is in sys.path)
    from git_utils import format_commits, get_flow_commits  # type: ignore[import-not-found,no-redef]


def generate_review_guide(
    flow: Flow,
    plan_path: Path,
    logger: logging.Logger,
    retries: int = 3,
) -> Path | None:
    """Generate a complete review guide with template and agent enrichment.

    Creates the base review guide template and then enriches it with
    intelligent testing/review guidance from an agent.

    Args:
        flow: The completed implementation flow.
        plan_path: Path to the plan file.
        logger: Logger for recording progress to flow.log.
        retries: Number of auto-retries for the enrichment agent (default: 3).

    Returns:
        Path to the generated review guide file, or None if enrichment failed.
    """
    # Create the base template
    guide_path = _create_base_review_guide(flow, plan_path, logger)

    # Enrich with intelligent guidance
    if not _enrich_review_guide_with_agent(flow, plan_path, guide_path, logger, retries):
        return None

    return guide_path


def _create_base_review_guide(
    flow: Flow,
    plan_path: Path,
    logger: logging.Logger,
) -> Path:
    """Create the base review guide template with static content.

    Generates a markdown file with the review guide structure including:
    - Flow metadata (name, timestamp, plan path)
    - Git commits made by the flow
    - Placeholder for testing guidance (to be enriched by agent)
    - Validation command
    - Feedback commands

    Args:
        flow: The completed implementation flow.
        plan_path: Path to the plan file.
        logger: Logger for recording progress to flow.log.

    Returns:
        Path to the generated review guide file.
    """
    # Get commits made by this flow
    commits = get_flow_commits(plan_path.stem)
    commits_markdown = format_commits(commits)

    # Get flow type from config
    flow_type = flow.config.flow_type or "implement"

    # Generate timestamp
    timestamp = datetime.now(UTC).strftime("%Y-%m-%d %H:%M:%S UTC")

    # Build the review guide content
    content = f"""# Review Guide: {flow.name}

**Generated:** {timestamp}
**Plan:** `{plan_path}`
**Flow Type:** {flow_type}

## What Was Implemented

### Git Commits

{commits_markdown}

## Testing & Review Guidance

<!-- AGENT_ENRICHMENT_PLACEHOLDER -->

## Full Validation

Run the full validation suite to verify all changes:

```bash
uv run scripts/validate.py
```

## Providing Feedback

If you find issues or have suggestions, use the feedback command:

```bash
# Report an issue
flow feedback --flow {flow.name} --type issue --message "Description of the issue"

# Suggest an improvement
flow feedback --flow {flow.name} --type suggestion --message "Description of the suggestion"

# Report a positive pattern
flow feedback --flow {flow.name} --type positive --message "What worked well"
```

## View Learnings

To see what the system learned from this flow:

```bash
# View pending learnings
flow learnings pending

# View all learnings for this flow
flow learnings list --flow {flow.name}
```
"""

    # Write the review guide
    guide_path = Path(flow.base_dir) / flow.name / "review-guide.md"
    guide_path.parent.mkdir(parents=True, exist_ok=True)
    guide_path.write_text(content, encoding="utf-8")

    log_and_print(
        logger,
        f"Review guide created: {guide_path.resolve()}",
        print_prefix="  [Guide] ",
    )

    return guide_path


def _enrich_review_guide_with_agent(
    flow: Flow,
    plan_path: Path,
    guide_path: Path,
    logger: logging.Logger,
    retries: int = 3,
) -> bool:
    """Enrich the review guide with intelligent testing/review guidance.

    Spawns a builder agent to add context-specific testing guidance,
    code review focus areas, and a manual testing checklist to the review guide.

    Args:
        flow: The main implementation flow.
        plan_path: Path to the plan file (for context).
        guide_path: Path to the review guide file to enrich.
        logger: Logger for recording progress to flow.log.
        retries: Number of auto-retries for stuck/timeout agents (default: 3).

    Returns:
        True if enrichment completed successfully, False otherwise.
    """
    # Read spec plan content for context
    plan_content = plan_path.read_text(encoding="utf-8")

    # Truncate plan content if too long (to fit in prompt)
    max_plan_chars = 8000
    if len(plan_content) > max_plan_chars:
        plan_content = plan_content[:max_plan_chars] + "\n\n[... truncated ...]"

    # Build the enrichment input
    enrichment_input = f"""Enrich the review guide with intelligent testing/review guidance.

## File to Edit

`{guide_path.resolve()}`

## What Was Implemented

The following spec plan was implemented:

```markdown
{plan_content}
```

## Your Task

Edit the review guide file to replace the placeholder section (marked with
`<!-- AGENT_ENRICHMENT_PLACEHOLDER -->`) with intelligent, context-specific guidance:

1. **Testing Guidance**: Specific smoke tests to run, edge cases to check, and
   integration points to verify based on what was implemented.

2. **Code Review Focus Areas**: Complex logic to scrutinize, security
   considerations, performance implications, and potential edge cases specific
   to this implementation.

3. **Manual Testing Checklist**: A markdown checklist with checkboxes (`- [ ]`)
   for manual testing steps the reviewer should perform.

## Rules

- Only edit the review guide file specified above
- Replace the "_Pending enrichment..._" text with your guidance
- Be specific to THIS implementation, not generic advice
- Keep the guidance concise but actionable
"""

    # Spawn the enrichment agent (using step=0 for post-implementation phase)
    result = flow.run(
        "builder",
        agent=AgentInfo(step=0, role="guide-enricher"),
        input=enrichment_input,
        auto_retry=retries,
        initiated_by="flow.api",
    )

    if result.exit_reason == ExitReason.COMPLETED:
        log_and_print(
            logger,
            "Review guide enriched with testing guidance",
            print_prefix="  [Guide] ",
        )
        return True
    else:
        log_and_print(
            logger,
            f"Review guide enrichment failed: {result.exit_reason}",
            level="ERROR",
            print_prefix="  [Guide] ",
        )
        return False
