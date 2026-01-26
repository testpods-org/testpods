# Flow CLI

Structured multi-agent workflows.

A primitives-based orchestration toolkit focusing on traceability, management of context, and system prompts.

Provides file-based communication, spec-plan-driven execution, and complete visibility into agent operations.

**Key traits:**

- **Primitives, not framework** - Building blocks for your orchestration logic
- **File-based traceability** - Every agent operation logged and auditable
- **Spec-plan driven** - Structured workflows with validation at each step
- **Full visibility** - Control over system prompts, shared context, and agent artifacts

## Quick Start

### Step 1: Install the Flow CLI Binaries

```bash
# Clone and build (one-time setup)
git clone https://github.com/ebbe-brandstrup/flow-cli.git
cd flow-cli
chmod +x .flow/setup.sh
./.flow/setup.sh

# Build both binaries
uv run python build_flow.py
uv run python build_proof.py

# Install to your PATH
sudo cp dist/flow dist/proof /usr/local/bin/
# Or for user-local install: cp dist/flow dist/proof ~/.local/bin/

# Verify
flow --version
proof --version
```

### Step 2: Set Up Your Project

```bash
cd /path/to/your-project

# Interactive setup wizard (recommended)
flow scaffold

# Or non-interactive with defaults
flow scaffold --non-interactive
```

This creates two folders:

**`.flow/`** - Generic infrastructure that works in any project:

- `base.yaml` - Project-wide config
- `templates/*.md` - Workflow templates
- `scripts/flows/lib/*.py` - Utility modules (if `--include-scripts`)
- `learnings/` - Learnings system (if `--include-learnings`)
- `proof/` - Proof generation system (if `--include-proof`)

**`.claude/skills/flow-cli/`** - Claude Code skill with documentation and examples:

- Getting started guide for project-specific setup
- Documentation for writing orchestration scripts
- Working examples from the flow-cli repository

After scaffolding completes, a **kick-starter prompt** is printed. Paste this prompt into your coding agent (Claude Code) to begin guided setup, which creates project-specific configuration (flow configs, agent discovery, orchestration scripts).

The Claude Code skill is the **recommended entry point** for learning how to use the flow system. For Claude Code users, ask the skill for help with writing or modifying flow scripts.

**Network Requirement:** The scaffold command fetches template files from GitHub at runtime, requiring an active internet connection.

### Step 3: Complete Guided Setup

After running `flow scaffold`, paste the kick-starter prompt into your coding agent. The agent will interview you about your project to create project-specific configuration:

1. **Discover existing agents** - Find agents in your project (e.g., `.claude/agents/`)
2. **Create new agents** - Optionally create builder, reviewer, or other agent types
3. **Generate flow configs** - Create `.flow/implement/base.yaml` and similar configs
4. **Create orchestration scripts** - Generate scripts tailored to your project

### Step 4: Write Orchestration Scripts

Create UV inline scripts that use the Python API. The guided setup can create initial scripts for you. The Claude Code skill includes documentation and working examples from the flow-cli repository.

This is the recommended way to orchestrate flows:

```python
#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = ["flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli"]
# ///
"""My implementation flow."""

from flow import Flow, AgentInfo

with Flow("my-feature", flow_type="implement", reset=True) as flow:
    result = flow.run(
        "builder",
        agent=AgentInfo(step=1, role="builder"),
        input="Implement the feature",
        auto_retry=3,
    )

    if result.exit_reason == "completed":
        flow.set_status("completed")
    else:
        flow.set_status("failed")
```

Run directly: `chmod +x implement.py && ./implement.py`

See [flow/docs/api_docs.md](flow/docs/api_docs.md) for complete Python API documentation.

### Step 4: Monitor Your Flows

```bash
flow dashboard          # Live terminal UI
flow status --flow my-feature  # Quick status check
```

## Your First Flow

This guide walks you through running a complete flow end-to-end, from writing a spec plan to seeing results.

### Prerequisites

Before starting, ensure you have:

1. **Completed Quick Start** - Flow CLI installed and working (`flow --version`)
2. **Scaffolded your project** - Run `flow scaffold` with `--include-scripts` to get orchestration scripts
3. **A project to work on** - Any codebase where you want to implement changes

### Step 1: Create a Simple Spec Plan

Create a spec plan file that describes what you want to implement. Save this as `specs/hello-world.md`:

