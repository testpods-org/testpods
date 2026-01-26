package org.testpods.junit;

import org.junit.jupiter.api.extension.*;
import org.testpods.core.pods.TestPodDefaults;

/**
 * JUnit 5 extension for TestPods lifecycle management.
 *
 * <p>This extension handles setup and cleanup of TestPod resources during test execution. It
 * ensures proper cleanup of thread-local state to prevent memory leaks in thread pool executors
 * when running tests in parallel.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * @ExtendWith(TestPodsExtension.class)
 * class MyIntegrationTest {
 *     // ...
 * }
 * }</pre>
 */
public class TestPodsExtension
    implements BeforeEachCallback,
        BeforeAllCallback,
        AfterEachCallback,
        AfterAllCallback,
        ExecutionCondition {

  @Override
  public void afterAll(ExtensionContext extensionContext) throws Exception {
    // Clear thread-local state to prevent memory leaks in thread pool executors
    TestPodDefaults.clear();
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) throws Exception {}

  @Override
  public void beforeAll(ExtensionContext extensionContext) throws Exception {}

  @Override
  public void beforeEach(ExtensionContext extensionContext) throws Exception {}

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext extensionContext) {
    return null;
  }
}
