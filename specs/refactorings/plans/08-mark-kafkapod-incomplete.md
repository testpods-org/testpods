# Plan: Mark KafkaPod as Incomplete

**Priority:** Low
**Effort:** Small
**Category:** Documentation / Developer Experience
**Phase:** 4 - Cleanup (Can be done in parallel)

---

## Overview

Mark `KafkaPod.java` as incomplete/unimplemented to prevent developers from wasting time debugging a non-functional stub class.

## Problem Statement

`KafkaPod.java` is a stub class where every method is unimplemented:
- All methods return `null`, empty strings, `false`, or `0`
- Lifecycle methods (`start()`, `stop()`) are no-ops
- No actual Kafka functionality exists

### Consequences
Developers who try to use KafkaPod will:
1. Get null pointer exceptions
2. Experience silent failures
3. Waste time debugging a non-functional class

### Current State

```java
// KafkaPod.java - ALL METHODS ARE STUBS
public class KafkaPod extends BaseTestPod<KafkaPod> {

    @Override
    public void start() {
        // Empty - does nothing!
    }

    @Override
    public void stop() {
        // Empty - does nothing!
    }

    @Override
    public String getExternalHost() {
        return "";  // Returns empty string!
    }

    @Override
    public int getExternalPort() {
        return 0;  // Returns 0!
    }

    // ... all methods are stubs
}
```

## Proposed Solution

Use both `@Deprecated` annotation AND throw `UnsupportedOperationException` in lifecycle methods:

```java
/**
 * Kafka test pod for running Apache Kafka in tests.
 *
 * @deprecated This class is not yet implemented. Attempting to use it will result
 *             in runtime errors. See GitHub issue #XXX for implementation status.
 *             Consider using Testcontainers Kafka module as an alternative.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public class KafkaPod extends BaseTestPod<KafkaPod> {

    private static final String NOT_IMPLEMENTED =
        "KafkaPod is not yet implemented. " +
        "Consider using Testcontainers Kafka module (org.testcontainers:kafka).";

    @Override
    public void start() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    // ... other methods
}
```

### Why Both?

1. **`@Deprecated` annotation:** IDEs show warnings at usage sites, JavaDoc explains the situation
2. **Throw exceptions:** Provides immediate feedback if used accidentally
3. **Points to alternative:** Helps users find a working solution (Testcontainers)

## Technical Considerations

- **forRemoval = false:** Keep the class for future implementation
- **Alternative suggestion:** Mention Testcontainers Kafka module
- **Clear message:** Explain what's happening and why

## Acceptance Criteria

### Functional Requirements
- [ ] `KafkaPod` class has `@Deprecated` annotation
- [ ] JavaDoc clearly states the class is not implemented
- [ ] `start()` throws `UnsupportedOperationException`
- [ ] `stop()` throws `UnsupportedOperationException`

### Non-Functional Requirements
- [ ] IDE shows deprecation warning when class is used
- [ ] Alternative (Testcontainers) is mentioned in docs

### Quality Gates
- [ ] Test verifies exceptions are thrown

## Files to Modify

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/pods/external/kafka/KafkaPod.java` | Add deprecation, throw exceptions |

## MVP

### KafkaPod.java (marked as incomplete)

```java
package org.testpods.core.pods.external.kafka;

import org.testpods.core.cluster.HostAndPort;
import org.testpods.core.cluster.TestNamespace;
import org.testpods.core.pods.BaseTestPod;
import org.testpods.core.wait.WaitStrategy;

