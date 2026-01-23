# Spec 05: Namespace and Cluster Management Enhancements

**Version:** 1.0
**Priority:** P0 (MVP Phase 1)
**Status:** Ready for Implementation
**PRD References:** FR-1 (Cluster Management), FR-7.13-7.15 (Namespace Management)

---

## Overview

Enhance `TestNamespace` and `K8sCluster` to fully support the JUnit extension's lifecycle needs, including namespace creation, deletion, random suffix generation, and cleanup policies.

## Problem Statement

The existing `TestNamespace` and `K8sCluster` classes need enhancements:
- `TestNamespace` needs `createIfNotExists()` and `delete()` methods
- `NamespaceNaming` needs to generate unique names with random suffixes
- Cleanup policies (MANAGED, NAMESPACE, NONE) need implementation

## Proposed Solution

Complete the implementation of namespace and cluster management classes to support full lifecycle management.

---

## Technical Approach

### NamespaceNaming Updates

**File:** `core/src/main/java/org/testpods/core/cluster/NamespaceNaming.java`

```java
package org.testpods.core.cluster;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Strategies for generating Kubernetes namespace names for tests.
 *
 * <p>All generated names follow Kubernetes naming conventions:
 * <ul>
 *   <li>Lowercase alphanumeric characters or '-'</li>
 *   <li>Must start with an alphanumeric character</li>
 *   <li>Maximum 63 characters</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // For test class
 * String name = NamespaceNaming.forTestClass(MyTest.class);
 * // Result: "testpods-mytest-a1b2c"
 *
 * // Fixed name
 * String name = NamespaceNaming.fixed("my-namespace");
 * // Result: "my-namespace"
 * }</pre>
 */
public final class NamespaceNaming {

    private static final String PREFIX = "testpods";
    private static final int SUFFIX_LENGTH = 5;
    private static final int MAX_NAMESPACE_LENGTH = 63;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] SUFFIX_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private NamespaceNaming() {}

    /**
     * Generate a namespace name for a test class.
     *
     * <p>Format: {@code testpods-{classname}-{random5}}
     *
     * @param testClass the test class
     * @return namespace name safe for Kubernetes
     */
    public static String forTestClass(Class<?> testClass) {
        String className = testClass.getSimpleName()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");

        String suffix = randomSuffix();

        // Ensure we don't exceed max length
        String base = PREFIX + "-" + className;
        int maxBaseLength = MAX_NAMESPACE_LENGTH - 1 - SUFFIX_LENGTH;
        if (base.length() > maxBaseLength) {
            base = base.substring(0, maxBaseLength);
        }

        return base + "-" + suffix;
    }

    /**
     * Generate a namespace name with a custom context.
     *
     * <p>Format: {@code testpods-{context}-{random5}}
     *
     * @param context descriptive context (will be sanitized)
     * @return namespace name safe for Kubernetes
     */
    public static String forContext(String context) {
        String sanitized = context.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");

        String suffix = randomSuffix();

        String base = PREFIX + "-" + sanitized;
        int maxBaseLength = MAX_NAMESPACE_LENGTH - 1 - SUFFIX_LENGTH;
        if (base.length() > maxBaseLength) {
            base = base.substring(0, maxBaseLength);
        }

        return base + "-" + suffix;
    }

    /**
     * Use a fixed namespace name (no random suffix).
     *
     * <p>Use with caution - parallel test execution may conflict.
     *
     * @param name exact namespace name
     * @return the name (validated)
     */
    public static String fixed(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Namespace name cannot be empty");
        }
        if (name.length() > MAX_NAMESPACE_LENGTH) {
            throw new IllegalArgumentException(
                "Namespace name exceeds 63 characters: " + name);
        }
        if (!name.matches("^[a-z0-9][a-z0-9-]*[a-z0-9]$|^[a-z0-9]$")) {
            throw new IllegalArgumentException(
                "Invalid namespace name (must be lowercase alphanumeric with dashes): " + name);
        }
        return name;
    }

    private static String randomSuffix() {
        char[] suffix = new char[SUFFIX_LENGTH];
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix[i] = SUFFIX_CHARS[RANDOM.nextInt(SUFFIX_CHARS.length)];
        }
        return new String(suffix);
    }
}
```

### TestNamespace Updates

**File:** `core/src/main/java/org/testpods/core/cluster/TestNamespace.java`

