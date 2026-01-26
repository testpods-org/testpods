# Agent Protocol for Flow CLI

This document describes how agents receive instructions and communicate within the Flow CLI multi-agent orchestration system.

## Overview

When the Flow CLI spawns an agent, it generates a complete system prompt that contains **all context the agent needs directly inline**. This architecture ensures 100% reliable instruction delivery - agents don't need to read external files to understand their task.

The system prompt includes:

1. **Identity**: Flow name, agent name, type, optional role description, and step number
2. **Shared Files**: Context files with their contents or write instructions
3. **Previous Agent Reports**: Output from agents in the chain (if chained with `--after`)
4. **Completion Workflow**: Instructions for progress reporting and completion signaling
5. **Agent Definition**: The agent's base system prompt from config
6. **Task**: The task description, optionally with spec plan context

## System Prompt Structure

Every spawned agent receives a complete system prompt following this structure:

```markdown
[Frontmatter from agent config's system_prompt, if any - hoisted to top]

# Multi-Agent Flow Protocol

You are participating in a multi-agent workflow orchestrated by the Flow CLI.

## Your Identity

- Flow: {flow_name}
- Agent: {agent_name}
- Type: {agent_type}
- Role: {description} (if configured)
- Step: {step_number or N/A}

## Shared Context Files

### Writable Shared Files

**IMPORTANT**: Before signaling completion, you MUST append to these files using the exact format shown.

#### {file_name}

**Path**: {file_path}
**Info**: {description}
**Template**:

\`\`\`md
{write_template_content}
\`\`\`

### Readable Shared Files

#### {file_name}

**Path**: {file_path}
**Info**: {description}

## Previous Agent Reports

[If spawned with --after, reports from previous agent(s)]

## Output Requirements

[Completion workflow template content - progress reporting and completion signaling]

## Agent Definition

[Full contents of agent's system_prompt from config, minus frontmatter]

## Your Assignment

[Optional preamble text if assignment_preamble was provided]

[Task content - either raw input, or spec plan context wrapped in a markdown code block]
```

## Template Syntax Conventions

The system uses two types of placeholders in templates:

### System-Resolved Variables: `{variable}`

Variables in curly braces are replaced by Flow CLI during prompt generation. These are automatically filled in before the agent sees the prompt.

| Variable         | Description                         |
| ---------------- | ----------------------------------- |
| `{flow_name}`    | Name of the current flow            |
| `{flow_dir}`     | Flow directory path                 |
| `{agent_name}`   | Unique agent identifier             |
| `{agent_type}`   | Agent type from config              |
| `{step}`         | Step number                         |
| `{step_title}`   | Step title from spec plan           |
| `{plan_name}`    | Name of the plan file (without .md) |
| `{specs_folder}` | Specs folder path                   |
| `{timestamp}`    | Current timestamp                   |
| `{report_file}`  | Path to the report file             |
| `{success_file}` | Path for completed report           |
| `{failed_file}`  | Path for failed report              |

### Agent-Filled Placeholders: `[instructions]`

Placeholders in square brackets indicate where agents should fill in content during execution. These provide guidance on what the agent should write.

Example:

```markdown
## Summary

[Summarize the work you just did as a concise bullet point list. Each bullet should describe a concrete accomplishment or change.]
```

## Shared Context Files

When shared files are configured in your flow config (e.g., `.flow/implement/base.yaml`), they are included in the system prompt with clear instructions.

### Read-Only Files

Agents are instructed to read these files for context. The path and description are provided inline:

```yaml
shared_files:
  coding_standards:
    path: ".claude/docs/coding-standards.md"
    info: "Project coding standards and conventions. Follow these patterns."
```

**Note**: Non-existent read-only files are automatically filtered out. This allows optional shared files like implementation logs that don't exist for the first step.

### Writable Files

For writable files, the system prompt includes:

- The file path
- A description of the file's purpose
- The exact template format to use when appending

```yaml
shared_files:
  implementation_log:
    path: "{flow_dir}/{flow_name}/implementation-log.md"
    info: "Accumulated decisions and context from previous steps."
    writable: true
    write_template: "@.flow/templates/implementation-log-template.md"
```

The `write_template` supports:

