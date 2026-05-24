package org.testpods.core.pods.external.postgresql;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.testpods.core.PropertyContext;
import org.testpods.core.pods.PodLifecycleHooks;
import org.testpods.core.pods.StatefulSetPod;
import org.testpods.core.service.ClusterIPServiceManager;
import org.testpods.core.service.CompositeServiceManager;
import org.testpods.core.service.HeadlessServiceManager;
import org.testpods.core.service.ServiceManager;
import org.testpods.core.wait.WaitStrategy;
import org.testpods.core.workload.StatefulSetManager;
import org.testpods.core.workload.WorkloadManager;

/**
 * A PostgreSQL database pod for integration testing.
 *
 * <p>Provides a fully configured PostgreSQL instance running in Kubernetes with automatic lifecycle
 * management through the TestPods JUnit extension.
 *
 * <h2>Basic Usage</h2>
 *
 * <pre>{@code
 * @TestPods
 * class MyDatabaseTest {
 *
 *     @Pod
 *     static PostgreSQLPod postgres = new PostgreSQLPod()
 *         .withDatabase("myapp")
 *         .withUsername("testuser")
 *         .withPassword("testpass");
 *
 *     @DynamicPropertySource
 *     static void configureProperties(DynamicPropertyRegistry registry) {
 *         registry.add("spring.datasource.url", postgres::getJdbcUrl);
 *         registry.add("spring.datasource.username", postgres::getUsername);
 *         registry.add("spring.datasource.password", postgres::getPassword);
 *     }
 *
 *     @Test
 *     void shouldConnectToDatabase() {
 *         try (Connection conn = DriverManager.getConnection(
 *                 postgres.getJdbcUrl(),
 *                 postgres.getUsername(),
 *                 postgres.getPassword())) {
 *             // Test database operations
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>With Initialization Script</h2>
 *
 * <pre>{@code
 * @Pod
 * static PostgreSQLPod postgres = new PostgreSQLPod()
 *     .withDatabase("orders")
 *     .withInitScript("db/init.sql");  // Loaded from classpath
 * }</pre>
 *
 * @see StatefulSetPod
 */
@lombok.extern.slf4j.Slf4j
public class PostgreSQLPod extends StatefulSetPod<PostgreSQLPod> implements PodLifecycleHooks {

  // === Constants ===

  public static final String DEFAULT_IMAGE = "postgres:16-alpine";
  public static final int POSTGRESQL_PORT = 5432;
  public static final String DEFAULT_DATABASE = "test";
  public static final String DEFAULT_USERNAME = "test";
  public static final String DEFAULT_PASSWORD = "test";
  public static final String DATA_VOLUME_NAME = "data";
  public static final String DATA_MOUNT_PATH = "/var/lib/postgresql/data";
  public static final String DEFAULT_STORAGE_SIZE = "1Gi";

  // === Configuration ===

  private String image = DEFAULT_IMAGE;
  private String databaseName = DEFAULT_DATABASE;
  private final Set<String> additionalDatabases = new LinkedHashSet<>();
  private String username = DEFAULT_USERNAME;
  private String password = DEFAULT_PASSWORD;
  private final Map<String, String> urlParameters = new LinkedHashMap<>();
  private String initScriptPath;
  private String initScriptContent;
  private boolean persistentData;
  private String storageSize = DEFAULT_STORAGE_SIZE;

  // === Constructors ===

  /** Create a PostgreSQL pod with the default image (postgres:16-alpine). */
  public PostgreSQLPod() {
    this(DEFAULT_IMAGE);
  }

  /**
   * Create a PostgreSQL pod with a specific image.
   *
   * @param image PostgreSQL image (e.g., "postgres:15-alpine", "postgres:14")
   */
  public PostgreSQLPod(String image) {
    this.image = image;
    this.name = "postgres";
  }

  // === Configuration Fluent API ===

  /**
   * Set the PostgreSQL image version.
   *
   * @param version version tag (e.g., "15", "16-alpine")
   * @return this pod for chaining
   */
  public PostgreSQLPod withVersion(String version) {
    this.image = "postgres:" + version;
    return this;
  }

  /**
   * Set the database name.
   *
   * @param databaseName name of the database to create
   * @return this pod for chaining
   */
  public PostgreSQLPod withDatabase(String databaseName) {
    this.databaseName = databaseName;
    this.additionalDatabases.remove(databaseName);
    return this;
  }