```java
package org.testpods.core.cluster;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Represents a Kubernetes namespace for test resources.
 *
 * <p>TestNamespace manages the lifecycle of a Kubernetes namespace including:
 * <ul>
 *   <li>Creation (idempotent)</li>
 *   <li>Deletion with wait for cleanup</li>
 *   <li>Access to the cluster client</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * K8sCluster cluster = K8sCluster.discover();
 * TestNamespace ns = new TestNamespace(cluster, "testpods-mytest-abc12");
 * ns.createIfNotExists();
 *
 * // Use namespace for pods...
 *
 * ns.delete();  // Cleanup
 * }</pre>
 */
public class TestNamespace {

    private static final Logger LOG = LoggerFactory.getLogger(TestNamespace.class);

    private final K8sCluster cluster;
    private final String name;
    private boolean created = false;

    /**
     * Create a TestNamespace reference.
     *
     * @param cluster the Kubernetes cluster
     * @param name    namespace name
     */
    public TestNamespace(K8sCluster cluster, String name) {
        this.cluster = cluster;
        this.name = name;
    }

    /**
     * Get the namespace name.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the cluster this namespace belongs to.
     */
    public K8sCluster getCluster() {
        return cluster;
    }

    /**
     * Create the namespace if it doesn't exist.
     *
     * <p>This operation is idempotent - calling multiple times is safe.
     *
     * @return this namespace for chaining
     */
    public TestNamespace createIfNotExists() {
        KubernetesClient client = cluster.getClient();
        Namespace existing = client.namespaces().withName(name).get();

        if (existing == null) {
            LOG.info("Creating namespace: {}", name);

            Namespace ns = new NamespaceBuilder()
                .withNewMetadata()
                    .withName(name)
                    .addToLabels("managed-by", "testpods")
                    .addToLabels("testpods.io/namespace", "true")
                .endMetadata()
                .build();

            client.namespaces().resource(ns).create();
            created = true;

            LOG.info("Created namespace: {}", name);
        } else {
            LOG.debug("Namespace already exists: {}", name);
        }

        return this;
    }

    /**
     * Delete the namespace and wait for cleanup.
     *
     * <p>This method waits up to 2 minutes for the namespace to be fully deleted.
     * All resources in the namespace are deleted as part of namespace deletion.
     */
    public void delete() {
        KubernetesClient client = cluster.getClient();
        Namespace existing = client.namespaces().withName(name).get();

        if (existing == null) {
            LOG.debug("Namespace already deleted: {}", name);
            return;
        }

        LOG.info("Deleting namespace: {}", name);

        client.namespaces().withName(name).delete();

        // Wait for deletion to complete
        try {
            client.namespaces().withName(name)
                .waitUntilCondition(ns -> ns == null, 2, TimeUnit.MINUTES);
            LOG.info("Deleted namespace: {}", name);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while waiting for namespace deletion: {}", name);
        }
    }

    /**
     * Delete only resources managed by TestPods (labeled managed-by=testpods).
     *
     * <p>This is used for MANAGED cleanup policy to leave user-created resources.
     */
    public void deleteManagedResources() {
        KubernetesClient client = cluster.getClient();

        LOG.info("Deleting TestPods-managed resources in namespace: {}", name);

        // Delete deployments
        client.apps().deployments()
            .inNamespace(name)
            .withLabel("managed-by", "testpods")
            .delete();

        // Delete statefulsets
        client.apps().statefulSets()
            .inNamespace(name)
            .withLabel("managed-by", "testpods")
            .delete();

        // Delete services
        client.services()
            .inNamespace(name)
            .withLabel("managed-by", "testpods")
            .delete();

        // Delete configmaps
        client.configMaps()
            .inNamespace(name)
            .withLabel("managed-by", "testpods")
            .delete();

        // Delete secrets
        client.secrets()
            .inNamespace(name)
            .withLabel("managed-by", "testpods")
            .delete();

        LOG.info("Deleted TestPods-managed resources in namespace: {}", name);
    }

    /**
     * Check if this namespace was created by this TestNamespace instance.
     */
    public boolean wasCreated() {
        return created;
    }

    /**
     * Check if the namespace exists in the cluster.
     */
    public boolean exists() {
        KubernetesClient client = cluster.getClient();
        return client.namespaces().withName(name).get() != null;
    }

    @Override
    public String toString() {
        return "TestNamespace[" + name + "]";
    }
}
```

### ExternalAccessStrategy Update

**File:** `core/src/main/java/org/testpods/core/cluster/ExternalAccessStrategy.java`

