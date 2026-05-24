package org.testpods.core.provisioning;

/**
 *
 * The Provisioner is responsible for provisioning and managing test pods and their associated resources in the Kubernetes cluster.
 * It provides methods for setting up and tearing down test environments, as well as interacting with the Registry.*
 */
public class Provisioner {

    private Registry registry;

    public Provisioner() {
    }

    /** Tear down test pods and resources via the registry. Cluster lifecycle is owned by MinikubeCluster.close(). */
    public void tearDown() {
        if (registry != null) registry.tearDown();
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }


    public void ensureClusterIsReady() {
        if (registry != null) registry.ensureClusterIsReady();
    }

    public void ensureNamespaceIsActive() {
        if (registry != null) registry.ensureNamespaceIsActive();
    }

    public void provisionTestPods() {
        if (registry != null) registry.provisionTestPods();
    }
}
