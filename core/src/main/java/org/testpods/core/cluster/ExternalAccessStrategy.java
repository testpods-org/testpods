package org.testpods.core.cluster;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testpods.core.pods.Pod;

/**
 * Strategy for accessing pods from outside the Kubernetes cluster.
 *
 * <p>Different cluster types require different access methods:
 *
 * <ul>
 *   <li>Minikube: localhost forwarding or {@code minikube service}
 *   <li>Kind: Port forwarding (no external IP)
 *   <li>Remote/Cloud: LoadBalancer or Ingress
 * </ul>
 */
public interface ExternalAccessStrategy {

  /**
   * Get the external endpoint for reaching a pod from test code.
   *
   * @param pod The pod to access
   * @param internalPort The container port to expose
   * @return Host and port for external access
   */
  HostAndPort getExternalEndpoint(Pod<?> pod, int internalPort);

  /** Clean up any resources (e.g., port forwards) when done. Called when the pod is stopped. */
  default void cleanup(Pod<?> pod) {
    // Default: no cleanup needed
  }

  // =============================================================
  // Factory methods
  // =============================================================

  /**
   * Use kubectl port-forward to access pods. Works with any cluster but requires an active
   * port-forward.
   */
  static ExternalAccessStrategy portForward() {
    return new PortForwardAccessStrategy();
  }

  /**
   * Use an external {@code kubectl port-forward} process to expose pod endpoints on localhost.
   *
   * <p>Unlike {@link #portForward()}, the byte relay does not run inside the test JVM. This is
   * useful for debug sessions where suspending JVM threads must not break browser or CLI access to
   * the forwarded ports.
   */
  static ExternalAccessStrategy localhostPortForward() {
    return new LocalhostPortForwardAccessStrategy(null);
  }

  /**
   * Use an external {@code kubectl port-forward} process against a specific kube context.
   *
   * @param kubeContext kubeconfig context passed as {@code kubectl --context <context>}
   */
  static ExternalAccessStrategy localhostPortForward(String kubeContext) {
    return new LocalhostPortForwardAccessStrategy(kubeContext);
  }

  /** Use LoadBalancer services to access pods. Requires cloud provider or MetalLB. */
  static ExternalAccessStrategy loadBalancer() {
    return new LoadBalancerAccessStrategy();
  }

  /**
   * Use Minikube's service URL mechanism. Only works with Minikube clusters.
   *
   * @deprecated This factory has no profile context and cannot construct a usable strategy.
   *     Construct a {@link MinikubeCluster} via {@code MinikubeCluster.create()} — it wires the
   *     profile-aware strategy internally.
   */
  @Deprecated
  static ExternalAccessStrategy minikubeService() {
    throw new UnsupportedOperationException(
        "Use MinikubeCluster.create() — profile context is required for the "
            + "minikube service strategy");
  }

  /**
   * Package-private factory used by {@link MinikubeCluster} to wire the access strategy with the
   * profile context required by {@link MinikitCli}.
   */
  static ExternalAccessStrategy minikubeService(MinikitCli cli, String profileName) {
    return new MinikubeServiceAccessStrategy(cli, profileName);
  }
}

// =============================================================
// PortForwardAccessStrategy
// =============================================================

/**
 * Uses Fabric8's LocalPortForward to create port forwards to pods. This works with any cluster
 * type.
 */
class PortForwardAccessStrategy implements ExternalAccessStrategy {

  // Track active port forwards for cleanup
  private final Map<String, LocalPortForward> activeForwards = new ConcurrentHashMap<>();
  private final Map<String, HostAndPort> cachedEndpoints = new ConcurrentHashMap<>();

  @Override
  public HostAndPort getExternalEndpoint(Pod<?> pod, int internalPort) {
    String key = pod.getNamespace().getName() + "/" + pod.getName() + ":" + internalPort;

    return cachedEndpoints.computeIfAbsent(
        key,
        k -> {
          try {
            int localPort = pod.getFixedExposedPort(internalPort).orElseGet(this::findAvailablePort);

            KubernetesClient client = pod.getCluster().getClient();

            // Port forward to the service (more stable than pod)
            LocalPortForward portForward =
                client
                    .services()
                    .inNamespace(pod.getNamespace().getName())
                    .withName(pod.getName())
                    .portForward(internalPort, localPort);

            activeForwards.put(key, portForward);

            return HostAndPort.localhost(localPort);
          } catch (Exception e) {
            throw new RuntimeException("Failed to create port forward for " + pod.getName(), e);
          }
        });
  }

