"""Review loop utility for orchestration scripts.

This module provides a reusable review loop pattern that can be used for
any builder/reviewer agent pair. The pattern is:

    Build -> Review -> [Fix -> Re-review]* -> Done

The loop continues until:
- The reviewer approves (returns REVIEW_RESULT: APPROVED)
- Max attempts are reached
- A critical error occurs

Example usage:
    from scripts.flows.lib.review_loop import run_review_loop, ReviewLoopConfig

    config = ReviewLoopConfig(
        reviewer_agent="reviewer",
        fixer_agent="builder",
        reviewer_role="reviewer",
        fixer_role="review-fixer",
        max_attempts=3,
        approval_marker="REVIEW_RESULT: APPROVED",
    )

    result = run_review_loop(
        flow=flow,
        config=config,
        step=step_number,
        reviewer_input="Review the implementation...",
        fixer_input_fn=lambda report: f"Fix issues: {report}",
        after_agent_id=last_agent_id,
        logger=logger,
    )
"""

from __future__ import annotations

import logging
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING

from flow.types import ExitReason

if TYPE_CHECKING:
    from flow import Flow


@dataclass
class ReviewLoopConfig:
    """Configuration for a review loop.

    Attributes:
        reviewer_agent: Agent type for the reviewer (e.g., "reviewer").
        fixer_agent: Agent type for the fixer (e.g., "builder").
        reviewer_role: Role name for reviewer agents (e.g., "reviewer", "doc-reviewer").
        fixer_role: Role name for fixer agents (e.g., "review-fixer", "doc-fixer").
        max_attempts: Maximum review-fix cycles before giving up (default: 3).
        approval_marker: String to look for in reviewer report to indicate approval.
        auto_retry: Number of auto-retries for stuck/timeout agents (default: 3).
        fail_flow_on_max_attempts: If True (default), reaching max attempts is an error.
            If False, reaching max attempts logs a warning but returns non-approved.
    """

    reviewer_agent: str
    fixer_agent: str
    reviewer_role: str
    fixer_role: str
    max_attempts: int = 3
    approval_marker: str = "REVIEW_RESULT: APPROVED"
    auto_retry: int = 3
    fail_flow_on_max_attempts: bool = True


@dataclass
class ReviewLoopResult:
    """Result of a review loop execution.

    Attributes:
        approved: True if the reviewer approved, False otherwise.
        final_agent_id: The ID of the last agent that ran (reviewer or fixer).
        attempts: Number of review-fix cycles that were executed (1-indexed).
        report_file: Path to the final reviewer's report file, or None.
    """

    approved: bool
    final_agent_id: str | None
    attempts: int
    report_file: str | None


def check_review_approved(report_file: str | None, approval_marker: str) -> bool:
    """Check if a reviewer's report indicates approval.

    The approval marker must appear as a standalone statement, not within
    quoted or backtick-enclosed text. This prevents false positives when
    the marker is mentioned in explanations like:
        "To issue `REVIEW_RESULT: APPROVED`:"

    Args:
        report_file: Path to the reviewer's report file.
        approval_marker: String marker that indicates approval.

    Returns:
        True if the approval marker is found as a standalone statement,
        False otherwise.
    """
    if not report_file:
        return False
    try:
        content = Path(report_file).read_text(encoding="utf-8")
        return _is_standalone_marker(content, approval_marker)
    except FileNotFoundError:
        return False


def _is_standalone_marker(content: str, marker: str) -> bool:
    """Check if marker appears as standalone text, not within quotes or backticks.

    A marker is considered standalone if it is NOT immediately preceded by:
    - Backtick (`)
    - Double quote (")
    - Single quote (')

    Args:
        content: The text content to search.
        marker: The marker string to find.

    Returns:
        True if a standalone occurrence is found, False otherwise.
    """
    import re

    # Escape the marker for use in regex
    escaped_marker = re.escape(marker)

    # Pattern: marker NOT preceded by `, ", or '
    # Uses negative lookbehind to ensure no quote character immediately before
    pattern = rf'(?<![`"\']){escaped_marker}'

    return bool(re.search(pattern, content))


