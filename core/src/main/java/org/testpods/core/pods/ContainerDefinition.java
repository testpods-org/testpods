package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.Container;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testpods.core.pods.builders.ContainerSpec;
import org.testpods.core.wait.WaitStrategy;

/**
 * Composable container-shape configuration shared by generic pods.
 *
 * <p>Owns the user-supplied container details (image, ports, environment, command, args, readiness
 * probe). Used by {@link GenericPod} and {@link GenericStatefulPod}. Domain pods (PostgreSQL,
 * Kafka, MongoDB) own their container directly and don't compose this class.
 */
public class ContainerDefinition {

  private String image;
  private final List<Integer> ports = new ArrayList<>();
  private final Map<String, String> env = new LinkedHashMap<>();
  private List<String> command;
  private List<String> args;
  private Integer primaryPort;
  private String readinessPath;
  private Integer readinessPort;

  public ContainerDefinition withImage(String image) {
    this.image = image;
    return this;
  }

  public ContainerDefinition withPort(int port) {
    this.ports.add(port);
    if (this.primaryPort == null) {
      this.primaryPort = port;
    }
    return this;
  }

  public ContainerDefinition withPrimaryPort(int port) {
    this.ports.add(port);
    this.primaryPort = port;
    return this;
  }

  public ContainerDefinition withEnv(String name, String value) {
    this.env.put(name, value);
    return this;
  }

  public ContainerDefinition withEnv(Map<String, String> values) {
    this.env.putAll(values);
    return this;
  }

  public ContainerDefinition withCommand(String... command) {
    this.command = Arrays.asList(command);
    return this;
  }

  public ContainerDefinition withArgs(String... args) {
    this.args = Arrays.asList(args);
    return this;
  }

  public ContainerDefinition withHttpReadinessProbe(String path, int port) {
    this.readinessPath = path;
    this.readinessPort = port;
    return this;
  }

  public int getPrimaryPort() {
    if (primaryPort != null) {
      return primaryPort;
    }
    if (!ports.isEmpty()) {
      return ports.get(0);
    }
    return 80;
  }

  public String getImage() {
    return image;
  }

  public Container buildContainer(String name) {
    ContainerSpec spec = new ContainerSpec().withName(name).withImage(image);

    if (command != null && !command.isEmpty()) {
      spec.withCommand(command.toArray(new String[0]));
    }
    if (args != null && !args.isEmpty()) {
      spec.withArgs(args.toArray(new String[0]));
    }
    for (Map.Entry<String, String> e : env.entrySet()) {
      spec.withEnv(e.getKey(), e.getValue());
    }
    for (int p : ports) {
      spec.withPort(p);
    }

    if (readinessPath != null && readinessPort != null) {
      spec.withReadinessProbe(
          probe -> probe.httpGet(readinessPort, readinessPath).initialDelay(5).period(10).timeout(5));
    } else if (!ports.isEmpty()) {
      spec.withReadinessProbe(
          probe -> probe.tcpSocket(getPrimaryPort()).initialDelay(5).period(10).timeout(5));
    }

    return spec.build();
  }

  public WaitStrategy deriveDefaultWaitStrategy() {
    if (readinessPath != null && readinessPort != null) {
      return WaitStrategy.forHttp(readinessPath, readinessPort).withTimeout(Duration.ofMinutes(1));
    }
    if (primaryPort != null || !ports.isEmpty()) {
      return WaitStrategy.forPort(getPrimaryPort()).withTimeout(Duration.ofMinutes(1));
    }
    return WaitStrategy.forReadinessProbe().withTimeout(Duration.ofMinutes(1));
  }
}
