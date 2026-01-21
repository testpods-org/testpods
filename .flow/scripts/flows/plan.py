#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = [
#     "flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli",
# ]
# ///
"""Planning flow script for the Flow CLI.

This script creates high-quality, reviewed spec plans from feature ideas.
It runs BEFORE implementation to produce structured, reviewed plans ready
for implement.py.

Workflow (4 steps + 2 human checkpoints):
1. Analyze - Parse idea, collect clarifications/suggestions
2. Draft - Create spec plan draft with showcase placeholder
3. Showcase - Add showcase requirements section
   → [Human 3.5: Review draft, inline feedback support]
4. Review - Single unified review with fixer + showcase regeneration loop
   → [Human 4.5: Final review, inline feedback support]
   → Finalize

Usage:
    uv run .flow/scripts/flows/plan.py "feature idea description" [options]
    uv run .flow/scripts/flows/plan.py ideas/my-feature.md  # Read idea from file
    uv run .flow/scripts/flows/plan.py "Add user authentication" --name auth-feature
    uv run .flow/scripts/flows/plan.py "Fix caching bug" --info "Focus on Redis cache"
    uv run .flow/scripts/flows/plan.py "Add logging" --reset  # Start fresh

Options:
    --name <name>       Feature name for output file (default: auto-generated)
    --info <context>    Additional context for agents
    --reset             Start fresh (default: resume existing state)
"""

from __future__ import annotations

import sys
from pathlib import Path

# Add lib directory to path for standalone uv execution
# Use .resolve() to handle both direct execution and exec() via shim
sys.path.insert(0, str(Path(__file__).resolve().parent ))

import argparse  # noqa: E402
import logging  # noqa: E402
import re  # noqa: E402
import traceback  # noqa: E402
from datetime import UTC, datetime  # noqa: E402

from rich.console import Console  # noqa: E402
from rich.markdown import Markdown  # noqa: E402

from flow import AgentInfo, Flow  # noqa: E402
from flow.lib.flow_status import read_status  # noqa: E402
from flow.lib.logging_setup import get_flow_logger, log_and_print  # noqa: E402
from flow.types import ExitReason  # noqa: E402
from proof.showcase import (  # noqa: E402
    SHOWCASE_PLACEHOLDER,
    has_showcase_section,
)
from lib import (  # noqa: E402
    InfoTracker,
    ReviewLoopConfig,
    clean_feedback_markers,
    display_clarification_questions,
    display_diff,
    display_suggestions,
    parse_clarification_from_report,
    parse_suggestions_from_report,
    prompt_human_review,
    resolve_feedback_markers,
)

# Rich console for markdown rendering
console = Console()

# Configuration
MAX_REVIEW_FIX_ATTEMPTS = 3
MAX_BUILD_RETRIES = 3
REVIEW_APPROVAL_MARKER = "REVIEW_RESULT: APPROVED"

# Preamble for reviewer agents
REVIEWER_PREAMBLE = (
    "It is now your job to review the spec plan for completeness, clarity, "
    "anti-code compliance, and consistency. Check that all sections are present "
    "and the implementation steps are clear and actionable."
)

# Instructions for inline feedback resolution
INLINE_FEEDBACK_INSTRUCTIONS = (
    "\n\nIMPORTANT: Do NOT remove or modify the [FEEDBACK: ...] markers "
    "in the draft file. Leave them exactly as they are - the orchestration "
    "system will automatically mark them as resolved.\n\n"
    "For each feedback item you address, include in your report:\n"
    "FEEDBACK: [the original feedback text]\n"
    "RESOLUTION: [what you did to address it]"
)

# Strict preservation instructions for fixer agents
FIXER_PRESERVATION_INSTRUCTIONS = (
    "\n\nCRITICAL PRESERVATION RULES - You MUST follow these:\n"
    "1. ONLY fix the SPECIFIC issues mentioned in the reviewer's feedback\n"
    "2. NEVER delete steps, sections, or content unless the reviewer EXPLICITLY said to delete them\n"
    "3. NEVER rewrite or rephrase content that wasn't flagged as problematic\n"
    "4. Make MINIMAL, SURGICAL changes - change only what is necessary to address each issue\n"
    "5. PRESERVE the original intent, emphasis, and wording of unflagged content\n"
    "6. If unsure whether something should be changed, DON'T change it\n\n"
    "VIOLATION OF THESE RULES (deleting unflagged content, rewriting approved sections, "
    "or making changes beyond the reviewer's feedback) is a CRITICAL ERROR."
)

# Single unified review loop configuration (replaces old structural/final distinction)
REVIEW_CONFIG = ReviewLoopConfig(
    reviewer_agent="plan-reviewer",
    fixer_agent="plan-creator",
    reviewer_role="reviewer",
    fixer_role="fixer",
    max_attempts=MAX_REVIEW_FIX_ATTEMPTS,
    approval_marker=REVIEW_APPROVAL_MARKER,
    fail_flow_on_max_attempts=True,
)


def _get_draft_dir(feature_name: str) -> Path:
    """Get the directory for a spec plan's draft and snapshots.

    Args:
        feature_name: Name of the feature/spec plan.

    Returns:
        Path to the draft directory (specs/drafts/{feature_name}/).
    """
    return Path("specs/drafts") / feature_name


def _save_snapshot(draft_path: Path, snapshot_name: str) -> Path:
    """Save a snapshot of the current draft content.

    Args:
        draft_path: Path to the current draft file.
        snapshot_name: Name for the snapshot (e.g., "step2", "step3").

    Returns:
        Path to the saved snapshot file.
    """
    snapshot_path = draft_path.parent / f"snapshot-{snapshot_name}.md"
    content = draft_path.read_text(encoding="utf-8")
    snapshot_path.write_text(content, encoding="utf-8")
    return snapshot_path


