package org.testpods.core.pods.builders;

import static org.assertj.core.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Probe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ProbeSpec fluent builder.
 *
 * <p>These tests verify all probe types (tcpSocket, httpGet, httpsGet, exec), timing configuration,
 * default values, and validation behavior.
 */
class ProbeSpecTest {

  @Nested
  class TcpSocketProbe {

    @Test
    void shouldBuildTcpSocketProbe() {
      Probe probe = new ProbeSpec().tcpSocket(5432).build();

      assertThat(probe.getTcpSocket()).isNotNull();
      assertThat(probe.getTcpSocket().getPort().getIntVal()).isEqualTo(5432);
      assertThat(probe.getHttpGet()).isNull();
      assertThat(probe.getExec()).isNull();
    }

    @Test
    void shouldBuildTcpSocketProbeWithCustomTiming() {
      Probe probe =
          new ProbeSpec()
              .tcpSocket(3306)
              .initialDelay(10)
              .period(5)
              .timeout(2)
              .failureThreshold(5)
              .successThreshold(2)
              .build();

      assertThat(probe.getTcpSocket().getPort().getIntVal()).isEqualTo(3306);
      assertThat(probe.getInitialDelaySeconds()).isEqualTo(10);
      assertThat(probe.getPeriodSeconds()).isEqualTo(5);
      assertThat(probe.getTimeoutSeconds()).isEqualTo(2);
      assertThat(probe.getFailureThreshold()).isEqualTo(5);
      assertThat(probe.getSuccessThreshold()).isEqualTo(2);
    }
  }

  @Nested
  class HttpGetProbe {

    @Test
    void shouldBuildHttpGetProbe() {
      Probe probe = new ProbeSpec().httpGet(8080, "/health").build();

      assertThat(probe.getHttpGet()).isNotNull();
      assertThat(probe.getHttpGet().getPort().getIntVal()).isEqualTo(8080);
      assertThat(probe.getHttpGet().getPath()).isEqualTo("/health");
      assertThat(probe.getHttpGet().getScheme()).isEqualTo("HTTP");
      assertThat(probe.getTcpSocket()).isNull();
      assertThat(probe.getExec()).isNull();
    }

    @Test
    void shouldBuildHttpGetProbeWithRootPath() {
      Probe probe = new ProbeSpec().httpGet(80, "/").build();

      assertThat(probe.getHttpGet().getPort().getIntVal()).isEqualTo(80);
      assertThat(probe.getHttpGet().getPath()).isEqualTo("/");
    }

    @Test
    void shouldBuildHttpGetProbeWithNestedPath() {
      Probe probe = new ProbeSpec().httpGet(8080, "/api/v1/health").build();

      assertThat(probe.getHttpGet().getPath()).isEqualTo("/api/v1/health");
    }
  }

  @Nested
  class HttpsGetProbe {

    @Test
    void shouldBuildHttpsGetProbe() {
      Probe probe = new ProbeSpec().httpsGet(8443, "/ready").build();

      assertThat(probe.getHttpGet()).isNotNull();
      assertThat(probe.getHttpGet().getPort().getIntVal()).isEqualTo(8443);
      assertThat(probe.getHttpGet().getPath()).isEqualTo("/ready");
      assertThat(probe.getHttpGet().getScheme()).isEqualTo("HTTPS");
      assertThat(probe.getTcpSocket()).isNull();
      assertThat(probe.getExec()).isNull();
    }

    @Test
    void shouldBuildHttpsGetProbeWithHealthPath() {
      Probe probe = new ProbeSpec().httpsGet(443, "/health").build();

      assertThat(probe.getHttpGet().getPort().getIntVal()).isEqualTo(443);
      assertThat(probe.getHttpGet().getPath()).isEqualTo("/health");
      assertThat(probe.getHttpGet().getScheme()).isEqualTo("HTTPS");
    }
  }

  @Nested
  class ExecProbe {

    @Test
    void shouldBuildExecProbeWithSingleCommand() {
      Probe probe = new ProbeSpec().exec("pg_isready").build();

      assertThat(probe.getExec()).isNotNull();
      assertThat(probe.getExec().getCommand()).containsExactly("pg_isready");
      assertThat(probe.getTcpSocket()).isNull();
      assertThat(probe.getHttpGet()).isNull();
    }