def check_step_review_approved(
    flow: Flow,
    step: int,
    approval_marker: str,
    role: str = "reviewer",
) -> bool:
    """Check if any reviewer agent approved the step.

    This is used when resuming a flow to detect if the reviewer stage
    already completed successfully.

    Args:
        flow: The Flow instance.
        step: Step number to check.
        approval_marker: String marker that indicates approval.
        role: Role name to look for (default: "reviewer").

    Returns:
        True if a reviewer for this step approved (has approval_marker in report).
    """
    from flow.lib.flow_status import read_status

    agent_id = flow.get_last_agent_id(step=step, role=role)
    if not agent_id:
        return False

    # Get the report file path from status
    status = read_status(flow.name, base_dir=flow.base_dir)
    if status is None:
        return False

    agent = status.agents.get(agent_id)
    if agent and agent.completion_file:
        return check_review_approved(agent.completion_file, approval_marker)
    return False


def check_doc_review_approved(
    flow: Flow,
    step: int,
    approval_marker: str,
) -> bool:
    """Check if documentation reviewer already approved (for resume support).

    This is used when resuming a flow to detect if the documentation review stage
    already completed successfully.

    Args:
        flow: The Flow instance.
        step: Step number to check (typically the last step number).
        approval_marker: String marker that indicates approval.

    Returns:
        True if a doc-reviewer for this step approved (has approval_marker in report).
    """
    return check_step_review_approved(flow, step, approval_marker, role="doc-reviewer")


def run_review_loop(
    flow: Flow,
    config: ReviewLoopConfig,
    step: int,
    reviewer_input: str,
    fixer_input_fn: Callable[[str | None], str],
    after_agent_id: str | None,
    logger: logging.Logger,
    log_prefix: str = "  [Review] ",
    reviewer_preamble: str | None = None,
    plan_path: str | None = None,
    run_post_fix_validation: Callable[[], tuple[bool, str]] | None = None,
    validation_fix_input_fn: Callable[[str], str] | None = None,
    max_validation_fix_attempts: int = 3,
    initiated_by: str = "flow.api",
) -> ReviewLoopResult:
    """Run a review loop until approved or max attempts reached.

    This function encapsulates the review -> fix -> re-review pattern used
    throughout the implement.py orchestration script. It handles:
    - Running the reviewer agent
    - Checking for approval marker in the report
    - Running the fixer agent if not approved
    - Optional post-fix validation with its own fix loop
    - Logging progress

    Args:
        flow: The Flow instance.
        config: Review loop configuration.
        step: Current step number for agent naming.
        reviewer_input: Input text for the reviewer agent.
        fixer_input_fn: Function that takes report_file path and returns fixer input text.
        after_agent_id: Agent ID to chain after (for the first reviewer).
        logger: Logger for recording events to flow.log.
        log_prefix: Prefix for log messages (default: "  [Review] ").
        reviewer_preamble: Optional preamble for reviewer assignment section.
        plan_path: Optional path to plan file for reviewer context.
        run_post_fix_validation: Optional validation function returning (passed, output).
            If provided with validation_fix_input_fn, runs validation after each fix
            and triggers additional fix cycles if validation fails.
        validation_fix_input_fn: Function to generate validation fix input from output.
            Required if run_post_fix_validation is provided.
        max_validation_fix_attempts: Max validation fix attempts per review cycle (default: 3).
        initiated_by: Initiator string for breadcrumbs (default: "flow.api").

    Returns:
        ReviewLoopResult with approval status and final agent info.
    """
    from flow import AgentInfo
    from flow.lib.logging_setup import log_and_print

    last_agent_id = after_agent_id

    for attempt in range(config.max_attempts):
        # Run reviewer
        review_result = flow.run(
            config.reviewer_agent,
            agent=AgentInfo(step=step, role=config.reviewer_role),
            input=reviewer_input,
            plan_path=plan_path,
            assignment_preamble=reviewer_preamble,
            after=last_agent_id,
            auto_retry=config.auto_retry,
            initiated_by=initiated_by,
        )

        # Check approval
        if check_review_approved(review_result.report_file, config.approval_marker):
            log_and_print(logger, "Review approved", print_prefix=log_prefix)
            return ReviewLoopResult(
                approved=True,
                final_agent_id=review_result.final_agent,
                attempts=attempt + 1,
                report_file=review_result.report_file,
            )

        log_and_print(
            logger,
            f"Review attempt {attempt + 1}: issues found, fixing",
            print_prefix=log_prefix,
        )

        # Run fixer
        fixer_input = fixer_input_fn(review_result.report_file)
        fix_result = flow.run(
            config.fixer_agent,
            agent=AgentInfo(step=step, role=config.fixer_role),
            input=fixer_input,
            after=review_result.final_agent,
            auto_retry=config.auto_retry,
            initiated_by=initiated_by,
        )

        if fix_result.exit_reason != ExitReason.COMPLETED:
            log_and_print(
                logger,
                f"Fix failed: {fix_result.exit_reason}",
                level="WARNING",
                print_prefix=log_prefix,
            )
            return ReviewLoopResult(
                approved=False,
                final_agent_id=fix_result.final_agent,
                attempts=attempt + 1,
                report_file=review_result.report_file,
            )

        last_agent_id = fix_result.final_agent

        # Optional post-fix validation
        if run_post_fix_validation is not None and validation_fix_input_fn is not None:
            validation_result = _run_post_fix_validation_loop(
                flow=flow,
                config=config,
                step=step,
                last_agent_id=last_agent_id,
                run_validation=run_post_fix_validation,
                validation_fix_input_fn=validation_fix_input_fn,
                max_attempts=max_validation_fix_attempts,
                logger=logger,
                initiated_by=initiated_by,
            )
            if validation_result is None:
                # Validation failed after max attempts
                return ReviewLoopResult(
                    approved=False,
                    final_agent_id=last_agent_id,
                    attempts=attempt + 1,
                    report_file=review_result.report_file,
                )
            last_agent_id = validation_result

    # Max attempts reached
    log_and_print(
        logger,
        f"Review not approved after {config.max_attempts} attempts",
        level="WARNING" if not config.fail_flow_on_max_attempts else "ERROR",
        print_prefix=log_prefix,
    )

    return ReviewLoopResult(
        approved=False,
        final_agent_id=last_agent_id,
        attempts=config.max_attempts,
        report_file=None,
    )