  /**
   * Set the primary database and additional databases to create during PostgreSQL initialization.
   *
   * <p>The first database is created by the official PostgreSQL image via {@code POSTGRES_DB}.
   * Additional databases are created by a generated init SQL script.
   *
   * @param databaseName primary database name
   * @param additionalDatabaseNames extra databases to create
   * @return this pod for chaining
   */
  public PostgreSQLPod withDatabases(String databaseName, String... additionalDatabaseNames) {
    this.databaseName = databaseName;
    this.additionalDatabases.clear();
    return withAdditionalDatabases(additionalDatabaseNames);
  }

  /**
   * Add another database to create during PostgreSQL initialization.
   *
   * @param databaseName additional database name
   * @return this pod for chaining
   */
  public PostgreSQLPod withAdditionalDatabase(String databaseName) {
    if (!databaseName.equals(this.databaseName)) {
      this.additionalDatabases.add(databaseName);
    }
    return this;
  }

  /**
   * Add databases to create during PostgreSQL initialization.
   *
   * @param databaseNames additional database names
   * @return this pod for chaining
   */
  public PostgreSQLPod withAdditionalDatabases(String... databaseNames) {
    for (String databaseName : databaseNames) {
      withAdditionalDatabase(databaseName);
    }
    return this;
  }

  /**
   * Set the database username.
   *
   * @param username PostgreSQL username
   * @return this pod for chaining
   */
  public PostgreSQLPod withUsername(String username) {
    this.username = username;
    return this;
  }

  /**
   * Set the database password.
   *
   * @param password PostgreSQL password
   * @return this pod for chaining
   */
  public PostgreSQLPod withPassword(String password) {
    this.password = password;
    return this;
  }

  /**
   * Add a JDBC URL parameter.
   *
   * @param key parameter name
   * @param value parameter value
   * @return this pod for chaining
   */
  public PostgreSQLPod withUrlParam(String key, String value) {
    this.urlParameters.put(key, value);
    return this;
  }

  /**
   * Set an initialization SQL script from the classpath.
   *
   * <p>The script will be executed once when the database starts.
   *
   * @param classpathResource path to SQL file on classpath (e.g., "db/init.sql")
   * @return this pod for chaining
   */
  public PostgreSQLPod withInitScript(String classpathResource) {
    this.initScriptPath = classpathResource;
    return this;
  }

  /**
   * Set initialization SQL content directly.
   *
   * @param sql SQL statements to execute on startup
   * @return this pod for chaining
   */
  public PostgreSQLPod withInitSql(String sql) {
    this.initScriptContent = sql;
    return this;
  }

  /** Enable persistent PostgreSQL data using a StatefulSet volumeClaimTemplate. */
  public PostgreSQLPod withPersistentData() {
    this.persistentData = true;
    return this;
  }

  /**
   * Enable persistent PostgreSQL data and request the given storage size.
   *
   * @param storageSize Kubernetes quantity, e.g. "512Mi", "1Gi", "10Gi"
   * @return this pod for chaining
   */
  public PostgreSQLPod withPersistentData(String storageSize) {
    this.persistentData = true;
    this.storageSize = storageSize;
    return this;
  }

  /** Disable persistent PostgreSQL data. Data will live only for the pod lifetime. */
  public PostgreSQLPod withoutPersistentData() {
    this.persistentData = false;
    return this;
  }

  // === Connection Information ===

  /**
   * Get the JDBC connection URL for external access (from test code).
   *
   * @return JDBC URL like "jdbc:postgresql://host:port/database"
   */
  public String getJdbcUrl() {
    return getJdbcUrl(databaseName);
  }

  /**
   * Get the JDBC connection URL for a specific database.
   *
   * @param databaseName database name
   * @return JDBC URL like "jdbc:postgresql://host:port/database"
   */
  public String getJdbcUrl(String databaseName) {
    return "jdbc:postgresql://"
        + getExternalHost()
        + ":"
        + getExternalPort()
        + "/"
        + databaseName
        + constructUrlParameters();
  }

  /**
   * Get the internal JDBC URL for pod-to-pod communication.
   *
   * @return JDBC URL using Kubernetes service DNS
   */
  public String getInternalJdbcUrl() {
    return getInternalJdbcUrl(databaseName);
  }

