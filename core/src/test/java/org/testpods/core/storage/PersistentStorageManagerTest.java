package org.testpods.core.storage;

import static org.junit.jupiter.api.Assertions.*;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersistentStorageManagerTest {

  @Test
  void constructor_setsSize() {
    PersistentStorageManager manager = new PersistentStorageManager("10Gi");
    assertEquals("10Gi", manager.getSize());
  }

  @Test
  void defaults_hasExpectedValues() {
    PersistentStorageManager manager = new PersistentStorageManager("5Gi");
    assertEquals("data", manager.getVolumeName());
    assertEquals("/data", manager.getMountPath());
    assertNull(manager.getStorageClassName());
  }

  @Test
  void withStorageClass_setsStorageClassName() {
    PersistentStorageManager manager =
        new PersistentStorageManager("1Gi").withStorageClass("fast-ssd");
    assertEquals("fast-ssd", manager.getStorageClassName());
  }

  @Test
  void withVolumeName_setsVolumeName() {
    PersistentStorageManager manager =
        new PersistentStorageManager("1Gi").withVolumeName("my-data");
    assertEquals("my-data", manager.getVolumeName());
  }

  @Test
  void withMountPath_setsMountPath() {
    PersistentStorageManager manager =
        new PersistentStorageManager("1Gi").withMountPath("/var/lib/data");
    assertEquals("/var/lib/data", manager.getMountPath());
  }

  @Test
  void fluentMethods_returnThis() {
    PersistentStorageManager manager = new PersistentStorageManager("1Gi");
    assertSame(manager, manager.withStorageClass("standard"));
    assertSame(manager, manager.withVolumeName("vol"));
    assertSame(manager, manager.withMountPath("/mnt"));
  }

  @Test
  void getVolumes_returnsSingleVolume() {
    PersistentStorageManager manager =
        new PersistentStorageManager("5Gi").withVolumeName("db-data");

    List<Volume> volumes = manager.getVolumes();
    assertEquals(1, volumes.size());

    Volume volume = volumes.get(0);
    assertEquals("db-data", volume.getName());
    assertNotNull(volume.getPersistentVolumeClaim());
    assertEquals("db-data", volume.getPersistentVolumeClaim().getClaimName());
  }

  @Test
  void getMountsFor_returnsSingleMount() {
    PersistentStorageManager manager =
        new PersistentStorageManager("5Gi")
            .withVolumeName("db-data")
            .withMountPath("/var/lib/postgresql");

    List<VolumeMount> mounts = manager.getMountsFor("main");
    assertEquals(1, mounts.size());

    VolumeMount mount = mounts.get(0);
    assertEquals("db-data", mount.getName());
    assertEquals("/var/lib/postgresql", mount.getMountPath());
  }

  @Test
  void getPvcTemplates_returnsPvcWithSize() {
    PersistentStorageManager manager = new PersistentStorageManager("10Gi").withVolumeName("data");

    List<PersistentVolumeClaim> pvcs = manager.getPvcTemplates();
    assertEquals(1, pvcs.size());

    PersistentVolumeClaim pvc = pvcs.get(0);
    assertEquals("data", pvc.getMetadata().getName());
    assertTrue(pvc.getSpec().getAccessModes().contains("ReadWriteOnce"));
    assertEquals("10Gi", pvc.getSpec().getResources().getRequests().get("storage").toString());
  }

  @Test
  void getPvcTemplates_withStorageClass_setsStorageClassName() {
    PersistentStorageManager manager =
        new PersistentStorageManager("5Gi").withStorageClass("fast-ssd");

    List<PersistentVolumeClaim> pvcs = manager.getPvcTemplates();
    assertEquals("fast-ssd", pvcs.get(0).getSpec().getStorageClassName());
  }

  @Test
  void getPvcTemplates_withoutStorageClass_hasNullStorageClassName() {
    PersistentStorageManager manager = new PersistentStorageManager("5Gi");

    List<PersistentVolumeClaim> pvcs = manager.getPvcTemplates();
    assertNull(pvcs.get(0).getSpec().getStorageClassName());
  }

  @Test
  void factoryMethod_persistent_createsManager() {
    PersistentStorageManager manager = StorageManager.persistent("20Gi");
    assertEquals("20Gi", manager.getSize());
  }
}
