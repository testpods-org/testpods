package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.testpods.core.ExecResult;
import org.testpods.core.PropertyContext;
import org.testpods.core.TestPodStartException;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.Namespace;
import org.testpods.core.cluster.minikube.MinikubeImageLoadTarget;
import org.testpods.core.cluster.minikube.MinikubeImageLoader;
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
@Slf4j
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
  protected final Set<Integer> exposedPorts = new LinkedHashSet<>();
  protected final Map<Integer, Integer> fixedExposedPorts = new LinkedHashMap<>();
  protected final Map<Integer, org.testpods.core.cluster.HostAndPort> mappedPorts =
      new ConcurrentHashMap<>();
  private static final MinikubeImageLoader MINIKUBE_IMAGE_LOADER = new MinikubeImageLoader();
  private static final Map<K8sCluster, Set<String>> PRELOADED_MINIKUBE_IMAGES =
      Collections.synchronizedMap(new IdentityHashMap<>());

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

  @Override
  public SELF withExposedPorts(Integer... ports) {
    for (Integer port : ports) {
      if (port == null || port <= 0 || port > 65535) {
        throw new IllegalArgumentException("port must be between 1 and 65535: " + port);
      }
      exposedPorts.add(port);
    }
    return self();
  }

  @Override
  public SELF withFixedExposedPort(int hostPort, int containerPort) {
    if (hostPort <= 0 || hostPort > 65535) {
      throw new IllegalArgumentException("hostPort must be between 1 and 65535: " + hostPort);
    }
    if (containerPort <= 0 || containerPort > 65535) {
      throw new IllegalArgumentException(
          "containerPort must be between 1 and 65535: " + containerPort);
    }
    exposedPorts.add(containerPort);
    fixedExposedPorts.put(containerPort, hostPort);
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

    PodResource podResource = getPodResource();

    ExecWatch watch =
        (containerName != null)
            ? podResource
                .inContainer(containerName)
                .writingOutput(stdout)
                .writingError(stderr)
                .exec(command)
            : podResource
                .writingOutput(stdout)
                .writingError(stderr)
                .exec(command);

    try {
      int exitCode = watch.exitCode().get(30, TimeUnit.SECONDS);
      return new ExecResult(exitCode, stdout.toString(), stderr.toString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while executing command", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Command execution failed", e);
    } catch (TimeoutException e) {
      throw new IllegalStateException("Command execution timed out", e);
    } finally {
      watch.close();
    }
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

  @Override
  public String getHost() {
    return getExternalHost();
  }

  @Override
  public int getMappedPort(int originalPort) {
    return getExternalEndpoint(originalPort).port();
  }

  @Override
  public OptionalInt getFixedExposedPort(int containerPort) {
    Integer fixedPort = fixedExposedPorts.get(containerPort);
    return fixedPort != null ? OptionalInt.of(fixedPort) : OptionalInt.empty();
  }

  protected org.testpods.core.cluster.HostAndPort getExternalEndpoint(int internalPort) {
    if (cluster == null || namespace == null) {
      throw new IllegalStateException(
          "Pod " + name + " is not started; external endpoint is unavailable");
    }
    return mappedPorts.computeIfAbsent(
        internalPort, port -> cluster.getAccessStrategy().getExternalEndpoint(this, port));
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
    getActiveWaitStrategy().waitUntilReady(this);
  }

  protected WaitStrategy getActiveWaitStrategy() {
    return this.waitStrategy != null ? this.waitStrategy : getDefaultWaitStrategy();
  }

  /**
   * For Minikube-backed clusters, cache an external dependency image in Docker Desktop and load it
   * into the active Minikube profile before Kubernetes schedules the pod.
   */
  protected void preloadExternalImageForMinikube(String image) {
    if (image == null || image.isBlank()) {
      return;
    }
    if (!(cluster instanceof MinikubeImageLoadTarget minikube)) {
      return;
    }

    synchronized (PRELOADED_MINIKUBE_IMAGES) {
      Set<String> loadedImages =
          PRELOADED_MINIKUBE_IMAGES.computeIfAbsent(cluster, ignored -> new HashSet<>());
      if (loadedImages.contains(image)) {
        return;
      }
      log.info(
          "Preloading dependency image {} into minikube profile {}",
          image,
          minikube.getProfileName());
      MINIKUBE_IMAGE_LOADER.loadCachedOrPull(minikube.getProfileName(), image);
      loadedImages.add(image);
    }
  }

  /**
   * Get the default wait strategy for this pod type. Subclasses override to provide appropriate
   * defaults.
   */
  protected abstract WaitStrategy getDefaultWaitStrategy();

  /**
   * Build pod-specific deployment details for debug logging.
   *
   * <p>Domain pods can override this to append connection strings, broker addresses, database
   * names, credentials metadata, or other developer-facing runtime details.
   */
  protected List<String> buildCustomDeploymentDetailLines() {
    return List.of();
  }

  /** Build Service customizers for this pod. Subclasses can override for workload-specific needs. */
  protected List<UnaryOperator<ServiceBuilder>> buildServiceCustomizers() {
    return List.of();
  }

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

  /** Log detailed Kubernetes deployment information for this pod at debug level. */
  protected void logDeploymentDetails() {
    if (!log.isDebugEnabled()) {
      return;
    }

    try {
      log.debug("TestPod deployment details for '{}':\n{}", name, buildDeploymentDetailsReport());
    } catch (Exception e) {
      log.debug("Could not build deployment details for '{}': {}", name, e.getMessage(), e);
    }
  }

  String buildDeploymentDetailsReport() {
    List<String> lines = new ArrayList<>();
    lines.add("Pod");
    lines.add("  name: " + name);
    lines.add("  namespace: " + namespace.getName());
    lines.add("  workload: " + workload.getWorkloadType() + "/" + workload.getName());
    lines.add("  ready: " + isReady());
    lines.add("  running: " + isRunning());
    lines.add("  waitStrategy: " + getActiveWaitStrategy().getClass().getName());
    lines.add("  internal: " + getInternalHost() + ":" + getInternalPort());
    try {
      lines.add("  external: " + getExternalHost() + ":" + getExternalPort());
    } catch (Exception e) {
      lines.add("  external: unavailable (" + e.getMessage() + ")");
    }

    appendServices(lines);
    appendPods(lines);
    appendConfigMaps(lines);
    appendPersistentVolumeClaims(lines);

    List<String> customLines = buildCustomDeploymentDetailLines();
    if (!customLines.isEmpty()) {
      lines.add("Custom");
      for (String line : customLines) {
        lines.add("  " + line);
      }
    }

    return String.join(System.lineSeparator(), lines);
  }

  private void appendServices(List<String> lines) {
    var services =
        getClient()
            .services()
            .inNamespace(namespace.getName())
            .withLabel("app", name)
            .list()
            .getItems();
    lines.add("Services");
    if (services.isEmpty()) {
      lines.add("  none");
      return;
    }
    for (Service service : services) {
      var spec = service.getSpec();
      lines.add("  " + service.getMetadata().getName());
      lines.add("    type: " + nullSafe(spec.getType()));
      lines.add("    clusterIP: " + nullSafe(spec.getClusterIP()));
      lines.add("    selector: " + nullSafe(spec.getSelector()));
      if (spec.getPorts() != null) {
        for (ServicePort port : spec.getPorts()) {
          lines.add(
              "    port: "
                  + nullSafe(port.getName())
                  + " "
                  + port.getPort()
                  + " -> "
                  + port.getTargetPort()
                  + (port.getNodePort() != null ? " nodePort=" + port.getNodePort() : ""));
        }
      }
    }
  }

  private void appendPods(List<String> lines) {
    var pods =
        getClient().pods().inNamespace(namespace.getName()).withLabel("app", name).list().getItems();
    lines.add("Runtime Pods");
    if (pods.isEmpty()) {
      lines.add("  none");
      return;
    }

    for (io.fabric8.kubernetes.api.model.Pod pod : pods) {
      lines.add("  " + pod.getMetadata().getName());
      if (pod.getStatus() != null) {
        lines.add("    phase: " + nullSafe(pod.getStatus().getPhase()));
        lines.add("    podIP: " + nullSafe(pod.getStatus().getPodIP()));
      }
      if (pod.getSpec() != null) {
        lines.add("    nodeName: " + nullSafe(pod.getSpec().getNodeName()));
        if (pod.getSpec().getContainers() != null) {
          for (Container container : pod.getSpec().getContainers()) {
            appendContainer(lines, container);
          }
        }
      }
    }
  }

  private void appendContainer(List<String> lines, Container container) {
    lines.add("    container: " + container.getName());
    lines.add("      image: " + container.getImage());
    if (container.getPorts() != null && !container.getPorts().isEmpty()) {
      List<String> ports = new ArrayList<>();
      for (ContainerPort port : container.getPorts()) {
        ports.add(nullSafe(port.getName()) + ":" + port.getContainerPort());
      }
      lines.add("      ports: " + ports);
    }
    if (container.getEnv() != null && !container.getEnv().isEmpty()) {
      List<String> env = new ArrayList<>();
      for (EnvVar envVar : container.getEnv()) {
        env.add(envVar.getName() + "=" + maskEnvValue(envVar.getName(), envVar.getValue()));
      }
      lines.add("      env: " + env);
    }
    if (container.getVolumeMounts() != null && !container.getVolumeMounts().isEmpty()) {
      List<String> mounts = new ArrayList<>();
      for (VolumeMount mount : container.getVolumeMounts()) {
        mounts.add(mount.getName() + ":" + mount.getMountPath());
      }
      lines.add("      volumeMounts: " + mounts);
    }
  }

  private void appendConfigMaps(List<String> lines) {
    var configMaps =
        getClient()
            .configMaps()
            .inNamespace(namespace.getName())
            .withLabel("app", name)
            .list()
            .getItems();
    lines.add("ConfigMaps");
    if (configMaps.isEmpty()) {
      lines.add("  none");
      return;
    }
    for (ConfigMap configMap : configMaps) {
      lines.add("  " + configMap.getMetadata().getName());
      lines.add(
          "    keys: "
              + (configMap.getData() != null ? configMap.getData().keySet() : Set.of()));
    }
  }

  private void appendPersistentVolumeClaims(List<String> lines) {
    Set<String> claimNames = collectMountedPersistentVolumeClaimNames();
    lines.add("PersistentVolumeClaims");
    if (claimNames.isEmpty()) {
      lines.add("  none");
      return;
    }

    for (String claimName : claimNames) {
      var pvc =
          getClient()
              .persistentVolumeClaims()
              .inNamespace(namespace.getName())
              .withName(claimName)
              .get();
      if (pvc == null) {
        lines.add("  " + claimName + " (not found)");
        continue;
      }
      lines.add("  " + claimName);
      if (pvc.getStatus() != null) {
        lines.add("    phase: " + nullSafe(pvc.getStatus().getPhase()));
        lines.add("    capacity: " + nullSafe(pvc.getStatus().getCapacity()));
      }
      if (pvc.getSpec() != null) {
        lines.add("    storageClassName: " + nullSafe(pvc.getSpec().getStorageClassName()));
        lines.add("    volumeName: " + nullSafe(pvc.getSpec().getVolumeName()));
        lines.add("    accessModes: " + nullSafe(pvc.getSpec().getAccessModes()));
        if (pvc.getSpec().getResources() != null
            && pvc.getSpec().getResources().getRequests() != null) {
          lines.add("    requests: " + pvc.getSpec().getResources().getRequests());
        }
      }
    }
  }

  private Set<String> collectMountedPersistentVolumeClaimNames() {
    Set<String> claimNames = new LinkedHashSet<>();
    var pods =
        getClient().pods().inNamespace(namespace.getName()).withLabel("app", name).list().getItems();
    for (io.fabric8.kubernetes.api.model.Pod pod : pods) {
      if (pod.getSpec() == null || pod.getSpec().getVolumes() == null) {
        continue;
      }
      for (Volume volume : pod.getSpec().getVolumes()) {
        if (volume.getPersistentVolumeClaim() != null) {
          claimNames.add(volume.getPersistentVolumeClaim().getClaimName());
        }
      }
    }
    return claimNames;
  }

  private static String maskEnvValue(String name, String value) {
    if (value == null) {
      return "";
    }
    String upperName = name.toUpperCase(java.util.Locale.ROOT);
    if (upperName.contains("PASSWORD")
        || upperName.contains("SECRET")
        || upperName.contains("TOKEN")
        || upperName.contains("KEY")) {
      return "****";
    }
    return value;
  }

  private static String nullSafe(Object value) {
    return value == null ? "<none>" : String.valueOf(value);
  }

  private Integer getConfiguredFixedPortOrNull(int containerPort) {
    OptionalInt fixedPort = getFixedExposedPort(containerPort);
    return fixedPort.isPresent() ? fixedPort.getAsInt() : null;
  }

  // =============================================================
  // Abstract methods - must be implemented by subclasses
  // =============================================================

  @Override
  public final void start() {
    resolveRuntimeDefaults();
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
    resolveRuntimeDefaults();
    exposedPorts.add(getInternalPort());

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
              .ports(exposedPorts)
              .labels(buildLabels())
              .selector(java.util.Map.of("app", name))
              .customizers(buildServiceCustomizers())
              .nodePort(getConfiguredFixedPortOrNull(getInternalPort()))
              .nodePorts(fixedExposedPorts)
              .client(client)
              .build();
      serviceMgr.create(svc);

      waitForReady();
      logDeploymentDetails();
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
    if (cluster != null) {
      try {
        cluster.getAccessStrategy().cleanup(this);
      } catch (Exception ignored) {
      }
    }
    mappedPorts.clear();
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

    Namespace sharedNamespace = TestPodDefaults.getSharedNamespace();
    if (sharedNamespace != null) {
      namespace = sharedNamespace;
      return;
    }

    namespace = cluster.getDefaultNamespace();
    if (namespace == null) {
      namespace = cluster.createNamespace();
    }
  }

  private void cleanup() {
    try {
      if (cluster != null) cluster.getAccessStrategy().cleanup(this);
    } catch (Exception ignored) {
    }
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
