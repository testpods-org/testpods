#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = [
#     "flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli",
# ]
# ///
"""Run learnings analysis after a flow completes.

This script spawns a learnings-analyst agent to analyze a completed flow
and propose improvements to agent instructions and guidelines.

Usage:
    ./run_analyst.py <completed_flow_name>

Example:
    ./run_analyst.py impl-add-auth

The analyst will:
1. Read flow artifacts via `flow artifacts --flow <name>`
2. Analyze implementation log, step reports, execution events
3. Write entries to the learnings database
4. Create a PR with proposed improvements
"""

from __future__ import annotations

import sys

from flow import AgentInfo, Flow
from flow.lib.config import find_project_root
from flow.lib.flow_status import read_status
from flow.lib.learnings import get_improvable_files


def run_learnings_analyst(completed_flow_name: str) -> None:
    """Run learnings analysis after a flow completes.

    Args:
        completed_flow_name: Name of the completed flow to analyze.
    """
    root = find_project_root()

    # Read the completed flow's status to get its metadata
    completed_status = read_status(completed_flow_name)
    if completed_status is None:
        print(f"Error: Flow '{completed_flow_name}' not found")
        sys.exit(1)
    flow_type = completed_status.metadata.flow_type or "unknown"

    # Get improvable files from config
    improvable = get_improvable_files(root)

    # Format the improvable files list
    improvable_list = "\n".join(f"- {f}" for f in improvable)

    # Create a new flow for the learnings analysis
    analyst_flow = Flow(
        f"learnings-{completed_flow_name}",
        config_path=".flow/learnings/analyst.yaml",
        reset=True,
    )

    # Spawn the analyst agent with improvable files list
    spawn_result = analyst_flow.spawn(
        agent_type="learnings-analyst",
        agent=AgentInfo(step=1, role="analyst"),
        input=f"""Analyze the completed flow: {completed_flow_name}

## Flow Type

{flow_type}

## Files You May Propose Changes To

{improvable_list}

## Files You Should NOT Modify

- Source code files - Only propose changes to agent instructions and guidelines
- Writable shared files - These are per-flow context, not permanent instructions

## Instructions

1. Run `flow artifacts --flow {completed_flow_name}` to get execution data
2. Analyze the implementation log, step reports, and events
3. Identify patterns, anti-patterns, bugs, and missed requirements
4. For each observation, create a learning entry with `flow feedback`
5. Propose improvements to the files listed above
6. Create a PR with your proposed changes

Be thorough but focused. Only propose changes that have clear evidence from the flow execution.
""",
    )

    # Wait for completion
    results = analyst_flow.await_all([spawn_result])

    # Finalize flow status based on agent results
    if results[0].exit_reason == "completed":
        analyst_flow.set_status("completed")
        print(f"Learnings analysis complete for {completed_flow_name}")
    else:
        analyst_flow.set_status("failed")
        print(f"Learnings analysis failed for {completed_flow_name}: {results[0].exit_reason}")
        sys.exit(1)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: uv run run_analyst.py <completed_flow_name>")
        sys.exit(1)

    run_learnings_analyst(sys.argv[1])