def _load_snapshot(draft_path: Path, snapshot_name: str) -> str | None:
    """Load a snapshot's content.

    Args:
        draft_path: Path to the draft file (used to find snapshot directory).
        snapshot_name: Name of the snapshot to load.

    Returns:
        The snapshot content, or None if snapshot doesn't exist.
    """
    snapshot_path = draft_path.parent / f"snapshot-{snapshot_name}.md"
    if not snapshot_path.exists():
        return None
    return snapshot_path.read_text(encoding="utf-8")


def _display_changes_since_snapshot(
    draft_path: Path,
    snapshot_name: str,
    old_label: str,
    new_label: str = "Current Draft",
) -> bool:
    """Display diff between a snapshot and the current draft.

    Args:
        draft_path: Path to the current draft file.
        snapshot_name: Name of the snapshot to compare against.
        old_label: Label for the snapshot version in diff output.
        new_label: Label for the current version in diff output.

    Returns:
        True if changes were displayed, False if snapshot not found.
    """
    old_content = _load_snapshot(draft_path, snapshot_name)
    if old_content is None:
        return False

    new_content = draft_path.read_text(encoding="utf-8")
    display_diff(old_content, new_content, old_label=old_label, new_label=new_label)
    return True


def _delete_draft_dir(draft_path: Path) -> None:
    """Delete the entire draft directory including all snapshots.

    Called when the spec plan is finalized to clean up.

    Args:
        draft_path: Path to the draft file.
    """
    import shutil

    draft_dir = draft_path.parent
    if draft_dir.exists() and draft_dir.name != "drafts":  # Safety check
        shutil.rmtree(draft_dir)


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a reviewed spec plan from a feature idea")
    parser.add_argument("idea", type=str, help="Feature idea text or path to idea file")
    parser.add_argument(
        "--name",
        type=str,
        default=None,
        help="Feature name for output file (default: auto-generated from idea)",
    )
    parser.add_argument(
        "--info",
        type=str,
        default=None,
        help="Additional context for agents",
    )
    parser.add_argument(
        "--reset",
        action="store_true",
        help="Reset flow state and start fresh (default: resume existing state)",
    )
    args = parser.parse_args()

    # Check if idea is a file path - if so, read contents and use filename as default name
    idea_path = Path(args.idea)
    if idea_path.exists() and idea_path.is_file():
        idea_text = idea_path.read_text(encoding="utf-8").strip()
        default_name = idea_path.stem  # filename without extension
    else:
        idea_text = args.idea
        default_name = generate_feature_name(args.idea)

    # Use provided name or default
    feature_name = args.name or default_name

    # Determine paths (outside context manager since they don't depend on flow)
    # Draft directory: contains draft.md and snapshot files
    draft_dir = _get_draft_dir(feature_name)
    draft_dir.mkdir(parents=True, exist_ok=True)
    draft_path = draft_dir / "draft.md"

    # Save original input as snapshot (supports diffing against original for both file and string inputs)
    input_snapshot_path = draft_dir / "snapshot-input.md"
    if not input_snapshot_path.exists():
        input_snapshot_path.write_text(idea_text, encoding="utf-8")
    # Final spec path: clean version written after final approval
    final_spec_path = Path("specs") / f"{feature_name}.md"

    # Initialize flow with context manager for automatic cleanup on interruption
    with Flow(
        f"plan_{feature_name}",
        reset=args.reset,
        flow_type="plan",
    ) as flow:
        # Get orchestration logger for detailed flow.log entries
        logger = get_flow_logger(flow.name, "orchestration")

        log_and_print(logger, f"=== Planning: {feature_name} ===")
        log_and_print(logger, f"Flow folder: {Path(flow.base_dir) / flow.name}")
        log_and_print(logger, f"Draft: {draft_path}")
        log_and_print(logger, f"Final output: {final_spec_path}")
        log_and_print(logger, f"Mode: {'reset (fresh start)' if args.reset else 'resume (continuing existing)'}")
        if args.info:
            log_and_print(logger, f"Additional context: {args.info}")

        # Track --info usage: add to first agent that runs (handles resume from any stage)
        info_tracker = InfoTracker(args.info)

        # Planning log path for context
        planning_log_path = Path(flow.base_dir) / flow.name / "planning-log.md"

        try:
            # STEPS 1-3: Create (analyze + draft + showcase)
            log_and_print(logger, "Steps 1-3: Create Spec Plan", print_prefix="\n=== ")
            step1_ok, last_agent_id, suggested_name = run_step1_create(
                flow=flow,
                idea=idea_text,
                draft_path=draft_path,
                planning_log_path=planning_log_path,
                info_tracker=info_tracker,
                logger=logger,
            )
            if not step1_ok:
                flow.set_status("failed")
                sys.exit("Steps 1-3 (Create) failed")

            # Use suggested name for final spec if available
            if suggested_name:
                final_spec_path = Path("specs") / f"{suggested_name}.md"
                log_and_print(logger, f"Using suggested name for spec: {suggested_name}", print_prefix="  ")

            # Save snapshot for diff display at human checkpoint
            _save_snapshot(draft_path, "step3")

            # HUMAN CHECKPOINT 3.5
            if _is_step_completed(flow, 3):
                log_and_print(
                    logger,
                    "Step 3.5: Human Review (Draft) - already approved",
                    print_prefix="\n=== ",
                )
                # Get the last agent from step 3 for chaining
                last_agent_id = flow.get_last_agent_id(step=3, role="human-feedback-fixer")
                if not last_agent_id:
                    last_agent_id = flow.get_last_agent_id(step=3, role="showcase-regenerator")
                if not last_agent_id:
                    last_agent_id = flow.get_last_agent_id(step=3, role="showcase")
            else:
                log_and_print(logger, "Step 3.5: Human Review (Draft)", print_prefix="\n=== ")

                # Display diff showing transformation from original input to draft
                _display_changes_since_snapshot(
                    draft_path,
                    snapshot_name="input",
                    old_label="Original Input",
                    new_label="Draft (after Step 1)",
                )

                human_approved, last_agent_id = run_human_approval_checkpoint(
                    flow=flow,
                    draft_path=draft_path,
                    planning_log_path=planning_log_path,
                    info_tracker=info_tracker,
                    logger=logger,
                    checkpoint_name="Draft Review",
                    last_agent_id=last_agent_id,
                    step=3,
                    regenerate_showcase=True,
                )
                if not human_approved:
                    flow.set_status("failed")
                    sys.exit("Step 3.5 (Human Approval - Draft) failed")
                # Mark step 3 (including human approval) as completed
                flow.mark_step_completed(3)
                log_and_print(logger, "Step 3 marked complete", print_prefix="  ")

                # Save snapshot after human approves draft (baseline for Step 2 diff)
                _save_snapshot(draft_path, "human1")
                log_and_print(logger, "Snapshot saved: human1 (after draft approval)", print_prefix="  ")

            # STEP 4: Single Review
            log_and_print(logger, "Step 4: Review", print_prefix="\n=== ")
            step4_ok, last_agent_id = run_step4_review(
                flow=flow,
                draft_path=draft_path,
                planning_log_path=planning_log_path,
                info_tracker=info_tracker,
                logger=logger,
                after_agent_id=last_agent_id,
            )
            if not step4_ok:
                flow.set_status("failed")
                sys.exit("Step 4 (Review) failed")

            # HUMAN CHECKPOINT 4.5
            if _is_step_completed(flow, 4):
                log_and_print(
                    logger,
                    "Step 4.5: Human Review (Final) - already approved",
                    print_prefix="\n=== ",
                )
            else:
                log_and_print(logger, "Step 4.5: Human Review (Final)", print_prefix="\n=== ")

                # Display diff showing what changed since draft approval
                _display_changes_since_snapshot(
                    draft_path,
                    snapshot_name="human1",
                    old_label="Draft (after Your Approval)",
                    new_label="Current (after Review)",
                )

                # Get report path for display
                report_agent = flow.get_last_agent_id(step=4, role="human-feedback-fixer")
                if not report_agent:
                    report_agent = flow.get_last_agent_id(step=4, role="reviewer")
                initial_report = _get_agent_report_path(flow, report_agent)

                human_approved, _ = run_human_approval_checkpoint(
                    flow=flow,
                    draft_path=draft_path,
                    planning_log_path=planning_log_path,
                    info_tracker=info_tracker,
                    logger=logger,
                    checkpoint_name="Final Review",
                    last_agent_id=last_agent_id,
                    step=4,
                    regenerate_showcase=True,
                    mark_step_completed=True,
                    initial_report_path=initial_report,
                )
                if not human_approved:
                    flow.set_status("failed")
                    sys.exit("Step 4.5 (Human Approval - Final) failed")

            # FINALIZE
            log_and_print(logger, "Finalize Spec", print_prefix="\n=== ")
            draft_content = draft_path.read_text(encoding="utf-8")
            clean_content = clean_feedback_markers(draft_content)
            final_spec_path.write_text(clean_content, encoding="utf-8")
            log_and_print(logger, f"Final spec written to: {final_spec_path}", print_prefix="  ")

            # Delete the draft directory (includes draft and all snapshots)
            _delete_draft_dir(draft_path)
            log_and_print(logger, f"Draft directory deleted: {draft_path.parent}", print_prefix="  ")

            # All steps completed successfully
            flow.set_status("completed")

            # Show completion statistics
            from flow.lib.flow_stats import format_stats_report, get_flow_stats

            stats = get_flow_stats(flow.name, base_dir=flow.base_dir, fixer_pattern="fixer")
            if stats:
                print("\n" + "=" * 60)
                report = format_stats_report(stats, verbosity="normal")
                print(report)
                print("=" * 60)

            print(f"\n✅ Spec plan created: {final_spec_path.absolute()}")
            print(f"   Ready for implementation with: uv run .flow/scripts/flows/implement.py {final_spec_path}")

        except Exception as e:
            # Log the error to flow.log
            logger = get_flow_logger(flow.name, "orchestration")
            logger.error(f"Flow crashed: {e}")
            logger.error(traceback.format_exc())

            # Mark flow as failed on any crash
            flow.set_status("failed")
            raise


