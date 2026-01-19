# TestPods Class Hierarchy

## Package Structure

```
org.testpods.core/
├── TestPod.java                    # Interface - the contract
├── BaseTestPod.java                # Abstract - shared implementation
├── ExecResult.java                 # Record - command execution result
│
├── builder/
│   ├── InitContainerBuilder.java   # Mid-level API for init containers
│   └── SidecarBuilder.java         # Mid-level API for sidecars
│
├── cluster/
│   ├── K8sCluster.java             # Interface + implementations (Minikube, Kind, etc.)
│   ├── ExternalAccessStrategy.java # Interface + implementations (PortForward, NodePort, etc.)
│   └── HostAndPort.java            # Record - host:port value object
│
├── config/
│   └── PropertyContext.java        # Shared property registry for pod-to-pod config
│
├── namespace/
│   └── TestNamespace.java          # Kubernetes namespace wrapper
│
├── wait/
│   ├── WaitStrategy.java           # Interface with factory methods
│   ├── ReadinessProbeWaitStrategy.java  # Wait for K8s readiness probe
│   ├── LogMessageWaitStrategy.java      # Wait for log message pattern
│   ├── PortWaitStrategy.java            # Wait for TCP port open
│   ├── HttpWaitStrategy.java            # Wait for HTTP endpoint
│   ├── CommandWaitStrategy.java         # Wait for exec command success
│   └── CompositeWaitStrategy.java       # Combine multiple strategies
│
├── workload/
│   ├── StatefulSetPod.java         # Abstract - for StatefulSet-backed pods
│   └── DeploymentPod.java          # Abstract - for Deployment-backed pods
│
└── pod/
    ├── MongoDBPod.java             # Concrete - MongoDB
    ├── KafkaPod.java               # Concrete - Kafka
    ├── ServicePod.java             # Concrete - User application services
    └── GenericTestPod.java         # Concrete - Any arbitrary image
```

## Class Hierarchy

```
<<interface>>
TestPod<SELF>                         ← Contract only
    │
    │ implements
    ▼
<<abstract>>
BaseTestPod<SELF>                     ← Shared implementation
    │  - name, namespace, labels, annotations
    │  - initContainerConfigurers, sidecarConfigurers
    │  - podCustomizers
    │  + withName(), inNamespace(), withLabels()
    │  + withInitContainer(), withSidecar()       [Mid-level API]
    │  + withPodCustomizer()                      [Low-level API]
    │  # applyPodCustomizations()
    │  # waitForReady()
    │
    ├───────────────────────────────┬
    │                               │
    ▼                               ▼
<<abstract>>                   <<abstract>>
StatefulSetPod<SELF>           DeploymentPod<SELF>
    │  + withStatefulSetCustomizer()    │  + withDeploymentCustomizer()
    │  + withServiceCustomizer()        │  + withServiceCustomizer()
    │  + withPvcCustomizer()            │
    │  # buildStatefulSet()             │  # buildDeployment()
    │  # buildService()                 │  # buildService()
    │  # buildMainContainer() [abstract]│  # buildMainContainer() [abstract]
    │                                   │
    ├─────────┬─────────┐               ├─────────┬
    ▼         ▼         ▼               ▼         ▼
MongoDBPod  KafkaPod  (others)     ServicePod  GenericTestPod
```

## Key Design Decisions

### 1. Interface vs Abstract Class

- **TestPod<SELF>** is an **interface** for:
  - Clean contract definition
  - Easy mocking in tests
  - Multiple inheritance possibility

- **BaseTestPod<SELF>** is an **abstract class** for:
  - Shared state (name, namespace, labels)
  - Common method implementations
  - Template methods (applyPodCustomizations)

### 2. Workload-Specific Abstract Classes

- **StatefulSetPod** - For stateful infrastructure (MongoDB, Kafka, Redis)
  - Creates StatefulSet + headless Service
  - Supports PVC customization
  
- **DeploymentPod** - For stateless services (ServicePod, GenericTestPod)
  - Creates Deployment + ClusterIP Service
  - Simpler lifecycle management

### 3. Three-Level API

**High-Level** (domain-specific):
```java
new MongoDBPod()
    .withVersion("6.0")
    .withCredentials("admin", "secret")
```

**Mid-Level** (lambda-based, no .endX()):
```java
.withInitContainer(init -> init
    .withName("permission-fix")
    .withImage("busybox:latest")
    .withCommand("chmod", "777", "/data"))
```

**Low-Level** (full Fabric8 access, uses .endX()):
```java
.withPodCustomizer(podSpec -> podSpec
    .editOrNewSecurityContext()
        .withRunAsUser(1000L)
    .endSecurityContext())
```

## Import Clarifications

### Container type in buildMainContainer()

The `Container` type comes from Fabric8:
```java
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
```

### Distinguishing our builders from Fabric8

| Our Builder (Mid-Level) | Fabric8 Builder (Low-Level) |
|------------------------|----------------------------|
| `org.testpods.core.builder.InitContainerBuilder` | `io.fabric8.kubernetes.api.model.ContainerBuilder` |
| `org.testpods.core.builder.SidecarBuilder` | `io.fabric8.kubernetes.api.model.ContainerBuilder` |

Our builders are **simplified facades** that produce Fabric8 `Container` objects.

## Complete File List (23 files)

| # | File | Type |
|---|------|------|
| 1 | `TestPod.java` | Interface |
| 2 | `BaseTestPod.java` | Abstract Class |
| 3 | `ExecResult.java` | Record |
| 4 | `InitContainerBuilder.java` | Class |
| 5 | `SidecarBuilder.java` | Class |
| 6 | `K8sCluster.java` | Interface + Implementations |
| 7 | `ExternalAccessStrategy.java` | Interface + Implementations |
| 8 | `HostAndPort.java` | Record |
| 9 | `PropertyContext.java` | Class |
| 10 | `TestNamespace.java` | Class |
| 11 | `WaitStrategy.java` | Interface |
| 12 | `ReadinessProbeWaitStrategy.java` | Class |
| 13 | `LogMessageWaitStrategy.java` | Class |
| 14 | `PortWaitStrategy.java` | Class |
| 15 | `HttpWaitStrategy.java` | Class |
| 16 | `CommandWaitStrategy.java` | Class |
| 17 | `CompositeWaitStrategy.java` | Class |
| 18 | `StatefulSetPod.java` | Abstract Class |
| 19 | `DeploymentPod.java` | Abstract Class |
| 20 | `MongoDBPod.java` | Class |
| 21 | `KafkaPod.java` | Class |
| 22 | `ServicePod.java` | Class |
| 23 | `GenericTestPod.java` | Class |
