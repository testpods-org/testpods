package org.testpods.junit;

import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testpods.core.ExecResult;
import org.testpods.core.PropertyContext;
import org.testpods.core.cluster.ExternalAccessStrategy;
import org.testpods.core.cluster.HostAndPort;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.Namespace;
import org.testpods.core.pods.Pod;
import org.testpods.core.pods.builders.InitContainerBuilder;
import org.testpods.core.pods.builders.SidecarBuilder;
import org.testpods.core.provisioning.Registry;
import org.testpods.core.wait.WaitStrategy;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TestPodsExtensionAssignmentTest {

    static final K8sCluster STUB_CLUSTER = stubCluster();
    static final K8sCluster OTHER_CLUSTER = stubCluster();
    static final Namespace STUB_NAMESPACE =
            Namespace.owned("testpods-test", new io.fabric8.kubernetes.api.model.NamespaceBuilder()
                    .withNewMetadata().withName("testpods-test").endMetadata()
                    .withNewStatus().withPhase("Active").endStatus()
                    .build());

    private static K8sCluster stubCluster() {
        return new K8sCluster() {
            @Override public KubernetesClient getClient() { return null; }
            @Override public ExternalAccessStrategy getAccessStrategy() { return null; }
            @Override public Namespace getDefaultNamespace() { return null; }
            @Override public Namespace getNamespace(String name) { return null; }
            @Override public Namespace createNamespace(String name) { return null; }
            @Override public Namespace createNamespace() { return null; }
            @Override public Namespace attachNamespace(String name) { return null; }
            @Override public void deleteNamespace(String name) {}
            @Override public K8sCluster withNamespace() { return this; }
            @Override public void close() {}
        };
    }

    /** A second concrete pod type, distinct from {@link StubPod}, for type-mismatch tests. */
    static class OtherStubPod implements Pod<OtherStubPod> {
        private final String name;

        OtherStubPod(String name) { this.name = name; }

        @Override public String getName() { return name; }
        @Override public OtherStubPod withName(String n) { throw new UnsupportedOperationException(); }
        @Override public OtherStubPod inNamespace(Namespace ns) { throw new UnsupportedOperationException(); }
        @Override public OtherStubPod inNamespace(String ns) { throw new UnsupportedOperationException(); }
        @Override public OtherStubPod inCluster(K8sCluster c) { throw new UnsupportedOperationException(); }
        @Override public OtherStubPod withLabels(Map<String, String> l) { throw new UnsupportedOperationException(); }
        @Override public OtherStubPod withAnnotations(Map<String, String> a) { throw new UnsupportedOperationException(); }
        @Override public OtherStubPod withResources(String cpu, String mem) { throw new UnsupportedOperationException(); }
        @Override public OtherStubPod withInitContainer(Consumer<InitContainerBuilder> c) { throw new UnsupportedOperationException(); }
        @Override public OtherStubPod withSidecar(Consumer<SidecarBuilder> c) { throw new UnsupportedOperationException(); }
        @Override public OtherStubPod withPodCustomizer(UnaryOperator<PodSpecBuilder> c) { throw new UnsupportedOperationException(); }
        @Override public OtherStubPod waitingFor(WaitStrategy s) { throw new UnsupportedOperationException(); }
        @Override public void start() {}
        @Override public void stop() {}
        @Override public boolean isRunning() { return false; }
        @Override public boolean isReady() { return false; }
        @Override public String getLogs() { return null; }
        @Override public String getLogs(Duration since) { return null; }
        @Override public String getLogs(String containerName) { return null; }
        @Override public ExecResult exec(String... command) { return null; }
        @Override public ExecResult exec(String containerName, String... command) { return null; }
        @Override public Namespace getNamespace() { return null; }
        @Override public K8sCluster getCluster() { return null; }
        @Override public String getInternalHost() { return null; }
        @Override public int getInternalPort() { return 0; }
        @Override public String getExternalHost() { return null; }
        @Override public int getExternalPort() { return 0; }
        @Override public void publishProperties(PropertyContext ctx) {}
    }

    static class StubPod implements Pod<StubPod> {
        private final String name;

        StubPod(String name) { this.name = name; }

        @Override public String getName() { return name; }
        @Override public StubPod withName(String n) { throw new UnsupportedOperationException(); }
        @Override public StubPod inNamespace(Namespace ns) { throw new UnsupportedOperationException(); }
        @Override public StubPod inNamespace(String ns) { throw new UnsupportedOperationException(); }
        @Override public StubPod inCluster(K8sCluster c) { throw new UnsupportedOperationException(); }
        @Override public StubPod withLabels(Map<String, String> l) { throw new UnsupportedOperationException(); }
        @Override public StubPod withAnnotations(Map<String, String> a) { throw new UnsupportedOperationException(); }
        @Override public StubPod withResources(String cpu, String mem) { throw new UnsupportedOperationException(); }
        @Override public StubPod withInitContainer(Consumer<InitContainerBuilder> c) { throw new UnsupportedOperationException(); }
        @Override public StubPod withSidecar(Consumer<SidecarBuilder> c) { throw new UnsupportedOperationException(); }
        @Override public StubPod withPodCustomizer(UnaryOperator<PodSpecBuilder> c) { throw new UnsupportedOperationException(); }
        @Override public StubPod waitingFor(WaitStrategy s) { throw new UnsupportedOperationException(); }
        @Override public void start() {}
        @Override public void stop() {}
        @Override public boolean isRunning() { return false; }
        @Override public boolean isReady() { return false; }
        @Override public String getLogs() { return null; }
        @Override public String getLogs(Duration since) { return null; }
        @Override public String getLogs(String containerName) { return null; }
        @Override public ExecResult exec(String... command) { return null; }
        @Override public ExecResult exec(String containerName, String... command) { return null; }
        @Override public Namespace getNamespace() { return null; }
        @Override public K8sCluster getCluster() { return null; }
        @Override public String getInternalHost() { return null; }
        @Override public int getInternalPort() { return 0; }
        @Override public String getExternalHost() { return null; }
        @Override public int getExternalPort() { return 0; }
        @Override public void publishProperties(PropertyContext ctx) {}
    }

    TestPodsExtension extension;
    Registry registry;

    @BeforeEach
    void setUp() {
        extension = new TestPodsExtension();
        registry = new Registry();
        extension.registry = registry;
    }

    private static Field field(Class<?> cls, String name) throws NoSuchFieldException {
        Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static ExtensionContext contextFor(Class<?> testClass) {
        return (ExtensionContext) Proxy.newProxyInstance(
                ExtensionContext.class.getClassLoader(),
                new Class<?>[] { ExtensionContext.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getRequiredTestClass")) return testClass;
                    if (method.getName().equals("getTestInstance")) return Optional.empty();
                    if (method.getName().equals("toString")) return "ExtensionContext(" + testClass.getName() + ")";
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, org.testpods.core.provisioning.FieldInitialization> getInitializations(Registry registry)
            throws Exception {
        java.lang.reflect.Field f = Registry.class.getDeclaredField("testPodInitializationsByName");
        f.setAccessible(true);
        return (Map<String, org.testpods.core.provisioning.FieldInitialization>) f.get(registry);
    }

    @Nested
    class ExtensionProvisioningTests {

        static class RecordingCluster implements K8sCluster {
            boolean closed;
            boolean namespaceDeleted;

            @Override public KubernetesClient getClient() { return null; }
            @Override public ExternalAccessStrategy getAccessStrategy() {
                return (pod, internalPort) -> new HostAndPort("127.0.0.1", 30000 + internalPort % 1000);
            }
            @Override public Namespace getDefaultNamespace() { return STUB_NAMESPACE; }
            @Override public Namespace getNamespace(String name) {
                return STUB_NAMESPACE.getName().equals(name) ? STUB_NAMESPACE : null;
            }
            @Override public Namespace createNamespace(String name) { return STUB_NAMESPACE; }
            @Override public Namespace createNamespace() { return STUB_NAMESPACE; }
            @Override public Namespace attachNamespace(String name) { return STUB_NAMESPACE; }
            @Override public void deleteNamespace(String name) { namespaceDeleted = true; }
            @Override public K8sCluster withNamespace() { return this; }
            @Override public void close() { closed = true; }
        }

        static class RecordingPod extends StubPod {
            K8sCluster cluster;
            Namespace namespace;
            boolean started;
            boolean stopped;

            RecordingPod(String name) {
                super(name);
            }

            @Override public RecordingPod inCluster(K8sCluster cluster) {
                this.cluster = cluster;
                return this;
            }

            @Override public RecordingPod inNamespace(Namespace namespace) {
                this.namespace = namespace;
                return this;
            }

            @Override public void start() {
                started = true;
                if (namespace == null && cluster != null) {
                    namespace = cluster.getDefaultNamespace();
                }
            }

            @Override public void stop() {
                stopped = true;
            }

            @Override public Namespace getNamespace() {
                return namespace;
            }

            @Override public K8sCluster getCluster() {
                return cluster;
            }

            @Override public String getExternalHost() {
                return "127.0.0.1";
            }

            @Override public int getExternalPort() {
                return 35432;
            }
        }

        @TestPods
        static class TestClassWithInitializedPod {
            static RecordingCluster cluster = new RecordingCluster();

            @RegisterCluster
            static K8sCluster registeredCluster = cluster;

            @TestPod
            static RecordingPod postgres = new RecordingPod("postgres");
        }

        @TestPods(deleteNamespaceAfterTests = false)
        static class TestClassWithPreservedNamespace {
            static RecordingCluster cluster = new RecordingCluster();

            @RegisterCluster
            static K8sCluster registeredCluster = cluster;

            @TestPod
            static RecordingPod postgres = new RecordingPod("postgres");
        }

        @Test
        void beforeAll_provisionsInitializedTestPodAndAfterAllStopsIt() throws Exception {
            TestClassWithInitializedPod.cluster = new RecordingCluster();
            TestClassWithInitializedPod.registeredCluster = TestClassWithInitializedPod.cluster;
            TestClassWithInitializedPod.postgres = new RecordingPod("postgres");

            try {
                extension.beforeAll(contextFor(TestClassWithInitializedPod.class));

                assertThat(TestClassWithInitializedPod.postgres.started).isTrue();
                assertThat(TestClassWithInitializedPod.postgres.getCluster())
                        .isSameAs(TestClassWithInitializedPod.cluster);
                assertThat(TestClassWithInitializedPod.postgres.getNamespace()).isSameAs(STUB_NAMESPACE);
                assertThat(registry.getPodsByName()).containsEntry("postgres", TestClassWithInitializedPod.postgres);
            } finally {
                extension.afterAll(contextFor(TestClassWithInitializedPod.class));
            }

            assertThat(TestClassWithInitializedPod.postgres.stopped).isTrue();
            assertThat(TestClassWithInitializedPod.cluster.namespaceDeleted).isTrue();
            assertThat(TestClassWithInitializedPod.cluster.closed).isTrue();
        }

        @Test
        void afterAll_preservesPodsNamespaceAndCluster_whenNamespaceDeletionDisabled() throws Exception {
            TestClassWithPreservedNamespace.cluster = new RecordingCluster();
            TestClassWithPreservedNamespace.registeredCluster = TestClassWithPreservedNamespace.cluster;
            TestClassWithPreservedNamespace.postgres = new RecordingPod("postgres");

            extension.beforeAll(contextFor(TestClassWithPreservedNamespace.class));
            extension.afterAll(contextFor(TestClassWithPreservedNamespace.class));

            assertThat(TestClassWithPreservedNamespace.postgres.started).isTrue();
            assertThat(TestClassWithPreservedNamespace.postgres.stopped).isFalse();
            assertThat(TestClassWithPreservedNamespace.cluster.namespaceDeleted).isFalse();
            assertThat(TestClassWithPreservedNamespace.cluster.closed).isFalse();
        }
    }

    @Nested
    class AssignClusterTests {

        static class OneNullField {
            static K8sCluster cluster;
        }

        static class OneInitializedField {
            static K8sCluster cluster;
        }

        static class TwoNullFields {
            static K8sCluster cluster1;
            static K8sCluster cluster2;
        }

        static class NoK8sClusterField {
            static String unrelated = "hello";
        }

        @BeforeEach
        void resetFields() throws Exception {
            field(OneNullField.class, "cluster").set(null, null);
            field(TwoNullFields.class, "cluster1").set(null, null);
            field(TwoNullFields.class, "cluster2").set(null, null);
            field(OneInitializedField.class, "cluster").set(null, OTHER_CLUSTER);
        }

        @Test
        void nullField_getsClusterAssigned() {
            extension.assignClusterToTestClassStaticField(OneNullField.class, STUB_CLUSTER);
            assertThat(OneNullField.cluster).isSameAs(STUB_CLUSTER);
        }

        @Test
        void initializedField_notOverwritten() {
            extension.assignClusterToTestClassStaticField(OneInitializedField.class, STUB_CLUSTER);
            assertThat(OneInitializedField.cluster).isSameAs(OTHER_CLUSTER);
        }

        @Test
        void multipleNullFields_allGetAssigned() {
            extension.assignClusterToTestClassStaticField(TwoNullFields.class, STUB_CLUSTER);
            assertThat(TwoNullFields.cluster1).isSameAs(STUB_CLUSTER);
            assertThat(TwoNullFields.cluster2).isSameAs(STUB_CLUSTER);
        }

        @Test
        void noK8sClusterField_doesNotThrow() {
            assertThatCode(() -> extension.assignClusterToTestClassStaticField(NoK8sClusterField.class, STUB_CLUSTER))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class AssignPodsTests {

        static class WithFieldNamedMyPod {
            @TestPod
            static StubPod myPod;
        }

        static class WithPodNameAnnotation {
            @TestPod(podName = "custom-name")
            static StubPod myPod;
        }

        static class WithInitializedPodField {
            @TestPod
            static StubPod myPod;
        }

        static class WithNoTestPodAnnotation {
            static StubPod myPod;
        }

        @BeforeEach
        void resetFields() throws Exception {
            field(WithFieldNamedMyPod.class, "myPod").set(null, null);
            field(WithPodNameAnnotation.class, "myPod").set(null, null);
            field(WithInitializedPodField.class, "myPod").set(null, null);
            field(WithNoTestPodAnnotation.class, "myPod").set(null, null);
        }

        @Test
        void nullField_matchedByFieldName_getsAssigned() {
            StubPod pod = new StubPod("myPod");
            registry.addPod("myPod", pod);
            extension.assignInitializedPodsToTestClassFields(WithFieldNamedMyPod.class, registry);
            assertThat(WithFieldNamedMyPod.myPod).isSameAs(pod);
        }

        @Test
        void nullField_matchedByAnnotationPodName_getsAssigned() {
            StubPod pod = new StubPod("custom-name");
            registry.addPod("custom-name", pod);
            extension.assignInitializedPodsToTestClassFields(WithPodNameAnnotation.class, registry);
            assertThat(WithPodNameAnnotation.myPod).isSameAs(pod);
        }

        @Test
        void initializedField_notOverwritten() throws Exception {
            StubPod existingPod = new StubPod("existing");
            field(WithInitializedPodField.class, "myPod").set(null, existingPod);

            registry.addPod("myPod", new StubPod("new"));
            extension.assignInitializedPodsToTestClassFields(WithInitializedPodField.class, registry);

            assertThat(WithInitializedPodField.myPod).isSameAs(existingPod);
        }

        @Test
        void emptyRegistry_fieldRemainsNull() {
            extension.assignInitializedPodsToTestClassFields(WithFieldNamedMyPod.class, registry);
            assertThat(WithFieldNamedMyPod.myPod).isNull();
        }

        @Test
        void noMatchingPodName_fieldRemainsNull() {
            registry.addPod("other-name", new StubPod("other-name"));
            extension.assignInitializedPodsToTestClassFields(WithFieldNamedMyPod.class, registry);
            assertThat(WithFieldNamedMyPod.myPod).isNull();
        }

        @Test
        void noTestPodAnnotatedFields_doesNotThrow() {
            registry.addPod("myPod", new StubPod("myPod"));
            assertThatCode(() -> extension.assignInitializedPodsToTestClassFields(WithNoTestPodAnnotation.class, registry))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class NonStaticAssignmentTests {

        static class WithNonStaticClusterField {
            K8sCluster cluster;
        }

        static class WithNonStaticInitializedClusterField {
            K8sCluster cluster;
        }

        static class WithNonStaticPodField {
            @TestPod
            StubPod myPod;
        }

        static class WithNonStaticPodNameAnnotation {
            @TestPod(podName = "custom-name")
            StubPod myPod;
        }

        static class WithNonStaticInitializedPodField {
            @TestPod
            StubPod myPod;
        }

        WithNonStaticClusterField clusterTarget;
        WithNonStaticInitializedClusterField initializedClusterTarget;
        WithNonStaticPodField podTarget;
        WithNonStaticPodNameAnnotation podNameTarget;
        WithNonStaticInitializedPodField initializedPodTarget;

        @BeforeEach
        void resetInstances() {
            clusterTarget = new WithNonStaticClusterField();
            initializedClusterTarget = new WithNonStaticInitializedClusterField();
            podTarget = new WithNonStaticPodField();
            podNameTarget = new WithNonStaticPodNameAnnotation();
            initializedPodTarget = new WithNonStaticInitializedPodField();
        }

        @Test
        void nullNonStaticClusterField_getsClusterAssigned() {
            extension.assignClusterToNonStaticFields(clusterTarget, clusterTarget.getClass(), STUB_CLUSTER);
            assertThat(clusterTarget.cluster).isSameAs(STUB_CLUSTER);
        }

        @Test
        void nonNullNonStaticClusterField_notOverwritten() {
            initializedClusterTarget.cluster = OTHER_CLUSTER;
            extension.assignClusterToNonStaticFields(
                    initializedClusterTarget, initializedClusterTarget.getClass(), STUB_CLUSTER);
            assertThat(initializedClusterTarget.cluster).isSameAs(OTHER_CLUSTER);
        }

        @Test
        void nullNonStaticPodField_matchedByFieldName_getsAssigned() {
            StubPod pod = new StubPod("myPod");
            registry.addPod("myPod", pod);
            extension.assignPodsToNonStaticFields(podTarget, podTarget.getClass(), registry);
            assertThat(podTarget.myPod).isSameAs(pod);
        }

        @Test
        void nullNonStaticPodField_matchedByAnnotationPodName_getsAssigned() {
            StubPod pod = new StubPod("custom-name");
            registry.addPod("custom-name", pod);
            extension.assignPodsToNonStaticFields(podNameTarget, podNameTarget.getClass(), registry);
            assertThat(podNameTarget.myPod).isSameAs(pod);
        }

        @Test
        void nonNullNonStaticPodField_notOverwritten() {
            StubPod existing = new StubPod("existing");
            initializedPodTarget.myPod = existing;
            registry.addPod("myPod", new StubPod("from-registry"));
            extension.assignPodsToNonStaticFields(initializedPodTarget, initializedPodTarget.getClass(), registry);
            assertThat(initializedPodTarget.myPod).isSameAs(existing);
        }

        @Test
        void emptyRegistry_nonStaticPodFieldRemainsNull() {
            extension.assignPodsToNonStaticFields(podTarget, podTarget.getClass(), registry);
            assertThat(podTarget.myPod).isNull();
        }

        @Test
        void noMatchingPodName_nonStaticFieldRemainsNull() {
            registry.addPod("other-name", new StubPod("other-name"));
            extension.assignPodsToNonStaticFields(podTarget, podTarget.getClass(), registry);
            assertThat(podTarget.myPod).isNull();
        }

        static class WithMismatchedPodType {
            @TestPod
            OtherStubPod myPod;
        }

        static class WithClusterAndPod {
            K8sCluster cluster;

            @TestPod
            StubPod myPod;
        }

        @Test
        void assignPodsToNonStaticFields_skipsField_whenTypeDoesNotMatch() {
            // Registry has a StubPod under name "myPod", but the field is typed OtherStubPod.
            // The extension should warn and leave the field as null rather than throw.
            registry.addPod("myPod", new StubPod("myPod"));
            WithMismatchedPodType instance = new WithMismatchedPodType();

            assertThatCode(() ->
                    extension.assignPodsToNonStaticFields(instance, instance.getClass(), registry))
                    .doesNotThrowAnyException();
            assertThat(instance.myPod).isNull();
        }

        @Test
        void beforeEach_path_doesNotThrow_whenRegistryClusterIsNull() {
            // Mirrors the null-guard in beforeEach: when registry.getCluster() is null,
            // the extension must not attempt cluster assignment and must not crash.
            // Verify the guard's precondition: registry has no cluster by default.
            assertThat(registry.getCluster()).isNull();

            // And verify that the pod-assignment path that runs unconditionally in beforeEach
            // does not throw when the registry has no cluster.
            assertThatCode(() ->
                    extension.assignPodsToNonStaticFields(podTarget, podTarget.getClass(), registry))
                    .doesNotThrowAnyException();
        }

        @Test
        void postProcessTestInstance_injectsIntoProvidedJUnitInstance() throws Exception {
            WithClusterAndPod instance = new WithClusterAndPod();
            StubPod pod = new StubPod("myPod");
            registry.setCluster(STUB_CLUSTER);
            registry.addPod("myPod", pod);

            extension.postProcessTestInstance(instance, contextFor(WithClusterAndPod.class));

            assertThat(instance.cluster).isSameAs(STUB_CLUSTER);
            assertThat(instance.myPod).isSameAs(pod);
        }
    }

    @Nested
    class PopulateRegistryNonStaticTests {

        @TestPods
        static class TestClassWithNonStaticPod {
            static int instantiations = 0;

            TestClassWithNonStaticPod() {
                instantiations++;
            }

            @RegisterCluster
            static K8sCluster cluster = STUB_CLUSTER;

            @TestPod
            StubPod kafkaPod = new StubPod("kafkaPod");
        }

        static class ProviderWithNonStaticPod {
            @TestPod
            StubPod providerPod = new StubPod("providerPod");
        }

        static class SecondProviderWithNonStaticPod {
            @TestPod
            StubPod secondProviderPod = new StubPod("secondProviderPod");
        }

        static class ProviderWithStaticCluster {
            @RegisterCluster
            static K8sCluster cluster = OTHER_CLUSTER;
        }

        static class ProviderWithNonStaticCluster {
            @RegisterCluster
            K8sCluster cluster = OTHER_CLUSTER;
        }

        @TestPods(testpodsProviders = {ProviderWithNonStaticPod.class})
        static class TestClassUsingProvider {
            @RegisterCluster
            static K8sCluster cluster = STUB_CLUSTER;
        }

        @TestPods(testpodsProviders = {
                ProviderWithNonStaticPod.class,
                SecondProviderWithNonStaticPod.class
        })
        static class TestClassUsingMultiplePodProviders {
            @RegisterCluster
            static K8sCluster cluster = STUB_CLUSTER;
        }

        @TestPods(testpodsProviders = {ProviderWithStaticCluster.class, ProviderWithNonStaticPod.class})
        static class TestClassUsingStaticProviderCluster {
        }

        @TestPods(testpodsProviders = {ProviderWithNonStaticCluster.class, ProviderWithNonStaticPod.class})
        static class TestClassUsingNonStaticProviderCluster {
        }

        @Test
        void nonStaticInitializedPodInTestClass_notRegisteredViaProbeInstance() throws Exception {
            TestClassWithNonStaticPod.instantiations = 0;
            extension.testPodsAnnotation = TestClassWithNonStaticPod.class.getAnnotation(TestPods.class);
            extension.populateAndValidateRegistry(TestClassWithNonStaticPod.class);
            var inits = getInitializations(extension.registry);
            assertThat(inits.values()).noneMatch(fi -> fi.podName().equals("kafkaPod"));
            assertThat(TestClassWithNonStaticPod.instantiations).isZero();
        }

        @Test
        void nonStaticInitializedPodInProvider_registeredWhenProviderScanned() throws Exception {
            extension.testPodsAnnotation = TestClassUsingProvider.class.getAnnotation(TestPods.class);
            extension.populateAndValidateRegistry(TestClassUsingProvider.class);
            var inits = getInitializations(extension.registry);
            assertThat(inits.values()).anyMatch(fi -> fi.podName().equals("providerPod"));
        }

        @Test
        void multipleProvidersWithNonStaticPods_allRegisteredWhenProviderScanned() throws Exception {
            extension.testPodsAnnotation = TestClassUsingMultiplePodProviders.class.getAnnotation(TestPods.class);
            extension.populateAndValidateRegistry(TestClassUsingMultiplePodProviders.class);

            var inits = getInitializations(extension.registry);

            assertThat(inits.values()).anyMatch(fi -> fi.podName().equals("providerPod"));
            assertThat(inits.values()).anyMatch(fi -> fi.podName().equals("secondProviderPod"));
        }

        @Test
        void staticClusterFromProvider_isUsedWhenTestClassHasNoCluster() {
            extension.testPodsAnnotation = TestClassUsingStaticProviderCluster.class.getAnnotation(TestPods.class);

            extension.populateAndValidateRegistry(TestClassUsingStaticProviderCluster.class);

            assertThat(extension.registry.getCluster()).isSameAs(OTHER_CLUSTER);
        }

        @Test
        void nonStaticClusterFromProvider_isUsedWhenTestClassHasNoCluster() {
            extension.testPodsAnnotation = TestClassUsingNonStaticProviderCluster.class.getAnnotation(TestPods.class);

            extension.populateAndValidateRegistry(TestClassUsingNonStaticProviderCluster.class);

            assertThat(extension.registry.getCluster()).isSameAs(OTHER_CLUSTER);
        }

        @Test
        void providerCanSupplyBothClusterAndTestPodConfiguration() throws Exception {
            extension.testPodsAnnotation = TestClassUsingStaticProviderCluster.class.getAnnotation(TestPods.class);

            extension.populateAndValidateRegistry(TestClassUsingStaticProviderCluster.class);

            var inits = getInitializations(extension.registry);
            assertThat(extension.registry.getCluster()).isSameAs(OTHER_CLUSTER);
            assertThat(inits.values()).anyMatch(fi -> fi.podName().equals("providerPod"));
        }
    }

    @Nested
    class TestPodLifecycleAttributeTests {

        static class WithDefaultLifecycle {
            @TestPod
            static StubPod myPod;
        }

        static class WithPerMethodLifecycle {
            @TestPod(lifecycle = TestPodLifecycle.PER_METHOD)
            static StubPod myPod;
        }

        @Test
        void testPodLifecycle_defaultsToPerClass() throws Exception {
            TestPod annotation = field(WithDefaultLifecycle.class, "myPod").getAnnotation(TestPod.class);

            assertThat(annotation.lifecycle()).isEqualTo(TestPodLifecycle.PER_CLASS);
        }

        @Test
        void testPodLifecycle_canBeSetToPerMethod() throws Exception {
            TestPod annotation = field(WithPerMethodLifecycle.class, "myPod").getAnnotation(TestPod.class);

            assertThat(annotation.lifecycle()).isEqualTo(TestPodLifecycle.PER_METHOD);
        }
    }
}
