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

  /**
   * True when this {@code Namespace} was created by the owning {@link K8sCluster}.
   * External (attached) namespaces have {@code owned == false} and must not be deleted
   * by the cluster on tear-down.
   */
  @Getter
  private final boolean owned;

  /**
   * @deprecated Use {@link #owned(String, io.fabric8.kubernetes.api.model.Namespace)} or
   *     {@link #external(String, io.fabric8.kubernetes.api.model.Namespace)} instead.
   *     Retained only so that existing callers compile during the Wave 1/Wave 2 transition.
   */
  @Deprecated
  public Namespace(String name, io.fabric8.kubernetes.api.model.Namespace clusterNamespace) {
      this(name, clusterNamespace, true);
  }

  Namespace(String name, io.fabric8.kubernetes.api.model.Namespace clusterNamespace, boolean owned) {
      this.name = name;
      this.clusterNamespace = clusterNamespace;
      this.owned = owned;
  }

  private Namespace(String name) {
      this.name = name;
      this.clusterNamespace = null;
      this.owned = false;
  }

  /** Factory for a namespace that was created by (and is owned by) the cluster. */
  public static Namespace owned(String name, io.fabric8.kubernetes.api.model.Namespace cn) {
    return new Namespace(name, cn, true);
  }

  /** Factory for a namespace that already existed in the cluster (attached, not owned). */
  public static Namespace external(String name, io.fabric8.kubernetes.api.model.Namespace cn) {
    return new Namespace(name, cn, false);
  }

  /** Returns true if the namespace has been created in the cluster and is in phase {@code Active}. */
  public boolean isCreatedInCluster() {
    if (clusterNamespace == null) {
      return false;
    }
    NamespaceStatus status = clusterNamespace.getStatus();
    if (status == null) {
      return false;
    }
    return "Active".equalsIgnoreCase(status.getPhase());
  }
}