/**
 * Kafka test pod for running Apache Kafka in tests.
 * <p>
 * <strong>WARNING: This class is not yet implemented.</strong>
 * All lifecycle methods will throw {@link UnsupportedOperationException}.
 * <p>
 * <strong>Alternatives:</strong>
 * <ul>
 *   <li>Use <a href="https://www.testcontainers.org/modules/kafka/">Testcontainers Kafka</a>:
 *       {@code org.testcontainers:kafka}</li>
 *   <li>Use an external Kafka cluster for integration tests</li>
 * </ul>
 *
 * @deprecated This class is not yet implemented. Attempting to use it will result
 *             in runtime errors. Consider using Testcontainers Kafka module as an alternative.
 *             See GitHub issue for implementation status.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public class KafkaPod extends BaseTestPod<KafkaPod> {

    private static final String NOT_IMPLEMENTED =
        "KafkaPod is not yet implemented. " +
        "Consider using Testcontainers Kafka module (org.testcontainers:kafka). " +
        "See https://www.testcontainers.org/modules/kafka/ for documentation.";

    private String bootstrapServers;
    private int kafkaPort = 9092;
    private int zookeeperPort = 2181;

    public KafkaPod() {
        super();
    }

    // =========================================================================
    // Lifecycle methods - throw UnsupportedOperationException
    // =========================================================================

    @Override
    public void start() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    // =========================================================================
    // State methods - throw or return sensible defaults with warning
    // =========================================================================

    @Override
    public boolean isRunning() {
        return false;  // Never running since start() throws
    }

    @Override
    public boolean isReady() {
        return false;  // Never ready since start() throws
    }

    @Override
    public String getExternalHost() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public int getExternalPort() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    protected WaitStrategy getDefaultWaitStrategy() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    // =========================================================================
    // Kafka-specific methods - stubs with deprecation
    // =========================================================================

    /**
     * Get the Kafka bootstrap servers address.
     *
     * @return the bootstrap servers address
     * @throws UnsupportedOperationException always, as this class is not implemented
     * @deprecated This class is not implemented
     */
    @Deprecated
    public String getBootstrapServers() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    /**
     * Get the Zookeeper connection string.
     *
     * @return the Zookeeper connection string
     * @throws UnsupportedOperationException always, as this class is not implemented
     * @deprecated This class is not implemented
     */
    @Deprecated
    public String getZookeeperConnect() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    // =========================================================================
    // Fluent configuration - still works for future implementation
    // =========================================================================

    /**
     * Configure the Kafka port.
     *
     * @param port the Kafka broker port
     * @return this for chaining
     * @deprecated This class is not implemented. Configuration will be used when implemented.
     */
    @Deprecated
    public KafkaPod withKafkaPort(int port) {
        this.kafkaPort = port;
        return self();
    }

    /**
     * Configure the Zookeeper port.
     *
     * @param port the Zookeeper port
     * @return this for chaining
     * @deprecated This class is not implemented. Configuration will be used when implemented.
     */
    @Deprecated
    public KafkaPod withZookeeperPort(int port) {
        this.zookeeperPort = port;
        return self();
    }

    @Override
    protected KafkaPod self() {
        return this;
    }
}
```

## Test Plan

### KafkaPodTest.java

```java
@Test
void shouldThrowOnStart() {
    KafkaPod kafka = new KafkaPod();

    assertThatThrownBy(kafka::start)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("not yet implemented")
        .hasMessageContaining("Testcontainers");
}

@Test
void shouldThrowOnStop() {
    KafkaPod kafka = new KafkaPod();

    assertThatThrownBy(kafka::stop)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("not yet implemented");
}

@Test
void shouldThrowOnGetExternalHost() {
    KafkaPod kafka = new KafkaPod();

    assertThatThrownBy(kafka::getExternalHost)
        .isInstanceOf(UnsupportedOperationException.class);
}

@Test
void shouldThrowOnGetBootstrapServers() {
    KafkaPod kafka = new KafkaPod();

    assertThatThrownBy(kafka::getBootstrapServers)
        .isInstanceOf(UnsupportedOperationException.class);
}

@Test
void isRunningShouldReturnFalse() {
    KafkaPod kafka = new KafkaPod();

    assertThat(kafka.isRunning()).isFalse();
}

@Test
void isReadyShouldReturnFalse() {
    KafkaPod kafka = new KafkaPod();

    assertThat(kafka.isReady()).isFalse();
}

@Test
@SuppressWarnings("deprecation")
void configurationMethodsShouldStillWork() {
    // Configuration should work for when the class is eventually implemented
    KafkaPod kafka = new KafkaPod()
        .withKafkaPort(9093)
        .withZookeeperPort(2182);

    // Just verify no exception on configuration
    assertThat(kafka).isNotNull();
}
```

## IDE Warning Preview

After this change, when a developer uses KafkaPod, their IDE will show:

```
⚠️ 'KafkaPod' is deprecated
   This class is not yet implemented. Attempting to use it will result
   in runtime errors. Consider using Testcontainers Kafka module as an alternative.
```

## References

- Spec: `specs/refactorings/08-mark-kafkapod-incomplete.md`
- Current implementation: `core/src/main/java/org/testpods/core/pods/external/kafka/KafkaPod.java`
- Testcontainers Kafka: https://www.testcontainers.org/modules/kafka/

---

## Validation Output

After implementation, write results to `specs/refactorings/08-mark-kafkapod-incomplete_result.md`
