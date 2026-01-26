package org.testpods.core.storage;

import static org.junit.jupiter.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmptyDirStorageManagerTest {

  @Test
  void constructor_setsMountPath() {
    EmptyDirStorageManager manager = new EmptyDirStorageManager("/tmp/scratch");
    assertEquals("/tmp/scratch", manager.getMountPath());
  }

  @Test
  void defaults_hasExpectedValues() {
    EmptyDirStorageManager manager = new EmptyDirStorageManager("/tmp");
    assertEquals("scratch", manager.getVolumeName());
  }

  @Test
  void withVolumeName_setsVolumeName() {
    EmptyDirStorageManager manager =
        new EmptyDirStorageManager("/tmp").withVolumeName("temp-space");
    assertEquals("temp-space", manager.getVolumeName());
  }

  @Test
  void withSizeLimit_returnsSelf() {
    EmptyDirStorageManager manager = new EmptyDirStorageManager("/tmp");
    assertSame(manager, manager.withSizeLimit("1Gi"));
  }

  @Test
  void withMedium_returnsSelf() {
    EmptyDirStorageManager manager = new EmptyDirStorageManager("/tmp");
    assertSame(manager, manager.withMedium("Memory"));
  }

  @Test
  void fluentMethods_returnThis() {
    EmptyDirStorageManager manager = new EmptyDirStorageManager("/tmp");
    assertSame(manager, manager.withVolumeName("vol"));
    assertSame(manager, manager.withSizeLimit("500Mi"));
    assertSame(manager, manager.withMedium("Memory"));
  }

  @Test
  void getVolumes_returnsEmptyDirVolume() {
    EmptyDirStorageManager manager =
        new EmptyDirStorageManager("/tmp").withVolumeName("scratch-space");

    List<Volume> volumes = manager.getVolumes();
    assertEquals(1, volumes.size());

    Volume volume = volumes.get(0);
    assertEquals("scratch-space", volume.getName());
    assertNotNull(volume.getEmptyDir());
  }

  @Test
  void getVolumes_withSizeLimit_setsLimit() {
    EmptyDirStorageManager manager = new EmptyDirStorageManager("/tmp").withSizeLimit("2Gi");

    Volume volume = manager.getVolumes().get(0);
    assertEquals("2Gi", volume.getEmptyDir().getSizeLimit().toString());
  }

  @Test
  void getVolumes_withMedium_setsMedium() {
    EmptyDirStorageManager manager = new EmptyDirStorageManager("/tmp").withMedium("Memory");

    Volume volume = manager.getVolumes().get(0);
    assertEquals("Memory", volume.getEmptyDir().getMedium());
  }

  @Test
  void getVolumes_withAllOptions_setsAll() {
    EmptyDirStorageManager manager =
        new EmptyDirStorageManager("/cache")
            .withVolumeName("ram-cache")
            .withSizeLimit("512Mi")
            .withMedium("Memory");

    Volume volume = manager.getVolumes().get(0);
    assertEquals("ram-cache", volume.getName());
    assertEquals("512Mi", volume.getEmptyDir().getSizeLimit().toString());
    assertEquals("Memory", volume.getEmptyDir().getMedium());
  }

  @Test
  void getMountsFor_returnsSingleMount() {
    EmptyDirStorageManager manager =
        new EmptyDirStorageManager("/var/cache").withVolumeName("cache");

    List<VolumeMount> mounts = manager.getMountsFor("main");
    assertEquals(1, mounts.size());

    VolumeMount mount = mounts.get(0);
    assertEquals("cache", mount.getName());
    assertEquals("/var/cache", mount.getMountPath());
  }

  @Test
  void getPvcTemplates_returnsEmptyList() {
    EmptyDirStorageManager manager = new EmptyDirStorageManager("/tmp");
    assertTrue(manager.getPvcTemplates().isEmpty());
  }

  @Test
  void createPvcs_doesNotThrow() {
    EmptyDirStorageManager manager = new EmptyDirStorageManager("/tmp");
    assertDoesNotThrow(() -> manager.createPvcs("test-ns", null));
  }

  @Test
  void deletePvcs_doesNotThrow() {
    EmptyDirStorageManager manager = new EmptyDirStorageManager("/tmp");
    assertDoesNotThrow(() -> manager.deletePvcs("test-ns", null));
  }

  @Test
  void factoryMethod_emptyDir_createsManager() {
    EmptyDirStorageManager manager = StorageManager.emptyDir("/scratch");
    assertEquals("/scratch", manager.getMountPath());
  }
}