    @Test
    void shouldBuildExecProbeWithMultipleArgs() {
      Probe probe = new ProbeSpec().exec("pg_isready", "-U", "postgres", "-d", "mydb").build();

      assertThat(probe.getExec().getCommand())
          .containsExactly("pg_isready", "-U", "postgres", "-d", "mydb");
    }

    @Test
    void shouldBuildExecProbeWithShellCommand() {
      Probe probe = new ProbeSpec().exec("/bin/sh", "-c", "curl localhost:8080/health").build();

      assertThat(probe.getExec().getCommand())
          .containsExactly("/bin/sh", "-c", "curl localhost:8080/health");
    }
  }

  @Nested
  class TimingDefaults {

    @Test
    void shouldUseKubernetesDefaultsForTcpSocket() {
      Probe probe = new ProbeSpec().tcpSocket(8080).build();

      // Kubernetes defaults per documentation
      assertThat(probe.getInitialDelaySeconds()).isEqualTo(0);
      assertThat(probe.getPeriodSeconds()).isEqualTo(10);
      assertThat(probe.getTimeoutSeconds()).isEqualTo(1);
      assertThat(probe.getFailureThreshold()).isEqualTo(3);
      assertThat(probe.getSuccessThreshold()).isEqualTo(1);
    }

    @Test
    void shouldUseKubernetesDefaultsForHttpGet() {
      Probe probe = new ProbeSpec().httpGet(8080, "/health").build();

      assertThat(probe.getInitialDelaySeconds()).isEqualTo(0);
      assertThat(probe.getPeriodSeconds()).isEqualTo(10);
      assertThat(probe.getTimeoutSeconds()).isEqualTo(1);
      assertThat(probe.getFailureThreshold()).isEqualTo(3);
      assertThat(probe.getSuccessThreshold()).isEqualTo(1);
    }

    @Test
    void shouldUseKubernetesDefaultsForExec() {
      Probe probe = new ProbeSpec().exec("check").build();

      assertThat(probe.getInitialDelaySeconds()).isEqualTo(0);
      assertThat(probe.getPeriodSeconds()).isEqualTo(10);
      assertThat(probe.getTimeoutSeconds()).isEqualTo(1);
      assertThat(probe.getFailureThreshold()).isEqualTo(3);
      assertThat(probe.getSuccessThreshold()).isEqualTo(1);
    }
  }

  @Nested
  class TimingOverrides {

    @Test
    void shouldOverrideInitialDelay() {
      Probe probe = new ProbeSpec().tcpSocket(8080).initialDelay(30).build();

      assertThat(probe.getInitialDelaySeconds()).isEqualTo(30);
      // Other defaults should remain
      assertThat(probe.getPeriodSeconds()).isEqualTo(10);
    }

    @Test
    void shouldOverridePeriod() {
      Probe probe = new ProbeSpec().tcpSocket(8080).period(5).build();

      assertThat(probe.getPeriodSeconds()).isEqualTo(5);
      // Other defaults should remain
      assertThat(probe.getInitialDelaySeconds()).isEqualTo(0);
    }

    @Test
    void shouldOverrideTimeout() {
      Probe probe = new ProbeSpec().tcpSocket(8080).timeout(3).build();

      assertThat(probe.getTimeoutSeconds()).isEqualTo(3);
    }

    @Test
    void shouldOverrideFailureThreshold() {
      Probe probe = new ProbeSpec().tcpSocket(8080).failureThreshold(10).build();

      assertThat(probe.getFailureThreshold()).isEqualTo(10);
    }

    @Test
    void shouldOverrideSuccessThreshold() {
      Probe probe = new ProbeSpec().tcpSocket(8080).successThreshold(2).build();

      assertThat(probe.getSuccessThreshold()).isEqualTo(2);
    }

