# Flow CLI - Python API Documentation

Comprehensive documentation for the Flow CLI Python API, enabling multi-agent orchestration from Python scripts.

## Setup (No Pre-installation Required)

The Python API uses **uv single-file scripts** with PEP 723 inline dependencies. No separate installation is required - uv handles dependency resolution automatically on first run.

### Script Template

```python
#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = ["flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli"]
# ///

from flow import AgentInfo, Flow

# Your orchestration code here
```

Save as `implement.py`, make executable (`chmod +x implement.py`), and run: `./implement.py`

### Alternative: Install as Package

```bash
# Install from git
pip install "flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli"

# Or clone and install locally
git clone https://github.com/ebbe-brandstrup/flow-cli.git
cd flow-cli && pip install -e .
```

## Configuration

Flow CLI requires a config file in your project's `.flow/` directory. Configuration is organized by flow type (e.g., `.flow/implement/base.yaml`). This file defines agent types, timeouts, and shared context files.

### Minimal Configuration

```yaml
# .flow/implement/base.yaml
agent_interface: claude_code_cli # or opencode

# Directory where flow data is stored
flow_dir: flows

# Default timeouts (in seconds)
defaults:
  stuck_timeout_secs: 1200 # 20 minutes - no activity = stuck
  max_wait_secs: 3600 # 60 minutes - maximum wait time

# Agent type definitions
agents:
  builder:
    model: anthropic/opus # provider/model format
    system_prompt: .claude/agents/builder.md
```

### Full Configuration Example

```yaml
agent_interface: claude_code_cli # or opencode
flow_dir: flows
default_branch: main

defaults:
  stuck_timeout_secs: 1200
  max_wait_secs: 3600

# OpenCode-specific configuration (only used when agent_interface: opencode)
opencode:
  server_hostname: 127.0.0.1

# Spec plan parsing configuration
# Pattern must have exactly 3 capture groups: (status) (step_number) (title)
spec_plan:
  top_level_end_marker: "## Implementation Steps"
  step_header_pattern: "^### Status: ([^|]*) \\| Step (\\d+): (.+)$"

# Completion workflow configuration
# IMPORTANT: The template is REQUIRED for agents to understand the completion protocol.
# Without a template, the report config values (filename, success_suffix, failure_suffix)
# are not exposed to agents - they only exist as placeholder substitutions in the template.
completion_workflow:
  template: "@.flow/templates/completion-workflow-template.md"
  report:
    filename: report.md
    success_suffix: .complete.md
    failure_suffix: .failed.md

# Shared context files (read by all agents in a flow)
shared_files:
  implementation_log:
    path: "{flow_dir}/{flow_name}/implementation-log.md"
    info: "Accumulated decisions from previous steps"
    writable: true
    write_template: "@.flow/templates/implementation-log-template.md"

  architecture_guide:
    path: "docs/architecture.md"
    info: "System architecture documentation"
    agents: # Only include for specific agent types
      - builder
      - reviewer

# Model format: provider/model (e.g., anthropic/sonnet, anthropic/opus)
# Claude Code CLI: extracts model part after "/" (anthropic/opus → opus)
# OpenCode: uses full string (anthropic/claude-sonnet-4-5)
agents:
  builder:
    description: "Implements code changes following the spec plan. The reviewer validates your work."
    model: anthropic/opus
    system_prompt: "@.claude/agents/builder.md"

  reviewer:
    description: "Validates code changes (read-only review). Never modifies source files directly."
    model: anthropic/sonnet
    system_prompt: "@.claude/agents/reviewer.md"

  # Per-agent context window override (tokens)
  large-context-agent:
    model: anthropic/opus
    context_window: 180000 # Override model's default context window

# Global model context window overrides (model → tokens)
# Takes precedence over built-in registry but overridden by agent-level settings
model_context_windows:
  anthropic/opus: 180000 # Override default for all opus agents
  openai/gpt-4o: 100000 # Override default for all gpt-4o agents
```

### Declared Artifacts

Agents can declare artifacts they are expected to produce. These are validated after agent completion, and if any are missing, the agent session is automatically resumed to fix them.

```yaml
agents:
  builder:
    model: anthropic/opus
    system_prompt: "@.claude/agents/builder.md"
    declared_artifacts:
      # Report with alternatives (for renamed completion files)
      - type: report
        path: "{agent_dir}/report.complete.md"
        alternatives:
          - "{agent_dir}/report.failed.md"
        description: "Completion report (renamed from report.md)"
      # Append-mode artifact (alternatives ignored)
      - type: log_entry
        path: "{flow_dir}/{flow_name}/implementation-log.md"
        append: true
        description: "Implementation log entry for this step"
```

**Artifact Fields:**

- `type` - Category of artifact (e.g., "report", "log_entry", "code_change")
- `path` - Path template with variables: `{agent_dir}`, `{flow_dir}`, `{flow_name}`
- `append` - If true, validates that the file grew (for log files). If false (default), validates the file exists.
- `description` - Human-readable description included in prompts when resuming to fix missing artifacts.
- `alternatives` - Alternative paths that can satisfy this artifact. For non-append artifacts, validation passes if `path` exists OR any path in `alternatives` exists. Ignored for append-mode artifacts. Uses same template variables as `path`.

**Path Template Variables:**

- `{agent_dir}` - Resolved to the agent's directory (e.g., `flows/my-flow/step-1/agents/step-01-builder`)
- `{flow_dir}` - Resolved to the flow's directory (e.g., `flows/my-flow`)
- `{flow_name}` - Resolved to the name of the flow (e.g., `my-flow`)

**Validation Behavior:**

- Artifacts are validated after each agent completes via `await_all()` (enabled by default)
- If artifacts are missing, the agent session is resumed with a prompt explaining what's missing
- The agent can make up to `artifact_retry_limit` (default: 3) attempts to produce the artifacts
- If validation still fails after retries, `ArtifactValidationError` is raised

**Disabling Validation:**

```python
# Skip artifact validation for this await
results = flow.await_all([agent1, agent2], validate_artifacts=False)
```

