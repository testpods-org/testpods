# Flow CLI Configuration Format

This document describes the structure and options available in the Flow CLI configuration files (e.g., `.flow/implement/base.yaml`).

## Overview

The Flow CLI reads configuration from hierarchical config files in the `.flow/` directory. Configuration is organized by flow type (e.g., `.flow/implement/base.yaml`) with optional variants. This file defines:

- Which agent interface to use (e.g. Claude Code CLI)
- Where flow data is stored
- Default timeout values
- Spec plan parsing configuration
- Completion workflow settings (templates and report naming)
- Dashboard settings (multi-directory monitoring, worktree discovery)
- Agent definitions with their configurations

## File Location

### Recommended Setup (Hierarchical Config)

Use hierarchical config files organized by flow type:

```
your-project/
  .flow/
    base.yaml                    # Optional: project-wide defaults
    implement/
      base.yaml                  # Base config for all implement flows
      new-feature.yaml           # Variant: inherits from base.yaml
      bug-fix.yaml               # Variant: inherits from base.yaml
    dependency-update/
      base.yaml                  # Base for dependency update flows
    analysis/
      base.yaml                  # Base for analysis flows
  flows/                         # Default flow data directory
```

## Configuration Structure

### Complete Example

```yaml
# Agent interface: claude_code_cli or opencode
agent_interface: claude_code_cli

# Directory for flow data
flow_dir: flows

# Default git branch for commit searches
default_branch: main

# Default timeouts (in seconds)
defaults:
  stuck_timeout_secs: 1200 # 20 minutes - no activity = stuck
  max_wait_secs: 3600 # 60 minutes - maximum wait time for await

# OpenCode-specific configuration (when using agent_interface: opencode)
opencode:
  server_hostname: 127.0.0.1

# Spec plan parsing configuration
spec_plan:
  top_level_end_marker: "## Implementation Steps"
  step_header_pattern: "^### Status: ([^|]*) \\| Step (\\d+): (.+)$"

# Completion workflow configuration
completion_workflow:
  template: "@.flow/templates/completion-workflow-template.md"
  report:
    filename: report.md
    success_suffix: .complete.md
    failure_suffix: .failed.md

# Dashboard settings
dashboard:
  discover_worktrees: true # Auto-discover flows in git worktrees

# Agent definitions (model in provider/model format)
agents:
  builder:
    model: anthropic/opus
    system_prompt: .claude/agents/builder.md
    mcp_config: .mcp-builder-agent.json

  plan-step-tester:
    model: anthropic/sonnet
    system_prompt: .claude/agents/plan-step-tester.md

  web-reviewer:
    model: anthropic/sonnet
    system_prompt: .claude/agents/web-reviewer.md

  # Agent with per-agent context window override
  compact-builder:
    model: anthropic/opus
    system_prompt: .claude/agents/builder.md
    context_window: 150000 # Limit context to 150k tokens

# Global context window overrides (optional)
# model_context_windows:
#   anthropic/opus: 180000

shared_files:
  implementation_log:
    path: "{flow_dir}/{flow_name}/implementation-log.md"
    info: "Accumulated decisions and context from previous steps."
    writable: true
    write_template: "@.flow/templates/implementation-log-template.md"
```

## Config Inheritance

Config inheritance allows you to share common configuration across different flow types while maintaining type-specific overrides. This is useful when:

- Different flow types need different agents (implement vs. dependency-update)
- Some flows need additional shared files
- Flow types should have isolated configurations
- Common settings should be shared without duplication

### Core Concepts

#### Flow Type

A category of flows that share common purpose and configuration:

- `implement` - Feature implementation, bug fixes
- `dependency-update` - Version upgrades
- `analysis` - Token usage, codebase analysis
- Custom types as needed

#### Variant

A specific configuration within a flow type:

- `new-feature` - New feature implementation
- `bug-fix` - Bug fix implementation
- `refactor` - Code refactoring

### Inheritance Fields

Two fields enable config inheritance:

#### `base_config`

