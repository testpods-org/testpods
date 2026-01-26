"""Orchestration utilities for flow scripts.

This module provides reusable orchestration patterns for flow scripts.

Example usage:
    from scripts.flows.lib.orchestration_utils import InfoTracker

    # Track --info usage (add to first agent that runs)
    info_tracker = InfoTracker(args.info)

    # First agent gets the info appended
    input1 = info_tracker.add_to_input("Task for agent 1")
    # -> "Task for agent 1\n\nAdditional context:\n{args.info}" (if info provided)

    # Subsequent agents don't get it again
    input2 = info_tracker.add_to_input("Task for agent 2")
    # -> "Task for agent 2" (info already used)
"""

from __future__ import annotations


class InfoTracker:
    """Tracks whether additional info has been added to an agent input.

    When resuming a flow, we want additional context (like --info) to be passed
    to whichever agent runs first, not just a specific agent. This class ensures
    info is only added once across all agent invocations.

    This is useful for orchestration scripts that may resume from any point
    (builder, validation-fixer, reviewer, etc.) and need to pass context
    exactly once to the first agent that runs.

    Attributes:
        info: The additional context string, or None if not provided.

    Example:
        >>> tracker = InfoTracker("Fix the circular import issue")
        >>> tracker.add_to_input("Implement step 1")
        'Implement step 1\\n\\nAdditional context:\\nFix the circular import issue'
        >>> tracker.add_to_input("Implement step 2")
        'Implement step 2'  # Info already used, not added again
    """

    def __init__(self, info: str | None) -> None:
        """Initialize the InfoTracker.

        Args:
            info: Additional context string to append once, or None if no
                  additional context is provided.
        """
        self._info = info
        self._used = False

    @property
    def info(self) -> str | None:
        """The additional context string, or None if not provided."""
        return self._info

    def add_to_input(self, base_input: str) -> str:
        """Add info to input if not already used, then mark as used.

        If info exists and has not been used yet, appends it to the base_input
        with a clear separator. After the first use, subsequent calls return
        the base_input unchanged.

        Args:
            base_input: The original input string for the agent.

        Returns:
            The input string with info appended (if info exists and unused),
            or the unchanged base_input (if no info or already used).

        Example:
            >>> tracker = InfoTracker("Focus on performance")
            >>> tracker.add_to_input("Build feature X")
            'Build feature X\\n\\nAdditional context:\\nFocus on performance'
            >>> tracker.add_to_input("Build feature Y")
            'Build feature Y'
        """
        if self._info and not self._used:
            self._used = True
            return f"{base_input}\n\nAdditional context:\n{self._info}"
        return base_input
