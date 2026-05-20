# Refactoring 11 — Managed Pod Architecture

**Status:** Design approved, ready for plan
**Date:** 2026-05-19
**Scope:** `org.testpods.core.pods` package (with downstream rename impact across the codebase)

## Goal

Restructure the pod inheritance hierarchy to:

1. Eliminate the `TestPod` name collision between the core interface (`org.testpods.core.pods.TestPod`) and the JUnit annotation (`org.testpods.junit.TestPod`).
2. Extract container-shape state from `GenericTestPod` into a composable `ContainerDefinition`, so the inheritance chain doesn't carry concerns specific to generic pods.
3. Make the lifecycle-hooks contract (currently an unused `TestPodLifecycleHooks` interface) load-bearing and integrated into the managed provisioning workflow.
4. Deduplicate `DeploymentPod` / `StatefulSetPod` — currently ~70% identical — by introducing a `WorkloadResource` strategy and lifting the lifecycle algorithm into the abstract base.

## Final architecture

```
<<interface>>
Pod<SELF>                                  (was TestPod)
    │
    ▼
<<abstract>>
BaseManagedPod<SELF>                       (was BaseTestPod)
  - owns lifecycle algorithm (start/stop/cleanup/isRunning/isReady)
  - delegates kind-specific work to WorkloadResource
  - invokes PodLifecycleHooks if subclass implements it
    │
    ├──────────────────────────────┐
    ▼                              ▼
<<abstract>>                  <<abstract>>
DeploymentPod<SELF>           StatefulSetPod<SELF>
  - binds DeploymentWorkload   - binds StatefulSetWorkload
  - exposes Deployment-shaped   - exposes StatefulSet-shaped
    customizers                   customizers (incl. PVC)
    │                              │
    ▼                              ├──────────┬─────────┐
GenericPod                         ▼          ▼         ▼
  (was GenericTestPod)        GenericStatefulPod  PostgreSQLPod  KafkaPod
  composes ContainerDefinition  composes ContainerDefinition  (own container)  (own container)

<<interface>>  PodLifecycleHooks   (optional, opt-in)
  - preStart(), postStart(), preStop()

ContainerDefinition (composable, not in inheritance chain)
  - image, ports, env, command, args, probes
  - buildContainer(name): Container
  - getPrimaryPort(): int
  - deriveDefaultWaitStrategy(): WaitStrategy

org.testpods.core.workload.WorkloadManager   (already exists; wired in step 4)
  - DeploymentManager, StatefulSetManager
org.testpods.core.service.ServiceManager     (already exists; wired in step 4)
  - ClusterIPServiceManager, HeadlessServiceManager
```

## Component details

### `Pod<SELF>` (renamed from `TestPod<SELF>`)

The core contract. Same shape as today's `TestPod` — only the type name and javadoc references change. Package stays `org.testpods.core.pods`. The JUnit annotation `org.testpods.junit.TestPod` is unaffected (the name collision goes away naturally).

### `BaseManagedPod<SELF>` (renamed from `BaseTestPod<SELF>`)

Abstract base. Same responsibilities as today's `BaseTestPod` (configuration state, mid-level customizations, observability impls), plus:

- Owns the `start()` and `stop()` algorithms (lifted from `DeploymentPod`/`StatefulSetPod`).
- Owns `isRunning()` and `isReady()` (delegating to the bound `WorkloadManager`).
- Invokes `PodLifecycleHooks` methods if the concrete subclass implements them.

Subclasses contribute (via constructor or factory methods):

- A `WorkloadManager` instance for their workload kind.
- A `ServiceManager` instance for their service kind.
- A main container (via abstract `buildMainContainer()`).
- Kind-specific customizers as additional fluent methods.

### `PodLifecycleHooks` (renamed from `TestPodLifecycleHooks`)

Optional opt-in interface:

```java
public interface PodLifecycleHooks {
    default void preStart()  {}
    default void postStart() {}
    default void preStop()   {}
}
```

