# TestPods - Testcontainers for K8s

## Background & Motivation

* Use cases with TestContainers
    * Infrastructure - first test and then development - shared infrastructure into a test-util lib.
    * Services - driven by development - when working on a service, then having one or two other services running. Maintaining mocks takes time.
    * System E2E test - slices with specific data flows.

```java
@Testcontainer
class JunitTestWithContainersIT {
    
    @Container
    public GenericContainer redis = new GenericContainer(DockerImageName.parse("redis:6-alpine"))
            .withExposedPorts(6379);
    
    @Container
    KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");
}

@TestInfrastructure("basicInfrastructure")
static InfrastructureBuilder infrastructureBuilder = InfrastructureBuilder
            .withInfrastructureName("basicInfrastructure")
            .withMongoDb("orders")
            .withKafka(new TopicDefinition("processed-orders", partitions, replicas))
            .withRabbit("order-requests");
            //QueueDefinition(String queueName, String exchangeName, String dlQueueName, String dlExchangeName)

static ServicesBuilder orderServicesSliceBuilder = of("orderServicesSlice", snapshotServiceDescriptions)
        .withInfrastructure(infrastructureBuilder)
        .withOrderService()
        .withProductService();

// In test class
@TestWithInfrastructure("basicInfrastructure")
class OrderServicesIT {
    
    static Services orderServices = orderServicesSliceBuilder.build();
    // Inject the running infrastructure into the
    Infrastructure infrastructure;
}

//JUnit extension to process test classes with @TestWithInfrastructure and build, configure and start infrastructere containers in Docker Desktop
class TestWithInfrastructureExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback  {
    
}
```

## Project structure

* Core library
* BOM - dependency management
* Modules
  * Kafka
  * MongoDB
  * PostgreSQL
* Examples - Spring Boot
  * Order service
  * Product service
  * E2E System tests

## [TestPod Annotations](core/src/main/java/org/testpods/junit)
```java

@TestPods
class JUnitWithTestPodsIT {

  @RegisterCluster
  static K8sCluster cluster;

  @TestPod(podName = "orderDB")
MongoDBPod mongo = new MongoDBPod()
    .withDatabase("Orders")
    .withInitContainer(init -> init
        .withName("permission-fix")
        .withImage("busybox:latest")
        .withCommand("sh", "-c", "chmod -R 777 /data/db"))
    .withSidecar(sidecar -> sidecar
        .withName("metrics-exporter")
        .withImage("bitnami/mongodb-exporter:latest")
        .withPort(9216));

}
//JUnit extension to process test classes with @TestPods class and use @RegisterCluster and @TestPod to construct a K8s cluster with dedicated namespace and provision the TestPods via
class TestPodsExtension {}  
```
  
### [TestPodsExtension](core/src/main/java/org/testpods/junit/TestPodsExtension.java)
  

### Test pod classes

```
<interface>
   Pod
    │
    ▼
<abstract>
BaseManagedPod
    │
    ├───────────────────────────────┬
    │                               │
    ▼                               ▼
<abstract>                       <abstract>
StatefulSetPod                   DeploymentPod
    │                                   │
    ├─────────┬                         ├─────────┬
    ▼         ▼                         ▼         ▼
MongoDBPod  GenericStatefulPod     ServicePod  GenericPod
```

### [KafkaPod IT](core/src/test/java/org/testpods/core/pods/external/kafka/KafkaPodIT.java)

### Example System-tests
  * [AllInClusterIT](examples/system-tests/src/test/java/org/testpods/examples/systemtests/AllInClusterIT.java)
  * [LocalProductServiceDevIT](examples/system-tests/src/test/java/org/testpods/examples/systemtests/LocalProductServiceDevIT.java)

### Future Annotations
  * [TestPodsExtensionTest](core/src/test/java/org/testpods/junit/TestPodsExtensionTest.java)
  * Composability and reuse

```java
interface OrderSystemCatalog extends TestPodCatalog<OrderSystemCatalog> {
      OrderSystemCatalog kafka();
      OrderSystemCatalog postgres();
      OrderSystemCatalog orderService();
      OrderSystemCatalog productService();

      default TestPodGroup infrastructure() {
          return kafka().postgres().asGroup("infrastructure");
      }

      default TestPodGroup services() {
          return orderService().productService().asGroup("services");
      }

  @RegisterTestPodCatalog
  static OrderSystemCatalog catalog = TestPodCatalogBuilder
          .newCatalog(OrderSystemCatalog.class)
          .register("kafka", kafkaPod)
          .register("postgres", postgresPod)
          .register("orderService", orderService)
          .register("productService", productService)
          .build();

  @RegisterTestPodGroup
  static TestPodGroup infrastructure = catalog.infrastructure();

  @RegisterTestPodGroup
  static TestPodGroup services = catalog.services().dependsOn(infrastructure);
}
```

