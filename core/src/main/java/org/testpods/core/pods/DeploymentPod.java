package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
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
 * Base class for pods backed by a Kubernetes Deployment.
 *
 * <p>Deployments are appropriate for:
 *
 * <ul>
 *   <li>Stateless application services
 *   <li>Generic containers without specific persistence needs
 *   <li>Services that don't need stable network identity
 * </ul>
 *
 * <p>This class handles:
 *
 * <ul>
 *   <li>Deployment creation and lifecycle
 *   <li>ClusterIP Service creation
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
public abstract class DeploymentPod<SELF extends DeploymentPod<SELF>> extends BaseTestPod<SELF> {

  private static final Logger LOG = LoggerFactory.getLogger(DeploymentPod.class);

  // =============================================================
  // Low-level customizers
  // =============================================================

  protected final List<UnaryOperator<DeploymentBuilder>> deploymentCustomizers = new ArrayList<>();
  protected final List<UnaryOperator<ServiceBuilder>> serviceCustomizers = new ArrayList<>();

  // =============================================================
  // Created resources (for cleanup)
  // =============================================================

  protected Deployment deployment;
  protected Service service;

  // =============================================================
  // Low-level customization methods
  // =============================================================

  /**
   * Customize the Deployment using Fabric8 builders.
   *
   * <p>Example:
   *
   * <pre>{@code
   * .withDeploymentCustomizer(dep -> dep
   *     .editSpec()
   *         .withReplicas(3)
   *         .withNewStrategy()
   *             .withType("RollingUpdate")
   *         .endStrategy()
   *     .endSpec())
   * }</pre>
   */
  public SELF withDeploymentCustomizer(UnaryOperator<DeploymentBuilder> customizer) {
    this.deploymentCustomizers.add(customizer);
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
      // Build and create Deployment
      this.deployment = buildDeployment();
      client.apps().deployments().inNamespace(ns).resource(deployment).create();

      // Build and create Service
      this.service = buildService();
      client.services().inNamespace(ns).resource(service).create();

      // Wait for ready
      waitForReady();

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
      if (deployment != null) {
        client.apps().deployments().inNamespace(ns).withName(name).delete();
        LOG.debug("Deleted deployment: {}", name);
      }
    } catch (Exception e) {
      LOG.debug("Failed to delete deployment '{}': {}", name, e.getMessage());
    }

    this.service = null;
    this.deployment = null;
  }

  @Override
  public void stop() {
    KubernetesClient client = getClient();

    if (deployment != null) {
      final List<StatusDetails> statusDetails =
          client.apps().deployments().inNamespace(namespace.getName()).withName(name).delete();
      deployment = null;
    }

    if (service != null) {
      client.services().inNamespace(namespace.getName()).withName(name).delete();
      service = null;
    }
  }

  @Override
  public boolean isRunning() {
    KubernetesClient client = getClient();
    Deployment current =
        client.apps().deployments().inNamespace(namespace.getName()).withName(name).get();

    if (current == null) {
      return false;
    }

    Integer replicas = current.getStatus().getReplicas();
    return replicas != null && replicas > 0;
  }

  @Override
  public boolean isReady() {
    KubernetesClient client = getClient();
    Deployment current =
        client.apps().deployments().inNamespace(namespace.getName()).withName(name).get();

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
    HostAndPort endpoint =
        namespace.getCluster().getAccessStrategy().getExternalEndpoint(this, getInternalPort());
    return endpoint.host();
  }

  @Override
  public int getExternalPort() {
    HostAndPort endpoint =
        namespace.getCluster().getAccessStrategy().getExternalEndpoint(this, getInternalPort());
    return endpoint.port();
  }

  // =============================================================
  // Default wait strategy
  // =============================================================

  //    @Override
  protected WaitStrategy getDefaultWaitStrategy() {
    return WaitStrategy.forReadinessProbe().withTimeout(java.time.Duration.ofMinutes(1));
  }

  // =============================================================
  // Resource building
  // =============================================================

  /** Build the Deployment resource. */
  protected Deployment buildDeployment() {
    Map<String, String> podLabels = buildLabels();

    // Build base Deployment
    DeploymentBuilder builder =
        new DeploymentBuilder()
            .withNewMetadata()
            .withName(name)
            .withNamespace(namespace.getName())
            .withLabels(podLabels)
            .endMetadata()
            .withNewSpec()
            .withReplicas(1)
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

    // Apply Deployment-level customizers
    for (UnaryOperator<DeploymentBuilder> customizer : deploymentCustomizers) {
      builder = customizer.apply(builder);
    }

    return builder.build();
  }

  /** Build the Service resource. Creates a ClusterIP service by default. */
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
            .withType("ClusterIP")
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