def _parse_feature_name_from_report(report_content: str) -> str | None:
    """Extract the suggested feature name from an analyzer report.

    Looks for `FEATURE_NAME:` followed by a kebab-case name.

    Args:
        report_content: The full text content of the analyzer report.

    Returns:
        The suggested feature name, or None if not found.
    """
    # Pattern to match FEATURE_NAME: followed by a kebab-case name
    pattern = re.compile(r"FEATURE_NAME:\s*`?([a-z0-9-]+)`?", re.IGNORECASE)
    match = pattern.search(report_content)
    if match:
        return match.group(1).lower()
    return None


def generate_feature_name(idea: str) -> str:
    """Generate a kebab-case feature name from an idea description.

    Takes the first few significant words from the idea and converts them
    to a URL-friendly kebab-case identifier.

    Args:
        idea: The feature idea description.

    Returns:
        A kebab-case feature name (e.g., "add-user-authentication").
    """
    # Remove special characters except spaces and hyphens
    cleaned = re.sub(r"[^\w\s-]", "", idea.lower())
    # Split into words
    words = cleaned.split()
    # Take first 5 words max, filter out very short words
    significant_words = [w for w in words if len(w) > 2][:5]
    # Join with hyphens
    if significant_words:
        return "-".join(significant_words)
    # Fallback if no significant words
    return "feature-plan"


