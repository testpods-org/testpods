package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import org.testpods.core.PropertyContext;
import org.testpods.core.wait.WaitStrategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A generic pod for running any container image.
 * <p>
 * Use this when there's no specific TestPod implementation for your image,
 * or when you need full control over the container configuration.
 * <p>
 * Unlike domain-specific pods (MongoDBPod, KafkaPod), GenericTestPod requires
 * you to specify all configuration - there are minimal defaults.
 * <p>
 * Example:
 * <pre>{@code
 * GenericTestPod redis = new GenericTestPod("redis:7-alpine")
 *     .withPort(6379)
 *     .withCommand("redis-server", "--appendonly", "yes")
 *     .inNamespace(namespace);
 * 
 * GenericTestPod nginx = new GenericTestPod("nginx:1.25")
 *     .withPort(80)
 *     .withEnv("NGINX_HOST", "localhost")
 *     .waitingFor(WaitStrategy.forHttp("/health", 80))
 *     .inNamespace(namespace);
 * 
 * GenericTestPod custom = new GenericTestPod("mycompany/custom-tool:latest")
 *     .withPort(8080)
 *     .withEnv("CONFIG_PATH", "/etc/config")
 *     .withInitContainer(init -> init
 *         .withName("config-loader")
 *         .withImage("busybox")
 *         .withCommand("sh", "-c", "wget -O /config/app.conf http://config-server/app"))
 *     .inNamespace(namespace);
 * }</pre>
 */
public class GenericTestPod extends DeploymentPod<GenericTestPod> {

    private final String image;
    private final List<Integer> ports = new ArrayList<>();
    private final Map<String, String> env = new LinkedHashMap<>();
    private List<String> command;
    private List<String> args;
    private Integer primaryPort;

    // Readiness probe configuration
    private String readinessPath;
    private Integer readinessPort;

    /**
     * Create a generic pod with the specified image.
     *
     * @param image Full image reference (e.g., "nginx:latest", "myregistry.io/myimage:v1")
     */
    public GenericTestPod(String image) {
        this.image = image;
        this.name = deriveNameFromImage(image);
        this.labels.put("app", this.name);
    }

    /**
     * Derive a Kubernetes-safe name from an image reference.
     * Examples:
     * - "nginx:latest" -> "nginx"
     * - "redis:7-alpine" -> "redis"
     * - "myregistry.io/team/app:v1" -> "app"
     */
    private static String deriveNameFromImage(String image) {
        // Remove tag
        String withoutTag = image.contains(":") 
            ? image.substring(0, image.lastIndexOf(':')) 
            : image;
        
        // Remove registry and path, keep only the image name
        String name = withoutTag.contains("/")
            ? withoutTag.substring(withoutTag.lastIndexOf('/') + 1)
            : withoutTag;
        
        // Make it DNS-safe
        return name.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }

    // =============================================================
    // Configuration fluent API
    // =============================================================

    /**
     * Expose a port from the container.
     * The first port added becomes the primary port.
     */
    public GenericTestPod withPort(int port) {
        this.ports.add(port);
        if (this.primaryPort == null) {
            this.primaryPort = port;
        }
        return this;
    }

    /**
     * Expose a port and mark it as the primary port.
     */
    public GenericTestPod withPrimaryPort(int port) {
        this.ports.add(port);
        this.primaryPort = port;
        return this;
    }

    /**
     * Add an environment variable.
     */
    public GenericTestPod withEnv(String name, String value) {
        this.env.put(name, value);
        return this;
    }

    /**
     * Add multiple environment variables.
     */
    public GenericTestPod withEnv(Map<String, String> env) {
        this.env.putAll(env);
        return this;
    }

    /**
     * Set the command (replaces the image's ENTRYPOINT).
     */
    public GenericTestPod withCommand(String... command) {
        this.command = Arrays.asList(command);
        return this;
    }

    /**
     * Set the arguments (replaces the image's CMD).
     */
    public GenericTestPod withArgs(String... args) {
        this.args = Arrays.asList(args);
        return this;
    }

    /**
     * Configure an HTTP readiness probe.
     */
    public GenericTestPod withHttpReadinessProbe(String path, int port) {
        this.readinessPath = path;
        this.readinessPort = port;
        return this;
    }

