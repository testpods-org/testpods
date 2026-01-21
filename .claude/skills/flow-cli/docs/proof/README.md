# Proof Generation Package

A standalone package for evidence collection and proof document generation. Designed to produce clear, technical documentation that demonstrates implementation completeness.

## Purpose and Audience

This package helps engineers produce concrete proof that implementations work as intended. It is designed for technical audiences (engineers, scientists) who value:

- Technical accuracy over persuasion
- Concrete evidence over assertions
- Honesty about limitations over promotion of benefits
- Neutral, factual language over enthusiasm

## Architecture

The proof package logic is standalone with no dependencies on the flow package.
However, when using it in practice, it will be beneficial to use Flow CLI for the orchestration. The dependency direction then is:

```
proof → flow (proof orchestration uses Flow API)
flow → proof (never - flow package does not import proof)
```

### Package Structure

```
proof/
├── __init__.py           # Public API exports
├── api.py                # ProofCollector class (main API)
├── cli.py                # Standalone proof CLI
├── types.py              # Pydantic models for specs and evidence
├── showcase.py           # Showcase spec parsing
├── schemas/
│   └── evidence_schema.py # Pydantic models for evidence.json validation
├── scripts/
│   └── validate_evidence.py # Standalone evidence.json validation script
├── collectors/
│   ├── cli_collector.py  # CLI evidence collection (asciinema)
│   └── web_collector.py  # Web evidence collection (Playwright - stub)
├── generators/
│   └── html_proof.py     # HTML proof document generation
├── orchestration/
│   ├── generate_proof.py # Multi-agent orchestration script
│   └── agents/           # Agent prompt files
└── resources/
    └── showcase.yaml     # Scaffold template
```

## Installation

The proof package is included with flow-cli. No additional installation required for CLI evidence collection.

```bash
# Standard installation
uv sync

# For web evidence collection (requires Playwright)
uv sync --extra proof-web
playwright install
```

### System Dependencies

For CLI recordings:

- **asciinema** - Terminal recording (`brew install asciinema` / `apt install asciinema`)

For web evidence (optional):

- **Playwright** - Browser automation (installed via `uv sync --extra proof-web`)

## CLI Usage

The `proof` command provides four subcommands:

### `proof scaffold`

Scaffold the proof system structure for your project:

**Network Requirement:** The scaffold command fetches template files from GitHub at runtime, ensuring you always get the latest versions. An active internet connection to `raw.githubusercontent.com` is required.

**Private Repository Authentication:** If scaffolding from a private repository, authentication is handled automatically using (in order of priority): `GITHUB_TOKEN` environment variable, `GH_TOKEN` environment variable, or GitHub CLI token (from `gh auth login`).

```bash
# Scaffold the proof system structure
proof scaffold

# Overwrite existing files
proof scaffold --force

# Preview what would be created without creating files
proof scaffold --dry-run
```

The `--dry-run` option shows what files would be created without actually creating them. This is useful for previewing the scaffold operation before committing to it.

The scaffold command creates the following files:

- `.flow/proof/schemas/evidence_schema.py` - Pydantic models for evidence validation
- `.flow/proof/scripts/validate_evidence.py` - Standalone validation script
- `.flow/proof/base.yaml` - Proof flow configuration
- Agent instruction files (narrative-writer, fact-checker, showcase-planner)