```markdown
# Hello World Feature

## Executive Summary

### Problem Statement

The project needs a simple greeting module to demonstrate the flow system.

### Solution Statement

Add a greeting module with a configurable greeting function.

### Solution Properties

1. **Simple implementation** - Single file, easy to verify
2. **Testable** - Includes unit tests
3. **Documented** - Clear docstrings

---

## Background Research

This is a demonstration feature. No existing code to integrate with.

---

<!-- SHOWCASE_PLACEHOLDER -->

---

## Implementation Steps

### Status: ❌ | Step 1: Create greeting module

#### Step 1 Purpose

Create the core greeting functionality.

#### Step 1 Description

Create `src/greeting.py` with:

- A `greet(name: str) -> str` function that returns "Hello, {name}!"
- A docstring explaining usage

---

### Status: ❌ | Step 2: Add tests

#### Step 2 Purpose

Ensure the greeting function works correctly.

#### Step 2 Description

Create `tests/test_greeting.py` with:

- Test for basic greeting
- Test for empty name handling

---

### Status: ❌ | Step 3: Verify tests pass

#### Step 3 Purpose

Confirm the implementation is complete.

#### Step 3 Description

Run the test suite and verify all tests pass.
```

### Step 2: Run the Implementation Flow

Execute the implement script with your spec plan:

```bash
# Make the script executable (first time only)
chmod +x .flow/scripts/flows/implement.py

# Run the implementation flow
./.flow/scripts/flows/implement.py specs/hello-world.md
```

**Options:**

- `--reset` - Start fresh (discard any existing progress)
- `--info "context"` - Provide additional context for the builder agent
- `--flow-type implement` - Use implement flow config (default)

### Step 3: Monitor Progress

While the flow is running, you can monitor it in several ways:

**Option A: Live Dashboard (recommended)**

Open another terminal and run:

```bash
flow dashboard
```

The dashboard shows:

- All active flows and their status
- Agent progress (pending, running, completed, failed)
- Context window usage
- Stuck agent detection

**Option B: Quick Status Check**

```bash
# Check the status of your flow
flow status --flow implement_hello-world

# The flow name is "implement_" + the spec filename (without extension)
```

### Step 4: Understand the Artifacts

Once the flow starts, a directory is created at `flows/implement_hello-world/` containing:

```
flows/implement_hello-world/
├── status.json              # Current flow status (agents, progress)
├── breadcrumbs.jsonl        # Audit trail of all events
├── flow.log                 # Detailed orchestration log
└── step-1/                  # Directory for step 1
    └── agents/
        └── step-01-builder/ # Builder agent's working directory
            ├── report.md    # Agent's progress report (or report.complete.md)
            └── system_prompt.md  # Full context given to the agent
```

**Key files to inspect:**

- `status.json` - See which agents are running, completed, or failed
- `report.md` - Read the agent's progress and decisions
- `system_prompt.md` - See exactly what context the agent received
- `flow.log` - Debug timing issues or understand orchestration flow

### Step 5: Understand the Flow Lifecycle

A typical implementation flow proceeds through these stages:

1. **Build** - The builder agent implements the step
2. **Validate** - Linting, type checking, and tests run automatically
3. **Fix** (if needed) - Builder fixes any validation failures
4. **Review** - Reviewer agent checks code quality
5. **Fix** (if needed) - Builder addresses reviewer feedback
6. **Commit** - Changes are committed with a descriptive message
7. **Next Step** - Process repeats for subsequent steps

**Status meanings:**

| Status        | Meaning                                        |
| ------------- | ---------------------------------------------- |
| `running`     | Flow is actively executing                     |
| `completed`   | All steps finished successfully                |
| `failed`      | A step failed after max retries                |
| `interrupted` | Flow was stopped (Ctrl+C) - can resume         |
| `stuck`       | Agent shows no activity for configured timeout |

**Agent completion signals:**

Agents signal completion by renaming their report file:

- `report.complete.md` - Agent finished successfully
- `report.failed.md` - Agent encountered an unrecoverable error

### Resuming an Interrupted Flow

If a flow is interrupted (Ctrl+C, system crash, etc.), simply re-run the same command:

```bash
# Resume from where it left off
./.flow/scripts/flows/implement.py specs/hello-world.md
```

The flow automatically detects completed steps and agents, resuming from the last checkpoint. Use `--reset` only if you want to start completely fresh.

## What is Flow CLI?

### Library, Not Framework

Flow CLI provides orchestration primitives while you retain full control over your workflow logic. It augments your existing tooling rather than taking over.

- **Everything customizable** - Agent prompts, shared context, MCP configurations
- **Full control over agent system prompts** - Declare shared context (writable for inter-agent memory, read-only for rules and guidelines)
- **Agent protocol automatically enforced** - Declared artifacts (reports, completion signals) handled transparently
- **Complete insight** - See exactly what context was used and what happened in every agent run

