# Refactoring 01: Extract WorkloadManager Component

**Priority:** High
**Effort:** Large
**Category:** Architecture / Composition Over Inheritance

---

## Problem Statement

The current hierarchy has significant code duplication between `DeploymentPod` and `StatefulSetPod`:

```
TestPod (interface)
    └── BaseTestPod (abstract - common state)
            ├── DeploymentPod (abstract - Deployment-specific)
            │       └── GenericTestPod, etc.
            └── StatefulSetPod (abstract - StatefulSet-specific)
                    └── PostgreSQLPod, MongoDBPod, etc.
```

Both `DeploymentPod` and `StatefulSetPod` have:
- Nearly identical `start()` methods (~70% shared structure)
- Nearly identical `stop()` methods
- Nearly identical `isRunning()` / `isReady()` patterns
- Duplicated customizer lists (`serviceCustomizers`, etc.)

This means:
- Bug fixes must be applied twice
- New features get inconsistent implementations
- Adding new workload types (Job, DaemonSet) requires more duplication

---

## Design Constraints

### MUST Preserve

1. **BaseTestPod remains** - It provides essential cross-cutting concerns:
   - State management (name, namespace, labels, annotations)
   - Fluent API foundation (`self()` pattern)
   - Mid-level customization (init containers, sidecars)
   - Common operations (`getLogs()`, `exec()`, `waitForReady()`)
   - Helper methods (`ensureNamespace()`, `getClient()`, `buildLabels()`)

2. **Fluent interface coherence** - The developer API must remain clean:
   ```java
   // This style MUST continue to work
   PostgreSQLPod postgres = new PostgreSQLPod()
       .withName("db")
       .withDatabase("myapp")
       .withPersistentVolume("1Gi")
       .withInitContainer(init -> init.withImage("busybox"));
   ```

3. **Internal composition** - WorkloadManager is an internal implementation detail, NOT exposed to pod users.

---

## Proposed Solution

### Target Architecture

```
TestPod (interface)
    └── BaseTestPod (abstract - common state, fluent API)
            └── ComposableTestPod (abstract - workload composition)
                    ├── PostgreSQLPod (composes StatefulSetManager)
                    ├── GenericTestPod (composes DeploymentManager)
                    └── Future: BatchJobPod (composes JobManager)
```

### WorkloadManager Interface

```java
package org.testpods.core.workload;

/**
 * Manages the Kubernetes workload resource (Deployment, StatefulSet, Job, etc.).
 * This is an internal implementation detail, not exposed to pod users.
 */
public interface WorkloadManager {

    /**
     * Create the workload in the cluster.
     * @param config Workload configuration from the pod
     */
    void create(WorkloadConfig config);

    /**
     * Delete the workload from the cluster.
     */
    void delete();

    /**
     * Check if the workload exists and has running replicas.
     */
    boolean isRunning();

    /**
     * Check if the workload has all desired replicas ready.
     */
    boolean isReady();

    /**
     * Get the workload name for label selectors.
     */
    String getName();
}
```

### WorkloadConfig Record

```java
package org.testpods.core.workload;

/**
 * Configuration passed from pod to workload manager.
 */
public record WorkloadConfig(
    String name,
    String namespace,
    Map<String, String> labels,
    Map<String, String> annotations,
    Container mainContainer,
    PodSpecBuilder podSpec,
    KubernetesClient client
) {}
```

### StatefulSetManager Implementation

```java
package org.testpods.core.workload;

public class StatefulSetManager implements WorkloadManager {

    private StatefulSet statefulSet;
    private final String headlessServiceName;
    private WorkloadConfig config;

    // Optional PVC configuration
    private boolean withPersistentVolume = false;
    private String storageSize = "1Gi";
    private String storageClassName;

    public StatefulSetManager withPersistentVolume(String size) {
        this.withPersistentVolume = true;
        this.storageSize = size;
        return this;
    }

    @Override
    public void create(WorkloadConfig config) {
        this.config = config;
        // Build and create StatefulSet using config
        // (Move code from StatefulSetPod.createStatefulSet() here)
    }

    @Override
    public void delete() {
        // Move code from StatefulSetPod.stop() here
    }

    // ... isRunning(), isReady() implementations
}
```

### DeploymentManager Implementation

```java
package org.testpods.core.workload;

public class DeploymentManager implements WorkloadManager {

    private Deployment deployment;
    private WorkloadConfig config;

    @Override
    public void create(WorkloadConfig config) {
        this.config = config;
        // Build and create Deployment using config
        // (Move code from DeploymentPod.buildDeployment() here)
    }

    // ... other methods
}
```

### ComposableTestPod Base Class

