# Flow CLI - Command Line Documentation

Comprehensive documentation for Flow CLI commands, enabling multi-agent orchestration from shell scripts and command line.

## Setup Options

### Option 1: Pre-built Binaries (Simplest)

Download platform-specific binaries from GitHub Releases:

- `flow-linux-x86_64` - Linux x64
- `flow-macos-arm64` - macOS Apple Silicon
- `flow-macos-x86_64` - macOS Intel
- `flow-windows-x86_64.exe` - Windows x64

```bash
# macOS/Linux: Download and make executable
curl -L -o flow https://github.com/ebbe-brandstrup/flow-cli/releases/latest/download/flow-macos-arm64
chmod +x flow
./flow --version

# Move to PATH for global access
sudo mv flow /usr/local/bin/
```

### Option 2: From Source

Clone the repository and run with uv:

```bash
git clone https://github.com/ebbe-brandstrup/flow-cli.git
cd flow-cli
uv sync
uv run flow --help
```

### Option 3: Build Your Own Binary

Build a standalone executable using PyInstaller:

```bash
git clone https://github.com/ebbe-brandstrup/flow-cli.git
cd flow-cli
uv sync --all-extras
uv run python build_flow.py
# Binary created at: dist/flow
```

## Configuration

Flow CLI requires a config file in your project's `.flow/` directory. Configuration is organized by flow type (e.g., `.flow/implement/base.yaml`). See the Python API documentation for full configuration options.

### Minimal Configuration

```yaml
# .flow/implement/base.yaml
agent_interface: claude_code_cli # or opencode

flow_dir: flows

defaults:
  stuck_timeout_secs: 1200 # 20 minutes
  max_wait_secs: 3600 # 60 minutes

agents:
  builder:
    model: anthropic/opus # provider/model format
    system_prompt: .claude/agents/builder.md

  # Agent with context window override (optional)
  compact-builder:
    model: anthropic/opus
    system_prompt: .claude/agents/builder.md
    context_window: 150000 # Limit context to 150k tokens

# Global context window overrides (optional)
# model_context_windows:
#   anthropic/opus: 180000  # Override for all opus agents

# Completion workflow configuration
# IMPORTANT: The template is REQUIRED for agents to understand the completion protocol.
# Without a template, agents won't know how to signal completion.
completion_workflow:
  template: "@.flow/templates/completion-workflow-simple.md"
  report:
    filename: report.md
    success_suffix: .complete.md
    failure_suffix: .failed.md
```

### Completion Workflow Requirement

**CRITICAL**: The `completion_workflow.template` field is **required** for agents to understand the completion signaling protocol.

The `report` config values define filenames, but these are **only exposed to agents through template substitution**. Without a template, agents don't know what files to rename for completion.

**Minimal template** (`.flow/templates/completion-workflow-simple.md`):

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

The placeholders `{report_file}`, `{success_file}`, and `{failed_file}` are automatically replaced with actual filenames before agents see them.

For production flows with structured reporting, see `.flow/templates/completion-workflow-template.md` in the Flow CLI repository. For specialized workflows:

- **Implementation flows** (code changes): Use `implementation-completion-workflow-template.md` which includes git statistics
- **Planning flows** (spec creation): Use `planning-completion-workflow-template.md` with streamlined reporting

## Global Options

All commands support these global options:

| Option           | Description                                                     |
| ---------------- | --------------------------------------------------------------- |
| `--config`, `-c` | Path to config file (highest priority, overrides `--flow-type`) |
| `--version`      | Show version and exit                                           |
| `--help`         | Show help message and exit                                      |
| `--initiated-by` | Caller identifier for breadcrumb tracking                       |

## Config Resolution

Many commands support specifying configuration via `--flow-type` and `--variant` options as a convenient alternative to the global `--config` option:

| Option              | Description                                                                                                         |
| ------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `--flow-type`, `-t` | Flow type (e.g., implement, dependency-update). Resolves to `.flow/{flow_type}/base.yaml`                           |
| `--variant`, `-v`   | Config variant (e.g., new-feature, bug-fix). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml` |

**Priority:** Global `--config` > `--flow-type` + `--variant` > command defaults

**Examples:**

```bash
# Using global --config
flow --config .flow/implement/base.yaml config --table

# Using --flow-type (resolves to .flow/implement/base.yaml)
flow config --flow-type implement --table

# Using --flow-type with --variant (resolves to .flow/implement/new-feature.yaml)
flow spawn builder -f my-flow -t implement -v new-feature -a builder-1 -i "Task"
```

## Setup Commands

### `flow scaffold`

Create a complete `.flow/` configuration folder for a new project with an interactive wizard or CLI options.

**Network Requirement:** This command fetches all template files from GitHub at runtime, ensuring you always get the latest versions. An active internet connection to `raw.githubusercontent.com` is required during scaffolding.

**Private Repository Authentication:** If you're scaffolding from a private repository, authentication is handled automatically using (in order of priority):

1. `GITHUB_TOKEN` environment variable
2. `GH_TOKEN` environment variable
3. GitHub CLI token (from `gh auth login`)

For most users with GitHub CLI installed, running `gh auth login` once is sufficient.

```bash
flow scaffold [OPTIONS]
```

**Options:**

| Option                               | Description                                                |
| ------------------------------------ | ---------------------------------------------------------- |
| `--force`                            | Delete existing .flow folder before scaffolding            |
| `--dry-run`                          | Show what would be created without creating files          |
| `--flow-types`                       | Flow types to scaffold: implement, plan, or both           |
| `--agent-interface`                  | Agent CLI interface: claude_code_cli or opencode           |
| `--default-branch`                   | Default git branch name (default: main)                    |
| `--include-learnings/--no-learnings` | Include learnings system scaffolding (default: yes)        |
| `--include-proof/--no-proof`         | Include proof generation system scaffolding (default: yes) |
| `--include-scripts/--no-scripts`     | Include example orchestration scripts (default: yes)       |
| `--include-skill/--no-skill`         | Include FlowCLI skill for Claude Code (default: yes)       |
| `--update-skill`                     | Only update skill content (skip .flow/ scaffolding)        |
| `--non-interactive`                  | Skip all prompts and use defaults or provided options      |

**Created Files:**

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

With `--include-scripts`:

```
.flow/
└── scripts/
    └── flows/
        ├── implement.py                         # Implementation only
        ├── run_learnings.py                     # Standalone learnings analysis
        ├── run_proof.py                         # Standalone proof generation
        ├── implement_learnings_proof.py         # Full workflow orchestrator
        ├── plan.py                              # Planning orchestration
        └── lib/
            ├── __init__.py                      # Module exports
            ├── git_utils.py                     # Git utilities
            ├── guide_utils.py                   # Review guide generation
            ├── interactive_utils.py             # Interactive prompts and user input
            ├── learnings_runner.py              # Learnings analysis
            ├── orchestration_utils.py           # Shared orchestration helpers
            ├── proof_runner.py                  # Proof generation runner
            ├── review_loop.py                   # Build/review loop pattern
            └── validation_utils.py              # Validation utilities
