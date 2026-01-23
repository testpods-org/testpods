# Plan: Fix Thread-Unsafe Static State in TestPodDefaults

**Priority:** High
**Effort:** Small
**Category:** Bug Fix / Thread Safety
**Phase:** 4 - Cleanup (Can be done in parallel)

---

## Overview

Fix thread-safety issues in `TestPodDefaults` by replacing static fields with `InheritableThreadLocal`, ensuring parallel JUnit 5 test execution works correctly.

## Problem Statement

`TestPodDefaults.setClusterSupplier()` and related methods use static state that is shared across all threads:

```java
// Current implementation (THREAD-UNSAFE)
public class TestPodDefaults {
    private static Supplier<K8sCluster> clusterSupplier;
    private static TestNamespace sharedNamespace;

    public static void setClusterSupplier(Supplier<K8sCluster> supplier) {
        clusterSupplier = supplier;  // Static field - NOT THREAD SAFE!
    }

    public static K8sCluster resolveCluster() {
        return clusterSupplier.get();
    }
}
```

### Impact
- Tests running in parallel may get wrong cluster configurations
- Race conditions when multiple test classes set different defaults
- Flaky tests that pass/fail randomly depending on execution order
- Resource leaks when pods use wrong namespace

## Proposed Solution

Use `InheritableThreadLocal` to isolate defaults per test thread:

```java
public class TestPodDefaults {

    private static final InheritableThreadLocal<Supplier<K8sCluster>> clusterSupplier =
        new InheritableThreadLocal<>();

    private static final InheritableThreadLocal<TestNamespace> sharedNamespace =
        new InheritableThreadLocal<>();

    public static void setClusterSupplier(Supplier<K8sCluster> supplier) {
        clusterSupplier.set(supplier);
    }

    public static K8sCluster resolveCluster() {
        Supplier<K8sCluster> supplier = clusterSupplier.get();
        if (supplier == null) {
            throw new IllegalStateException(
                "No cluster supplier configured. Use @TestPods or set TestPodDefaults.setClusterSupplier()");
        }
        return supplier.get();
    }

    public static void clear() {
        clusterSupplier.remove();
        sharedNamespace.remove();
    }
}
```

### Why InheritableThreadLocal?

- **Thread isolation:** Each test thread gets its own copy
- **Child thread inheritance:** Child threads (e.g., parallel assertions, async operations) inherit parent values
- **Memory safety:** `remove()` prevents memory leaks

## Technical Considerations

- **InheritableThreadLocal vs ThreadLocal:** Use `InheritableThreadLocal` because JUnit 5 may spawn child threads for parallel streams or async assertions
- **Clear on cleanup:** Must call `clear()` in `afterAll()` to prevent memory leaks
- **Copy constructor:** Override `childValue()` for defensive copying if needed
- **Backward compatibility:** Existing single-threaded tests continue to work unchanged

## Acceptance Criteria

### Functional Requirements
- [ ] `TestPodDefaults` uses `InheritableThreadLocal` for all state
- [ ] `clear()` method exists to clean up state
- [ ] JUnit extension calls `clear()` in `afterAll()`
- [ ] Parallel tests don't interfere with each other's defaults

### Non-Functional Requirements
- [ ] Existing single-threaded tests continue to work
- [ ] No memory leaks from ThreadLocal values

### Quality Gates
- [ ] Thread safety tests pass
- [ ] Parallel test execution works correctly

## Files to Modify

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/pods/TestPodDefaults.java` | Change to InheritableThreadLocal |
| `core/src/main/java/org/testpods/junit/TestPodsExtension.java` | Add cleanup in afterAll |
| `core/src/test/java/org/testpods/core/pods/TestPodDefaultsTest.java` | Add thread safety tests |

## MVP

### TestPodDefaults.java (fixed implementation)

```java
package org.testpods.core.pods;

import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.TestNamespace;
import java.util.function.Supplier;

/**
 * Thread-safe defaults for TestPod configuration.
 * Uses InheritableThreadLocal to support parallel test execution.
 */
public final class TestPodDefaults {

    private static final InheritableThreadLocal<Context> THREAD_CONTEXT =
        new InheritableThreadLocal<>() {
            @Override
            protected Context childValue(Context parentValue) {
                // Create defensive copy for child threads
                if (parentValue == null) return null;
                return new Context(parentValue);
            }
        };

    private TestPodDefaults() {}

    // =========================================================================
    // Thread-local context management
    // =========================================================================

    public static void setClusterSupplier(Supplier<K8sCluster> supplier) {
        getOrCreateContext().clusterSupplier = supplier;
    }

    public static void setNamespaceNameSupplier(Supplier<String> supplier) {
        getOrCreateContext().namespaceNameSupplier = supplier;
    }

    public static void setSharedNamespace(TestNamespace namespace) {
        getOrCreateContext().sharedNamespace = namespace;
    }

    /**
     * Clear thread-local context.
     * MUST be called in afterAll/afterEach to prevent memory leaks.
     */
    public static void clear() {
        THREAD_CONTEXT.remove();
    }

    // =========================================================================
    // Resolution with null-safety
    // =========================================================================

    public static K8sCluster resolveCluster() {
        Context ctx = THREAD_CONTEXT.get();
        if (ctx != null && ctx.clusterSupplier != null) {
            return ctx.clusterSupplier.get();
        }
        throw new IllegalStateException(
            "No cluster configured. Use @TestPods annotation or call TestPodDefaults.setClusterSupplier()");
    }

