#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = [
#     "flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli",
# ]
# ///
"""Run learnings aggregation to find patterns.

This script spawns a learnings-aggregator agent to analyze accumulated
learnings and identify recurring patterns.

Usage:
    ./run_aggregator.py <flow_type>
    ./run_aggregator.py --all-types

Example:
    ./run_aggregator.py implement
    ./run_aggregator.py --all-types

The aggregator will:
1. Read all learnings via `flow learnings --json`
2. Group learnings by tags, files, and categories
3. Identify patterns with 3+ occurrences
4. Create/update clusters in the learning clusters file
5. Create PRs for high-confidence systematic improvements
"""

from __future__ import annotations

import sys

from flow import AgentInfo, Flow
from flow.lib.config import find_project_root
from flow.lib.learnings import get_improvable_files


def run_aggregator(flow_type: str | None = None, all_types: bool = False) -> None:
    """Run aggregator to find patterns across flows.

    Args:
        flow_type: Specific flow type to analyze.
        all_types: If True, analyze all flow types.
    """
    root = find_project_root()

    if not flow_type and not all_types:
        print("Error: Must specify --flow-type or --all-types")
        sys.exit(1)

    # Get improvable files from config
    improvable = get_improvable_files(root)
    improvable_list = "\n".join(f"- {f}" for f in improvable)

    # Determine scope
    scope = "--all-types" if all_types else f"--flow-type {flow_type}"
    scope_name = "all-types" if all_types else flow_type

    # Create a new flow for the aggregation
    aggregator_flow = Flow(
        f"aggregator-{scope_name}",
        config_path=".flow/learnings/aggregator.yaml",
        reset=True,
    )

    # Spawn the aggregator agent
    spawn_result = aggregator_flow.spawn(
        agent_type="learnings-aggregator",
        agent=AgentInfo(step=1, role="aggregator"),
        input=f"""Analyze patterns in accumulated learnings.

## Scope

{scope}

## Files You May Propose Changes To

{improvable_list}

## Files You Should NOT Modify

- Source code files - Only propose changes to agent instructions and guidelines
- Writable shared files - These are per-flow context, not permanent instructions

## Instructions

1. Run `flow learnings {scope} --json` to get all learnings
2. Group learnings by:
   - Tags (common themes)
   - Related files (same files affected)
   - Categories (anti-patterns, bugs, etc.)
3. Identify patterns with 3+ occurrences
4. For each pattern:
   - Analyze root cause
   - Propose systematic fix
   - Create or update cluster in `flow learnings --clusters`
5. Create PRs for high-confidence improvements

Focus on systemic issues that appear across multiple flows. Single occurrences
are not patterns - wait for more evidence before proposing changes.
""",
    )

    # Wait for completion
    results = aggregator_flow.await_all([spawn_result])

    # Finalize flow status based on agent results
    if results[0].exit_reason == "completed":
        aggregator_flow.set_status("completed")
        print(f"Aggregation complete for {scope_name}")
    else:
        aggregator_flow.set_status("failed")
        print(f"Aggregation failed for {scope_name}: {results[0].exit_reason}")
        sys.exit(1)


def print_usage() -> None:
    """Print usage information."""
    print("Usage: ./run_aggregator.py <flow_type>")
    print("       ./run_aggregator.py --all-types")
    print()
    print("Arguments:")
    print("  <flow_type>    Specific flow type to analyze (e.g., 'implement')")
    print("  --all-types    Analyze learnings from all flow types")
    print()
    print("Options:")
    print("  -h, --help     Show this help message")
    print()
    print("Example:")
    print("  ./run_aggregator.py implement")
    print("  ./run_aggregator.py --all-types")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print_usage()
        sys.exit(1)

    arg = sys.argv[1]

    if arg in ("-h", "--help"):
        print_usage()
        sys.exit(0)
    elif arg == "--all-types":
        run_aggregator(all_types=True)
    elif arg.startswith("-"):
        print(f"Error: Unknown option '{arg}'")
        print()
        print_usage()
        sys.exit(1)
    else:
        run_aggregator(flow_type=arg)
