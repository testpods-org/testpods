package org.testpods.core.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for HeadlessServiceManager.
 */
class HeadlessServiceManagerTest {

    private HeadlessServiceManager manager;

    @BeforeEach
    void setUp() {
        manager = new HeadlessServiceManager();
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
    void getServiceTypeShouldReturnHeadless() {
        assertThat(manager.getServiceType()).isEqualTo("Headless");
    }
}
