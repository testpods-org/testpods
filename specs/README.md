# TestPods Implementation Specifications

**Phase:** MVP (Phase 1)
**Target:** Working first version with automated tests and JavaDoc

---

## Overview

These specifications define the implementation plan for TestPods Phase 1 (MVP) as defined in the PRD. The goal is to deliver a working library that enables:

- JUnit 5 annotation-driven tests with Kubernetes pods
- PostgreSQL database module for integration testing
- Spring Boot integration via `@DynamicPropertySource`

---

## Implementation Order

The specs should be implemented in this order due to dependencies:

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Spec 05: Namespace and Cluster Management               │
│    - Foundation for all other specs                        │
│    - NamespaceNaming, TestNamespace, ExternalAccessStrategy│
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Spec 03: StatefulSetPod Base Class                      │
│    - Required by PostgreSQLPod                             │
│    - Complete StatefulSet lifecycle management             │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. Spec 01: JUnit Extension Implementation                 │
│    - Core extension with @TestPods, @Pod field discovery   │
│    - Uses TestNamespace for lifecycle                      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Spec 02: PostgreSQL Pod Implementation                  │
│    - First database module                                 │
│    - Depends on StatefulSetPod and working extension       │
└─────────────────────────────────────────────────────────────┘
```

---

## Spec Summary

| Spec | Title | Priority | Status |
|------|-------|----------|--------|
| [01](01-junit-extension-implementation.md) | JUnit Extension Implementation | P0 | Ready |
| [02](02-postgresql-pod-implementation.md) | PostgreSQL Pod Implementation | P0 | Ready |
| [03](03-statefulset-pod-base-class.md) | StatefulSetPod Base Class | P0 | Ready |
| [05](05-namespace-and-cluster-management.md) | Namespace and Cluster Management | P0 | Ready |

**Note:** Spec 04 (@DependsOn Annotation) was removed following plan review - deferred to Phase 2 per YAGNI principle.

---

## MVP Scope (Phase 1)

From PRD Section 17.1:

- [x] **Minikube integration** - K8sCluster, MinikubeCluster exist
- [ ] **Single container deployment** - GenericTestPod exists, needs testing
- [ ] **JUnit 5 extension** - Spec 01
- [ ] **Docker Engine runtime support** - Via Minikube
- [ ] **Basic PostgreSQL module** - Spec 02
- [ ] **Documentation and examples** - JavaDoc required

### Deliverables

1. **Working JUnit Extension**
   - `@TestPods` class annotation
   - `@Pod` field annotation
   - Static and instance field support
   - Automatic namespace management

2. **PostgreSQL Module**
   - `PostgreSQLPod` with fluent API
   - JDBC connection string methods
   - Spring Boot `@DynamicPropertySource` integration
   - Init script support

3. **Automated Tests**
   - Unit tests for each component
   - Integration tests with Minikube
   - Spring Boot integration test

4. **Documentation**
   - JavaDoc on all public classes
   - Usage examples in JavaDoc
   - README with quick start

---

## Success Criteria

### Functional

```java
// This test should work after MVP implementation:

@TestPods
class OrderServiceIntegrationTest {

    @Pod
    static PostgreSQLPod postgres = new PostgreSQLPod()
        .withDatabase("orders")
        .withInitScript("db/schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void shouldConnectToDatabase() {
        assertThat(postgres.isRunning()).isTrue();
        // Database operations work
    }
}
```

### Quality

- [ ] Test coverage > 80% on core packages
- [ ] All public methods have JavaDoc
- [ ] No memory leaks from unclosed resources
- [ ] Clean shutdown with resource cleanup

---

## Prerequisites

Before implementing:

1. **Minikube installed** with "minikit" profile
2. **Java 17+** for language features
3. **Maven** for building
4. **Running cluster**: `minikube start -p minikit`

---

## PRD References

- [Goals and Objectives](../prd/02_goals_and_objectives.md)
- [Functional Requirements](../prd/06_functional_requirements.md)
- [Development Phases](../prd/17_development_phases.md)
- [Test API Design Decisions](../prd/21_test_api_design_decisions.md)

---

## Notes for Implementers

1. **Start small**: Get a single GenericTestPod working with the extension first
2. **Test early**: Write integration tests as you go
3. **Follow patterns**: Look at existing code in DeploymentPod, GenericTestPod
4. **JavaDoc**: Add documentation as you write, not after
5. **Keep it simple**: MVP = minimal viable product, don't over-engineer
