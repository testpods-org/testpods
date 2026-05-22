package org.testpods.core.cluster;

/**
 * Policy applied to a profile owned by a {@code MinikubeCluster} when the cluster is closed.
 *
 * <p>Only applies when TestPods started the profile. Profiles that were already running when the
 * cluster attached are always left running regardless of policy.
 */
public enum ProfileLifecyclePolicy {
  /** Destroy the profile on {@code close()} (default for profiles TestPods started). */
  DESTROY_ON_CLOSE,
  /** Stop but do not destroy the profile on {@code close()}. */
  STOP_ON_CLOSE,
  /** Leave the profile running on {@code close()}. */
  LEAVE_RUNNING
}
