"""Interactive utilities for collecting human input during flows.

This module provides utilities for:
- Displaying clarification questions with Rich formatting
- Collecting user answers interactively
- Parsing agent responses for clarification/suggestion markers
- Displaying suggestions for user acceptance/rejection

Example usage:
    from scripts.flows.lib.interactive_utils import (
        display_clarification_questions,
        display_suggestions,
        parse_clarification_from_report,
        parse_suggestions_from_report,
    )

    # Parse agent report for clarification markers
    questions = parse_clarification_from_report(agent_report)
    if questions:
        answers = display_clarification_questions(questions)
        # Store answers in planning-log.md

    # Parse and display suggestions
    suggestions = parse_suggestions_from_report(agent_report)
    if suggestions:
        response = display_suggestions(suggestions)
        # 'y' = accept, 'n' = reject, other = modification text
"""

from __future__ import annotations

import difflib
import re
import sys
from pathlib import Path

from prompt_toolkit import PromptSession
from prompt_toolkit.input import create_input
from prompt_toolkit.output import create_output
from rich.console import Console
from rich.panel import Panel
from rich.text import Text

# termios is Unix-only, used for draining stdin
try:
    import termios

    _HAS_TERMIOS = True
except ImportError:
    termios = None  # type: ignore[assignment]
    _HAS_TERMIOS = False

# prompt_toolkit + Rich Integration
# =================================
# When using prompt_toolkit for input alongside Rich for output, terminal handling
# requires careful setup. The _prompt() function encapsulates the key requirements:
#
# 1. Drains pending terminal input (e.g., late CPR responses from Rich's layout queries)
# 2. Prints instruction text via console.print() - flushes Rich's terminal state
# 3. Uses explicit create_input()/create_output() - ensures proper terminal handling
# 4. Creates fresh PromptSession per call - Rich output between prompts needs fresh state
#
# Note: The stdin inheritance fix (stdin=subprocess.DEVNULL in agent Popen calls)
# resolved terminal state corruption that previously required workarounds. Now
# prompt_toolkit works correctly and handles Ctrl+W and other editing commands
# properly by tracking prompt position during line redraws.


def _drain_stdin() -> None:
    """Drain any pending input from stdin (e.g., late CPR responses).

    When Rich queries cursor position for layout (via \\x1b[6n), the terminal
    sends back CPR responses (\\x1b[row;colR). If these arrive after prompt_toolkit
    takes over, they appear as literal garbage characters like "^[[128;1R".

    This function flushes the terminal input buffer to discard any pending
    responses before we start reading user input.
    """
    if not sys.stdin.isatty():
        return

    if not _HAS_TERMIOS:
        return

    # termios is guaranteed to be available when _HAS_TERMIOS is True
    assert termios is not None
    try:
        termios.tcflush(sys.stdin.fileno(), termios.TCIFLUSH)
    except OSError:
        pass  # Ignore errors, best effort


def _prompt(instruction: str, prompt_text: str = "Your response: ") -> str:
    """Prompt for input with proper terminal handling when used alongside Rich.

    Args:
        instruction: Text to print before the prompt (e.g., "Enter 'y' to accept...").
                     This is printed before prompting to flush Rich's terminal state.
        prompt_text: The actual prompt string shown on the input line.

    Returns:
        The user's input string.

    This function handles proper Rich + prompt_toolkit interaction:
    1. Drain stdin - discards late-arriving CPR responses from Rich's layout queries
    2. Print instruction text via console.print() - flushes Rich's terminal state
    3. Use prompt_toolkit with fresh input/output - proper prompt redrawing on edits

    prompt_toolkit handles Ctrl+W and other editing commands correctly by tracking
    prompt position, unlike raw readline which gets confused by Rich's escape sequences.
    """
    # Drain any pending terminal input (late CPR responses, etc.)
    _drain_stdin()

    # Print instruction to flush Rich's terminal state
    console = Console()
    console.print(f"[dim]{instruction}[/dim]")

    # Use prompt_toolkit with explicit input/output for proper terminal handling
    # Fresh objects per call ensure clean state after Rich output
    session: PromptSession[str] = PromptSession(
        input=create_input(),
        output=create_output(),
    )
    return session.prompt(prompt_text)