**Type:** `string` (file path)
**Default:** `null`
**Required:** No

Path to a parent config file to inherit from. The path is resolved relative to the **project root** (the directory containing `.flow/`).

```yaml
# .flow/implement/new-feature.yaml
base_config: .flow/implement/base.yaml # Inherits from implement base
flow_type: implement
# ... additional or overriding settings
```

#### `flow_type`

**Type:** `string`
**Default:** `null`
**Required:** No (but recommended for config inheritance)

Identifies the category of flow. Used for organizing configs and can be used by other systems (like learnings databases) for per-type isolation.

```yaml
flow_type: implement # e.g., "implement", "dependency-update", "analysis"
```

### Inheritance Rules

Config inheritance follows these rules for merging parent and child configurations:

#### Rule 1: Scalar Fields → Overwrite

Single-value fields replace their parent's value:

```yaml
# .flow/implement/base.yaml
flow_type: implement
flow_dir: flows
default_branch: main

# .flow/implement/new-feature.yaml
base_config: .flow/implement/base.yaml
flow_dir: custom-flows   # ← Overwrites parent's value
# default_branch inherited as "main"
# flow_type inherited as "implement"
```

#### Rule 2: Dict Fields → Deep Merge

Dict fields (like `agents`, `shared_files`, `defaults`) are recursively merged:

```yaml
# .flow/implement/base.yaml
flow_type: implement
agents:
  builder:
    model: anthropic/opus
    system_prompt: "@.claude/agents/builder.md"
  code-reviewer:
    model: anthropic/sonnet
    system_prompt: "@.claude/agents/code-reviewer.md"

# .flow/implement/new-feature.yaml
base_config: .flow/implement/base.yaml
agents:
  # NEW agent - added to parent's agents
  documentation-updater:
    model: anthropic/sonnet
    system_prompt: "@.claude/agents/documentation-updater.md"
  # OVERRIDE existing agent's field
  builder:
    mcp_config: .mcp-extra-tools.json  # ← Merged into existing config
```

**Result after merge:**

```yaml
agents:
  builder:
    model: anthropic/opus # From base
    system_prompt: "@.claude/agents/builder.md" # From base
    mcp_config: .mcp-extra-tools.json # From new-feature.yaml
  code-reviewer:
    model: anthropic/sonnet # From base
    system_prompt: "@.claude/agents/code-reviewer.md" # From base
  documentation-updater:
    model: anthropic/sonnet # From new-feature.yaml
    system_prompt: "@.claude/agents/documentation-updater.md"
```

#### Rule 3: List Fields → Concatenate

List fields are appended (child entries come after parent entries):

```yaml
# .flow/implement/base.yaml
shared_files:
  documentation_updates:
    agents:
      - builder
      - reviewer

# .flow/implement/new-feature.yaml
base_config: .flow/implement/base.yaml
shared_files:
  documentation_updates:
    agents:
      - documentation-updater  # ← Appended after base entries
```

**Result after merge:**

```yaml
shared_files:
  documentation_updates:
    agents:
      - builder # From base
      - reviewer # From base
      - documentation-updater # From new-feature.yaml (appended)
```

### Path Resolution

All paths in config files (including `base_config`) are resolved relative to the **project root**, not relative to the config file's location. This is consistent with how `@` prefix paths work throughout the system.

The project root is determined by walking up the directory tree looking for a `.flow/` directory.

```yaml
# In .flow/implement/new-feature.yaml:
base_config: .flow/implement/base.yaml  # Resolved from project root

# NOT this (relative to config file):
base_config: base.yaml  # WRONG - paths are relative to project root
```

### CLI Usage

Use `--flow-type` and `--variant` options to specify configs by type:

```bash
# Using explicit config path
flow init --config .flow/implement/new-feature.yaml --flow my-feature

# Using flow-type + variant shorthand
flow init --flow-type implement --variant new-feature --flow my-feature
# Resolves to: .flow/implement/new-feature.yaml

# Using just flow-type (uses base.yaml)
flow init --flow-type implement --flow my-feature
# Resolves to: .flow/implement/base.yaml
```

