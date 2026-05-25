package org.testpods.junit;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.*;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.ProfileLifecyclePolicy;
import org.testpods.core.pods.Pod;
import org.testpods.core.pods.TestPodDefaults;
import org.testpods.core.provisioning.Provisioner;
import org.testpods.core.provisioning.ReflectionHelper;
import org.testpods.core.provisioning.Registry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

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
 * @TestPods
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
        TestInstancePostProcessor {

    TestPods testPodsAnnotation;
    Registry registry = new Registry();
    Provisioner provisioner = new Provisioner();

    @Override
    public void beforeAll(@NonNull ExtensionContext extensionContext) throws Exception {
        //TODO look into the enclosing test classes which may also have relevant annotations.
        Class<?> testClass = extensionContext.getRequiredTestClass();
        testPodsAnnotation = testClass.getAnnotation(TestPods.class);
        assert testPodsAnnotation != null : "TestPods annotation is required on the test class";
        populateAndValidateRegistry(testClass);

        provisioner.setRegistry(registry);

        // start the cluster and ensure namespace is running.
        provisioner.ensureClusterIsReady();
        provisioner.ensureNamespaceIsActive();
        provisioner.provisionTestPods();

        // initialize the test pods
        // inject cluster into the test pods
        // deploy the test pods
        // await readiness

        // assign the initialized test pods to the test class fields
        assignClusterToTestClassStaticField(testClass, registry.getCluster());
        assignInitializedPodsToTestClassFields(testClass, registry);
        extensionContext.getTestInstance()
                .ifPresent(testInstance -> injectIntoTestInstance(testInstance, testClass));

    }

    /**
     * Scans the given class for static fields annotated with {@link TestPod} that is not already
     * initialized.
     * If such a field is found, the registry is searched for a matching pod and assigned to the field.
     * A pod is said to be matching a @TestPod field when the field Pod type matches both the pod type and the pod name.
     *
     * Examples:
     * // This myPod field is already initialized and will not be assigned a pod.
     * @TestPod
     * static MyPod myPod = new MyPod();
     *
     * // This myPod field is not initialized and will be assigned a pod from the registry with the name "MyPod".
     * @TestPod
     * static MyPod myPod;
     *
     * // This myPod field is not initialized and will be assigned a pod from the registry with the name "my-pod" that matches the podName attribute.
     * @TestPod(podName = "my-pod")
     * static MyPod myPod;
     *
     * Note that the pod name is case-sensitive and must match exactly with the pod name in the registry.
     * This applies to both the field name and the pod name in the registry.
     * Also note that the podName attribute value takes precedence over the field name when assigning a pod.
     *
     * @param testClass the test class to inject active pods into fields annotated with @TestPod which are not already initialized.
     */
    void assignInitializedPodsToTestClassFields(Class<?> testClass, Registry registry) {
        Map<String, Pod<?>> podsByName = registry.getPodsByName();
        if (podsByName.isEmpty()) return;

        for (Field field : testClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(TestPod.class)) continue;
            if (!Modifier.isStatic(field.getModifiers())) continue;

            field.setAccessible(true);
            Object current;
            try {
                current = field.get(null);
            } catch (IllegalAccessException e) {
                log.error("Cannot access @TestPod field '{}' in {}: {}",
                        field.getName(), testClass.getSimpleName(), e.getMessage());
                continue;
            }

            if (current != null) continue;

            TestPod annotation = field.getAnnotation(TestPod.class);
            String podName = resolvePodName(annotation, field.getName());

            Pod<?> pod = podsByName.get(podName);
            if (pod == null) {
                log.warn("No pod found in registry with name '{}' for @TestPod field '{}' in {}",
                        podName, field.getName(), testClass.getSimpleName());
                continue;
            }

            if (!field.getType().isAssignableFrom(pod.getClass())) {
                log.warn("Pod '{}' type {} is not assignable to @TestPod field '{}' type {} in {}",
                        podName, pod.getClass().getSimpleName(),
                        field.getName(), field.getType().getSimpleName(), testClass.getSimpleName());
                continue;
            }

            try {
                field.set(null, pod);
                log.debug("Assigned pod '{}' to field '{}' in {}", podName, field.getName(), testClass.getSimpleName());
            } catch (IllegalAccessException e) {
                log.error("Cannot assign pod '{}' to field '{}' in {}: {}",
                        podName, field.getName(), testClass.getSimpleName(), e.getMessage());
            }
        }
    }

    private String resolvePodName(TestPod annotation, String fieldName) {
        if (!annotation.podName().isEmpty()) return annotation.podName();
        if (!annotation.value().isEmpty()) return annotation.value();
        return fieldName;
    }

    /**
     * Scans the given class for a static field of type K8sCluster.
     * If found, assigns the cluster to the field provided the field is not already initialized.
     * If multiple fields are found, then the cluster is assigned to all of them.
     *
     * @param testClass the test class to inject the cluster into a field of the type K8sCluster
     */
    void assignClusterToTestClassStaticField(Class<?> testClass, K8sCluster cluster) {
        for (Field field : testClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!K8sCluster.class.isAssignableFrom(field.getType())) continue;

            field.setAccessible(true);
            try {
                if (field.get(null) != null) continue;
                field.set(null, cluster);
                log.debug("Injected cluster into field '{}' in {}", field.getName(), testClass.getSimpleName());
            } catch (IllegalAccessException e) {
                log.error("Cannot assign cluster to field '{}' in {}: {}",
                        field.getName(), testClass.getSimpleName(), e.getMessage());
            }
        }
    }

    void assignClusterToNonStaticFields(Object instance, Class<?> testClass, K8sCluster cluster) {
        for (Field field : testClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (!K8sCluster.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            try {
                if (field.get(instance) != null) continue;
                field.set(instance, cluster);
                log.debug("Injected cluster into non-static field '{}' in {}",
                        field.getName(), testClass.getSimpleName());
            } catch (IllegalAccessException e) {
                log.error("Cannot assign cluster to non-static field '{}' in {}: {}",
                        field.getName(), testClass.getSimpleName(), e.getMessage());
            }
        }
    }

    void assignPodsToNonStaticFields(Object instance, Class<?> testClass, Registry registry) {
        Map<String, Pod<?>> podsByName = registry.getPodsByName();
        if (podsByName.isEmpty()) return;
        for (Field field : testClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (!field.isAnnotationPresent(TestPod.class)) continue;
            field.setAccessible(true);
            try {
                if (field.get(instance) != null) continue;
                String podName = resolvePodName(field.getAnnotation(TestPod.class), field.getName());
                Pod<?> pod = podsByName.get(podName);
                if (pod == null) {
                    log.warn("No pod found in registry with name '{}' for non-static @TestPod field '{}' in {}",
                            podName, field.getName(), testClass.getSimpleName());
                    continue;
                }
                if (!field.getType().isAssignableFrom(pod.getClass())) {
                    log.warn("Pod '{}' type {} is not assignable to non-static field '{}' type {} in {}",
                            podName, pod.getClass().getSimpleName(),
                            field.getName(), field.getType().getSimpleName(), testClass.getSimpleName());
                    continue;
                }
                field.set(instance, pod);
                log.debug("Assigned pod '{}' to non-static field '{}' in {}",
                        podName, field.getName(), testClass.getSimpleName());
            } catch (IllegalAccessException e) {
                log.error("Cannot access or assign non-static @TestPod field '{}' in {}: {}",
                        field.getName(), testClass.getSimpleName(), e.getMessage());
            }
        }
    }

    void populateAndValidateRegistry(Class<?> testClass) {
        // Static fields in test class
        ReflectionHelper.ClusterRegistration clusterRegistration =
                ReflectionHelper.scanClassForClusterRegistrationDetails(testClass);
        K8sCluster cluster = clusterRegistration != null ? clusterRegistration.cluster() : null;
        var testPodDeclarations = ReflectionHelper.scanTestClassForTestPodDeclarationsOnly(testClass);
        registry.addTestPodDeclarations(testPodDeclarations);
        var staticInitializations = ReflectionHelper.scanClassForTestPodInitializationsOnly(testClass);
        registry.addTestPodInitializations(staticInitializations);

        boolean deleteProfileAfterTests =
                clusterRegistration != null
                        ? clusterRegistration.deleteProfileAfterTests()
                        : testPodsAnnotation.deleteProfileAfterTests();
        registry.setProfileLifecyclePolicy(
                deleteProfileAfterTests
                        ? ProfileLifecyclePolicy.DESTROY_ON_CLOSE
                        : ProfileLifecyclePolicy.LEAVE_RUNNING);

        // Provider classes — static and non-static (new behaviour for non-static)
        final Class<?>[] testpodsProviders = testPodsAnnotation.testpodsProviders();
        if (testpodsProviders.length > 0) {
            // Instantiate each provider exactly once and share the instances across both scans
            // to avoid invoking provider constructors twice.
            Map<Class<?>, Object> providerInstances = new java.util.HashMap<>();
            for (Class<?> provider : testpodsProviders) {
                Object instance = ReflectionHelper.tryInstantiate(provider);
                if (instance != null) {
                    providerInstances.put(provider, instance);
                }
            }
            var providedInitializations =
                    ReflectionHelper.scanTestPodsProvidersForAllTestPodInitializers(
                            testpodsProviders, providerInstances);
            registry.addTestPodInitializations(providedInitializations);
            if (cluster == null) {
                ReflectionHelper.ClusterRegistration providerRegistration =
                        ReflectionHelper.scanTestPodsProvidersForClusterRegistrationDetails(
                                testpodsProviders, providerInstances);
                if (providerRegistration != null) {
                    cluster = providerRegistration.cluster();
                    deleteProfileAfterTests = providerRegistration.deleteProfileAfterTests();
                } else {
                    cluster = ReflectionHelper.scanTestPodsProvidersForClusterRegistration(
                            testpodsProviders, providerInstances);
                }
            }
        }
        registry.setCluster(cluster);
        registry.validateConfiguration();
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext extensionContext) throws Exception {
        injectIntoTestInstance(testInstance, extensionContext.getRequiredTestClass());
    }

    private void injectIntoTestInstance(Object testInstance, Class<?> testClass) {
        K8sCluster cluster = registry.getCluster();
        if (cluster != null) {
            assignClusterToNonStaticFields(testInstance, testClass, cluster);
        }
        assignPodsToNonStaticFields(testInstance, testClass, registry);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
    }


    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        if (testPodsAnnotation == null || testPodsAnnotation.deleteNamespaceAfterTests()) {
            registry.tearDown();
            if (registry.getCluster() != null) {
                registry.getCluster().close();
            }
        } else {
            log.info(
                    "Leaving TestPods namespace and provisioned resources running for {} because "
                            + "@TestPods(deleteNamespaceAfterTests = false) is set",
                    extensionContext.getRequiredTestClass().getSimpleName());
        }
        // Clear thread-local state to prevent memory leaks in thread pool executors
        TestPodDefaults.clear();
    }
}
