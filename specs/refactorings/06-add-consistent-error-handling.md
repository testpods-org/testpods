# Refactoring 06: Add Consistent Error Handling in Pod Lifecycle

**Priority:** High
**Effort:** Small
**Category:** Reliability / Error Handling

---

## Problem Statement

Error handling in `start()` methods is inconsistent between `DeploymentPod` and `StatefulSetPod`:

### DeploymentPod (lines 129-141) - HAS error handling:
```java
try {
    waitForReady();
} catch (Exception e) {
    // Attempts cleanup on failure
    final List<StatusDetails> delete = client.services().delete();
    delete.forEach(status -> {System.out.println(status.toString());});

    final List<StatusDetails> deploymentDelete = client.apps().deployments()
        .inNamespace(namespace.getName())
        .withName(name).delete();
    deploymentDelete.forEach(status -> {System.out.println(status.toString());});
}
```

### StatefulSetPod (spec lines 137-161) - NO error handling:
```java
// Create StatefulSet
statefulSet = createStatefulSet(client, ns);

// Create services
createHeadlessService(client, ns);
service = createNodePortService(client, ns);

// Wait for ready - NO TRY/CATCH!
waitForReady();

externalAccess = namespace.getCluster().getAccessStrategy()...
```

---

## Impact

- If `waitForReady()` times out on a StatefulSetPod, orphaned resources remain in the cluster
- Users hit quota limits from accumulated zombie resources
- Inconsistent behavior confuses developers

---

## Proposed Solution

### Option A: Extract to Base Class (Recommended)

Add error handling in `ComposableTestPod.start()`:

```java
@Override
public void start() {
    ensureNamespace();

    if (!namespace.isCreated()) {
        namespace.create();
    }

    try {
        // Create resources
        workloadManager.create(buildWorkloadConfig());
        serviceManager.create(buildServiceConfig());

        // Wait for ready
        waitForReady();

    } catch (Exception e) {
        // Clean up on failure
        cleanup();
        throw new TestPodStartException("Failed to start pod: " + name, e);
    }
}

private void cleanup() {
    try {
        if (serviceManager != null) {
            serviceManager.delete();
        }
    } catch (Exception ignored) {
        // Best effort cleanup
    }

    try {
        if (workloadManager != null) {
            workloadManager.delete();
        }
    } catch (Exception ignored) {
        // Best effort cleanup
    }
}
```

### Option B: Add to Both Classes (If not doing composition refactor)

Add identical try/catch blocks to both `DeploymentPod` and `StatefulSetPod`.

---

## Additional Improvements

1. **Use proper logging** instead of `System.out.println`:
   ```java
   private static final Logger LOG = LoggerFactory.getLogger(DeploymentPod.class);

   } catch (Exception e) {
       LOG.warn("Start failed, cleaning up resources for pod: {}", name);
       cleanup();
       throw new TestPodStartException("Failed to start pod: " + name, e);
   }
   ```

2. **Create custom exception**:
   ```java
   public class TestPodStartException extends RuntimeException {
       public TestPodStartException(String message, Throwable cause) {
           super(message, cause);
       }
   }
   ```

---

## Files to Modify

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/pods/DeploymentPod.java` | Fix existing error handling, use logging |
| `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java` | Add error handling |
| `core/src/main/java/org/testpods/core/TestPodStartException.java` | Create new exception |

If composition refactor done first:
| `core/src/main/java/org/testpods/core/pods/ComposableTestPod.java` | Add centralized error handling |

---

## Success Criteria

1. [ ] Both `DeploymentPod` and `StatefulSetPod` have error handling in `start()`
2. [ ] On failure, cleanup is attempted for all created resources
3. [ ] Proper exception with context is thrown
4. [ ] Logging used instead of `System.out.println`
5. [ ] No orphaned resources after failed starts

---

## Test Plan

```java
@Test
void shouldCleanupOnStartFailure() {
    // Create a pod that will fail to start
    TestPod pod = new GenericTestPod()
        .withName("fail-test")
        .withImage("nonexistent-image:v999")
        .waitingFor(WaitStrategy.forLogMessage("READY").withTimeout(Duration.ofSeconds(5)));

    assertThatThrownBy(() -> pod.start())
        .isInstanceOf(TestPodStartException.class);

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
```

---

## Validation Step

After implementation, the agent must:

1. **Verify error handling** - Both pod types have try/catch in start()
2. **Test cleanup** - Failed start cleans up resources
3. **Check logging** - No System.out.println, uses Logger
4. **Run tests** - `./gradlew :core:test`
5. **Document findings** - Write to `specs/refactorings/06-add-consistent-error-handling_result.md`

### Validation Output Format

```markdown
# Validation Result: Add Consistent Error Handling

## Implementation Summary
- Files modified: [list]

## Verification
| Check | DeploymentPod | StatefulSetPod |
|-------|---------------|----------------|
| Has try/catch in start() | [Y/N] | [Y/N] |
| Cleanup on failure | [Y/N] | [Y/N] |
| Uses Logger | [Y/N] | [Y/N] |
| Throws TestPodStartException | [Y/N] | [Y/N] |

## Test Results
- Cleanup test: [Pass/Fail]
- No orphaned resources: [Y/N]

## Deviations from Plan
[List any deviations and reasoning]
```