**Option precedence:**

1. Global `--config` option (highest priority)
2. `--flow-type` + `--variant` combination (resolves to `.flow/{flow_type}/{variant}.yaml` or `.flow/{flow_type}/base.yaml`)

**Note:** There is no default fallback - you must specify either `--config` or `--flow-type`.

### Example: Three-Level Inheritance

```yaml
# .flow/base.yaml - Project-wide defaults
flow_type: _base
flow_dir: flows
default_branch: main
defaults:
  stuck_timeout_secs: 1200
  max_wait_secs: 3600

# .flow/implement/base.yaml - Implement flow base
base_config: .flow/base.yaml
flow_type: implement
agent_interface: claude_code_cli
agents:
  builder:
    model: anthropic/opus
    system_prompt: "@.claude/agents/builder.md"
  reviewer:
    model: anthropic/opus
    system_prompt: "@.claude/agents/reviewer.md"
shared_files:
  implementation_log:
    path: "{flow_dir}/{flow_name}/implementation-log.md"
    writable: true

# .flow/implement/new-feature.yaml - New feature variant
base_config: .flow/implement/base.yaml
agents:
  documentation-updater:
    model: anthropic/opus
    system_prompt: "@.claude/agents/documentation-updater.md"
shared_files:
  feature_spec:
    path: "{specs_folder}/{plan_name}.md"
    info: "The feature specification being implemented"
```

### Error Handling

**Circular Inheritance Detection:**

The config loader detects circular inheritance and raises an error:

```yaml
# .flow/a.yaml
base_config: .flow/b.yaml

# .flow/b.yaml
base_config: .flow/a.yaml  # ERROR: Circular inheritance detected
```

**Missing Base Config:**

If a `base_config` path doesn't exist, a clear error is raised:

```
ConfigNotFoundError: Config file not found: .flow/missing.yaml
If using base_config, the path must be relative to project root.
```

## Configuration Options

### `agent_interface`

**Type:** `string`
**Default:** `claude_code_cli`
**Required:** No

Specifies which agent interface implementation to use for spawning and monitoring agents.

**Supported values:**

- `claude_code_cli` - Uses Anthropic's Claude Code CLI tool
- `opencode` - Uses the OpenCode SDK with TypeScript wrapper for per-agent configuration

```yaml
agent_interface: claude_code_cli
# OR
agent_interface: opencode
```

### `flow_dir`

**Type:** `string`
**Default:** `flows`
**Required:** No

The directory where flow data is stored. Each flow gets its own subdirectory containing status files, agent directories, and reports.

```yaml
flow_dir: flows
```

**Directory structure created:**

```
flows/
  my-flow-name/
    status.json           # Flow status tracking
    breadcrumbs.jsonl     # Command audit trail
    agents/
      step-01-builder/
        system_prompt.md  # Full system prompt sent to agent
        report.md         # Agent progress report
```

### `default_branch`

**Type:** `string`
**Default:** `main`
**Required:** No

The default git branch used for commit searches in commands like `flow monitor-step`. This is the branch that feature branches are expected to diverge from.

```yaml
default_branch: main
```

Set this to match your repository's default branch (commonly `main` or `master`).

### `defaults`

**Type:** `object`
**Required:** No

Default timeout values used when monitoring agents.

#### `defaults.stuck_timeout_secs`

**Type:** `integer`
**Default:** `1200` (20 minutes)

Seconds of inactivity before an agent is considered "stuck". Activity is detected through:

- Report file modifications (mtime changes)
- Heartbeat signals
- Process status changes

```yaml
defaults:
  stuck_timeout_secs: 1200
```

#### `defaults.max_wait_secs`

**Type:** `integer`
**Default:** `3600` (60 minutes)

Maximum time to wait for an agent to complete during `flow await` commands. After this timeout, the await returns with `timeout` exit reason.

```yaml
defaults:
  max_wait_secs: 3600
```

#### `defaults.stuck_retry_grace_secs`

