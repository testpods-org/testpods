# Refactoring 04: Fix Init Script ConfigMap Mount Bug

**Priority:** Critical (Bug Fix)
**Effort:** Small
**Category:** Bug Fix

---

## Problem Statement

In the PostgreSQL pod implementation spec (`specs/02-postgresql-pod-implementation.md`), the `createInitScriptConfigMap()` method creates a ConfigMap containing init scripts, but `buildMainContainer()` never mounts this ConfigMap as a volume.

**Result:** PostgreSQL init scripts will never execute because the files don't appear in `/docker-entrypoint-initdb.d/`.

---

## Root Cause

The implementation creates the ConfigMap resource but fails to:
1. Add a Volume referencing the ConfigMap to the pod spec
2. Add a VolumeMount to the container pointing to `/docker-entrypoint-initdb.d/`

---

## Proposed Solution

### Fix in PostgreSQLPod.buildMainContainer()

Add volume mount to container:

```java
@Override
protected Container buildMainContainer() {
    var builder = new ContainerBuilder()
        .withName("postgres")
        .withImage(image)
        .addNewPort().withContainerPort(5432).endPort()
        .withEnv(buildEnvVars());

    // Add init script volume mount if init scripts exist
    if (!initScripts.isEmpty()) {
        builder.addToVolumeMounts(new VolumeMountBuilder()
            .withName("init-scripts")
            .withMountPath("/docker-entrypoint-initdb.d")
            .withReadOnly(true)
            .build());
    }

    return builder.build();
}
```

### Add Volume to Pod Spec

Override `applyPodCustomizations()` or use the pod customizer:

```java
@Override
protected PodSpecBuilder applyPodCustomizations(PodSpecBuilder baseSpec) {
    baseSpec = super.applyPodCustomizations(baseSpec);

    // Add init scripts volume if needed
    if (!initScripts.isEmpty()) {
        baseSpec.addToVolumes(new VolumeBuilder()
            .withName("init-scripts")
            .withNewConfigMap()
                .withName(name + "-init")
            .endConfigMap()
            .build());
    }

    return baseSpec;
}
```

---

## Files to Modify

| File | Change |
|------|--------|
| `specs/02-postgresql-pod-implementation.md` | Update spec with fix |
| (Future) `PostgreSQLPod.java` | Implement fix when pod is created |

---

## Success Criteria

1. [ ] ConfigMap volume is added to pod spec when init scripts exist
2. [ ] VolumeMount points to `/docker-entrypoint-initdb.d`
3. [ ] VolumeMount is read-only
4. [ ] Init scripts execute on PostgreSQL startup
5. [ ] Test case verifies init script creates expected database objects

---

## Test Plan

```java
@Test
void shouldExecuteInitScripts() {
    PostgreSQLPod postgres = new PostgreSQLPod()
        .withName("test-init")
        .withInitScript("01-create-schema.sql", "CREATE TABLE test_table (id INT);");

    postgres.start();

    try (Connection conn = postgres.getConnection()) {
        // Verify table was created by init script
        ResultSet rs = conn.getMetaData().getTables(null, null, "test_table", null);
        assertThat(rs.next()).isTrue();
    } finally {
        postgres.stop();
    }
}
```

---

## Validation Step

After implementation, the agent must:

1. **Verify volume** - Check pod spec includes ConfigMap volume
2. **Verify mount** - Check container has correct volume mount
3. **Integration test** - Run PostgreSQL with init script, verify objects created
4. **Document findings** - Write to `specs/refactorings/04-fix-init-script-configmap-mount_result.md`

### Validation Output Format

```markdown
# Validation Result: Fix Init Script ConfigMap Mount

## Implementation Summary
- Files modified: [list]

## Verification
| Check | Result |
|-------|--------|
| Volume added to pod spec | [Y/N] |
| VolumeMount in container | [Y/N] |
| Mount path correct | [Y/N] |
| Init scripts execute | [Y/N] |

## Test Results
- Integration test: [Pass/Fail]
- Init script created table: [Y/N]

## Deviations from Plan
[List any deviations and reasoning]
```
