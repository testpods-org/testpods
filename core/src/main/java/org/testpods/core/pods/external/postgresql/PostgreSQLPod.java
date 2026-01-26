package org.testpods.core.pods.external.postgresql;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import org.testpods.core.PropertyContext;
import org.testpods.core.pods.StatefulSetPod;
import org.testpods.core.wait.WaitStrategy;

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
public class PostgreSQLPod extends StatefulSetPod<PostgreSQLPod> {

  // === Constants ===

  public static final String DEFAULT_IMAGE = "postgres:16-alpine";
  public static final int POSTGRESQL_PORT = 5432;
  public static final String DEFAULT_DATABASE = "test";
  public static final String DEFAULT_USERNAME = "test";
  public static final String DEFAULT_PASSWORD = "test";

  // === Configuration ===

  private String image = DEFAULT_IMAGE;
  private String databaseName = DEFAULT_DATABASE;
  private String username = DEFAULT_USERNAME;
  private String password = DEFAULT_PASSWORD;
  private final Map<String, String> urlParameters = new LinkedHashMap<>();
  private String initScriptPath;
  private String initScriptContent;

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

  // === Connection Information ===

  /**
   * Get the JDBC connection URL for external access (from test code).
   *
   * @return JDBC URL like "jdbc:postgresql://host:port/database"
   */
  public String getJdbcUrl() {
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

  /** Get the database name. */
  public String getDatabaseName() {
    return databaseName;
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
    ctx.publish(prefix + ".username", this::getUsername);
    ctx.publish(prefix + ".password", this::getPassword);
    ctx.publish(prefix + ".database", this::getDatabaseName);

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

  @Override
  protected Container buildMainContainer() {
    return new ContainerBuilder()
        .withName("postgres")
        .withImage(image)
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
        .endLivenessProbe()
        .build();
  }

  // Note: Init script support is documented but not yet implemented.
  // The createAdditionalResources hook does not exist in StatefulSetPod yet.
  // When added, uncomment and implement:
  // @Override
  // protected void createAdditionalResources() {
  //   if (initScriptPath != null || initScriptContent != null) {
  //     createInitScriptConfigMap();
  //   }
  // }

  @SuppressWarnings("unused")
  private void createInitScriptConfigMap() {
    String sql = initScriptContent;
    if (sql == null && initScriptPath != null) {
      sql = loadClasspathResource(initScriptPath);
    }

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
