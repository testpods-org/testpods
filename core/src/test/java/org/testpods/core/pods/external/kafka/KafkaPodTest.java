package org.testpods.core.pods.external.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PodSpec;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.testpods.core.cluster.Namespace;

class KafkaPodTest {

  static class TestableKafkaPod extends KafkaPod {
    Container buildContainerForTest() {
      return buildMainContainer();
    }

    PodSpec buildPodSpecForTest() {
      return buildPodSpec();
    }
  }

  @Test
  void defaultPodShouldUseApacheKafkaImageAndName() {
    KafkaPod pod = new KafkaPod();

    assertThat(pod.getName()).isEqualTo("kafka");
    assertThat(pod.getImage()).isEqualTo(KafkaPod.DEFAULT_IMAGE);
    assertThat(pod.getInternalPort()).isEqualTo(KafkaPod.EXTERNAL_LISTENER_PORT);
  }

  @Test
  void shouldSupportConfluentKafkaImage() {
    KafkaPod pod = new KafkaPod().withConfluentVersion("7.8.0");

    assertThat(pod.getImage()).isEqualTo("confluentinc/cp-kafka:7.8.0");
  }

  @Test
  void buildMainContainerShouldExposeKafkaListeners() {
    TestableKafkaPod pod =
        (TestableKafkaPod) new TestableKafkaPod().inNamespace(Namespace.external("test-ns", null));

    Container container = pod.buildContainerForTest();

    assertThat(container.getName()).isEqualTo("kafka");
    assertThat(container.getPorts())
        .extracting("containerPort")
        .containsExactly(
            KafkaPod.INTERNAL_LISTENER_PORT,
            KafkaPod.EXTERNAL_LISTENER_PORT,
            KafkaPod.CONTROLLER_LISTENER_PORT);
  }

  @Test
  void buildMainContainerShouldConfigureKraftListenersForBothImageFamilies() {
    TestableKafkaPod pod =
        (TestableKafkaPod)
            new TestableKafkaPod()
                .inNamespace(Namespace.external("test-ns", null))
                .withExternalPort(30093);

    Map<String, String> env = envMap(pod.buildContainerForTest());

    assertThat(env)
        .containsEntry("KAFKA_NODE_ID", "1")
        .containsEntry("KAFKA_BROKER_ID", "1")
        .containsEntry("KAFKA_PROCESS_ROLES", "broker,controller")
        .containsEntry(
            "KAFKA_CONTROLLER_QUORUM_VOTERS",
            "1@localhost:" + KafkaPod.CONTROLLER_LISTENER_PORT)
        .containsEntry("KAFKA_INTER_BROKER_LISTENER_NAME", "INTERNAL")
        .containsKey("CLUSTER_ID");

    assertThat(env.get("KAFKA_ADVERTISED_LISTENERS"))
        .isEqualTo(
            "INTERNAL://kafka.test-ns.svc.cluster.local:9092,EXTERNAL://127.0.0.1:30093");
  }

  @Test
  void withKafkaPropertyShouldConvertPropertyNameToDockerEnvironmentVariable() {
    TestableKafkaPod pod =
        (TestableKafkaPod)
            new TestableKafkaPod()
                .inNamespace(Namespace.external("test-ns", null))
                .withKafkaProperty("log.retention.ms", "60000")
                .withKafkaProperty("metric_reporters", "example")
                .withKafkaProperty("some-dashed.property", "value");

    Map<String, String> env = envMap(pod.buildContainerForTest());

    assertThat(env)
        .containsEntry("KAFKA_LOG_RETENTION_MS", "60000")
        .containsEntry("KAFKA_METRIC__REPORTERS", "example")
        .containsEntry("KAFKA_SOME___DASHED_PROPERTY", "value");
  }

  @Test
  void withTopicsShouldValidateAndStoreTopicNames() {
    KafkaPod pod = new KafkaPod().withTopics("order-events", "inventory.events");

    assertThat(pod.getTopics()).containsExactly("order-events", "inventory.events");
    assertThatThrownBy(() -> pod.withTopic("bad topic"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("topic");
  }

  @Test
  void internalBootstrapServersShouldUseServiceDnsAndInternalListener() {
    KafkaPod pod = new KafkaPod().inNamespace(Namespace.external("test-ns", null));

    assertThat(pod.getInternalBootstrapServers())
        .isEqualTo("kafka.test-ns.svc.cluster.local:9092");
  }

  @Test
  void getBootstrapServersShouldRequireStartedPodBecauseItCreatesPortForward() {
    KafkaPod pod = new KafkaPod();

    assertThatThrownBy(pod::getBootstrapServers)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not started");
  }

  @Test
  void withUiShouldAddRedpandaConsoleSidecar() {
    TestableKafkaPod pod =
        (TestableKafkaPod)
            new TestableKafkaPod().inNamespace(Namespace.external("test-ns", null)).withUi();

    PodSpec spec = pod.buildPodSpecForTest();

    assertThat(pod.isUiEnabled()).isTrue();
    assertThat(spec.getContainers()).extracting(Container::getName).contains("redpanda-console");

    Container ui =
        spec.getContainers().stream()
            .filter(container -> "redpanda-console".equals(container.getName()))
            .findFirst()
            .orElseThrow();

    assertThat(ui.getImage()).isEqualTo(KafkaPod.DEFAULT_UI_IMAGE);
    assertThat(ui.getPorts()).extracting("containerPort").containsExactly(KafkaPod.UI_PORT);
    assertThat(envMap(ui)).containsEntry("KAFKA_BROKERS", "localhost:9092");
  }

  @Test
  void withUiImageAndEnvShouldCustomizeRedpandaConsoleSidecar() {
    TestableKafkaPod pod =
        (TestableKafkaPod)
            new TestableKafkaPod()
                .inNamespace(Namespace.external("test-ns", null))
                .withUiImage("docker.redpanda.com/redpandadata/console:v3.7.3")
                .withUiEnv("LOGGER_LEVEL", "debug");

    Container ui =
        pod.buildPodSpecForTest().getContainers().stream()
            .filter(container -> "redpanda-console".equals(container.getName()))
            .findFirst()
            .orElseThrow();

    assertThat(pod.getUiImage()).isEqualTo("docker.redpanda.com/redpandadata/console:v3.7.3");
    assertThat(envMap(ui)).containsEntry("LOGGER_LEVEL", "debug");
  }

  @Test
  void getUiUrlShouldRequireUiToBeEnabled() {
    KafkaPod pod = new KafkaPod();

    assertThatThrownBy(pod::getUiUrl)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not enabled");
  }

  @Test
  void uiAndKafkaExternalPortsMustDiffer() {
    assertThatThrownBy(() -> new KafkaPod().withExternalPort(KafkaPod.DEFAULT_UI_EXTERNAL_PORT).withUi())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("differ");

    assertThatThrownBy(() -> new KafkaPod().withUiExternalPort(KafkaPod.DEFAULT_EXTERNAL_PORT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("differ");
  }

  private static Map<String, String> envMap(Container container) {
    return container.getEnv().stream()
        .collect(Collectors.toMap(EnvVar::getName, EnvVar::getValue));
  }
}
