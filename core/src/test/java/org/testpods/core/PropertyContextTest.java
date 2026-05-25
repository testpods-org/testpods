package org.testpods.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.Test;

class PropertyContextTest {

  @Test
  void resolvesPublishedPropertiesLazily() {
    PropertyContext context = new PropertyContext();
    StringBuilder value = new StringBuilder("initial");

    context.publish("service.host", value::toString);
    value.replace(0, value.length(), "updated");

    assertThat(context.resolve("service.host")).isEqualTo("updated");
  }

  @Test
  void interpolatesPropertyReferences() {
    PropertyContext context = new PropertyContext();
    context.publish("postgres.internal.uri", () -> "jdbc:postgresql://postgres:5432/app");
    context.publish("postgres.username", () -> "app_user");

    assertThat(
            context.interpolate(
                "url=${postgres.internal.uri};username=${postgres.username};mode=test"))
        .isEqualTo("url=jdbc:postgresql://postgres:5432/app;username=app_user;mode=test");
  }

  @Test
  void extractsReferencesInEncounterOrder() {
    assertThat(
            PropertyContext.referencesIn(
                "${postgres.internal.uri}:${postgres.username}:${kafka.internal.bootstrapServers}"))
        .containsExactly(
            "postgres.internal.uri", "postgres.username", "kafka.internal.bootstrapServers");
  }

  @Test
  void resolveAllPreservesRequestedOrder() {
    PropertyContext context = new PropertyContext();
    context.publish("a", () -> "1");
    context.publish("b", () -> "2");

    assertThat(context.resolveAll("b", "a")).containsExactly(entry("b", "2"), entry("a", "1"));
  }

  @Test
  void failsWhenInterpolatedPropertyHasNotBeenPublished() {
    PropertyContext context = new PropertyContext();

    assertThatThrownBy(() -> context.interpolate("${missing.value}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing.value");
  }
}
