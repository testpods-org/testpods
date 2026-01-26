package org.testpods.core.workload;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages Kubernetes StatefulSet workloads.
 *
 * <p>StatefulSets are appropriate for:
 *
 * <ul>
 *   <li>Databases (PostgreSQL, MongoDB, MySQL)
 *   <li>Message brokers (Kafka, RabbitMQ)
 *   <li>Distributed caches (Redis Cluster)
 *   <li>Any workload requiring stable network identity or persistent storage
 * </ul>
 */
public class StatefulSetManager implements WorkloadManager {

  private static final Logger LOG = LoggerFactory.getLogger(StatefulSetManager.class);

  private StatefulSet statefulSet;
  private WorkloadConfig config;

  // StatefulSet-specific configuration
  private String serviceName;
  private List<PersistentVolumeClaim> pvcTemplates = List.of();

  /**
   * Set the headless service name for stable DNS.
   *
   * <p>If not set, defaults to the workload name.
   *
   * @param serviceName the name of the headless service
   * @return this manager for chaining
   */
  public StatefulSetManager withServiceName(String serviceName) {
    this.serviceName = serviceName;
    return this;
  }

  /**
   * Set the PVC templates for persistent storage.
   *
   * @param templates list of PVC templates to attach to the StatefulSet
   * @return this manager for chaining
   */
  public StatefulSetManager withPvcTemplates(List<PersistentVolumeClaim> templates) {
    this.pvcTemplates = templates != null ? templates : List.of();
    return this;
  }

  @Override
  public void create(WorkloadConfig config) {
    this.config = config;

    StatefulSetBuilder builder =
        new StatefulSetBuilder()
            .withNewMetadata()
            .withName(config.name())
            .withNamespace(config.namespace())
            .withLabels(config.labels())
            .endMetadata()
            .withNewSpec()
            .withReplicas(1)
            .withServiceName(serviceName != null ? serviceName : config.name())
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

    // Add PVC templates if configured
    if (!pvcTemplates.isEmpty()) {
      builder.editSpec().addAllToVolumeClaimTemplates(pvcTemplates).endSpec();
    }

    this.statefulSet = builder.build();

    config
        .client()
        .apps()
        .statefulSets()
        .inNamespace(config.namespace())
        .resource(statefulSet)
        .create();

    LOG.debug("Created statefulset: {}/{}", config.namespace(), config.name());
  }

  @Override
  public void delete() {
    if (config != null && statefulSet != null) {
      config
          .client()
          .apps()
          .statefulSets()
          .inNamespace(config.namespace())
          .withName(config.name())
          .delete();
      LOG.debug("Deleted statefulset: {}/{}", config.namespace(), config.name());
      statefulSet = null;
    }
  }

  @Override
  public boolean isRunning() {
    if (config == null) {
      return false;
    }

    StatefulSet current =
        config
            .client()
            .apps()
            .statefulSets()
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

    StatefulSet current =
        config
            .client()
            .apps()
            .statefulSets()
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
   * Get the created StatefulSet resource.
   *
   * @return the StatefulSet, or null if not created
   */
  public StatefulSet getStatefulSet() {
    return statefulSet;
  }
}
