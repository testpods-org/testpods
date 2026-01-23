# Plan: Create ContainerSpec Builder for Fluent Container Definition

**Priority:** Medium
**Effort:** Medium
**Category:** Developer Experience / API Design
**Phase:** 3 - Developer Experience

---

## Overview

Create a `ContainerSpec` builder class that provides a simpler, more fluent API for defining containers compared to the verbose Fabric8 `ContainerBuilder`, while maintaining an escape hatch for advanced customization.

## Problem Statement

Each pod type builds containers using raw Fabric8 `ContainerBuilder` with verbose, repetitive code:

```java
// Current approach - verbose and inconsistent
@Override
protected Container buildMainContainer() {
    return new ContainerBuilder()
        .withName("postgres")
        .withImage(image)
        .addNewPort()
            .withContainerPort(5432)
        .endPort()
        .addNewEnv()
            .withName("POSTGRES_PASSWORD")
            .withValue(password)
        .endEnv()
        .addNewEnv()
            .withName("POSTGRES_DB")
            .withValue(database)
        .endEnv()
        .withNewReadinessProbe()
            .withNewTcpSocket()
                .withNewPort(5432)
            .endTcpSocket()
            .withInitialDelaySeconds(5)
            .withPeriodSeconds(2)
        .endReadinessProbe()
        .build();
}
```

### Issues
- Verbose (many levels of nesting)
- Inconsistent across pod types
- Requires knowledge of Fabric8 builder patterns
- No validation until runtime
- The mid-level API has `InitContainerBuilder` and `SidecarBuilder` but no equivalent for main containers

## Proposed Solution

Create a `ContainerSpec` builder that:
- Provides flat, fluent methods for common operations
- Includes a `ProbeSpec` helper for readiness/liveness probes
- Offers an escape hatch via `customize()` for advanced Fabric8 customization
- Matches the style of existing `InitContainerBuilder` and `SidecarBuilder`

### After (Fluent)

```java
@Override
protected Container buildMainContainer() {
    return new ContainerSpec()
        .withName("postgres")
        .withImage(image)
        .withPort(5432)
        .withEnv("POSTGRES_PASSWORD", password)
        .withEnv("POSTGRES_DB", database)
        .withReadinessProbe(probe -> probe
            .tcpSocket(5432)
            .initialDelay(5)
            .period(2))
        .build();
}
```

## Technical Considerations

### Design Constraints (MUST Preserve)

1. **Fluent interface** - Must be chainable and readable
2. **Full Fabric8 access** - Escape hatch for advanced customization
3. **Consistency** - Match existing `InitContainerBuilder` and `SidecarBuilder` patterns

### API Design Decisions

- Use simple method names (`withPort()` not `addNewPort()`)
- Use `Consumer<ProbeSpec>` for probe configuration (matches SidecarBuilder pattern)
- Provide overloads for common patterns (e.g., `withPort(int)` and `withPort(int, String)`)

## Acceptance Criteria

### Functional Requirements
- [ ] `ContainerSpec` exists with fluent API for common container configuration
- [ ] `ProbeSpec` exists for fluent probe configuration
- [ ] API matches style of existing `InitContainerBuilder` and `SidecarBuilder`
- [ ] Escape hatch (`customize()`) allows full Fabric8 access
- [ ] At least one concrete pod updated to use `ContainerSpec`

### Non-Functional Requirements
- [ ] Code is less verbose and more readable
- [ ] Method chaining works correctly

### Quality Gates
- [ ] All existing tests pass
- [ ] Test coverage for ContainerSpec API

## Files to Create/Modify

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/builders/ContainerSpec.java` | Create new |
| `core/src/main/java/org/testpods/core/builders/ProbeSpec.java` | Create new |
| `core/src/main/java/org/testpods/core/pods/GenericTestPod.java` | Update to use ContainerSpec |

## MVP

### ContainerSpec.java

```java
package org.testpods.core.builders;

import io.fabric8.kubernetes.api.model.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Fluent builder for container specifications.
 * <p>
 * Provides a simpler API than raw Fabric8 ContainerBuilder while maintaining
 * full flexibility through the {@link #customize(UnaryOperator)} escape hatch.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * Container container = new ContainerSpec()
 *     .withName("postgres")
 *     .withImage("postgres:16-alpine")
 *     .withPort(5432)
 *     .withEnv("POSTGRES_PASSWORD", "secret")
 *     .withEnv("POSTGRES_DB", "myapp")
 *     .withReadinessProbe(probe -> probe
 *         .tcpSocket(5432)
 *         .initialDelay(5)
 *         .period(2))
 *     .build();
 * }</pre>
 */
public class ContainerSpec {

