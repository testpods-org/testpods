# TestPods Agent Plugin — Claude Code Instructions

This is the **plugin subdirectory** for the TestPods Agent.
It lives inside the testpods repo at `testpods-agent/` and is not a standalone git repo.

---

## Repository Structure

```
plugin/                      Distributable plugin (installed via marketplace)
  .claude-plugin/
    plugin.json              Plugin manifest
  AGENTS.md                  Agent instructions — read by Claude Code during TestPods sessions
  hooks/                     Hooks directory (placeholder)
  scripts/                   Scripts directory (placeholder)

justfile                     Development command runner — `just` to see all recipes
plugin.justfile              Plugin runtime recipes (placeholder)
tests/                       pytest test suite
pyproject.toml               Python project config (pytest, pythonpath)
CHANGELOG.md                 Plugin release history
```

Only the `plugin/` directory is distributed to users. Everything else is development tooling.

---

## Development

Use `just` for all development tasks (run `just` to see available recipes):

```bash
just test          # run pytest suite
```

---

## Releasing

Tags use the `testpods-agent/v*` prefix (e.g., `testpods-agent/v0.1.0`) since this
is a subdirectory of the testpods repo, not a standalone repo.

The marketplace repo is at `../../java-expert-agent-project/claude-plugins`.

```bash
just release-status          # check current version
just release 0.2.0           # prepare a release
just release-push            # push after review
```

---

## Testing the Plugin Locally

```bash
claude --plugin-dir testpods-agent/plugin
```