Method names drop the `Container` suffix — these hooks fire around the **pod's** lifecycle (workload + service + waits), not just container startup. `BaseManagedPod.start()/stop()` invokes them via `instanceof` checks. No reflection, no annotations.

**Wiring inside `BaseManagedPod` (after step 4):**

```java
public final void start() {
    if (this instanceof PodLifecycleHooks h) h.preStart();
    try {
        KubernetesClient client = getClient();
        String ns = namespace.getName();
        PodSpec podSpec = buildPodSpec();          // builds container + customizations

        WorkloadConfig workloadConfig = WorkloadConfig.builder()
            .name(name).namespace(ns)
            .labels(buildLabels()).annotations(annotations)
            .podSpec(podSpec).client(client)
            .build();
        workload.create(workloadConfig);

        ServiceConfig serviceConfig = ServiceConfig.builder()
            .name(name).namespace(ns)
            .port(getInternalPort())
            .labels(buildLabels())
            .selector(Map.of("app", name))
            .client(client)
            .build();
        serviceMgr.create(serviceConfig);

        waitForReady();
    } catch (Exception e) {
        cleanup();
        throw new TestPodStartException(name, e.getMessage(), e);
    }
    if (this instanceof PodLifecycleHooks h) h.postStart();
}

public final void stop() {
    if (this instanceof PodLifecycleHooks h) h.preStop();
    serviceMgr.delete();
    workload.delete();
}

public final boolean isRunning() { return workload.isRunning(); }
public final boolean isReady()   { return workload.isReady(); }
```

Step 2 (hook integration) lands an interim version that doesn't yet use the managers — it pulls `start()`/`stop()` up to `BaseManagedPod` as `final` methods that wrap hook calls around a `protected abstract doStart()` / `doStop()` template method, with `DeploymentPod` and `StatefulSetPod` keeping their current bodies inside `doStart()`/`doStop()`. Step 4 replaces those template methods with the manager-backed algorithm above and deletes the `doStart()`/`doStop()` abstractions.

### `ContainerDefinition`

Standalone composable class that captures everything `GenericTestPod` currently carries about its container:

```java
public class ContainerDefinition {
    private String image;
    private final List<PortEntry> ports = new ArrayList<>();
    private final Map<String,String> env = new LinkedHashMap<>();
    private List<String> command;
    private List<String> args;
    private Integer primaryPort;
    private String readinessPath;
    private Integer readinessPort;

    // Fluent setters (returning ContainerDefinition):
    //   withImage, withPort, withPrimaryPort, withEnv,
    //   withCommand, withArgs, withHttpReadinessProbe

    public Container buildContainer(String name);   // builds a Fabric8 Container
    public int getPrimaryPort();
    public WaitStrategy deriveDefaultWaitStrategy(); // HTTP → port → readiness probe
}
```

Used by `GenericPod` and `GenericStatefulPod`. Domain pods (`PostgreSQLPod`, `KafkaPod`) keep their existing `buildMainContainer()` and ignore `ContainerDefinition` entirely.

### `WorkloadManager` and `ServiceManager` (already exist — wire them in)

The codebase already contains the strategy abstractions this refactor needs, in `org.testpods.core.workload` and `org.testpods.core.service`. They were introduced by historical refactorings 01 and 02 but were never wired into the pod classes. Step 4 of this refactor finishes that work.

Existing pieces we reuse as-is:

- `WorkloadManager` (interface) with `create(WorkloadConfig)`, `delete()`, `isRunning()`, `isReady()`, `getName()`.
- `DeploymentManager` (impl) — wraps a Fabric8 `Deployment`.
- `StatefulSetManager` (impl) — wraps a Fabric8 `StatefulSet`, supports PVC templates via `withPvcTemplates(List<PersistentVolumeClaim>)` and an optional headless service name via `withServiceName(String)`.
- `WorkloadConfig` (record) — name, namespace, labels, annotations, podSpec, client.
- `ServiceManager` (interface) with `create(ServiceConfig)`, `delete()`, `getService()`, `getName()`.
- `ClusterIPServiceManager`, `HeadlessServiceManager`, `NodePortServiceManager` (impls).
- `ServiceConfig` (record) — name, namespace, port, labels, selector, customizers, client.