def display_clarification_questions(questions: list[str]) -> list[str]:
    """Display clarification questions and collect answers from the user.

    Uses Rich Panel to display questions in a formatted box, then prompts
    for each answer one at a time using prompt_toolkit.

    Args:
        questions: List of question strings to display and ask.

    Returns:
        List of answer strings (same length as questions).

    Example:
        >>> questions = ["What authentication method?", "Which database?"]
        >>> answers = display_clarification_questions(questions)
        # User interactively answers each question
        # Returns: ["OAuth2", "PostgreSQL"]
    """
    console = Console()

    # Build a numbered list of questions
    numbered_questions = "\n".join(f"{i + 1}. {q}" for i, q in enumerate(questions))

    # Display questions in a panel
    console.print(
        Panel(
            numbered_questions,
            title="[bold yellow]Clarification Needed[/bold yellow]",
            border_style="yellow",
            expand=False,
        )
    )

    console.print()  # Blank line for readability

    # Collect answers one at a time
    answers: list[str] = []
    for i, _ in enumerate(questions):
        answer = _prompt(f"Answer question {i + 1}:", prompt_text=f"A{i + 1}: ")
        answers.append(answer)

    return answers


def display_suggestions(suggestions: list[str]) -> str:
    """Display suggestions and ask user to accept, reject, or modify.

    Uses Rich Panel to display suggestions in a formatted box, then prompts
    the user for their response.

    Args:
        suggestions: List of suggestion strings to display.

    Returns:
        User response: 'y' for accept, 'n' for reject, or modification text.

    Example:
        >>> suggestions = ["Consider using existing cache module", "Add pagination"]
        >>> response = display_suggestions(suggestions)
        # User sees panel and responds
        # Returns: "y", "n", or "Use Redis instead of in-memory cache"
    """
    console = Console()

    # Build a numbered list of suggestions
    numbered_suggestions = "\n".join(f"{i + 1}. {s}" for i, s in enumerate(suggestions))

    # Display suggestions in a panel
    console.print(
        Panel(
            numbered_suggestions,
            title="[bold blue]Suggestions[/bold blue]",
            border_style="blue",
            expand=False,
        )
    )

    console.print()  # Blank line for readability

    response = _prompt("Enter 'y' to accept, 'n' to reject, or type modifications:")

    return response


def parse_clarification_from_report(report_content: str) -> list[str]:
    """Extract clarification questions from an agent report.

    Looks for the `CLARIFICATION_NEEDED:` marker followed by a numbered list
    of questions. Each question is on a new line starting with a digit and period.

    Args:
        report_content: The full text content of the agent report.

    Returns:
        List of question strings extracted from the report.
        Returns empty list if no marker found or no questions parsed.

    Example:
        >>> report = '''
        ... Some analysis text...
        ...
        ... CLARIFICATION_NEEDED:
        ... 1. What authentication method should be used?
        ... 2. Should we support multiple databases?
        ...
        ... More text...
        ... '''
        >>> questions = parse_clarification_from_report(report)
        >>> questions
        ['What authentication method should be used?', 'Should we support multiple databases?']
    """
    return _parse_marker_block(report_content, "CLARIFICATION_NEEDED:")


def parse_suggestions_from_report(report_content: str) -> list[str]:
    """Extract suggestions from an agent report.

    Looks for the `SUGGESTIONS:` marker followed by a numbered list
    of suggestions. Each suggestion is on a new line starting with a digit and period.

    Args:
        report_content: The full text content of the agent report.

    Returns:
        List of suggestion strings extracted from the report.
        Returns empty list if no marker found or no suggestions parsed.

    Example:
        >>> report = '''
        ... Some analysis text...
        ...
        ... SUGGESTIONS:
        ... 1. Consider using the existing cache module from flow/lib/cache.py
        ... 2. This could be consolidated with the existing metrics feature
        ...
        ... More text...
        ... '''
        >>> suggestions = parse_suggestions_from_report(report)
        >>> suggestions
        ['Consider using the existing cache module from flow/lib/cache.py',
         'This could be consolidated with the existing metrics feature']
    """
    return _parse_marker_block(report_content, "SUGGESTIONS:")


