package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.testpods.core.ExecResult;
import org.testpods.core.PropertyContext;
import org.testpods.core.TestPodStartException;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.Namespace;
import org.testpods.core.pods.builders.InitContainerBuilder;
import org.testpods.core.pods.builders.SidecarBuilder;
import org.testpods.core.service.ServiceConfig;
import org.testpods.core.service.ServiceManager;
import org.testpods.core.wait.WaitStrategy;
import org.testpods.core.workload.WorkloadConfig;
import org.testpods.core.workload.WorkloadManager;

/**
 * Base implementation of {@link Pod} providing common functionality.
 *
 * <p>This class handles:
 *
 * <ul>
 *   <li>Configuration state (name, namespace, labels, annotations)
 *   <li>Mid-level customization (init containers, sidecars)
 *   <li>Low-level pod spec customization
 *   <li>Common operations (getLogs, exec)
 * </ul>
 *
 * <p>Subclasses must implement:
 *
 * <ul>
 *   <li>{@link #start()} - Create K8s resources
 *   <li>{@link #stop()} - Delete K8s resources
 *   <li>{@link #isRunning()} - Check if workload exists
 *   <li>{@link #isReady()} - Check if workload is ready
 *   <li>{@link #getInternalPort()} - Return primary container port
 *   <li>{@link #getExternalHost()}, {@link #getExternalPort()} - External access
 *   <li>{@link #publishProperties(PropertyContext)} - Publish connection properties
 *   <li>{@link #getDefaultWaitStrategy()} - Default wait strategy for this pod type
 * </ul>
 *
 * @param <SELF> The concrete type for fluent method chaining
 */
public abstract class BaseManagedPod<SELF extends BaseManagedPod<SELF>> implements Pod<SELF> {

  // =============================================================
  // Configuration state
  // =============================================================
  protected K8sCluster cluster;
  protected String name;
  protected Namespace namespace;
  protected WorkloadManager workload;
  protected ServiceManager serviceMgr;
  protected final Map<String, String> labels = new LinkedHashMap<>();
  protected final Map<String, String> annotations = new LinkedHashMap<>();
  protected String cpuRequest;
  protected String memoryRequest;
  protected WaitStrategy waitStrategy;

  // Lazy initialization support - these are used when namespace is not explicitly set
//  protected K8sCluster cluster;
  protected String explicitNamespaceName;

  // =============================================================
  // Mid-level customizations
  // =============================================================

  protected final List<Consumer<InitContainerBuilder>> initContainerConfigurers = new ArrayList<>();
  protected final List<Consumer<SidecarBuilder>> sidecarConfigurers = new ArrayList<>();

  // =============================================================
  // Low-level customizations
  // =============================================================

  protected final List<UnaryOperator<PodSpecBuilder>> podCustomizers = new ArrayList<>();

  // =============================================================
  // Fluent self-return helper
  // =============================================================

  @SuppressWarnings("unchecked")
  protected SELF self() {
    return (SELF) this;
  }

  // =============================================================
  // Configuration implementation
  // =============================================================

  @Override
  public SELF withName(String name) {
    this.name = name;
    return self();
  }

  @Override
  public SELF inNamespace(Namespace namespace) {
    this.namespace = namespace;
    return self();
  }

  @Override
  public SELF inNamespace(String namespaceName) {
    this.explicitNamespaceName = namespaceName;
    return self();
  }

  @Override
  public SELF inCluster(K8sCluster cluster) {
    this.cluster = cluster;
    return self();
  }

  @Override
  public SELF withLabels(Map<String, String> labels) {
    this.labels.putAll(labels);
    return self();
  }

  @Override
  public SELF withAnnotations(Map<String, String> annotations) {
    this.annotations.putAll(annotations);
    return self();
  }

  @Override
  public SELF withResources(String cpuRequest, String memoryRequest) {
    this.cpuRequest = cpuRequest;
    this.memoryRequest = memoryRequest;
    return self();
  }

  // =============================================================
  // Mid-level implementation
  // =============================================================

  @Override
  public SELF withInitContainer(Consumer<InitContainerBuilder> configurer) {
    this.initContainerConfigurers.add(configurer);
    return self();
  }

  @Override
  public SELF withSidecar(Consumer<SidecarBuilder> configurer) {
    this.sidecarConfigurers.add(configurer);
    return self();
  }

  // =============================================================
  // Low-level implementation
  // =============================================================

  @Override
  public SELF withPodCustomizer(UnaryOperator<PodSpecBuilder> customizer) {
    this.podCustomizers.add(customizer);
    return self();
  }

  // =============================================================
  // Wait strategy
  // =============================================================