**Type:** `integer`
**Default:** `30` (30 seconds)

Grace period to wait before spawning a retry agent when an agent is detected as stuck. During this grace period, the system re-checks the agent's activity - if the agent resumes work (e.g., after system wake from sleep), the retry is cancelled and monitoring continues for the original agent.

If the agent remains stuck after the grace period, the original process is terminated before spawning the retry agent. This prevents both the original and retry agents from running simultaneously.

```yaml
defaults:
  stuck_retry_grace_secs: 30
```

### `spec_plan`

**Type:** `object`
**Required:** No

Configuration for parsing specification plan files to extract step information.

#### `spec_plan.top_level_end_marker`

**Type:** `string`
**Default:** `## Implementation Steps`

The markdown heading that marks the end of the top-level overview content and the beginning of step definitions in a plan file.

#### `spec_plan.step_header_pattern`

**Type:** `string` (regex pattern)
**Default:** `^### Status: ([^|]*) \| Step (\d+): (.+)$`

Regular expression pattern used to identify step headers in the plan file. **Must contain exactly 3 capture groups:**

1. **Status indicator** - The status emoji or text (e.g., `✅`, `❌`, or empty)
2. **Step number** - Digits identifying the step (e.g., `1`, `2`, `10`)
3. **Step title** - The descriptive title of the step

The pattern is validated during config loading. A `ConfigValidationError` is raised if the pattern does not contain exactly 3 capture groups.

**Example patterns:**

```yaml
spec_plan:
  top_level_end_marker: "## Implementation Steps"
  # Default pattern - matches "### Status: ✅ | Step 1: Add database models"
  step_header_pattern: "^### Status: ([^|]*) \\| Step (\\d+): (.+)$"
```

**Custom pattern example:**

```yaml
spec_plan:
  # Custom format: "## [DONE] Step 3 - Implement API"
  step_header_pattern: "^## \\[([^\\]]*)\\] Step (\\d+) - (.+)$"
```

**What each capture group matches in the default pattern:**

| Group | Pattern Part | Matches     | Example               |
| ----- | ------------ | ----------- | --------------------- | ------------------- |
| 1     | `([^         | ]\*)`       | Status indicator      | `✅ ` or `` (empty) |
| 2     | `(\d+)`      | Step number | `1`, `2`, `10`        |
| 3     | `(.+)`       | Step title  | `Add database models` |

### `completion_workflow`

**Type:** `object`
**Required:** No

Configuration for how agents complete their work, including templates and report file naming.

#### `completion_workflow.template`

**Type:** `string` (file path with @ prefix)
**Default:** `null`

Optional path to a markdown template file for agent completion workflow. When specified (with `@` prefix for file references), the template content is included inline in the agent's system prompt, providing consistent formatting guidance for progress reporting and completion signaling.

The template should include:

- Report structure/template for progress updates
- Completion signaling instructions (file renaming)

See `.flow/templates/completion-workflow-template.md` for a general-purpose example. Specialized templates are also available:

- `implementation-completion-workflow-template.md` - For implementation flows (includes git statistics)
- `planning-completion-workflow-template.md` - For planning flows (streamlined, no git)

#### `completion_workflow.report`

**Type:** `object`
**Required:** No

Configures the naming conventions for agent report files.

##### `completion_workflow.report.filename`

**Type:** `string`
**Default:** `report.md`

The base filename for agent progress reports.

##### `completion_workflow.report.success_suffix`

**Type:** `string`
**Default:** `.complete.md`

Suffix used when renaming report file on successful completion.
`report.md` becomes `report.complete.md`

##### `completion_workflow.report.failure_suffix`

**Type:** `string`
**Default:** `.failed.md`

Suffix used when renaming report file on failure.
`report.md` becomes `report.failed.md`

```yaml
completion_workflow:
  template: "@.flow/templates/completion-workflow-template.md"
  report:
    filename: report.md
    success_suffix: .complete.md
    failure_suffix: .failed.md
```

### `dashboard`

**Type:** `object`
**Required:** No

