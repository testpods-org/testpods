package org.testpods.core.pods;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.EnvVar;
import org.junit.jupiter.api.Test;
import org.testpods.core.PropertyContext;

class ContainerDefinitionTest {

  @Test
  void resolvesEnvironmentTemplatesFromPropertyContextWhenContainerIsBuilt() {
    PropertyContext context = new PropertyContext();
    context.publish("postgres.internal.uri", () -> "jdbc:postgresql://postgres:5432/app");
    context.publish("kafka.internal.bootstrapServers", () -> "kafka:9092");

    var container =
        new ContainerDefinition()
            .withImage("example/service:latest")
            .withEnv("DATABASE_URL", "${postgres.internal.uri}")
            .withEnv("KAFKA_BROKERS", "${kafka.internal.bootstrapServers}")
            .withPropertyContext(context)
            .buildContainer("service");

    assertThat(container.getEnv())
        .extracting(EnvVar::getValue)
        .containsExactly("jdbc:postgresql://postgres:5432/app", "kafka:9092");
  }

  @Test
  void reportsReferencedPropertiesFromEnvironmentTemplates() {
    var definition =
        new ContainerDefinition()
            .withImage("example/service:latest")
            .withEnv("DATABASE_URL", "${postgres.internal.uri}")
            .withEnv("KAFKA_BROKERS", "${kafka.internal.bootstrapServers}")
            .withEnv("PLAIN", "literal");

    assertThat(definition.getReferencedProperties())
        .containsExactly("postgres.internal.uri", "kafka.internal.bootstrapServers");
  }
}