    private String name;
    private String image;
    private final List<ContainerPort> ports = new ArrayList<>();
    private final Map<String, String> env = new LinkedHashMap<>();
    private final List<EnvVar> envVars = new ArrayList<>();
    private final List<VolumeMount> volumeMounts = new ArrayList<>();
    private String[] command;
    private String[] args;
    private Probe readinessProbe;
    private Probe livenessProbe;
    private Probe startupProbe;
    private ResourceRequirements resources;
    private final List<UnaryOperator<ContainerBuilder>> customizers = new ArrayList<>();

    // === Basic Configuration ===

    public ContainerSpec withName(String name) {
        this.name = name;
        return this;
    }

    public ContainerSpec withImage(String image) {
        this.image = image;
        return this;
    }

    public ContainerSpec withPort(int port) {
        this.ports.add(new ContainerPortBuilder()
            .withContainerPort(port)
            .build());
        return this;
    }

    public ContainerSpec withPort(int port, String name) {
        this.ports.add(new ContainerPortBuilder()
            .withContainerPort(port)
            .withName(name)
            .build());
        return this;
    }

    // === Environment Variables ===

    public ContainerSpec withEnv(String name, String value) {
        this.env.put(name, value);
        return this;
    }

    public ContainerSpec withEnvFrom(String configMapName, String key) {
        this.envVars.add(new EnvVarBuilder()
            .withName(key)
            .withNewValueFrom()
                .withNewConfigMapKeyRef()
                    .withName(configMapName)
                    .withKey(key)
                .endConfigMapKeyRef()
            .endValueFrom()
            .build());
        return this;
    }

    public ContainerSpec withSecretEnv(String envName, String secretName, String key) {
        this.envVars.add(new EnvVarBuilder()
            .withName(envName)
            .withNewValueFrom()
                .withNewSecretKeyRef()
                    .withName(secretName)
                    .withKey(key)
                .endSecretKeyRef()
            .endValueFrom()
            .build());
        return this;
    }

    // === Commands ===

    public ContainerSpec withCommand(String... command) {
        this.command = command;
        return this;
    }

    public ContainerSpec withArgs(String... args) {
        this.args = args;
        return this;
    }

    // === Volume Mounts ===

    public ContainerSpec withVolumeMount(String name, String mountPath) {
        this.volumeMounts.add(new VolumeMountBuilder()
            .withName(name)
            .withMountPath(mountPath)
            .build());
        return this;
    }

    public ContainerSpec withVolumeMount(String name, String mountPath, boolean readOnly) {
        this.volumeMounts.add(new VolumeMountBuilder()
            .withName(name)
            .withMountPath(mountPath)
            .withReadOnly(readOnly)
            .build());
        return this;
    }

    // === Probes ===

    public ContainerSpec withReadinessProbe(Consumer<ProbeSpec> configurer) {
        ProbeSpec spec = new ProbeSpec();
        configurer.accept(spec);
        this.readinessProbe = spec.build();
        return this;
    }

    public ContainerSpec withLivenessProbe(Consumer<ProbeSpec> configurer) {
        ProbeSpec spec = new ProbeSpec();
        configurer.accept(spec);
        this.livenessProbe = spec.build();
        return this;
    }

    public ContainerSpec withStartupProbe(Consumer<ProbeSpec> configurer) {
        ProbeSpec spec = new ProbeSpec();
        configurer.accept(spec);
        this.startupProbe = spec.build();
        return this;
    }

    // === Resources ===

    public ContainerSpec withResources(String cpuRequest, String memoryRequest) {
        this.resources = new ResourceRequirementsBuilder()
            .addToRequests("cpu", new Quantity(cpuRequest))
            .addToRequests("memory", new Quantity(memoryRequest))
            .build();
        return this;
    }

    public ContainerSpec withResourceLimits(String cpuLimit, String memoryLimit) {
        ResourceRequirementsBuilder builder = this.resources != null
            ? new ResourceRequirementsBuilder(this.resources)
            : new ResourceRequirementsBuilder();

        this.resources = builder
            .addToLimits("cpu", new Quantity(cpuLimit))
            .addToLimits("memory", new Quantity(memoryLimit))
            .build();
        return this;
    }

    // === Escape Hatch ===

    /**
     * Apply raw Fabric8 customization for advanced use cases.
     * <p>
     * Use this when you need functionality not exposed by ContainerSpec's fluent API.
     *
     * @param customizer function to modify the underlying ContainerBuilder
     * @return this for chaining
     */
    public ContainerSpec customize(UnaryOperator<ContainerBuilder> customizer) {
        this.customizers.add(customizer);
        return this;
    }

