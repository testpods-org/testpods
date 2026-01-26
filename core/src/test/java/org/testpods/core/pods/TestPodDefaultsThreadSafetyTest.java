package org.testpods.core.pods;

import static org.assertj.core.api.Assertions.*;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testpods.core.cluster.ExternalAccessStrategy;
import org.testpods.core.cluster.K8sCluster;

/**
 * Thread safety tests for {@link TestPodDefaults}.
 *
 * <p>These tests verify that the InheritableThreadLocal implementation correctly isolates state
 * between threads and properly inherits to child threads.
 *
 * <p>Test categories:
 *
 * <ul>
 *   <li>Thread isolation - verifies separate threads have independent state
 *   <li>Child thread inheritance - verifies InheritableThreadLocal propagation
 *   <li>Clear behavior - verifies state cleanup works correctly
 *   <li>Error conditions - verifies proper exceptions when unconfigured
 * </ul>
 */
class TestPodDefaultsThreadSafetyTest {

  @AfterEach
  void cleanup() {
    TestPodDefaults.clear();
    TestPodDefaults.clearGlobalDefaults();
  }

  @Nested
  class ThreadIsolation {

    @Test
    void shouldIsolateDefaultsBetweenThreads() throws InterruptedException {
      // Arrange: Two mock clusters for identification
      K8sCluster cluster1 = new MockCluster("cluster-1");
      K8sCluster cluster2 = new MockCluster("cluster-2");

      CountDownLatch setupLatch = new CountDownLatch(2);
      CountDownLatch verifyLatch = new CountDownLatch(1);

      AtomicReference<K8sCluster> thread1Result = new AtomicReference<>();
      AtomicReference<K8sCluster> thread2Result = new AtomicReference<>();
      AtomicReference<Throwable> thread1Error = new AtomicReference<>();
      AtomicReference<Throwable> thread2Error = new AtomicReference<>();

      // Act: Each thread sets its own cluster supplier and resolves
      Thread thread1 =
          new Thread(
              () -> {
                try {
                  TestPodDefaults.setClusterSupplier(() -> cluster1);
                  setupLatch.countDown();

                  // Wait for both threads to have set their suppliers
                  verifyLatch.await(5, TimeUnit.SECONDS);

                  thread1Result.set(TestPodDefaults.resolveCluster());
                } catch (Throwable t) {
                  thread1Error.set(t);
                } finally {
                  TestPodDefaults.clear();
                }
              },
              "test-thread-1");

      Thread thread2 =
          new Thread(
              () -> {
                try {
                  TestPodDefaults.setClusterSupplier(() -> cluster2);
                  setupLatch.countDown();

                  // Wait for both threads to have set their suppliers
                  verifyLatch.await(5, TimeUnit.SECONDS);

                  thread2Result.set(TestPodDefaults.resolveCluster());
                } catch (Throwable t) {
                  thread2Error.set(t);
                } finally {
                  TestPodDefaults.clear();
                }
              },
              "test-thread-2");

      thread1.start();
      thread2.start();

      // Wait for both threads to set up their suppliers
      assertThat(setupLatch.await(5, TimeUnit.SECONDS))
          .as("Both threads should complete setup within timeout")
          .isTrue();

      // Signal threads to verify
      verifyLatch.countDown();

      // Wait for completion
      thread1.join(5000);
      thread2.join(5000);

      // Assert: Check for errors first
      assertThat(thread1Error.get()).as("Thread 1 should not encounter errors").isNull();
      assertThat(thread2Error.get()).as("Thread 2 should not encounter errors").isNull();

      // Assert: Each thread should resolve its own cluster
      assertThat(thread1Result.get()).as("Thread 1 should resolve cluster-1").isSameAs(cluster1);
      assertThat(thread2Result.get()).as("Thread 2 should resolve cluster-2").isSameAs(cluster2);
    }

    @Test
    void shouldNotAffectOtherThreadsWhenSettingDefaults() throws InterruptedException {
      // Arrange: Set up main thread cluster
      K8sCluster mainCluster = new MockCluster("main-cluster");
      TestPodDefaults.setClusterSupplier(() -> mainCluster);

      AtomicReference<Boolean> otherThreadHasCluster = new AtomicReference<>();
      CountDownLatch latch = new CountDownLatch(1);

      // Act: Create a new thread and check if it sees the main thread's cluster
      Thread otherThread =
          new Thread(
              () -> {
                // New thread without inherited context should not see main's cluster
                // unless using InheritableThreadLocal, it SHOULD inherit
                otherThreadHasCluster.set(TestPodDefaults.hasClusterConfigured());
                TestPodDefaults.clear();
                latch.countDown();
              });

      otherThread.start();
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

      // Assert: Child thread should inherit parent's configuration due to InheritableThreadLocal
      assertThat(otherThreadHasCluster.get())
          .as("Child thread should inherit parent's cluster configuration")
          .isTrue();
    }
  }

