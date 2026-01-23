# Refactoring 07: Fix Thread-Unsafe Static State in TestPodDefaults

**Priority:** High
**Effort:** Small
**Category:** Bug Fix / Thread Safety

---

## Problem Statement

`TestPodDefaults.setClusterSupplier()` and related methods use static state that is shared across all threads. This causes problems with JUnit 5 parallel test execution:

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

---

## Impact

- Tests running in parallel may get wrong cluster configurations
- Race conditions when multiple test classes set different defaults
- Flaky tests that pass/fail randomly depending on execution order
- Resource leaks when pods use wrong namespace

---

## Proposed Solution

### Option A: Use InheritableThreadLocal (Recommended)

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

    public static void setSharedNamespace(TestNamespace namespace) {
        sharedNamespace.set(namespace);
    }

    public static TestNamespace getSharedNamespace() {
        return sharedNamespace.get();
    }

    /**
     * Clear all defaults for current thread. Call in @AfterEach or @AfterAll.
     */
    public static void clear() {
        clusterSupplier.remove();
        sharedNamespace.remove();
    }
}
```

### Option B: Pass Through Extension Context

Use JUnit 5's `ExtensionContext.Store` for test-scoped state:

```java
public class TestPodsExtension implements BeforeAllCallback, AfterAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(TestPodsExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        K8sCluster cluster = createCluster();
        context.getStore(NAMESPACE).put("cluster", cluster);
    }

    public static K8sCluster getCluster(ExtensionContext context) {
        return context.getStore(NAMESPACE).get("cluster", K8sCluster.class);
    }
}
```

This requires pods to have access to the extension context, which may need API changes.

---

## Recommendation

**Use Option A (InheritableThreadLocal)** because:
1. Minimal API changes required
2. Works with existing pod creation patterns
3. InheritableThreadLocal ensures child threads (e.g., in parallel streams) inherit values
4. Easy to clear state after tests

---

## Files to Modify

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/pods/TestPodDefaults.java` | Change to ThreadLocal |
| `core/src/main/java/org/testpods/junit/TestPodsExtension.java` | Add cleanup in afterAll |
| `core/src/test/java/org/testpods/core/pods/TestPodDefaultsTest.java` | Add thread safety tests |

---

## Success Criteria

1. [ ] `TestPodDefaults` uses `InheritableThreadLocal` for all state
2. [ ] `clear()` method exists to clean up state
3. [ ] JUnit extension calls `clear()` in `afterAll()`
4. [ ] Parallel tests don't interfere with each other's defaults
5. [ ] Existing single-threaded tests continue to work

---

## Test Plan

```java
class TestPodDefaultsThreadSafetyTest {

    @Test
    void shouldIsolateDefaultsBetweenThreads() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<K8sCluster> thread1Cluster = new AtomicReference<>();
        AtomicReference<K8sCluster> thread2Cluster = new AtomicReference<>();

        // Thread 1 sets cluster A
        Thread t1 = new Thread(() -> {
            K8sCluster clusterA = mock(K8sCluster.class);
            TestPodDefaults.setClusterSupplier(() -> clusterA);
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            thread1Cluster.set(TestPodDefaults.resolveCluster());
            latch.countDown();
        });

        // Thread 2 sets cluster B
        Thread t2 = new Thread(() -> {
            K8sCluster clusterB = mock(K8sCluster.class);
            TestPodDefaults.setClusterSupplier(() -> clusterB);
            thread2Cluster.set(TestPodDefaults.resolveCluster());
            latch.countDown();
        });

        t1.start();
        t2.start();
        latch.await();

        // Each thread should get its own cluster
        assertThat(thread1Cluster.get()).isNotSameAs(thread2Cluster.get());
    }

    @Test
    void clearShouldRemoveThreadLocalState() {
        TestPodDefaults.setClusterSupplier(() -> mock(K8sCluster.class));
        TestPodDefaults.clear();

        assertThatThrownBy(TestPodDefaults::resolveCluster)
            .isInstanceOf(IllegalStateException.class);
    }
}
```

---

## Validation Step

After implementation, the agent must:

1. **Verify ThreadLocal usage** - All static fields use InheritableThreadLocal
2. **Test isolation** - Parallel threads have isolated state
3. **Test cleanup** - clear() properly removes state
4. **Run parallel tests** - `./gradlew :core:test --parallel`
5. **Document findings** - Write to `specs/refactorings/07-fix-thread-unsafe-testpoddefaults_result.md`

### Validation Output Format

```markdown
# Validation Result: Fix Thread-Unsafe TestPodDefaults

## Implementation Summary
- Files modified: [list]

## Verification
| Check | Result |
|-------|--------|
| clusterSupplier uses InheritableThreadLocal | [Y/N] |
| sharedNamespace uses InheritableThreadLocal | [Y/N] |
| clear() method exists | [Y/N] |
| Extension calls clear() in afterAll | [Y/N] |

## Thread Safety Tests
- Isolation test: [Pass/Fail]
- Clear test: [Pass/Fail]
- Parallel execution: [Pass/Fail]

## Deviations from Plan
[List any deviations and reasoning]
```