  /**
   * Get the internal JDBC URL for a specific database.
   *
   * @param databaseName database name
   * @return JDBC URL using Kubernetes service DNS
   */
  public String getInternalJdbcUrl(String databaseName) {
    return "jdbc:postgresql://"
        + getInternalHost()
        + ":"
        + POSTGRESQL_PORT
        + "/"
        + databaseName
        + constructUrlParameters();
  }

  /**
   * Get the R2DBC connection URL for reactive access.
   *
   * @return R2DBC URL like "r2dbc:postgresql://host:port/database"
   */
  public String getR2dbcUrl() {
    return "r2dbc:postgresql://" + getExternalHost() + ":" + getExternalPort() + "/" + databaseName;
  }

  /**
   * Get the R2DBC connection URL for a specific database.
   *
   * @param databaseName database name
   * @return R2DBC URL like "r2dbc:postgresql://host:port/database"
   */
  public String getR2dbcUrl(String databaseName) {
    return "r2dbc:postgresql://" + getExternalHost() + ":" + getExternalPort() + "/" + databaseName;
  }

  /**
   * Get a standard PostgreSQL connection URI for external access.
   *
   * @return URI like "postgresql://user:password@host:port/database"
   */
  public String getPostgreSqlUri() {
    return getPostgreSqlUri(databaseName);
  }

  /**
   * Get a standard PostgreSQL connection URI for a specific database.
   *
   * @param databaseName database name
   * @return URI like "postgresql://user:password@host:port/database"
   */
  public String getPostgreSqlUri(String databaseName) {
    return "postgresql://"
        + username
        + ":"
        + password
        + "@"
        + getExternalHost()
        + ":"
        + getExternalPort()
        + "/"
        + databaseName
        + constructUrlParameters();
  }

  /** Get the database name. */
  public String getDatabaseName() {
    return databaseName;
  }

  /** Get all databases configured for creation, with the primary database first. */
  public List<String> getDatabaseNames() {
    List<String> databases = new ArrayList<>();
    databases.add(databaseName);
    databases.addAll(additionalDatabases);
    return Collections.unmodifiableList(databases);
  }

  /** Get additional databases configured beyond the primary database. */
  public List<String> getAdditionalDatabaseNames() {
    return List.copyOf(additionalDatabases);
  }

  /** Get the database username. */
  public String getUsername() {
    return username;
  }

  /** Get the database password. */
  public String getPassword() {
    return password;
  }

  /** Get the JDBC driver class name. */
  public String getDriverClassName() {
    return "org.postgresql.Driver";
  }

  public String getImage() {
    return image;
  }

  public boolean isPersistentDataEnabled() {
    return persistentData;
  }

  public String getStorageSize() {
    return storageSize;
  }

  @Override
  public int getInternalPort() {
    return POSTGRESQL_PORT;
  }

  private String constructUrlParameters() {
    if (urlParameters.isEmpty()) {
      return "";
    }
    StringJoiner joiner = new StringJoiner("&", "?", "");
    urlParameters.forEach((k, v) -> joiner.add(k + "=" + v));
    return joiner.toString();
  }

  // === Property Publishing ===

  @Override
  public void publishProperties(PropertyContext ctx) {
    String prefix = getName();

    // External (for test code)
    ctx.publish(prefix + ".host", this::getExternalHost);
    ctx.publish(prefix + ".port", () -> String.valueOf(getExternalPort()));
    ctx.publish(prefix + ".uri", this::getJdbcUrl);
    ctx.publish(prefix + ".jdbcUrl", this::getJdbcUrl);
    ctx.publish(prefix + ".r2dbcUrl", this::getR2dbcUrl);
    ctx.publish(prefix + ".postgresqlUri", this::getPostgreSqlUri);
    ctx.publish(prefix + ".username", this::getUsername);
    ctx.publish(prefix + ".password", this::getPassword);
    ctx.publish(prefix + ".database", this::getDatabaseName);
    ctx.publish(prefix + ".databases", () -> String.join(",", getDatabaseNames()));

    // Internal (for other pods in cluster)
    ctx.publish(prefix + ".internal.host", this::getInternalHost);
    ctx.publish(prefix + ".internal.port", () -> String.valueOf(POSTGRESQL_PORT));
    ctx.publish(prefix + ".internal.uri", this::getInternalJdbcUrl);
  }

