"""Learnings analysis runner for orchestration scripts.

This module provides functionality to run learnings analysis after flow completion,
spawning a learnings-analyst agent to analyze completed flows and propose improvements.
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

from flow import AgentInfo, Flow
from flow.lib.config import find_project_root
from flow.lib.flow_status import read_status
from flow.lib.git_utils import checkout_branch, get_current_branch
from flow.lib.learnings import get_improvable_files
from flow.lib.logging_setup import log_and_print
from flow.lib.pr_utils import get_base_branch_from_config
from flow.types import ExitReason, FlowStatusValue

if TYPE_CHECKING:
    pass


def run_learnings_analysis(
    flow: Flow,
    logger: logging.Logger,
    retries: int = 3,
) -> bool:
    """Run learnings analysis after flow completion.

    Spawns the learnings-analyst agent in a separate flow to analyze the completed
    implementation flow and propose improvements to agent instructions.

    Args:
        flow: The main implementation flow that completed.
        logger: Logger for recording progress to flow.log.
        retries: Number of auto-retries for stuck/timeout agents (default: 3).

    Returns:
        True if learnings analysis completed (or was skipped), False if it failed.
    """
    # Check if learnings system is scaffolded
    project_root = find_project_root()
    analyst_config_path = project_root / ".flow" / "learnings" / "analyst.yaml"

    if not analyst_config_path.exists():
        log_and_print(
            logger,
            "Learnings system not scaffolded (run 'flow learnings scaffold')",
            print_prefix="  [Info] ",
        )
        return True

    # Check if learnings flow already completed (for resume support)
    learnings_flow_name = f"learnings-{flow.name}"
    learnings_status = read_status(learnings_flow_name, base_dir=flow.base_dir)
    if learnings_status is not None and learnings_status.status == FlowStatusValue.COMPLETED:
        log_and_print(
            logger,
            "Learnings analysis already completed",
            print_prefix="  [Skipping] ",
        )
        return True

    # Read the completed flow's status to get its metadata
    completed_status = read_status(flow.name, base_dir=flow.base_dir)
    if completed_status is None:
        log_and_print(
            logger,
            f"Flow '{flow.name}' not found",
            level="ERROR",
            print_prefix="  [Error] ",
        )
        return False

    flow_type = completed_status.metadata.flow_type or "unknown"

    # Get improvable files from config
    improvable = get_improvable_files(project_root)
    improvable_list = "\n".join(f"- {f}" for f in improvable) if improvable else "None found"

    # Get the current branch to use as PR target
    # This ensures the learnings PR targets the branch we started from, not always "main"
    base_branch = get_current_branch(project_root)
    if base_branch is None:
        # Detached HEAD or not in git repo - fall back to configured default
        base_branch = get_base_branch_from_config(project_root=project_root)

    # Create a new flow for the learnings analysis with context manager for cleanup
    # Reset only if no prior attempt exists (allows resume of failed attempts)
    with Flow(
        learnings_flow_name,
        config_path=".flow/learnings/analyst.yaml",
        reset=(learnings_status is None),
    ) as learnings_flow:
        # Spawn and await the analyst agent
        analyst_result = learnings_flow.run(
            "learnings-analyst",
            agent=AgentInfo(step=1, role="analyst"),
            input=f"""Analyze the completed flow: {flow.name}

## Flow Type

{flow_type}

## Base Branch

{base_branch}

Target this branch when creating the PR. Use `--base {base_branch}` with `gh pr create`.

## Files You May Propose Changes To

{improvable_list}

## Files You Should NOT Modify

- Source code files - Only propose changes to agent instructions and guidelines
- Writable shared files - These are per-flow context, not permanent instructions

## Instructions

1. Run `flow artifacts --flow {flow.name}` to get execution data
2. Analyze the implementation log, step reports, and events
3. Identify patterns, anti-patterns, bugs, and missed requirements
4. For each observation, create a learning entry with `flow feedback`
5. Propose improvements to the files listed above
6. Create a PR with your proposed changes (targeting the base branch above)

Be thorough but focused. Only propose changes that have clear evidence from the flow execution.
""",
            auto_retry=retries,
            initiated_by="flow.api",
        )

        if analyst_result.exit_reason == ExitReason.COMPLETED:
            log_and_print(
                logger,
                "Learnings analysis complete",
                print_prefix="  [Learnings] ",
            )
            learnings_flow.set_status("completed")
            success = True
        else:
            log_and_print(
                logger,
                f"Learnings analysis failed: {analyst_result.exit_reason}",
                level="ERROR",
                print_prefix="  [Learnings] ",
            )
            learnings_flow.set_status("failed")
            success = False

    # Switch back to the original branch after learnings completes
    # (the learnings-analyst agent will have switched to a learnings/* branch)
    checkout_branch(base_branch, project_root)

    return success
