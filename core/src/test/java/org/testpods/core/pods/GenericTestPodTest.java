package org.testpods.core.pods;

import static org.junit.jupiter.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import org.junit.jupiter.api.Test;
import org.testpods.core.TestNamespace;
import org.testpods.core.cluster.client.MinikubeCluster;
import org.testpods.core.wait.WaitStrategy;

import java.io.IOException;

class GenericTestPodTest {

  @Test
  public void name() throws IOException {
    MinikubeCluster minikubeCluster = MinikubeCluster.create();
    TestNamespace namespace = new TestNamespace(minikubeCluster);
    try {
      GenericTestPod nginx =
          new GenericTestPod("nginx:1.25")
              .withPort(80)
              .withName("nginx")
              .withEnv("NGINX_HOST", "localhost")
              .waitingFor(WaitStrategy.forPort(80))
              .inNamespace(namespace);
      nginx.start();
      var nginxServiceRunning = getServiceServiceResource(minikubeCluster, namespace, nginx);
      assertNotNull(nginxServiceRunning.get());
      nginx.stop();
      var nginxServiceDeleted = getServiceServiceResource(minikubeCluster, namespace, nginx);
      assertNull(nginxServiceDeleted.get());
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    } finally {
      namespace.close();
      minikubeCluster.close();
    }
  }

  private static ServiceResource<Service> getServiceServiceResource(
      MinikubeCluster minikubeCluster, TestNamespace namespace, GenericTestPod nginx) {
    var nginxServiceRunning =
        minikubeCluster
            .getClient()
            .services()
            .inNamespace(namespace.getName())
            .withName(nginx.getName());
    return nginxServiceRunning;
  }
}
