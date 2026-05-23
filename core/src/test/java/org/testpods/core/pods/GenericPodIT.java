package org.testpods.core.pods;

import static org.junit.jupiter.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.Namespace;
import org.testpods.core.cluster.MinikubeCluster;
import org.testpods.core.wait.WaitStrategy;

class GenericPodIT {

  /**
   * Original verbose approach - explicit cluster and namespace. Still supported for full control.
   */
  @Test
  public void explicitClusterAndNamespace() throws IOException {
    MinikubeCluster cluster = K8sCluster.newMinikube();
    Namespace namespace = cluster.createNamespace();
    try {
      GenericPod nginx =
          new GenericPod("nginx:1.25")
              .withPort(80)
              .withName("nginx")
              .withEnv("NGINX_HOST", "localhost")
              .waitingFor(WaitStrategy.forPort(80))
              .inNamespace(namespace);
      nginx.start();
      var nginxServiceRunning = getServiceResource(cluster.getClient(), namespace, nginx);
      assertNotNull(nginxServiceRunning.get());
      nginx.stop();
      var nginxServiceDeleted = getServiceResource(cluster.getClient(), namespace, nginx);
      assertNull(nginxServiceDeleted.get());
    } finally {
      cluster.close();
    }
  }

  /**
   * Simplified approach - auto-discovers cluster, auto-generates namespace. Similar to
   * TestContainers simplicity.
   */
  @Test
  public void simplifiedAutoDiscover() {
    GenericPod nginx =
        new GenericPod("nginx:1.25")
            .withPort(80)
            .withName("nginx")
            .waitingFor(WaitStrategy.forPort(80));

    try {
      nginx.start(); // Auto-discovers cluster, creates namespace

      assertTrue(nginx.isRunning());
      assertNotNull(nginx.getNamespace());
      assertNotNull(nginx.getCluster());

      nginx.stop();
      assertFalse(nginx.isRunning());
    } finally {
        try {
          nginx.getCluster().close();
        } catch (IOException e) {
          // ignore
        }
    }
  }

  /** Intermediate approach - explicit cluster, auto-generated namespace. */
  @Test
  public void explicitClusterAutoNamespace() throws IOException {
    K8sCluster cluster = K8sCluster.newMinikube();

    GenericPod nginx =
        new GenericPod("nginx:1.25")
            .withPort(80)
            .withName("nginx")
            .waitingFor(WaitStrategy.forPort(80))
            .inCluster(cluster);

    try {
      nginx.start(); // Creates namespace automatically

      assertTrue(nginx.isRunning());
      assertNotNull(nginx.getNamespace());

      nginx.stop();
    } finally {
      cluster.close();
    }
  }

  /** Intermediate approach - auto-discover cluster, explicit namespace name. */
  @Test
  public void autoClusterExplicitNamespaceName() {
    GenericPod nginx =
        new GenericPod("nginx:1.25")
            .withPort(80)
            .withName("nginx")
            .waitingFor(WaitStrategy.forPort(80))
            .inNamespace("my-test-namespace");

    try {
      nginx.start(); // Auto-discovers cluster, uses specified namespace name

      assertTrue(nginx.isRunning());
      assertEquals("my-test-namespace", nginx.getNamespace().getName());

      nginx.stop();
    } finally {
        try {
          nginx.getCluster().close();
        } catch (IOException e) {
          // ignore
        }
    }
  }

  private static ServiceResource<Service> getServiceResource(
          KubernetesClient client, Namespace namespace, GenericPod pod) {
    return client.services().inNamespace(namespace.getName()).withName(pod.getName());
  }
}