- **File reference**: `@path/to/template.md` - loads template from file
- **Inline string**: Multi-line YAML string with template content

Template variables (`{step}`, `{step_title}`, `{agent_name}`, `{timestamp}`, etc.) are resolved to actual values before being shown to the agent.

## Spec Plan Integration

When an agent is spawned with `--plan-path`, the spec plan is parsed and included in the Task section:

### What Gets Included

1. **Overview**: Content from the start of the plan to the `top_level_end_marker` (default: `## Implementation Steps`)
2. **All Step Headers**: A list of all step headers for context
3. **Current Step Content**: The full content of the agent's current step

### Example Task Section

When using a spec plan, the content is wrapped in a markdown code block to avoid confusion from nested headers:

````markdown
## Your Assignment

```md
# Plan: Feature Implementation

This plan implements the new feature with three steps.

## Prerequisites

- Existing codebase understanding
- Test environment setup

## Implementation Steps (headers only)

- Step 1: Create database schema
- Step 2: Implement API endpoints
- Step 3: Add frontend components

## Current Step: Step 2

### Status: | Step 2: Implement API endpoints

Create REST API endpoints for the feature...

[Full step content follows]
```
````

When using raw task input (no spec plan), the content is included directly without wrapping:

```markdown
## Your Assignment

Implement the user authentication feature with the following requirements...
```

### Spec Plan Configuration

Configure parsing in your flow config (e.g., `.flow/implement/base.yaml`):

```yaml
spec_plan:
  top_level_end_marker: "## Implementation Steps"
  # Pattern must have exactly 3 capture groups: (status) (step_number) (title)
  step_header_pattern: "^### Status: ([^|]*) \\| Step (\\d+): (.+)$"
```

The `step_header_pattern` must contain exactly 3 capture groups:

1. Status indicator (e.g., `✅`, `❌`, or empty)
2. Step number (digits)
3. Step title (text)

## Assignment Preamble

The "## Your Assignment" section can include an optional preamble before the task content. This is useful for providing context-setting instructions to agents, particularly reviewer agents that need specific framing for their task.

When the `assignment_preamble` parameter is provided to `Flow.run()` or `Flow.spawn()`, the text is inserted at the start of the assignment section, before the task content.

### Example with Preamble

When spawning a reviewer with a preamble:

```python
flow.run(
    "reviewer",
    agent=AgentInfo(step=1, role="reviewer"),
    input="Review the changes",
    plan_path="specs/feature.md",
    assignment_preamble="It is now your job to review the changes for this plan-step:",
)
```

The resulting assignment section looks like:

````markdown
## Your Assignment

It is now your job to review the changes for this plan-step:

```md
# Feature Plan

## Overview

...

## Implementation Steps (headers only)

- Step 1: Add new feature

## Current Step: Step 1

...
```
````

### Example without Preamble

When no preamble is provided (the default), the assignment section contains only the task content:

````markdown
## Your Assignment

```md
# Feature Plan

...
```
````

````

The preamble is optional and defaults to `None`. When not provided, the assignment section starts directly with the task content.

## Previous Agent Chaining

When using `--after` to chain from previous agents, their completed reports are referenced in the system prompt.

### Single Agent Chain (`--after agent-1`)

```markdown
## Previous Agent Reports

The previous agent (step-01-builder) completed their work.
Their report is at: `flows/my-flow/agents/step-01-builder/report.complete.md`

Read this report to understand what was done and use it as context for your task.
````

### Multiple Agent Merge (`--after agent-1,agent-2,agent-3`)

```markdown
## Previous Agent Reports

Multiple agents have completed work that provides context for your task:

1. **web-reviewer**: `flows/my-flow/agents/web-reviewer/report.complete.md`
2. **backend-reviewer**: `flows/my-flow/agents/backend-reviewer/report.complete.md`
3. **supabase-reviewer**: `flows/my-flow/agents/supabase-reviewer/report.complete.md`