def run_step1_create(
    flow: Flow,
    idea: str,
    draft_path: Path,
    planning_log_path: Path,
    info_tracker: InfoTracker,
    logger: logging.Logger,
) -> tuple[bool, str | None, str | None]:
    """Run Step 1: Create phase (analyze with clarification pause + draft + showcase).

    This combines the previous steps 1 (analysis), 2 (draft), and 4 (showcase) into
    a single creation phase. Preserves early clarification collection.

    Returns:
        Tuple of (success, last_agent_id, suggested_feature_name).
        suggested_feature_name is a kebab-case name from the analyzer, or None.
    """
    last_agent_id: str | None = None
    suggested_name: str | None = None

    # STEP 1: Analyze (with clarification pause)
    if flow.has_completed_agent(step=1, role="analyzer"):
        log_and_print(logger, "Analyzer already completed", print_prefix="  [Skipping] ")
        last_agent_id = flow.get_last_agent_id(step=1, role="analyzer")
        # Try to get suggested name from existing report
        report_path = _get_agent_report_path(flow, last_agent_id)
        if report_path:
            report_content = report_path.read_text(encoding="utf-8")
            suggested_name = _parse_feature_name_from_report(report_content)
    else:
        log_and_print(logger, "Step 1: Analyze", print_prefix="  ")

        # Run plan-creator to analyze the idea
        analysis_input = info_tracker.add_to_input(
            f"Analyze this feature idea and identify any clarifications needed:\n\n{idea}\n\n"
            "REQUIRED: Include a `FEATURE_NAME:` line with a kebab-case name for this feature.\n"
            "The name should describe what the feature does, not the input prompt.\n"
            "Example: `FEATURE_NAME: context-aware-agent-stopping`\n\n"
            "If you have questions, include a `CLARIFICATION_NEEDED:` section with numbered questions.\n"
            "If you have suggestions for improvement or consolidation, include a `SUGGESTIONS:` section.\n\n"
            "IMPORTANT: For both questions and suggestions, provide sufficient context so a human "
            "reviewer can understand and assess them. Include:\n"
            "- What the current state is (with SPECIFIC file paths, line numbers, or values)\n"
            "- Why you're suggesting this (the reasoning)\n"
            "- What the impact would be\n\n"
            "CRITICAL: When suggestions involve files or code, you MUST include:\n"
            "- The exact file paths or code references that are wrong/missing\n"
            "- The correct values or locations\n\n"
            "Example 1 - Adding to a list:\n"
            "1. Add `completion-workflow-template.md` to Step 3's template list.\n"
            "   - Currently: Step 3 only lists `spec-plan-template.md` and `idea-template.md`\n"
            "   - Reason: The feature involves completion workflows, and this template defines the structure\n"
            "   - Impact: Ensures agents have the correct template structure for workflow files\n\n"
            "Example 2 - Correcting file references:\n"
            "1. Update Step 1 to use correct file paths.\n"
            "   - Currently: Step 1 references `flow/lib/monitor.py` and `flow/lib/spawner.py`\n"
            "   - Correct paths: `flow/lib/agent_monitor.py` and `flow/lib/agent_spawner.py`\n"
            "   - Reason: The spec uses outdated file names that don't exist in the codebase\n"
            "   - Impact: Builders will fail immediately when trying to modify non-existent files"
        )

        result = flow.run(
            "plan-creator",
            agent=AgentInfo(step=1, role="analyzer"),
            input=analysis_input,
            auto_retry=MAX_BUILD_RETRIES,
            initiated_by="flow.api",
        )

        if result.exit_reason != ExitReason.COMPLETED:
            log_and_print(logger, f"Analysis failed: {result.exit_reason}", level="ERROR", print_prefix="  ")
            return False, result.final_agent, None

        last_agent_id = result.final_agent

        # Parse the report for clarifications, suggestions, and feature name
        if result.report_file and Path(result.report_file).exists():
            report_content = Path(result.report_file).read_text(encoding="utf-8")

            # Extract suggested feature name
            suggested_name = _parse_feature_name_from_report(report_content)
            if suggested_name:
                log_and_print(logger, f"Suggested feature name: {suggested_name}", print_prefix="  ")

            # Handle clarifications (pause for human input)
            questions = parse_clarification_from_report(report_content)
            if questions:
                log_and_print(logger, f"Found {len(questions)} clarification question(s)", print_prefix="  ")
                answers = display_clarification_questions(questions)
                # Store in planning log for drafter context
                _append_clarifications_to_log(planning_log_path, questions, answers)
                log_and_print(logger, "Clarifications recorded in planning log", print_prefix="  ")

            # Handle suggestions
            suggestions = parse_suggestions_from_report(report_content)
            if suggestions:
                log_and_print(logger, f"Found {len(suggestions)} suggestion(s)", print_prefix="  ")
                response = display_suggestions(suggestions)
                # Store in planning log
                _append_suggestions_to_log(planning_log_path, suggestions, response)
                log_and_print(logger, "Suggestions recorded in planning log", print_prefix="  ")

        log_and_print(logger, "Analysis complete", print_prefix="  ")

    # STEP 2: Draft
    if flow.has_completed_agent(step=2, role="drafter"):
        log_and_print(logger, "Drafter already completed", print_prefix="  [Skipping] ")
        last_agent_id = flow.get_last_agent_id(step=2, role="drafter")
    else:
        log_and_print(logger, "Step 2: Draft", print_prefix="  ")

        # Read planning log for context (includes clarifications)
        planning_context = ""
        if planning_log_path.exists():
            planning_context = planning_log_path.read_text(encoding="utf-8")

        # Build the draft input
        draft_input_parts = [
            f"Draft the complete spec plan for this feature idea:\n\n{idea}",
        ]
        if planning_context:
            draft_input_parts.append(f"\nContext from analysis phase:\n\n{planning_context}")
        draft_input_parts.append(
            f"\n\nWrite the spec plan draft to: {draft_path}\n\n"
            f"IMPORTANT: Include the placeholder marker `{SHOWCASE_PLACEHOLDER}` in the plan "
            "where the Showcase Requirements section should be inserted. The showcase-planner "
            "agent will replace this marker with the actual showcase section."
        )

        draft_input = info_tracker.add_to_input("\n".join(draft_input_parts))

        result = flow.run(
            "plan-creator",
            agent=AgentInfo(step=2, role="drafter"),
            input=draft_input,
            after=last_agent_id,
            auto_retry=MAX_BUILD_RETRIES,
            initiated_by="flow.api",
        )

        if result.exit_reason != ExitReason.COMPLETED:
            log_and_print(logger, f"Draft failed: {result.exit_reason}", level="ERROR", print_prefix="  ")
            return False, result.final_agent, suggested_name

        last_agent_id = result.final_agent

        # Verify the draft file was created
        if not draft_path.exists():
            log_and_print(logger, f"Draft file not created at {draft_path}", level="ERROR", print_prefix="  ")
            return False, last_agent_id, suggested_name

        # Verify the placeholder is present
        draft_content = draft_path.read_text(encoding="utf-8")
        if SHOWCASE_PLACEHOLDER not in draft_content:
            log_and_print(
                logger,
                "Warning: Showcase placeholder not found in draft. Adding it.",
                level="WARNING",
                print_prefix="  ",
            )
            # Try to insert before ## Implementation Steps
            impl_marker = "## Implementation Steps"
            if impl_marker in draft_content:
                draft_content = draft_content.replace(
                    impl_marker,
                    f"{SHOWCASE_PLACEHOLDER}\n\n---\n\n{impl_marker}",
                )
                draft_path.write_text(draft_content, encoding="utf-8")
            else:
                log_and_print(
                    logger,
                    "Could not find insertion point for placeholder",
                    level="ERROR",
                    print_prefix="  ",
                )
                return False, last_agent_id, suggested_name

        log_and_print(logger, f"Draft complete: {draft_path}", print_prefix="  ")

    # STEP 3: Showcase
    if flow.has_completed_agent(step=3, role="showcase"):
        log_and_print(logger, "Showcase already completed", print_prefix="  [Skipping] ")
        last_agent_id = flow.get_last_agent_id(step=3, role="showcase")
    else:
        log_and_print(logger, "Step 3: Showcase", print_prefix="  ")

        # Verify the draft file exists and has placeholder
        if not draft_path.exists():
            log_and_print(logger, f"Draft file not found: {draft_path}", level="ERROR", print_prefix="  ")
            return False, last_agent_id, suggested_name

        draft_content = draft_path.read_text(encoding="utf-8")
        if SHOWCASE_PLACEHOLDER not in draft_content:
            # Check if section already exists
            if has_showcase_section(draft_path):
                log_and_print(logger, "Showcase section already exists", print_prefix="  ")
                return True, last_agent_id, suggested_name
            log_and_print(
                logger,
                "Neither placeholder nor existing showcase section found",
                level="ERROR",
                print_prefix="  ",
            )
            return False, last_agent_id, suggested_name

        # Read planning log for context
        planning_context = ""
        if planning_log_path.exists():
            planning_context = planning_log_path.read_text(encoding="utf-8")

        # Build showcase input with optional context
        showcase_input_parts = [
            f"Analyze the spec plan draft at {draft_path} and create the Showcase Requirements section.",
        ]
        if planning_context:
            showcase_input_parts.append(f"\nContext from planning:\n\n{planning_context}")
        showcase_input_parts.append(
            f"\n\nThe plan contains the placeholder marker `{SHOWCASE_PLACEHOLDER}` where you should "
            "insert the showcase section. Replace the placeholder with a complete "
            "`## Showcase Requirements` section containing a YAML specification of CLI demonstrations "
            "that prove the implementation works."
        )
        showcase_input = "\n".join(showcase_input_parts)

        result = flow.run(
            "showcase-planner",
            agent=AgentInfo(step=3, role="showcase"),
            input=showcase_input,
            after=last_agent_id,
            auto_retry=MAX_BUILD_RETRIES,
            initiated_by="flow.api",
        )

        if result.exit_reason != ExitReason.COMPLETED:
            log_and_print(logger, f"Showcase planning failed: {result.exit_reason}", level="ERROR", print_prefix="  ")
            return False, result.final_agent, suggested_name

        last_agent_id = result.final_agent

        # Verify the showcase section was added
        draft_content = draft_path.read_text(encoding="utf-8")
        if SHOWCASE_PLACEHOLDER in draft_content:
            log_and_print(
                logger,
                "Warning: Placeholder still present after showcase planning",
                level="WARNING",
                print_prefix="  ",
            )
            return False, last_agent_id, suggested_name

        if not has_showcase_section(draft_path):
            log_and_print(
                logger,
                "Showcase section not found in draft after planning",
                level="ERROR",
                print_prefix="  ",
            )
            return False, last_agent_id, suggested_name

        log_and_print(logger, "Showcase section added", print_prefix="  ")

    log_and_print(logger, "Steps 1-3 complete (analyze + draft + showcase)", print_prefix="  ")
    return True, last_agent_id, suggested_name


