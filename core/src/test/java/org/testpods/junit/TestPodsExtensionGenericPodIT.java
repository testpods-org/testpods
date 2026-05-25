package org.testpods.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testpods.core.ExecResult;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.pods.GenericPod;
import org.testpods.core.wait.WaitStrategy;

/**
 * Full minikube-backed integration test for the JUnit extension and a generic TestPod.
 *
 * <p>Run explicitly with:
 *
 * <pre>{@code
 * mvn verify -Dit.test=TestPodsExtensionGenericPodIT
 * }</pre>
 */
@TestPods
class TestPodsExtensionGenericPodIT {

  private static final int HTTP_PORT = 8080;
  private static final String RESPONSE_BODY = "testpods-extension-it";

  @RegisterCluster static K8sCluster cluster = K8sCluster.newMinikube().withNamespace();

  @TestPod
  static GenericPod httpServer =
      new GenericPod("busybox:1.36.1")
          .withName("generic-http-extension-it")
          .withPort(HTTP_PORT)
          .withHttpReadinessProbe("/", HTTP_PORT)
          .withCommand(
              "sh",
              "-c",
              "mkdir -p /www && printf '"
                  + RESPONSE_BODY
                  + "' > /www/index.html && httpd -f -p "
                  + HTTP_PORT
                  + " -h /www")
          .waitingFor(WaitStrategy.forHttp("/", HTTP_PORT).withTimeout(Duration.ofMinutes(3)));

  @Test
  void testPodsExtensionStartsGenericPodInMinikube() throws Exception {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(httpServer.getCluster()).isSameAs(cluster);
              assertThat(httpServer.getNamespace()).isNotNull();
              assertThat(httpServer.isRunning()).isTrue();
              assertThat(httpServer.isReady()).isTrue();
              assertThat(httpServer.getExternalHost()).isNotBlank();
              assertThat(httpServer.getExternalPort()).isPositive();
              assertThat(httpServer.getHost()).isEqualTo(httpServer.getExternalHost());
              assertThat(httpServer.getMappedPort(HTTP_PORT))
                  .isEqualTo(httpServer.getExternalPort());
              assertThat(httpServer.getExternalUrl())
                  .isEqualTo(
                      "http://"
                          + httpServer.getExternalHost()
                          + ":"
                          + httpServer.getExternalPort());
            });

    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertHttpServerResponds(httpServer));
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertHttpServerRespondsFromInsidePod(httpServer));
  }

  private static void assertHttpServerResponds(GenericPod httpServer) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(httpServer.getExternalUrl()))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(RESPONSE_BODY);
  }

  private static void assertHttpServerRespondsFromInsidePod(GenericPod httpServer) {
    ExecResult result =
        httpServer.exec(
            new String[] {
              "sh", "-c", "wget -q -O - http://127.0.0.1:" + HTTP_PORT + "/"
            });

    assertThat(result.exitCode()).as(result.getOutput()).isZero();
    assertThat(result.stdout()).isEqualTo(RESPONSE_BODY);
    assertThat(result.stderr()).isBlank();
  }
}
