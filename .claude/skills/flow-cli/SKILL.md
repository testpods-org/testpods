---
name: FlowCLI
description: Multi-agent orchestration toolkit for structured workflows with file-based traceability, spec-plan-driven execution, and complete visibility into agent operations.
---

# FlowCLI Skill

Use this skill when working with Flow CLI multi-agent orchestration - writing orchestration scripts, configuring flows, debugging agent issues, or understanding the Flow system.

> **New to Flow CLI?** Start with [GETTING_STARTED.md](GETTING_STARTED.md) or use the kick-starter prompt printed after `flow scaffold`.

> **CRITICAL**: Always run `.claude/skills/flow-cli/scripts/validate_flow_orchestration.py` on any flow orchestration script you create or edit. This ensures the script has required UV metadata and --dry-run flag.

## Quick Reference

### Essential Commands

| Command          | Description                            |
| ---------------- | -------------------------------------- |
| `flow scaffold`  | Set up .flow/ folder for a new project |
| `flow init`      | Initialize a new flow                  |
| `flow run`       | Spawn an agent and wait (most common)  |
| `flow spawn`     | Spawn agent asynchronously             |
| `flow await`     | Wait for agent completion              |
| `flow status`    | Show flow status                       |
| `flow dashboard` | Live terminal dashboard                |

### Core Pattern

```
spawn → monitor → complete
```

1. **Spawn** agents with task input and configuration
2. **Monitor** via dashboard or await commands
3. **Complete** when agents rename report to `.complete.md` or `.failed.md`

### Config Location

Flow configuration lives in `.flow/<flow-type>/base.yaml` (e.g., `.flow/implement/base.yaml`).

## Documentation (Progressive Disclosure)

Use the appropriate documentation based on your current task:

### Writing Orchestration Scripts

When building Python scripts that orchestrate multiple agents:

→ **Read `docs/api_docs.md`** for Python API reference (Flow class, AgentInfo, result types)
→ **See `examples/.flow/scripts/flows/implement.py`** for a complete working implementation workflow
→ **See `examples/.flow/scripts/flows/plan.py`** for a planning workflow example
→ **See `examples/.flow/scripts/flows/lib/`** for reusable orchestration utilities:

- `review_loop.py` - Build-review-fix loop pattern
- `validation_utils.py` - Running lint/type/test validation
- `git_utils.py` - Git commit operations
- `orchestration_utils.py` - InfoTracker and flow utilities

### Debugging Flow Issues

When troubleshooting agent behavior, stuck agents, or flow failures:

→ **Read `docs/cli_docs.md`** for CLI commands and options
→ **Read `docs/AGENT_PROTOCOL.md`** for how agents receive instructions and signal completion
→ **Check `flows/<flow-name>/flow.log`** for detailed orchestration logs
→ **Check `flows/<flow-name>/agents/<agent-id>/system_prompt.md`** to see exactly what the agent received

### Configuring Flows

When setting up or modifying flow configuration:

→ **Read `docs/FLOW_CONFIG_FORMAT.md`** for complete configuration options including:

- Agent definitions (model, system_prompt, mcp_config)
- Shared files (read-only and writable)
- Timeout settings
- Spec plan parsing
- Completion workflow templates

### Working with Learnings System

When analyzing flow executions or submitting feedback:

→ **Read `docs/LEARNINGS_SYSTEM.md`** for:

- Submitting human feedback via `flow feedback`
- Viewing learnings via `flow learnings`
- Running automated analysis
- Pattern detection with aggregator

### Working with Proof System

When generating evidence-backed documentation:

→ **Read `docs/proof/README.md`** for:

- Evidence collection from CLI demos
- Proof narrative generation
- Integration with spec plans

### Bootstrapping Flow CLI in a Project

When first setting up Flow CLI in a new project, or when users ask for help getting started with flow configuration:

→ **Read [GETTING_STARTED.md](GETTING_STARTED.md)** and follow the workflow to interview the user and create project-specific configuration.

