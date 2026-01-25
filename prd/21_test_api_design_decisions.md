# 21. Test API Design Decisions

**Created:** 2026-01-21
**Status:** Approved via Interview
**Focus:** How the core library is used in actual tests

---

## 21.1. Overview

This document captures design decisions for the TestPods JUnit integration API, determined through a structured interview process. The focus is on the developer experience when writing tests using the library.

---

## 21.2. Core API Style

### 21.2.1. Primary: Annotation-Driven

The primary API uses annotations for minimal boilerplate:

```java
@TestPods
class OrderServiceTest {

    @Pod
    static PostgreSQLPod postgres = new PostgreSQLPod();

    @Pod
    static KafkaPod kafka = new KafkaPod();

    @Test
    void shouldProcessOrder() {
        // Test code with postgres and kafka available
    }
}
```

**Key Decisions:**
- `@TestPods` on class level enables the extension
- `@Pod` on fields marks them for lifecycle management
- Both static (class-level sharing) and instance (per-test) fields supported
- Static fields recommended for performance (pods are expensive)

### 21.2.2. Secondary: Programmatic with Defaults

For advanced scenarios, programmatic configuration via `TestPodDefaults`:

```java
@BeforeAll
static void setup() {
    TestPodDefaults.setClusterSupplier(() -> K8sCluster.minikube());
    TestPodDefaults.setNamespaceNameSupplier(NamespaceNaming.forTestClass(MyTest.class));
}
```

---

## 21.3. Field Scope and Lifecycle

### 21.3.1. Supported Scopes

| Scope | Annotation | Lifecycle | Use Case |
|-------|------------|-----------|----------|
| Class-level | `static @Pod` | Started in @BeforeAll, stopped in @AfterAll | Shared across all test methods |
| Method-level | `@Pod` (non-static) | Started before each test, stopped after | Per-test isolation |

### 21.3.2. Recommendation

Static fields are recommended because Kubernetes pods have significant startup time. Method-level pods should only be used when test isolation is critical.

---

## 21.4. Startup Order and Dependencies

### 21.4.1. Default Behavior: Parallel

Pods without declared dependencies start concurrently for maximum performance.

### 21.4.2. Dependency Declaration

Use `@DependsOn` for explicit ordering:

```java
@Pod
static PostgreSQLPod postgres = new PostgreSQLPod();

@Pod
@DependsOn("postgres")
static GenericTestPod orderService = new GenericTestPod("mycompany/order-service")
    .withEnv("DATABASE_URL", "${postgres.internal.uri}");
```

### 21.4.3. Sequential Fallback

When `@TestPodGroup(parallel = false)`, pods start in registration/declaration order.

---

## 21.5. Connection Information

### 21.5.1. Dual Access Pattern

**Direct Accessors** (for test code):
```java
String url = postgres.getConnectionString();  // External URL for test
```

**Spring Properties** (for Spring Boot integration):
```java
@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getConnectionString);
}
```

### 21.5.2. Property Naming Convention

| Property | Description |
|----------|-------------|
| `{pod}.host` | External host (test code access) |
| `{pod}.port` | External port (test code access) |
| `{pod}.uri` | External connection URI |
| `{pod}.internal.host` | Internal host (pod-to-pod) |
| `{pod}.internal.port` | Internal port (pod-to-pod) |
| `{pod}.internal.uri` | Internal connection URI |

**External by default, internal on request.**

### 21.5.3. Spring Property Mapping

Configurable mapping to standard Spring Boot properties:

```java
@Pod(springProperties = true)  // Also publishes spring.datasource.url, etc.
static PostgreSQLPod postgres = new PostgreSQLPod();
```

Default publishes pod-prefixed names. With `springProperties = true`, also publishes standard Spring names.

---

## 21.6. TestPodGroup and TestPodCatalog

### 21.6.1. TestPodGroup

Groups related pods that share lifecycle and namespace:

```java
@TestPodGroup("infrastructure")
static TestPodGroup infrastructure = TestPodGroup.builder()
    .add(postgres)
    .add(kafka)
    .build();

@TestPodGroup("services")
@DependsOn("infrastructure")
static TestPodGroup services = TestPodGroup.builder()
    .add(orderService)
    .add(inventoryService)
    .build();
```

### 21.6.2. TestPodCatalog

Typed registry providing fluent access for reusability:

```java
// Definition
static KafkaPod kafkaPod = new KafkaPod();
static PostgreSQLPod postgresPod = new PostgreSQLPod();
static RedisPod redisPod = new RedisPod();

@RegisterTestPodCatalog
static TestPodCatalog catalog = TestPodCatalog.builder()
    .register(kafkaPod)
    .register(postgresPod)
    .register(redisPod)
    .build();

// Usage - fluent typed access (method names derived from variable names)
@RegisterTestPodGroup
static TestPodGroup infra = catalog.kafkaPod().postgresPod().asGroup();
```

**Implementation:** Dynamic proxy with method interception. Consider Java 21+ variable name extraction if feasible.

### 21.6.3. Inline Grouping

For simple cases, use `group` attribute:

```java
@Pod(group = "infrastructure")
static PostgreSQLPod postgres = new PostgreSQLPod();

@Pod(group = "infrastructure")
static KafkaPod kafka = new KafkaPod();
```

---

## 21.7. PropertyContext

### 21.7.1. Hierarchical Design

Each group has its own PropertyContext that inherits from dependencies:

```
infrastructure.context
    └── services.context (inherits infrastructure properties)
        └── test.context (inherits all)
```

### 21.7.2. Property Flow

