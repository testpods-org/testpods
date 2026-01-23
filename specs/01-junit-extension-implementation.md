# Spec 01: JUnit 5 Extension Implementation

**Version:** 1.0
**Priority:** P0 (MVP Phase 1)
**Status:** Ready for Implementation
**PRD References:** FR-7, Section 21 (Test API Design Decisions)

---

## Overview

Implement the `TestPodsExtension` JUnit 5 extension to manage the lifecycle of `@Pod` annotated fields in test classes. This is the core integration point between JUnit and the TestPods library.

## Problem Statement

The current `TestPodsExtension` class is a stub with empty callback methods. Tests cannot use the annotation-driven API (`@TestPods`, `@Pod`) without a working extension.

## Proposed Solution

Implement the extension following JUnit 5 best practices and Testcontainers patterns:
- Discover `@Pod` annotated fields using `ReflectionSupport`
- Manage static (shared) and instance (per-test) field lifecycles
- Use `ExtensionContext.Store.CloseableResource` for automatic cleanup
- Configure `TestPodDefaults` to enable lazy namespace resolution

---

## Technical Approach

### Architecture

```
@TestPods (class annotation)
    └── TestPodsExtension (JUnit 5 extension)
        ├── BeforeAllCallback: Start static @Pod fields
        ├── BeforeEachCallback: Start instance @Pod fields
        ├── AfterEachCallback: Stop instance @Pod fields
        └── AfterAllCallback: Stop static @Pod fields (via CloseableResource)
```

### Implementation Phases

#### Phase 1.1: Core Field Discovery and Lifecycle

**Files to create/modify:**

1. `core/src/main/java/org/testpods/junit/TestPodsExtension.java`

