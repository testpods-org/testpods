package org.testpods.core.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for NodePortServiceManager. */
class NodePortServiceManagerTest {

  private NodePortServiceManager manager;

  @BeforeEach
  void setUp() {
    manager = new NodePortServiceManager();
  }

  @Test
  void shouldReturnNullNameBeforeCreate() {
    assertThat(manager.getName()).isNull();
  }

  @Test
  void shouldReturnNullServiceBeforeCreate() {
    assertThat(manager.getService()).isNull();
  }

  @Test
  void shouldReturnNullNodePortBeforeCreate() {
    assertThat(manager.getNodePort()).isNull();
  }

  @Test
  void deleteShouldNotThrowBeforeCreate() {
    assertThatCode(() -> manager.delete()).doesNotThrowAnyException();
  }

  @Test
  void shouldImplementServiceManagerInterface() {
    assertThat(manager).isInstanceOf(ServiceManager.class);
  }

  @Test
  void getServiceTypeShouldReturnNodePort() {
    assertThat(manager.getServiceType()).isEqualTo("NodePort");
  }

  @Test
  void shouldSupportFluentNodePortConfiguration() {
    NodePortServiceManager configured = new NodePortServiceManager().withNodePort(30080);

    assertThat(configured).isNotNull();
  }
}
