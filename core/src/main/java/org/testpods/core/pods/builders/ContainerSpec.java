package org.testpods.core.pods.builders;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Fluent builder for Kubernetes containers.
 *
 * <p>Simplifies container configuration by providing a flat, readable API that hides the complexity
 * of the Fabric8 nested builder pattern. ContainerSpec is the main API for fluent container
 * definition in TestPods.
 *
 * <p><b>Usage examples:</b>
 *
 * <pre>{@code
 * // Simple container with port and environment
 * Container container = new ContainerSpec()
 *     .withName("my-app")
 *     .withImage("my-app:latest")
 *     .withPort(8080)
 *     .withEnv("JAVA_OPTS", "-Xmx512m")
 *     .build();
 *
 * // Database container with probes and resources
 * Container postgres = new ContainerSpec()
 *     .withName("postgres")
 *     .withImage("postgres:15")
 *     .withPort(5432)
 *     .withEnv("POSTGRES_PASSWORD", "secret")
 *     .withEnv("POSTGRES_DB", "testdb")
 *     .withReadinessProbe(probe -> probe
 *         .tcpSocket(5432)
 *         .initialDelay(5)
 *         .period(2))
 *     .withResources("100m", "256Mi")
 *     .withResourceLimits("500m", "512Mi")
 *     .build();
 *
 * // Container with secret environment and volume mount
 * Container app = new ContainerSpec()
 *     .withName("secure-app")
 *     .withImage("app:v1")
 *     .withSecretEnv("DB_PASSWORD", "db-secret", "password")
 *     .withEnvFrom("app-config", "database-url")
 *     .withVolumeMount("config", "/etc/config", true)
 *     .build();
 *
 * // Using the escape hatch for advanced Fabric8 customization
 * Container advanced = new ContainerSpec()
 *     .withName("advanced")
 *     .withImage("advanced:latest")
 *     .customize(builder -> builder
 *         .withNewSecurityContext()
 *             .withRunAsNonRoot(true)
 *             .withReadOnlyRootFilesystem(true)
 *         .endSecurityContext())
 *     .build();
 * }</pre>
 *
 * @see InitContainerBuilder
 * @see SidecarBuilder
 * @see ProbeSpec
 * @see io.fabric8.kubernetes.api.model.Container
 */
public class ContainerSpec {

  // Required fields
  private String name;
  private String image;

  // Ports
  private final List<PortEntry> ports = new ArrayList<>();

  // Environment variables - LinkedHashMap preserves insertion order
  private final Map<String, EnvVar> envVars = new LinkedHashMap<>();

  // Commands and arguments
  private List<String> command;
  private List<String> args;

  // Volume mounts
  private final List<VolumeMount> volumeMounts = new ArrayList<>();

  // Probes
  private Consumer<ProbeSpec> readinessProbeConfigurer;
  private Consumer<ProbeSpec> livenessProbeConfigurer;
  private Consumer<ProbeSpec> startupProbeConfigurer;

  // Resources
  private String cpuRequest;
  private String memoryRequest;
  private String cpuLimit;
  private String memoryLimit;

  // Customizers - applied in order
  private final List<UnaryOperator<ContainerBuilder>> customizers = new ArrayList<>();

  // =============================================================
  // Basic configuration
  // =============================================================

