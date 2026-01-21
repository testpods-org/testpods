# Various design thoughts and ideas - to be used for inspiration

> **Note:** Many of these ideas have been finalized in the [Test API Design Decisions](21_test_api_design_decisions.md) document.
> Items marked with [DECIDED] have been resolved. See that document for the final API design.

## Annotations used in tests

[DECIDED] The finalized annotation structure is:
- `@TestPods` on class level (required to enable the extension)
- `@Pod` on fields (both static and instance supported)
- `@DependsOn` for explicit dependency ordering

```java
@TestPods
class CustomerServiceWithJUnit5ExtensionTest {

    @Pod // started per test class (static field)
    static PostgreSQLPod postgres = new PostgreSQLPod();

    @Pod // started per test method (instance field)
    PostgreSQLPod methodLevelPostgres = new PostgreSQLPod();
}
```

---

## Original Brainstorming Notes (Historical Reference)

The following are the original brainstorming notes, preserved for reference:

```java
@Testcluster
@Testpods
class CustomerServiceWithJUnit5ExtensionTest {

@Testpod // started per test class
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

@Testpod // started per test method
PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

// Consider these abstraction levels to work with instead of TestPodGroup because they better match k8s concepts 1-1.
@WorkloadGroup
@DeploymentGroup / @StatefulSetGroup // however when testing the difference is not important but the grouping is.
@ServiceGroup

@Testpods - this is similar to TestContainers but the Enable name is more like SpringBoot convetion and signals that it now is active
@EnableTestPods  // Auto-registers extension, scans for @TestPod etc. field
// What to place where? If another class from another support lib as some TestPodCatalog and we want to use it in a test. Then what should trigger it being found?
// EnableTestPods on test class seems to be enough
class ExampleJunitTestClass {

    @TestPodGroup("infrastructure")
    static TestPodGroup infrastructure

    @TestPodGroup("systemService") // only support named references or also just use the bean name convention maybe?
    static TestPodGroup systemServices;

    void testMehtod() {
        systemServices.orderService().getPort();
    }

}
```

---

## TestPodGroup and TestPodCatalog Design [DECIDED]

[DECIDED] The final design uses:
1. **Inline grouping** via `@Pod(group = "groupName")` for simple cases
2. **TestPodGroup** for explicit group management with dependencies
3. **TestPodCatalog** for typed fluent access without strings

Final pattern:
```java
// Define pods
static KafkaPod kafkaPod = new KafkaPod();
static PostgreSQLPod postgresPod = new PostgreSQLPod();

// Register in catalog - accessor names derived from variable names
@RegisterTestPodCatalog
static TestPodCatalog catalog = TestPodCatalog.builder()
    .register(kafkaPod)
    .register(postgresPod)
    .build();

// Create group with fluent typed access
@RegisterTestPodGroup
static TestPodGroup infrastructure = catalog.kafkaPod().postgresPod().asGroup();
```

---

## Original TestPodCatalog Notes (Historical Reference)

```java
class ApplicationK8sResources {

    One approach is to also have ServiceGroup and DeploymentGroup/WorkloadGroup(which is better?) in addition to TestPodGroup.
    If only the TestPodGroup is used then the default convention remains where a pod is as defined - a deployment/statefullset with a 1-1 default service.
    But by making the ServiceGroup and WorkloadGroup the first class level then they can be managed independently from the TestPods.
    What could be the usecase for that? Error scenario testing where an external client calls a service which is missing a pod or something.

    @RegisterServiceGroup

    // add the used TestPods to a servicecatalog to create a "strongly typed" catalog which can be used when building service groups maybe.
    @ServiceCatalog
    ServiceCatalog infrastructureCatalog = ServiceCatalog.named("infrastructure")
    .register(kafka)
    .register(postgresql);


    static GenericTestPod kafka = new GenericTestPod("kafka")
    static GenericTestPod postgresql = new GenericTestPod("postgresql")

    @RegisterCluster
    static K8sCluster cluster = K8sCluster.minikube();

    @RegisterNamespace
    static TestNamespace testNS = new TestNamespace(cluster).withRandomSuffix();

    @RegisterTestPodCatalog
    static TestPodCatalog infrastructureCatalog =
        TestPodCatalog.named("infrastructureCatalog").nameSpace(testNS) // ns is optional
        .register(kafka)
        .register(postgresql);

    static GenericTestPod redis = new GenericTestPod("redis")

    @RegisterTestPodGroup
    static TestPodGroup infrastructure = TestPodGroup.named("infrastructure")
        .addAll(infrastructureCatalog.kafka().postgresql()) // could return a list of pods
        .add(redis);

    //TestPodGroup infrastructure = infrastructureCatalog.all();

    static GenericTestPod orderService = new GenericTestPod("order")
    static GenericTestPod productService = new GenericTestPod("product")

    @RegisterTestPodCatalog
    static TestPodCatalog systemServicesCatalog =
        TestPodCatalog.named("systemServicesCatalog")
        .register(kafka)
        .register(postgresql);

    @RegisterTestPodGroup("systemServices")
    static TestPodGroup systemServices =
    systemServicesCatalog.orderService().productService()
    .dependsOn(infrastructure);  //or waitFor - or both but the waitFor is for actually also waiting for it to have started whereas the dependsOn just means that it must be created - ready or not.

}
```

---

## Other Ideas [DECIDED]

[DECIDED] These concepts have been finalized:

- **TestPodRegistry**: Not needed as a separate concept. TestPodCatalog serves this purpose.
- **TestPods properties**: Implemented via PropertyContext with hierarchical inheritance. See [Section 21.7](21_test_api_design_decisions.md#217-propertycontext)
- **dependsOn vs waitFor**: Unified to `@DependsOn` which both establishes order AND waits for readiness
- **Parallel startup**: Default behavior, with explicit dependencies for ordering

---

## Remaining Ideas for Future Consideration

1. **ServiceGroup/WorkloadGroup separation** - For advanced scenarios where K8s Service and Deployment need independent lifecycle management (e.g., testing missing pod scenarios)

2. **Remote debugging** - Future feature to enable IDE remote debug sessions into cluster pods

3. **Operator testing mode** - Testing custom Kubernetes operators (deferred, see [Section 9](09_future_consideration_operator_testing_mode.md))
