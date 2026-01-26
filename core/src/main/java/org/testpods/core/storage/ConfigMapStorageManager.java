package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;

/**
 * Mounts a ConfigMap as a volume.
 *
 * <p>ConfigMaps allow you to decouple configuration from container images. When mounted as a
 * volume, each key in the ConfigMap becomes a file.
 *
 * <p>Use this for:
 *
 * <ul>
 *   <li>Configuration files
 *   <li>Init scripts (e.g., PostgreSQL /docker-entrypoint-initdb.d/)
 *   <li>Environment-specific settings
 * </ul>
 */
public class ConfigMapStorageManager implements StorageManager {

  private final String configMapName;
  private final String mountPath;
  private boolean readOnly = true;
  private Integer defaultMode;

  public ConfigMapStorageManager(String configMapName, String mountPath) {
    this.configMapName = configMapName;
    this.mountPath = mountPath;
  }

  /**
   * Set whether the volume should be mounted read-only.
   *
   * @param readOnly true for read-only (default), false for read-write
   * @return this manager for chaining
   */
  public ConfigMapStorageManager readOnly(boolean readOnly) {
    this.readOnly = readOnly;
    return this;
  }

  /**
   * Set the default file mode for mounted files.
   *
   * @param mode the file mode in octal (e.g., 0644)
   * @return this manager for chaining
   */
  public ConfigMapStorageManager withDefaultMode(int mode) {
    this.defaultMode = mode;
    return this;
  }

  @Override
  public List<Volume> getVolumes() {
    VolumeBuilder builder =
        new VolumeBuilder()
            .withName(configMapName)
            .withNewConfigMap()
            .withName(configMapName)
            .endConfigMap();

    if (defaultMode != null) {
      builder.editConfigMap().withDefaultMode(defaultMode).endConfigMap();
    }

    return List.of(builder.build());
  }

  @Override
  public List<VolumeMount> getMountsFor(String containerName) {
    return List.of(
        new VolumeMountBuilder()
            .withName(configMapName)
            .withMountPath(mountPath)
            .withReadOnly(readOnly)
            .build());
  }

  @Override
  public List<PersistentVolumeClaim> getPvcTemplates() {
    return List.of();
  }

  @Override
  public void createPvcs(String namespace, KubernetesClient client) {
    // No-op - ConfigMap volumes don't need PVCs
  }

  @Override
  public void deletePvcs(String namespace, KubernetesClient client) {
    // No-op
  }

  /** Get the ConfigMap name. */
  public String getConfigMapName() {
    return configMapName;
  }

  /** Get the mount path. */
  public String getMountPath() {
    return mountPath;
  }

  /** Check if read-only. */
  public boolean isReadOnly() {
    return readOnly;
  }
}
