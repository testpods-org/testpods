package org.testpods.core.cluster;

import org.testpods.core.pods.TestPod;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strategy for accessing pods from outside the Kubernetes cluster.
 *
 * <p>Different cluster types require different access methods:
 *
 * <ul>
 *   <li>Minikube: {@code minikube service} command or NodePort
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
  HostAndPort getExternalEndpoint(TestPod<?> pod, int internalPort);

  /** Clean up any resources (e.g., port forwards) when done. Called when the pod is stopped. */
  default void cleanup(TestPod<?> pod) {
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
   * Use NodePort services to access pods. Requires the cluster nodes to be reachable from test
   * code.
   */
  static ExternalAccessStrategy nodePort() {
    return new NodePortAccessStrategy();
  }

  /** Use LoadBalancer services to access pods. Requires cloud provider or MetalLB. */
  static ExternalAccessStrategy loadBalancer() {
    return new LoadBalancerAccessStrategy();
  }

  /** Use Minikube's service URL mechanism. Only works with Minikube clusters. */
  static ExternalAccessStrategy minikubeService() {
    return new MinikubeServiceAccessStrategy();
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
  public HostAndPort getExternalEndpoint(TestPod<?> pod, int internalPort) {
    String key = pod.getNamespace().getName() + "/" + pod.getName() + ":" + internalPort;

    return cachedEndpoints.computeIfAbsent(
        key,
        k -> {
          try {
            int localPort = findAvailablePort();

            KubernetesClient client = pod.getNamespace().getCluster().getClient();

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
  public void cleanup(TestPod<?> pod) {
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
// NodePortAccessStrategy
// =============================================================

/**
 * Uses NodePort services to access pods. Requires the Service to be of type NodePort and cluster
 * nodes to be reachable.
 */
class NodePortAccessStrategy implements ExternalAccessStrategy {

  private String nodeIp;

  /** Create with auto-detected node IP. */
  NodePortAccessStrategy() {
    this.nodeIp = null; // Will be detected from cluster
  }

  /** Create with explicit node IP. */
  NodePortAccessStrategy(String nodeIp) {
    this.nodeIp = nodeIp;
  }

  @Override
  public HostAndPort getExternalEndpoint(TestPod<?> pod, int internalPort) {
    KubernetesClient client = pod.getNamespace().getCluster().getClient();

    // Get the service
    Service service =
        client.services().inNamespace(pod.getNamespace().getName()).withName(pod.getName()).get();

    if (service == null) {
      throw new IllegalStateException("Service not found: " + pod.getName());
    }

    // Find the NodePort for the requested internal port
    Integer nodePort = null;
    for (ServicePort port : service.getSpec().getPorts()) {
      if (port.getPort().equals(internalPort)) {
        nodePort = port.getNodePort();
        break;
      }
    }

    if (nodePort == null) {
      throw new IllegalStateException(
          "NodePort not found for port "
              + internalPort
              + " on service "
              + pod.getName()
              + ". Ensure the service is of type NodePort.");
    }

    // Get node IP if not set
    if (nodeIp == null) {
      nodeIp = detectNodeIp(client);
    }

    return new HostAndPort(nodeIp, nodePort);
  }

  private String detectNodeIp(KubernetesClient client) {
    // Try to get the first node's internal IP
    var nodes = client.nodes().list().getItems();
    if (!nodes.isEmpty()) {
      var addresses = nodes.get(0).getStatus().getAddresses();
      for (var addr : addresses) {
        if ("InternalIP".equals(addr.getType())) {
          return addr.getAddress();
        }
      }
    }

    // Fallback to localhost (works for minikube with tunnel)
    return "127.0.0.1";
  }
}

// =============================================================
// LoadBalancerAccessStrategy
// =============================================================

/** Uses LoadBalancer services to access pods. Waits for the external IP to be assigned. */
class LoadBalancerAccessStrategy implements ExternalAccessStrategy {

  private static final int MAX_WAIT_SECONDS = 120;

  @Override
  public HostAndPort getExternalEndpoint(TestPod<?> pod, int internalPort) {
    KubernetesClient client = pod.getNamespace().getCluster().getClient();

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
 * Uses Minikube's service URL mechanism. Runs 'minikube service --url' to get the external
 * endpoint.
 */
class MinikubeServiceAccessStrategy implements ExternalAccessStrategy {

  private final Map<String, HostAndPort> cachedEndpoints = new ConcurrentHashMap<>();

  @Override
  public HostAndPort getExternalEndpoint(TestPod<?> pod, int internalPort) {
    String key = pod.getNamespace().getName() + "/" + pod.getName() + ":" + internalPort;

    return cachedEndpoints.computeIfAbsent(
        key,
        k -> {
          try {
            // Run: minikube service <n> -n <namespace> --url
            ProcessBuilder pb =
                new ProcessBuilder(
                    "minikube",
                    "service",
                    pod.getName(),
                    "-p",
                    "minikit",
                    "-n",
                    pod.getNamespace().getName(),
                    "--url");
            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream()))) {

              String line;
              while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Look for URL format: http://192.168.49.2:30001
                if (line.startsWith("http://") || line.startsWith("https://")) {
                  // Parse the URL
                  String withoutProtocol = line.replaceFirst("https?://", "");
                  return HostAndPort.parse(withoutProtocol);
                }
              }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
              throw new RuntimeException(
                  "minikube service command failed with exit code " + exitCode);
            }

            throw new RuntimeException("Could not parse minikube service URL output");

          } catch (IOException | InterruptedException e) {
            // Fallback to NodePort strategy
            return fallbackToNodePort(pod, internalPort);
          }
        });
  }

  private HostAndPort fallbackToNodePort(TestPod<?> pod, int internalPort) {
    try {
      // Get minikube IP
      ProcessBuilder pb = new ProcessBuilder("minikube", "ip");
      pb.redirectErrorStream(true);
      Process process = pb.start();

      String minikubeIp;
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        minikubeIp = reader.readLine();
      }
      process.waitFor();

      if (minikubeIp == null || minikubeIp.isEmpty()) {
        minikubeIp = "192.168.49.2"; // Common default
      }

      // Get NodePort from service
      KubernetesClient client = pod.getNamespace().getCluster().getClient();
      Service service =
          client.services().inNamespace(pod.getNamespace().getName()).withName(pod.getName()).get();

      if (service != null) {
        for (ServicePort port : service.getSpec().getPorts()) {
          if (port.getPort().equals(internalPort) && port.getNodePort() != null) {
            return new HostAndPort(minikubeIp.trim(), port.getNodePort());
          }
        }
      }

      throw new RuntimeException("Could not determine external endpoint for " + pod.getName());

    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Failed to get minikube service URL", e);
    }
  }
}
