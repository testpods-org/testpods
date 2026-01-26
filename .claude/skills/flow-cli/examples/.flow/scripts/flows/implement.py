#!/usr/bin/env -S uv run
"""Flow CLI plan implementation script.

This script implements multi-step plans for the flow-cli project itself.
It uses four agents:
- builder: implements features
- reviewer: reviews code quality
- documentation-updater: updates project documentation
- documentation-reviewer: reviews documentation quality

Flow per step:
  Build → Validate → [Fix → Validate]* → Review → [Fix → Validate → Re-review]* → Commit

After all steps complete:
  Documentation Update → Doc Review → [Fix → Re-review]* → Commit (if approved)

Validation runs directly via scripts/validate.py after each build/fix,
ensuring linting, type checking, and tests pass before proceeding.

After each step is committed, flow.mark_step_completed() is called to explicitly
mark the step as completed in the flow status. When all steps finish, flow.set_status()
is called to mark the flow as "completed" or "failed".

Usage: ./.flow/scripts/flows/implement.py <plan-file-path> [options]
Options: --info <info>, --reset, --flow-type <type>, --variant <variant>, --config <config>
Example: ./.flow/scripts/flows/implement.py specs/learnings-and-self-improvement.md
Example: ./.flow/scripts/flows/implement.py specs/feature.md --reset  # Start fresh
Example: ./.flow/scripts/flows/implement.py specs/feature.md --flow-type implement --variant new-feature
Example: ./.flow/scripts/flows/implement.py specs/feature.md --config test-flows/config_test_opencode.yaml
Example: ./.flow/scripts/flows/implement.py specs/feature.md --info "Resuming from step 3. Previous
    attempt failed due to circular imports - ensure models are imported lazily"
"""

from __future__ import annotations

import sys
from pathlib import Path

# Add lib directory to path for standalone uv execution
# Use .resolve() to handle both direct execution and exec() via shim
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))

import argparse  # noqa: E402
import traceback  # noqa: E402

from flow import AgentInfo, Flow  # noqa: E402
from flow.lib.flow_status import read_status  # noqa: E402
from flow.lib.logging_setup import get_flow_logger, log_and_print  # noqa: E402
from flow.lib.spec_parser import get_all_steps, get_pending_steps, mark_step_completed  # noqa: E402
from flow.types import ExitReason, FlowStatusValue  # noqa: E402
from lib import (  # noqa: E402
    InfoTracker,
    ReviewLoopConfig,
    check_doc_review_approved,
    check_step_review_approved,
    commit_and_push_changes,
    has_flow_commits,
    run_review_loop,
    run_validation,
    truncate_validation_output,
)

# Configuration
MAX_BUILD_RETRIES = 3
MAX_VALIDATION_FIX_ATTEMPTS = 3
MAX_REVIEW_FIX_ATTEMPTS = 3
MAX_DOC_REVIEW_FIX_ATTEMPTS = 3
FIX_RETRY_COUNT = 3
REVIEW_APPROVAL_MARKER = "REVIEW_RESULT: APPROVED"

# Preamble for reviewer agents to set context for their assignment
REVIEWER_PREAMBLE = (
    "It is now your job to follow the review workflow to thoroughly assess and verify "
    "the changes that have been made to achieve the goals of the following plan-step:"
)

# Preamble for documentation reviewer agents
DOC_REVIEWER_PREAMBLE = (
    "It is now your job to review the documentation updates made by the documentation-updater agent. "
    "Verify that all documentation changes accurately reflect the codebase and meet quality standards:"
)

# Review loop configurations
CODE_REVIEW_CONFIG = ReviewLoopConfig(
    reviewer_agent="reviewer",
    fixer_agent="builder",
    reviewer_role="reviewer",
    fixer_role="review-fixer",
    max_attempts=MAX_REVIEW_FIX_ATTEMPTS,
    approval_marker=REVIEW_APPROVAL_MARKER,
    fail_flow_on_max_attempts=True,
)

