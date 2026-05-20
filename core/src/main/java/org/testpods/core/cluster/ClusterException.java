package org.testpods.core.cluster;

import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.Getter;

/** Runtime exception for cluster-related errors. */
public class ClusterException extends RuntimeException {

  @Getter
  private KubernetesClientException kubernetesClientException;

  // TODO reduce constructors to support cluster-specific exceptions - shield defaults

  public ClusterException(String message) {
    super(message);
  }

  public ClusterException(String message, Throwable cause) {
    super(message, cause);
  }

  public ClusterException(KubernetesClientException kubernetesClientException) {
    this.kubernetesClientException = kubernetesClientException;
  }
}
