package org.testpods.junit;

import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testpods.core.ExecResult;
import org.testpods.core.PropertyContext;
import org.testpods.core.cluster.ExternalAccessStrategy;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.Namespace;
import org.testpods.core.pods.Pod;
import org.testpods.core.pods.builders.InitContainerBuilder;
import org.testpods.core.pods.builders.SidecarBuilder;
import org.testpods.core.provisioning.Registry;
import org.testpods.core.wait.WaitStrategy;
import org.testpods.junit.RegisterCluster;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TestPodsExtensionAssignmentTest {

    static final K8sCluster STUB_CLUSTER = stubCluster();
    static final K8sCluster OTHER_CLUSTER = stubCluster();

    private static K8sCluster stubCluster() {
        return new K8sCluster() {
            @Override public KubernetesClient getClient() { return null; }
            @Override public ExternalAccessStrategy getAccessStrategy() { return null; }
            @Override public Namespace getDefaultNamespace() { return null; }
            @Override public Namespace getNamespace(String name) { return null; }
            @Override public Namespace createNamespace(String name) { return null; }
            @Override public Namespace createNamespace() { return null; }
            @Override public K8sCluster withNamespace() { return this; }
            @Override public void close() {}
        };
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

    @SuppressWarnings("unchecked")
    private static Map<String, org.testpods.core.provisioning.FieldInitialization> getInitializations(Registry registry)
            throws Exception {
        java.lang.reflect.Field f = Registry.class.getDeclaredField("testPodInitializationsByName");
        f.setAccessible(true);
        return (Map<String, org.testpods.core.provisioning.FieldInitialization>) f.get(registry);
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
            extension.assignClusterToTestClassField(OneNullField.class, STUB_CLUSTER);
            assertThat(OneNullField.cluster).isSameAs(STUB_CLUSTER);
        }

        @Test
        void initializedField_notOverwritten() {
            extension.assignClusterToTestClassField(OneInitializedField.class, STUB_CLUSTER);
            assertThat(OneInitializedField.cluster).isSameAs(OTHER_CLUSTER);
        }

        @Test
        void multipleNullFields_allGetAssigned() {
            extension.assignClusterToTestClassField(TwoNullFields.class, STUB_CLUSTER);
            assertThat(TwoNullFields.cluster1).isSameAs(STUB_CLUSTER);
            assertThat(TwoNullFields.cluster2).isSameAs(STUB_CLUSTER);
        }

        @Test
        void noK8sClusterField_doesNotThrow() {
            assertThatCode(() -> extension.assignClusterToTestClassField(NoK8sClusterField.class, STUB_CLUSTER))
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
    }

    @Nested
    class PopulateRegistryNonStaticTests {

        @TestPods
        static class TestClassWithNonStaticPod {
            @RegisterCluster
            static K8sCluster cluster = STUB_CLUSTER;

            @TestPod
            StubPod kafkaPod = new StubPod("kafkaPod");
        }

        static class ProviderWithNonStaticPod {
            @TestPod
            StubPod providerPod = new StubPod("providerPod");
        }

        @TestPods(testpodsProviders = {ProviderWithNonStaticPod.class})
        static class TestClassUsingProvider {
            @RegisterCluster
            static K8sCluster cluster = STUB_CLUSTER;
        }

        @Test
        void nonStaticInitializedPodInTestClass_registeredViaProbeInstance() throws Exception {
            extension.testPodsAnnotation = TestClassWithNonStaticPod.class.getAnnotation(TestPods.class);
            extension.populateAndValidateRegistry(TestClassWithNonStaticPod.class);
            var inits = getInitializations(extension.registry);
            assertThat(inits.values()).anyMatch(fi -> fi.podName().equals("kafkaPod"));
        }

        @Test
        void nonStaticInitializedPodInProvider_registeredWhenProviderScanned() throws Exception {
            extension.testPodsAnnotation = TestClassUsingProvider.class.getAnnotation(TestPods.class);
            extension.populateAndValidateRegistry(TestClassUsingProvider.class);
            var inits = getInitializations(extension.registry);
            assertThat(inits.values()).anyMatch(fi -> fi.podName().equals("providerPod"));
        }
    }
}
