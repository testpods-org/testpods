package org.testpods.core.cluster;

/**
 * Lifecycle status of a minikit-managed minikube profile.
 *
 * <p>Reported by {@link MinikitCli#status(String)}; used by {@code MinikubeCluster} to decide
 * whether to start, attach to, or wait for a profile.
 */
enum ProfileStatus {
  /** The profile does not exist (or the {@code minikit} binary itself is missing). */
  NOT_FOUND,
  /** The profile exists but is stopped. */
  STOPPED,
  /** The profile is in the middle of starting up. */
  STARTING,
  /** The profile is up and the API server is reachable. */
  RUNNING,
  /** The profile is in an error state or its status could not be determined. */
  ERROR
}
