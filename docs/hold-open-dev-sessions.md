# TestPods Hold-Open Developer Sessions

## Problem

TestPods is useful both as a test fixture and as a developer tool for starting dependent services
such as PostgreSQL, Kafka, MongoDB, and UIs. Both workflows should expose useful endpoints on
`localhost:<port>`.

Fabric8 `LocalPortForward` gives convenient localhost access, but the byte relay lives inside the
test JVM:

```text
browser/curl -> 127.0.0.1:31093 -> Fabric8 relay in test JVM -> Kubernetes API server -> pod:8080
```

If a debugger suspends all JVM threads, browser/curl traffic can hang because the relay thread is
paused. That makes Fabric8 forwarding a poor default for paused-debug developer sessions.

## Localhost Forwarding

The preferred TestPods developer contract is:

```text
service endpoint == 127.0.0.1:<port>
```

For Minikube this is implemented with an external `kubectl port-forward` process:

```text
browser/curl -> 127.0.0.1:31093 -> kubectl process -> Kubernetes API server -> pod:8080
```

Because `kubectl` is a separate process, it keeps relaying traffic even if the test JVM is stopped
at a breakpoint or all JVM threads are suspended.

## Access Strategy

Minikube clusters default to externally managed localhost forwarding:

```java
K8sCluster cluster = K8sCluster.newMinikube();
```

The explicit builder form is:

```java
K8sCluster cluster =
    MinikubeCluster.builder()
        .localhostPortForwardAccess()
        .build();
```

Internally, TestPods starts commands like:

```bash
kubectl --context testpods -n <namespace> port-forward svc/kafka-it 30092:9093
kubectl --context testpods -n <namespace> port-forward svc/kafka-it 30093:8080
```

The user-facing endpoints stay stable:

```text
Kafka bootstrap servers: 127.0.0.1:30092
Kafka UI: http://127.0.0.1:30093/topics/?showInternal=true
```

## Kafka Defaults

KafkaPod should be usable without repeating localhost or port values in tests:

```java
KafkaPod kafka =
    new KafkaPod()
        .withName("kafka")
        .withUi()
        .inCluster(cluster);
```

Defaults:

- Kafka external listener: `127.0.0.1:30092`
- Redpanda Console UI: `http://127.0.0.1:30093/topics/?showInternal=true`

The caller can still override fixed ports when needed:

```java
new KafkaPod()
    .withExternalPort(31092)
    .withUiExternalPort(31093)
    .withUi();
```

## Tradeoffs

Using external `kubectl port-forward` as the Minikube default has a few concrete costs:

- `kubectl` must be installed and able to use the selected kube context.
- Fixed localhost ports can conflict with another running test or developer session.
- Starting child processes is heavier than Fabric8 in-process forwarding.
- Cleanup must terminate the child processes.

Those costs are acceptable for the current TestPods Minikube workflow because the benefit is a
single localhost behavior that works both in ordinary tests and paused-debug developer sessions.

For environments that do not want child processes, callers can still choose another access strategy:

```java
MinikubeCluster.builder()
    .accessStrategy(ExternalAccessStrategy.portForward())
    .build();
```

## Hold-Open Mode

`holdOpen` is a developer-session lifecycle helper. It starts pods, prints endpoints, then blocks
until the developer stops the process.

Possible API:

```java
TestPodsSession session =
    TestPodsSession.builder()
        .cluster(K8sCluster.newMinikube())
        .pod(new PostgreSQLPod().withName("postgres").withFixedExposedPort(30432, 5432))
        .pod(new KafkaPod().withName("kafka").withUi())
        .build();

session.start();
session.printEndpoints();
session.holdOpen();
```

Expected behavior:

1. Create or attach to the cluster.
2. Create a namespace.
3. Start the configured pods.
4. Print connection strings and UI URLs.
5. Block until Ctrl+C or an explicit stop signal.
6. Run normal cleanup on shutdown.

Example output:

```text
Namespace: testpods-dev-a8f3
PostgreSQL JDBC: jdbc:postgresql://127.0.0.1:30432/test
Kafka bootstrap: 127.0.0.1:30092
Kafka UI: http://127.0.0.1:30093/topics/?showInternal=true

Press Ctrl+C to stop and clean up.
Manual cleanup: kubectl delete namespace testpods-dev-a8f3
```
