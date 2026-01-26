package org.testpods.core.wait;

import java.time.Duration;
import java.util.Objects;
import org.testpods.core.pods.TestPod;

/**
 * Waits for the Kubernetes readiness probe to pass.
 *
 * <p>This is the most Kubernetes-native wait strategy. It trusts the pod's configured readiness
 * probe and polls the K8s API until the pod's {@link TestPod#isReady()} returns true.
 *
 * <p>The readiness probe is configured on the pod's container spec and can be:
 *
 * <ul>
 *   <li>HTTP GET - checks a specific endpoint
 *   <li>TCP Socket - checks if a port is open
 *   <li>Exec - runs a command in the container
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * new MongoDBPod()
 *     .waitingFor(WaitStrategy.forReadinessProbe())
 *     .waitingFor(WaitStrategy.forReadinessProbe()
 *         .withTimeout(Duration.ofMinutes(5)))
 * }</pre>
 *
 * <p>Default timeout: 2 minutes<br>
 * Default poll interval: 1 second
 */
public class ReadinessProbeWaitStrategy implements WaitStrategy {

  private final Duration timeout;
  private final Duration pollInterval;

  /** Create a new ReadinessProbeWaitStrategy with default settings. */
  public ReadinessProbeWaitStrategy() {
    this(Duration.ofMinutes(2), Duration.ofSeconds(1));
  }

  private ReadinessProbeWaitStrategy(Duration timeout, Duration pollInterval) {
    this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval must not be null");
  }

  @Override
  public void waitUntilReady(TestPod<?> pod) {
    Objects.requireNonNull(pod, "pod must not be null");

    long startTime = System.currentTimeMillis();
    long timeoutMillis = timeout.toMillis();
    long pollMillis = pollInterval.toMillis();

    Exception lastException = null;

    while (System.currentTimeMillis() - startTime < timeoutMillis) {
      try {
        if (pod.isReady()) {
          return; // Success!
        }
      } catch (Exception e) {
        // Pod might not exist yet or API call failed
        lastException = e;
      }

      try {
        Thread.sleep(pollMillis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while waiting for pod '" + pod.getName() + "' to be ready", e);
      }
    }

    // Timeout reached
    String message =
        String.format(
            "Timed out after %s waiting for pod '%s' to pass readiness probe",
            formatDuration(timeout), pod.getName());

    if (lastException != null) {
      throw new IllegalStateException(message + ": " + lastException.getMessage(), lastException);
    }
    throw new IllegalStateException(message);
  }

  @Override
  public WaitStrategy withTimeout(Duration timeout) {
    return new ReadinessProbeWaitStrategy(timeout, this.pollInterval);
  }

  @Override
  public WaitStrategy withPollInterval(Duration interval) {
    return new ReadinessProbeWaitStrategy(this.timeout, interval);
  }

  /**
   * Get the current timeout duration.
   *
   * @return The timeout duration
   */
  public Duration getTimeout() {
    return timeout;
  }

  /**
   * Get the current poll interval.
   *
   * @return The poll interval
   */
  public Duration getPollInterval() {
    return pollInterval;
  }

  private String formatDuration(Duration duration) {
    long seconds = duration.getSeconds();
    if (seconds < 60) {
      return seconds + "s";
    }
    long minutes = seconds / 60;
    long remainingSeconds = seconds % 60;
    if (remainingSeconds == 0) {
      return minutes + "m";
    }
    return minutes + "m " + remainingSeconds + "s";
  }

  @Override
  public String toString() {
    return String.format(
        "ReadinessProbeWaitStrategy[timeout=%s, pollInterval=%s]",
        formatDuration(timeout), formatDuration(pollInterval));
  }
}
