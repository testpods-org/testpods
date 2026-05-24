package org.testpods.core.pods.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.mockwebserver.Context;
import java.util.HashMap;
import okhttp3.mockwebserver.MockWebServer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testpods.core.cluster.ExternalAccessStrategy;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.Namespace;

/**
 * Unit tests for {@link LocalServicePod}.
 *
 * <p>Uses Fabric8's {@link KubernetesMockServer} to validate the Kubernetes resources that {@code
 * LocalServicePod#start()} creates against a fake API server.
 */
class LocalServicePodTest {

  private static final String POD_NAME = "product-service";
  private static final String NAMESPACE_NAME = "testpods-local";

  private KubernetesMockServer mockServer;
  private KubernetesClient client;
  private K8sCluster cluster;
  private Namespace namespace;

  @BeforeEach
  void setUp() {
    // CRUD mode (KubernetesMixedDispatcher) lets the mock server respond to create/get/
    // delete requests like a real API server, which LocalServicePod requires.
    HashMap<io.fabric8.mockwebserver.ServerRequest, java.util.Queue<io.fabric8.mockwebserver.ServerResponse>>
        responses = new HashMap<>();
    mockServer =
        new KubernetesMockServer(
            new Context(Serialization.jsonMapper()),
            new MockWebServer(),
            responses,
            new KubernetesMixedDispatcher(responses),
            true);
    mockServer.init();
    client = mockServer.createClient();

    namespace =
        Namespace.owned(
            NAMESPACE_NAME,
            new NamespaceBuilder()
                .withNewMetadata()
                .withName(NAMESPACE_NAME)
                .endMetadata()
                .withNewStatus()
                .withPhase("Active")
                .endStatus()
                .build());

    cluster = new StubCluster(client, namespace);
  }

  @AfterEach
  void tearDown() {
    if (client != null) {
      client.close();
    }
    if (mockServer != null) {
      mockServer.destroy();
    }
  }

  @Test
  void start_createsServiceAndEndpointsPointingAtHost() throws Exception {
    LocalServicePod pod =
        new LocalServicePod(POD_NAME).onHostPort(8082).exposingServicePort(8082).inCluster(cluster);

    pod.start();

    try {
      Service service =
          client.services().inNamespace(NAMESPACE_NAME).withName(POD_NAME).get();
      assertThat(service).isNotNull();
      assertThat(service.getSpec().getType()).isEqualTo("ClusterIP");
      // No selector — Endpoints are managed manually.
      assertThat(service.getSpec().getSelector()).isNullOrEmpty();
      assertThat(service.getSpec().getPorts()).hasSize(1);
      assertThat(service.getSpec().getPorts().get(0).getPort()).isEqualTo(8082);

      Endpoints endpoints =
          client.endpoints().inNamespace(NAMESPACE_NAME).withName(POD_NAME).get();
      assertThat(endpoints).isNotNull();
      assertThat(endpoints.getSubsets()).hasSize(1);
      EndpointSubset subset = endpoints.getSubsets().get(0);
      assertThat(subset.getAddresses()).isNotEmpty();
      String address = subset.getAddresses().get(0).getIp();
      assertThat(address).isNotBlank();
      assertThat(isValidIpAddress(address))
          .as("endpoint address %s must parse as a valid IPv4/IPv6 literal", address)
          .isTrue();
      assertThat(subset.getPorts()).extracting("port").contains(8082);
    } finally {
      pod.stop();
    }
  }

  @Test
  void stop_removesServiceAndEndpoints() {
    LocalServicePod pod =
        new LocalServicePod(POD_NAME).onHostPort(8082).exposingServicePort(8082).inCluster(cluster);

    pod.start();
    pod.stop();

    Service service = client.services().inNamespace(NAMESPACE_NAME).withName(POD_NAME).get();
    Endpoints endpoints = client.endpoints().inNamespace(NAMESPACE_NAME).withName(POD_NAME).get();
    assertThat(service).isNull();
    assertThat(endpoints).isNull();
  }

  @Test
  void internalHostAndPort_useNamespaceAndExposedPort() {
    LocalServicePod pod =
        new LocalServicePod(POD_NAME).onHostPort(8082).exposingServicePort(8082).inCluster(cluster);

    pod.start();
    try {
      assertThat(pod.getInternalHost())
          .isEqualTo(POD_NAME + "." + NAMESPACE_NAME + ".svc.cluster.local");
      assertThat(pod.getInternalPort()).isEqualTo(8082);
      assertThat(pod.getExternalHost()).isIn("localhost", "127.0.0.1");
      assertThat(pod.getExternalPort()).isEqualTo(8082);
    } finally {
      pod.stop();
    }
  }

  @Test
  void fluentSetters_returnSameInstanceForChaining() {
    LocalServicePod pod = new LocalServicePod(POD_NAME);
    assertThat(pod.onHostPort(8082)).isSameAs(pod);
    assertThat(pod.exposingServicePort(8082)).isSameAs(pod);
    assertThat(pod.inCluster(cluster)).isSameAs(pod);
    assertThat(pod.inNamespace(namespace)).isSameAs(pod);
    assertThat(pod.withName("renamed")).isSameAs(pod);
  }

  @Test
  void runningAndReady_reflectLifecycle() {
    LocalServicePod pod =
        new LocalServicePod(POD_NAME).onHostPort(8082).exposingServicePort(8082).inCluster(cluster);

    assertThat(pod.isRunning()).isFalse();
    assertThat(pod.isReady()).isFalse();

    pod.start();
    try {
      assertThat(pod.isRunning()).isTrue();
      assertThat(pod.isReady()).isTrue();
    } finally {
      pod.stop();
    }

    assertThat(pod.isRunning()).isFalse();
    assertThat(pod.isReady()).isFalse();
  }

  @Test
  void getLogsAndExec_areUnsupported() {
    LocalServicePod pod =
        new LocalServicePod(POD_NAME).onHostPort(8082).exposingServicePort(8082).inCluster(cluster);

    assertThatThrownBy(() -> pod.getLogs())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> pod.exec(new String[] {"ls"}))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static boolean isValidIpAddress(String s) {
    try {
      InetAddress addr = InetAddress.getByName(s);
      // getByName accepts hostnames too — make sure the input was a literal IP by
      // checking it round-trips to the same string.
      return addr.getHostAddress().equals(s);
    } catch (UnknownHostException e) {
      return false;
    }
  }

  /**
   * Minimal {@link K8sCluster} stub backed by the mock-server-provided client and a fixed
   * namespace. Only the methods exercised by {@link LocalServicePod} are implemented.
   */
  private static final class StubCluster implements K8sCluster {
    private final KubernetesClient client;
    private final Namespace namespace;

    StubCluster(KubernetesClient client, Namespace namespace) {
      this.client = client;
      this.namespace = namespace;
    }

    @Override
    public KubernetesClient getClient() {
      return client;
    }

    @Override
    public ExternalAccessStrategy getAccessStrategy() {
      return null;
    }

    @Override
    public Namespace getDefaultNamespace() {
      return namespace;
    }

    @Override
    public Namespace getNamespace(String name) {
      return namespace.getName().equals(name) ? namespace : null;
    }

    @Override
    public Namespace createNamespace(String name) {
      return namespace;
    }

    @Override
    public Namespace createNamespace() {
      return namespace;
    }

    @Override
    public Namespace attachNamespace(String name) {
      return namespace;
    }

    @Override
    public void deleteNamespace(String name) {}

    @Override
    public K8sCluster withNamespace() {
      return this;
    }

    @Override
    public void close() {}
  }
}