  /**
   * Set the container name.
   *
   * <p>This is required and must be a valid DNS label (lowercase alphanumeric and hyphens).
   *
   * @param name the container name
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Set the container image.
   *
   * <p>This is required. Use a full image reference including tag for reproducibility.
   *
   * @param image the container image (e.g., "postgres:15", "myregistry.io/app:v1")
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withImage(String image) {
    this.image = image;
    return this;
  }

  /**
   * Expose a container port.
   *
   * @param port the port number to expose
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withPort(int port) {
    this.ports.add(new PortEntry(port, null));
    return this;
  }

  /**
   * Expose a named container port.
   *
   * <p>Named ports can be referenced by name in Service definitions and probes.
   *
   * @param port the port number to expose
   * @param name the port name (must be a valid DNS label)
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withPort(int port, String name) {
    this.ports.add(new PortEntry(port, name));
    return this;
  }

  // =============================================================
  // Environment variables
  // =============================================================

  /**
   * Add an environment variable with a literal value.
   *
   * @param name the environment variable name
   * @param value the environment variable value
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withEnv(String name, String value) {
    this.envVars.put(name, new EnvVarBuilder().withName(name).withValue(value).build());
    return this;
  }

  /**
   * Add an environment variable sourced from a ConfigMap key.
   *
   * @param configMapName the name of the ConfigMap
   * @param key the key within the ConfigMap
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withEnvFrom(String configMapName, String key) {
    this.envVars.put(
        key,
        new EnvVarBuilder()
            .withName(key)
            .withNewValueFrom()
            .withNewConfigMapKeyRef()
            .withName(configMapName)
            .withKey(key)
            .endConfigMapKeyRef()
            .endValueFrom()
            .build());
    return this;
  }

  /**
   * Add an environment variable sourced from a Secret key.
   *
   * @param envName the environment variable name
   * @param secretName the name of the Secret
   * @param key the key within the Secret
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withSecretEnv(String envName, String secretName, String key) {
    this.envVars.put(
        envName,
        new EnvVarBuilder()
            .withName(envName)
            .withNewValueFrom()
            .withNewSecretKeyRef()
            .withName(secretName)
            .withKey(key)
            .endSecretKeyRef()
            .endValueFrom()
            .build());
    return this;
  }

  // =============================================================
  // Commands and arguments
  // =============================================================

  /**
   * Set the container command.
   *
   * <p>This replaces the image's ENTRYPOINT.
   *
   * @param command the command and any initial arguments
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withCommand(String... command) {
    this.command = Arrays.asList(command);
    return this;
  }

  /**
   * Set the container arguments.
   *
   * <p>This replaces the image's CMD.
   *
   * @param args the arguments to pass to the command
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withArgs(String... args) {
    this.args = Arrays.asList(args);
    return this;
  }

  // =============================================================
  // Volume mounts
  // =============================================================

  /**
   * Add a volume mount (read-write).
   *
   * @param name the volume name (must match a volume defined in the pod spec)
   * @param mountPath the path where the volume should be mounted
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withVolumeMount(String name, String mountPath) {
    return withVolumeMount(name, mountPath, false);
  }

  /**
   * Add a volume mount with explicit read-only setting.
   *
   * @param name the volume name (must match a volume defined in the pod spec)
   * @param mountPath the path where the volume should be mounted
   * @param readOnly whether the mount should be read-only
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withVolumeMount(String name, String mountPath, boolean readOnly) {
    this.volumeMounts.add(
        new VolumeMountBuilder()
            .withName(name)
            .withMountPath(mountPath)
            .withReadOnly(readOnly)
            .build());
    return this;
  }

  // =============================================================
  // Probes
  // =============================================================

  /**
   * Configure a readiness probe.
   *
   * <p>The readiness probe determines when the container is ready to accept traffic.
   *
   * <p>Example:
   *
   * <pre>{@code
   * .withReadinessProbe(probe -> probe
   *     .tcpSocket(5432)
   *     .initialDelay(5)
   *     .period(2))
   * }</pre>
   *
   * @param configurer a consumer that configures the probe
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withReadinessProbe(Consumer<ProbeSpec> configurer) {
    this.readinessProbeConfigurer = configurer;
    return this;
  }

  /**
   * Configure a liveness probe.
   *
   * <p>The liveness probe determines if the container is running. If the probe fails, the container
   * will be restarted.
   *
   * <p>Example:
   *
   * <pre>{@code
   * .withLivenessProbe(probe -> probe
   *     .httpGet(8080, "/health")
   *     .initialDelay(30)
   *     .period(10))
   * }</pre>
   *
   * @param configurer a consumer that configures the probe
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withLivenessProbe(Consumer<ProbeSpec> configurer) {
    this.livenessProbeConfigurer = configurer;
    return this;
  }

  /**
   * Configure a startup probe.
   *
   * <p>The startup probe determines when the container has started. While the startup probe is
   * running, liveness and readiness probes are disabled.
   *
   * <p>Example:
   *
   * <pre>{@code
   * .withStartupProbe(probe -> probe
   *     .exec("pg_isready", "-U", "postgres")
   *     .initialDelay(0)
   *     .period(5)
   *     .failureThreshold(30))
   * }</pre>
   *
   * @param configurer a consumer that configures the probe
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withStartupProbe(Consumer<ProbeSpec> configurer) {
    this.startupProbeConfigurer = configurer;
    return this;
  }

  // =============================================================
  // Resources
  // =============================================================

  /**
   * Set resource requests.
   *
   * <p>Resource requests specify the minimum resources the container needs.
   *
   * @param cpuRequest CPU request (e.g., "100m", "0.5", "1")
   * @param memoryRequest memory request (e.g., "256Mi", "1Gi")
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withResources(String cpuRequest, String memoryRequest) {
    this.cpuRequest = cpuRequest;
    this.memoryRequest = memoryRequest;
    return this;
  }

  /**
   * Set resource limits.
   *
   * <p>Resource limits specify the maximum resources the container can use.
   *
   * @param cpuLimit CPU limit (e.g., "500m", "1", "2")
   * @param memoryLimit memory limit (e.g., "512Mi", "2Gi")
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec withResourceLimits(String cpuLimit, String memoryLimit) {
    this.cpuLimit = cpuLimit;
    this.memoryLimit = memoryLimit;
    return this;
  }

  // =============================================================
  // Escape hatch
  // =============================================================

  /**
   * Apply a customization to the underlying Fabric8 ContainerBuilder.
   *
   * <p>This is an escape hatch for advanced configuration not covered by the fluent API. Multiple
   * customizers are applied in the order they are added.
   *
   * <p>Example:
   *
   * <pre>{@code
   * .customize(builder -> builder
   *     .withNewSecurityContext()
   *         .withRunAsNonRoot(true)
   *         .withReadOnlyRootFilesystem(true)
   *     .endSecurityContext())
   * }</pre>
   *
   * @param customizer a function that modifies the ContainerBuilder
   * @return this ContainerSpec for method chaining
   */
  public ContainerSpec customize(UnaryOperator<ContainerBuilder> customizer) {
    this.customizers.add(customizer);
    return this;
  }

