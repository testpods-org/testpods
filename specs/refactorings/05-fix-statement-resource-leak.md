# Refactoring 05: Fix Statement Resource Leak in PostgreSQLWaitStrategy

**Priority:** Medium (Bug Fix)
**Effort:** Small
**Category:** Bug Fix / Resource Management

---

## Problem Statement

In the PostgreSQL wait strategy implementation (`specs/02-postgresql-pod-implementation.md`), the connection check creates a Statement but never closes it:

```java
// Current (LEAKS STATEMENT)
try (Connection conn = DriverManager.getConnection(url, user, pass)) {
    conn.createStatement().execute("SELECT 1");  // Statement never closed!
    return;
}
```

While the Connection is properly closed (via try-with-resources), the Statement is leaked.

---

## Impact

- Minor resource leak during startup
- Violates JDBC best practices
- Could cause issues if wait strategy retries many times before succeeding

---

## Proposed Solution

Use nested try-with-resources:

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

---

## Files to Modify

| File | Change |
|------|--------|
| `specs/02-postgresql-pod-implementation.md` | Update wait strategy code |
| (Future) `PostgreSQLWaitStrategy.java` | Implement fix when created |

---

## Success Criteria

1. [ ] Statement is closed via try-with-resources
2. [ ] No resource leaks in wait strategy
3. [ ] Wait strategy still correctly detects PostgreSQL readiness

---

## Test Plan

```java
@Test
void waitStrategyShouldNotLeakResources() {
    // This is more of a code review check than a runtime test
    // Verify try-with-resources is used for both Connection and Statement
}
```

---

## Validation Step

After implementation, the agent must:

1. **Code review** - Verify try-with-resources for Statement
2. **Test** - Ensure wait strategy still works
3. **Document findings** - Write to `specs/refactorings/05-fix-statement-resource-leak_result.md`

### Validation Output Format

```markdown
# Validation Result: Fix Statement Resource Leak

## Implementation Summary
- Files modified: [list]

## Verification
| Check | Result |
|-------|--------|
| Statement in try-with-resources | [Y/N] |
| Wait strategy functions correctly | [Y/N] |

## Code Change
Before:
```java
// old code
```

After:
```java
// new code
```

## Deviations from Plan
[List any deviations and reasoning]
```
