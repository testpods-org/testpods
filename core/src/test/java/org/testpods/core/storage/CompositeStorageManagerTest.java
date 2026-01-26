package org.testpods.core.storage;

import static org.junit.jupiter.api.Assertions.*;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositeStorageManagerTest {

  @Test
  void constructor_withNoManagers_hasZeroSize() {
    CompositeStorageManager manager = new CompositeStorageManager();
    assertEquals(0, manager.size());
  }

  @Test
  void constructor_withMultipleManagers_hasSizeMatchingManagerCount() {
    CompositeStorageManager manager =
        new CompositeStorageManager(StorageManager.none(), StorageManager.emptyDir("/tmp"));
    assertEquals(2, manager.size());
  }

  @Test
  void getManager_validIndex_returnsManager() {
    EmptyDirStorageManager emptyDir = StorageManager.emptyDir("/tmp");
    CompositeStorageManager manager = new CompositeStorageManager(emptyDir);

    assertSame(emptyDir, manager.getManager(0));
  }

  @Test
  void getManager_negativeIndex_returnsNull() {
    CompositeStorageManager manager = new CompositeStorageManager(StorageManager.none());
    assertNull(manager.getManager(-1));
  }

  @Test
  void getManager_outOfBoundsIndex_returnsNull() {
    CompositeStorageManager manager = new CompositeStorageManager(StorageManager.none());
    assertNull(manager.getManager(1));
  }

  @Test
  void getVolumes_combinesVolumesFromAllManagers() {
    CompositeStorageManager manager =
        new CompositeStorageManager(
            StorageManager.emptyDir("/tmp").withVolumeName("scratch"),
            StorageManager.configMap("config", "/etc/config"),
            StorageManager.secret("creds", "/etc/creds"));

    List<Volume> volumes = manager.getVolumes();
    assertEquals(3, volumes.size());

    List<String> volumeNames = volumes.stream().map(Volume::getName).toList();
    assertTrue(volumeNames.contains("scratch"));
    assertTrue(volumeNames.contains("config"));
    assertTrue(volumeNames.contains("creds"));
  }

  @Test
  void getMountsFor_combinesMountsFromAllManagers() {
    CompositeStorageManager manager =
        new CompositeStorageManager(
            StorageManager.emptyDir("/tmp"), StorageManager.configMap("config", "/etc/config"));

    List<VolumeMount> mounts = manager.getMountsFor("main");
    assertEquals(2, mounts.size());

    List<String> mountPaths = mounts.stream().map(VolumeMount::getMountPath).toList();
    assertTrue(mountPaths.contains("/tmp"));
    assertTrue(mountPaths.contains("/etc/config"));
  }

  @Test
  void getPvcTemplates_combinesPvcTemplatesFromAllManagers() {
    CompositeStorageManager manager =
        new CompositeStorageManager(
            StorageManager.persistent("5Gi").withVolumeName("data1"),
            StorageManager.persistent("10Gi").withVolumeName("data2"),
            StorageManager.emptyDir("/tmp") // This has no PVC templates
            );

    List<PersistentVolumeClaim> pvcs = manager.getPvcTemplates();
    assertEquals(2, pvcs.size());

    List<String> pvcNames = pvcs.stream().map(p -> p.getMetadata().getName()).toList();
    assertTrue(pvcNames.contains("data1"));
    assertTrue(pvcNames.contains("data2"));
  }

  @Test
  void getVolumes_withNoManagers_returnsEmptyList() {
    CompositeStorageManager manager = new CompositeStorageManager();
    assertTrue(manager.getVolumes().isEmpty());
  }

  @Test
  void getMountsFor_withNoManagers_returnsEmptyList() {
    CompositeStorageManager manager = new CompositeStorageManager();
    assertTrue(manager.getMountsFor("main").isEmpty());
  }

  @Test
  void getPvcTemplates_withNoManagers_returnsEmptyList() {
    CompositeStorageManager manager = new CompositeStorageManager();
    assertTrue(manager.getPvcTemplates().isEmpty());
  }

  @Test
  void createPvcs_doesNotThrowWithNullClient() {
    CompositeStorageManager manager =
        new CompositeStorageManager(
            StorageManager.emptyDir("/tmp"), StorageManager.configMap("config", "/config"));
    // emptyDir and configMap don't need clients, so this should not throw
    assertDoesNotThrow(() -> manager.createPvcs("test-ns", null));
  }

  @Test
  void deletePvcs_doesNotThrowWithNullClient() {
    CompositeStorageManager manager =
        new CompositeStorageManager(
            StorageManager.emptyDir("/tmp"), StorageManager.configMap("config", "/config"));
    // emptyDir and configMap don't need clients, so this should not throw
    assertDoesNotThrow(() -> manager.deletePvcs("test-ns", null));
  }

  @Test
  void typicalDatabaseUseCase_combinesMultipleStorageTypes() {
    // Real-world example: A database pod with data, init scripts, and credentials
    CompositeStorageManager manager =
        new CompositeStorageManager(
            StorageManager.persistent("10Gi")
                .withVolumeName("pgdata")
                .withMountPath("/var/lib/postgresql/data"),
            StorageManager.configMap("init-scripts", "/docker-entrypoint-initdb.d"),
            StorageManager.secret("db-credentials", "/etc/secrets"));

    // Should have 3 volumes
    assertEquals(3, manager.getVolumes().size());

    // Should have 3 mounts
    assertEquals(3, manager.getMountsFor("postgres").size());

    // Should have 1 PVC template (only persistent storage)
    assertEquals(1, manager.getPvcTemplates().size());
    assertEquals("pgdata", manager.getPvcTemplates().get(0).getMetadata().getName());
  }
}
