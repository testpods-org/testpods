package org.testpods.core.pods.external.kafka;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.testpods.core.ExecResult;
import org.testpods.core.PropertyContext;
import org.testpods.core.pods.PodLifecycleHooks;
import org.testpods.core.pods.StatefulSetPod;
import org.testpods.core.service.ClusterIPServiceManager;
import org.testpods.core.service.CompositeServiceManager;
import org.testpods.core.service.HeadlessServiceManager;
import org.testpods.core.service.ServiceManager;
import org.testpods.core.wait.WaitStrategy;
import org.testpods.core.workload.StatefulSetManager;
import org.testpods.core.workload.WorkloadManager;

/**
 * A single-node Apache Kafka broker for integration testing.
 *
 * <p>The pod runs Kafka in KRaft combined mode, so no ZooKeeper pod is required. It supports both
 * commonly used Docker images:
 *
 * <ul>
 *   <li>{@code apache/kafka}
 *   <li>{@code confluentinc/cp-kafka}
 * </ul>
 *
 * <p>Kafka needs advertised listener metadata that clients can reach. TestPods therefore exposes a
 * dedicated external listener through a local Kubernetes port-forward and advertises that local
 * endpoint to clients.
 */
@Slf4j
public class KafkaPod extends StatefulSetPod<KafkaPod> implements PodLifecycleHooks {

  public static final String DEFAULT_IMAGE = "apache/kafka:3.9.1";
  public static final String DEFAULT_CONFLUENT_IMAGE = "confluentinc/cp-kafka:7.8.0";
  public static final int INTERNAL_LISTENER_PORT = 9092;
  public static final int EXTERNAL_LISTENER_PORT = 9093;
  public static final int CONTROLLER_LISTENER_PORT = 9094;
  public static final int DEFAULT_EXTERNAL_PORT = 30092;
  public static final String DEFAULT_UI_IMAGE = "docker.redpanda.com/redpandadata/console:v3.7.3";
  public static final int UI_PORT = 8080;
  public static final int DEFAULT_UI_EXTERNAL_PORT = 30093;

