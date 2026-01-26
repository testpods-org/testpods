package org.testpods.core.service;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages ClusterIP Service resources.
 *
 * <p>ClusterIP is the default service type for Kubernetes. It exposes the service on a
 * cluster-internal IP, making it only reachable from within the cluster.
 *
 * <p>This is appropriate for:
 *
 * <ul>
 *   <li>Internal services accessed by other pods
 *   <li>Default service type for Deployment-based pods
 *   <li>Services that don't need external access
 * </ul>
 */
public class ClusterIPServiceManager implements ServiceManager {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterIPServiceManager.class);

  private Service service;
  private ServiceConfig config;

  @Override
  public Service create(ServiceConfig config) {
    this.config = config;

    ServiceBuilder builder =
        new ServiceBuilder()
            .withNewMetadata()
            .withName(config.name())
            .withNamespace(config.namespace())
            .withLabels(config.labels())
            .endMetadata()
            .withNewSpec()
            .withSelector(config.selector())
            .withType("ClusterIP")
            .addNewPort()
            .withName("primary")
            .withPort(config.port())
            .withTargetPort(new IntOrString(config.port()))
            .endPort()
            .endSpec();

    // Apply customizers
    for (UnaryOperator<ServiceBuilder> customizer : config.customizers()) {
      builder = customizer.apply(builder);
    }

    this.service =
        config
            .client()
            .services()
            .inNamespace(config.namespace())
            .resource(builder.build())
            .create();

    LOG.debug("Created ClusterIP service: {}/{}", config.namespace(), config.name());
    return service;
  }

  @Override
  public void delete() {
    if (config != null && service != null) {
      config.client().services().inNamespace(config.namespace()).withName(config.name()).delete();
      LOG.debug("Deleted ClusterIP service: {}/{}", config.namespace(), config.name());
      service = null;
    }
  }

  @Override
  public Service getService() {
    return service;
  }

  @Override
  public String getName() {
    return config != null ? config.name() : null;
  }

  @Override
  public String getServiceType() {
    return "ClusterIP";
  }
}