Read ALL of these reports to understand the full context before starting your task.
```

## Completion Workflow

The completion workflow is configured via `completion_workflow.template` in config and includes:

1. **Progress Reporting**: Report template with sections for status, summary, and notes
2. **Completion Signaling**: File rename instructions for success/failure

**CRITICAL REQUIREMENT**: A completion workflow template is **required** for agents to understand the completion signaling protocol. The `report` config values (`filename`, `success_suffix`, `failure_suffix`) are only meaningful when substituted into a template - without a template, these values are never exposed to agents.

### How Template Substitution Works

The report config values are used as template placeholders:

- `{report_file}` → `report.md`
- `{success_file}` → `report.complete.md`
- `{failed_file}` → `report.failed.md`

These placeholders are replaced in the template before the system prompt is sent to the agent. **Without a template, agents have no way to know what files to rename for completion signaling.**

### Report File

Agents write progress to: `flows/<flow-name>/agents/<agent-id>/report.md`

### Completion Signaling

When finished:

- **Success**: Rename `report.md` to `report.complete.md` - Use when you completed your work
- **Failure**: Rename `report.md` to `report.failed.md` - Use ONLY when you could NOT complete

**Important Distinction**: Completion status vs task outcome

| Scenario                   | Completion Signal | Why                                  |
| -------------------------- | ----------------- | ------------------------------------ |
| Builder implements feature | Success           | Task completed                       |
| Reviewer approves code     | Success           | Review completed                     |
| Reviewer finds issues      | Success           | Review completed (outcome in report) |
| Fixer applies corrections  | Success           | Fix completed                        |
| Agent crashes mid-task     | Failure           | Could not complete                   |
| Agent stuck on error       | Failure           | Could not complete                   |

The outcome of your work (approved vs rejected, tests passing vs failing, etc.) is communicated through report content, NOT through the completion signal.

For reviewers: The flow checks for `REVIEW_RESULT: APPROVED` marker in report content to determine approval. File extension only indicates if you completed the review process.

### Configuration

**Production configuration** (with general-purpose template):

```yaml
completion_workflow:
  template: "@.flow/templates/completion-workflow-template.md"
  report:
    filename: report.md
    success_suffix: .complete.md
    failure_suffix: .failed.md
```

**Specialized templates** for specific flow types:

- `implementation-completion-workflow-template.md` - For flows with code changes (includes git statistics)
- `planning-completion-workflow-template.md` - For planning/spec flows (streamlined, no git)

**Minimal configuration** (for simple use cases):

```yaml
completion_workflow:
  template: "@.flow/templates/completion-workflow-simple.md"
```

### Minimal Template Example

For simple flows, a minimal template like this is sufficient:

```markdown
## Completion Protocol

When you've completed your task, signal completion by renaming your report file:

- **Success**: Rename `{report_file}` to `{success_file}`
- **Failure**: Rename `{report_file}` to `{failed_file}`

Write a brief summary of your work to `{report_file}` before renaming it.
```

The placeholders are automatically replaced with actual filenames before the agent sees the prompt.

## Activity Detection

The Flow CLI uses multi-tier activity detection:

### Tier 1: Process Status

- PID alive check using `psutil`
- Detects crashed or terminated processes

### Tier 2: File Modification Time

- Monitors report file mtime
- Any write to report.md resets the stuck timer

### Tier 3: Heartbeats (Optional)

- Explicit heartbeat signals via `flow heartbeat`
- Updates `last_heartbeat` timestamp

### Stuck Detection

An agent is considered "stuck" when:

1. Process is still alive, AND
2. No file modifications for `stuck_timeout_secs`, AND
3. No heartbeats for `stuck_timeout_secs`

Default stuck timeout is 20 minutes (1200 seconds).

## Agent Working Directory Structure

Each agent has its own working directory:

```
flows/<flow-name>/agents/<agent-id>/
  system_prompt.md    # Full system prompt sent to agent (includes task content)
  report.md           # Progress updates (or .complete.md/.failed.md)
```

## Configuration Reference

See [FLOW_CONFIG_FORMAT.md](FLOW_CONFIG_FORMAT.md) for complete configuration options including:

- `spec_plan` - Spec plan parsing configuration
- `completion_workflow` - Completion workflow and report settings
- `agents` - Agent type definitions
- `shared_files` - Shared file configurations

## Related Documentation

- [FLOW_CONFIG_FORMAT.md](FLOW_CONFIG_FORMAT.md) - Configuration options
- [USAGE_OVERVIEW.md](USAGE_OVERVIEW.md) - CLI command reference
- [README.md](README.md) - Quick start guide
