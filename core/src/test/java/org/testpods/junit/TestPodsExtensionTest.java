package org.testpods.junit;

import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.Namespace;
import org.testpods.core.pods.GenericPod;
import org.testpods.core.pods.external.kafka.KafkaPod;
import org.testpods.core.pods.external.postgresql.PostgreSQLPod;
import org.testpods.core.provisioning.GroupBuilder;


@TestPods
class TestPodsExtensionTest {


    /**
     */
    @RegisterCluster
    static K8sCluster cluster = K8sCluster.newMinikube().withNamespace();

    @RegisterNamespace("MyTestNamespace")
    static Namespace testNS = cluster.getDefaultNamespace(); // not instantiated yet

    @TestPod
    static KafkaPod kafkaPod = new KafkaPod().withTopics("order-events");

    @TestPod
    static PostgreSQLPod postgresPod = new PostgreSQLPod().withDatabase("orders").inNamespace(testNS);

    @TestPod
//  @DependsOn("postgres")
    static GenericPod orderService = new GenericPod("mycompany/order-service")
            .withEnv("DATABASE_URL", "${postgres.internal.uri}");

    @TestPod
//  @DependsOn("infrastructure")
    static GenericPod inventoryService = new GenericPod("mycompany/inventory:latest")
            .withEnv("DATABASE_URL", "${postgres.internal.uri}")
            .withEnv("KAFKA_BROKERS", "${kafka.internal.bootstrapServers}");

//   Experimental - can reuse the same pod definition in different groups - but is gained by having the catalog
//   @RegisterTestPodCatalog
//   static TestPodCatalog catalog = TestPodCatalogBuilder.newCatalog().register(kafkaPod).register(postgresPod).build();
//   @RegisterTestPodGroup
//   static TestPodGroup infrastructure = catalog.kafkaPod().postgresPod().asGroup();

    @RegisterTestPodGroup(groupName = "infrastructureGroup")
    static TestPodGroup infrastructureGroup = GroupBuilder.newGroup().add(postgresPod).add(kafkaPod).build();

//  @DependsOn("infrastructureGroup")
//  @WaitFor("infrastructureGroup")
    @RegisterTestPodGroup(groupName = "servicesGroup")
    static TestPodGroup servicesGroup = GroupBuilder.newGroup().add(orderService).add(inventoryService).dependsOn(infrastructureGroup).build();


}
