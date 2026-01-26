# Getting Started with Flow CLI

Flow CLI is a multi-agent orchestration toolkit that coordinates AI coding agents through structured workflows. It provides file-based traceability, spec-plan-driven execution, and complete visibility into agent operations.

This guide walks you through setting up Flow CLI for your specific project. A coding agent (Claude Code or OpenCode) will interview you about your project structure, then create customized configuration files and orchestration scripts.

## Prerequisites

See the [Flow CLI README](https://github.com/ebbe-brandstrup/flow-cli/blob/main/README.md) for installation and setup prerequisites.

## Guided Setup Workflow

When you paste the kick-starter prompt (shown after running `flow scaffold`), the agent follows this workflow to configure Flow CLI for your project.

### Phase 1: Discover Existing Agents

**Goal**: Understand what coding agents already exist in the project.

The agent should ask the user using `AskUserQuestion`:

1. **Where are your agent definition files stored?**
   - Common locations: `.claude/agents/`, `agents/`, `prompts/agents/`
   - Ask user to specify the path (don't assume `.claude/agents/`)
   - Verify the location exists

2. **What agents do you have?**
   - List agent files found in the specified location
   - Ask user to confirm which agents to use for Flow orchestration
   - Identify their roles (builder/implementer, reviewer, planner, etc.)

**If no agents exist**, proceed to Phase 2 to create them.

### Phase 2: Create Missing Agents (If Needed)

**Goal**: Ensure the project has the minimum agents needed for Flow orchestration.

Flow workflows typically need at least:

- **Builder/Implementer**: Makes code changes following a plan
- **Reviewer**: Validates code changes (read-only)
- **(Optional) Planner**: Creates implementation plans from feature requests

The agent should ask the user:

1. **Where should new agent files be created?**
   - Let user choose the location (e.g., `.claude/agents/`, `agents/`)

2. **For each missing agent, gather context**:
   - What programming languages/frameworks does the project use?
   - What coding style or patterns should agents follow?
   - Are there specific tools the agent should use? (e.g., MCP servers)

3. **What validation commands should agents run?**
   - Testing: `pytest`, `npm test`, `cargo test`, etc.
   - Linting: `ruff check`, `eslint`, `cargo clippy`, etc.
   - Type checking: `mypy --strict`, `tsc --noEmit`, etc.
   - These commands will be included in agent prompts AND orchestration scripts

**Agent file template** (minimal skeleton - fill based on project context):

```
---
name: <agent_name>
description: <Brief description of what this agent does>
tools: <Comma-separated list of tools - e.g., Glob, Grep, Read, Edit, Write, Bash>
model: <Model tier - e.g., opus, sonnet>
---

# <Agent Role Name>

<Brief description of the agent's purpose and responsibilities>

## Your Mission

<Specific instructions for what this agent should accomplish>

## Tech Stack

<List the languages, frameworks, and tools this project uses>

## Core Responsibilities

<Numbered list of key responsibilities>

## Validation

Before completing, run these validation commands:

    <validation_commands>

## Important Rules

<Project-specific rules and constraints>
```

### Phase 3: Interview About Project Context

**Goal**: Gather information needed to create Flow configuration files.

The agent should ask using `AskUserQuestion`:

1. **Project structure**:
   - What languages/frameworks does this project use?
   - Where is the main source code located?
   - Are there any monorepo patterns or multiple packages?

2. **Development workflow**:
   - How are features typically implemented? (spec → implement → review → merge)
   - Is there a spec/plan document format you use?
   - Do you use feature branches?

3. **Documentation and guides**:
   - Are there coding guidelines or architecture docs that agents should follow?
   - Where are they located? (e.g., `docs/`, `rules_and_guides/`, `CONTRIBUTING.md`)
   - Which guides should be shared with which agents?

4. **Git workflow**:
   - Auto-detect the default branch: check `git symbolic-ref refs/remotes/origin/HEAD`
   - Confirm with user: "Your default branch appears to be `main`. Is that correct?"

5. **Existing configs** (if applicable):
   - If `.flow/implement/` or `.flow/plan/` already exist, ask before overwriting
   - "I found existing Flow configs. Should I update them or create new variants?"

Continue interviewing until confident about the full picture before creating files.

### Phase 4: Create Configuration Files

**Goal**: Create `.flow/implement/base.yaml` and optionally `.flow/plan/base.yaml`.

Based on interview answers, create configuration that:

1. **References actual project agents**:

   ```yaml
   agents:
     builder:
       system_prompt: "@{path_to_builder_agent}"
       model: anthropic/opus
       # ...
     reviewer:
       system_prompt: "@{path_to_reviewer_agent}"
       model: anthropic/opus
       # ...
   ```

2. **Includes relevant shared_files**:

   ```yaml
   shared_files:
     # Writable shared file for context accumulation
     implementation_log:
       path: "{flow_dir}/{flow_name}/implementation-log.md"
       info: "Accumulated decisions and context from previous steps."
       writable: true
       write_template: "@.flow/templates/implementation-log-template.md"

     # Read-only shared files for agents
     coding_guidelines:
       path: "docs/CODING_GUIDELINES.md"
       info: "Project coding standards and patterns."
       agents:
         - builder
         - reviewer
   ```

3. **Sets appropriate spec_plan parsing** (for implement flows):

   ```yaml
   spec_plan:
     top_level_end_marker: "## Implementation Steps"
     step_header_pattern: "^### Status: ([^|]*) \\| Step (\\d+): (.+)$"
   ```

**Reference documentation**:

- See `docs/FLOW_CONFIG_FORMAT.md` for complete configuration options
- See `examples/.flow/` for working examples from the flow-cli repository

### Phase 5: Create First Orchestration Script

**Goal**: Help user set up their first orchestration script.

The agent should ask:

1. **Which workflow should we create first?**
   - **Implement flow** (most common): Takes a spec plan, implements step by step
   - **Plan flow**: Takes a feature request, creates a spec plan
   - **Custom flow**: User describes their own workflow pattern

2. **What validation steps should be included?**
   - Reference the validation commands gathered in Phase 2
   - Where in the workflow should validation run? (after each step, before review, etc.)

3. **What should happen after successful implementation?**
   - Automatic git commit?
   - Run learnings analysis?
   - Generate proof documentation?

**Generate the script from scratch** based on interview answers, using the patterns from `examples/.flow/` as reference. The script should:

- Use the `Flow` class context manager for cleanup
- Support resume (check `has_completed_agent()` before running)
- Include validation checkpoints with the user's commands
- Handle errors gracefully with proper status reporting

**CRITICAL: Always validate your script**

After creating or editing any flow orchestration script, you MUST run the validation script:

```bash
uv run .claude/skills/flow-cli/scripts/validate_flow_orchestration.py path/to/your/script.py
```

This validates that the script:

1. Starts with the required UV inline metadata header (PEP 723)
2. Has valid Python syntax
3. Accepts a `--dry-run` flag
4. Runs successfully with `--dry-run`

Scripts missing the UV metadata will fail with `ModuleNotFoundError: No module named 'flow'`.

> **Note**: The example scripts in `examples/.flow/` (from the flow-cli repo) don't include UV metadata because they run within the repo where flow-cli is installed. Scripts you create for external projects MUST include it.

**Required: --dry-run flag**

All orchestration scripts must accept a `--dry-run` flag that shows what the script would do without actually spawning agents or making changes. This enables safe testing and validation.

**Orchestration script requirements**

All flow orchestration scripts must:

1. **Start with the exact UV inline metadata header**:

   ```python
   #!/usr/bin/env -S uv run
   # /// script
   # requires-python = ">=3.13"
   # dependencies = [
   #     "flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli",
   # ]
   # ///
   ```

2. **Accept a `--dry-run` flag** that shows what the script would do without actually spawning agents or making changes

3. **Use argparse** to parse the spec plan path and --dry-run flag

**Example orchestration script structure**:

```python
#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = [
#     "flow-cli @ git+https://github.com/ebbe-brandstrup/flow-cli",
# ]
# ///
"""Orchestration script for implementing features from spec plans."""

import argparse
from pathlib import Path

from flow.api import Flow, AgentInfo
# IMPORTANT: Use these helpers for spec plan parsing - don't invent Flow methods
from flow.lib.spec_parser import get_all_steps, get_pending_steps, mark_step_completed


def main():
    parser = argparse.ArgumentParser(description="Implement features from spec plans")
    parser.add_argument("spec_plan", type=Path, help="Path to the spec plan file")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would happen without spawning agents",
    )
    args = parser.parse_args()
    plan_path = args.spec_plan

    if args.dry_run:
        print(f"[DRY RUN] Would process spec plan: {plan_path}")
        print("[DRY RUN] Would parse steps and spawn agents for each")
        return

    with Flow("my-flow", flow_type="implement") as flow:
        # Parse spec plan steps using helper functions
        all_steps = get_all_steps(plan_path, flow.config)
        pending_steps = get_pending_steps(plan_path, flow.config)

        if not all_steps:
            print("No steps found in plan file")
            return

        # Set total for progress tracking
        flow.set_total_steps(len(all_steps))

        for step_number, step_title in pending_steps:
            # Build
            builder_result = flow.run(
                "builder",
                agent=AgentInfo(step=step_number, role="builder"),
                input=f"Implement step {step_number}: {step_title}",
                plan_path=str(plan_path),
            )

            # Validation - {insert user's validation commands}

            # Review
            flow.run(
                "reviewer",
                agent=AgentInfo(step=step_number, role="reviewer"),
                input=f"Review step {step_number}: {step_title}",
                after=builder_result.final_agent,
            )

            # Mark step completed
            mark_step_completed(plan_path, step_number)
            flow.mark_step_completed(step_number)

        flow.set_status("completed")


if __name__ == "__main__":
    main()
```

### Phase 6: Summarize and Guide Verification

**Goal**: Show user what was created and how to verify.

1. **Summary of created files**:

   ```
   Created:
     .claude/agents/builder.md          - Implementation agent
     .claude/agents/reviewer.md         - Code review agent
     .flow/implement/base.yaml          - Implement flow configuration
     .flow/scripts/flows/implement.py   - Orchestration script

   Updated:
     (list any files that were modified)
   ```

2. **Verification instructions**:

   Tell the user to run `flow status` to verify the configuration is valid:

   ```bash
   flow status
   ```

   This checks that all referenced files exist and the configuration is parseable.

3. **Next steps**:
   - Create a spec plan document for your first feature
   - Run the orchestration script: `./.flow/scripts/flows/implement.py path/to/spec.md`
   - Monitor with: `flow dashboard`

## Reference Documentation

For detailed configuration options and advanced patterns:

| Document                     | Content                                               |
| ---------------------------- | ----------------------------------------------------- |
| `docs/FLOW_CONFIG_FORMAT.md` | Complete config schema: agents, shared_files, etc.    |
| `docs/api_docs.md`           | Python API reference for orchestration scripts        |
| `docs/cli_docs.md`           | CLI command reference                                 |
| `docs/AGENT_PROTOCOL.md`     | How agents receive instructions and signal completion |
| `examples/.flow/`            | Working configs and scripts from flow-cli repo        |

## Example Configurations

The `examples/.flow/` directory contains literal copies of configuration files from the flow-cli repository. These serve as working references:

- `examples/.flow/implement/base.yaml` - Full implement flow config with agents and shared_files
- `examples/.flow/plan/base.yaml` - Planning flow config
- `examples/.flow/scripts/flows/implement.py` - Complete implementation orchestrator
- `examples/.flow/scripts/flows/lib/` - Reusable orchestration utilities

Study these examples to understand patterns for:

- Agent definitions with tools and model tiers
- Shared file configuration for context passing
- Spec plan parsing configuration
- Validation integration in orchestration scripts
- Build → review → fix loops

## Troubleshooting

### "Agent file not found"

Ensure the paths in your config's `system_prompt` fields are correct:

```yaml
agents:
  builder:
    system_prompt: "@.claude/agents/builder.md" # Path must exist
```

### "Shared file not found"

Check that all `shared_files` paths exist in your project:

```yaml
shared_files:
  guidelines:
    path: "docs/GUIDELINES.md" # This file must exist
```

### "flow status" shows errors

Run `flow status` with verbose output to see details:

```bash
flow status -v
```

Common issues:

- YAML syntax errors in config files
- Missing `base_config` reference in flow-type config
- Invalid `agent_interface` value (must be `claude_code_cli` or `opencode`)