def run_step4_review(
    flow: Flow,
    draft_path: Path,
    planning_log_path: Path,
    info_tracker: InfoTracker,
    logger: logging.Logger,
    after_agent_id: str | None,
) -> tuple[bool, str | None]:
    """Run Step 4: Single unified review phase with showcase regeneration.

    Uses comprehensive review prompt covering all criteria.

    Review loop:
    - Runs plan-reviewer (single comprehensive review)
    - If issues: runs plan-creator fixer, then showcase-planner regenerator
    - Repeats until approved or max attempts

    Returns:
        Tuple of (approved, last_agent_id).
    """
    # Check if reviewer already approved (agent phase complete, but human checkpoint pending)
    if _check_step_approved(flow, step=4, role="reviewer"):
        log_and_print(logger, "Review already approved by agent", print_prefix="  [Skipping] ")
        # Get the last agent from step 4 for chaining
        last_agent_id = flow.get_last_agent_id(step=4, role="showcase-regenerator")
        if not last_agent_id:
            last_agent_id = flow.get_last_agent_id(step=4, role="fixer")
        if not last_agent_id:
            last_agent_id = flow.get_last_agent_id(step=4, role="reviewer")
        return True, last_agent_id

    # Custom review loop with showcase regeneration
    last_agent_id = after_agent_id

    for attempt in range(REVIEW_CONFIG.max_attempts):
        # Unified review input covering all criteria (merges old structural + final review)
        review_input = info_tracker.add_to_input(
            f"Review the spec plan draft at: {draft_path}\n\n"
            "Verify the following (comprehensive review):\n"
            "- All required sections are present (Executive Summary, Background, Implementation Steps, Showcase)\n"
            "- Implementation steps are clear, ordered, and actionable\n"
            "- Anti-code policy is followed (no implementation code, only pseudo-code/type signatures)\n"
            "- Showcase Requirements section accurately tests the implementation\n"
            "- Plan is ready for implementation\n\n"
            f"Include '{REVIEW_APPROVAL_MARKER}' in your report if approved."
        )

        review_result = flow.run(
            REVIEW_CONFIG.reviewer_agent,
            agent=AgentInfo(step=4, role=REVIEW_CONFIG.reviewer_role),
            input=review_input,
            after=last_agent_id,
            auto_retry=REVIEW_CONFIG.auto_retry,
            initiated_by="flow.api",
        )

        # Check approval
        if review_result.report_file:
            report_path = Path(review_result.report_file)
            if report_path.exists():
                report_content = report_path.read_text(encoding="utf-8")
                if REVIEW_APPROVAL_MARKER in report_content:
                    log_and_print(logger, "Agent approved review", print_prefix="  [Review] ")
                    return True, review_result.final_agent

        log_and_print(
            logger,
            f"Review attempt {attempt + 1}: issues found, fixing",
            print_prefix="  [Review] ",
        )

        # Check for clarifications
        if review_result.report_file and Path(review_result.report_file).exists():
            report_content = Path(review_result.report_file).read_text(encoding="utf-8")
            questions = parse_clarification_from_report(report_content)
            if questions:
                log_and_print(logger, f"Reviewer needs {len(questions)} clarification(s)", print_prefix="  ")
                answers = display_clarification_questions(questions)
                _append_clarifications_to_log(planning_log_path, questions, answers)

        # Run fixer
        fixer_input = info_tracker.add_to_input(
            f"Fix the issues identified by the reviewer in: {draft_path}{FIXER_PRESERVATION_INSTRUCTIONS}"
        )

        fix_result = flow.run(
            REVIEW_CONFIG.fixer_agent,
            agent=AgentInfo(step=4, role=REVIEW_CONFIG.fixer_role),
            input=fixer_input,
            after=review_result.final_agent,
            auto_retry=REVIEW_CONFIG.auto_retry,
            initiated_by="flow.api",
        )

        if fix_result.exit_reason != ExitReason.COMPLETED:
            log_and_print(
                logger,
                f"Fix failed: {fix_result.exit_reason}",
                level="WARNING",
                print_prefix="  [Review] ",
            )
            return False, fix_result.final_agent

        last_agent_id = fix_result.final_agent

        # Regenerate showcase section after fix
        log_and_print(logger, "Regenerating showcase section", print_prefix="  [Showcase] ")

        showcase_input = (
            f"Regenerate the Showcase Requirements section in {draft_path}.\n\n"
            "The plan has been updated. Update the existing `## Showcase Requirements` section "
            "to reflect the current state of the plan. Ensure the CLI demonstrations "
            "accurately test the implementation steps as currently defined."
        )

        showcase_result = flow.run(
            "showcase-planner",
            agent=AgentInfo(step=4, role="showcase-regenerator"),
            input=showcase_input,
            after=last_agent_id,
            auto_retry=MAX_BUILD_RETRIES,
            initiated_by="flow.api",
        )

        if showcase_result.exit_reason != ExitReason.COMPLETED:
            log_and_print(
                logger,
                f"Showcase regeneration failed: {showcase_result.exit_reason}",
                level="WARNING",
                print_prefix="  [Showcase] ",
            )
            # Continue with review loop even if showcase regeneration fails
        else:
            last_agent_id = showcase_result.final_agent

    # Max attempts reached
    log_and_print(
        logger,
        f"Review not approved after {REVIEW_CONFIG.max_attempts} attempts",
        level="ERROR",
        print_prefix="  [Review] ",
    )
    return False, last_agent_id


