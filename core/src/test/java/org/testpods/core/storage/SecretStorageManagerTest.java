package org.testpods.core.storage;

import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecretStorageManagerTest {

    @Test
    void constructor_setsSecretNameAndMountPath() {
        SecretStorageManager manager = new SecretStorageManager("my-secret", "/etc/secrets");
        assertEquals("my-secret", manager.getSecretName());
        assertEquals("/etc/secrets", manager.getMountPath());
    }

    @Test
    void defaults_isReadOnly() {
        SecretStorageManager manager = new SecretStorageManager("secret", "/secrets");
        assertTrue(manager.isReadOnly());
    }

    @Test
    void readOnly_setsReadOnly() {
        SecretStorageManager manager = new SecretStorageManager("secret", "/secrets")
            .readOnly(false);
        assertFalse(manager.isReadOnly());
    }

    @Test
    void fluentMethods_returnThis() {
        SecretStorageManager manager = new SecretStorageManager("secret", "/secrets");
        assertSame(manager, manager.readOnly(true));
        assertSame(manager, manager.withDefaultMode(0400));
    }

    @Test
    void getVolumes_returnsSecretVolume() {
        SecretStorageManager manager = new SecretStorageManager("db-credentials", "/etc/db");

        List<Volume> volumes = manager.getVolumes();
        assertEquals(1, volumes.size());

        Volume volume = volumes.get(0);
        assertEquals("db-credentials", volume.getName());
        assertNotNull(volume.getSecret());
        assertEquals("db-credentials", volume.getSecret().getSecretName());
    }

    @Test
    void getVolumes_withDefaultMode_setsMode() {
        SecretStorageManager manager = new SecretStorageManager("secret", "/secrets")
            .withDefaultMode(0400);

        Volume volume = manager.getVolumes().get(0);
        assertEquals(0400, volume.getSecret().getDefaultMode());
    }

    @Test
    void getVolumes_withoutDefaultMode_hasNullMode() {
        SecretStorageManager manager = new SecretStorageManager("secret", "/secrets");

        Volume volume = manager.getVolumes().get(0);
        assertNull(volume.getSecret().getDefaultMode());
    }

    @Test
    void getMountsFor_returnsSingleMount() {
        SecretStorageManager manager = new SecretStorageManager("tls-certs", "/etc/ssl/certs");

        List<VolumeMount> mounts = manager.getMountsFor("main");
        assertEquals(1, mounts.size());

        VolumeMount mount = mounts.get(0);
        assertEquals("tls-certs", mount.getName());
        assertEquals("/etc/ssl/certs", mount.getMountPath());
        assertTrue(mount.getReadOnly());
    }

    @Test
    void getMountsFor_withReadOnlyFalse_mountIsNotReadOnly() {
        SecretStorageManager manager = new SecretStorageManager("secret", "/secrets")
            .readOnly(false);

        VolumeMount mount = manager.getMountsFor("main").get(0);
        assertFalse(mount.getReadOnly());
    }

    @Test
    void getPvcTemplates_returnsEmptyList() {
        SecretStorageManager manager = new SecretStorageManager("secret", "/secrets");
        assertTrue(manager.getPvcTemplates().isEmpty());
    }

    @Test
    void createPvcs_doesNotThrow() {
        SecretStorageManager manager = new SecretStorageManager("secret", "/secrets");
        assertDoesNotThrow(() -> manager.createPvcs("test-ns", null));
    }

    @Test
    void deletePvcs_doesNotThrow() {
        SecretStorageManager manager = new SecretStorageManager("secret", "/secrets");
        assertDoesNotThrow(() -> manager.deletePvcs("test-ns", null));
    }

    @Test
    void factoryMethod_secret_createsManager() {
        SecretStorageManager manager = StorageManager.secret("api-keys", "/etc/api");
        assertEquals("api-keys", manager.getSecretName());
        assertEquals("/etc/api", manager.getMountPath());
    }
}
