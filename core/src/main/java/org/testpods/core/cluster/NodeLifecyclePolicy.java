package org.testpods.core.cluster;

/** @deprecated Use {@link ProfileLifecyclePolicy}. */
@Deprecated
public enum NodeLifecyclePolicy {
  /** @deprecated Use {@link ProfileLifecyclePolicy#DESTROY_ON_CLOSE}. */
  @Deprecated
  DESTROY_ON_CLOSE,
  /** @deprecated Use {@link ProfileLifecyclePolicy#STOP_ON_CLOSE}. */
  @Deprecated
  STOP_ON_CLOSE,
  /** @deprecated Use {@link ProfileLifecyclePolicy#LEAVE_RUNNING}. */
  @Deprecated
  LEAVE_RUNNING
}
