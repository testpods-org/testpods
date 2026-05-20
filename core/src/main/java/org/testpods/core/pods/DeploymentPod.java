package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.testpods.core.cluster.HostAndPort;
import org.testpods.core.service.ClusterIPServiceManager;
import org.testpods.core.service.ServiceManager;
import org.testpods.core.wait.WaitStrategy;
import org.testpods.core.workload.DeploymentManager;
import org.testpods.core.workload.WorkloadManager;

/**
 * Base class for pods backed by a Kubernetes Deployment.
 *
 * <p>Wires {@link DeploymentManager} as the workload strategy and {@link ClusterIPServiceManager}
 * as the service strategy. Subclasses implement {@link #buildMainContainer()} and any
 * domain-specific configuration.
 */
public abstract class DeploymentPod<SELF extends DeploymentPod<SELF>> extends BaseManagedPod<SELF> {

  // Kind-specific customizers retained on the user-facing surface, even though the
  // underlying managers don't yet consume them. They will be wired through in a follow-up.
  protected final List<UnaryOperator<DeploymentBuilder>> deploymentCustomizers = new ArrayList<>();
  protected final List<UnaryOperator<ServiceBuilder>> serviceCustomizers = new ArrayList<>();

  public SELF withDeploymentCustomizer(UnaryOperator<DeploymentBuilder> customizer) {
    this.deploymentCustomizers.add(customizer);
    return self();
  }

  public SELF withServiceCustomizer(UnaryOperator<ServiceBuilder> customizer) {
    this.serviceCustomizers.add(customizer);
    return self();
  }

  @Override
  protected WorkloadManager createWorkloadManager() {
    return new DeploymentManager();
  }

  @Override
  protected ServiceManager createServiceManager() {
    return new ClusterIPServiceManager();
  }

  @Override
  public String getExternalHost() {
    HostAndPort endpoint = cluster.getAccessStrategy().getExternalEndpoint(this, getInternalPort());
    return endpoint.host();
  }

  @Override
  public int getExternalPort() {
    HostAndPort endpoint = cluster.getAccessStrategy().getExternalEndpoint(this, getInternalPort());
    return endpoint.port();
  }

  @Override
  protected WaitStrategy getDefaultWaitStrategy() {
    return WaitStrategy.forReadinessProbe().withTimeout(java.time.Duration.ofMinutes(1));
  }
}
