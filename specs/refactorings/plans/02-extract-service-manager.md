# Plan: Extract ServiceManager Component

**Priority:** High
**Effort:** Medium
**Category:** Architecture / Composition Over Inheritance
**Phase:** 2 - Architecture Refactoring (Can be done in parallel with 01, 03)
**Depends On:** 01-extract-workload-manager (can be done in parallel)

---

## Overview

Extract service creation code from `DeploymentPod` and `StatefulSetPod` into composable `ServiceManager` components, enabling mix-and-match service types (ClusterIP, Headless, NodePort).

## Problem Statement

Service creation code is duplicated across pod base classes:

- `DeploymentPod.buildService()` creates ClusterIP service (lines 280-307)
- `StatefulSetPod.createHeadlessService()` creates Headless service (lines 298-318)
- `StatefulSetPod.createNodePortService()` creates NodePort service (lines 320-340)

The code differs subtly (ClusterIP vs Headless vs NodePort), but shares the same structure:
- Build ServiceBuilder with name, namespace, labels
- Add port configuration
- Apply customizers
- Create in cluster

Both classes also have duplicate `serviceCustomizers` lists.

## Proposed Solution

### ServiceManager Interface

```java
package org.testpods.core.service;

import io.fabric8.kubernetes.api.model.Service;

/**
 * Manages Kubernetes Service resources for test pods.
 * This is an internal implementation detail.
 */
public interface ServiceManager {

    /**
     * Create the service in the cluster.
     */
    Service create(ServiceConfig config);

    /**
     * Delete the service from the cluster.
     */
    void delete();

    /**
     * Get the created service, or null if not created.
     */
    Service getService();

    /**
     * Get the service name.
     */
    String getName();
}
```

### ServiceConfig Record

```java
package org.testpods.core.service;

import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Configuration for service creation.
 */
public record ServiceConfig(
    String name,
    String namespace,
    int port,
    Map<String, String> labels,
    Map<String, String> selector,
    List<UnaryOperator<ServiceBuilder>> customizers,
    KubernetesClient client
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        // Fluent builder implementation
    }
}
```

## Technical Considerations

### Design Constraints (MUST Preserve)

1. **Fluent interface** - Service configuration must remain intuitive:
   ```java
   pod.withServiceCustomizer(svc -> svc
       .editSpec()
           .withType("NodePort")
       .endSpec())
   ```

2. **Internal implementation** - ServiceManager is NOT exposed to pod users directly

3. **Backwards compatibility** - Existing pods should continue to work

### Service Types Required

| Type | Use Case | Implementation |
|------|----------|---------------|
| ClusterIP | Default for Deployments | Internal cluster access |
| Headless | StatefulSet stable DNS | `clusterIP: None` |
| NodePort | External access | Exposes on node ports |
| Composite | StatefulSet pods | Combines Headless + NodePort |

## Acceptance Criteria

### Functional Requirements
- [ ] `ServiceManager` interface exists with `create()`, `delete()`, `getService()` methods
- [ ] Three service types implemented: ClusterIP, Headless, NodePort
- [ ] `CompositeServiceManager` allows combining multiple service types
- [ ] Service customizer fluent API still works

### Non-Functional Requirements
- [ ] Duplicate service code eliminated from `DeploymentPod` and `StatefulSetPod`
- [ ] Services are created and deleted in correct order

### Quality Gates
- [ ] All existing tests pass
- [ ] Test coverage for each service type

## Files to Create/Modify

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/service/ServiceManager.java` | Create new |
| `core/src/main/java/org/testpods/core/service/ServiceConfig.java` | Create new |
| `core/src/main/java/org/testpods/core/service/ClusterIPServiceManager.java` | Create new |
| `core/src/main/java/org/testpods/core/service/HeadlessServiceManager.java` | Create new |
| `core/src/main/java/org/testpods/core/service/NodePortServiceManager.java` | Create new |
| `core/src/main/java/org/testpods/core/service/CompositeServiceManager.java` | Create new |
| `core/src/main/java/org/testpods/core/pods/ComposableTestPod.java` | Add serviceManager |

## MVP

### ServiceManager.java

```java
package org.testpods.core.service;

import io.fabric8.kubernetes.api.model.Service;

public interface ServiceManager {
    Service create(ServiceConfig config);
    void delete();
    Service getService();
    String getName();
}
```

### ClusterIPServiceManager.java

```java
package org.testpods.core.service;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;

public class ClusterIPServiceManager implements ServiceManager {

    private Service service;
    private ServiceConfig config;

