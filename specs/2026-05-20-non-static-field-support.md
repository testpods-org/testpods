# Non-Static Field Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow `@TestPod`, `@RegisterCluster`, and bare `K8sCluster`/`MinikubeCluster` fields in test classes and `testpodsProviders` classes to be non-static, with pods and clusters auto-registered and auto-injected using the same rules that apply to static fields today.

**Architecture:** Three layers of change. First, `ReflectionHelper` gains instance-aware scanning so non-static fields can be read by passing an object instance. Second, provider classes (`testpodsProviders`) are instantiated once during `beforeAll` so their non-static fields are scanned alongside static ones. Third, `TestPodsExtension.beforeEach` is implemented to inject the cluster and started pods into non-static fields of each test instance using the same null-check rules as the static assignments in `beforeAll`.

**Tech Stack:** Java 21, JUnit 5 (`BeforeEachCallback`/`ExtensionContext`), Lombok (already present), AssertJ for assertions.

---

## File Map

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/provisioning/ReflectionHelper.java` | Refactor + new public methods |
| `core/src/main/java/org/testpods/junit/TestPodsExtension.java` | Implement `beforeEach`; update `populateAndValidateRegistry` |
| `core/src/test/java/org/testpods/core/provisioning/ReflectionHelperNonStaticTest.java` | **New** — unit tests for instance-based scanning |
| `core/src/test/java/org/testpods/junit/TestPodsExtensionAssignmentTest.java` | Extend — add `@Nested` class for non-static injection |

---

## Design Rules (read before touching any code)

### resolveTestPodFields semantics

The private helper `resolveStaticTestPodFields` is renamed to `resolveTestPodFields(Class<?> clazz, Object instance)`:

- `instance == null` → scan **static fields only**
- `instance != null` → scan **non-static fields only** (the instance is used to read values)

This keeps scanning scopes separate and prevents duplicate entries when the two modes are combined.

### Injection rules for non-static fields (beforeEach)

The same rules as static fields, applied per test instance:

- Field is `null` → assign from registry (pod by name, cluster directly)
- Field is **non-null** → leave untouched

For non-static `@TestPod` fields whose field-initializer created a fresh (unstarted) pod, the user must declare the field without an initializer (`@TestPod KafkaPod kafkaPod;`) and let the registry supply the started pod. This mirrors the static pattern.

### Probe instance for test class non-static initialized fields

`populateAndValidateRegistry` creates a **probe instance** of the test class via its no-arg constructor to read non-static initialized `@TestPod` and `@RegisterCluster` fields. If the constructor is absent or throws, a warning is logged and non-static registration is skipped (graceful degradation).

---

## Task 1 — Extend ReflectionHelper with instance-based non-static scanning

**Files:**
- Modify: `core/src/main/java/org/testpods/core/provisioning/ReflectionHelper.java`
- Create: `core/src/test/java/org/testpods/core/provisioning/ReflectionHelperNonStaticTest.java`

---

- [ ] **Step 1.1 — Write failing tests**

Create `core/src/test/java/org/testpods/core/provisioning/ReflectionHelperNonStaticTest.java`:

```java
package org.testpods.core.provisioning;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.junit.RegisterCluster;
import org.testpods.junit.TestPod;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectionHelperNonStaticTest {

    // ─── Test-double classes ───────────────────────────────────────────────────

    static class StubCluster implements K8sCluster {
        @Override public io.fabric8.kubernetes.client.KubernetesClient getClient() { return null; }
        @Override public org.testpods.core.cluster.ExternalAccessStrategy getAccessStrategy() { return null; }
        @Override public org.testpods.core.cluster.Namespace getDefaultNamespace() { return null; }
        @Override public org.testpods.core.cluster.Namespace getNamespace(String n) { return null; }
        @Override public org.testpods.core.cluster.Namespace createNamespace(String n) { return null; }
        @Override public org.testpods.core.cluster.Namespace createNamespace() { return null; }
        @Override public K8sCluster withNamespace() { return this; }
        @Override public void close() {}
    }

    static class StubPod implements org.testpods.core.pods.Pod<StubPod> {
        private final String name;
        StubPod(String name) { this.name = name; }
        @Override public String getName() { return name; }
        // Pod interface stubs (all throw UnsupportedOperationException except getName)
        @Override public StubPod withName(String n) { throw new UnsupportedOperationException(); }
        @Override public StubPod inNamespace(org.testpods.core.cluster.Namespace ns) { throw new UnsupportedOperationException(); }
        @Override public StubPod inNamespace(String ns) { throw new UnsupportedOperationException(); }
        @Override public StubPod inCluster(K8sCluster c) { throw new UnsupportedOperationException(); }
        @Override public StubPod withLabels(java.util.Map<String, String> l) { throw new UnsupportedOperationException(); }
        @Override public StubPod withAnnotations(java.util.Map<String, String> a) { throw new UnsupportedOperationException(); }
        @Override public StubPod withResources(String cpu, String mem) { throw new UnsupportedOperationException(); }
        @Override public StubPod withInitContainer(java.util.function.Consumer<org.testpods.core.pods.builders.InitContainerBuilder> c) { throw new UnsupportedOperationException(); }
        @Override public StubPod withSidecar(java.util.function.Consumer<org.testpods.core.pods.builders.SidecarBuilder> c) { throw new UnsupportedOperationException(); }
        @Override public StubPod withPodCustomizer(java.util.function.UnaryOperator<io.fabric8.kubernetes.api.model.PodSpecBuilder> c) { throw new UnsupportedOperationException(); }
        @Override public StubPod waitingFor(org.testpods.core.wait.WaitStrategy s) { throw new UnsupportedOperationException(); }
        @Override public void start() {}
        @Override public void stop() {}
        @Override public boolean isRunning() { return false; }
        @Override public boolean isReady() { return false; }
        @Override public String getLogs() { return null; }
        @Override public String getLogs(java.time.Duration d) { return null; }
        @Override public String getLogs(String c) { return null; }
        @Override public org.testpods.core.ExecResult exec(String... cmd) { return null; }
        @Override public org.testpods.core.ExecResult exec(String c, String... cmd) { return null; }
        @Override public org.testpods.core.cluster.Namespace getNamespace() { return null; }
        @Override public K8sCluster getCluster() { return null; }
        @Override public String getInternalHost() { return null; }
        @Override public int getInternalPort() { return 0; }
        @Override public String getExternalHost() { return null; }
        @Override public int getExternalPort() { return 0; }
        @Override public void publishProperties(org.testpods.core.PropertyContext ctx) {}
    }

    // ─── Provider classes for scanning ────────────────────────────────────────

    static class ProviderWithNonStaticPod {
        @TestPod
        StubPod myPod = new StubPod("myPod");
    }

    static class ProviderWithNonStaticCluster {
        @RegisterCluster
        K8sCluster cluster = new StubCluster();
    }

    static class ProviderWithStaticAndNonStaticPod {
        @TestPod
        static StubPod staticPod = new StubPod("staticPod");

        @TestPod
        StubPod instancePod = new StubPod("instancePod");
    }

    static class ProviderWithNoArgConstructor {
        @TestPod
        StubPod pod = new StubPod("fromProvider");
    }

    static class ProviderWithoutNoArgConstructor {
        @SuppressWarnings("unused")
        private final String required;

        ProviderWithoutNoArgConstructor(String required) {
            this.required = required;
        }

        @TestPod
        StubPod pod = new StubPod("unreachable");
    }

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Nested
    class ScanClassForTestPodInitializationsOnlyWithInstance {

        @Test
        void nonStaticInitializedField_foundWhenInstanceProvided() {
            Object instance = new ProviderWithNonStaticPod();
            var result = ReflectionHelper.scanClassForTestPodInitializationsOnly(
                    ProviderWithNonStaticPod.class, instance);
            assertThat(result).hasSize(1);
            assertThat(result.values().iterator().next().podName()).isEqualTo("myPod");
        }

        @Test
        void nonStaticInitializedField_notFoundWhenInstanceNull() {
            var result = ReflectionHelper.scanClassForTestPodInitializationsOnly(
                    ProviderWithNonStaticPod.class, null);
            assertThat(result).isEmpty();
        }

        @Test
        void withInstance_onlyNonStaticFieldsReturned() {
            Object instance = new ProviderWithStaticAndNonStaticPod();
            var instanceResult = ReflectionHelper.scanClassForTestPodInitializationsOnly(
                    ProviderWithStaticAndNonStaticPod.class, instance);
            assertThat(instanceResult).hasSize(1);
            assertThat(instanceResult.values().iterator().next().podName()).isEqualTo("instancePod");
        }

        @Test
        void withNullInstance_onlyStaticFieldsReturned() {
            var staticResult = ReflectionHelper.scanClassForTestPodInitializationsOnly(
                    ProviderWithStaticAndNonStaticPod.class, null);
            assertThat(staticResult).hasSize(1);
            assertThat(staticResult.values().iterator().next().podName()).isEqualTo("staticPod");
        }
    }

    @Nested
    class ScanClassForClusterRegistrationWithInstance {

        @Test
        void nonStaticClusterField_foundWhenInstanceProvided() {
            Object instance = new ProviderWithNonStaticCluster();
            K8sCluster cluster = ReflectionHelper.scanClassForClusterRegistration(
                    ProviderWithNonStaticCluster.class, instance);
            assertThat(cluster).isNotNull().isInstanceOf(StubCluster.class);
        }

        @Test
        void nonStaticClusterField_notFoundWhenInstanceNull() {
            K8sCluster cluster = ReflectionHelper.scanClassForClusterRegistration(
                    ProviderWithNonStaticCluster.class, null);
            assertThat(cluster).isNull();
        }
    }

    @Nested
    class ScanTestPodsProvidersForAllTestPodInitializers {

        @Test
        void nonStaticFieldsInProvider_areIncluded() {
            var result = ReflectionHelper.scanTestPodsProvidersForAllTestPodInitializers(
                    new Class<?>[] { ProviderWithNoArgConstructor.class });
            assertThat(result).hasSize(1);
            assertThat(result.values().iterator().next().podName()).isEqualTo("fromProvider");
        }

        @Test
        void providerWithoutNoArgConstructor_gracefullySkipped() {
            var result = ReflectionHelper.scanTestPodsProvidersForAllTestPodInitializers(
                    new Class<?>[] { ProviderWithoutNoArgConstructor.class });
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class ScanTestPodsProvidersForClusterRegistration {

        @Test
        void nonStaticClusterFieldInProvider_isFound() {
            K8sCluster cluster = ReflectionHelper.scanTestPodsProvidersForClusterRegistration(
                    new Class<?>[] { ProviderWithNonStaticCluster.class });
            assertThat(cluster).isNotNull().isInstanceOf(StubCluster.class);
        }

        @Test
        void providerWithNoCluster_returnsNull() {
            K8sCluster cluster = ReflectionHelper.scanTestPodsProvidersForClusterRegistration(
                    new Class<?>[] { ProviderWithNonStaticPod.class });
            assertThat(cluster).isNull();
        }
    }
}
```

- [ ] **Step 1.2 — Run tests to confirm RED**

```bash
mvn -pl core clean test -Dtest=ReflectionHelperNonStaticTest 2>&1 | tail -20
```

Expected: **compilation failure** — `scanClassForTestPodInitializationsOnly(Class, Object)`, `scanClassForClusterRegistration(Class, Object)`, `scanTestPodsProvidersForAllTestPodInitializers`, and `scanTestPodsProvidersForClusterRegistration` do not exist yet.

- [ ] **Step 1.3 — Refactor resolveStaticTestPodFields → resolveTestPodFields**

In `ReflectionHelper.java`, rename the private method and change its semantics:

```java
// OLD — delete this:
// private static List<ResolvedTestPodField> resolveStaticTestPodFields(Class<?> clazz) { ... }

// NEW — replace with:
/**
 * @param instance null → scan static fields only; non-null → scan non-static fields only
 */
private static List<ResolvedTestPodField> resolveTestPodFields(Class<?> clazz, Object instance) {
    List<ResolvedTestPodField> resolved = new ArrayList<>();
    boolean scanStatic = (instance == null);
    for (Field field : clazz.getDeclaredFields()) {
        if (!field.isAnnotationPresent(TestPod.class)) continue;
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        if (isStatic != scanStatic) continue;
        field.setAccessible(true);
        Object value;
        try {
            value = field.get(isStatic ? null : instance);
        } catch (IllegalAccessException e) {
            log.error("Cannot access field '{}' in {}: {}",
                    field.getName(), clazz.getSimpleName(), e.getMessage());
            continue;
        }
        resolved.add(new ResolvedTestPodField(field, field.getAnnotation(TestPod.class), value));
    }
    return resolved;
}
```

Update the two callers inside `ReflectionHelper` that call the old name:
- `scanTestClassForTestPodDeclarationsOnly` → call `resolveTestPodFields(testClass, null)`
- existing `scanClassForTestPodInitializationsOnly(Class<?> clazz)` → call `resolveTestPodFields(clazz, null)`

- [ ] **Step 1.4 — Add scanClassForTestPodInitializationsOnly(Class, Object) overload**

Add this new public method to `ReflectionHelper.java`:

```java
/**
 * Scans for initialized (non-null) {@link TestPod} fields.
 *
 * @param instance null → static fields only; non-null → non-static fields only
 */
public static Map<String, FieldInitialization> scanClassForTestPodInitializationsOnly(
        Class<?> clazz, Object instance) {
    Map<String, FieldInitialization> result = new LinkedHashMap<>();
    for (ResolvedTestPodField resolved : resolveTestPodFields(clazz, instance)) {
        if (resolved.value() == null) {
            log.debug("Field '{}' in {} has no initializer — not an initialization field",
                    resolved.field().getName(), clazz.getSimpleName());
            continue;
        }
        FieldInitialization initialization = new FieldInitialization(
                resolved.field(),
                resolved.field().getName(),
                resolved.field().getType(),
                resolved.annotation(),
                clazz,
                Modifier.isPrivate(resolved.field().getModifiers()),
                resolved.value());
        initialization.typedInstance();
        result.put(initialization.podNamePrefixedWithClassName(), initialization);
        log.debug("Found @TestPod initialized field: {} {} = {} (podName='{}')",
                resolved.field().getType().getSimpleName(),
                resolved.field().getName(),
                resolved.value().getClass().getSimpleName(),
                initialization.podName());
    }
    return result;
}
```

- [ ] **Step 1.5 — Add scanClassForClusterRegistration(Class, Object) overload**

The existing varargs method keeps its signature and now delegates to a new single-class overload:

```java
/**
 * Scans one class for a {@link RegisterCluster}-annotated field.
 *
 * @param instance null → static fields only; non-null → non-static fields only
 */
public static K8sCluster scanClassForClusterRegistration(Class<?> clazz, Object instance) {
    boolean scanStatic = (instance == null);
    for (Field field : clazz.getDeclaredFields()) {
        if (!field.isAnnotationPresent(RegisterCluster.class)) continue;
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        if (isStatic != scanStatic) continue;
        if (!K8sCluster.class.isAssignableFrom(field.getType())) {
            log.warn(
                    "Field '{}' in {} is annotated with @RegisterCluster but its type {} does not implement K8sCluster — skipping",
                    field.getName(), clazz.getSimpleName(), field.getType().getSimpleName());
            continue;
        }
        field.setAccessible(true);
        try {
            Object value = field.get(isStatic ? null : instance);
            if (value == null) {
                log.warn("@RegisterCluster field '{}' in {} is null — skipping",
                        field.getName(), clazz.getSimpleName());
                continue;
            }
            log.debug("Found @RegisterCluster field: {} {} in {}",
                    field.getType().getSimpleName(), field.getName(), clazz.getSimpleName());
            return (K8sCluster) value;
        } catch (IllegalAccessException e) {
            log.error("Cannot access @RegisterCluster field '{}' in {}: {}",
                    field.getName(), clazz.getSimpleName(), e.getMessage());
        }
    }
    return null;
}
```

Update the existing varargs method to use this new overload instead of duplicating the loop:

```java
public static K8sCluster scanClassForClusterRegistration(Class<?>... classes) {
    for (Class<?> clazz : classes) {
        K8sCluster cluster = scanClassForClusterRegistration(clazz, null); // static-only
        if (cluster != null) return cluster;
    }
    log.debug("No @RegisterCluster field found in any of the scanned classes");
    return null;
}
```

- [ ] **Step 1.6 — Add tryInstantiate helper and high-level provider scanning methods**

Add these methods to `ReflectionHelper.java`:

```java
/**
 * Attempts to create an instance using the no-arg constructor.
 * Returns null and logs a warning if the constructor is absent or throws.
 */
static Object tryInstantiate(Class<?> clazz) {
    try {
        var constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    } catch (Exception e) {
        log.warn(
                "Cannot instantiate {} for non-static field scanning — non-static fields will be skipped: {}",
                clazz.getSimpleName(), e.getMessage());
        return null;
    }
}

/**
 * Scans each provider class for initialized {@link TestPod} fields,
 * including non-static fields by instantiating each provider.
 */
public static Map<String, FieldInitialization> scanTestPodsProvidersForAllTestPodInitializers(
        Class<?>[] testpodsProviders) {
    Map<String, FieldInitialization> result = new LinkedHashMap<>();
    for (Class<?> provider : testpodsProviders) {
        result.putAll(scanClassForTestPodInitializationsOnly(provider, null)); // static
        Object instance = tryInstantiate(provider);
        if (instance != null) {
            result.putAll(scanClassForTestPodInitializationsOnly(provider, instance)); // non-static
        }
    }
    return result;
}

/**
 * Scans each provider class for a {@link RegisterCluster}-annotated field,
 * including non-static fields by instantiating each provider.
 * Returns the first cluster found, or null.
 */
public static K8sCluster scanTestPodsProvidersForClusterRegistration(Class<?>[] testpodsProviders) {
    for (Class<?> provider : testpodsProviders) {
        K8sCluster cluster = scanClassForClusterRegistration(provider, null); // static
        if (cluster != null) return cluster;
        Object instance = tryInstantiate(provider);
        if (instance != null) {
            cluster = scanClassForClusterRegistration(provider, instance); // non-static
            if (cluster != null) return cluster;
        }
    }
    return null;
}
```

- [ ] **Step 1.7 — Run tests and confirm GREEN**

```bash
mvn -pl core clean test -Dtest=ReflectionHelperNonStaticTest 2>&1 | tail -20
```

Expected: `Tests run: N, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

Also confirm existing ReflectionHelper-related tests still pass:

```bash
mvn -pl core clean test 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 1.8 — Commit**

```bash
git add core/src/main/java/org/testpods/core/provisioning/ReflectionHelper.java \
        core/src/test/java/org/testpods/core/provisioning/ReflectionHelperNonStaticTest.java
git commit -m "refactor(ReflectionHelper): support non-static field scanning via instance parameter"
```

---

## Task 2 — Inject pods and cluster into non-static fields in beforeEach

**Files:**
- Modify: `core/src/main/java/org/testpods/junit/TestPodsExtension.java`
- Modify: `core/src/test/java/org/testpods/junit/TestPodsExtensionAssignmentTest.java`

---

- [ ] **Step 2.1 — Write failing tests**

In `TestPodsExtensionAssignmentTest.java`, add a new `@Nested` class. The existing class already has `StubPod`, `STUB_CLUSTER`, `OTHER_CLUSTER` and the `field()` helper — reuse them:

```java
@Nested
class NonStaticAssignmentTests {

    // Test-target classes — non-static fields
    static class WithNonStaticClusterField {
        K8sCluster cluster;
    }

    static class WithNonStaticInitializedClusterField {
        K8sCluster cluster;
    }

    static class WithNonStaticPodField {
        @TestPod
        StubPod myPod;
    }

    static class WithNonStaticPodNameAnnotation {
        @TestPod(podName = "custom-name")
        StubPod myPod;
    }

    static class WithNonStaticInitializedPodField {
        @TestPod
        StubPod myPod;
    }

    // Instances reset before each test
    WithNonStaticClusterField clusterTarget;
    WithNonStaticInitializedClusterField initializedClusterTarget;
    WithNonStaticPodField podTarget;
    WithNonStaticPodNameAnnotation podNameTarget;
    WithNonStaticInitializedPodField initializedPodTarget;

    @BeforeEach
    void resetInstances() {
        clusterTarget = new WithNonStaticClusterField();
        initializedClusterTarget = new WithNonStaticInitializedClusterField();
        podTarget = new WithNonStaticPodField();
        podNameTarget = new WithNonStaticPodNameAnnotation();
        initializedPodTarget = new WithNonStaticInitializedPodField();
    }

    @Test
    void nullNonStaticClusterField_getsClusterAssigned() {
        extension.assignClusterToNonStaticFields(clusterTarget, clusterTarget.getClass(), STUB_CLUSTER);
        assertThat(clusterTarget.cluster).isSameAs(STUB_CLUSTER);
    }

    @Test
    void nonNullNonStaticClusterField_notOverwritten() throws Exception {
        initializedClusterTarget.cluster = OTHER_CLUSTER;
        extension.assignClusterToNonStaticFields(
                initializedClusterTarget, initializedClusterTarget.getClass(), STUB_CLUSTER);
        assertThat(initializedClusterTarget.cluster).isSameAs(OTHER_CLUSTER);
    }

    @Test
    void nullNonStaticPodField_matchedByFieldName_getsAssigned() {
        StubPod pod = new StubPod("myPod");
        registry.addPod("myPod", pod);
        extension.assignPodsToNonStaticFields(podTarget, podTarget.getClass(), registry);
        assertThat(podTarget.myPod).isSameAs(pod);
    }

    @Test
    void nullNonStaticPodField_matchedByAnnotationPodName_getsAssigned() {
        StubPod pod = new StubPod("custom-name");
        registry.addPod("custom-name", pod);
        extension.assignPodsToNonStaticFields(podNameTarget, podNameTarget.getClass(), registry);
        assertThat(podNameTarget.myPod).isSameAs(pod);
    }

    @Test
    void nonNullNonStaticPodField_notOverwritten() {
        StubPod existing = new StubPod("existing");
        initializedPodTarget.myPod = existing;
        registry.addPod("myPod", new StubPod("from-registry"));
        extension.assignPodsToNonStaticFields(initializedPodTarget, initializedPodTarget.getClass(), registry);
        assertThat(initializedPodTarget.myPod).isSameAs(existing);
    }

    @Test
    void emptyRegistry_nonStaticPodFieldRemainsNull() {
        extension.assignPodsToNonStaticFields(podTarget, podTarget.getClass(), registry);
        assertThat(podTarget.myPod).isNull();
    }
}
```

- [ ] **Step 2.2 — Run tests to confirm RED**

```bash
mvn -pl core clean test -Dtest=TestPodsExtensionAssignmentTest 2>&1 | tail -20
```

Expected: **compilation failure** — `assignClusterToNonStaticFields` and `assignPodsToNonStaticFields` do not exist on `TestPodsExtension`.

- [ ] **Step 2.3 — Add assignClusterToNonStaticFields**

In `TestPodsExtension.java`, add this package-private method (same visibility pattern as the existing `assignClusterToTestClassField`):

```java
void assignClusterToNonStaticFields(Object instance, Class<?> testClass, K8sCluster cluster) {
    for (Field field : testClass.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) continue;
        if (!K8sCluster.class.isAssignableFrom(field.getType())) continue;
        field.setAccessible(true);
        try {
            if (field.get(instance) != null) continue;
            field.set(instance, cluster);
            log.debug("Injected cluster into non-static field '{}' in {}",
                    field.getName(), testClass.getSimpleName());
        } catch (IllegalAccessException e) {
            log.error("Cannot assign cluster to non-static field '{}' in {}: {}",
                    field.getName(), testClass.getSimpleName(), e.getMessage());
        }
    }
}
```

- [ ] **Step 2.4 — Add assignPodsToNonStaticFields**

In `TestPodsExtension.java`, add this package-private method:

```java
void assignPodsToNonStaticFields(Object instance, Class<?> testClass, Registry registry) {
    Map<String, Pod<?>> podsByName = registry.getPodsByName();
    if (podsByName.isEmpty()) return;
    for (Field field : testClass.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) continue;
        if (!field.isAnnotationPresent(TestPod.class)) continue;
        field.setAccessible(true);
        try {
            if (field.get(instance) != null) continue;
            String podName = resolvePodName(field.getAnnotation(TestPod.class), field.getName());
            Pod<?> pod = podsByName.get(podName);
            if (pod == null) {
                log.warn("No pod found in registry with name '{}' for non-static @TestPod field '{}' in {}",
                        podName, field.getName(), testClass.getSimpleName());
                continue;
            }
            if (!field.getType().isAssignableFrom(pod.getClass())) {
                log.warn("Pod '{}' type {} is not assignable to non-static field '{}' type {} in {}",
                        podName, pod.getClass().getSimpleName(),
                        field.getName(), field.getType().getSimpleName(), testClass.getSimpleName());
                continue;
            }
            field.set(instance, pod);
            log.debug("Assigned pod '{}' to non-static field '{}' in {}",
                    podName, field.getName(), testClass.getSimpleName());
        } catch (IllegalAccessException e) {
            log.error("Cannot access non-static @TestPod field '{}' in {}: {}",
                    field.getName(), testClass.getSimpleName(), e.getMessage());
        }
    }
}
```

- [ ] **Step 2.5 — Implement beforeEach**

Replace the empty `beforeEach` in `TestPodsExtension.java`:

```java
@Override
public void beforeEach(ExtensionContext extensionContext) throws Exception {
    Object testInstance = extensionContext.getRequiredTestInstance();
    Class<?> testClass = extensionContext.getRequiredTestClass();
    assignClusterToNonStaticFields(testInstance, testClass, registry.getCluster());
    assignPodsToNonStaticFields(testInstance, testClass, registry);
}
```

Note: `registry.getCluster()` is the Lombok-generated getter for the `@Getter K8sCluster cluster` field in `Registry`.

- [ ] **Step 2.6 — Run tests and confirm GREEN**

```bash
mvn -pl core clean test -Dtest=TestPodsExtensionAssignmentTest 2>&1 | tail -20
```

Expected: all tests pass, `BUILD SUCCESS`.

- [ ] **Step 2.7 — Commit**

```bash
git add core/src/main/java/org/testpods/junit/TestPodsExtension.java \
        core/src/test/java/org/testpods/junit/TestPodsExtensionAssignmentTest.java
