package org.testpods.core.service;

import io.fabric8.kubernetes.api.model.Service;

/**
 * Manages Kubernetes Service resources for test pods.
 * <p>
 * This is an internal implementation detail, not exposed to pod users.
 * It extracts service management logic from the pod class hierarchy
 * to enable composition over inheritance.
 * <p>
 * Available implementations:
 * <ul>
 *   <li>{@link ClusterIPServiceManager} - Default for Deployments</li>
 *   <li>{@link HeadlessServiceManager} - For StatefulSet stable DNS</li>
 *   <li>{@link NodePortServiceManager} - For external access</li>
 *   <li>{@link CompositeServiceManager} - Combines multiple service types</li>
 * </ul>
 */
public interface ServiceManager {

    /**
     * Create the service in the cluster.
     *
     * @param config Service configuration from the pod
     * @return the created Service resource
     */
    Service create(ServiceConfig config);

    /**
     * Delete the service from the cluster.
     */
    void delete();

    /**
     * Get the created service, or null if not created.
     *
     * @return the Service resource, or null
     */
    Service getService();

    /**
     * Get the service name.
     *
     * @return the name of the service resource
     */
    String getName();

    /**
     * Get the service type for logging/debugging.
     *
     * @return a human-readable service type (e.g., "ClusterIP", "Headless", "NodePort")
     */
    default String getServiceType() {
        return getClass().getSimpleName().replace("ServiceManager", "");
    }
}
