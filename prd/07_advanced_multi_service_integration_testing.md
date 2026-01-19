# 7. Advanced Multi-Service Integration Testing

<!-- TOC -->
- [7. Advanced Multi-Service Integration Testing](#7-advanced-multi-service-integration-testing)
  - [7.1. Scenario Overview](#71-scenario-overview)
  - [7.2. Detailed Scenario: E-Commerce Order Service](#72-detailed-scenario-e-commerce-order-service)
  - [7.3. Functional Requirements: Multi-Service Integration](#73-functional-requirements-multi-service-integration)
    - [7.3.1. FR-11: Test Environment Composition](#731-fr-11-test-environment-composition)
  - [7.4. API Design Examples](#74-api-design-examples)
    - [7.4.1. Approach 1: Separate Configuration Classes (Recommended)](#741-approach-1-separate-configuration-classes-recommended)
    - [7.4.2. Approach 2: JUnit Extension with Annotations](#742-approach-2-junit-extension-with-annotations)
    - [7.4.3. Approach 3: Declarative YAML Configuration](#743-approach-3-declarative-yaml-configuration)
  - [7.5. Connection Info Propagation Details](#75-connection-info-propagation-details)
    - [7.5.1. Automatic Property Mapping](#751-automatic-property-mapping)
    - [7.5.2. Custom Property Mapping](#752-custom-property-mapping)
    - [7.5.3. Environment Variable Conventions](#753-environment-variable-conventions)
  - [7.6. Lifecycle Coordination Diagram](#76-lifecycle-coordination-diagram)
  - [7.7. Design Considerations](#77-design-considerations)
    - [7.7.1. Why Separate Infrastructure and Services?](#771-why-separate-infrastructure-and-services)
    - [7.7.2. Why Explicit Connection Info Propagation?](#772-why-explicit-connection-info-propagation)
    - [7.7.3. Why Builder Pattern over Annotations?](#773-why-builder-pattern-over-annotations)
<!-- /TOC -->

## 7.1. Scenario Overview

A critical use case for Testpods is testing a Spring Boot service that operates within a larger system consisting of:
- **Shared infrastructure**: Databases, message brokers, caches (e.g., Kafka, PostgreSQL, Redis)
- **Companion services**: Other Spring Boot microservices that are part of the same application/system
- **Service under test**: The Spring Boot application being tested, started via standard `@SpringBootTest`

The testing challenge is orchestrating all these components with proper:
1. **Startup ordering**: Infrastructure → Companion services → Service under test
2. **Connection info propagation**: All services need connection details for shared infrastructure
3. **Readiness coordination**: Each tier must be fully ready before the next tier starts
4. **Spring Boot integration**: The service under test uses standard Spring Boot Test mechanisms

## 7.2. Detailed Scenario: E-Commerce Order Service

**System Components:**
- **Infrastructure:**
  - PostgreSQL (shared database)
  - Kafka (event streaming)
  - Redis (caching/session store)

- **Companion Services:**
  - `inventory-service`: Manages product inventory, consumes Kafka events, uses PostgreSQL
  - `notification-service`: Sends notifications, consumes Kafka events, uses Redis for rate limiting

- **Service Under Test:**
  - `order-service`: Processes orders, publishes Kafka events, uses PostgreSQL, calls inventory-service

**Test Requirements:**
1. Start infrastructure and wait for readiness
2. Inject infrastructure connection info into companion services
3. Start companion services and wait for readiness
4. Make infrastructure connection info available to Spring Boot Test context
5. Spring Boot Test starts order-service with injected properties
6. Execute integration tests that exercise the full system
7. Tear down in reverse order

## 7.3. Functional Requirements: Multi-Service Integration

### 7.3.1. FR-11: Test Environment Composition

- **FR-11.1**: **Infrastructure Declaration**
  - Declare multiple infrastructure components as a cohesive unit
  - Each component has a unique name for reference
  - Components are individually configurable
  - Infrastructure starts as a single coordinated operation
  - Readiness checks ensure all components are available before proceeding

- **FR-11.2**: **Service Declaration**
  - Declare multiple application services as a cohesive unit
  - Services can declare dependencies on infrastructure components
  - Services can declare dependencies on other services
  - Services automatically receive connection info from declared dependencies
  - Environment variables and/or configuration files injected automatically

- **FR-11.3**: **Dependency Resolution and Ordering**
  - Automatic startup ordering based on declared dependencies
  - Infrastructure tier always starts before dependent services
  - Inter-service dependencies respected (service A depends on service B)
  - Parallel startup within a tier where dependencies allow
  - Configurable timeouts for each tier's readiness

- **FR-11.4**: **Connection Info Management**
  - Automatic extraction of connection info from started infrastructure
  - Connection info available in multiple formats:
    - Spring Boot properties (`spring.datasource.url`, `spring.kafka.bootstrap-servers`, etc.)
    - Environment variables (`DATABASE_URL`, `KAFKA_BROKERS`, etc.)
    - Programmatic access (Java objects with typed accessors)
  - Single source of truth for connection info
  - Support for custom connection info mappings

- **FR-11.5**: **Spring Boot Test Integration**
  - Seamless integration with `@SpringBootTest`
  - Support for `@DynamicPropertySource` for property injection
  - Lifecycle coordination with Spring TestContext
  - Test environment ready before Spring context initialization
  - Support for `@TestConfiguration` to customize test context

- **FR-11.6**: **Reusable Test Environments**
  - Define test environments in separate Java classes
  - Test environments shareable across multiple test classes
  - Support for environment inheritance and composition
  - Autowiring support for test environment components
  - Environment variants (e.g., minimal vs. full infrastructure)

- **FR-11.7**: **Service Container Configuration**
  - Full Kubernetes deployment options for companion services
  - Environment variable injection from infrastructure
  - Resource limits and requests
  - Health checks and readiness probes
  - Service exposure and inter-service networking
  - Volume mounts for configuration files

## 7.4. API Design Examples

The following examples illustrate the developer experience for multi-service integration testing. These are conceptual designs to be refined during implementation.

### 7.4.1. Approach 1: Separate Configuration Classes (Recommended)

This approach separates infrastructure and services into reusable configuration classes that can be composed and shared across tests.

**Infrastructure Definition:**
```java
/**
 * Defines the shared infrastructure for the e-commerce system.
 * Can be reused across multiple test classes.
 */
public class ECommerceInfrastructure {

    private final TestpodsInfrastructure infrastructure;

    public ECommerceInfrastructure() {
        this.infrastructure = TestpodsInfrastructure.builder()
            .postgresql("orders-db")
                .database("ecommerce")
                .schema("orders", "inventory", "notifications")
                .username("app")
                .password("secret")
                .initScript("classpath:db/init.sql")
                .resources()
                    .memory("512Mi")
                    .cpu("500m")
                .endResources()
            .endPostgresql()

            .kafka("events")
                .brokers(1)
                .topics(
                    topic("order-events").partitions(3).replication(1),
                    topic("inventory-events").partitions(3).replication(1),
                    topic("notification-events").partitions(1).replication(1)
                )
                .resources()
                    .memory("1Gi")
                    .cpu("1000m")
                .endResources()
            .endKafka()

            .redis("cache")
                .maxMemory("256mb")
                .evictionPolicy(EvictionPolicy.ALLKEYS_LRU)
            .endRedis()

            .build();
    }

    public void start() {
        infrastructure.start();
        infrastructure.awaitReady(Duration.ofMinutes(2));
    }

    public void stop() {
        infrastructure.stop();
    }

    // Typed accessors for connection info
    public PostgresConnectionInfo postgresConnection() {
        return infrastructure.postgresql("orders-db").connectionInfo();
    }

    public KafkaConnectionInfo kafkaConnection() {
        return infrastructure.kafka("events").connectionInfo();
    }

    public RedisConnectionInfo redisConnection() {
        return infrastructure.redis("cache").connectionInfo();
    }

    // Spring Boot property injection
    public Map<String, String> asSpringProperties() {
        return infrastructure.toSpringProperties();
    }

    // Environment variables for container injection
    public Map<String, String> asEnvironment() {
        return infrastructure.toEnvironmentVariables();
    }
}
```

**Companion Services Definition:**
```java
/**
 * Defines companion services that integrate with the infrastructure.
 */
public class ECommerceServices {

    private final TestpodsServices services;

    public ECommerceServices(ECommerceInfrastructure infrastructure) {
        this.services = TestpodsServices.builder()
            .service("inventory-service")
                .image("mycompany/inventory-service:latest")
                .environment(infrastructure.asEnvironment())
                .environment("SERVICE_NAME", "inventory-service")
                .environment("LOG_LEVEL", "DEBUG")
                .port(8081)
                .healthCheck()
                    .httpGet("/actuator/health")
                    .port(8081)
                    .initialDelay(Duration.ofSeconds(10))
                    .period(Duration.ofSeconds(5))
                .endHealthCheck()
                .readinessProbe()
                    .httpGet("/actuator/health/readiness")
                    .port(8081)
                .endReadinessProbe()
                .resources()
                    .memory("512Mi")
                    .cpu("500m")
                .endResources()
            .endService()

            .service("notification-service")
                .image("mycompany/notification-service:latest")
                .environment(infrastructure.asEnvironment())
                .environment("SERVICE_NAME", "notification-service")
                .environment("SMTP_HOST", "mailhog")  // mock mail server
                .port(8082)
                .healthCheck()
                    .httpGet("/actuator/health")
                    .port(8082)
                .endHealthCheck()
            .endService()

            .build();
    }

    public void start() {
        services.start();
        services.awaitReady(Duration.ofMinutes(1));
    }

    public void stop() {
        services.stop();
    }

    // Get base URLs for companion services
    public String inventoryServiceUrl() {
        return services.service("inventory-service").getBaseUrl();
    }

    public String notificationServiceUrl() {
        return services.service("notification-service").getBaseUrl();
    }
}
```

**Composed Test Environment:**
```java
/**
 * Complete test environment combining infrastructure and services.
 * Manages lifecycle and provides unified access to connection info.
 */
public class ECommerceTestEnvironment implements TestEnvironment {

    private final ECommerceInfrastructure infrastructure;
    private final ECommerceServices services;
    private boolean started = false;

    public ECommerceTestEnvironment() {
        this.infrastructure = new ECommerceInfrastructure();
        this.services = new ECommerceServices(infrastructure);
    }

    @Override
    public void start() {
        if (!started) {
            // Ordered startup: infrastructure first, then services
            infrastructure.start();
            services.start();
            started = true;
        }
    }

    @Override
    public void stop() {
        if (started) {
            // Reverse order shutdown
            services.stop();
            infrastructure.stop();
            started = false;
        }
    }

    // Expose all connection properties for Spring Boot Test
    public Map<String, String> getSpringProperties() {
        Map<String, String> properties = new HashMap<>(infrastructure.asSpringProperties());
        properties.put("inventory.service.url", services.inventoryServiceUrl());
        properties.put("notification.service.url", services.notificationServiceUrl());
        return properties;
    }

    // Direct access to components for test assertions
    public ECommerceInfrastructure infrastructure() {
        return infrastructure;
    }

    public ECommerceServices services() {
        return services;
    }
}
```

**JUnit Test Using the Environment:**
```java
@SpringBootTest(classes = OrderServiceApplication.class)
@Testpods
class OrderServiceIntegrationTest {

    // Shared environment instance - started once for all tests in class
    static ECommerceTestEnvironment testEnv = new ECommerceTestEnvironment();

    @BeforeAll
    static void startEnvironment() {
        testEnv.start();
    }

    @AfterAll
    static void stopEnvironment() {
        testEnv.stop();
    }

    // Inject infrastructure connection properties into Spring Boot Test context
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        testEnv.getSpringProperties().forEach(registry::add);
    }

    // Spring beans from the application under test
    @Autowired
    private OrderService orderService;

    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Test
    void shouldCreateOrderAndPublishEvent() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest("SKU-123", 2);

        // Act
        Order order = orderService.createOrder(request);

        // Assert - verify order created
        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Assert - verify event published to Kafka and processed by inventory-service
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            // Query inventory-service to verify it received the event
            ResponseEntity<InventoryResponse> response = restTemplate.getForEntity(
                testEnv.services().inventoryServiceUrl() + "/api/inventory/SKU-123",
                InventoryResponse.class
            );
            assertThat(response.getBody().getReservedQuantity()).isEqualTo(2);
        });
    }

    @Test
    void shouldHandleInventoryServiceFailure() {
        // Test resilience when inventory-service is unavailable
        testEnv.services().service("inventory-service").stop();

        try {
            CreateOrderRequest request = new CreateOrderRequest("SKU-456", 1);
            Order order = orderService.createOrder(request);

            // Should still create order with PENDING_INVENTORY status
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_INVENTORY);
        } finally {
            testEnv.services().service("inventory-service").start();
        }
    }
}
```

### 7.4.2. Approach 2: JUnit Extension with Annotations

For simpler cases or when preferring annotation-based configuration:

```java
@SpringBootTest
@TestpodsEnvironment(
    infrastructure = @Infrastructure(
        postgresql = @PostgreSQL(
            name = "db",
            database = "ecommerce",
            initScript = "classpath:db/init.sql"
        ),
        kafka = @Kafka(
            name = "events",
            topics = {"order-events", "inventory-events"}
        ),
        redis = @Redis(name = "cache")
    ),
    services = @Services({
        @Service(
            name = "inventory-service",
            image = "mycompany/inventory-service:latest",
            port = 8081,
            connectsTo = {"db", "events"}
        ),
        @Service(
            name = "notification-service",
            image = "mycompany/notification-service:latest",
            port = 8082,
            connectsTo = {"events", "cache"}
        )
    })
)
class OrderServiceIntegrationTest {

    @Autowired
    TestpodsContext testpods;  // Injected by Testpods extension

    @Autowired
    OrderService orderService;

    @Test
    void shouldProcessOrder() {
        // testpods.services().get("inventory-service") available for assertions
        // ...
    }
}
```

### 7.4.3. Approach 3: Declarative YAML Configuration

For teams preferring configuration-as-code or sharing environments across language boundaries:

**test-environment.yaml:**
```yaml
apiVersion: testpods/v1
kind: TestEnvironment
metadata:
  name: ecommerce-integration
spec:
  infrastructure:
    postgresql:
      - name: orders-db
        database: ecommerce
        initScript: classpath:db/init.sql
        resources:
          memory: 512Mi
          cpu: 500m
    kafka:
      - name: events
        brokers: 1
        topics:
          - name: order-events
            partitions: 3
          - name: inventory-events
            partitions: 3
    redis:
      - name: cache
        maxMemory: 256mb

  services:
    - name: inventory-service
      image: mycompany/inventory-service:latest
      port: 8081
      connectsTo: [orders-db, events]
      environment:
        SERVICE_NAME: inventory-service
      healthCheck:
        httpGet:
          path: /actuator/health
          port: 8081

    - name: notification-service
      image: mycompany/notification-service:latest
      port: 8082
      connectsTo: [events, cache]

  springProperties:
    mapping:
      orders-db: spring.datasource
      events: spring.kafka
      cache: spring.data.redis
```

**Java test using YAML:**
```java
@SpringBootTest
@Testpods
@TestEnvironmentConfig("classpath:test-environment.yaml")
class OrderServiceIntegrationTest {

    @Autowired
    TestpodsEnvironment env;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        TestpodsEnvironment.loadFromConfig("classpath:test-environment.yaml")
            .getSpringProperties()
            .forEach(registry::add);
    }

    // ... tests
}
```

## 7.5. Connection Info Propagation Details

### 7.5.1. Automatic Property Mapping

Testpods automatically maps infrastructure connection info to standard Spring Boot properties:

| Infrastructure | Spring Property | Example Value |
|---------------|-----------------|---------------|
| PostgreSQL | `spring.datasource.url` | `jdbc:postgresql://orders-db:5432/ecommerce` |
| PostgreSQL | `spring.datasource.username` | `app` |
| PostgreSQL | `spring.datasource.password` | `secret` |
| Kafka | `spring.kafka.bootstrap-servers` | `events-kafka:9092` |
| Redis | `spring.data.redis.host` | `cache-redis` |
| Redis | `spring.data.redis.port` | `6379` |

### 7.5.2. Custom Property Mapping

```java
infrastructure.toSpringProperties(PropertyMapping.builder()
    .map("orders-db").toPrefix("spring.datasource")
    .map("events").toPrefix("app.messaging.kafka")
    .map("cache").toPrefix("app.cache.redis")
    .build()
);
```

### 7.5.3. Environment Variable Conventions

For injection into companion service containers:

```java
infrastructure.toEnvironmentVariables(EnvVarNaming.builder()
    .convention(EnvVarNaming.SCREAMING_SNAKE_CASE)
    .prefix("APP_")
    .build()
);
// Produces: APP_DATABASE_URL, APP_DATABASE_USERNAME, APP_KAFKA_BROKERS, etc.
```

## 7.6. Lifecycle Coordination Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Test Lifecycle                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. @BeforeAll / Testpods Extension Initialization                          │
│     │                                                                       │
│     ├─► Start Infrastructure (Parallel where possible)                      │
│     │   ├─► PostgreSQL ──► Wait for ready ─┐                                │
│     │   ├─► Kafka ───────► Wait for ready ─┼─► Infrastructure Ready         │
│     │   └─► Redis ───────► Wait for ready ─┘                                │
│     │                                                                       │
│     ├─► Extract Connection Info from Infrastructure                         │
│     │                                                                       │
│     ├─► Start Companion Services (Respecting dependencies)                  │
│     │   ├─► inventory-service (inject env vars) ──► Wait for ready ─┐       │
│     │   └─► notification-service (inject env vars) ► Wait for ready ─┼─►    │
│     │                                                      Services Ready   │
│     │                                                                       │
│     └─► Make Connection Info Available to Spring Test Context               │
│                                                                             │
│  2. Spring TestContext Initialization                                       │
│     │                                                                       │
│     ├─► @DynamicPropertySource populates Spring Environment                 │
│     └─► Spring Boot Application starts (order-service)                      │
│                                                                             │
│  3. Test Methods Execute                                                    │
│     │                                                                       │
│     ├─► @Test methods run against fully integrated system                   │
│     └─► Access to testEnv for assertions and control                        │
│                                                                             │
│  4. @AfterAll / Cleanup                                                     │
│     │                                                                       │
│     ├─► Stop Companion Services                                             │
│     ├─► Stop Infrastructure                                                 │
│     └─► Cleanup Kubernetes resources                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 7.7. Design Considerations

### 7.7.1. Why Separate Infrastructure and Services?

1. **Reusability**: Infrastructure definitions can be shared across multiple test classes
2. **Clarity**: Clear separation between stateless infrastructure and application services
3. **Flexibility**: Different tests can use same infrastructure with different service configurations
4. **Lifecycle Control**: Infrastructure can be started once and reused across test suites

### 7.7.2. Why Explicit Connection Info Propagation?

1. **Transparency**: Developers see exactly what connection info is being injected
2. **Debuggability**: Easy to inspect connection strings when tests fail
3. **Customization**: Allows overriding or transforming connection info as needed
4. **Framework Independence**: Works with Spring Boot, Quarkus, Micronaut, or plain Java

### 7.7.3. Why Builder Pattern over Annotations?

1. **IDE Support**: Full autocomplete and type checking during construction
2. **Conditional Logic**: Can programmatically vary configuration based on conditions
3. **Composition**: Builders can be composed and extended
4. **Readability**: Complex configurations remain readable with proper indentation
5. **Annotations Available**: Annotation-based approach available for simpler cases