git commit -m "feat(TestPodsExtension): inject pods and cluster into non-static fields in beforeEach"
```

---

## Task 3 — Update populateAndValidateRegistry to scan non-static fields

This task wires the new ReflectionHelper methods into the bootstrap so that non-static `@TestPod` and `@RegisterCluster` fields in the test class (via probe instance) and in provider classes are registered alongside static ones.

**Files:**
- Modify: `core/src/main/java/org/testpods/junit/TestPodsExtension.java`
- Modify: `core/src/test/java/org/testpods/junit/TestPodsExtensionAssignmentTest.java`

---

- [ ] **Step 3.1 — Write failing tests**

Add a new `@Nested` class to `TestPodsExtensionAssignmentTest.java` that tests `populateAndValidateRegistry` directly (the method is package-private, accessible from the test in the same package):

```java
@Nested
class PopulateRegistryNonStaticTests {

    // A minimal TestPods provider with only a non-static @TestPod field
    @TestPods
    static class TestClassWithNonStaticInitializedPod {
        // No-arg constructor is implicit

        @RegisterCluster
        static K8sCluster cluster = STUB_CLUSTER;

        @TestPod
        StubPod kafkaPod = new StubPod("kafkaPod");
    }

    static class ProviderWithNonStaticPod {
        @TestPod
        StubPod providerPod = new StubPod("providerPod");
    }