`BaseManagedPod` becomes:

- Owns a `WorkloadManager workload` and a `ServiceManager serviceMgr` (bound by subclasses).
- `start()` builds `WorkloadConfig` from its state (name, namespace, labels, annotations, podSpec via `buildPodSpec()`), calls `workload.create(...)`, builds `ServiceConfig`, calls `serviceMgr.create(...)`, then `waitForReady()`. Hook integration wraps the whole thing.
- `stop()`: hook, `serviceMgr.delete()`, `workload.delete()`.
- `isRunning()` → `workload.isRunning()`. `isReady()` → `workload.isReady()`.

`DeploymentPod` becomes thin:

- Constructor wires `workload = new DeploymentManager()` and `serviceMgr = new ClusterIPServiceManager()`.
- Keeps `buildMainContainer()` abstract.
- Exposes `withDeploymentCustomizer`/`withServiceCustomizer` (forwarding to its config-building hook — for now, retain the customizer lists; deeper integration with the existing managers' customizer plumbing is a future refactor).

`StatefulSetPod` becomes thin:

- Constructor wires `workload = new StatefulSetManager()` and `serviceMgr = new HeadlessServiceManager()`.
- Keeps `buildMainContainer()` abstract.
- Exposes `withPvcCustomizer` (collects PVC builders); the resulting `List<PersistentVolumeClaim>` is fed to `StatefulSetManager.withPvcTemplates(...)` before `start()`.
- Exposes `withStatefulSetCustomizer`/`withServiceCustomizer` similarly.

### `GenericPod` and `GenericStatefulPod`

- `GenericPod` (renamed from `GenericTestPod`) extends `DeploymentPod`. Owns a `ContainerDefinition` instance and forwards `withImage / withPort / withPrimaryPort / withEnv / withCommand / withArgs / withHttpReadinessProbe` to it (returning `SELF` for chaining). `buildMainContainer()` and `getDefaultWaitStrategy()` delegate to it.
- `GenericStatefulPod` is the StatefulSet sibling. Same `ContainerDefinition` composition, but extends `StatefulSetPod` and exposes the PVC customizer for user storage configuration.

### Domain pods (`PostgreSQLPod`, `KafkaPod`)

- Still extend `StatefulSetPod`.
- Keep their own `buildMainContainer()` (opinionated, not user-shaped).
- `PostgreSQLPod` migrates its existing `start()`/`stop()` overrides (init-script ConfigMap pre-create / post-delete) into `PodLifecycleHooks.preStart()` / `preStop()` implementations — this is the proof case for the hook integration.

## Implementation sequence

Five subtasks, executed in order. Each step must compile and pass tests before moving to the next.

| Step | Task | Touched files (representative) | Verification |
|------|------|-------------------------------|--------------|
| 0 | Rename `BaseTestPod` → `BaseManagedPod` (file + class + every reference + javadoc) | `BaseTestPod.java` → `BaseManagedPod.java`; `DeploymentPod.java`, `StatefulSetPod.java`, `TestPodDefaults.java`, `pods/classes.md` | `mvn -pl core compile`, `mvn -pl core test`; `grep -r BaseTestPod src/` returns nothing |
| 1 | Create `ContainerDefinition`; refactor `GenericTestPod` → `GenericPod` to compose it; add `GenericStatefulPod` | New `ContainerDefinition.java`; `GenericTestPod.java` → `GenericPod.java`; new `GenericStatefulPod.java`; `GenericTestPodTest.java` updates | `mvn -pl core test`; existing `GenericTestPodTest` passes against the new class |
| 2 | Rename `TestPodLifecycleHooks` → `PodLifecycleHooks`, rename hook methods, integrate via `instanceof` in `BaseManagedPod.start()/stop()`; migrate `PostgreSQLPod`'s start/stop overrides into hooks | `TestPodLifecycleHooks.java` → `PodLifecycleHooks.java`; `BaseManagedPod.java`; `PostgreSQLPod.java` | `mvn -pl core test`; PostgreSQL init-script tests still pass |
| 3 | Rename `TestPod` interface (`core.pods`) → `Pod`; update all references, javadoc, and `pods/classes.md` | `TestPod.java` → `Pod.java`; all subclasses and tests; `pods/classes.md`; `Registry.java`, `FieldDeclaration.java`, `FieldInitialization.java`, etc. | `mvn -pl core compile`, `mvn -pl core test`; `grep -r 'import org.testpods.core.pods.TestPod\b' src/` returns nothing |
| 4 | Wire the existing `WorkloadManager`/`ServiceManager` into the pod hierarchy. `BaseManagedPod` gains a `workload` and `serviceMgr` field plus the full `start/stop/isRunning/isReady` algorithm. `DeploymentPod` binds `DeploymentManager` + `ClusterIPServiceManager`. `StatefulSetPod` binds `StatefulSetManager` + `HeadlessServiceManager` and feeds PVC templates from collected customizers. The kind-specific `Deployment`/`StatefulSet`/PVC/Service customizers still live on the pod classes for the user-facing API; deeper integration with the managers' customizer plumbing is out of scope. | `BaseManagedPod.java` (gains start/stop/isRunning/isReady); `DeploymentPod.java`, `StatefulSetPod.java` (slim down); no new files | `mvn -pl core test`; full suite green |

### Sequencing rationale

- Step 0 is a pure rename — lowest risk, sets the BaseManagedPod foundation.
- Step 1 extracts `ContainerDefinition` while the surrounding hierarchy is still familiar.
- Step 2 lights up the hooks before the lifecycle is restructured, so the migration of `PostgreSQLPod` validates the contract first.
- Step 3 (interface rename) is done late because it touches the widest surface area; earlier steps would have to be re-churned if it ran first.
- Step 4 (the largest behavioral change) is last, after the surrounding shape has stabilized.

## Out of scope (explicit non-goals)

- No changes to `TestPodDefaults` resolution semantics.
- No changes to `JUnit` extension wiring beyond keeping it compilable through the renames.
- No changes to the Fabric8 dependency surface.
- No retrofitting `PostgreSQLPod` / `KafkaPod` to use `ContainerDefinition`.
- No new `WaitStrategy` types.

## Risks and mitigations

| Risk | Mitigation |
|------|-----------|
| The `instanceof` pattern variable for hooks is Java 16+. | Project already uses Java 17+ syntax elsewhere (`PostgreSQLWaitStrategy`, `Provisioner`); confirm `pom.xml` source/target ≥ 17 in plan step. |
| Step 3 rename churns many test files. | Stage as a separate commit; rely on IDE-equivalent rename via `grep`+`sed`-style updates carefully reviewed. |
| Step 4 changes shipping behavior subtly (single algorithm covers both workloads). | Preserve resource ordering (workload then service, reverse on cleanup); compare against current `DeploymentPod` and `StatefulSetPod` line-by-line before merging. |
| `PostgreSQLPod.start()` currently calls `ensureNamespace()` which is commented out in `BaseTestPod`. | Note in step 2 plan — the migrated hooks must not reintroduce the dead path. |

## Verification expectations per step

Each step must end with:

1. `mvn -pl core compile` succeeds with no warnings beyond the existing baseline.
2. `mvn -pl core test` succeeds (or the failing-test set is unchanged from baseline if some tests were already failing pre-refactor — capture baseline at step 0).
3. A `grep` sanity check confirming no stragglers using the old name(s).
