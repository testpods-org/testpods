# 12. API Design Principles

<!-- TOC -->
- [12. API Design Principles](#12-api-design-principles)
  - [12.1. Fluent Interface Pattern](#121-fluent-interface-pattern)
  - [12.2. Declarative Configuration](#122-declarative-configuration)
  - [12.3. JUnit Integration with Annotations](#123-junit-integration-with-annotations)
  - [12.4. Dependency Management](#124-dependency-management)
  - [12.5. TestPodGroup and TestPodCatalog](#125-testpodgroup-and-testpodcatalog)
  - [12.6. Property Context and Spring Integration](#126-property-context-and-spring-integration)
  - [12.7. Configuration Options](#127-configuration-options)
<!-- /TOC -->

## 12.1. Fluent Interface Pattern

All pod types use a fluent builder pattern with generics for type-safe method chaining:

```java
// PostgreSQL with fluent configuration
PostgreSQLPod postgres = new PostgreSQLPod()
    .withVersion("16")
    .withDatabase("orders")
    .withUsername("app")
    .withPassword("secret")
    .withResources("500m", "512Mi");

// Kafka with KRaft mode (no ZooKeeper)
KafkaPod kafka = new KafkaPod()
    .withKraftMode(true)
    .withTopic("order-events", 3, 1)  // topic, partitions, replication
    .withTopic("inventory-events", 3, 1);

// Generic container for any image
GenericTestPod nginx = new GenericTestPod("nginx:1.25")
    .withPort(80)
    .withEnv("NGINX_HOST", "localhost")
    .withHttpReadinessProbe("/health", 80);
```

## 12.2. Declarative Configuration

Multi-container pods with init containers and sidecars:

```java
GenericTestPod appWithSidecar = new GenericTestPod("myapp:latest")
    .withName("app-with-logging")
    .withPort(8080)
    .withInitContainer(init -> init
        .withName("init-config")
        .withImage("busybox:latest")
        .withCommand("sh", "-c", "cp /config/* /app/config/")
    )
    .withSidecar(sidecar -> sidecar
        .withName("log-forwarder")
        .withImage("fluent/fluentd:latest")
        .withEnv("FLUENTD_CONF", "fluent.conf")
    );
```

Low-level Kubernetes API access when needed:

```java
GenericTestPod customPod = new GenericTestPod("myapp:latest")
    .withPodCustomizer(podSpec -> podSpec
        .withTerminationGracePeriodSeconds(60L)
        .withServiceAccountName("my-service-account")
    );
```

## 12.3. JUnit Integration with Annotations

### 12.3.1. Basic Test with Single Pod

```java
@TestPods
class SimpleIntegrationTest {

    @Pod
    static PostgreSQLPod postgres = new PostgreSQLPod()
        .withDatabase("testdb");

    @Test
    void shouldConnectToDatabase() {
        String url = postgres.getConnectionString();
        // Test with database
    }
}
```

### 12.3.2. Multiple Pods with Dependencies

```java
@TestPods
class MultiPodIntegrationTest {

    @Pod(group = "infrastructure")
    static PostgreSQLPod postgres = new PostgreSQLPod();

    @Pod(group = "infrastructure")
    static KafkaPod kafka = new KafkaPod();

    @Pod
    @DependsOn("infrastructure")
    static GenericTestPod orderService = new GenericTestPod("mycompany/order-service:latest")
        .withEnv("DATABASE_URL", "${postgres.internal.uri}")
        .withEnv("KAFKA_BROKERS", "${kafka.internal.bootstrapServers}");

    @Test
    void shouldProcessOrders() {
        // All pods started in correct order
    }
}
```

### 12.3.3. Spring Boot Test Integration

```java
@TestPods
@SpringBootTest(classes = OrderServiceApplication.class)
class OrderServiceSpringTest {

    @Pod
    static PostgreSQLPod postgres = new PostgreSQLPod()
        .withDatabase("orders");

    @Pod
    static KafkaPod kafka = new KafkaPod()
        .withTopic("order-events");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getConnectionString);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private OrderService orderService;

    @Test
    void shouldCreateOrder() {
        Order order = orderService.createOrder(new CreateOrderRequest("SKU-123", 2));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }
}
```

## 12.4. Dependency Management

### 12.4.1. Explicit Dependencies

```java
@Pod
static PostgreSQLPod postgres = new PostgreSQLPod();

@Pod
@DependsOn("postgres")  // Waits for postgres to be ready
static GenericTestPod app = new GenericTestPod("myapp:latest");
```

### 12.4.2. Group Dependencies

```java
@Pod(group = "infrastructure")
static PostgreSQLPod postgres = new PostgreSQLPod();

@Pod(group = "infrastructure")
static KafkaPod kafka = new KafkaPod();

@Pod(group = "services")
@DependsOn("infrastructure")  // Waits for entire infrastructure group
static GenericTestPod orderService = new GenericTestPod("order-service:latest");

@Pod(group = "services")
@DependsOn("infrastructure")
static GenericTestPod inventoryService = new GenericTestPod("inventory-service:latest");
```

## 12.5. TestPodGroup and TestPodCatalog

### 12.5.1. TestPodCatalog for Typed Access

```java
// Define pods
static KafkaPod kafkaPod = new KafkaPod().withKraftMode(true);
static PostgreSQLPod postgresPod = new PostgreSQLPod().withDatabase("app");
static RedisPod redisPod = new RedisPod();

// Register in catalog - method names derived from variable names
@RegisterTestPodCatalog
static TestPodCatalog catalog = TestPodCatalog.builder()
    .register(kafkaPod)
    .register(postgresPod)
    .register(redisPod)
    .build();

// Create group with fluent typed access (no strings!)
@RegisterTestPodGroup
static TestPodGroup infrastructure = catalog.kafkaPod().postgresPod().redisPod().asGroup();
```

### 12.5.2. TestPodGroup with Dependencies

```java
@RegisterTestPodGroup
static TestPodGroup infrastructure = TestPodGroup.builder()
    .add(postgres)
    .add(kafka)
    .build();

@RegisterTestPodGroup
@DependsOn("infrastructure")
static TestPodGroup services = TestPodGroup.builder()
    .add(orderService)
    .add(inventoryService)
    .dependsOn(infrastructure)
    .build();
```

## 12.6. Property Context and Spring Integration

### 12.6.1. Property Naming Convention

| Property | Description |
|----------|-------------|
| `postgres.host` | External host for test code |
| `postgres.port` | External port for test code |
| `postgres.uri` | External connection URI |
| `postgres.internal.host` | Internal host for pod-to-pod |
| `postgres.internal.port` | Internal port for pod-to-pod |
| `postgres.internal.uri` | Internal connection URI |

### 12.6.2. Template Interpolation

```java
// Environment variables with property references
GenericTestPod app = new GenericTestPod("myapp:latest")
    .withEnv("DATABASE_URL", "${postgres.internal.uri}")
    .withEnv("KAFKA_BROKERS", "${kafka.internal.bootstrapServers}")
    .withEnv("REDIS_HOST", "${redis.internal.host}");
```

### 12.6.3. Spring Properties Mapping

```java
// Default: pod-prefixed properties
// postgres.uri, postgres.username, etc.

// With springProperties=true: also publishes standard Spring names
@Pod(springProperties = true)
static PostgreSQLPod postgres = new PostgreSQLPod();
// Publishes: spring.datasource.url, spring.datasource.username, etc.
```

## 12.7. Configuration Options

### 12.7.1. Test-Level Configuration

```java
@TestPods(
    namespace = "custom-namespace",     // Override auto-generated namespace
    cleanup = CleanupPolicy.NAMESPACE,  // MANAGED (default), NAMESPACE, or NONE
    failFast = true,                    // Stop all on first failure (default)
    debug = false,                      // Keep pods running after failure
    devMode = false                     // Start pods without running tests
)
class ConfiguredTest {
    // ...
}
```

### 12.7.2. Pod-Level Configuration

```java
@Pod(
    group = "infrastructure",           // Logical grouping
    namespace = "other-namespace",      // Override test namespace
    springProperties = true             // Publish Spring property names
)
static PostgreSQLPod postgres = new PostgreSQLPod();
```

### 12.7.3. Development Mode

For local development with long-lived pods:

```java
@TestPods(devMode = true)
class DevEnvironment {

    @Pod
    static PostgreSQLPod postgres = new PostgreSQLPod();

    @Pod
    static KafkaPod kafka = new KafkaPod();

    // Run this test to start pods and wait
    // Developer can then run Spring Boot app from IDE
    // connecting to these pods
    @Test
    void startDevEnvironment() {
        System.out.println("Postgres: " + postgres.getConnectionString());
        System.out.println("Kafka: " + kafka.getBootstrapServers());
        // Test doesn't run, pods stay up until manual shutdown
    }
}
```
