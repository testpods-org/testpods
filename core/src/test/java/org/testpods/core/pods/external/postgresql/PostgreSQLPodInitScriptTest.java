package org.testpods.core.pods.external.postgresql;

import static org.assertj.core.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for PostgreSQLPod init script volume and mount configuration. */
class PostgreSQLPodInitScriptTest {

  /**
   * Test subclass that exposes buildMainContainer and applyPodCustomizations for testing without
   * needing a Kubernetes cluster.
   */
  static class TestablePostgreSQLPod extends PostgreSQLPod {
    Container buildContainerForTest() {
      return buildMainContainer();
    }

    PodSpecBuilder applyPodCustomizationsForTest(PodSpecBuilder baseSpec) {
      return applyPodCustomizations(baseSpec);
    }
  }

  @Test
  void shouldMountInitScriptsVolume() {
    TestablePostgreSQLPod postgres =
        (TestablePostgreSQLPod)
            new TestablePostgreSQLPod().withName("test-mount").withInitSql("SELECT 1;");

    PodSpecBuilder baseSpec = new PodSpecBuilder();
    PodSpecBuilder podSpec = postgres.applyPodCustomizationsForTest(baseSpec);

    // Verify volume exists
    List<Volume> volumes = podSpec.build().getVolumes();
    assertThat(volumes).isNotEmpty();
    assertThat(volumes).extracting(Volume::getName).contains("init-scripts");

    // Verify volume references ConfigMap
    Volume initScriptsVolume =
        volumes.stream().filter(v -> "init-scripts".equals(v.getName())).findFirst().orElseThrow();
    assertThat(initScriptsVolume.getConfigMap()).isNotNull();
    assertThat(initScriptsVolume.getConfigMap().getName()).isEqualTo("test-mount-init");

    // Verify volume mount in container
    Container mainContainer = postgres.buildContainerForTest();
    List<VolumeMount> mounts = mainContainer.getVolumeMounts();
    assertThat(mounts).isNotEmpty();

    VolumeMount initScriptsMount =
        mounts.stream().filter(m -> "init-scripts".equals(m.getName())).findFirst().orElseThrow();
    assertThat(initScriptsMount.getMountPath()).isEqualTo("/docker-entrypoint-initdb.d");
    assertThat(initScriptsMount.getReadOnly()).isTrue();
  }

  @Test
  void shouldNotMountVolumeWithoutInitScripts() {
    TestablePostgreSQLPod postgres =
        (TestablePostgreSQLPod) new TestablePostgreSQLPod().withName("test-no-init");

    PodSpecBuilder baseSpec = new PodSpecBuilder();
    PodSpecBuilder podSpec = postgres.applyPodCustomizationsForTest(baseSpec);

    // Verify no init-scripts volume
    List<Volume> volumes = podSpec.build().getVolumes();
    if (volumes != null) {
      assertThat(volumes).extracting(Volume::getName).doesNotContain("init-scripts");
    }

    // Verify no init-scripts volume mount in container
    Container mainContainer = postgres.buildContainerForTest();
    List<VolumeMount> mounts = mainContainer.getVolumeMounts();
    if (mounts != null) {
      assertThat(mounts).extracting(VolumeMount::getName).doesNotContain("init-scripts");
    }
  }

  @Test
  void volumeMountPathShouldBeCorrect() {
    TestablePostgreSQLPod postgres =
        (TestablePostgreSQLPod)
            new TestablePostgreSQLPod().withName("test-path").withInitSql("SELECT 1;");

    Container container = postgres.buildContainerForTest();
    VolumeMount mount = container.getVolumeMounts().get(0);

    assertThat(mount.getMountPath())
        .as("Mount path should be PostgreSQL init script directory")
        .isEqualTo("/docker-entrypoint-initdb.d");
  }

  @Test
  void configMapNameShouldMatchPodNamePattern() {
    TestablePostgreSQLPod postgres =
        (TestablePostgreSQLPod)
            new TestablePostgreSQLPod().withName("my-postgres").withInitSql("SELECT 1;");

    PodSpecBuilder baseSpec = new PodSpecBuilder();
    PodSpecBuilder podSpec = postgres.applyPodCustomizationsForTest(baseSpec);

    Volume volume = podSpec.build().getVolumes().get(0);
    assertThat(volume.getConfigMap().getName())
        .as("ConfigMap name should follow {podName}-init pattern")
        .isEqualTo("my-postgres-init");
  }

  @Test
  void volumeNameShouldMatchVolumeMountName() {
    TestablePostgreSQLPod postgres =
        (TestablePostgreSQLPod)
            new TestablePostgreSQLPod().withName("test-consistency").withInitSql("SELECT 1;");

    // Get volume from pod spec
    PodSpecBuilder baseSpec = new PodSpecBuilder();
    PodSpecBuilder podSpec = postgres.applyPodCustomizationsForTest(baseSpec);
    String volumeName = podSpec.build().getVolumes().get(0).getName();

    // Get volume mount from container
    Container container = postgres.buildContainerForTest();
    String mountName = container.getVolumeMounts().get(0).getName();

    assertThat(volumeName)
        .as("Volume name must match VolumeMount name for Kubernetes linking")
        .isEqualTo(mountName);
  }

}