  // === Wait Strategy ===

  @Override
  protected WaitStrategy getDefaultWaitStrategy() {
    return new PostgreSQLWaitStrategy().withTimeout(Duration.ofMinutes(2));
  }

  // === StatefulSet Building ===

  /** Volume name for init scripts ConfigMap mount. */
  static final String INIT_SCRIPTS_VOLUME_NAME = "init-scripts";

  /** Mount path for PostgreSQL Docker image init scripts. */
  static final String INIT_SCRIPTS_MOUNT_PATH = "/docker-entrypoint-initdb.d";

  @Override
  protected Container buildMainContainer() {
    ContainerBuilder builder =
        new ContainerBuilder()
            .withName("postgres")
            .withImage(image)
            .withImagePullPolicy("IfNotPresent")
            .addNewPort()
            .withContainerPort(POSTGRESQL_PORT)
            .withName("postgres")
            .endPort()
            .addNewEnv()
            .withName("POSTGRES_DB")
            .withValue(databaseName)
            .endEnv()
            .addNewEnv()
            .withName("POSTGRES_USER")
            .withValue(username)
            .endEnv()
            .addNewEnv()
            .withName("POSTGRES_PASSWORD")
            .withValue(password)
            .endEnv()
            .addNewEnv()
            .withName("PGDATA")
            .withValue(DATA_MOUNT_PATH + "/pgdata")
            .endEnv()
            // Performance: disable fsync for tests
            .withArgs("-c", "fsync=off", "-c", "synchronous_commit=off")
            .withNewReadinessProbe()
            .withNewExec()
            .withCommand("pg_isready", "-U", username, "-d", databaseName)
            .endExec()
            .withInitialDelaySeconds(5)
            .withPeriodSeconds(5)
            .withTimeoutSeconds(3)
            .endReadinessProbe()
            .withNewLivenessProbe()
            .withNewExec()
            .withCommand("pg_isready", "-U", username, "-d", databaseName)
            .endExec()
            .withInitialDelaySeconds(30)
            .withPeriodSeconds(10)
            .withTimeoutSeconds(5)
            .endLivenessProbe();

    if (persistentData) {
      builder
          .addNewVolumeMount()
          .withName(DATA_VOLUME_NAME)
          .withMountPath(DATA_MOUNT_PATH)
          .endVolumeMount();
    }

    // Add init scripts volume mount if configured
    if (hasInitScripts()) {
      builder
          .addNewVolumeMount()
          .withName(INIT_SCRIPTS_VOLUME_NAME)
          .withMountPath(INIT_SCRIPTS_MOUNT_PATH)
          .withReadOnly(true)
          .endVolumeMount();
    }

    return builder.build();
  }

  /**
   * Check if init scripts are configured.
   *
   * @return true if either initScriptPath or initScriptContent is set
   */
  boolean hasInitScripts() {
    return initScriptPath != null || initScriptContent != null || !additionalDatabases.isEmpty();
  }

  @Override
  protected WorkloadManager createWorkloadManager() {
    StatefulSetManager manager = new StatefulSetManager().withServiceName(name + "-headless");
    if (persistentData) {
      PersistentVolumeClaim pvc =
          new PersistentVolumeClaimBuilder()
              .withNewMetadata()
              .withName(DATA_VOLUME_NAME)
              .endMetadata()
              .withNewSpec()
              .withAccessModes("ReadWriteOnce")
              .withNewResources()
              .addToRequests("storage", new Quantity(storageSize))
              .endResources()
              .endSpec()
              .build();
      manager.withPvcTemplates(List.of(pvc));
    }
    return manager;
  }

  @Override
  protected ServiceManager createServiceManager() {
    return new CompositeServiceManager(new ClusterIPServiceManager(), new HeadlessServiceManager())
        .withSuffixes("", "-headless");
  }

  @Override
  protected PodSpecBuilder applyPodCustomizations(PodSpecBuilder baseSpec) {
    baseSpec = super.applyPodCustomizations(baseSpec);

    // Add init scripts volume if configured
    if (hasInitScripts()) {
      baseSpec.addToVolumes(
          new VolumeBuilder()
              .withName(INIT_SCRIPTS_VOLUME_NAME)
              .withNewConfigMap()
              .withName(name + "-init")
              .endConfigMap()
              .build());
    }

    return baseSpec;
  }