```

With `--include-skill`:

```
.claude/
└── skills/
    └── flow-cli/
        ├── SKILL.md                             # FlowCLI skill definition
        ├── docs/                                # Documentation files
        └── examples/flows/                      # Example orchestration scripts
```

**Examples:**

```bash
# Interactive wizard (recommended for new users)
flow scaffold

# Non-interactive with defaults
flow scaffold --non-interactive

# Full setup with specific options
flow scaffold --non-interactive --flow-types both \
  --agent-interface claude_code_cli --include-learnings --include-scripts

# Preview what would be created
flow scaffold --dry-run

# Force overwrite existing .flow folder
flow scaffold --force

# Minimal setup without optional systems
flow scaffold --non-interactive --flow-types implement \
  --no-learnings --no-proof --no-scripts

# Update skill content only (no .flow/ changes)
flow scaffold --update-skill
```

**Output (dry-run):**

```
Files that would be created
┌─────────────────────────────┬────────┐
│ Path                        │ Status │
├─────────────────────────────┼────────┤
│ .flow/base.yaml             │ new    │
│ .flow/implement/base.yaml   │ new    │
│ .flow/templates/...         │ new    │
└─────────────────────────────┴────────┘

Optional systems:
  Would scaffold: learnings system
  Would scaffold: proof system
  Would scaffold: orchestration scripts
```

**Exit Codes:**

| Code | Meaning                                                |
| ---- | ------------------------------------------------------ |
| 0    | Success                                                |
| 1    | Error (.flow exists without --force, or network error) |
| 2    | Invalid arguments (e.g., bad --agent-interface value)  |

**Network Error Messages:**

If you encounter network errors during scaffolding:

- **"Network error fetching file from GitHub"** - Check your internet connection
- **"Request timed out"** - The request took longer than 30 seconds; retry or check connectivity
- **"File not found on GitHub"** - Usually indicates an issue with the Flow CLI release; open an issue
- **"Failed to fetch file (HTTP 401/403/404)"** with authentication hint - For private repositories, run `gh auth login` or set `GITHUB_TOKEN` environment variable

For corporate environments, ensure `github.com` and `raw.githubusercontent.com` are accessible through your proxy/firewall.

---

## Core Commands

### `flow init`

Initialize a new flow directory structure.

```bash
flow init --flow <name> [OPTIONS]
```

**Options:**

| Option              | Description                                                                                                         |
| ------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `--flow`, `-f`      | **Required.** Name of the flow to initialize                                                                        |
| `--plan-path`       | Path to the plan file for this flow                                                                                 |
| `--specs-folder`    | Specs folder path (default: "specs")                                                                                |
| `--flow-type`, `-t` | Flow type (e.g., implement, dependency-update). Resolves to `.flow/{flow_type}/base.yaml`                           |
| `--variant`, `-v`   | Config variant (e.g., new-feature, bug-fix). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml` |
| `--reset`           | Delete existing flow before initializing                                                                            |

**Config Resolution Priority:**

1. Global `--config` option (highest priority)
2. `--flow-type` + `--variant` combination (resolves to `.flow/{flow_type}/{variant}.yaml` or `.flow/{flow_type}/base.yaml`)

**Note:** There is no default fallback - you must specify either `--config` or `--flow-type`.

**Examples:**

```bash
# Initialize with flow-type (uses .flow/implement/base.yaml)
flow init --flow my-feature-2024-01-15 --flow-type implement

# Initialize with a plan file
flow init --flow impl-step-1 --plan-path specs/my-plan/plan.md

# Initialize with flow-type (uses .flow/implement/base.yaml)
flow init --flow my-impl --flow-type implement

# Initialize with flow-type and variant (uses .flow/implement/new-feature.yaml)
flow init --flow my-impl --flow-type implement --variant new-feature

# Initialize with explicit config path
flow init --flow my-impl --config .flow/implement/new-feature.yaml

# Reset existing flow and reinitialize
flow init --flow my-impl --reset
```

**Output:**

```json
{
  "success": true,
  "flow_name": "my-feature-2024-01-15",
  "flow_path": "flows/my-feature-2024-01-15",
  "status_file": "flows/my-feature-2024-01-15/status.json"
}
```

### `flow run`

Spawn an agent and wait for completion in one operation. This is the most common command for orchestration.

```bash
flow run <agent_type> --flow <name> --input <task> [OPTIONS]
```

**Arguments:**

| Argument     | Description                                                  |
| ------------ | ------------------------------------------------------------ |
| `AGENT_TYPE` | Type of agent to spawn (must be defined in your flow config) |

**Options:**

| Option                 | Description                                                                                                |
| ---------------------- | ---------------------------------------------------------------------------------------------------------- |
| `--flow`, `-f`         | **Required.** Flow name                                                                                    |
| `--agent`, `-a`        | Agent ID (auto-generated if `--step` and `--role` used)                                                    |
| `--input`, `-i`        | **Required.** Task description (or `@path/to/file.md`)                                                     |
| `--step`, `-s`         | Step number in multi-step flow                                                                             |
| `--role`, `-r`         | Agent role for auto-naming                                                                                 |
| `--step-title`         | Step title for template variables                                                                          |
| `--after`              | Comma-separated agent IDs for context chaining                                                             |
| `--mcp-config`         | Override MCP config path                                                                                   |
| `--agent-interface`    | Override agent interface (overrides config hierarchy)                                                      |
| `--auto-retry`         | Auto-retry stuck/timeout agents up to N times                                                              |
| `--stuck-timeout-secs` | Seconds of no activity before considering stuck                                                            |
| `--max-wait-secs`      | Maximum seconds to wait for completion                                                                     |
| `--flow-type`, `-t`    | Flow type (e.g., implement). Resolves to `.flow/{flow_type}/base.yaml`                                     |
| `--variant`, `-v`      | Config variant (e.g., new-feature). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml` |

**Examples:**

```bash
# Basic run - spawn and wait
flow run builder -f impl-2024 -a builder-1 -i "Implement the feature"

