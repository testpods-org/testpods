package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;

/**
 * Mounts a Secret as a volume.
 *
 * <p>Secrets are similar to ConfigMaps but are intended for sensitive data. When mounted, each key
 * in the Secret becomes a file.
 *
 * <p>Use this for:
 *
 * <ul>
 *   <li>Database credentials
 *   <li>TLS certificates
 *   <li>API keys
 * </ul>
 */
public class SecretStorageManager implements StorageManager {

  private final String secretName;
  private final String mountPath;
  private boolean readOnly = true;
  private Integer defaultMode;

  public SecretStorageManager(String secretName, String mountPath) {
    this.secretName = secretName;
    this.mountPath = mountPath;
  }

  /**
   * Set whether the volume should be mounted read-only.
   *
   * @param readOnly true for read-only (default), false for read-write
   * @return this manager for chaining
   */
  public SecretStorageManager readOnly(boolean readOnly) {
    this.readOnly = readOnly;
    return this;
  }

  /**
   * Set the default file mode for mounted files.
   *
   * @param mode the file mode in octal (e.g., 0400 for secrets)
   * @return this manager for chaining
   */
  public SecretStorageManager withDefaultMode(int mode) {
    this.defaultMode = mode;
    return this;
  }

  @Override
  public List<Volume> getVolumes() {
    VolumeBuilder builder =
        new VolumeBuilder()
            .withName(secretName)
            .withNewSecret()
            .withSecretName(secretName)
            .endSecret();

    if (defaultMode != null) {
      builder.editSecret().withDefaultMode(defaultMode).endSecret();
    }

    return List.of(builder.build());
  }

  @Override
  public List<VolumeMount> getMountsFor(String containerName) {
    return List.of(
        new VolumeMountBuilder()
            .withName(secretName)
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
    // No-op - Secret volumes don't need PVCs
  }

  @Override
  public void deletePvcs(String namespace, KubernetesClient client) {
    // No-op
  }

  /** Get the Secret name. */
  public String getSecretName() {
    return secretName;
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
