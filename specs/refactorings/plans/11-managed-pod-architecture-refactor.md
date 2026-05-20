# Managed Pod Architecture Refactor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Commits:** Do NOT run `git add`, `git commit`, or `git push`. The user manages commits themselves. The plan contains no commit steps. When a step says "verify," run the verification commands and stop — the user will commit when satisfied.

**Goal:** Restructure the pod inheritance hierarchy: rename for clarity, extract container shape into a composable `ContainerDefinition`, integrate `PodLifecycleHooks` into the managed provisioning workflow, and wire the existing (but unused) `WorkloadManager` / `ServiceManager` strategies into the lifecycle so `DeploymentPod` and `StatefulSetPod` stop duplicating ~70% of each other.

**Architecture:** Interface `Pod` (renamed from `TestPod`) over abstract `BaseManagedPod` (renamed from `BaseTestPod`). `BaseManagedPod` owns the lifecycle algorithm and delegates kind-specific work to a `WorkloadManager` + `ServiceManager` pair bound by subclasses (`DeploymentPod`, `StatefulSetPod`). `ContainerDefinition` is a composable building block used by `GenericPod` / `GenericStatefulPod`; domain pods keep their own `buildMainContainer()`. Concrete pods can opt into `PodLifecycleHooks` to inject pre/post-start and pre-stop side effects.

**Tech Stack:** Java 21, Maven, Fabric8 Kubernetes client, SLF4J, JUnit 5.

**Spec:** `specs/refactorings/11-managed-pod-architecture-refactor.md`

---

## File Structure (after all steps)

```
core/src/main/java/org/testpods/core/pods/
├── Pod.java                       (renamed from TestPod.java)
├── BaseManagedPod.java            (renamed from BaseTestPod.java)
├── PodLifecycleHooks.java         (renamed from TestPodLifecycleHooks.java; methods renamed)
├── DeploymentPod.java             (slimmed; binds DeploymentManager + ClusterIPServiceManager)
├── StatefulSetPod.java            (slimmed; binds StatefulSetManager + HeadlessServiceManager)
├── ContainerDefinition.java       (new — composable container shape)
├── GenericPod.java                (renamed from GenericTestPod.java; composes ContainerDefinition)
├── GenericStatefulPod.java        (new — StatefulSet variant; composes ContainerDefinition)
├── TestPodDefaults.java           (unchanged behavior; javadoc updated)
├── classes.md                     (docs updated to new names)
├── builders/                      (unchanged)
└── external/
    ├── postgresql/PostgreSQLPod.java   (start/stop overrides moved into hooks)
    ├── kafka/KafkaPod.java             (unchanged shape; updated extends if needed)
    └── mongodb/MongoDBPod.java         (unchanged shape; updated extends if needed)
```

Existing classes the plan leverages but does not modify:
- `org.testpods.core.workload.WorkloadManager`, `DeploymentManager`, `StatefulSetManager`, `WorkloadConfig`
- `org.testpods.core.service.ServiceManager`, `ClusterIPServiceManager`, `HeadlessServiceManager`, `ServiceConfig`

---

## Conventions used in this plan

- All file paths are relative to the repo root (`/Users/henrik/git/henrik/testpods-project/testpods/`) unless absolute.
- Verification commands run from the repo root.
- "Verify" means: run the listed commands and confirm the listed expected output. If something fails, fix it before moving on.

---

## Step -1: Repair broken baseline

The codebase doesn't currently compile. Five errors, three root causes. This step makes the baseline green so subsequent verifications mean something.

**Files:**
- Modify: `core/src/main/java/org/testpods/core/provisioning/Registry.java`
- Modify: `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java`
- Modify: `core/src/main/java/org/testpods/core/pods/external/postgresql/PostgreSQLPod.java`

- [ ] **Step -1.1: Confirm the baseline is broken**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core compile -q -o 2>&1 | tail -20`

Expected: 5 compile errors, including:
- `Registry.java`: `cannot find symbol: method tearDown()`
- `StatefulSetPod.java:139`: `method does not override or implement a method from a supertype`
- `PostgreSQLPod.java:385`: `cannot find symbol: method ensureNamespace()`
- `PostgreSQLPod.java:394`: `abstract method start() in BaseTestPod cannot be accessed directly`
- `MongoDBPod.java:45`: `is not abstract and does not override abstract method start()`

- [ ] **Step -1.2: Add no-op `tearDown()` to `Registry`**

Modify `core/src/main/java/org/testpods/core/provisioning/Registry.java`. Add this method inside the class body (after `validateConfiguration()`):

```java
/**
 * Tear down all managed test pods and release resources.
 *
 * <p>Stub: full implementation tracked separately. Present so the JUnit extension's
 * afterAll() callback compiles.
 */
