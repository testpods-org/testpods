#!/usr/bin/env -S uv run
"""Standalone proof generation script.

This script runs proof generation for a completed implementation flow.
It is idempotent - if proof generation already completed for the flow,
it will be skipped. It also skips if the plan has no showcase section.

The proof generation creates:
- Evidence from CLI demos (captured output, timing, exit codes)
- A narrative proof document synthesizing the evidence
- Fact-checked claims with evidence citations

Usage:
    ./.flow/scripts/flows/run_proof.py --flow-dir <path> --plan <plan-file>
    ./.flow/scripts/flows/run_proof.py --flow-dir flows/implement_my-feature --plan specs/my-feature.md

Options:
    --flow-dir <path>   Path to the flow directory (e.g., flows/implement_my-feature)
    --plan <path>       Path to the plan file containing showcase requirements
"""

from __future__ import annotations

import sys
from pathlib import Path

# Add lib directory to path for standalone uv execution
# Use .resolve() to handle both direct execution and exec() via shim
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))

import argparse  # noqa: E402
import os  # noqa: E402

from flow import Flow  # noqa: E402
from flow.lib.flow_status import read_status  # noqa: E402
from flow.lib.logging_setup import get_flow_logger, log_and_print  # noqa: E402
from flow.types import FlowStatusValue  # noqa: E402
from proof.showcase import has_showcase_section  # noqa: E402
from lib import run_proof_generation  # noqa: E402

# Configuration
PROOF_RETRIES = 3


def main() -> None:
    parser = argparse.ArgumentParser(description="Run proof generation for a completed flow")
    parser.add_argument(
        "--flow-dir",
        type=Path,
        required=True,
        help="Path to the flow directory (e.g., flows/implement_my-feature)",
    )
    parser.add_argument(
        "--plan",
        type=Path,
        required=True,
        help="Path to the plan file",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Re-run proof generation even if previously completed",
    )
    parser.add_argument(
        "--no-serve",
        action="store_true",
        help="Skip starting the webserver after proof generation (for subprocess use)",
    )
    args = parser.parse_args()

    flow_dir: Path = args.flow_dir
    plan_path: Path = args.plan
    force: bool = args.force
    no_serve: bool = args.no_serve

    # Validate flow directory exists
    if not flow_dir.exists():
        sys.exit(f"Flow directory not found: {flow_dir}")

    # Validate plan file exists
    if not plan_path.exists():
        sys.exit(f"Plan file not found: {plan_path}")

    # Check if plan has showcase section (skip if not)
    if not has_showcase_section(plan_path):
        print(f"No showcase requirements in plan: {plan_path}")
        print("PROOF_SKIPPED: no showcase requirements")
        sys.exit(0)

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

    # Check if proof already completed (idempotent, unless --force)
    proof_flow_name = f"proof-{flow_name}"
    proof_status = read_status(proof_flow_name, base_dir=base_dir)
    if proof_status is not None and proof_status.status == FlowStatusValue.COMPLETED:
        if not force:
            print(f"Proof generation already completed for {flow_name}")
            sys.exit(0)
        print(f"Force re-running proof generation for {flow_name}")

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

        log_and_print(logger, f"=== Proof Generation: {flow_name} ===")
        log_and_print(logger, f"Flow folder: {flow_dir}")
        log_and_print(logger, f"Plan: {plan_path}")

        # Run proof generation
        success = run_proof_generation(flow, plan_path, logger, PROOF_RETRIES, force=force)

        if success:
            log_and_print(logger, "Proof generation completed successfully", print_prefix="\n=== ")
            proof_html = flow_dir / "proof.html"
            if proof_html.exists() and not no_serve:
                # Replace this process with view_proof.py - user can Ctrl+C to stop
                view_script = Path("scripts/view_proof.py")
                os.execvp("uv", ["uv", "run", str(view_script), "--random-port", str(proof_html)])
            sys.exit(0)
        else:
            log_and_print(logger, "Proof generation failed", level="ERROR", print_prefix="\n=== ")
            print("\nRetry command:")
            print(f"  ./.flow/scripts/flows/run_proof.py --flow-dir {flow_dir} --plan {plan_path}")
            sys.exit(1)


if __name__ == "__main__":
    main()
