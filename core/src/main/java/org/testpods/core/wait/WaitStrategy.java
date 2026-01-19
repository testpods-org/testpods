package org.testpods.core.wait;

import org.testpods.core.pods.TestPod;

import java.time.Duration;

/**
 * Strategy for waiting until a pod is ready to accept traffic.
 * <p>
 * Kubernetes has its own readiness probe mechanism, but sometimes tests
 * need additional waiting logic (e.g., wait for specific log message).
 * <p>
 * Built-in strategies:
 * <ul>
 *   <li>{@link #forReadinessProbe()} - Trust K8s readiness probe</li>
 *   <li>{@link #forLogMessage(String)} - Wait for log output</li>
 *   <li>{@link #forPort(int)} - Wait for TCP port</li>
 *   <li>{@link #forHttp(String, int)} - Wait for HTTP endpoint</li>
 *   <li>{@link #forCommand(String...)} - Wait for command to succeed</li>
 *   <li>{@link #allOf(WaitStrategy...)} - Combine multiple strategies</li>
 * </ul>
 * <p>
 * Example:
 * <pre>{@code
 * MongoDBPod mongo = new MongoDBPod()
 *     .waitingFor(WaitStrategy.forLogMessage(".*waiting for connections.*")
 *         .withTimeout(Duration.ofMinutes(2)));
 * 
 * GenericTestPod app = new GenericTestPod("myapp:latest")
 *     .waitingFor(WaitStrategy.forHttp("/health", 8080)
 *         .withTimeout(Duration.ofSeconds(30)));
 * }</pre>
 */
public interface WaitStrategy {

    /**
     * Wait until the pod is ready according to this strategy.
     *
     * @param pod The pod to wait for
     * @throws IllegalStateException if the pod doesn't become ready within the timeout
     */
    void waitUntilReady(TestPod<?> pod);

    /**
     * Create a copy of this strategy with a different timeout.
     *
     * @param timeout The new timeout duration
     * @return A new WaitStrategy with the specified timeout
     */
    WaitStrategy withTimeout(Duration timeout);

    /**
     * Create a copy of this strategy with a different poll interval.
     *
     * @param interval The new poll interval
     * @return A new WaitStrategy with the specified poll interval
     */
    WaitStrategy withPollInterval(Duration interval);

    // =============================================================
    // Factory methods
    // =============================================================

    /**
     * Wait for the Kubernetes readiness probe to pass.
     * <p>
     * This is the default for most infrastructure pods. It trusts the pod's
     * configured readiness probe and polls the K8s API until the pod reports ready.
     * <p>
     * Default timeout: 2 minutes
     */
    static ReadinessProbeWaitStrategy forReadinessProbe() {
        return new ReadinessProbeWaitStrategy();
    }

    /**
     * Wait for a specific log message pattern to appear.
     * <p>
     * Uses regex matching against the container logs.
     * <p>
     * Example:
     * <pre>{@code
     * WaitStrategy.forLogMessage(".*Started Application in .* seconds.*")
     * WaitStrategy.forLogMessage(".*Ready to accept connections.*")
     * }</pre>
     *
     * @param regex The regex pattern to match
     */
    static LogMessageWaitStrategy forLogMessage(String regex) {
        return new LogMessageWaitStrategy(regex, 1);
    }

    /**
     * Wait for a log message pattern to appear a specific number of times.
     * <p>
     * Useful when a message appears multiple times during startup and you need
     * to wait for a specific occurrence.
     *
     * @param regex The regex pattern to match
     * @param times The number of times the pattern must appear
     */
    static LogMessageWaitStrategy forLogMessage(String regex, int times) {
        return new LogMessageWaitStrategy(regex, times);
    }

    /**
     * Wait for a TCP port to be open and accepting connections.
     * <p>
     * Attempts to connect to the pod's external endpoint on the specified port.
     * <p>
     * Default timeout: 1 minute
     *
     * @param port The port to check
     */
    static PortWaitStrategy forPort(int port) {
        return new PortWaitStrategy(port);
    }

    /**
     * Wait for an HTTP endpoint to return a successful status code.
     * <p>
     * By default, considers 2xx status codes as success.
     * <p>
     * Example:
     * <pre>{@code
     * WaitStrategy.forHttp("/health", 8080)
     * WaitStrategy.forHttp("/actuator/health/readiness", 8080).forStatusCode(200)
     * }</pre>
     *
     * @param path The HTTP path to request
     * @param port The port the HTTP server listens on
     */
    static HttpWaitStrategy forHttp(String path, int port) {
        return new HttpWaitStrategy(path, port);
    }

    /**
     * Wait for a command to exit with code 0.
     * <p>
     * The command is executed inside the container using exec.
     * <p>
     * Example:
     * <pre>{@code
     * WaitStrategy.forCommand("mongosh", "--eval", "db.adminCommand('ping')")
     * WaitStrategy.forCommand("pg_isready", "-U", "postgres")
     * WaitStrategy.forCommand("redis-cli", "ping")
     * }</pre>
     *
     * @param command The command and arguments to execute
     */
    static CommandWaitStrategy forCommand(String... command) {
        return new CommandWaitStrategy(command);
    }

    /**
     * Combine multiple strategies - all must pass for the pod to be considered ready.
     * <p>
     * Strategies are executed in order. The total timeout is shared among all strategies.
     * <p>
     * Example:
     * <pre>{@code
     * WaitStrategy.allOf(
     *     WaitStrategy.forPort(8080),
     *     WaitStrategy.forHttp("/health", 8080),
     *     WaitStrategy.forLogMessage(".*Ready.*")
     * )
     * }</pre>
     *
     * @param strategies The strategies to combine
     */
    static CompositeWaitStrategy allOf(WaitStrategy... strategies) {
        return new CompositeWaitStrategy(strategies);
    }
}