def _parse_marker_block(content: str, marker: str) -> list[str]:
    """Parse a numbered list following a specific marker.

    This is a shared helper for parsing both CLARIFICATION_NEEDED: and SUGGESTIONS:
    blocks which share the same format. Handles multi-line items where continuation
    lines are indented or contain sub-items (a), b), etc.).

    Args:
        content: The text content to search.
        marker: The marker string to find (e.g., "CLARIFICATION_NEEDED:").

    Returns:
        List of extracted items from the numbered list.
        Returns empty list if marker not found or no items parsed.
    """
    # Find the marker position
    marker_pos = content.find(marker)
    if marker_pos == -1:
        return []

    # Extract text after the marker
    text_after_marker = content[marker_pos + len(marker) :]

    # Pattern to match the start of a numbered item
    numbered_item_pattern = re.compile(r"^\s*\d+\.\s*(.+)$")

    items: list[str] = []
    current_item_lines: list[str] = []
    lines = text_after_marker.split("\n")

    for line in lines:
        stripped = line.strip()

        # Skip leading blank lines before any items
        if not stripped and not current_item_lines and not items:
            continue

        # Check if this starts a new numbered item
        match = numbered_item_pattern.match(line)
        if match:
            # Save the previous item if we have one
            if current_item_lines:
                items.append("\n".join(current_item_lines))
            # Start a new item
            current_item_lines = [match.group(1).strip()]
            continue

        # If we're collecting an item, check for continuation or end
        if current_item_lines:
            # Blank line ends the current item and the block
            if not stripped:
                items.append("\n".join(current_item_lines))
                current_item_lines = []
                break

            # Section header or marker ends the block
            if stripped.startswith("##"):
                items.append("\n".join(current_item_lines))
                current_item_lines = []
                break
            if stripped.endswith(":") and stripped[0].isalpha():
                items.append("\n".join(current_item_lines))
                current_item_lines = []
                break

            # Otherwise, it's a continuation line (indented text, sub-items like a), b))
            current_item_lines.append(stripped)

    # Don't forget the last item if we didn't hit a terminator
    if current_item_lines:
        items.append("\n".join(current_item_lines))

    return items


def prompt_human_review(
    draft_path: str,
    checkpoint_name: str,
) -> tuple[bool, str | None, list[tuple[str, str]] | None, Path | None]:
    """Prompt the human to review a spec plan draft and provide feedback.

    Works directly with the draft file at specs/drafts/. The draft persists
    throughout the planning flow, accumulating feedback and resolution history.

    Args:
        draft_path: Path to the draft spec plan file in specs/drafts/.
        checkpoint_name: Name of the checkpoint (e.g., "Structural Review", "Final Review").

    Returns:
        Tuple of (approved, direct_feedback, inline_feedback, draft_path):
        - approved: True if human approves, False otherwise
        - direct_feedback: String of direct feedback if provided, None if inline or approved
        - inline_feedback: List of (context, feedback) tuples if 'd' selected, None otherwise
        - draft_path: Path to the draft file when inline feedback is used, None otherwise

    Example:
        >>> approved, direct_feedback, inline_feedback, draft_path = prompt_human_review(
        ...     "specs/drafts/my-feature-draft.md",
        ...     "Structural Review",
        ... )
        # User can respond with:
        #   'y' -> (True, None, None, None)
        #   'd' -> (False, None, [(context, feedback), ...], Path("specs/drafts/..."))
        #   'any text' -> (False, 'any text', None, None)
    """
    console = Console()

    draft_file = Path(draft_path)
    if not draft_file.exists():
        console.print(f"[red]Error: Draft file not found: {draft_path}[/red]")
        return False, "Draft file not found", None, None

    # Display checkpoint panel
    panel_content = (
        f"[bold]Checkpoint:[/bold] {checkpoint_name}\n\n"
        f"[bold]Draft file:[/bold] {draft_file}\n"
        f"  (Open in your editor to add inline feedback)\n\n"
        "[bold]Inline feedback syntax:[/bold]\n"
        "  [FEEDBACK: your feedback here]\n\n"
        "[bold]Options:[/bold]\n"
        "  [cyan]y[/cyan]     - Approve the plan\n"
        "  [cyan]d[/cyan]     - Use inline feedback from draft file\n"
        "  [cyan]<text>[/cyan] - Direct feedback to the agent"
    )

    console.print(
        Panel(
            panel_content,
            title=f"[bold yellow]Human Review: {checkpoint_name}[/bold yellow]",
            border_style="yellow",
            expand=False,
        )
    )

    console.print()  # Blank line for readability

    # Use loop instead of recursion for re-prompting when no inline feedback found
    while True:
        response = _prompt("Enter your choice or feedback:")

        if response.lower() == "y":
            # Approved - draft remains for history
            return True, None, None, None

        if response.lower() == "d":
            # Parse inline feedback from draft file
            draft_content = draft_file.read_text(encoding="utf-8")
            inline_feedback = parse_inline_feedback(draft_content)

            if not inline_feedback:
                console.print("[yellow]No inline feedback markers found in draft file.[/yellow]")
                console.print("[dim]Use [FEEDBACK: your feedback here] syntax to add feedback.[/dim]")
                # Continue loop to re-prompt
                continue

            console.print(f"[green]Found {len(inline_feedback)} inline feedback item(s)[/green]")
            # Return the draft_path so callers can resolve markers in the draft
            return False, None, inline_feedback, draft_file

        # Direct feedback provided
        return False, response, None, None