def _get_agent_report_path(flow: Flow, agent_id: str | None) -> Path | None:
    """Get the report file path for a given agent ID.

    Uses completion_file (report.complete.md) which is the actual report file.
    The report_file field in status.json is just a placeholder path (report.md)
    that gets renamed to report.complete.md or report.failed.md.

    Args:
        flow: The Flow instance.
        agent_id: The agent ID to look up.

    Returns:
        Path to the report file, or None if not found.
    """
    if not agent_id:
        return None
    status = read_status(flow.name, base_dir=flow.base_dir)
    if status is None or agent_id not in status.agents:
        return None
    agent = status.agents[agent_id]
    if agent.completion_file:
        completion_path = Path(agent.completion_file)
        if completion_path.exists():
            return completion_path
    return None


def _check_step_approved(flow: Flow, step: int, role: str) -> bool:
    """Check if a reviewer agent approved for a given step.

    Args:
        flow: The Flow instance.
        step: Step number to check.
        role: Reviewer role to check.

    Returns:
        True if the reviewer approved, False otherwise.
    """
    agent_id = flow.get_last_agent_id(step=step, role=role)
    if not agent_id:
        return False

    status = read_status(flow.name, base_dir=flow.base_dir)
    if status is None:
        return False

    agent = status.agents.get(agent_id)
    if agent and agent.completion_file:
        try:
            content = Path(agent.completion_file).read_text(encoding="utf-8")
            return REVIEW_APPROVAL_MARKER in content
        except FileNotFoundError:
            return False
    return False


def _is_step_completed(flow: Flow, step: int) -> bool:
    """Check if a step is in the completed_steps list.

    This is the authoritative check for whether a step (including its human
    approval checkpoint) has been fully completed.

    Args:
        flow: The Flow instance.
        step: Step number to check.

    Returns:
        True if the step is in completed_steps, False otherwise.
    """
    status = read_status(flow.name, base_dir=flow.base_dir)
    if status is None:
        return False
    return step in status.completed_steps