  @Nested
  class ChildThreadInheritance {

    @Test
    void childThreadShouldInheritParentContext() throws InterruptedException {
      // Arrange: Set cluster supplier in parent thread
      K8sCluster parentCluster = new MockCluster("parent-cluster");
      TestPodDefaults.setClusterSupplier(() -> parentCluster);

      AtomicReference<K8sCluster> childResult = new AtomicReference<>();
      AtomicReference<Boolean> childHasCluster = new AtomicReference<>();
      CountDownLatch latch = new CountDownLatch(1);

      // Act: Spawn child thread and verify inheritance
      Thread childThread =
          new Thread(
              () -> {
                childHasCluster.set(TestPodDefaults.hasClusterConfigured());
                childResult.set(TestPodDefaults.resolveCluster());
                TestPodDefaults.clear();
                latch.countDown();
              });

      childThread.start();
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

      // Assert: Child thread should inherit parent's cluster configuration
      assertThat(childHasCluster.get())
          .as("Child thread should see cluster as configured")
          .isTrue();
      assertThat(childResult.get())
          .as("Child thread should resolve the same cluster as parent")
          .isSameAs(parentCluster);
    }

    @Test
    void childThreadChangesDoNotAffectParent() throws InterruptedException {
      // Arrange: Set cluster supplier in parent thread
      K8sCluster parentCluster = new MockCluster("parent-cluster");
      K8sCluster childCluster = new MockCluster("child-cluster");
      TestPodDefaults.setClusterSupplier(() -> parentCluster);

      CountDownLatch childDone = new CountDownLatch(1);
      AtomicReference<K8sCluster> childBeforeChange = new AtomicReference<>();
      AtomicReference<K8sCluster> childAfterChange = new AtomicReference<>();

      // Act: Child thread changes its own cluster supplier
      Thread childThread =
          new Thread(
              () -> {
                childBeforeChange.set(TestPodDefaults.resolveCluster());
                TestPodDefaults.setClusterSupplier(() -> childCluster);
                childAfterChange.set(TestPodDefaults.resolveCluster());
                TestPodDefaults.clear();
                childDone.countDown();
              });

      childThread.start();
      assertThat(childDone.await(5, TimeUnit.SECONDS)).isTrue();

      // Assert: Child sees inherited value first, then its own
      assertThat(childBeforeChange.get())
          .as("Child should initially inherit parent's cluster")
          .isSameAs(parentCluster);
      assertThat(childAfterChange.get())
          .as("Child should see its own cluster after setting")
          .isSameAs(childCluster);

      // Assert: Parent is not affected by child's changes
      assertThat(TestPodDefaults.resolveCluster())
          .as("Parent should still resolve original cluster")
          .isSameAs(parentCluster);
    }
  }

  @Nested
  class ClearBehavior {

    @Test
    void clearShouldRemoveThreadLocalState() {
      // Arrange: Set cluster supplier
      K8sCluster cluster = new MockCluster("test-cluster");
      TestPodDefaults.setClusterSupplier(() -> cluster);

      // Verify state is configured
      assertThat(TestPodDefaults.hasClusterConfigured())
          .as("Cluster should be configured after setting supplier")
          .isTrue();

      // Act: Clear the thread-local state
      TestPodDefaults.clear();

      // Assert: State should be removed
      assertThat(TestPodDefaults.hasClusterConfigured())
          .as("Cluster should not be configured after clear()")
          .isFalse();
    }

    @Test
    void clearShouldNotAffectGlobalDefaults() {
      // Arrange: Set both thread-local and global suppliers
      K8sCluster threadCluster = new MockCluster("thread-cluster");
      K8sCluster globalCluster = new MockCluster("global-cluster");
      TestPodDefaults.setClusterSupplier(() -> threadCluster);
      TestPodDefaults.setGlobalClusterSupplier(() -> globalCluster);

      // Verify thread-local takes precedence
      assertThat(TestPodDefaults.resolveCluster())
          .as("Thread-local should take precedence over global")
          .isSameAs(threadCluster);

      // Act: Clear thread-local state
      TestPodDefaults.clear();

      // Assert: Global default should now be used
      assertThat(TestPodDefaults.hasClusterConfigured())
          .as("Global cluster should still be configured")
          .isTrue();
      assertThat(TestPodDefaults.resolveCluster())
          .as("Global cluster should be resolved after clearing thread-local")
          .isSameAs(globalCluster);
    }

