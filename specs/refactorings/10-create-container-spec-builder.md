# Refactoring 10: Create ContainerSpec Builder for Fluent Container Definition

**Priority:** Medium
**Effort:** Medium
**Category:** Developer Experience / API Design

---

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

This is:
- Verbose (many levels of nesting)
- Inconsistent across pod types
- Requires knowledge of Fabric8 builder patterns
- No validation until runtime

The mid-level API has `InitContainerBuilder` and `SidecarBuilder` but no equivalent for main containers.

---

## Design Constraints

### MUST Preserve

1. **Fluent interface** - Must be chainable and readable
2. **Full Fabric8 access** - Escape hatch for advanced customization
3. **Consistency** - Match existing `InitContainerBuilder` and `SidecarBuilder` patterns

---

## Proposed Solution

### ContainerSpec Builder

```java
package org.testpods.core.pods.builders;

import io.fabric8.kubernetes.api.model.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public ContainerSpec withEnvFrom(String configMapName) {
        this.envVars.add(new EnvVarBuilder()
                .withName(configMapName)
                .withNewValueFrom()
                .withNewConfigMapKeyRef()
                .withName(configMapName)
                .endConfigMapKeyRef()
                .endValueFrom()
                .build());
        return this;
    }

    public ContainerSpec withSecretEnv(String name, String secretName, String key) {
        this.envVars.add(new EnvVarBuilder()
                .withName(name)
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
        if (this.resources == null) {
            this.resources = new ResourceRequirements();
        }
        // Add limits to existing resources
        return this;
    }

    // === Escape Hatch ===

    /**
     * Apply raw Fabric8 customization for advanced use cases.
     */
    public ContainerSpec customize(UnaryOperator<ContainerBuilder> customizer) {
        this.customizers.add(customizer);
        return this;
    }

    // === Build ===

    public Container build() {
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
}
```

### ProbeSpec Builder

```java
package org.testpods.core.pods.builders;

/**
 * Fluent builder for Kubernetes probes.
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
    private String[] execCommand;

    public ProbeSpec tcpSocket(int port) {
        this.tcpPort = port;
        return this;
    }

    public ProbeSpec httpGet(int port, String path) {
        this.httpPort = port;
        this.httpPath = path;
        return this;
    }

    public ProbeSpec exec(String... command) {
        this.execCommand = command;
        return this;
    }

    public ProbeSpec initialDelay(int seconds) {
        this.initialDelaySeconds = seconds;
        return this;
    }

    public ProbeSpec period(int seconds) {
        this.periodSeconds = seconds;
        return this;
    }

    public ProbeSpec timeout(int seconds) {
        this.timeoutSeconds = seconds;
        return this;
    }

    public ProbeSpec failureThreshold(int threshold) {
        this.failureThreshold = threshold;
        return this;
    }

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
                    .withPath(httpPath)
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

---

## Usage Examples

### Before (Verbose)
```java
@Override
protected Container buildMainContainer() {
    return new ContainerBuilder()
        .withName("postgres")
        .withImage(image)
        .addNewPort().withContainerPort(5432).endPort()
        .addNewEnv().withName("POSTGRES_PASSWORD").withValue(password).endEnv()
        .addNewEnv().withName("POSTGRES_DB").withValue(database).endEnv()
        .withNewReadinessProbe()
            .withNewTcpSocket().withNewPort(5432).endTcpSocket()
            .withInitialDelaySeconds(5)
            .withPeriodSeconds(2)
        .endReadinessProbe()
        .build();
}
```

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

---

## Files to Create/Modify

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/builders/ContainerSpec.java` | Create new |
| `core/src/main/java/org/testpods/core/builders/ProbeSpec.java` | Create new |
| `core/src/main/java/org/testpods/core/pods/GenericTestPod.java` | Update to use ContainerSpec |

---

## Success Criteria

1. [ ] `ContainerSpec` exists with fluent API for common container configuration
2. [ ] `ProbeSpec` exists for fluent probe configuration
3. [ ] API matches style of existing `InitContainerBuilder` and `SidecarBuilder`
4. [ ] Escape hatch (`customize()`) allows full Fabric8 access
5. [ ] At least one concrete pod updated to use `ContainerSpec`
6. [ ] Code is less verbose and more readable
7. [ ] All existing tests pass

---

## Validation Step

After implementation, the agent must:

1. **Compare verbosity** - Count lines before/after in `buildMainContainer()`
2. **Test fluent API** - All methods chainable
3. **Test escape hatch** - customize() applies Fabric8 modifications
4. **Run tests** - `./gradlew :core:test`
5. **Document findings** - Write to `specs/refactorings/10-create-container-spec-builder_result.md`

### Validation Output Format

```markdown
# Validation Result: Create ContainerSpec Builder

## Implementation Summary
- Files created: [list]
- Files modified: [list]

## Verbosity Comparison
| Pod | Before (lines) | After (lines) | Reduction |
|-----|----------------|---------------|-----------|
| GenericTestPod | X | Y | Z% |

## API Tests
| Feature | Working? |
|---------|----------|
| withName() | [Y/N] |
| withImage() | [Y/N] |
| withPort() | [Y/N] |
| withEnv() | [Y/N] |
| withReadinessProbe() | [Y/N] |
| customize() escape hatch | [Y/N] |

## Test Results
- Tests run: X
- Passed: X
- Failed: X

## Deviations from Plan
[List any deviations and reasoning]
```