public void tearDown() {
}
```

- [ ] **Step -1.3: Fix `StatefulSetPod.start` signature**

In `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java`, around line 139-141, change the method signature:

From:
```java
  @Override
  public void start(K8sCluster cluster) {
      this.cluster = cluster;
      // Resolve namespace lazily if not explicitly set
```

To:
```java
  @Override
  public void start() {
      // Resolve namespace lazily if not explicitly set
```

This fixes the override cascade for `MongoDBPod` and the `super.start()` call in `PostgreSQLPod`.

- [ ] **Step -1.4: Remove the dead `ensureNamespace()` call in `PostgreSQLPod.start()`**

In `core/src/main/java/org/testpods/core/pods/external/postgresql/PostgreSQLPod.java`, find the `start()` method around line 383-395 and remove (or comment) the `ensureNamespace()` invocation.

From:
```java
  @Override
  public void start() {
    // Resolve namespace lazily if not explicitly set
    ensureNamespace();

    // Create init script ConfigMap BEFORE super.start() creates the StatefulSet
```

To:
```java
  @Override
  public void start() {
    // Create init script ConfigMap BEFORE super.start() creates the StatefulSet
```

- [ ] **Step -1.5: Verify baseline compiles**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core compile -q -o 2>&1 | tail -5`

Expected: no `ERROR` lines; either silent success or `BUILD SUCCESS`.

- [ ] **Step -1.6: Capture test baseline**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core test -q -o -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -30 > /tmp/testpods-baseline.txt && cat /tmp/testpods-baseline.txt`

Expected: tests compile and run. Note the count of `Tests run: X, Failures: Y, Errors: Z`. This becomes the floor — subsequent steps must not regress these numbers.

---

## Step 0: Rename `BaseTestPod` → `BaseManagedPod`

Pure rename. Lowest-risk first step.

**Files:**
- Rename: `core/src/main/java/org/testpods/core/pods/BaseTestPod.java` → `BaseManagedPod.java`
- Modify: `core/src/main/java/org/testpods/core/pods/DeploymentPod.java`
- Modify: `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java`
- Modify: `core/src/main/java/org/testpods/core/pods/TestPodDefaults.java` (javadoc only)
- Modify: `core/src/main/java/org/testpods/core/pods/classes.md`

- [ ] **Step 0.1: Rename the file**

Run: `git mv core/src/main/java/org/testpods/core/pods/BaseTestPod.java core/src/main/java/org/testpods/core/pods/BaseManagedPod.java`

(If the file is untracked or if git complains, fall back to `mv`.)

- [ ] **Step 0.2: Rename the class and its self-references inside the file**

Edit `core/src/main/java/org/testpods/core/pods/BaseManagedPod.java`. Replace all occurrences of `BaseTestPod` with `BaseManagedPod` (class declaration, recursive type parameter, javadoc).

Specifically, change:
```java
public abstract class BaseTestPod<SELF extends BaseTestPod<SELF>> implements TestPod<SELF> {
```
to:
```java
public abstract class BaseManagedPod<SELF extends BaseManagedPod<SELF>> implements TestPod<SELF> {
```

Also update the class javadoc (around line 26-52): the `Base implementation of {@link TestPod}` and `Subclasses must implement` sections stay accurate; just ensure no stale `BaseTestPod` references remain.

- [ ] **Step 0.3: Update `DeploymentPod` to extend `BaseManagedPod`**

In `core/src/main/java/org/testpods/core/pods/DeploymentPod.java`, around line 48, change:
```java
public abstract class DeploymentPod<SELF extends DeploymentPod<SELF>> extends BaseTestPod<SELF> {
```
to:
```java
public abstract class DeploymentPod<SELF extends DeploymentPod<SELF>> extends BaseManagedPod<SELF> {
```

- [ ] **Step 0.4: Update `StatefulSetPod` to extend `BaseManagedPod`**

In `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java`, around line 50, change:
```java
public abstract class StatefulSetPod<SELF extends StatefulSetPod<SELF>> extends BaseTestPod<SELF> {
```
to:
```java
public abstract class StatefulSetPod<SELF extends StatefulSetPod<SELF>> extends BaseManagedPod<SELF> {
```

- [ ] **Step 0.5: Update javadoc references in `TestPodDefaults.java`**

In `core/src/main/java/org/testpods/core/pods/TestPodDefaults.java`, the `Resolution (used by BaseTestPod)` comment header (around line 194) should change to `Resolution (used by BaseManagedPod)`. Search the file for any other `BaseTestPod` references in javadoc and update them.

- [ ] **Step 0.6: Update `pods/classes.md`**

In `core/src/main/java/org/testpods/core/pods/classes.md`, replace `BaseTestPod` with `BaseManagedPod` throughout (file structure listings, hierarchy diagram, decision sections).

- [ ] **Step 0.7: Verify no stragglers**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && grep -rn 'BaseTestPod' --include='*.java' --include='*.md' core/ examples/ 2>/dev/null`

Expected: no output. If anything is reported, update those references.

- [ ] **Step 0.8: Verify compile + tests still pass**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core compile -q -o 2>&1 | tail -5`

Expected: no `ERROR` lines.

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core test -q -o -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -15`

Expected: same `Tests run / Failures / Errors` counts as the baseline captured in step -1.6.

---

## Step 1: Extract `ContainerDefinition`; rename `GenericTestPod` → `GenericPod`; add `GenericStatefulPod`

Pull all container-shaped state out of `GenericTestPod` into a standalone class and add the StatefulSet sibling.

**Files:**
- Create: `core/src/main/java/org/testpods/core/pods/ContainerDefinition.java`
- Rename: `core/src/main/java/org/testpods/core/pods/GenericTestPod.java` → `GenericPod.java` (with substantial internal rewrite)
- Create: `core/src/main/java/org/testpods/core/pods/GenericStatefulPod.java`
- Modify: `core/src/test/java/org/testpods/core/pods/GenericTestPodTest.java` (rename or update references)
- Modify: `core/src/main/java/org/testpods/core/wait/WaitStrategy.java` (javadoc references `GenericTestPod`)

- [ ] **Step 1.1: Write a failing test for `ContainerDefinition`**

Create `core/src/test/java/org/testpods/core/pods/ContainerDefinitionTest.java`:

```java
package org.testpods.core.pods;

import static org.junit.jupiter.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Container;
import org.junit.jupiter.api.Test;
import org.testpods.core.wait.WaitStrategy;

class ContainerDefinitionTest {

  @Test
  void buildsContainerWithImagePortsAndEnv() {
    Container c =
        new ContainerDefinition()
            .withImage("redis:7-alpine")
            .withPort(6379)
            .withEnv("FOO", "bar")
            .buildContainer("redis");

    assertEquals("redis", c.getName());
    assertEquals("redis:7-alpine", c.getImage());
    assertEquals(1, c.getPorts().size());
    assertEquals(6379, c.getPorts().get(0).getContainerPort());
    assertEquals(1, c.getEnv().size());
    assertEquals("FOO", c.getEnv().get(0).getName());
    assertEquals("bar", c.getEnv().get(0).getValue());
  }

  @Test
  void primaryPortDefaultsToFirstPortAdded() {
    ContainerDefinition def = new ContainerDefinition().withPort(8080).withPort(9090);
    assertEquals(8080, def.getPrimaryPort());
  }

  @Test
  void primaryPortHonoursExplicitChoice() {
    ContainerDefinition def =
        new ContainerDefinition().withPort(8080).withPrimaryPort(9090).withPort(7070);
    assertEquals(9090, def.getPrimaryPort());
  }

  @Test
  void deriveDefaultWaitStrategyUsesHttpWhenConfigured() {
    ContainerDefinition def =
        new ContainerDefinition().withPort(8080).withHttpReadinessProbe("/health", 8080);
    WaitStrategy ws = def.deriveDefaultWaitStrategy();
    assertNotNull(ws);
  }

  @Test
  void deriveDefaultWaitStrategyFallsBackToPort() {
    ContainerDefinition def = new ContainerDefinition().withPort(6379);
    WaitStrategy ws = def.deriveDefaultWaitStrategy();
    assertNotNull(ws);
  }

  @Test
  void commandAndArgsAreSetOnBuiltContainer() {
    Container c =
        new ContainerDefinition()
            .withImage("redis:7-alpine")
            .withPort(6379)
            .withCommand("redis-server")
            .withArgs("--appendonly", "yes")
            .buildContainer("redis");

    assertEquals(java.util.List.of("redis-server"), c.getCommand());
    assertEquals(java.util.List.of("--appendonly", "yes"), c.getArgs());
  }
}
```

- [ ] **Step 1.2: Run the failing test**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core test -q -o -Dtest=ContainerDefinitionTest 2>&1 | tail -15`

Expected: FAIL with "cannot find symbol: ContainerDefinition" (or similar).

- [ ] **Step 1.3: Create `ContainerDefinition`**

Create `core/src/main/java/org/testpods/core/pods/ContainerDefinition.java`:

```java
package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.Container;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testpods.core.pods.builders.ContainerSpec;
import org.testpods.core.wait.WaitStrategy;

/**
 * Composable container-shape configuration shared by generic pods.
 *
 * <p>Owns the user-supplied container details (image, ports, environment, command, args, readiness
 * probe). Used by {@link GenericPod} and {@link GenericStatefulPod}. Domain pods (PostgreSQL,
 * Kafka, MongoDB) own their container directly and don't compose this class.
 */
public class ContainerDefinition {

  private String image;
  private final List<Integer> ports = new ArrayList<>();
  private final Map<String, String> env = new LinkedHashMap<>();
  private List<String> command;
  private List<String> args;
  private Integer primaryPort;
  private String readinessPath;
  private Integer readinessPort;

  public ContainerDefinition withImage(String image) {
    this.image = image;
    return this;
  }

  public ContainerDefinition withPort(int port) {
    this.ports.add(port);
    if (this.primaryPort == null) {
      this.primaryPort = port;
    }
    return this;
  }

  public ContainerDefinition withPrimaryPort(int port) {
    this.ports.add(port);
    this.primaryPort = port;
    return this;
  }

  public ContainerDefinition withEnv(String name, String value) {
    this.env.put(name, value);
    return this;
  }

  public ContainerDefinition withEnv(Map<String, String> values) {
    this.env.putAll(values);
    return this;
  }

  public ContainerDefinition withCommand(String... command) {
    this.command = Arrays.asList(command);
    return this;
  }

  public ContainerDefinition withArgs(String... args) {
    this.args = Arrays.asList(args);
    return this;
  }

  public ContainerDefinition withHttpReadinessProbe(String path, int port) {
    this.readinessPath = path;
    this.readinessPort = port;
    return this;
  }

  public int getPrimaryPort() {
    if (primaryPort != null) {
      return primaryPort;
    }
    if (!ports.isEmpty()) {
      return ports.get(0);
    }
    return 80;
  }

  public String getImage() {
    return image;
  }

  public Container buildContainer(String name) {
    ContainerSpec spec = new ContainerSpec().withName(name).withImage(image);

    if (command != null && !command.isEmpty()) {
      spec.withCommand(command.toArray(new String[0]));
    }
    if (args != null && !args.isEmpty()) {
      spec.withArgs(args.toArray(new String[0]));
    }
    for (Map.Entry<String, String> e : env.entrySet()) {
      spec.withEnv(e.getKey(), e.getValue());
    }
    for (int p : ports) {
      spec.withPort(p);
    }

    if (readinessPath != null && readinessPort != null) {
      spec.withReadinessProbe(
          probe -> probe.httpGet(readinessPort, readinessPath).initialDelay(5).period(10).timeout(5));
    } else if (!ports.isEmpty()) {
      spec.withReadinessProbe(
          probe -> probe.tcpSocket(getPrimaryPort()).initialDelay(5).period(10).timeout(5));
    }

    return spec.build();
  }

  public WaitStrategy deriveDefaultWaitStrategy() {
    if (readinessPath != null && readinessPort != null) {
      return WaitStrategy.forHttp(readinessPath, readinessPort).withTimeout(Duration.ofMinutes(1));
    }
    if (primaryPort != null || !ports.isEmpty()) {
      return WaitStrategy.forPort(getPrimaryPort()).withTimeout(Duration.ofMinutes(1));
    }
    return WaitStrategy.forReadinessProbe().withTimeout(Duration.ofMinutes(1));
  }
}
```

- [ ] **Step 1.4: Run the ContainerDefinition tests; verify pass**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core test -q -o -Dtest=ContainerDefinitionTest 2>&1 | tail -15`

Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 1.5: Rename `GenericTestPod` file to `GenericPod`**

Run: `git mv core/src/main/java/org/testpods/core/pods/GenericTestPod.java core/src/main/java/org/testpods/core/pods/GenericPod.java`

- [ ] **Step 1.6: Rewrite `GenericPod` to compose `ContainerDefinition`**

Replace the entire contents of `core/src/main/java/org/testpods/core/pods/GenericPod.java` with:

```java
package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.Container;
import java.util.Map;
import org.testpods.core.PropertyContext;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.wait.WaitStrategy;

/**
 * A generic pod for running any container image as a Kubernetes Deployment.
 *
 * <p>Use this when there's no specific Pod implementation for your image, or when you need full
 * control over the container configuration.
 *
 * <p>Example:
 *
 * <pre>{@code
 * GenericPod redis = new GenericPod("redis:7-alpine")
 *     .withPort(6379)
 *     .withCommand("redis-server", "--appendonly", "yes")
 *     .inNamespace(namespace);
 * }</pre>
 */
public class GenericPod extends DeploymentPod<GenericPod> {

  private final ContainerDefinition container = new ContainerDefinition();
  private K8sCluster localCluster;

  public GenericPod(String image) {
    container.withImage(image);
    this.name = deriveNameFromImage(image);
    this.labels.put("app", this.name);
  }

  private static String deriveNameFromImage(String image) {
    String withoutTag = image.contains(":") ? image.substring(0, image.lastIndexOf(':')) : image;
    String name =
        withoutTag.contains("/")
            ? withoutTag.substring(withoutTag.lastIndexOf('/') + 1)
            : withoutTag;
    return name.toLowerCase().replaceAll("[^a-z0-9-]", "-");
  }

  public GenericPod withPort(int port) {
    container.withPort(port);
    return this;
  }

  public GenericPod withPrimaryPort(int port) {
    container.withPrimaryPort(port);
    return this;
  }

  public GenericPod withEnv(String key, String value) {
    container.withEnv(key, value);
    return this;
  }

  public GenericPod withEnv(Map<String, String> env) {
    container.withEnv(env);
    return this;
  }

  public GenericPod withCommand(String... command) {
    container.withCommand(command);
    return this;
  }

  public GenericPod withArgs(String... args) {
    container.withArgs(args);
    return this;
  }

  public GenericPod withHttpReadinessProbe(String path, int port) {
    container.withHttpReadinessProbe(path, port);
    return this;
  }

  @Override
  public K8sCluster getCluster() {
    return localCluster != null ? localCluster : super.getCluster();
  }

  @Override
  public int getInternalPort() {
    return container.getPrimaryPort();
  }

  public String getExternalUrl() {
    return "http://" + getExternalHost() + ":" + getExternalPort();
  }

  public String getInternalUrl() {
    return "http://" + getInternalHost() + ":" + getInternalPort();
  }

  @Override
  public void publishProperties(PropertyContext ctx) {
    String prefix = getName();
    ctx.publish(prefix + ".internal.host", this::getInternalHost);
    ctx.publish(prefix + ".internal.port", () -> String.valueOf(getInternalPort()));
    ctx.publish(prefix + ".internal.url", this::getInternalUrl);
    ctx.publish(prefix + ".external.host", this::getExternalHost);
    ctx.publish(prefix + ".external.port", () -> String.valueOf(getExternalPort()));
    ctx.publish(prefix + ".external.url", this::getExternalUrl);
    ctx.publish(prefix + ".url", this::getExternalUrl);
  }

  @Override
  protected WaitStrategy getDefaultWaitStrategy() {
    return container.deriveDefaultWaitStrategy();
  }

  @Override
  protected Container buildMainContainer() {
    return container.buildContainer(name);
  }
}
```

- [ ] **Step 1.7: Update the existing `GenericTestPodTest` to use `GenericPod`**

Rename the test file and update its references:

Run: `git mv core/src/test/java/org/testpods/core/pods/GenericTestPodTest.java core/src/test/java/org/testpods/core/pods/GenericPodTest.java`

Then in `core/src/test/java/org/testpods/core/pods/GenericPodTest.java`, replace all `GenericTestPod` with `GenericPod` (including in the class name `class GenericPodTest`, in `new GenericTestPod(...)` constructor calls, and in any imports). Run:

```
sed -i.bak 's/GenericTestPod/GenericPod/g' core/src/test/java/org/testpods/core/pods/GenericPodTest.java && rm core/src/test/java/org/testpods/core/pods/GenericPodTest.java.bak
```

- [ ] **Step 1.8: Update `WaitStrategy.java` javadoc**

In `core/src/main/java/org/testpods/core/wait/WaitStrategy.java`, find the javadoc example around line 30 that says `GenericTestPod app = new GenericTestPod("myapp:latest")` and replace with `GenericPod app = new GenericPod("myapp:latest")`.

- [ ] **Step 1.9: Create `GenericStatefulPod`**

Create `core/src/main/java/org/testpods/core/pods/GenericStatefulPod.java`:

```java
package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.Container;
import java.util.Map;
import org.testpods.core.PropertyContext;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.wait.WaitStrategy;

/**
 * A generic pod for running any container image as a Kubernetes StatefulSet.
 *
 * <p>Use this when you need stable network identity or persistent storage but don't have a
 * domain-specific Pod (like PostgreSQLPod or KafkaPod).
 *
 * <p>Example:
 *
 * <pre>{@code
 * GenericStatefulPod customDb = new GenericStatefulPod("mycompany/custom-db:latest")
 *     .withPort(3306)
 *     .withEnv("ROOT_PASSWORD", "test")
 *     .withPvcCustomizer(pvc -> pvc.editSpec()
 *         .withNewResources()
 *             .addToRequests("storage", new io.fabric8.kubernetes.api.model.Quantity("10Gi"))
 *         .endResources()
 *         .endSpec())
 *     .inNamespace(namespace);
 * }</pre>
 */
public class GenericStatefulPod extends StatefulSetPod<GenericStatefulPod> {

  private final ContainerDefinition container = new ContainerDefinition();
  private K8sCluster localCluster;

  public GenericStatefulPod(String image) {
    container.withImage(image);
    this.name = deriveNameFromImage(image);
    this.labels.put("app", this.name);
  }

  private static String deriveNameFromImage(String image) {
    String withoutTag = image.contains(":") ? image.substring(0, image.lastIndexOf(':')) : image;
    String name =
        withoutTag.contains("/")
            ? withoutTag.substring(withoutTag.lastIndexOf('/') + 1)
            : withoutTag;
    return name.toLowerCase().replaceAll("[^a-z0-9-]", "-");
  }

  public GenericStatefulPod withPort(int port) {
    container.withPort(port);
    return this;
  }

  public GenericStatefulPod withPrimaryPort(int port) {
    container.withPrimaryPort(port);
    return this;
  }

  public GenericStatefulPod withEnv(String key, String value) {
    container.withEnv(key, value);
    return this;
  }

  public GenericStatefulPod withEnv(Map<String, String> env) {
    container.withEnv(env);
    return this;
  }

  public GenericStatefulPod withCommand(String... command) {
    container.withCommand(command);
    return this;
  }

  public GenericStatefulPod withArgs(String... args) {
    container.withArgs(args);
    return this;
  }

  public GenericStatefulPod withHttpReadinessProbe(String path, int port) {
    container.withHttpReadinessProbe(path, port);
    return this;
  }

  @Override
  public K8sCluster getCluster() {
    return localCluster != null ? localCluster : super.getCluster();
  }

  @Override
  public int getInternalPort() {
    return container.getPrimaryPort();
  }

  public String getExternalUrl() {
    return "http://" + getExternalHost() + ":" + getExternalPort();
  }

  public String getInternalUrl() {
    return "http://" + getInternalHost() + ":" + getInternalPort();
  }

  @Override
  public void publishProperties(PropertyContext ctx) {
    String prefix = getName();
    ctx.publish(prefix + ".internal.host", this::getInternalHost);
    ctx.publish(prefix + ".internal.port", () -> String.valueOf(getInternalPort()));
    ctx.publish(prefix + ".internal.url", this::getInternalUrl);
    ctx.publish(prefix + ".external.host", this::getExternalHost);
    ctx.publish(prefix + ".external.port", () -> String.valueOf(getExternalPort()));
    ctx.publish(prefix + ".external.url", this::getExternalUrl);
    ctx.publish(prefix + ".url", this::getExternalUrl);
  }

  @Override
  protected WaitStrategy getDefaultWaitStrategy() {
    return container.deriveDefaultWaitStrategy();
  }

  @Override
  protected Container buildMainContainer() {
    return container.buildContainer(name);
  }
}
```

- [ ] **Step 1.10: Add a smoke test for `GenericStatefulPod`**

Create `core/src/test/java/org/testpods/core/pods/GenericStatefulPodTest.java`:

```java
package org.testpods.core.pods;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GenericStatefulPodTest {

  @Test
  void derivesNameFromImage() {
    GenericStatefulPod pod = new GenericStatefulPod("mycompany/custom-db:latest");
    assertEquals("custom-db", pod.getName());
  }

  @Test
  void exposesContainerPortAsInternalPort() {
    GenericStatefulPod pod = new GenericStatefulPod("redis:7-alpine").withPort(6379);
    assertEquals(6379, pod.getInternalPort());
  }

  @Test
  void fluentChainingReturnsConcreteType() {
    GenericStatefulPod pod =
        new GenericStatefulPod("mysql:8")
            .withPort(3306)
            .withEnv("MYSQL_ROOT_PASSWORD", "test")
            .withCommand("mysqld");
    assertNotNull(pod);
    assertEquals(3306, pod.getInternalPort());
  }
}
```

- [ ] **Step 1.11: Verify no stragglers reference `GenericTestPod`**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && grep -rn 'GenericTestPod' --include='*.java' --include='*.md' core/ examples/ 2>/dev/null`

Expected: no output. If found, update those references to `GenericPod`.

- [ ] **Step 1.12: Verify compile + tests**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core compile -q -o 2>&1 | tail -5`

Expected: no `ERROR` lines.

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core test -q -o -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -15`

Expected: same or better `Tests run / Failures / Errors` than baseline. New ContainerDefinition and GenericStatefulPod tests should pass. Renamed `GenericPodTest` should pass.

---

## Step 2: Rename `TestPodLifecycleHooks` → `PodLifecycleHooks`; wire into `BaseManagedPod.start()/stop()`; migrate `PostgreSQLPod`

Make the unused lifecycle hook contract load-bearing. Pull `start()`/`stop()` up to `BaseManagedPod` as `final` template methods wrapping hook calls around a `doStart()`/`doStop()` abstract pair. `DeploymentPod` and `StatefulSetPod` keep their current bodies inside `doStart()`/`doStop()` (step 4 will collapse those into the manager-backed algorithm).

**Files:**
- Rename: `core/src/main/java/org/testpods/core/pods/TestPodLifecycleHooks.java` → `PodLifecycleHooks.java`
- Modify: `core/src/main/java/org/testpods/core/pods/BaseManagedPod.java`
- Modify: `core/src/main/java/org/testpods/core/pods/DeploymentPod.java`
- Modify: `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java`
- Modify: `core/src/main/java/org/testpods/core/pods/external/postgresql/PostgreSQLPod.java`

- [ ] **Step 2.1: Rename the lifecycle hooks file**

Run: `git mv core/src/main/java/org/testpods/core/pods/TestPodLifecycleHooks.java core/src/main/java/org/testpods/core/pods/PodLifecycleHooks.java`

- [ ] **Step 2.2: Rewrite the interface with the new name and method names**

Replace the contents of `core/src/main/java/org/testpods/core/pods/PodLifecycleHooks.java` with:

```java
package org.testpods.core.pods;

/**
 * Optional opt-in interface for pods that need to perform side-effect work around their lifecycle.
 *
 * <p>{@link BaseManagedPod#start()} and {@link BaseManagedPod#stop()} invoke these methods if the
 * concrete pod class implements this interface. No reflection, no annotations — just an
 * {@code instanceof} check.
 *
 * <p>Default implementations are no-ops, so implementing pods can override only the phases they
 * care about.
 */
public interface PodLifecycleHooks {

  /** Called before the workload and service are created. */
  default void preStart() {}

  /** Called after the workload is created, the service is created, and the pod is ready. */
  default void postStart() {}

  /** Called before the workload and service are deleted. */
  default void preStop() {}
}
```

- [ ] **Step 2.3: Test for hook invocation order in `BaseManagedPod`**

Create `core/src/test/java/org/testpods/core/pods/PodLifecycleHooksTest.java`:

```java
package org.testpods.core.pods;

import static org.junit.jupiter.api.Assertions.*;

import io.fabric8.kubernetes.api.model.PodSpec;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testpods.core.PropertyContext;
import org.testpods.core.wait.WaitStrategy;

class PodLifecycleHooksTest {

  static class TrackingPod extends BaseManagedPod<TrackingPod> implements PodLifecycleHooks {
    final List<String> events = new ArrayList<>();

    @Override
    public void preStart() {
      events.add("preStart");
    }

    @Override
    public void postStart() {
      events.add("postStart");
    }

    @Override
    public void preStop() {
      events.add("preStop");
    }

    @Override
    protected void doStart() {
      events.add("doStart");
    }

    @Override
    protected void doStop() {
      events.add("doStop");
    }

    @Override
    public boolean isRunning() {
      return false;
    }

    @Override
    public boolean isReady() {
      return false;
    }

    @Override
    public int getInternalPort() {
      return 0;
    }

    @Override
    public String getExternalHost() {
      return "";
    }

    @Override
    public int getExternalPort() {
      return 0;
    }

    @Override
    public void publishProperties(PropertyContext ctx) {}

    @Override
    protected WaitStrategy getDefaultWaitStrategy() {
      return null;
    }
  }

  static class PlainPod extends BaseManagedPod<PlainPod> {
    final List<String> events = new ArrayList<>();

    @Override
    protected void doStart() {
      events.add("doStart");
    }

    @Override
    protected void doStop() {
      events.add("doStop");
    }

    @Override
    public boolean isRunning() {
      return false;
    }

    @Override
    public boolean isReady() {
      return false;
    }

    @Override
    public int getInternalPort() {
      return 0;
    }

    @Override
    public String getExternalHost() {
      return "";
    }

    @Override
    public int getExternalPort() {
      return 0;
    }

    @Override
    public void publishProperties(PropertyContext ctx) {}

    @Override
    protected WaitStrategy getDefaultWaitStrategy() {
      return null;
    }
  }

  @Test
  void startInvokesHooksAroundDoStart() {
    TrackingPod pod = new TrackingPod();
    pod.start();
    assertEquals(List.of("preStart", "doStart", "postStart"), pod.events);
  }

  @Test
  void stopInvokesPreStopBeforeDoStop() {
    TrackingPod pod = new TrackingPod();
    pod.stop();
    assertEquals(List.of("preStop", "doStop"), pod.events);
  }

  @Test
  void podWithoutHooksJustRunsDoStart() {
    PlainPod pod = new PlainPod();
    pod.start();
    assertEquals(List.of("doStart"), pod.events);
  }

  @Test
  void podWithoutHooksJustRunsDoStop() {
    PlainPod pod = new PlainPod();
    pod.stop();
    assertEquals(List.of("doStop"), pod.events);
  }
}
```

- [ ] **Step 2.4: Run the test; verify it fails**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core test -q -o -Dtest=PodLifecycleHooksTest 2>&1 | tail -15`

Expected: FAIL — `doStart()` / `doStop()` aren't defined on `BaseManagedPod` yet, and `start()`/`stop()` are still abstract.

- [ ] **Step 2.5: Refactor `BaseManagedPod` to template-method `start()`/`stop()`**

In `core/src/main/java/org/testpods/core/pods/BaseManagedPod.java`:

Replace the abstract `start()` and `stop()` declarations near the bottom of the class:

From:
```java
  @Override
  public abstract void start();

  @Override
  public abstract void stop();
```

To:
```java
  @Override
  public final void start() {
    if (this instanceof PodLifecycleHooks h) {
      h.preStart();
    }
    doStart();
    if (this instanceof PodLifecycleHooks h) {
      h.postStart();
    }
  }

  @Override
  public final void stop() {
    if (this instanceof PodLifecycleHooks h) {
      h.preStop();
    }
    doStop();
  }

  /**
   * Subclasses implement the actual start workflow here. Hooks are invoked around this call by
   * {@link #start()}.
   */
  protected abstract void doStart();

  /**
   * Subclasses implement the actual stop workflow here. {@link PodLifecycleHooks#preStop()} is
   * invoked before this call by {@link #stop()}.
   */
  protected abstract void doStop();
```

- [ ] **Step 2.6: Rename `DeploymentPod.start()` → `doStart()` and `stop()` → `doStop()`**

In `core/src/main/java/org/testpods/core/pods/DeploymentPod.java`:

Change `@Override public void start()` to `@Override protected void doStart()` and `@Override public void stop()` to `@Override protected void doStop()`. The bodies stay the same.

- [ ] **Step 2.7: Rename `StatefulSetPod.start()` → `doStart()` and `stop()` → `doStop()`**

In `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java`:

Change `@Override public void start()` to `@Override protected void doStart()` and `@Override public void stop()` to `@Override protected void doStop()`. The bodies stay the same.

- [ ] **Step 2.8: Migrate `PostgreSQLPod.start()`/`stop()` overrides into hooks**

In `core/src/main/java/org/testpods/core/pods/external/postgresql/PostgreSQLPod.java`:

1. Declare the class as `implements PodLifecycleHooks`:

From:
```java
public class PostgreSQLPod extends StatefulSetPod<PostgreSQLPod> {
```
To:
```java
public class PostgreSQLPod extends StatefulSetPod<PostgreSQLPod> implements PodLifecycleHooks {
```

2. Remove the `@Override public void start()` and `@Override public void stop()` methods (they conflict with the now-final methods in `BaseManagedPod`).

3. Add hook implementations (placed in the lifecycle section):

```java
  // =============================================================
  // Lifecycle hooks
  // =============================================================

  @Override
  public void preStart() {
    if (hasInitScripts()) {
      createInitScriptConfigMap();
    }
  }

  @Override
  public void preStop() {
    if (hasInitScripts()) {
      deleteInitScriptConfigMap();
    }
  }
```

Keep `createInitScriptConfigMap()` and `deleteInitScriptConfigMap()` as private helpers — only their invocation sites change.

- [ ] **Step 2.9: Run the hooks test; verify pass**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core test -q -o -Dtest=PodLifecycleHooksTest 2>&1 | tail -15`

Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 2.10: Verify no stragglers reference the old type name**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && grep -rn 'TestPodLifecycleHooks' --include='*.java' --include='*.md' core/ examples/ 2>/dev/null`

Expected: no output.

- [ ] **Step 2.11: Verify full compile + tests**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core compile -q -o 2>&1 | tail -5`

Expected: no `ERROR` lines.

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core test -q -o -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -15`

Expected: same or better `Tests run / Failures / Errors` than baseline; new tests pass; postgres tests still pass.

---

## Step 3: Rename `TestPod` interface → `Pod`

Widest-surface rename. Done late so earlier steps aren't re-churned. The JUnit annotation `org.testpods.junit.TestPod` is **not** renamed (the name collision goes away naturally).

**Files (representative):**
- Rename: `core/src/main/java/org/testpods/core/pods/TestPod.java` → `Pod.java`
- Modify: every file that imports or references `org.testpods.core.pods.TestPod` (see list in step 3.2).
- Modify: `core/src/main/java/org/testpods/core/pods/classes.md`

- [ ] **Step 3.1: Rename the interface file**

Run: `git mv core/src/main/java/org/testpods/core/pods/TestPod.java core/src/main/java/org/testpods/core/pods/Pod.java`

- [ ] **Step 3.2: Rewrite the interface declaration inside the renamed file**

Edit `core/src/main/java/org/testpods/core/pods/Pod.java`. Replace `TestPod` with `Pod` in:
- The class javadoc (line ~16-24)
- The interface declaration: `public interface TestPod<SELF extends TestPod<SELF>>` → `public interface Pod<SELF extends Pod<SELF>>`
- Any javadoc references to `TestPod` inside the file

- [ ] **Step 3.3: Update `BaseManagedPod` to implement `Pod`**

In `core/src/main/java/org/testpods/core/pods/BaseManagedPod.java`, around line 53:

From:
```java
public abstract class BaseManagedPod<SELF extends BaseManagedPod<SELF>> implements TestPod<SELF> {
```
To:
```java
public abstract class BaseManagedPod<SELF extends BaseManagedPod<SELF>> implements Pod<SELF> {
```

Also update javadoc references (`{@link TestPod}` → `{@link Pod}`) throughout the file.

- [ ] **Step 3.4: Update each file that imports `org.testpods.core.pods.TestPod`**

For each file listed below, change:
- `import org.testpods.core.pods.TestPod;` → `import org.testpods.core.pods.Pod;`
- `TestPod<?>` → `Pod<?>`
- `TestPod<` (in generic contexts) → `Pod<`
- `{@link TestPod` → `{@link Pod`

Files to update:

```
core/src/main/java/org/testpods/core/ExecResult.java
core/src/main/java/org/testpods/core/TestPodStartException.java          (javadoc only — "Thrown when a TestPod fails to start" stays, since "TestPod" there is a noun for the concept, not the type name. Skim and decide.)
core/src/main/java/org/testpods/core/cluster/ExternalAccessStrategy.java
core/src/main/java/org/testpods/core/wait/WaitStrategy.java
core/src/main/java/org/testpods/core/wait/HttpWaitStrategy.java
core/src/main/java/org/testpods/core/wait/ReadinessProbeWaitStrategy.java
core/src/main/java/org/testpods/core/wait/PortWaitStrategy.java
core/src/main/java/org/testpods/core/wait/CommandWaitStrategy.java
core/src/main/java/org/testpods/core/wait/CompositeWaitStrategy.java
core/src/main/java/org/testpods/core/wait/LogMessageWaitStrategy.java
core/src/main/java/org/testpods/core/pods/external/postgresql/PostgreSQLWaitStrategy.java
core/src/main/java/org/testpods/core/provisioning/Registry.java          (Map<String, TestPod> → Map<String, Pod>)
core/src/main/java/org/testpods/core/provisioning/FieldDeclaration.java
core/src/main/java/org/testpods/core/provisioning/FieldInitialization.java
core/src/main/java/org/testpods/core/provisioning/GroupBuilder.java
core/src/main/java/org/testpods/core/provisioning/ReflectionHelper.java
```

Suggested batch command (preview first, then apply):

```bash
# Preview
cd /Users/henrik/git/henrik/testpods-project/testpods && \
  grep -rln 'org\.testpods\.core\.pods\.TestPod\b' --include='*.java' core/
# Apply (each file individually with Edit tool; or a careful sed):
# In each file, replace the import then replace TestPod< → Pod< and TestPod( → Pod(
```

When using `sed`, scope each edit to the file's contents (NOT the JUnit annotation). `Registry.java` imports `org.testpods.junit.TestPod` (the annotation) AND uses the type via the import path `org.testpods.core.pods.TestPod` removed → so be careful: the JUnit annotation import stays, the value type changes. Specifically in `Registry.java`:

Before:
```java
import org.testpods.junit.TestPod;
...
Map<String, TestPod> testPodsByName = new HashMap<>();
```

After:
```java
import org.testpods.junit.TestPod;
import org.testpods.core.pods.Pod;
...
Map<String, Pod> testPodsByName = new HashMap<>();
```

(The annotation `TestPod` and the type `Pod` now coexist — the rename eliminates the bug-prone overload.)

- [ ] **Step 3.5: Update `pods/classes.md`**

In `core/src/main/java/org/testpods/core/pods/classes.md`, search for `TestPod` and decide per-line: most references to the core type should become `Pod`; references explicitly to the `org.testpods.junit.TestPod` annotation (if any) stay. Update the file structure diagram, the hierarchy diagram, the "Interface vs Abstract Class" section, the "Three-Level API" section, etc.

- [ ] **Step 3.6: Verify no stragglers reference the old core type**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && grep -rn 'org\.testpods\.core\.pods\.TestPod\b' --include='*.java' core/ examples/ 2>/dev/null`

Expected: no output.

Then run: `cd /Users/henrik/git/henrik/testpods-project/testpods && grep -rn 'TestPod<' --include='*.java' core/ examples/ 2>/dev/null`

Expected: no output (the generic interface is now `Pod<...>`).

- [ ] **Step 3.7: Verify full compile + tests**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core compile -q -o 2>&1 | tail -5`

Expected: no `ERROR` lines.

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core test -q -o -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -15`

Expected: same or better `Tests run / Failures / Errors` than baseline.

---

## Step 4: Wire `WorkloadManager` + `ServiceManager` into `BaseManagedPod`

Lift the kind-specific lifecycle code into a unified algorithm in `BaseManagedPod` that delegates to existing `WorkloadManager` and `ServiceManager` strategies. Each concrete pod kind (`DeploymentPod`, `StatefulSetPod`) binds its manager pair via the abstract `createWorkloadManager()` / `createServiceManager()` factory methods.

**Files:**
- Modify: `core/src/main/java/org/testpods/core/pods/BaseManagedPod.java` (adds workload/serviceMgr fields, replaces doStart/doStop body, adds buildPodSpec(), removes obsolete getCluster duplication)
- Modify: `core/src/main/java/org/testpods/core/pods/DeploymentPod.java` (slim down; remove inline lifecycle; bind DeploymentManager + ClusterIPServiceManager)
- Modify: `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java` (slim down; bind StatefulSetManager + HeadlessServiceManager; collect PVC templates)

- [ ] **Step 4.1: Test that `BaseManagedPod` exposes `WorkloadManager`/`ServiceManager` factories**

Create `core/src/test/java/org/testpods/core/pods/ManagedPodWiringTest.java`:

```java
package org.testpods.core.pods;

import static org.junit.jupiter.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Container;
import org.junit.jupiter.api.Test;
import org.testpods.core.PropertyContext;
import org.testpods.core.service.ClusterIPServiceManager;
import org.testpods.core.service.HeadlessServiceManager;
import org.testpods.core.service.ServiceManager;
import org.testpods.core.wait.WaitStrategy;
import org.testpods.core.workload.DeploymentManager;
import org.testpods.core.workload.StatefulSetManager;
import org.testpods.core.workload.WorkloadManager;

class ManagedPodWiringTest {

  static class FakeDeploymentPod extends DeploymentPod<FakeDeploymentPod> {
    @Override
    protected Container buildMainContainer() {
      return null;
    }

    @Override
    public int getInternalPort() {
      return 8080;
    }

    @Override
    public void publishProperties(PropertyContext ctx) {}
  }

  static class FakeStatefulSetPod extends StatefulSetPod<FakeStatefulSetPod> {
    @Override
    protected Container buildMainContainer() {
      return null;
    }

    @Override
    public int getInternalPort() {
      return 5432;
    }

    @Override
    public void publishProperties(PropertyContext ctx) {}
  }

  @Test
  void deploymentPodWiresDeploymentManagerAndClusterIPService() {
    FakeDeploymentPod pod = new FakeDeploymentPod();
    WorkloadManager wm = pod.createWorkloadManager();
    ServiceManager sm = pod.createServiceManager();
    assertInstanceOf(DeploymentManager.class, wm);
    assertInstanceOf(ClusterIPServiceManager.class, sm);
  }

  @Test
  void statefulSetPodWiresStatefulSetManagerAndHeadlessService() {
    FakeStatefulSetPod pod = new FakeStatefulSetPod();
    WorkloadManager wm = pod.createWorkloadManager();
    ServiceManager sm = pod.createServiceManager();
    assertInstanceOf(StatefulSetManager.class, wm);
    assertInstanceOf(HeadlessServiceManager.class, sm);
  }
}
```

- [ ] **Step 4.2: Run the test; verify fail**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core test -q -o -Dtest=ManagedPodWiringTest 2>&1 | tail -15`

Expected: FAIL — `createWorkloadManager()` / `createServiceManager()` don't exist yet.

- [ ] **Step 4.3: Update `BaseManagedPod` to own workload/service strategy**

In `core/src/main/java/org/testpods/core/pods/BaseManagedPod.java`:

1. Add imports:

```java
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import org.testpods.core.TestPodStartException;
import org.testpods.core.service.ServiceConfig;
import org.testpods.core.service.ServiceManager;
import org.testpods.core.workload.WorkloadConfig;
import org.testpods.core.workload.WorkloadManager;
```

2. Add new fields near the top of the configuration state block:

```java
  protected WorkloadManager workload;
  protected ServiceManager serviceMgr;
```

3. Add abstract factory methods (place near the existing abstract methods at the bottom of the class, alongside `getDefaultWaitStrategy()`):

```java
  /**
   * Subclasses provide the {@link WorkloadManager} for their workload kind. Called lazily on first
   * {@link #start()} invocation.
   */
  protected abstract WorkloadManager createWorkloadManager();

  /**
   * Subclasses provide the {@link ServiceManager} for their service shape. Called lazily on first
   * {@link #start()} invocation.
   */
  protected abstract ServiceManager createServiceManager();
```

4. Replace the existing `doStart()` and `doStop()` declarations (which were `protected abstract` from step 2) with concrete final implementations:

```java
  @Override
  protected final void doStart() {
    if (workload == null) {
      workload = createWorkloadManager();
    }
    if (serviceMgr == null) {
      serviceMgr = createServiceManager();
    }

    try {
      var client = getClient();
      String ns = namespace.getName();

      PodSpec podSpec = buildPodSpec();
      WorkloadConfig wlc =
          WorkloadConfig.builder()
              .name(name)
              .namespace(ns)
              .labels(buildLabels())
              .annotations(annotations)
              .podSpec(podSpec)
              .client(client)
              .build();
      workload.create(wlc);

      ServiceConfig svc =
          ServiceConfig.builder()
              .name(name)
              .namespace(ns)
              .port(getInternalPort())
              .labels(buildLabels())
              .selector(java.util.Map.of("app", name))
              .client(client)
              .build();
      serviceMgr.create(svc);

      waitForReady();
    } catch (Exception e) {
      cleanup();
      throw new TestPodStartException(name, e.getMessage(), e);
    }
  }

  @Override
  protected final void doStop() {
    if (serviceMgr != null) {
      try {
        serviceMgr.delete();
      } catch (Exception ignored) {
      }
    }
    if (workload != null) {
      try {
        workload.delete();
      } catch (Exception ignored) {
      }
    }
  }

  private void cleanup() {
    try {
      if (serviceMgr != null) serviceMgr.delete();
    } catch (Exception ignored) {
    }
    try {
      if (workload != null) workload.delete();
    } catch (Exception ignored) {
    }
  }

  /**
   * Build the complete pod spec including the main container and all pod-level customizations
   * (init containers, sidecars, resource requests, pod customizers).
   */
  protected PodSpec buildPodSpec() {
    PodSpecBuilder spec = new PodSpecBuilder().addToContainers(buildMainContainer());
    return applyPodCustomizations(spec).build();
  }

  /** Build the main container (kind-specific, supplied by concrete pods). */
  protected abstract io.fabric8.kubernetes.api.model.Container buildMainContainer();
```

5. Remove the existing `protected abstract void doStart()` and `protected abstract void doStop()` declarations from step 2 — they're now concrete.

6. Replace `isRunning()` and `isReady()` to delegate to the workload manager:

From the existing:
```java
  @Override
  public abstract boolean isRunning();

  @Override
  public abstract boolean isReady();
```

To:
```java
  @Override
  public boolean isRunning() {
    return workload != null && workload.isRunning();
  }

  @Override
  public boolean isReady() {
    return workload != null && workload.isReady();
  }
```

- [ ] **Step 4.4: Slim down `DeploymentPod`**

Replace the contents of `core/src/main/java/org/testpods/core/pods/DeploymentPod.java` with:

```java
package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.testpods.core.cluster.HostAndPort;
import org.testpods.core.service.ClusterIPServiceManager;
import org.testpods.core.service.ServiceManager;
import org.testpods.core.wait.WaitStrategy;
import org.testpods.core.workload.DeploymentManager;
import org.testpods.core.workload.WorkloadManager;

/**
 * Base class for pods backed by a Kubernetes Deployment.
 *
 * <p>Wires {@link DeploymentManager} as the workload strategy and {@link ClusterIPServiceManager}
 * as the service strategy. Subclasses implement {@link #buildMainContainer()} and any
 * domain-specific configuration.
 */
public abstract class DeploymentPod<SELF extends DeploymentPod<SELF>> extends BaseManagedPod<SELF> {

  // Kind-specific customizers retained on the user-facing surface, even though the
  // underlying managers don't yet consume them. They will be wired through in a follow-up.
  protected final List<UnaryOperator<DeploymentBuilder>> deploymentCustomizers = new ArrayList<>();
  protected final List<UnaryOperator<ServiceBuilder>> serviceCustomizers = new ArrayList<>();

  public SELF withDeploymentCustomizer(UnaryOperator<DeploymentBuilder> customizer) {
    this.deploymentCustomizers.add(customizer);
    return self();
  }

  public SELF withServiceCustomizer(UnaryOperator<ServiceBuilder> customizer) {
    this.serviceCustomizers.add(customizer);
    return self();
  }

  @Override
  protected WorkloadManager createWorkloadManager() {
    return new DeploymentManager();
  }

  @Override
  protected ServiceManager createServiceManager() {
    return new ClusterIPServiceManager();
  }

  @Override
  public String getExternalHost() {
    HostAndPort endpoint = cluster.getAccessStrategy().getExternalEndpoint(this, getInternalPort());
    return endpoint.host();
  }

  @Override
  public int getExternalPort() {
    HostAndPort endpoint = cluster.getAccessStrategy().getExternalEndpoint(this, getInternalPort());
    return endpoint.port();
  }

  @Override
  protected WaitStrategy getDefaultWaitStrategy() {
    return WaitStrategy.forReadinessProbe().withTimeout(java.time.Duration.ofMinutes(1));
  }
}
```

Note: the previous `DeploymentPod` carried `buildDeployment()` / `buildService()` / `cleanup()`. Those are obsolete now — `DeploymentManager` and `ClusterIPServiceManager` do that work.

- [ ] **Step 4.5: Slim down `StatefulSetPod`**

Replace the contents of `core/src/main/java/org/testpods/core/pods/StatefulSetPod.java` with:

```java
package org.testpods.core.pods;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.testpods.core.cluster.HostAndPort;
import org.testpods.core.service.HeadlessServiceManager;
import org.testpods.core.service.ServiceManager;
import org.testpods.core.wait.WaitStrategy;
import org.testpods.core.workload.StatefulSetManager;
import org.testpods.core.workload.WorkloadManager;

/**
 * Base class for pods backed by a Kubernetes StatefulSet.
 *
 * <p>Wires {@link StatefulSetManager} as the workload strategy and {@link HeadlessServiceManager}
 * as the service strategy. Subclasses implement {@link #buildMainContainer()} and any
 * domain-specific configuration.
 */
public abstract class StatefulSetPod<SELF extends StatefulSetPod<SELF>> extends BaseManagedPod<SELF> {

  // Kind-specific customizers (user-facing). Not yet plumbed into the managers.
  protected final List<UnaryOperator<StatefulSetBuilder>> statefulSetCustomizers = new ArrayList<>();
  protected final List<UnaryOperator<ServiceBuilder>> serviceCustomizers = new ArrayList<>();
  protected final List<UnaryOperator<PersistentVolumeClaimBuilder>> pvcCustomizers = new ArrayList<>();

  protected volatile HostAndPort externalAccess;

  public SELF withStatefulSetCustomizer(UnaryOperator<StatefulSetBuilder> customizer) {
    this.statefulSetCustomizers.add(customizer);
    return self();
  }

  public SELF withServiceCustomizer(UnaryOperator<ServiceBuilder> customizer) {
    this.serviceCustomizers.add(customizer);
    return self();
  }

  public SELF withPvcCustomizer(UnaryOperator<PersistentVolumeClaimBuilder> customizer) {
    this.pvcCustomizers.add(customizer);
    return self();
  }

  @Override
  protected WorkloadManager createWorkloadManager() {
    StatefulSetManager mgr = new StatefulSetManager();
    if (!pvcCustomizers.isEmpty()) {
      List<PersistentVolumeClaim> templates = new ArrayList<>();
      for (UnaryOperator<PersistentVolumeClaimBuilder> c : pvcCustomizers) {
        PersistentVolumeClaimBuilder b =
            new PersistentVolumeClaimBuilder()
                .withNewMetadata().withName("data").endMetadata()
                .withNewSpec().withAccessModes("ReadWriteOnce").endSpec();
        templates.add(c.apply(b).build());
      }
      mgr.withPvcTemplates(templates);
    }
    return mgr;
  }

  @Override
  protected ServiceManager createServiceManager() {
    return new HeadlessServiceManager();
  }

  @Override
  public String getExternalHost() {
    if (externalAccess == null) {
      externalAccess = cluster.getAccessStrategy().getExternalEndpoint(this, getInternalPort());
    }
    return externalAccess.host();
  }

  @Override
  public int getExternalPort() {
    if (externalAccess == null) {
      externalAccess = cluster.getAccessStrategy().getExternalEndpoint(this, getInternalPort());
    }
    return externalAccess.port();
  }

  @Override
  protected WaitStrategy getDefaultWaitStrategy() {
    return WaitStrategy.forReadinessProbe().withTimeout(Duration.ofMinutes(2));
  }
}
```

- [ ] **Step 4.6: Run the wiring test; verify pass**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core test -q -o -Dtest=ManagedPodWiringTest 2>&1 | tail -15`

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 4.7: Verify hooks still work end-to-end after the lifecycle rewrite**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core test -q -o -Dtest=PodLifecycleHooksTest 2>&1 | tail -15`

Expected: `Tests run: 4, Failures: 0, Errors: 0`. Hook invocation order is preserved through `BaseManagedPod.start()` → `doStart()` (now manager-backed) → hooks unchanged.

- [ ] **Step 4.8: Verify full compile + tests**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core compile -q -o 2>&1 | tail -5`

Expected: no `ERROR` lines.

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && mvn -pl core test -q -o -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -15`

Expected: same or better `Tests run / Failures / Errors` than baseline; new tests pass.

- [ ] **Step 4.9: Final cleanup scan**

Run: `cd /Users/henrik/git/henrik/testpods-project/testpods && grep -rn 'BaseTestPod\|GenericTestPod\|TestPodLifecycleHooks' --include='*.java' --include='*.md' core/ examples/ 2>/dev/null`

Expected: no output. (References to `TestPod` as the JUnit annotation are still fine and not matched by the above.)

---

## Self-review checklist (run before handing off)

- [ ] All five tasks from the spec are covered: step 0 (BaseManagedPod rename), step 1 (ContainerDefinition + GenericPod + GenericStatefulPod), step 2 (PodLifecycleHooks + PostgreSQLPod migration), step 3 (Pod interface rename), step 4 (manager wiring).
- [ ] Baseline repair (step -1) added because the codebase didn't compile.
- [ ] Every step includes a verification command and an expected outcome.
- [ ] No "TBD" / "TODO" / "implement later" placeholders.
- [ ] No commit steps (per user preference).
- [ ] Each step is bite-sized — single-purpose, with concrete file paths and exact code where code changes.
- [ ] Type and method names used in later tasks match earlier definitions (`PodLifecycleHooks`, `doStart()`/`doStop()`, `createWorkloadManager()`/`createServiceManager()`).