    @Override
    public Service create(ServiceConfig config) {
        this.config = config;

        ServiceBuilder builder = new ServiceBuilder()
            .withNewMetadata()
                .withName(config.name())
                .withNamespace(config.namespace())
                .withLabels(config.labels())
            .endMetadata()
            .withNewSpec()
                .withSelector(config.selector())
                .withType("ClusterIP")
                .addNewPort()
                    .withName("primary")
                    .withPort(config.port())
                    .withTargetPort(new IntOrString(config.port()))
                .endPort()
            .endSpec();

        // Apply customizers
        for (var customizer : config.customizers()) {
            builder = customizer.apply(builder);
        }

        this.service = config.client().services()
            .inNamespace(config.namespace())
            .resource(builder.build())
            .create();

        return service;
    }

    @Override
    public void delete() {
        if (config != null && service != null) {
            config.client().services()
                .inNamespace(config.namespace())
                .withName(config.name())
                .delete();
            service = null;
        }
    }

    @Override
    public Service getService() {
        return service;
    }

    @Override
    public String getName() {
        return config != null ? config.name() : null;
    }
}
```

### HeadlessServiceManager.java

```java
package org.testpods.core.service;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;

public class HeadlessServiceManager implements ServiceManager {

    private Service service;
    private ServiceConfig config;

    @Override
    public Service create(ServiceConfig config) {
        this.config = config;

        ServiceBuilder builder = new ServiceBuilder()
            .withNewMetadata()
                .withName(config.name())
                .withNamespace(config.namespace())
                .withLabels(config.labels())
            .endMetadata()
            .withNewSpec()
                .withSelector(config.selector())
                .withClusterIP("None")  // Headless
                .addNewPort()
                    .withName("primary")
                    .withPort(config.port())
                    .withTargetPort(new IntOrString(config.port()))
                .endPort()
            .endSpec();

        for (var customizer : config.customizers()) {
            builder = customizer.apply(builder);
        }

        this.service = config.client().services()
            .inNamespace(config.namespace())
            .resource(builder.build())
            .create();

        return service;
    }

    @Override
    public void delete() {
        if (config != null && service != null) {
            config.client().services()
                .inNamespace(config.namespace())
                .withName(config.name())
                .delete();
            service = null;
        }
    }

    @Override
    public Service getService() {
        return service;
    }

    @Override
    public String getName() {
        return config != null ? config.name() : null;
    }
}
```

### NodePortServiceManager.java

```java
package org.testpods.core.service;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;

public class NodePortServiceManager implements ServiceManager {

    private Service service;
    private ServiceConfig config;
    private Integer specifiedNodePort;

    public NodePortServiceManager withNodePort(int nodePort) {
        this.specifiedNodePort = nodePort;
        return this;
    }

    @Override
    public Service create(ServiceConfig config) {
        this.config = config;

        ServiceBuilder builder = new ServiceBuilder()
            .withNewMetadata()
                .withName(config.name())
                .withNamespace(config.namespace())
                .withLabels(config.labels())
            .endMetadata()
            .withNewSpec()
                .withSelector(config.selector())
                .withType("NodePort")
                .addNewPort()
                    .withName("primary")
                    .withPort(config.port())
                    .withTargetPort(new IntOrString(config.port()))
                .endPort()
            .endSpec();

        if (specifiedNodePort != null) {
            builder.editSpec()
                .editFirstPort()
                    .withNodePort(specifiedNodePort)
                .endPort()
            .endSpec();
        }

        for (var customizer : config.customizers()) {
            builder = customizer.apply(builder);
        }

        this.service = config.client().services()
            .inNamespace(config.namespace())
            .resource(builder.build())
            .create();

        return service;
    }

    @Override
    public void delete() {
        if (config != null && service != null) {
            config.client().services()
                .inNamespace(config.namespace())
                .withName(config.name())
                .delete();
            service = null;
        }
    }

    @Override
    public Service getService() {
        return service;
    }

    @Override
    public String getName() {
        return config != null ? config.name() : null;
    }
}
```

### CompositeServiceManager.java

```java
package org.testpods.core.service;

import io.fabric8.kubernetes.api.model.Service;
import java.util.ArrayList;
import java.util.List;

/**
 * Combines multiple service managers for pods needing multiple services
 * (e.g., StatefulSet with headless + NodePort).
 */
public class CompositeServiceManager implements ServiceManager {

    private final List<ServiceManager> managers;
    private final List<String> nameSuffixes;