Configure default behavior for the interactive dashboard (`flow dashboard` command).

#### `dashboard.discover_worktrees`

**Type:** `boolean`
**Default:** `true`

When enabled, the dashboard automatically discovers git worktrees and includes flows from each worktree's configured `flow_dir`. This is useful for parallel development workflows where you have multiple worktrees (e.g., `trees/feature-a`, `trees/feature-b`) and want a unified view of all running flows.

Worktree discovery requires git to be available. If git is not found, the dashboard displays a warning and continues without worktree discovery.

```yaml
dashboard:
  discover_worktrees: true
```

**CLI overrides:**

The dashboard command provides flags to override this setting:

- `--discover-worktrees` - Enable worktree discovery (overrides config)
- `--no-discover-worktrees` - Disable worktree discovery (overrides config)

**Additional CLI options:**

- `--flow-dir, -d PATH` - Additional flow directory to monitor (repeatable). Use this to explicitly add directories beyond the configured `flow_dir` and discovered worktrees.
- `--limit N` - Maximum number of flows to display (default: 20). Helps maintain dashboard performance with large flow histories.

**Example usage:**

```bash
# Use config defaults
flow dashboard

# Explicitly add extra directories
flow dashboard -d /path/to/other/flows -d /another/project/flows

# Disable worktree discovery for this session
flow dashboard --no-discover-worktrees

# Limit displayed flows
flow dashboard --limit 10
```

### `opencode`

**Type:** `object`
**Required:** No

OpenCode-specific configuration options. These settings are only used when `agent_interface: opencode` is set.

#### `opencode.server_hostname`

**Type:** `string`
**Default:** `127.0.0.1`

The hostname for the OpenCode server.

```yaml
opencode:
  server_hostname: 127.0.0.1
```

### `agents`

**Type:** `object` (map of agent name to config)
**Default:** `{}`
**Required:** No (but recommended)

Defines the agent types available for spawning. Each key is the agent type name, and the value is its configuration.

```yaml
agents:
  agent-name:
    model: anthropic/sonnet # provider/model format
    system_prompt: "@path/to/prompt.md" # File reference (@ prefix)
    # OR inline:
    # system_prompt: "You are a helpful assistant."
    mcp_config: path/to/mcp-config.json
```

#### Agent Configuration

Each agent definition supports the following fields:

##### `model`

**Type:** `string`
**Default:** `anthropic/sonnet`
**Required:** No

The model to use for this agent, specified in `provider/model` format. Each interface extracts what it needs:

- **Claude Code CLI**: Extracts the model part after `/` (e.g., `anthropic/opus` → uses `opus`)
- **OpenCode**: Uses the full string (e.g., `anthropic/claude-sonnet-4-5`)

**Common values for Claude Code CLI:**

- `anthropic/haiku` - Fastest and most cost-effective, suitable for simple tasks
- `anthropic/sonnet` - Balanced performance and cost, recommended for most tasks
- `anthropic/opus` - Most capable, use for complex reasoning and difficult problems

**Common values for OpenCode:**

- `anthropic/claude-haiku-4-5` - Fast Anthropic model
- `anthropic/claude-sonnet-4-5` - Balanced Anthropic model
- `anthropic/claude-opus-4-5` - Most capable Anthropic model
- `openai/gpt-4o` - OpenAI's GPT-4o model
- `groq/llama-3.3-70b-versatile` - Groq's Llama model

```yaml
agents:
  simple-agent:
    model: anthropic/sonnet # Recommended default

  complex-agent:
    model: anthropic/opus # For complex reasoning

  # OpenCode with specific model version
  opencode-agent:
    model: anthropic/claude-sonnet-4-5
```

##### `system_prompt`

**Type:** `string`
**Default:** `null` (no custom system prompt)
**Required:** No

The base system prompt for this agent type. Supports dual format:

- **File reference**: `@path/to/prompt.md` - loads prompt from file (@ prefix)
- **Inline string**: Multi-line string with the prompt content

File paths are resolved relative to the project root.

