# Refactoring 02: Extract ServiceManager Component

**Priority:** High
**Effort:** Medium
**Category:** Architecture / Composition Over Inheritance
**Depends On:** 01-extract-workload-manager (can be done in parallel)

---

## Problem Statement

Service creation code is duplicated across `DeploymentPod` and `StatefulSetPod`:

- `DeploymentPod.buildService()` creates ClusterIP service (lines 280-307)
- `StatefulSetPod.createHeadlessService()` creates Headless service (lines 298-318)
- `StatefulSetPod.createNodePortService()` creates NodePort service (lines 320-340)

The code differs subtly (ClusterIP vs Headless vs NodePort), but shares the same structure:
- Build ServiceBuilder with name, namespace, labels
- Add port configuration
- Apply customizers
- Create in cluster

Both classes also have duplicate `serviceCustomizers` lists.

---

## Design Constraints

### MUST Preserve

1. **Fluent interface** - Service configuration must remain intuitive:
   ```java
   pod.withServiceCustomizer(svc -> svc
       .editSpec()
           .withType("NodePort")
       .endSpec())
   ```

2. **Internal implementation** - ServiceManager is NOT exposed to pod users directly

3. **Backwards compatibility** - Existing pods should continue to work

---

## Proposed Solution

### ServiceManager Interface

```java
package org.testpods.core.service;

import io.fabric8.kubernetes.api.model.Service;

/**
 * Manages Kubernetes Service resources for test pods.
 * This is an internal implementation detail.
 */
public interface ServiceManager {

    /**
     * Create the service in the cluster.
     */
    Service create(ServiceConfig config);

    /**
     * Delete the service from the cluster.
     */
    void delete();

    /**
     * Get the created service, or null if not created.
     */
    Service getService();
}
```

### ServiceConfig Record

```java
package org.testpods.core.service;

public record ServiceConfig(
    String name,
    String namespace,
    int port,
    Map<String, String> labels,
    Map<String, String> selector,
    List<UnaryOperator<ServiceBuilder>> customizers,
    KubernetesClient client
) {}
```

### Service Manager Implementations

```java
// ClusterIP (default for DeploymentPod)
public class ClusterIPServiceManager implements ServiceManager {
    @Override
    public Service create(ServiceConfig config) {
        ServiceBuilder builder = new ServiceBuilder()
            .withNewMetadata()
                .withName(config.name())
                .withNamespace(config.namespace())
                .withLabels(config.labels())
            .endMetadata()
            .withNewSpec()
                .withSelector(config.selector())
                .withType("ClusterIP")
                .addNewPort()
                    .withName("primary")
                    .withPort(config.port())
                    .withTargetPort(new IntOrString(config.port()))
                .endPort()
            .endSpec();

        // Apply customizers
        for (var customizer : config.customizers()) {
            builder = customizer.apply(builder);
        }

        return config.client().services()
            .inNamespace(config.namespace())
            .resource(builder.build())
            .create();
    }
}

// Headless (for StatefulSet stable DNS)
public class HeadlessServiceManager implements ServiceManager {
    @Override
    public Service create(ServiceConfig config) {
        // Similar but with .withClusterIP("None")
    }
}

// NodePort (for external access)
public class NodePortServiceManager implements ServiceManager {
    @Override
    public Service create(ServiceConfig config) {
        // Similar but with .withType("NodePort")
    }
}
```

### Composite Service Manager

For pods that need multiple services (e.g., StatefulSet with headless + NodePort):

```java
public class CompositeServiceManager implements ServiceManager {
    private final List<ServiceManager> managers;

    public CompositeServiceManager(ServiceManager... managers) {
        this.managers = List.of(managers);
    }

    @Override
    public Service create(ServiceConfig config) {
        Service primary = null;
        for (ServiceManager manager : managers) {
            Service svc = manager.create(adjustConfig(config, manager));
            if (primary == null) primary = svc;
        }
        return primary;
    }

    @Override
    public void delete() {
        // Delete all in reverse order
        for (int i = managers.size() - 1; i >= 0; i--) {
            managers.get(i).delete();
        }
    }
}
```

---

## Implementation Steps

1. **Create interfaces**
   - `ServiceManager` interface
   - `ServiceConfig` record

2. **Create implementations**
   - `ClusterIPServiceManager`
   - `HeadlessServiceManager`
   - `NodePortServiceManager`
   - `CompositeServiceManager`

3. **Integrate with ComposableTestPod**
   - Add `serviceManager` field
   - Delegate service operations to manager

4. **Update concrete pods**
   - `GenericTestPod` uses `ClusterIPServiceManager`
   - `PostgreSQLPod` uses `CompositeServiceManager(HeadlessServiceManager, NodePortServiceManager)`

5. **Remove duplicate code**
   - Remove `buildService()` from `DeploymentPod`
   - Remove service methods from `StatefulSetPod`

---

## Files to Modify

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/service/ServiceManager.java` | Create new |
| `core/src/main/java/org/testpods/core/service/ServiceConfig.java` | Create new |
| `core/src/main/java/org/testpods/core/service/ClusterIPServiceManager.java` | Create new |
| `core/src/main/java/org/testpods/core/service/HeadlessServiceManager.java` | Create new |
| `core/src/main/java/org/testpods/core/service/NodePortServiceManager.java` | Create new |
| `core/src/main/java/org/testpods/core/service/CompositeServiceManager.java` | Create new |
| `core/src/main/java/org/testpods/core/pods/ComposableTestPod.java` | Add serviceManager |

---

## Success Criteria

1. [ ] `ServiceManager` interface exists with `create()`, `delete()`, `getService()` methods
2. [ ] Three service types are implemented: ClusterIP, Headless, NodePort
3. [ ] `CompositeServiceManager` allows combining multiple service types
4. [ ] Service customizer fluent API still works:
   ```java
   pod.withServiceCustomizer(svc -> svc.editSpec().withType("LoadBalancer").endSpec())
   ```
5. [ ] Duplicate service code is eliminated from `DeploymentPod` and `StatefulSetPod`
6. [ ] All existing tests pass

---

## Validation Step

After implementation, the agent must:

1. **Verify implementations** - Each ServiceManager type creates correct service type
2. **Test composite** - StatefulSetPod-based pods get both headless and NodePort services
3. **Check API** - Fluent customizer API unchanged
4. **Run tests** - `./gradlew :core:test`
5. **Document findings** - Write to `specs/refactorings/02-extract-service-manager_result.md`

### Validation Output Format

```markdown
# Validation Result: Extract ServiceManager

## Implementation Summary
- Files created: [list]
- Files modified: [list]

## Service Type Tests
| Service Type | Creates Correctly | Deletes Correctly |
|--------------|------------------|-------------------|
| ClusterIP    | [Y/N]            | [Y/N]            |
| Headless     | [Y/N]            | [Y/N]            |
| NodePort     | [Y/N]            | [Y/N]            |
| Composite    | [Y/N]            | [Y/N]            |

## Test Results
- Tests run: X
- Passed: X
- Failed: X

## Deviations from Plan
[List any deviations and reasoning]
```
