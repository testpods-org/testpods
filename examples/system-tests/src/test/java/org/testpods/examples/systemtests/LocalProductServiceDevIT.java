package org.testpods.examples.systemtests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.pods.GenericPod;
import org.testpods.core.pods.external.kafka.KafkaPod;
import org.testpods.core.pods.external.postgresql.PostgreSQLPod;
import org.testpods.core.pods.local.LocalServicePod;
import org.testpods.junit.RegisterCluster;
import org.testpods.junit.TestPod;
import org.testpods.junit.TestPods;

@TestPods
class LocalProductServiceDevIT {

  private static final Logger log = LoggerFactory.getLogger(LocalProductServiceDevIT.class);
  private static final String ORDER_IMAGE = "examples/order-service:test-current";

  @RegisterCluster static K8sCluster cluster = K8sCluster.newMinikube().withNamespace();

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

  // Uncomment this and remove the LocalServicePod below to run product-service in cluster.
  // @TestPod
  // static GenericPod productService =
  //     GenericPod.fromLocalImage("examples/product-service:test-current")
  //         .withName("product-service")
  //         .withPort(8082)
  //         .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://productdb:5432/productdb")
  //         .withEnv("SPRING_DATASOURCE_USERNAME", "product_user")
  //         .withEnv("SPRING_DATASOURCE_PASSWORD", "product_pass")
  //         .withEnv("SPRING_KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");

  @TestPod
  static LocalServicePod productService =
      new LocalServicePod("product-service").onHostPort(8082).exposingServicePort(8082);

  @TestPod
  static GenericPod orderService =
      GenericPod.fromLocalImage(ORDER_IMAGE)
          .withName("order-service")
          .withPort(8081)
          .withExposedPorts(8081)
          .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://orderdb:5432/orderdb")
          .withEnv("SPRING_DATASOURCE_USERNAME", "order_user")
          .withEnv("SPRING_DATASOURCE_PASSWORD", "order_pass")
          .withEnv("SPRING_KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
          .withEnv("PRODUCT_SERVICE_BASE_URL", "http://product-service:8082");

  @Test
  void placeOrderConsumedByLocalProductService() {
    log.info(
        "BREAKPOINT ANCHOR - cluster ready. Kafka=localhost:9092 "
            + "productdb=jdbc:postgresql://localhost:5433/productdb "
            + "orderService=http://localhost:{}",
        orderService.getMappedPort(8081));

    RestClient productClient = RestClient.create("http://localhost:8082");
    RestClient orderClient =
        RestClient.create(
            "http://" + orderService.getHost() + ":" + orderService.getMappedPort(8081));

    ResponseEntity<ProductResponse> productResponse =
        productClient
            .post()
            .uri("/products")
            .body(Map.of("name", "Debuggable TestPods mug", "stockCount", 10))
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
        .untilAsserted(() -> assertThat(stockCount(product.id())).isEqualTo(7));
  }

  private static int stockCount(UUID productId) throws Exception {
    try (var connection =
            DriverManager.getConnection(
                "jdbc:postgresql://localhost:5433/productdb", "product_user", "product_pass");
        var statement =
            connection.prepareStatement("select stock_count from products where id = ?")) {
      statement.setObject(1, productId);
      try (var resultSet = statement.executeQuery()) {
        assertThat(resultSet.next()).isTrue();
        return resultSet.getInt(1);
      }
    }
  }

  record ProductResponse(UUID id, String name, Integer stockCount, Instant createdAt) {}

  record OrderResponse(UUID id, UUID productId, Integer quantity, String status, Instant createdAt) {}
}