    @Test
    void shouldOverrideAllTimingParameters() {
      Probe probe =
          new ProbeSpec()
              .httpGet(8080, "/health")
              .initialDelay(15)
              .period(20)
              .timeout(5)
              .failureThreshold(6)
              .successThreshold(3)
              .build();

      assertThat(probe.getInitialDelaySeconds()).isEqualTo(15);
      assertThat(probe.getPeriodSeconds()).isEqualTo(20);
      assertThat(probe.getTimeoutSeconds()).isEqualTo(5);
      assertThat(probe.getFailureThreshold()).isEqualTo(6);
      assertThat(probe.getSuccessThreshold()).isEqualTo(3);
    }
  }

  @Nested
  class Validation {

    @Test
    void shouldThrowWhenNoProbeTypeConfigured() {
      ProbeSpec spec = new ProbeSpec();

      assertThatThrownBy(spec::build)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Probe type not configured");
    }

    @Test
    void shouldThrowWhenOnlyTimingConfigured() {
      ProbeSpec spec = new ProbeSpec().initialDelay(10).period(5);

      assertThatThrownBy(spec::build)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("tcpSocket()");
    }
  }

  @Nested
  class FluentChaining {

    @Test
    void shouldSupportFluentMethodChaining() {
      ProbeSpec spec = new ProbeSpec();

      // All methods should return the same instance for chaining
      assertThat(spec.tcpSocket(8080)).isSameAs(spec);
      assertThat(spec.initialDelay(10)).isSameAs(spec);
      assertThat(spec.period(5)).isSameAs(spec);
      assertThat(spec.timeout(2)).isSameAs(spec);
      assertThat(spec.failureThreshold(3)).isSameAs(spec);
      assertThat(spec.successThreshold(1)).isSameAs(spec);
    }

    @Test
    void httpGetShouldReturnSameInstance() {
      ProbeSpec spec = new ProbeSpec();
      assertThat(spec.httpGet(8080, "/health")).isSameAs(spec);
    }

    @Test
    void httpsGetShouldReturnSameInstance() {
      ProbeSpec spec = new ProbeSpec();
      assertThat(spec.httpsGet(8443, "/health")).isSameAs(spec);
    }

    @Test
    void execShouldReturnSameInstance() {
      ProbeSpec spec = new ProbeSpec();
      assertThat(spec.exec("check")).isSameAs(spec);
    }
  }

  @Nested
  class ProbeTypeSelection {

    @Test
    void tcpSocketProbeHasPriority() {
      // When multiple probe types are set, tcpSocket has priority per implementation
      // (checked first in build() method)
      Probe probe = new ProbeSpec().tcpSocket(8080).httpGet(8081, "/health").build();

      // TCP socket is checked first in build(), so it takes priority
      assertThat(probe.getTcpSocket()).isNotNull();
      assertThat(probe.getTcpSocket().getPort().getIntVal()).isEqualTo(8080);
      // HttpGet is null because tcpSocket was used
      assertThat(probe.getHttpGet()).isNull();
    }

    @Test
    void httpGetProbeUsedWhenNoTcpSocket() {
      Probe probe = new ProbeSpec().httpGet(8081, "/health").exec("check").build();

      // HTTP GET is checked second, so it's used when there's no TCP socket
      assertThat(probe.getHttpGet()).isNotNull();
      assertThat(probe.getHttpGet().getPort().getIntVal()).isEqualTo(8081);
      assertThat(probe.getTcpSocket()).isNull();
      // Exec is null because httpGet was used
      assertThat(probe.getExec()).isNull();
    }
  }

  @Nested
  class IntegrationWithContainerSpec {

    @Test
    void shouldWorkWithConsumerPattern() {
      // This tests the integration pattern used by ContainerSpec
      ProbeSpec probeSpec = new ProbeSpec();

      // Simulate Consumer<ProbeSpec> behavior
      java.util.function.Consumer<ProbeSpec> configurer =
          probe -> probe.tcpSocket(5432).initialDelay(5).period(2);

      configurer.accept(probeSpec);
      Probe probe = probeSpec.build();

      assertThat(probe.getTcpSocket()).isNotNull();
      assertThat(probe.getTcpSocket().getPort().getIntVal()).isEqualTo(5432);
      assertThat(probe.getInitialDelaySeconds()).isEqualTo(5);
      assertThat(probe.getPeriodSeconds()).isEqualTo(2);
    }
  }
}
