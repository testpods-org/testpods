# Plan Review Summary

**Date:** 2026-01-21
**Reviewers:** DHH, Kieran, Simplicity
**Specs Reviewed:** 01-05 (Spec 04 subsequently deleted)

---

## DHH's Review (7/10)

### What's Good
- User-facing API is beautiful and convention-driven
- Sensible defaults (zero-config PostgreSQL)
- Clear implementation guidance ("Start small", "Keep it simple")

### Critical Issues

1. **Class hierarchy too deep**
   - 4 levels for PostgreSQLPod (TestPod → BaseTestPod → StatefulSetPod → PostgreSQLPod)
   - Suggests composition over inheritance

2. **PropertyContext/template interpolation over-engineered**
   - `${postgres.internal.uri}` syntax requires template engine
   - Use lambdas instead: `() -> postgres.getInternalJdbcUrl()`

3. **deleteManagedResources() shouldn't exist**
   - Six explicit delete calls for each resource type
   - Namespace deletion handles cascading cleanup

### Verdict
> "Ship MVP with half the features and twice the polish"

---

## Kieran's Review (Critical Bugs Found)

### Must Fix Before Implementation

1. **Init script ConfigMap never mounted** (Spec 02)
   - `createInitScriptConfigMap()` creates ConfigMap
   - `buildMainContainer()` never mounts it
   - **Functional bug** - init scripts won't execute

2. **Thread-unsafe static state** (Spec 01)
   - `TestPodDefaults.setClusterSupplier()` uses static state
   - JUnit 5 parallel test execution will cause race conditions

3. **Process execution without timeout** (Spec 05)
   - `process.waitFor()` waits indefinitely if minikube hangs
   - Add 30-second timeout

4. **Resource leak in wait strategy** (Spec 02)
   - `conn.createStatement()` never closed
   - Use try-with-resources for Statement


### Naming/Consistency Issues

- Use `@TestPod` and forget `@Pod` annotation
- Inconsistent null handling across fluent API
- Missing `@throws` JavaDoc on exception-throwing methods
- Field `withPersistentVolume` named like a method

### Test Coverage Gaps

- "Unit tests" require running Minikube (actually integration tests)
- No true unit tests with mocked Kubernetes client
- No tests for error paths (SQL syntax errors, null passwords, etc.)
- Placeholder test methods with only comments

---

## Simplicity Review (40% LOC Reduction Possible)


### Key Insight
> The existing codebase is closer to MVP-ready than the specs suggest. The specs introduce unnecessary abstractions.

### Recommended Simplified Order

1. **Spec 01** (JUnit Extension) - Minimal version, just field discovery and lifecycle
2. **Spec 02** (PostgreSQL) - With simplified wait strategy
3. **Spec 05** (Namespace) - Already mostly implemented

### What Existing Code Already Has

- `TestNamespace.java` - has `create()` and `close()`
- `NamespaceNaming.java` - has `forTestClass()`
- `StatefulSetPod.java` - has full lifecycle
- `BaseTestPod.java` - comprehensive (possibly too comprehensive)

---

## Consensus Recommendations

| Action | DHH | Kieran | Simplicity |
|--------|-----|--------|------------|
| Fix init script ConfigMap mounting | - | ✅ | - |
| Remove R2DBC, property publishing | ✅ | - | ✅ |
| Add process timeout in Spec 05 | - | ✅ | - |
| Fix Statement resource leak | - | ✅ | - |
| Address thread-safety of TestPodDefaults | - | ✅ | - |

---

## Action Items

### High Priority (Bugs)

- [ ] Fix init script ConfigMap mounting in Spec 02
- [ ] Add timeout to minikube process execution in Spec 05
- [ ] Fix Statement resource leak in PostgreSQLWaitStrategy
- [ ] Address TestPodDefaults thread-safety

### Simplification

- [ ] Remove property publishing system
- [ ] Remove `deleteManagedResources()` from Spec 05
- [ ] Remove PVC support from Spec 03

### Clarification

- [ ] Document null handling policy for fluent API
- [ ] Add true unit tests (mocked) separate from integration tests

---