def _append_clarifications_to_log(
    planning_log_path: Path,
    questions: list[str],
    answers: list[str],
) -> None:
    """Append clarification Q&A to the planning log.

    Args:
        planning_log_path: Path to the planning log file.
        questions: List of questions asked.
        answers: List of answers received.
    """
    if not planning_log_path.exists():
        return

    content = planning_log_path.read_text(encoding="utf-8")
    timestamp = datetime.now(UTC).strftime("%Y-%m-%d %H:%M:%S UTC")

    entry = f"\n---\n\n## Clarifications Collected\n\n**Timestamp:** {timestamp}\n\n"
    for q, a in zip(questions, answers):
        entry += f"- **{q}**: {a}\n"
    entry += "\n"

    content += entry
    planning_log_path.write_text(content, encoding="utf-8")


def _append_suggestions_to_log(
    planning_log_path: Path,
    suggestions: list[str],
    response: str,
) -> None:
    """Append suggestions and response to the planning log.

    Args:
        planning_log_path: Path to the planning log file.
        suggestions: List of suggestions shown.
        response: User response ('y', 'n', or modification text).
    """
    if not planning_log_path.exists():
        return

    content = planning_log_path.read_text(encoding="utf-8")
    timestamp = datetime.now(UTC).strftime("%Y-%m-%d %H:%M:%S UTC")

    # Determine response type
    if response.lower() == "y":
        response_text = "Accepted"
    elif response.lower() == "n":
        response_text = "Rejected"
    else:
        response_text = f"Modified: {response}"

    entry = f"\n---\n\n## Suggestions Reviewed\n\n**Timestamp:** {timestamp}\n\n"
    entry += "**Suggestions:**\n"
    for s in suggestions:
        entry += f"- {s}\n"
    entry += f"\n**User Response:** {response_text}\n\n"

    content += entry
    planning_log_path.write_text(content, encoding="utf-8")


def _append_human_feedback_to_log(
    planning_log_path: Path,
    direct_feedback: str | None,
    inline_feedback: list[tuple[str, str]] | None,
    checkpoint_name: str,
) -> None:
    """Append human review feedback to the planning log.

    Args:
        planning_log_path: Path to the planning log file.
        direct_feedback: Direct feedback text if provided.
        inline_feedback: List of (context, feedback) tuples if provided.
        checkpoint_name: Name of the checkpoint where feedback was collected.
    """
    if not planning_log_path.exists():
        return

    content = planning_log_path.read_text(encoding="utf-8")
    timestamp = datetime.now(UTC).strftime("%Y-%m-%d %H:%M:%S UTC")

    entry = f"\n---\n\n## Human Feedback ({checkpoint_name})\n\n**Timestamp:** {timestamp}\n\n"

    if direct_feedback:
        entry += f"**Direct Feedback:**\n{direct_feedback}\n\n"

    if inline_feedback:
        entry += "**Inline Feedback:**\n"
        for context, feedback in inline_feedback:
            entry += f"- **{context}**: {feedback}\n"
        entry += "\n"

    content += entry
    planning_log_path.write_text(content, encoding="utf-8")


def _format_feedback_for_agent(
    direct_feedback: str | None,
    inline_feedback: list[tuple[str, str]] | None,
) -> str:
    """Format human feedback into a string suitable for agent input.

    Args:
        direct_feedback: Direct feedback text if provided.
        inline_feedback: List of (context, feedback) tuples if provided.

    Returns:
        Formatted feedback string.
    """
    parts: list[str] = []

    if direct_feedback:
        parts.append(f"Human feedback:\n{direct_feedback}")

    if inline_feedback:
        parts.append("Human inline feedback (with location context):")
        for context, feedback in inline_feedback:
            parts.append(f"- At '{context}': {feedback}")

    return "\n".join(parts)


def _parse_resolutions_from_report(report_content: str) -> list[tuple[str, str]]:
    """Parse FEEDBACK/RESOLUTION pairs from an agent report.

    The agent is instructed to include resolution pairs in this format:
        FEEDBACK: [the original feedback text]
        RESOLUTION: [what was done to address it]

    Args:
        report_content: The full text content of the agent's completion report.

    Returns:
        List of (original_feedback, resolution) tuples.
        Returns empty list if no resolution pairs found.

    Example:
        >>> report = '''
        ... Made the requested changes.
        ...
        ... FEEDBACK: Add more detail to step 3
        ... RESOLUTION: Added implementation details with code examples
        ...
        ... FEEDBACK: Consider edge cases
        ... RESOLUTION: Added error handling section
        ... '''
        >>> resolutions = _parse_resolutions_from_report(report)
        >>> resolutions
        [('Add more detail to step 3', 'Added implementation details with code examples'),
         ('Consider edge cases', 'Added error handling section')]
    """
    resolutions: list[tuple[str, str]] = []

    # Pattern to match FEEDBACK: followed by text, then RESOLUTION: followed by text
    # Uses multiline mode to handle each on its own line
    pattern = re.compile(
        r"FEEDBACK:\s*(.+?)\s*\nRESOLUTION:\s*(.+?)(?=\n\n|\nFEEDBACK:|\Z)",
        re.IGNORECASE | re.DOTALL,
    )

    for match in pattern.finditer(report_content):
        original_feedback = match.group(1).strip()
        resolution = match.group(2).strip()
        if original_feedback and resolution:
            resolutions.append((original_feedback, resolution))

    return resolutions


def _display_agent_report(report_path: Path, logger: logging.Logger) -> None:
    """Display the full agent report with markdown rendering.

    Uses Rich to render the report as formatted markdown in the terminal.

    Args:
        report_path: Path to the agent's report file.
        logger: Logger instance for flow.log.
    """
    if not report_path.exists():
        return

    report_content = report_path.read_text(encoding="utf-8")

    # Log to flow.log (plain text, truncated)
    logger.info(f"Agent report preview: {report_content[:500]}...")

    # Print full report with Rich markdown rendering
    console.print("\n[bold cyan]═══ Agent Report ═══[/bold cyan]")
    # console.print(Markdown(report_content))  # DEBUG: Testing if Markdown causes terminal issues
    console.print(report_content)  # Plain text for testing
    console.print("[bold cyan]═══════════════════[/bold cyan]\n")