# Run with input from file
flow run builder -f impl-2024 -a builder-1 -i @task.md

# Auto-naming with step and role (generates: step-03-builder)
flow run builder -f impl-2024 --step 3 --role builder -i "Implement step 3"

# Run with auto-retry
flow run builder -f impl-2024 --step 1 --role builder \
  -i "Implement feature" --auto-retry 3

# Chain from previous agent's output
flow run reviewer -f impl-2024 -a reviewer-1 \
  --after builder-1 -i "Review the implementation"

# Run with custom timeouts
flow run builder -f impl-2024 -a builder-1 -i "Complex task" \
  --stuck-timeout-secs 1800 --max-wait-secs 7200

# Run with flow-type config resolution
flow run builder -f my-feature -t implement -a builder-1 -i "Implement task"

# Run with flow-type and variant
flow run builder -f my-feature -t implement -v new-feature \
  --step 1 --role builder -i "Implement new feature"
```

**Output:**

```json
{
  "success": true,
  "exit_reason": "completed",
  "final_agent": "step-01-builder",
  "attempts": [{ "agent": "step-01-builder", "exit_reason": "completed" }],
  "report_file": "flows/impl-2024/step-1/agents/step-01-builder/report.complete.md"
}
```

### `flow spawn`

Spawn an agent asynchronously (non-blocking). Use with `flow await` or `flow await-all` to wait for completion.

```bash
flow spawn <agent_type> --flow <name> --input <task> [OPTIONS]
```

**Options:**

| Option              | Description                                                                                                |
| ------------------- | ---------------------------------------------------------------------------------------------------------- |
| `--flow`, `-f`      | **Required.** Flow name                                                                                    |
| `--agent`, `-a`     | Agent ID (auto-generated if `--step` and `--role` used)                                                    |
| `--input`, `-i`     | **Required.** Task description (or `@path/to/file.md`)                                                     |
| `--step`, `-s`      | Step number in multi-step flow                                                                             |
| `--role`, `-r`      | Agent role for auto-naming                                                                                 |
| `--step-title`      | Step title for template variables                                                                          |
| `--after`           | Comma-separated agent IDs for context chaining                                                             |
| `--mcp-config`      | Override MCP config path                                                                                   |
| `--agent-interface` | Override agent interface (overrides config hierarchy)                                                      |
| `--plan`            | Path to spec plan file. If provided with --step, plan content is inlined in system prompt                  |
| `--flow-type`, `-t` | Flow type (e.g., implement). Resolves to `.flow/{flow_type}/base.yaml`                                     |
| `--variant`, `-v`   | Config variant (e.g., new-feature). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml` |

**Examples:**

```bash
# Spawn and continue (non-blocking)
flow spawn builder -f impl-2024 -a builder-1 -i "Implement feature"

# Spawn with spec plan - inlines plan content in system prompt
flow spawn builder -f impl-2024 --step 3 --role builder \
  --plan specs/my-feature.md -i "Implement step 3"

# Spawn multiple agents for parallel execution
flow spawn reviewer -f impl-2024 -a security-reviewer \
  --after builder-1 -i "Security review"
flow spawn reviewer -f impl-2024 -a api-reviewer \
  --after builder-1 -i "API review"

# Spawn with flow-type config resolution
flow spawn builder -f my-feature -t implement -a builder-1 -i "Task"

# Spawn with flow-type and variant
flow spawn builder -f my-feature -t implement -v new-feature \
  --step 1 --role builder -i "Implement feature"
```

**Output:**

```json
{
  "success": true,
  "agent_id": "builder-1",
  "pid": 12345,
  "report_file": "flows/impl-2024/step-1/agents/builder-1/report.md"
}
```

### `flow await`

Wait for an agent to complete.

```bash
flow await --flow <name> --agent <agent_id> [OPTIONS]
```

**Options:**

| Option                                         | Description                                                                                                |
| ---------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `--flow`, `-f`                                 | **Required.** Flow name                                                                                    |
| `--agent`, `-a`                                | **Required.** Agent ID to wait for                                                                         |
| `--stuck-timeout-secs`                         | Seconds of no activity before considering stuck                                                            |
| `--max-wait-secs`                              | Maximum seconds to wait for completion                                                                     |
| `--auto-retry`                                 | Auto-retry stuck/timeout agents up to N times                                                              |
| `--validate-artifacts/--no-validate-artifacts` | Validate declared artifacts after completion (default: enabled)                                            |
| `--artifact-retry-limit`                       | Max resume attempts for missing artifacts (default: 3)                                                     |
| `--flow-type`, `-t`                            | Flow type (e.g., implement). Resolves to `.flow/{flow_type}/base.yaml`                                     |
| `--variant`, `-v`                              | Config variant (e.g., new-feature). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml` |

**Exit Reasons:**

| Exit Reason    | Description                                        |
| -------------- | -------------------------------------------------- |
| `completed`    | Agent finished successfully (report.complete.md)   |
| `failed`       | Agent failed (report.failed.md)                    |
| `stuck`        | No activity detected for stuck_timeout_secs        |
| `timeout`      | max_wait_secs exceeded                             |
| `process_died` | Agent process terminated unexpectedly              |
| `interrupted`  | Agent was intentionally stopped (e.g., via Ctrl+C) |

**Examples:**

```bash
# Basic await
flow await -f impl-2024 -a step-01-builder

# With custom timeouts
flow await -f impl-2024 -a builder-1 \
  --stuck-timeout-secs 600 --max-wait-secs 1800

# With auto-retry (spawns step-01-builder-retry-1, etc.)
flow await -f impl-2024 -a step-01-builder --auto-retry 3

# With flow-type config resolution
flow await -f impl-2024 -t implement -a step-01-builder

# Disable artifact validation
flow await -f impl-2024 -a step-01-builder --no-validate-artifacts