    @TestPods(testpodsProviders = {ProviderWithNonStaticPod.class})
    static class TestClassUsingProvider {
        @RegisterCluster
        static K8sCluster cluster = STUB_CLUSTER;
    }

    @Test
    void nonStaticInitializedPodInTestClass_registeredViaProbeInstance() {
        extension.testPodsAnnotation = TestClassWithNonStaticInitializedPod.class.getAnnotation(TestPods.class);
        extension.populateAndValidateRegistry(TestClassWithNonStaticInitializedPod.class);
        assertThat(extension.registry.testPodInitializationsByName)
                .anyMatch((entry) -> entry.getValue().podName().equals("kafkaPod"));
    }

    @Test
    void nonStaticInitializedPodInProvider_registeredWhenProviderScanned() {
        extension.testPodsAnnotation = TestClassUsingProvider.class.getAnnotation(TestPods.class);
        extension.populateAndValidateRegistry(TestClassUsingProvider.class);
        assertThat(extension.registry.testPodInitializationsByName)
                .anyMatch((entry) -> entry.getValue().podName().equals("providerPod"));
    }
}
```

> **Note on test access:** `testPodInitializationsByName` is package-private in `Registry` (same package `org.testpods.core.provisioning`), but the test is in `org.testpods.junit`. Access it via reflection in the test:
>
> ```java
> @SuppressWarnings("unchecked")
> private Map<String, FieldInitialization> getInitializations(Registry registry) throws Exception {
>     Field f = Registry.class.getDeclaredField("testPodInitializationsByName");
>     f.setAccessible(true);
>     return (Map<String, FieldInitialization>) f.get(registry);
> }
> ```
>
> Then use `getInitializations(extension.registry).values()` instead of `extension.registry.testPodInitializationsByName` in the assertions.

Revise the tests to use the reflection accessor:

```java
@Test
void nonStaticInitializedPodInTestClass_registeredViaProbeInstance() throws Exception {
    extension.testPodsAnnotation = TestClassWithNonStaticInitializedPod.class.getAnnotation(TestPods.class);
    extension.populateAndValidateRegistry(TestClassWithNonStaticInitializedPod.class);
    var inits = getInitializations(extension.registry);
    assertThat(inits.values()).anyMatch(fi -> fi.podName().equals("kafkaPod"));
}

