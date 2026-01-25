package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import org.testpods.core.ExecResult;
import org.testpods.core.PropertyContext;
import org.testpods.core.cluster.TestNamespace;
import org.testpods.core.pods.builders.InitContainerBuilder;
import org.testpods.core.pods.builders.SidecarBuilder;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.wait.WaitStrategy;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Primary abstraction for a test workload in Kubernetes.
 * Represents a running component that tests can interact with.
 * <p>
 * This is the "Testcontainers equivalent" interface - like {@code Container} in Testcontainers,
 * but for Kubernetes pods and their backing workload resources.
 *
 * @param <SELF> The concrete type for fluent method chaining
 */
public interface TestPod<SELF extends TestPod<SELF>> {

    // =============================================================
    // Configuration
    // =============================================================

    /**
     * Set the name for this pod and its associated resources.
     * This becomes the K8s resource name and part of the DNS name.
     */
    SELF withName(String name);

    /**
     * Deploy this pod into the specified namespace.
     */
    SELF inNamespace(TestNamespace namespace);

    /**
     * Deploy this pod into a namespace with the specified name.
     * <p>
     * The cluster will be resolved automatically via {@link K8sCluster#discover()}
     * or from {@link TestPodDefaults} if configured.
     * <p>
     * The namespace will be created if it doesn't exist when {@link #start()} is called.
     *
     * @param namespaceName The namespace name to use
     */
    SELF inNamespace(String namespaceName);

    /**
     * Deploy this pod into the specified cluster.
     * <p>
     * A default namespace will be created automatically when {@link #start()} is called.
     * The namespace name will be resolved from {@link TestPodDefaults}
     * or generated as {@code testpods-xxxxx}.
     *
     * @param cluster The cluster to deploy into
     */
    SELF inCluster(K8sCluster cluster);

    /**
     * Add labels to the pod and its associated resources.
     */
    SELF withLabels(Map<String, String> labels);

    /**
     * Add annotations to the pod.
     */
    SELF withAnnotations(Map<String, String> annotations);

    /**
     * Set resource requests for the main container.
     *
     * @param cpuRequest    CPU request (e.g., "100m", "0.5")
     * @param memoryRequest Memory request (e.g., "128Mi", "1Gi")
     */
    SELF withResources(String cpuRequest, String memoryRequest);

    // =============================================================
    // Mid-level customization (simplified builders, no .endX())
    // =============================================================

    /**
     * Add an init container that runs before the main container starts.
     * Init containers run sequentially and must complete successfully.
     * <p>
     * Example:
     * <pre>{@code
     * .withInitContainer(init -> init
     *     .withName("permission-fix")
     *     .withImage("busybox:latest")
     *     .withCommand("sh", "-c", "chmod 777 /data"))
     * }</pre>
     */
    SELF withInitContainer(Consumer<InitContainerBuilder> configurer);

    /**
     * Add a sidecar container that runs alongside the main container.
     * Sidecars share the pod's network and can share volumes.
     * <p>
     * Example:
     * <pre>{@code
     * .withSidecar(sidecar -> sidecar
     *     .withName("metrics-exporter")
     *     .withImage("prom/statsd-exporter:latest")
     *     .withPort(9102))
     * }</pre>
     */
    SELF withSidecar(Consumer<SidecarBuilder> configurer);

    // =============================================================
    // Low-level customization (full Fabric8 access)
    // =============================================================

    /**
     * Customize the Pod spec directly using Fabric8 builders.
     * This is the escape hatch for advanced K8s configuration not exposed
     * through the high-level API.
     * <p>
     * Example:
     * <pre>{@code
     * .withPodCustomizer(podSpec -> podSpec
     *     .editOrNewSecurityContext()
     *         .withRunAsUser(1000L)
     *         .withFsGroup(1000L)
     *     .endSecurityContext())
     * }</pre>
     */
    SELF withPodCustomizer(UnaryOperator<PodSpecBuilder> customizer);

    // =============================================================
    // Wait strategy
    // =============================================================

    /**
     * Configure how to wait for this pod to be ready.
     * Defaults vary by pod type (e.g., readiness probe for databases).
     */
    SELF waitingFor(WaitStrategy strategy);

    // =============================================================
    // Lifecycle
    // =============================================================

    /**
     * Start the pod and wait for it to be ready.
     * Creates all necessary K8s resources (workload, service, configmaps, etc.).
     */
    void start();

    /**
     * Stop the pod and clean up K8s resources.
     */
    void stop();

    /**
     * Check if the pod's workload resource exists and has running replicas.
     */
    boolean isRunning();

    /**
     * Check if the pod is ready to accept traffic (readiness probe passing).
     */
    boolean isReady();

    // =============================================================
    // Observability
    // =============================================================

    /**
     * Get logs from the main container.
     */
    String getLogs();

    /**
     * Get logs from the main container since the specified duration.
     */
    String getLogs(Duration since);

    /**
     * Get logs from a specific container in the pod.
     */
    String getLogs(String containerName);

    /**
     * Execute a command in the main container.
     */
    ExecResult exec(String... command);

    /**
     * Execute a command in a specific container.
     */
    ExecResult exec(String containerName, String... command);

    // =============================================================
    // Accessors
    // =============================================================

    /**
     * Get the name of this pod (used as K8s resource name).
     */
    String getName();

    /**
     * Get the namespace this pod is deployed in.
     */
    TestNamespace getNamespace();

    // =============================================================
    // Connection - Internal (pod-to-pod within cluster)
    // =============================================================

    /**
     * Get the cluster-internal DNS hostname.
     * Format: {name}.{namespace}.svc.cluster.local
     */
    String getInternalHost();

    /**
     * Get the primary internal port (original container port).
     */
    int getInternalPort();

    // =============================================================
    // Connection - External (test code to pod)
    // =============================================================

    /**
     * Get the external hostname for test code to connect.
     * Depends on cluster's ExternalAccessStrategy (port-forward, nodeport, etc.).
     */
    String getExternalHost();

    /**
     * Get the external port for test code to connect.
     */
    int getExternalPort();

    // =============================================================
    // Property publishing
    // =============================================================

    /**
     * Publish connection properties to a shared PropertyContext.
     * Called automatically after the pod starts.
     * <p>
     * Implementations should publish both internal and external connection details
     * using the naming convention: {podName}.internal.* and {podName}.external.*
     */
    void publishProperties(PropertyContext ctx);
}
