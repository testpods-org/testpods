package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testpods.core.PropertyContext;
import org.testpods.core.TestPodStartException;
import org.testpods.core.cluster.HostAndPort;
import org.testpods.core.wait.WaitStrategy;

/**
 * Base class for pods backed by a Kubernetes StatefulSet.
 *
 * <p>StatefulSets are appropriate for:
 *
 * <ul>
 *   <li>Databases (MongoDB, PostgreSQL, MySQL)
 *   <li>Message brokers (Kafka, RabbitMQ)
 *   <li>Distributed caches (Redis Cluster)
 *   <li>Any workload requiring stable network identity or persistent storage
 * </ul>
 *
 * <p>This class handles:
 *
 * <ul>
 *   <li>StatefulSet creation and lifecycle
 *   <li>Headless Service creation for stable DNS
 *   <li>Low-level customization via Fabric8 builders
 * </ul>
 *
 * <p>Subclasses must implement:
 *
 * <ul>
 *   <li>{@link #buildMainContainer()} - Define the primary container
 *   <li>{@link #getInternalPort()} - Return the primary port
 *   <li>{@link #publishProperties(PropertyContext)} - Publish connection info
 * </ul>
 *
 * @param <SELF> The concrete type for fluent method chaining
 */
public abstract class StatefulSetPod<SELF extends StatefulSetPod<SELF>> extends BaseTestPod<SELF> {

  private static final Logger LOG = LoggerFactory.getLogger(StatefulSetPod.class);

  // =============================================================
  // Low-level customizers
  // =============================================================

  protected final List<UnaryOperator<StatefulSetBuilder>> statefulSetCustomizers =
      new ArrayList<>();
  protected final List<UnaryOperator<ServiceBuilder>> serviceCustomizers = new ArrayList<>();
  protected final List<UnaryOperator<PersistentVolumeClaimBuilder>> pvcCustomizers =
      new ArrayList<>();

  // =============================================================
  // Created resources (for cleanup)
  // =============================================================

  protected StatefulSet statefulSet;
  protected Service service;

  // =============================================================
  // External access (set after start)
  // =============================================================

  protected volatile HostAndPort externalAccess;

  // =============================================================
  // Low-level customization methods
  // =============================================================

  /**
   * Customize the StatefulSet using Fabric8 builders.
   *
   * <p>Example:
   *
   * <pre>{@code
   * .withStatefulSetCustomizer(ss -> ss
   *     .editSpec()
   *         .withReplicas(3)
   *     .endSpec())
   * }</pre>
   */
  public SELF withStatefulSetCustomizer(UnaryOperator<StatefulSetBuilder> customizer) {
    this.statefulSetCustomizers.add(customizer);
    return self();
  }

  /**
   * Customize the Service using Fabric8 builders.
   *
   * <p>Example:
   *
   * <pre>{@code
   * .withServiceCustomizer(svc -> svc
   *     .editSpec()
   *         .withType("NodePort")
   *     .endSpec())
   * }</pre>
   */
  public SELF withServiceCustomizer(UnaryOperator<ServiceBuilder> customizer) {
    this.serviceCustomizers.add(customizer);
    return self();
  }

  /**
   * Customize the PVC template using Fabric8 builders.
   *
   * <p>Example:
   *
   * <pre>{@code
   * .withPvcCustomizer(pvc -> pvc
   *     .editSpec()
   *         .withStorageClassName("fast-ssd")
   *         .withNewResources()
   *             .addToRequests("storage", new Quantity("50Gi"))
   *         .endResources()
   *     .endSpec())
   * }</pre>
   */
  public SELF withPvcCustomizer(UnaryOperator<PersistentVolumeClaimBuilder> customizer) {
    this.pvcCustomizers.add(customizer);
    return self();
  }

  // =============================================================
  // Lifecycle implementation
  // =============================================================

  @Override
  public void start() {
    // Resolve namespace lazily if not explicitly set
    ensureNamespace();

    // Ensure namespace is created in cluster
    if (!namespace.isCreated()) {
      namespace.create();
    }

    KubernetesClient client = getClient();
    String ns = namespace.getName();

    try {
      // Build and create StatefulSet
      this.statefulSet = buildStatefulSet();
      client.apps().statefulSets().inNamespace(ns).resource(statefulSet).create();

      // Build and create Service
      this.service = buildService();
      client.services().inNamespace(ns).resource(service).create();

      // Wait for ready
      waitForReady();

      // Set external access info after pod is ready
      this.externalAccess =
          namespace.getCluster().getAccessStrategy().getExternalEndpoint(this, getInternalPort());

    } catch (Exception e) {
      LOG.warn("Start failed for pod '{}', cleaning up resources", name);
      cleanup(client, ns);
      throw new TestPodStartException(name, e.getMessage(), e);
    }
  }

  private void cleanup(KubernetesClient client, String ns) {
    // Delete in reverse order of creation
    try {
      if (service != null) {
        client.services().inNamespace(ns).withName(name).delete();
        LOG.debug("Deleted service: {}", name);
      }
    } catch (Exception e) {
      LOG.debug("Failed to delete service '{}': {}", name, e.getMessage());
    }

    try {
      if (statefulSet != null) {
        client.apps().statefulSets().inNamespace(ns).withName(name).delete();
        LOG.debug("Deleted statefulset: {}", name);
      }
    } catch (Exception e) {
      LOG.debug("Failed to delete statefulset '{}': {}", name, e.getMessage());
    }

    this.service = null;
    this.statefulSet = null;
  }

