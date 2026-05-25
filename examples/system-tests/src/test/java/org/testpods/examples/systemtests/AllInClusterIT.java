package org.testpods.examples.systemtests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.pods.GenericPod;
import org.testpods.core.pods.external.kafka.KafkaPod;
import org.testpods.core.pods.external.postgresql.PostgreSQLPod;
import org.testpods.junit.RegisterCluster;
import org.testpods.junit.TestPod;
import org.testpods.junit.TestPods;

@TestPods
class AllInClusterIT {

  private static final String ORDER_IMAGE = "examples/order-service:test-current";
  private static final String PRODUCT_IMAGE = "examples/product-service:test-current";

//  @RegisterCluster static K8sCluster cluster;

  @TestPod
  static PostgreSQLPod orderDb =
      new PostgreSQLPod()
          .withName("orderdb")
          .withDatabase("orderdb")
          .withUsername("order_user")
          .withPassword("order_pass")
          .withFixedExposedPort(5432, 5432);

  @TestPod
  static PostgreSQLPod productDb =
      new PostgreSQLPod()
          .withName("productdb")
          .withDatabase("productdb")
          .withUsername("product_user")
          .withPassword("product_pass")
          .withFixedExposedPort(5433, 5432);

  @TestPod
  static KafkaPod kafka =
      new KafkaPod().withName("kafka").withTopics("order-events").withExternalPort(9092);

  @TestPod
  static GenericPod productService =
      GenericPod.fromLocalImage(PRODUCT_IMAGE)
          .withName("product-service")
          .withPort(8082)
          .withExposedPorts(8082)
          .withEnv("SPRING_DATASOURCE_URL", "${productdb.internal.uri}")
          .withEnv("SPRING_DATASOURCE_USERNAME", "${productdb.username}")
          .withEnv("SPRING_DATASOURCE_PASSWORD", "${productdb.password}")
          .withEnv("SPRING_KAFKA_BOOTSTRAP_SERVERS", "${kafka.internal.bootstrapServers}");

  @TestPod
  static GenericPod orderService =
      GenericPod.fromLocalImage(ORDER_IMAGE)
          .withName("order-service")
          .withPort(8081)
          .withExposedPorts(8081)
          .withEnv("SPRING_DATASOURCE_URL", "${orderdb.internal.uri}")
          .withEnv("SPRING_DATASOURCE_USERNAME", "${orderdb.username}")
          .withEnv("SPRING_DATASOURCE_PASSWORD", "${orderdb.password}")
          .withEnv("SPRING_KAFKA_BOOTSTRAP_SERVERS", "${kafka.internal.bootstrapServers}")
          .withEnv("PRODUCT_SERVICE_BASE_URL", "${product-service.internal.url}");

  @Test
  void placeOrderAndDecrementStock() {
    RestClient productClient = restClient(productService, 8082);
    RestClient orderClient = restClient(orderService, 8081);

    ResponseEntity<ProductResponse> productResponse =
        productClient
            .post()
            .uri("/products")
            .body(Map.of("name", "TestPods mug", "stockCount", 10))
            .retrieve()
            .toEntity(ProductResponse.class);

    assertThat(productResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    ProductResponse product = productResponse.getBody();
    assertThat(product).isNotNull();

    ResponseEntity<OrderResponse> orderResponse =
        orderClient
            .post()
            .uri("/orders")
            .body(Map.of("productId", product.id(), "quantity", 3))
            .retrieve()
            .toEntity(OrderResponse.class);

    assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        productClient
                            .get()
                            .uri("/products/{id}", product.id())
                            .retrieve()
                            .body(ProductResponse.class)
                            .stockCount())
                    .isEqualTo(7));
  }

  private static RestClient restClient(GenericPod pod, int port) {
    return RestClient.create("http://" + pod.getHost() + ":" + pod.getMappedPort(port));
  }

  record ProductResponse(UUID id, String name, Integer stockCount, Instant createdAt) {}

  record OrderResponse(UUID id, UUID productId, Integer quantity, String status, Instant createdAt) {}
}
