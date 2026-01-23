# Plan: Add Consistent Error Handling in Pod Lifecycle

**Priority:** High
**Effort:** Small
**Category:** Reliability / Error Handling
**Phase:** 1 - Critical Bug Fixes (Do First)

---

## Overview

Standardize error handling in `start()` methods across `DeploymentPod` and `StatefulSetPod` to ensure cleanup on failure and prevent orphaned Kubernetes resources.

## Problem Statement

Error handling in `start()` methods is inconsistent:

### DeploymentPod (lines 129-141) - HAS error handling (but uses System.out):
```java
try {
    waitForReady();
} catch (Exception e) {
    // Attempts cleanup on failure
    final List<StatusDetails> delete = client.services().delete();
    delete.forEach(status -> {System.out.println(status.toString());});
    // ... similar for deployment
}
```

### StatefulSetPod (spec lines 137-161) - NO error handling:
```java
statefulSet = createStatefulSet(client, ns);
createHeadlessService(client, ns);
service = createNodePortService(client, ns);
waitForReady();  // NO TRY/CATCH!
```

### Impact
- If `waitForReady()` times out on a StatefulSetPod, orphaned resources remain in the cluster
- Users hit quota limits from accumulated zombie resources
- Inconsistent behavior confuses developers

## Proposed Solution

### Option A: Centralized Error Handling (Recommended)

Extract to base class using ResourceTracker pattern:

```java
// BaseTestPod.java or ComposableTestPod.java

@Override
public void start() {
    ensureNamespace();

    if (!namespace.isCreated()) {
        namespace.create();
    }

    try {
        doStart();  // Template method for subclass-specific logic
        waitForReady();

    } catch (Exception e) {
        cleanup();
        throw new TestPodStartException("Failed to start pod: " + name, e);
    }
}

private void cleanup() {
    LOG.warn("Start failed, cleaning up resources for pod: {}", name);

    try {
        if (serviceManager != null) {
            serviceManager.delete();
        }
    } catch (Exception ignored) {
        LOG.debug("Cleanup of service failed", ignored);
    }

    try {
        if (workloadManager != null) {
            workloadManager.delete();
        }
    } catch (Exception ignored) {
        LOG.debug("Cleanup of workload failed", ignored);
    }
}

protected abstract void doStart();
```

### Custom Exception

```java
package org.testpods.core;

public class TestPodStartException extends RuntimeException {

    public TestPodStartException(String message, Throwable cause) {
        super(message, cause);
    }

    public TestPodStartException(String message) {
        super(message);
    }
}
```

## Technical Considerations

- **Cleanup Order:** Delete in reverse order of creation (services before workload)
- **Best-Effort Cleanup:** Don't throw from cleanup - log and continue
- **Proper Logging:** Replace `System.out.println` with SLF4J Logger
- **Exception Wrapping:** Wrap original exception for debugging context
- **Idempotent Cleanup:** Handle case where resources weren't created yet

## Acceptance Criteria

### Functional Requirements
- [ ] Both `DeploymentPod` and `StatefulSetPod` have error handling in `start()`
- [ ] On failure, cleanup is attempted for all created resources
- [ ] Proper exception with context is thrown (`TestPodStartException`)
- [ ] No orphaned resources after failed starts

### Non-Functional Requirements
- [ ] Logging used instead of `System.out.println`
- [ ] SLF4J Logger at class level

### Quality Gates
- [ ] Test verifies cleanup on start failure
- [ ] Test verifies resources are deleted
- [ ] All existing tests pass

## Files to Modify

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/TestPodStartException.java` | Create new exception |
| `core/src/main/java/org/testpods/core/pods/DeploymentPod.java:129-141` | Fix error handling, use Logger |
| `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java:118-167` | Add error handling |
| `core/src/main/java/org/testpods/core/pods/BaseTestPod.java` | Add Logger constant |

## Test Plan

### CleanupOnFailureTest.java

```java
@Test
void shouldCleanupOnStartFailure() {
    // Create a pod that will fail to start
    TestPod pod = new GenericTestPod()
        .withName("fail-test")
        .withImage("nonexistent-image:v999")
        .waitingFor(WaitStrategy.forLogMessage("READY").withTimeout(Duration.ofSeconds(5)));

    assertThatThrownBy(() -> pod.start())
        .isInstanceOf(TestPodStartException.class)
        .hasMessageContaining("fail-test");

    // Verify no resources left behind
    KubernetesClient client = getClient();
    assertThat(client.apps().deployments()
        .inNamespace(ns)
        .withName("fail-test")
        .get()).isNull();

    assertThat(client.services()
        .inNamespace(ns)
        .withName("fail-test")
        .get()).isNull();
}

