"""Git utilities for orchestration scripts.

This module provides reusable git operations for flow orchestration scripts.
Functions here operate on the current working directory's git repository.

Example usage:
    from scripts.flows.lib.git_utils import commit_and_push_changes, get_flow_commits

    # Check for flow commits
    if has_flow_commits("my-plan"):
        commits = get_flow_commits("my-plan")
        print(format_commits(commits))

    # Commit and push changes
    commit_and_push_changes(logger, "[my-plan] Step 1: Initial implementation")
"""

from __future__ import annotations

import logging
import subprocess

from flow.lib.logging_setup import log_and_print


def has_flow_commits(plan_stem: str) -> bool:
    """Check if any commits were made by this flow.

    Uses git log --grep to find commits with the flow's prefix pattern
    [plan_stem] in the commit message.

    Args:
        plan_stem: The plan file stem used in commit message prefixes
                   (e.g., "my-feature" matches commits like "[my-feature] Step 1: ...")

    Returns:
        True if commits with the flow prefix exist, False otherwise.

    Example:
        >>> if has_flow_commits("proof-generation-package"):
        ...     print("Flow has commits")
    """
    result = subprocess.run(
        ["git", "log", "--oneline", f"--grep=\\[{plan_stem}\\]", "--max-count=1"],
        capture_output=True,
        text=True,
    )
    return bool(result.stdout.strip())


def get_flow_commits(plan_stem: str) -> list[tuple[str, str]]:
    """Get commits made by this flow in chronological order (oldest first).

    Uses git log --oneline --reverse --grep to find commits with the flow's
    prefix pattern [plan_stem] in the commit message.

    Args:
        plan_stem: The plan file stem used in commit message prefixes
                   (e.g., "my-feature" matches commits like "[my-feature] Step 1: ...")

    Returns:
        List of (sha, message) tuples for commits with the flow prefix,
        ordered from oldest to newest. Returns empty list if no commits found.

    Example:
        >>> commits = get_flow_commits("proof-generation-package")
        >>> for sha, msg in commits:
        ...     print(f"{sha}: {msg}")
    """
    result = subprocess.run(
        ["git", "log", "--oneline", "--reverse", f"--grep=\\[{plan_stem}\\]"],
        capture_output=True,
        text=True,
    )
    commits = []
    for line in result.stdout.strip().split("\n"):
        if line:
            sha, *msg_parts = line.split(" ", 1)
            commits.append((sha, msg_parts[0] if msg_parts else ""))
    return commits


def format_commits(commits: list[tuple[str, str]]) -> str:
    """Format commits as a markdown list.

    Args:
        commits: List of (sha, message) tuples from get_flow_commits()

    Returns:
        Markdown-formatted list of commits with SHA in backticks,
        or "_No commits made_" if the list is empty.

    Example:
        >>> commits = [("abc123", "Step 1"), ("def456", "Step 2")]
        >>> print(format_commits(commits))
        - `abc123` Step 1
        - `def456` Step 2
    """
    if not commits:
        return "_No commits made_"
    return "\n".join(f"- `{sha}` {msg}" for sha, msg in commits)


def has_remote() -> bool:
    """Check if a git remote is configured.

    Returns:
        True if at least one remote exists, False otherwise.
    """
    result = subprocess.run(["git", "remote"], capture_output=True, text=True)
    return bool(result.stdout.strip())


def has_upstream() -> bool:
    """Check if the current branch has an upstream tracking branch configured.

    Returns:
        True if an upstream is configured, False otherwise.
    """
    result = subprocess.run(
        ["git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}"],
        capture_output=True,
        text=True,
    )
    return result.returncode == 0


def commit_and_push_changes(logger: logging.Logger, message: str) -> bool:
    """Stage, commit, and push changes if there are any.

    Performs the following steps:
    1. Run `git add -A` to stage all changes (including untracked files)
    2. Check `git diff --cached --quiet` to see if there are staged changes
    3. If changes exist, run `git commit -m message`
    4. If a remote exists, run `git push` to push the commit

    Uses log_and_print to log the operation to both flow.log and stdout.

    Args:
        logger: Logger for recording git operations to flow.log
        message: Commit message (should follow project commit conventions)

    Returns:
        True if a commit was made, False if there were no changes to commit.

    Example:
        >>> from flow.lib.logging_setup import get_flow_logger
        >>> logger = get_flow_logger("my-flow", "orchestration")
        >>> commit_and_push_changes(logger, "[my-plan] Step 1: Add feature")
        True  # If changes were committed
    """
    subprocess.run(["git", "add", "-A"], check=True)

    # Check if there are staged changes to commit
    result = subprocess.run(
        ["git", "diff", "--cached", "--quiet"],
        capture_output=True,
    )

    if result.returncode != 0:
        # There are staged changes, commit them
        subprocess.run(["git", "commit", "-m", message], check=True)
        log_and_print(logger, f"Committed: {message}", print_prefix="  [Git] ")

        # Push to remote only if one exists
        if has_remote():
            if has_upstream():
                subprocess.run(["git", "push"], check=True)
            else:
                # New branch without upstream - set it on first push
                subprocess.run(["git", "push", "-u", "origin", "HEAD"], check=True)
            log_and_print(logger, "Pushed to remote", print_prefix="  [Git] ")
        else:
            log_and_print(logger, "No remote configured, skipping push", print_prefix="  [Git] ")
        return True
    else:
        log_and_print(logger, "No changes to commit", print_prefix="  [Git] ")
        return False
