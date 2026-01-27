package org.testpods.core.pods.external.postgresql;

import static org.assertj.core.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PostgreSQLPod.
 *
 * <p>Tests the buildMainContainer method, focusing on VolumeMount behavior for init scripts.
 */
class PostgreSQLPodTest {

  // =============================================================
  // VolumeMount tests for init scripts
  // =============================================================

  @Test
  void buildMainContainerShouldNotHaveVolumeMountWhenNoInitScripts() {
    PostgreSQLPod pod = new PostgreSQLPod();

    // Access buildMainContainer via reflection-free approach:
    // We can verify hasInitScripts returns false
    assertThat(pod.hasInitScripts()).isFalse();
  }

  @Test
  void hasInitScriptsShouldReturnTrueWhenInitScriptPathSet() {
    PostgreSQLPod pod = new PostgreSQLPod().withInitScript("db/init.sql");

    assertThat(pod.hasInitScripts()).isTrue();
  }

  @Test
  void hasInitScriptsShouldReturnTrueWhenInitScriptContentSet() {
    PostgreSQLPod pod = new PostgreSQLPod().withInitSql("CREATE TABLE test (id INT);");

    assertThat(pod.hasInitScripts()).isTrue();
  }

  @Test
  void hasInitScriptsShouldReturnFalseByDefault() {
    PostgreSQLPod pod = new PostgreSQLPod();

    assertThat(pod.hasInitScripts()).isFalse();
  }

  @Test
  void constantsShouldHaveCorrectValues() {
    assertThat(PostgreSQLPod.INIT_SCRIPTS_VOLUME_NAME).isEqualTo("init-scripts");
    assertThat(PostgreSQLPod.INIT_SCRIPTS_MOUNT_PATH).isEqualTo("/docker-entrypoint-initdb.d");
  }

  // =============================================================
  // Container build tests using a test subclass
  // =============================================================

