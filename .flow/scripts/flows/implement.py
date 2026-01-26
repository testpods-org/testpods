#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = [
#     "flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli",
# ]
# ///
"""Orchestration script for implementing features from spec plans.

This script takes a spec plan file and implements it step by step using
builder and reviewer agents. Each step goes through:

    Build -> Validate -> Review -> [Fix -> Re-review]* -> Done

Usage:
    ./implement.py path/to/spec-plan.md
    ./implement.py path/to/spec-plan.md --dry-run
"""

import argparse
import logging
import subprocess
import sys
from pathlib import Path

from flow import AgentInfo, Flow
from flow.lib.logging_setup import log_and_print
from flow.lib.spec_parser import get_all_steps, get_pending_steps, mark_step_completed, parse_spec_plan
from flow.types import ExitReason


def run_validation(logger: logging.Logger, is_final_step: bool = False) -> tuple[bool, str]:
    """Run Maven validation and return (passed, output).

    Args:
        logger: Logger for recording validation runs to flow.log
        is_final_step: If True, run full test suite. If False, skip tests (compile only).

    Returns:
        Tuple of (passed: bool, output: str) where output contains stdout+stderr
    """
    if is_final_step:
        # Run full test suite on final step
        cmd = ["mvn", "clean", "compile", "test-compile", "test"]
    else:
        # Just compile for intermediate steps
        cmd = ["mvn", "clean", "compile", "test-compile"]

    cmd_str = " ".join(cmd)
    log_and_print(logger, f"Running: {cmd_str}", print_prefix="    ")

    result = subprocess.run(cmd, capture_output=True, text=True)
    output = result.stdout + result.stderr
    passed = result.returncode == 0

    logger.info(f"Validation {'passed' if passed else 'failed'}")

    return passed, output


def truncate_validation_output(output: str, max_chars: int = 8000) -> str:
    """Truncate validation output to fit in agent context."""
    if len(output) <= max_chars:
        return output
    return output[:max_chars] + "\n\n[... output truncated ...]"


