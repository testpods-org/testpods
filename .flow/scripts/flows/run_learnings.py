#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = [
#     "flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli",
# ]
# ///
"""Standalone learnings analysis script.

This script runs learnings analysis for a completed implementation flow.
It is idempotent - if learnings analysis already completed for the flow,
it will be skipped.

The script spawns a learnings-analyst agent in a separate flow to analyze
the completed implementation flow and propose improvements to agent instructions.

Usage:
    ./.flow/scripts/flows/run_learnings.py --flow-dir <path> --plan <plan-file>
    ./.flow/scripts/flows/run_learnings.py --flow-dir flows/implement_my-feature --plan specs/my-feature.md

Options:
    --flow-dir <path>   Path to the flow directory (e.g., flows/implement_my-feature)
    --plan <path>       Path to the plan file (for logging/reference)
"""

from __future__ import annotations

import sys
from pathlib import Path

# Add lib directory to path for standalone uv execution
# Use .resolve() to handle both direct execution and exec() via shim
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))

import argparse  # noqa: E402

from flow import Flow  # noqa: E402
from flow.lib.flow_status import read_status  # noqa: E402
from flow.lib.logging_setup import get_flow_logger, log_and_print  # noqa: E402
from flow.types import FlowStatusValue  # noqa: E402
from lib import run_learnings_analysis  # noqa: E402

# Configuration
LEARNINGS_RETRIES = 3


def main() -> None:
    parser = argparse.ArgumentParser(description="Run learnings analysis for a completed flow")
    parser.add_argument(
        "--flow-dir",
        type=Path,
        help="Path to the flow directory (e.g., flows/implement_my-feature)",
    )
    parser.add_argument(
        "--plan",
        type=Path,
        help="Path to the plan file",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would happen without spawning agents",
    )
    args = parser.parse_args()

    # Handle dry-run without required arguments
    if args.dry_run:
        print("[DRY RUN] Run learnings analysis orchestration script")
        print("[DRY RUN] Usage: run_learnings.py --flow-dir <path> --plan <path>")
        print("[DRY RUN] Would spawn learnings-analyst agent to:")
        print("[DRY RUN]   1. Analyze completed implementation flow")
        print("[DRY RUN]   2. Identify patterns and improvements")
        print("[DRY RUN]   3. Propose updates to agent instructions")
        return

    if not args.flow_dir:
        parser.error("--flow-dir is required unless using --dry-run")
    if not args.plan:
        parser.error("--plan is required unless using --dry-run")

    flow_dir: Path = args.flow_dir
    plan_path: Path = args.plan

    # Validate flow directory exists
    if not flow_dir.exists():
        sys.exit(f"Flow directory not found: {flow_dir}")

    # Validate plan file exists
    if not plan_path.exists():
        sys.exit(f"Plan file not found: {plan_path}")

    # Extract flow name and base_dir from flow_dir
    # e.g., flows/implement_my-feature -> base_dir="flows", name="implement_my-feature"
    flow_name = flow_dir.name
    base_dir = str(flow_dir.parent)

    # Check if the source flow exists and get its config_path
    source_status = read_status(flow_name, base_dir=base_dir)
    if source_status is None:
        sys.exit(f"Flow '{flow_name}' not found in {base_dir}")

    config_path = source_status.metadata.config_path
    if not config_path:
        sys.exit(f"Flow '{flow_name}' has no config_path in metadata")

    # Check if learnings already completed (idempotent)
    learnings_flow_name = f"learnings-{flow_name}"
    learnings_status = read_status(learnings_flow_name, base_dir=base_dir)
    if learnings_status is not None and learnings_status.status == FlowStatusValue.COMPLETED:
        print(f"Learnings analysis already completed for {flow_name}")
        sys.exit(0)

    # Create Flow using config_path from status.json
    # Context manager ensures proper cleanup on interrupt
    with Flow(
        flow_name,
        config_path=config_path,
        plan_path=str(plan_path),
        reset=False,
    ) as flow:
        # Get orchestration logger
        logger = get_flow_logger(flow.name, "orchestration")

        log_and_print(logger, f"=== Learnings Analysis: {flow_name} ===")
        log_and_print(logger, f"Flow folder: {flow_dir}")
        log_and_print(logger, f"Plan: {plan_path}")

        # Run learnings analysis
        success = run_learnings_analysis(flow, logger, LEARNINGS_RETRIES)

        if success:
            log_and_print(logger, "Learnings analysis completed successfully", print_prefix="\n=== ")
            sys.exit(0)
        else:
            log_and_print(logger, "Learnings analysis failed", level="ERROR", print_prefix="\n=== ")
            print("\nRetry command:")
            print(f"  ./.flow/scripts/flows/run_learnings.py --flow-dir {flow_dir} --plan {plan_path}")
            sys.exit(1)


if __name__ == "__main__":
    main()
