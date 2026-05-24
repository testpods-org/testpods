package org.testpods.core.pods.local;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.testpods.core.ExecResult;
import org.testpods.core.PropertyContext;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.Namespace;
import org.testpods.core.pods.Pod;
import org.testpods.core.pods.TestPodDefaults;
import org.testpods.core.pods.builders.InitContainerBuilder;
import org.testpods.core.pods.builders.SidecarBuilder;
import org.testpods.core.wait.WaitStrategy;

/**
 * A {@link Pod} implementation that exposes a service running on the developer's laptop to the
 * cluster, instead of scheduling a workload inside the cluster.
 *
 * <p>It works by creating a Kubernetes {@code Service} with no selector and a manually managed
 * {@code Endpoints} object whose addresses point at the host machine's IP. In-cluster pods can then
 * resolve {@code <name>.<namespace>.svc.cluster.local} and reach the locally running service.
 *
 * <p>Host resolution prefers Minikube's {@code host.minikube.internal} DNS name; if that fails to
 * resolve (e.g. on hyperkit/qemu drivers), it falls back to {@link InetAddress#getLocalHost()}.
 * The resolved IP is written to the {@code Endpoints} resource because Kubernetes requires an IP
 * address there, not a DNS name.
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * new LocalServicePod("product-service")
 *     .onHostPort(8082)             // port the local service listens on
 *     .exposingServicePort(8082);   // port the cluster Service exposes
 * }</pre>
 */
@Slf4j
public class LocalServicePod implements Pod<LocalServicePod> {

  private static final String HOST_DNS = "host.minikube.internal";

  private String name;
  private K8sCluster cluster;
  private Namespace namespace;
  private String explicitNamespaceName;
  private Integer hostPort;
  private Integer exposingServicePort;
  private final Map<String, String> labels = new LinkedHashMap<>();
  private final Map<String, String> annotations = new LinkedHashMap<>();

  private boolean started;
  private Service createdService;
  private Endpoints createdEndpoints;

  /**
   * Create a new LocalServicePod with the given Kubernetes Service name.
   *
   * @param name the K8s Service / Endpoints name that in-cluster clients will resolve
   */
  public LocalServicePod(String name) {
    this.name = name;
  }

  /**
   * Set the TCP port on the developer's laptop that the local service is listening on. This
   * becomes the {@code port} of the {@code Endpoints} subset.
   *
   * @param port host port (1-65535)
   */
  public LocalServicePod onHostPort(int port) {
    validatePort(port, "hostPort");
    this.hostPort = port;
    return this;
  }

  /**
   * Set the port that the cluster-side {@code Service} exposes for in-cluster clients. Usually the
   * same as {@link #onHostPort(int)} but they can differ.
   *
   * @param port service port (1-65535)
   */
  public LocalServicePod exposingServicePort(int port) {
    validatePort(port, "exposingServicePort");
    this.exposingServicePort = port;
    return this;
  }

  // =============================================================
  // Pod<SELF> configuration methods
  // =============================================================

  @Override
  public LocalServicePod withName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public LocalServicePod inNamespace(Namespace namespace) {
    this.namespace = namespace;
    return this;
  }

  @Override
  public LocalServicePod inNamespace(String namespaceName) {
    this.explicitNamespaceName = namespaceName;
    return this;
  }

  @Override
  public LocalServicePod inCluster(K8sCluster cluster) {
    this.cluster = cluster;
    return this;
  }

  @Override
  public LocalServicePod withLabels(Map<String, String> labels) {
    this.labels.putAll(labels);
    return this;
  }

  @Override
  public LocalServicePod withAnnotations(Map<String, String> annotations) {
    this.annotations.putAll(annotations);
    return this;
  }

  @Override
  public LocalServicePod withResources(String cpuRequest, String memoryRequest) {
    throw unsupported("withResources");
  }

  @Override
  public LocalServicePod withInitContainer(Consumer<InitContainerBuilder> configurer) {
    throw unsupported("withInitContainer");
  }

  @Override
  public LocalServicePod withSidecar(Consumer<SidecarBuilder> configurer) {
    throw unsupported("withSidecar");
  }