@Test
void nonStaticInitializedPodInProvider_registeredWhenProviderScanned() throws Exception {
    extension.testPodsAnnotation = TestClassUsingProvider.class.getAnnotation(TestPods.class);
    extension.populateAndValidateRegistry(TestClassUsingProvider.class);
    var inits = getInitializations(extension.registry);
    assertThat(inits.values()).anyMatch(fi -> fi.podName().equals("providerPod"));
}
```

- [ ] **Step 3.2 — Run tests to confirm RED**

```bash
mvn -pl core clean test -Dtest=TestPodsExtensionAssignmentTest#PopulateRegistryNonStaticTests 2>&1 | tail -20
```

Expected: test failures — `populateAndValidateRegistry` currently only uses static-scanning methods, so the non-static pod registrations are not found.

- [ ] **Step 3.3 — Update populateAndValidateRegistry**

Replace the current `populateAndValidateRegistry` implementation in `TestPodsExtension.java` with:

```java
private void populateAndValidateRegistry(Class<?> testClass) {
    // Static fields in test class (unchanged behaviour)
    K8sCluster cluster = ReflectionHelper.scanClassForClusterRegistration(testClass);
    var testPodDeclarations = ReflectionHelper.scanTestClassForTestPodDeclarationsOnly(testClass);
    registry.addTestPodDeclarations(testPodDeclarations);
    var staticInitializations = ReflectionHelper.scanClassForTestPodInitializationsOnly(testClass);
    registry.addTestPodInitializations(staticInitializations);

    // Non-static fields in test class via probe instance (new behaviour)
    Object probe = ReflectionHelper.tryInstantiate(testClass);
    if (probe != null) {
        var nonStaticInitializations = ReflectionHelper.scanClassForTestPodInitializationsOnly(testClass, probe);
        registry.addTestPodInitializations(nonStaticInitializations);
        if (cluster == null) {
            cluster = ReflectionHelper.scanClassForClusterRegistration(testClass, probe);
        }
    }

    // Provider classes — static and non-static (new behaviour for non-static)
    final Class<?>[] testpodsProviders = testPodsAnnotation.testpodsProviders();
    Set<K8sCluster> providedClusters = new HashSet<>();
    if (testpodsProviders != null && testpodsProviders.length > 0) {
        var providedInitializations =
                ReflectionHelper.scanTestPodsProvidersForAllTestPodInitializers(testpodsProviders);
        registry.addTestPodInitializations(providedInitializations);
        K8sCluster providedCluster =
                ReflectionHelper.scanTestPodsProvidersForClusterRegistration(testpodsProviders);
        if (providedCluster != null) providedClusters.add(providedCluster);
    }

    if (cluster == null && !providedClusters.isEmpty()) {
        cluster = providedClusters.stream().findFirst().orElse(null);
    }
    assert cluster != null : "A K8Cluster is required for TestPods to provision resources.";
    registry.setCluster(cluster);
    registry.validateConfiguration();
}
```

Also make `tryInstantiate` in `ReflectionHelper` accessible from `TestPodsExtension`. Since they're in different packages, change its visibility from package-private to `public`:

```java
// In ReflectionHelper.java:
public static Object tryInstantiate(Class<?> clazz) { ... }
```

- [ ] **Step 3.4 — Run tests and confirm GREEN**

```bash
mvn -pl core clean test -Dtest=TestPodsExtensionAssignmentTest 2>&1 | tail -20
```

Expected: all tests pass. Then run the full test suite:

```bash
mvn -pl core clean test 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3.5 — Commit**