    // === Build ===

    public Container build() {
        Objects.requireNonNull(name, "Container name is required");
        Objects.requireNonNull(image, "Container image is required");

        ContainerBuilder builder = new ContainerBuilder()
            .withName(name)
            .withImage(image)
            .withPorts(ports)
            .withVolumeMounts(volumeMounts);

        // Add simple env vars
        for (Map.Entry<String, String> entry : env.entrySet()) {
            builder.addNewEnv()
                .withName(entry.getKey())
                .withValue(entry.getValue())
                .endEnv();
        }

        // Add complex env vars
        builder.addAllToEnv(envVars);

        if (command != null) {
            builder.withCommand(command);
        }
        if (args != null) {
            builder.withArgs(args);
        }
        if (readinessProbe != null) {
            builder.withReadinessProbe(readinessProbe);
        }
        if (livenessProbe != null) {
            builder.withLivenessProbe(livenessProbe);
        }
        if (startupProbe != null) {
            builder.withStartupProbe(startupProbe);
        }
        if (resources != null) {
            builder.withResources(resources);
        }

        // Apply customizers
        for (UnaryOperator<ContainerBuilder> customizer : customizers) {
            builder = customizer.apply(builder);
        }

        return builder.build();
    }

    /**
     * Get the container name.
     */
    public String getName() {
        return name;
    }
}
```

### ProbeSpec.java

```java
package org.testpods.core.builders;

import io.fabric8.kubernetes.api.model.*;

/**
 * Fluent builder for Kubernetes probes.
 * <p>
 * Used with {@link ContainerSpec#withReadinessProbe(Consumer)},
 * {@link ContainerSpec#withLivenessProbe(Consumer)}, and
 * {@link ContainerSpec#withStartupProbe(Consumer)}.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * .withReadinessProbe(probe -> probe
 *     .tcpSocket(5432)
 *     .initialDelay(5)
 *     .period(2))
 * }</pre>
 */
public class ProbeSpec {

    private int initialDelaySeconds = 0;
    private int periodSeconds = 10;
    private int timeoutSeconds = 1;
    private int failureThreshold = 3;
    private int successThreshold = 1;

    // Probe type (only one should be set)
    private Integer tcpPort;
    private Integer httpPort;
    private String httpPath;
    private String httpScheme;
    private String[] execCommand;

    // === Probe Types ===

    /**
     * Configure TCP socket probe.
     *
     * @param port the port to check
     */
    public ProbeSpec tcpSocket(int port) {
        this.tcpPort = port;
        return this;
    }

    /**
     * Configure HTTP GET probe.
     *
     * @param port the port to query
     * @param path the HTTP path (e.g., "/health")
     */
    public ProbeSpec httpGet(int port, String path) {
        this.httpPort = port;
        this.httpPath = path;
        return this;
    }

    /**
     * Configure HTTPS GET probe.
     *
     * @param port the port to query
     * @param path the HTTP path (e.g., "/health")
     */
    public ProbeSpec httpsGet(int port, String path) {
        this.httpPort = port;
        this.httpPath = path;
        this.httpScheme = "HTTPS";
        return this;
    }

    /**
     * Configure exec probe.
     *
     * @param command the command to execute
     */
    public ProbeSpec exec(String... command) {
        this.execCommand = command;
        return this;
    }

    // === Timing Configuration ===

    /**
     * Set initial delay before first probe.
     *
     * @param seconds delay in seconds
     */
    public ProbeSpec initialDelay(int seconds) {
        this.initialDelaySeconds = seconds;
        return this;
    }

    /**
     * Set probe period.
     *
     * @param seconds period in seconds
     */
    public ProbeSpec period(int seconds) {
        this.periodSeconds = seconds;
        return this;
    }

    /**
     * Set probe timeout.
     *
     * @param seconds timeout in seconds
     */
    public ProbeSpec timeout(int seconds) {
        this.timeoutSeconds = seconds;
        return this;
    }

    /**
     * Set failure threshold.
     *
     * @param threshold number of failures before considered failed
     */
    public ProbeSpec failureThreshold(int threshold) {
        this.failureThreshold = threshold;
        return this;
    }

    /**
     * Set success threshold.
     *
     * @param threshold number of successes before considered healthy
     */
    public ProbeSpec successThreshold(int threshold) {
        this.successThreshold = threshold;
        return this;
    }

    // === Build ===

