package org.testpods.core.pods.external.postgresql;

import static org.assertj.core.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testpods.core.cluster.HostAndPort;
import org.testpods.core.cluster.Namespace;
import org.testpods.core.pods.PodLifecycleHooks;

/**
 * Unit tests for PostgreSQLPod.
 *
 * <p>Tests the buildMainContainer method, focusing on VolumeMount behavior for init scripts.
 */
class PostgresSQLPodTest {

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
  void hasInitScriptsShouldReturnTrueWhenAdditionalDatabasesSet() {
    PostgreSQLPod pod = new PostgreSQLPod().withAdditionalDatabases("orders", "inventory");

    assertThat(pod.hasInitScripts()).isTrue();
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

    void setExternalAccessForTest(String host, int port) {
      this.externalAccess = new HostAndPort(host, port);
    }

    List<String> customDeploymentDetailLinesForTest() {
      return buildCustomDeploymentDetailLines();
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
  void buildMainContainerShouldIncludeVolumeMountWithAdditionalDatabases() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod) new TestablePostgreSQLPod().withAdditionalDatabase("inventory");

    Container container = pod.buildContainerForTest();

    assertThat(container.getVolumeMounts())
        .extracting(VolumeMount::getName)
        .contains(PostgreSQLPod.INIT_SCRIPTS_VOLUME_NAME);
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

  @Test
  void withDatabasesShouldSetPrimaryAndAdditionalDatabases() {
    PostgreSQLPod pod = new PostgreSQLPod().withDatabases("orders", "inventory", "billing");

    assertThat(pod.getDatabaseName()).isEqualTo("orders");
    assertThat(pod.getDatabaseNames()).containsExactly("orders", "inventory", "billing");
    assertThat(pod.getAdditionalDatabaseNames()).containsExactly("inventory", "billing");
  }

  @Test
  void withAdditionalDatabasesShouldNotDuplicatePrimaryDatabase() {
    PostgreSQLPod pod =
        new PostgreSQLPod().withDatabase("orders").withAdditionalDatabases("orders", "inventory");

    assertThat(pod.getDatabaseNames()).containsExactly("orders", "inventory");
  }

  @Test
  void buildInitSqlShouldCreateAdditionalDatabasesBeforeUserSql() {
    PostgreSQLPod pod =
        new PostgreSQLPod()
            .withDatabases("orders", "inventory", "billing-db")
            .withInitSql("CREATE TABLE orders_table (id INT);");

    String sql = pod.buildInitSql();

    assertThat(sql)
        .contains("CREATE DATABASE \"inventory\";")
        .contains("CREATE DATABASE \"billing-db\";")
        .contains("CREATE TABLE orders_table (id INT);");
    assertThat(sql.indexOf("CREATE DATABASE \"inventory\";"))
        .isLessThan(sql.indexOf("CREATE TABLE orders_table (id INT);"));
  }

  @Test
  void buildInitSqlShouldEscapeDatabaseIdentifiers() {
    PostgreSQLPod pod = new PostgreSQLPod().withAdditionalDatabase("team\"db");

    assertThat(pod.buildInitSql()).contains("CREATE DATABASE \"team\"\"db\";");
  }

  @Test
  void buildInitSqlShouldReturnNullWithoutAdditionalDatabasesOrUserSql() {
    PostgreSQLPod pod = new PostgreSQLPod();

    assertThat(pod.buildInitSql()).isNull();
  }

  @Test
  void getJdbcUrlShouldSupportSpecificDatabaseNames() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod)
            new TestablePostgreSQLPod()
                .withDatabases("orders", "inventory")
                .withUrlParam("sslmode", "disable");
    pod.setExternalAccessForTest("127.0.0.1", 30432);