### Built for Engineers

Flow CLI is designed for engineers running long, structured multi-agent workflows with detailed spec plans and complete control.

- **Long-running, consistent workflows** - Multi-hour implementation flows that maintain quality throughout
- **Complete control over instructions** - Define exactly what each agent sees and does
- **Predefined context management** - Shared files, templates, and inter-agent communication
- **File-based audit trails** - Every agent's system prompt, report, and completion status preserved
- **Resume capability** - Interrupted flows can continue from the last completed step
- **Parallel execution support** - Run multiple agents concurrently when tasks are independent

## Key Concepts

### Flows

A flow is a named orchestration session that tracks multiple agents working together. Each flow has:

- A status file (`status.json`) tracking all agents
- A breadcrumb log (`breadcrumbs.jsonl`) for audit trails
- Agent working directories with input/output files

### Agents

Agents are spawned processes (coding agent instances) that:

- Receive all task context directly in their system prompt
- Write progress to `report.md`
- Signal completion by renaming their report to `report.complete.md` or `report.failed.md`

**Supported agent interfaces:**

- [Claude Code](https://github.com/anthropics/claude-code)
- [OpenCode](https://github.com/sst/opencode)

See [AGENT_PROTOCOL.md](AGENT_PROTOCOL.md) for the complete agent protocol specification.

### Monitoring

The CLI uses multi-tier activity detection (PID alive check, file modification time, optional heartbeats). Agents are marked "stuck" when no activity is detected for the configured timeout.

## Commands

Core commands:

| Command          | Description                            |
| ---------------- | -------------------------------------- |
| `flow scaffold`  | Set up .flow/ folder for a new project |
| `flow init`      | Initialize a new flow                  |
| `flow run`       | Spawn an agent and wait (common)       |
| `flow status`    | Show flow status                       |
| `flow dashboard` | Live terminal dashboard                |

See [USAGE_OVERVIEW.md](USAGE_OVERVIEW.md) for the complete command reference (20+ commands).

## Documentation

| Document                                       | Description                                     |
| ---------------------------------------------- | ----------------------------------------------- |
| [USAGE_OVERVIEW.md](USAGE_OVERVIEW.md)         | Complete command reference and common workflows |
| [FLOW_CONFIG_FORMAT.md](FLOW_CONFIG_FORMAT.md) | Configuration file format and options           |
| [AGENT_PROTOCOL.md](AGENT_PROTOCOL.md)         | Protocol for custom agent implementations       |
| [LEARNINGS_SYSTEM.md](LEARNINGS_SYSTEM.md)     | Learnings and self-improvement system guide     |
| [flow/docs/api_docs.md](flow/docs/api_docs.md) | Python API reference documentation              |
| [flow/docs/cli_docs.md](flow/docs/cli_docs.md) | CLI command reference documentation             |
| [proof/README.md](proof/README.md)             | Proof generation package documentation          |

## Requirements

| Requirement | Version | Notes                                   |
| ----------- | ------- | --------------------------------------- |
| Python      | 3.13+   | Required for Python API usage           |
| Astral UV   | Latest  | Package manager and Python version mgr  |
| Git         | Any     | Integrated functionality                |
| Node.js     | 22+     | Required for OpenCode wrapper interface |
| npm         | Any     | Required for building from source       |

You'll also need at least one agent CLI installed:

- **Claude Code**: `npm install -g @anthropic-ai/claude-code`
- **OpenCode**: See [github.com/sst/opencode](https://github.com/sst/opencode)

**Tip**: For optimal performance with Flow agents, consider disabling automatic compaction in Claude Code CLI. This provides larger effective context windows, reducing the need for early retries on complex tasks.

## Planning and Proof

### Spec Plan Planning

Create high-quality, reviewed spec plans before implementation:

```bash
# Create a spec plan from a feature idea
./.flow/scripts/flows/plan.py "Add user authentication with OAuth support"

# Then implement it
./.flow/scripts/flows/implement.py specs/add-user-authentication.md
```

See [USAGE_OVERVIEW.md](USAGE_OVERVIEW.md) for the complete planning workflow.

### Proof Generation

Generate evidence-backed documentation of implementation completeness:

```bash
# Collect evidence from your spec plan's showcase requirements
proof collect --showcase specs/my-feature.md

# Or collect and generate proof in one step
proof run --showcase specs/my-feature.md --output ./proof
```

See [proof/README.md](proof/README.md) for complete documentation.

## Troubleshooting

### Scaffold Network Errors

The `flow scaffold` and `proof scaffold` commands fetch template files from GitHub at runtime. If you encounter network errors:

**Common issues:**

- **"Network error fetching file from GitHub"** - Check your internet connection and try again
- **"Request timed out"** - The request took longer than 30 seconds; retry or check for slow connections
- **"File not found on GitHub"** - This usually indicates an issue with the Flow CLI release; please open an issue
- **"Failed to fetch file (HTTP 401/403/404)"** with authentication hint - For private repositories, see below

**Private repository authentication:**

If scaffolding from a private repository, authentication is handled automatically using (in order of priority):

1. `GITHUB_TOKEN` environment variable
2. `GH_TOKEN` environment variable
3. GitHub CLI token (from `gh auth login`)

For most users, running `gh auth login` once is sufficient.

**Corporate network considerations:**

- If you're behind a corporate proxy, ensure `github.com` and `raw.githubusercontent.com` are accessible
- Some firewalls block raw GitHub content; contact your IT team if needed

## Developing Flow CLI

This section is for contributors and those developing Flow CLI itself.

### Development Setup

```bash
# Clone the repository
git clone https://github.com/ebbe-brandstrup/flow-cli.git
cd flow-cli

# Run setup (checks prerequisites and installs dependencies)
chmod +x .flow/setup.sh
./.flow/setup.sh

# Run CLI during development
uv run flow --help

# Run validation checks
uv run scripts/validate.py --quiet-user --auto-format
```

### Building the Binaries

```bash
# Build for current platform
uv run python build_flow.py   # Main CLI
uv run python build_proof.py  # Proof generation CLI

# Output: dist/flow and dist/proof (or .exe on Windows)
./dist/flow --version
./dist/proof --version
```

See [RELEASING.md](RELEASING.md) for full build and release documentation.

### Architecture

Flow CLI follows a strict 3-layer architecture:

- `flow/lib/` contains all business logic and utilities
- `flow/api.py` provides a thin Python API wrapper that delegates to lib/
- `flow/commands/` provides thin CLI wrappers that delegate to lib/

This ensures consistency between CLI and Python API, and keeps all logic testable in one place.

### Project Structure

```
flow-cli/
├── flow/                    # Main flow package
│   ├── api.py               # Python API (Flow, AgentInfo)
│   ├── main.py              # CLI entry point (Click)
│   ├── types.py             # Pydantic models
│   ├── commands/            # CLI command implementations
│   ├── lib/                 # Core library code
│   └── interfaces/          # Agent interface implementations
├── proof/                   # Proof generation package (standalone)
│   ├── api.py               # ProofCollector class
│   ├── cli.py               # Standalone proof CLI
│   ├── collectors/          # CLI and web evidence collection
│   ├── generators/          # Proof document generation
│   └── orchestration/       # Multi-agent proof workflow
├── .flow/                   # Flow config for developing flow-cli itself
│   ├── base.yaml            # Project-wide defaults
│   ├── implement/           # Config hierarchy for implement flows
│   ├── templates/           # Agent prompt and shared file templates
│   └── scripts/flows/       # Flow orchestration scripts (actual location)
├── scripts/                 # Development scripts (validate.py, etc.)
│   └── flows/               # Import shims for .flow/scripts/flows/
├── tests/
│   ├── unit/                # Unit tests (mocked dependencies)
│   ├── integration/         # Integration tests (file system, MockAgentInterface)
│   └── e2e/                 # E2E tests (real agents)
├── USAGE_OVERVIEW.md        # Command reference
├── FLOW_CONFIG_FORMAT.md    # Configuration reference
└── AGENT_PROTOCOL.md        # Agent protocol documentation
```

### Tech Stack

- **uv** - Package manager and Python version management
- **Click** - CLI framework
- **Pydantic** - Data validation
- **PyYAML** - Config parsing
- **Textual** - Interactive terminal UI (built on Rich)
- **psutil** - Process monitoring
- **python-dotenv** - Environment loading

Check `pyproject.toml` for exact version constraints.

### Self-Development Configuration

This repo includes its own flow configuration:

```
.flow/                         # Flow config for flow-cli development
├── base.yaml                  # Project-wide defaults
├── implement/                 # Config hierarchy for implement flows
│   ├── base.yaml              # Base config (agents, shared_files)
│   └── new-feature.yaml       # Variant with documentation-updater
├── templates/                 # Agent prompt and shared file templates
└── scripts/flows/             # Orchestration scripts
    ├── implement.py           # Implementation script (builder + reviewer)
    └── plan.py                # Planning script
.claude/agents/                # Agent definitions for this repo
```

See [USAGE_OVERVIEW.md](USAGE_OVERVIEW.md) for usage examples and development workflows.

## License

MIT