    public Probe build() {
        ProbeBuilder builder = new ProbeBuilder()
            .withInitialDelaySeconds(initialDelaySeconds)
            .withPeriodSeconds(periodSeconds)
            .withTimeoutSeconds(timeoutSeconds)
            .withFailureThreshold(failureThreshold)
            .withSuccessThreshold(successThreshold);

        if (tcpPort != null) {
            builder.withNewTcpSocket()
                .withNewPort(tcpPort)
                .endTcpSocket();
        } else if (httpPort != null) {
            builder.withNewHttpGet()
                .withPort(new IntOrString(httpPort))
                .withPath(httpPath != null ? httpPath : "/")
                .withScheme(httpScheme)
                .endHttpGet();
        } else if (execCommand != null) {
            builder.withNewExec()
                .withCommand(execCommand)
                .endExec();
        }

        return builder.build();
    }
}
```

## Test Plan

### ContainerSpecTest.java

```java
@Test
void shouldBuildBasicContainer() {
    Container container = new ContainerSpec()
        .withName("test")
        .withImage("nginx:latest")
        .withPort(80)
        .build();

    assertThat(container.getName()).isEqualTo("test");
    assertThat(container.getImage()).isEqualTo("nginx:latest");
    assertThat(container.getPorts()).hasSize(1);
    assertThat(container.getPorts().get(0).getContainerPort()).isEqualTo(80);
}

@Test
void shouldAddEnvironmentVariables() {
    Container container = new ContainerSpec()
        .withName("app")
        .withImage("app:1.0")
        .withEnv("DB_HOST", "localhost")
        .withEnv("DB_PORT", "5432")
        .build();

    assertThat(container.getEnv())
        .extracting(EnvVar::getName, EnvVar::getValue)
        .containsExactly(
            tuple("DB_HOST", "localhost"),
            tuple("DB_PORT", "5432")
        );
}

@Test
void shouldConfigureReadinessProbe() {
    Container container = new ContainerSpec()
        .withName("db")
        .withImage("postgres:16")
        .withReadinessProbe(probe -> probe
            .tcpSocket(5432)
            .initialDelay(10)
            .period(5))
        .build();

    Probe probe = container.getReadinessProbe();
    assertThat(probe).isNotNull();
    assertThat(probe.getTcpSocket().getPort().getIntVal()).isEqualTo(5432);
    assertThat(probe.getInitialDelaySeconds()).isEqualTo(10);
    assertThat(probe.getPeriodSeconds()).isEqualTo(5);
}

@Test
void shouldAllowCustomization() {
    Container container = new ContainerSpec()
        .withName("custom")
        .withImage("custom:1.0")
        .customize(builder -> builder
            .withImagePullPolicy("Always")
            .withNewSecurityContext()
                .withRunAsNonRoot(true)
            .endSecurityContext())
        .build();

    assertThat(container.getImagePullPolicy()).isEqualTo("Always");
    assertThat(container.getSecurityContext().getRunAsNonRoot()).isTrue();
}

@Test
void shouldRequireName() {
    ContainerSpec spec = new ContainerSpec()
        .withImage("test:1.0");

    assertThatThrownBy(spec::build)
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("name");
}

@Test
void shouldRequireImage() {
    ContainerSpec spec = new ContainerSpec()
        .withName("test");

    assertThatThrownBy(spec::build)
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("image");
}
```

## Verbosity Comparison

### Before (Fabric8 ContainerBuilder) - 18 lines

```java
return new ContainerBuilder()
    .withName("postgres")
    .withImage(image)
    .addNewPort()
        .withContainerPort(5432)
    .endPort()
    .addNewEnv()
        .withName("POSTGRES_PASSWORD")
        .withValue(password)
    .endEnv()
    .addNewEnv()
        .withName("POSTGRES_DB")
        .withValue(database)
    .endEnv()
    .withNewReadinessProbe()
        .withNewTcpSocket()
            .withNewPort(5432)
        .endTcpSocket()
        .withInitialDelaySeconds(5)
        .withPeriodSeconds(2)
    .endReadinessProbe()
    .build();
```

### After (ContainerSpec) - 10 lines

```java
return new ContainerSpec()
    .withName("postgres")
    .withImage(image)
    .withPort(5432)
    .withEnv("POSTGRES_PASSWORD", password)
    .withEnv("POSTGRES_DB", database)
    .withReadinessProbe(probe -> probe
        .tcpSocket(5432)
        .initialDelay(5)
        .period(2))
    .build();
```

**Reduction: 44% fewer lines, cleaner nesting**

## References

- Spec: `specs/refactorings/10-create-container-spec-builder.md`
- Existing pattern: `core/src/main/java/org/testpods/core/builders/InitContainerBuilder.java`
- Existing pattern: `core/src/main/java/org/testpods/core/builders/SidecarBuilder.java`

---

## Validation Output

After implementation, write results to `specs/refactorings/10-create-container-spec-builder_result.md`