    public CompositeServiceManager(ServiceManager... managers) {
        this.managers = List.of(managers);
        this.nameSuffixes = new ArrayList<>();
        for (int i = 0; i < managers.length; i++) {
            nameSuffixes.add(i == 0 ? "" : "-" + i);
        }
    }

    public CompositeServiceManager withSuffixes(String... suffixes) {
        nameSuffixes.clear();
        for (String suffix : suffixes) {
            nameSuffixes.add(suffix);
        }
        return this;
    }

    @Override
    public Service create(ServiceConfig config) {
        Service primary = null;

        for (int i = 0; i < managers.size(); i++) {
            ServiceManager manager = managers.get(i);
            String suffix = i < nameSuffixes.size() ? nameSuffixes.get(i) : "";

            ServiceConfig adjustedConfig = new ServiceConfig(
                config.name() + suffix,
                config.namespace(),
                config.port(),
                config.labels(),
                config.selector(),
                config.customizers(),
                config.client()
            );

            Service svc = manager.create(adjustedConfig);
            if (primary == null) primary = svc;
        }

        return primary;
    }

    @Override
    public void delete() {
        // Delete in reverse order
        for (int i = managers.size() - 1; i >= 0; i--) {
            try {
                managers.get(i).delete();
            } catch (Exception e) {
                // Log but continue
            }
        }
    }

    @Override
    public Service getService() {
        return managers.isEmpty() ? null : managers.get(0).getService();
    }

    @Override
    public String getName() {
        return managers.isEmpty() ? null : managers.get(0).getName();
    }

    /**
     * Get a specific service by index.
     */
    public Service getService(int index) {
        return index < managers.size() ? managers.get(index).getService() : null;
    }
}
```

## Test Plan

### ServiceManagerTest.java

```java
@Test
void clusterIPServiceShouldCreateCorrectType() {
    ServiceManager manager = new ClusterIPServiceManager();
    ServiceConfig config = ServiceConfig.builder()
        .name("test-svc")
        .namespace("test-ns")
        .port(8080)
        .labels(Map.of("app", "test"))
        .selector(Map.of("app", "test"))
        .client(client)
        .build();

    Service svc = manager.create(config);

    assertThat(svc.getSpec().getType()).isEqualTo("ClusterIP");
    assertThat(svc.getSpec().getClusterIP()).isNotEqualTo("None");
}

@Test
void headlessServiceShouldHaveClusterIPNone() {
    ServiceManager manager = new HeadlessServiceManager();
    ServiceConfig config = createTestConfig();

    Service svc = manager.create(config);

    assertThat(svc.getSpec().getClusterIP()).isEqualTo("None");
}

@Test
void compositeServiceShouldCreateMultiple() {
    ServiceManager manager = new CompositeServiceManager(
        new HeadlessServiceManager(),
        new NodePortServiceManager()
    ).withSuffixes("-headless", "");

    ServiceConfig config = createTestConfig();
    manager.create(config);

    assertThat(client.services().inNamespace("test-ns").withName("test-svc-headless").get())
        .isNotNull();
    assertThat(client.services().inNamespace("test-ns").withName("test-svc").get())
        .isNotNull();
}
```

## Usage Examples

### GenericTestPod (DeploymentManager + ClusterIPServiceManager)

```java
public class GenericTestPod extends ComposableTestPod<GenericTestPod> {

    @Override
    protected WorkloadManager createWorkloadManager() {
        return new DeploymentManager();
    }

    @Override
    protected ServiceManager createServiceManager() {
        return new ClusterIPServiceManager();
    }
}
```

### PostgreSQLPod (StatefulSetManager + CompositeServiceManager)

```java
public class PostgreSQLPod extends ComposableTestPod<PostgreSQLPod> {

    @Override
    protected WorkloadManager createWorkloadManager() {
        return new StatefulSetManager()
            .withServiceName(name + "-headless")
            .withPvcTemplates(storageManager.getPvcTemplates());
    }

    @Override
    protected ServiceManager createServiceManager() {
        return new CompositeServiceManager(
            new HeadlessServiceManager(),
            new NodePortServiceManager()
        ).withSuffixes("-headless", "");
    }
}
```

## References

- Spec: `specs/refactorings/02-extract-service-manager.md`
- Current DeploymentPod service code: `core/src/main/java/org/testpods/core/pods/DeploymentPod.java:280-307`
- Current StatefulSetPod service code: `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java:298-340`
- Fabric8 Service docs: https://github.com/fabric8io/kubernetes-client

---

## Validation Output

After implementation, write results to `specs/refactorings/02-extract-service-manager_result.md`
