package org.testpods.core.pods;

import java.util.function.Supplier;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.NamespaceNaming;
import org.testpods.core.cluster.TestNamespace;

/**
 * Configurable defaults for TestPod instances with thread-safe isolation.
 *
 * <p>This class provides thread-isolated configuration for TestPod instances using {@link
 * InheritableThreadLocal}, enabling safe parallel test execution in JUnit 5.
 *
 * <p>Defaults are resolved in this order:
 *
 * <ol>
 *   <li>Thread-local context (set by JUnit extensions per-test)
 *   <li>Global defaults (set at application startup)
 *   <li>Built-in fallbacks (auto-discover cluster, generate namespace name)
 * </ol>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>All thread-local state is stored in an {@link InheritableThreadLocal}, which means:
 *
 * <ul>
 *   <li>Each test thread gets its own isolated configuration
 *   <li>Child threads (e.g., parallel assertions) inherit parent configuration
 *   <li>No race conditions between parallel tests
 * </ul>
 *
 * <p><strong>Important:</strong> Call {@link #clear()} in your test cleanup (afterAll) to prevent
 * memory leaks in thread pool executors.
 *
 * <h2>JUnit Extension Usage</h2>
 *
 * <pre>{@code
 * public class TestPodExtension implements BeforeAllCallback, AfterAllCallback {
 *
 *     @Override
 *     public void beforeAll(ExtensionContext context) {
 *         Class<?> testClass = context.getRequiredTestClass();
 *
 *         // Configure defaults for this test class
 *         TestPodDefaults.setNamespaceNameSupplier(
 *             NamespaceNaming.forTestClass(testClass)
 *         );
 *
 *         // Optionally set explicit cluster
 *         TestPodDefaults.setClusterSupplier(() -> K8sCluster.minikube());
 *     }
 *
 *     @Override
 *     public void afterAll(ExtensionContext context) {
 *         TestPodDefaults.clear();
 *     }
 * }
 * }</pre>
 *
 * <h2>Standalone Usage</h2>
 *
 * For non-JUnit usage, global defaults can be configured:
 *
 * <pre>{@code
 * // At application startup
 * TestPodDefaults.setGlobalClusterSupplier(() -> K8sCluster.minikube());
 * TestPodDefaults.setGlobalNamespaceNameSupplier(
 *     NamespaceNaming.withContext("integration-tests")
 * );
 * }</pre>
 */
public final class TestPodDefaults {

  /**
   * Thread-local context using InheritableThreadLocal for safe parallel test execution.
   *
   * <p>InheritableThreadLocal ensures that child threads (spawned for parallel assertions, async
   * operations, etc.) inherit the parent thread's configuration.
   */
  private static final InheritableThreadLocal<Context> THREAD_CONTEXT =
      new InheritableThreadLocal<>() {
        @Override
        protected Context childValue(Context parentValue) {
          // Create defensive copy for child threads
          return parentValue != null ? new Context(parentValue) : null;
        }
      };

  // Global defaults (fallback when no thread-local context)
  private static volatile Supplier<K8sCluster> globalClusterSupplier;
  private static volatile Supplier<String> globalNamespaceNameSupplier;
  private static volatile TestNamespace globalSharedNamespace;

  private TestPodDefaults() {}

  // =========================================================================
  // Thread-local context (for JUnit extensions)
  // =========================================================================

  /**
   * Set a thread-local cluster supplier. This takes precedence over global defaults.
   *
   * @param supplier the cluster supplier to use for this thread
   */
  public static void setClusterSupplier(Supplier<K8sCluster> supplier) {
    getOrCreateContext().clusterSupplier = supplier;
  }

  /**
   * Set a thread-local namespace name supplier. This takes precedence over global defaults.
   *
   * @param supplier the namespace name supplier to use for this thread
   */
  public static void setNamespaceNameSupplier(Supplier<String> supplier) {
    getOrCreateContext().namespaceNameSupplier = supplier;
  }

  /**
   * Set a thread-local shared namespace. When set, all TestPods in this thread will use this
   * namespace.
   *
   * @param namespace the shared namespace for this thread
   */
  public static void setSharedNamespace(TestNamespace namespace) {
    getOrCreateContext().sharedNamespace = namespace;
  }

  /**
   * Clear thread-local context. Call this in test cleanup (afterAll/afterEach).
   *
   * <p><strong>Important:</strong> Always call this method in your cleanup to prevent memory leaks,
   * especially when using thread pools.
   *
   * @deprecated Use {@link #clear()} instead for consistency with common naming conventions.
   */
  @Deprecated
  public static void clearThreadLocal() {
    clear();
  }

