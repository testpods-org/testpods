# 18. Open Questions and Discussion Points

<!-- TOC -->
- [18. Open Questions and Discussion Points](#18-open-questions-and-discussion-points)
  - [18.1. Technical Decisions](#181-technical-decisions)
  - [18.2. API Design Questions](#182-api-design-questions)
  - [18.3. Runtime Questions](#183-runtime-questions)
  - [18.4. Module System Questions](#184-module-system-questions)
  - [18.5. Performance Questions](#185-performance-questions)
  - [18.6. Remaining Open Questions](#186-remaining-open-questions)
<!-- /TOC -->

## 18.1. Technical Decisions

- [x] Which Kubernetes Java client library? (Fabric8 vs. official)
  - **RESOLVED:** Fabric8 Kubernetes Client - already implemented in codebase
- [x] Maven vs. Gradle for project build?
  - **RESOLVED:** Maven - already configured in pom.xml
- [x] Module discovery mechanism (classpath scanning vs. explicit registration)?
  - **RESOLVED:** Explicit registration via `@Pod` annotation on fields and `TestPodCatalog.register()`. See [Section 21.6](21_test_api_design_decisions.md#216-testpodgroup-and-testpodcatalog)
- [x] Configuration format (Java DSL only or YAML support)?
  - **RESOLVED:** Java DSL primary. YAML support deferred to later phase.
- [ ] How to handle Kubernetes version compatibility?
  - **OPEN:** Not yet addressed

## 18.2. API Design Questions

- [x] Annotation-based vs. programmatic lifecycle management?
  - **RESOLVED:** Both supported. Annotation-driven (`@TestPods` + `@Pod`) is primary, programmatic with `TestPodDefaults` is secondary. See [Section 21.2](21_test_api_design_decisions.md#212-core-api-style)
- [x] How to expose low-level Kubernetes API when needed?
  - **RESOLVED:** Via `withPodCustomizer(UnaryOperator<PodSpecBuilder>)` for full Fabric8 access. Already implemented in BaseTestPod.
- [x] Namespace management strategy?
  - **RESOLVED:** Per-test-class namespace with random suffix. Configurable via `@TestPods(namespace=...)` or `@Pod(namespace=...)` override. See [Section 21.8](21_test_api_design_decisions.md#218-namespace-management)
- [x] How to handle test parallelization safely?
  - **RESOLVED:** Random namespace suffix (`testpods-{context}-{5chars}`) ensures isolation. Already implemented in NamespaceNaming. See [Section 21.10.3](21_test_api_design_decisions.md#2110-junit-integration-details)

## 18.3. Runtime Questions

- [x] Runtime detection strategy (auto vs. explicit configuration)?
  - **RESOLVED:** Auto-discovery via `K8sCluster.discover()` with fallback chain: minikit profile -> default minikube -> (future: kind, kubeconfig). Explicit via `K8sCluster.minikube(profile)`.
- [ ] How to validate runtime capabilities?
  - **OPEN:** Not yet addressed
- [ ] Support for runtime-specific features?
  - **OPEN:** Not yet addressed

## 18.4. Module System Questions

- [x] Module versioning strategy?
  - **RESOLVED:** Pods specify version via fluent API (e.g., `new PostgreSQLPod().withVersion("15")`). No separate module versioning needed.
- [x] How to handle module dependencies?
  - **RESOLVED:** Explicit `@DependsOn("podName")` annotation or `group.dependsOn(otherGroup)`. Parallel by default, dependency graph respected. See [Section 21.4](21_test_api_design_decisions.md#214-startup-order-and-dependencies)
- [ ] Contribution process for community modules?
  - **OPEN:** Not yet addressed (future concern)

## 18.5. Performance Questions

- [x] Cluster caching strategy (per test suite, per day, manual)?
  - **RESOLVED:** Cluster reused across test runs (Minikube stays running). Namespace per test class with cleanup policy. See [Section 21.8](21_test_api_design_decisions.md#218-namespace-management)
- [ ] Image caching/preloading?
  - **OPEN:** Relies on Minikube's image cache. No explicit preloading API yet.
- [x] Parallel pod deployment?
  - **RESOLVED:** Parallel by default within groups. Sequential when dependencies declared or `@TestPodGroup(parallel=false)`. See [Section 21.4](21_test_api_design_decisions.md#214-startup-order-and-dependencies)

## 18.6. Remaining Open Questions

The following questions remain open for future resolution:

1. **Kubernetes version compatibility** - How to handle API differences across K8s versions?
2. **Runtime capability validation** - How to check if cluster supports required features?
3. **Runtime-specific features** - How to expose containerd vs Docker-specific capabilities?
4. **Community module contribution** - Process for accepting third-party pod implementations
5. **Image preloading** - API for pre-pulling images before test execution
6. **Operator testing mode** - Deferred per [Section 9](09_future_consideration_operator_testing_mode.md)