```java
package org.testpods.core.cluster;

/**
 * Strategy for accessing services running in the Kubernetes cluster from test code.
 *
 * <p>Different cluster types (Minikube, Kind, remote) have different methods
 * for exposing services to the host machine.
 */
public interface ExternalAccessStrategy {

    /**
     * Get the external access point for a service.
     *
     * @param namespace   the namespace containing the service
     * @param serviceName the service name
     * @param servicePort the service port (internal)
     * @return host and port accessible from the test machine
     */
    HostAndPort getExternalAccess(TestNamespace namespace, String serviceName, int servicePort);
}
```

### MinikubeExternalAccessStrategy

**File:** `core/src/main/java/org/testpods/core/cluster/client/MinikubeExternalAccessStrategy.java`

```java
package org.testpods.core.cluster.client;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.testpods.core.cluster.ExternalAccessStrategy;
import org.testpods.core.cluster.HostAndPort;
import org.testpods.core.cluster.TestNamespace;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Optional;

/**
 * External access strategy for Minikube clusters.
 *
 * <p>Uses Minikube's node IP and NodePort services for external access.
 */
public class MinikubeExternalAccessStrategy implements ExternalAccessStrategy {

    private final String profile;
    private String cachedIp;

    public MinikubeExternalAccessStrategy(String profile) {
        this.profile = profile;
    }

    @Override
    public HostAndPort getExternalAccess(TestNamespace namespace, String serviceName, int servicePort) {
        String host = getMinikubeIp();
        int nodePort = getNodePort(namespace, serviceName, servicePort);
        return new HostAndPort(host, nodePort);
    }

    private String getMinikubeIp() {
        if (cachedIp != null) {
            return cachedIp;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("minikube", "ip", "-p", profile);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                cachedIp = reader.readLine().trim();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new ClusterException("minikube ip failed with exit code: " + exitCode);
            }

            return cachedIp;

        } catch (Exception e) {
            throw new ClusterException("Failed to get Minikube IP", e);
        }
    }

    private int getNodePort(TestNamespace namespace, String serviceName, int servicePort) {
        KubernetesClient client = namespace.getCluster().getClient();

        Service service = client.services()
            .inNamespace(namespace.getName())
            .withName(serviceName)
            .get();

        if (service == null) {
            throw new ClusterException(
                "Service not found: " + serviceName + " in namespace " + namespace.getName());
        }

        Optional<ServicePort> port = service.getSpec().getPorts().stream()
            .filter(p -> p.getPort() == servicePort)
            .findFirst();

        if (port.isEmpty() || port.get().getNodePort() == null) {
            throw new ClusterException(
                "NodePort not found for service " + serviceName + " port " + servicePort);
        }

        return port.get().getNodePort();
    }
}
```

---

## Acceptance Criteria

### Functional Requirements

- [ ] `NamespaceNaming.forTestClass()` generates unique names with 5-char random suffix
- [ ] `TestNamespace.createIfNotExists()` is idempotent
- [ ] `TestNamespace.delete()` waits for namespace deletion
- [ ] `TestNamespace.deleteManagedResources()` only deletes labeled resources
- [ ] `MinikubeExternalAccessStrategy` returns correct NodePort access

### Quality Gates

- [ ] Unit tests for naming generation
- [ ] Unit tests for namespace operations
- [ ] JavaDoc on all public classes and methods

---

## Test Plan

### Unit Tests

```java
class NamespaceNamingTest {

    @Test
    void shouldGenerateUniqueNamesForSameClass() {
        String name1 = NamespaceNaming.forTestClass(MyTest.class);
        String name2 = NamespaceNaming.forTestClass(MyTest.class);

        assertThat(name1).isNotEqualTo(name2);
        assertThat(name1).startsWith("testpods-mytest-");
        assertThat(name2).startsWith("testpods-mytest-");
    }

    @Test
    void shouldNotExceed63Characters() {
        String name = NamespaceNaming.forTestClass(
            VeryLongTestClassNameThatExceedsLimits.class);

        assertThat(name.length()).isLessThanOrEqualTo(63);
    }

    @Test
    void shouldValidateFixedNames() {
        assertThatThrownBy(() -> NamespaceNaming.fixed("Invalid_Name"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

class TestNamespaceTest {

    @Test
    void createShouldBeIdempotent() {
        // Mock cluster and verify create is only called once
    }

    @Test
    void deleteShouldWaitForCompletion() {
        // Verify waitUntilCondition is called
    }
}
```

---

## Dependencies

- Fabric8 Kubernetes Client (existing)
- SLF4J for logging

---

## References

- Kubernetes Namespace Naming: https://kubernetes.io/docs/concepts/overview/working-with-objects/names/
- PRD FR-1: Cluster Management
- PRD FR-7.13-7.15: Namespace Management
