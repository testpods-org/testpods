# Fix Statement Resource Leak in PostgreSQLWaitStrategy

## Executive Summary

### Problem Statement

In the PostgreSQL wait strategy implementation, a JDBC Statement is created but never closed, causing a resource leak:

```java
// Current implementation - LEAKS STATEMENT
try (Connection conn = DriverManager.getConnection(url, user, pass)) {
    conn.createStatement().execute("SELECT 1");  // Statement never closed!
    return;
}
```

While the Connection is properly closed via try-with-resources, the Statement is leaked. This:
- Violates JDBC best practices
- Could cause issues if the wait strategy retries many times before succeeding
- May contribute to connection pool exhaustion in edge cases

### Solution Statement

Fix the resource leak by including the Statement in the try-with-resources block, ensuring both Connection and Statement are properly closed:

```java
try (Connection conn = DriverManager.getConnection(url, username, password);
     PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
    stmt.execute();
    return;
}
```

### Solution Properties

- **JDBC Best Practice Compliance:** Always close Statement, ResultSet, and Connection via try-with-resources
- **Automatic Resource Ordering:** Try-with-resources automatically closes Statement before Connection
- **PreparedStatement Choice:** Using PreparedStatement over Statement for better type safety
- **Minimal Change:** Single-line fix with no behavioral changes to wait strategy logic

---

## Background Research

### JDBC Resource Management Best Practices

Per Oracle JDBC documentation, all JDBC resources must be explicitly closed:
- Connection, Statement, and ResultSet should all be in try-with-resources
- Resources are closed in reverse order of declaration
- PreparedStatement is preferred over Statement for parameterized queries

### Existing Wait Strategy Pattern

The `PostgreSQLWaitStrategy` follows the standard wait strategy pattern:
1. Poll at regular intervals until timeout
2. Check connection by executing a simple query (`SELECT 1`)
3. Return success when query executes without exception

### Related Files

- `core/src/main/java/org/testpods/core/wait/PostgreSQLWaitStrategy.java` - Contains the bug
- `core/src/main/java/org/testpods/core/wait/WaitStrategy.java` - Interface definition

---

## Implementation Steps

### Status: ✅ | Step 1: Fix Statement Resource Leak

#### Step 1 Purpose

Ensure the Statement created during PostgreSQL readiness checks is properly closed to prevent resource leaks.

#### Step 1 Description

Modify the `checkConnection` method in `PostgreSQLWaitStrategy` to include the PreparedStatement in the try-with-resources block.

**File to modify:** `core/src/main/java/org/testpods/core/wait/PostgreSQLWaitStrategy.java`

**Change required in `checkConnection` method:**

Before (pseudo-code - current implementation):
```
method checkConnection(url):
    try-with-resources(Connection):
        connection.createStatement().execute("SELECT 1")  // Statement not in try-with-resources
        return true
    catch SQLException:
        return false
```

After (pseudo-code - fixed implementation):
```
method checkConnection(url):
    try-with-resources(Connection, PreparedStatement):  // Both resources managed
        statement.execute()
        return true
    catch SQLException:
        return false
```

**Tests to verify:**
- Existing `PostgreSQLWaitStrategyTest` tests should continue to pass
- Code review confirms both Connection and PreparedStatement are in try-with-resources
- Wait strategy still correctly detects PostgreSQL readiness

**Acceptance Criteria:**
- [ ] PreparedStatement is included in try-with-resources declaration
- [ ] No Statement created outside of try-with-resources
- [ ] All existing wait strategy tests pass
- [ ] PostgreSQL pod startup still works correctly

---
