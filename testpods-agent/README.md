# TestPods Agent

A Claude Code plugin that provides expert agents for the TestPods Kubernetes testing library.

The plugin delivers a team of specialized agents (planner, builder, reviewer) that understand
TestPods conventions, architecture, and testing patterns.

---

## Prerequisites

- [Claude Code](https://claude.ai/code)

---

## Installation

Install from the testpods-org marketplace:

```bash
# Add the marketplace (one time)
/plugin marketplace add testpods-org/claude-plugins

# Install the plugin
/plugin install testpods-agent@testpods-org
```

Then in the testpods project, start Claude Code:

```bash
cd testpods
claude
```

---

## Repository Structure

```
testpods-agent/
├── plugin/                  <- distributed plugin (what users install)
│   ├── .claude-plugin/
│   │   └── plugin.json      Plugin manifest
│   ├── AGENTS.md            Agent instructions
│   ├── hooks/               Hooks (placeholder)
│   └── scripts/             Scripts (placeholder)
├── scripts/release/         <- release automation scripts
├── tests/                   <- pytest test suite
├── justfile                 <- repo management recipes (test, release)
├── plugin.justfile          <- plugin runtime recipes
├── pyproject.toml           <- Python project config
└── CHANGELOG.md             <- release history
```

The `plugin/` subdirectory is the installable plugin. Everything outside it is development tooling and is not distributed to users.

---

## Development

Prerequisites for development:
- [uv](https://docs.astral.sh/uv/) -- for running Python scripts and tests
- [just](https://github.com/casey/just) (v1.19+) -- command runner
- [jq](https://jqlang.github.io/jq/) -- used by release scripts

```bash
just test         # run the pytest test suite
```

---

## Releasing

This plugin lives inside the testpods repo, so release tags use a `testpods-agent/v` prefix
(e.g., `testpods-agent/v0.1.0`).

The marketplace repo is expected at `../../java-expert-agent-project/claude-plugins`.

```bash
# 1. Check current version and commits since last release
just release-status

# 2. Prepare the release: bumps versions, generates changelog, commits, tags,
#    and updates the marketplace repo
just release 0.2.0

# 3. Review the changes before pushing
git log --oneline -3

# 4. Push both repos (testpods with tags + marketplace)
just release-push
```
