package org.testpods.core.cluster;

import org.testpods.core.cluster.client.MinikubeCluster;

import io.fabric8.kubernetes.client.KubernetesClient;

import java.io.Closeable;

public interface K8sCluster extends Closeable {
  KubernetesClient getClient();

  ExternalAccessStrategy getAccessStrategy();

  /** Create a K8sCluster connected to a Minikube cluster using the default profile. */
  static K8sCluster minikube() {
    return MinikubeCluster.create();
  }

  static K8sCluster kind(String clusterName) {
    return null;
  }

  static K8sCluster fromKubeconfig() { // Current context
    return null;
  }

  static K8sCluster fromKubeconfig(String path) // Explicit config
      {
    return null;
  }
}