# Custom artifact retry limit
flow await -f impl-2024 -a step-01-builder --artifact-retry-limit 5
```

**Output:**

```json
{
  "success": true,
  "exit_reason": "completed",
  "agent_id": "step-01-builder",
  "agent_status": "completed",
  "report_file": "flows/impl-2024/step-1/agents/step-01-builder/report.complete.md",
  "started_at": "2024-01-15T10:30:00",
  "completed_at": "2024-01-15T10:45:00"
}
```

### `flow await-all`

Wait for multiple agents to complete.

```bash
flow await-all --flow <name> --agents <agent_ids> [OPTIONS]
```

**Options:**

| Option                                         | Description                                                                                                |
| ---------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `--flow`, `-f`                                 | **Required.** Flow name                                                                                    |
| `--agents`, `-a`                               | **Required.** Comma-separated list of agent IDs                                                            |
| `--stuck-timeout-secs`                         | Seconds of no activity before considering stuck                                                            |
| `--max-wait-secs`                              | Maximum seconds to wait for completion                                                                     |
| `--validate-artifacts/--no-validate-artifacts` | Validate declared artifacts after completion (default: enabled)                                            |
| `--artifact-retry-limit`                       | Max resume attempts for missing artifacts (default: 3)                                                     |
| `--flow-type`, `-t`                            | Flow type (e.g., implement). Resolves to `.flow/{flow_type}/base.yaml`                                     |
| `--variant`, `-v`                              | Config variant (e.g., new-feature). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml` |

**Examples:**

```bash
# Wait for multiple agents
flow await-all -f impl-2024 --agents builder-1,tester-1,reviewer-1

# Wait for parallel reviewers
flow await-all -f impl-2024 --agents "security-reviewer,api-reviewer,perf-reviewer"

# With flow-type config resolution
flow await-all -f impl-2024 -t implement --agents builder-1,tester-1,reviewer-1

# Disable artifact validation for all agents
flow await-all -f impl-2024 --agents builder-1,tester-1 --no-validate-artifacts

# Custom artifact retry limit
flow await-all -f impl-2024 --agents builder-1,tester-1 --artifact-retry-limit 5
```

**Output:**

```json
{
  "success": true,
  "all_completed": true,
  "agents": [
    {"agent_id": "security-reviewer", "exit_reason": "completed", ...},
    {"agent_id": "api-reviewer", "exit_reason": "completed", ...}
  ],
  "summary": {
    "total": 2,
    "completed": 2,
    "failed": 0,
    "stuck": 0,
    "timeout": 0,
    "process_died": 0,
    "interrupted": 0
  }
}
```

## Status Commands

### `flow status`

Show status of a flow or specific agent.

```bash
flow status --flow <name> [OPTIONS]
```

**Options:**

| Option          | Description                  |
| --------------- | ---------------------------- |
| `--flow`, `-f`  | **Required.** Flow name      |
| `--agent`, `-a` | Specific agent ID (optional) |
| `--json`        | Output as JSON (default)     |
| `--table`       | Output as formatted table    |

**Examples:**

```bash
# Show flow status (JSON)
flow status -f impl-2024

# Show flow status as table
flow status -f impl-2024 --table

# Show specific agent status
flow status -f impl-2024 -a step-01-builder
```

### `flow stats`

Show comprehensive flow statistics.

```bash
flow stats --flow <name> [OPTIONS]
```

**Options:**

| Option              | Description                                         |
| ------------------- | --------------------------------------------------- |
| `--flow`, `-f`      | **Required.** Flow name                             |
| `--json`            | Output as JSON                                      |
| `--verbosity`, `-v` | Report verbosity: minimal, normal, detailed         |
| `--fixer-pattern`   | Pattern to identify fixer agents (default: "fixer") |

**Examples:**

```bash
# Show statistics
flow stats -f implement_auth

# Detailed JSON output
flow stats -f implement_auth --json -v detailed
```

### `flow report`

Get agent report path and optionally content.

```bash
flow report --flow <name> --agent <agent_id> [OPTIONS]
```

**Options:**

| Option          | Description                      |
| --------------- | -------------------------------- |
| `--flow`, `-f`  | **Required.** Flow name          |
| `--agent`, `-a` | **Required.** Agent identifier   |
| `--content`     | Include report content in output |

**Examples:**

```bash
# Get report path
flow report -f impl-2024 -a step-01-builder

# Get report with content
flow report -f impl-2024 -a step-01-builder --content
```

**Output:**

```json
{
  "agent": "step-01-builder",
  "status": "complete",
  "path": "flows/impl-2024/step-1/agents/step-01-builder/report.complete.md",
  "exists": true
}
```

### `flow dashboard`

Interactive TUI dashboard for flow monitoring.

```bash
flow dashboard [OPTIONS]
```

**Options:**

| Option                                         | Description                                                                                                |
| ---------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `--flow`, `-f`                                 | Specific flow to display (optional)                                                                        |
| `--refresh`, `-r`                              | Refresh interval in seconds (default: 1.0)                                                                 |
| `--flow-dir`, `-d`                             | Additional flow directory to monitor (can be specified multiple times)                                     |
| `--discover-worktrees/--no-discover-worktrees` | Enable/disable git worktree auto-discovery (default: from config, or true)                                 |
| `--limit`                                      | Maximum number of flows to display (default: 20)                                                           |
| `--flow-type`, `-t`                            | Flow type (e.g., implement). Resolves to `.flow/{flow_type}/base.yaml`                                     |
| `--variant`, `-v`                              | Config variant (e.g., new-feature). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml` |

**Key Bindings:**

| Key      | Action            |
| -------- | ----------------- |
| `q`      | Quit              |
| `r`      | Manual refresh    |
| `Enter`  | View details      |
| `Escape` | Go back           |
| `t`      | Toggle transcript |

**Examples:**

```bash
# Show all flows
flow dashboard

# Show specific flow
flow dashboard -f impl-2024

# Custom refresh interval
flow dashboard --refresh 2.0

# Monitor multiple directories
flow dashboard -d /path/to/other/flows -d /another/flows

# Disable worktree discovery
flow dashboard --no-discover-worktrees

# Limit displayed flows
flow dashboard --limit 10

# With flow-type config resolution
flow dashboard -t implement

