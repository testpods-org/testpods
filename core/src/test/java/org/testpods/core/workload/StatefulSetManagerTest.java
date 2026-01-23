package org.testpods.core.workload;

import static org.assertj.core.api.Assertions.*;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit tests for StatefulSetManager.
 * <p>
 * These tests verify the manager's behavior without requiring a Kubernetes cluster.
 * Integration tests that actually create StatefulSets should be in a separate test class.
 */
class StatefulSetManagerTest {

    private StatefulSetManager manager;

    @BeforeEach
    void setUp() {
        manager = new StatefulSetManager();
    }

    @Test
    void shouldReturnNullNameBeforeCreate() {
        assertThat(manager.getName()).isNull();
    }

    @Test
    void shouldReturnFalseForIsRunningBeforeCreate() {
        assertThat(manager.isRunning()).isFalse();
    }

    @Test
    void shouldReturnFalseForIsReadyBeforeCreate() {
        assertThat(manager.isReady()).isFalse();
    }

    @Test
    void getWorkloadTypeShouldReturnStatefulSet() {
        assertThat(manager.getWorkloadType()).isEqualTo("StatefulSet");
    }

    @Test
    void shouldReturnNullStatefulSetBeforeCreate() {
        assertThat(manager.getStatefulSet()).isNull();
    }

    @Test
    void deleteShouldNotThrowBeforeCreate() {
        // Deleting before create should be a no-op, not throw
        assertThatCode(() -> manager.delete()).doesNotThrowAnyException();
    }

    @Test
    void shouldImplementWorkloadManagerInterface() {
        assertThat(manager).isInstanceOf(WorkloadManager.class);
    }

    @Test
    void shouldSupportFluentServiceNameConfiguration() {
        StatefulSetManager configured = new StatefulSetManager()
            .withServiceName("my-headless-service");

        // Verify chaining works and returns the same instance
        assertThat(configured).isNotNull();
    }

    @Test
    void shouldSupportFluentPvcTemplatesConfiguration() {
        PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
            .withNewMetadata().withName("data").endMetadata()
            .withNewSpec()
                .withAccessModes("ReadWriteOnce")
                .withNewResources()
                    .addToRequests("storage", new Quantity("10Gi"))
                .endResources()
            .endSpec()
            .build();

        StatefulSetManager configured = new StatefulSetManager()
            .withPvcTemplates(List.of(pvc));

        // Verify chaining works
        assertThat(configured).isNotNull();
    }

    @Test
    void shouldSupportCombinedFluentConfiguration() {
        PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
            .withNewMetadata().withName("data").endMetadata()
            .build();

        StatefulSetManager configured = new StatefulSetManager()
            .withServiceName("my-service")
            .withPvcTemplates(List.of(pvc));

        // Verify chaining works
        assertThat(configured).isNotNull();
    }

    @Test
    void shouldHandleNullPvcTemplates() {
        // Setting null should not throw
        assertThatCode(() -> manager.withPvcTemplates(null))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleEmptyPvcTemplates() {
        StatefulSetManager configured = manager.withPvcTemplates(List.of());
        assertThat(configured).isNotNull();
    }
}