  @Override
  public LocalServicePod withPodCustomizer(UnaryOperator<PodSpecBuilder> customizer) {
    throw unsupported("withPodCustomizer");
  }

  @Override
  public LocalServicePod waitingFor(WaitStrategy strategy) {
    throw unsupported("waitingFor");
  }

  // =============================================================
  // Lifecycle
  // =============================================================

  @Override
  public void start() {
    if (hostPort == null) {
      throw new IllegalStateException(
          "LocalServicePod '" + name + "' is missing onHostPort(...) — required before start()");
    }
    if (exposingServicePort == null) {
      throw new IllegalStateException(
          "LocalServicePod '"
              + name
              + "' is missing exposingServicePort(...) — required before start()");
    }

    resolveRuntimeDefaults();
    String hostIp = resolveHostIp();

    KubernetesClient client = cluster.getClient();
    String ns = namespace.getName();
    Map<String, String> allLabels = buildLabels();

    Service service =
        new ServiceBuilder()
            .withNewMetadata()
            .withName(name)
            .withNamespace(ns)
            .withLabels(allLabels)
            .withAnnotations(annotations)
            .endMetadata()
            .withNewSpec()
            .withType("ClusterIP")
            // Intentionally no selector — Endpoints are managed manually so the service
            // forwards to the developer's laptop instead of in-cluster pods.
            .addNewPort()
            .withName("primary")
            .withPort(exposingServicePort)
            .withTargetPort(new IntOrString(hostPort))
            .withProtocol("TCP")
            .endPort()
            .endSpec()
            .build();

    Endpoints endpoints =
        new EndpointsBuilder()
            .withNewMetadata()
            .withName(name)
            .withNamespace(ns)
            .withLabels(allLabels)
            .endMetadata()
            .addNewSubset()
            .addNewAddress()
            .withIp(hostIp)
            .endAddress()
            .addNewPort()
            .withName("primary")
            .withPort(hostPort)
            .withProtocol("TCP")
            .endPort()
            .endSubset()
            .build();

    try {
      createdService = client.services().inNamespace(ns).resource(service).create();
      createdEndpoints = client.endpoints().inNamespace(ns).resource(endpoints).create();
      started = true;
      log.info(
          "Started LocalServicePod '{}': in-cluster {}.{}.svc.cluster.local:{} -> host {}:{}",
          name,
          name,
          ns,
          exposingServicePort,
          hostIp,
          hostPort);
    } catch (RuntimeException e) {
      // Best-effort cleanup on failure so we don't leave half a pair behind.
      safeDelete(() -> client.endpoints().inNamespace(ns).withName(name).delete());
      safeDelete(() -> client.services().inNamespace(ns).withName(name).delete());
      throw e;
    }
  }

  @Override
  public void stop() {
    if (cluster == null || namespace == null) {
      started = false;
      createdService = null;
      createdEndpoints = null;
      return;
    }
    KubernetesClient client = cluster.getClient();
    String ns = namespace.getName();
    try {
      try {
        client.endpoints().inNamespace(ns).withName(name).delete();
      } catch (KubernetesClientException kce) {
        if (kce.getCode() != 404) {
          throw kce;
        }
      }
      try {
        client.services().inNamespace(ns).withName(name).delete();
      } catch (KubernetesClientException kce) {
        if (kce.getCode() != 404) {
          throw kce;
        }
      }
    } finally {
      started = false;
      createdService = null;
      createdEndpoints = null;
    }
  }

  @Override
  public boolean isRunning() {
    return started;
  }

  @Override
  public boolean isReady() {
    return started;
  }

  // =============================================================
  // Observability — not applicable
  // =============================================================

  @Override
  public String getLogs() {
    throw logsUnsupported();
  }

  @Override
  public String getLogs(Duration since) {
    throw logsUnsupported();
  }

  @Override
  public String getLogs(String containerName) {
    throw logsUnsupported();
  }

  @Override
  public ExecResult exec(String... command) {
    throw execUnsupported();
  }

  @Override
  public ExecResult exec(String containerName, String... command) {
    throw execUnsupported();
  }

  // =============================================================
  // Accessors
  // =============================================================

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Namespace getNamespace() {
    return namespace;
  }