```bash
git add core/src/main/java/org/testpods/junit/TestPodsExtension.java \
        core/src/main/java/org/testpods/core/provisioning/ReflectionHelper.java \
        core/src/test/java/org/testpods/junit/TestPodsExtensionAssignmentTest.java
git commit -m "feat(TestPodsExtension): scan non-static @TestPod and @RegisterCluster fields via probe instances"
```

---

## Self-Review

### Spec coverage

| Requirement | Covered by |
|---|---|
| Non-static `@TestPod` fields in provider classes are scanned | Task 1 — `scanTestPodsProvidersForAllTestPodInitializers` |
| Non-static `@RegisterCluster` fields in provider classes are scanned | Task 1 — `scanTestPodsProvidersForClusterRegistration` |
| Non-static `@TestPod` fields in test class are scanned (probe instance) | Task 3 — `populateAndValidateRegistry` |
| Non-static `@RegisterCluster` fields in test class are scanned (probe instance) | Task 3 — `populateAndValidateRegistry` |
| Non-static `K8sCluster` fields in test class receive the active cluster | Task 2 — `assignClusterToNonStaticFields` + `beforeEach` |
| Non-static `@TestPod` null fields in test class receive started pods | Task 2 — `assignPodsToNonStaticFields` + `beforeEach` |
| Same rules as static: null → assign, non-null → leave | Task 2 — null-checks in both new assign methods |
| Provider with no-arg constructor required for non-static scanning | Task 1 — `tryInstantiate` with graceful warning |

### Placeholder scan

No placeholders. All code is complete.

### Type consistency

- `resolveTestPodFields` is called with `null` or an `Object instance` throughout.
- `scanClassForTestPodInitializationsOnly(Class, Object)` matches the signature used in `scanClassForAllTestPodInitializations` internal calls — confirmed consistent.
- `assignClusterToNonStaticFields(Object, Class<?>, K8sCluster)` and `assignPodsToNonStaticFields(Object, Class<?>, Registry)` match the test call signatures.
