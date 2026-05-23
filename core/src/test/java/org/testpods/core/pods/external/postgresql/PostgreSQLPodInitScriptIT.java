package org.testpods.core.pods.external.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testpods.core.wait.WaitStrategy;

/** Integration tests proving PostgreSQL Docker entrypoint init scripts execute in Kubernetes. */
class PostgreSQLPodInitScriptIT {

  @Test
  void shouldExecuteInitScripts() throws Exception {
    PostgreSQLPod postgres =
        new PostgreSQLPod()
            .withName("test-init")
            .withDatabase("testdb")
            .withUsername("testuser")
            .withPassword("testpass")
            .withInitSql("CREATE TABLE test_table (id INT PRIMARY KEY);")
            .withUrlParam("sslmode", "disable")
            .waitingFor(
                WaitStrategy.forLogMessage(".*database system is ready to accept connections.*", 2)
                    .withTimeout(Duration.ofMinutes(2)));

    try {
      postgres.start();
      assertTableExists(postgres, "test_table");
    } finally {
      postgres.stop();
      if (postgres.getCluster() != null) {
        postgres.getCluster().close();
      }
    }
  }

  @Test
  void shouldExecuteInitScriptsFromClasspath() throws Exception {
    PostgreSQLPod postgres =
        new PostgreSQLPod()
            .withName("test-init-classpath")
            .withDatabase("testdb")
            .withUsername("testuser")
            .withPassword("testpass")
            .withInitScript("db/init.sql")
            .withUrlParam("sslmode", "disable")
            .waitingFor(
                WaitStrategy.forLogMessage(".*database system is ready to accept connections.*", 2)
                    .withTimeout(Duration.ofMinutes(2)));

    try {
      postgres.start();
      assertTableExists(postgres, "test_table");

      try (Connection conn =
              DriverManager.getConnection(
                  postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
          ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM test_table")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).as("Init script should have inserted data").isGreaterThan(0);
      }
    } finally {
      postgres.stop();
      if (postgres.getCluster() != null) {
        postgres.getCluster().close();
      }
    }
  }

  private static void assertTableExists(PostgreSQLPod postgres, String tableName) throws Exception {
    try (Connection conn =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      ResultSet tables = conn.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"});
      assertThat(tables.next()).as("Init script should have created " + tableName).isTrue();
    }
  }
}
