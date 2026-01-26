#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = [
#     "flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli",
# ]
# ///
"""Orchestration script for creating spec plans from feature requests.

This script takes a feature request and uses the planner agent to create
a detailed spec plan document.

Usage:
    ./plan.py "Add ConfigMap hot-reloading support" --name configmap-hot-reload
    ./plan.py feature-request.md --name my-feature
    ./plan.py "Feature description" --dry-run
"""

import argparse
import sys
from pathlib import Path

from flow import AgentInfo, Flow
from flow.lib.logging_setup import log_and_print
from flow.types import ExitReason


def main():
    parser = argparse.ArgumentParser(description="Create spec plans from feature requests")
    parser.add_argument(
        "feature_request",
        nargs="?",
        help="Feature request text or path to a file containing the request",
    )
    parser.add_argument(
        "--name",
        help="Name for the flow and output spec plan",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would happen without spawning agents",
    )
    parser.add_argument(
        "--output-dir",
        default="specs",
        help="Directory to write the spec plan (default: specs)",
    )
    args = parser.parse_args()

    # Handle dry-run without required arguments
    if args.dry_run and (not args.feature_request or not args.name):
        print("[DRY RUN] Plan flow orchestration script")
        print("[DRY RUN] Usage: plan.py <feature_request> --name <name> [--dry-run] [--output-dir <dir>]")
        print("[DRY RUN] Would spawn planner agent to:")
        print("[DRY RUN]   1. Analyze the feature request")
        print("[DRY RUN]   2. Research existing codebase patterns")
        print("[DRY RUN]   3. Create detailed implementation steps")
        print("[DRY RUN]   4. Write spec plan to output directory")
        return

    if not args.feature_request:
        parser.error("feature_request is required unless using --dry-run")
    if not args.name:
        parser.error("--name is required unless using --dry-run")

    # Determine if feature_request is a file path or inline text
    request_path = Path(args.feature_request)
    if request_path.exists():
        feature_request = request_path.read_text()
    else:
        feature_request = args.feature_request

    flow_name = args.name
    output_dir = Path(args.output_dir)

    if args.dry_run:
        print(f"[DRY RUN] Feature request: {feature_request[:100]}...")
        print(f"[DRY RUN] Flow name: {flow_name}")
        print(f"[DRY RUN] Output directory: {output_dir}")
        print("[DRY RUN] Would spawn planner agent to:")
        print("[DRY RUN]   1. Analyze the feature request")
        print("[DRY RUN]   2. Research existing codebase patterns")
        print("[DRY RUN]   3. Create detailed implementation steps")
        print(f"[DRY RUN]   4. Write spec plan to: {output_dir / f'{flow_name}.md'}")
        return

    # Ensure output directory exists
    output_dir.mkdir(parents=True, exist_ok=True)

    # Initialize flow with plan configuration
    flow = Flow(
        flow_name,
        flow_type="plan",
    )

    logger = flow.get_logger()
    log_and_print(logger, f"Starting planning: {flow_name}")

    spec_plan_path = output_dir / f"{flow_name}.md"

    with flow:
        # Run planner agent
        log_and_print(logger, "Running planner agent")

        planner_input = f"""Create a detailed spec plan for the following feature request:

{feature_request}

Write the spec plan to: {spec_plan_path}

The spec plan should follow this format:

# Feature: [Feature Name]

## Overview
[Brief description of the feature and its purpose]

## Context
[Relevant background, related code, dependencies]

## Implementation Steps

### Status: Pending | Step 1: [Step Title]
[Detailed description of what to implement]

**Files:**
- `path/to/file.java` - [what changes]

**Acceptance Criteria:**
- [ ] Criterion 1
- [ ] Criterion 2

### Status: Pending | Step 2: [Step Title]
...

## Testing Strategy
[How the feature should be tested]

## Risks and Considerations
[Potential issues, edge cases, migration concerns]
"""

        planner_result = flow.run(
            "planner",
            agent=AgentInfo(step=1, role="planner"),
            input=planner_input,
            auto_retry=3,
        )

        if planner_result.exit_reason != ExitReason.COMPLETED:
            log_and_print(logger, f"Planner failed: {planner_result.exit_reason}", level="ERROR")
            flow.set_status("failed")
            sys.exit(1)

        # Verify spec plan was created
        if spec_plan_path.exists():
            log_and_print(logger, f"Spec plan created: {spec_plan_path}")
        else:
            log_and_print(logger, "Warning: Spec plan file not found at expected location", level="WARNING")

        flow.set_status("completed")
        log_and_print(logger, "Planning complete")
        print(f"\nSpec plan written to: {spec_plan_path}")
        print(f"\nTo implement this plan, run:")
        print(f"  ./.flow/scripts/flows/implement.py {spec_plan_path}")


if __name__ == "__main__":
    main()
