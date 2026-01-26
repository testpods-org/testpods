package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Combines multiple storage managers for pods needing multiple volume types.
 *
 * <p>Example: A database pod might need:
 *
 * <ul>
 *   <li>Persistent storage for data
 *   <li>ConfigMap for init scripts
 *   <li>Secret for credentials
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>{@code
 * StorageManager storage = new CompositeStorageManager(
 *     StorageManager.persistent("10Gi"),
 *     StorageManager.configMap("init-scripts", "/docker-entrypoint-initdb.d"),
 *     StorageManager.secret("db-credentials", "/etc/secrets")
 * );
 * }</pre>
 */
public class CompositeStorageManager implements StorageManager {

  private static final Logger LOG = LoggerFactory.getLogger(CompositeStorageManager.class);

  private final List<StorageManager> managers;

  public CompositeStorageManager(StorageManager... managers) {
    this.managers = List.of(managers);
  }

  @Override
  public List<Volume> getVolumes() {
    return managers.stream().flatMap(m -> m.getVolumes().stream()).toList();
  }

  @Override
  public List<VolumeMount> getMountsFor(String containerName) {
    return managers.stream().flatMap(m -> m.getMountsFor(containerName).stream()).toList();
  }

  @Override
  public List<PersistentVolumeClaim> getPvcTemplates() {
    return managers.stream().flatMap(m -> m.getPvcTemplates().stream()).toList();
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
        LOG.debug("Failed to delete PVCs from manager {}: {}", i, e.getMessage());
        // Continue deleting others
      }
    }
  }

  /**
   * Get the number of storage managers in this composite.
   *
   * @return the number of managers
   */
  public int size() {
    return managers.size();
  }

  /**
   * Get a specific storage manager by index.
   *
   * @param index the index (0-based)
   * @return the storage manager, or null if index is out of bounds
   */
  public StorageManager getManager(int index) {
    if (index < 0 || index >= managers.size()) {
      return null;
    }
    return managers.get(index);
  }
}
