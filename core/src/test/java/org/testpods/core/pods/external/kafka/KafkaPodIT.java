package org.testpods.core.pods.external.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Test;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.MinikubeCluster;
import org.testpods.junit.RegisterCluster;
import org.testpods.junit.TestPods;

/**
 * Integration tests proving KafkaPod starts and exposes Kafka plus its Redpanda Console UI.
 */
@TestPods(deleteProfileAfterTests = false)
@Slf4j
public class KafkaPodIT {

    private static final boolean HOLD_PODS_OPEN = true;

    @RegisterCluster()
    static K8sCluster cluster;

    @Test
    void startsKafkaAndExposesBrokerAndRedpandaUi() throws Exception {
        KafkaPod kafka = new KafkaPod().withName("kafka-it").withTopics("orders-it").withUi();
        try {
            kafka.start();
            assertThat(kafka.getCluster()).isSameAs(cluster);
            printDebugEndpoints(kafka);

            await()
                    .atMost(Duration.ofSeconds(30))
                    .untilAsserted(
                            () -> {
                                assertThat(kafka.isRunning()).isTrue();
                                assertThat(kafka.isReady()).isTrue();
                            });
            assertThat(kafka.getBootstrapServers())
                    .isEqualTo("127.0.0.1:" + KafkaPod.DEFAULT_EXTERNAL_PORT);
            await()
                    .atMost(Duration.ofSeconds(60))
                    .untilAsserted(() -> assertKafkaTopicVisibleFromTestJvm(kafka, "orders-it"));
            await()
                    .atMost(Duration.ofSeconds(60))
                    .untilAsserted(() -> assertRedpandaConsoleReachable(kafka));
            holdOpenForDebugIfRequested(kafka);
        } finally {
            kafka.stop();
        }
    }

    private static void printDebugEndpoints(KafkaPod kafka) {
        log.info("Kafka bootstrap servers: {}", kafka.getBootstrapServers());
        log.info("Kafka UI: {}", kafka.getUiUrl());
        log.info("Kafka namespace: {}", kafka.getNamespace().getName());
        log.info("Kafka service: kubectl --context {} -n {} get service kafka-it -o wide", ((MinikubeCluster) kafka.getCluster()).getProfileName(), kafka.getNamespace().getName());
    }

    private static void holdOpenForDebugIfRequested(KafkaPod kafka) throws InterruptedException {
        if (!HOLD_PODS_OPEN) {
            return;
        }
        log.info("KafkaPodIT is holding pods open. Browser UI: {}. Stop the test process to clean up.", kafka.getUiUrl());
        new CountDownLatch(1).await();
    }

    private static void assertKafkaTopicVisibleFromTestJvm(KafkaPod kafka, String topic)
            throws Exception {
        Map<String, Object> config =
                Map.of(
                        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                        kafka.getBootstrapServers(),
                        AdminClientConfig.CLIENT_ID_CONFIG,
                        "testpods-kafkapod-it",
                        AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG,
                        "10000",
                        AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG,
                        "20000");

        try (AdminClient admin = AdminClient.create(config)) {
            Set<String> topics = admin.listTopics().names().get(30, TimeUnit.SECONDS);
            assertThat(topics).contains(topic);
        }
    }

    private static void assertRedpandaConsoleReachable(KafkaPod kafka) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(kafka.getUiUrl()))
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isBetween(200, 399);
        assertThat(response.body()).containsIgnoringCase("redpanda");
    }
}