def parse_inline_feedback(content: str) -> list[tuple[str, str]]:
    """Extract inline feedback markers with surrounding context.

    Looks for `[FEEDBACK: feedback text here]` markers in the content
    and extracts them along with the preceding header or line for context.

    Args:
        content: The text content to search for feedback markers.

    Returns:
        List of (context, feedback) tuples where:
        - context: The preceding markdown header (## ...) or "Document start" if no header found
        - feedback: The feedback text inside the marker

    Example:
        >>> content = '''
        ... ## Implementation Steps
        ...
        ... ### Step 1: Add authentication
        ...
        ... [FEEDBACK: This step should come after database setup]
        ...
        ... ### Step 2: Database setup
        ... '''
        >>> feedback = parse_inline_feedback(content)
        >>> feedback
        [('### Step 1: Add authentication', 'This step should come after database setup')]

    Edge case - feedback before any header uses "Document start" as context:
        >>> content = '''
        ... Some intro text without headers.
        ... [FEEDBACK: Add a title header]
        ... '''
        >>> feedback = parse_inline_feedback(content)
        >>> feedback
        [('Document start', 'Add a title header')]
    """
    # Pattern to match [FEEDBACK: ...] markers (non-greedy to handle multiple on same line)
    # Uses negative lookahead to skip resolved markers: [FEEDBACK-RESOLVED: ...]
    # Uses DOTALL to support multiline feedback text
    feedback_pattern = re.compile(
        r"\[FEEDBACK:(?!-RESOLVED)\s*(.+?)\]",
        re.IGNORECASE | re.DOTALL,
    )

    # Pattern to match markdown headers
    header_pattern = re.compile(r"^(#+\s*.+)$", re.MULTILINE)

    # Find all headers with their positions
    headers: list[tuple[int, str]] = []
    for match in header_pattern.finditer(content):
        headers.append((match.start(), match.group(1).strip()))

    results: list[tuple[str, str]] = []

    # Find all feedback markers with their positions
    for match in feedback_pattern.finditer(content):
        marker_start = match.start()
        feedback_text = match.group(1).strip()

        # Find the most recent header before this marker
        context = "Document start"
        for header_pos, header_text in headers:
            if header_pos < marker_start:
                context = header_text
            else:
                break

        results.append((context, feedback_text))

    return results


def clean_feedback_markers(content: str) -> str:
    """Remove all feedback markers from content to produce clean spec.

    Removes both unresolved [FEEDBACK: ...] markers and resolved
    [FEEDBACK-RESOLVED: ... | FEEDBACK: ...] markers from the content.

    Args:
        content: The text content containing feedback markers.

    Returns:
        Clean content with all feedback markers removed.

    Example:
        >>> content = '''
        ... ## Step 1
        ... [FEEDBACK: Add more detail]
        ... Some text here.
        ... [FEEDBACK-RESOLVED: Added details | FEEDBACK: Add more detail]
        ... '''
        >>> clean = clean_feedback_markers(content)
        >>> clean
        '\\n## Step 1\\n\\nSome text here.\\n\\n'
    """
    # Remove resolved feedback markers first (they're longer and contain FEEDBACK:)
    resolved_pattern = re.compile(
        r"\[FEEDBACK-RESOLVED:\s*.+?\s*\|\s*FEEDBACK:\s*.+?\]",
        re.IGNORECASE | re.DOTALL,
    )
    content = resolved_pattern.sub("", content)

    # Remove unresolved feedback markers
    feedback_pattern = re.compile(
        r"\[FEEDBACK:\s*.+?\]",
        re.IGNORECASE | re.DOTALL,
    )
    content = feedback_pattern.sub("", content)

    # Clean up any resulting multiple blank lines (more than 2 newlines -> 2)
    content = re.sub(r"\n{3,}", "\n\n", content)

    return content


