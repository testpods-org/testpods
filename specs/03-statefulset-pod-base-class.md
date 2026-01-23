# Spec 03: StatefulSetPod Base Class Implementation

**Version:** 1.0
**Priority:** P0 (MVP Phase 1)
**Status:** Ready for Implementation
**PRD References:** FR-2 (Container/Pod Management), FR-6 (Custom Container Support)

---

## Overview

Implement `StatefulSetPod` as the base class for database pods that need stable network identities and persistent storage. This complements the existing `DeploymentPod` for stateless workloads.

## Problem Statement

The existing `StatefulSetPod` class is likely incomplete or a stub. Database pods (PostgreSQL, MongoDB, etc.) require StatefulSet semantics for:
- Stable network identifiers (predictable pod names)
- Ordered startup and shutdown
- Persistent volume support (optional for tests)

## Proposed Solution

Implement `StatefulSetPod` with full StatefulSet resource creation, NodePort service exposure, and readiness waiting via Fabric8 client.

---

## Technical Approach

### Architecture

```
BaseTestPod<SELF>
    ├── DeploymentPod<SELF>    (stateless workloads)
    └── StatefulSetPod<SELF>   (databases, stateful services)
```

### Implementation

**File:** `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java`

```java
package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.testpods.core.cluster.HostAndPort;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Base class for pods deployed as Kubernetes StatefulSets.
 *
 * <p>StatefulSetPod is appropriate for database pods and other workloads that need:
 * <ul>
 *   <li>Stable, unique network identifiers</li>
 *   <li>Stable, persistent storage (optional)</li>
 *   <li>Ordered, graceful deployment and scaling</li>
 * </ul>
 *
 * <p>Subclasses must implement:
 * <ul>
 *   <li>{@link #buildMainContainer()} - Define the primary container</li>
 *   <li>{@link #getInternalPort()} - Return the primary service port</li>
 *   <li>{@link #publishProperties(PropertyContext)} - Publish connection properties</li>
 * </ul>
 *
 * <h2>Example Subclass</h2>
 * <pre>{@code
 * public class PostgreSQLPod extends StatefulSetPod<PostgreSQLPod> {
 *
 *     @Override
 *     protected Container buildMainContainer() {
 *         return new ContainerBuilder()
 *             .withName("postgres")
 *             .withImage("postgres:16-alpine")
 *             .addNewPort().withContainerPort(5432).endPort()
 *             .build();
 *     }
 *
 *     @Override
 *     public int getInternalPort() {
 *         return 5432;
 *     }
 * }
 * }</pre>
 *
 * @param <SELF> The concrete subclass type for fluent method chaining
 */
public abstract class StatefulSetPod<SELF extends StatefulSetPod<SELF>> extends BaseTestPod<SELF> {

    // === Configuration ===

    protected boolean withPersistentVolume = false;
    protected String storageSize = "1Gi";
    protected String storageClassName;

    // === Runtime state ===

    protected StatefulSet statefulSet;
    protected Service service;
    protected HostAndPort externalAccess;

    // === Configuration API ===

    /**
     * Enable persistent volume for this pod.
     *
     * <p>Note: For most integration tests, persistent storage is not needed
     * and disabled by default for faster startup.
     *
     * @param size storage size (e.g., "1Gi", "10Gi")
     * @return this pod for chaining
     */
    public SELF withPersistentVolume(String size) {
        this.withPersistentVolume = true;
        this.storageSize = size;
        return self();
    }

    /**
     * Set the storage class for persistent volumes.
     *
     * @param storageClassName Kubernetes StorageClass name
     * @return this pod for chaining
     */
    public SELF withStorageClass(String storageClassName) {
        this.storageClassName = storageClassName;
        return self();
    }

    // === Lifecycle Implementation ===

    @Override
    public void start() {
        ensureNamespace();

        KubernetesClient client = getClient();
        String ns = namespace.getName();

        // 1. Create additional resources (init scripts, secrets, etc.)
        createAdditionalResources();

        // 2. Create the StatefulSet
        statefulSet = createStatefulSet(client, ns);

        // 3. Create the headless service (required for StatefulSet)
        createHeadlessService(client, ns);

        // 4. Create NodePort service for external access
        service = createNodePortService(client, ns);

        // 5. Wait for pod to be ready
        waitForReady();

        // 6. Get external access info
        externalAccess = namespace.getCluster().getAccessStrategy()
            .getExternalAccess(namespace, name, getInternalPort());
    }

    @Override
    public void stop() {
        if (namespace == null) return;

        KubernetesClient client = getClient();
        String ns = namespace.getName();

        // Delete in reverse order
        if (service != null) {
            client.services().inNamespace(ns).withName(name).delete();
        }

        // Delete headless service
        client.services().inNamespace(ns).withName(name + "-headless").delete();

        if (statefulSet != null) {
            client.apps().statefulSets().inNamespace(ns).withName(name).delete();

            // Wait for deletion
            client.apps().statefulSets().inNamespace(ns).withName(name)
                .waitUntilCondition(ss -> ss == null, 60, TimeUnit.SECONDS);
        }

        // Delete additional resources
        deleteAdditionalResources();
    }

    @Override
    public boolean isRunning() {
        if (namespace == null || name == null) return false;

        KubernetesClient client = getClient();
        StatefulSet ss = client.apps().statefulSets()
            .inNamespace(namespace.getName())
            .withName(name)
            .get();

        return ss != null && ss.getStatus() != null
            && ss.getStatus().getReplicas() != null
            && ss.getStatus().getReplicas() > 0;
    }

    @Override
    public boolean isReady() {
        if (namespace == null || name == null) return false;

        KubernetesClient client = getClient();
        StatefulSet ss = client.apps().statefulSets()
            .inNamespace(namespace.getName())
            .withName(name)
            .get();

        if (ss == null || ss.getStatus() == null) return false;

        Integer desired = ss.getSpec().getReplicas();
        Integer ready = ss.getStatus().getReadyReplicas();

        return desired != null && ready != null && ready >= desired;
    }

    @Override
    public String getExternalHost() {
        if (externalAccess == null) {
            throw new IllegalStateException("Pod not started. Call start() first.");
        }
        return externalAccess.host();
    }

    @Override
    public int getExternalPort() {
        if (externalAccess == null) {
            throw new IllegalStateException("Pod not started. Call start() first.");
        }
        return externalAccess.port();
    }

    // === Resource Creation ===

    protected StatefulSet createStatefulSet(KubernetesClient client, String ns) {
        Container mainContainer = buildMainContainer();
        Map<String, String> labels = buildLabels();

        var specBuilder = new StatefulSetBuilder()
            .withNewMetadata()
                .withName(name)
                .withNamespace(ns)
                .withLabels(labels)
                .withAnnotations(annotations)
            .endMetadata()
            .withNewSpec()
                .withServiceName(name + "-headless")
                .withReplicas(1)
                .withNewSelector()
                    .withMatchLabels(Map.of("app", name))
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .withLabels(labels)
                    .endMetadata()
                    .withNewSpec()
                        .withContainers(mainContainer)
                    .endSpec()
                .endTemplate();

        // Add PVC template if persistent storage enabled
        if (withPersistentVolume) {
            specBuilder = specBuilder
                .addNewVolumeClaimTemplate()
                    .withNewMetadata()
                        .withName("data")
                    .endMetadata()
                    .withNewSpec()
                        .withAccessModes("ReadWriteOnce")
                        .withNewResources()
                            .addToRequests("storage", new Quantity(storageSize))
                        .endResources()
                        .withStorageClassName(storageClassName)
                    .endSpec()
                .endVolumeClaimTemplate();
        }

        StatefulSet ss = specBuilder.endSpec().build();

        // Apply pod customizations
        PodSpec podSpec = ss.getSpec().getTemplate().getSpec();
        PodSpecBuilder podSpecBuilder = new PodSpecBuilder(podSpec);
        podSpecBuilder = applyPodCustomizations(podSpecBuilder);
        ss.getSpec().getTemplate().setSpec(podSpecBuilder.build());

        return client.apps().statefulSets()
            .inNamespace(ns)
            .resource(ss)
            .create();
    }

    protected void createHeadlessService(KubernetesClient client, String ns) {
        Service headless = new ServiceBuilder()
            .withNewMetadata()
                .withName(name + "-headless")
                .withNamespace(ns)
                .addToLabels("app", name)
                .addToLabels("managed-by", "testpods")
            .endMetadata()
            .withNewSpec()
                .withClusterIP("None")
                .withSelector(Map.of("app", name))
                .addNewPort()
                    .withPort(getInternalPort())
                    .withTargetPort(new IntOrString(getInternalPort()))
                    .withName("primary")
                .endPort()
            .endSpec()
            .build();

        client.services().inNamespace(ns).resource(headless).create();
    }

    protected Service createNodePortService(KubernetesClient client, String ns) {
        Service nodePort = new ServiceBuilder()
            .withNewMetadata()
                .withName(name)
                .withNamespace(ns)
                .addToLabels("app", name)
                .addToLabels("managed-by", "testpods")
            .endMetadata()
            .withNewSpec()
                .withType("NodePort")
                .withSelector(Map.of("app", name))
                .addNewPort()
                    .withPort(getInternalPort())
                    .withTargetPort(new IntOrString(getInternalPort()))
                    .withName("primary")
                .endPort()
            .endSpec()
            .build();

        return client.services().inNamespace(ns).resource(nodePort).create();
    }

    // === Extension Points ===

    /**
     * Build the main container for this pod.
     * Subclasses must implement to define their container image, ports, env, etc.
     */
    protected abstract Container buildMainContainer();

    /**
     * Create additional Kubernetes resources (ConfigMaps, Secrets, etc.).
     * Override in subclasses that need extra resources.
     */
    protected void createAdditionalResources() {
        // Default: no additional resources
    }

    /**
     * Delete additional Kubernetes resources.
     * Override in subclasses that create extra resources.
     */
    protected void deleteAdditionalResources() {
        // Default: no additional resources to delete
    }
}
```

