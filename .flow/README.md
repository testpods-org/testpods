# Flow CLI Development Configuration

This directory contains the flow configuration for **developing the flow-cli itself**.

## Contents

- `base.yaml` - Project-wide defaults (inherited by all flow types)
- `implement/` - Config hierarchy for implementation flows
  - `base.yaml` - Base config for implement flows (agents, shared_files, workflows)
  - `new-feature.yaml` - Variant that adds documentation-updater agent
- `plan/` - Config hierarchy for spec plan planning flows
  - `base.yaml` - Config for planning flows (plan-creator, plan-reviewer, showcase-planner agents)
- `setup.sh` - Development environment setup script
- `templates/` - Templates for agent prompts and shared files

## Config Inheritance

This project uses hierarchical config inheritance:

```
.flow/base.yaml                          # Project-wide defaults
├── .flow/implement/base.yaml            # Base for implement flows
│   └── .flow/implement/new-feature.yaml # Adds documentation-updater
└── .flow/plan/base.yaml                 # Base for planning flows
```

**Usage:**

```bash
# Use the base implement config
flow init --flow my-impl --flow-type implement

# Use the new-feature variant (includes documentation-updater agent)
flow init --flow my-impl --flow-type implement --variant new-feature

# Use the planning flow config
./.flow/scripts/flows/plan.py "Add user authentication"
```

See [FLOW_CONFIG_FORMAT.md](../FLOW_CONFIG_FORMAT.md) for full inheritance documentation.

## Setup Script

The `setup.sh` script prepares a complete development environment:

```bash
./.flow/setup.sh
```

This script:

1. **Installs Python dependencies** - Runs `uv sync --all-extras` to install all packages including dev dependencies
2. **Installs OpenCode wrapper dependencies** - Runs `npm install` in `flow/interfaces/opencode/wrapper/` for the TypeScript wrapper

After running setup, you can use both the Claude Code CLI and OpenCode agent interfaces.

## Template Files

### `templates/completion-workflow-template.md`

The general-purpose completion workflow template is included inline in agent system prompts. It defines:

- **Progress Reporting**: Report structure with sections for status, summary, deferred responsibilities, artifacts created, and notes
- **Completion Workflow**: Step-by-step instructions for generating the final report and signaling completion

**Specialized templates** are also available for specific flow types:

- `implementation-completion-workflow-template.md` - For flows with code changes (includes git statistics, 4-step workflow)
- `planning-completion-workflow-template.md` - For planning/spec flows (streamlined 2-step workflow, no git)

This template uses two types of placeholders:

- **System-resolved variables** (`{variable}`): Replaced by Flow CLI during prompt generation
  - `{step}` - Current step number
  - `{report_file}` - Path to the report file
  - `{success_file}` - Path for completed report
  - `{failed_file}` - Path for failed report
- **Agent-filled placeholders** (`[instructions]`): Guidance for what the agent should write

### `templates/implementation-log-template.md`

Template for entries in the implementation log shared file. Agents append to this file to track decisions and context across steps.

Variables resolved before showing to agent:

- `{flow_name}` - Name of the flow
- `{step}` - Current step number
- `{step_title}` - Title of the current step
- `{agent_name}` - Agent identifier
- `{timestamp}` - Current timestamp

## When to Update

Update files in this directory when:

- Adding new config options to `flow/types.py` that should be used for self-development
- Changing agent definitions:
  - Implement agents: `builder`, `reviewer`, `documentation-updater`
  - Plan agents: `plan-creator`, `plan-reviewer`, `showcase-planner`
- Modifying shared file configuration
- Updating template formats or adding new templates
- Adding new flow types or variants

## Related Files

- `.flow/scripts/flows/implement.py` - Uses implement config for plan implementation
- `.flow/scripts/flows/plan.py` - Uses plan config for spec plan creation
- `.claude/agents/` - Agent system prompts referenced by config
- `FLOW_CONFIG_FORMAT.md` - Full configuration reference (including config inheritance)
- `AGENT_PROTOCOL.md` - Agent protocol documentation