def resolve_feedback_markers(
    draft_path: Path,
    resolutions: list[tuple[str, str]],
) -> None:
    """Replace feedback markers with resolved markers in the draft file.

    Transforms unresolved feedback markers:
      [FEEDBACK: original text]
    To resolved markers:
      [FEEDBACK-RESOLVED: agent comment | FEEDBACK: original text]

    Args:
        draft_path: Path to the draft file containing feedback markers.
        resolutions: List of (original_feedback, agent_comment) tuples where:
            - original_feedback: The feedback text to find and resolve
            - agent_comment: The agent's comment about how it was addressed

    Example:
        >>> resolutions = [
        ...     ("Add more detail here", "Added implementation steps with examples"),
        ...     ("Consider edge cases", "Added error handling section"),
        ... ]
        >>> resolve_feedback_markers(Path("spec.md"), resolutions)
        # Transforms [FEEDBACK: Add more detail here] to
        # [FEEDBACK-RESOLVED: Added implementation steps with examples | FEEDBACK: Add more detail here]
    """
    if not draft_path.exists():
        return

    content = draft_path.read_text(encoding="utf-8")

    for original_feedback, agent_comment in resolutions:
        # Build the original marker pattern (case-insensitive)
        # We need to escape special regex characters in the feedback text
        escaped_feedback = re.escape(original_feedback)
        # Pattern matches [FEEDBACK: original text] with flexible whitespace
        pattern = re.compile(
            rf"\[FEEDBACK:\s*{escaped_feedback}\s*\]",
            re.IGNORECASE | re.DOTALL,
        )

        # Build the replacement marker
        resolved_marker = f"[FEEDBACK-RESOLVED: {agent_comment} | FEEDBACK: {original_feedback}]"

        # Replace the first occurrence
        content = pattern.sub(resolved_marker, content, count=1)

    draft_path.write_text(content, encoding="utf-8")


def display_diff(
    old_content: str,
    new_content: str,
    old_label: str = "Previous",
    new_label: str = "Current",
) -> None:
    """Display a colored unified diff between two text contents.

    Uses Rich to display additions in green and deletions in red,
    making it easy to see what changed between versions.

    Args:
        old_content: The original/previous version of the content.
        new_content: The new/current version of the content.
        old_label: Label for the old version (shown in diff header).
        new_label: Label for the new version (shown in diff header).

    Example:
        >>> display_diff(
        ...     "Line 1\\nLine 2\\nLine 3",
        ...     "Line 1\\nModified Line 2\\nLine 3\\nLine 4",
        ...     old_label="Draft v1",
        ...     new_label="Draft v2",
        ... )
        # Displays colored diff showing Line 2 changed and Line 4 added
    """
    console = Console()

    old_lines = old_content.splitlines(keepends=True)
    new_lines = new_content.splitlines(keepends=True)

    diff = difflib.unified_diff(
        old_lines,
        new_lines,
        fromfile=old_label,
        tofile=new_label,
        lineterm="",
    )

    diff_lines = list(diff)

    if not diff_lines:
        console.print("[dim]No changes detected.[/dim]")
        return

    # Build colored output
    styled_text = Text()
    for line in diff_lines:
        line_stripped = line.rstrip("\n")
        if line.startswith("+++") or line.startswith("---"):
            styled_text.append(line_stripped + "\n", style="bold")
        elif line.startswith("@@"):
            styled_text.append(line_stripped + "\n", style="cyan")
        elif line.startswith("+"):
            styled_text.append(line_stripped + "\n", style="green")
        elif line.startswith("-"):
            styled_text.append(line_stripped + "\n", style="red")
        else:
            styled_text.append(line_stripped + "\n")

    console.print(
        Panel(
            styled_text,
            title="[bold yellow]Changes Since Last Approval[/bold yellow]",
            border_style="yellow",
            expand=False,
        )
    )
