package org.testpods.core.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoOpStorageManagerTest {

    @Test
    void getVolumes_returnsEmptyList() {
        NoOpStorageManager manager = new NoOpStorageManager();
        assertTrue(manager.getVolumes().isEmpty());
    }

    @Test
    void getMountsFor_returnsEmptyList() {
        NoOpStorageManager manager = new NoOpStorageManager();
        assertTrue(manager.getMountsFor("main").isEmpty());
    }

    @Test
    void getPvcTemplates_returnsEmptyList() {
        NoOpStorageManager manager = new NoOpStorageManager();
        assertTrue(manager.getPvcTemplates().isEmpty());
    }

    @Test
    void createPvcs_doesNotThrow() {
        NoOpStorageManager manager = new NoOpStorageManager();
        // Should not throw with null client
        assertDoesNotThrow(() -> manager.createPvcs("test-ns", null));
    }

    @Test
    void deletePvcs_doesNotThrow() {
        NoOpStorageManager manager = new NoOpStorageManager();
        // Should not throw with null client
        assertDoesNotThrow(() -> manager.deletePvcs("test-ns", null));
    }

    @Test
    void factoryMethod_none_returnsNoOpStorageManager() {
        StorageManager manager = StorageManager.none();
        assertInstanceOf(NoOpStorageManager.class, manager);
    }
}