### Completion Workflow Requirement

**CRITICAL**: The `completion_workflow.template` field is **required** for agents to understand the completion signaling protocol.

The `report` config values (`filename`, `success_suffix`, `failure_suffix`) define what files agents should use for completion, but these values are **only exposed to agents through template substitution**:

- `{report_file}` is replaced with the configured filename (e.g., `report.md`)
- `{success_file}` is replaced with the success filename (e.g., `report.complete.md`)
- `{failed_file}` is replaced with the failure filename (e.g., `report.failed.md`)

**Without a template, agents have no way to know about the completion protocol.**

**Minimal template example** (for simple flows):

```markdown
## Completion Protocol

When you've completed your task, signal completion by renaming your report file:

- **Success**: Rename `{report_file}` to `{success_file}` - Use when you completed your work
- **Failure**: Rename `{report_file}` to `{failed_file}` - Use ONLY when you could NOT complete

**IMPORTANT**: "Success" means you finished your task, regardless of outcome.

- A reviewer who finds issues signals Success (the review was successful!)
- Only signal Failure if you genuinely couldn't complete (crash, error, blocked).

Write a brief summary of your work to `{report_file}` before renaming it.
```

Save this as `.flow/templates/completion-workflow-simple.md` and reference it in your config:

```yaml
completion_workflow:
  template: "@.flow/templates/completion-workflow-simple.md"
  report:
    filename: report.md
    success_suffix: .complete.md
    failure_suffix: .failed.md
```

For production flows, see `.flow/templates/completion-workflow-template.md` in the Flow CLI repository for a general-purpose template with structured reporting. For specialized workflows:

- **Implementation flows** (code changes): Use `implementation-completion-workflow-template.md` which includes git statistics
- **Planning flows** (spec creation): Use `planning-completion-workflow-template.md` with streamlined reporting

## Core API

### Flow Class

The main orchestration interface. A `Flow` represents a named orchestration session that tracks multiple agents working together.

#### Constructor

```python
Flow(
    name: str,
    *,
    plan_path: str | None = None,
    specs_folder: str = "specs",
    reset: bool = False,
    config_path: str | Path | None = None,
    flow_type: str | None = None,
    variant: str | None = None,
)
```

**Parameters:**

- `name` - Flow name (alphanumeric, hyphens, underscores only). Used in directory paths.
- `plan_path` - Optional path to the plan file for this flow.
- `specs_folder` - Specs folder path (default: "specs").
- `reset` - If `True`, delete existing flow before initializing. Use when starting fresh.
- `config_path` - Path to config file. Either `config_path` or `flow_type` is required.
- `flow_type` - Flow type (e.g., "implement"). Resolves to `.flow/{flow_type}/base.yaml` or `.flow/{flow_type}/{variant}.yaml`. Either `config_path` or `flow_type` is required.
- `variant` - Optional variant name (e.g., "new-feature"). Only used with `flow_type`.

**Example:**

```python
from flow import Flow

# Using flow_type (resolves to .flow/implement/base.yaml)
flow = Flow("implement-auth", flow_type="implement", plan_path="specs/auth.md")

# Using explicit config_path
flow = Flow("implement-auth", config_path=".flow/implement/base.yaml")

# Using flow_type with variant (resolves to .flow/implement/new-feature.yaml)
flow = Flow("implement-auth", flow_type="implement", variant="new-feature")

# Start fresh (deletes existing flow data)
flow = Flow("implement-auth", flow_type="implement", reset=True)
```

#### Flow.run()

Spawn an agent and wait for completion. This is the most common pattern.

```python
def run(
    self,
    agent_type: str,
    *,
    agent: AgentInfo,
    input: str,
    after: str | list[str] | None = None,
    mcp_config: str | None = None,
    agent_interface: AgentInterfaceType | None = None,
    plan_path: str | None = None,
    assignment_preamble: str | None = None,
    auto_retry: int = 0,
    stuck_timeout_secs: int | None = None,
    max_wait_secs: int | None = None,
    initiated_by: str | None = None,
) -> AwaitWithRetryResult
```

**Parameters:**

- `agent_type` - Type of agent to spawn (must be defined in your flow config).
- `agent` - `AgentInfo` specifying agent naming (step+role or explicit agent_id).
- `input` - Task description for the agent.
- `after` - Agent ID(s) whose reports to include as context for chaining.
- `mcp_config` - Override MCP config path for this agent.
- `agent_interface` - Override agent interface for this call (overrides config hierarchy).
- `plan_path` - Optional path to spec plan file. If provided with `agent.step`, plan content is parsed and inlined in the system prompt.
- `assignment_preamble` - Optional preamble text to insert at the start of the "## Your Assignment" section, before the task content. Useful for providing context-setting instructions (e.g., for reviewer agents).
- `auto_retry` - Max retry attempts on stuck/timeout (default: 0). Note: Rate limit errors are not retried, as retrying immediately won't help.
- `stuck_timeout_secs` - Seconds before considering agent stuck. Overrides config default.
- `max_wait_secs` - Maximum seconds to wait for completion. Overrides config default.
- `initiated_by` - Caller identifier for breadcrumb tracking.

**Returns:** `AwaitWithRetryResult` with `exit_reason`, `final_agent`, `attempts`, `report_file`.

**Example:**

```python
result = flow.run(
    "builder",
    agent=AgentInfo(step=1, role="builder"),
    input="Implement user authentication",
    auto_retry=3,  # Retry up to 3 times if stuck
)

if result.exit_reason != "completed":
    raise SystemExit(f"Build failed: {result.exit_reason}")
```

**Example with assignment_preamble (for reviewers):**

```python
# The preamble provides context-setting instructions before the task content
result = flow.run(
    "reviewer",
    agent=AgentInfo(step=1, role="reviewer"),
    input="Review the changes",
    plan_path="specs/feature.md",
    assignment_preamble="It is now your job to review the changes for this plan-step:",
)
```

This produces a system prompt with the assignment section formatted as:

````markdown
## Your Assignment

