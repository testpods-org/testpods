# feat: System Tests Skeleton Module

## Overview

Create a `system-tests` Spring Boot module that serves as a test harness for testing `order-service` and `product-service` together. This is a **test-only module** with no main application code - only integration tests that can orchestrate and verify the behavior of both microservices.

For the initial skeleton, we create the bare minimum structure with a simple no-op test that compiles and runs. No actual test logic yet.

## Problem Statement / Motivation

Currently, each service has its own unit/integration tests that test the service in isolation. There is no way to test the complete order flow across both services:
1. Create product in product-service
2. Place order in order-service (which validates product via REST)
3. Order-service publishes `OrderPlacedEvent` to Kafka
4. Product-service consumes event and decrements stock

A dedicated system-tests module provides a place for these end-to-end tests.

## Proposed Solution

Create a minimal test-only Maven module:
- Copy structure from `reference-example-service` template
- kep all `src/main/java` code
- Add a simple no-op test that passes
- Configure connection properties for external Kafka/PostgreSQL

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Java version | 21 | Match order-service and product-service |
| Service dependencies | None | Black-box testing via REST APIs only |
| Database access | None | Test via REST APIs, not direct DB queries |
| Kafka access | None for skeleton | Can add later for message verification |
| Maven plugin | Skip spring-boot-maven-plugin | No executable JAR needed |
| Test phase | `test` (Surefire) | Simple for skeleton, can switch to Failsafe later |

## Technical Approach

### Directory Structure

```
examples/
├── order-service/
├── product-service/
├── reference-example-service/
├── docker-compose.yml
└── system-tests/                      # NEW
    ├── pom.xml
    ├── mvnw, mvnw.cmd                 # Maven wrapper
    ├── .mvn/wrapper/maven-wrapper.properties
    └── src/
        └── test/
            ├── java/
            │   └── org/testpods/examples/systemtests/
            │       └── SystemTestsApplicationTests.java
            └── resources/
                └── application-test.yaml
```

### Files to Create

#### 1. system-tests/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.1</version>
        <relativePath/>
    </parent>
    <groupId>org.testpods.examples</groupId>
    <artifactId>system-tests</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>system-tests</name>
    <description>System integration tests for order and product services</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- Core Test Support -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- SKIP spring-boot-maven-plugin - no main class -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <skip>true</skip>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

#### 2. system-tests/src/test/java/org/testpods/examples/systemtests/SystemTestsApplicationTests.java

```java
package org.testpods.examples.systemtests;

import org.junit.jupiter.api.Test;

/**
 * Skeleton system test class.
 *
 * Future tests will orchestrate order-service and product-service together.
 * For now, just a simple no-op test to verify the module compiles and runs.
 */
class SystemTestsApplicationTests {

    @Test
    void contextLoads() {
        // No-op test - placeholder for future system tests
    }
}
```

#### 3. system-tests/src/test/resources/application-test.yaml

```yaml
# Connection properties for external services
# These can be overridden via environment variables in CI

order-service:
  base-url: http://localhost:8081

product-service:
  base-url: http://localhost:8082

spring:
  kafka:
    bootstrap-servers: localhost:9092
```

#### 4. Copy Maven Wrapper

Copy from `reference-example-service`:
- `mvnw`
- `mvnw.cmd`
- `.mvn/wrapper/maven-wrapper.properties`

## Acceptance Criteria

- [ ] `system-tests/` directory created with correct structure
- [ ] `pom.xml` configured with Java 21, test dependencies, skip spring-boot-maven-plugin
- [ ] `SystemTestsApplicationTests.java` exists with no-op test
- [ ] `application-test.yaml` exists with service URL placeholders
- [ ] Maven wrapper files copied from reference-example-service
- [ ] `mvn clean test` succeeds in system-tests directory
- [ ] No executable JAR created in target/

## Implementation Tasks

### Phase 1: Create Module Structure

1. Create `system-tests/` directory
2. Copy Maven wrapper files from reference-example-service:
   - `mvnw`
   - `mvnw.cmd`
   - `.mvn/wrapper/maven-wrapper.properties`
3. Create `pom.xml` with minimal test dependencies

### Phase 2: Create Test Code

4. Create directory `src/test/java/org/testpods/examples/systemtests/`
5. Create `SystemTestsApplicationTests.java` with no-op test
6. Create `src/test/resources/application-test.yaml`

### Phase 3: Verify Build

7. Run `./mvnw clean test` from system-tests directory
8. Verify test passes
9. Verify no JAR in target/ (only test artifacts)

## Dependencies & Risks

### Dependencies
- Spring Boot 4.0.1 (same as other services)
- JUnit 5 (via spring-boot-starter-test)

### Risks
- **Low**: Maven wrapper version mismatch with reference template
- **Low**: Spring Boot 4.0.1 test starter may have different behavior than 3.x

## Future Considerations

Once skeleton is established, future work includes:
- Add REST client dependencies (spring-boot-starter-webmvc-test)
- Add Kafka test support (spring-kafka-test)
- Create actual integration tests for order flow
- Consider Testcontainers for self-contained test execution
- Add to parent POM as module for reactor build

## References

### Internal References
- Reference template: `examples/reference-example-service/pom.xml`
- Order service: `examples/order-service/`
- Product service: `examples/product-service/`
- Docker compose: `examples/docker-compose.yml`

### External References
- [Spring Boot 4.0 Testing Documentation](https://docs.spring.io/spring-boot/4.0/reference/testing/spring-boot-applications)
- [Maven Failsafe Plugin](https://maven.apache.org/surefire/maven-failsafe-plugin/)
