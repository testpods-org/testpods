package org.testpods.core.cluster.client;

/** Runtime exception for cluster-related errors. */
public class ClusterException extends RuntimeException {

  public ClusterException(String message) {
    super(message);
  }

  public ClusterException(String message, Throwable cause) {
    super(message, cause);
  }
}