**Note:** Showcase requirements are defined inline in your spec plans under a `## Showcase Requirements` section, rather than in a separate YAML file. See [Showcase Spec Embedded in Spec Plans](#showcase-spec-embedded-in-spec-plans) for details.

### `proof collect`

Collect evidence based on a showcase specification:

```bash
# From standalone YAML file
proof collect --showcase showcase.yaml

# From markdown spec plan with embedded showcase section
proof collect --showcase specs/my-plan.md

# With custom output directory
proof collect --showcase showcase.yaml --output ./evidence

# With flow metadata
proof collect --showcase showcase.yaml --flow-name my-impl
```

### `proof generate`

Generate a proof document from collected evidence:

```bash
# Generate proof.html from evidence.json
proof generate --evidence ./proof/evidence.json

# Custom output path
proof generate --evidence ./proof/evidence.json --output ./output/proof.html
```

### `proof run`

Collect evidence and generate proof in one step:

```bash
# Complete workflow
proof run --showcase showcase.yaml

# With all options
proof run --showcase showcase.yaml --output ./proof --flow-name my-impl
```

## Python API Usage

### Basic Usage

```python
from proof import ProofCollector, ShowcaseSpec, CLIDemoSpec, CLICommandSpec

# Create a showcase spec programmatically
spec = ShowcaseSpec(
    project_type="cli",
    description="Demonstrate my CLI tool",
    cli_demos=[
        CLIDemoSpec(
            name="help_command",
            description="Shows help is available",
            commands=[
                CLICommandSpec(
                    cmd="my-tool --help",
                    expect_exit_code=0,
                    expect_pattern="Usage:",
                )
            ]
        )
    ]
)

# Collect evidence
collector = ProofCollector(output_dir="./proof")
evidence = collector.collect(spec)

# Generate proof document
proof_path = collector.generate_narrative(evidence)
print(f"Proof generated: {proof_path}")
```

### Loading from YAML

```python
from proof import ProofCollector, load_showcase_yaml

# Load spec from file
spec = load_showcase_yaml("showcase.yaml")

# Collect and generate
collector = ProofCollector(output_dir="./proof")
proof_path = collector.run(spec)
```

### Integration with Flow Artifacts

```python
import json
import subprocess
from proof import ProofCollector

# Get flow artifacts
result = subprocess.run(
    ["flow", "artifacts", "--flow", "my-impl", "--json"],
    capture_output=True,
    text=True
)
artifacts = json.loads(result.stdout)

# Collect base evidence first
collector = ProofCollector(output_dir="./proof")
evidence = collector.collect(spec)

# Enrich with flow artifacts (adds metadata like timing, agents, commits)
enriched = collector.enrich_from_flow_artifacts(evidence, artifacts)

# Generate proof
proof_path = collector.generate_narrative(enriched)
```

## Showcase Spec Format

The showcase specification defines what demonstrations to run for evidence collection.

### YAML Format

```yaml
# Required: Type of project (cli, web, hybrid)
project_type: cli

# Optional: Description of what to showcase
description: "Demonstration of authentication feature"

# CLI demonstrations
cli_demos:
  - name: help_command
    description: "Shows help is available"
    commands:
      - cmd: "my-cli --help"
        expect_exit_code: 0
        expect_pattern: "Usage:"
        timeout_secs: 30
        record: false

  - name: login_flow
    description: "Demonstrates login workflow"
    record: true # Creates demo-level asciinema recording
    commands:
      - cmd: "my-cli login --user test"
        expect_exit_code: 0
        expect_pattern: "Login successful"
        delay_before_secs: 0.5 # Pause before command for human viewing
        delay_after_secs: 1.0 # Pause after command to show output

# Web captures (requires Playwright) - list of capture configurations
web_captures:
  - base_url: "http://localhost:3000"
    start_command: "npm run dev"
    start_wait_secs: 10

    pages:
      - name: homepage
        url: "/"
        screenshot: true
        performance: true
        accessibility: false

    flows:
      - name: login_flow
        description: "User login journey"
        video: true
        steps:
          - action: navigate
            target: "/login"
          - action: fill
            target: "#username"
            value: "testuser"
          - action: click
            target: "#submit"
          - action: wait_for
            target: "#dashboard"
            state: visible
          - action: screenshot
            target: "login_complete"

    performance_thresholds:
      lcp_ms: 2500
      fcp_ms: 1800
      tti_ms: 3800
      cls: 0.1
```

### Embedded in Markdown

Showcase specs can be embedded in spec plan markdown files:

````markdown
# My Feature Plan

## Overview

This plan implements...

## Steps

1. Create types...
2. Implement logic...

## Showcase Requirements

The following demonstrations showcase the completed implementation:

```yaml
project_type: cli
cli_demos:
  - name: feature_demo
    description: "Shows the feature works"
    commands:
      - cmd: "my-cli feature --input test.txt"
        expect_exit_code: 0
        expect_pattern: "Processed"
```
````

Use `parse_showcase_section()` to extract the spec from markdown:

```python
from proof import parse_showcase_section, has_showcase_section
from pathlib import Path

spec_path = Path("specs/my-plan.md")

# Quick check if section exists
if has_showcase_section(spec_path):
    spec = parse_showcase_section(spec_path)
    if spec:
        # Use spec...
```

## Multi-Agent Orchestration

The proof package includes a multi-agent orchestration script for generating comprehensive proofs.

### Workflow

The proof generation workflow consists of two phases:

**Phase 1: Deterministic Evidence Collection (Python)**

Evidence collection is performed by Python code (not an agent), ensuring reliable and reproducible results:

- Parses the showcase spec from the plan file
- Executes CLI demos via `CLICollector`, creating asciinema recordings when `record: true`
- Writes validated `evidence.json` using the `EvidenceReport` Pydantic schema
- Configurable delays between commands for human-viewable recordings

**Phase 2: Agent-Based Narrative Generation**

The orchestration then runs 2 agents in sequence:

1. **narrative-writer** - Reads `evidence.json`, synthesizes clear technical report from evidence (outputs proof.md)
2. **fact-checker** - Validates claims in proof.md against evidence, ensures consistency

**Phase 3: Deterministic HTML Conversion (Python)**

After fact-checking, the validated markdown is converted to HTML:

- Converts markdown to HTML using python-markdown (tables, fenced_code, toc extensions)
- Embeds .cast recordings as interactive asciinema players (for CLI-based evidence) and HTML image/video elements for web-app demonstrations
- Applies professional styling for typography and code blocks
- Produces self-contained `proof.html` as final output

### Running Orchestration

```bash
# Generate proof for a completed flow
./proof/orchestration/generate_proof.py --flow-name implement_my-feature

# With custom output directory
./proof/orchestration/generate_proof.py --flow-name impl-auth --output ./my-proof

# With existing showcase spec
./proof/orchestration/generate_proof.py --flow-name impl-auth --showcase showcase.yaml

# Reset and regenerate
./proof/orchestration/generate_proof.py --flow-name impl-auth --reset

# With additional context
./proof/orchestration/generate_proof.py --flow-name impl-auth --info "Focus on CLI demos"
```

### Flow Configuration

The orchestration uses `.flow/proof/base.yaml` for agent configuration:

```yaml
agent_interface: claude_code_cli
flow_dir: flows

# Note: Evidence collection is now done deterministically in Python code
# (proof_runner.collect_evidence), so no evidence-collector agent is needed.

agents:
  showcase-planner:
    model: anthropic/sonnet
    system_prompt: .flow/proof/agents/showcase-planner.md

  narrative-writer:
    model: anthropic/sonnet
    system_prompt: .flow/proof/agents/narrative-writer.md

  fact-checker:
    model: anthropic/sonnet
    system_prompt: .flow/proof/agents/fact-checker.md
```

## Output Structure

Evidence collection creates the following structure:

```
proof/
├── evidence.json           # Structured evidence index
├── cli/
│   ├── help-output.json    # Command output + timing
│   ├── help-output.cast    # asciinema recording
│   └── ...
├── web/
│   ├── homepage.png        # Screenshots
│   ├── login-flow.webm     # Video recordings
│   ├── performance.json    # Core Web Vitals
│   └── ...
└── proof.html              # Final HTML proof document
```

### Evidence JSON Schema

The `evidence.json` file follows the `EvidenceReport` Pydantic schema (defined in `.flow/proof/schemas/evidence_schema.py`):

```json
{
  "collected_at": "2025-01-15T14:30:00Z",
  "flow_name": "implement_auth-feature",
  "showcase_spec": { "...": "original ShowcaseSpec" },
  "cli_evidence": [
    {
      "name": "help_command",
      "description": "Shows help is available",
      "commands": [
        {
          "command": "my-cli --help",
          "exit_code": 0,
          "stdout": "Usage: my-cli [OPTIONS]...",
          "stderr": "",
          "duration_ms": 125.5,
          "expected_pattern": "Usage:",
          "pattern_matched": true,
          "recording_path": "cli/help-output.cast"
        }
      ],
      "recording_path": null,
      "passed": true,
      "failure_reason": null
    }
  ],
  "web_evidence": [],
  "summary": {
    "total_demos": 1,
    "passed_demos": 1,
    "failed_demos": 0,
    "total_commands": 1,
    "passed_commands": 1,
    "failed_commands": 0
  },
  "errors": []
}
```

**Schema Hierarchy:**

- `EvidenceReport` (top-level)
  - `cli_evidence`: list of `DemoEvidence`
    - `commands`: list of `CommandEvidence` (with separate stdout/stderr)
  - `web_evidence`: list of `WebEvidence`
  - `summary`: `EvidenceSummary` (aggregate statistics)

You can validate evidence files using the standalone script:

```bash
./.flow/proof/scripts/validate_evidence.py evidence.json
```

## Anti-Hype Guidelines

All generated output follows these guidelines for an engineer/scientist audience:

### DO NOT use

- Emojis of any kind
- Celebratory language ("successfully", "great", "amazing", "powerful")
- Marketing language ("revolutionizes", "game-changing", "seamlessly")
- Hyperbole ("incredibly", "extremely", "very" as intensifiers)
- Unnecessary adjectives ("robust", "elegant", "beautiful")

### DO use

- Precise technical terminology
- Quantitative metrics where available
- Explicit tradeoff descriptions
- Acknowledgment of what doesn't work or wasn't implemented
- "This approach was chosen because..." (with rationale)
- "Alternatives considered: X (rejected because Y)"
- "Limitations: ..."

### Example Transformations

| Instead of                               | Use                                                                   |
| ---------------------------------------- | --------------------------------------------------------------------- |
| "Successfully implemented amazing auth!" | "Authentication implemented. Supports JWT and session tokens."        |
| "Robust error handling"                  | "Error handling covers: invalid input, network timeout, auth failure" |
| "Seamlessly integrates with..."          | "Integrates with X via REST API. Requires API key configuration."     |
| "Incredibly fast performance"            | "Response time: 45ms avg, 120ms p99"                                  |

## API Reference

### Classes

#### `ProofCollector`

Main API for evidence collection and proof generation.

```python
collector = ProofCollector(
    output_dir: Path | str,           # Where to write evidence/proof
    project_root: Path | str = None,  # Root for command execution
)

# Collect evidence
evidence = collector.collect(
    showcase_spec: ShowcaseSpec | Path | str,  # Spec or path to YAML
    metadata: dict[str, str] | None = None,    # Optional metadata
) -> EvidenceReport

# Generate HTML proof document
proof_path = collector.generate_narrative(
    evidence: EvidenceReport,
    output_path: Path | str | None = None,  # Defaults to output_dir/proof.html
) -> Path

# Convenience: collect + generate
proof_path = collector.run(showcase_spec, metadata) -> Path

# Enrich evidence with flow artifacts (adds timing, agents, commits)
enriched = collector.enrich_from_flow_artifacts(
    evidence: EvidenceReport,              # Existing evidence to enrich
    artifacts_json: dict[str, object],     # Flow artifacts JSON
) -> EvidenceReport                        # Returns enriched copy
```

### Functions

#### Showcase Parsing

```python
from proof import load_showcase_yaml, parse_showcase_section, has_showcase_section

# Load from YAML file
spec = load_showcase_yaml(path: Path) -> ShowcaseSpec

# Parse from markdown with embedded section
spec = parse_showcase_section(path: Path) -> ShowcaseSpec | None

# Check if markdown has showcase section
exists = has_showcase_section(path: Path) -> bool
```

#### CLI Evidence Collection

The CLI collector provides functions for collecting demo evidence:

```python
from proof.collectors.cli_collector import (
    collect_all,
    collect_demo,
    check_asciinema_installed,
)
from proof.schemas.evidence_schema import DemoEvidence

# Check tool availability
has_asciinema = check_asciinema_installed() -> bool

# Collect all demos from spec - returns list[DemoEvidence]
demo_evidence_list = collect_all(
    spec: ShowcaseSpec,
    output_dir: Path,
    cwd: Path,
) -> list[DemoEvidence]

# Collect single demo - returns DemoEvidence
demo_evidence = collect_demo(
    demo: CLIDemoSpec,
    output_dir: Path,
    cwd: Path,
) -> DemoEvidence
```

#### Web Evidence Collection

The web collector provides a class-based API for collecting web captures:

```python
from proof.collectors.web_collector import WebCollector, check_playwright_installed

# Check if Playwright is available
has_playwright = check_playwright_installed() -> bool

# Create collector with output directory
collector = WebCollector(output_dir: Path)

# Collect web evidence from spec - returns list[WebEvidence]
# Currently raises NotImplementedError (Playwright integration pending)
web_evidence = collector.collect(spec: ShowcaseSpec) -> list[WebEvidence]
```

Note: Web evidence collection requires Playwright. If Playwright is not installed, a `WebCollectorError` is raised with installation instructions.

#### HTML Proof Generation

```python
from proof import generate_proof_html

# Generate HTML proof document from evidence
html = generate_proof_html(evidence: EvidenceReport) -> str
```

### Types

All types use Pydantic with `model_config = {"extra": "forbid"}` to catch typos.

#### Showcase Specification Types

- `ShowcaseSpec` - Top-level specification
- `CLIDemoSpec` - CLI demonstration scenario
- `CLICommandSpec` - Single CLI command
- `WebCaptureSpec` - Web capture configuration
- `WebPageSpec` - Web page to capture
- `WebFlowSpec` - Web user flow
- `WebFlowStepSpec` - Single step in a web flow
- `PerformanceThresholds` - Performance metric thresholds

#### Evidence Types

Located in `.flow/proof/schemas/evidence_schema.py`:

- `EvidenceReport` - Top-level evidence package (validated Pydantic model)
- `DemoEvidence` - Evidence from executing one demo (group of commands)
- `CommandEvidence` - Evidence from executing one CLI command (with separate stdout/stderr)
- `EvidenceSummary` - Aggregate statistics (pass/fail counts)

Located in `proof/types.py`:

- `WebEvidence` - Evidence from web capture

### Exceptions

- `ShowcaseParseError` - Raised when showcase spec loading fails
- `CLICollectorError` - Raised when CLI evidence collection fails
- `WebCollectorError` - Raised when web evidence collection fails

## Testing

```bash
# Run proof package tests
uv run pytest tests/unit/proof/ -v

# Run with coverage
uv run pytest tests/unit/proof/ --cov=proof --cov-report=term-missing
```

## Related Documentation

- [USAGE_OVERVIEW.md](../USAGE_OVERVIEW.md) - Flow CLI usage guide
- [AGENT_PROTOCOL.md](../AGENT_PROTOCOL.md) - Agent communication protocol
