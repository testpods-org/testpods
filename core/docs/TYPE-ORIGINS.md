# TestPods Type Origins

This document clarifies which types come from the TestPods library versus external dependencies (primarily Fabric8 Kubernetes Client).

## External Dependencies

### Fabric8 Kubernetes Client (`io.fabric8.kubernetes`)

The Fabric8 client provides all the Kubernetes API model classes and the client for interacting with the cluster.

#### Model Classes Used

| Fabric8 Class | Package | Used In |
|---------------|---------|---------|
| `Container` | `io.fabric8.kubernetes.api.model` | `buildMainContainer()` return type |
| `ContainerBuilder` | `io.fabric8.kubernetes.api.model` | Building containers |
| `ContainerPort` | `io.fabric8.kubernetes.api.model` | Port specifications |
| `ContainerPortBuilder` | `io.fabric8.kubernetes.api.model` | Building ports |
| `EnvVar` | `io.fabric8.kubernetes.api.model` | Environment variables |
| `EnvVarBuilder` | `io.fabric8.kubernetes.api.model` | Building env vars |
| `VolumeMount` | `io.fabric8.kubernetes.api.model` | Volume mounts |
| `VolumeMountBuilder` | `io.fabric8.kubernetes.api.model` | Building mounts |
| `PodSpec` | `io.fabric8.kubernetes.api.model` | Pod specification |
| `PodSpecBuilder` | `io.fabric8.kubernetes.api.model` | Pod customization |
| `Service` | `io.fabric8.kubernetes.api.model` | K8s Service |
| `ServiceBuilder` | `io.fabric8.kubernetes.api.model` | Building Services |
| `ServicePort` | `io.fabric8.kubernetes.api.model` | Service ports |
| `Namespace` | `io.fabric8.kubernetes.api.model` | K8s Namespace |
| `NamespaceBuilder` | `io.fabric8.kubernetes.api.model` | Building Namespaces |
| `StatefulSet` | `io.fabric8.kubernetes.api.model.apps` | StatefulSet workload |
| `StatefulSetBuilder` | `io.fabric8.kubernetes.api.model.apps` | Building StatefulSets |
| `Deployment` | `io.fabric8.kubernetes.api.model.apps` | Deployment workload |
| `DeploymentBuilder` | `io.fabric8.kubernetes.api.model.apps` | Building Deployments |
| `PersistentVolumeClaim` | `io.fabric8.kubernetes.api.model` | PVCs |
| `PersistentVolumeClaimBuilder` | `io.fabric8.kubernetes.api.model` | Building PVCs |
| `Quantity` | `io.fabric8.kubernetes.api.model` | Resource quantities |
| `IntOrString` | `io.fabric8.kubernetes.api.model` | Port/target specs |
| `ResourceRequirements` | `io.fabric8.kubernetes.api.model` | CPU/memory limits |
| `ResourceRequirementsBuilder` | `io.fabric8.kubernetes.api.model` | Building resources |
| `NetworkPolicy` | `io.fabric8.kubernetes.api.model.networking.v1` | Network policies |
| `ResourceQuota` | `io.fabric8.kubernetes.api.model` | Resource quotas |

#### Client Classes Used

| Fabric8 Class | Package | Used For |
|---------------|---------|----------|
| `KubernetesClient` | `io.fabric8.kubernetes.client` | All K8s API operations |
| `KubernetesClientBuilder` | `io.fabric8.kubernetes.client` | Creating clients |
| `LocalPortForward` | `io.fabric8.kubernetes.client` | Port forwarding |
| `Config` | `io.fabric8.kubernetes.client` | Kubeconfig loading |
| `ExecListener` | `io.fabric8.kubernetes.client.dsl` | Exec callbacks |
| `ExecWatch` | `io.fabric8.kubernetes.client.dsl` | Exec watch handle |

### MongoDB Driver (Optional)

| Class | Package | Used In |
|-------|---------|---------|
| `MongoClient` | `com.mongodb.client` | `MongoDBPod.createClient()` |
| `MongoClients` | `com.mongodb.client` | Client factory |

---

## TestPods Library Types

### Core Package (`org.testpods.core`)

| Class | Type | Description |
|-------|------|-------------|
| `TestPod<SELF>` | Interface | The main contract for all test pods |
| `BaseTestPod<SELF>` | Abstract Class | Shared implementation for all pods |
| `ExecResult` | Record | Result of container exec commands |

### Builder Package (`org.testpods.core.builder`)

| Class | Type | Description |
|-------|------|-------------|
| `InitContainerBuilder` | Class | Simplified builder for init containers |
| `SidecarBuilder` | Class | Simplified builder for sidecar containers |