  /**
   * Clear all thread-local state for the current thread.
   *
   * <p><strong>Important:</strong> Always call this method in your test cleanup (afterAll) to
   * prevent memory leaks when using thread pool executors.
   *
   * <p>This method removes the thread-local context, freeing any references held by the current
   * thread. Child threads that inherited the context are not affected.
   */
  public static void clear() {
    THREAD_CONTEXT.remove();
  }

  // =========================================================================
  // Global defaults (fallback)
  // =========================================================================

  /**
   * Set global cluster supplier (used when no thread-local supplier).
   *
   * @param supplier the global cluster supplier
   */
  public static void setGlobalClusterSupplier(Supplier<K8sCluster> supplier) {
    globalClusterSupplier = supplier;
  }

  /**
   * Set global namespace name supplier (used when no thread-local supplier).
   *
   * @param supplier the global namespace name supplier
   */
  public static void setGlobalNamespaceNameSupplier(Supplier<String> supplier) {
    globalNamespaceNameSupplier = supplier;
  }

  /**
   * Set a global shared namespace. When set, all TestPods will use this namespace unless
   * overridden.
   *
   * @param namespace the global shared namespace
   */
  public static void setGlobalSharedNamespace(TestNamespace namespace) {
    globalSharedNamespace = namespace;
  }

  /** Clear all global defaults. */
  public static void clearGlobalDefaults() {
    globalClusterSupplier = null;
    globalNamespaceNameSupplier = null;
    globalSharedNamespace = null;
  }

  // =========================================================================
  // Resolution (used by BaseTestPod)
  // =========================================================================

  /**
   * Check if a cluster supplier has been configured (thread-local or global).
   *
   * @return true if a cluster supplier is configured, false otherwise
   */
  public static boolean hasClusterConfigured() {
    Context ctx = THREAD_CONTEXT.get();
    if (ctx != null && ctx.clusterSupplier != null) {
      return true;
    }
    return globalClusterSupplier != null;
  }

  /**
   * Get the shared namespace if one is configured.
   *
   * @return Shared namespace, or null if none configured
   */
  public static TestNamespace getSharedNamespace() {
    Context ctx = THREAD_CONTEXT.get();
    if (ctx != null && ctx.sharedNamespace != null) {
      return ctx.sharedNamespace;
    }
    return globalSharedNamespace;
  }

  /**
   * Resolve the cluster to use.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>Thread-local supplier
   *   <li>Global supplier
   *   <li>Auto-discover via {@link K8sCluster#discover()}
   * </ol>
   *
   * @return The resolved cluster
   */
  public static K8sCluster resolveCluster() {
    // Thread-local first
    Context ctx = THREAD_CONTEXT.get();
    if (ctx != null && ctx.clusterSupplier != null) {
      return ctx.clusterSupplier.get();
    }

    // Global supplier
    if (globalClusterSupplier != null) {
      return globalClusterSupplier.get();
    }

    // Fallback to auto-discovery
    return K8sCluster.discover();
  }

  /**
   * Resolve the namespace name to use.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>Thread-local supplier
   *   <li>Global supplier
   *   <li>Default generator: {@code testpods-xxxxx}
   * </ol>
   *
   * @return The resolved namespace name, or null if using default naming
   */
  public static String resolveNamespaceName() {
    // Thread-local first
    Context ctx = THREAD_CONTEXT.get();
    if (ctx != null && ctx.namespaceNameSupplier != null) {
      return ctx.namespaceNameSupplier.get();
    }

    // Global supplier
    if (globalNamespaceNameSupplier != null) {
      return globalNamespaceNameSupplier.get();
    }

    // Fallback to default generator
    return NamespaceNaming.generate();
  }

  // =========================================================================
  // Internal
  // =========================================================================

  /**
   * Get or create the thread-local context for the current thread.
   *
   * @return the context for the current thread
   */
  private static Context getOrCreateContext() {
    Context ctx = THREAD_CONTEXT.get();
    if (ctx == null) {
      ctx = new Context();
      THREAD_CONTEXT.set(ctx);
    }
    return ctx;
  }

  /**
   * Mutable context holder for thread-local state.
   *
   * <p>This class uses volatile fields to ensure visibility across threads when the context is
   * copied to child threads via {@link InheritableThreadLocal#childValue(Object)}.
   */
  private static class Context {
    volatile Supplier<K8sCluster> clusterSupplier;
    volatile Supplier<String> namespaceNameSupplier;
    volatile TestNamespace sharedNamespace;

    /** Create an empty context. */
    Context() {}

    /**
     * Copy constructor for child thread inheritance.
     *
     * <p>Creates a defensive copy of the parent context to ensure isolation between parent and
     * child threads. Changes in either thread will not affect the other.
     *
     * @param parent the parent context to copy from
     */
    Context(Context parent) {
      this.clusterSupplier = parent.clusterSupplier;
      this.namespaceNameSupplier = parent.namespaceNameSupplier;
      this.sharedNamespace = parent.sharedNamespace;
    }
  }
}