    // =============================================================
    // Connection information
    // =============================================================

    @Override
    public int getInternalPort() {
        if (primaryPort != null) {
            return primaryPort;
        }
        if (!ports.isEmpty()) {
            return ports.get(0);
        }
        return 80; // Default fallback
    }

    /**
     * Get the external URL for this service.
     */
    public String getExternalUrl() {
        return "http://" + getExternalHost() + ":" + getExternalPort();
    }

    /**
     * Get the internal URL for this service (for other pods).
     */
    public String getInternalUrl() {
        return "http://" + getInternalHost() + ":" + getInternalPort();
    }

    // =============================================================
    // Property publishing
    // =============================================================

    @Override
    public void publishProperties(PropertyContext ctx) {
        String prefix = getName();

        // Internal (for pods in cluster)
        ctx.publish(prefix + ".internal.host", this::getInternalHost);
        ctx.publish(prefix + ".internal.port", () -> String.valueOf(getInternalPort()));
        ctx.publish(prefix + ".internal.url", this::getInternalUrl);

        // External (for test code)
        ctx.publish(prefix + ".external.host", this::getExternalHost);
        ctx.publish(prefix + ".external.port", () -> String.valueOf(getExternalPort()));
        ctx.publish(prefix + ".external.url", this::getExternalUrl);

        // Convenience alias
        ctx.publish(prefix + ".url", this::getExternalUrl);
    }

    // =============================================================
    // Default wait strategy
    // =============================================================

    @Override
    protected WaitStrategy getDefaultWaitStrategy() {
        // If HTTP readiness is configured, use that
        if (readinessPath != null && readinessPort != null) {
            return WaitStrategy.forHttp(readinessPath, readinessPort)
                .withTimeout(Duration.ofMinutes(1));
        }
        
        // Otherwise, use port check on primary port
        if (primaryPort != null || !ports.isEmpty()) {
            return WaitStrategy.forPort(getInternalPort())
                .withTimeout(Duration.ofMinutes(1));
        }
        
        // Fallback to readiness probe
        return WaitStrategy.forReadinessProbe()
            .withTimeout(Duration.ofMinutes(1));
    }

    // =============================================================
    // Container building
    // =============================================================

    @Override
    protected Container buildMainContainer() {
        ContainerBuilder builder = new ContainerBuilder()
            .withName(name)
            .withImage(image);

        // Add command if specified
        if (command != null && !command.isEmpty()) {
            builder.withCommand(command);
        }

        // Add args if specified
        if (args != null && !args.isEmpty()) {
            builder.withArgs(args);
        }

        // Add environment variables
        if (!env.isEmpty()) {
            List<EnvVar> envVars = env.entrySet().stream()
                .map(e -> new EnvVarBuilder()
                    .withName(e.getKey())
                    .withValue(e.getValue())
                    .build())
                .toList();
            builder.withEnv(envVars);
        }

        // Add ports
        if (!ports.isEmpty()) {
            List<io.fabric8.kubernetes.api.model.ContainerPort> containerPorts = ports.stream()
                .map(p -> new ContainerPortBuilder()
                    .withContainerPort(p)
                    .build())
                .toList();
            builder.withPorts(containerPorts);
        }

        // Add readiness probe if configured
        if (readinessPath != null && readinessPort != null) {
            builder.withNewReadinessProbe()
                .withNewHttpGet()
                    .withPath(readinessPath)
                    .withPort(new IntOrString(readinessPort))
                .endHttpGet()
                .withInitialDelaySeconds(5)
                .withPeriodSeconds(10)
                .withTimeoutSeconds(5)
            .endReadinessProbe();
        } else if (!ports.isEmpty()) {
            // Default TCP probe on first port
            builder.withNewReadinessProbe()
                .withNewTcpSocket()
                    .withPort(new IntOrString(getInternalPort()))
                .endTcpSocket()
                .withInitialDelaySeconds(5)
                .withPeriodSeconds(10)
                .withTimeoutSeconds(5)
            .endReadinessProbe();
        }

        return builder.build();
    }
}
