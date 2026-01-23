# Refactoring 08: Mark KafkaPod as Incomplete

**Priority:** Low
**Effort:** Small
**Category:** Documentation / Developer Experience

---

## Problem Statement

`KafkaPod.java` is a stub class where every method is unimplemented:
- All methods return `null`, empty strings, `false`, or `0`
- Lifecycle methods (`start()`, `stop()`) are no-ops
- No actual Kafka functionality exists

Developers who try to use KafkaPod will:
1. Get null pointer exceptions
2. Experience silent failures
3. Waste time debugging a non-functional class

---

## Current State

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

---

## Proposed Solution

### Option A: Deprecate with Clear Message

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
    // ...
}
```

### Option B: Throw UnsupportedOperationException

```java
public class KafkaPod extends BaseTestPod<KafkaPod> {

    private static final String NOT_IMPLEMENTED =
        "KafkaPod is not yet implemented. See GitHub issue #XXX for status.";

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

### Option C: Both (Recommended)

Use `@Deprecated` annotation AND throw exceptions in lifecycle methods.

---

## Recommendation

**Use Option C** because:
1. IDEs will show deprecation warnings at usage sites
2. JavaDoc explains the situation clearly
3. Runtime exceptions provide immediate feedback if used accidentally
4. Points users to alternative solutions

---

## Files to Modify

| File | Change |
|------|--------|
| `core/src/main/java/org/testpods/core/pods/external/kafka/KafkaPod.java` | Add deprecation, throw exceptions |

---

## Success Criteria

1. [ ] `KafkaPod` class has `@Deprecated` annotation
2. [ ] JavaDoc clearly states the class is not implemented
3. [ ] `start()` throws `UnsupportedOperationException`
4. [ ] `stop()` throws `UnsupportedOperationException`
5. [ ] IDE shows deprecation warning when class is used
6. [ ] Alternative (Testcontainers) is mentioned in docs

---

## Test Plan

```java
@Test
void shouldThrowOnStart() {
    KafkaPod kafka = new KafkaPod();

    assertThatThrownBy(kafka::start)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("not yet implemented");
}
```

---

## Validation Step

After implementation, the agent must:

1. **Verify deprecation** - `@Deprecated` annotation present
2. **Verify exceptions** - start() and stop() throw UnsupportedOperationException
3. **Verify JavaDoc** - Clear message about implementation status
4. **Document findings** - Write to `specs/refactorings/08-mark-kafkapod-incomplete_result.md`

### Validation Output Format

```markdown
# Validation Result: Mark KafkaPod Incomplete

## Implementation Summary
- Files modified: [list]

## Verification
| Check | Result |
|-------|--------|
| @Deprecated annotation present | [Y/N] |
| JavaDoc explains status | [Y/N] |
| start() throws UnsupportedOperationException | [Y/N] |
| stop() throws UnsupportedOperationException | [Y/N] |
| Alternative mentioned | [Y/N] |

## Deviations from Plan
[List any deviations and reasoning]
```
