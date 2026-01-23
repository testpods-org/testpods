# Plan: Extract StorageManager Component

**Priority:** High
**Effort:** Medium
**Category:** Architecture / Composition Over Inheritance
**Phase:** 2 - Architecture Refactoring (Can be done in parallel with 01, 02)
**Depends On:** 01-extract-workload-manager (can be done in parallel)

---

## Overview

Extract storage/volume management from `StatefulSetPod` into a composable `StorageManager` component, enabling reusable storage configurations across different workload types.

## Problem Statement

Storage/volume management is currently embedded in `StatefulSetPod`:
- `withPersistentVolume()` and `withStorageClass()` are instance methods
- PVC template creation is coupled to StatefulSet-specific code
- Cannot reuse storage capabilities for other workload types

### Limitations
- Future `JobPod` might need scratch storage
- `DeploymentPod` might need shared volumes
- ConfigMap/Secret volume mounting is scattered across implementations

## Proposed Solution

### StorageManager Interface

```java
package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
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

    static StorageManager none() {
        return new NoOpStorageManager();
    }

    static StorageManager persistent(String size) {
        return new PersistentStorageManager(size);
    }

    static StorageManager emptyDir(String mountPath) {
        return new EmptyDirStorageManager(mountPath);
    }

    static StorageManager configMap(String configMapName, String mountPath) {
        return new ConfigMapStorageManager(configMapName, mountPath);
    }

    static StorageManager secret(String secretName, String mountPath) {
        return new SecretStorageManager(secretName, mountPath);
    }
}
```

## Technical Considerations

### Design Constraints (MUST Preserve)

1. **Fluent interface** - Storage configuration must be intuitive:
   ```java
   pod.withPersistentVolume("1Gi")
      .withStorageClass("fast-ssd")
   ```

2. **Optional by default** - Most test pods don't need persistent storage

3. **Internal implementation** - StorageManager is NOT exposed to users directly

### Storage Types Required

| Type | Use Case | Creates Volumes | Creates PVCs |
|------|----------|-----------------|--------------|
| None | Default | No | No |
| Persistent | Database data | Yes (PVC ref) | Yes (template or standalone) |
| EmptyDir | Scratch space | Yes | No |
| ConfigMap | Config files | Yes | No |
| Secret | Credentials | Yes | No |
| Composite | Multiple types | Yes | Maybe |

## Acceptance Criteria

### Functional Requirements
- [ ] `StorageManager` interface exists with factory methods
- [ ] Five storage types implemented: None, Persistent, EmptyDir, ConfigMap, Secret
- [ ] `CompositeStorageManager` allows combining multiple storage types
- [ ] Fluent API works correctly: `pod.withPersistentVolume("1Gi").withStorageClass("fast")`
- [ ] StatefulSet pods use `getPvcTemplates()` for volume claim templates

### Non-Functional Requirements
- [ ] Storage is reusable across different workload types
- [ ] Embedded storage code removed from `StatefulSetPod`

### Quality Gates
- [ ] All existing tests pass
- [ ] Test coverage for each storage type

## Files to Create/Modify

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

## MVP

### NoOpStorageManager.java

```java
package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;

/**
 * Default storage manager - no storage configuration.
 */
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

### PersistentStorageManager.java

```java
package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;

/**
 * Manages persistent volume storage with PVC.
 */
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
        // For Deployments - reference the PVC by name
        // For StatefulSets - volumes come from volumeClaimTemplates
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

### EmptyDirStorageManager.java

```java
package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;

/**
 * Manages ephemeral emptyDir volumes.
 */
public class EmptyDirStorageManager implements StorageManager {

    private final String mountPath;
    private String volumeName = "scratch";
    private String sizeLimit;

    public EmptyDirStorageManager(String mountPath) {
        this.mountPath = mountPath;
    }

    public EmptyDirStorageManager withVolumeName(String name) {
        this.volumeName = name;
        return this;
    }

    public EmptyDirStorageManager withSizeLimit(String limit) {
        this.sizeLimit = limit;
        return this;
    }

    @Override
    public List<Volume> getVolumes() {
        VolumeBuilder builder = new VolumeBuilder()
            .withName(volumeName)
            .withNewEmptyDir();

        if (sizeLimit != null) {
            builder.editEmptyDir()
                .withSizeLimit(new Quantity(sizeLimit))
            .endEmptyDir();
        }

        return List.of(builder.build());
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
        return List.of();  // EmptyDir doesn't use PVCs
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

### ConfigMapStorageManager.java

```java
package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;

/**
 * Mounts a ConfigMap as a volume.
 */
public class ConfigMapStorageManager implements StorageManager {

    private final String configMapName;
    private final String mountPath;
    private boolean readOnly = true;

