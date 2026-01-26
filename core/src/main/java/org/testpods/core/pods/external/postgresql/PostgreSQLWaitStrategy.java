package org.testpods.core.pods.external.postgresql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import org.testpods.core.pods.TestPod;
import org.testpods.core.wait.WaitStrategy;

/**
 * Wait strategy for PostgreSQL pods.
 *
 * <p>Performs a multi-layer readiness check:
 *
 * <ol>
 *   <li>Wait for Kubernetes pod to be Ready
 *   <li>Wait for "database system is ready to accept connections" log message (x2)
 *   <li>Verify JDBC connection with SELECT 1
 * </ol>
 */
public class PostgreSQLWaitStrategy implements WaitStrategy {

  private Duration timeout = Duration.ofMinutes(1);
  private Duration pollInterval = Duration.ofMillis(500);

  @Override
  public PostgreSQLWaitStrategy withTimeout(Duration timeout) {
    PostgreSQLWaitStrategy copy = new PostgreSQLWaitStrategy();
    copy.timeout = timeout;
    copy.pollInterval = this.pollInterval;
    return copy;
  }

  @Override
  public PostgreSQLWaitStrategy withPollInterval(Duration interval) {
    PostgreSQLWaitStrategy copy = new PostgreSQLWaitStrategy();
    copy.timeout = this.timeout;
    copy.pollInterval = interval;
    return copy;
  }

  @Override
  public void waitUntilReady(TestPod<?> testPod) {
    if (!(testPod instanceof PostgreSQLPod)) {
      throw new IllegalArgumentException("PostgreSQLWaitStrategy requires PostgreSQLPod");
    }

    PostgreSQLPod postgres = (PostgreSQLPod) testPod;
    long deadline = System.currentTimeMillis() + timeout.toMillis();

    // Step 1: Wait for pod to be ready (Kubernetes readiness probe)
    waitForPodReady(postgres, deadline);

    // Step 2: Wait for log message (PostgreSQL logs this twice)
    waitForLogMessage(postgres, "database system is ready to accept connections", 2, deadline);

    // Step 3: Verify JDBC connection
    waitForJdbcConnection(postgres, deadline);
  }

  private void waitForPodReady(PostgreSQLPod postgres, long deadline) {
    while (System.currentTimeMillis() < deadline) {
      if (postgres.isReady()) {
        return;
      }
      sleep(pollInterval);
    }
    throw new IllegalStateException("Timed out waiting for PostgreSQL pod to be ready");
  }

  private void waitForLogMessage(PostgreSQLPod postgres, String message, int times, long deadline) {
    while (System.currentTimeMillis() < deadline) {
      String logs = postgres.getLogs();
      long count = logs.lines().filter(line -> line.contains(message)).count();
      if (count >= times) {
        return;
      }
      sleep(pollInterval);
    }
    throw new IllegalStateException("Timed out waiting for PostgreSQL log message: " + message);
  }

  private void waitForJdbcConnection(PostgreSQLPod postgres, long deadline) {
    String url = postgres.getJdbcUrl();
    String user = postgres.getUsername();
    String pass = postgres.getPassword();

    while (System.currentTimeMillis() < deadline) {
      // Both Connection and PreparedStatement are in try-with-resources to prevent resource leaks.
      // PreparedStatement is preferred over Statement for type safety.
      try (Connection conn = DriverManager.getConnection(url, user, pass);
          PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
        stmt.execute();
        return; // Success!
      } catch (SQLException e) {
        // Connection failed, retry
        sleep(pollInterval);
      }
    }
    throw new IllegalStateException("Timed out waiting for PostgreSQL JDBC connection at: " + url);
  }

  private void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting", e);
    }
  }
}
