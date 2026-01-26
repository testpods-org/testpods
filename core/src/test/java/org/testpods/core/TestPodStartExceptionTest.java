package org.testpods.core;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Unit tests for TestPodStartException. */
class TestPodStartExceptionTest {

  @Test
  void shouldCreateWithPodNameMessageAndCause() {
    RuntimeException cause = new RuntimeException("Timeout waiting for pod");

    TestPodStartException exception =
        new TestPodStartException("my-pod", "connection refused", cause);

    assertThat(exception.getMessage()).contains("my-pod").contains("connection refused");
    assertThat(exception.getCause()).isSameAs(cause);
    assertThat(exception.getPodName()).isEqualTo("my-pod");
  }

  @Test
  void shouldCreateWithMessageAndCause() {
    RuntimeException cause = new RuntimeException("Connection refused");

    TestPodStartException exception =
        new TestPodStartException("Failed to start pod 'test-pod': timeout", cause);

    assertThat(exception.getMessage()).contains("test-pod");
    assertThat(exception.getCause()).isSameAs(cause);
    assertThat(exception.getPodName()).isEqualTo("test-pod");
  }

  @Test
  void shouldExtractPodNameFromMessage() {
    TestPodStartException exception =
        new TestPodStartException("Failed to start pod 'postgres-test': network error", null);

    assertThat(exception.getPodName()).isEqualTo("postgres-test");
  }

  @Test
  void shouldReturnUnknownWhenPodNameCannotBeExtracted() {
    TestPodStartException exception = new TestPodStartException("Some random error message", null);

    assertThat(exception.getPodName()).isEqualTo("unknown");
  }

  @Test
  void shouldHandleNullMessage() {
    TestPodStartException exception = new TestPodStartException(null, null);

    assertThat(exception.getPodName()).isEqualTo("unknown");
  }
}