**Examples:**

```yaml
agents:
  # File reference (most common)
  builder:
    model: anthropic/opus
    system_prompt: "@.claude/agents/builder.md"

  # Inline prompt
  simple-agent:
    model: anthropic/sonnet
    system_prompt: |
      You are a code reviewer.

      Review the provided code for:
      - Correctness
      - Performance
      - Best practices
```

##### `mcp_config`

**Type:** `string` (file path)
**Default:** `null`
**Required:** No

Path to an MCP (Model Context Protocol) configuration file for this agent. MCP servers provide additional tools and context to agents.

```yaml
agents:
  web-crawler:
    model: anthropic/sonnet
    mcp_config: .mcp-web-tools.json
```

##### `agent_interface`

**Type:** `string` (enum)
**Default:** `null` (use top-level `agent_interface`)
**Required:** No

Override the agent interface for this specific agent type. When set, this takes precedence over the top-level `agent_interface` setting, but can still be overridden by CLI/API parameters.

This enables heterogeneous agent setups where different agent types can use different interface implementations.

**Supported values:** Same as top-level `agent_interface` (`claude_code_cli`, `opencode`)

```yaml
# Top-level default
agent_interface: claude_code_cli

agents:
  # Uses top-level default (claude_code_cli)
  standard-builder:
    model: anthropic/sonnet

  # Override for this specific agent type to use OpenCode
  opencode-agent:
    model: anthropic/claude-sonnet-4-5
    agent_interface: opencode # Override to use OpenCode for this agent
```

**Resolution priority (highest to lowest):**

1. CLI/API parameter (`--agent-interface` or `agent_interface` kwarg)
2. Agent-level `agent_interface` in config (this field)
3. Top-level `agent_interface` (fallback)

##### `context_window`

**Type:** `integer`
**Default:** `null` (use model registry default)
**Required:** No

Override the context window size (in tokens) for this specific agent. When set, this takes precedence over global `model_context_windows` overrides and the built-in model registry.

This is useful when you want to limit context usage for specific agents to reduce costs, or when using a model variant with a different context window than the registry default.

```yaml
agents:
  # Agent with custom context window
  compact-agent:
    model: anthropic/opus
    context_window: 100000 # Use 100k tokens instead of default 200k

  # Agent using registry default
  standard-agent:
    model: anthropic/sonnet
    # No context_window = use model registry default (200k for Anthropic)
```

**Context window resolution priority (highest to lowest):**

1. Agent-level `context_window` (this field)
2. Global `model_context_windows` override
3. Built-in model registry
4. Provider default (200k for Anthropic, 128k for OpenAI)
5. Global fallback (200k tokens)

##### `declared_artifacts`

**Type:** `list[object]`
**Default:** `[]` (empty list)
**Required:** No

Specifies artifacts the agent is expected to produce. These are validated after agent completion, and if any are missing, the agent session is automatically resumed to fix the issue.

Each artifact object supports the following fields:

- `type` (string, required): Category of artifact (e.g., `"report"`, `"log_entry"`, `"code_change"`)
- `path` (string, required): Path template with variables `{agent_dir}`, `{flow_dir}`, and `{flow_name}`
- `append` (boolean, default: `false`): If true, validates the file grew (for log files). If false, validates the file exists.
- `description` (string, optional): Human-readable description included in prompts when resuming to fix missing artifacts.
- `alternatives` (list[string], default: `[]`): Alternative paths that can satisfy this artifact. For non-append artifacts, validation passes if `path` exists OR any path in `alternatives` exists. Ignored for append-mode artifacts. Uses same template variables as `path`.

**Path template variables:**

- `{agent_dir}` - Resolved to the agent's directory (e.g., `flows/my-flow/step-1/agents/step-01-builder`)
- `{flow_dir}` - Resolved to the flow's directory (e.g., `flows/my-flow`)
- `{flow_name}` - Resolved to the name of the flow (e.g., `my-flow`)

**Example:**

