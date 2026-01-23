package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Manages persistent volume storage with PVC.
 * <p>
 * For StatefulSets, provides PVC templates that are automatically created.
 * For Deployments, creates standalone PVCs.
 */
public class PersistentStorageManager implements StorageManager {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentStorageManager.class);

    private final String size;
    private String storageClassName;
    private String volumeName = "data";
    private String mountPath = "/data";

    public PersistentStorageManager(String size) {
        this.size = size;
    }

    /**
     * Set the storage class name.
     *
     * @param className the storage class (e.g., "standard", "fast-ssd")
     * @return this manager for chaining
     */
    public PersistentStorageManager withStorageClass(String className) {
        this.storageClassName = className;
        return this;
    }

    /**
     * Set the volume name.
     *
     * @param name the volume name (default: "data")
     * @return this manager for chaining
     */
    public PersistentStorageManager withVolumeName(String name) {
        this.volumeName = name;
        return this;
    }

    /**
     * Set the mount path.
     *
     * @param path the mount path (default: "/data")
     * @return this manager for chaining
     */
    public PersistentStorageManager withMountPath(String path) {
        this.mountPath = path;
        return this;
    }

    @Override
    public List<Volume> getVolumes() {
        // For Deployments - reference the PVC by name
        // For StatefulSets - volumes come from volumeClaimTemplates automatically
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
        PersistentVolumeClaimBuilder builder = new PersistentVolumeClaimBuilder()
            .withNewMetadata()
                .withName(volumeName)
            .endMetadata()
            .withNewSpec()
                .withAccessModes("ReadWriteOnce")
                .withNewResources()
                    .addToRequests("storage", new Quantity(size))
                .endResources()
            .endSpec();

        if (storageClassName != null) {
            builder.editSpec()
                .withStorageClassName(storageClassName)
            .endSpec();
        }

        return List.of(builder.build());
    }

    @Override
    public void createPvcs(String namespace, KubernetesClient client) {
        // For Deployment workloads, create standalone PVC
        for (PersistentVolumeClaim pvc : getPvcTemplates()) {
            PersistentVolumeClaim withNs = new PersistentVolumeClaimBuilder(pvc)
                .editMetadata()
                    .withNamespace(namespace)
                .endMetadata()
                .build();

            client.persistentVolumeClaims()
                .inNamespace(namespace)
                .resource(withNs)
                .create();

            LOG.debug("Created PVC: {}/{}", namespace, volumeName);
        }
    }

    @Override
    public void deletePvcs(String namespace, KubernetesClient client) {
        client.persistentVolumeClaims()
            .inNamespace(namespace)
            .withName(volumeName)
            .delete();
        LOG.debug("Deleted PVC: {}/{}", namespace, volumeName);
    }

    /**
     * Get the configured storage size.
     */
    public String getSize() {
        return size;
    }

    /**
     * Get the configured storage class name.
     */
    public String getStorageClassName() {
        return storageClassName;
    }

    /**
     * Get the volume name.
     */
    public String getVolumeName() {
        return volumeName;
    }

    /**
     * Get the mount path.
     */
    public String getMountPath() {
        return mountPath;
    }
}
