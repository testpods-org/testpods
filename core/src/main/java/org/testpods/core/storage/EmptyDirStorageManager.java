package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.List;

/**
 * Manages ephemeral emptyDir volumes.
 * <p>
 * EmptyDir volumes are created when a pod is assigned to a node and exist
 * as long as the pod runs on that node. Data is lost when the pod is removed.
 * <p>
 * Use this for:
 * <ul>
 *   <li>Scratch space for computations</li>
 *   <li>Temporary file storage</li>
 *   <li>Sharing data between containers in a pod</li>
 * </ul>
 */
public class EmptyDirStorageManager implements StorageManager {

    private final String mountPath;
    private String volumeName = "scratch";
    private String sizeLimit;
    private String medium;

    public EmptyDirStorageManager(String mountPath) {
        this.mountPath = mountPath;
    }

    /**
     * Set the volume name.
     *
     * @param name the volume name (default: "scratch")
     * @return this manager for chaining
     */
    public EmptyDirStorageManager withVolumeName(String name) {
        this.volumeName = name;
        return this;
    }

    /**
     * Set the size limit for the emptyDir.
     *
     * @param limit the size limit (e.g., "1Gi")
     * @return this manager for chaining
     */
    public EmptyDirStorageManager withSizeLimit(String limit) {
        this.sizeLimit = limit;
        return this;
    }

    /**
     * Set the storage medium.
     *
     * @param medium "Memory" for tmpfs, or empty for node's default
     * @return this manager for chaining
     */
    public EmptyDirStorageManager withMedium(String medium) {
        this.medium = medium;
        return this;
    }

    @Override
    public List<Volume> getVolumes() {
        VolumeBuilder builder = new VolumeBuilder()
            .withName(volumeName)
            .withNewEmptyDir()
            .endEmptyDir();

        if (sizeLimit != null) {
            builder.editEmptyDir()
                .withSizeLimit(new Quantity(sizeLimit))
            .endEmptyDir();
        }

        if (medium != null) {
            builder.editEmptyDir()
                .withMedium(medium)
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
        // No-op - emptyDir doesn't need PVCs
    }

    @Override
    public void deletePvcs(String namespace, KubernetesClient client) {
        // No-op
    }

    /**
     * Get the mount path.
     */
    public String getMountPath() {
        return mountPath;
    }

    /**
     * Get the volume name.
     */
    public String getVolumeName() {
        return volumeName;
    }
}