---

## Acceptance Criteria

### Functional Requirements

- [ ] `StatefulSetPod` creates a StatefulSet with 1 replica
- [ ] Creates headless service for stable DNS
- [ ] Creates NodePort service for external access
- [ ] `isRunning()` returns true when StatefulSet has pods
- [ ] `isReady()` returns true when all replicas are ready
- [ ] `stop()` deletes all resources and waits for cleanup
- [ ] `withPersistentVolume()` enables PVC templates

### Quality Gates

- [ ] Unit tests for resource creation
- [ ] Integration test with actual StatefulSet
- [ ] JavaDoc on all public methods

---

## Test Plan

### Unit Tests

```java
class StatefulSetPodTest {

    @Test
    void shouldBuildStatefulSetWithCorrectLabels() {
        // Test that buildLabels() returns expected labels
    }

    @Test
    void shouldCreateHeadlessService() {
        // Test headless service creation
    }
}
```

### Integration Tests

```java
@TestPods
class StatefulSetPodIntegrationTest {

    // Simple stateful pod for testing
    static class TestStatefulPod extends StatefulSetPod<TestStatefulPod> {
        TestStatefulPod() {
            this.name = "test-stateful";
        }

        @Override
        protected Container buildMainContainer() {
            return new ContainerBuilder()
                .withName("nginx")
                .withImage("nginx:alpine")
                .addNewPort().withContainerPort(80).endPort()
                .build();
        }

        @Override
        public int getInternalPort() {
            return 80;
        }

        @Override
        public void publishProperties(PropertyContext ctx) {
            ctx.publish("test.host", this::getExternalHost);
        }
    }

    @Pod
    static TestStatefulPod statefulPod = new TestStatefulPod();

    @Test
    void shouldCreateStatefulSet() {
        assertThat(statefulPod.isRunning()).isTrue();
        assertThat(statefulPod.isReady()).isTrue();
    }

    @Test
    void shouldProvideExternalAccess() {
        assertThat(statefulPod.getExternalHost()).isNotEmpty();
        assertThat(statefulPod.getExternalPort()).isGreaterThan(0);
    }
}
```

---

## Dependencies

- Fabric8 Kubernetes Client (existing)
- No new dependencies required

---

## References

- Kubernetes StatefulSets: https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/
- PRD FR-2: Container/Pod Management
- Existing DeploymentPod implementation for patterns