**Note**: These are **our simplified builders**, NOT Fabric8's `ContainerBuilder`. They produce Fabric8 `Container` objects but hide the complexity.

```java
// Our builder (mid-level API)
org.testpods.core.builder.InitContainerBuilder

// Fabric8 builder (low-level API)  
io.fabric8.kubernetes.api.model.ContainerBuilder
```

### Cluster Package (`org.testpods.core.cluster`)

| Class | Type | Description |
|-------|------|-------------|
| `K8sCluster` | Interface | Cluster connection abstraction |
| `MinikubeCluster` | Class (package-private) | Minikube implementation |
| `KindCluster` | Class (package-private) | Kind implementation |
| `KubeconfigCluster` | Class (package-private) | Generic kubeconfig implementation |
| `ExternalAccessStrategy` | Interface | Strategy for external pod access |
| `PortForwardAccessStrategy` | Class (package-private) | Port-forward implementation |
| `NodePortAccessStrategy` | Class (package-private) | NodePort implementation |
| `LoadBalancerAccessStrategy` | Class (package-private) | LoadBalancer implementation |
| `MinikubeServiceAccessStrategy` | Class (package-private) | Minikube service URL implementation |
| `HostAndPort` | Record | Host:port value object |

### Config Package (`org.testpods.core.config`)

| Class | Type | Description |
|-------|------|-------------|
| `PropertyContext` | Class | Shared property registry for pod configuration |

### Namespace Package (`org.testpods.core.namespace`)

| Class | Type | Description |
|-------|------|-------------|
| `TestNamespace` | Class | Kubernetes namespace wrapper |

### Wait Package (`org.testpods.core.wait`)

| Class | Type | Description |
|-------|------|-------------|
| `WaitStrategy` | Interface | Wait strategy contract with factory methods |
| `ReadinessProbeWaitStrategy` | Class | Wait for K8s readiness probe |
| `LogMessageWaitStrategy` | Class | Wait for log message pattern |
| `PortWaitStrategy` | Class | Wait for TCP port |
| `HttpWaitStrategy` | Class | Wait for HTTP endpoint |
| `CommandWaitStrategy` | Class | Wait for command success |
| `CompositeWaitStrategy` | Class | Combine multiple strategies |

### Workload Package (`org.testpods.core.workload`)

| Class | Type | Description |
|-------|------|-------------|
| `StatefulSetPod<SELF>` | Abstract Class | Base for StatefulSet-backed pods |
| `DeploymentPod<SELF>` | Abstract Class | Base for Deployment-backed pods |

### Pod Package (`org.testpods.core.pod`)

| Class | Type | Description |
|-------|------|-------------|
| `MongoDBPod` | Class | MongoDB test pod |
| `KafkaPod` | Class | Kafka test pod |
| `ServicePod` | Class | User application service pod |
| `GenericTestPod` | Class | Generic pod for any image |

---

## Import Cheat Sheet

### When creating a new pod implementation

```java
// TestPods imports
import org.testpods.core.config.PropertyContext;
import org.testpods.core.workload.StatefulSetPod;  // or DeploymentPod
import org.testpods.core.wait.WaitStrategy;

// Fabric8 imports for building the container
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
```

### When using the mid-level API

```java
// Only TestPods imports needed
import org.testpods.core.builder.InitContainerBuilder;
import org.testpods.core.builder.SidecarBuilder;

// Usage - no Fabric8 knowledge required
.withInitContainer(init -> init
    .withName("my-init")
    .withImage("busybox")
    .withCommand("echo", "hello"))
```

### When using the low-level API

```java
// Need Fabric8 imports
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;

// Usage - full Fabric8 builder access
.withPodCustomizer(podSpec -> podSpec
    .editOrNewSecurityContext()
        .withRunAsUser(1000L)
    .endSecurityContext())
```

---

## Dependency Summary

```xml
<!-- pom.xml dependencies -->
<dependencies>
    <!-- Required: Fabric8 Kubernetes Client -->
    <dependency>
        <groupId>io.fabric8</groupId>
        <artifactId>kubernetes-client</artifactId>
        <version>6.10.0</version>
    </dependency>
    
    <!-- Required: JUnit 5 -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.0</version>
    </dependency>
    
    <!-- Optional: MongoDB driver (only if using MongoDBPod.createClient()) -->
    <dependency>
        <groupId>org.mongodb</groupId>
        <artifactId>mongodb-driver-sync</artifactId>
        <version>4.11.0</version>
        <optional>true</optional>
    </dependency>
</dependencies>
```