It is now your job to review the changes for this plan-step:

```md
# Feature Plan

...
```
````

#### Flow.spawn()

Spawn an agent asynchronously (non-blocking). Use with `await_all()` for parallel execution.

```python
def spawn(
    self,
    agent_type: str,
    *,
    agent: AgentInfo,
    input: str,
    after: str | list[str] | None = None,
    mcp_config: str | None = None,
    agent_interface: AgentInterfaceType | None = None,
    plan_path: str | None = None,
    assignment_preamble: str | None = None,
    initiated_by: str | None = None,
) -> SpawnResult
```

**Parameters:**

- `agent_type` - Type of agent to spawn (must be defined in your flow config).
- `agent` - `AgentInfo` specifying agent naming (step+role or explicit agent_id).
- `input` - Task description for the agent.
- `after` - Agent ID(s) whose reports to include as context for chaining.
- `mcp_config` - Override MCP config path for this agent.
- `agent_interface` - Override agent interface for this call (overrides config hierarchy).
- `plan_path` - Optional path to spec plan file. If provided with `agent.step`, plan content is parsed and inlined in the system prompt.
- `assignment_preamble` - Optional preamble text to insert at the start of the "## Your Assignment" section, before the task content. Useful for providing context-setting instructions (e.g., for reviewer agents).
- `initiated_by` - Caller identifier for breadcrumb tracking.

**Returns:** `SpawnResult` with `agent_id`, `pid`, `report_file`.

**Example:**

```python
# Launch multiple agents in parallel
reviewer1 = flow.spawn(
    "reviewer",
    agent=AgentInfo(step=1, role="security-reviewer"),
    input="Review authentication implementation for security issues",
)

reviewer2 = flow.spawn(
    "reviewer",
    agent=AgentInfo(step=1, role="api-reviewer"),
    input="Review API design and consistency",
)

# Wait for all to complete
results = flow.await_all([reviewer1, reviewer2])
```

#### Flow.await_all()

Wait for multiple agents to complete (blocking).

```python
def await_all(
    self,
    agents: list[str] | list[SpawnResult],
    *,
    stuck_timeout_secs: int | None = None,
    max_wait_secs: int | None = None,
    initiated_by: str | None = None,
    validate_artifacts: bool = True,
    artifact_retry_limit: int = 3,
) -> list[AwaitResult]
```

**Parameters:**

- `agents` - List of agent IDs or `SpawnResult` objects to wait for.
- `stuck_timeout_secs` - Seconds before considering agents stuck.
- `max_wait_secs` - Maximum seconds to wait for completion.
- `initiated_by` - Caller identifier for breadcrumb tracking.
- `validate_artifacts` - If True (default), validate declared artifacts after each agent completes. If artifacts are missing, resumes the agent session to fix them.
- `artifact_retry_limit` - Max resume attempts for missing artifacts (default: 3).

**Returns:** List of `AwaitResult`, one per agent.

**Raises:** `ArtifactValidationError` if an agent fails to produce required artifacts after retries.

#### Flow.set_status()

Update the flow's overall status. Call when flow completes or fails.

```python
def set_status(self, status: str) -> None
```

**Parameters:**

- `status` - One of the `FlowStatusValue` values: `"running"`, `"completed"`, `"failed"`, `"interrupted"`. Enum values are also accepted since `FlowStatusValue` inherits from `str`.

**Example:**

```python
from flow.types import FlowStatusValue

try:
    # ... run agents ...
    flow.set_status("completed")  # String works
    # or: flow.set_status(FlowStatusValue.COMPLETED)  # Enum also works
except Exception:
    flow.set_status("failed")
    raise
```

#### Flow.mark_step_completed()

Mark a step as completed. Call after all agents for a step have finished successfully.

```python
def mark_step_completed(self, step: int) -> None
```

**Parameters:**

- `step` - Step number to mark as completed (must be positive).

This method is idempotent - calling it multiple times with the same step number is safe.

#### Flow.set_total_steps()

Register the total number of planned steps. Enables progress tracking in dashboard.

```python
def set_total_steps(self, total_steps: int) -> None
```

**Important:** When resuming flows, ensure you pass the **total** step count, not just the remaining/pending steps. If you filter out completed steps, get the full count separately:

```python
from flow.lib.spec_parser import get_all_steps, get_pending_steps

# CORRECT: Always use the total count from ALL steps
all_steps = get_all_steps(plan_path, flow.config)
pending_steps = get_pending_steps(plan_path, flow.config)
flow.set_total_steps(len(all_steps))  # Total steps in plan

for step_number, step_title in pending_steps:
    # Process only pending steps...
```

```python
# INCORRECT: This breaks progress tracking on resume
pending_steps = get_pending_steps(plan_path, flow.config)
flow.set_total_steps(len(pending_steps))  # Wrong! Overwrites correct total
```

#### Flow.has_completed_agent()

Check if an agent with a given role completed successfully for a step. Essential for resume capability.

```python
def has_completed_agent(self, step: int, role: str) -> bool
```

**Parameters:**

- `step` - Step number (1-based)
- `role` - Agent role (e.g., "builder", "reviewer")

**Returns:** `True` if at least one agent with the given role completed for this step.

**Example:**

```python
if flow.has_completed_agent(step=1, role="builder"):
    print("Builder already completed for step 1, skipping...")
else:
    # Run the builder
    flow.run("builder", agent=AgentInfo(step=1, role="builder"), ...)
```

#### Flow.get_last_agent_id()

Get the agent ID of the last completed agent with a given role. Used for chaining.

```python
def get_last_agent_id(self, step: int, role: str) -> str | None
```

**Returns:** Agent ID of the last completed agent, or `None` if not found.

**Example:**

```python
# Resume from where we left off
builder_id = flow.get_last_agent_id(step=1, role="builder")
if builder_id:
    # Chain reviewer from builder's output
    flow.run(
        "reviewer",
        agent=AgentInfo(step=1, role="reviewer"),
        input="Review the implementation",
        after=builder_id,  # Include builder's report as context
    )
```