    assertThat(pod.getJdbcUrl()).isEqualTo("jdbc:postgresql://127.0.0.1:30432/orders?sslmode=disable");
    assertThat(pod.getJdbcUrl("inventory"))
        .isEqualTo("jdbc:postgresql://127.0.0.1:30432/inventory?sslmode=disable");
  }

  @Test
  void getPostgreSqlUriShouldSupportSpecificDatabaseNames() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod)
            new TestablePostgreSQLPod()
                .withDatabases("orders", "inventory")
                .withUsername("app_user")
                .withPassword("app_pass")
                .withUrlParam("sslmode", "disable");
    pod.setExternalAccessForTest("127.0.0.1", 30432);

    assertThat(pod.getPostgreSqlUri())
        .isEqualTo("postgresql://app_user:app_pass@127.0.0.1:30432/orders?sslmode=disable");
    assertThat(pod.getPostgreSqlUri("inventory"))
        .isEqualTo("postgresql://app_user:app_pass@127.0.0.1:30432/inventory?sslmode=disable");
  }

  @Test
  void customDeploymentDetailsShouldIncludePostgreSQLConnectionDetails() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod)
            new TestablePostgreSQLPod()
                .withDatabases("orders", "inventory")
                .withUsername("app_user")
                .withPassword("app_pass")
                .withPersistentData("2Gi");
    pod.setExternalAccessForTest("127.0.0.1", 30432);
    pod.inNamespace(Namespace.external("test-ns", null));

    assertThat(pod.customDeploymentDetailLinesForTest())
        .contains(
            "postgresql.databases: [orders, inventory]",
            "postgresql.username: app_user",
            "postgresql.jdbcUrl: jdbc:postgresql://127.0.0.1:30432/orders",
            "postgresql.uri: postgresql://app_user:app_pass@127.0.0.1:30432/orders",
            "postgresql.internalJdbcUrl: jdbc:postgresql://postgres.test-ns.svc.cluster.local:5432/orders",
            "postgresql.persistentData: true",
            "postgresql.storageSize: 2Gi",
            "postgresql.pgdata: /var/lib/postgresql/data/pgdata");
  }

  // =============================================================
  // Start method tests (Step 3 - ConfigMap creation order)
  // =============================================================

  @Test
  void postgreSQLPodShouldUseLifecycleHooksForInitScripts() {
    assertThat(PodLifecycleHooks.class).isAssignableFrom(PostgreSQLPod.class);
  }

  @Test
  void persistentDataShouldBeDisabledByDefault() {
    PostgreSQLPod pod = new PostgreSQLPod();

    assertThat(pod.isPersistentDataEnabled()).isFalse();
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

  @Test
  void buildMainContainerShouldUseDirectPostgreSQLProbeCommand() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod)
            new TestablePostgreSQLPod()
                .withDatabase("orders")
                .withUsername("orders_user");

    Container container = pod.buildContainerForTest();

    assertThat(container.getReadinessProbe().getExec().getCommand())
        .containsExactly("pg_isready", "-U", "orders_user", "-d", "orders");
    assertThat(container.getLivenessProbe().getExec().getCommand())
        .containsExactly("pg_isready", "-U", "orders_user", "-d", "orders");
  }

  @Test
  void withPersistentDataShouldMountPostgreSQLDataVolume() {
    TestablePostgreSQLPod pod =
        (TestablePostgreSQLPod) new TestablePostgreSQLPod().withPersistentData("2Gi");

    Container container = pod.buildContainerForTest();

    assertThat(pod.isPersistentDataEnabled()).isTrue();
    assertThat(pod.getStorageSize()).isEqualTo("2Gi");
    assertThat(container.getVolumeMounts())
        .extracting(VolumeMount::getName)
        .contains(PostgreSQLPod.DATA_VOLUME_NAME);
    assertThat(container.getEnv())
        .anySatisfy(
            env -> {
              assertThat(env.getName()).isEqualTo("PGDATA");
              assertThat(env.getValue()).isEqualTo("/var/lib/postgresql/data/pgdata");
            });
  }
}
