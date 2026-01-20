package org.testpods.core.pods;

import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.NamespaceNaming;
import org.testpods.core.cluster.TestNamespace;

import java.util.function.Supplier;

/**
 * Configurable defaults for TestPod instances.
 * <p>
 * This class allows configuration of default cluster and namespace behavior
 * when TestPods are created without explicit cluster/namespace specification.
 * <p>
 * Defaults are resolved in this order:
 * <ol>
 *   <li>Thread-local context (set by JUnit extensions per-test)</li>
 *   <li>Global defaults (set at application startup)</li>
 *   <li>Built-in fallbacks (auto-discover cluster, generate namespace name)</li>
 * </ol>
 *
 * <h2>Future JUnit Extension Usage</h2>
 * A JUnit extension would typically:
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
 *         TestPodDefaults.clearThreadLocal();
 *     }
 * }
 * }</pre>
 *
 * <h2>Standalone Usage</h2>
 * For non-JUnit usage, global defaults can be configured:
 * <pre>{@code
 * // At application startup
 * TestPodDefaults.setGlobalClusterSupplier(() -> K8sCluster.minikube());
 * TestPodDefaults.setGlobalNamespaceNameSupplier(
 *     NamespaceNaming.withContext("integration-tests")
 * );
 * }</pre>
 */
public final class TestPodDefaults {

    // Thread-local context (for JUnit extension per-test isolation)
    private static final ThreadLocal<Context> THREAD_CONTEXT = new ThreadLocal<>();

    // Global defaults (fallback when no thread-local context)
    private static volatile Supplier<K8sCluster> globalClusterSupplier;
    private static volatile Supplier<String> globalNamespaceNameSupplier;
    private static volatile TestNamespace globalSharedNamespace;

    private TestPodDefaults() {}

    // =========================================================================
    // Thread-local context (for JUnit extensions)
    // =========================================================================

    /**
     * Set a thread-local cluster supplier.
     * This takes precedence over global defaults.
     */
    public static void setClusterSupplier(Supplier<K8sCluster> supplier) {
        getOrCreateContext().clusterSupplier = supplier;
    }

    /**
     * Set a thread-local namespace name supplier.
     * This takes precedence over global defaults.
     */
    public static void setNamespaceNameSupplier(Supplier<String> supplier) {
        getOrCreateContext().namespaceNameSupplier = supplier;
    }

    /**
     * Set a thread-local shared namespace.
     * When set, all TestPods in this thread will use this namespace.
     */
    public static void setSharedNamespace(TestNamespace namespace) {
        getOrCreateContext().sharedNamespace = namespace;
    }

    /**
     * Clear thread-local context.
     * Call this in test cleanup (afterAll/afterEach).
     */
    public static void clearThreadLocal() {
        THREAD_CONTEXT.remove();
    }

    // =========================================================================
    // Global defaults (fallback)
    // =========================================================================

    /**
     * Set global cluster supplier (used when no thread-local supplier).
     */
    public static void setGlobalClusterSupplier(Supplier<K8sCluster> supplier) {
        globalClusterSupplier = supplier;
    }

    /**
     * Set global namespace name supplier (used when no thread-local supplier).
     */
    public static void setGlobalNamespaceNameSupplier(Supplier<String> supplier) {
        globalNamespaceNameSupplier = supplier;
    }

    /**
     * Set a global shared namespace.
     * When set, all TestPods will use this namespace unless overridden.
     */
    public static void setGlobalSharedNamespace(TestNamespace namespace) {
        globalSharedNamespace = namespace;
    }

    /**
     * Clear all global defaults.
     */
    public static void clearGlobalDefaults() {
        globalClusterSupplier = null;
        globalNamespaceNameSupplier = null;
        globalSharedNamespace = null;
    }

    // =========================================================================
    // Resolution (used by BaseTestPod)
    // =========================================================================

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
     * <p>
     * Resolution order:
     * <ol>
     *   <li>Thread-local supplier</li>
     *   <li>Global supplier</li>
     *   <li>Auto-discover via {@link K8sCluster#discover()}</li>
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
     * <p>
     * Resolution order:
     * <ol>
     *   <li>Thread-local supplier</li>
     *   <li>Global supplier</li>
     *   <li>Default generator: {@code testpods-xxxxx}</li>
     * </ol>
     *
     * @return The resolved namespace name
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
     */
    private static class Context {
        Supplier<K8sCluster> clusterSupplier;
        Supplier<String> namespaceNameSupplier;
        TestNamespace sharedNamespace;
    }
}