# With flow-type and specific flow
flow dashboard -f impl-2024 -t implement
```

#### Web Dashboard (textual serve)

Serve the dashboard as a web application accessible via browser for remote access and team sharing.

**Using the launcher script (recommended):**

```bash
uv run scripts/serve_dashboard.py
```

**Options:**

| Option           | Description                                                 |
| ---------------- | ----------------------------------------------------------- |
| `--remote`, `-r` | Bind to 0.0.0.0 for remote access (default: localhost only) |
| `--port`, `-p`   | Port to bind to (default: 8000)                             |
| `--dev`, `-d`    | Enable dev mode with CSS hot reload                         |
| `--watch`, `-w`  | Auto-restart on Python file changes                         |

**Examples:**

```bash
# Basic usage (localhost:8000)
uv run scripts/serve_dashboard.py

# Remote access for team sharing
uv run scripts/serve_dashboard.py --remote --port 8080

# Development with CSS hot reload
uv run scripts/serve_dashboard.py --dev

# Development with CSS hot reload + Python auto-restart
uv run scripts/serve_dashboard.py --dev --watch

# Access in browser
open http://localhost:8000
```

**Alternative (raw textual serve):**

```bash
uv run textual serve "python -m flow.commands.dashboard"
uv run textual serve --host 0.0.0.0 --port 8080 "python -m flow.commands.dashboard"
```

## Learnings Commands

### `flow feedback`

Submit findings about a flow as a learning entry.

```bash
flow feedback --flow <name> --category <category> --title <title> [OPTIONS]
```

**Options:**

| Option                | Description                                                                       |
| --------------------- | --------------------------------------------------------------------------------- |
| `--flow`, `-f`        | **Required.** Flow name                                                           |
| `--category`, `-c`    | **Required.** Feedback category: smoke_test, code_review, bug, missed_requirement |
| `--title`, `-t`       | **Required.** Brief title for the feedback                                        |
| `--severity`, `-s`    | Severity level: low, medium, high, critical (default: medium)                     |
| `--description`, `-d` | Detailed description (supports `@file.md` for file reference)                     |
| `--step`              | Step number (optional)                                                            |
| `--file`, `-F`        | Affected files (can be specified multiple times)                                  |
| `--tag`               | Tags for clustering (can be specified multiple times)                             |
| `--source`            | Learning source: human_feedback (default), flow_analysis                          |
| `--no-enrich`         | Skip feedback enrichment agent (not yet implemented)                              |
| `--json`              | Output as JSON                                                                    |

**Examples:**

```bash
# Basic feedback after smoke testing
flow feedback -f impl-auth -c smoke_test -s high \
  -t "Login fails silently" -d "No error shown for wrong password"

# Bug report with affected files
flow feedback -f impl-auth -c bug -t "Crash on save" \
  -F src/auth/login.py -F src/auth/errors.py

# Code review finding with description from file
flow feedback -f impl-auth -c code_review \
  -t "Missing validation" -d @review-notes.md

# Automated analysis (from learnings-analyst agent)
flow feedback -f impl-auth --source flow_analysis -c bug \
  -t "Template variable not resolved" -d "Detailed analysis..."
```

**Output:**

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

### `flow learnings`

View learnings from the database with filtering options.

```bash
flow learnings [--flow-type <type>] [--all-types] [OPTIONS]
```

**Options:**

| Option              | Description                                                                              |
| ------------------- | ---------------------------------------------------------------------------------------- |
| `--flow-type`, `-t` | Flow type to query (e.g., implement)                                                     |
| `--flow`, `-f`      | Filter by specific flow name                                                             |
| `--source`, `-s`    | Filter by source: flow_analysis, human_feedback, aggregation                             |
| `--category`, `-C`  | Filter by category: pattern, anti_pattern, tooling, instruction, bug, missed_requirement |
| `--clusters`        | Show pattern clusters instead of individual entries                                      |
| `--all-types`       | Query learnings across all flow types                                                    |
| `--limit`, `-n`     | Maximum number of entries (default: 20)                                                  |
| `--json`            | Output as JSON                                                                           |

**Examples:**

```bash
# View recent learnings for a flow type
flow learnings --flow-type implement

# View human feedback only
flow learnings --flow-type implement --source human_feedback

# View anti-patterns discovered by agents
flow learnings -t implement -s flow_analysis -C anti_pattern

# Show detected pattern clusters
flow learnings --flow-type implement --clusters

# View all learnings across all flow types
flow learnings --all-types

# JSON output for scripting
flow learnings --flow-type implement --json
```

### `flow learnings scaffold`

Scaffold the learnings system into your project.

```bash
flow learnings scaffold [--dry-run] [--overwrite]
```

**Options:**

| Option        | Description                                       |
| ------------- | ------------------------------------------------- |
| `--dry-run`   | Show what would be created without creating files |
| `--overwrite` | Replace existing files                            |

**Examples:**

```bash
# Preview what would be created
flow learnings scaffold --dry-run

# Create the learnings system
flow learnings scaffold

# Overwrite existing files (to reset customizations)
flow learnings scaffold --overwrite
```

### `flow artifacts`

Collect and output flow execution artifacts for analysis.

```bash
flow artifacts --flow <name> [--format <format>] [--include-transcripts] [--validate]
```

**Options:**

| Option                  | Description                                                   |
| ----------------------- | ------------------------------------------------------------- |
| `--flow`, `-f`          | **Required.** Flow name                                       |
| `--format`              | Output format: json (default), summary                        |
| `--include-transcripts` | Include transcript file paths in output                       |
| `--validate`            | Validate all declared artifacts exist; exit code 1 if missing |

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

**Output:**

```json
{
  "success": true,
  "artifacts": {
    "flow_name": "impl-auth",
    "flow_type": "implement",
    "status": "completed",
    "timing": {
      "start": "2025-01-15T14:30:00Z",
      "end": "2025-01-15T15:45:00Z",
      "duration_seconds": 4500
    },
    "agents": [
      {
        "agent_id": "step-01-builder",
        "status": "completed",
        "step": 1,
        "retry_count": 0,
        "declared_artifacts": [
          {
            "type": "report",
            "path": "flows/impl-auth/step-1/agents/step-01-builder/report.md",
            "exists": true,
            "description": "Step completion report"
          }
        ]
      }
    ]
  }
}
```

## Agent Management Commands

### `flow complete`

Manually mark an agent as completed or failed.

```bash
flow complete --flow <name> --agent <agent_id> --status <status>
```

**Options:**

| Option           | Description                                   |
| ---------------- | --------------------------------------------- |
| `--flow`, `-f`   | **Required.** Flow name                       |
| `--agent`, `-a`  | **Required.** Agent ID to mark complete       |
| `--status`, `-s` | **Required.** Status: "completed" or "failed" |

**Examples:**

```bash
# Mark agent as completed
flow complete -f impl-2024 -a step-01-builder --status completed

