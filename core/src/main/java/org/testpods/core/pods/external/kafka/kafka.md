# Kafka TestPod

`KafkaPod` runs a single-node Kafka broker in KRaft combined mode. ZooKeeper is not required.

## Basic JUnit Usage

```java
@TestPod
static KafkaPod kafka =
    new KafkaPod()
        .withTopics("order-events", "inventory-events");

String bootstrapServers = kafka.getBootstrapServers();
```

Other pods inside Kubernetes should use the internal bootstrap address:

```java
service.withEnv("KAFKA_BROKERS", "${kafka.internal.bootstrapServers}");
```

## Images

The default image is `apache/kafka:3.9.1`.

```java
new KafkaPod("apache/kafka:3.9.1");
new KafkaPod().withApacheVersion("3.9.1");
new KafkaPod().withConfluentVersion("7.8.0");
```

Both `apache/kafka` and `confluentinc/cp-kafka` are configured with explicit KRaft environment
variables.

## External Access

Kafka clients use broker metadata returned by Kafka after bootstrap. Because of that, Kafka must
advertise an endpoint the test JVM can reach. `KafkaPod` creates a local Kubernetes port-forward for
the external listener and advertises `127.0.0.1:30092` by default.

Use another local port when running multiple Kafka pods:

```java
new KafkaPod().withExternalPort(30093);
```

## Topics

Topics configured with `withTopics` or `withTopic` are created after the broker is ready:

```java
new KafkaPod()
    .withDefaultPartitions(3)
    .withTopic("order-events")
    .withTopic("audit-events", 1);
```

## Redpanda Console UI

Enable a Redpanda Console sidecar when you want a browser UI for inspecting topics, records, and
consumer groups during a test run:

```java
static KafkaPod kafka =
    new KafkaPod()
        .withTopics("order-events")
        .withUi();
```

After startup, TestPods logs a URL like:

```text
Kafka UI: http://127.0.0.1:30093
```

The UI runs in the same pod as Kafka and connects to `localhost:9092`. TestPods exposes the UI to
the test JVM with a local port-forward. Customize the sidecar image or local port when needed:

```java
new KafkaPod()
    .withUiImage("docker.redpanda.com/redpandadata/console:v3.7.3")
    .withUiExternalPort(31080);
```

## Custom Broker Properties

Use `withKafkaProperty` for broker properties:

```java
new KafkaPod()
    .withKafkaProperty("log.retention.ms", "60000")
    .withAutoCreateTopics(false);
```
