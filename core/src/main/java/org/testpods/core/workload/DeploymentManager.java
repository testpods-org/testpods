package org.testpods.core.workload;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages Kubernetes Deployment workloads.
 *
 * <p>Deployments are appropriate for:
 *
 * <ul>
 *   <li>Stateless application services
 *   <li>Generic containers without persistence needs
 *   <li>Services that don't need stable network identity
 * </ul>
 */
public class DeploymentManager implements WorkloadManager {

  private static final Logger LOG = LoggerFactory.getLogger(DeploymentManager.class);

  private Deployment deployment;
  private WorkloadConfig config;

  @Override
  public void create(WorkloadConfig config) {
    this.config = config;

    DeploymentBuilder builder =
        new DeploymentBuilder()
            .withNewMetadata()
            .withName(config.name())
            .withNamespace(config.namespace())
            .withLabels(config.labels())
            .endMetadata()
            .withNewSpec()
            .withReplicas(1)
            .withNewSelector()
            .withMatchLabels(config.getSelector())
            .endSelector()
            .withNewTemplate()
            .withNewMetadata()
            .withLabels(config.labels())
            .withAnnotations(config.annotations())
            .endMetadata()
            .withSpec(config.podSpec())
            .endTemplate()
            .endSpec();

    this.deployment = builder.build();

    config
        .client()
        .apps()
        .deployments()
        .inNamespace(config.namespace())
        .resource(deployment)
        .create();

    LOG.debug("Created deployment: {}/{}", config.namespace(), config.name());
  }

  @Override
  public void delete() {
    if (config != null && deployment != null) {
      config
          .client()
          .apps()
          .deployments()
          .inNamespace(config.namespace())
          .withName(config.name())
          .delete();
      LOG.debug("Deleted deployment: {}/{}", config.namespace(), config.name());
      deployment = null;
    }
  }

  @Override
  public boolean isRunning() {
    if (config == null) {
      return false;
    }

    Deployment current =
        config
            .client()
            .apps()
            .deployments()
            .inNamespace(config.namespace())
            .withName(config.name())
            .get();

    if (current == null || current.getStatus() == null) {
      return false;
    }

    Integer replicas = current.getStatus().getReplicas();
    return replicas != null && replicas > 0;
  }

  @Override
  public boolean isReady() {
    if (config == null) {
      return false;
    }

    Deployment current =
        config
            .client()
            .apps()
            .deployments()
            .inNamespace(config.namespace())
            .withName(config.name())
            .get();

    if (current == null || current.getStatus() == null) {
      return false;
    }

    Integer readyReplicas = current.getStatus().getReadyReplicas();
    Integer desiredReplicas = current.getSpec().getReplicas();

    return readyReplicas != null && readyReplicas.equals(desiredReplicas);
  }

  @Override
  public String getName() {
    return config != null ? config.name() : null;
  }

  /**
   * Get the created Deployment resource.
   *
   * @return the Deployment, or null if not created
   */
  public Deployment getDeployment() {
    return deployment;
  }
}