# Mark agent as failed
flow complete -f impl-2024 -a tester-1 --status failed
```

### `flow heartbeat`

Send a heartbeat signal from an agent.

```bash
flow heartbeat --flow <name> --agent <agent_id> [OPTIONS]
```

**Options:**

| Option          | Description                                  |
| --------------- | -------------------------------------------- |
| `--flow`, `-f`  | **Required.** Flow name                      |
| `--agent`, `-a` | **Required.** Agent ID sending heartbeat     |
| `--pid`         | Optional PID to update (register process ID) |

**Examples:**

```bash
# Send heartbeat
flow heartbeat -f impl-2024 -a step-01-builder

# Send heartbeat with PID update
flow heartbeat -f impl-2024 -a tester-1 --pid 12345
```

### `flow agent has`

Check if an agent with given role completed for a step.

```bash
flow agent has --flow <name> --step <n> --role <role>
```

Returns exit code 0 if completed agent exists, 1 otherwise.

**Examples:**

```bash
# Check if builder completed for step 3
flow agent has -f impl-2024 -s 3 -r builder

# Use in shell script
if flow agent has -f impl-2024 -s 3 -r builder; then
  echo "Builder already completed, skipping..."
fi
```

### `flow agent get`

Get the agent ID of a completed agent with given role.

```bash
flow agent get --flow <name> --step <n> --role <role>
```

Returns the most recently completed agent ID, or exits with code 1 if none exists.

**Examples:**

```bash
# Get builder agent ID for chaining
BUILDER_ID=$(flow agent get -f impl-2024 -s 3 -r builder | jq -r '.agent_id')

# Use for chaining
flow spawn reviewer -f impl-2024 --after "$BUILDER_ID" -i "Review implementation"
```

## Progress Tracking Commands

### `flow set-total-steps`

Set the total number of planned steps for a flow.

```bash
flow set-total-steps --flow <name> --total-steps <n>
```

**Important:** When resuming flows, always pass the **total** step count from the plan, not the remaining/pending step count. If your parser filters out completed steps, count all steps separately:

```bash
# CORRECT: Count ALL steps in the plan
TOTAL_STEPS=$(grep -c "^### Step" plan.md)
flow set-total-steps -f impl-2024 --total-steps "$TOTAL_STEPS"

# Then iterate only over pending steps...
```

```bash
# INCORRECT: This breaks progress tracking on resume
PENDING_STEPS=$(grep -c "^### Step.*❌" plan.md)  # Only pending!
flow set-total-steps -f impl-2024 --total-steps "$PENDING_STEPS"  # Wrong!
```

**Examples:**

```bash
# Set total steps after parsing plan
flow set-total-steps -f impl-2024 --total-steps 10
```

### `flow monitor-step`

Monitor step completion with report and commit verification.

```bash
flow monitor-step --flow <name> --step <n> [OPTIONS]
```

**Options:**

| Option          | Description                        |
| --------------- | ---------------------------------- |
| `--flow`, `-f`  | **Required.** Flow name            |
| `--step`, `-s`  | **Required.** Step number to check |
| `--plan-name`   | Plan name for commit search        |
| `--base-branch` | Base branch for git commit search  |

**Examples:**

```bash
# Check step completion status
flow monitor-step -f impl-2024 --step 5

# With plan name
flow monitor-step -f impl-2024 --step 3 --plan-name my-feature
```

**Output:**

```json
{
  "success": true,
  "status": "complete",
  "step": 5,
  "report_files": [
    "flows/impl-2024/step-5/agents/step-05-builder/report.complete.md"
  ],
  "commit_sha": "abc123",
  "commit_message": "[my-feature] Step 5: Implement caching",
  "checks": {
    "all_agents_completed": true,
    "commit_exists": true
  }
}
```

## Administration Commands

### `flow delete`

Delete a flow and all its artifacts.

```bash
flow delete --flow <name> [OPTIONS]
```

**Options:**

| Option         | Description                                |
| -------------- | ------------------------------------------ |
| `--flow`, `-f` | **Required.** Flow name                    |
| `--force`      | Force deletion without error if not exists |

**Examples:**

```bash
# Delete a flow
flow delete -f impl-2024

# Force delete (idempotent)
flow delete -f old-flow --force
```

### `flow config`

Display and validate flow configuration.

```bash
flow config [OPTIONS]
```

**Options:**

| Option              | Description                                                                                                |
| ------------------- | ---------------------------------------------------------------------------------------------------------- |
| `--validate`        | Validate configuration and report errors                                                                   |
| `--json`            | Output as JSON (default)                                                                                   |
| `--table`           | Output as formatted table                                                                                  |
| `--flow-type`, `-t` | Flow type (e.g., implement). Resolves to `.flow/{flow_type}/base.yaml`                                     |
| `--variant`, `-v`   | Config variant (e.g., new-feature). Requires `--flow-type`. Resolves to `.flow/{flow_type}/{variant}.yaml` |

**Examples:**

```bash
# Show configuration (uses global --config or defaults)
flow config

# Validate only
flow config --validate

# Show as table
flow config --table

# Show config for a specific flow type
flow config --flow-type implement --table

# Show config for a specific flow type and variant
flow config --flow-type implement --variant new-feature

# Alternative: use global --config option
flow --config .flow/implement/base.yaml config --table
```

## JSON Output Parsing

All commands output JSON for programmatic use. Use `jq` for parsing:

```bash
# Get exit reason from run command
flow run builder -f impl -a b1 -i "Task" | jq -r '.exit_reason'

# Check if all agents completed
flow await-all -f impl --agents a1,a2 | jq '.all_completed'

# Extract agent IDs
flow status -f impl | jq -r '.flow.agents | keys[]'

# Get report file path
flow report -f impl -a b1 | jq -r '.path'
```

## Shell Script Example

Complete bash script showing multi-step workflow with error handling:

```bash
#!/bin/bash
set -euo pipefail

FLOW_NAME="implement-feature"
PLAN_FILE="specs/my-feature.md"

# Initialize flow
echo "Initializing flow..."
flow init --flow "$FLOW_NAME" --plan-path "$PLAN_FILE" --reset

