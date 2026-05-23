package org.testpods.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.pods.external.postgresql.PostgreSQLPod;
import org.testpods.core.wait.WaitStrategy;

/**
 * Full minikube-backed integration test for the JUnit extension and PostgreSQL TestPod.
 *
 * <p>Run explicitly with:
 *
 * <pre>{@code
 * mvn verify -Dit.test=TestPodsExtensionPostgreSQLIT
 * }</pre>
 */
@TestPods
class TestPodsExtensionPostgreSQLIT {

  @RegisterCluster static K8sCluster cluster = K8sCluster.newMinikube().withNamespace();

  @TestPod
  static PostgreSQLPod postgres =
      new PostgreSQLPod()
          .withName("postgres-extension-it")
          .withDatabase("orders")
          .withUsername("orders_user")
          .withPassword("orders_pass")
          .withUrlParam("sslmode", "disable")
          .waitingFor(
              WaitStrategy.forLogMessage(".*database system is ready to accept connections.*", 2)
                  .withTimeout(Duration.ofMinutes(3)));

  @Test
  void testPodsExtensionStartsPostgreSQLPodInMinikube() throws Exception {
    assertThat(postgres.getCluster()).isSameAs(cluster);
    assertThat(postgres.getNamespace()).isNotNull();
    assertThat(postgres.isRunning()).isTrue();
    assertThat(postgres.isReady()).isTrue();
    assertThat(postgres.getExternalHost()).isNotBlank();
    assertThat(postgres.getExternalPort()).isPositive();

    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement = connection.prepareStatement("SELECT 1");
        var resultSet = statement.executeQuery()) {
      assertThat(resultSet.next()).isTrue();
      assertThat(resultSet.getInt(1)).isEqualTo(1);
    }
  }
}