  @Override
  public void cleanup(Pod<?> pod) {
    String keyPrefix = pod.getNamespace().getName() + "/" + pod.getName() + ":";

    activeForwards
        .entrySet()
        .removeIf(
            entry -> {
              if (entry.getKey().startsWith(keyPrefix)) {
                try {
                  entry.getValue().close();
                } catch (IOException e) {
                  // Log but don't fail
                }
                cachedEndpoints.remove(entry.getKey());
                return true;
              }
              return false;
            });
  }

  private int findAvailablePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new RuntimeException("Failed to find available port", e);
    }
  }
}

// =============================================================
// LocalhostPortForwardAccessStrategy
// =============================================================

/**
 * Uses an external {@code kubectl port-forward} process to create localhost forwards.
 *
 * <p>The forwarding process is outside the test JVM. If a debugger suspends all JVM threads,
 * already-started {@code kubectl} forwards continue to serve browser and CLI traffic.
 */
@Slf4j
class LocalhostPortForwardAccessStrategy implements ExternalAccessStrategy {

  private static final Duration START_TIMEOUT = Duration.ofSeconds(20);

  private final String kubeContext;
  private final Map<String, ForwardProcess> activeForwards = new ConcurrentHashMap<>();

  LocalhostPortForwardAccessStrategy(String kubeContext) {
    this.kubeContext = kubeContext == null || kubeContext.isBlank() ? null : kubeContext;
  }

  @Override
  public HostAndPort getExternalEndpoint(Pod<?> pod, int internalPort) {
    String key = pod.getNamespace().getName() + "/" + pod.getName() + ":" + internalPort;
    return activeForwards
        .computeIfAbsent(key, ignored -> startForward(pod, internalPort))
        .endpoint();
  }

  @Override
  public void cleanup(Pod<?> pod) {
    String keyPrefix = pod.getNamespace().getName() + "/" + pod.getName() + ":";
    activeForwards
        .entrySet()
        .removeIf(
            entry -> {
              if (!entry.getKey().startsWith(keyPrefix)) {
                return false;
              }
              entry.getValue().stop();
              return true;
            });
  }

  private ForwardProcess startForward(Pod<?> pod, int internalPort) {
    int localPort = pod.getFixedExposedPort(internalPort).orElseGet(this::findAvailablePort);
    HostAndPort endpoint = HostAndPort.localhost(localPort);
    List<String> command = kubectlPortForwardCommand(pod, internalPort, localPort);

    try {
      Process process =
          new ProcessBuilder(command)
              .redirectErrorStream(true)
              .start();
      consumeOutput(process, command);
      waitUntilListening(process, endpoint, command);
      log.info(
          "Started localhost port-forward for {}/{}: {} -> {} using: {}",
          pod.getNamespace().getName(),
          pod.getName(),
          endpoint,
          internalPort,
          String.join(" ", command));
      return new ForwardProcess(endpoint, process, command);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to start localhost port-forward with command: " + String.join(" ", command), e);
    }
  }

  private List<String> kubectlPortForwardCommand(Pod<?> pod, int internalPort, int localPort) {
    List<String> command = new ArrayList<>();
    command.add("kubectl");
    if (kubeContext != null) {
      command.add("--context");
      command.add(kubeContext);
    }
    command.add("-n");
    command.add(pod.getNamespace().getName());
    command.add("port-forward");
    command.add("svc/" + pod.getName());
    command.add(localPort + ":" + internalPort);
    return command;
  }

  private void waitUntilListening(Process process, HostAndPort endpoint, List<String> command) {
    long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      if (!process.isAlive()) {
        throw new IllegalStateException(
            "kubectl port-forward exited before "
                + endpoint
                + " was reachable. Command: "
                + String.join(" ", command));
      }
      if (canConnect(endpoint)) {
        return;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        process.destroy();
        throw new IllegalStateException(
            "Interrupted while waiting for localhost port-forward: " + String.join(" ", command),
            e);
      }
    }

