package org.testpods.core.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ClusterIPServiceManager.
 */
class ClusterIPServiceManagerTest {

    private ClusterIPServiceManager manager;

    @BeforeEach
    void setUp() {
        manager = new ClusterIPServiceManager();
    }

    @Test
    void shouldReturnNullNameBeforeCreate() {
        assertThat(manager.getName()).isNull();
    }

    @Test
    void shouldReturnNullServiceBeforeCreate() {
        assertThat(manager.getService()).isNull();
    }

    @Test
    void deleteShouldNotThrowBeforeCreate() {
        assertThatCode(() -> manager.delete()).doesNotThrowAnyException();
    }

    @Test
    void shouldImplementServiceManagerInterface() {
        assertThat(manager).isInstanceOf(ServiceManager.class);
    }

    @Test
    void getServiceTypeShouldReturnClusterIP() {
        assertThat(manager.getServiceType()).isEqualTo("ClusterIP");
    }
}
