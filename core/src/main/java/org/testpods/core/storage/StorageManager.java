package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.List;

/**
 * Manages volume configuration for test pods.
 * <p>
 * This is an internal implementation detail, not exposed to pod users directly.
 * It provides volumes, volume mounts, and PVC templates for workloads.
 * <p>
 * Available implementations:
 * <ul>
 *   <li>{@link NoOpStorageManager} - No storage (default)</li>
 *   <li>{@link PersistentStorageManager} - PVC-backed storage</li>
 *   <li>{@link EmptyDirStorageManager} - Ephemeral scratch space</li>
 *   <li>{@link ConfigMapStorageManager} - ConfigMap volume mounting</li>
 *   <li>{@link SecretStorageManager} - Secret volume mounting</li>
 *   <li>{@link CompositeStorageManager} - Combines multiple storage types</li>
 * </ul>
 */
public interface StorageManager {

    /**
     * Get volumes to add to the pod spec.
     *
     * @return list of volumes (may be empty)
     */
    List<Volume> getVolumes();

    /**
     * Get volume mounts for a container.
     *
     * @param containerName the name of the container (for future per-container mounts)
     * @return list of volume mounts (may be empty)
     */
    List<VolumeMount> getMountsFor(String containerName);

    /**
     * Get PVC templates for StatefulSet workloads.
     * <p>
     * Returns empty list for workloads that don't support volume claim templates
     * or for storage types that don't use PVCs.
     *
     * @return list of PVC templates (may be empty)
     */
    List<PersistentVolumeClaim> getPvcTemplates();

    /**
     * Create any standalone PVCs needed (for Deployment workloads).
     * <p>
     * StatefulSets use volumeClaimTemplates instead, so this is a no-op for them.
     *
     * @param namespace the namespace to create PVCs in
     * @param client the Kubernetes client
     */
    void createPvcs(String namespace, KubernetesClient client);

    /**
     * Delete any standalone PVCs created.
     *
     * @param namespace the namespace to delete PVCs from
     * @param client the Kubernetes client
     */
    void deletePvcs(String namespace, KubernetesClient client);

    // === Factory methods ===

    /**
     * Create a no-op storage manager (no storage).
     */
    static StorageManager none() {
        return new NoOpStorageManager();
    }

    /**
     * Create a persistent storage manager with the specified size.
     *
     * @param size the storage size (e.g., "1Gi", "10Gi")
     */
    static PersistentStorageManager persistent(String size) {
        return new PersistentStorageManager(size);
    }

    /**
     * Create an emptyDir storage manager.
     *
     * @param mountPath the path to mount the emptyDir
     */
    static EmptyDirStorageManager emptyDir(String mountPath) {
        return new EmptyDirStorageManager(mountPath);
    }

    /**
     * Create a ConfigMap storage manager.
     *
     * @param configMapName the name of the ConfigMap
     * @param mountPath the path to mount the ConfigMap
     */
    static ConfigMapStorageManager configMap(String configMapName, String mountPath) {
        return new ConfigMapStorageManager(configMapName, mountPath);
    }

    /**
     * Create a Secret storage manager.
     *
     * @param secretName the name of the Secret
     * @param mountPath the path to mount the Secret
     */
    static SecretStorageManager secret(String secretName, String mountPath) {
        return new SecretStorageManager(secretName, mountPath);
    }
}
