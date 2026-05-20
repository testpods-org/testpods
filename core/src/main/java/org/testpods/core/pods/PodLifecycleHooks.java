package org.testpods.core.pods;

/**
 * Optional opt-in interface for pods that need to perform side-effect work around their lifecycle.
 *
 * <p>{@link BaseManagedPod#start()} and {@link BaseManagedPod#stop()} invoke these methods if the
 * concrete pod class implements this interface. No reflection, no annotations — just an
 * {@code instanceof} check.
 *
 * <p>Default implementations are no-ops, so implementing pods can override only the phases they
 * care about.
 */
public interface PodLifecycleHooks {

  /** Called before the workload and service are created. */
  default void preStart() {}

  /** Called after the workload is created, the service is created, and the pod is ready. */
  default void postStart() {}

  /** Called before the workload and service are deleted. */
  default void preStop() {}
}
