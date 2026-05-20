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

    public void tearDown() {
        // stop the cluster and delete the namespace
        // inspect the K8sCluster annotation to see if the cluster should be stopped and namespace deleted

    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }
}