# Set total steps
flow set-total-steps --flow "$FLOW_NAME" --total-steps 3

# Step 1: Build
echo "Step 1: Building..."
RESULT=$(flow run builder \
  --flow "$FLOW_NAME" \
  --step 1 --role builder \
  --input "Implement user authentication" \
  --auto-retry 2)

EXIT_REASON=$(echo "$RESULT" | jq -r '.exit_reason')
if [ "$EXIT_REASON" != "completed" ]; then
  echo "Build failed: $EXIT_REASON"
  exit 1
fi

BUILDER_ID=$(echo "$RESULT" | jq -r '.final_agent')
echo "Builder completed: $BUILDER_ID"

# Step 2: Parallel review
echo "Step 2: Running parallel reviews..."
flow spawn reviewer \
  --flow "$FLOW_NAME" \
  --step 2 --role security-reviewer \
  --after "$BUILDER_ID" \
  --input "Review for security vulnerabilities"

flow spawn reviewer \
  --flow "$FLOW_NAME" \
  --step 2 --role api-reviewer \
  --after "$BUILDER_ID" \
  --input "Review API design"

# Wait for all reviewers
REVIEW_RESULT=$(flow await-all \
  --flow "$FLOW_NAME" \
  --agents "step-02-security-reviewer,step-02-api-reviewer")

ALL_COMPLETED=$(echo "$REVIEW_RESULT" | jq '.all_completed')
if [ "$ALL_COMPLETED" != "true" ]; then
  echo "Some reviews failed"
  exit 1
fi

echo "Reviews completed!"

# Step 3: Final validation
echo "Step 3: Running tests..."
RESULT=$(flow run tester \
  --flow "$FLOW_NAME" \
  --step 3 --role tester \
  --after "step-02-security-reviewer,step-02-api-reviewer" \
  --input "Run comprehensive test suite" \
  --auto-retry 1)

EXIT_REASON=$(echo "$RESULT" | jq -r '.exit_reason')
if [ "$EXIT_REASON" != "completed" ]; then
  echo "Tests failed: $EXIT_REASON"
  exit 1
fi

# Show final statistics
echo ""
echo "=== Flow Statistics ==="
flow stats --flow "$FLOW_NAME"

echo ""
echo "Workflow completed successfully!"
```

## Resume Pattern

Script demonstrating resume capability:

```bash
#!/bin/bash
set -euo pipefail

FLOW_NAME="resumable-workflow"

# Initialize only if flow doesn't exist
if ! flow status --flow "$FLOW_NAME" > /dev/null 2>&1; then
  echo "Creating new flow..."
  flow init --flow "$FLOW_NAME"
  flow set-total-steps --flow "$FLOW_NAME" --total-steps 3
fi

for STEP in 1 2 3; do
  echo "Processing step $STEP..."

  # Check if builder already completed
  if flow agent has --flow "$FLOW_NAME" --step "$STEP" --role builder; then
    echo "  Builder already completed for step $STEP, skipping..."
    BUILDER_ID=$(flow agent get --flow "$FLOW_NAME" --step "$STEP" --role builder | jq -r '.agent_id')
  else
    # Run the builder
    RESULT=$(flow run builder \
      --flow "$FLOW_NAME" \
      --step "$STEP" --role builder \
      --input "Implement step $STEP")

    EXIT_REASON=$(echo "$RESULT" | jq -r '.exit_reason')
    if [ "$EXIT_REASON" != "completed" ]; then
      echo "Step $STEP failed: $EXIT_REASON"
      exit 1
    fi
    BUILDER_ID=$(echo "$RESULT" | jq -r '.final_agent')
  fi

  # Check if reviewer already completed
  if flow agent has --flow "$FLOW_NAME" --step "$STEP" --role reviewer; then
    echo "  Reviewer already completed for step $STEP, skipping..."
  else
    RESULT=$(flow run reviewer \
      --flow "$FLOW_NAME" \
      --step "$STEP" --role reviewer \
      --after "$BUILDER_ID" \
      --input "Review step $STEP")

    EXIT_REASON=$(echo "$RESULT" | jq -r '.exit_reason')
    if [ "$EXIT_REASON" != "completed" ]; then
      echo "Review for step $STEP failed"
      exit 1
    fi
  fi

  echo "Step $STEP completed!"
done

echo "All steps completed!"
```

## Build-Review-Fix Pattern

The build-review-fix loop ensures quality through iterative review cycles. This pattern chains a builder agent with a reviewer agent, iterating until the reviewer approves or max attempts are reached.

**Flow:**

```
Build → Review → [Fix → Re-review]* → Done
```

### Shell Script Example

```bash
#!/bin/bash
set -euo pipefail

FLOW_NAME="build-review-example"
REVIEW_APPROVAL_MARKER="REVIEW_RESULT: APPROVED"
MAX_REVIEW_ATTEMPTS=3

# Initialize flow
flow init --flow "$FLOW_NAME" --flow-type implement --reset
flow set-total-steps --flow "$FLOW_NAME" --total-steps 1

# Initial build
echo "Building..."
BUILD_RESULT=$(flow run builder \
  --flow "$FLOW_NAME" \
  --step 1 --role builder \
  --input "Implement the new feature" \
  --auto-retry 2)

EXIT_REASON=$(echo "$BUILD_RESULT" | jq -r '.exit_reason')
if [ "$EXIT_REASON" != "completed" ]; then
  echo "Build failed: $EXIT_REASON"
  exit 1
fi

LAST_AGENT=$(echo "$BUILD_RESULT" | jq -r '.final_agent')
echo "Build completed: $LAST_AGENT"

