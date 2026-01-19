# 12. API Design Principles

<!-- TOC -->
- [12. API Design Principles](#12-api-design-principles)
  - [12.1. Fluent Interface Pattern](#121-fluent-interface-pattern)
  - [12.2. Declarative Configuration](#122-declarative-configuration)
  - [12.3. Module System](#123-module-system)
<!-- /TOC -->

## 12.1. Fluent Interface Pattern

```java
// Example conceptual API (to be refined)
PostgreSQLContainer postgres = new PostgreSQLContainer()
    .withDatabaseName("testdb")
    .withUsername("user")
    .withPassword("password")
    .withResourceLimits("500m", "512Mi")
    .withPersistentStorage("1Gi")
    .start();
```

## 12.2. Declarative Configuration

```java
// Multi-container pod example
PodContainer pod = new PodBuilder()
    .withName("app-with-sidecar")
    .addContainer(
        new GenericContainer("nginx:latest")
            .withName("web")
            .withPort(80)
    )
    .addContainer(
        new GenericContainer("fluent/fluentd:latest")
            .withName("log-forwarder")
    )
    .withConfigMap("app-config", configMapData)
    .start();
```

## 12.3. Module System

```java
// Using built-in module
@Testpods
class MyIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer();

    @Container
    static KafkaContainer kafka = new KafkaContainer()
        .withBrokers(3);

    @Test
    void testWithDependencies() {
        // Test code with postgres and kafka available
    }
}
```
