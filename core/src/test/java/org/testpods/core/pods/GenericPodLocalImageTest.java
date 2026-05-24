package org.testpods.core.pods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testpods.core.cluster.ClusterException;
import org.testpods.core.cluster.ExternalAccessStrategy;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.Namespace;
import org.testpods.core.cluster.minikube.MinikubeImageLoadTarget;

class GenericPodLocalImageTest {

  @AfterEach
  void resetImageLoader() {
    GenericPod.resetLocalImageLoaderForTests();
  }

  @Test
  void fromLocalImage_loadsImageBeforeSchedulingPod() {
    RecordingImageLoader loader = new RecordingImageLoader();
    GenericPod.setLocalImageLoaderForTests(loader);
    GenericPod pod =
        GenericPod.fromLocalImage("examples/order-service:test-current")
            .inCluster(new StubMinikubeCluster("testpods"));

    ((PodLifecycleHooks) pod).preStart();

    assertThat(loader.loads).containsExactly("testpods examples/order-service:test-current");
  }

  @Test
  void fromLocalImage_loadsSameImageOncePerCluster() {
    RecordingImageLoader loader = new RecordingImageLoader();
    GenericPod.setLocalImageLoaderForTests(loader);
    StubMinikubeCluster cluster = new StubMinikubeCluster("testpods");

    GenericPod.fromLocalImage("examples/order-service:test-current").inCluster(cluster).preStart();
    GenericPod.fromLocalImage("examples/order-service:test-current").inCluster(cluster).preStart();

    assertThat(loader.loads).containsExactly("testpods examples/order-service:test-current");
  }

  @Test
  void fromLocalImage_usesNeverPullPolicy() {
    GenericPod pod = GenericPod.fromLocalImage("examples/order-service:test-current");

    assertThat(pod.buildMainContainer().getImagePullPolicy()).isEqualTo("Never");
  }

  @Test
  void fromLocalImage_requiresMinikubeCluster() {
    GenericPod pod =
        GenericPod.fromLocalImage("examples/order-service:test-current")
            .inCluster(new StubCluster());

    assertThatThrownBy(pod::preStart)
        .isInstanceOf(ClusterException.class)
        .hasMessageContaining("requires a MinikubeCluster");
  }

  private static final class RecordingImageLoader implements GenericPod.LocalImageLoader {
    private final List<String> loads = new ArrayList<>();

    @Override
    public void load(String profileName, String imageTag) {
      loads.add(profileName + " " + imageTag);
    }
  }

  private static final class StubMinikubeCluster implements K8sCluster, MinikubeImageLoadTarget {
    private final String profileName;

    private StubMinikubeCluster(String profileName) {
      this.profileName = profileName;
    }

    @Override
    public String getProfileName() {
      return profileName;
    }

    @Override
    public io.fabric8.kubernetes.client.KubernetesClient getClient() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ExternalAccessStrategy getAccessStrategy() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Namespace getDefaultNamespace() {
      return null;
    }

    @Override
    public Namespace getNamespace(String name) {
      return null;
    }

    @Override
    public Namespace createNamespace(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Namespace createNamespace() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Namespace attachNamespace(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteNamespace(String name) {}

    @Override
    public K8sCluster withNamespace() {
      return this;
    }

    @Override
    public void close() {}
  }

  private static final class StubCluster implements K8sCluster {

    @Override
    public io.fabric8.kubernetes.client.KubernetesClient getClient() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ExternalAccessStrategy getAccessStrategy() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Namespace getDefaultNamespace() {
      return null;
    }

    @Override
    public Namespace getNamespace(String name) {
      return null;
    }

    @Override
    public Namespace createNamespace(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Namespace createNamespace() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Namespace attachNamespace(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteNamespace(String name) {}

    @Override
    public K8sCluster withNamespace() {
      return this;
    }

    @Override
    public void close() {}
  }
}