This guide walks through discovering existing agents, creating new ones if needed, and generating `.flow/` configuration tailored to the specific project.

### General Reference

For overall understanding of the Flow CLI system:

→ **Read `docs/README.md`** for project overview and quick start
→ **Read `docs/USAGE_OVERVIEW.md`** for common workflows and usage patterns

## Examples Location

The `examples/` directory mirrors a real project's `.flow/` structure with literal copies from the flow-cli project:

```
examples/
└── .flow/
    ├── base.yaml                    # Project-wide defaults
    ├── implement/
    │   └── base.yaml                # Implementation flow config
    ├── plan/
    │   └── base.yaml                # Planning flow config
    └── scripts/flows/
        ├── implement.py             # Implementation workflow
        ├── plan.py                  # Planning workflow
        ├── run_learnings.py         # Standalone learnings analysis
        ├── run_proof.py             # Standalone proof generation
        ├── implement_learnings_proof.py  # Full workflow orchestrator
        └── lib/                     # Reusable utilities
```

See `examples/README.md` for detailed documentation on how to use these examples.

### Key Patterns in Examples

The example scripts demonstrate:

- **Multi-step plan parsing** - Extract steps from spec plans with status markers
- **Build → Validate → Review cycles** - Iterative quality assurance
- **Resume capability** - Using `has_completed_agent()` and `get_last_agent_id()`
- **Validation integration** - Running lint, type check, and tests between steps
- **Review loops** - Configurable reviewer/fixer patterns with approval markers
- **Git integration** - Automatic commits after approved changes
- **Error handling** - Proper status management and cleanup on failure

### Spec Plan Parsing API

**IMPORTANT:** Use these helper functions from `flow.lib.spec_parser` - do NOT invent methods on the `Flow` class.

```python
from flow.lib.spec_parser import get_all_steps, get_pending_steps, mark_step_completed

# Get all steps (for total count)
all_steps = get_all_steps(plan_path, flow.config)
# Returns: [(1, "Step title"), (2, "Another step"), ...]

# Get only pending steps (not marked completed)
pending_steps = get_pending_steps(plan_path, flow.config)

# Mark a step completed in the plan file (❌ → ✅)
mark_step_completed(plan_path, step_number)
```

Common pattern:

```python
all_steps = get_all_steps(plan_path, flow.config)
flow.set_total_steps(len(all_steps))

for step_number, step_title in get_pending_steps(plan_path, flow.config):
    result = flow.run("builder", agent=AgentInfo(step=step_number, role="builder"), ...)
    mark_step_completed(plan_path, step_number)
    flow.mark_step_completed(step_number)
```

## Updating This Skill

To update this skill file to the latest version from the Flow CLI repository:

```bash
flow scaffold --update-skill
```

This fetches the latest SKILL.md from the main branch and updates `.claude/skills/flow-cli/SKILL.md` with new features, documentation references, and examples.

## Tips

### Orchestration Script Requirements

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

Always validate scripts with:

```bash
uv run .claude/skills/flow-cli/scripts/validate_flow_orchestration.py path/to/script.py
```

### For Orchestration Scripts

1. **Use context manager** for automatic cleanup:

   ```python
   with Flow("my-flow", flow_type="implement") as flow:
       # Agents are cleaned up on exit or interrupt
   ```

2. **Design for resume** - Check `has_completed_agent()` before running agents

3. **Chain agents** with `after` parameter to pass context:

   ```python
   flow.run("reviewer", ..., after=builder_result.final_agent)
   ```

4. **Use `auto_retry`** for resilience against stuck agents

### For Configuration

1. **Use hierarchical configs** - Base config inherits, variants override
2. **Template variables** in paths: `{flow_dir}`, `{flow_name}`, `{step}`
3. **Shared files** provide consistent context across agents

### For Debugging

1. **Check system_prompt.md** - See exactly what agent received
2. **Read flow.log** - Detailed timestamped orchestration events
3. **Use `flow dashboard`** - Real-time visibility into all flows
