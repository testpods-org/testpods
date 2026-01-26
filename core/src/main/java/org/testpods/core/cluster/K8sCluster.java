package org.testpods.core.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.Closeable;
import org.testpods.core.cluster.client.ClusterException;
import org.testpods.core.cluster.client.MinikubeCluster;

/**
 * Abstraction for a Kubernetes cluster connection.
 *
 * <p>Provides access to the Kubernetes client and the strategy for external access to services
 * running in the cluster.
 */
public interface K8sCluster extends Closeable {

  KubernetesClient getClient();

  ExternalAccessStrategy getAccessStrategy();

  /**
   * Auto-discover an available Kubernetes cluster.
   *
   * <p>Discovery order:
   *
   * <ol>
   *   <li>Minikube with "minikit" profile (if running)
   *   <li>Minikube with "minikube" profile (if running)
   *   <li>Additional cluster types will be added in future (Kind, etc.)
   * </ol>
   *
   * @return A connected K8sCluster
   * @throws ClusterException if no cluster can be discovered
   */
  static K8sCluster discover() {
    // Try minikit profile first (TestPods default)
    try {
      return MinikubeCluster.create();
    } catch (ClusterException e) {
      // minikit not running, try default minikube
    }

    // Try default minikube profile
    try {
      return MinikubeCluster.withProfile("minikube");
    } catch (ClusterException e) {
      // minikube not running either
    }

    // TODO: Add Kind cluster detection
    // TODO: Add generic kubeconfig detection

    throw new ClusterException(
        "No Kubernetes cluster found. Start minikube with: minikube start -p minikit");
  }

  /** Create a K8sCluster connected to a Minikube cluster using the default profile ("minikit"). */
  static K8sCluster minikube() {
    return MinikubeCluster.create();
  }

  /** Create a K8sCluster connected to a Minikube cluster using a specific profile. */
  static K8sCluster minikube(String profile) {
    return MinikubeCluster.withProfile(profile);
  }

  static K8sCluster kind(String clusterName) {
    // TODO: Implement Kind cluster support
    throw new UnsupportedOperationException("Kind cluster support not yet implemented");
  }

  static K8sCluster fromKubeconfig() {
    // TODO: Implement kubeconfig-based cluster
    throw new UnsupportedOperationException("Kubeconfig cluster support not yet implemented");
  }

  static K8sCluster fromKubeconfig(String path) {
    // TODO: Implement kubeconfig-based cluster with explicit path
    throw new UnsupportedOperationException("Kubeconfig cluster support not yet implemented");
  }
}
