package org.testpods.core.provisioning;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.junit.RegisterCluster;
import org.testpods.junit.TestPod;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectionHelperNonStaticTest {

    static class StubCluster implements K8sCluster {
        @Override public io.fabric8.kubernetes.client.KubernetesClient getClient() { return null; }
        @Override public org.testpods.core.cluster.ExternalAccessStrategy getAccessStrategy() { return null; }
        @Override public org.testpods.core.cluster.Namespace getDefaultNamespace() { return null; }
        @Override public org.testpods.core.cluster.Namespace getNamespace(String n) { return null; }
        @Override public org.testpods.core.cluster.Namespace createNamespace(String n) { return null; }
        @Override public org.testpods.core.cluster.Namespace createNamespace() { return null; }
        @Override public org.testpods.core.cluster.Namespace attachNamespace(String n) { return null; }
        @Override public void deleteNamespace(String n) {}
        @Override public K8sCluster withNamespace() { return this; }
        @Override public void close() {}
    }

    static class StubPod implements org.testpods.core.pods.Pod<StubPod> {
        private final String name;
        StubPod(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public StubPod withName(String n) { throw new UnsupportedOperationException(); }
        @Override public StubPod inNamespace(org.testpods.core.cluster.Namespace ns) { throw new UnsupportedOperationException(); }
        @Override public StubPod inNamespace(String ns) { throw new UnsupportedOperationException(); }
        @Override public StubPod inCluster(K8sCluster c) { throw new UnsupportedOperationException(); }
        @Override public StubPod withLabels(java.util.Map<String, String> l) { throw new UnsupportedOperationException(); }
        @Override public StubPod withAnnotations(java.util.Map<String, String> a) { throw new UnsupportedOperationException(); }
        @Override public StubPod withResources(String cpu, String mem) { throw new UnsupportedOperationException(); }
        @Override public StubPod withInitContainer(java.util.function.Consumer<org.testpods.core.pods.builders.InitContainerBuilder> c) { throw new UnsupportedOperationException(); }
        @Override public StubPod withSidecar(java.util.function.Consumer<org.testpods.core.pods.builders.SidecarBuilder> c) { throw new UnsupportedOperationException(); }
        @Override public StubPod withPodCustomizer(java.util.function.UnaryOperator<io.fabric8.kubernetes.api.model.PodSpecBuilder> c) { throw new UnsupportedOperationException(); }
        @Override public StubPod waitingFor(org.testpods.core.wait.WaitStrategy s) { throw new UnsupportedOperationException(); }
        @Override public void start() {}
        @Override public void stop() {}
        @Override public boolean isRunning() { return false; }
        @Override public boolean isReady() { return false; }
        @Override public String getLogs() { return null; }
        @Override public String getLogs(java.time.Duration d) { return null; }
        @Override public String getLogs(String c) { return null; }
        @Override public org.testpods.core.ExecResult exec(String... cmd) { return null; }
        @Override public org.testpods.core.ExecResult exec(String c, String... cmd) { return null; }
        @Override public org.testpods.core.cluster.Namespace getNamespace() { return null; }
        @Override public K8sCluster getCluster() { return null; }
        @Override public String getInternalHost() { return null; }
        @Override public int getInternalPort() { return 0; }
        @Override public String getExternalHost() { return null; }
        @Override public int getExternalPort() { return 0; }
        @Override public void publishProperties(org.testpods.core.PropertyContext ctx) {}
    }

    static class ProviderWithNonStaticPod {
        @TestPod
        StubPod myPod = new StubPod("myPod");
    }

    static class ProviderWithNonStaticCluster {
        @RegisterCluster
        K8sCluster cluster = new StubCluster();
    }

    static class ProviderWithStaticAndNonStaticPod {
        @TestPod
        static final StubPod staticPod = new StubPod("staticPod");
        @TestPod
        StubPod instancePod = new StubPod("instancePod");
    }

    static class ProviderWithNoArgConstructor {
        @TestPod
        StubPod fromProvider = new StubPod("fromProvider");
    }

    static class ProviderWithoutNoArgConstructor {
        @SuppressWarnings("unused")
        private final String required;
        ProviderWithoutNoArgConstructor(String required) { this.required = required; }
        @TestPod
        StubPod pod = new StubPod("unreachable");
    }

    @Nested
    class ScanClassForTestPodInitializationsOnlyWithInstance {

        @Test
        void nonStaticInitializedField_foundWhenInstanceProvided() {
            Object instance = new ProviderWithNonStaticPod();
            var result = ReflectionHelper.scanClassForTestPodInitializationsOnly(
                    ProviderWithNonStaticPod.class, instance);
            assertThat(result).hasSize(1);
            assertThat(result.values().iterator().next().podName()).isEqualTo("myPod");
        }

        @Test
        void nonStaticInitializedField_notFoundWhenInstanceNull() {
            var result = ReflectionHelper.scanClassForTestPodInitializationsOnly(
                    ProviderWithNonStaticPod.class, null);
            assertThat(result).isEmpty();
        }

        @Test
        void withInstance_onlyNonStaticFieldsReturned() {
            Object instance = new ProviderWithStaticAndNonStaticPod();
            var result = ReflectionHelper.scanClassForTestPodInitializationsOnly(
                    ProviderWithStaticAndNonStaticPod.class, instance);
            assertThat(result).hasSize(1);
            assertThat(result.values().iterator().next().podName()).isEqualTo("instancePod");
        }

        @Test
        void withNullInstance_onlyStaticFieldsReturned() {
            var result = ReflectionHelper.scanClassForTestPodInitializationsOnly(
                    ProviderWithStaticAndNonStaticPod.class, null);
            assertThat(result).hasSize(1);
            assertThat(result.values().iterator().next().podName()).isEqualTo("staticPod");
        }
    }

    @Nested
    class ScanClassForClusterRegistrationWithInstance {

        @Test
        void nonStaticClusterField_foundWhenInstanceProvided() {
            Object instance = new ProviderWithNonStaticCluster();
            K8sCluster cluster = ReflectionHelper.scanClassForClusterRegistration(
                    ProviderWithNonStaticCluster.class, instance);
            assertThat(cluster).isNotNull().isInstanceOf(StubCluster.class);
        }

        @Test
        void nonStaticClusterField_notFoundWhenInstanceNull() {
            K8sCluster cluster = ReflectionHelper.scanClassForClusterRegistration(
                    ProviderWithNonStaticCluster.class, null);
            assertThat(cluster).isNull();
        }
    }

    @Nested
    class ScanTestPodsProvidersForAllTestPodInitializers {

        @Test
        void nonStaticFieldsInProvider_areIncluded() {
            var result = ReflectionHelper.scanTestPodsProvidersForAllTestPodInitializers(
                    new Class<?>[] { ProviderWithNoArgConstructor.class });
            assertThat(result).hasSize(1);
            assertThat(result.values().iterator().next().podName()).isEqualTo("fromProvider");
        }

        @Test
        void providerWithoutNoArgConstructor_gracefullySkipped() {
            var result = ReflectionHelper.scanTestPodsProvidersForAllTestPodInitializers(
                    new Class<?>[] { ProviderWithoutNoArgConstructor.class });
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class ScanTestPodsProvidersForClusterRegistration {

        @Test
        void nonStaticClusterFieldInProvider_isFound() {
            K8sCluster cluster = ReflectionHelper.scanTestPodsProvidersForClusterRegistration(
                    new Class<?>[] { ProviderWithNonStaticCluster.class });
            assertThat(cluster).isNotNull().isInstanceOf(StubCluster.class);
        }

        @Test
        void providerWithNoCluster_returnsNull() {
            K8sCluster cluster = ReflectionHelper.scanTestPodsProvidersForClusterRegistration(
                    new Class<?>[] { ProviderWithNonStaticPod.class });
            assertThat(cluster).isNull();
        }
    }

    @Nested
    class TryInstantiate {

        @Test
        void classWithNoArgConstructor_returnsNonNullInstance() {
            Object instance = ReflectionHelper.tryInstantiate(ProviderWithNoArgConstructor.class);
            assertThat(instance).isNotNull().isInstanceOf(ProviderWithNoArgConstructor.class);
        }

        @Test
        void classWithoutNoArgConstructor_returnsNull() {
            Object instance = ReflectionHelper.tryInstantiate(ProviderWithoutNoArgConstructor.class);
            assertThat(instance).isNull();
        }
    }

    /**
     * Provider whose no-arg constructor increments a static counter, so tests can verify
     * how many times the constructor is invoked during a single scanning pass.
     */
    static class CountingProvider {
        static int instantiations = 0;

        CountingProvider() {
            instantiations++;
        }

        @TestPod
        StubPod countedPod = new StubPod("countedPod");
    }

    @Nested
    class ProviderInstantiationCount {

        @org.junit.jupiter.api.BeforeEach
        void resetCounter() {
            CountingProvider.instantiations = 0;
        }

        @Test
        void scanTestPodsProviderForTestPodInitializations_doesNotInstantiateProvider() {
            // The package-private helper expects callers to supply the instance themselves;
            // it must not invoke the provider's no-arg constructor.
            Object instance = ReflectionHelper.tryInstantiate(CountingProvider.class);
            int afterInstantiate = CountingProvider.instantiations;

            ReflectionHelper.scanTestPodsProviderForTestPodInitializations(CountingProvider.class, instance);

            assertThat(CountingProvider.instantiations).isEqualTo(afterInstantiate);
        }

        @Test
        void scanTestPodsProvidersForAllTestPodInitializers_withInstancesMap_doesNotInstantiate() {
            // When the caller passes pre-created instances, no additional constructor calls
            // should occur, regardless of how many scanning methods consume the map.
            Object instance = ReflectionHelper.tryInstantiate(CountingProvider.class);
            int afterInstantiate = CountingProvider.instantiations;
            java.util.Map<Class<?>, Object> instances = new java.util.HashMap<>();
            instances.put(CountingProvider.class, instance);

            ReflectionHelper.scanTestPodsProvidersForAllTestPodInitializers(
                    new Class<?>[] { CountingProvider.class }, instances);
            ReflectionHelper.scanTestPodsProvidersForClusterRegistration(
                    new Class<?>[] { CountingProvider.class }, instances);

            assertThat(CountingProvider.instantiations).isEqualTo(afterInstantiate);
        }

        @Test
        void singleArgOverload_instantiatesProviderExactlyOnce() {
            // The single-argument overload must internally instantiate each provider only once,
            // even though both static and non-static scans run against it.
            ReflectionHelper.scanTestPodsProvidersForAllTestPodInitializers(
                    new Class<?>[] { CountingProvider.class });

            assertThat(CountingProvider.instantiations).isEqualTo(1);
        }
    }
}
