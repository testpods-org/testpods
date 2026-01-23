# TestPods Refactoring Plans

This folder contains detailed implementation plans for the refactoring tasks identified in the codebase opportunities analysis.

## Plan Overview

| # | Plan | Priority | Effort | Phase | Status |
|---|------|----------|--------|-------|--------|
| 09 | [Fix Broken StatefulSetPod Methods](09-fix-broken-statefulsetpod-methods.md) | Critical | Small | 1 | ✅ Done |
| 04 | [Fix Init Script ConfigMap Mount](04-fix-init-script-configmap-mount.md) | Critical | Small | 1 | ⏸️ Deferred |
| 06 | [Add Consistent Error Handling](06-add-consistent-error-handling.md) | High | Small | 1 | ✅ Done |
| 01 | [Extract WorkloadManager](01-extract-workload-manager.md) | High | Large | 2 | ✅ Done |
| 02 | [Extract ServiceManager](02-extract-service-manager.md) | High | Medium | 2 | ✅ Done |
| 03 | [Extract StorageManager](03-extract-storage-manager.md) | High | Medium | 2 | ✅ Done |
| 10 | [Create ContainerSpec Builder](10-create-container-spec-builder.md) | Medium | Medium | 3 | |
| 05 | [Fix Statement Resource Leak](05-fix-statement-resource-leak.md) | Medium | Small | 4 | |
| 07 | [Fix Thread-Unsafe TestPodDefaults](07-fix-thread-unsafe-testpoddefaults.md) | High | Small | 4 | |
| 08 | [Mark KafkaPod Incomplete](08-mark-kafkapod-incomplete.md) | Low | Small | 4 | |

## Recommended Execution Order

### Phase 1: Critical Bug Fixes ✅ COMPLETE

These tasks fix broken functionality and should be completed before other work:

1. **09-fix-broken-statefulsetpod-methods** - ✅ Done (commit 494baec)
2. **04-fix-init-script-configmap-mount** - ⏸️ Deferred (PostgreSQLPod not yet implemented; this is a design pattern for future implementation)
3. **06-add-consistent-error-handling** - ✅ Done (commit 9b29f06)

### Phase 2: Architecture Refactoring ✅ COMPLETE

These tasks transform the codebase to composition over inheritance:

- **01-extract-workload-manager** - ✅ Done - Extract Deployment/StatefulSet management
- **02-extract-service-manager** - ✅ Done - Extract ClusterIP/Headless/NodePort service management
- **03-extract-storage-manager** - ✅ Done - Extract PVC/EmptyDir/ConfigMap storage management

### Phase 3: Developer Experience

After architecture refactoring:

- **10-create-container-spec-builder** - Simplify container definition with fluent API

### Phase 4: Cleanup (Parallel)

These can be done in any order:

- **05-fix-statement-resource-leak** - Fix JDBC resource leak in wait strategy
- **07-fix-thread-unsafe-testpoddefaults** - Fix parallel test execution issues
- **08-mark-kafkapod-incomplete** - Document unimplemented class

## Target Architecture

After completing Phase 2 tasks (01-03), the architecture becomes:

```
TestPod (interface)
    │
    └── BaseTestPod (abstract - common state, fluent API)
            │
            └── ComposableTestPod (abstract - composes managers)
                    │
                    ├── PostgreSQLPod
                    │   ├─ workload: StatefulSetManager
                    │   ├─ service: HeadlessServiceManager + NodePortServiceManager
                    │   └─ storage: PersistentStorageManager (optional)
                    │
                    ├── GenericTestPod
                    │   ├─ workload: DeploymentManager
                    │   ├─ service: ClusterIPServiceManager
                    │   └─ storage: NoOpStorageManager
                    │
                    └── (Future) BatchJobPod
                        ├─ workload: JobManager
                        ├─ service: None
                        └─ storage: EmptyDirStorageManager
```

## Benefits

1. **Reduced hierarchy** - From 4 levels to 3 levels
2. **No more duplication** - Common code in managers, not duplicated base classes
3. **Mix and match** - Combine any workload type with any service/storage type
4. **Easier testing** - Mock individual managers
5. **Fluent API preserved** - Developer experience unchanged

## Key Design Principles

All plans follow these principles from the parent README:

1. **BaseTestPod remains** - Essential cross-cutting concerns preserved
2. **Fluent interface coherence** - Clean, readable developer-facing API
3. **Components are internal** - Managers are implementation details, not exposed to users

## Plan Structure

Each plan includes:

- **Overview** - What the plan accomplishes
- **Problem Statement** - What's wrong and why it matters
- **Proposed Solution** - How to fix it with code examples
- **Technical Considerations** - Design constraints and decisions
- **Acceptance Criteria** - Functional and quality requirements
- **Files to Modify** - Specific files and line numbers
- **MVP** - Minimal implementation code
- **Test Plan** - Test cases to validate the implementation
- **References** - Links to specs, docs, and related files

## Validation

After implementing a plan, write results to `specs/refactorings/<task-number>_result.md` following the format specified in each plan.

## Generated

These plans were generated on 2025-01-23 based on:

- Repository analysis: `specs/refactorings/REFACTORINGS_README.md`
- Individual specs: `specs/refactorings/01-*.md` through `specs/refactorings/10-*.md`
- Codebase research: `core/src/main/java/org/testpods/core/`
- Best practices research: Fabric8 docs, Java 17+ patterns, composition over inheritance
