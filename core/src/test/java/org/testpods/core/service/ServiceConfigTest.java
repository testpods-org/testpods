package org.testpods.core.service;

import static org.assertj.core.api.Assertions.*;

import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/** Unit tests for ServiceConfig record. */
class ServiceConfigTest {

  private static KubernetesClient createTestClient() {
    try {
      return new KubernetesClientBuilder().build();
    } catch (Exception e) {
      return null;
    }
  }

  @Test
  void shouldCreateWithBuilder() {
    KubernetesClient client = createTestClient();
    if (client == null) return;

    try {
      ServiceConfig config =
          ServiceConfig.builder()
              .name("test-svc")
              .namespace("test-ns")
              .port(8080)
              .labels(Map.of("app", "test-svc"))
              .selector(Map.of("app", "test-pod"))
              .client(client)
              .build();

      assertThat(config.name()).isEqualTo("test-svc");
      assertThat(config.namespace()).isEqualTo("test-ns");
      assertThat(config.port()).isEqualTo(8080);
      assertThat(config.labels()).containsEntry("app", "test-svc");
      assertThat(config.selector()).containsEntry("app", "test-pod");
      assertThat(config.client()).isEqualTo(client);
    } finally {
      client.close();
    }
  }

  @Test
  void shouldHandleNullLabelsAndSelector() {
    KubernetesClient client = createTestClient();
    if (client == null) return;

    try {
      ServiceConfig config =
          new ServiceConfig("svc", "ns", 80, null, null, null, null, null, null, client);

      assertThat(config.labels()).isNotNull().isEmpty();
      assertThat(config.selector()).isNotNull().isEmpty();
      assertThat(config.customizers()).isNotNull().isEmpty();
    } finally {
      client.close();
    }
  }

  @Test
  void shouldSupportConfiguredNodePort() {
    KubernetesClient client = createTestClient();
    if (client == null) return;

    try {
      ServiceConfig config =
          ServiceConfig.builder()
              .name("svc")
              .namespace("ns")
              .port(80)
              .nodePort(30080)
              .client(client)
              .build();

      assertThat(config.nodePort()).isEqualTo(30080);
    } finally {
      client.close();
    }
  }

  @Test
  void shouldSupportMultiplePortsAndNodePorts() {
    KubernetesClient client = createTestClient();
    if (client == null) return;

    try {
      ServiceConfig config =
          ServiceConfig.builder()
              .name("svc")
              .namespace("ns")
              .port(5432)
              .ports(List.of(5432, 9092))
              .nodePorts(Map.of(5432, 30432, 9092, 30092))
              .client(client)
              .build();

      assertThat(config.ports()).containsExactly(5432, 9092);
      assertThat(config.nodePorts()).containsEntry(5432, 30432).containsEntry(9092, 30092);
    } finally {
      client.close();
    }
  }

  @Test
  void shouldRejectNullName() {
    KubernetesClient client = createTestClient();
    if (client == null) return;

    try {
      assertThatThrownBy(
              () ->
                  new ServiceConfig(
                      null, "ns", 80, null, Map.of(), Map.of(), List.of(), null, null, client))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("name");
    } finally {
      client.close();
    }
  }

  @Test
  void shouldRejectNullNamespace() {
    KubernetesClient client = createTestClient();
    if (client == null) return;

    try {
      assertThatThrownBy(
              () ->
                  new ServiceConfig(
                      "svc", null, 80, null, Map.of(), Map.of(), List.of(), null, null, client))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("namespace");
    } finally {
      client.close();
    }
  }

  @Test
  void shouldRejectInvalidPort() {
    KubernetesClient client = createTestClient();
    if (client == null) return;

    try {
      assertThatThrownBy(
              () ->
                  new ServiceConfig(
                      "svc", "ns", 0, null, Map.of(), Map.of(), List.of(), null, null, client))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("port");

      assertThatThrownBy(
              () ->
                  new ServiceConfig(
                      "svc", "ns", -1, null, Map.of(), Map.of(), List.of(), null, null, client))
          .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(
              () ->
                  new ServiceConfig(
                      "svc", "ns", 70000, null, Map.of(), Map.of(), List.of(), null, null, client))
          .isInstanceOf(IllegalArgumentException.class);
    } finally {
      client.close();
    }
  }

  @Test
  void shouldRejectInvalidNodePort() {
    KubernetesClient client = createTestClient();
    if (client == null) return;

    try {
      assertThatThrownBy(
              () ->
                  new ServiceConfig(
                      "svc", "ns", 80, null, Map.of(), Map.of(), List.of(), 0, null, client))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("nodePort");

      assertThatThrownBy(
              () ->
                  new ServiceConfig(
                      "svc",
                      "ns",
                      80,
                      null,
                      Map.of(),
                      Map.of(),
                      List.of(),
                      70000,
                      null,
                      client))
          .isInstanceOf(IllegalArgumentException.class);
    } finally {
      client.close();
    }
  }

  @Test
  void shouldRejectNullClient() {
    assertThatThrownBy(
            () ->
                new ServiceConfig(
                    "svc", "ns", 80, null, Map.of(), Map.of(), List.of(), null, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("client");
  }

  @Test
  void shouldSupportCustomizers() {
    KubernetesClient client = createTestClient();
    if (client == null) return;

    try {
      UnaryOperator<ServiceBuilder> customizer =
          builder -> builder.editSpec().withType("LoadBalancer").endSpec();

      ServiceConfig config =
          ServiceConfig.builder()
              .name("svc")
              .namespace("ns")
              .port(80)
              .addCustomizer(customizer)
              .client(client)
              .build();

      assertThat(config.customizers()).hasSize(1);
    } finally {
      client.close();
    }
  }

  @Test
  void labelsShouldBeImmutable() {
    KubernetesClient client = createTestClient();
    if (client == null) return;

    try {
      ServiceConfig config =
          new ServiceConfig(
              "svc",
              "ns",
              80,
              null,
              Map.of("key", "value"),
              Map.of(),
              List.of(),
              null,
              null,
              client);

      assertThatThrownBy(() -> config.labels().put("new", "value"))
          .isInstanceOf(UnsupportedOperationException.class);
    } finally {
      client.close();
    }
  }
}