#### Flow.get_statistics()

Get comprehensive flow statistics including timing, agent counts, and success rates.

```python
def get_statistics(self, fixer_pattern: str = "fixer") -> FlowStatistics | None
```

**Parameters:**

- `fixer_pattern` - String pattern to identify fixer agents (default: "fixer")

**Returns:** `FlowStatistics` object or `None` if flow not found.

**Example:**

```python
stats = flow.get_statistics()
if stats:
    print(f"Duration: {stats.total_duration_formatted}")
    print(f"Success Rate: {stats.success_rate_percentage}%")
    print(f"Total Agents: {stats.total_agents}")
```

#### Flow.get_logger()

Get a logger for this flow, configured to write to the flow's log file.

```python
def get_logger(self, component: str = "orchestration") -> logging.Logger
```

**Parameters:**

- `component` - Logger component name (default: "orchestration"). Appears in log entries for filtering.

**Returns:** A `logging.Logger` instance configured to write to `flows/<flow-name>/flow.log`.

**Example:**

```python
flow = Flow("my-feature", flow_type="implement")
logger = flow.get_logger()
logger.info("Starting build phase")

# With custom component name for filtering
build_logger = flow.get_logger("build-phase")
build_logger.info("Building module X")
```

This is the recommended way to get a logger in orchestration scripts. Previously, you would need to import `get_flow_logger` directly:

```python
# Old approach (still works, but prefer flow.get_logger())
from flow.lib.logging_setup import get_flow_logger
logger = get_flow_logger(flow.name, "orchestration")
```

#### Flow.cleanup()

Kill all running agents for this flow and update their status to `"interrupted"` with `exit_reason="interrupted"`. This is called automatically when using Flow as a context manager.

```python
def cleanup(self) -> list[str]
```

**Returns:** List of agent IDs that were killed. Empty list if no agents were running or termination failed.

**Example:**

```python
# Manual cleanup (prefer context manager instead)
flow = Flow("my-flow", flow_type="implement")
# ... spawn agents ...
killed = flow.cleanup()
print(f"Killed agents: {killed}")
```

#### Context Manager Usage (Recommended)

The `Flow` class supports the context manager protocol for automatic cleanup when the orchestration exits (normal completion or exception/interrupt).

```python
with Flow("my-flow", flow_type="implement") as flow:
    result = flow.run("builder", agent=AgentInfo(step=1, role="builder"), ...)
    # If interrupted (Ctrl+C) or exception occurs,
    # running agents are automatically terminated
# Cleanup happens here, even on exception
```

**Why use context manager?**

- **Automatic cleanup**: Running agents are terminated when the `with` block exits
- **Interrupt handling**: Ctrl+C during orchestration properly cleans up spawned agents
- **Exception safety**: Agents are cleaned up even when exceptions occur
- **Status tracking**: Killed agents are marked as `"interrupted"` with `exit_reason="interrupted"`

**Recommended pattern for orchestration scripts:**

```python
#!/usr/bin/env -S uv run
from flow import AgentInfo, Flow

with Flow("implement-feature", flow_type="implement", reset=True) as flow:
    try:
        result = flow.run(
            "builder",
            agent=AgentInfo(step=1, role="builder"),
            input="Implement the feature",
        )
        if result.exit_reason != "completed":
            flow.set_status("failed")
            raise SystemExit(f"Build failed: {result.exit_reason}")

        flow.set_status("completed")
    except Exception:
        flow.set_status("failed")
        raise
# Agents are automatically cleaned up on exit
```

### AgentInfo Class

Specifies how to identify/name an agent. Use either step+role for auto-generated naming, or explicit agent_id.

```python
from dataclasses import dataclass

@dataclass
class AgentInfo:
    agent_id: str | None = None
    step: int | None = None
    role: str | None = None
    step_title: str | None = None
```

**Usage Patterns:**

```python
from flow import AgentInfo

# Auto-generated agent ID: "step-01-builder"
AgentInfo(step=1, role="builder")

# Auto-generated with title (for templates): "step-01-builder"
AgentInfo(step=1, role="builder", step_title="Add authentication")

# Explicit agent ID (bypasses auto-generation)
AgentInfo(agent_id="my-custom-agent")
```

When using step+role, the agent ID is generated as `step-{NN}-{role}` (e.g., `step-01-builder`). If an agent with that ID already exists, a counter suffix is added (`step-01-builder-2`).

### Result Types

#### SpawnResult

Returned by `Flow.spawn()`.

```python
class SpawnResult(BaseModel):
    agent_id: str          # Unique identifier for the agent
    pid: int               # Process ID of the spawned agent
    report_file: str       # Path to the agent's report file
    warnings: list[str]    # Any warnings during spawn
```

#### AwaitResult

Returned by `Flow.await_all()`.

```python
class AwaitResult(BaseModel):
    exit_reason: ExitReason     # Why the agent stopped (see ExitReason enum)
    agent_status: AgentStatus   # Full status of the agent
    report_file: str | None     # Path to completion report (if exists)
```

#### AwaitWithRetryResult

Returned by `Flow.run()`.

```python
class AwaitWithRetryResult(BaseModel):
    exit_reason: ExitReason        # Why the agent stopped (see ExitReason enum)
    final_agent: str               # ID of the final agent (may differ if retries occurred)
    attempts: list[RetryAttempt]   # History of all attempts
    report_file: str | None        # Path to final completion report
```

**Exit Reasons:**

The `ExitReason` enum defines why an agent stopped executing:

| Exit Reason    | Description                                                  |
| -------------- | ------------------------------------------------------------ |
| `completed`    | Agent finished successfully (report renamed to .complete.md) |
| `failed`       | Agent failed (report renamed to .failed.md)                  |
| `stuck`        | No activity detected for stuck_timeout_secs                  |
| `timeout`      | max_wait_secs exceeded                                       |
| `process_died` | Agent process terminated unexpectedly                        |
| `interrupted`  | Agent was intentionally stopped (e.g., via Ctrl+C)           |

### Status Enums

