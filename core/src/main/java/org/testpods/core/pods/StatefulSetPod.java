package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.testpods.core.cluster.HostAndPort;
import org.testpods.core.service.HeadlessServiceManager;
import org.testpods.core.service.ServiceManager;
import org.testpods.core.wait.WaitStrategy;
import org.testpods.core.workload.StatefulSetManager;
import org.testpods.core.workload.WorkloadManager;

/**
 * Base class for pods backed by a Kubernetes StatefulSet.
 *
 * <p>Wires {@link StatefulSetManager} as the workload strategy and {@link HeadlessServiceManager}
 * as the service strategy. Subclasses implement {@link #buildMainContainer()} and any
 * domain-specific configuration.
 */
public abstract class StatefulSetPod<SELF extends StatefulSetPod<SELF>> extends BaseManagedPod<SELF> {

  // Kind-specific customizers (user-facing). Not yet plumbed into the managers.
  protected final List<UnaryOperator<StatefulSetBuilder>> statefulSetCustomizers = new ArrayList<>();
  protected final List<UnaryOperator<ServiceBuilder>> serviceCustomizers = new ArrayList<>();
  protected final List<UnaryOperator<PersistentVolumeClaimBuilder>> pvcCustomizers = new ArrayList<>();

  protected volatile HostAndPort externalAccess;

  public SELF withStatefulSetCustomizer(UnaryOperator<StatefulSetBuilder> customizer) {
    this.statefulSetCustomizers.add(customizer);
    return self();
  }

  public SELF withServiceCustomizer(UnaryOperator<ServiceBuilder> customizer) {
    this.serviceCustomizers.add(customizer);
    return self();
  }

  public SELF withPvcCustomizer(UnaryOperator<PersistentVolumeClaimBuilder> customizer) {
    this.pvcCustomizers.add(customizer);
    return self();
  }

  @Override
  protected WorkloadManager createWorkloadManager() {
    StatefulSetManager mgr = new StatefulSetManager();
    if (!pvcCustomizers.isEmpty()) {
      List<PersistentVolumeClaim> templates = new ArrayList<>();
      for (UnaryOperator<PersistentVolumeClaimBuilder> c : pvcCustomizers) {
        PersistentVolumeClaimBuilder b =
            new PersistentVolumeClaimBuilder()
                .withNewMetadata().withName("data").endMetadata()
                .withNewSpec().withAccessModes("ReadWriteOnce").endSpec();
        templates.add(c.apply(b).build());
      }
      mgr.withPvcTemplates(templates);
    }
    return mgr;
  }

  @Override
  protected ServiceManager createServiceManager() {
    return new HeadlessServiceManager();
  }

  @Override
  public String getExternalHost() {
    if (externalAccess == null) {
      externalAccess = cluster.getAccessStrategy().getExternalEndpoint(this, getInternalPort());
    }
    return externalAccess.host();
  }

  @Override
  public int getExternalPort() {
    if (externalAccess == null) {
      externalAccess = cluster.getAccessStrategy().getExternalEndpoint(this, getInternalPort());
    }
    return externalAccess.port();
  }

  @Override
  protected WaitStrategy getDefaultWaitStrategy() {
    return WaitStrategy.forReadinessProbe().withTimeout(Duration.ofMinutes(2));
  }
}