# Review loop
for ATTEMPT in $(seq 1 $MAX_REVIEW_ATTEMPTS); do
  echo "Review attempt $ATTEMPT..."

  # Run reviewer
  REVIEW_RESULT=$(flow run reviewer \
    --flow "$FLOW_NAME" \
    --step 1 --role reviewer \
    --after "$LAST_AGENT" \
    --input "Review the implementation. Include '$REVIEW_APPROVAL_MARKER' if approved.")

  EXIT_REASON=$(echo "$REVIEW_RESULT" | jq -r '.exit_reason')
  if [ "$EXIT_REASON" != "completed" ]; then
    echo "Review failed: $EXIT_REASON"
    exit 1
  fi

  # Check for approval in report
  REPORT_FILE=$(echo "$REVIEW_RESULT" | jq -r '.report_file')
  if [ -n "$REPORT_FILE" ] && [ -f "$REPORT_FILE" ]; then
    if grep -q "$REVIEW_APPROVAL_MARKER" "$REPORT_FILE"; then
      echo "Review approved!"
      break
    fi
  fi

  # Not approved - run fixer
  if [ "$ATTEMPT" -eq "$MAX_REVIEW_ATTEMPTS" ]; then
    echo "Review not approved after $MAX_REVIEW_ATTEMPTS attempts"
    exit 1
  fi

  echo "Running fixer..."
  REVIEWER_AGENT=$(echo "$REVIEW_RESULT" | jq -r '.final_agent')
  FIX_RESULT=$(flow run builder \
    --flow "$FLOW_NAME" \
    --step 1 --role review-fixer \
    --after "$REVIEWER_AGENT" \
    --input "Fix the issues identified by the reviewer." \
    --auto-retry 2)

  EXIT_REASON=$(echo "$FIX_RESULT" | jq -r '.exit_reason')
  if [ "$EXIT_REASON" != "completed" ]; then
    echo "Fix failed: $EXIT_REASON"
    exit 1
  fi

  LAST_AGENT=$(echo "$FIX_RESULT" | jq -r '.final_agent')
done

echo "Build-review cycle completed successfully!"
```

### Key Concepts

**Approval Marker Convention:**

- Use a consistent string like `REVIEW_RESULT: APPROVED`
- Include the marker instruction in reviewer input
- Grep for the marker in the report file

**Agent Chaining:**

- Reviewers chain from builders using `--after`
- Fixers chain from reviewers to get the feedback
- Track `final_agent` from each result for proper chaining

**Failure Modes:**

- **Fail flow**: Exit with error on max attempts (for critical reviews like code review)
- **Warn and continue**: Log warning but proceed (for optional reviews like documentation)

**Resume Support:**

```bash
# Check if review was already approved
check_review_approved() {
  local FLOW=$1 STEP=$2 ROLE=$3

  # Get the last reviewer agent
  RESULT=$(flow agent get --flow "$FLOW" --step "$STEP" --role "$ROLE" 2>/dev/null) || return 1
  AGENT_ID=$(echo "$RESULT" | jq -r '.agent_id')

  # Find report file
  REPORT_DIR="flows/$FLOW/step-$STEP/agents/$AGENT_ID"
  for SUFFIX in ".complete.md" ".md"; do
    REPORT="$REPORT_DIR/report$SUFFIX"
    if [ -f "$REPORT" ] && grep -q "$REVIEW_APPROVAL_MARKER" "$REPORT"; then
      return 0
    fi
  done
  return 1
}

# Usage
if check_review_approved "$FLOW_NAME" 1 reviewer; then
  echo "Review already approved, skipping..."
else
  # Run the review loop...
fi
```

## Tips and Best Practices

### 1. Always Check Exit Reasons

```bash
RESULT=$(flow run builder -f impl -a b1 -i "Task")
if [ "$(echo "$RESULT" | jq -r '.exit_reason')" != "completed" ]; then
  echo "Failed!" && exit 1
fi
```

**Note on flow status management:** The CLI commands automatically manage agent statuses, but overall flow status (running/completed/failed) should be managed via the Python API (`flow.set_status()`). For pure shell scripts, ensure your script exits cleanly on success or failure - the dashboard will show "running" for flows with no explicit completion signal. For proper status tracking, use the Python API (see `flow/docs/api_docs.md`).

### 2. Use Auto-Retry for Resilience

```bash
# Retry up to 3 times on stuck/timeout
flow run builder -f impl --step 1 --role builder \
  -i "Complex task" --auto-retry 3
```

### 3. Design for Resume

- Don't use `--reset` unless starting fresh
- Use `flow agent has` to check completion before running
- Use `flow agent get` to retrieve agent IDs for chaining
- Always use the **total** step count for `set-total-steps`, not the pending count (see above)

### 4. Use Valid Step Numbers for Post-Step Agents

When running agents after all implementation steps (e.g., documentation updaters), use the **last step number** rather than `total_steps + 1`. Out-of-bounds step numbers cause incorrect progress display:

```bash
# CORRECT: Use the last step number
LAST_STEP=8  # For an 8-step plan
flow run documentation-updater -f impl --step "$LAST_STEP" --role docs \
  -i "Update documentation"
```

```bash
# INCORRECT: Out-of-bounds step number
TOTAL_STEPS=8
flow run documentation-updater -f impl --step $((TOTAL_STEPS + 1)) --role docs \
  -i "Update documentation"  # Step 9 of 8!
```

### 5. Chain Agents for Context

```bash
# Reviewer sees builder's report
flow run reviewer -f impl --step 1 --role reviewer \
  --after step-01-builder -i "Review implementation"
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

### 7. Use File Input for Complex Tasks

For long task descriptions, use file input:

```bash
# Write task to file
cat > task.md << 'EOF'
Implement user authentication with:
- JWT tokens
- Password hashing
- Session management
EOF

# Reference with @ prefix
flow run builder -f impl -a b1 -i @task.md
```

### 8. Multi-Step Plans: Defer Test Validation

For multi-step plans that make breaking API changes, tests may intentionally fail until all related changes are complete across multiple steps. Use a two-phase validation strategy:

**Intermediate Steps:** Run only linting and type checking (skip tests)

```bash
# For intermediate steps - lint and type check only
uv run scripts/validate.py --auto-format --skip-tests
```

**Final Step:** Run full test suite and fix any remaining failures

```bash
# After all implementation steps, run full validation
uv run scripts/validate.py --auto-format

# If tests fail, spawn a test-fixer with explicit instructions
if [ $? -ne 0 ]; then
  flow run builder -f "$FLOW_NAME" --step "$LAST_STEP" --role test-fixer \
    -i "Fix ALL remaining test failures. All implementation steps are complete. \
        Your ONLY task is to ensure ALL tests pass. Do not defer any failures."
fi
```

**Why this pattern?**

- Agents working on step N shouldn't fix tests for code that step N+1 will change
- Linting and type errors should always be fixed immediately (broken syntax/types)
- Test failures are deferred until all related changes are complete
- A dedicated "test-fixer" phase ensures all tests pass before documentation/completion

See `.flow/scripts/flows/implement.py` for a complete implementation of this pattern.