    public static String resolveNamespaceName() {
        Context ctx = THREAD_CONTEXT.get();
        if (ctx != null && ctx.namespaceNameSupplier != null) {
            return ctx.namespaceNameSupplier.get();
        }
        return null;  // Will use default naming
    }

    public static TestNamespace getSharedNamespace() {
        Context ctx = THREAD_CONTEXT.get();
        return ctx != null ? ctx.sharedNamespace : null;
    }

    public static boolean hasClusterConfigured() {
        Context ctx = THREAD_CONTEXT.get();
        return ctx != null && ctx.clusterSupplier != null;
    }

    // =========================================================================
    // Internal context holder
    // =========================================================================

    private static Context getOrCreateContext() {
        Context ctx = THREAD_CONTEXT.get();
        if (ctx == null) {
            ctx = new Context();
            THREAD_CONTEXT.set(ctx);
        }
        return ctx;
    }

    /**
     * Internal context holding all thread-local state.
     */
    private static class Context {
        volatile Supplier<K8sCluster> clusterSupplier;
        volatile Supplier<String> namespaceNameSupplier;
        volatile TestNamespace sharedNamespace;

        Context() {}

        // Copy constructor for child threads
        Context(Context parent) {
            this.clusterSupplier = parent.clusterSupplier;
            this.namespaceNameSupplier = parent.namespaceNameSupplier;
            this.sharedNamespace = parent.sharedNamespace;
        }
    }
}
```

### TestPodsExtension.java (add cleanup)

```java
package org.testpods.junit;

import org.junit.jupiter.api.extension.*;
import org.testpods.core.pods.TestPodDefaults;

public class TestPodsExtension implements BeforeAllCallback, AfterAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        // Configure defaults for this test class
        // ... existing setup code ...
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        // IMPORTANT: Clear thread-local state to prevent memory leaks
        TestPodDefaults.clear();

        // ... existing cleanup code ...
    }
}
```

## Test Plan

### TestPodDefaultsThreadSafetyTest.java

```java
class TestPodDefaultsThreadSafetyTest {

    @AfterEach
    void cleanup() {
        TestPodDefaults.clear();
    }

    @Test
    void shouldIsolateDefaultsBetweenThreads() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<K8sCluster> thread1Cluster = new AtomicReference<>();
        AtomicReference<K8sCluster> thread2Cluster = new AtomicReference<>();

        K8sCluster clusterA = mock(K8sCluster.class);
        K8sCluster clusterB = mock(K8sCluster.class);

        // Thread 1 sets cluster A
        Thread t1 = new Thread(() -> {
            try {
                TestPodDefaults.setClusterSupplier(() -> clusterA);
                Thread.sleep(50);  // Give time for thread 2 to set its value
                thread1Cluster.set(TestPodDefaults.resolveCluster());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                TestPodDefaults.clear();
                latch.countDown();
            }
        });

        // Thread 2 sets cluster B
        Thread t2 = new Thread(() -> {
            try {
                TestPodDefaults.setClusterSupplier(() -> clusterB);
                thread2Cluster.set(TestPodDefaults.resolveCluster());
            } finally {
                TestPodDefaults.clear();
                latch.countDown();
            }
        });

        t1.start();
        t2.start();
        latch.await(5, TimeUnit.SECONDS);

        // Each thread should get its own cluster
        assertThat(thread1Cluster.get()).isSameAs(clusterA);
        assertThat(thread2Cluster.get()).isSameAs(clusterB);
    }

    @Test
    void childThreadShouldInheritParentContext() throws Exception {
        K8sCluster cluster = mock(K8sCluster.class);
        TestPodDefaults.setClusterSupplier(() -> cluster);

        AtomicReference<K8sCluster> childCluster = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Child thread should inherit parent's context
        Thread child = new Thread(() -> {
            childCluster.set(TestPodDefaults.resolveCluster());
            latch.countDown();
        });

        child.start();
        latch.await(1, TimeUnit.SECONDS);

        assertThat(childCluster.get()).isSameAs(cluster);
    }

    @Test
    void clearShouldRemoveThreadLocalState() {
        TestPodDefaults.setClusterSupplier(() -> mock(K8sCluster.class));
        assertThat(TestPodDefaults.hasClusterConfigured()).isTrue();

        TestPodDefaults.clear();

        assertThat(TestPodDefaults.hasClusterConfigured()).isFalse();
        assertThatThrownBy(TestPodDefaults::resolveCluster)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowWhenNoClusterConfigured() {
        // Ensure clean state
        TestPodDefaults.clear();

        assertThatThrownBy(TestPodDefaults::resolveCluster)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No cluster configured");
    }
}
```

## References

- Spec: `specs/refactorings/07-fix-thread-unsafe-testpoddefaults.md`
- Current implementation: `core/src/main/java/org/testpods/core/pods/TestPodDefaults.java:63-65`
- InheritableThreadLocal docs: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/InheritableThreadLocal.html
- JUnit 5 Parallel Execution: https://junit.org/junit5/docs/current/user-guide/#writing-tests-parallel-execution

---

## Validation Output

After implementation, write results to `specs/refactorings/07-fix-thread-unsafe-testpoddefaults_result.md`
