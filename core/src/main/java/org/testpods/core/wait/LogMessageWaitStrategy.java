package org.testpods.core.wait;

import org.testpods.core.pods.TestPod;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Waits for a specific log message pattern to appear in the pod's logs.
 * <p>
 * Uses regex matching against the container logs. This is useful when
 * the readiness probe doesn't capture the full initialization sequence,
 * or when you need to wait for a specific startup message.
 * <p>
 * Common patterns:
 * <pre>{@code
 * // MongoDB ready
 * WaitStrategy.forLogMessage(".*Waiting for connections.*")
 * 
 * // Kafka ready
 * WaitStrategy.forLogMessage(".*\\[KafkaServer id=\\d+\\] started.*")
 * 
 * // Spring Boot application
 * WaitStrategy.forLogMessage(".*Started \\w+ in .* seconds.*")
 * 
 * // PostgreSQL ready
 * WaitStrategy.forLogMessage(".*database system is ready to accept connections.*")
 * 
 * // Wait for message to appear twice (e.g., after restart)
 * WaitStrategy.forLogMessage(".*Ready.*", 2)
 * }</pre>
 * <p>
 * Default timeout: 2 minutes<br>
 * Default poll interval: 1 second
 */
public class LogMessageWaitStrategy implements WaitStrategy {

    private final String regex;
    private final Pattern pattern;
    private final int times;
    private final Duration timeout;
    private final Duration pollInterval;

    /**
     * Create a new LogMessageWaitStrategy.
     *
     * @param regex The regex pattern to match
     * @param times The number of times the pattern must appear
     * @throws PatternSyntaxException if the regex is invalid
     */
    LogMessageWaitStrategy(String regex, int times) {
        this(regex, times, Duration.ofMinutes(2), Duration.ofSeconds(1));
    }

    private LogMessageWaitStrategy(String regex, int times, Duration timeout, Duration pollInterval) {
        this.regex = Objects.requireNonNull(regex, "regex must not be null");
        this.pattern = Pattern.compile(regex, Pattern.MULTILINE);
        if (times < 1) {
            throw new IllegalArgumentException("times must be at least 1");
        }
        this.times = times;
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval must not be null");
    }

    @Override
    public void waitUntilReady(TestPod<?> pod) {
        Objects.requireNonNull(pod, "pod must not be null");
        
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeout.toMillis();
        long pollMillis = pollInterval.toMillis();
        
        String lastLogs = "";
        int lastMatchCount = 0;
        Exception lastException = null;
        
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try {
                String logs = pod.getLogs();
                if (logs != null && !logs.isEmpty()) {
                    lastLogs = logs;
                    int matchCount = countMatches(logs);
                    lastMatchCount = matchCount;
                    
                    if (matchCount >= times) {
                        return; // Success!
                    }
                }
            } catch (Exception e) {
                // Pod might not be running yet, logs might not be available
                lastException = e;
            }
            
            try {
                Thread.sleep(pollMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                    "Interrupted while waiting for log message in pod '" + pod.getName() + "'", e);
            }
        }
        
        // Timeout reached - build helpful error message
        StringBuilder message = new StringBuilder();
        message.append(String.format(
            "Timed out after %s waiting for log message matching '%s' ",
            formatDuration(timeout), regex));
        
        if (times > 1) {
            message.append(String.format("to appear %d times (found %d) ", times, lastMatchCount));
        }
        message.append(String.format("in pod '%s'", pod.getName()));
        
        if (lastException != null) {
            message.append(": ").append(lastException.getMessage());
        }
        
        // Include a snippet of the logs for debugging
        if (!lastLogs.isEmpty()) {
            String logSnippet = getLogSnippet(lastLogs, 500);
            message.append("\n\nLast log output:\n").append(logSnippet);
        }
        
        throw new IllegalStateException(message.toString(), lastException);
    }

    /**
     * Count how many times the pattern matches in the logs.
     */
    private int countMatches(String logs) {
        Matcher matcher = pattern.matcher(logs);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
    
    /**
     * Get a snippet of the logs for error messages.
     */
    private String getLogSnippet(String logs, int maxLength) {
        if (logs.length() <= maxLength) {
            return logs;
        }
        // Return the last N characters (most recent logs are usually at the end)
        return "..." + logs.substring(logs.length() - maxLength);
    }

    @Override
    public WaitStrategy withTimeout(Duration timeout) {
        return new LogMessageWaitStrategy(regex, times, timeout, this.pollInterval);
    }

    @Override
    public WaitStrategy withPollInterval(Duration interval) {
        return new LogMessageWaitStrategy(regex, times, this.timeout, interval);
    }
    
    /**
     * Get the regex pattern being matched.
     *
     * @return The regex pattern
     */
    public String getRegex() {
        return regex;
    }
    
    /**
     * Get the number of times the pattern must appear.
     *
     * @return The required match count
     */
    public int getTimes() {
        return times;
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
        return String.format("LogMessageWaitStrategy[regex='%s', times=%d, timeout=%s]",
            regex, times, formatDuration(timeout));
    }
}