  // =============================================================
  // Lifecycle hooks
  // =============================================================

  @Override
  public void preStart() {
    preloadExternalImageForMinikube(image);
    if (hasInitScripts()) {
      createInitScriptConfigMap();
    }
  }

  @Override
  public void postStart() {
    log.info("PostgreSQL TestPod '{}' is ready", name);
    log.info("PostgreSQL image: {}", image);
    log.info("PostgreSQL namespace: {}", namespace.getName());
    log.info("PostgreSQL databases: {}", getDatabaseNames());
    log.info("PostgreSQL username: {}", username);
    log.info("PostgreSQL internal JDBC URL: {}", getInternalJdbcUrl());
    log.info("PostgreSQL external JDBC URL: {}", getJdbcUrl());
    log.info("PostgreSQL external URI: {}", getPostgreSqlUri());
    log.info("PostgreSQL external host/port: {}:{}", getExternalHost(), getExternalPort());
    log.info(
        "PostgreSQL persistent data: {}{}",
        persistentData ? "enabled" : "disabled",
        persistentData ? " (" + storageSize + " PVC)" : "");
  }

  @Override
  protected List<String> buildCustomDeploymentDetailLines() {
    List<String> lines = new ArrayList<>();
    lines.add("postgresql.databases: " + getDatabaseNames());
    lines.add("postgresql.username: " + username);
    lines.add("postgresql.jdbcUrl: " + getJdbcUrl());
    lines.add("postgresql.uri: " + getPostgreSqlUri());
    lines.add("postgresql.internalJdbcUrl: " + getInternalJdbcUrl());
    lines.add("postgresql.persistentData: " + persistentData);
    if (persistentData) {
      lines.add("postgresql.storageSize: " + storageSize);
      lines.add("postgresql.pgdata: " + DATA_MOUNT_PATH + "/pgdata");
    }
    return lines;
  }

  @Override
  public void preStop() {
    if (hasInitScripts()) {
      deleteInitScriptConfigMap();
    }
  }

  /**
   * Create the init script ConfigMap in Kubernetes.
   *
   * <p>This method is called from {@link #start()} before the StatefulSet is created, ensuring the
   * ConfigMap exists when the pod spec references it.
   */
  private void createInitScriptConfigMap() {
    String sql = buildInitSql();

    if (sql == null) {
      return;
    }

    KubernetesClient client = getClient();
    ConfigMap configMap =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName(name + "-init")
            .withNamespace(namespace.getName())
            .addToLabels("app", name)
            .addToLabels("managed-by", "testpods")
            .endMetadata()
            .addToData("init.sql", sql)
            .build();

    client.configMaps().inNamespace(namespace.getName()).resource(configMap).create();
  }

  String buildInitSql() {
    List<String> sections = new ArrayList<>();
    String additionalDatabaseSql = buildAdditionalDatabaseInitSql();
    if (!additionalDatabaseSql.isBlank()) {
      sections.add(additionalDatabaseSql);
    }

    String userSql = initScriptContent;
    if (userSql == null && initScriptPath != null) {
      userSql = loadClasspathResource(initScriptPath);
    }
    if (userSql != null && !userSql.isBlank()) {
      sections.add(userSql);
    }

    if (sections.isEmpty()) {
      return null;
    }
    return String.join("\n\n", sections);
  }

  private String buildAdditionalDatabaseInitSql() {
    if (additionalDatabases.isEmpty()) {
      return "";
    }

    StringBuilder sql = new StringBuilder("-- Generated by TestPods: create additional databases\n");
    for (String databaseName : additionalDatabases) {
      sql.append("CREATE DATABASE ").append(quoteIdentifier(databaseName)).append(";\n");
    }
    return sql.toString();
  }

  private static String quoteIdentifier(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }

  /**
   * Delete the init script ConfigMap from Kubernetes.
   *
   * <p>This method is called from {@link #stop()} after the StatefulSet is deleted.
   */
  private void deleteInitScriptConfigMap() {
    KubernetesClient client = getClient();
    client.configMaps().inNamespace(namespace.getName()).withName(name + "-init").delete();
  }

  private String loadClasspathResource(String path) {
    try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
      if (is == null) {
        throw new IllegalArgumentException("Resource not found: " + path);
      }
      return new String(is.readAllBytes());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load init script: " + path, e);
    }
  }
}