DOC_REVIEW_CONFIG = ReviewLoopConfig(
    reviewer_agent="documentation-reviewer",
    fixer_agent="documentation-updater",
    reviewer_role="doc-reviewer",
    fixer_role="doc-fixer",
    max_attempts=MAX_DOC_REVIEW_FIX_ATTEMPTS,
    approval_marker=REVIEW_APPROVAL_MARKER,
    fail_flow_on_max_attempts=True,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Implement a multi-step plan for the flow-cli project")
    parser.add_argument("plan_file", type=Path, help="Path to the plan file")
    parser.add_argument(
        "--info",
        type=str,
        default=None,
        help="Additional context for the builder (e.g., resume info, constraints)",
    )
    parser.add_argument(
        "--reset",
        action="store_true",
        help="Reset flow state and start fresh (default: resume existing state)",
    )
    parser.add_argument(
        "--flow-type",
        type=str,
        default="implement",
        help="Flow type for config resolution (default: implement)",
    )
    parser.add_argument(
        "--variant",
        type=str,
        default=None,
        help="Config variant (e.g., 'new-feature' for .flow/implement/new-feature.yaml)",
    )
    parser.add_argument(
        "--config",
        type=str,
        default=None,
        help="Path to config file (overrides --flow-type)",
    )
    args = parser.parse_args()

    plan_path: Path = args.plan_file
    if not plan_path.exists():
        sys.exit(f"Plan not found: {plan_path}")

    # Initialize flow with context manager for automatic cleanup on interruption
    with Flow(
        f"implement_{plan_path.stem}",
        plan_path=str(plan_path),
        reset=args.reset,
        config_path=args.config,
        flow_type=args.flow_type if not args.config else None,
        variant=args.variant if not args.config else None,
    ) as flow:
        # When resuming a failed/interrupted flow, reset status to "running"
        # This provides visibility that the flow is being actively worked on
        if not args.reset:
            current_status = read_status(flow.name, base_dir=flow.base_dir)
            if current_status and current_status.status in (
                FlowStatusValue.FAILED,
                FlowStatusValue.INTERRUPTED,
            ):
                flow.set_status("running")

        # Get orchestration logger for detailed flow.log entries
        logger = get_flow_logger(flow.name, "orchestration")

        log_and_print(logger, f"=== Implementing: {plan_path} ===")
        log_and_print(logger, f"Flow folder: {Path(flow.base_dir) / flow.name}")
        log_and_print(logger, f"Flow type: {flow.config.flow_type or 'implement'}")
        if args.variant:
            log_and_print(logger, f"Variant: {args.variant}")
        if args.config:
            log_and_print(logger, f"Config: {args.config}")
        log_and_print(logger, f"Mode: {'reset (fresh start)' if args.reset else 'resume (continuing existing)'}")
        if args.info:
            log_and_print(logger, f"Additional context: {args.info}")

        # Track --info usage: add to first agent that runs (handles resume from any stage)
        info_tracker = InfoTracker(args.info)

        # Get all steps to determine total count (for progress tracking)
        all_steps = get_all_steps(plan_path, flow.config)
        if not all_steps:
            flow.set_status("failed")
            sys.exit("No steps found in plan file")

        # Get pending steps to iterate over
        pending_steps = get_pending_steps(plan_path, flow.config)
        if not pending_steps:
            log_and_print(logger, "All steps are already marked as completed")
            flow.set_status("completed")
            sys.exit(0)

        try:
            total_steps = len(all_steps)
            last_step_number = all_steps[-1][0]

            # Register total number of steps for progress tracking
            flow.set_total_steps(total_steps)

            for step_index, (step_number, step_title) in enumerate(pending_steps):
                is_final_step = step_number == last_step_number
                log_and_print(logger, f"Step {step_number}/{total_steps}: {step_title}", print_prefix="\n=== ")

                # Check if builder already completed (for resuming interrupted flows)
                if flow.has_completed_agent(step=step_number, role="builder"):
                    log_and_print(logger, "Builder already completed for this step", print_prefix="  [Skipping] ")
                    last_agent_id = flow.get_last_agent_id(step=step_number, role="builder")
                    # If a validation-fixer ran after, use that as the chain point
                    fixer_id = flow.get_last_agent_id(step=step_number, role="validation-fixer")
                    if fixer_id:
                        last_agent_id = fixer_id
                else:
                    # Build prompt (info_tracker adds --info to first agent that runs)
                    # The plan content will be inlined by the spawner when plan_path is provided
                    build_input = info_tracker.add_to_input(f"Implement step {step_number}: {step_title}")

                    # Build with auto-retry
                    build_result = flow.run(
                        "builder",
                        agent=AgentInfo(step=step_number, role="builder"),
                        input=build_input,
                        plan_path=str(plan_path),
                        auto_retry=MAX_BUILD_RETRIES,
                        initiated_by="flow.api",
                    )
                    if build_result.exit_reason != ExitReason.COMPLETED:
                        flow.set_status("failed")
                        sys.exit(f"Build failed: {build_result.exit_reason.value}")

                    last_agent_id = build_result.final_agent

                    # Validation loop: validate -> fix -> re-validate until passing
                    for validation_attempt in range(MAX_VALIDATION_FIX_ATTEMPTS):
                        log_and_print(logger, f"Validation attempt {validation_attempt + 1}", print_prefix="  [Validation] ")
                        passed, validation_output = run_validation(logger, is_final_step)
                        if passed:
                            log_and_print(logger, "All checks passed", print_prefix="  [Validation] ")
                            break

                        log_and_print(logger, "Checks failed, sending to builder for fixes", print_prefix="  [Validation] ")

                        # Fix validation failures
                        fix_input = info_tracker.add_to_input(
                            f"Fix validation failures for step {step_number}\n\n"
                            "Validation (linting, type checking, tests) failed.\n\n"
                            "Validation output:\n"
                            "```\n"
                            f"{truncate_validation_output(validation_output)}\n"
                            "```\n\n"
                            "Review the errors above and fix them. "
                            "Note: If 'Ruff Formatter' fails with 'Would reformat' messages, "
                            "run 'uv run ruff format flow/' to fix formatting issues."
                        )
                        fix_result = flow.run(
                            "builder",
                            agent=AgentInfo(step=step_number, role="validation-fixer"),
                            input=fix_input,
                            after=last_agent_id,
                            auto_retry=FIX_RETRY_COUNT,
                            initiated_by="flow.api",
                        )
                        if fix_result.exit_reason != ExitReason.COMPLETED:
                            flow.set_status("failed")
                            sys.exit(f"Validation fix failed: {fix_result.exit_reason.value}")
                        last_agent_id = fix_result.final_agent
                    else:
                        flow.set_status("failed")
                        sys.exit(f"Validation failed after {MAX_VALIDATION_FIX_ATTEMPTS} fix attempts")

                # Check if reviewer already approved (for resuming interrupted flows)
                if check_step_review_approved(flow, step_number, REVIEW_APPROVAL_MARKER):
                    log_and_print(logger, "Reviewer already approved for this step", print_prefix="  [Skipping] ")
                    # Skip to commit
                else:
                    # Review loop using utility
                    review_input = info_tracker.add_to_input(
                        f"Review the implementation for step {step_number}: {step_title}\n\n"
                        f"Verify the implementation meets the step requirements shown in the spec plan below. "
                        f"Check that all acceptance criteria for this step are satisfied.\n\n"
                        f"Include '{REVIEW_APPROVAL_MARKER}' in your report if approved."
                    )

                    # Define validation fix input generator for post-fix validation
                    def make_validation_fix_input(validation_output: str) -> str:
                        return info_tracker.add_to_input(
                            f"Fix validation failures for step {step_number}\n\n"
                            "Validation (linting, type checking, tests) failed after "
                            "fixing reviewer feedback.\n\n"
                            "Validation output:\n"
                            "```\n"
                            f"{truncate_validation_output(validation_output)}\n"
                            "```\n\n"
                            "Review the errors above and fix them. "
                            "Note: If 'Ruff Formatter' fails with 'Would reformat' messages, "
                            "run 'uv run ruff format flow/' to fix formatting issues."
                        )

                    review_result = run_review_loop(
                        flow=flow,
                        config=CODE_REVIEW_CONFIG,
                        step=step_number,
                        reviewer_input=review_input,
                        fixer_input_fn=lambda _: info_tracker.add_to_input(f"Fix reviewer feedback for step {step_number}"),
                        after_agent_id=last_agent_id,
                        logger=logger,
                        log_prefix="  [Review] ",
                        reviewer_preamble=REVIEWER_PREAMBLE,
                        plan_path=str(plan_path),
                        run_post_fix_validation=lambda: run_validation(logger, is_final_step),
                        validation_fix_input_fn=make_validation_fix_input,
                        max_validation_fix_attempts=MAX_VALIDATION_FIX_ATTEMPTS,
                        initiated_by="flow.api",
                    )

                    if not review_result.approved:
                        log_and_print(
                            logger,
                            f"Review failed after {CODE_REVIEW_CONFIG.max_attempts} fix attempts",
                            level="ERROR",
                            print_prefix="  [Review] ",
                        )
                        flow.set_status("failed")
                        sys.exit(f"Review failed after {CODE_REVIEW_CONFIG.max_attempts} fix attempts")

                    last_agent_id = review_result.final_agent_id

                # Mark step as completed in plan file before committing
                if mark_step_completed(plan_path, step_number):
                    print(f"  [Plan] Marked step {step_number} as completed ✅")

                # Commit if there are changes (dummy test plan runs don't produce any changes to commit)
                commit_and_push_changes(logger, f"[{plan_path.stem}] Step {step_number}: {step_title}")

                # Mark step as explicitly completed in flow status
                flow.mark_step_completed(step_number)

            # Final test validation: ensure ALL tests pass before documentation
            # This catches test failures that were intentionally deferred during intermediate steps
            log_and_print(logger, "Final Test Validation", print_prefix="\n=== ")
            for final_test_attempt in range(MAX_VALIDATION_FIX_ATTEMPTS):
                log_and_print(logger, f"Running full test suite (attempt {final_test_attempt + 1})", print_prefix="  [Tests] ")
                # Run with is_final_step=True to include all tests
                final_passed, final_output = run_validation(logger, is_final_step=True)
                if final_passed:
                    log_and_print(logger, "All tests pass", print_prefix="  [Tests] ")
                    break

                log_and_print(logger, "Test failures found, fixing", print_prefix="  [Tests] ")
                # Spawn builder with explicit instructions to fix ALL test failures
                test_fix_input = info_tracker.add_to_input(
                    "Fix ALL remaining test failures\n\n"
                    "All implementation steps are complete. Your ONLY task is to ensure "
                    "ALL tests in the test suite pass. Do not defer any failures - fix them now.\n\n"
                    "Validation output:\n"
                    "```\n"
                    f"{truncate_validation_output(final_output)}\n"
                    "```\n\n"
                    "Fix ALL failing tests. This is the final validation before documentation."
                )
                test_fix_result = flow.run(
                    "builder",
                    agent=AgentInfo(step=last_step_number, role="test-fixer"),
                    input=test_fix_input,
                    auto_retry=FIX_RETRY_COUNT,
                    initiated_by="flow.api",
                )
                if test_fix_result.exit_reason != ExitReason.COMPLETED:
                    flow.set_status("failed")
                    sys.exit(f"Test fix failed: {test_fix_result.exit_reason.value}")
            else:
                flow.set_status("failed")
                sys.exit(f"Tests failed after {MAX_VALIDATION_FIX_ATTEMPTS} fix attempts")

            # Commit any test fixes made during final validation
            commit_and_push_changes(logger, f"[{plan_path.stem}] Fix remaining test failures")

            # Documentation update: only run if this flow made any commits
            # Skip for read-only test flows that don't produce any commits
            if has_flow_commits(plan_path.stem):
                log_and_print(logger, "Documentation Update", print_prefix="\n=== ")

                # Track the last documentation-related agent for chaining
                last_doc_agent_id: str | None = None

                # Check if documentation-updater already completed (resume support)
                if flow.has_completed_agent(step=last_step_number, role="docs"):
                    log_and_print(logger, "Documentation update already completed", print_prefix="  [Skipping] ")
                    last_doc_agent_id = flow.get_last_agent_id(step=last_step_number, role="docs")
                    # If a doc-fixer ran after, use that as the chain point
                    doc_fixer_id = flow.get_last_agent_id(step=last_step_number, role="doc-fixer")
                    if doc_fixer_id:
                        last_doc_agent_id = doc_fixer_id
                else:
                    # Run documentation-updater
                    doc_input = (
                        "All implementation steps are complete. Review and update all project documentation to reflect the changes made."
                    )
                    doc_result = flow.run(
                        "documentation-updater",
                        agent=AgentInfo(step=last_step_number, role="docs"),
                        input=doc_input,
                        auto_retry=3,
                        initiated_by="flow.api",
                    )

                    if doc_result.exit_reason != ExitReason.COMPLETED:
                        log_and_print(
                            logger,
                            f"Documentation update {doc_result.exit_reason.value}",
                            level="WARNING",
                            print_prefix="  [Docs] ",
                        )
                        # Skip review if doc update failed - but don't fail the flow
                        last_doc_agent_id = None
                    else:
                        log_and_print(logger, "Documentation update completed", print_prefix="  [Docs] ")
                        last_doc_agent_id = doc_result.final_agent

                # Documentation review loop (only if doc update succeeded)
                if last_doc_agent_id is not None:
                    # Check if doc reviewer already approved (resume support)
                    if check_doc_review_approved(flow, last_step_number, REVIEW_APPROVAL_MARKER):
                        log_and_print(logger, "Documentation reviewer already approved", print_prefix="  [Skipping] ")
                    else:
                        doc_review_input = (
                            "Review the documentation updates made for this implementation.\n\n"
                            "Verify that all documentation changes accurately reflect the codebase. "
                            "Check for accuracy, completeness, coherence, and consistency.\n\n"
                            f"Include '{REVIEW_APPROVAL_MARKER}' in your report if approved."
                        )

                        doc_review_result = run_review_loop(
                            flow=flow,
                            config=DOC_REVIEW_CONFIG,
                            step=last_step_number,
                            reviewer_input=doc_review_input,
                            fixer_input_fn=lambda _: "Fix the documentation issues identified by the reviewer.",
                            after_agent_id=last_doc_agent_id,
                            logger=logger,
                            log_prefix="  [Doc Review] ",
                            reviewer_preamble=DOC_REVIEWER_PREAMBLE,
                            initiated_by="flow.api",
                        )

                        # Fail flow if doc review not approved
                        if not doc_review_result.approved:
                            log_and_print(
                                logger,
                                f"Documentation review failed after {DOC_REVIEW_CONFIG.max_attempts} fix attempts",
                                level="ERROR",
                                print_prefix="  [Doc Review] ",
                            )
                            flow.set_status("failed")
                            sys.exit(f"Documentation review failed after {DOC_REVIEW_CONFIG.max_attempts} fix attempts")

                # Commit documentation changes
                commit_and_push_changes(logger, f"[{plan_path.stem}] Update documentation")
            else:
                log_and_print(logger, "Documentation Update", print_prefix="\n=== ")
                log_and_print(logger, "No code changes to document (read-only test flow)", print_prefix="  [Skipping] ")

            # All steps completed successfully
            flow.set_status("completed")

            # Show completion statistics (using normal verbosity for detailed summary)
            from flow.lib.flow_stats import format_stats_report, get_flow_stats

            stats = get_flow_stats(flow.name, base_dir=flow.base_dir, fixer_pattern="fixer")
            if stats:
                print("\n" + "=" * 60)
                report = format_stats_report(stats, verbosity="normal")
                print(report)
                print("=" * 60)
            else:
                # Fallback if stats unavailable
                print(f"\n=== Complete! {total_steps} steps implemented ===")

        except Exception as e:
            # Log the error to flow.log
            logger = get_flow_logger(flow.name, "orchestration")
            logger.error(f"Flow crashed: {e}")
            logger.error(traceback.format_exc())

            # Mark flow as failed on any crash
            flow.set_status("failed")
            raise


if __name__ == "__main__":
    main()