The Flow CLI uses strongly-typed enums for all status-related values. These enums inherit from both `str` and `Enum`, allowing them to be compared with strings while providing type safety and IDE autocomplete.

```python
from flow.types import AgentStatusValue, FlowStatusValue, ExitReason, ContextHealthStatus, StepStatus
```

#### AgentStatusValue

Valid agent lifecycle states:

| Value         | Description                                        |
| ------------- | -------------------------------------------------- |
| `pending`     | Agent has been created but not yet started         |
| `running`     | Agent is currently executing                       |
| `completed`   | Agent finished successfully                        |
| `failed`      | Agent encountered an error                         |
| `stuck`       | Agent is unresponsive or in a stuck state          |
| `interrupted` | Agent was intentionally stopped (e.g., via Ctrl+C) |

#### FlowStatusValue

Valid flow states:

| Value         | Description                                          |
| ------------- | ---------------------------------------------------- |
| `running`     | Flow is currently executing with active agents       |
| `completed`   | Flow finished successfully with all agents completed |
| `failed`      | Flow ended due to one or more agent failures         |
| `interrupted` | Flow was intentionally stopped (e.g., via Ctrl+C)    |

#### ExitReason

Why an agent stopped executing (see Exit Reasons table above).

#### ContextHealthStatus

Context window health indicators:

| Value      | Description                                      |
| ---------- | ------------------------------------------------ |
| `HEALTHY`  | Context window usage is within acceptable limits |
| `WARNING`  | Context window usage is approaching capacity     |
| `CRITICAL` | Context window usage is at or near capacity      |

#### StepStatus

Step monitoring result states:

| Value      | Description                                               |
| ---------- | --------------------------------------------------------- |
| `complete` | Step finished successfully with all checks passed         |
| `failed`   | Step failed due to agent failures or missing requirements |
| `pending`  | Step has not yet completed                                |

**Usage Example:**

```python
from flow.types import AgentStatusValue, ExitReason, FlowStatusValue

# Enum values work with string comparison (because they inherit from str)
if agent.status == AgentStatusValue.COMPLETED:
    print("Agent completed")

# Also works with string literals
if result.exit_reason == "completed":
    print("Success")

# Use .value to get the string representation
print(f"Status: {agent.status.value}")  # Output: "Status: completed"
```

### Exception Types

```python
from flow import FlowError, FlowInitError, AgentError, ArtifactValidationError

class FlowError(Exception):
    """Base exception for Flow API errors."""

class FlowInitError(FlowError):
    """Raised when flow initialization fails."""

class AgentError(FlowError):
    """Raised when agent operations fail."""

class ArtifactValidationError(Exception):
    """Raised when artifact validation fails after retries."""
```

#### ArtifactValidationError

Raised when an agent fails to produce required declared artifacts after the maximum number of resume attempts. This exception is raised by `await_all()` when `validate_artifacts=True` (the default).

```python
from flow import ArtifactValidationError

try:
    results = flow.await_all([agent1, agent2])
except ArtifactValidationError as e:
    print(f"Agent failed to produce artifacts: {e}")
```

## Common Patterns

### Pattern 1: Simple Sequential Workflow

Run agents one after another, each building on the previous.

```python
#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = ["flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli"]
# ///

from flow import AgentInfo, Flow

flow = Flow("simple-workflow", flow_type="implement", reset=True)

# Step 1: Build
result = flow.run(
    "builder",
    agent=AgentInfo(step=1, role="builder"),
    input="Implement the user login feature",
)

if result.exit_reason != "completed":
    raise SystemExit(f"Build failed: {result.exit_reason}")

# Step 2: Review (chain from builder)
result = flow.run(
    "reviewer",
    agent=AgentInfo(step=1, role="reviewer"),
    input="Review the login implementation",
    after=result.final_agent,  # Include builder's report as context
)

if result.exit_reason != "completed":
    raise SystemExit(f"Review failed: {result.exit_reason}")

flow.set_status("completed")
print("Workflow completed successfully!")
```

### Pattern 2: Build-Test-Fix Loop

Iteratively build and fix until validation passes.

```python
#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = ["flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli"]
# ///

import subprocess
from flow import AgentInfo, Flow

MAX_FIX_ATTEMPTS = 3

flow = Flow("build-test-fix", flow_type="implement", reset=True)

# Initial build
result = flow.run(
    "builder",
    agent=AgentInfo(step=1, role="builder"),
    input="Implement the payment processing module",
    auto_retry=2,
)

if result.exit_reason != "completed":
    raise SystemExit(f"Initial build failed: {result.exit_reason}")

last_agent = result.final_agent

# Validation loop
for attempt in range(MAX_FIX_ATTEMPTS):
    # Run tests
    test_result = subprocess.run(
        ["pytest", "tests/", "-v"],
        capture_output=True,
        text=True,
    )

    if test_result.returncode == 0:
        print("All tests passed!")
        break

    print(f"Tests failed (attempt {attempt + 1}), sending to fixer...")

    # Fix validation failures
    fix_result = flow.run(
        "builder",
        agent=AgentInfo(step=1, role="fixer"),
        input=f"Fix test failures:\n\n{test_result.stdout}\n{test_result.stderr}",
        after=last_agent,
        auto_retry=2,
    )

    if fix_result.exit_reason != "completed":
        raise SystemExit(f"Fix attempt failed: {fix_result.exit_reason}")

    last_agent = fix_result.final_agent
else:
    raise SystemExit(f"Tests still failing after {MAX_FIX_ATTEMPTS} fix attempts")

flow.set_status("completed")
```

### Pattern 3: Build-Review-Fix Loop

The build-review-fix loop is a comprehensive pattern for ensuring quality through iterative review cycles. This pattern chains a builder agent with a reviewer agent, iterating until the reviewer approves or max attempts are reached.

**Flow:**

```
Build → Review → [Fix → Re-review]* → Done
```

**Key Components:**

- **Builder Agent**: Implements or fixes code/documentation
- **Reviewer Agent**: Validates quality and provides feedback
- **Approval Marker**: String that signals approval (e.g., `"REVIEW_RESULT: APPROVED"`)
- **Max Attempts**: Limit on review-fix iterations

