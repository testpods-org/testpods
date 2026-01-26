# Fix Thread-Unsafe Static State in TestPodDefaults

## Executive Summary

### Problem Statement

`TestPodDefaults.setClusterSupplier()` and related methods use static fields that are shared across all threads. This creates thread-safety issues when running JUnit 5 tests in parallel:

- Tests running in parallel may get wrong cluster configurations
- Race conditions when multiple test classes set different defaults
- Flaky tests that pass/fail randomly depending on execution order
- Resource leaks when pods use wrong namespace

Current implementation uses unsafe static fields:
```java
private static Supplier<K8sCluster> clusterSupplier;
private static TestNamespace sharedNamespace;
```

### Solution Statement

Replace static fields with `InheritableThreadLocal` to isolate defaults per test thread. This ensures:
- Each test thread gets its own copy of configuration
- Child threads (parallel assertions, async operations) inherit parent values
- Memory safety via `remove()` method prevents leaks
- Backward compatibility with existing single-threaded tests

### Solution Properties

- **Thread isolation**: Uses `InheritableThreadLocal` for all mutable state
- **Child thread inheritance**: Overrides `childValue()` for defensive copying
- **Memory safety**: Requires explicit `clear()` call in `afterAll()` lifecycle
- **Backward compatible**: Existing single-threaded tests work unchanged
- **Context pattern**: Groups all thread-local state into single Context object

---

## Background Research

### Why InheritableThreadLocal over ThreadLocal

- JUnit 5 may spawn child threads for parallel streams or async assertions
- `InheritableThreadLocal` propagates values to child threads automatically
- `ThreadLocal` would cause child threads to see null values unexpectedly

### JUnit 5 Extension Lifecycle

- `afterAll()` callback is the appropriate place to call `clear()`
- Extension must implement `AfterAllCallback` interface
- Cleanup prevents memory leaks in thread pool executors

### References

- InheritableThreadLocal docs: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/InheritableThreadLocal.html
- JUnit 5 Parallel Execution: https://junit.org/junit5/docs/current/user-guide/#writing-tests-parallel-execution
- Current implementation: `core/src/main/java/org/testpods/core/pods/TestPodDefaults.java`

---

<!-- SHOWCASE_PLACEHOLDER -->

---

## Implementation Steps

### Status: ✅ | Step 1: Refactor TestPodDefaults to use InheritableThreadLocal

#### Step 1 Purpose

Replace thread-unsafe static fields with thread-safe `InheritableThreadLocal` storage to enable parallel test execution.

#### Step 1 Description

**File to modify:** `core/src/main/java/org/testpods/core/pods/TestPodDefaults.java`

**Changes required:**

1. Create internal `Context` class to hold all thread-local state:
   - Type signature: `private static class Context`
   - Fields: `clusterSupplier`, `namespaceNameSupplier`, `sharedNamespace` (all volatile)
   - Copy constructor for child thread inheritance

2. Replace static fields with single `InheritableThreadLocal<Context>`:
   - Type signature: `private static final InheritableThreadLocal<Context> THREAD_CONTEXT`
   - Override `childValue(Context)` to create defensive copy

3. Update setter methods to use `getOrCreateContext()` pattern:
   - `setClusterSupplier(Supplier<K8sCluster>)` - stores in context
   - `setNamespaceNameSupplier(Supplier<String>)` - stores in context
   - `setSharedNamespace(TestNamespace)` - stores in context

4. Update getter/resolution methods with null-safety:
   - `resolveCluster()` - throws `IllegalStateException` if no supplier configured
   - `resolveNamespaceName()` - returns null for default naming
   - `getSharedNamespace()` - returns null if not set
   - `hasClusterConfigured()` - returns boolean for checking state

5. Add `clear()` method:
   - Type signature: `public static void clear()`
   - Pseudo-code: call `THREAD_CONTEXT.remove()` to clean up thread-local state

6. Add private helper:
   - Type signature: `private static Context getOrCreateContext()`
   - Pseudo-code: get context from thread-local, create new if null, return context

---

### Status: ✅ | Step 2: Update TestPodsExtension to cleanup thread-local state

#### Step 2 Purpose

Ensure thread-local state is cleaned up after test execution to prevent memory leaks in thread pool executors.

#### Step 2 Description

**File to modify:** `core/src/main/java/org/testpods/junit/TestPodsExtension.java`

**Changes required:**

1. Ensure class implements `AfterAllCallback` interface

2. In `afterAll(ExtensionContext)` method:
   - Add call to `TestPodDefaults.clear()` before or after existing cleanup
   - Pseudo-code: at end of afterAll(), call TestPodDefaults.clear() to remove thread-local state

---

### Status: ✅ | Step 3: Add thread safety tests

#### Step 3 Purpose

Verify that the thread-local implementation correctly isolates state between threads and properly inherits to child threads.

#### Step 3 Description

**File to create:** `core/src/test/java/org/testpods/core/pods/TestPodDefaultsThreadSafetyTest.java`

**Test cases to implement:**

1. `shouldIsolateDefaultsBetweenThreads()`:
   - Create two threads, each setting different cluster suppliers
   - Verify each thread resolves its own cluster (not the other's)
   - Use `CountDownLatch` for synchronization

2. `childThreadShouldInheritParentContext()`:
   - Set cluster supplier in parent thread
   - Spawn child thread and verify it inherits parent's cluster
   - Confirms `InheritableThreadLocal` behavior

3. `clearShouldRemoveThreadLocalState()`:
   - Set cluster supplier
   - Verify `hasClusterConfigured()` returns true
   - Call `clear()`
   - Verify `hasClusterConfigured()` returns false
   - Verify `resolveCluster()` throws `IllegalStateException`

4. `shouldThrowWhenNoClusterConfigured()`:
   - Ensure clean state via `clear()`
   - Verify `resolveCluster()` throws `IllegalStateException` with descriptive message

**Test utilities needed:**
- `@AfterEach` cleanup calling `TestPodDefaults.clear()`
- Mock `K8sCluster` instances for testing
- `CountDownLatch` and `AtomicReference` for thread coordination

---

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

---

## Validation Output

After implementation, write results to `specs/refactorings/07-fix-thread-unsafe-testpoddefaults_result.md`
