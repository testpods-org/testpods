package org.testpods.core.pods.builders;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for Kubernetes container probes.
 *
 * <p>Simplifies probe configuration by providing a flat, readable API that hides the complexity of
 * the Fabric8 nested builder pattern.
 *
 * <p>ProbeSpec supports three probe types (mutually exclusive):
 *
 * <ul>
 *   <li><b>TCP Socket</b> - checks if a port is open
 *   <li><b>HTTP GET</b> - checks HTTP endpoint returns 2xx/3xx
 *   <li><b>Exec</b> - runs a command, checks exit code is 0
 * </ul>
 *
 * <p><b>Usage examples:</b>
 *
 * <pre>{@code
 * // TCP socket probe on port 5432
 * Probe tcpProbe = new ProbeSpec()
 *     .tcpSocket(5432)
 *     .initialDelay(5)
 *     .period(10)
 *     .build();
 *
 * // HTTP GET probe
 * Probe httpProbe = new ProbeSpec()
 *     .httpGet(8080, "/health")
 *     .initialDelay(10)
 *     .timeout(5)
 *     .failureThreshold(5)
 *     .build();
 *
 * // HTTPS GET probe
 * Probe httpsProbe = new ProbeSpec()
 *     .httpsGet(8443, "/ready")
 *     .build();
 *
 * // Exec probe
 * Probe execProbe = new ProbeSpec()
 *     .exec("pg_isready", "-U", "postgres")
 *     .initialDelay(5)
 *     .build();
 * }</pre>
 *
 * <p>The probe is built with Kubernetes defaults for any unspecified timing values:
 *
 * <ul>
 *   <li>initialDelaySeconds: 0
 *   <li>periodSeconds: 10
 *   <li>timeoutSeconds: 1
 *   <li>failureThreshold: 3
 *   <li>successThreshold: 1
 * </ul>
 *
 * @see io.fabric8.kubernetes.api.model.Probe
 */
public class ProbeSpec {

  // Probe type - only one should be set
  private Integer tcpSocketPort;
  private Integer httpGetPort;
  private String httpGetPath;
  private String httpScheme;
  private List<String> execCommand;

  // Timing configuration with Kubernetes defaults
  private int initialDelaySeconds = 0;
  private int periodSeconds = 10;
  private int timeoutSeconds = 1;
  private int failureThreshold = 3;
  private int successThreshold = 1;

  /**
   * Configure a TCP socket probe.
   *
   * <p>The probe succeeds if a TCP connection can be established to the specified port.
   *
   * @param port the port to check
   * @return this ProbeSpec for method chaining
   */
  public ProbeSpec tcpSocket(int port) {
    this.tcpSocketPort = port;
    return this;
  }

  /**
   * Configure an HTTP GET probe.
   *
   * <p>The probe succeeds if the HTTP request returns a status code between 200 and 399.
   *
   * @param port the port to connect to
   * @param path the path to request (e.g., "/health")
   * @return this ProbeSpec for method chaining
   */
  public ProbeSpec httpGet(int port, String path) {
    this.httpGetPort = port;
    this.httpGetPath = path;
    this.httpScheme = "HTTP";
    return this;
  }

  /**
   * Configure an HTTPS GET probe.
   *
   * <p>The probe succeeds if the HTTPS request returns a status code between 200 and 399.
   *
   * @param port the port to connect to
   * @param path the path to request (e.g., "/health")
   * @return this ProbeSpec for method chaining
   */
  public ProbeSpec httpsGet(int port, String path) {
    this.httpGetPort = port;
    this.httpGetPath = path;
    this.httpScheme = "HTTPS";
    return this;
  }

  /**
   * Configure an exec probe.
   *
   * <p>The probe succeeds if the command exits with status code 0.
   *
   * @param command the command and arguments to execute
   * @return this ProbeSpec for method chaining
   */
  public ProbeSpec exec(String... command) {
    this.execCommand = Arrays.asList(command);
    return this;
  }

  /**
   * Set the initial delay before the probe starts.
   *
   * <p>This gives the container time to start up before probing begins.
   *
   * @param seconds the delay in seconds (default: 0)
   * @return this ProbeSpec for method chaining
   */
  public ProbeSpec initialDelay(int seconds) {
    this.initialDelaySeconds = seconds;
    return this;
  }

  /**
   * Set the period between probe executions.
   *
   * @param seconds the period in seconds (default: 10)
   * @return this ProbeSpec for method chaining
   */
  public ProbeSpec period(int seconds) {
    this.periodSeconds = seconds;
    return this;
  }

  /**
   * Set the timeout for each probe execution.
   *
   * @param seconds the timeout in seconds (default: 1)
   * @return this ProbeSpec for method chaining
   */
  public ProbeSpec timeout(int seconds) {
    this.timeoutSeconds = seconds;
    return this;
  }

  /**
   * Set the failure threshold.
   *
   * <p>The probe must fail this many consecutive times to be considered failed.
   *
   * @param threshold the failure threshold (default: 3)
   * @return this ProbeSpec for method chaining
   */
  public ProbeSpec failureThreshold(int threshold) {
    this.failureThreshold = threshold;
    return this;
  }

  /**
   * Set the success threshold.
   *
   * <p>The probe must succeed this many consecutive times to be considered successful. For liveness
   * and startup probes, this must be 1.
   *
   * @param threshold the success threshold (default: 1)
   * @return this ProbeSpec for method chaining
   */
  public ProbeSpec successThreshold(int threshold) {
    this.successThreshold = threshold;
    return this;
  }

  /**
   * Build the Fabric8 Probe object.
   *
   * @return the configured Probe
   * @throws IllegalStateException if no probe type has been configured
   */
  public Probe build() {
    if (tcpSocketPort == null && httpGetPort == null && execCommand == null) {
      throw new IllegalStateException(
          "Probe type not configured. Call tcpSocket(), httpGet(), httpsGet(), or exec() first.");
    }

    ProbeBuilder builder =
        new ProbeBuilder()
            .withInitialDelaySeconds(initialDelaySeconds)
            .withPeriodSeconds(periodSeconds)
            .withTimeoutSeconds(timeoutSeconds)
            .withFailureThreshold(failureThreshold)
            .withSuccessThreshold(successThreshold);

    if (tcpSocketPort != null) {
      builder.withNewTcpSocket().withPort(new IntOrString(tcpSocketPort)).endTcpSocket();
    } else if (httpGetPort != null) {
      builder
          .withNewHttpGet()
          .withPort(new IntOrString(httpGetPort))
          .withPath(httpGetPath)
          .withScheme(httpScheme)
          .endHttpGet();
    } else if (execCommand != null) {
      builder.withNewExec().withCommand(execCommand).endExec();
    }

    return builder.build();
  }
}