@Test
void shouldCleanupStatefulSetOnStartFailure() {
    TestPod pod = new MongoDBPod()
        .withName("fail-mongo")
        .waitingFor(WaitStrategy.forLogMessage("NEVER").withTimeout(Duration.ofSeconds(5)));

    assertThatThrownBy(() -> pod.start())
        .isInstanceOf(TestPodStartException.class);

    KubernetesClient client = getClient();
    assertThat(client.apps().statefulSets()
        .inNamespace(ns)
        .withName("fail-mongo")
        .get()).isNull();

    assertThat(client.services()
        .inNamespace(ns)
        .withName("fail-mongo")
        .get()).isNull();
}
```

## MVP

### TestPodStartException.java

```java
package org.testpods.core;

/**
 * Thrown when a TestPod fails to start.
 * Contains context about which pod failed and the underlying cause.
 */
public class TestPodStartException extends RuntimeException {

    private final String podName;

    public TestPodStartException(String message, Throwable cause) {
        super(message, cause);
        this.podName = extractPodName(message);
    }

    public TestPodStartException(String podName, String message, Throwable cause) {
        super("Failed to start pod '" + podName + "': " + message, cause);
        this.podName = podName;
    }

    public String getPodName() {
        return podName;
    }

    private static String extractPodName(String message) {
        // Best effort extraction
        if (message != null && message.contains("'")) {
            int start = message.indexOf("'") + 1;
            int end = message.indexOf("'", start);
            if (end > start) {
                return message.substring(start, end);
            }
        }
        return "unknown";
    }
}
```

### DeploymentPod.java (updated start method)

```java
private static final Logger LOG = LoggerFactory.getLogger(DeploymentPod.class);

@Override
public void start() {
    ensureNamespace();

    if (!namespace.isCreated()) {
        namespace.create();
    }

    KubernetesClient client = getClient();
    String ns = namespace.getName();

    try {
        // Build and create Deployment
        this.deployment = buildDeployment();
        client.apps().deployments()
            .inNamespace(ns)
            .resource(deployment)
            .create();

        // Build and create Service
        this.service = buildService();
        client.services()
            .inNamespace(ns)
            .resource(service)
            .create();

        // Wait for ready
        waitForReady();

        // Set external access
        externalAccess = namespace.getCluster().getAccessStrategy()
            .getExternalEndpoint(this, getInternalPort());

    } catch (Exception e) {
        LOG.warn("Start failed for pod '{}', cleaning up resources", name);
        cleanup(client, ns);
        throw new TestPodStartException(name, e.getMessage(), e);
    }
}

private void cleanup(KubernetesClient client, String ns) {
    // Delete in reverse order of creation
    try {
        if (service != null) {
            client.services().inNamespace(ns).withName(name).delete();
            LOG.debug("Deleted service: {}", name);
        }
    } catch (Exception e) {
        LOG.debug("Failed to delete service '{}': {}", name, e.getMessage());
    }

    try {
        if (deployment != null) {
            client.apps().deployments().inNamespace(ns).withName(name).delete();
            LOG.debug("Deleted deployment: {}", name);
        }
    } catch (Exception e) {
        LOG.debug("Failed to delete deployment '{}': {}", name, e.getMessage());
    }

    this.service = null;
    this.deployment = null;
}
```

### StatefulSetPod.java (add similar error handling)

```java
private static final Logger LOG = LoggerFactory.getLogger(StatefulSetPod.class);

@Override
public void start() {
    ensureNamespace();

    if (!namespace.isCreated()) {
        namespace.create();
    }

    KubernetesClient client = getClient();
    String ns = namespace.getName();

    try {
        // Create StatefulSet
        this.statefulSet = createStatefulSet(client, ns);

        // Create services
        createHeadlessService(client, ns);
        this.service = createNodePortService(client, ns);

        // Wait for ready
        waitForReady();

        // Set external access
        externalAccess = namespace.getCluster().getAccessStrategy()
            .getExternalEndpoint(this, getInternalPort());

    } catch (Exception e) {
        LOG.warn("Start failed for pod '{}', cleaning up resources", name);
        cleanup(client, ns);
        throw new TestPodStartException(name, e.getMessage(), e);
    }
}

private void cleanup(KubernetesClient client, String ns) {
    try {
        if (service != null) {
            client.services().inNamespace(ns).withName(name).delete();
        }
    } catch (Exception e) {
        LOG.debug("Failed to delete service '{}': {}", name, e.getMessage());
    }

    try {
        // Headless service
        client.services().inNamespace(ns).withName(name + "-headless").delete();
    } catch (Exception e) {
        LOG.debug("Failed to delete headless service '{}': {}", name, e.getMessage());
    }

    try {
        if (statefulSet != null) {
            client.apps().statefulSets().inNamespace(ns).withName(name).delete();
        }
    } catch (Exception e) {
        LOG.debug("Failed to delete statefulset '{}': {}", name, e.getMessage());
    }

    this.service = null;
    this.statefulSet = null;
}
```

## References

- Spec: `specs/refactorings/06-add-consistent-error-handling.md`
- DeploymentPod current: `core/src/main/java/org/testpods/core/pods/DeploymentPod.java:129-141`
- Best practices: Try-with-resources and cleanup-on-failure patterns

---

## Validation Output

After implementation, write results to `specs/refactorings/06-add-consistent-error-handling_result.md`
