package org.testpods.core.service;

import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Configuration passed from a pod to its service manager.
 *
 * <p>This record captures all the information needed to create a Service resource in the cluster.
 *
 * @param name the name of the service resource
 * @param namespace the namespace to create the service in
 * @param port the primary port to expose
 * @param labels labels to apply to the service
 * @param selector pod selector labels (typically "app" -> podName)
 * @param customizers functions to customize the ServiceBuilder
 * @param client the Kubernetes client to use for API calls
 */
public record ServiceConfig(
    String name,
    String namespace,
    int port,
    List<Integer> ports,
    Map<String, String> labels,
    Map<String, String> selector,
    List<UnaryOperator<ServiceBuilder>> customizers,
    Integer nodePort,
    Map<Integer, Integer> nodePorts,
    KubernetesClient client) {

  public ServiceConfig {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(namespace, "namespace must not be null");
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("port must be between 1 and 65535");
    }
    if (nodePort != null && (nodePort <= 0 || nodePort > 65535)) {
      throw new IllegalArgumentException("nodePort must be between 1 and 65535");
    }
    ports = normalizePorts(port, ports);
    nodePorts = nodePorts != null ? Map.copyOf(nodePorts) : Map.of();
    for (Map.Entry<Integer, Integer> e : nodePorts.entrySet()) {
      validatePort(e.getKey(), "nodePorts key");
      validatePort(e.getValue(), "nodePorts value");
    }
    Objects.requireNonNull(client, "client must not be null");
    labels = labels != null ? Map.copyOf(labels) : Map.of();
    selector = selector != null ? Map.copyOf(selector) : Map.of();
    customizers = customizers != null ? List.copyOf(customizers) : List.of();
  }

  /** Create a builder for ServiceConfig. */
  public static Builder builder() {
    return new Builder();
  }

  private static List<Integer> normalizePorts(int primaryPort, List<Integer> configuredPorts) {
    List<Integer> result = new ArrayList<>();
    result.add(primaryPort);
    if (configuredPorts != null) {
      for (Integer configuredPort : configuredPorts) {
        if (configuredPort == null) {
          continue;
        }
        validatePort(configuredPort, "ports");
        if (!result.contains(configuredPort)) {
          result.add(configuredPort);
        }
      }
    }
    return List.copyOf(result);
  }

  private static void validatePort(int port, String name) {
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException(name + " must be between 1 and 65535");
    }
  }

  /** Fluent builder for ServiceConfig. */
  public static class Builder {
    private String name;
    private String namespace;
    private int port;
    private List<Integer> ports = new ArrayList<>();
    private Map<String, String> labels = new LinkedHashMap<>();
    private Map<String, String> selector = new LinkedHashMap<>();
    private List<UnaryOperator<ServiceBuilder>> customizers = new ArrayList<>();
    private Integer nodePort;
    private Map<Integer, Integer> nodePorts = new LinkedHashMap<>();
    private KubernetesClient client;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder namespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public Builder port(int port) {
      this.port = port;
      return this;
    }

    public Builder ports(Iterable<Integer> ports) {
      this.ports = new ArrayList<>();
      if (ports != null) {
        for (Integer port : ports) {
          this.ports.add(port);
        }
      }
      return this;
    }

    public Builder labels(Map<String, String> labels) {
      this.labels = new LinkedHashMap<>(labels);
      return this;
    }

    public Builder selector(Map<String, String> selector) {
      this.selector = new LinkedHashMap<>(selector);
      return this;
    }

    public Builder customizers(List<UnaryOperator<ServiceBuilder>> customizers) {
      this.customizers = new ArrayList<>(customizers);
      return this;
    }

    public Builder addCustomizer(UnaryOperator<ServiceBuilder> customizer) {
      this.customizers.add(customizer);
      return this;
    }

    public Builder nodePort(Integer nodePort) {
      this.nodePort = nodePort;
      return this;
    }

    public Builder nodePorts(Map<Integer, Integer> nodePorts) {
      this.nodePorts = new LinkedHashMap<>(nodePorts);
      return this;
    }

    public Builder client(KubernetesClient client) {
      this.client = client;
      return this;
    }

    public ServiceConfig build() {
      return new ServiceConfig(
          name, namespace, port, ports, labels, selector, customizers, nodePort, nodePorts, client);
    }
  }
}
