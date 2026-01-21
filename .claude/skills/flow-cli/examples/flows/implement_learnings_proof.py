#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = [
#     "flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli",
# ]
# ///
"""Orchestrator script that runs implement, then learnings and proof in parallel.

This script combines the implementation workflow with post-implementation tasks:
1. Runs implement.py (blocking, passes through all args)
2. If implementation succeeds, runs run_learnings.py and run_proof.py in parallel

This provides a convenient single command for the full workflow while
keeping the individual scripts available for targeted reruns.

Usage:
    ./.flow/scripts/flows/implement_learnings_proof.py <plan-file> [options]
    ./.flow/scripts/flows/implement_learnings_proof.py specs/my-feature.md
    ./.flow/scripts/flows/implement_learnings_proof.py specs/my-feature.md --skip-learnings
    ./.flow/scripts/flows/implement_learnings_proof.py specs/my-feature.md --skip-proof
    ./.flow/scripts/flows/implement_learnings_proof.py specs/my-feature.md --reset

Options:
    --skip-learnings    Skip learnings analysis after implementation
    --skip-proof        Skip proof generation after implementation
    All other options are passed through to implement.py
"""

from __future__ import annotations

import os
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from rich.console import Console

console = Console()


def run_implement(plan_file: str, passthrough_args: list[str]) -> tuple[bool, Path]:
    """Run implement.py with the given plan file and passthrough args.

    Args:
        plan_file: Path to the plan file.
        passthrough_args: Additional arguments to pass to implement.py.

    Returns:
        Tuple of (success, flow_dir):
        - success: True if implementation completed successfully
        - flow_dir: Path to the flow directory
    """
    plan_path = Path(plan_file)
    flow_name = f"implement_{plan_path.stem}"
    flow_dir = Path("flows") / flow_name

    cmd = ["uv", "run", ".flow/scripts/flows/implement.py", plan_file, *passthrough_args]
    console.print("\n[bold cyan]=== Running Implementation ===[/bold cyan]")
    console.print(f"[dim]Command: {' '.join(cmd)}[/dim]\n")

    result = subprocess.run(cmd, check=False)

    return result.returncode == 0, flow_dir


def run_learnings(flow_dir: Path, plan_file: str) -> tuple[str, bool]:
    """Run learnings.py for the given flow.

    Args:
        flow_dir: Path to the flow directory.
        plan_file: Path to the plan file.

    Returns:
        Tuple of (task_name, success).
    """
    cmd = ["uv", "run", ".flow/scripts/flows/run_learnings.py", "--flow-dir", str(flow_dir), "--plan", plan_file]
    result = subprocess.run(cmd, capture_output=True, text=True, check=False)

    if result.returncode != 0:
        console.print("[red]Learnings analysis failed[/red]")
        if result.stderr:
            console.print(f"[dim]{result.stderr}[/dim]")
        return "learnings", False

    return "learnings", True


def run_proof(flow_dir: Path, plan_file: str) -> tuple[str, bool]:
    """Run proof.py for the given flow.

    Args:
        flow_dir: Path to the flow directory.
        plan_file: Path to the plan file.

    Returns:
        Tuple of (task_name, success).
    """
    cmd = ["uv", "run", ".flow/scripts/flows/run_proof.py", "--flow-dir", str(flow_dir), "--plan", plan_file, "--no-serve"]
    result = subprocess.run(cmd, capture_output=True, text=True, check=False)

    if result.returncode != 0:
        console.print("[red]Proof generation failed[/red]")
        if result.stderr:
            console.print(f"[dim]{result.stderr}[/dim]")
        return "proof", False

    return "proof", True


def main() -> None:
    # Parse args manually to extract our flags and passthrough the rest
    args = sys.argv[1:]

    if not args:
        console.print("[red]Error: Plan file is required[/red]")
        console.print("Usage: ./.flow/scripts/flows/implement_learnings_proof.py <plan-file> [options]")
        sys.exit(1)

    # First positional arg is the plan file
    plan_file = args[0]
    remaining_args = args[1:]

    # Check if plan file exists
    if not Path(plan_file).exists():
        console.print(f"[red]Error: Plan file not found: {plan_file}[/red]")
        sys.exit(1)

    # Extract our flags
    skip_learnings = "--skip-learnings" in remaining_args
    skip_proof = "--skip-proof" in remaining_args

    # Remove our flags from passthrough args
    passthrough_args = [arg for arg in remaining_args if arg not in ("--skip-learnings", "--skip-proof")]

    # Run implementation (blocking)
    success, flow_dir = run_implement(plan_file, passthrough_args)

    if not success:
        console.print("\n[red]=== Implementation Failed ===[/red]")
        console.print("Fix the issues and resume with:")
        console.print(f"  ./.flow/scripts/flows/implement.py {plan_file}")
        sys.exit(1)

    # Run post-implementation tasks in parallel
    tasks_to_run: list[str] = []
    if not skip_learnings:
        tasks_to_run.append("learnings")
    if not skip_proof:
        tasks_to_run.append("proof")

    if not tasks_to_run:
        console.print("\n[bold green]=== Complete ===[/bold green]")
        console.print("Implementation completed (post-implementation tasks skipped)")
        sys.exit(0)

    console.print("\n[bold cyan]=== Post-Implementation Tasks ===[/bold cyan]")
    console.print(f"Running: {', '.join(tasks_to_run)}")

    failures: list[str] = []

    with ThreadPoolExecutor(max_workers=2) as executor:
        futures = {}

        if not skip_learnings:
            futures[executor.submit(run_learnings, flow_dir, plan_file)] = "learnings"
        if not skip_proof:
            futures[executor.submit(run_proof, flow_dir, plan_file)] = "proof"

        for future in as_completed(futures):
            task_name, task_success = future.result()
            if task_success:
                console.print(f"  [green]OK[/green] {task_name}")
            else:
                console.print(f"  [red]FAILED[/red] {task_name}")
                failures.append(task_name)

    if failures:
        console.print("\n[yellow]=== Partial Success ===[/yellow]")
        console.print("Implementation completed but the following post-tasks failed:")
        for task in failures:
            console.print(f"  - {task}")
        console.print("\nRetry commands:")
        if "learnings" in failures:
            console.print(f"  ./.flow/scripts/flows/run_learnings.py --flow-dir {flow_dir} --plan {plan_file}")
        if "proof" in failures:
            console.print(f"  ./.flow/scripts/flows/run_proof.py --flow-dir {flow_dir} --plan {plan_file}")
        sys.exit(1)

    console.print("\n[bold green]=== Complete ===[/bold green]")
    console.print("All tasks completed successfully")

    # Start webserver if proof was generated
    proof_html = flow_dir / "proof.html"
    if not skip_proof and proof_html.exists():
        view_script = Path("scripts/view_proof.py")
        # Replace this process with view_proof.py - user can Ctrl+C to stop
        os.execvp("uv", ["uv", "run", str(view_script), "--random-port", str(proof_html)])

    sys.exit(0)


if __name__ == "__main__":
    main()
