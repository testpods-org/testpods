package org.testpods.core.wait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.testpods.core.pods.TestPod;

/**
 * Combines multiple wait strategies that must all pass.
 *
 * <p>Strategies are executed sequentially in the order provided. The total timeout is shared across
 * all strategies - each strategy gets the remaining time after the previous strategies complete.
 *
 * <p>If any strategy times out or fails, the entire composite fails with a message indicating which
 * strategy failed.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Wait for readiness probe AND a specific log message
 * WaitStrategy.allOf(
 *     WaitStrategy.forReadinessProbe(),
 *     WaitStrategy.forLogMessage(".*Application started.*")
 * )
 *
 * // Wait for readiness probe, log message, AND HTTP endpoint
 * WaitStrategy.allOf(
 *     WaitStrategy.forReadinessProbe(),
 *     WaitStrategy.forLogMessage(".*Spring application started.*"),
 *     WaitStrategy.forHttp("/actuator/health/readiness", 8080)
 * )
 *
 * // With custom timeout for the entire composite
 * WaitStrategy.allOf(
 *     WaitStrategy.forReadinessProbe(),
 *     WaitStrategy.forCommand("test", "-f", "/tmp/ready")
 * ).withTimeout(Duration.ofMinutes(10))
 * }</pre>
 *
 * <p>Default timeout: 5 minutes (shared across all strategies)<br>
 * Default poll interval: 1 second (applied to all contained strategies)
 */
public class CompositeWaitStrategy implements WaitStrategy {

  private final List<WaitStrategy> strategies;
  private final Duration timeout;
  private final Duration pollInterval;

  /**
   * Create a composite wait strategy.
   *
   * @param strategies The strategies to combine (executed in order)
   * @throws IllegalArgumentException if no strategies are provided
   */
  public CompositeWaitStrategy(WaitStrategy... strategies) {
    this(Arrays.asList(strategies), Duration.ofMinutes(5), Duration.ofSeconds(1));
  }

  private CompositeWaitStrategy(
      List<WaitStrategy> strategies, Duration timeout, Duration pollInterval) {
    if (strategies == null || strategies.isEmpty()) {
      throw new IllegalArgumentException("At least one strategy is required");
    }
    for (WaitStrategy strategy : strategies) {
      Objects.requireNonNull(strategy, "strategies cannot contain null elements");
    }
    this.strategies = new ArrayList<>(strategies);
    this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval must not be null");
  }

  @Override
  public void waitUntilReady(TestPod<?> pod) {
    Objects.requireNonNull(pod, "pod must not be null");

    long startTime = System.currentTimeMillis();
    long timeoutMillis = timeout.toMillis();

    for (int i = 0; i < strategies.size(); i++) {
      WaitStrategy strategy = strategies.get(i);

      // Calculate remaining time for this strategy
      long elapsed = System.currentTimeMillis() - startTime;
      long remaining = timeoutMillis - elapsed;

      if (remaining <= 0) {
        throw new IllegalStateException(
            String.format(
                "Composite wait strategy timed out after %s before starting strategy %d of %d "
                    + "(%s) for pod '%s'",
                formatDuration(timeout),
                i + 1,
                strategies.size(),
                strategy.getClass().getSimpleName(),
                pod.getName()));
      }

      // Apply the remaining time as the strategy's timeout
      WaitStrategy timedStrategy =
          strategy.withTimeout(Duration.ofMillis(remaining)).withPollInterval(pollInterval);

      try {
        timedStrategy.waitUntilReady(pod);
      } catch (IllegalStateException e) {
        // Re-throw with context about which strategy failed
        throw new IllegalStateException(
            String.format(
                "Strategy %d of %d (%s) failed for pod '%s': %s",
                i + 1,
                strategies.size(),
                strategy.getClass().getSimpleName(),
                pod.getName(),
                e.getMessage()),
            e);
      }
    }
  }

  @Override
  public WaitStrategy withTimeout(Duration timeout) {
    return new CompositeWaitStrategy(strategies, timeout, this.pollInterval);
  }

  @Override
  public WaitStrategy withPollInterval(Duration interval) {
    // Apply poll interval to all contained strategies
    List<WaitStrategy> updatedStrategies = new ArrayList<>();
    for (WaitStrategy strategy : strategies) {
      updatedStrategies.add(strategy.withPollInterval(interval));
    }
    return new CompositeWaitStrategy(updatedStrategies, this.timeout, interval);
  }

  /**
   * Get the number of strategies in this composite.
   *
   * @return The number of contained strategies
   */
  public int getStrategyCount() {
    return strategies.size();
  }

  /**
   * Get an unmodifiable view of the contained strategies.
   *
   * @return The list of strategies
   */
  public List<WaitStrategy> getStrategies() {
    return Collections.unmodifiableList(strategies);
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
    StringBuilder sb = new StringBuilder("CompositeWaitStrategy[");
    sb.append("strategies=[");
    for (int i = 0; i < strategies.size(); i++) {
      if (i > 0) sb.append(", ");
      sb.append(strategies.get(i).getClass().getSimpleName());
    }
    sb.append("], timeout=").append(formatDuration(timeout));
    sb.append("]");
    return sb.toString();
  }
}
