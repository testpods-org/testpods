"""Proof generation runner for orchestration scripts.

This module provides functionality to run proof generation after flow completion,
using the ProofCollector API for evidence collection and spawning agents
for narrative writing and fact-checking.

The proof generation workflow consists of:
1. Evidence collection - ProofCollector runs demos, captures output, recordings
2. narrative-writer agent - Synthesizes clear technical report from evidence
3. fact-checker agent - Validates claims against evidence, ensures consistency
"""

from __future__ import annotations

import logging
from pathlib import Path

from flow import Flow
from flow.lib.config import find_project_root
from flow.lib.flow_status import read_status
from flow.lib.logging_setup import log_and_print
from flow.types import FlowStatusValue
from proof.api import ProofCollector
from proof.showcase import ShowcaseParseError, has_showcase_section, parse_showcase_section


def run_proof_generation(
    flow: Flow,
    plan_path: Path,
    logger: logging.Logger,
    retries: int = 3,
    *,
    force: bool = False,
) -> bool:
    """Run proof generation after flow completion.

    Generates verifiable proof documentation for a completed implementation flow.
    Only runs if the plan file contains a "## Showcase Requirements" section.

    The proof generation creates:
    - Evidence from CLI demos (captured output, timing, exit codes)
    - A narrative proof document synthesizing the evidence
    - Fact-checked claims with evidence citations

    Args:
        flow: The main implementation flow that completed.
        plan_path: Path to the plan file containing showcase requirements.
        logger: Logger for recording progress to flow.log.
        retries: Number of auto-retries for stuck/timeout agents (default: 3).
        force: If True, re-run proof generation even if previously completed.
               This will re-collect evidence and reset the proof flow.

    Returns:
        True if proof generation completed successfully or was skipped.
        False if proof generation failed (fatal error).

    Note:
        - If plan has no showcase section, returns True (skipped, not an error)
        - If proof system not scaffolded, returns True (skipped, not an error)
        - If proof flow already completed (resume), returns True (skipped) - unless force=True
        - If showcase YAML is invalid, returns False (fatal)
        - If any proof agent fails, returns False (fatal)
    """
    # Check if plan has showcase requirements section
    if not has_showcase_section(plan_path):
        log_and_print(
            logger,
            "No showcase requirements in plan (skipping proof generation)",
            print_prefix="  [Info] ",
        )
        return True

    # Check if proof system is scaffolded
    project_root = find_project_root()
    proof_config_path = project_root / ".flow" / "proof" / "base.yaml"

    if not proof_config_path.exists():
        log_and_print(
            logger,
            "Proof system not scaffolded (run 'flow proof scaffold' to enable)",
            print_prefix="  [Info] ",
        )
        return True

    # Check if proof flow already completed (for resume support, unless force=True)
    proof_flow_name = f"proof-{flow.name}"
    proof_status = read_status(proof_flow_name, base_dir=flow.base_dir)
    if proof_status is not None and proof_status.status == FlowStatusValue.COMPLETED:
        if not force:
            log_and_print(
                logger,
                "Proof generation already completed",
                print_prefix="  [Skipping] ",
            )
            return True
        log_and_print(
            logger,
            "Force re-running proof generation",
            print_prefix="  [Force] ",
        )

    # Parse showcase spec - FAIL if invalid (this is a fatal error)
    try:
        showcase_spec = parse_showcase_section(plan_path)
        if showcase_spec is None:
            # This shouldn't happen since we checked has_showcase_section
            log_and_print(
                logger,
                "Showcase section found but parsing returned None",
                level="ERROR",
                print_prefix="  [Error] ",
            )
            return False
    except ShowcaseParseError as e:
        log_and_print(
            logger,
            f"Invalid showcase specification: {e}",
            level="ERROR",
            print_prefix="  [Error] ",
        )
        return False

    # Import here to avoid circular imports
    from proof.orchestration.generate_proof import run_proof_workflow

    # Output directory for proof artifacts
    output_dir = Path(flow.base_dir) / flow.name

    # Check if evidence.json already exists (resume support, unless force=True)
    evidence_path = output_dir / "evidence.json"
    should_collect = not evidence_path.exists() or force
    if should_collect:
        # Collect evidence using ProofCollector API
        log_and_print(
            logger,
            "Collecting evidence..." if not force else "Re-collecting evidence...",
            print_prefix="  [Evidence] ",
        )

        try:
            collector = ProofCollector(output_dir=output_dir)
            evidence_report = collector.collect(
                showcase_spec=showcase_spec,
                metadata={"flow_name": flow.name},
            )

            log_and_print(
                logger,
                f"Evidence collected: {evidence_report.summary.passed_demos}/{evidence_report.summary.total_demos} demos passed",
                print_prefix="  [Evidence] ",
            )
        except Exception as e:
            log_and_print(
                logger,
                f"Evidence collection failed: {e}",
                level="ERROR",
                print_prefix="  [Error] ",
            )
            return False
    else:
        log_and_print(
            logger,
            "Evidence already collected (resuming)",
            print_prefix="  [Skipping] ",
        )

    # Create proof flow with context manager for proper cleanup on interrupt
    # Reset if no prior attempt exists, or if force=True
    with Flow(
        proof_flow_name,
        config_path=".flow/proof/base.yaml",
        reset=(proof_status is None or force),
    ) as proof_flow:
        # Run the proof workflow (now 2 agents: narrative-writer, fact-checker)
        log_and_print(
            logger,
            f"Starting proof generation for: {flow.name}",
            print_prefix="  [Proof] ",
        )

        success = run_proof_workflow(
            proof_flow=proof_flow,
            source_flow_name=flow.name,
            output_dir=output_dir,
            plan_path=plan_path,
            showcase_spec=showcase_spec,
            logger=logger,
            retries=retries,
        )

        if success:
            log_and_print(
                logger,
                "Proof generation complete",
                print_prefix="  [Proof] ",
            )
            return True
        else:
            log_and_print(
                logger,
                "Proof generation failed",
                level="ERROR",
                print_prefix="  [Proof] ",
            )
            return False
