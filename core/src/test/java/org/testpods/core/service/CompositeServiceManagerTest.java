package org.testpods.core.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Unit tests for CompositeServiceManager. */
class CompositeServiceManagerTest {

  @Test
  void shouldCreateWithMultipleManagers() {
    CompositeServiceManager manager =
        new CompositeServiceManager(new HeadlessServiceManager(), new NodePortServiceManager());

    assertThat(manager.size()).isEqualTo(2);
  }

  @Test
  void shouldReturnNullNameBeforeCreate() {
    CompositeServiceManager manager = new CompositeServiceManager(new ClusterIPServiceManager());

    assertThat(manager.getName()).isNull();
  }

  @Test
  void shouldReturnNullServiceBeforeCreate() {
    CompositeServiceManager manager = new CompositeServiceManager(new ClusterIPServiceManager());

    assertThat(manager.getService()).isNull();
  }

  @Test
  void deleteShouldNotThrowBeforeCreate() {
    CompositeServiceManager manager =
        new CompositeServiceManager(new HeadlessServiceManager(), new NodePortServiceManager());

    assertThatCode(() -> manager.delete()).doesNotThrowAnyException();
  }

  @Test
  void shouldImplementServiceManagerInterface() {
    CompositeServiceManager manager = new CompositeServiceManager(new ClusterIPServiceManager());

    assertThat(manager).isInstanceOf(ServiceManager.class);
  }

  @Test
  void getServiceTypeShouldReturnComposite() {
    CompositeServiceManager manager = new CompositeServiceManager(new ClusterIPServiceManager());

    assertThat(manager.getServiceType()).isEqualTo("Composite");
  }

  @Test
  void shouldSupportFluentSuffixesConfiguration() {
    CompositeServiceManager configured =
        new CompositeServiceManager(new HeadlessServiceManager(), new NodePortServiceManager())
            .withSuffixes("-headless", "");

    assertThat(configured).isNotNull();
    assertThat(configured.size()).isEqualTo(2);
  }

  @Test
  void shouldReturnNullForInvalidManagerIndex() {
    CompositeServiceManager manager = new CompositeServiceManager(new ClusterIPServiceManager());

    assertThat(manager.getManager(-1)).isNull();
    assertThat(manager.getManager(1)).isNull();
    assertThat(manager.getService(-1)).isNull();
    assertThat(manager.getService(1)).isNull();
  }

  @Test
  void shouldReturnManagerByIndex() {
    HeadlessServiceManager headless = new HeadlessServiceManager();
    NodePortServiceManager nodePort = new NodePortServiceManager();

    CompositeServiceManager manager = new CompositeServiceManager(headless, nodePort);

    assertThat(manager.getManager(0)).isSameAs(headless);
    assertThat(manager.getManager(1)).isSameAs(nodePort);
  }

  @Test
  void shouldHandleEmptyComposite() {
    CompositeServiceManager manager = new CompositeServiceManager();

    assertThat(manager.size()).isEqualTo(0);
    assertThat(manager.getService()).isNull();
    assertThat(manager.getName()).isNull();
  }

  @Test
  void shouldHandleSingleManager() {
    CompositeServiceManager manager = new CompositeServiceManager(new ClusterIPServiceManager());

    assertThat(manager.size()).isEqualTo(1);
  }
}
