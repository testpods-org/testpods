---
name: FlowCLI
description: Multi-agent orchestration toolkit for structured workflows with file-based traceability, spec-plan-driven execution, and complete visibility into agent operations.
---

# FlowCLI Skill

Use this skill when working with Flow CLI multi-agent orchestration - writing orchestration scripts, configuring flows, debugging agent issues, or understanding the Flow system.

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
→ **See `examples/flows/implement.py`** for a complete working implementation workflow
→ **See `examples/flows/plan.py`** for a planning workflow example
→ **See `examples/flows/lib/`** for reusable orchestration utilities:

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

### General Reference

For overall understanding of the Flow CLI system:

→ **Read `docs/README.md`** for project overview and quick start
→ **Read `docs/USAGE_OVERVIEW.md`** for common workflows and usage patterns

## Examples Location

All runnable orchestration examples are in `examples/flows/`:

| File                           | Description                                           |
| ------------------------------ | ----------------------------------------------------- |
| `implement.py`                 | Full implementation workflow with build/review/commit |
| `plan.py`                      | Planning workflow with clarifications and review      |
| `run_learnings.py`             | Standalone learnings analysis                         |
| `run_proof.py`                 | Standalone proof generation                           |
| `implement_learnings_proof.py` | Full workflow orchestrator (implement + post-tasks)   |
| `lib/`                         | Reusable orchestration utilities                      |

### Key Patterns in Examples

The example scripts demonstrate:

- **Multi-step plan parsing** - Extract steps from spec plans with status markers
- **Build → Validate → Review cycles** - Iterative quality assurance
- **Resume capability** - Using `has_completed_agent()` and `get_last_agent_id()`
- **Validation integration** - Running lint, type check, and tests between steps
- **Review loops** - Configurable reviewer/fixer patterns with approval markers
- **Git integration** - Automatic commits after approved changes
- **Error handling** - Proper status management and cleanup on failure

## Updating This Skill

To update this skill file to the latest version from the Flow CLI repository:

```bash
flow scaffold --update-skill
```

This fetches the latest SKILL.md from the main branch and updates `.claude/skills/flow-cli/SKILL.md` with new features, documentation references, and examples.

## Tips

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