def main():
    parser = argparse.ArgumentParser(description="Implement features from spec plans")
    parser.add_argument(
        "spec_plan",
        nargs="?",
        help="Path to the spec plan file",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would happen without spawning agents",
    )
    parser.add_argument(
        "--resume",
        action="store_true",
        help="Resume from where the flow left off",
    )
    parser.add_argument(
        "--step",
        type=int,
        help="Start from a specific step number",
    )
    args = parser.parse_args()

    # Handle dry-run without spec_plan argument
    if args.dry_run and not args.spec_plan:
        print("[DRY RUN] Implement flow orchestration script")
        print("[DRY RUN] Usage: implement.py <spec_plan.md> [--dry-run] [--resume] [--step N]")
        print("[DRY RUN] Would parse spec plan for implementation steps")
        print("[DRY RUN] For each step:")
        print("[DRY RUN]   1. Spawn builder agent to implement")
        print("[DRY RUN]   2. Run validation: mvn clean compile test-compile test")
        print("[DRY RUN]   3. Spawn reviewer agent to review")
        print("[DRY RUN]   4. If issues found, spawn builder to fix")
        print("[DRY RUN]   5. Repeat until approved or max attempts reached")
        return

    if not args.spec_plan:
        parser.error("spec_plan is required unless using --dry-run")

    spec_plan_path = Path(args.spec_plan)
    if not spec_plan_path.exists():
        print(f"Error: Spec plan not found: {spec_plan_path}")
        sys.exit(1)

    flow_name = spec_plan_path.stem

    if args.dry_run:
        print(f"[DRY RUN] Would process spec plan: {spec_plan_path}")
        print(f"[DRY RUN] Flow name: {flow_name}")
        print("[DRY RUN] Would parse spec plan for steps")
        print("[DRY RUN] For each step:")
        print("[DRY RUN]   1. Spawn builder agent to implement")
        print("[DRY RUN]   2. Run validation: mvn clean compile test-compile test")
        print("[DRY RUN]   3. Spawn reviewer agent to review")
        print("[DRY RUN]   4. If issues found, spawn builder to fix")
        print("[DRY RUN]   5. Repeat until approved or max attempts reached")
        return

    # Initialize flow with implement configuration
    flow = Flow(
        flow_name,
        flow_type="implement",
        plan_path=str(spec_plan_path),
    )

    logger = flow.get_logger()
    log_and_print(logger, f"Starting implementation: {flow_name}")

    # Parse steps from spec plan using the spec_parser helpers
    all_steps = get_all_steps(spec_plan_path, flow.config)
    if not all_steps:
        log_and_print(logger, "No steps found in spec plan", level="ERROR")
        sys.exit(1)

    # For step content, we need the full parsed plan
    plan_content = spec_plan_path.read_text()
    parsed_plan = parse_spec_plan(plan_content, flow.config.spec_plan)
    step_content_map = {step.number: step.content for step in parsed_plan.steps}

    log_and_print(logger, f"Found {len(all_steps)} steps in spec plan")

    # Set total steps for progress tracking
    flow.set_total_steps(len(all_steps))

    # Determine starting step
    start_step = args.step if args.step else 1
    if args.resume:
        # Find last completed step by checking reviewer completion
        for step_number, _step_title in reversed(all_steps):
            if flow.has_completed_agent(step=step_number, role="reviewer"):
                start_step = step_number + 1
                break

    # Get pending steps for iteration
    pending_steps = get_pending_steps(spec_plan_path, flow.config)
    is_final_step = False

    with flow:
        last_agent_id = None

        for idx, (step_number, step_title) in enumerate(pending_steps):
            if step_number < start_step:
                log_and_print(logger, f"Skipping step {step_number} (already completed)")
                continue

            # Check if this is the final step
            is_final_step = idx == len(pending_steps) - 1

            log_and_print(logger, f"Step {step_number}: {step_title}")

            # Get step content from the parsed plan
            step_content = step_content_map.get(step_number, "")

            # Check if builder already completed for this step
            if flow.has_completed_agent(step=step_number, role="builder"):
                log_and_print(logger, "  Builder already completed, skipping")
                last_agent_id = flow.get_last_agent_id(step=step_number, role="builder")
            else:
                # Run builder
                log_and_print(logger, "  Running builder agent")
                builder_result = flow.run(
                    "builder",
                    agent=AgentInfo(step=step_number, role="builder"),
                    input=f"Implement step {step_number}: {step_title}\n\n{step_content}",
                    plan_path=str(spec_plan_path),
                    after=last_agent_id,
                    auto_retry=3,
                )

                if builder_result.exit_reason != ExitReason.COMPLETED:
                    log_and_print(logger, f"  Builder failed: {builder_result.exit_reason}", level="ERROR")
                    flow.set_status("failed")
                    sys.exit(1)

                last_agent_id = builder_result.final_agent

            # Run validation (skip tests for intermediate steps, run full validation on final step)
            log_and_print(logger, "  Running validation")
            passed, output = run_validation(logger, is_final_step=is_final_step)

            if not passed:
                log_and_print(logger, "  Validation failed, running fixer")
                truncated_output = truncate_validation_output(output)
                fix_result = flow.run(
                    "builder",
                    agent=AgentInfo(step=step_number, role="validation-fixer"),
                    input=f"Fix validation errors:\n\n{truncated_output}",
                    after=last_agent_id,
                    auto_retry=3,
                )
                last_agent_id = fix_result.final_agent

                # Re-run validation
                passed, output = run_validation(logger, is_final_step=is_final_step)
                if not passed:
                    log_and_print(logger, "  Validation still failing after fix", level="WARNING")

            # Check if reviewer already approved
            if flow.has_completed_agent(step=step_number, role="reviewer"):
                log_and_print(logger, "  Reviewer already completed, skipping")
                last_agent_id = flow.get_last_agent_id(step=step_number, role="reviewer")
                # Mark step completed in the plan file
                mark_step_completed(spec_plan_path, step_number)
                flow.mark_step_completed(step_number)
                continue

            # Run reviewer
            log_and_print(logger, "  Running reviewer agent")
            review_result = flow.run(
                "reviewer",
                agent=AgentInfo(step=step_number, role="reviewer"),
                input=f"Review the implementation of step {step_number}: {step_title}",
                plan_path=str(spec_plan_path),
                after=last_agent_id,
                auto_retry=3,
            )

            # Check for approval
            if review_result.report_file:
                report_content = Path(review_result.report_file).read_text()
                if "REVIEW_RESULT: APPROVED" in report_content:
                    log_and_print(logger, "  Review approved")
                else:
                    log_and_print(logger, "  Review found issues - running fix loop")
                    # Simple fix loop
                    for attempt in range(3):
                        fix_result = flow.run(
                            "builder",
                            agent=AgentInfo(step=step_number, role="review-fixer"),
                            input=f"Fix the issues found by the reviewer:\n\n{report_content[:8000]}",
                            after=review_result.final_agent,
                            auto_retry=3,
                        )

                        # Re-review
                        review_result = flow.run(
                            "reviewer",
                            agent=AgentInfo(step=step_number, role="reviewer"),
                            input=f"Review the fixes for step {step_number}",
                            after=fix_result.final_agent,
                            auto_retry=3,
                        )

                        if review_result.report_file:
                            report_content = Path(review_result.report_file).read_text()
                            if "REVIEW_RESULT: APPROVED" in report_content:
                                log_and_print(logger, f"  Review approved after {attempt + 1} fix attempts")
                                break
                    else:
                        log_and_print(logger, "  Max fix attempts reached", level="WARNING")

            last_agent_id = review_result.final_agent

            # Mark step completed in the plan file and flow status
            mark_step_completed(spec_plan_path, step_number)
            flow.mark_step_completed(step_number)

        flow.set_status("completed")
        log_and_print(logger, "Implementation complete")


if __name__ == "__main__":
    main()

