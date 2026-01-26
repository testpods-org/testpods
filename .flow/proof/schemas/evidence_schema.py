"""Evidence schema for validated evidence.json files.

This module defines strict Pydantic models for the evidence.json format,
enabling programmatic validation and consistent parsing by agents.

The schema hierarchy is:
- EvidenceReport (top-level)
  - cli_evidence: list[DemoEvidence]
    - commands: list[CommandEvidence]
  - web_evidence: list[WebEvidence] (from proof.types)
  - summary: EvidenceSummary
"""

from pydantic import BaseModel, Field

from proof.types import ShowcaseSpec, WebEvidence


class CommandEvidence(BaseModel):
    """Evidence from executing one CLI command.

    Records the result of executing a single CLI command, including
    separate stdout/stderr capture, timing, and pattern matching results.

    Attributes:
        command: The executed command string.
        exit_code: Process exit code (0 typically means success).
        stdout: Standard output from the command.
        stderr: Standard error from the command.
        duration_ms: Execution time in milliseconds.
        expected_pattern: Regex pattern that was expected in output (if specified).
        pattern_matched: Whether the expected pattern was found (None if no pattern).
        recording_path: Path to command-level .cast recording (if recorded).
    """

    model_config = {"extra": "forbid"}

    command: str = Field(description="The executed command")
    exit_code: int = Field(description="Process exit code")
    stdout: str = Field(description="Standard output")
    stderr: str = Field(description="Standard error")
    duration_ms: float = Field(description="Execution time in milliseconds")
    expected_pattern: str | None = Field(
        default=None,
        description="Regex pattern that was expected",
    )
    pattern_matched: bool | None = Field(
        default=None,
        description="Whether pattern matched",
    )
    recording_path: str | None = Field(
        default=None,
        description="Path to command-level .cast recording",
    )


class DemoEvidence(BaseModel):
    """Evidence from executing one demo (group of related commands).

    Represents the collected evidence from executing all commands in a
    single CLIDemoSpec. Each demo contains multiple commands that together
    demonstrate a feature or workflow.

    Attributes:
        name: Demo name from ShowcaseSpec.
        description: Demo description from ShowcaseSpec.
        commands: Evidence from each command executed in sequence.
        recording_path: Path to demo-level .cast recording (if demo recorded as session).
        passed: Whether all commands passed their expectations.
        failure_reason: If failed, why (first command that failed).
    """

    model_config = {"extra": "forbid"}

    name: str = Field(description="Demo name from ShowcaseSpec")
    description: str = Field(description="Demo description from ShowcaseSpec")
    commands: list[CommandEvidence] = Field(
        default_factory=list,
        description="Evidence from each command",
    )
    recording_path: str | None = Field(
        default=None,
        description="Path to demo-level .cast recording",
    )
    passed: bool = Field(description="Whether all commands passed expectations")
    failure_reason: str | None = Field(
        default=None,
        description="If failed, why",
    )


class EvidenceSummary(BaseModel):
    """Aggregate statistics for evidence collection.

    Provides high-level statistics about the evidence collection run,
    useful for quick assessment of proof quality.

    Attributes:
        total_demos: Number of demos executed.
        passed_demos: Number of demos that passed all expectations.
        failed_demos: Number of demos that failed at least one expectation.
        total_commands: Total commands executed across all demos.
        passed_commands: Commands that passed expectations.
        failed_commands: Commands that failed expectations.
    """

    model_config = {"extra": "forbid"}

    total_demos: int = Field(description="Number of demos executed")
    passed_demos: int = Field(description="Number that passed")
    failed_demos: int = Field(description="Number that failed")
    total_commands: int = Field(description="Total commands across all demos")
    passed_commands: int = Field(description="Commands that passed")
    failed_commands: int = Field(description="Commands that failed")


class EvidenceReport(BaseModel):
    """Top-level evidence package for a flow.

    This is the root model for evidence.json files. It contains all
    collected evidence along with metadata for reproducibility, plus
    optional enrichment fields for flow context.

    Attributes:
        collected_at: ISO timestamp of when evidence was collected.
        flow_name: Name of the implementation flow this evidence is for.
        showcase_spec: The ShowcaseSpec used for collection (for reproducibility).
        cli_evidence: List of CLI demo results with command-level evidence.
        web_evidence: List of web capture results (empty until Playwright implemented).
        summary: Aggregate statistics about the collection.
        errors: Any errors encountered during evidence collection.
        spec_plan_summary: Summary extracted from spec plan (enrichment).
        git_commits: Associated git commits (enrichment).
        agent_summaries: Summaries extracted from agent reports (enrichment).
    """

    model_config = {"extra": "forbid"}

    collected_at: str = Field(description="ISO timestamp of collection")
    flow_name: str = Field(description="Name of the implementation flow")
    showcase_spec: ShowcaseSpec | None = Field(
        default=None,
        description="The spec used (for reproducibility)",
    )
    cli_evidence: list[DemoEvidence] = Field(
        default_factory=list,
        description="CLI demo results",
    )
    web_evidence: list[WebEvidence] = Field(
        default_factory=list,
        description="Web capture results (empty until Playwright implemented)",
    )
    summary: EvidenceSummary = Field(description="Aggregate stats")
    errors: list[str] = Field(
        default_factory=list,
        description="Any collection errors",
    )
    # Enrichment fields (populated from flow artifacts)
    spec_plan_summary: str | None = Field(
        default=None,
        description="Summary extracted from spec plan",
    )
    git_commits: list[str] = Field(
        default_factory=list,
        description="Associated git commits",
    )
    agent_summaries: list[str] = Field(
        default_factory=list,
        description="Summaries extracted from agent reports",
    )