  @Override
  public void stop() {
    KubernetesClient client = getClient();

    if (statefulSet != null) {
      client.apps().statefulSets().inNamespace(namespace.getName()).withName(name).delete();
      statefulSet = null;
    }

    if (service != null) {
      client.services().inNamespace(namespace.getName()).withName(name).delete();
      service = null;
    }
  }

  @Override
  public boolean isRunning() {
    KubernetesClient client = getClient();
    StatefulSet current =
        client.apps().statefulSets().inNamespace(namespace.getName()).withName(name).get();

    if (current == null) {
      return false;
    }

    Integer replicas = current.getStatus().getReplicas();
    return replicas != null && replicas > 0;
  }

  @Override
  public boolean isReady() {
    KubernetesClient client = getClient();
    StatefulSet current =
        client.apps().statefulSets().inNamespace(namespace.getName()).withName(name).get();

    if (current == null) {
      return false;
    }

    Integer readyReplicas = current.getStatus().getReadyReplicas();
    Integer desiredReplicas = current.getSpec().getReplicas();

    return readyReplicas != null && readyReplicas.equals(desiredReplicas);
  }

  // =============================================================
  // Connection - External access via cluster's strategy
  // =============================================================

  @Override
  public String getExternalHost() {
    if (externalAccess == null) {
      throw new IllegalStateException(
          "Pod '" + name + "' not started. Call start() before accessing external endpoint.");
    }
    return externalAccess.host();
  }

  @Override
  public int getExternalPort() {
    if (externalAccess == null) {
      throw new IllegalStateException(
          "Pod '" + name + "' not started. Call start() before accessing external endpoint.");
    }
    return externalAccess.port();
  }

  // =============================================================
  // Default wait strategy
  // =============================================================

  @Override
  protected WaitStrategy getDefaultWaitStrategy() {
    return WaitStrategy.forReadinessProbe().withTimeout(Duration.ofMinutes(2));
  }

  // =============================================================
  // Resource building
  // =============================================================

  /** Build the StatefulSet resource. */
  protected StatefulSet buildStatefulSet() {
    Map<String, String> podLabels = buildLabels();

    // Build base StatefulSet
    StatefulSetBuilder builder =
        new StatefulSetBuilder()
            .withNewMetadata()
            .withName(name)
            .withNamespace(namespace.getName())
            .withLabels(podLabels)
            .endMetadata()
            .withNewSpec()
            .withReplicas(1)
            .withServiceName(name)
            .withNewSelector()
            .withMatchLabels(Map.of("app", name))
            .endSelector()
            .withNewTemplate()
            .withNewMetadata()
            .withLabels(podLabels)
            .withAnnotations(annotations)
            .endMetadata()
            .withNewSpec()
            .addToContainers(buildMainContainer())
            .endSpec()
            .endTemplate()
            .endSpec();

    // Apply pod customizations (init containers, sidecars, etc.)
    PodSpec currentPodSpec = builder.buildSpec().getTemplate().getSpec();
    PodSpecBuilder podSpecBuilder = new PodSpecBuilder(currentPodSpec);
    podSpecBuilder = applyPodCustomizations(podSpecBuilder);

    builder.editSpec().editTemplate().withSpec(podSpecBuilder.build()).endTemplate().endSpec();

    // Apply PVC templates if customizers exist
    if (!pvcCustomizers.isEmpty()) {
      PersistentVolumeClaimBuilder pvcBuilder =
          new PersistentVolumeClaimBuilder()
              .withNewMetadata()
              .withName("data")
              .endMetadata()
              .withNewSpec()
              .withAccessModes("ReadWriteOnce")
              .endSpec();

      for (UnaryOperator<PersistentVolumeClaimBuilder> customizer : pvcCustomizers) {
        pvcBuilder = customizer.apply(pvcBuilder);
      }

      builder.editSpec().addToVolumeClaimTemplates(pvcBuilder.build()).endSpec();
    }

    // Apply StatefulSet-level customizers
    for (UnaryOperator<StatefulSetBuilder> customizer : statefulSetCustomizers) {
      builder = customizer.apply(builder);
    }

    return builder.build();
  }

  /**
   * Build the Service resource. Creates a headless service for StatefulSet (ClusterIP: None) by
   * default, or a regular ClusterIP service if customized.
   */
  protected Service buildService() {
    ServiceBuilder builder =
        new ServiceBuilder()
            .withNewMetadata()
            .withName(name)
            .withNamespace(namespace.getName())
            .withLabels(buildLabels())
            .endMetadata()
            .withNewSpec()
            .addToSelector("app", name)
            .withClusterIP("None") // Headless service for StatefulSet
            .addNewPort()
            .withName("primary")
            .withPort(getInternalPort())
            .withTargetPort(new IntOrString(getInternalPort()))
            .endPort()
            .endSpec();

    // Apply service customizers
    for (UnaryOperator<ServiceBuilder> customizer : serviceCustomizers) {
      builder = customizer.apply(builder);
    }

    return builder.build();
  }

  // =============================================================
  // Abstract methods - must be implemented by concrete pods
  // =============================================================

  /**
   * Build the main container for this pod.
   *
   * <p>Subclasses implement this to define their specific container: image, ports, environment
   * variables, probes, etc.
   *
   * @return The Fabric8 Container object
   */
  protected abstract io.fabric8.kubernetes.api.model.Container buildMainContainer();
}
