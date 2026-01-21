# Flow CLI Usage Overview

This document provides a comprehensive guide to using the Flow CLI for multi-agent orchestration.

## Table of Contents

- [Overview](#overview)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Commands Reference](#commands-reference)
- [Common Workflows](#common-workflows)
- [Architecture](#architecture)
- [Monitoring and Debugging](#monitoring-and-debugging)
- [Spec Plan Planning Flow](#spec-plan-planning-flow)
- [Learnings and Self-Improvement](#learnings-and-self-improvement)
- [Development Commands](#development-commands)
- [Proof Generation Integration](#proof-generation-integration)

## Overview

The Flow CLI is a multi-agent orchestration tool that provides primitives for:

- **Spawning agents**: Launch AI agents (Claude Code) as background processes
- **Monitoring**: Track agent progress via PID, file activity, and heartbeats
- **Coordination**: Orchestrate multiple agents working on related tasks
- **Communication**: File-based inter-agent communication for transparency

The CLI follows a "primitives, not patterns" philosophy - it provides building blocks while leaving orchestration logic to external scripts.

## Installation

### Using uv (Recommended)

```bash
# Clone the repository
git clone https://github.com/ebbe-brandstrup/flow-cli.git
cd flow-cli

# Install with uv
uv sync

# Run the CLI
uv run flow --help
```

### Development Installation

```bash
# Install in development mode with all dependencies
uv sync --all-extras

# Or use the setup script for full development capability (includes OpenCode interface)
./.flow/setup.sh

# Run directly
uv run flow --help

# Or using Python module
uv run python -m flow.main --help
```

## Quick Start

### 1. Scaffold Your Project (Recommended)

The easiest way to set up Flow CLI in a new project is with the scaffold wizard:

```bash
# Interactive wizard - guides you through configuration choices
flow scaffold

# Or non-interactive with defaults
flow scaffold --non-interactive
```

The wizard creates a complete `.flow/` configuration with:

- **Base config** (`.flow/base.yaml`) - Project-wide settings
- **Flow configs** (`.flow/implement/`, `.flow/plan/`) - Flow-type-specific settings
- **Templates** (`.flow/templates/`) - Completion workflow templates
- **Orchestration scripts** (`.flow/scripts/flows/`) - Ready-to-use orchestration scripts including the full workflow orchestrator
- **Learnings system** - For capturing insights from flow executions
- **Proof system** - For evidence-backed documentation

After scaffolding, customize the configs for your project and you're ready to run flows.

See the [Scaffold Command](#scaffold-command) section below for full documentation.

---

### Alternative: Manual Configuration

If you prefer to configure manually, create a hierarchical config structure in `.flow/` (e.g., `.flow/implement/base.yaml`):

```yaml
agent_interface: claude_code_cli # or opencode
flow_dir: flows

agents:
  builder:
    model: anthropic/sonnet # provider/model format
    system_prompt: .claude/agents/builder.md
```

### 2. Create a Flow

```bash
flow init --flow my-feature
```

### 3. Spawn an Agent

```bash
flow spawn builder \
  --flow my-feature \
  --agent builder-1 \
  --input "Implement user authentication"
```

### 4. Monitor Progress

```bash
# Wait for completion
flow await --flow my-feature --agent builder-1

# Or watch the dashboard
flow dashboard
```

### 5. Check Results

```bash
# View flow status
flow status --flow my-feature

# Read agent report
flow report --flow my-feature --agent builder-1
```

## Commands Reference

### `flow scaffold`

Create a complete `.flow/` configuration folder for a new project. This is the recommended way to set up Flow CLI in a new project.

```bash
flow scaffold [OPTIONS]
```

**Options:**

| Option                               | Description                                           | Default         |
| ------------------------------------ | ----------------------------------------------------- | --------------- |
| `--force`                            | Delete existing .flow folder before scaffolding       | -               |
| `--dry-run`                          | Show what would be created without creating files     | -               |
| `--flow-types`                       | Flow types to scaffold: implement, plan, or both      | implement       |
| `--agent-interface`                  | Agent CLI interface: claude_code_cli or opencode      | claude_code_cli |
| `--default-branch`                   | Default git branch name                               | main            |
| `--include-learnings/--no-learnings` | Include learnings system scaffolding                  | yes             |
| `--include-proof/--no-proof`         | Include proof generation system scaffolding           | yes             |
| `--include-scripts/--no-scripts`     | Include example orchestration scripts                 | yes             |
| `--include-skill/--no-skill`         | Include FlowCLI skill for Claude Code                 | yes             |
| `--update-skill`                     | Only update skill content (skip .flow/ scaffolding)   | -               |
| `--non-interactive`                  | Skip all prompts and use defaults or provided options | -               |

**Interactive Mode:**

Without `--non-interactive`, the wizard guides you through configuration choices:

1. **Flow types** - Which flow types to set up (implement, plan, or both)
2. **Agent CLI** - Which agent interface to use (Claude Code CLI or OpenCode)
3. **Default branch** - Default git branch name for the project
4. **Learnings system** - Whether to include learnings for capturing insights
5. **Proof system** - Whether to include proof generation system for demonstrations
6. **Orchestration scripts** - Whether to include example scripts
7. **FlowCLI skill** - Whether to include the FlowCLI skill for Claude Code

**Created Files:**

Core configuration files:

```
.flow/
├── base.yaml                                    # Project-wide configuration
├── implement/
│   └── base.yaml                                # Implementation flow config
├── plan/
│   └── base.yaml                                # Planning flow config
└── templates/
    ├── implementation-completion-workflow-template.md
    ├── implementation-log-template.md
    ├── planning-completion-workflow-template.md
    └── planning-log-template.md
```

With `--include-scripts` (default: yes):

```
scripts/
└── flows/
    ├── implement.py                             # Implementation only
    ├── learnings.py                             # Standalone learnings analysis
    ├── proof.py                                 # Standalone proof generation
    ├── implement_learnings_proof.py             # Full workflow orchestrator
    ├── plan.py                                  # Planning orchestration
    └── lib/
        ├── __init__.py                          # Module exports
        ├── validation_utils.py                  # Validation utilities
        ├── review_loop.py                       # Build/review loop pattern
        ├── learnings_runner.py                  # Learnings analysis
        ├── git_utils.py                         # Git utilities
        ├── guide_utils.py                       # Review guide generation
        └── orchestration_utils.py               # Shared orchestration helpers
```

With `--include-learnings` (default: yes):

```
.flow/
  learnings/
    analyst.yaml                                 # Flow config for learnings-analyst
    aggregator.yaml                              # Flow config for learnings-aggregator
    agents/
      learnings-analyst.md                       # Analyst agent prompt
      learnings-aggregator.md                    # Aggregator agent prompt
  scripts/
    learnings/
      run_analyst.py                             # Run after a flow completes
      run_aggregator.py                          # Run on-demand for pattern detection
```

With `--include-skill` (default: yes):

```
.claude/
└── skills/
    └── flow-cli/
        ├── SKILL.md                             # FlowCLI skill definition
        ├── docs/
        │   ├── api_docs.md                      # Python API documentation
        │   ├── cli_docs.md                      # CLI command documentation
        │   └── ...                              # Other documentation files
        └── examples/
            └── flows/                           # Example orchestration scripts
                ├── implement.py
                └── lib/                         # Orchestration utilities
```

The FlowCLI skill provides Claude Code with context-aware documentation for Flow CLI development. Use `--update-skill` to refresh skill content without re-scaffolding your project.

**Examples:**

```bash
# Interactive wizard (recommended for first-time setup)
flow scaffold

# Non-interactive with all defaults
flow scaffold --non-interactive

# Full setup with both flow types
flow scaffold --non-interactive --flow-types both --include-scripts

# Preview what would be created
flow scaffold --dry-run

# Force recreate existing .flow folder
flow scaffold --force

# Minimal setup for implementation only
flow scaffold --non-interactive --flow-types implement \
  --no-learnings --no-proof --no-scripts

# Use OpenCode instead of Claude Code CLI
flow scaffold --non-interactive --agent-interface opencode

# Custom git branch name
flow scaffold --non-interactive --default-branch develop

# Update skill content only (no .flow/ changes)
flow scaffold --update-skill
```

**Relationship to Individual Scaffold Commands:**

`flow scaffold` is a unified command that orchestrates multiple sub-scaffolds:

| Sub-scaffold          | Equivalent to             | Included by Default |
| --------------------- | ------------------------- | ------------------- |
| Core .flow/ configs   | (no separate command)     | Always              |
| Learnings system      | `flow learnings scaffold` | Yes                 |
| Proof system          | `proof scaffold`          | Yes                 |
| Orchestration scripts | (no separate command)     | Yes                 |

**Note on Proof System:** The `--include-proof` option scaffolds the proof generation system (6 files via `proof scaffold`). When both `--include-learnings` and `--include-proof` are enabled, additional aggregator scripts that combine learnings and proof workflows are also included.

If you've already scaffolded using `flow scaffold`, you typically don't need to run the individual scaffold commands. However, you can use them to add systems later:

```bash
# Add learnings to an existing project
flow learnings scaffold
```

---

### `flow init`

Initialize a new flow for orchestrating agents.

```bash
flow init --flow <name> [--initiated-by <caller>] [--plan-path <path>] [--specs-folder <path>] [--flow-type <type>] [--variant <variant>] [--reset]
```

**Options:**

- `--flow, -f` (required): Unique name for the flow
- `--initiated-by`: Identifier of the calling script (for audit trail)
- `--plan-path`: Path to a plan file (stored in metadata). The plan name (stem without extension) is used for shared file path variables.
- `--specs-folder`: Specs folder path for shared file path variables (default: `specs`)
- `--flow-type, -t`: Flow type (e.g., `implement`, `dependency-update`). Resolves to `.flow/{flow_type}/base.yaml` or `.flow/{flow_type}/{variant}.yaml`
- `--variant, -v`: Config variant (e.g., `new-feature`, `bug-fix`). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml`
- `--reset`: Delete existing flow (including all agents, reports, and logs) before initializing. Equivalent to `flow delete --force` + `flow init`.

**Config Resolution Priority:**

1. Global `--config` option (highest priority)
2. `--flow-type` + `--variant` combination (resolves to `.flow/{flow_type}/{variant}.yaml` or `.flow/{flow_type}/base.yaml`)

**Note:** There is no default fallback - you must specify either `--config` or `--flow-type`.

**Examples:**

```bash
# Basic initialization
flow init --flow implement-auth --initiated-by "deploy-script"

# Re-initialize existing flow (clears previous run)
flow init --flow my-impl --reset

# Initialize with plan file and reset
flow init --flow impl-feature --plan-path specs/my-plan.md --reset

# Initialize with flow-type (uses .flow/implement/base.yaml)
flow init --flow my-impl --flow-type implement

# Initialize with flow-type and variant (uses .flow/implement/new-feature.yaml)
flow init --flow my-impl --flow-type implement --variant new-feature

# Initialize with explicit config path
flow init --flow my-impl --config .flow/implement/new-feature.yaml
```

**Creates:**

```
flows/implement-auth/
  status.json           # Flow status tracking
  breadcrumbs.jsonl     # Command audit trail
  agents/               # Agent working directories
```

---

### `flow spawn`

Spawn a new agent process within a flow.

```bash
flow spawn <agent-type> \
  --flow <name> \
  [--agent <agent-id>] \
  --input <text> \
  [--after <agent-ids>] \
  [--step <number>] \
  [--role <role>] \
  [--step-title <title>] \
  [--mcp-config <path>] \
  [--agent-interface <type>] \
  [--plan <path>] \
  [--flow-type <type>] \
  [--variant <variant>] \
  [--initiated-by <caller>]
```

**Arguments:**

- `agent-type`: Agent type defined in config (e.g., `builder`)

**Options:**

- `--flow, -f` (required): Flow name
- `--agent, -a`: Unique agent identifier within the flow. Optional if `--step` and `--role` are provided (auto-generates name as `step-{NN}-{role}`).
- `--input, -i` (required): Task description as plain text, or `@filepath` to read from file
- `--after`: Chain from previous agent(s). Single agent ID or comma-separated list for merging multiple reports
- `--step, -s`: Step number for multi-step workflows
- `--role, -r`: Agent role for auto-naming. When used with `--step`, auto-generates agent name as `step-{NN}-{role}` (e.g., `step-03-builder`). If that name exists, appends counter (e.g., `step-03-builder-2`).
- `--step-title`: Step title for shared file template variables
- `--mcp-config`: Override MCP config path for this agent (takes precedence over agent type config)
- `--agent-interface`: Override agent interface for this spawn (highest priority, overrides config)
- `--plan`: Path to spec plan file. If provided with `--step`, plan content is inlined in system prompt
- `--flow-type, -t`: Flow type (e.g., `implement`). Resolves to `.flow/{flow_type}/base.yaml` or `.flow/{flow_type}/{variant}.yaml`
- `--variant, -v`: Config variant (e.g., `new-feature`). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml`
- `--initiated-by`: Caller identifier

**Examples:**

First agent with plain text input:

```bash
flow spawn builder \
  --flow my-feature \
  --agent step-01-builder \
  --step 1 \
  --input "Create database schema for users and posts tables"
```

Second agent chained from first:

```bash
flow spawn builder \
  --flow my-feature \
  --agent step-02-builder \
  --step 2 \
  --after step-01-builder \
  --input "Implement API endpoints using the schema"
```

Merge multiple agent outputs:

```bash
flow spawn builder \
  --flow my-feature \
  --agent merger \
  --after reviewer-1,reviewer-2,reviewer-3 \
  --input "Consolidate feedback from all reviewers and apply fixes"
```

With custom MCP config (for dynamic tool configurations):

```bash
flow spawn builder \
  --flow my-feature \
  --agent builder-e2e \
  --step 3 \
  --step-title "Add E2E Tests" \
  --mcp-config .mcp-playwright-e2e.json \
  --input "Write end-to-end tests for the feature"
```

Auto-naming with `--step` and `--role` (no `--agent` needed):

```bash
# Generates agent name: step-03-builder
flow spawn builder \
  --flow my-feature \
  --step 3 \
  --role builder \
  --input "Implement step 3 of the plan"

# If step-03-builder already exists, generates: step-03-builder-2
flow spawn builder \
  --flow my-feature \
  --step 3 \
  --role builder \
  --input "Another builder for step 3"
```

With spec plan file (inlines plan content in system prompt):

```bash
flow spawn builder \
  --flow my-feature \
  --step 3 \
  --role builder \
  --plan specs/my-feature.md \
  --input "Implement step 3"
```

**Output (JSON):**

```json
{
  "success": true,
  "agent_id": "step-01-builder",
  "pid": 12345,
  "report_file": "flows/my-feature/agents/step-01-builder/report.md",
  "system_prompt_file": "flows/my-feature/agents/step-01-builder/system_prompt.md"
}
```

---

### `flow await`

Wait for a single agent to complete, fail, get stuck, or timeout.

```bash
flow await \
  --flow <name> \
  --agent <agent-id> \
  [--stuck-timeout-secs <secs>] \
  [--max-wait-secs <secs>] \
  [--auto-retry <n>] \
  [--flow-type <type>] \
  [--variant <variant>] \
  [--initiated-by <caller>]
```

**Options:**

- `--flow, -f` (required): Flow name
- `--agent, -a` (required): Agent identifier
- `--stuck-timeout-secs`: Override default stuck timeout
- `--max-wait-secs`: Override default maximum wait time
- `--auto-retry`: Auto-retry stuck/timeout agents up to N times. Each retry agent is named `{original}-retry-{count}` and is automatically chained to the previous attempt via `--after`.
- `--flow-type, -t`: Flow type (e.g., `implement`). Resolves to `.flow/{flow_type}/base.yaml` or `.flow/{flow_type}/{variant}.yaml`
- `--variant, -v`: Config variant (e.g., `new-feature`). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml`
- `--initiated-by`: Caller identifier

**Examples:**

Basic await:

```bash
flow await --flow my-feature --agent step-01-builder --stuck-timeout-secs 600
```

With auto-retry (automatically respawns stuck agents):

```bash
# If step-01-builder gets stuck, spawns step-01-builder-retry-1, then retry-2, etc.
flow await --flow my-feature --agent step-01-builder --auto-retry 3
```

**Output (JSON):**

Without `--auto-retry`:

```json
{
  "success": true,
  "exit_reason": "completed",
  "agent_id": "step-01-builder",
  "agent_status": "completed",
  "report_file": "flows/my-feature/agents/step-01-builder/report.complete.md",
  "started_at": "2025-01-15T14:30:00Z",
  "completed_at": "2025-01-15T14:45:00Z"
}
```

With `--auto-retry` (includes retry history):

```json
{
  "success": true,
  "exit_reason": "completed",
  "final_agent": "step-01-builder-retry-2",
  "attempts": [
    { "agent": "step-01-builder", "exit_reason": "stuck" },
    { "agent": "step-01-builder-retry-1", "exit_reason": "stuck" },
    { "agent": "step-01-builder-retry-2", "exit_reason": "completed" }
  ],
  "report_file": "flows/my-feature/agents/step-01-builder-retry-2/report.complete.md"
}
```

**Exit Reasons:**

- `completed`: Agent finished successfully (report renamed to `.complete.md`)
- `failed`: Agent reported failure (report renamed to `.failed.md`)
- `stuck`: No activity for `stuck_timeout_secs`
- `timeout`: Maximum wait time exceeded
- `process_died`: Agent process terminated unexpectedly
- `interrupted`: Agent was intentionally stopped (e.g., via Ctrl+C)

---

### `flow run`

Spawn an agent and wait for completion in a single command. This combines `spawn` + `await` + auto-retry, which is the most common orchestration pattern.

```bash
flow run <agent-type> \
  --flow <name> \
  [--agent <agent-id>] \
  --input <text> \
  [--step <number>] \
  [--role <role>] \
  [--step-title <title>] \
  [--after <agent-ids>] \
  [--mcp-config <path>] \
  [--agent-interface <type>] \
  [--auto-retry <n>] \
  [--stuck-timeout-secs <secs>] \
  [--max-wait-secs <secs>] \
  [--flow-type <type>] \
  [--variant <variant>] \
  [--initiated-by <caller>]
```

**Arguments:**

- `agent-type`: Agent type defined in config (e.g., `builder`)

**Options:**

- `--flow, -f` (required): Flow name
- `--agent, -a`: Agent identifier. Optional if `--step` and `--role` are provided.
- `--input, -i` (required): Task description as plain text, or `@filepath` to read from file
- `--step, -s`: Step number for multi-step workflows
- `--role, -r`: Agent role for auto-naming (with `--step`, generates: `{role}-step-{step}`)
- `--step-title`: Step title for template variables
- `--after`: Chain from previous agent(s). Comma-separated list for merging multiple reports.
- `--mcp-config`: Override MCP config path for this agent
- `--agent-interface`: Override agent interface for this spawn (highest priority, overrides config)
- `--auto-retry`: Auto-retry stuck/timeout agents up to N times
- `--stuck-timeout-secs`: Override default stuck timeout
- `--max-wait-secs`: Override default maximum wait time
- `--flow-type, -t`: Flow type (e.g., `implement`). Resolves to `.flow/{flow_type}/base.yaml` or `.flow/{flow_type}/{variant}.yaml`
- `--variant, -v`: Config variant (e.g., `new-feature`). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml`
- `--initiated-by`: Caller identifier

**Examples:**

Basic run (spawn and wait):

```bash
flow run builder \
  --flow my-feature \
  --agent builder-1 \
  --input "Implement the user authentication feature"
```

Run with auto-naming and auto-retry (recommended pattern):

```bash
flow run builder \
  --flow my-impl \
  --step 1 \
  --role builder \
  --input "Implement step 1 from the plan" \
  --auto-retry 3
```

Run chained after previous agent:

```bash
flow run plan-step-tester \
  --flow my-impl \
  --step 1 \
  --role tester \
  --after step-01-builder \
  --input "Run all tests for step 1" \
  --auto-retry 2
```

**Output (JSON):**

Same format as `flow await` with `--auto-retry`:

```json
{
  "success": true,
  "exit_reason": "completed",
  "final_agent": "step-01-builder",
  "attempts": [{ "agent": "step-01-builder", "exit_reason": "completed" }],
  "report_file": "flows/my-impl/agents/step-01-builder/report.complete.md"
}
```

If retries occurred:

```json
{
  "success": true,
  "exit_reason": "completed",
  "final_agent": "step-01-builder-retry-1",
  "attempts": [
    { "agent": "step-01-builder", "exit_reason": "stuck" },
    { "agent": "step-01-builder-retry-1", "exit_reason": "completed" }
  ],
  "report_file": "flows/my-impl/agents/step-01-builder-retry-1/report.complete.md"
}
```

---

### `flow await-all`

Wait for multiple agents to complete in parallel.

```bash
flow await-all \
  --flow <name> \
  --agents <id1,id2,id3> \
  [--stuck-timeout-secs <secs>] \
  [--max-wait-secs <secs>] \
  [--flow-type <type>] \
  [--variant <variant>] \
  [--initiated-by <caller>]
```

**Options:**

- `--flow, -f` (required): Flow name
- `--agents` (required): Comma-separated list of agent IDs
- `--stuck-timeout-secs`: Override default stuck timeout
- `--max-wait-secs`: Override default maximum wait time
- `--flow-type, -t`: Flow type (e.g., `implement`). Resolves to `.flow/{flow_type}/base.yaml` or `.flow/{flow_type}/{variant}.yaml`
- `--variant, -v`: Config variant (e.g., `new-feature`). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml`
- `--initiated-by`: Caller identifier

**Example:**

```bash
flow await-all \
  --flow my-feature \
  --agents "reviewer-1,reviewer-2,reviewer-3"
```

---

### `flow heartbeat`

Send a heartbeat signal for an agent (used by agents to indicate activity).

```bash
flow heartbeat \
  --flow <name> \
  --agent <agent-id> \
  [--pid <pid>] \
  [--initiated-by <caller>]
```

**Example:**

```bash
flow heartbeat --flow my-feature --agent step-01-builder
```

---

### `flow complete`

Manually mark an agent as completed or failed.

```bash
flow complete \
  --flow <name> \
  --agent <agent-id> \
  --status <completed|failed> \
  [--initiated-by <caller>]
```

**Example:**

```bash
flow complete --flow my-feature --agent step-01-builder --status completed
```

---

### `flow status`

Display the current status of a flow and its agents.

```bash
flow status --flow <name> [--json] [--initiated-by <caller>]
```

**Options:**

- `--flow, -f` (required): Flow name
- `--json`: Output as JSON instead of formatted table
- `--initiated-by`: Caller identifier

**Example:**

```bash
flow status --flow my-feature
```

**Output:**

```
Flow: my-feature
Status: running
Started: 2025-01-15T14:30:00Z

Agents:
+-----------------+-----------+--------+---------+---------------+
| Agent           | Status    | PID    | Context | Last Activity |
+-----------------+-----------+--------+---------+---------------+
| step-01-builder | completed | -      | -       | -             |
| step-02-builder | running   | 12345  | 45%     | 30s ago       |
| step-02-tester  | pending   | -      | -       | -             |
+-----------------+-----------+--------+---------+---------------+
```

---

### `flow stats`

Show comprehensive flow statistics including timing, agent counts, retry/fixer usage, and success rates.

```bash
flow stats --flow <name> [--json] [--verbosity <level>] [--fixer-pattern <pattern>] [--initiated-by <caller>]
```

**Options:**

- `--flow, -f` (required): Flow name
- `--json`: Output as JSON (default: human-readable report)
- `--verbosity, -v`: Report verbosity level (`minimal`, `normal`, `detailed`)
- `--fixer-pattern`: Pattern to identify fixer agents (default: `fixer`)
- `--initiated-by`: Caller identifier

**Examples:**

```bash
# Show statistics (human-readable)
flow stats --flow implement_auth

# JSON output for scripting
flow stats --flow implement_auth --json

# Detailed verbosity
flow stats --flow implement_auth -v detailed
```

---

### `flow set-total-steps`

Set the total number of planned steps for a flow. This enables progress tracking in the dashboard.

```bash
flow set-total-steps --flow <name> --total-steps <n> [--initiated-by <caller>]
```

**Options:**

- `--flow, -f` (required): Flow name
- `--total-steps, -n` (required): Total number of planned steps (positive integer)
- `--initiated-by`: Caller identifier

**Example:**

```bash
# After parsing a plan file with 10 steps
flow set-total-steps --flow impl-2024 --total-steps 10
```

---

### `flow agent has`

Check if an agent with the given role has completed for a specific step. Useful for resume logic.

```bash
flow agent has --flow <name> --step <n> --role <role>
```

**Options:**

- `--flow, -f` (required): Flow name
- `--step, -s` (required): Step number
- `--role, -r` (required): Agent role (e.g., `builder`, `reviewer`)

**Exit Code:**

- `0` if a completed agent exists
- `1` if no completed agent found

**Examples:**

```bash
# Check if builder completed for step 3
flow agent has -f impl-2024 -s 3 -r builder

# Use in shell script for conditional execution
if flow agent has -f impl-2024 -s 3 -r builder; then
  echo "Builder already completed for step 3, skipping..."
fi
```

---

### `flow agent get`

Get the agent ID of the most recently completed agent with the given role. Useful for chaining.

```bash
flow agent get --flow <name> --step <n> --role <role>
```

**Options:**

- `--flow, -f` (required): Flow name
- `--step, -s` (required): Step number
- `--role, -r` (required): Agent role (e.g., `builder`, `reviewer`)

**Exit Code:**

- `0` if agent found (outputs JSON with `agent_id`)
- `1` if no completed agent found

**Examples:**

```bash
# Get the builder agent ID for step 3
flow agent get -f impl-2024 -s 3 -r builder

# Use for chaining in shell scripts
BUILDER_ID=$(flow agent get -f impl-2024 -s 3 -r builder | jq -r '.agent_id')
flow spawn reviewer -f impl-2024 --after "$BUILDER_ID" -i "Review the implementation"
```

**Output (JSON):**

```json
{
  "success": true,
  "found": true,
  "agent_id": "step-03-builder",
  "flow": "impl-2024",
  "step": 3,
  "role": "builder"
}
```

---

### `flow report`

Get agent report path and optionally content.

```bash
flow report --flow <name> --agent <agent-id> [--content] [--initiated-by <caller>]
```

**Options:**

- `--flow, -f` (required): Flow name
- `--agent, -a` (required): Agent identifier
- `--content`: Include the report file content in the JSON output
- `--initiated-by`: Caller identifier

Report status is determined by file suffix:

- `.complete.md` -> status: `"complete"`
- `.failed.md` -> status: `"failed"`
- `.md` -> status: `"in_progress"`

**Examples:**

Get report path:

```bash
flow report --flow my-feature --agent step-01-builder
```

Get report path with content:

```bash
flow report --flow my-feature --agent step-01-builder --content
```

**Output (JSON):**

Without `--content`:

```json
{
  "agent": "step-01-builder",
  "status": "complete",
  "path": "flows/my-feature/agents/step-01-builder/report.complete.md",
  "exists": true
}
```

With `--content` (includes file content):

```json
{
  "agent": "step-01-builder",
  "status": "complete",
  "path": "flows/my-feature/agents/step-01-builder/report.complete.md",
  "exists": true,
  "content": "# Implementation Report\n\n## Summary\n..."
}
```

---

### `flow monitor-step`

Check completion status of a specific step in a multi-step flow.

```bash
flow monitor-step --flow <name> --step <number> [--plan-name <name>] [--initiated-by <caller>]
```

**Output (JSON):**

```json
{
  "success": true,
  "status": "complete",
  "step": 1,
  "report_files": [
    "flows/my-feature/agents/step-01-builder/report.complete.md",
    "flows/my-feature/agents/step-01-reviewer/report.complete.md"
  ],
  "commit_sha": "abc1234",
  "commit_message": "[my-feature] Step 1: Create database schema",
  "checks": {
    "all_agents_completed": true,
    "commit_exists": true
  }
}
```

**Output fields:**

- `success`: `true` only when step is fully complete (all checks pass)
- `status`: "complete", "failed", or "pending"
- `report_files`: Array of all agent report paths for this step
- `checks.all_agents_completed`: `true` if all step-N agents have reports
- `checks.commit_exists`: `true` if a matching commit was found

---

### `flow delete`

Delete a flow and all its data.

```bash
flow delete --flow <name> [--force] [--initiated-by <caller>]
```

**Options:**

- `--flow, -f` (required): Flow name
- `--force`: Delete without confirmation (skip if flow has running agents)
- `--initiated-by`: Caller identifier

**Example:**

```bash
flow delete --flow old-feature --force
```

---

### `flow config`

Display or validate the current configuration.

```bash
flow config [--validate] [--json] [--table] [--flow-type <type>] [--variant <variant>] [--initiated-by <caller>]
```

**Options:**

- `--validate`: Check configuration for errors
- `--json`: Output as JSON (default)
- `--table`: Output as formatted table
- `--flow-type, -t`: Flow type (e.g., `implement`). Resolves to `.flow/{flow_type}/base.yaml` or `.flow/{flow_type}/{variant}.yaml`
- `--variant, -v`: Config variant (e.g., `new-feature`). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml`
- `--initiated-by`: Caller identifier

**Examples:**

```bash
# Validate configuration
flow config --validate

# Show config as table for specific flow type
flow config --flow-type implement --table

# Show config for a variant
flow config --flow-type implement --variant new-feature
```

---

### `flow dashboard`

Launch an interactive terminal dashboard for monitoring flows.

```bash
flow dashboard [--flow <name>] [--flow-dir <path>]... [--discover-worktrees|--no-discover-worktrees] [--limit <n>] [--flow-type <type>] [--variant <variant>]
```

**Options:**

- `--flow, -f`: Focus on a specific flow (optional, shows all flows by default)
- `--refresh, -r`: Refresh interval in seconds (default: 1.0)
- `--flow-dir, -d`: Additional flow directory to monitor (can be specified multiple times)
- `--discover-worktrees/--no-discover-worktrees`: Enable/disable git worktree auto-discovery (default: from config, or true)
- `--limit`: Maximum number of flows to display (default: 20)
- `--flow-type, -t`: Flow type (e.g., `implement`). Resolves to `.flow/{flow_type}/base.yaml` or `.flow/{flow_type}/{variant}.yaml`
- `--variant, -v`: Config variant (e.g., `new-feature`). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml`

**Features:**

- Real-time status updates
- Color-coded agent states
- Context window usage visualization
- Keyboard navigation (arrow keys, Enter to drill down, Q to quit)
- Multi-directory monitoring (including git worktrees)

**Examples:**

```bash
# Monitor all flows in default directory
flow dashboard

# Monitor a specific flow
flow dashboard -f my-implementation

# Monitor multiple directories
flow dashboard -d /path/to/other/flows -d /another/flows

# Disable worktree discovery
flow dashboard --no-discover-worktrees

# Limit displayed flows
flow dashboard --limit 10
```

**Web Dashboard (textual serve):**

Serve the dashboard as a web application accessible via browser:

```bash
# Using launcher script (recommended)
uv run scripts/serve_dashboard.py

# Remote access for team sharing
uv run scripts/serve_dashboard.py --remote --port 8080

# Development with CSS hot reload + Python auto-restart
uv run scripts/serve_dashboard.py --dev --watch
```

Opens at http://localhost:8000. Reads configuration from `.flow/base.yaml`.

---

### `flow feedback`

Submit human findings about a flow as a learning entry.

```bash
flow feedback --flow <name> --category <category> --title <title> [OPTIONS]
```

**Options:**

- `--flow, -f` (required): Flow name
- `--category, -c` (required): Feedback category (smoke_test | code_review | bug | missed_requirement)
- `--title, -t` (required): Brief title for the feedback
- `--severity, -s`: Severity level (low | medium | high | critical, default: medium)
- `--description, -d`: Detailed description (supports `@file.md` for file reference)
- `--step`: Step number (optional)
- `--file, -F`: Affected files (can be specified multiple times)
- `--tag`: Tags for clustering (can be specified multiple times)
- `--no-enrich`: Skip feedback enrichment agent (not yet implemented)
- `--json`: Output as JSON

**Examples:**

```bash
# Basic feedback after smoke testing
flow feedback --flow impl-auth --category smoke_test --severity high \
  --title "Login fails silently" \
  --description "No error shown for wrong password"

# Bug report with affected files
flow feedback --flow impl-auth --category bug --title "Crash on save" \
  --file src/auth/login.py --file src/auth/errors.py

# Code review finding with description from file
flow feedback --flow impl-auth --category code_review \
  --title "Missing validation" --description @review-notes.md

# With tags for pattern clustering
flow feedback --flow impl-auth --category bug --severity critical \
  --title "Auth token expired" --tag auth --tag security
```

**Output (JSON):**

```json
{
  "success": true,
  "entry": {
    "id": "a1b2c3d4-5678-90ab-cdef-ghijklmnopqr",
    "flow_type": "implement",
    "flow_name": "impl-auth",
    "category": "bug",
    "severity": "high",
    "title": "Login fails silently"
  }
}
```

---

### `flow learnings`

View learnings from the database with filtering options.

```bash
flow learnings [--flow-type <type>] [--all-types] [OPTIONS]
```

**Options:**

- `--flow-type, -t`: Flow type to query (e.g., implement)
- `--flow, -f`: Filter by specific flow name
- `--source, -s`: Filter by source (flow_analysis | human_feedback | aggregation)
- `--category, -C`: Filter by category (pattern | anti_pattern | tooling | instruction | bug | missed_requirement)
- `--clusters`: Show pattern clusters instead of individual entries
- `--all-types`: Query learnings across all flow types
- `--limit, -n`: Maximum number of entries (default: 20)
- `--json`: Output as JSON

**Examples:**

```bash
# View recent learnings for a flow type
flow learnings --flow-type implement

# View human feedback only
flow learnings --flow-type implement --source human_feedback

# View anti-patterns discovered by agents
flow learnings --flow-type implement --source flow_analysis --category anti_pattern

# View learnings for a specific flow
flow learnings --flow impl-auth

# Show detected pattern clusters
flow learnings --flow-type implement --clusters

# View all learnings across all flow types
flow learnings --all-types

# JSON output for scripting
flow learnings --flow-type implement --json
```

**Output (Table):**

```
Learnings for flow type 'implement'

┌─────────────────────┬────────────────┬──────────────────┬────────────────────────┬─────────────────┬──────────┐
│ Timestamp           │ Source         │ Category         │ Title                  │ Flow            │ Severity │
├─────────────────────┼────────────────┼──────────────────┼────────────────────────┼─────────────────┼──────────┤
│ 2025-01-15T14:45:00 │ human_feedback │ bug              │ Login fails silently   │ impl-auth       │ high     │
│ 2025-01-15T14:30:00 │ flow_analysis  │ anti_pattern     │ Duplicate validation   │ impl-auth       │ -        │
└─────────────────────┴────────────────┴──────────────────┴────────────────────────┴─────────────────┴──────────┘
```

---

### `flow learnings scaffold`

Scaffold the learnings system into your project.

```bash
flow learnings scaffold [--dry-run] [--overwrite]
```

**Options:**

- `--dry-run`: Show what would be created without creating files
- `--overwrite`: Replace existing files

**Creates:**

```
.flow/
  learnings/
    analyst.yaml              # Flow config for learnings-analyst
    aggregator.yaml           # Flow config for learnings-aggregator
    agents/
      learnings-analyst.md    # Agent prompt (customizable)
      learnings-aggregator.md # Agent prompt (customizable)
  scripts/
    learnings/
      run_analyst.py          # Run after a flow completes
      run_aggregator.py       # Run on-demand for pattern detection
```

**Examples:**

```bash
# Preview what would be created
flow learnings scaffold --dry-run

# Create the learnings system
flow learnings scaffold

# Overwrite existing files (to reset customizations)
flow learnings scaffold --overwrite
```

---

### `flow artifacts`

Collect and output flow execution artifacts for analysis.

```bash
flow artifacts --flow <name> [--format <format>] [--include-transcripts] [--validate]
```

**Options:**

- `--flow, -f` (required): Flow name
- `--format`: Output format (json | summary, default: json)
- `--include-transcripts`: Include transcript file paths in output
- `--validate`: Validate all declared artifacts exist; exit code 1 if missing

**Examples:**

```bash
# Get artifacts as JSON (default)
flow artifacts --flow impl-auth

# Human-readable summary
flow artifacts --flow impl-auth --format summary

# Include transcript locations for detailed analysis
flow artifacts --flow impl-auth --include-transcripts

# Validate all declared artifacts exist
flow artifacts --flow impl-auth --validate
```

**Output (JSON):**

```json
{
  "success": true,
  "artifacts": {
    "flow_name": "impl-auth",
    "flow_type": "implement",
    "status": "completed",
    "plan_name": "auth-spec",
    "plan_path": "specs/auth-spec.md",
    "timing": {
      "start": "2025-01-15T14:30:00Z",
      "end": "2025-01-15T15:45:00Z",
      "duration_seconds": 4500,
      "duration_formatted": "1h 15m"
    },
    "steps": {
      "1": {
        "status": "completed",
        "agents": ["step-01-builder", "step-01-reviewer"]
      },
      "2": { "status": "completed", "agents": ["step-02-builder"] }
    },
    "agents": [
      {
        "agent_id": "step-01-builder",
        "agent_type": "builder",
        "status": "completed",
        "step": 1,
        "retry_count": 0,
        "context_usage": {
          "percentage": 45,
          "tokens_used": 90000,
          "tokens_total": 200000
        }
      }
    ],
    "git_commits": [
      { "sha": "abc1234", "message": "[impl-auth] Step 1: Add authentication" }
    ]
  }
}
```

## Common Workflows

### Using the Python API (Recommended)

The Python API provides a cleaner interface for orchestration scripts.

**Use Context Manager for Automatic Cleanup:** When using the `Flow` class, wrap your orchestration code in a `with` statement. This ensures that if your script is interrupted (Ctrl+C) or crashes, all running agents are automatically terminated and their status is updated to `"interrupted"` with `exit_reason="interrupted"`.

```python
#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = ["flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli"]
# ///
from flow import AgentInfo, Flow

steps = [
    "Create database schema for users and posts",
    "Implement API endpoints for CRUD operations",
    "Add authentication middleware",
]

# Use context manager for automatic cleanup on exit/interrupt
with Flow("my-plan", flow_type="implement", reset=True) as flow:
    last_agent = None
    for step_num, step_task in enumerate(steps, 1):
        result = flow.run(
            "builder",
            agent=AgentInfo(step=step_num, role="builder"),
            input=step_task,
            after=last_agent,
            auto_retry=3,
        )
        if result.exit_reason != "completed":
            raise SystemExit(f"Step {step_num} failed: {result.exit_reason}")
        last_agent = result.final_agent
        print(f"Step {step_num} completed")
# Running agents are automatically cleaned up when exiting the with block
```

### Parallel Code Reviews (Python API)

```python
from flow import AgentInfo, Flow

with Flow("my-feature", flow_type="implement") as flow:
    # Spawn all reviewers in parallel
    reviewer_types = ["web-reviewer", "backend-reviewer", "supabase-reviewer"]
    spawn_results = [
        flow.spawn(
            reviewer_type,
            agent=AgentInfo(step=1, role=reviewer_type),
            input="Review the implementation for best practices",
            after="step-01-builder",
        )
        for reviewer_type in reviewer_types
    ]

    # Wait for all reviewers
    await_results = flow.await_all(spawn_results)

    # Merge all reviewer feedback
    merge_result = flow.run(
        "builder",
        agent=AgentInfo(step=1, role="merger"),
        input="Consolidate all reviewer feedback and apply fixes",
        after=[r.agent_status.name for r in await_results],
    )
```

### Using the CLI Directly

For shell scripts or when you need CLI output:

#### Sequential Step Implementation

```python
#!/usr/bin/env python3
import subprocess
import json

def run_flow(cmd: list[str]) -> dict:
    result = subprocess.run(cmd, capture_output=True, text=True)
    return json.loads(result.stdout)

# Initialize flow
subprocess.run(["flow", "init", "--flow", "my-plan", "--reset"])

steps = [
    "Create database schema for users and posts",
    "Implement API endpoints for CRUD operations",
]

last_agent = None
for step_num, step_task in enumerate(steps, 1):
    cmd = [
        "flow", "run", "builder",
        "--flow", "my-plan",
        "--step", str(step_num),
        "--role", "builder",
        "--input", step_task,
        "--auto-retry", "3",
    ]
    if last_agent:
        cmd.extend(["--after", last_agent])

    result = run_flow(cmd)
    if result["exit_reason"] != "completed":
        print(f"Step {step_num} failed: {result['exit_reason']}")
        break
    last_agent = result["final_agent"]
    print(f"Step {step_num} completed")
```

#### Parallel Code Reviews

```python
#!/usr/bin/env python3
import subprocess
import json

def run_flow(cmd: list[str]) -> dict:
    result = subprocess.run(cmd, capture_output=True, text=True)
    return json.loads(result.stdout)

# Spawn all reviewers in parallel
reviewers = ["web-reviewer", "backend-reviewer", "supabase-reviewer"]
for reviewer in reviewers:
    subprocess.run([
        "flow", "spawn", reviewer,
        "--flow", "my-feature",
        "--step", "1", "--role", reviewer,
        "--after", "step-01-builder",
        "--input", "Review the implementation"
    ])

# Wait for all reviewers
subprocess.run([
    "flow", "await-all",
    "--flow", "my-feature",
    "--agents", ",".join([f"{r}-step-1" for r in reviewers])
])

# Merge feedback
merge_result = run_flow([
    "flow", "run", "builder",
    "--flow", "my-feature",
    "--step", "1", "--role", "merger",
    "--after", ",".join([f"{r}-step-1" for r in reviewers]),
    "--input", "Consolidate feedback and apply fixes"
])
```

## Build/Review Cycle Best Practices

The build-review-fix loop is a powerful pattern for ensuring quality through iterative review cycles. This section explains when to use review loops, how to structure reviewer agents, and best practices for handling review failures.

### When to Use Review Loops

Review loops are most valuable when:

1. **Code Quality Matters**: For production code changes, chain a reviewer after the builder to catch issues before committing
2. **Documentation Updates**: Ensure documentation is accurate, complete, and consistent
3. **Multi-Step Plans**: Each step can have its own build-review cycle
4. **High-Stakes Changes**: Security-sensitive or architecture-impacting changes benefit from review

### Structuring Reviewer Agents

Reviewer agents need clear guidelines for what to validate and how to signal approval:

**System Prompt Structure:**

```markdown
# Review Criteria

You are a code reviewer. Validate the following dimensions:

1. Code quality and style
2. Type safety
3. Test coverage
4. Security considerations

# Approval Format

If the code meets all criteria:

- State: REVIEW_RESULT: APPROVED
- Provide brief confirmation

If changes are needed:

- State: REVIEW_RESULT: CHANGES_REQUIRED
- List specific issues with severity (CRITICAL, IMPORTANT, MINOR)
- Provide actionable fix suggestions
```

**Key Elements:**

- **Clear criteria**: Explicit list of what to check
- **Approval marker**: Consistent string for automation (e.g., `REVIEW_RESULT: APPROVED`)
- **Structured feedback**: Severity levels help fixers prioritize

### Approval Marker Conventions

Use a consistent approval marker string that's easy to detect programmatically:

```python
REVIEW_APPROVAL_MARKER = "REVIEW_RESULT: APPROVED"
```

Include the marker requirement in reviewer input:

```python
flow.run(
    "reviewer",
    agent=AgentInfo(step=1, role="reviewer"),
    input=f"Review the implementation. Include '{REVIEW_APPROVAL_MARKER}' if approved.",
    after=builder_id,
)
```

Check for approval by reading the report file:

```python
if review_result.report_file:
    content = Path(review_result.report_file).read_text(encoding="utf-8")
    if REVIEW_APPROVAL_MARKER in content:
        print("Review approved!")
```

### Handling Review Failures

Choose the appropriate failure mode based on the review type:

**Fail Flow (Critical Reviews):**

For code reviews, security reviews, or other critical validations, fail the flow if max attempts are reached:

```python
else:
    flow.set_status("failed")
    raise SystemExit(f"Review not approved after {MAX_ATTEMPTS} attempts")
```

**Warn and Continue (Optional Reviews):**

For documentation reviews or other non-critical validations, log a warning but continue:

```python
else:
    print(f"Warning: Review not approved after {MAX_ATTEMPTS} attempts, continuing...")
    # Proceed with flow
```

### Example: Code Review Loop

See `.flow/scripts/flows/implement.py` for a production implementation that includes:

- Code review with `reviewer` after each builder step
- Maximum 3 review-fix attempts
- Resume support with `check_review_approved()` helper
- Validation loop integration (lint → type check → test → review)

### Example: Documentation Review Loop

Documentation updates can also benefit from review:

```python
# Run documentation updater
doc_result = flow.run(
    "documentation-updater",
    agent=AgentInfo(step=last_step, role="docs"),
    input="Update documentation to reflect code changes",
    after=builder_id,
)

# Review documentation
for attempt in range(MAX_DOC_REVIEW_ATTEMPTS):
    review_result = flow.run(
        "documentation-reviewer",
        agent=AgentInfo(step=last_step, role="doc-reviewer"),
        input=f"Review documentation. Include '{REVIEW_APPROVAL_MARKER}' if approved.",
        after=doc_result.final_agent,
    )

    if REVIEW_APPROVAL_MARKER in Path(review_result.report_file).read_text(encoding="utf-8"):
        break

    # Fix documentation issues
    doc_result = flow.run(
        "documentation-updater",
        agent=AgentInfo(step=last_step, role="doc-fixer"),
        input="Fix the documentation issues identified by the reviewer.",
        after=review_result.final_agent,
    )
```

## Architecture

Flow CLI follows a strict 3-layer architecture that ensures consistency between CLI and Python API while keeping all logic testable in one place.

### Layered Architecture Diagram

```
┌────────────────────────────────────────────┐
│  flow/api.py + flow/commands/*.py          │  ← Thin wrappers (Python API + Click)
│  (Python API + CLI interface)              │    Max 10-20 lines per function
├────────────────────────────────────────────┤    Delegate to lib/
│  flow/lib/*.py                             │  ← Business logic & utilities
│  (Core functionality)                      │    All real work happens here
├────────────────────────────────────────────┤
│  flow/types.py                             │  ← Data models (Pydantic)
└────────────────────────────────────────────┘
```

### Layer Responsibilities

1. **`flow/lib/`** = All business logic and utilities
   - Core functionality, algorithms, data processing
   - Utilities: file I/O, path handling, process management
   - Pure Python - no Click decorators, no CLI concerns

2. **`flow/api.py`** = Thin Python API wrapper
   - Delegates all work to `flow/lib/` functions
   - Max 10-20 lines per method
   - No business logic - only parameter parsing and lib/ calls

3. **`flow/commands/*.py`** = Thin CLI wrappers
   - Click decorators + command definitions
   - Delegates all work to `flow/lib/` functions
   - Max 10-20 lines per command
   - No business logic - only argument parsing and lib/ calls

### Code Examples

**Correct Pattern - api.py delegates to lib/:**

```python
# ✅ CORRECT - api.py delegates to lib/
class Flow:
    def spawn(self, agent_type, input):
        """Thin wrapper - delegates to lib/"""
        spawner = AgentSpawner(self.name, self.config)  # from flow.lib
        return spawner.spawn(agent_type, input)         # lib does the work
```

**Incorrect Pattern - business logic in api.py:**

```python
# ❌ WRONG - Business logic in api.py
class Flow:
    def spawn(self, agent_type, input):
        # 50+ lines of spawning logic here...
        if agent_type not in self.config.agents:
            raise ValueError(...)
        # ... more business logic ...
```

**Correct Pattern - commands/ delegates to lib/:**

```python
# ✅ CORRECT - commands/ delegates to lib/
@click.command()
def status(flow: str):
    """Thin wrapper - delegates to lib/"""
    from flow.lib.flow_status import read_status, print_status_table

    result = read_status(flow)           # lib does the work
    print_status_table(result)           # lib does the formatting
```

**Incorrect Pattern - business logic in commands/:**

```python
# ❌ WRONG - Business logic in commands/
@click.command()
def status(flow: str):
    # 50+ lines of status checking logic...
    status_path = Path("flows") / flow / "status.json"
    # ... more business logic ...
```

**Correct Pattern - using existing lib/ utilities:**

```python
# ✅ CORRECT - Uses existing lib/ utility
from flow.lib.utils import load_inline_or_file

def load_template(path: str) -> str:
    return load_inline_or_file(path, base_dir=Path.cwd())
```

**Incorrect Pattern - duplicating lib/ functionality:**

```python
# ❌ WRONG - Duplicates lib/ functionality
def load_template(path: str) -> str:
    if path.startswith("@"):
        return Path(path[1:]).read_text(encoding="utf-8")
    return path
```

### Why This Matters

- **Maintainability**: Logic lives in one place (lib/), not scattered across commands + API
- **Testability**: Test lib/ functions directly without Click/API overhead
- **Consistency**: Same logic powers both CLI and Python API
- **No Duplication**: api.py and commands/ can't diverge if they both call lib/

**Before Implementing**: Always check `flow/lib/` for existing functionality. Never reimplement logic that belongs in or already exists in lib/.

---

## Monitoring and Debugging

### Breadcrumb Trail

Every command with `--initiated-by` is logged to `flows/<name>/breadcrumbs.jsonl`:

```json
{"timestamp": "2025-01-15T14:30:00Z", "command": "flow init", "initiated_by": "implement.py"}
{"timestamp": "2025-01-15T14:30:05Z", "command": "flow spawn builder", "initiated_by": "implement.py"}
{"timestamp": "2025-01-15T14:45:00Z", "command": "flow await step-01-builder", "initiated_by": "implement.py"}
```

### Status File

The `flows/<name>/status.json` file contains the complete flow state:

```json
{
  "flow_name": "my-feature",
  "status": "running",
  "started_at": "2025-01-15T14:30:00Z",
  "last_updated_at": "2025-01-15T14:45:00Z",
  "agents": {
    "step-01-builder": {
      "name": "step-01-builder",
      "status": "completed",
      "pid": null,
      "started_at": "2025-01-15T14:30:05Z",
      "completed_at": "2025-01-15T14:45:00Z"
    }
  }
}
```

### Agent Working Directories

Each agent has its own directory with output files:

```
flows/my-feature/agents/step-01-builder/
  system_prompt.md        # Full system prompt (includes task inline)
  report.md               # Progress updates (or .complete.md/.failed.md)
```

Note: Task context is included directly in the system prompt rather than in a separate input file.

## Example Script

See [.flow/scripts/flows/implement.py](.flow/scripts/flows/implement.py) for a complete orchestration example using the Python API. This is the script used for developing Flow CLI itself.

The script demonstrates best practices for:

- `Flow` class for initialization with `reset=True` for clean runs
- `flow.run()` combining spawn + await + auto-retry
- `AgentInfo` for agent naming with step and role
- Chaining with `after` parameter for report handoff
- Test loops with fix retries
- Parallel code reviews with `flow.spawn()` and `flow.await_all()`

```bash
# Run directly (requires uv in PATH)
uv run .flow/scripts/flows/implement.py specs/my-plan.md

# Or explicitly with uv
uv run .flow/scripts/flows/implement.py specs/my-plan.md
```

## Spec Plan Planning Flow

The planning flow creates high-quality, reviewed spec plans from feature ideas. It runs BEFORE implementation to produce structured plans with verifiable demonstrations.

### Purpose

- **Systematic analysis** - Analyze feature ideas for clarity, completeness, and consolidation opportunities
- **Upfront review** - Agent review loops catch issues early in the planning phase
- **Planned demonstrations** - Define upfront what "done" looks like with concrete CLI demos
- **Anti-code policy** - Plans contain pseudo-code and type signatures, not implementation code
- **Human clarification** - Collect answers to ambiguities during planning, not during coding

### CLI Usage

```bash
uv run .flow/scripts/flows/plan.py <idea> [options]
```

**Arguments:**

- `idea` (required): Feature idea or description to plan (quoted string)

**Options:**

- `--name <name>`: Feature name for output file (default: auto-generated from idea as kebab-case)
- `--info <context>`: Additional context for agents (passed to first agent that runs)
- `--reset`: Start fresh and delete existing flow state (default: resume existing state)

### Examples

```bash
# Basic usage - auto-generates feature name
uv run .flow/scripts/flows/plan.py "Add user authentication with OAuth support"
# Output: specs/add-user-authentication-with-oauth.md

# With custom output name
uv run .flow/scripts/flows/plan.py "Add caching layer" --name redis-caching
# Output: specs/redis-caching.md

# With additional context for agents
uv run .flow/scripts/flows/plan.py "Fix pagination bug" --info "Focus on cursor-based pagination"

# Reset and start fresh (discards previous progress)
uv run .flow/scripts/flows/plan.py "Add logging" --reset
```

### Workflow Overview

The planning flow executes 5 steps with agent review loops:

```
Feature Idea
    ↓
[Step 1] plan-creator analyzes, asks questions
    ↓ (human answers clarifications)
[Step 2] plan-creator drafts complete plan
    ↓
[Step 3] plan-reviewer reviews structure
    ↓ (fix loop until approved)
[Step 4] showcase-planner adds CLI demonstrations
    ↓
[Step 5] plan-reviewer reviews complete plan
    ↓ (fix + showcase regenerate loop until approved)
    ↓
specs/{feature-name}.md ready for implement.py
```

### Step Details

**Step 1: Initial Analysis**

The plan-creator agent analyzes the feature idea and may:

- Identify `CLARIFICATION_NEEDED:` questions requiring human input
- Suggest `SUGGESTIONS:` for improvement or consolidation

Human answers are collected interactively and stored in the planning log.

**Step 2: Draft Plan**

Creates the complete spec plan with:

- Executive Summary (problem/solution/properties)
- Background Research (context, existing patterns)
- Implementation Steps (actionable steps with validation criteria)
- Showcase placeholder marker for Step 4

**Step 3: Structural Review**

The plan-reviewer validates:

- All required sections are present
- Implementation steps are clear and actionable
- Anti-code policy is followed (no implementation code)
- Steps are properly ordered with dependencies

If issues are found, plan-creator fixes them in a loop (max 3 attempts).

**Step 4: Showcase Planning**

The showcase-planner agent:

- Analyzes the plan to understand what will be implemented
- Creates the `## Showcase Requirements` section with CLI demonstrations
- Replaces the placeholder marker with concrete YAML specifications

**Step 5: Final Review with Showcase Regeneration**

Final validation of the complete plan. On each fix iteration:

1. plan-reviewer identifies issues
2. plan-creator applies fixes
3. showcase-planner regenerates demonstrations to match updated plan

This ensures showcase requirements stay synchronized with plan changes.

### Output Locations

| File                                 | Description                          |
| ------------------------------------ | ------------------------------------ |
| `specs/{feature-name}.md`            | Generated spec plan                  |
| `flows/plan_{feature-name}/`         | Flow artifacts directory             |
| `flows/plan_{feature-name}/flow.log` | Detailed orchestration log           |
| `planning-log.md`                    | Human clarifications and agent notes |

### Resume Capability

The planning flow supports resume:

```bash
# First run (interrupted or stopped)
uv run .flow/scripts/flows/plan.py "Add authentication"

# Resume from where it stopped
uv run .flow/scripts/flows/plan.py "Add authentication"

# Start fresh (discard progress)
uv run .flow/scripts/flows/plan.py "Add authentication" --reset
```

Each step checks for prior completion and skips if already done.

### Integration with Implementation

After planning completes, run the implementation flow:

```bash
# Step 1: Plan the feature
uv run .flow/scripts/flows/plan.py "Add user authentication"
# Output: specs/add-user-authentication.md

# Step 2: Full workflow (implement + learnings + proof)
uv run .flow/scripts/flows/implement_learnings_proof.py specs/add-user-authentication.md

# Or implementation only
uv run .flow/scripts/flows/implement.py specs/add-user-authentication.md
```

### Agents Used

| Agent              | Model  | Role                                      |
| ------------------ | ------ | ----------------------------------------- |
| `plan-creator`     | Opus   | Drafts and fixes spec plans               |
| `plan-reviewer`    | Opus   | Reviews plans for completeness and policy |
| `showcase-planner` | Sonnet | Creates CLI demonstration specifications  |

### Anti-Code Policy

Plans must NOT contain implementation code:

- **Allowed:** Type signatures, pseudo-code (explicitly labeled), interface definitions
- **Not Allowed:** Copy-pasteable code, working implementations, complete functions

This prevents copy-paste errors and ensures builders write tested, compiled code.

---

## Learnings and Self-Improvement

The Flow CLI includes a learnings system for capturing execution insights and improving agent behavior over time. This enables both automated analysis and human feedback to drive continuous improvement.

### Quick Start

1. **Scaffold the learnings system:**

   ```bash
   flow learnings scaffold
   ```

2. **Submit feedback after testing:**

   ```bash
   flow feedback --flow impl-auth --category bug --severity high \
     --title "Login fails silently"
   ```

3. **View accumulated learnings:**

   ```bash
   flow learnings --flow-type implement
   ```

4. **Run analysis after flow completion:**

   ```bash
   ./.flow/scripts/learnings/run_analyst.py impl-auth
   ```

For the comprehensive guide including integration patterns and workflows, see [LEARNINGS_SYSTEM.md](LEARNINGS_SYSTEM.md).

### Implementation Workflow Scripts

The implementation workflow has been separated into standalone scripts for granular control:

| Script                                             | Purpose                                          |
| -------------------------------------------------- | ------------------------------------------------ |
| `.flow/scripts/flows/implement.py`                 | Implementation only (build/review/commit cycles) |
| `.flow/scripts/flows/run_learnings.py`             | Standalone learnings analysis (idempotent)       |
| `.flow/scripts/flows/run_proof.py`                 | Standalone proof generation (idempotent)         |
| `.flow/scripts/flows/implement_learnings_proof.py` | Orchestrator that runs all three                 |

#### Full Workflow (Recommended)

For most use cases, use the orchestrator which runs implementation followed by learnings and proof in parallel:

```bash
# Full workflow - runs implement, then learnings+proof in parallel
uv run .flow/scripts/flows/implement_learnings_proof.py specs/my-feature.md

# Skip specific post-implementation tasks
uv run .flow/scripts/flows/implement_learnings_proof.py specs/my-feature.md --skip-learnings
uv run .flow/scripts/flows/implement_learnings_proof.py specs/my-feature.md --skip-proof

# Reset and start fresh
uv run .flow/scripts/flows/implement_learnings_proof.py specs/my-feature.md --reset
```

All other options (e.g., `--info`, `--flow-type`, `--variant`, `--config`) are passed through to `implement.py`.

#### Implementation Only

Run just the implementation without post-processing:

```bash
uv run .flow/scripts/flows/implement.py specs/my-feature.md
```

This is useful when:

- You want to manually run learnings/proof later
- You're iterating on implementation and don't need proof yet
- The plan doesn't have a showcase section

#### Standalone Learnings Analysis

Run learnings analysis independently after implementation completes:

```bash
uv run .flow/scripts/flows/run_learnings.py --flow-dir flows/implement_my-feature --plan specs/my-feature.md
```

**Idempotent behavior**: If learnings analysis already completed for the flow, it will be skipped automatically.

#### Standalone Proof Generation

Run proof generation independently:

```bash
uv run .flow/scripts/flows/run_proof.py --flow-dir flows/implement_my-feature --plan specs/my-feature.md
```

**Idempotent behavior**: Skips if proof already completed OR if the plan has no showcase section.

#### Retry Failed Components

If learnings or proof fails during the orchestrated workflow, retry commands are shown:

```
=== Partial Success ===
Implementation completed but the following post-tasks failed:
  - proof

Retry commands:
  uv run .flow/scripts/flows/run_proof.py --flow-dir flows/implement_my-feature --plan specs/my-feature.md
```

This allows targeted retry without re-running the entire implementation.

#### Post-Completion Phases Details

**Learnings Analysis:**

- Spawns `learnings-analyst` agent in a separate flow (`learnings-{flow_name}`)
- Captures patterns, anti-patterns, and insights
- Runs with `auto_retry=3` for resilience
- Skips gracefully if learnings system not scaffolded

**Proof Generation:**

- Creates evidence-backed documentation of implementation completeness
- Only runs if the plan has a `## Showcase Requirements` section
- Produces evidence from CLI demos (captured output, timing, exit codes)
- Generates narrative proof document with fact-checked claims

**Review Guide Generation:**

- After learnings analysis, generates `flows/{flow_name}/review-guide.md`
- Combines template sections with intelligent guidance
- Helps you effectively test, review, and provide feedback

## Development Commands

Commands for developing Flow CLI itself or running advanced development workflows.

### Run Dashboard in Development Mode

For live refresh during dashboard development:

```bash
uv run textual run --dev flow.commands.dashboard:FlowDashboard
```

This enables hot-reloading of CSS and instant feedback on UI changes.

### Build Standalone Binary

Create a standalone executable for distribution:

```bash
# Install dev dependencies
uv sync --all-extras

# Build standalone executable
uv run python build_flow.py

# Executable is at dist/flow (or dist/flow.exe on Windows)
./dist/flow --version
```

The binary bundles Python, all dependencies, and the OpenCode wrapper for portable distribution.

### Run Validation Checks

Run the full validation suite (linting, type checking, tests):

```bash
# Full validation
uv run scripts/validate.py --quiet-user --auto-format

# Skip E2E tests (faster)
uv run scripts/validate.py --skip-e2e --quiet-user --auto-format
```

### Run Tests

```bash
# Run all tests
uv run pytest tests/

# Run unit tests only
uv run pytest tests/unit/

# Run integration tests only
uv run pytest tests/integration/

# Run E2E tests (requires Claude CLI installed)
uv run pytest tests/e2e/ -m e2e
```

### Linting and Formatting

```bash
# Check linting
uv run ruff check flow

# Auto-fix linting issues
uv run ruff check flow --fix

# Type checking
uv run mypy flow
```

---

## Proof Generation Integration

The proof generation system integrates with Flow CLI to produce evidence-backed documentation of implementation completeness.

### Post-Flow Proof Generation

After completing an implementation flow, generate proof using the orchestration script:

```bash
# Generate proof for a completed flow
./proof/orchestration/generate_proof.py --flow-name implement_my-feature

# The workflow has three phases:
# Phase 1: Evidence collection (Python code)
#   - Parses showcase spec, executes CLI demos
#   - Creates asciinema recordings when record: true
#   - Writes validated evidence.json
#
# Phase 2: Agent-based narrative (2 agents)
#   1. narrative-writer - Synthesizes technical report from evidence
#   2. fact-checker - Validates claims against evidence
#
# Phase 3: HTML conversion (Python code)
#   - Converts validated markdown to HTML with embedded asciinema players
#   - Produces self-contained proof.html as final output
```

### Using flow artifacts

The proof system can consume `flow artifacts` output for metadata:

```bash
# Get artifacts from completed flow
flow artifacts --flow my-impl --json > artifacts.json

# Use in proof generation
python -c "
from proof import ProofCollector, load_showcase_yaml
import json

# Load showcase spec and collect base evidence
spec = load_showcase_yaml('showcase.yaml')
collector = ProofCollector('./proof')
evidence = collector.collect(spec)

# Enrich with flow artifacts (adds timing, agents, commits)
artifacts = json.load(open('artifacts.json'))
enriched = collector.enrich_from_flow_artifacts(evidence, artifacts)
collector.generate_narrative(enriched)  # Generates proof.html
"
```

### Standalone proof CLI

The `proof` CLI works independently of flow:

```bash
# View scaffolding options
proof scaffold

# Collect evidence (executes CLI demos, captures outputs)
proof collect --showcase showcase.yaml --output ./proof

# Generate proof document
proof generate --evidence ./proof/evidence.json

# Or combine both steps
proof run --showcase showcase.yaml --output ./proof
```

### Showcase Spec Embedded in Spec Plans

Showcase requirements can be embedded in spec plan markdown files:

````markdown
## Showcase Requirements

```yaml
project_type: cli
cli_demos:
  - name: feature_demo
    description: "Demonstrates the feature"
    commands:
      - cmd: "my-cli feature --input test"
        expect_exit_code: 0
        expect_pattern: "Success"
```
````

Use `parse_showcase_section()` to extract:

```python
from proof import parse_showcase_section
spec = parse_showcase_section("specs/my-plan.md")
```

### Output Guidelines

All proof output follows anti-hype guidelines for technical audiences:

- No emojis, celebratory language, or marketing speak
- Concrete evidence with metrics where available
- Honest acknowledgment of limitations and tradeoffs

See [proof/README.md](proof/README.md) for complete documentation.

---

## Related Documentation

- [FLOW_CONFIG_FORMAT.md](FLOW_CONFIG_FORMAT.md) - Configuration file reference
- [AGENT_PROTOCOL.md](AGENT_PROTOCOL.md) - How agents communicate with the flow system
- [LEARNINGS_SYSTEM.md](LEARNINGS_SYSTEM.md) - Learnings and self-improvement guide
- [proof/README.md](proof/README.md) - Proof generation package documentation
- [README.md](README.md) - Project overview and quick start
