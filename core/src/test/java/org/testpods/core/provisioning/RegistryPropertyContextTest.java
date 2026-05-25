package org.testpods.core.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.testpods.core.pods.GenericPod;
import org.testpods.junit.TestPod;

class RegistryPropertyContextTest {

  static class OutOfOrderPods {
    @TestPod
    static GenericPod service =
        new GenericPod("example/service:latest")
            .withName("service")
            .withEnv("DATABASE_URL", "${postgres.internal.uri}")
            .withEnv("KAFKA_BROKERS", "${kafka.internal.bootstrapServers}");

    @TestPod
    static GenericPod kafka = new GenericPod("kafka:latest").withName("kafka");

    @TestPod
    static GenericPod postgres = new GenericPod("postgres:latest").withName("postgres");
  }

  static class CyclicPods {
    @TestPod
    static GenericPod first =
        new GenericPod("example/first:latest").withName("first").withEnv("SECOND", "${second.url}");

    @TestPod
    static GenericPod second =
        new GenericPod("example/second:latest").withName("second").withEnv("FIRST", "${first.url}");
  }

  @Test
  void startsPodsBeforePodsThatReferenceTheirProperties() {
    Registry registry = new Registry();
    registry.addTestPodInitializations(
        ReflectionHelper.scanClassForTestPodInitializationsOnly(OutOfOrderPods.class));

    assertThat(registry.orderInitializationsForStart())
        .extracting(FieldInitialization::podName)
        .containsExactly("postgres", "kafka", "service");
  }

  @Test
  void detectsCyclicPropertyDependencies() {
    Registry registry = new Registry();
    registry.addTestPodInitializations(
        ReflectionHelper.scanClassForTestPodInitializationsOnly(CyclicPods.class));

    assertThatThrownBy(registry::orderInitializationsForStart)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cyclic TestPod property dependency");
  }
}