```yaml
agents:
  # Application approval agent - produces one of two possible reports
  application-reviewer:
    model: anthropic/opus
    system_prompt: "@.claude/agents/application-reviewer.md"
    declared_artifacts:
      # Decision report with alternatives
      # The agent produces EITHER an approval OR rejection report based on review
      - type: decision_report
        path: "{agent_dir}/approved_application_report.md"
        alternatives:
          - "{agent_dir}/rejected_application_report.md"
        description: "Application decision report - either approval or rejection"

  # Builder agent with append-mode log artifact
  builder:
    model: anthropic/opus
    system_prompt: "@.claude/agents/builder.md"
    declared_artifacts:
      # Append-mode artifact (alternatives ignored for append mode)
      - type: log_entry
        path: "{flow_dir}/{flow_name}/implementation-log.md"
        append: true
        description: "Implementation log entry documenting decisions and progress"
```

The `alternatives` feature is useful when an agent's output depends on its decision. In the example above, the application reviewer produces exactly one report based on whether it approves or rejects the application. Validation passes if either file exists.

**Validation behavior:**

- Artifacts are validated after each agent completes via `Flow.await_all()` or CLI commands `flow await` and `flow await-all` (enabled by default)
- If artifacts are missing, the agent session is resumed with a prompt explaining what's missing
- The agent can make up to `artifact_retry_limit` (default: 3) attempts to produce the artifacts
- If validation still fails after retries, `ArtifactValidationError` is raised

**Disabling validation:**

In the Python API, use `validate_artifacts=False`:

```python
results = flow.await_all([agent1, agent2], validate_artifacts=False)
```

In the CLI, use `--no-validate-artifacts`:

```bash
flow await -f my-flow -a reviewer-1 --no-validate-artifacts
flow await-all -f my-flow --agents reviewer-1,reviewer-2 --no-validate-artifacts
```

### `model_context_windows`

**Type:** `object` (map of model ID to token count)
**Default:** `null`
**Required:** No

Global overrides for model context window sizes. Keys are model identifiers in `provider/model` format, values are token counts.

These overrides take precedence over the built-in model registry but are overridden by agent-level `context_window` settings.

```yaml
# Override context windows for specific models globally
model_context_windows:
  anthropic/opus: 180000 # All opus agents use 180k
  anthropic/sonnet: 150000 # All sonnet agents use 150k
  openai/gpt-4o: 100000 # All gpt-4o agents use 100k
```

**Use cases:**

- Reduce context usage to control costs
- Account for system prompt overhead
- Use custom models with non-standard context windows

### `shared_files`

**Type:** `object` (map of name to config)
**Default:** `{}`
**Required:** No

Defines files that all agents should read before starting their task. These provide cross-agent context like implementation logs, coding standards, or step-specific notes.

#### Shared File Configuration

##### `path`

**Type:** `string`
**Required:** Yes

Path to the shared file. Supports variables:

- `{flow_dir}` - Flow directory from config (default: `flows`)
- `{flow_name}` - Name of the current flow
- `{plan_name}` - Plan name from `--plan-path` (stem without extension)
- `{specs_folder}` - Specs folder path (default: `specs`)
- `{step}` - Current step number (if `--step` provided at spawn)

##### `info`

**Type:** `string`
**Required:** Yes

Description of the file's purpose. This is included in the protocol prefix to help agents understand why they should read the file.

##### `writable`

**Type:** `boolean`
**Default:** `false`
**Required:** No

If true, agents must append to this file before signaling completion.

##### `write_template`

**Type:** `string`
**Required:** No (required if `writable: true`)

Template for what agents should append. Can be:

- Inline string (multi-line YAML)
- File reference: `@path/to/template.md`

Supports template variables: `{flow_dir}`, `{step}`, `{step_title}`, `{agent_name}`, `{agent_type}`, `{flow_name}`, `{plan_name}`, `{specs_folder}`, `{timestamp}`

##### `agents`

**Type:** `list[string]`
**Default:** `null` (include for all agents)
**Required:** No

List of agent type names that should receive this shared file. When omitted or empty, the file is included for all agent types.

