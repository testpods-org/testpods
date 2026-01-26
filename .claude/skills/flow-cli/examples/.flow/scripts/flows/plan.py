#!/usr/bin/env -S uv run
"""Planning flow script for the Flow CLI.

This script refines and structures prepared plan files into complete spec plans.
It takes an already-prepared plan .md file and runs it through the drafting and
showcase planning phases, with a single human checkpoint before finalization.

Workflow (2 steps + 1 human checkpoint):
1. Draft - Refine prepared plan into complete spec plan with showcase placeholder
2. Showcase - Add showcase requirements section
   → [Human Checkpoint: Review draft, inline feedback support]
   → Finalize

Usage:
    uv run .flow/scripts/flows/plan.py specs/drafts/feature/input.md [options]
    uv run .flow/scripts/flows/plan.py specs/drafts/auth/input.md --name auth-feature
    uv run .flow/scripts/flows/plan.py specs/drafts/cache/input.md --info "Focus on Redis"
    uv run .flow/scripts/flows/plan.py specs/drafts/log/input.md --skip-showcase
    uv run .flow/scripts/flows/plan.py specs/drafts/log/input.md --reset

Options:
    --name <name>       Feature name for output file (default: filename stem)
    --info <context>    Additional context for agents
    --skip-showcase     Skip showcase-planner agent, leave HTML comment placeholder
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
    clean_feedback_markers,
    display_diff,
    prompt_human_review,
    resolve_feedback_markers,
)

# Rich console for markdown rendering
console = Console()

# Configuration
MAX_BUILD_RETRIES = 3

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
    parser = argparse.ArgumentParser(
        description="Refine a prepared plan file into a complete spec plan"
    )
    parser.add_argument(
        "plan_file",
        type=Path,
        help="Path to prepared plan .md file",
    )
    parser.add_argument(
        "--name",
        type=str,
        default=None,
        help="Feature name for output file (default: filename stem)",
    )
    parser.add_argument(
        "--info",
        type=str,
        default=None,
        help="Additional context for agents",
    )
    parser.add_argument(
        "--skip-showcase",
        action="store_true",
        help="Skip showcase-planner agent, leave HTML comment placeholder",
    )
    parser.add_argument(
        "--reset",
        action="store_true",
        help="Reset flow state and start fresh (default: resume existing state)",
    )
    args = parser.parse_args()

    # Validate plan file exists
    plan_path: Path = args.plan_file
    if not plan_path.exists():
        sys.exit(f"Error: Plan file not found: {plan_path}")
    if not plan_path.is_file():
        sys.exit(f"Error: Not a file: {plan_path}")

    # Read plan content and derive feature name from filename stem
    plan_content = plan_path.read_text(encoding="utf-8").strip()
    feature_name = args.name or plan_path.stem

    # Determine paths (outside context manager since they don't depend on flow)
    # Draft directory: contains draft.md and snapshot files
    draft_dir = _get_draft_dir(feature_name)
    draft_dir.mkdir(parents=True, exist_ok=True)
    draft_path = draft_dir / "draft.md"

    # Save original input as snapshot (supports diffing against original)
    input_snapshot_path = draft_dir / "snapshot-input.md"
    if not input_snapshot_path.exists():
        input_snapshot_path.write_text(plan_content, encoding="utf-8")
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
            # STEPS 1-2: Create (draft + showcase)
            log_and_print(logger, "Steps 1-2: Create Spec Plan", print_prefix="\n=== ")
            step_ok, last_agent_id = run_step1_create(
                flow=flow,
                plan_content=plan_content,
                draft_path=draft_path,
                planning_log_path=planning_log_path,
                info_tracker=info_tracker,
                logger=logger,
                skip_showcase=args.skip_showcase,
            )
            if not step_ok:
                flow.set_status("failed")
                sys.exit("Steps 1-2 (Create) failed")

            # Save snapshot for diff display at human checkpoint
            _save_snapshot(draft_path, "step2")

            # HUMAN CHECKPOINT
            if _is_step_completed(flow, 2):
                log_and_print(
                    logger,
                    "Human Checkpoint: Review - already approved",
                    print_prefix="\n=== ",
                )
                # Get the last agent from step 2 for chaining
                last_agent_id = flow.get_last_agent_id(step=2, role="human-feedback-fixer")
                if not last_agent_id:
                    last_agent_id = flow.get_last_agent_id(step=2, role="showcase-regenerator")
                if not last_agent_id:
                    last_agent_id = flow.get_last_agent_id(step=2, role="showcase")
            else:
                log_and_print(logger, "Human Checkpoint: Review", print_prefix="\n=== ")

                # Display diff showing transformation from original input to draft
                _display_changes_since_snapshot(
                    draft_path,
                    snapshot_name="input",
                    old_label="Original Input",
                    new_label="Draft (after Steps 1-2)",
                )

                # Regenerate showcase only if not skipped
                regenerate = not args.skip_showcase

                human_approved, last_agent_id = run_human_approval_checkpoint(
                    flow=flow,
                    draft_path=draft_path,
                    planning_log_path=planning_log_path,
                    info_tracker=info_tracker,
                    logger=logger,
                    checkpoint_name="Review",
                    last_agent_id=last_agent_id,
                    step=2,
                    regenerate_showcase=regenerate,
                    mark_step_completed=True,
                )
                if not human_approved:
                    flow.set_status("failed")
                    sys.exit("Human Checkpoint (Review) failed")

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


def run_step1_create(
    flow: Flow,
    plan_content: str,
    draft_path: Path,
    planning_log_path: Path,
    info_tracker: InfoTracker,
    logger: logging.Logger,
    *,
    skip_showcase: bool = False,
) -> tuple[bool, str | None]:
    """Run Steps 1-2: Create phase (draft + showcase).

    Step 1: Draft - Refine prepared plan into complete spec plan
    Step 2: Showcase - Add showcase requirements section (or placeholder if skipped)

    Args:
        flow: The Flow instance.
        plan_content: The prepared plan content to refine.
        draft_path: Path to write the draft spec plan.
        planning_log_path: Path to the planning log file.
        info_tracker: InfoTracker for adding --info context.
        logger: Logger for flow logging.
        skip_showcase: If True, skip showcase-planner and insert HTML comment placeholder.

    Returns:
        Tuple of (success, last_agent_id).
    """
    last_agent_id: str | None = None

    # STEP 1: Draft
    if flow.has_completed_agent(step=1, role="drafter"):
        log_and_print(logger, "Drafter already completed", print_prefix="  [Skipping] ")
        last_agent_id = flow.get_last_agent_id(step=1, role="drafter")
    else:
        log_and_print(logger, "Step 1: Draft", print_prefix="  ")

        # Read planning log for context
        planning_context = ""
        if planning_log_path.exists():
            planning_context = planning_log_path.read_text(encoding="utf-8")

        # Build the draft input - refine prepared plan
        draft_input_parts = [
            "Refine and structure this prepared plan into a complete spec plan:\n\n"
            f"{plan_content}\n\n"
            "Focus on:\n"
            "- Ensuring the plan follows the spec plan template exactly\n"
            "- Verifying all required sections are present and complete\n"
            "- Ensuring implementation steps are clear, ordered, and actionable\n"
            "- Enforcing the anti-code policy (no implementation code, only pseudo-code/type signatures)\n",
        ]
        if planning_context:
            draft_input_parts.append(f"\nContext from planning log:\n\n{planning_context}")
        draft_input_parts.append(
            f"\n\nWrite the spec plan draft to: {draft_path}\n\n"
            f"IMPORTANT: Include the placeholder marker `{SHOWCASE_PLACEHOLDER}` in the plan "
            "where the Showcase Requirements section should be inserted. The showcase-planner "
            "agent will replace this marker with the actual showcase section."
        )

        draft_input = info_tracker.add_to_input("\n".join(draft_input_parts))

        result = flow.run(
            "plan-creator",
            agent=AgentInfo(step=1, role="drafter"),
            input=draft_input,
            after=last_agent_id,
            auto_retry=MAX_BUILD_RETRIES,
            initiated_by="flow.api",
        )

        if result.exit_reason != ExitReason.COMPLETED:
            log_and_print(logger, f"Draft failed: {result.exit_reason}", level="ERROR", print_prefix="  ")
            return False, result.final_agent

        last_agent_id = result.final_agent

        # Verify the draft file was created
        if not draft_path.exists():
            log_and_print(logger, f"Draft file not created at {draft_path}", level="ERROR", print_prefix="  ")
            return False, last_agent_id

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
                return False, last_agent_id

        log_and_print(logger, f"Draft complete: {draft_path}", print_prefix="  ")

    # STEP 2: Showcase
    if skip_showcase:
        # Skip showcase-planner - insert HTML comment placeholder
        log_and_print(logger, "Step 2: Showcase (skipped - inserting placeholder)", print_prefix="  ")

        draft_content = draft_path.read_text(encoding="utf-8")
        if SHOWCASE_PLACEHOLDER in draft_content:
            # Replace placeholder with HTML comment
            showcase_placeholder_comment = (
                "## Showcase Requirements\n\n"
                "<!-- SHOWCASE_TODO: Run with showcase-planner or manually add showcase requirements -->\n"
            )
            draft_content = draft_content.replace(SHOWCASE_PLACEHOLDER, showcase_placeholder_comment)
            draft_path.write_text(draft_content, encoding="utf-8")
            log_and_print(logger, "Showcase placeholder inserted (--skip-showcase)", print_prefix="  ")
        elif has_showcase_section(draft_path):
            log_and_print(logger, "Showcase section already exists", print_prefix="  ")
        else:
            log_and_print(
                logger,
                "Warning: Neither placeholder nor showcase section found",
                level="WARNING",
                print_prefix="  ",
            )
    elif flow.has_completed_agent(step=2, role="showcase"):
        log_and_print(logger, "Showcase already completed", print_prefix="  [Skipping] ")
        last_agent_id = flow.get_last_agent_id(step=2, role="showcase")
    else:
        log_and_print(logger, "Step 2: Showcase", print_prefix="  ")

        # Verify the draft file exists and has placeholder
        if not draft_path.exists():
            log_and_print(logger, f"Draft file not found: {draft_path}", level="ERROR", print_prefix="  ")
            return False, last_agent_id

        draft_content = draft_path.read_text(encoding="utf-8")
        if SHOWCASE_PLACEHOLDER not in draft_content:
            # Check if section already exists
            if has_showcase_section(draft_path):
                log_and_print(logger, "Showcase section already exists", print_prefix="  ")
                return True, last_agent_id
            log_and_print(
                logger,
                "Neither placeholder nor existing showcase section found",
                level="ERROR",
                print_prefix="  ",
            )
            return False, last_agent_id

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
            agent=AgentInfo(step=2, role="showcase"),
            input=showcase_input,
            after=last_agent_id,
            auto_retry=MAX_BUILD_RETRIES,
            initiated_by="flow.api",
        )

        if result.exit_reason != ExitReason.COMPLETED:
            log_and_print(logger, f"Showcase planning failed: {result.exit_reason}", level="ERROR", print_prefix="  ")
            return False, result.final_agent

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
            return False, last_agent_id

        if not has_showcase_section(draft_path):
            log_and_print(
                logger,
                "Showcase section not found in draft after planning",
                level="ERROR",
                print_prefix="  ",
            )
            return False, last_agent_id

        log_and_print(logger, "Showcase section added", print_prefix="  ")

    log_and_print(logger, "Steps 1-2 complete (draft + showcase)", print_prefix="  ")
    return True, last_agent_id


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
    console.print(Markdown(report_content))
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
