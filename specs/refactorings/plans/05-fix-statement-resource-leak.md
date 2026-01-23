# Plan: Fix Statement Resource Leak in PostgreSQLWaitStrategy

**Priority:** Medium
**Effort:** Small
**Category:** Bug Fix / Resource Management
**Phase:** 4 - Cleanup (Can be done in parallel)

---

## Overview

Fix a resource leak in the PostgreSQL wait strategy where a JDBC Statement is created but never closed.

## Problem Statement

In the PostgreSQL wait strategy implementation, the connection check creates a Statement but never closes it:

```java
// Current (LEAKS STATEMENT)
try (Connection conn = DriverManager.getConnection(url, user, pass)) {
    conn.createStatement().execute("SELECT 1");  // Statement never closed!
    return;
}
```

While the Connection is properly closed (via try-with-resources), the Statement is leaked.

### Impact
- Minor resource leak during startup
- Violates JDBC best practices
- Could cause issues if wait strategy retries many times before succeeding
- May contribute to connection pool exhaustion in edge cases

## Proposed Solution

Use nested try-with-resources to ensure both Connection and Statement are closed:

```java
try (Connection conn = DriverManager.getConnection(url, user, pass);
     Statement stmt = conn.createStatement()) {
    stmt.execute("SELECT 1");
    return;
}
```

Or use PreparedStatement for type safety:

```java
try (Connection conn = DriverManager.getConnection(url, user, pass);
     PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
    stmt.execute();
    return;
}
```

## Technical Considerations

- **JDBC Best Practice:** Always close Statement, ResultSet, and Connection in try-with-resources
- **Ordering:** Statement must be closed before Connection (try-with-resources handles this automatically)
- **PreparedStatement:** Slightly more overhead but better type safety and reusability

## Acceptance Criteria

### Functional Requirements
- [ ] Statement is closed via try-with-resources
- [ ] No resource leaks in wait strategy
- [ ] Wait strategy still correctly detects PostgreSQL readiness

### Quality Gates
- [ ] Code review confirms try-with-resources usage
- [ ] Wait strategy test passes

## Files to Modify

| File | Change |
|------|--------|
| `specs/02-postgresql-pod-implementation.md` | Update wait strategy code in spec |
| `core/src/main/java/org/testpods/core/wait/PostgreSQLWaitStrategy.java` (future) | Implement fix when created |

## MVP

### PostgreSQLWaitStrategy.java (fixed implementation)

```java
package org.testpods.core.wait;

import org.testpods.core.pods.TestPod;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Wait strategy that checks PostgreSQL readiness via JDBC connection.
 */
public class PostgreSQLWaitStrategy implements WaitStrategy {

    private final String database;
    private final String username;
    private final String password;
    private Duration timeout = Duration.ofSeconds(60);
    private Duration pollInterval = Duration.ofSeconds(1);

    public PostgreSQLWaitStrategy(String database, String username, String password) {
        this.database = database;
        this.username = username;
        this.password = password;
    }

    @Override
    public void waitUntilReady(TestPod<?> pod) {
        String host = pod.getExternalHost();
        int port = pod.getExternalPort();
        String url = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);

        long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < deadline) {
            if (checkConnection(url)) {
                return;  // Success
            }

            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new WaitTimeoutException("Interrupted while waiting for PostgreSQL", e);
            }
        }

        throw new WaitTimeoutException(
            "PostgreSQL did not become ready within " + timeout);
    }

    private boolean checkConnection(String url) {
        // FIXED: Both Connection and Statement are in try-with-resources
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {

            stmt.execute();
            return true;

        } catch (SQLException e) {
            // Connection failed - PostgreSQL not ready yet
            return false;
        }
    }

    public PostgreSQLWaitStrategy withTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public PostgreSQLWaitStrategy withPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
        return this;
    }
}
```

## Test Plan

### PostgreSQLWaitStrategyTest.java

```java
@Test
void shouldCloseStatementProperly() {
    // This is more of a code review check than a runtime test
    // Verify try-with-resources is used for both Connection and Statement

    // Static analysis or code review should confirm:
    // 1. Connection is in try-with-resources
    // 2. Statement/PreparedStatement is in try-with-resources
    // 3. No Statement created outside of try-with-resources
}

@Test
void shouldDetectReadyPostgres() {
    // Integration test with actual PostgreSQL
    PostgreSQLPod postgres = new PostgreSQLPod()
        .withName("test-wait")
        .waitingFor(new PostgreSQLWaitStrategy("testdb", "postgres", "secret"));

    postgres.start();
    try {
        assertThat(postgres.isReady()).isTrue();
    } finally {
        postgres.stop();
    }
}
```

## Code Change Summary

### Before (LEAKS STATEMENT)

```java
try (Connection conn = DriverManager.getConnection(url, user, pass)) {
    conn.createStatement().execute("SELECT 1");  // Statement never closed!
    return;
}
```

### After (FIXED)

```java
try (Connection conn = DriverManager.getConnection(url, username, password);
     PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
    stmt.execute();
    return;
}
```

## References

- Spec: `specs/refactorings/05-fix-statement-resource-leak.md`
- Implementation spec: `specs/02-postgresql-pod-implementation.md` (lines around 499-515)
- JDBC Best Practices: https://docs.oracle.com/javase/tutorial/jdbc/basics/processingsqlstatements.html
- Try-With-Resources: https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html

---

## Validation Output

After implementation, write results to `specs/refactorings/05-fix-statement-resource-leak_result.md`