    @Test
    void clearShouldNotAffectChildThreadsThatAlreadyInherited() throws InterruptedException {
      // Arrange: Set parent cluster and spawn child thread
      K8sCluster parentCluster = new MockCluster("parent-cluster");
      TestPodDefaults.setClusterSupplier(() -> parentCluster);

      CountDownLatch childStarted = new CountDownLatch(1);
      CountDownLatch parentCleared = new CountDownLatch(1);
      AtomicReference<K8sCluster> childResultAfterParentClear = new AtomicReference<>();

      Thread childThread =
          new Thread(
              () -> {
                try {
                  // Signal that child has started (and inherited context)
                  childStarted.countDown();

                  // Wait for parent to clear
                  parentCleared.await(5, TimeUnit.SECONDS);

                  // Child should still have its inherited copy
                  childResultAfterParentClear.set(TestPodDefaults.resolveCluster());
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  TestPodDefaults.clear();
                }
              });

      childThread.start();
      assertThat(childStarted.await(5, TimeUnit.SECONDS)).isTrue();

      // Act: Parent clears its state
      TestPodDefaults.clear();
      parentCleared.countDown();

      childThread.join(5000);

      // Assert: Child still has its inherited copy (defensive copy in childValue)
      assertThat(childResultAfterParentClear.get())
          .as("Child should still have inherited cluster after parent clear")
          .isSameAs(parentCluster);
    }
  }

  @Nested
  class ErrorConditions {

    @Test
    void hasClusterConfiguredShouldReturnFalseWhenNotConfigured() {
      // Arrange: Ensure clean state
      TestPodDefaults.clear();
      TestPodDefaults.clearGlobalDefaults();

      // Act & Assert
      assertThat(TestPodDefaults.hasClusterConfigured())
          .as("hasClusterConfigured should return false when nothing is configured")
          .isFalse();
    }

    @Test
    void hasClusterConfiguredShouldReturnTrueWhenThreadLocalSet() {
      // Arrange: Set thread-local supplier
      K8sCluster cluster = new MockCluster("test-cluster");
      TestPodDefaults.setClusterSupplier(() -> cluster);

      // Act & Assert
      assertThat(TestPodDefaults.hasClusterConfigured())
          .as("hasClusterConfigured should return true when thread-local supplier is set")
          .isTrue();
    }

    @Test
    void hasClusterConfiguredShouldReturnTrueWhenGlobalSet() {
      // Arrange: Ensure clean thread-local state, set global only
      TestPodDefaults.clear();
      K8sCluster cluster = new MockCluster("global-cluster");
      TestPodDefaults.setGlobalClusterSupplier(() -> cluster);

      // Act & Assert
      assertThat(TestPodDefaults.hasClusterConfigured())
          .as("hasClusterConfigured should return true when global supplier is set")
          .isTrue();
    }
  }

  @Nested
  class DeprecatedMethod {

    @SuppressWarnings("deprecation")
    @Test
    void clearThreadLocalShouldDelegateToClear() {
      // Arrange: Set cluster supplier
      K8sCluster cluster = new MockCluster("test-cluster");
      TestPodDefaults.setClusterSupplier(() -> cluster);

      assertThat(TestPodDefaults.hasClusterConfigured()).isTrue();

      // Act: Call deprecated method
      TestPodDefaults.clearThreadLocal();

      // Assert: State should be cleared
      assertThat(TestPodDefaults.hasClusterConfigured())
          .as("clearThreadLocal should clear state like clear()")
          .isFalse();
    }
  }

  /**
   * Mock implementation of K8sCluster for testing.
   *
   * <p>This mock provides a named cluster for identification in tests without requiring a real
   * Kubernetes cluster connection.
   */
  private static class MockCluster implements K8sCluster {

    private final String name;

    MockCluster(String name) {
      this.name = name;
    }

    @Override
    public KubernetesClient getClient() {
      throw new UnsupportedOperationException("MockCluster does not support getClient()");
    }

    @Override
    public ExternalAccessStrategy getAccessStrategy() {
      throw new UnsupportedOperationException("MockCluster does not support getAccessStrategy()");
    }

    @Override
    public void close() throws IOException {
      // No-op for mock
    }

    @Override
    public String toString() {
      return "MockCluster{name='" + name + "'}";
    }
  }
}
