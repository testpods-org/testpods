package org.testpods.core.workload;

import static org.assertj.core.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Unit tests for WorkloadConfig record.
 */
class WorkloadConfigTest {

    // Use a simple client instance for testing (will be null in unit tests without cluster)
    private static KubernetesClient createTestClient() {
        try {
            return new KubernetesClientBuilder().build();
        } catch (Exception e) {
            // Return null if no cluster available - tests that need it will skip
            return null;
        }
    }

    @Test
    void shouldCreateWithBuilder() {
        KubernetesClient client = createTestClient();
        if (client == null) {
            // Skip if no cluster
            return;
        }

        try {
            Container container = new ContainerBuilder()
                .withName("main")
                .withImage("nginx:latest")
                .build();

            PodSpec podSpec = new PodSpecBuilder()
                .addToContainers(container)
                .build();

            WorkloadConfig config = WorkloadConfig.builder()
                .name("test-pod")
                .namespace("test-ns")
                .labels(Map.of("app", "test-pod", "env", "test"))
                .annotations(Map.of("description", "test pod"))
                .podSpec(podSpec)
                .client(client)
                .build();

            assertThat(config.name()).isEqualTo("test-pod");
            assertThat(config.namespace()).isEqualTo("test-ns");
            assertThat(config.labels()).containsEntry("app", "test-pod");
            assertThat(config.labels()).containsEntry("env", "test");
            assertThat(config.annotations()).containsEntry("description", "test pod");
            assertThat(config.podSpec()).isEqualTo(podSpec);
            assertThat(config.client()).isEqualTo(client);
        } finally {
            client.close();
        }
    }

    @Test
    void shouldReturnSelector() {
        KubernetesClient client = createTestClient();
        if (client == null) {
            return;
        }

        try {
            PodSpec podSpec = new PodSpecBuilder().build();

            WorkloadConfig config = new WorkloadConfig(
                "my-app",
                "my-ns",
                Map.of("app", "my-app"),
                null,
                podSpec,
                client
            );

            assertThat(config.getSelector()).containsEntry("app", "my-app");
        } finally {
            client.close();
        }
    }

    @Test
    void shouldHandleNullLabelsAndAnnotations() {
        KubernetesClient client = createTestClient();
        if (client == null) {
            return;
        }

        try {
            PodSpec podSpec = new PodSpecBuilder().build();

            WorkloadConfig config = new WorkloadConfig(
                "my-pod",
                "my-ns",
                null,  // null labels
                null,  // null annotations
                podSpec,
                client
            );

            assertThat(config.labels()).isNotNull().isEmpty();
            assertThat(config.annotations()).isNotNull().isEmpty();
        } finally {
            client.close();
        }
    }

    @Test
    void shouldRejectNullName() {
        KubernetesClient client = createTestClient();
        if (client == null) {
            return;
        }

        try {
            PodSpec podSpec = new PodSpecBuilder().build();

            assertThatThrownBy(() -> new WorkloadConfig(
                null,  // null name
                "my-ns",
                Map.of(),
                Map.of(),
                podSpec,
                client
            )).isInstanceOf(NullPointerException.class)
              .hasMessageContaining("name");
        } finally {
            client.close();
        }
    }

    @Test
    void shouldRejectNullNamespace() {
        KubernetesClient client = createTestClient();
        if (client == null) {
            return;
        }

        try {
            PodSpec podSpec = new PodSpecBuilder().build();

            assertThatThrownBy(() -> new WorkloadConfig(
                "my-pod",
                null,  // null namespace
                Map.of(),
                Map.of(),
                podSpec,
                client
            )).isInstanceOf(NullPointerException.class)
              .hasMessageContaining("namespace");
        } finally {
            client.close();
        }
    }

    @Test
    void shouldRejectNullPodSpec() {
        KubernetesClient client = createTestClient();
        if (client == null) {
            return;
        }

        try {
            assertThatThrownBy(() -> new WorkloadConfig(
                "my-pod",
                "my-ns",
                Map.of(),
                Map.of(),
                null,  // null podSpec
                client
            )).isInstanceOf(NullPointerException.class)
              .hasMessageContaining("podSpec");
        } finally {
            client.close();
        }
    }

    @Test
    void shouldRejectNullClient() {
        PodSpec podSpec = new PodSpecBuilder().build();

        assertThatThrownBy(() -> new WorkloadConfig(
            "my-pod",
            "my-ns",
            Map.of(),
            Map.of(),
            podSpec,
            null  // null client
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("client");
    }

    @Test
    void labelsShouldBeImmutable() {
        KubernetesClient client = createTestClient();
        if (client == null) {
            return;
        }

        try {
            PodSpec podSpec = new PodSpecBuilder().build();

            WorkloadConfig config = new WorkloadConfig(
                "my-pod",
                "my-ns",
                Map.of("app", "my-pod"),
                Map.of(),
                podSpec,
                client
            );

            assertThatThrownBy(() -> config.labels().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        } finally {
            client.close();
        }
    }
}