```java
package org.testpods.junit;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.NamespaceNaming;
import org.testpods.core.cluster.TestNamespace;
import org.testpods.core.pods.TestPod;
import org.testpods.core.pods.TestPodDefaults;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;

public class TestPodsExtension implements
    BeforeAllCallback,
    BeforeEachCallback,
    AfterEachCallback,
    AfterAllCallback,
    ExecutionCondition {

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(TestPodsExtension.class);

    private static final String SHARED_NAMESPACE_KEY = "shared-namespace";
    private static final String CLUSTER_KEY = "cluster";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        // Future: Check if Minikube is available
        return ConditionEvaluationResult.enabled("TestPods enabled");
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();

        // 1. Get or create cluster connection
        K8sCluster cluster = getOrCreateCluster(context);

        // 2. Create shared namespace for this test class
        String namespaceName = NamespaceNaming.forTestClass(testClass);
        TestNamespace sharedNamespace = new TestNamespace(cluster, namespaceName);
        sharedNamespace.createIfNotExists();

        // 3. Register namespace for cleanup
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put(SHARED_NAMESPACE_KEY, new NamespaceCloseableResource(sharedNamespace));

        // 4. Configure TestPodDefaults for lazy resolution
        TestPodDefaults.setClusterSupplier(() -> cluster);
        TestPodDefaults.setSharedNamespace(sharedNamespace);

        // 5. Discover and start static @Pod fields
        List<Field> staticPodFields = discoverPodFields(testClass, true);
        startPods(staticPodFields, null, context);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        // Start instance-level @Pod fields
        Object testInstance = context.getRequiredTestInstance();
        List<Field> instancePodFields = discoverPodFields(testInstance.getClass(), false);
        startPods(instancePodFields, testInstance, context);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        // Stop instance-level pods
        Object testInstance = context.getRequiredTestInstance();
        List<Field> instancePodFields = discoverPodFields(testInstance.getClass(), false);
        stopPods(instancePodFields, testInstance);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        // Cleanup happens automatically via CloseableResource
        TestPodDefaults.clear();
    }

    // === Field Discovery ===

    private List<Field> discoverPodFields(Class<?> clazz, boolean staticOnly) {
        Predicate<Field> predicate = field ->
            (AnnotationSupport.isAnnotated(field, org.testpods.junit.TestPod.class) ||
             TestPod.class.isAssignableFrom(field.getType())) &&
            Modifier.isStatic(field.getModifiers()) == staticOnly;

        return ReflectionSupport.findFields(
            clazz,
            predicate,
            HierarchyTraversalMode.TOP_DOWN
        );
    }

    // === Pod Lifecycle ===

    private void startPods(List<Field> fields, Object target, ExtensionContext context) {
        for (Field field : fields) {
            startPodForField(field, target, context);
        }
    }

    private void startPodForField(Field field, Object target, ExtensionContext context) {
        try {
            field.setAccessible(true);
            Object value = field.get(target);

            if (value == null) {
                throw new ExtensionConfigurationException(
                    "@Pod field '" + field.getName() + "' must be initialized");
            }

            if (!(value instanceof TestPod<?>)) {
                throw new ExtensionConfigurationException(
                    "@Pod field '" + field.getName() + "' must implement TestPod interface");
            }

            TestPod<?> pod = (TestPod<?>) value;
            pod.start();

            // Register for cleanup if static
            if (target == null) {
                ExtensionContext.Store store = context.getStore(NAMESPACE);
                store.put("pod-" + field.getName(), new PodCloseableResource(pod));
            }

        } catch (IllegalAccessException e) {
            throw new ExtensionConfigurationException(
                "Cannot access field '" + field.getName() + "'", e);
        }
    }

    private void stopPods(List<Field> fields, Object target) {
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(target);
                if (value instanceof TestPod<?>) {
                    ((TestPod<?>) value).stop();
                }
            } catch (IllegalAccessException e) {
                // Log warning but continue stopping other pods
            }
        }
    }

    // === Cluster Management ===

    private K8sCluster getOrCreateCluster(ExtensionContext context) {
        ExtensionContext.Store store = context.getRoot().getStore(NAMESPACE);
        return store.getOrComputeIfAbsent(
            CLUSTER_KEY,
            key -> new ClusterCloseableResource(),
            ClusterCloseableResource.class
        ).getCluster();
    }

    // === CloseableResource Implementations ===

    private static class ClusterCloseableResource
            implements ExtensionContext.Store.CloseableResource {
        private final K8sCluster cluster;

        ClusterCloseableResource() {
            this.cluster = K8sCluster.discover();
        }

        K8sCluster getCluster() { return cluster; }

        @Override
        public void close() throws Exception {
            cluster.close();
        }
    }

    private static class NamespaceCloseableResource
            implements ExtensionContext.Store.CloseableResource {
        private final TestNamespace namespace;

        NamespaceCloseableResource(TestNamespace namespace) {
            this.namespace = namespace;
        }

        @Override
        public void close() throws Exception {
            namespace.delete();
        }
    }

    private static class PodCloseableResource
            implements ExtensionContext.Store.CloseableResource {
        private final TestPod<?> pod;

        PodCloseableResource(TestPod<?> pod) {
            this.pod = pod;
        }

        @Override
        public void close() throws Exception {
            pod.stop();
        }
    }
}
```

2. `core/src/main/java/org/testpods/junit/TestPods.java` - Update with configuration options:

```java
package org.testpods.junit;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestPodsExtension.class)
@Inherited
public @interface TestPods {

    /**
     * Override the auto-generated namespace name.
     */
    String namespace() default "";

    /**
     * Cleanup policy after tests complete.
     */
    CleanupPolicy cleanup() default CleanupPolicy.MANAGED;

    /**
     * Stop all pods on first failure.
     */
    boolean failFast() default true;

    /**
     * Keep pods running after test failure for debugging.
     */
    boolean debug() default false;
}
```

3. `core/src/main/java/org/testpods/junit/CleanupPolicy.java` - New enum:

