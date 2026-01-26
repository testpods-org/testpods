# Flow CLI Examples

This directory contains the literal config files and orchestration scripts from the flow-cli project's `.flow/` folder. They serve as working reference implementations showing real-world usage of the Flow CLI system.

## Directory Structure

```
examples/
└── .flow/
    ├── base.yaml                    # Project-wide defaults (timeouts, flow_dir)
    ├── implement/
    │   └── base.yaml                # Implementation flow config (agents, shared_files)
    ├── plan/
    │   └── base.yaml                # Planning flow config
    └── scripts/flows/
        ├── implement.py             # Implementation workflow orchestrator
        ├── plan.py                  # Planning workflow orchestrator
        ├── run_learnings.py         # Standalone learnings analysis runner
        ├── run_proof.py             # Standalone proof generation runner
        ├── implement_learnings_proof.py  # Full workflow (implement + post-tasks)
        └── lib/                     # Reusable orchestration utilities
            ├── review_loop.py       # Build-review-fix loop pattern
            ├── validation_utils.py  # Running lint/type/test validation
            ├── git_utils.py         # Git commit operations
            ├── orchestration_utils.py  # InfoTracker and flow utilities
            └── ...
```

## How to Use These Examples

These examples are **reference implementations** - study them to understand how Flow CLI configurations and scripts work together. Do not copy them directly into your project; instead:

1. **Study the structure** - See how configs reference agents, shared_files, and templates
2. **Understand the patterns** - Note how orchestration scripts use the Flow API
3. **Use GETTING_STARTED.md** - Follow the guided workflow to create configs tailored to YOUR project

The [GETTING_STARTED.md](../GETTING_STARTED.md) workflow will interview you about your project's existing agents and documentation, then generate appropriate `.flow/` configuration.

## Key Patterns Demonstrated

### Configuration (`base.yaml` files)

- **Hierarchical configs**: `implement/base.yaml` inherits from `base.yaml` via `base_config:`
- **Agent definitions**: Model, system_prompt path, MCP config, declared artifacts
- **Shared files**: Read-only context (guides) and writable context (logs)
- **Template paths**: `@` prefix for file references, `{flow_dir}` variables

### Orchestration Scripts

- **Flow context manager**: `with Flow("name", flow_type="implement") as flow:`
- **Agent execution**: `flow.run("agent", agent=AgentInfo(...), input="...")`
- **Resume capability**: `has_completed_agent()` and `get_last_agent_id()`
- **Validation integration**: Running lint/type/test between steps
- **Review loops**: Configurable reviewer/fixer patterns

### Utility Libraries (`lib/`)

- **review_loop.py**: Generic build → review → fix loop with configurable agents
- **validation_utils.py**: Running and reporting validation command results
- **git_utils.py**: Safe git operations with proper error handling
- **orchestration_utils.py**: InfoTracker for status display, flow utilities

## Important Notes

- These configs reference paths like `@.claude/agents/builder.md` which are specific to the flow-cli project
- Your project will need different agent paths and shared_files
- The GETTING_STARTED.md workflow helps you create project-specific versions