**Using Orchestration Utilities:**

The `run_review_loop()` function from `scripts.flows.lib` encapsulates this pattern:

```python
from scripts.flows.lib import ReviewLoopConfig, run_review_loop

# Configure the review loop
review_config = ReviewLoopConfig(
    reviewer_agent="reviewer",
    fixer_agent="builder",
    reviewer_role="reviewer",
    fixer_role="review-fixer",
    max_attempts=3,
    approval_marker="REVIEW_RESULT: APPROVED",
    fail_flow_on_max_attempts=True,
)

# Run the review loop (handles iteration, approval checking, and chaining)
result = run_review_loop(
    flow=flow,
    config=review_config,
    step=step_num,
    reviewer_input="Review the implementation",
    fixer_input_fn=lambda report: f"Fix issues from review: {report}",
    after_agent_id=build_result.final_agent,
    logger=logger,
    plan_path=plan_path,  # Optional: for reviewer context
)

if not result.approved:
    flow.set_status("failed")
    raise SystemExit("Review not approved after max attempts")
```

**Best Practices:**

- Use descriptive role names (e.g., `"reviewer"`, `"review-fixer"`) for tracking
- Include the approval marker instruction in reviewer input
- Chain fixers after reviewers using `after` parameter
- Consider different failure modes: fail flow vs warn and continue
- Add resume support with `has_completed_agent()` and `get_last_agent_id()`

**For a complete implementation, see `.flow/scripts/flows/implement.py`.**

### Pattern 4: Parallel Agents with await_all

Run multiple agents simultaneously for tasks that don't depend on each other.

```python
#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = ["flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli"]
# ///

from flow import AgentInfo, Flow

flow = Flow("parallel-review", flow_type="implement", reset=True)

# Build first
build_result = flow.run(
    "builder",
    agent=AgentInfo(step=1, role="builder"),
    input="Implement the API endpoints",
)

if build_result.exit_reason != "completed":
    raise SystemExit(f"Build failed: {build_result.exit_reason}")

# Spawn multiple reviewers in parallel
reviewers = []
review_types = [
    ("security", "Review for security vulnerabilities"),
    ("performance", "Review for performance issues"),
    ("api-design", "Review API design and consistency"),
]

for role_suffix, task in review_types:
    spawn_result = flow.spawn(
        "reviewer",
        agent=AgentInfo(step=1, role=f"{role_suffix}-reviewer"),
        input=task,
        after=build_result.final_agent,
    )
    reviewers.append(spawn_result)

# Wait for all reviewers to complete
results = flow.await_all(reviewers)

# Check results
all_passed = all(r.exit_reason == "completed" for r in results)
if not all_passed:
    failed = [r for r in results if r.exit_reason != "completed"]
    raise SystemExit(f"Some reviews failed: {[r.exit_reason for r in failed]}")

flow.set_status("completed")
print("All reviews completed successfully!")
```

### Pattern 5: Agent Chaining with `after` Parameter

Pass context from one agent to another using the `after` parameter.

```python
#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = ["flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli"]
# ///

from flow import AgentInfo, Flow

flow = Flow("chained-workflow", flow_type="implement", reset=True)

# Architect designs the system
architect_result = flow.run(
    "architect",
    agent=AgentInfo(step=1, role="architect"),
    input="Design the database schema for user management",
)

# Builder implements based on architect's design
builder_result = flow.run(
    "builder",
    agent=AgentInfo(step=1, role="builder"),
    input="Implement the database schema",
    after=architect_result.final_agent,  # Architect's report included
)

# Tester writes tests based on both architect and builder output
tester_result = flow.run(
    "tester",
    agent=AgentInfo(step=1, role="tester"),
    input="Write comprehensive tests for the database schema",
    after=[architect_result.final_agent, builder_result.final_agent],  # Multiple agents
)

flow.set_status("completed")
```

### Pattern 6: Resume Capability

Resume interrupted workflows without re-running completed steps using the Flow API's built-in resume support.

**Key Methods:**

- `has_completed_agent(step, role)` - Check if an agent already completed for a step
- `get_last_agent_id(step, role)` - Get the last completed agent's ID for chaining
- `mark_step_completed(step)` - Mark a step as completed after all agents finish

**Important:** When resuming a flow that previously failed or was interrupted, reset the status to `"running"` to provide visibility that the flow is being actively worked on.

**Basic Resume Pattern:**

```python
from flow import AgentInfo, Flow
from flow.lib.flow_status import read_status
from flow.types import FlowStatusValue

# Don't use reset=True to preserve existing state
flow = Flow("resumable-workflow", flow_type="implement")

# Reset status to "running" when resuming a failed/interrupted flow
current_status = read_status(flow.name, base_dir=flow.base_dir)
if current_status and current_status.status in (
    FlowStatusValue.FAILED,
    FlowStatusValue.INTERRUPTED,
):
    flow.set_status("running")

for step_num, step_title in steps:
    # Check if builder already completed (for resume)
    if flow.has_completed_agent(step=step_num, role="builder"):
        print("  Builder already completed, skipping...")
        last_agent = flow.get_last_agent_id(step=step_num, role="builder")
    else:
        result = flow.run(
            "builder",
            agent=AgentInfo(step=step_num, role="builder"),
            input=f"Implement step {step_num}: {step_title}",
        )
        last_agent = result.final_agent

    # Similar pattern for reviewers...
    flow.mark_step_completed(step_num)
```

**For a complete production implementation with validation and review loops, see `.flow/scripts/flows/implement.py`.**

## Complete Working Examples

For production-ready orchestration scripts demonstrating all the patterns above, see these working examples in the Flow CLI repository:

| Script                             | Description                                                                                         |
| ---------------------------------- | --------------------------------------------------------------------------------------------------- |
| `.flow/scripts/flows/implement.py` | Full implementation workflow with build-validate-review cycles, resume support, and git integration |
| `.flow/scripts/flows/plan.py`      | Planning workflow with interactive clarifications, showcase generation, and multi-stage review      |

