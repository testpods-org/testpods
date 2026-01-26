package org.testpods.core.storage;

import static org.junit.jupiter.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigMapStorageManagerTest {

  @Test
  void constructor_setsConfigMapNameAndMountPath() {
    ConfigMapStorageManager manager = new ConfigMapStorageManager("my-config", "/etc/config");
    assertEquals("my-config", manager.getConfigMapName());
    assertEquals("/etc/config", manager.getMountPath());
  }

  @Test
  void defaults_isReadOnly() {
    ConfigMapStorageManager manager = new ConfigMapStorageManager("config", "/config");
    assertTrue(manager.isReadOnly());
  }

  @Test
  void readOnly_setsReadOnly() {
    ConfigMapStorageManager manager =
        new ConfigMapStorageManager("config", "/config").readOnly(false);
    assertFalse(manager.isReadOnly());
  }

  @Test
  void fluentMethods_returnThis() {
    ConfigMapStorageManager manager = new ConfigMapStorageManager("config", "/config");
    assertSame(manager, manager.readOnly(true));
    assertSame(manager, manager.withDefaultMode(0644));
  }

  @Test
  void getVolumes_returnsConfigMapVolume() {
    ConfigMapStorageManager manager = new ConfigMapStorageManager("app-config", "/etc/app");

    List<Volume> volumes = manager.getVolumes();
    assertEquals(1, volumes.size());

    Volume volume = volumes.get(0);
    assertEquals("app-config", volume.getName());
    assertNotNull(volume.getConfigMap());
    assertEquals("app-config", volume.getConfigMap().getName());
  }

  @Test
  void getVolumes_withDefaultMode_setsMode() {
    ConfigMapStorageManager manager =
        new ConfigMapStorageManager("config", "/config").withDefaultMode(0755);

    Volume volume = manager.getVolumes().get(0);
    assertEquals(0755, volume.getConfigMap().getDefaultMode());
  }

  @Test
  void getVolumes_withoutDefaultMode_hasNullMode() {
    ConfigMapStorageManager manager = new ConfigMapStorageManager("config", "/config");

    Volume volume = manager.getVolumes().get(0);
    assertNull(volume.getConfigMap().getDefaultMode());
  }

  @Test
  void getMountsFor_returnsSingleMount() {
    ConfigMapStorageManager manager =
        new ConfigMapStorageManager("init-scripts", "/docker-entrypoint-initdb.d");

    List<VolumeMount> mounts = manager.getMountsFor("main");
    assertEquals(1, mounts.size());

    VolumeMount mount = mounts.get(0);
    assertEquals("init-scripts", mount.getName());
    assertEquals("/docker-entrypoint-initdb.d", mount.getMountPath());
    assertTrue(mount.getReadOnly());
  }

  @Test
  void getMountsFor_withReadOnlyFalse_mountIsNotReadOnly() {
    ConfigMapStorageManager manager =
        new ConfigMapStorageManager("config", "/config").readOnly(false);

    VolumeMount mount = manager.getMountsFor("main").get(0);
    assertFalse(mount.getReadOnly());
  }

  @Test
  void getPvcTemplates_returnsEmptyList() {
    ConfigMapStorageManager manager = new ConfigMapStorageManager("config", "/config");
    assertTrue(manager.getPvcTemplates().isEmpty());
  }

  @Test
  void createPvcs_doesNotThrow() {
    ConfigMapStorageManager manager = new ConfigMapStorageManager("config", "/config");
    assertDoesNotThrow(() -> manager.createPvcs("test-ns", null));
  }

  @Test
  void deletePvcs_doesNotThrow() {
    ConfigMapStorageManager manager = new ConfigMapStorageManager("config", "/config");
    assertDoesNotThrow(() -> manager.deletePvcs("test-ns", null));
  }

  @Test
  void factoryMethod_configMap_createsManager() {
    ConfigMapStorageManager manager = StorageManager.configMap("my-cm", "/mnt/config");
    assertEquals("my-cm", manager.getConfigMapName());
    assertEquals("/mnt/config", manager.getMountPath());
  }
}