    public ConfigMapStorageManager(String configMapName, String mountPath) {
        this.configMapName = configMapName;
        this.mountPath = mountPath;
    }

    public ConfigMapStorageManager readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    @Override
    public List<Volume> getVolumes() {
        return List.of(
            new VolumeBuilder()
                .withName(configMapName)
                .withNewConfigMap()
                    .withName(configMapName)
                .endConfigMap()
                .build()
        );
    }

    @Override
    public List<VolumeMount> getMountsFor(String containerName) {
        return List.of(
            new VolumeMountBuilder()
                .withName(configMapName)
                .withMountPath(mountPath)
                .withReadOnly(readOnly)
                .build()
        );
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

### CompositeStorageManager.java

```java
package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Combines multiple storage managers for pods needing multiple volume types.
 */
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

    @Override
    public List<PersistentVolumeClaim> getPvcTemplates() {
        return managers.stream()
            .flatMap(m -> m.getPvcTemplates().stream())
            .toList();
    }

    @Override
    public void createPvcs(String namespace, KubernetesClient client) {
        for (StorageManager manager : managers) {
            manager.createPvcs(namespace, client);
        }
    }

    @Override
    public void deletePvcs(String namespace, KubernetesClient client) {
        // Delete in reverse order
        for (int i = managers.size() - 1; i >= 0; i--) {
            try {
                managers.get(i).deletePvcs(namespace, client);
            } catch (Exception e) {
                // Log but continue
            }
        }
    }
}
```

### Integration with ComposableTestPod

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

    // Used when building container spec
    protected void applyStorageToContainer(ContainerBuilder container) {
        for (VolumeMount mount : storageManager.getMountsFor(container.getName())) {
            container.addToVolumeMounts(mount);
        }
    }

    // Used when building pod spec
    protected PodSpecBuilder applyStorageToPodSpec(PodSpecBuilder podSpec) {
        for (Volume volume : storageManager.getVolumes()) {
            podSpec.addToVolumes(volume);
        }
        return podSpec;
    }
}
```

## Test Plan

### StorageManagerTest.java

```java
@Test
void persistentStorageCreatesCorrectVolume() {
    StorageManager manager = StorageManager.persistent("1Gi");

    List<Volume> volumes = manager.getVolumes();
    assertThat(volumes).hasSize(1);
    assertThat(volumes.get(0).getPersistentVolumeClaim()).isNotNull();
}

@Test
void persistentStorageCreatesPvcTemplate() {
    StorageManager manager = ((PersistentStorageManager) StorageManager.persistent("5Gi"))
        .withStorageClass("fast-ssd");

    List<PersistentVolumeClaim> templates = manager.getPvcTemplates();
    assertThat(templates).hasSize(1);
    assertThat(templates.get(0).getSpec().getStorageClassName()).isEqualTo("fast-ssd");
    assertThat(templates.get(0).getSpec().getResources().getRequests().get("storage").getAmount())
        .isEqualTo("5Gi");
}

@Test
void emptyDirDoesNotCreatePvc() {
    StorageManager manager = StorageManager.emptyDir("/tmp/scratch");

    assertThat(manager.getPvcTemplates()).isEmpty();
    assertThat(manager.getVolumes()).hasSize(1);
    assertThat(manager.getVolumes().get(0).getEmptyDir()).isNotNull();
}

@Test
void compositeStorageCombinesVolumes() {
    StorageManager manager = new CompositeStorageManager(
        StorageManager.persistent("1Gi"),
        StorageManager.configMap("app-config", "/config")
    );

    assertThat(manager.getVolumes()).hasSize(2);
    assertThat(manager.getMountsFor("main")).hasSize(2);
}
```

## Usage Examples

### PostgreSQLPod with Persistent Storage

```java
PostgreSQLPod postgres = new PostgreSQLPod()
    .withName("db")
    .withPersistentVolume("10Gi")
    .withStorageClass("fast-ssd");
```

### GenericTestPod with Scratch Space

```java
GenericTestPod pod = new GenericTestPod()
    .withImage("busybox")
    .withEmptyDir("/tmp/work");
```

### Pod with Multiple Volumes

```java
// Internal usage for complex pods
storageManager = new CompositeStorageManager(
    StorageManager.persistent("5Gi"),
    StorageManager.configMap("app-config", "/etc/config"),
    StorageManager.secret("db-credentials", "/etc/secrets")
);
```

## References

- Spec: `specs/refactorings/03-extract-storage-manager.md`
- Current StatefulSetPod storage code: `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java:281-298`
- Fabric8 PVC docs: https://github.com/fabric8io/kubernetes-client

---

## Validation Output

After implementation, write results to `specs/refactorings/03-extract-storage-manager_result.md`
