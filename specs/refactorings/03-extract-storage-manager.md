# Refactoring 03: Extract StorageManager Component

**Priority:** High
**Effort:** Medium
**Category:** Architecture / Composition Over Inheritance
**Depends On:** 01-extract-workload-manager (can be done in parallel)

---

## Problem Statement

Storage/volume management is currently embedded in `StatefulSetPod`:
- `withPersistentVolume()` and `withStorageClass()` are instance methods
- PVC template creation is coupled to StatefulSet-specific code
- Cannot reuse storage capabilities for other workload types

This limits flexibility:
- Future `JobPod` might need scratch storage
- `DeploymentPod` might need shared volumes
- ConfigMap/Secret volume mounting is scattered across implementations

---

## Design Constraints

### MUST Preserve

1. **Fluent interface** - Storage configuration must be intuitive:
   ```java
   pod.withPersistentVolume("1Gi")
      .withStorageClass("fast-ssd")
   ```

2. **Optional by default** - Most test pods don't need persistent storage

3. **Internal implementation** - StorageManager is NOT exposed to users directly

---

## Proposed Solution

### StorageManager Interface

```java
package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.*;

import java.util.List;

/**
 * Manages volume configuration for test pods.
 * Provides volumes, volume mounts, and PVC templates for workloads.
 */
public interface StorageManager {

    /**
     * Get volumes to add to the pod spec.
     */
    List<Volume> getVolumes();

    /**
     * Get volume mounts for the specified container.
     */
    List<VolumeMount> getMountsFor(String containerName);

    /**
     * Get PVC templates for StatefulSet workloads.
     * Returns empty list for workloads that don't support volume claim templates.
     */
    List<PersistentVolumeClaim> getPvcTemplates();

    /**
     * Create any standalone PVCs (for Deployment workloads).
     */
    void createPvcs(String namespace, KubernetesClient client);

    /**
     * Delete any standalone PVCs created.
     */
    void deletePvcs(String namespace, KubernetesClient client);

    // === Factory methods ===

    /**
     * No storage (default for most test pods).
     */
    static StorageManager none() {
        return new NoOpStorageManager();
    }

    /**
     * Persistent volume storage.
     */
    static StorageManager persistent(String size) {
        return new PersistentStorageManager(size);
    }

    /**
     * EmptyDir volume (ephemeral, pod-scoped).
     */
    static StorageManager emptyDir(String mountPath) {
        return new EmptyDirStorageManager(mountPath);
    }

    /**
     * ConfigMap volume.
     */
    static StorageManager configMap(String configMapName, String mountPath) {
        return new ConfigMapStorageManager(configMapName, mountPath);
    }

    /**
     * Secret volume.
     */
    static StorageManager secret(String secretName, String mountPath) {
        return new SecretStorageManager(secretName, mountPath);
    }
}
```

### NoOpStorageManager (Default)

```java
package org.testpods.core.storage;

public class NoOpStorageManager implements StorageManager {
    @Override
    public List<Volume> getVolumes() {
        return List.of();
    }

    @Override
    public List<VolumeMount> getMountsFor(String containerName) {
        return List.of();
    }

    @Override
    public List<PersistentVolumeClaim> getPvcTemplates() {
        return List.of();
    }

    @Override
    public void createPvcs(String namespace, KubernetesClient client) {
        // No-op
    }

    @Override
    public void deletePvcs(String namespace, KubernetesClient client) {
        // No-op
    }
}
```

### PersistentStorageManager

```java
package org.testpods.core.storage;

public class PersistentStorageManager implements StorageManager {

    private final String size;
    private String storageClassName;
    private String volumeName = "data";
    private String mountPath = "/data";

    public PersistentStorageManager(String size) {
        this.size = size;
    }

    public PersistentStorageManager withStorageClass(String className) {
        this.storageClassName = className;
        return this;
    }

    public PersistentStorageManager withVolumeName(String name) {
        this.volumeName = name;
        return this;
    }

    public PersistentStorageManager withMountPath(String path) {
        this.mountPath = path;
        return this;
    }

    @Override
    public List<Volume> getVolumes() {
        // For StatefulSets, volumes come from volumeClaimTemplates
        // For Deployments, we need to reference the PVC
        return List.of(
            new VolumeBuilder()
                .withName(volumeName)
                .withNewPersistentVolumeClaim()
                    .withClaimName(volumeName)
                .endPersistentVolumeClaim()
                .build()
        );
    }

    @Override
    public List<VolumeMount> getMountsFor(String containerName) {
        return List.of(
            new VolumeMountBuilder()
                .withName(volumeName)
                .withMountPath(mountPath)
                .build()
        );
    }

    @Override
    public List<PersistentVolumeClaim> getPvcTemplates() {
        return List.of(
            new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                    .withName(volumeName)
                .endMetadata()
                .withNewSpec()
                    .withAccessModes("ReadWriteOnce")
                    .withStorageClassName(storageClassName)
                    .withNewResources()
                        .addToRequests("storage", new Quantity(size))
                    .endResources()
                .endSpec()
                .build()
        );
    }

    @Override
    public void createPvcs(String namespace, KubernetesClient client) {
        // For Deployment workloads, create standalone PVC
        for (PersistentVolumeClaim pvc : getPvcTemplates()) {
            client.persistentVolumeClaims()
                .inNamespace(namespace)
                .resource(pvc)
                .create();
        }
    }

    @Override
    public void deletePvcs(String namespace, KubernetesClient client) {
        client.persistentVolumeClaims()
            .inNamespace(namespace)
            .withName(volumeName)
            .delete();
    }
}
```