def _run_post_fix_validation_loop(
    flow: Flow,
    config: ReviewLoopConfig,
    step: int,
    last_agent_id: str | None,
    run_validation: Callable[[], tuple[bool, str]],
    validation_fix_input_fn: Callable[[str], str],
    max_attempts: int,
    logger: logging.Logger,
    initiated_by: str,
) -> str | None:
    """Run validation after fixes, with fix loop if validation fails.

    This helper handles the validation -> fix -> revalidate pattern that
    happens after review fixes.

    Args:
        flow: The Flow instance.
        config: Review loop configuration (for agent types and auto_retry).
        step: Current step number.
        last_agent_id: Agent ID to chain after.
        run_validation: Validation function returning (passed, output).
        validation_fix_input_fn: Function to generate fix input from validation output.
        max_attempts: Maximum validation fix attempts.
        logger: Logger for recording events.
        initiated_by: Initiator string for breadcrumbs.

    Returns:
        Final agent ID if validation passed, None if validation failed after max attempts.
    """
    from flow import AgentInfo
    from flow.lib.logging_setup import log_and_print

    log_prefix = "  [Validation] "
    current_agent_id = last_agent_id

    log_and_print(logger, "Running post-fix validation", print_prefix=log_prefix)
    passed, validation_output = run_validation()

    if passed:
        log_and_print(logger, "All checks passed", print_prefix=log_prefix)
        return current_agent_id

    # Validation failed, enter fix loop
    for val_attempt in range(max_attempts - 1):
        log_and_print(logger, "Post-fix validation failed, fixing", print_prefix=log_prefix)

        fix_input = validation_fix_input_fn(validation_output)
        val_fix_result = flow.run(
            config.fixer_agent,
            agent=AgentInfo(step=step, role="validation-fixer"),
            input=fix_input,
            after=current_agent_id,
            auto_retry=config.auto_retry,
            initiated_by=initiated_by,
        )

        if val_fix_result.exit_reason != ExitReason.COMPLETED:
            log_and_print(
                logger,
                f"Validation fix failed: {val_fix_result.exit_reason}",
                level="ERROR",
                print_prefix=log_prefix,
            )
            return None

        current_agent_id = val_fix_result.final_agent

        revalidate_passed, validation_output = run_validation()
        if revalidate_passed:
            log_and_print(logger, "All checks passed", print_prefix=log_prefix)
            return current_agent_id

    # Max validation fix attempts reached
    log_and_print(
        logger,
        "Validation failed after review fixes",
        level="ERROR",
        print_prefix=log_prefix,
    )
    return None
