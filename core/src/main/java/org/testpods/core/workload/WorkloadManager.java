package org.testpods.core.workload;

/**
 * Manages a Kubernetes workload resource (Deployment, StatefulSet, Job, etc.).
 * <p>
 * This is an internal implementation detail, not exposed to pod users.
 * It extracts the workload management logic from the pod class hierarchy
 * to enable composition over inheritance.
 * <p>
 * Each implementation handles a specific workload type:
 * <ul>
 *   <li>{@link DeploymentManager} - for stateless pods</li>
 *   <li>{@link StatefulSetManager} - for stateful pods requiring stable identity</li>
 * </ul>
 */
public interface WorkloadManager {

    /**
     * Create the workload in the cluster.
     *
     * @param config Workload configuration from the pod
     */
    void create(WorkloadConfig config);

    /**
     * Delete the workload from the cluster.
     */
    void delete();

    /**
     * Check if the workload exists and has running replicas.
     *
     * @return true if at least one replica is running
     */
    boolean isRunning();

    /**
     * Check if the workload has all desired replicas ready.
     *
     * @return true if ready replicas equals desired replicas
     */
    boolean isReady();

    /**
     * Get the workload name.
     *
     * @return the name of the workload resource
     */
    String getName();

    /**
     * Get the workload type for logging/debugging.
     *
     * @return a human-readable workload type (e.g., "Deployment", "StatefulSet")
     */
    default String getWorkloadType() {
        return getClass().getSimpleName().replace("Manager", "");
    }
}
