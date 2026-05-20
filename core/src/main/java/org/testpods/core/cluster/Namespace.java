package org.testpods.core.cluster;

import io.fabric8.kubernetes.api.model.NamespaceStatus;
import lombok.Getter;
import lombok.experimental.Delegate;

/** A Kubernetes namespace for running test pods. */
public class Namespace {

  @Getter
  private final String name;

  @Delegate
  private final io.fabric8.kubernetes.api.model.Namespace clusterNamespace;

  public Namespace(String name, io.fabric8.kubernetes.api.model.Namespace clusterNamespace) {
      this.name = name;
      this.clusterNamespace = clusterNamespace;
  }

  private Namespace(String name) {
      this.name = name;
      this.clusterNamespace = null;
  }

  /** Returns true if the namespace has been created in the cluster. */
  public boolean isCreatedInCluster() {
    final NamespaceStatus status = clusterNamespace.getStatus();
    //TODO The status on the namespace is only valid at the time which the clients returns an updated starte from the cluster.
    return true;
  }
}