  @Override
  public SELF waitingFor(WaitStrategy strategy) {
    this.waitStrategy = strategy;
    return self();
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
  public String getInternalHost() {
    return name + "." + namespace.getName() + ".svc.cluster.local";
  }

  // =============================================================
  // Observability implementation
  // =============================================================

  @Override
  public String getLogs() {
    return getLogs((Duration) null);
  }

  @Override
  public String getLogs(Duration since) {
    PodResource podResource = getPodResource();
    if (since != null) {
      return podResource.sinceSeconds((int) since.toSeconds()).getLog();
    }
    return podResource.getLog();
  }

  @Override
  public String getLogs(String containerName) {
    return getPodResource().inContainer(containerName).getLog();
  }

  @Override
  public ExecResult exec(String... command) {
    return exec(null, command);
  }

  @Override
  public ExecResult exec(String containerName, String... command) {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    CountDownLatch latch = new CountDownLatch(1);
    int[] exitCode = {-1};

    PodResource podResource = getPodResource();

    ExecWatch watch =
        (containerName != null)
            ? podResource
                .inContainer(containerName)
                .writingOutput(stdout)
                .writingError(stderr)
                .usingListener(createExecListener(latch, exitCode))
                .exec(command)
            : podResource
                .writingOutput(stdout)
                .writingError(stderr)
                .usingListener(createExecListener(latch, exitCode))
                .exec(command);

    try {
      boolean completed = latch.await(30, TimeUnit.SECONDS);
      if (!completed) {
        throw new IllegalStateException("Command execution timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while executing command", e);
    } finally {
      watch.close();
    }

    return new ExecResult(exitCode[0], stdout.toString(), stderr.toString());
  }

  private ExecListener createExecListener(CountDownLatch latch, int[] exitCode) {
    return new ExecListener() {
      @Override
      public void onOpen() {
        // Connection opened
      }

      @Override
      public void onFailure(Throwable t, Response response) {
        exitCode[0] = -1;
        latch.countDown();
      }

      @Override
      public void onClose(int code, String reason) {
        exitCode[0] = code;
        latch.countDown();
      }
    };
  }

  // =============================================================
  // Protected helpers for subclasses
  // =============================================================
  
  /** Get the Kubernetes client from the namespace's cluster. */
  protected KubernetesClient getClient() {
    if (cluster == null) {
      throw new ClassCastException("No cluster specified for pod " + name + " in namespace");
    }
    return cluster.getClient();
  }

  @Override
  public K8sCluster getCluster() {
    return cluster;
  }

  /**
   * Get the PodResource for this test pod.
   *
   * <p>This finds the first pod matching the "app" label and returns a PodResource for it, which
   * provides access to logs, exec, and other pod operations.
   *
   * <p>Since test pods typically run as single-replica deployments or statefulsets, getting the
   * first matching pod is the expected behavior.
   *
   * @return PodResource for the test pod
   * @throws IllegalStateException if no pod is found matching the label
   */
  protected PodResource getPodResource() {
    KubernetesClient client = getClient();
    PodList pods = client.pods().inNamespace(namespace.getName()).withLabel("app", name).list();

    if (pods.getItems().isEmpty()) {
      throw new IllegalStateException(
          "No pod found with label app=" + name + " in namespace " + namespace.getName());
    }

    String podName = pods.getItems().get(0).getMetadata().getName();
    return client.pods().inNamespace(namespace.getName()).withName(podName);
  }

  /**
   * Apply all pod customizations to a base pod spec.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Adds init containers from mid-level API
   *   <li>Adds sidecar containers from mid-level API
   *   <li>Applies resource requests if specified
   *   <li>Applies low-level pod customizers
   * </ol>
   *
   * <p>Subclasses call this when building their workload resource.
   *
   * @param baseSpec The base pod spec (with main container already added)
   * @return The customized pod spec
   */
  protected PodSpecBuilder applyPodCustomizations(PodSpecBuilder baseSpec) {
    // 1. Add init containers from mid-level API
    for (Consumer<InitContainerBuilder> configurer : initContainerConfigurers) {
      InitContainerBuilder builder = new InitContainerBuilder();
      configurer.accept(builder);
      Container initContainer = builder.build();
      baseSpec.addToInitContainers(initContainer);
    }

    // 2. Add sidecars from mid-level API
    for (Consumer<SidecarBuilder> configurer : sidecarConfigurers) {
      SidecarBuilder builder = new SidecarBuilder();
      configurer.accept(builder);
      Container sidecar = builder.build();
      baseSpec.addToContainers(sidecar);
    }

    // 3. Apply resource requests to main container (first container)
    if (cpuRequest != null || memoryRequest != null) {
      PodSpec currentSpec = baseSpec.build();
      if (currentSpec.getContainers() != null && !currentSpec.getContainers().isEmpty()) {
        Container mainContainer = currentSpec.getContainers().get(0);
        if (mainContainer.getResources() == null) {
          var resourcesBuilder = new ResourceRequirementsBuilder();
          if (cpuRequest != null) {
            resourcesBuilder.addToRequests("cpu", new Quantity(cpuRequest));
          }
          if (memoryRequest != null) {
            resourcesBuilder.addToRequests("memory", new Quantity(memoryRequest));
          }
          mainContainer.setResources(resourcesBuilder.build());
        }
      }
    }

    // 4. Apply low-level customizers (full Fabric8 access)
    for (UnaryOperator<PodSpecBuilder> customizer : podCustomizers) {
      baseSpec = customizer.apply(baseSpec);
    }

    return baseSpec;
  }

  /** Wait for the pod to be ready according to the configured strategy. */
  protected void waitForReady() {
    WaitStrategy strategy =
        this.waitStrategy != null ? this.waitStrategy : getDefaultWaitStrategy();
    strategy.waitUntilReady(this);
  }

  /**
   * Get the default wait strategy for this pod type. Subclasses override to provide appropriate
   * defaults.
   */
  protected abstract WaitStrategy getDefaultWaitStrategy();

  /** Build standard labels for this pod. Includes "app" label and any user-specified labels. */
  protected Map<String, String> buildLabels() {
    Map<String, String> allLabels = new LinkedHashMap<>();
    allLabels.put("app", name);
    allLabels.put("managed-by", "testpods");
    allLabels.putAll(labels);
    return allLabels;
  }

  /** Create environment variable list from a map. */
  protected List<EnvVar> toEnvVars(Map<String, String> env) {
    return env.entrySet().stream()
        .map(e -> new EnvVarBuilder().withName(e.getKey()).withValue(e.getValue()).build())
        .toList();
  }

  // =============================================================
  // Abstract methods - must be implemented by subclasses
  // =============================================================

  @Override
  public final void start() {
    if (this instanceof PodLifecycleHooks h) {
      h.preStart();
    }
    doStart();
    if (this instanceof PodLifecycleHooks h) {
      h.postStart();
    }
  }

  @Override
  public final void stop() {
    if (this instanceof PodLifecycleHooks h) {
      h.preStop();
    }
    doStop();
  }

  /**
   * Implements the standard start workflow: ensure managers are bound, then create the workload
   * and service via their strategies. Hooks are invoked around this call by {@link #start()}.
   */
  protected final void doStart() {
    if (workload == null) {
      workload = createWorkloadManager();
    }
    if (serviceMgr == null) {
      serviceMgr = createServiceManager();
    }

    try {
      var client = getClient();
      String ns = namespace.getName();

      PodSpec podSpec = buildPodSpec();
      WorkloadConfig wlc =
          WorkloadConfig.builder()
              .name(name)
              .namespace(ns)
              .labels(buildLabels())
              .annotations(annotations)
              .podSpec(podSpec)
              .client(client)
              .build();
      workload.create(wlc);

      ServiceConfig svc =
          ServiceConfig.builder()
              .name(name)
              .namespace(ns)
              .port(getInternalPort())
              .labels(buildLabels())
              .selector(java.util.Map.of("app", name))
              .client(client)
              .build();
      serviceMgr.create(svc);

      waitForReady();
    } catch (Exception e) {
      cleanup();
      throw new TestPodStartException(name, e.getMessage(), e);
    }
  }

  /**
   * Implements the standard stop workflow: delete service then workload via strategies.
   * {@link PodLifecycleHooks#preStop()} is invoked before this call by {@link #stop()}.
   */
  protected final void doStop() {
    if (serviceMgr != null) {
      try {
        serviceMgr.delete();
      } catch (Exception ignored) {
      }
    }
    if (workload != null) {
      try {
        workload.delete();
      } catch (Exception ignored) {
      }
    }
  }

  private void cleanup() {
    try {
      if (serviceMgr != null) serviceMgr.delete();
    } catch (Exception ignored) {
    }
    try {
      if (workload != null) workload.delete();
    } catch (Exception ignored) {
    }
  }

  /**
   * Build the complete pod spec including the main container and all pod-level customizations
   * (init containers, sidecars, resource requests, pod customizers).
   */
  protected PodSpec buildPodSpec() {
    PodSpecBuilder spec = new PodSpecBuilder().addToContainers(buildMainContainer());
    return applyPodCustomizations(spec).build();
  }

  /** Build the main container (kind-specific, supplied by concrete pods). */
  protected abstract io.fabric8.kubernetes.api.model.Container buildMainContainer();

  /**
   * Subclasses provide the {@link WorkloadManager} for their workload kind. Called lazily on first
   * {@link #start()} invocation.
   */
  protected abstract WorkloadManager createWorkloadManager();

  /**
   * Subclasses provide the {@link ServiceManager} for their service shape. Called lazily on first
   * {@link #start()} invocation.
   */
  protected abstract ServiceManager createServiceManager();

  @Override
  public boolean isRunning() {
    return workload != null && workload.isRunning();
  }

  @Override
  public boolean isReady() {
    return workload != null && workload.isReady();
  }

  @Override
  public abstract int getInternalPort();

  @Override
  public abstract String getExternalHost();

  @Override
  public abstract int getExternalPort();

  @Override
  public abstract void publishProperties(PropertyContext ctx);
}