```java
package org.testpods.core.pods;

/**
 * Base class for pods that compose workload and service managers.
 * Extends BaseTestPod to retain all common functionality.
 */
public abstract class ComposableTestPod<SELF extends ComposableTestPod<SELF>>
    extends BaseTestPod<SELF> {

    protected WorkloadManager workloadManager;
    protected ServiceManager serviceManager;

    @Override
    public void start() {
        ensureNamespace();

        if (!namespace.isCreated()) {
            namespace.create();
        }

        // Delegate to managers
        WorkloadConfig config = buildWorkloadConfig();
        workloadManager.create(config);
        serviceManager.create(buildServiceConfig());

        waitForReady();
    }

    @Override
    public void stop() {
        if (serviceManager != null) {
            serviceManager.delete();
        }
        if (workloadManager != null) {
            workloadManager.delete();
        }
    }

    @Override
    public boolean isRunning() {
        return workloadManager != null && workloadManager.isRunning();
    }

    @Override
    public boolean isReady() {
        return workloadManager != null && workloadManager.isReady();
    }

    protected abstract Container buildMainContainer();
    protected abstract WorkloadManager createWorkloadManager();
    protected abstract ServiceManager createServiceManager();
}
```

---

## Implementation Steps

1. **Create interfaces and records**
   - `WorkloadManager` interface
   - `WorkloadConfig` record
   - `ServiceConfig` record (for ServiceManager - see task 02)

2. **Create manager implementations**
   - `StatefulSetManager` - extract code from `StatefulSetPod`
   - `DeploymentManager` - extract code from `DeploymentPod`

3. **Create ComposableTestPod**
   - Extends `BaseTestPod`
   - Composes WorkloadManager and ServiceManager
   - Implements common `start()`, `stop()`, `isRunning()`, `isReady()`

4. **Migrate concrete pods**
   - Update `GenericTestPod` to extend `ComposableTestPod`
   - Update `PostgreSQLPod` to extend `ComposableTestPod`
   - Update `MongoDBPod` to extend `ComposableTestPod`

5. **Deprecate old base classes**
   - Mark `DeploymentPod` and `StatefulSetPod` as `@Deprecated`
   - Keep them for backwards compatibility temporarily

6. **Update tests**

---

## Files to Modify

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/workload/WorkloadManager.java` | Create new |
| `core/src/main/java/org/testpods/core/workload/WorkloadConfig.java` | Create new |
| `core/src/main/java/org/testpods/core/workload/StatefulSetManager.java` | Create new |
| `core/src/main/java/org/testpods/core/workload/DeploymentManager.java` | Create new |
| `core/src/main/java/org/testpods/core/pods/ComposableTestPod.java` | Create new |
| `core/src/main/java/org/testpods/core/pods/GenericTestPod.java` | Modify to extend ComposableTestPod |
| `core/src/main/java/org/testpods/core/pods/DeploymentPod.java` | Deprecate |
| `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java` | Deprecate |

---

## Success Criteria

1. [ ] `WorkloadManager` interface exists with `create()`, `delete()`, `isRunning()`, `isReady()` methods
2. [ ] `StatefulSetManager` creates StatefulSets correctly
3. [ ] `DeploymentManager` creates Deployments correctly
4. [ ] `ComposableTestPod` extends `BaseTestPod` and composes managers
5. [ ] Existing fluent API still works unchanged:
   ```java
   new PostgreSQLPod().withName("db").withDatabase("test").start();
   ```
6. [ ] All existing tests pass without modification
7. [ ] Code duplication between workload types is eliminated (DRY)
8. [ ] Adding a new workload type (e.g., JobManager) requires only the manager, not a new base class

---

## Validation Step

After implementation, the agent must:

1. **Compare architecture** - Verify the class hierarchy matches the target design
2. **Run all tests** - `./gradlew :core:test`
3. **Check fluent API** - Ensure the developer-facing API is unchanged
4. **Measure duplication** - Verify no duplicate lifecycle code between managers
5. **Document findings** - Write comparison to `specs/refactorings/01-extract-workload-manager_result.md`

### Validation Output File Format

```markdown
# Validation Result: Extract WorkloadManager

## Implementation Summary
- Files created: [list]
- Files modified: [list]

## Architecture Comparison
| Aspect | Original Goal | Actual Implementation | Match? |
|--------|---------------|----------------------|--------|
| BaseTestPod preserved | Yes | [Y/N] | |
| Fluent API unchanged | Yes | [Y/N] | |
| Duplication eliminated | Yes | [Y/N] | |

## Test Results
- Tests run: X
- Passed: X
- Failed: X

## Deviations from Plan
[List any deviations and reasoning]

## Recommendations
[Any follow-up work needed]
```