```java
package org.testpods.junit;

public enum CleanupPolicy {
    /** Delete only TestPods-managed resources */
    MANAGED,

    /** Delete the entire namespace */
    NAMESPACE,

    /** Leave everything for debugging */
    NONE
}
```

4. `core/src/main/java/org/testpods/core/pods/TestPodDefaults.java` - Add shared namespace:

```java
// Add to existing class:

private static TestNamespace sharedNamespace;

public static void setSharedNamespace(TestNamespace namespace) {
    sharedNamespace = namespace;
}

public static TestNamespace getSharedNamespace() {
    return sharedNamespace;
}

public static void clear() {
    clusterSupplier = null;
    namespaceNameSupplier = null;
    sharedNamespace = null;
}
```

5. `core/src/main/java/org/testpods/core/cluster/TestNamespace.java` - Add methods:

```java
// Add to existing class:

public void createIfNotExists() {
    KubernetesClient client = cluster.getClient();
    Namespace existing = client.namespaces().withName(name).get();
    if (existing == null) {
        Namespace ns = new NamespaceBuilder()
            .withNewMetadata()
                .withName(name)
                .addToLabels("managed-by", "testpods")
            .endMetadata()
            .build();
        client.namespaces().resource(ns).create();
    }
}

public void delete() {
    KubernetesClient client = cluster.getClient();
    client.namespaces().withName(name).delete();
    // Wait for deletion
    client.namespaces().withName(name)
        .waitUntilCondition(ns -> ns == null, 60, TimeUnit.SECONDS);
}
```

---

## Acceptance Criteria

### Functional Requirements

- [ ] `@TestPods` annotation enables the extension on test classes
- [ ] Static `@Pod` fields are started in `@BeforeAll` and stopped in `@AfterAll`
- [ ] Instance `@Pod` fields are started before each test and stopped after
- [ ] Namespace is created per test class with random suffix
- [ ] Cluster connection is shared across all tests in the run
- [ ] Resources are cleaned up even when tests fail

### Quality Gates

- [ ] Unit tests for field discovery
- [ ] Integration test with actual Minikube cluster
- [ ] JavaDoc on all public classes and methods
- [ ] No memory leaks from unclosed resources

---

## Test Plan

### Unit Tests

`core/src/test/java/org/testpods/junit/TestPodsExtensionTest.java`:

```java
@TestPods
class TestPodsExtensionTest {

    @Pod
    static GenericTestPod nginx = new GenericTestPod("nginx:alpine")
        .withPort(80);

    @Test
    void podShouldBeRunning() {
        assertThat(nginx.isRunning()).isTrue();
        assertThat(nginx.isReady()).isTrue();
    }

    @Test
    void shouldProvideExternalAccess() {
        String host = nginx.getExternalHost();
        int port = nginx.getExternalPort();

        assertThat(host).isNotEmpty();
        assertThat(port).isGreaterThan(0);
    }
}
```

### Integration Tests

```java
@TestPods
@SpringBootTest
class SpringIntegrationTest {

    @Pod
    static GenericTestPod redis = new GenericTestPod("redis:7-alpine")
        .withPort(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getExternalHost);
        registry.add("spring.redis.port", () -> String.valueOf(redis.getExternalPort()));
    }

    @Test
    void shouldConnectToRedis() {
        // Test Spring can connect to Redis pod
    }
}
```

---

## Dependencies

- JUnit Jupiter API 5.x
- JUnit Platform Commons (for ReflectionSupport)
- Fabric8 Kubernetes Client (existing)

---

## References

- PRD Section 21: Test API Design Decisions
- PRD FR-7: JUnit Integration
- JUnit 5 Extension Model: https://junit.org/junit5/docs/current/user-guide/#extensions
- Testcontainers JUnit 5 Extension: https://github.com/testcontainers/testcontainers-java

---

## Open Questions

1. Should we support `@Nested` test classes with separate pod lifecycles?
2. Should parallel pod startup be enabled by default?
3. How to handle extension ordering with Spring Boot's extension?