  @Override
  public K8sCluster getCluster() {
    return cluster;
  }

  @Override
  public String getInternalHost() {
    return name + "." + namespace.getName() + ".svc.cluster.local";
  }

  @Override
  public int getInternalPort() {
    if (exposingServicePort == null) {
      throw new IllegalStateException("exposingServicePort not configured");
    }
    return exposingServicePort;
  }

  @Override
  public String getExternalHost() {
    return "localhost";
  }

  @Override
  public int getExternalPort() {
    if (hostPort == null) {
      throw new IllegalStateException("hostPort not configured");
    }
    return hostPort;
  }

  @Override
  public void publishProperties(PropertyContext ctx) {
    String prefix = name;
    ctx.publish(prefix + ".internal.host", this::getInternalHost);
    ctx.publish(prefix + ".internal.port", () -> String.valueOf(getInternalPort()));
    ctx.publish(prefix + ".internal.url", () -> getInternalHost() + ":" + getInternalPort());
    ctx.publish(prefix + ".external.host", this::getExternalHost);
    ctx.publish(prefix + ".external.port", () -> String.valueOf(getExternalPort()));
  }

  // =============================================================
  // Internal helpers
  // =============================================================

  Service getCreatedService() {
    return createdService;
  }

  Endpoints getCreatedEndpoints() {
    return createdEndpoints;
  }

  /**
   * Resolve a routable IP address for the developer's host machine.
   *
   * <p>Order: {@value #HOST_DNS} (Minikube auto-DNS) → {@link InetAddress#getLocalHost()}.
   */
  static String resolveHostIp() {
    try {
      return InetAddress.getByName(HOST_DNS).getHostAddress();
    } catch (UnknownHostException ignored) {
      // host.minikube.internal isn't resolvable on this driver; fall back.
    }
    try {
      return InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      throw new IllegalStateException(
          "Could not resolve a host IP for LocalServicePod: neither '"
              + HOST_DNS
              + "' nor InetAddress.getLocalHost() succeeded",
          e);
    }
  }

  private Map<String, String> buildLabels() {
    Map<String, String> all = new LinkedHashMap<>();
    all.put("app", name);
    all.put("managed-by", "testpods");
    all.put("testpods.io/kind", "local-service");
    all.putAll(labels);
    return all;
  }

  /**
   * Mirrors the resolution logic in {@code BaseManagedPod.resolveRuntimeDefaults()} so that
   * {@code Registry.provisionTestPods()} can inject a cluster and rely on the same defaults.
   */
  private void resolveRuntimeDefaults() {
    if (cluster == null) {
      cluster = TestPodDefaults.resolveCluster();
    }
    if (namespace != null) {
      return;
    }
    if (explicitNamespaceName != null && !explicitNamespaceName.isBlank()) {
      Namespace existing = cluster.getNamespace(explicitNamespaceName);
      if (existing != null) {
        namespace = existing;
        return;
      }
      try {
        namespace = cluster.createNamespace(explicitNamespaceName);
      } catch (RuntimeException e) {
        namespace = cluster.attachNamespace(explicitNamespaceName);
      }
      return;
    }
    Namespace shared = TestPodDefaults.getSharedNamespace();
    if (shared != null) {
      namespace = shared;
      return;
    }
    namespace = cluster.getDefaultNamespace();
    if (namespace == null) {
      namespace = cluster.createNamespace();
    }
  }

  private static void validatePort(int port, String label) {
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException(label + " must be between 1 and 65535: " + port);
    }
  }

  private static UnsupportedOperationException unsupported(String op) {
    return new UnsupportedOperationException(
        "LocalServicePod does not support " + op + "(...) — it does not schedule a workload");
  }

  private static UnsupportedOperationException logsUnsupported() {
    return new UnsupportedOperationException(
        "LocalServicePod represents a service running outside the cluster; logs/exec are not available");
  }

  private static UnsupportedOperationException execUnsupported() {
    return logsUnsupported();
  }

  private static void safeDelete(Runnable deletion) {
    try {
      deletion.run();
    } catch (RuntimeException ignored) {
      // best-effort cleanup
    }
  }
}
