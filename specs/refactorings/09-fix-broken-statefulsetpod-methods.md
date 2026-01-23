# Refactoring 09: Fix Broken StatefulSetPod Methods

**Priority:** Critical
**Effort:** Small
**Category:** Bug Fix

---

## Problem Statement

Several methods in `StatefulSetPod.java` contain commented-out implementations or return invalid values:

### getExternalHost() and getExternalPort()

The spec (03-statefulset-pod-base-class.md) shows these should use `externalAccess`:

```java
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
```

However, the actual implementation may have these commented out or returning null/-1.

### getDefaultWaitStrategy()

The method is referenced in `BaseTestPod.waitForReady()` but may not be properly implemented in `StatefulSetPod`.

---

## Impact

- **Critical:** Any pod extending `StatefulSetPod` cannot provide external access info
- Developers get null/0 when calling `getExternalHost()`/`getExternalPort()`
- PostgreSQLPod, MongoDBPod, and other database pods are broken

---

## Proposed Solution

### Fix getExternalHost() and getExternalPort()

Ensure these methods:
1. Check if `externalAccess` is set
2. Throw clear exception if pod not started
3. Return correct values from `externalAccess`

```java
@Override
public String getExternalHost() {
    if (externalAccess == null) {
        throw new IllegalStateException(
            "Pod '" + name + "' not started. Call start() before accessing external endpoint.");
    }
    return externalAccess.host();
}

@Override
public int getExternalPort() {
    if (externalAccess == null) {
        throw new IllegalStateException(
            "Pod '" + name + "' not started. Call start() before accessing external endpoint.");
    }
    return externalAccess.port();
}
```

### Fix getDefaultWaitStrategy()

Implement appropriate default for StatefulSet pods:

```java
@Override
protected WaitStrategy getDefaultWaitStrategy() {
    return WaitStrategy.forReadinessProbe()
        .withTimeout(Duration.ofMinutes(2));  // StatefulSets may take longer
}
```

### Ensure externalAccess is Set in start()

The `start()` method must set `externalAccess` after service creation:

```java
@Override
public void start() {
    // ... create resources ...

    // Wait for ready
    waitForReady();

    // Get external access info - MUST happen after pod is ready
    externalAccess = namespace.getCluster().getAccessStrategy()
        .getExternalEndpoint(this, getInternalPort());
}
```

---

## Files to Modify

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java` | Fix all broken methods |

---

## Success Criteria

1. [ ] `getExternalHost()` returns valid hostname after start()
2. [ ] `getExternalPort()` returns valid port (> 0) after start()
3. [ ] Both methods throw clear exception before start()
4. [ ] `getDefaultWaitStrategy()` returns non-null strategy
5. [ ] `externalAccess` is set at end of `start()`
6. [ ] PostgreSQLPod can provide connection info after starting

---

## Test Plan

```java
@Test
void shouldProvideExternalAccessAfterStart() {
    StatefulSetPod pod = new TestStatefulSetPod();
    pod.start();

    assertThat(pod.getExternalHost()).isNotEmpty();
    assertThat(pod.getExternalPort()).isGreaterThan(0);
}

@Test
void shouldThrowBeforeStart() {
    StatefulSetPod pod = new TestStatefulSetPod();

    assertThatThrownBy(pod::getExternalHost)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not started");

    assertThatThrownBy(pod::getExternalPort)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not started");
}

@Test
void shouldHaveDefaultWaitStrategy() {
    StatefulSetPod pod = new TestStatefulSetPod();
    WaitStrategy strategy = pod.getDefaultWaitStrategy();

    assertThat(strategy).isNotNull();
}
```

---

## Validation Step

After implementation, the agent must:

1. **Test external access** - Verify host/port return valid values after start
2. **Test exceptions** - Verify clear exceptions before start
3. **Test default strategy** - Verify non-null wait strategy
4. **Run integration test** - Start a StatefulSet pod and verify access
5. **Document findings** - Write to `specs/refactorings/09-fix-broken-statefulsetpod-methods_result.md`

### Validation Output Format

```markdown
# Validation Result: Fix Broken StatefulSetPod Methods

## Implementation Summary
- Files modified: [list]

## Verification
| Method | Before | After | Working? |
|--------|--------|-------|----------|
| getExternalHost() | [null/empty] | [valid] | [Y/N] |
| getExternalPort() | [0/-1] | [valid port] | [Y/N] |
| getDefaultWaitStrategy() | [null] | [WaitStrategy] | [Y/N] |

## Test Results
- External access test: [Pass/Fail]
- Exception test: [Pass/Fail]
- Wait strategy test: [Pass/Fail]

## Deviations from Plan
[List any deviations and reasoning]
```
