package org.testpods.core.pods.external.postgresql;

import static org.assertj.core.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.wait.WaitStrategy;

/**
 * Integration and unit tests for PostgreSQLPod init script functionality.
 *
 * <p>Tests verify:
 *
 * <ul>
 *   <li>Init scripts execute on PostgreSQL startup
 *   <li>Volume mount is correctly configured in pod spec
 *   <li>No volume/mount when init scripts are absent
 * </ul>
 */
class PostgreSQLPodInitScriptTest {

  // =============================================================
  // Integration Tests - Init Script Execution
  // =============================================================

  /**
   * Integration test that verifies init scripts actually execute on PostgreSQL startup.
   *
   * <p>This test:
   *
   * <ol>
   *   <li>Creates a PostgreSQL pod with an init script that creates a table
   *   <li>Starts the pod and waits for it to be ready (using log-based wait strategy)
   *   <li>Connects to the database and verifies the table was created
   *   <li>Cleans up the pod
   * </ol>
   *
   * <p>Note: Uses a log-based wait strategy followed by manual JDBC connection because the default
   * PostgreSQLWaitStrategy has a timing issue where it tries to access the JDBC URL before the
   * external access endpoint is configured.
   *
   * <p>This test requires a stable Kubernetes environment with working port forwarding. It may be
   * flaky in CI environments. Run manually with: {@code mvn test
   * -Dtest=PostgreSQLPodInitScriptTest#shouldExecuteInitScripts}
   */
  @Disabled(
      "Requires stable Kubernetes port forwarding - run manually to verify init script execution")
  @Test
  void shouldExecuteInitScripts() throws SQLException, IOException, InterruptedException {
    PostgreSQLPod postgres =
        new PostgreSQLPod()
            .withName("test-init")
            .withDatabase("testdb")
            .withUsername("testuser")
            .withPassword("testpass")
            .withInitSql("CREATE TABLE test_table (id INT PRIMARY KEY);")
            // Disable SSL for test connections (PostgreSQL image doesn't have SSL configured)
            .withUrlParam("sslmode", "disable")
            // Use log-based wait strategy to avoid timing issue in PostgreSQLWaitStrategy
            .waitingFor(
                WaitStrategy.forLogMessage(".*database system is ready to accept connections.*", 2)
                    .withTimeout(Duration.ofMinutes(2)));

    try {
      postgres.start();

      // Small delay to ensure PostgreSQL is fully ready for connections after log message
      Thread.sleep(1000);

      // Verify table was created by init script using JDBC
      try (Connection conn =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        // Query database metadata to check if table exists
        ResultSet tables =
            conn.getMetaData().getTables(null, null, "test_table", new String[] {"TABLE"});
        assertThat(tables.next()).as("Init script should have created test_table").isTrue();
      }
    } finally {
      postgres.stop();
      cleanupResources(postgres);
    }
  }

  /**
   * Integration test that verifies init scripts from classpath resources execute.
   *
   * <p>This test uses a SQL file from src/test/resources/db/init.sql.
   *
   * <p>This test requires a stable Kubernetes environment with working port forwarding. It may be
   * flaky in CI environments. Run manually with: {@code mvn test
   * -Dtest=PostgreSQLPodInitScriptTest#shouldExecuteInitScriptsFromClasspath}
   */
  @Disabled(
      "Requires stable Kubernetes port forwarding - run manually to verify init script execution")
  @Test
  void shouldExecuteInitScriptsFromClasspath()
      throws SQLException, IOException, InterruptedException {
    PostgreSQLPod postgres =
        new PostgreSQLPod()
            .withName("test-init-classpath")
            .withDatabase("testdb")
            .withUsername("testuser")
            .withPassword("testpass")
            .withInitScript("db/init.sql")
            // Disable SSL for test connections (PostgreSQL image doesn't have SSL configured)
            .withUrlParam("sslmode", "disable")
            // Use log-based wait strategy to avoid timing issue in PostgreSQLWaitStrategy
            .waitingFor(
                WaitStrategy.forLogMessage(".*database system is ready to accept connections.*", 2)
                    .withTimeout(Duration.ofMinutes(2)));

    try {
      postgres.start();

      // Small delay to ensure PostgreSQL is fully ready for connections after log message
      Thread.sleep(1000);

      // Verify table was created by init script
      try (Connection conn =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        // Query database metadata to check if table exists
        ResultSet tables =
            conn.getMetaData().getTables(null, null, "test_table", new String[] {"TABLE"});
        assertThat(tables.next())
            .as("Init script from classpath should have created test_table")
            .isTrue();

        // Also verify the data was inserted
        ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM test_table");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).as("Init script should have inserted data").isGreaterThan(0);
      }
    } finally {
      postgres.stop();
      cleanupResources(postgres);
    }
  }

  // =============================================================
  // Unit Tests - Volume Mount Configuration
  // =============================================================

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

  // =============================================================
  // Helper Methods
  // =============================================================

  /** Clean up namespace and cluster resources after tests. */
  private void cleanupResources(PostgreSQLPod postgres) throws IOException {
    if (postgres.getNamespace() != null) {
      postgres.getNamespace().close();
      K8sCluster cluster = postgres.getNamespace().getCluster();
      if (cluster != null) {
        cluster.close();
      }
    }
  }
}