  // =============================================================
  // Build
  // =============================================================

  /**
   * Get the container name.
   *
   * @return the container name, or null if not set
   */
  public String getName() {
    return name;
  }

  /**
   * Build the Fabric8 Container object.
   *
   * @return the configured Container
   * @throws NullPointerException if name or image is not set
   */
  public Container build() {
    Objects.requireNonNull(name, "Container name is required. Call withName() first.");
    Objects.requireNonNull(image, "Container image is required. Call withImage() first.");

    ContainerBuilder builder = new ContainerBuilder().withName(name).withImage(image);

    // Add ports
    if (!ports.isEmpty()) {
      builder.withPorts(
          ports.stream()
              .map(
                  p -> {
                    ContainerPortBuilder portBuilder =
                        new ContainerPortBuilder().withContainerPort(p.port);
                    if (p.name != null) {
                      portBuilder.withName(p.name);
                    }
                    return portBuilder.build();
                  })
              .toList());
    }

    // Add environment variables (preserving insertion order)
    if (!envVars.isEmpty()) {
      builder.withEnv(new ArrayList<>(envVars.values()));
    }

    // Add command
    if (command != null && !command.isEmpty()) {
      builder.withCommand(command);
    }

    // Add args
    if (args != null && !args.isEmpty()) {
      builder.withArgs(args);
    }

    // Add volume mounts
    if (!volumeMounts.isEmpty()) {
      builder.withVolumeMounts(volumeMounts);
    }

    // Add probes
    if (readinessProbeConfigurer != null) {
      ProbeSpec probeSpec = new ProbeSpec();
      readinessProbeConfigurer.accept(probeSpec);
      builder.withReadinessProbe(probeSpec.build());
    }

    if (livenessProbeConfigurer != null) {
      ProbeSpec probeSpec = new ProbeSpec();
      livenessProbeConfigurer.accept(probeSpec);
      builder.withLivenessProbe(probeSpec.build());
    }

    if (startupProbeConfigurer != null) {
      ProbeSpec probeSpec = new ProbeSpec();
      startupProbeConfigurer.accept(probeSpec);
      builder.withStartupProbe(probeSpec.build());
    }

    // Add resources
    if (cpuRequest != null || memoryRequest != null || cpuLimit != null || memoryLimit != null) {
      ResourceRequirementsBuilder resourceBuilder = new ResourceRequirementsBuilder();

      if (cpuRequest != null || memoryRequest != null) {
        Map<String, Quantity> requests = new LinkedHashMap<>();
        if (cpuRequest != null) {
          requests.put("cpu", new Quantity(cpuRequest));
        }
        if (memoryRequest != null) {
          requests.put("memory", new Quantity(memoryRequest));
        }
        resourceBuilder.withRequests(requests);
      }

      if (cpuLimit != null || memoryLimit != null) {
        Map<String, Quantity> limits = new LinkedHashMap<>();
        if (cpuLimit != null) {
          limits.put("cpu", new Quantity(cpuLimit));
        }
        if (memoryLimit != null) {
          limits.put("memory", new Quantity(memoryLimit));
        }
        resourceBuilder.withLimits(limits);
      }

      builder.withResources(resourceBuilder.build());
    }

    // Apply customizers in order
    for (UnaryOperator<ContainerBuilder> customizer : customizers) {
      builder = customizer.apply(builder);
    }

    return builder.build();
  }

  // =============================================================
  // Internal helper classes
  // =============================================================

  /** Internal record to hold port configuration. */
  private record PortEntry(int port, String name) {}
}