    process.destroy();
    throw new IllegalStateException(
        "Timed out waiting for localhost port-forward "
            + endpoint
            + ". Command: "
            + String.join(" ", command));
  }

  private boolean canConnect(HostAndPort endpoint) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), 500);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private void consumeOutput(Process process, List<String> command) {
    Thread reader =
        new Thread(
            () -> {
              try (BufferedReader lines =
                  new BufferedReader(
                      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = lines.readLine()) != null) {
                  log.debug("kubectl port-forward [{}]: {}", String.join(" ", command), line);
                }
              } catch (IOException e) {
                log.debug("Stopped reading kubectl port-forward output: {}", e.getMessage());
              }
            },
            "testpods-kubectl-port-forward-output");
    reader.setDaemon(true);
    reader.start();
  }

  private int findAvailablePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to find available local port", e);
    }
  }

  private record ForwardProcess(HostAndPort endpoint, Process process, List<String> command) {
    void stop() {
      if (!process.isAlive()) {
        return;
      }
      process.destroy();
      try {
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroyForcibly();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        process.destroyForcibly();
      }
    }
  }
}

// =============================================================
// LoadBalancerAccessStrategy
// =============================================================

/** Uses LoadBalancer services to access pods. Waits for the external IP to be assigned. */
class LoadBalancerAccessStrategy implements ExternalAccessStrategy {

  private static final int MAX_WAIT_SECONDS = 120;

  @Override
  public HostAndPort getExternalEndpoint(Pod<?> pod, int internalPort) {
    KubernetesClient client = pod.getCluster().getClient();

    long startTime = System.currentTimeMillis();
    long timeoutMillis = MAX_WAIT_SECONDS * 1000L;

    while (System.currentTimeMillis() - startTime < timeoutMillis) {
      Service service =
          client.services().inNamespace(pod.getNamespace().getName()).withName(pod.getName()).get();

      if (service == null) {
        throw new IllegalStateException("Service not found: " + pod.getName());
      }

      // Check for LoadBalancer ingress
      var ingress = service.getStatus().getLoadBalancer().getIngress();
      if (ingress != null && !ingress.isEmpty()) {
        var firstIngress = ingress.get(0);
        String host = firstIngress.getIp();
        if (host == null || host.isEmpty()) {
          host = firstIngress.getHostname();
        }

        if (host != null && !host.isEmpty()) {
          // Find the port
          for (ServicePort port : service.getSpec().getPorts()) {
            if (port.getPort().equals(internalPort)) {
              return new HostAndPort(host, port.getPort());
            }
          }
        }
      }

      // Wait and retry
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted while waiting for LoadBalancer IP", e);
      }
    }

    throw new IllegalStateException(
        "LoadBalancer IP not assigned within "
            + MAX_WAIT_SECONDS
            + " seconds for "
            + pod.getName());
  }
}

// =============================================================
// MinikubeServiceAccessStrategy
// =============================================================

/**
 * Uses Minikube's service URL mechanism via {@link MinikitCli} to get the external endpoint.
 * Constructed by {@link MinikubeCluster} with the profile context required by the CLI wrapper.
 */
class MinikubeServiceAccessStrategy implements ExternalAccessStrategy {

  private final MinikitCli cli;
  private final String profileName;
  private final Map<String, HostAndPort> cachedEndpoints = new ConcurrentHashMap<>();

  MinikubeServiceAccessStrategy(MinikitCli cli, String profileName) {
    this.cli = cli;
    this.profileName = profileName;
  }

  @Override
  public HostAndPort getExternalEndpoint(Pod<?> pod, int internalPort) {
    String key = pod.getNamespace().getName() + "/" + pod.getName() + ":" + internalPort;

    return cachedEndpoints.computeIfAbsent(
        key,
        k -> {
          try {
            String url = cli.serviceUrl(profileName, pod.getNamespace().getName(), pod.getName());
            String withoutProtocol = url.trim().replaceFirst("https?://", "");
            return HostAndPort.parse(withoutProtocol);
          } catch (ClusterException e) {
            throw new IllegalStateException(
                "Could not determine minikube service URL for " + pod.getName(), e);
          }
        });
  }
}