```java
// Infrastructure pods publish their properties
postgres.publishProperties(infrastructureContext);
kafka.publishProperties(infrastructureContext);

// Service pods receive infrastructure properties
orderService.withPropertyContext(servicesContext);  // Can read postgres.*, kafka.*
```

### 21.7.3. Template Interpolation

```java
orderService.withEnv("DATABASE_URL", "${postgres.internal.uri}");
// Resolved at start time from PropertyContext
```

---

## 21.8. Namespace Management

### 21.8.1. Default Behavior

- One namespace per test class. Support multiple namespaces in the future
- Random suffix for parallel execution safety: `testpods-{context}-{5chars}`
- Deleted after test class completes

### 21.8.2. Override Options

```java
@TestPods(namespace = "my-fixed-namespace")  // Fixed name
class MyTest { }

@Pod(namespace = "other-namespace")  // Pod in different namespace
static GenericTestPod external = new GenericTestPod("nginx");
```

### 21.8.3. Cleanup Policy

Configurable via annotation:

```java
@TestPods(cleanup = CleanupPolicy.MANAGED)    // Only managed resources (default)
@TestPods(cleanup = CleanupPolicy.NAMESPACE)  // Delete entire namespace
@TestPods(cleanup = CleanupPolicy.NONE)       // Leave everything (for debugging)
```

---

## 21.9. Error Handling and Debugging

### 21.9.1. Fail-Fast Configuration

```java
@TestPods(failFast = true)   // Default: stop all on first failure
@TestPods(failFast = false)  // Continue with partial start for inspection
```

### 21.9.2. Debug Mode

```java
@TestPods(debug = true)  // Pods stay up after test failure
```

**Future:** Support remote debugging into cluster pods from IDE.

### 21.9.3. Dev Mode

For local development with long-lived pods:

```java
@TestPods(devMode = true)  // Start pods, wait for shutdown signal, don't run tests
```

Use case: Developer runs this test to start infrastructure, then runs Spring Boot app from IDE connecting to the cluster.

---

## 21.10. JUnit Integration Details

### 21.10.1. Target Version

JUnit 5 (current). Migrate to JUnit 6 when released.

### 21.10.2. Extension Lifecycle

```
@BeforeAll (TestPodsExtension)
├── Discover @Pod fields
├── Build dependency graph
├── Create namespaces (random suffix)
├── Start groups/pods (respecting dependencies, parallel where allowed)
├── Populate PropertyContext
└── Ready for test methods

@AfterAll (TestPodsExtension)
├── Stop pods (reverse order)
├── Delete namespace (per cleanup policy)
└── Close cluster connections
```

### 21.10.3. Parallel Test Execution

Supported. Random namespace suffix ensures isolation when same test class runs in parallel (e.g., CI matrix builds).

---

## 21.11. Concrete Pod Implementations

### 21.11.1. MVP Priority

1. **GenericTestPod** - Any container (complete)
2. **PostgreSQLPod** - Relational database (to implement)
3. **KafkaPod** - Message broker with KRaft mode, no ZooKeeper (to implement)

### 21.11.2. Later Additions

- MongoDB (partially complete)
- Redis
- RabbitMQ
- MySQL

---

## 21.12. Implementation Priority

### Phase 1: Basic JUnit Integration
1. Implement `TestPodsExtension` with `@TestPods` and `@Pod` support
2. Static field lifecycle management
3. Single namespace per test class
4. Parallel startup (no dependencies)

### Phase 2: Dependencies and Groups
1. `@DependsOn` annotation
2. `TestPodGroup` with dependency ordering
3. Instance field support

### Phase 3: PropertyContext and Spring Integration
1. PropertyContext implementation (hierarchical)
2. `@DynamicPropertySource` integration
3. Template interpolation (`${pod.property}`)

### Phase 4: TestPodCatalog and Advanced Features
1. `TestPodCatalog` with typed accessors
2. Dev mode
3. Debug mode
4. Cleanup policies

---

## 21.13. Example: Complete Test Class

```java
@TestPods
@SpringBootTest(classes = OrderServiceApplication.class)
class OrderServiceIntegrationTest {

    // Infrastructure
    @Pod(group = "infrastructure")
    static PostgreSQLPod postgres = new PostgreSQLPod()
        .withDatabase("orders");

    @Pod(group = "infrastructure")
    static KafkaPod kafka = new KafkaPod()
        .withTopic("order-events");

    // Companion service
    @Pod
    @DependsOn("infrastructure")
    static GenericTestPod inventoryService = new GenericTestPod("mycompany/inventory:latest")
        .withEnv("DATABASE_URL", "${postgres.internal.uri}")
        .withEnv("KAFKA_BROKERS", "${kafka.internal.bootstrapServers}");

    // Spring Boot property injection
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getConnectionString);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("inventory.service.url", inventoryService::getExternalUrl);
    }

    @Autowired
    private OrderService orderService;

    @Test
    void shouldCreateOrderAndNotifyInventory() {
        // Test with full infrastructure available
        Order order = orderService.createOrder(new CreateOrderRequest("SKU-123", 2));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Verify inventory service received the event
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            // Use inventoryService.getExternalUrl() to query
        });
    }
}
```

---

## 21.14. Open Items for Future Interviews

1. **Example Services Interview**: Design tests for the example Spring Boot services in the project (using Kafka and PostgreSQL)
2. **System Tests Interview**: Design the `system-tests` service for end-to-end testing of entire application stacks
3. **Operator Support**: Testing with Kubernetes operators (Strimzi, Zalando Postgres Operator)
4. **Remote Debugging**: IDE remote debug support for cluster pods
