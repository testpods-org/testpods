package org.testpods.core;

/**
 * Thrown when a TestPod fails to start. Contains context about which pod failed and the underlying
 * cause.
 */
public class TestPodStartException extends RuntimeException {

  private final String podName;

  /**
   * Create a TestPodStartException with pod name and cause.
   *
   * @param podName the name of the pod that failed to start
   * @param message additional context about the failure
   * @param cause the underlying cause of the failure
   */
  public TestPodStartException(String podName, String message, Throwable cause) {
    super("Failed to start pod '" + podName + "': " + message, cause);
    this.podName = podName;
  }

  /**
   * Create a TestPodStartException from a message and cause.
   *
   * @param message the error message (should contain pod name in quotes)
   * @param cause the underlying cause
   */
  public TestPodStartException(String message, Throwable cause) {
    super(message, cause);
    this.podName = extractPodName(message);
  }

  /**
   * Get the name of the pod that failed to start.
   *
   * @return the pod name, or "unknown" if it couldn't be determined
   */
  public String getPodName() {
    return podName;
  }

  private static String extractPodName(String message) {
    // Best effort extraction from message like "Failed to start pod 'name': ..."
    if (message != null && message.contains("'")) {
      int start = message.indexOf("'") + 1;
      int end = message.indexOf("'", start);
      if (end > start) {
        return message.substring(start, end);
      }
    }
    return "unknown";
  }
}