  private static final String INTERNAL_LISTENER = "INTERNAL";
  private static final String EXTERNAL_LISTENER = "EXTERNAL";
  private static final String CONTROLLER_LISTENER = "CONTROLLER";
  private static final Pattern TOPIC_NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,249}");
  private static final String TOPIC_COMMAND =
      "cli=$(findKafkaTopics); \"$cli\" \"$@\"";
  private static final String FIND_KAFKA_TOPICS_FUNCTION =
      "findKafkaTopics() { "
          + "if command -v kafka-topics >/dev/null 2>&1; then command -v kafka-topics; "
          + "elif command -v kafka-topics.sh >/dev/null 2>&1; then command -v kafka-topics.sh; "
          + "elif [ -x /opt/kafka/bin/kafka-topics.sh ]; then echo /opt/kafka/bin/kafka-topics.sh; "
          + "else echo kafka-topics.sh; fi; }";

  private String image = DEFAULT_IMAGE;
  private String clusterId = randomKafkaClusterId();
  private String externalHost = "127.0.0.1";
  private int externalPort = DEFAULT_EXTERNAL_PORT;
  private int defaultPartitions = 1;
  private short defaultReplicationFactor = 1;
  private boolean autoCreateTopics = true;
  private boolean uiEnabled;
  private String uiImage = DEFAULT_UI_IMAGE;
  private String uiExternalHost = "127.0.0.1";
  private int uiExternalPort = DEFAULT_UI_EXTERNAL_PORT;
  private final Map<String, TopicSpec> topics = new LinkedHashMap<>();
  private final Map<String, String> kafkaEnv = new LinkedHashMap<>();
  private final Map<String, String> uiEnv = new LinkedHashMap<>();
  private volatile LocalPortForward externalPortForward;
  private volatile LocalPortForward uiPortForward;

  /** Create a Kafka pod with the default Apache Kafka image. */
  public KafkaPod() {
    this(DEFAULT_IMAGE);
  }

  /**
   * Create a Kafka pod with a specific image.
   *
   * @param image image reference, for example {@code apache/kafka:3.9.1} or {@code
   *     confluentinc/cp-kafka:7.8.0}
   */
  public KafkaPod(String image) {
    this.image = image;
    this.name = "kafka";
    this.exposedPorts.add(INTERNAL_LISTENER_PORT);
    this.exposedPorts.add(CONTROLLER_LISTENER_PORT);
  }

  /** Use an {@code apache/kafka:<version>} image. */
  public KafkaPod withVersion(String version) {
    return withApacheVersion(version);
  }

  /** Use an {@code apache/kafka:<version>} image. */
  public KafkaPod withApacheVersion(String version) {
    this.image = "apache/kafka:" + version;
    return this;
  }

  /** Use the default supported Confluent {@code cp-kafka} image. */
  public KafkaPod withConfluentImage() {
    this.image = DEFAULT_CONFLUENT_IMAGE;
    return this;
  }

  /** Use a {@code confluentinc/cp-kafka:<version>} image. */
  public KafkaPod withConfluentVersion(String version) {
    this.image = "confluentinc/cp-kafka:" + version;
    return this;
  }

  /**
   * Set the local port used for external Kafka clients.
   *
   * <p>This port is advertised by Kafka and bound locally via Kubernetes port-forwarding after the
   * pod starts.
   */
  public KafkaPod withExternalPort(int port) {
    validatePort(port, "external port");
    if (uiEnabled && port == uiExternalPort) {
      throw new IllegalArgumentException("external Kafka port must differ from UI external port");
    }
    this.externalPort = port;
    return this;
  }

  /**
   * Override the host advertised to external Kafka clients.
   *
   * <p>The default is {@code 127.0.0.1}, matching the local port-forward created by this pod.
   */
  public KafkaPod withExternalHost(String host) {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("external host must not be blank");
    }
    this.externalHost = host;
    return this;
  }

  /** Override the KRaft cluster id used to format broker storage. */
  public KafkaPod withClusterId(String clusterId) {
    if (clusterId == null || clusterId.isBlank()) {
      throw new IllegalArgumentException("clusterId must not be blank");
    }
    this.clusterId = clusterId;
    return this;
  }

  /** Configure the default partition count for topics created through {@link #withTopics}. */
  public KafkaPod withDefaultPartitions(int partitions) {
    if (partitions < 1) {
      throw new IllegalArgumentException("partitions must be at least 1");
    }
    this.defaultPartitions = partitions;
    return this;
  }

  /** Enable or disable Kafka broker-side automatic topic creation. */
  public KafkaPod withAutoCreateTopics(boolean enabled) {
    this.autoCreateTopics = enabled;
    return this;
  }

  /** Enable the Redpanda Console UI sidecar with the default image and local port. */
  public KafkaPod withUi() {
    return withUi(true);
  }

  /**
   * Enable or disable the Redpanda Console UI sidecar.
   *
   * <p>When enabled, TestPods creates a local port-forward and logs the UI URL after Kafka starts.
   */
  public KafkaPod withUi(boolean enabled) {
    this.uiEnabled = enabled;
    if (enabled) {
      if (uiExternalPort == externalPort) {
        throw new IllegalArgumentException("UI external port must differ from Kafka external port");
      }
      this.exposedPorts.add(UI_PORT);
    } else {
      this.exposedPorts.remove(UI_PORT);
      closeUiPortForward();
    }
    return this;
  }

  /** Enable the Redpanda Console UI sidecar. Alias for {@link #withUi()}. */
  public KafkaPod withRedpandaConsole() {
    return withUi();
  }

  /** Set the Redpanda Console image used by the UI sidecar and enable the UI. */
  public KafkaPod withUiImage(String image) {
    if (image == null || image.isBlank()) {
      throw new IllegalArgumentException("UI image must not be blank");
    }
    this.uiImage = image;
    return withUi();
  }

  /** Set the local port used for the UI and enable the UI. */
  public KafkaPod withUiExternalPort(int port) {
    validatePort(port, "UI external port");
    if (port == externalPort) {
      throw new IllegalArgumentException("UI external port must differ from Kafka external port");
    }
    this.uiExternalPort = port;
    return withUi();
  }

  /** Set the host used in the logged UI URL and enable the UI. */
  public KafkaPod withUiExternalHost(String host) {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("UI external host must not be blank");
    }
    this.uiExternalHost = host;
    return withUi();
  }

  /** Add or override a Redpanda Console environment variable and enable the UI. */
  public KafkaPod withUiEnv(String name, String value) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("UI environment variable name must not be blank");
    }
    this.uiEnv.put(name, value);
    return withUi();
  }

  /** Add topics to create after Kafka is ready, using the default partition settings. */
  public KafkaPod withTopics(String... topics) {
    for (String topic : topics) {
      withTopic(topic);
    }
    return this;
  }

  /** Add one topic to create after Kafka is ready. */
  public KafkaPod withTopic(String topic) {
    return withTopic(topic, defaultPartitions, defaultReplicationFactor);
  }

  /** Add one topic to create after Kafka is ready. */
  public KafkaPod withTopic(String topic, int partitions) {
    return withTopic(topic, partitions, defaultReplicationFactor);
  }

  /** Add one topic to create after Kafka is ready. */
  public KafkaPod withTopic(String topic, int partitions, short replicationFactor) {
    validateTopic(topic);
    if (partitions < 1) {
      throw new IllegalArgumentException("partitions must be at least 1");
    }
    if (replicationFactor < 1) {
      throw new IllegalArgumentException("replicationFactor must be at least 1");
    }
    this.topics.put(topic, new TopicSpec(topic, partitions, replicationFactor));
    return this;
  }

  /**
   * Add or override a Kafka environment variable.
   *
   * <p>The name must be the final environment variable name, for example {@code
   * KAFKA_LOG_RETENTION_MS}.
   */
  public KafkaPod withKafkaEnv(String name, String value) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("environment variable name must not be blank");
    }
    this.kafkaEnv.put(name, value);
    return this;
  }

  /**
   * Add or override a Kafka broker property using Docker image environment variable conversion.
   *
   * <p>For example {@code withKafkaProperty("log.retention.ms", "60000")} sets {@code
   * KAFKA_LOG_RETENTION_MS=60000}.
   */
  public KafkaPod withKafkaProperty(String property, String value) {
    if (property == null || property.isBlank()) {
      throw new IllegalArgumentException("property must not be blank");
    }
    return withKafkaEnv(toKafkaEnvName(property), value);
  }

  /** Get the image used by this pod. */
  public String getImage() {
    return image;
  }

  /** Get the generated or configured KRaft cluster id. */
  public String getClusterId() {
    return clusterId;
  }

  /** Get the topics configured for creation. */
  public List<String> getTopics() {
    return List.copyOf(topics.keySet());
  }

  /** Return whether the Redpanda Console UI sidecar is enabled. */
  public boolean isUiEnabled() {
    return uiEnabled;
  }

  /** Get the Redpanda Console UI image. */
  public String getUiImage() {
    return uiImage;
  }

  /** Get the Redpanda Console UI URL for test code running outside Kubernetes. */
  public String getUiUrl() {
    if (!uiEnabled) {
      throw new IllegalStateException("Kafka UI is not enabled. Call withUi() first.");
    }
    ensureUiPortForward();
    return "http://" + uiExternalHost + ":" + uiExternalPort;
  }

  /** Get the bootstrap servers for test code running outside Kubernetes. */
  public String getBootstrapServers() {
    return getExternalHost() + ":" + getExternalPort();
  }

  /** Get the bootstrap servers for pods running inside Kubernetes. */
  public String getInternalBootstrapServers() {
    return getInternalHost() + ":" + INTERNAL_LISTENER_PORT;
  }

  @Override
  public int getInternalPort() {
    return EXTERNAL_LISTENER_PORT;
  }

  @Override
  public String getExternalHost() {
    ensureExternalPortForward();
    return externalHost;
  }

  @Override
  public int getExternalPort() {
    ensureExternalPortForward();
    return externalPort;
  }

  @Override
  public int getMappedPort(int originalPort) {
    if (originalPort == EXTERNAL_LISTENER_PORT) {
      return getExternalPort();
    }
    return super.getMappedPort(originalPort);
  }

  @Override
  public void publishProperties(PropertyContext ctx) {
    String prefix = getName();

    ctx.publish(prefix + ".bootstrapServers", this::getBootstrapServers);
    ctx.publish(prefix + ".host", this::getExternalHost);
    ctx.publish(prefix + ".port", () -> String.valueOf(getExternalPort()));

    ctx.publish(prefix + ".external.bootstrapServers", this::getBootstrapServers);
    ctx.publish(prefix + ".external.host", this::getExternalHost);
    ctx.publish(prefix + ".external.port", () -> String.valueOf(getExternalPort()));

    ctx.publish(prefix + ".internal.bootstrapServers", this::getInternalBootstrapServers);
    ctx.publish(prefix + ".internal.host", this::getInternalHost);
    ctx.publish(prefix + ".internal.port", () -> String.valueOf(INTERNAL_LISTENER_PORT));

    if (uiEnabled) {
      ctx.publish(prefix + ".ui.url", this::getUiUrl);
      ctx.publish(prefix + ".ui.host", () -> uiExternalHost);
      ctx.publish(prefix + ".ui.port", () -> String.valueOf(uiExternalPort));
    }
  }

  @Override
  protected WaitStrategy getDefaultWaitStrategy() {
    return new KafkaWaitStrategy().withTimeout(Duration.ofMinutes(3));
  }

  @Override
  protected Container buildMainContainer() {
    Map<String, String> env = buildKafkaEnvironment();

    return new ContainerBuilder()
        .withName("kafka")
        .withImage(image)
        .withPorts(
            new ContainerPortBuilder()
                .withName("internal")
                .withContainerPort(INTERNAL_LISTENER_PORT)
                .build(),
            new ContainerPortBuilder()
                .withName("external")
                .withContainerPort(EXTERNAL_LISTENER_PORT)
                .build(),
            new ContainerPortBuilder()
                .withName("controller")
                .withContainerPort(CONTROLLER_LISTENER_PORT)
                .build())
        .withEnv(toEnvVars(env))
        .withNewReadinessProbe()
        .withNewExec()
        .withCommand("sh", "-c", kafkaCliReadinessCommand())
        .endExec()
        .withInitialDelaySeconds(10)
        .withPeriodSeconds(5)
        .withTimeoutSeconds(5)
        .endReadinessProbe()
        .withNewLivenessProbe()
        .withNewExec()
        .withCommand("sh", "-c", kafkaCliReadinessCommand())
        .endExec()
        .withInitialDelaySeconds(30)
        .withPeriodSeconds(10)
        .withTimeoutSeconds(5)
        .endLivenessProbe()
        .build();
  }

  @Override
  protected PodSpecBuilder applyPodCustomizations(PodSpecBuilder baseSpec) {
    if (uiEnabled) {
      baseSpec.addToContainers(buildUiContainer());
    }
    return super.applyPodCustomizations(baseSpec);
  }

  @Override
  protected WorkloadManager createWorkloadManager() {
    return new StatefulSetManager().withServiceName(name + "-headless");
  }

  @Override
  protected ServiceManager createServiceManager() {
    return new CompositeServiceManager(new ClusterIPServiceManager(), new HeadlessServiceManager())
        .withSuffixes("", "-headless");
  }

  @Override
  public void postStart() {
    ensureExternalPortForward();
    if (uiEnabled) {
      ensureUiPortForward();
    }
    createTopics();
    log.info("Kafka TestPod '{}' is ready", name);
    log.info("Kafka image: {}", image);
    log.info("Kafka namespace: {}", namespace.getName());
    log.info("Kafka bootstrap servers: {}", getBootstrapServers());
    log.info("Kafka internal bootstrap servers: {}", getInternalBootstrapServers());
    if (!topics.isEmpty()) {
      log.info("Kafka topics: {}", topics.keySet());
    }
    if (uiEnabled) {
      log.info("Kafka UI: {}", getUiUrl());
    }
  }

  @Override
  public void preStop() {
    closeUiPortForward();
    closeExternalPortForward();
  }

  @Override
  protected List<String> buildCustomDeploymentDetailLines() {
    List<String> lines = new ArrayList<>();
    lines.add("kafka.image: " + image);
    lines.add("kafka.bootstrapServers: " + getBootstrapServers());
    lines.add("kafka.internalBootstrapServers: " + getInternalBootstrapServers());
    lines.add("kafka.externalListener: " + externalHost + ":" + externalPort);
    lines.add("kafka.internalListener: " + getInternalHost() + ":" + INTERNAL_LISTENER_PORT);
    lines.add("kafka.topics: " + getTopics());
    if (uiEnabled) {
      lines.add("kafka.ui.url: " + getUiUrl());
      lines.add("kafka.ui.image: " + uiImage);
    }
    return lines;
  }

  private Container buildUiContainer() {
    Map<String, String> env = new LinkedHashMap<>();
    env.put("KAFKA_BROKERS", "localhost:" + INTERNAL_LISTENER_PORT);
    env.putAll(uiEnv);

    return new ContainerBuilder()
        .withName("redpanda-console")
        .withImage(uiImage)
        .withPorts(
            new ContainerPortBuilder()
                .withName("ui")
                .withContainerPort(UI_PORT)
                .build())
        .withEnv(toEnvVars(env))
        .withNewReadinessProbe()
        .withNewHttpGet()
        .withPath("/")
        .withPort(new io.fabric8.kubernetes.api.model.IntOrString(UI_PORT))
        .endHttpGet()
        .withInitialDelaySeconds(5)
        .withPeriodSeconds(5)
        .withTimeoutSeconds(3)
        .endReadinessProbe()
        .withNewLivenessProbe()
        .withNewHttpGet()
        .withPath("/")
        .withPort(new io.fabric8.kubernetes.api.model.IntOrString(UI_PORT))
        .endHttpGet()
        .withInitialDelaySeconds(20)
        .withPeriodSeconds(10)
        .withTimeoutSeconds(3)
        .endLivenessProbe()
        .build();
  }

  private Map<String, String> buildKafkaEnvironment() {
    String internalBootstrap = getInternalHost() + ":" + INTERNAL_LISTENER_PORT;
    String externalBootstrap = externalHost + ":" + externalPort;
    String controller = "localhost:" + CONTROLLER_LISTENER_PORT;

    Map<String, String> env = new LinkedHashMap<>();
    env.put("CLUSTER_ID", clusterId);
    env.put("KAFKA_NODE_ID", "1");
    env.put("KAFKA_BROKER_ID", "1");
    env.put("KAFKA_PROCESS_ROLES", "broker,controller");
    env.put(
        "KAFKA_LISTENERS",
        INTERNAL_LISTENER
            + "://0.0.0.0:"
            + INTERNAL_LISTENER_PORT
            + ","
            + EXTERNAL_LISTENER
            + "://0.0.0.0:"
            + EXTERNAL_LISTENER_PORT
            + ","
            + CONTROLLER_LISTENER
            + "://0.0.0.0:"
            + CONTROLLER_LISTENER_PORT);
    env.put(
        "KAFKA_ADVERTISED_LISTENERS",
        INTERNAL_LISTENER
            + "://"
            + internalBootstrap
            + ","
            + EXTERNAL_LISTENER
            + "://"
            + externalBootstrap);
    env.put(
        "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
        INTERNAL_LISTENER
            + ":PLAINTEXT,"
            + EXTERNAL_LISTENER
            + ":PLAINTEXT,"
            + CONTROLLER_LISTENER
            + ":PLAINTEXT");
    env.put("KAFKA_INTER_BROKER_LISTENER_NAME", INTERNAL_LISTENER);
    env.put("KAFKA_CONTROLLER_LISTENER_NAMES", CONTROLLER_LISTENER);
    env.put("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@" + controller);
    env.put("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1");
    env.put("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
    env.put("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
    env.put("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0");
    env.put("KAFKA_AUTO_CREATE_TOPICS_ENABLE", Boolean.toString(autoCreateTopics));
    env.put("KAFKA_NUM_PARTITIONS", Integer.toString(defaultPartitions));
    env.putAll(kafkaEnv);
    return env;
  }

  private void createTopics() {
    for (TopicSpec topic : topics.values()) {
      ExecResult result =
          execInMainContainer(
              new String[] {
                "sh",
                "-c",
                FIND_KAFKA_TOPICS_FUNCTION + " " + TOPIC_COMMAND,
                "kafka-topics",
                "--bootstrap-server",
                "localhost:" + INTERNAL_LISTENER_PORT,
                "--create",
                "--if-not-exists",
                "--topic",
                topic.name(),
                "--partitions",
                Integer.toString(topic.partitions()),
                "--replication-factor",
                Short.toString(topic.replicationFactor())
              });
      if (result.exitCode() != 0) {
        throw new IllegalStateException(
            "Failed to create Kafka topic '"
                + topic.name()
                + "': "
                + result.stderr()
                + result.stdout());
      }
    }
  }

  ExecResult execInMainContainer(String... command) {
    return exec(command);
  }

  private synchronized void ensureExternalPortForward() {
    if (externalPortForward != null) {
      return;
    }
    if (cluster == null || namespace == null) {
      throw new IllegalStateException(
          "Kafka pod " + name + " is not started; external endpoint is unavailable");
    }
    try {
      externalPortForward =
          getClient()
              .services()
              .inNamespace(namespace.getName())
              .withName(name)
              .portForward(EXTERNAL_LISTENER_PORT, externalPort);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to create local port-forward for Kafka on "
              + externalHost
              + ":"
              + externalPort
              + ". Choose another port with withExternalPort(...).",
          e);
    }
  }

  private synchronized void closeExternalPortForward() {
    if (externalPortForward == null) {
      return;
    }
    try {
      externalPortForward.close();
    } catch (IOException e) {
      log.debug("Failed to close Kafka port-forward for '{}': {}", name, e.getMessage());
    } finally {
      externalPortForward = null;
    }
  }

  private synchronized void ensureUiPortForward() {
    if (uiPortForward != null) {
      return;
    }
    if (!uiEnabled) {
      throw new IllegalStateException("Kafka UI is not enabled. Call withUi() first.");
    }
    if (cluster == null || namespace == null) {
      throw new IllegalStateException(
          "Kafka pod " + name + " is not started; UI endpoint is unavailable");
    }
    try {
      uiPortForward =
          getClient()
              .services()
              .inNamespace(namespace.getName())
              .withName(name)
              .portForward(UI_PORT, uiExternalPort);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to create local port-forward for Kafka UI on "
              + uiExternalHost
              + ":"
              + uiExternalPort
              + ". Choose another port with withUiExternalPort(...).",
          e);
    }
  }

  private synchronized void closeUiPortForward() {
    if (uiPortForward == null) {
      return;
    }
    try {
      uiPortForward.close();
    } catch (IOException e) {
      log.debug("Failed to close Kafka UI port-forward for '{}': {}", name, e.getMessage());
    } finally {
      uiPortForward = null;
    }
  }

  private static String kafkaCliReadinessCommand() {
    return FIND_KAFKA_TOPICS_FUNCTION
        + " cli=$(findKafkaTopics); \"$cli\" --bootstrap-server localhost:"
        + INTERNAL_LISTENER_PORT
        + " --list >/dev/null";
  }

  static String kafkaCliCommand() {
    return FIND_KAFKA_TOPICS_FUNCTION + " " + TOPIC_COMMAND;
  }

  private static String toKafkaEnvName(String property) {
    StringBuilder env = new StringBuilder("KAFKA_");
    for (int i = 0; i < property.length(); i++) {
      char c = property.charAt(i);
      if (c == '.') {
        env.append('_');
      } else if (c == '_') {
        env.append("__");
      } else if (c == '-') {
        env.append("___");
      } else {
        env.append(Character.toUpperCase(c));
      }
    }
    return env.toString();
  }

  private static String randomKafkaClusterId() {
    UUID uuid = UUID.randomUUID();
    byte[] bytes =
        ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits())
            .array();
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static void validateTopic(String topic) {
    if (topic == null || !TOPIC_NAME_PATTERN.matcher(topic).matches()) {
      throw new IllegalArgumentException(
          "topic must be 1-249 characters and contain only letters, digits, '.', '_' or '-'");
    }
    if (".".equals(topic) || "..".equals(topic)) {
      throw new IllegalArgumentException("topic must not be '.' or '..'");
    }
  }

  private static void validatePort(int port, String name) {
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException(name + " must be between 1 and 65535");
    }
  }

  private record TopicSpec(String name, int partitions, short replicationFactor) {}
}
