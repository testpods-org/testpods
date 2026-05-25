package org.testpods.core.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExternalAccessStrategyTest {

  @Test
  void shouldCreateFabric8PortForwardStrategy() {
    assertThat(ExternalAccessStrategy.portForward()).isNotNull();
  }

  @Test
  void shouldCreateLocalhostPortForwardStrategy() {
    assertThat(ExternalAccessStrategy.localhostPortForward()).isNotNull();
    assertThat(ExternalAccessStrategy.localhostPortForward("testpods")).isNotNull();
  }
}