These scripts demonstrate:

- Multi-step plan parsing and execution
- Build → Validate → [Fix → Validate]\* → Review → [Fix → Re-review]\* → Commit workflow
- Resume capability using `has_completed_agent()` and `get_last_agent_id()`
- Validation integration via `run_validation()` from `scripts.flows.lib`
- Review loops using `run_review_loop()` with configurable reviewers and approval markers
- Git commit and push automation via `commit_and_push_changes()` from `scripts.flows.lib`
- Statistics display after completion via `flow.get_statistics()`
- Keyboard interrupt handling with flow state preservation

## Tips and Best Practices

### 1. Always Handle Errors

```python
try:
    # ... run agents ...
    flow.set_status("completed")
except Exception:
    flow.set_status("failed")
    raise
```

**Important: `sys.exit()` bypasses exception handlers!**

`sys.exit()` raises `SystemExit`, which inherits from `BaseException`, not `Exception`. This means `sys.exit()` calls inside a try block will NOT be caught by `except Exception:`.

**Always call `flow.set_status("failed")` before `sys.exit()`:**

```python
try:
    result = flow.run(...)
    if result.exit_reason != "completed":
        flow.set_status("failed")  # Must be called BEFORE sys.exit()
        sys.exit(f"Build failed: {result.exit_reason}")

    flow.set_status("completed")
except Exception:
    flow.set_status("failed")
    raise
```

### 2. Use auto_retry for Resilience

```python
# Retry up to 3 times if agent gets stuck or times out
result = flow.run(..., auto_retry=3)
```

### 3. Design for Resume

- Don't use `reset=True` unless you want to start fresh
- Use `has_completed_agent()` to skip completed work
- Use `mark_step_completed()` after successful steps
- Always use the **total** step count for `set_total_steps()`, not the pending count (see above)

### 4. Use Valid Step Numbers for Post-Step Agents

When running agents after all implementation steps (e.g., documentation updaters), use the **last step number** rather than `total_steps + 1`. Out-of-bounds step numbers cause incorrect progress display:

```python
# CORRECT: Use the last step number
last_step = all_steps[-1][0]  # e.g., 8 for an 8-step plan
doc_result = flow.run(
    "documentation-updater",
    agent=AgentInfo(step=last_step, role="docs"),
    input="Update documentation",
)
```

```python
# INCORRECT: Out-of-bounds step number
doc_result = flow.run(
    "documentation-updater",
    agent=AgentInfo(step=total_steps + 1, role="docs"),  # Step 9 of 8!
    input="Update documentation",
)
```

### 5. Chain Agents for Context

```python
# The reviewer sees the builder's report
result = flow.run("reviewer", ..., after=builder_result.final_agent)
```

### 6. Monitor with Dashboard

While your script runs, open another terminal:

```bash
# Open the dashboard overview (showing all flows)
flow dashboard

# Open the dashboard for the specific flow
flow dashboard --flow your-flow-name
```

This shows real-time progress of all agents.

### 7. Use Flow Logging for Debugging

Each flow has a dedicated log file at `flows/<flow-name>/flow.log` that captures all flow operations.

**Use `flow.get_logger()` to get a logger (recommended):**

```python
import traceback

flow = Flow("my-feature", flow_type="implement")
logger = flow.get_logger()  # Returns logger for "orchestration" component

# Log important events
logger.info("Starting build phase")
logger.debug("Agent config loaded")
logger.warning("Retrying after timeout")
logger.error(f"Operation failed: {e}")
logger.error(traceback.format_exc())  # Include full traceback

# Use custom component name for filtering
build_logger = flow.get_logger("build-phase")
build_logger.info("Building module X")
```

**Always log errors before re-raising:**

```python
try:
    result = flow.run(...)
except Exception as e:
    logger = flow.get_logger()
    logger.error(f"Flow crashed: {e}")
    logger.error(traceback.format_exc())
    flow.set_status("failed")
    raise
```

This ensures errors are captured in `flow.log` even if the script terminates unexpectedly.

**Alternative: Direct import (for cases where Flow instance is not available):**

```python
from flow.lib.logging_setup import get_flow_logger
logger = get_flow_logger(flow_name, "orchestration")
```

### 8. Multi-Step Plans: Defer Test Validation

For multi-step plans that make breaking API changes, tests may intentionally fail until all related changes are complete across multiple steps. Use a two-phase validation strategy:

**Intermediate Steps:** Run only linting and type checking (skip tests)

```python
def run_validation(is_final_step: bool = False) -> tuple[bool, str]:
    """Run validation - lint+types for intermediate steps, full tests for final."""
    cmd = ["uv", "run", "scripts/validate.py", "--auto-format"]
    if not is_final_step:
        cmd.append("--skip-tests")  # Only lint + type check
    # ... run and return result
```

**Final Step:** Run full test suite and fix any remaining failures

````python
# After all implementation steps complete:
for attempt in range(MAX_FIX_ATTEMPTS):
    passed, output = run_validation(is_final_step=True)  # Full tests
    if passed:
        break

    # Explicit instruction: fix ALL test failures, don't defer
    fix_result = flow.run(
        "builder",
        agent=AgentInfo(step=last_step, role="test-fixer"),
        input=(
            "Fix ALL remaining test failures.\n\n"
            "All implementation steps are complete. Your ONLY task is to ensure "
            "ALL tests pass. Do not defer any failures - fix them now.\n\n"
            f"Validation output:\n```\n{output}\n```"
        ),
    )
````

**Why this pattern?**

- Agents working on step N shouldn't fix tests for code that step N+1 will change
- Linting and type errors should always be fixed immediately (broken syntax/types)
- Test failures are deferred until all related changes are complete
- A dedicated "test-fixer" phase ensures all tests pass before documentation/completion

### 9. Integrating Learnings Analysis

After a flow completes, run the learnings-analyst to capture insights and propose improvements:

