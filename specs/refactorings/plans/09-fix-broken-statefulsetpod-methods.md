# Plan: Fix Broken StatefulSetPod Methods

**Priority:** Critical
**Effort:** Small
**Category:** Bug Fix
**Phase:** 1 - Critical Bug Fixes (Do First)

---

## Overview

Fix broken methods in `StatefulSetPod.java` that return null/invalid values, preventing any StatefulSet-based pods (PostgreSQLPod, MongoDBPod) from functioning correctly.

## Problem Statement

Several methods in `StatefulSetPod.java` are either commented out or return invalid values:
- `getExternalHost()` returns `null`
- `getExternalPort()` returns `-1` or `0`
- `getDefaultWaitStrategy()` returns `null`
- `externalAccess` field is never set in `start()`

This makes all StatefulSet-backed database pods completely non-functional.

## Proposed Solution

### 1. Fix getExternalHost() and getExternalPort()

Implement proper null-checks with clear error messages:

```java
// StatefulSetPod.java

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

### 2. Fix getDefaultWaitStrategy()

Return a sensible default for StatefulSet pods:

```java
@Override
protected WaitStrategy getDefaultWaitStrategy() {
    return WaitStrategy.forReadinessProbe()
        .withTimeout(Duration.ofMinutes(2));  // StatefulSets may take longer
}
```

### 3. Set externalAccess in start()

Ensure `start()` sets `externalAccess` after the pod is ready:

```java
@Override
public void start() {
    // ... existing resource creation ...

    waitForReady();

    // Set external access info AFTER pod is ready
    externalAccess = namespace.getCluster().getAccessStrategy()
        .getExternalEndpoint(this, getInternalPort());
}
```

## Technical Considerations

- **Backward Compatibility:** Existing code calling these methods before `start()` will now get clear exceptions instead of silent null/0 values
- **Thread Safety:** The `externalAccess` field should be volatile to ensure visibility across threads
- **Performance:** No impact - just fixing broken implementations

## Acceptance Criteria

### Functional Requirements
- [ ] `getExternalHost()` returns valid hostname after `start()`
- [ ] `getExternalPort()` returns valid port (> 0) after `start()`
- [ ] Both methods throw `IllegalStateException` with clear message before `start()`
- [ ] `getDefaultWaitStrategy()` returns non-null `WaitStrategy`
- [ ] `externalAccess` is set at end of `start()`
- [ ] PostgreSQLPod and MongoDBPod can provide connection info after starting

### Quality Gates
- [ ] All existing tests pass
- [ ] New tests added for exception scenarios
- [ ] Test coverage for before/after start() state

## Files to Modify

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java:207-234` | Fix getExternalHost(), getExternalPort(), getDefaultWaitStrategy() |
| `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java:118-167` | Ensure externalAccess is set in start() |

## Test Plan

### StatefulSetPodTest.java

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

## MVP

### StatefulSetPod.java (key changes)

```java
public abstract class StatefulSetPod<SELF extends StatefulSetPod<SELF>> extends BaseTestPod<SELF> {

    protected volatile HostAndPort externalAccess;  // Add volatile

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

    @Override
    protected WaitStrategy getDefaultWaitStrategy() {
        return WaitStrategy.forReadinessProbe()
            .withTimeout(Duration.ofMinutes(2));
    }
}
```

## References

- Spec: `specs/refactorings/09-fix-broken-statefulsetpod-methods.md`
- Current implementation: `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java:207-234`
- Related: `core/src/main/java/org/testpods/core/pods/DeploymentPod.java` (for comparison)

---

## Validation Output

After implementation, write results to `specs/refactorings/09-fix-broken-statefulsetpod-methods_result.md`