### CompositeStorageManager

For pods needing multiple volume types:

```java
public class CompositeStorageManager implements StorageManager {
    private final List<StorageManager> managers;

    public CompositeStorageManager(StorageManager... managers) {
        this.managers = List.of(managers);
    }

    @Override
    public List<Volume> getVolumes() {
        return managers.stream()
            .flatMap(m -> m.getVolumes().stream())
            .toList();
    }

    @Override
    public List<VolumeMount> getMountsFor(String containerName) {
        return managers.stream()
            .flatMap(m -> m.getMountsFor(containerName).stream())
            .toList();
    }

    // ... other methods aggregate similarly
}
```

---

## Integration with ComposableTestPod

```java
public abstract class ComposableTestPod<SELF extends ComposableTestPod<SELF>>
    extends BaseTestPod<SELF> {

    protected StorageManager storageManager = StorageManager.none();

    // Fluent API preserved
    public SELF withPersistentVolume(String size) {
        this.storageManager = StorageManager.persistent(size);
        return self();
    }

    public SELF withStorageClass(String className) {
        if (storageManager instanceof PersistentStorageManager psm) {
            psm.withStorageClass(className);
        }
        return self();
    }

    public SELF withEmptyDir(String mountPath) {
        this.storageManager = StorageManager.emptyDir(mountPath);
        return self();
    }

    // Used when building pod spec
    protected void applyStorageToContainer(ContainerBuilder container) {
        for (VolumeMount mount : storageManager.getMountsFor(container.getName())) {
            container.addToVolumeMounts(mount);
        }
    }
}
```

---

## Implementation Steps

1. **Create interfaces**
   - `StorageManager` interface with factory methods
   - Implementations: `NoOpStorageManager`, `PersistentStorageManager`, `EmptyDirStorageManager`, `ConfigMapStorageManager`, `SecretStorageManager`
   - `CompositeStorageManager` for combining multiple types

2. **Integrate with workload managers**
   - `StatefulSetManager` uses `getPvcTemplates()` for volumeClaimTemplates
   - `DeploymentManager` uses `createPvcs()` for standalone PVCs

3. **Update ComposableTestPod**
   - Add `storageManager` field
   - Add fluent methods: `withPersistentVolume()`, `withStorageClass()`, `withEmptyDir()`
   - Apply storage to container builds

4. **Remove embedded storage code**
   - Remove `withPersistentVolume` field from `StatefulSetPod`
   - Remove PVC template building from `StatefulSetPod`

---

## Files to Modify

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/storage/StorageManager.java` | Create new |
| `core/src/main/java/org/testpods/core/storage/NoOpStorageManager.java` | Create new |
| `core/src/main/java/org/testpods/core/storage/PersistentStorageManager.java` | Create new |
| `core/src/main/java/org/testpods/core/storage/EmptyDirStorageManager.java` | Create new |
| `core/src/main/java/org/testpods/core/storage/ConfigMapStorageManager.java` | Create new |
| `core/src/main/java/org/testpods/core/storage/SecretStorageManager.java` | Create new |
| `core/src/main/java/org/testpods/core/storage/CompositeStorageManager.java` | Create new |
| `core/src/main/java/org/testpods/core/pods/ComposableTestPod.java` | Add storage support |

---

## Success Criteria

1. [ ] `StorageManager` interface exists with factory methods
2. [ ] Five storage types implemented: None, Persistent, EmptyDir, ConfigMap, Secret
3. [ ] `CompositeStorageManager` allows combining multiple storage types
4. [ ] Fluent API works correctly:
   ```java
   pod.withPersistentVolume("1Gi").withStorageClass("fast")
   ```
5. [ ] StatefulSet pods use getPvcTemplates() for volume claim templates
6. [ ] Storage is reusable across different workload types
7. [ ] All existing tests pass

---

## Validation Step

After implementation, the agent must:

1. **Test storage types** - Each type creates correct volumes/mounts
2. **Test PVC templates** - StatefulSet workloads get correct templates
3. **Test fluent API** - Chained methods work correctly
4. **Run tests** - `./gradlew :core:test`
5. **Document findings** - Write to `specs/refactorings/03-extract-storage-manager_result.md`

### Validation Output Format

```markdown
# Validation Result: Extract StorageManager

## Implementation Summary
- Files created: [list]
- Files modified: [list]

## Storage Type Tests
| Storage Type | Volumes Correct | Mounts Correct | PVC Templates |
|--------------|-----------------|----------------|---------------|
| None         | [Y/N]           | [Y/N]          | N/A           |
| Persistent   | [Y/N]           | [Y/N]          | [Y/N]         |
| EmptyDir     | [Y/N]           | [Y/N]          | N/A           |
| ConfigMap    | [Y/N]           | [Y/N]          | N/A           |
| Secret       | [Y/N]           | [Y/N]          | N/A           |

## Integration Tests
- StatefulSet + Persistent: [Pass/Fail]
- Deployment + EmptyDir: [Pass/Fail]
- Pod + ConfigMap mount: [Pass/Fail]

## Test Results
- Tests run: X
- Passed: X
- Failed: X

## Deviations from Plan
[List any deviations and reasoning]
```
