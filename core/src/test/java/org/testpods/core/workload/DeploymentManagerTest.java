package org.testpods.core.workload;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DeploymentManager.
 *
 * <p>These tests verify the manager's behavior without requiring a Kubernetes cluster. Integration
 * tests that actually create deployments should be in a separate test class.
 */
class DeploymentManagerTest {

  private DeploymentManager manager;

  @BeforeEach
  void setUp() {
    manager = new DeploymentManager();
  }

  @Test
  void shouldReturnNullNameBeforeCreate() {
    assertThat(manager.getName()).isNull();
  }

  @Test
  void shouldReturnFalseForIsRunningBeforeCreate() {
    assertThat(manager.isRunning()).isFalse();
  }

  @Test
  void shouldReturnFalseForIsReadyBeforeCreate() {
    assertThat(manager.isReady()).isFalse();
  }

  @Test
  void getWorkloadTypeShouldReturnDeployment() {
    assertThat(manager.getWorkloadType()).isEqualTo("Deployment");
  }

  @Test
  void shouldReturnNullDeploymentBeforeCreate() {
    assertThat(manager.getDeployment()).isNull();
  }

  @Test
  void deleteShouldNotThrowBeforeCreate() {
    // Deleting before create should be a no-op, not throw
    assertThatCode(() -> manager.delete()).doesNotThrowAnyException();
  }

  @Test
  void shouldImplementWorkloadManagerInterface() {
    assertThat(manager).isInstanceOf(WorkloadManager.class);
  }
}
