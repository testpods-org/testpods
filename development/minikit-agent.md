# Minikit Agent

Instructions for using the minikit CLI to manage local Kubernetes development nodes.

## Key Concepts

- **Node** = minikube instance
- **Cluster** = Kubernetes running inside the node
- `minikit up` works without configuration (uses sane defaults)
- Optional `Minikit` file (YAML, no extension) for customization

## Core Commands

### Start a Node

```bash
minikit up                    # Start with defaults or Minikit file
minikit up -f staging.yaml    # Use specific config
minikit up --wait             # Wait for node readiness
minikit up --provision        # Force re-provisioning
```

### Stop a Node

```bash
minikit down                  # Graceful shutdown
minikit down --force          # Force stop immediately
```

### Delete a Node

```bash
minikit destroy               # Delete with confirmation prompt
minikit destroy --force       # Delete without prompt
```

### Check Status

```bash
minikit status                # Show node state
minikit status --verbose      # Detailed information
```

### SSH into Node

```bash
minikit ssh                   # Open shell in node
```

### Restart with New Config

```bash
minikit reload                # Restart with updated Minikit file
minikit reload --provision    # Force re-provisioning on restart
```

## Configuration Commands

### Create Config File

```bash
minikit init                        # Create minimal Minikit file
minikit init --template=dev         # Use dev template
minikit init --template=ci          # Use CI template
minikit init --name=my-node         # Set node name
minikit init --force                # Overwrite existing file
```

Available templates: `minimal`, `dev`, `ci`, `multi-node`

### Validate Config

```bash
minikit validate              # Validate Minikit file syntax
minikit validate -f other.yaml
```

### View/Edit Config

```bash
minikit config get            # View current config
minikit config edit           # Edit Minikit file
minikit config set cpus 4     # Modify specific value
```

## Management Commands

### List All Nodes

```bash
minikit list                  # List minikit-managed nodes
minikit list --all            # Include all minikube profiles
```

### Switch Active Node

```bash
minikit switch my-project     # Switch kubectl context
```

## Utility Commands

### Run kubectl Commands

```bash
minikit kubectl -- get pods
minikit kubectl -- apply -f deployment.yaml
minikit kubectl -- logs pod-name
```

### Open Dashboard

```bash
minikit dashboard             # Open K8s dashboard in browser
minikit dashboard --port=9090 # Use custom port
```

### Manage Addons

```bash
minikit addons list           # List available addons
minikit addons enable ingress # Enable addon
minikit addons disable registry
```

### Check Versions

```bash
minikit version               # Show minikit, minikube, kubectl versions
```

## Advanced Commands

### Provisioning

```bash
minikit provision             # Run all provisioning scripts
minikit provision --script=setup.sh
```

### Snapshots

```bash
minikit snapshot save baseline      # Save current state
minikit snapshot list               # List snapshots
minikit snapshot restore baseline   # Restore state
minikit snapshot delete baseline    # Delete snapshot
```

## Default Configuration

When no `Minikit` file exists, these defaults apply:

| Setting | Default |
|---------|---------|
| name | Current directory name |
| driver | docker |
| cpus | 2 |
| memory | 4096 MB |
| disk | 20 GB |
| addons | metrics-server |

## Common Workflows

### Quick Start (No Config)

```bash
cd my-project
minikit up
minikit kubectl -- get nodes
```

### Team Project Setup

```bash
minikit init --template=dev
# Edit Minikit file as needed
minikit up
git add Minikit && git commit -m "Add node config"
```

### CI Pipeline

```bash
minikit up --wait
minikit kubectl -- apply -f manifests/
make test
minikit destroy --force
```

### Multi-Project

```bash
cd project-a && minikit up
cd project-b && minikit up
minikit list
minikit switch project-a
```