```python
from flow import AgentInfo, Flow
from flow.lib.config import find_project_root
from flow.lib.flow_status import read_status
from flow.lib.learnings import get_improvable_files

def run_learnings_analyst(completed_flow_name: str) -> None:
    """Run learnings analysis after a flow completes."""
    root = find_project_root()

    # Get flow metadata
    completed_status = read_status(completed_flow_name)
    if completed_status is None:
        print(f"Flow '{completed_flow_name}' not found")
        return
    flow_type = completed_status.metadata.flow_type or "unknown"

    # Get files the analyst can propose changes to
    improvable = get_improvable_files(root)
    improvable_list = "\n".join(f"- {f}" for f in improvable)

    # Create and run the learnings analyst flow
    analyst_flow = Flow(
        f"learnings-{completed_flow_name}",
        config_path=".flow/learnings/analyst.yaml",
        reset=True,
    )

    spawn_result = analyst_flow.spawn(
        agent_type="learnings-analyst",
        agent=AgentInfo(step=1, role="analyst"),
        input=f"""Analyze the completed flow: {completed_flow_name}

## Flow Type
{flow_type}

## Files You May Propose Changes To
{improvable_list}

## Instructions
1. Run `flow artifacts --flow {completed_flow_name}` to get execution data
2. Analyze implementation log, step reports, and events
3. For each observation, create a learning entry
4. Propose improvements and create a PR
""",
    )

    results = analyst_flow.await_all([spawn_result])
    if results[0].exit_reason == "completed":
        print(f"Learnings analysis complete for {completed_flow_name}")
```

**Integration in orchestration scripts:**

```python
# .flow/scripts/flows/implement.py
from flow import Flow

flow = Flow("impl-feature", flow_type="implement", reset=True)

# ... run implementation steps ...

flow.set_status("completed")

# Optionally run learnings analysis
if os.environ.get("RUN_LEARNINGS_ANALYSIS"):
    run_learnings_analyst(flow.name)
```

For the full learnings system including scaffolding and configuration, see `LEARNINGS_SYSTEM.md`.

## Spec Plan Parsing

The `flow.lib.spec_parser` module provides utilities for parsing spec plan files and extracting steps.

### Helper Functions

These convenience functions handle the common patterns of reading and parsing spec plans:

#### get_all_steps()

Get all steps from a plan file, including completed ones.

```python
from flow.lib.spec_parser import get_all_steps

all_steps = get_all_steps(plan_path, flow.config)
# Returns: [(1, "Step title"), (2, "Another step"), ...]
```

**Parameters:**

- `plan_path` - Path to the spec plan file
- `config` - FlowConfig (use `flow.config`)

**Returns:** List of `(step_number, step_title)` tuples for all steps.

#### get_pending_steps()

Get only pending (non-completed) steps from a plan file.

```python
from flow.lib.spec_parser import get_pending_steps

pending_steps = get_pending_steps(plan_path, flow.config)
# Returns only steps not marked with ✓
```

**Parameters:**

- `plan_path` - Path to the spec plan file
- `config` - FlowConfig (use `flow.config`)

**Returns:** List of `(step_number, step_title)` tuples for pending steps only.

#### mark_step_completed()

Mark a step as completed in the plan file (changes status indicator to ✅).

```python
from flow.lib.spec_parser import mark_step_completed

mark_step_completed(plan_path, step_number)
```

**Parameters:**

- `plan_path` - Path to the spec plan file
- `step_number` - The step number to mark as completed

**Returns:** `True` if the file was modified, `False` if no changes were made.

### Common Pattern

```python
from pathlib import Path
from flow.lib.spec_parser import get_all_steps, get_pending_steps, mark_step_completed

plan_path = Path("specs/my-feature.md")

with Flow("my-feature", flow_type="implement") as flow:
    # Get all steps for total count (progress tracking)
    all_steps = get_all_steps(plan_path, flow.config)
    flow.set_total_steps(len(all_steps))

    # Get pending steps to iterate
    pending_steps = get_pending_steps(plan_path, flow.config)

    for step_number, step_title in pending_steps:
        # Run agents for this step...
        result = flow.run(
            "builder",
            agent=AgentInfo(step=step_number, role="builder"),
            input=f"Implement step {step_number}: {step_title}",
        )

        # Mark step completed in the plan file
        mark_step_completed(plan_path, step_number)
        flow.mark_step_completed(step_number)
```

### Low-Level API

For more control, use `parse_spec_plan` directly:

```python
from flow.lib.spec_parser import parse_spec_plan

content = Path(plan_path).read_text()
parsed = parse_spec_plan(content, flow.config.spec_plan)

# Access parsed data
print(parsed.overview)  # Content before implementation steps
for step in parsed.steps:
    print(f"Step {step.number}: {step.title} (status: {step.status})")
    print(step.content)  # Full step content
```

## Orchestration Utilities

The `scripts.flows.lib` module provides reusable utilities for building orchestration scripts. These utilities encapsulate common patterns like review loops, validation, and git operations.

### Module Reference

| Module                   | Purpose                                                                        |
| ------------------------ | ------------------------------------------------------------------------------ |
| `review_loop.py`         | Build-review-fix loop pattern with configurable reviewers and approval markers |
| `validation_utils.py`    | Validation running (lint, type check, tests) and output processing             |
| `git_utils.py`           | Git commit and branch operations for orchestration scripts                     |
| `orchestration_utils.py` | `InfoTracker` for cumulative context and flow utilities                        |
| `learnings_runner.py`    | Post-flow learnings analysis orchestration                                     |
| `guide_utils.py`         | Review guide generation for structured reviews                                 |
| `interactive_utils.py`   | Clarification questions and feedback parsing for interactive flows             |
| `proof_runner.py`        | Proof generation orchestration                                                 |

### Usage

Import utilities from `scripts.flows.lib`:

```python
from scripts.flows.lib import (
    InfoTracker,
    ReviewLoopConfig,
    run_review_loop,
    run_validation,
    commit_and_push_changes,
)
```

**For complete examples, see `.flow/scripts/flows/implement.py` and `.flow/scripts/flows/plan.py`.**
