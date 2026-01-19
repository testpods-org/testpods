package org.testpods.core.wait;

import org.testpods.core.pods.ExecResult;
import org.testpods.core.pods.TestPod;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

/**
 * Waits for a command to exit with code 0 when executed inside the container.
 * <p>
 * This strategy repeatedly executes a command using {@link TestPod#exec(String...)}
 * until it returns exit code 0, or the timeout expires.
 * <p>
 * This is useful for:
 * <ul>
 *   <li>Database health checks (e.g., mysqladmin ping)</li>
 *   <li>Checking for file existence (e.g., test -f /tmp/ready)</li>
 *   <li>Custom readiness scripts</li>
 *   <li>Service-specific health commands</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // MySQL ready check
 * WaitStrategy.forCommand("mysqladmin", "ping", "-h", "localhost")
 * 
 * // PostgreSQL ready check
 * WaitStrategy.forCommand("pg_isready", "-h", "localhost")
 * 
 * // MongoDB ready check
 * WaitStrategy.forCommand("mongosh", "--eval", "db.adminCommand('ping')")
 * 
 * // Wait for a file to exist
 * WaitStrategy.forCommand("test", "-f", "/tmp/initialization-complete")
 * 
 * // Custom readiness script
 * WaitStrategy.forCommand("/scripts/check-ready.sh")
 * 
 * // With custom timeout
 * WaitStrategy.forCommand("redis-cli", "ping")
 *     .withTimeout(Duration.ofSeconds(30))
 * }</pre>
 * <p>
 * Default timeout: 1 minute<br>
 * Default poll interval: 500ms
 */
public class CommandWaitStrategy implements WaitStrategy {

    private final String[] command;
    private final Duration timeout;
    private final Duration pollInterval;

    /**
     * Create a new CommandWaitStrategy.
     *
     * @param command The command and arguments to execute
     */
    CommandWaitStrategy(String... command) {
        this(command, Duration.ofMinutes(1), Duration.ofMillis(500));
    }

    private CommandWaitStrategy(String[] command, Duration timeout, Duration pollInterval) {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("command must not be null or empty");
        }
        this.command = Arrays.copyOf(command, command.length);
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval must not be null");
    }

    @Override
    public void waitUntilReady(TestPod<?> pod) {
        Objects.requireNonNull(pod, "pod must not be null");
        
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeout.toMillis();
        long pollMillis = pollInterval.toMillis();
        
        ExecResult lastResult = null;
        Exception lastException = null;
        
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try {
                ExecResult result = pod.exec(command);
                lastResult = result;
                
                if (result.isSuccess()) {
                    return; // Success!
                }
            } catch (Exception e) {
                // Command execution failed (pod not ready, exec not available, etc.)
                lastException = e;
            }
            
            try {
                Thread.sleep(pollMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                    "Interrupted while waiting for command in pod '" + pod.getName() + "'", e);
            }
        }
        
        // Timeout reached - build helpful error message
        String commandStr = String.join(" ", command);
        StringBuilder message = new StringBuilder();
        message.append(String.format(
            "Timed out after %s waiting for command '%s' to succeed in pod '%s'",
            formatDuration(timeout), commandStr, pod.getName()));
        
        if (lastResult != null) {
            message.append(String.format("\nLast exit code: %d", lastResult.exitCode()));
            if (!lastResult.stderr().isEmpty()) {
                message.append("\nStderr: ").append(truncate(lastResult.stderr(), 200));
            }
            if (!lastResult.stdout().isEmpty()) {
                message.append("\nStdout: ").append(truncate(lastResult.stdout(), 200));
            }
        }
        
        if (lastException != null) {
            message.append("\nLast error: ").append(lastException.getMessage());
        }
        
        throw new IllegalStateException(message.toString(), lastException);
    }
    
    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    @Override
    public WaitStrategy withTimeout(Duration timeout) {
        return new CommandWaitStrategy(command, timeout, this.pollInterval);
    }

    @Override
    public WaitStrategy withPollInterval(Duration interval) {
        return new CommandWaitStrategy(command, this.timeout, interval);
    }
    
    /**
     * Get the command being executed.
     *
     * @return A copy of the command array
     */
    public String[] getCommand() {
        return Arrays.copyOf(command, command.length);
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
        return String.format("CommandWaitStrategy[command='%s', timeout=%s]",
            String.join(" ", command), formatDuration(timeout));
    }
}
