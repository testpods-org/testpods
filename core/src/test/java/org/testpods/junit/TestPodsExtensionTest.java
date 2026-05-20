package org.testpods.junit;

import org.testpods.core.cluster.K8sCluster;
import org.testpods.core.cluster.Namespace;
import org.testpods.core.pods.GenericPod;
import org.testpods.core.pods.external.kafka.KafkaPod;
import org.testpods.core.pods.external.postgresql.PostgreSQLPod;
import org.testpods.core.provisioning.GroupBuilder;


@TestPods
// use case 1: scan classen efter testpod annotations og cluster og namespace(optional) min setup. ingen link mellem testpods og cluster/namespace.
// en fælles group underneden. start samtidigt som udgangspunkt. måske efter type såsopm statefulsetup først
// der kan være groups osv. og det kan samles op også

//@TestPods(testpodsProviders = {ExampleTestpodsProvider.class})
// use case 2: genbruge cluster og groups fra andre classer

class TestPodsExtensionTest {


    /**
     * skal den @RegisterCluster bruges til noget? Man starter den i klassen, men skal den ikke
     * manages i test extensions for at sikre at dne bliver startet op på det korrkete tidspnt Det
     * samme gælder for @RegisterNamespace - for det blier jo lavet som en del af minikuble cluster
     * create men vhis man explict angiver et så skal det jo være det gældende namespace. plus at man
     * kan have flere namespaces i en cluster
     *
     * // HUSK at et cluster bliver altid injected ind i testclassen såman kan bare declare det.
     * Hvordan med at få injected testpods?
     * // Skal det være det samme med Testpods?
     * Så skal test extensionen efter at have samlet testpods og cluster, så scanne test classen og se
     * om der en KafkaPod eller PostgreSQLPod variable?
     * Men hvad så hvis der er flere kafkapods?
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


    /**
     * Testpod - TestContainer
     * Testpods - TestWithGridInfrastructure - picked up by TestPodsExtension
     * TestpodGroup - GridTestInfrastructure
     * @TestPodGroup("systemService") // only support named
     * references or also just use the bean name convention maybe?
     * static TestPodGroup systemServices;
     */
}
