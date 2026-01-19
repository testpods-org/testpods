package org.testpods.core.pods;

import org.testpods.core.cluster.K8sCluster;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;

import java.io.Closeable;

/** A Kubernetes namespace for running test pods. */
public class TestNamespace implements Closeable {

  private final K8sCluster cluster;
  private final String name;
  private boolean created = false;

  public TestNamespace(K8sCluster cluster, String name) {
    this.cluster = cluster;
    this.name = name;
  }

  /** Creates the namespace in the cluster if it doesn't already exist. */
  public void create() {
    if (created) {
      return;
    }

    Namespace existing = cluster.getClient().namespaces().withName(name).get();
    if (existing == null) {
      Namespace ns =
          new NamespaceBuilder().withNewMetadata().withName(name).endMetadata().build();
      cluster.getClient().namespaces().resource(ns).create();
    }
    created = true;
  }

  /** Deletes the namespace from the cluster. */
  @Override
  public void close() {
    if (created) {
      cluster.getClient().namespaces().withName(name).delete();
      created = false;
    }
  }

  /** Returns the namespace name. */
  public String getName() {
    return name;
  }

  /** Returns true if the namespace has been created. */
  public boolean isCreated() {
    return created;
  }

  /** Returns the cluster this namespace belongs to. */
  public K8sCluster getCluster() {
    return cluster;
  }
}
