package org.testpods.core.pods.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.testpods.core.cluster.ExternalAccessStrategy;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.Namespace;

class LocalServicePodTest {

  private static final String POD_NAME = "product-service";
  private static final String NAMESPACE_NAME = "testpods-local";
  private final Namespace namespace =
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
  private final K8sCluster cluster = new StubCluster(namespace);

  @Test
  void start_createsServiceAndEndpointsPointingAtHost() {
    RecordingGateway gateway = new RecordingGateway();
    LocalServicePod pod = newPod(gateway);

    pod.start();

    try {
      Service service = gateway.service;
      assertThat(service).isNotNull();
      assertThat(service.getMetadata().getName()).isEqualTo(POD_NAME);
      assertThat(service.getMetadata().getNamespace()).isEqualTo(NAMESPACE_NAME);
      assertThat(service.getSpec().getType()).isEqualTo("ClusterIP");
      assertThat(service.getSpec().getSelector()).isNullOrEmpty();
      assertThat(service.getSpec().getPorts()).hasSize(1);
      assertThat(service.getSpec().getPorts().get(0).getPort()).isEqualTo(8082);
      assertThat(service.getSpec().getPorts().get(0).getTargetPort().getIntVal()).isEqualTo(8082);

      Endpoints endpoints = gateway.endpoints;
      assertThat(endpoints).isNotNull();
      assertThat(endpoints.getMetadata().getName()).isEqualTo(POD_NAME);
      assertThat(endpoints.getMetadata().getNamespace()).isEqualTo(NAMESPACE_NAME);
      assertThat(endpoints.getSubsets()).hasSize(1);
      String address = endpoints.getSubsets().get(0).getAddresses().get(0).getIp();
      assertThat(address).isNotBlank();
      assertThat(isValidIpAddress(address))
          .as("endpoint address %s must parse as a valid IPv4/IPv6 literal", address)
          .isTrue();
      assertThat(endpoints.getSubsets().get(0).getPorts()).extracting("port").contains(8082);
    } finally {
      pod.stop();
    }
  }

  @Test
  void stop_removesServiceAndEndpoints() {
    RecordingGateway gateway = new RecordingGateway();
    LocalServicePod pod = newPod(gateway);

    pod.start();
    pod.stop();

    assertThat(gateway.deletedEndpoints).isEqualTo(NAMESPACE_NAME + "/" + POD_NAME);
    assertThat(gateway.deletedService).isEqualTo(NAMESPACE_NAME + "/" + POD_NAME);
  }

  @Test
  void internalHostAndPort_useNamespaceAndExposedPort() {
    LocalServicePod pod = newPod(new RecordingGateway());

    pod.start();
    try {
      assertThat(pod.getInternalHost())
          .isEqualTo(POD_NAME + "." + NAMESPACE_NAME + ".svc.cluster.local");
      assertThat(pod.getInternalPort()).isEqualTo(8082);
      assertThat(pod.getExternalHost()).isEqualTo("localhost");
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
    LocalServicePod pod = newPod(new RecordingGateway());

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
    LocalServicePod pod = newPod(new RecordingGateway());

    assertThatThrownBy(pod::getLogs).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> pod.exec(new String[] {"ls"}))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private LocalServicePod newPod(RecordingGateway gateway) {
    return new LocalServicePod(POD_NAME)
        .onHostPort(8082)
        .exposingServicePort(8082)
        .inCluster(cluster)
        .withGatewayForTests(gateway);
  }

  private static boolean isValidIpAddress(String s) {
    try {
      InetAddress addr = InetAddress.getByName(s);
      return addr.getHostAddress().equals(s);
    } catch (UnknownHostException e) {
      return false;
    }
  }

  private static final class RecordingGateway implements LocalServicePod.KubernetesGateway {
    private Service service;
    private Endpoints endpoints;
    private String deletedService;
    private String deletedEndpoints;

    @Override
    public Service createService(String namespace, Service service) {
      this.service = service;
      return service;
    }

    @Override
    public Endpoints createEndpoints(String namespace, Endpoints endpoints) {
      this.endpoints = endpoints;
      return endpoints;
    }

    @Override
    public void deleteService(String namespace, String name) {
      deletedService = namespace + "/" + name;
    }

    @Override
    public void deleteEndpoints(String namespace, String name) {
      deletedEndpoints = namespace + "/" + name;
    }
  }

  private static final class StubCluster implements K8sCluster {
    private final Namespace namespace;

    private StubCluster(Namespace namespace) {
      this.namespace = namespace;
    }

    @Override
    public KubernetesClient getClient() {
      throw new UnsupportedOperationException();
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