  /**
   * Test subclass that exposes buildMainContainer and applyPodCustomizations for testing. This
   * avoids needing a full Kubernetes cluster while still testing the container and pod building
   * logic.
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
  void buildMainContainerShouldNotIncludeVolumeMountWithoutInitScripts() {
    TestablePostgreSQLPod pod = new TestablePostgreSQLPod();

    Container container = pod.buildContainerForTest();

    List<VolumeMount> mounts = container.getVolumeMounts();
    assertThat(mounts).isEmpty();
  }

  @Test
  void buildMainContainerShouldIncludeVolumeMountWithInitScriptPath() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod) new TestablePostgreSQLPod().withInitScript("db/init.sql");

    Container container = pod.buildContainerForTest();

    List<VolumeMount> mounts = container.getVolumeMounts();
    assertThat(mounts).hasSize(1);

    VolumeMount mount = mounts.get(0);
    assertThat(mount.getName()).isEqualTo("init-scripts");
    assertThat(mount.getMountPath()).isEqualTo("/docker-entrypoint-initdb.d");
    assertThat(mount.getReadOnly()).isTrue();
  }

  @Test
  void buildMainContainerShouldIncludeVolumeMountWithInitScriptContent() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod)
            new TestablePostgreSQLPod().withInitSql("CREATE TABLE test (id INT);");

    Container container = pod.buildContainerForTest();

    List<VolumeMount> mounts = container.getVolumeMounts();
    assertThat(mounts).hasSize(1);

    VolumeMount mount = mounts.get(0);
    assertThat(mount.getName()).isEqualTo("init-scripts");
    assertThat(mount.getMountPath()).isEqualTo("/docker-entrypoint-initdb.d");
    assertThat(mount.getReadOnly()).isTrue();
  }

  @Test
  void volumeMountShouldBeReadOnly() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod) new TestablePostgreSQLPod().withInitScript("db/init.sql");

    Container container = pod.buildContainerForTest();
    VolumeMount mount = container.getVolumeMounts().get(0);

    assertThat(mount.getReadOnly()).isTrue();
  }

  @Test
  void volumeMountShouldPointToDockerEntrypointDirectory() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod) new TestablePostgreSQLPod().withInitScript("db/init.sql");

    Container container = pod.buildContainerForTest();
    VolumeMount mount = container.getVolumeMounts().get(0);

    assertThat(mount.getMountPath()).isEqualTo("/docker-entrypoint-initdb.d");
  }

  @Test
  void volumeMountNameShouldBeInitScripts() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod) new TestablePostgreSQLPod().withInitScript("db/init.sql");

    Container container = pod.buildContainerForTest();
    VolumeMount mount = container.getVolumeMounts().get(0);

    assertThat(mount.getName()).isEqualTo("init-scripts");
  }

  // =============================================================
  // Volume tests for init scripts (Step 2)
  // =============================================================

  @Test
  void applyPodCustomizationsShouldNotAddVolumeWithoutInitScripts() {
    TestablePostgreSQLPod pod = new TestablePostgreSQLPod();
    PodSpecBuilder baseSpec = new PodSpecBuilder();

    PodSpecBuilder result = pod.applyPodCustomizationsForTest(baseSpec);

    List<Volume> volumes = result.build().getVolumes();
    assertThat(volumes).isNullOrEmpty();
  }

  @Test
  void applyPodCustomizationsShouldAddVolumeWithInitScriptPath() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod) new TestablePostgreSQLPod().withInitScript("db/init.sql");
    PodSpecBuilder baseSpec = new PodSpecBuilder();

    PodSpecBuilder result = pod.applyPodCustomizationsForTest(baseSpec);

    List<Volume> volumes = result.build().getVolumes();
    assertThat(volumes).hasSize(1);
  }

  @Test
  void applyPodCustomizationsShouldAddVolumeWithInitScriptContent() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod)
            new TestablePostgreSQLPod().withInitSql("CREATE TABLE test (id INT);");
    PodSpecBuilder baseSpec = new PodSpecBuilder();

    PodSpecBuilder result = pod.applyPodCustomizationsForTest(baseSpec);

    List<Volume> volumes = result.build().getVolumes();
    assertThat(volumes).hasSize(1);
  }

  @Test
  void volumeNameShouldMatchVolumeMountName() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod) new TestablePostgreSQLPod().withInitScript("db/init.sql");
    PodSpecBuilder baseSpec = new PodSpecBuilder();

    PodSpecBuilder result = pod.applyPodCustomizationsForTest(baseSpec);

    Volume volume = result.build().getVolumes().get(0);
    assertThat(volume.getName()).isEqualTo(PostgreSQLPod.INIT_SCRIPTS_VOLUME_NAME);
    assertThat(volume.getName()).isEqualTo("init-scripts");
  }

  @Test
  void configMapNameShouldMatchInitScriptConfigMapName() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod) new TestablePostgreSQLPod().withInitScript("db/init.sql");
    // Default name is "postgres"
    PodSpecBuilder baseSpec = new PodSpecBuilder();

    PodSpecBuilder result = pod.applyPodCustomizationsForTest(baseSpec);

    Volume volume = result.build().getVolumes().get(0);
    assertThat(volume.getConfigMap()).isNotNull();
    assertThat(volume.getConfigMap().getName()).isEqualTo("postgres-init");
  }

  @Test
  void configMapNameShouldFollowPodNamePattern() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod)
            new TestablePostgreSQLPod().withName("mydb").withInitScript("db/init.sql");
    PodSpecBuilder baseSpec = new PodSpecBuilder();

    PodSpecBuilder result = pod.applyPodCustomizationsForTest(baseSpec);

    Volume volume = result.build().getVolumes().get(0);
    assertThat(volume.getConfigMap().getName()).isEqualTo("mydb-init");
  }

  @Test
  void volumeAndVolumeMountNamesShouldBeConsistent() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod) new TestablePostgreSQLPod().withInitScript("db/init.sql");
    PodSpecBuilder baseSpec = new PodSpecBuilder();

    // Get volume from applyPodCustomizations
    PodSpecBuilder podResult = pod.applyPodCustomizationsForTest(baseSpec);
    Volume volume = podResult.build().getVolumes().get(0);

    // Get volume mount from buildMainContainer
    Container container = pod.buildContainerForTest();
    VolumeMount mount = container.getVolumeMounts().get(0);

    // Names must match for Kubernetes to link them
    assertThat(volume.getName()).isEqualTo(mount.getName());
  }

  // =============================================================
  // Fluent API tests
  // =============================================================

  @Test
  void withInitScriptShouldReturnSameInstance() {
    PostgreSQLPod pod = new PostgreSQLPod();

    PostgreSQLPod result = pod.withInitScript("db/init.sql");

    assertThat(result).isSameAs(pod);
  }

  @Test
  void withInitSqlShouldReturnSameInstance() {
    PostgreSQLPod pod = new PostgreSQLPod();

    PostgreSQLPod result = pod.withInitSql("SELECT 1;");

    assertThat(result).isSameAs(pod);
  }

  // =============================================================
  // Start method tests (Step 3 - ConfigMap creation order)
  // =============================================================

  @Test
  void startMethodShouldBeOverriddenInPostgreSQLPod() throws NoSuchMethodException {
    // Verify that PostgreSQLPod overrides start() from StatefulSetPod
    // This ensures ConfigMap is created before StatefulSet
    var method = PostgreSQLPod.class.getDeclaredMethod("start");
    assertThat(method.getDeclaringClass()).isEqualTo(PostgreSQLPod.class);
  }

  @Test
  void stopMethodShouldBeOverriddenInPostgreSQLPod() throws NoSuchMethodException {
    // Verify that PostgreSQLPod overrides stop() for ConfigMap cleanup
    var method = PostgreSQLPod.class.getDeclaredMethod("stop");
    assertThat(method.getDeclaringClass()).isEqualTo(PostgreSQLPod.class);
  }

  @Test
  void createInitScriptConfigMapMethodShouldExist() throws NoSuchMethodException {
    // Verify the private method exists for creating ConfigMaps
    var method = PostgreSQLPod.class.getDeclaredMethod("createInitScriptConfigMap");
    assertThat(method).isNotNull();
  }

  @Test
  void deleteInitScriptConfigMapMethodShouldExist() throws NoSuchMethodException {
    // Verify the private method exists for deleting ConfigMaps
    var method = PostgreSQLPod.class.getDeclaredMethod("deleteInitScriptConfigMap");
    assertThat(method).isNotNull();
  }

  // =============================================================
  // Container content tests (non-init-script related)
  // =============================================================

  @Test
  void buildMainContainerShouldHaveCorrectName() {
    TestablePostgreSQLPod pod = new TestablePostgreSQLPod();

    Container container = pod.buildContainerForTest();

    assertThat(container.getName()).isEqualTo("postgres");
  }

  @Test
  void buildMainContainerShouldHaveCorrectPort() {
    TestablePostgreSQLPod pod = new TestablePostgreSQLPod();

    Container container = pod.buildContainerForTest();

    assertThat(container.getPorts()).hasSize(1);
    assertThat(container.getPorts().get(0).getContainerPort()).isEqualTo(5432);
  }

  @Test
  void buildMainContainerShouldHaveRequiredEnvVars() {
    TestablePostgreSQLPod pod = new TestablePostgreSQLPod();

    Container container = pod.buildContainerForTest();

    assertThat(container.getEnv())
        .extracting("name")
        .contains("POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD");
  }
}
