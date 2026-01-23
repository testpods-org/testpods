package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.List;

/**
 * Default storage manager that provides no storage.
 * <p>
 * Use this for pods that don't need any persistent or ephemeral storage.
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