When specified, only the listed agent types will have this file included in their protocol prefix. Agent names must match keys defined in the `agents` config section.

**Validation:** Config loading fails if any agent name in the list doesn't exist in the `agents` section.

#### Shared Files Example

```yaml
shared_files:
  implementation_log:
    path: "{flow_dir}/{flow_name}/implementation-log.md"
    info: "Accumulated decisions and context from previous steps."
    writable: true
    write_template: "@.flow/templates/implementation-log-template.md"
    # No agents field = included for ALL agents

  coding_standards:
    path: ".claude/docs/coding-standards.md"
    info: "Project coding standards and conventions. Follow these patterns."
    # No writable = read-only (default)
    # No agents = available to all agents

  builder_guidelines:
    path: ".claude/docs/builder-guidelines.md"
    info: "Guidelines specific to builder agents."
    agents: # Only these agent types receive this file
      - builder
      - frontend-builder

  reviewer_checklist:
    path: ".claude/docs/reviewer-checklist.md"
    info: "Checklist for code review agents."
    agents:
      - reviewer

  step_notes:
    path: "{flow_dir}/{flow_name}/step-{step}-notes.md"
    info: "Notes specific to the current step."
    writable: true
    write_template: |
      ## Notes for Step {step}

      [Your observations and context for this step]
```

#### Additional Agent Configuration Notes

The `model`, `system_prompt`, and `mcp_config` fields are documented in detail in the [Agent Configuration](#agent-configuration) section above. This section provides additional context for configuring agents within the `shared_files` section.

## Examples

### Minimal Configuration

If you only need defaults, you can use a minimal config:

```yaml
agents:
  builder:
    system_prompt: .claude/agents/builder.md
```

### Full-Stack Development Setup (Claude Code CLI)

For a multi-agent development workflow with builders, testers, and reviewers using Claude Code CLI:

```yaml
agent_interface: claude_code_cli
flow_dir: flows

defaults:
  stuck_timeout_secs: 1200
  max_wait_secs: 3600

agents:
  builder:
    model: anthropic/opus
    system_prompt: .claude/agents/builder.md
    mcp_config: .mcp-dev.json

  plan-step-tester:
    model: anthropic/sonnet
    system_prompt: .claude/agents/plan-step-tester.md
    mcp_config: .mcp-dev.json

  web-reviewer:
    model: anthropic/sonnet
    system_prompt: .claude/agents/web-reviewer.md

  backend-reviewer:
    model: anthropic/sonnet
    system_prompt: .claude/agents/backend-reviewer.md

  supabase-reviewer:
    model: anthropic/sonnet
    system_prompt: .claude/agents/supabase-reviewer.md
```

### OpenCode Setup

For using OpenCode with per-agent configuration:

```yaml
agent_interface: opencode
flow_dir: flows

defaults:
  stuck_timeout_secs: 1200
  max_wait_secs: 3600

opencode:
  server_hostname: 127.0.0.1

agents:
  builder:
    model: anthropic/claude-sonnet-4-5
    system_prompt: .claude/agents/builder.md
    mcp_config: .mcp-dev.json

  plan-reviewer:
    model: anthropic/claude-haiku-4-5
    system_prompt: .claude/agents/plan-reviewer.md
```

### Quick Prototyping Setup

For fast iteration with lighter models:

```yaml
defaults:
  stuck_timeout_secs: 600 # 10 minutes
  max_wait_secs: 1800 # 30 minutes

agents:
  quick-builder:
    model: anthropic/haiku
    system_prompt: .claude/agents/builder.md
```

## Validation

You can validate your configuration file using the `flow config` command:

```bash
# Display current configuration
flow config

# Validate configuration and show any errors
flow config --validate
```

## Related Documentation

- [USAGE_OVERVIEW.md](USAGE_OVERVIEW.md) - Complete command reference and workflows
- [AGENT_PROTOCOL.md](AGENT_PROTOCOL.md) - How agents communicate with the flow system
- [README.md](README.md) - Quick start guide
