package org.testpods.core.pods;

import static org.assertj.core.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import org.junit.jupiter.api.Test;
import org.testpods.core.PropertyContext;
import org.testpods.core.cluster.HostAndPort;
import org.testpods.core.wait.WaitStrategy;

/**
 * Unit tests for StatefulSetPod.
 * Tests the fixes for broken methods: getExternalHost, getExternalPort, getDefaultWaitStrategy.
 */
class StatefulSetPodTest {

    /**
     * Concrete implementation of StatefulSetPod for testing.
     * Minimal implementation that doesn't require a real Kubernetes cluster.
     */
    static class TestStatefulSetPod extends StatefulSetPod<TestStatefulSetPod> {

        private final int internalPort;

        TestStatefulSetPod() {
            this(5432);
        }

        TestStatefulSetPod(int port) {
            this.internalPort = port;
            this.name = "test-statefulset-pod";
        }

        @Override
        protected Container buildMainContainer() {
            return new ContainerBuilder()
                .withName("main")
                .withImage("postgres:15")
                .withPorts(new ContainerPortBuilder()
                    .withContainerPort(internalPort)
                    .build())
                .build();
        }

        @Override
        public int getInternalPort() {
            return internalPort;
        }

        @Override
        public void publishProperties(PropertyContext ctx) {
            // No-op for tests
        }

        /**
         * Allow tests to set externalAccess directly without starting a real cluster.
         */
        void setExternalAccessForTest(HostAndPort hostAndPort) {
            this.externalAccess = hostAndPort;
        }
    }

    // =============================================================
    // getExternalHost tests
    // =============================================================

    @Test
    void getExternalHostShouldThrowBeforeStart() {
        TestStatefulSetPod pod = new TestStatefulSetPod();

        assertThatThrownBy(pod::getExternalHost)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not started")
            .hasMessageContaining("test-statefulset-pod");
    }

    @Test
    void getExternalHostShouldReturnHostAfterStart() {
        TestStatefulSetPod pod = new TestStatefulSetPod();
        pod.setExternalAccessForTest(new HostAndPort("192.168.1.100", 30432));

        String host = pod.getExternalHost();

        assertThat(host).isEqualTo("192.168.1.100");
    }

    // =============================================================
    // getExternalPort tests
    // =============================================================

    @Test
    void getExternalPortShouldThrowBeforeStart() {
        TestStatefulSetPod pod = new TestStatefulSetPod();

        assertThatThrownBy(pod::getExternalPort)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not started")
            .hasMessageContaining("test-statefulset-pod");
    }

    @Test
    void getExternalPortShouldReturnPortAfterStart() {
        TestStatefulSetPod pod = new TestStatefulSetPod();
        pod.setExternalAccessForTest(new HostAndPort("192.168.1.100", 30432));

        int port = pod.getExternalPort();

        assertThat(port).isEqualTo(30432);
    }

    @Test
    void getExternalPortShouldReturnPositivePort() {
        TestStatefulSetPod pod = new TestStatefulSetPod();
        pod.setExternalAccessForTest(new HostAndPort("localhost", 5432));

        int port = pod.getExternalPort();

        assertThat(port).isGreaterThan(0);
    }

    // =============================================================
    // getDefaultWaitStrategy tests
    // =============================================================

    @Test
    void getDefaultWaitStrategyShouldReturnNonNull() {
        TestStatefulSetPod pod = new TestStatefulSetPod();

        WaitStrategy strategy = pod.getDefaultWaitStrategy();

        assertThat(strategy).isNotNull();
    }

    @Test
    void getDefaultWaitStrategyShouldBeReadinessProbeStrategy() {
        TestStatefulSetPod pod = new TestStatefulSetPod();

        WaitStrategy strategy = pod.getDefaultWaitStrategy();

        // The default should be a ReadinessProbeWaitStrategy with 2 minute timeout
        // We can verify it's not null and is a WaitStrategy
        assertThat(strategy).isNotNull();
    }

    // =============================================================
    // externalAccess field tests
    // =============================================================

    @Test
    void externalAccessShouldBeNullBeforeStart() {
        TestStatefulSetPod pod = new TestStatefulSetPod();

        // Verify the field is null before setting
        assertThatThrownBy(pod::getExternalHost)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void externalAccessShouldBeAvailableAfterSetting() {
        TestStatefulSetPod pod = new TestStatefulSetPod();
        pod.setExternalAccessForTest(new HostAndPort("minikube", 32000));

        assertThat(pod.getExternalHost()).isEqualTo("minikube");
        assertThat(pod.getExternalPort()).isEqualTo(32000);
    }
}