def run_human_approval_checkpoint(
    flow: Flow,
    draft_path: Path,
    planning_log_path: Path,
    info_tracker: InfoTracker,
    logger: logging.Logger,
    checkpoint_name: str,
    last_agent_id: str | None,
    *,
    step: int,
    regenerate_showcase: bool = False,
    mark_step_completed: bool = False,
    initial_report_path: Path | None = None,
) -> tuple[bool, str | None]:
    """Run a human approval checkpoint with feedback loop.

    Prompts the human to review the spec plan draft. If they provide feedback,
    spawns the plan-creator agent to make changes and re-prompts.

    Args:
        flow: The Flow instance.
        draft_path: Path to the spec plan draft file.
        planning_log_path: Path to the planning log file.
        info_tracker: InfoTracker for adding --info context.
        logger: Logger for flow logging.
        checkpoint_name: Name of this checkpoint (e.g., "Structural Review").
        last_agent_id: ID of the last agent for chaining.
        step: Step number for agent tracking (e.g., 3 or 5).
        regenerate_showcase: If True, regenerate showcase section after each fix.
        mark_step_completed: If True, mark step as completed when human approves.
        initial_report_path: Optional path to display before the first human review prompt.

    Returns:
        Tuple of (approved, final_agent_id):
        - approved: True if human approved, False if flow should abort
        - final_agent_id: ID of the last agent that ran (may be None if no fixes)
    """
    current_agent_id = last_agent_id

    # Display initial report before prompting (useful when resuming)
    if initial_report_path:
        _display_agent_report(initial_report_path, logger)

    while True:
        log_and_print(
            logger,
            f"Waiting for human review at checkpoint: {checkpoint_name}",
            print_prefix="  [Human Review] ",
        )

        approved, direct_feedback, inline_feedback, returned_draft_path = prompt_human_review(
            str(draft_path),
            checkpoint_name,
        )

        if approved:
            log_and_print(
                logger,
                f"Human approved at checkpoint: {checkpoint_name}",
                print_prefix="  [Human Review] ",
            )
            if mark_step_completed:
                flow.mark_step_completed(step)
            return True, current_agent_id

        # Human provided feedback - log it and spawn fixer agent
        _append_human_feedback_to_log(
            planning_log_path,
            direct_feedback,
            inline_feedback,
            checkpoint_name,
        )

        feedback_text = _format_feedback_for_agent(direct_feedback, inline_feedback)

        log_and_print(
            logger,
            "Human provided feedback, spawning agent to make changes",
            print_prefix="  [Human Review] ",
        )

        # Spawn plan-creator to address the feedback
        # Include resolution instructions only for inline feedback
        fix_input_text = (
            f"Address the following human feedback on the spec plan draft at {draft_path}:\n\n"
            f"{feedback_text}\n\n"
            "Make the requested changes to the spec plan draft."
            f"{FIXER_PRESERVATION_INSTRUCTIONS}"
        )

        if inline_feedback:
            fix_input_text += INLINE_FEEDBACK_INSTRUCTIONS

        fix_input = info_tracker.add_to_input(fix_input_text)

        fix_result = flow.run(
            "plan-creator",
            agent=AgentInfo(step=step, role="human-feedback-fixer"),
            input=fix_input,
            after=current_agent_id,
            auto_retry=MAX_BUILD_RETRIES,
            initiated_by="flow.api",
        )

        if fix_result.exit_reason != ExitReason.COMPLETED:
            log_and_print(
                logger,
                f"Agent failed to process feedback: {fix_result.exit_reason}",
                level="ERROR",
                print_prefix="  [Human Review] ",
            )
            return False, fix_result.final_agent

        # If inline feedback was used, parse resolutions and update the draft file
        if inline_feedback and returned_draft_path and fix_result.report_file:
            report_path = Path(fix_result.report_file)
            if report_path.exists():
                report_content = report_path.read_text(encoding="utf-8")
                resolutions = _parse_resolutions_from_report(report_content)
                if resolutions:
                    resolve_feedback_markers(returned_draft_path, resolutions)
                    log_and_print(
                        logger,
                        f"Resolved {len(resolutions)} feedback marker(s)",
                        print_prefix="  [Human Review] ",
                    )

        current_agent_id = fix_result.final_agent

        # Optionally regenerate showcase section after human feedback fix
        if regenerate_showcase:
            log_and_print(
                logger,
                "Regenerating showcase section after human feedback",
                print_prefix="  [Showcase] ",
            )

            showcase_input = (
                f"Regenerate the Showcase Requirements section in {draft_path}.\n\n"
                "The plan has been updated based on human feedback. Update the existing "
                "`## Showcase Requirements` section to reflect the current state of the plan. "
                "Ensure the CLI demonstrations accurately test the implementation steps as "
                "currently defined."
            )

            showcase_result = flow.run(
                "showcase-planner",
                agent=AgentInfo(step=step, role="showcase-regenerator"),
                input=showcase_input,
                after=current_agent_id,
                auto_retry=MAX_BUILD_RETRIES,
                initiated_by="flow.api",
            )

            if showcase_result.exit_reason != ExitReason.COMPLETED:
                log_and_print(
                    logger,
                    f"Showcase regeneration failed: {showcase_result.exit_reason}",
                    level="WARNING",
                    print_prefix="  [Showcase] ",
                )
                # Continue with human review even if showcase regeneration fails
            else:
                current_agent_id = showcase_result.final_agent

        # Display full agent report before re-prompting
        if fix_result.report_file:
            _display_agent_report(Path(fix_result.report_file), logger)

        log_and_print(
            logger,
            "Agent completed changes, re-prompting human for review",
            print_prefix="  [Human Review] ",
        )


if __name__ == "__main__":
    main()
