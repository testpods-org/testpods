# TestPods Agent — Agent Instructions

This plugin provides a team of TestPods expert agents for planning, building,
and reviewing implementations in the TestPods Kubernetes testing library.

Read this file before performing any TestPods development tasks in a project using this plugin.

---

## Available Agents

<!-- TODO: Define the agent team instructions here.
     Reference the existing agents in .claude/agents/ (planner, builder, reviewer)
     for patterns and conventions specific to the TestPods codebase. -->

### Planner

Plans implementation work for TestPods features and changes.

### Builder

Implements code changes following spec plans for TestPods.

### Reviewer

Reviews code changes for TestPods implementations.

---

## Workflow Rules

<!-- TODO: Define workflow rules for the agent team. -->

1. **Read specs before implementing** — understand the full context of a change before writing code.
2. **Follow existing patterns** — the TestPods codebase has established conventions; follow them.
3. **Test changes** — verify implementations work against a running Kubernetes cluster when possible.
