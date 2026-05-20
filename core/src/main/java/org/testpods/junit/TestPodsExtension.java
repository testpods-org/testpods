package org.testpods.junit;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.*;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.pods.TestPodDefaults;
import org.testpods.core.provisioning.Provisioner;
import org.testpods.core.provisioning.ReflectionHelper;
import org.testpods.core.provisioning.Registry;

import java.util.HashSet;
import java.util.Set;

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
@Slf4j
public class TestPodsExtension
        implements
        BeforeEachCallback,
        BeforeAllCallback,
        AfterEachCallback,
        AfterAllCallback,
        ExecutionCondition {

    TestPods testPodsAnnotation;
    Registry registry = new Registry();
    Provisioner provisioner = new Provisioner();

    @Override
    public void beforeAll(@NonNull ExtensionContext extensionContext) throws Exception {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        testPodsAnnotation = testClass.getAnnotation(TestPods.class);
        assert testPodsAnnotation != null : "TestPods annotation is required on the test class";


        //Populate TestPodsRepository
        K8sCluster cluster =  ReflectionHelper.scanClassForClusterRegistration(testClass);
        var testPodDeclarations = ReflectionHelper.scanTestClassForTestPodDeclarationsOnly(testClass);
        registry.addTestPodDeclarations(testPodDeclarations);
        var testPodInitializations = ReflectionHelper.scanClassForTestPodInitializationsOnly(testClass);
        registry.addTestPodInitializations(testPodInitializations);

        final Class<?>[] testpodsProviders = testPodsAnnotation.testpodsProviders();
        Set<K8sCluster> providedClusters = new HashSet<>();
        if (testpodsProviders != null && testpodsProviders.length > 0) {
            var providedTestPodInitializations = ReflectionHelper.scanTestPodsProvidersForTestPodInitializers(testpodsProviders);
            registry.addTestPodInitializations(providedTestPodInitializations); // priority to test class initializers
            final K8sCluster providedCluster = ReflectionHelper.scanClassForClusterRegistration(testpodsProviders);
            providedClusters.add(providedCluster);
        }

        if (cluster == null && !providedClusters.isEmpty()) {
            cluster = providedClusters.stream().findFirst().orElse(null);
        }
        assert cluster != null : "A K8Cluster is required for TestPods to provision resources.";
        registry.setCluster(cluster);

        registry.validateConfiguration();
        provisioner.setRegistry(registry);

        // start the cluster and ensure namespace is running.
        // initialize the test pods
        // inject cluster into the test pods
        // deploy the test pods
        // await readiness

        // assign the initialized test pods to the test class fields
        // assign the cluster to the test class field

    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
    }


    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {

        registry.tearDown();
        // Clear thread-local state to prevent memory leaks in thread pool executors
        TestPodDefaults.clear();
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext extensionContext) {
        return null;
    }
}
